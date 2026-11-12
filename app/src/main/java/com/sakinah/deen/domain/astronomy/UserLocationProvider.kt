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
    val isFromGps: Boolean
)

class UserLocationProvider(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "sakinah_location_prefs"
        private const val KEY_LAT = "saved_latitude"
        private const val KEY_LON = "saved_longitude"
        private const val KEY_CITY = "saved_city"
        private const val KEY_COUNTRY = "saved_country"

        // Defaults to Makkah al-Mukarramah if permissions are denied and no cache exists
        val DEFAULT_MAKKAH = ResolvedLocation(
            latitude = 21.4225,
            longitude = 39.8262,
            cityName = "Makkah",
            countryName = "Saudi Arabia",
            isFromGps = false
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
                cont.resume(null)
            }
        }

        return if (freshCoords != null) {
            val (lat, lon) = freshCoords
            val (city, country) = resolveCityName(lat, lon)
            val resolved = ResolvedLocation(
                latitude = lat,
                longitude = lon,
                cityName = city,
                countryName = country,
                isFromGps = true
            )
            saveCachedLocation(resolved)
            resolved
        } else {
            getCachedLocation() ?: DEFAULT_MAKKAH
        }
    }

    suspend fun resolveCityName(latitude: Double, longitude: Double): Pair<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val address = addresses?.firstOrNull()
                    val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "Local Area"
                    val country = address?.countryName ?: ""
                    Pair(city, country)
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val address = addresses?.firstOrNull()
                    val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "Local Area"
                    val country = address?.countryName ?: ""
                    Pair(city, country)
                }
            } catch (e: Exception) {
                Pair("Detected Location", "")
            }
        }

    fun getCachedLocation(): ResolvedLocation? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
        val lon = prefs.getFloat(KEY_LON, 0f).toDouble()
        val city = prefs.getString(KEY_CITY, "Saved Location") ?: "Saved Location"
        val country = prefs.getString(KEY_COUNTRY, "") ?: ""
        return ResolvedLocation(lat, lon, city, country, isFromGps = false)
    }

    fun saveCachedLocation(location: ResolvedLocation) {
        prefs.edit()
            .putFloat(KEY_LAT, location.latitude.toFloat())
            .putFloat(KEY_LON, location.longitude.toFloat())
            .putString(KEY_CITY, location.cityName)
            .putString(KEY_COUNTRY, location.countryName)
            .apply()
    }
}
