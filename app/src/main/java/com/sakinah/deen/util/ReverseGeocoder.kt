package com.sakinah.deen.util

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Turns the app's saved coordinates into a city/country pair for profile pre-fill.
 *
 * `Geocoder` needs network or a device-side geocoder service, so every failure (offline,
 * no backend, denied permission) simply returns null and the caller keeps the manual fields
 * editable instead of blocking the profile step.
 */
object ReverseGeocoder {

    data class CityCountry(val city: String, val country: String)

    suspend fun lookup(
        context: Context,
        latitude: Double,
        longitude: Double
    ): CityCountry? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            @Suppress("DEPRECATION")
            val address = geocoder.getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return@runCatching null

            val city = address.locality
                ?: address.subAdminArea
                ?: address.adminArea
                ?: ""
            val country = address.countryName ?: ""
            if (city.isBlank() && country.isBlank()) null else CityCountry(city, country)
        }.getOrNull()
    }
}
