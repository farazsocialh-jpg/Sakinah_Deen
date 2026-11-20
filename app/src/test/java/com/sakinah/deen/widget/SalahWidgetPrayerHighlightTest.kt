package com.sakinah.deen.widget

import com.sakinah.deen.data.model.PrayerType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalahWidgetPrayerHighlightTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val times = mapOf(
        PrayerType.FAJR to LocalTime.of(5, 0),
        PrayerType.DHUHR to LocalTime.of(12, 30),
        PrayerType.ASR to LocalTime.of(16, 0),
        PrayerType.MAGHRIB to LocalTime.of(18, 30),
        PrayerType.ISHA to LocalTime.of(20, 0)
    )

    @Test
    fun `timestamp inside dhuhr highlights DHUHR`() {
        val now = LocalTime.of(13, 0)
        val widgetTimes = WidgetTimes(zone, LocalDate.of(2026, 11, 19), times)
        assertEquals(PrayerType.DHUHR, currentPrayer(widgetTimes, now))
        // counting down to the next prayer, ASR
        assertEquals(PrayerType.ASR, widgetTimes.nextToday(now))
        assertEquals(180L, minutesUntil(now, times[PrayerType.ASR]!!))
    }

    @Test
    fun `friday dhuhr chip is named Jumuah`() {
        // 2026-11-20 is a Friday.
        val friday = LocalDate.of(2026, 11, 20)
        assertTrue(friday.dayOfWeek == DayOfWeek.FRIDAY)
        val widgetTimes = WidgetTimes(zone, friday, times)
        val state = SalahBoardState(
            times = widgetTimes,
            now = LocalTime.of(13, 0),
            next = widgetTimes.nextToday(LocalTime.of(13, 0)),
            current = currentPrayer(widgetTimes, LocalTime.of(13, 0)),
            completed = emptySet()
        )
        val dhuhrChip = state.chips.first { it.key == PrayerType.DHUHR.name }
        assertEquals("Jumuah", dhuhrChip.label)
        assertTrue(dhuhrChip.isCurrent)
    }
}
