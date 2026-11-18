package com.sakinah.deen

import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.domain.astronomy.ResolvedLocation
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.ui.viewmodel.SalahViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.LocalTime

/** Freshness metadata and the "never a fake clock time" fallback. */
class LocationFreshnessAndFallbackTest {

    private val hour = 60L * 60 * 1000

    @Test
    fun locationAgeAndStalenessFollowTheStoredTimestamp() {
        val now = 1_800_000_000_000L
        val location = ResolvedLocation(
            latitude = 25.2854,
            longitude = 51.5310,
            cityName = "Doha",
            countryName = "Qatar",
            isFromGps = true,
            timestampMillis = now - 2 * hour
        )

        assertEquals(2 * hour, location.ageMillis(now))
        assertFalse(location.isStale(UserLocationProvider.CACHED_LOCATION_MAX_AGE_MS, now))
        assertTrue(location.isStale(1 * hour, now))
    }

    @Test
    fun locationWithoutTimestampIsNotClaimedToBeFreshOrStale() {
        val location = ResolvedLocation(
            latitude = 25.2854,
            longitude = 51.5310,
            cityName = "Doha",
            countryName = "Qatar",
            isFromGps = false
        )
        assertNull(location.ageMillis(1_800_000_000_000L))
        assertFalse(location.isStale(nowMillis = 1_800_000_000_000L))
    }

    @Test
    fun missingPrayerTimeShowsUnavailableMarker_neverAStaticTime() {
        assertEquals("--:--", SalahViewModel.prayerTimeLabel(null))
        assertEquals("--:--", SalahViewModel.UNAVAILABLE_TIME)
        assertEquals(
            "05:00 AM",
            SalahViewModel.prayerTimeLabel(LocalTime(5, 0))
        )
    }

    @Test
    fun unknownStoredPrayerTypeIsSkipped_neverFoldedIntoFajr() {
        assertNull(SalahViewModel.parsePrayerType("NOT_A_PRAYER"))
        assertNull(SalahViewModel.parsePrayerType(null))
        assertEquals(PrayerType.ISHA, SalahViewModel.parsePrayerType("ISHA"))
    }
}
