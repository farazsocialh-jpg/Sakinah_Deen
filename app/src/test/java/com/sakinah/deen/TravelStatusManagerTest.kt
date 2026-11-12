package com.sakinah.deen

import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.domain.travel.TravelStatusManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Default Muqim rakats vs Qasr, and the 80 km / saved-Home rule. */
class TravelStatusManagerTest {

    @Test
    fun noHomeSaved_neverActivatesQasr_evenWhenFarAway() {
        val manager = TravelStatusManager()
        val state = manager.evaluateTravelState(Pair(0.0, 0.0)) // far from default London anchor

        assertFalse(manager.hasHomeAnchor)
        assertFalse(state.isTraveler)
        assertFalse(state.isQasrActive)
        assertEquals(4, state.shortenedRakats[PrayerType.DHUHR])
        assertEquals(4, state.shortenedRakats[PrayerType.ASR])
        assertEquals(4, state.shortenedRakats[PrayerType.ISHA])
        assertEquals(2, state.shortenedRakats[PrayerType.FAJR])
        assertEquals(3, state.shortenedRakats[PrayerType.MAGHRIB])
    }

    @Test
    fun homeSaved_butUnder80km_staysMuqim() {
        val manager = TravelStatusManager()
        manager.setHomeAnchor(Pair(25.0, 55.0)) // Dubai-ish
        val nearby = Pair(25.1, 55.1) // ~15 km away
        val state = manager.evaluateTravelState(nearby)

        assertTrue(manager.hasHomeAnchor)
        assertFalse(state.isTraveler)
        assertFalse(state.isQasrActive)
        assertEquals(4, state.shortenedRakats[PrayerType.DHUHR])
        assertEquals(4, state.shortenedRakats[PrayerType.ASR])
        assertEquals(4, state.shortenedRakats[PrayerType.ISHA])
    }

    @Test
    fun homeSaved_andAtLeast80km_activatesQasr() {
        val manager = TravelStatusManager()
        manager.setHomeAnchor(Pair(25.0, 55.0))
        // ~900 km away
        val farAway = Pair(32.0, 35.0)
        val state = manager.evaluateTravelState(farAway)

        assertTrue(state.isTraveler)
        assertTrue(state.isQasrActive)
        assertEquals(2, state.shortenedRakats[PrayerType.DHUHR])
        assertEquals(2, state.shortenedRakats[PrayerType.ASR])
        assertEquals(2, state.shortenedRakats[PrayerType.ISHA])
        assertEquals(2, state.shortenedRakats[PrayerType.FAJR])
        assertEquals(3, state.shortenedRakats[PrayerType.MAGHRIB])
    }

    @Test
    fun exactlyAtThresholdBoundary() {
        val manager = TravelStatusManager()
        manager.setHomeAnchor(Pair(0.0, 0.0))
        // 80 km north
        val km80Lat = 80.0 / 111.0
        val state = manager.evaluateTravelState(Pair(km80Lat, 0.0))
        assertTrue(state.distanceKm >= TravelStatusManager.TRAVEL_THRESHOLD_KM)
        assertTrue(state.isQasrActive)
    }

    @Test
    fun clearHomeAnchor_returnsToMuqim() {
        val manager = TravelStatusManager()
        manager.setHomeAnchor(Pair(25.0, 55.0))
        manager.clearHomeAnchor()
        assertFalse(manager.hasHomeAnchor)
        val state = manager.evaluateTravelState(Pair(0.0, 0.0))
        assertFalse(state.isQasrActive)
        assertEquals(4, state.shortenedRakats[PrayerType.DHUHR])
    }

    @Test
    fun haversineDistance_isSymmetric() {
        val manager = TravelStatusManager()
        val a = Pair(25.0, 55.0)
        val b = Pair(32.0, 35.0)
        assertEquals(
            manager.calculateHaversineDistanceKm(a, b),
            manager.calculateHaversineDistanceKm(b, a),
            0.001
        )
    }
}
