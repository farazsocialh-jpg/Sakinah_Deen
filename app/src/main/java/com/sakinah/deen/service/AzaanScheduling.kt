package com.sakinah.deen.service

import android.content.Context
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import java.time.ZoneId

/** Shared entry point for arming Azaan alarms from Activity, onboarding, and Settings. */
object AzaanScheduling {

    /**
     * Resolves the best available location (saved/manual first, then GPS), persists new GPS
     * coordinates, enables alarms, and immediately registers the daily exact-alarm table.
     */
    suspend fun scheduleWithBestLocation(
        context: Context,
        locationProvider: UserLocationProvider? = null
    ) {
        val prefs = SakinahPreferences(context)

        val saved = prefs.locationCoordinates
        val coordinates = when {
            saved != null && prefs.isManualLocation -> saved
            saved != null && locationProvider == null -> saved
            locationProvider != null -> {
                val resolved = runCatching { locationProvider.getFreshOrCachedLocation() }.getOrNull()
                if (resolved != null && resolved.isFromGps) {
                    prefs.saveGpsLocation(
                        latitude = resolved.latitude,
                        longitude = resolved.longitude,
                        label = listOf(resolved.cityName, resolved.countryName)
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                            .ifBlank { prefs.locationLabel },
                        countryCode = resolved.countryCode.takeIf { it.isNotBlank() },
                        timeZoneId = prefs.locationTimeZoneId ?: ZoneId.systemDefault().id
                    )
                    Pair(resolved.latitude, resolved.longitude)
                } else {
                    saved ?: resolved?.let { Pair(it.latitude, it.longitude) }
                }
            }
            else -> saved
        }

        // Never arm alarms for a placeholder city. If no real location exists yet, leave
        // alarms disabled until onboarding/GPS or a manual city supplies coordinates.
        if (coordinates == null) {
            prefs.alarmsEnabled = false
            return
        }
        prefs.alarmsEnabled = true
        schedule(context, prefs, coordinates)
    }

    /** Re-arms alarms from the already persisted location, without touching GPS. */
    fun scheduleStoredLocation(context: Context) {
        val prefs = SakinahPreferences(context)
        val coordinates = prefs.locationCoordinates
        if (coordinates == null) {
            prefs.alarmsEnabled = false
            return
        }
        prefs.alarmsEnabled = true
        schedule(context, prefs, coordinates)
    }

    private fun schedule(
        context: Context,
        prefs: SakinahPreferences,
        coordinates: Pair<Double, Double>
    ) {
        PrayerAlarmScheduler(
            context = context,
            astronomyEngine = PrayerCalculationEngineImpl(),
            preferences = prefs
        ).scheduleAllDailyAlarms(
            coordinates = coordinates,
            method = prefs.calculationMethodOverride,
            madhhab = prefs.madhhab
        )
    }
}
