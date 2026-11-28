package com.sakinah.deen

import com.sakinah.deen.service.PrayerReminderContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The late reminder must never repeat a reference on two consecutive posts. */
class LatePrayerRotationTest {

    @Test
    fun consecutiveIndicesHaveDifferentSources() {
        val count = PrayerReminderContent.lateReminderCount
        for (index in 0 until count * 3) {
            val current = PrayerReminderContent.lateReminderFor(index)
            val next = PrayerReminderContent.lateReminderFor(index + 1)
            assertNotEquals("index $index and ${index + 1} must differ", current.source, next.source)
            assertNotEquals("index $index and ${index + 1} must differ", current.english, next.english)
        }
    }

    @Test
    fun sourcesAreUniqueAcrossTheWholePool() {
        val sources = (0 until PrayerReminderContent.lateReminderCount)
            .map { PrayerReminderContent.lateReminderFor(it).source }
        assertEquals(sources.size, sources.toSet().size)
    }

    @Test
    fun rotationWrapsAndHandlesNegativeIndices() {
        val count = PrayerReminderContent.lateReminderCount
        assertEquals(
            PrayerReminderContent.lateReminderFor(0).source,
            PrayerReminderContent.lateReminderFor(count).source
        )
        assertEquals(
            PrayerReminderContent.lateReminderFor(1).source,
            PrayerReminderContent.lateReminderFor(count + 1).source
        )
        assertEquals(
            PrayerReminderContent.lateReminderFor(0).source,
            PrayerReminderContent.lateReminderFor(-count).source
        )
        assertEquals(
            PrayerReminderContent.lateReminderFor(count - 1).source,
            PrayerReminderContent.lateReminderFor(-1).source
        )
    }
}
