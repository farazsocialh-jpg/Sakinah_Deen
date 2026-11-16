package com.sakinah.deen.service

import android.content.Context
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.util.TimeZoneResolver
import java.time.ZoneId
import org.koin.core.context.GlobalContext

/** Shared entry point for arming Azaan alarms from Activity, onboarding, and Settings. */
object AzaanScheduling {

    /**
     * Resolves the best available location (saved/manual first, then GPS), persists new GPS
     * coordinates, and registers the daily exact-alarm table when alarms are enabled.
     *
     * The master Azaan switch belongs to the user: this helper never turns it back on, so
     * toggling Azaan off in Settings stays off across permission grants and location updates.
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
                    val label = listOf(resolved.cityName, resolved.countryName)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                        .ifBlank { prefs.locationLabel }
                    val countryCode = resolved.countryCode.takeIf { it.isNotBlank() }
                    // GPS can land in a different timezone than the last saved city; resolve
                    // the zone from the new fix instead of keeping the stale preference.
                    val zoneId = TimeZoneResolver.resolveZoneIdString(
                        explicitZoneId = null,
                        cityLabel = label,
                        countryCode = countryCode,
                        longitude = resolved.longitude
                    )
                    prefs.saveGpsLocation(
                        latitude = resolved.latitude,
                        longitude = resolved.longitude,
                        label = label,
                        countryCode = countryCode,
                        timeZoneId = zoneId
                    )
                    Pair(resolved.latitude, resolved.longitude)
                } else {
                    saved ?: resolved?.let { Pair(it.latitude, it.longitude) }
                }
            }
            else -> saved
        }

        // Never arm alarms for a placeholder city. If no real location exists yet, clear the
        // pending table and wait for onboarding/GPS or a manual city.
        if (coordinates == null) {
            cancelAllAlarms(context)
            return
        }
        schedule(context, prefs, coordinates)
    }

    /** Re-arms alarms from the already persisted location, without touching GPS. */
    fun scheduleStoredLocation(context: Context) {
        val prefs = SakinahPreferences(context)
        val coordinates = prefs.locationCoordinates
        if (coordinates == null) {
            cancelAllAlarms(context)
            return
        }
        schedule(context, prefs, coordinates)
    }

    private fun schedule(
        context: Context,
        prefs: SakinahPreferences,
        coordinates: Pair<Double, Double>
    ) {
        // Respect the user's master switch: only onboarding or the Settings toggle may flip it.
        if (!prefs.alarmsEnabled) {
            cancelAllAlarms(context)
            return
        }
        PrayerAlarmScheduler(
            context = context,
            astronomyEngine = PrayerCalculationEngineImpl(),
            preferences = prefs,
            prayerTimes = resolvePrayerTimeRepository()
        ).scheduleAllDailyAlarms(
            coordinates = coordinates,
            method = prefs.calculationMethodOverride,
            madhhab = prefs.madhhab
        )
    }

    private fun cancelAllAlarms(context: Context) {
        runCatching {
            PrayerAlarmScheduler(
                context = context,
                astronomyEngine = PrayerCalculationEngineImpl(),
                preferences = SakinahPreferences(context)
            ).cancelAllAlarms()
        }
    }

    /** Repository from the running Koin graph so online/cached timings are used when available. */
    private fun resolvePrayerTimeRepository(): PrayerTimeRepository? =
        runCatching { GlobalContext.getOrNull()?.get<PrayerTimeRepository>() }.getOrNull()

    /** Zone of the saved prayer location, used by receivers that need "today" there. */
    fun activeZoneId(prefs: SakinahPreferences): ZoneId =
        TimeZoneResolver.resolveZoneId(prefs.locationTimeZoneId ?: ZoneId.systemDefault().id)
}
