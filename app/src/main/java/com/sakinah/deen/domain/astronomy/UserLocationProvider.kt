package com.sakinah.deen.domain.astronomy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val countryName: String,
    val isFromGps: Boolean,
    /** ISO 3166-1 alpha-2 code, used to pick the country's Sunni calculation authority. */
    val countryCode: String = ""
)

class UserLocationProvider(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "sakinah_location_prefs"
        private const val KEY_LAT = "saved_latitude"
        private const val KEY_LON = "saved_longitude"
        private const val KEY_CITY = "saved_city"
        private const val KEY_COUNTRY = "saved_country"
        private const val KEY_COUNTRY_CODE = "saved_country_code"

        // Defaults to Makkah al-Mukarramah if permissions are denied and no cache exists
        val DEFAULT_MAKKAH = ResolvedLocation(
            latitude = 21.4225,
            longitude = 39.8262,
            cityName = "Makkah",
            countryName = "Saudi Arabia",
            isFromGps = false,
            countryCode = "SA"
        )
    }

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshOrCachedLocation(): ResolvedLocation {
        if (!hasLocationPermission()) {
            return getCachedLocation() ?: DEFAULT_MAKKAH
        }

        val freshCoords: Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(Pair(loc.latitude, loc.longitude))
                    } else {
                        // Fresh high-accuracy query with fallback
                        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { freshLoc ->
                                if (freshLoc != null) {
                                    cont.resume(Pair(freshLoc.latitude, freshLoc.longitude))
                                } else {
                                    cont.resume(null)
                                }
                            }
                            .addOnFailureListener {
                                cont.resume(null)
                            }
                    }
                }.addOnFailureListener {
                    cont.resume(null)
                }
            } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
                cont.resume(null)
            }
        }

        return if (freshCoords != null) {
            val (lat, lon) = freshCoords
            val area = resolveArea(lat, lon)
            val resolved = ResolvedLocation(
                latitude = lat,
                longitude = lon,
                cityName = area.city,
                countryName = area.countryName,
                isFromGps = true,
                countryCode = area.countryCode
            )
            saveCachedLocation(resolved)
            resolved
        } else {
            getCachedLocation() ?: DEFAULT_MAKKAH
        }
    }

    suspend fun resolveCityName(latitude: Double, longitude: Double): Pair<String, String> {
        val area = resolveArea(latitude, longitude)
        return Pair(area.city, area.countryName)
    }

    /** City, country name and ISO country code for a coordinate pair. */
    suspend fun resolveArea(latitude: Double, longitude: Double): AreaNames =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }
                val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "Local Area"
                AreaNames(
                    city = city,
                    countryName = address?.countryName ?: "",
                    countryCode = address?.countryCode.orEmpty().uppercase(Locale.US)
                )
            } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
                AreaNames(city = "Detected Location", countryName = "", countryCode = "")
            }
        }

    fun getCachedLocation(): ResolvedLocation? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
        val lon = prefs.getFloat(KEY_LON, 0f).toDouble()
        val city = prefs.getString(KEY_CITY, "Saved Location") ?: "Saved Location"
        val country = prefs.getString(KEY_COUNTRY, "") ?: ""
        val countryCode = prefs.getString(KEY_COUNTRY_CODE, "") ?: ""
        return ResolvedLocation(lat, lon, city, country, isFromGps = false, countryCode = countryCode)
    }

    fun saveCachedLocation(location: ResolvedLocation) {
        prefs.edit()
            .putFloat(KEY_LAT, location.latitude.toFloat())
            .putFloat(KEY_LON, location.longitude.toFloat())
            .putString(KEY_CITY, location.cityName)
            .putString(KEY_COUNTRY, location.countryName)
            .putString(KEY_COUNTRY_CODE, location.countryCode)
            .apply()
    }
}

/** Reverse-geocoded place names for a coordinate pair. */
data class AreaNames(
    val city: String,
    val countryName: String,
    val countryCode: String
)
