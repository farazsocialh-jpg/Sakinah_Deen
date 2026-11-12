package com.sakinah.deen

import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.domain.travel.SafarPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "never show 2-rakat Safar by accident" rules. */
class SafarPolicyTest {

    private val doha = Pair(25.2854, 51.5310)
    private val farAway = Pair(32.0, 35.0) // ~900 km from Doha

    @Test
    fun noSavedHome_neverShortensPrayers() {
        assertFalse(SafarPolicy.isTraveler(null, farAway))
        assertFalse(SafarPolicy.isTraveler(null, Pair(0.0, 0.0)))

        val rakats = SafarPolicy.rakats(SafarPolicy.isTraveler(null, farAway))
        assertEquals(4, rakats[PrayerType.DHUHR])
        assertEquals(4, rakats[PrayerType.ASR])
        assertEquals(4, rakats[PrayerType.ISHA])
    }

    @Test
    fun zeroCoordinates_areTreatedAsNoHome() {
        assertFalse(SafarPolicy.isValidCoordinates(Pair(0.0, 0.0)))
        assertNull(SafarPolicy.sanitize(Pair(0.0, 0.0)))

        val isTraveler = SafarPolicy.isTraveler(Pair(0.0, 0.0), farAway)
        assertFalse(isTraveler)

        val rakats = SafarPolicy.rakats(isTraveler)
        assertEquals(4, rakats[PrayerType.DHUHR])
        assertEquals(4, rakats[PrayerType.ASR])
        assertEquals(4, rakats[PrayerType.ISHA])
    }

    @Test
    fun zeroCoordinatesAsCurrentFix_neverShorten() {
        // Home is valid, but the current fix has not resolved yet.
        assertFalse(SafarPolicy.isTraveler(doha, Pair(0.0, 0.0)))
    }

    @Test
    fun outOfRangeCoordinates_areInvalid() {
        assertFalse(SafarPolicy.isValidCoordinates(Pair(95.0, 20.0)))
        assertFalse(SafarPolicy.isValidCoordinates(Pair(20.0, 200.0)))
        assertFalse(SafarPolicy.isValidCoordinates(Pair(Double.NaN, 20.0)))
    }

    @Test
    fun savedHome_awayFromIt_shortensPrayers() {
        assertTrue(SafarPolicy.isTraveler(doha, farAway))

        val rakats = SafarPolicy.rakats(true)
        assertEquals(2, rakats[PrayerType.DHUHR])
        assertEquals(2, rakats[PrayerType.ASR])
        assertEquals(2, rakats[PrayerType.ISHA])
    }

    @Test
    fun backWithinThreshold_clearsManualTravelMode() {
        val nearHome = Pair(25.30, 51.50) // ~3 km from Doha

        assertTrue(SafarPolicy.shouldClearManualTravelMode(doha, nearHome))
        assertFalse(SafarPolicy.shouldClearManualTravelMode(doha, farAway))
        // No saved Home: nothing to return to, so a manual override is left alone.
        assertFalse(SafarPolicy.shouldClearManualTravelMode(null, nearHome))
        assertFalse(SafarPolicy.shouldClearManualTravelMode(Pair(0.0, 0.0), nearHome))
    }
}
