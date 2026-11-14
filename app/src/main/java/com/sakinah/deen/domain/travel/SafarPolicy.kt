package com.sakinah.deen.domain.travel

import com.sakinah.deen.data.model.PrayerType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure rules for when Safar (Qasr) applies.
 *
 * Kept free of Android dependencies so the "never shorten prayers by accident"
 * invariants can be unit tested directly. A null or `(0.0, 0.0)` Home Location is
 * always treated as "no Home saved", which means normal (four rakat) salah.
 */
object SafarPolicy {

    const val THRESHOLD_KM = 80.0
    private const val EARTH_RADIUS_KM = 6371.0

    val MUQIM_RAKATS: Map<PrayerType, Int> = mapOf(
        PrayerType.FAJR to 2,
        PrayerType.DHUHR to 4,
        PrayerType.ASR to 4,
        PrayerType.MAGHRIB to 3,
        PrayerType.ISHA to 4
    )

    val QASR_RAKATS: Map<PrayerType, Int> = mapOf(
        PrayerType.FAJR to 2,
        PrayerType.DHUHR to 2,
        PrayerType.ASR to 2,
        PrayerType.MAGHRIB to 3,
        PrayerType.ISHA to 2
    )

    /**
     * `(0.0, 0.0)` is the null island: what an unresolved GPS fix or a blank
     * preference looks like. It is never a real Home Location.
     */
    fun isValidCoordinates(coordinates: Pair<Double, Double>?): Boolean {
        val (latitude, longitude) = coordinates ?: return false
        if (latitude.isNaN() || longitude.isNaN()) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /** Returns the coordinates when they can be a real Home, otherwise `null`. */
    fun sanitize(coordinates: Pair<Double, Double>?): Pair<Double, Double>? =
        coordinates?.takeIf { isValidCoordinates(it) }

    fun distanceKm(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
        val lat1 = Math.toRadians(from.first)
        val lon1 = Math.toRadians(from.second)
        val lat2 = Math.toRadians(to.first)
        val lon2 = Math.toRadians(to.second)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = (
            sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            ).coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /** Distance from a saved Home, or `0.0` when either side is not a real fix. */
    fun distanceFromHomeKm(
        home: Pair<Double, Double>?,
        current: Pair<Double, Double>?
    ): Double {
        val validHome = sanitize(home) ?: return 0.0
        val validCurrent = sanitize(current) ?: return 0.0
        return distanceKm(validHome, validCurrent)
    }

    fun isTraveler(home: Pair<Double, Double>?, current: Pair<Double, Double>?): Boolean {
        val validHome = sanitize(home) ?: return false
        val validCurrent = sanitize(current) ?: return false
        return distanceKm(validHome, validCurrent) >= THRESHOLD_KM
    }

    fun rakats(isTraveler: Boolean): Map<PrayerType, Int> =
        if (isTraveler) QASR_RAKATS else MUQIM_RAKATS

    /**
     * Travel Mode is a manual override from the Travel screen. Once the user is back
     * within the threshold of a saved Home, the override must be dropped so the
     * dashboard returns to four rakat.
     */
    fun shouldClearManualTravelMode(
        home: Pair<Double, Double>?,
        current: Pair<Double, Double>?
    ): Boolean {
        if (sanitize(home) == null) return false
        return distanceFromHomeKm(home, current) < THRESHOLD_KM
    }
}
