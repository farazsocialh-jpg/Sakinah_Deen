package com.sakinah.deen.domain.astronomy

/**
 * Why the prayer screen decided to refresh itself after the app came back to the foreground.
 *
 * Kept pure (no Android types) so the "permission granted in system settings" and "rapid
 * timezone travel" cases can be unit tested: both used to leave the table on the old zone
 * until the user pulled to refresh.
 */
enum class SalahRefreshReason {
    /** Nothing relevant changed. */
    NONE,

    /** Location permission was granted while the app was backgrounded. */
    PERMISSION_GRANTED,

    /** Location permission was revoked; the screen must stop claiming a GPS fix. */
    PERMISSION_REVOKED,

    /** The device timezone changed (flight, manual clock change, DST). */
    SYSTEM_ZONE_CHANGED
}

object SalahRefreshPolicy {

    /**
     * @param hadPermission whether the app held location permission when it went to the
     *   background.
     * @param hasPermission whether it holds it now.
     * @param previousSystemZone the device's default zone id when it went to the background.
     * @param currentSystemZone the device's default zone id now.
     * @param isManualLocation true when the reader picked a city, in which case the device
     *   zone is irrelevant to the prayer table (the saved location zone wins).
     */
    fun decide(
        hadPermission: Boolean,
        hasPermission: Boolean,
        previousSystemZone: String?,
        currentSystemZone: String?,
        isManualLocation: Boolean
    ): SalahRefreshReason {
        if (!hadPermission && hasPermission) return SalahRefreshReason.PERMISSION_GRANTED
        if (hadPermission && !hasPermission) return SalahRefreshReason.PERMISSION_REVOKED
        val zoneChanged = !previousSystemZone.isNullOrBlank() &&
            !currentSystemZone.isNullOrBlank() &&
            previousSystemZone != currentSystemZone
        if (zoneChanged && !isManualLocation) return SalahRefreshReason.SYSTEM_ZONE_CHANGED
        return SalahRefreshReason.NONE
    }
}
