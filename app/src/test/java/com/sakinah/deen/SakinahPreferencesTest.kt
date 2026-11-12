package com.sakinah.deen

import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.travel.TravelStatusManager
import com.sakinah.deen.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Home Location persistence rules that decide whether Safar may show 2 rakat. */
class SakinahPreferencesTest {

    private fun prefs() = SakinahPreferences(FakeSharedPreferences())

    @Test
    fun homeLocationIsUnsetByDefault() {
        val p = prefs()
        assertNull(p.homeCoordinates)
        assertNull(p.homeCityName)
        assertFalse(p.travelModeActive)
    }

    @Test
    fun zeroCoordinates_doNotBecomeAHome() {
        val p = prefs()
        p.saveHomeAnchor(0.0, 0.0, "Null Island")
        assertNull(p.homeCoordinates)

        // A value written directly (e.g. by an older build) is still ignored.
        p.homeLatitude = 0.0
        p.homeLongitude = 0.0
        assertNull(p.homeCoordinates)
    }

    @Test
    fun savingHomeLocation_resetsTravelMode_andRestoresFourRakat() {
        val p = prefs()
        p.travelModeActive = true
        p.qasrActive = true

        p.saveHomeAnchor(25.2854, 51.5310, "Doha, Qatar")

        assertFalse(p.travelModeActive)
        assertFalse(p.qasrActive)
        assertEquals(Pair(25.2854, 51.5310), p.homeCoordinates)

        val state = TravelStatusManager(p.homeCoordinates)
            .evaluateTravelState(Pair(25.2854, 51.5310))
        assertFalse(state.isTraveler)
        assertEquals(4, state.shortenedRakats[PrayerType.DHUHR])
        assertEquals(4, state.shortenedRakats[PrayerType.ASR])
        assertEquals(4, state.shortenedRakats[PrayerType.ISHA])
    }

    @Test
    fun invalidFix_doesNotOverwriteTheSavedHome() {
        val p = prefs()
        p.saveHomeAnchor(25.2854, 51.5310, "Doha, Qatar")

        p.saveHomeAnchor(0.0, 0.0, "Broken fix")

        assertEquals(Pair(25.2854, 51.5310), p.homeCoordinates)
        assertEquals("Doha, Qatar", p.homeCityName)
    }

    @Test
    fun clearingHomeLocation_returnsToNormalRakat() {
        val p = prefs()
        p.saveHomeAnchor(25.2854, 51.5310, "Doha, Qatar")
        p.travelModeActive = true

        p.clearHomeAnchor()

        assertNull(p.homeCoordinates)
        assertNull(p.homeCityName)
        assertFalse(p.travelModeActive)
        assertFalse(p.qasrActive)

        val state = TravelStatusManager(p.homeCoordinates)
            .evaluateTravelState(Pair(32.0, 35.0))
        assertFalse(state.isTraveler)
        assertEquals(4, state.shortenedRakats[PrayerType.DHUHR])
    }
}
