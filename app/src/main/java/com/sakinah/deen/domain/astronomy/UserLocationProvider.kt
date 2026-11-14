package com.sakinah.deen.domain.astronomy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
        private const val KEY_LAT_BITS = "saved_latitude_bits"
        private const val KEY_LON_BITS = "saved_longitude_bits"
        private const val KEY_CITY = "saved_city"
        private const val KEY_COUNTRY = "saved_country"
        private const val KEY_COUNTRY_CODE = "saved_country_code"

        /** A cached fix older than this is not considered a usable "current" location. */
        private const val LAST_LOCATION_MAX_AGE_MS = 5 * 60 * 1000L

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

        val freshCoords: Pair<Double, Double>? = requestFreshCoordinates()
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

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshCoordinates(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val tokenSource = CancellationTokenSource()
            cont.invokeOnCancellation { tokenSource.cancel() }
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    if (!cont.isActive) return@addOnSuccessListener
                    val age = if (loc != null) System.currentTimeMillis() - loc.time else Long.MAX_VALUE
                    if (loc != null && age in 0..LAST_LOCATION_MAX_AGE_MS) {
                        cont.resume(Pair(loc.latitude, loc.longitude))
                    } else {
                        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                            .addOnSuccessListener { fresh ->
                                if (cont.isActive) {
                                    cont.resume(fresh?.let { Pair(it.latitude, it.longitude) })
                                }
                            }
                            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                            .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                    }
                }.addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (cont.isActive) cont.resume(null)
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
                    geocodeAsync(geocoder, latitude, longitude)
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): android.location.Address? = suspendCancellableCoroutine { cont ->
        val listener = Geocoder.GeocodeListener { addresses ->
            if (cont.isActive) cont.resume(addresses.firstOrNull())
        }
        try {
            geocoder.getFromLocation(latitude, longitude, 1, listener)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (cont.isActive) cont.resume(null)
        }
    }

    fun getCachedLocation(): ResolvedLocation? {
        if (!prefs.contains(KEY_LAT_BITS) && !prefs.contains(KEY_LAT)) return null
        val lat = readDouble(KEY_LAT_BITS, KEY_LAT) ?: return null
        val lon = readDouble(KEY_LON_BITS, KEY_LON) ?: return null
        val city = prefs.getString(KEY_CITY, "Saved Location") ?: "Saved Location"
        val country = prefs.getString(KEY_COUNTRY, "") ?: ""
        val countryCode = prefs.getString(KEY_COUNTRY_CODE, "") ?: ""
        return ResolvedLocation(lat, lon, city, country, isFromGps = false, countryCode = countryCode)
    }

    private fun readDouble(bitsKey: String, legacyFloatKey: String): Double? {
        return if (prefs.contains(bitsKey)) {
            Double.fromBits(prefs.getLong(bitsKey, 0L))
        } else {
            @Suppress("DEPRECATION")
            prefs.getFloat(legacyFloatKey, 0f).toDouble()
        }
    }

    fun saveCachedLocation(location: ResolvedLocation) {
        prefs.edit()
            .putLong(KEY_LAT_BITS, location.latitude.toRawBits())
            .putLong(KEY_LON_BITS, location.longitude.toRawBits())
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
