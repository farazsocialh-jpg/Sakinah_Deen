package com.sakinah.deen.widget

import androidx.core.content.edit

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Nearest mosque to the user's saved coordinates. */
data class NearestMasjid(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Straight-line distance in metres. */
    val distanceMeters: Int,
    /** Rough walking time at ~5 km/h, rounded up. */
    val walkingMinutes: Int
) {
    val distanceLabel: String
        get() = if (distanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters} m"
        }
    val walkingLabel: String get() = "~${walkingMinutes} min walk"
}

private const val CACHE_PREFS = "sakinah_masjid_cache"
private const val KEY_LAT = "q_lat"
private const val KEY_LON = "q_lon"
private const val KEY_NAME = "name"
private const val KEY_MLAT = "m_lat"
private const val KEY_MLON = "m_lon"
private const val KEY_DIST = "dist_m"
private const val KEY_WALK = "walk_min"
private const val KEY_FOUND = "found"
private const val KEY_FETCHED = "fetched_at"
private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours
private const val LOCATION_TOLERANCE = 0.001 // ~100 m

private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun readCache(context: Context, lat: Double, lon: Double): NearestMasjid? {
    val p = prefs(context)
    if (!p.contains(KEY_FETCHED)) return null
    val qLat = p.getFloat(KEY_LAT, Float.NaN).toDouble()
    val qLon = p.getFloat(KEY_LON, Float.NaN).toDouble()
    if (qLat.isNaN() || qLon.isNaN()) return null
    if (kotlin.math.abs(qLat - lat) > LOCATION_TOLERANCE || kotlin.math.abs(qLon - lon) > LOCATION_TOLERANCE) return null
    val fetched = p.getLong(KEY_FETCHED, 0L)
    if (System.currentTimeMillis() - fetched > CACHE_TTL_MS) return null
    if (!p.getBoolean(KEY_FOUND, false)) return null
    val name = p.getString(KEY_NAME, null) ?: return null
    val mLat = p.getFloat(KEY_MLAT, Float.NaN).toDouble()
    val mLon = p.getFloat(KEY_MLON, Float.NaN).toDouble()
    if (mLat.isNaN() || mLon.isNaN()) return null
    return NearestMasjid(
        name = name,
        latitude = mLat,
        longitude = mLon,
        distanceMeters = p.getInt(KEY_DIST, 0),
        walkingMinutes = p.getInt(KEY_WALK, 1)
    )
}

private fun writeCache(context: Context, lat: Double, lon: Double, found: NearestMasjid?) {
    prefs(context).edit {
        putFloat(KEY_LAT, lat.toFloat())
        putFloat(KEY_LON, lon.toFloat())
        putLong(KEY_FETCHED, System.currentTimeMillis())
        if (found == null) {
            putBoolean(KEY_FOUND, false)
        } else {
            putBoolean(KEY_FOUND, true)
            putString(KEY_NAME, found.name)
            putFloat(KEY_MLAT, found.latitude.toFloat())
            putFloat(KEY_MLON, found.longitude.toFloat())
            putInt(KEY_DIST, found.distanceMeters)
            putInt(KEY_WALK, found.walkingMinutes)
        }
    }
}

private fun fetchNearest(lat: Double, lon: Double): NearestMasjid? {
    val query = """
        [out:json][timeout:12];
        (
          node["amenity"="mosque"](around:5000,$lat,$lon);
          node["amenity"="place_of_worship"]["religion"="muslim"](around:5000,$lat,$lon);
          way["amenity"="mosque"](around:5000,$lat,$lon);
          way["amenity"="place_of_worship"]["religion"="muslim"](around:5000,$lat,$lon);
        );
        out center;
    """.trimIndent()
    val url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8")
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8000
        readTimeout = 10000
        setRequestProperty("User-Agent", "SakinahDeen-Android/1.0")
        setRequestProperty("Accept", "application/json")
    }
    return try {
        if (connection.responseCode !in 200..299) return null
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val elements = root.optJSONArray("elements") ?: return null
        var best: NearestMasjid? = null
        var bestDist = Double.MAX_VALUE
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val latV = when {
                el.has("lat") -> el.optDouble("lat", Double.NaN)
                el.optJSONObject("center") != null -> el.getJSONObject("center").optDouble("lat", Double.NaN)
                else -> Double.NaN
            }
            val lonV = when {
                el.has("lon") -> el.optDouble("lon", Double.NaN)
                el.optJSONObject("center") != null -> el.getJSONObject("center").optDouble("lon", Double.NaN)
                else -> Double.NaN
            }
            if (latV.isNaN() || lonV.isNaN()) continue
            val tags = el.optJSONObject("tags")
            val name = tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("name:en")?.takeIf { it.isNotBlank() }
                ?: "Masjid"
            val dist = haversineMeters(lat, lon, latV, lonV)
            if (dist < bestDist) {
                bestDist = dist
                val walkMin = kotlin.math.ceil(dist / 83.33).toInt().coerceAtLeast(1)
                best = NearestMasjid(name, latV, lonV, dist.toInt(), walkMin)
            }
        }
        best
    } catch (e: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

/**
 * Returns the nearest mosque, using a 6-hour cache keyed on the user's coordinates.
 * Never throws; returns null when offline or no result.
 */
suspend fun loadNearestMasjid(context: Context, latitude: Double, longitude: Double): NearestMasjid? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        readCache(context, latitude, longitude)?.let { return@withContext it }
        val result = fetchNearest(latitude, longitude)
        writeCache(context, latitude, longitude, result)
        result
    }
