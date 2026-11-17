package com.sakinah.deen.domain.astronomy

import java.time.ZoneId

/**
 * Guard for everything that becomes a prayer-time input.
 *
 * The app once persisted whatever coordinates arrived from a fix, a geocoder fallback or a
 * restored backup, including `(0, 0)` (the Gulf of Guinea) and a couple of `NaN` values from
 * a failed sensor read. Those silently produced a plausible-looking but wrong timetable for
 * the wrong place, so every write now goes through here.
 */
object LocationValidator {

    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0

    /**
     * True only for a finite, in-range pair that is not the classic `(0, 0)` "no fix yet"
     * sentinel. Exact 0/0 is treated as "unknown" rather than as a real location.
     */
    fun isValidCoordinates(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) return false
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) return false
        if (latitude == 0.0 && longitude == 0.0) return false
        return true
    }

    /** Sanitised copy of a coordinate pair, or null when it cannot be trusted. */
    fun sanitize(latitude: Double, longitude: Double): Pair<Double, Double>? =
        if (isValidCoordinates(latitude, longitude)) Pair(latitude, longitude) else null

    /** True when [zoneId] is a real IANA zone the platform can resolve. */
    fun isValidZoneId(zoneId: String?): Boolean {
        if (zoneId.isNullOrBlank()) return false
        val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return false
        // A zone whose rules cannot be read (malformed custom offset) is not usable either.
        return runCatching { zone.rules }.isSuccess
    }

    /**
     * Coordinate-stamped fix, kept together so a stale latitude can never be paired with a
     * fresh longitude after a failed update.
     */
    fun isUsableFix(latitude: Double, longitude: Double, fixTimestampMillis: Long?, nowMillis: Long): Boolean {
        if (!isValidCoordinates(latitude, longitude)) return false
        if (fixTimestampMillis == null) return true
        if (fixTimestampMillis <= 0L || fixTimestampMillis > nowMillis + 60_000L) return false
        return true
    }
}
