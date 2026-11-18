package com.sakinah.deen

import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.service.PrayerAlarmScheduler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** A refresh must never cancel working alarms for an incomplete timetable. */
class PrayerAlarmScheduleValidationTest {

    private fun complete() = mapOf(
        PrayerType.FAJR to LocalTime.of(5, 0),
        PrayerType.SUNRISE to LocalTime.of(6, 20),
        PrayerType.DHUHR to LocalTime.of(11, 30),
        PrayerType.ASR to LocalTime.of(14, 45),
        PrayerType.MAGHRIB to LocalTime.of(17, 10),
        PrayerType.ISHA to LocalTime.of(18, 40)
    )

    @Test
    fun completeFivePrayerTable_isAccepted() {
        assertTrue(PrayerAlarmScheduler.isCompleteSchedule(complete()))
        // Sunrise is informational; its absence must not block scheduling.
        assertTrue(
            PrayerAlarmScheduler.isCompleteSchedule(complete() - PrayerType.SUNRISE)
        )
    }

    @Test
    fun emptyOrPartialTable_isRejected() {
        assertFalse(PrayerAlarmScheduler.isCompleteSchedule(null))
        assertFalse(PrayerAlarmScheduler.isCompleteSchedule(emptyMap()))
        assertFalse(
            PrayerAlarmScheduler.isCompleteSchedule(
                complete() - PrayerType.ISHA
            )
        )
    }
}
