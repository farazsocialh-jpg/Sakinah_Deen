package com.sakinah.deen.domain.travel

import com.sakinah.deen.data.model.PrayerType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TravelState(
    val homeAnchorCoordinates: Pair<Double, Double>,
    val currentCoordinates: Pair<Double, Double>,
    val distanceKm: Double,
    val isTraveler: Boolean,
    val isQasrActive: Boolean,
    val isJamPermitted: Boolean,
    val shortenedRakats: Map<PrayerType, Int>
)

class TravelStatusManager(
    private var homeAnchorCoordinates: Pair<Double, Double> = Pair(51.5074, -0.1278) // Default London Anchor
) {
    companion object {
        const val TRAVEL_THRESHOLD_KM = 80.0
        private const val EARTH_RADIUS_KM = 6371.0
    }

    fun setHomeAnchor(coordinates: Pair<Double, Double>) {
        this.homeAnchorCoordinates = coordinates
    }

    fun getHomeAnchor(): Pair<Double, Double> = homeAnchorCoordinates

    fun calculateHaversineDistanceKm(
        point1: Pair<Double, Double>,
        point2: Pair<Double, Double>
    ): Double {
        val lat1 = Math.toRadians(point1.first)
        val lon1 = Math.toRadians(point1.second)
        val lat2 = Math.toRadians(point2.first)
        val lon2 = Math.toRadians(point2.second)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun evaluateTravelState(currentCoordinates: Pair<Double, Double>): TravelState {
        val distance = calculateHaversineDistanceKm(homeAnchorCoordinates, currentCoordinates)
        val isTraveler = distance >= TRAVEL_THRESHOLD_KM

        val rakats = if (isTraveler) {
            mapOf(
                PrayerType.FAJR to 2,
                PrayerType.DHUHR to 2, // Shortened from 4
                PrayerType.ASR to 2,   // Shortened from 4
                PrayerType.MAGHRIB to 3,
                PrayerType.ISHA to 2   // Shortened from 4
            )
        } else {
            mapOf(
                PrayerType.FAJR to 2,
                PrayerType.DHUHR to 4,
                PrayerType.ASR to 4,
                PrayerType.MAGHRIB to 3,
                PrayerType.ISHA to 4
            )
        }

        return TravelState(
            homeAnchorCoordinates = homeAnchorCoordinates,
            currentCoordinates = currentCoordinates,
            distanceKm = distance,
            isTraveler = isTraveler,
            isQasrActive = isTraveler,
            isJamPermitted = isTraveler,
            shortenedRakats = rakats
        )
    }
}
