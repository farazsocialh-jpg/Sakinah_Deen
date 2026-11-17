package com.sakinah.deen

import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.LocationValidator
import com.sakinah.deen.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coordinates and zone ids are the only inputs that can silently produce a wrong timetable,
 * so they are validated on write *and* on read.
 */
class LocationValidatorTest {

    @Test
    fun rejectsNonFiniteAndOutOfRangeCoordinates() {
        assertFalse(LocationValidator.isValidCoordinates(Double.NaN, 51.5))
        assertFalse(LocationValidator.isValidCoordinates(25.2, Double.NaN))
        assertFalse(LocationValidator.isValidCoordinates(Double.POSITIVE_INFINITY, 0.0))
        assertFalse(LocationValidator.isValidCoordinates(90.1, 0.0))
        assertFalse(LocationValidator.isValidCoordinates(-90.1, 0.0))
        assertFalse(LocationValidator.isValidCoordinates(0.0, 180.1))
        assertFalse(LocationValidator.isValidCoordinates(0.0, -180.1))
    }

    @Test
    fun rejectsTheZeroZeroSentinel() {
        assertFalse(LocationValidator.isValidCoordinates(0.0, 0.0))
        assertNull(LocationValidator.sanitize(0.0, 0.0))
    }

    @Test
    fun acceptsRealLocationsIncludingExtremesAndHalfDegrees() {
        assertTrue(LocationValidator.isValidCoordinates(25.2854, 51.5310))
        assertTrue(LocationValidator.isValidCoordinates(21.422487, 39.826206))
        // Poles and the antimeridian are real places, not errors.
        assertTrue(LocationValidator.isValidCoordinates(90.0, 0.0))
        assertTrue(LocationValidator.isValidCoordinates(-90.0, 180.0))
        assertEquals(Pair(0.1, 0.1), LocationValidator.sanitize(0.1, 0.1))
    }

    @Test
    fun validatesIanaZoneIdsOnly() {
        assertTrue(LocationValidator.isValidZoneId("Asia/Qatar"))
        assertTrue(LocationValidator.isValidZoneId("America/Los_Angeles"))
        assertTrue(LocationValidator.isValidZoneId("UTC"))
        assertFalse(LocationValidator.isValidZoneId(null))
        assertFalse(LocationValidator.isValidZoneId(""))
        assertFalse(LocationValidator.isValidZoneId("   "))
        assertFalse(LocationValidator.isValidZoneId("Mars/Olympus_Mons"))
        assertFalse(LocationValidator.isValidZoneId("+03:00 finally-not-a-zone"))
    }

    @Test
    fun staleOrFutureFixTimestampsAreUnusable() {
        val now = 1_700_000_000_000L
        assertTrue(LocationValidator.isUsableFix(25.2, 51.5, now, now))
        assertTrue(LocationValidator.isUsableFix(25.2, 51.5, null, now))
        assertFalse(LocationValidator.isUsableFix(25.2, 51.5, 0L, now))
        assertFalse(LocationValidator.isUsableFix(25.2, 51.5, now + 10 * 60_000L, now))
        assertFalse(LocationValidator.isUsableFix(Double.NaN, 51.5, now, now))
    }

    @Test
    fun invalidFixIsNeverPersisted() {
        val prefs = SakinahPreferences(FakeSharedPreferences())

        assertFalse(prefs.saveLocation(Double.NaN, 51.5, "Broken"))
        assertFalse(prefs.saveManualLocation(0.0, 0.0, "Null Island", "GH", "Africa/Accra"))
        assertFalse(prefs.saveGpsLocation(200.0, 51.5, "Out of range", "GB", "Europe/London"))

        assertNull(prefs.locationCoordinates)
        assertEquals("", prefs.locationLabel)
        assertFalse(prefs.isManualLocation)
    }

    @Test
    fun invalidZoneIsDroppedButValidOneIsKept() {
        val prefs = SakinahPreferences(FakeSharedPreferences())

        assertTrue(prefs.saveManualLocation(25.2854, 51.5310, "Doha, Qatar", "QA", "Not/AZone"))
        assertNull(prefs.locationTimeZoneId)

        assertTrue(prefs.saveManualLocation(25.2854, 51.5310, "Doha, Qatar", "QA", "Asia/Qatar"))
        assertEquals("Asia/Qatar", prefs.locationTimeZoneId)
    }

    @Test
    fun untrustedStoredCoordinateDegradesToNoLocation() {
        val backing = FakeSharedPreferences()
        val prefs = SakinahPreferences(backing)
        prefs.locationLatitude = 0.0
        prefs.locationLongitude = 0.0
        assertNull(prefs.locationCoordinates)

        // A new install has no location at all instead of an inherited London default.
        val fresh = SakinahPreferences(FakeSharedPreferences())
        assertNull(fresh.locationCoordinates)
        assertEquals("", fresh.locationLabel)
    }
}
