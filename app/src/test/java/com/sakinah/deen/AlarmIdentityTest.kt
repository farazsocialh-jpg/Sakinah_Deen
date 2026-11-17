package com.sakinah.deen

import com.sakinah.deen.service.AlarmIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the audit's #26/#27/#33 findings: alarm identities must be deterministic for the
 * daily table (so cancel always finds the armed alarm) and unique for one-off test/snooze
 * alarms (so concurrent requests cannot overwrite each other).
 */
class AlarmIdentityTest {

    @Test
    fun prayerKey_isStableAndCaseInsensitive() {
        assertEquals(AlarmIdentity.prayerKey("FAJR"), AlarmIdentity.prayerKey("fajr"))
        assertEquals("prayer/FAJR", AlarmIdentity.prayerKey("  fajr "))
    }

    @Test
    fun prayerKey_handlesFreeFormNames() {
        assertEquals("prayer/AL_FATIHA", AlarmIdentity.prayerKey("al fatiha"))
        assertEquals("prayer/SALAH", AlarmIdentity.prayerKey("   "))
    }

    @Test
    fun uri_isDerivedFromTheKey() {
        val key = AlarmIdentity.prayerKey("MAGHRIB")
        assertEquals("sakinah://alarm/prayer/MAGHRIB", AlarmIdentity.uri(key))
        assertTrue(AlarmIdentity.uri(key).startsWith(AlarmIdentity.URI_PREFIX))
    }

    @Test
    fun requestCode_isDeterministicAndNonNegative() {
        val key = AlarmIdentity.prayerKey("ISHA")
        assertEquals(AlarmIdentity.requestCode(key), AlarmIdentity.requestCode(key))
        assertTrue(AlarmIdentity.requestCode(key) >= 0)
    }

    @Test
    fun requestCode_differsPerPrayer() {
        val codes = listOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA")
            .map { AlarmIdentity.requestCode(AlarmIdentity.prayerKey(it)) }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun schedulingAndCancelling_buildTheSameIdentity() {
        // Both paths call AlarmIdentity.prayerKey(prayer) again; nothing else may differ or the
        // armed PendingIntent becomes unreachable and the alarm is orphaned.
        val scheduled = AlarmIdentity.prayerKey("ASR")
        val cancelled = AlarmIdentity.prayerKey("ASR")
        assertEquals(AlarmIdentity.uri(scheduled), AlarmIdentity.uri(cancelled))
        assertEquals(AlarmIdentity.requestCode(scheduled), AlarmIdentity.requestCode(cancelled))
    }

    @Test
    fun testKeys_areUniqueEvenForTheSameMillisecond() {
        val triggerAt = 1_700_000_000_000L
        val keys = (0 until 2_000).map { AlarmIdentity.testKey("DHUHR", triggerAt) }
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.all { it.startsWith("test/DHUHR/$triggerAt-") })
    }

    @Test
    fun snoozeKeys_areUniqueAndDistinctFromTestKeys() {
        val triggerAt = 1_700_000_000_000L
        val snoozes = (0 until 200).map { AlarmIdentity.snoozeKey("FAJR", triggerAt) }
        assertEquals(snoozes.size, snoozes.distinct().size)
        assertNotEquals(
            AlarmIdentity.uri(AlarmIdentity.snoozeKey("FAJR", triggerAt)),
            AlarmIdentity.uri(AlarmIdentity.testKey("FAJR", triggerAt))
        )
    }

    @Test
    fun triggerTimestamp_readsTheEncodedInstant() {
        val key = AlarmIdentity.testKey("FAJR", 1_700_000_123_000L)
        assertEquals(1_700_000_123_000L, AlarmIdentity.triggerTimestamp(key))
        // Prayer keys have no expiry, so they are never pruned.
        assertEquals(null, AlarmIdentity.triggerTimestamp(AlarmIdentity.prayerKey("FAJR")))
    }

    @Test
    fun pruneExpired_dropsOnlyFiredOneOffKeys() {
        val now = 10_000_000L
        val fresh = AlarmIdentity.testKey("FAJR", now + 60_000L)
        val fired = AlarmIdentity.testKey("FAJR", now - 48 * 60 * 60_000L)
        val prayer = AlarmIdentity.prayerKey("FAJR")

        val pruned = AlarmIdentity.pruneExpired(
            keys = listOf(fresh, fired, prayer),
            nowMillis = now,
            graceMillis = 60_000L
        )

        assertTrue(fresh in pruned)
        assertTrue(prayer in pruned)
        assertTrue("a long-fired one-off alarm must not be retried", fired !in pruned)
    }

    @Test
    fun registryEncoding_roundTripsAndNormalises() {
        val keys = listOf("prayer/FAJR", "test/FAJR/100-1", "snooze/ASR/200-2")
        val encoded = AlarmIdentity.encode(keys + keys + listOf("  ", ""))
        assertEquals(keys.sorted(), AlarmIdentity.decode(encoded))
        assertEquals(emptyList<String>(), AlarmIdentity.decode(null))
        assertEquals(emptyList<String>(), AlarmIdentity.decode("  \n \n"))
    }
}
