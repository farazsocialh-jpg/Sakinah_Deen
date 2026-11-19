package com.sakinah.deen

import com.sakinah.deen.service.PrayerAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Overnight Fajr scheduling: a Fajr time that has already passed today must roll onto
 * tomorrow's table instead of being skipped or double-armed.
 */
class OvernightFajrSchedulingTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun fajrAlreadyPassedToday_rollsToTomorrow() {
        val now = ZonedDateTime.of(today, LocalTime.of(23, 0), zone)
        val todayFajr = LocalTime.of(5, 0)
        val tomorrowFajr = LocalTime.of(5, 1)

        val planned = PrayerAlarmScheduler.planTrigger(
            todayTime = todayFajr,
            tomorrowTime = tomorrowFajr,
            todayDate = today,
            now = now,
            zone = zone,
            offsetMinutes = 0
        )

        assertTrue("Fajr that already passed must roll to tomorrow", planned.rollsToTomorrow)
        assertEquals(today.plusDays(1), planned.trigger.toLocalDate())
        assertEquals(tomorrowFajr, planned.trigger.toLocalTime())
    }

    @Test
    fun fajrNotYetToday_staysOnToday() {
        val now = ZonedDateTime.of(today, LocalTime.of(4, 0), zone)
        val todayFajr = LocalTime.of(5, 0)

        val planned = PrayerAlarmScheduler.planTrigger(
            todayTime = todayFajr,
            tomorrowTime = LocalTime.of(5, 1),
            todayDate = today,
            now = now,
            zone = zone,
            offsetMinutes = 0
        )

        assertFalse("Fajr later today must not roll", planned.rollsToTomorrow)
        assertEquals(today, planned.trigger.toLocalDate())
        assertEquals(todayFajr, planned.trigger.toLocalTime())
    }

    @Test
    fun fajrExactlyNow_rollsToTomorrow() {
        val fajrTime = LocalTime.of(5, 0)
        val now = ZonedDateTime.of(today, fajrTime, zone)

        val planned = PrayerAlarmScheduler.planTrigger(
            todayTime = fajrTime,
            tomorrowTime = LocalTime.of(5, 1),
            todayDate = today,
            now = now,
            zone = zone,
            offsetMinutes = 0
        )

        assertTrue("A Fajr exactly at now has already passed and must roll", planned.rollsToTomorrow)
    }

    @Test
    fun offsetPushesFajrPastMidnight_rollsCorrectly() {
        // A positive offset can move today's alert past midnight; the roll decision must use
        // the offset-adjusted instant, not the raw prayer time.
        val now = ZonedDateTime.of(today.plusDays(1), LocalTime.of(0, 30), zone)
        val todayFajr = LocalTime.of(23, 0)

        val planned = PrayerAlarmScheduler.planTrigger(
            todayTime = todayFajr,
            tomorrowTime = LocalTime.of(23, 0),
            todayDate = today,
            now = now,
            zone = zone,
            offsetMinutes = 60
        )

        assertTrue("Offset-adjusted Fajr (23:00+60m=00:00) has passed by 00:30 next day", planned.rollsToTomorrow)
    }
}
