package com.sakinah.deen.domain.travel

import com.sakinah.deen.data.model.PrayerType

data class TravelState(
    val homeAnchorCoordinates: Pair<Double, Double>?,
    val currentCoordinates: Pair<Double, Double>,
    val distanceKm: Double,
    val isTraveler: Boolean,
    val isQasrActive: Boolean,
    val isJamPermitted: Boolean,
    val shortenedRakats: Map<PrayerType, Int>
)

class TravelStatusManager(
    /** `null` until the user explicitly saves a Home location; Qasr never activates without one. */
    homeAnchorCoordinates: Pair<Double, Double>? = null,
    /** Optional live source (e.g. preferences) so the anchor is never captured once. */
    private val homeAnchorProvider: (() -> Pair<Double, Double>?)? = null
) {
    companion object {
        const val TRAVEL_THRESHOLD_KM = SafarPolicy.THRESHOLD_KM
    }

    /**
     * Invalid anchors — most importantly `(0.0, 0.0)` from an unresolved GPS fix —
     * are stored as "no Home" so they can never activate Qasr.
     */
    private var homeAnchorCoordinates: Pair<Double, Double>? =
        SafarPolicy.sanitize(homeAnchorCoordinates)

    private fun currentHomeAnchor(): Pair<Double, Double>? =
        homeAnchorProvider?.let { SafarPolicy.sanitize(it()) } ?: homeAnchorCoordinates

    /** Whether a Home location has been explicitly saved (as opposed to the unset default). */
    val hasHomeAnchor: Boolean get() = currentHomeAnchor() != null

    fun setHomeAnchor(coordinates: Pair<Double, Double>) {
        this.homeAnchorCoordinates = SafarPolicy.sanitize(coordinates)
    }

    fun clearHomeAnchor() {
        this.homeAnchorCoordinates = null
    }

    fun getHomeAnchor(): Pair<Double, Double>? = currentHomeAnchor()

    fun calculateHaversineDistanceKm(
        point1: Pair<Double, Double>,
        point2: Pair<Double, Double>
    ): Double = SafarPolicy.distanceKm(point1, point2)

    fun evaluateTravelState(currentCoordinates: Pair<Double, Double>): TravelState {
        val home = currentHomeAnchor()
        // No saved Home location => always Muqim (never show 2 rakat by default).
        val distance = SafarPolicy.distanceFromHomeKm(home, currentCoordinates)
        val isTraveler = SafarPolicy.isTraveler(home, currentCoordinates)

        return TravelState(
            homeAnchorCoordinates = home,
            currentCoordinates = currentCoordinates,
            distanceKm = distance,
            isTraveler = isTraveler,
            isQasrActive = isTraveler,
            isJamPermitted = isTraveler,
            shortenedRakats = SafarPolicy.rakats(isTraveler)
        )
    }
}
