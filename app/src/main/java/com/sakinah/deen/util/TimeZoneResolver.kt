package com.sakinah.deen.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Resolves IANA timezones offline for manual city selection, country codes, or fallback coordinates
 * so prayer countdowns and timestamps reflect the selected location rather than the phone's local zone.
 */
object TimeZoneResolver {

    private val countryToZone = mapOf(
        "US" to "America/New_York",
        "CA" to "America/Toronto",
        "GB" to "Europe/London",
        "UK" to "Europe/London",
        "SA" to "Asia/Riyadh",
        "QA" to "Asia/Qatar",
        "AE" to "Asia/Dubai",
        "KW" to "Asia/Kuwait",
        "BH" to "Asia/Bahrain",
        "OM" to "Asia/Muscat",
        "EG" to "Africa/Cairo",
        "JO" to "Asia/Amman",
        "TR" to "Europe/Istanbul",
        "PK" to "Asia/Karachi",
        "IN" to "Asia/Kolkata",
        "BD" to "Asia/Dhaka",
        "MY" to "Asia/Kuala_Lumpur",
        "ID" to "Asia/Jakarta",
        "SG" to "Asia/Singapore",
        "AU" to "Australia/Sydney",
        "FR" to "Europe/Paris",
        "DE" to "Europe/Berlin",
        "MA" to "Africa/Casablanca",
        "NG" to "Africa/Lagos",
        "ZA" to "Africa/Johannesburg",
    )

    private val labelHints = listOf(
        "los angeles" to "America/Los_Angeles",
        "san francisco" to "America/Los_Angeles",
        "seattle" to "America/Los_Angeles",
        "chicago" to "America/Chicago",
        "dallas" to "America/Chicago",
        "houston" to "America/Chicago",
        "new york" to "America/New_York",
        "dearborn" to "America/Detroit",
        "toronto" to "America/Toronto",
        "vancouver" to "America/Vancouver",
        "london" to "Europe/London",
        "paris" to "Europe/Paris",
        "istanbul" to "Europe/Istanbul",
        "cairo" to "Africa/Cairo",
        "makkah" to "Asia/Riyadh",
        "mecca" to "Asia/Riyadh",
        "madinah" to "Asia/Riyadh",
        "medina" to "Asia/Riyadh",
        "riyadh" to "Asia/Riyadh",
        "jeddah" to "Asia/Riyadh",
        "doha" to "Asia/Qatar",
        "dubai" to "Asia/Dubai",
        "abu dhabi" to "Asia/Dubai",
        "amman" to "Asia/Amman",
        "jerusalem" to "Asia/Jerusalem",
        "karachi" to "Asia/Karachi",
        "lahore" to "Asia/Karachi",
        "islamabad" to "Asia/Karachi",
        "delhi" to "Asia/Kolkata",
        "mumbai" to "Asia/Kolkata",
        "dhaka" to "Asia/Dhaka",
        "kuala lumpur" to "Asia/Kuala_Lumpur",
        "jakarta" to "Asia/Jakarta",
        "singapore" to "Asia/Singapore",
        "sydney" to "Australia/Sydney",
        "tokyo" to "Asia/Tokyo",
        "casablanca" to "Africa/Casablanca",
    )

    fun resolveZoneIdString(
        explicitZoneId: String? = null,
        cityLabel: String? = null,
        countryCode: String? = null,
        longitude: Double? = null,
    ): String {
        if (!explicitZoneId.isNullOrBlank()) {
            val valid = runCatching { ZoneId.of(explicitZoneId).id }.getOrNull()
            if (valid != null) return valid
        }
        val lowerLabel = cityLabel?.lowercase().orEmpty()
        labelHints.firstOrNull { (hint, _) -> lowerLabel.contains(hint) }?.let { return it.second }
        countryCode?.uppercase()?.let { code ->
            countryToZone[code]?.let { return it }
        }
        if (longitude != null && longitude.isFinite()) {
            val offsetHours = (longitude / 15.0).roundToInt().coerceIn(-12, 14)
            return if (offsetHours == 0) "UTC" else {
                val sign = if (offsetHours > 0) "+" else "-"
                val absH = kotlin.math.abs(offsetHours).toString().padStart(2, '0')
                "UTC$sign$absH:00"
            }
        }
        return ZoneId.systemDefault().id
    }

    fun resolveZoneId(timeZoneId: String?): ZoneId =
        timeZoneId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

    fun shortAbbreviation(timeZoneId: String?, instant: Instant = Instant.now()): String {
        val zone = resolveZoneId(timeZoneId)
        val zoned = instant.atZone(zone)
        return runCatching {
            zone.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: zoned.offset.id
    }
}
