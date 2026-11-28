package com.sakinah.deen

import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.service.AlarmIdentity
import com.sakinah.deen.service.LatePrayerReminderPlanner
import com.sakinah.deen.service.UrgentReminderAlarmIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure JVM checks for the end-of-window trigger and its midnight/offset edge cases. */
class LatePrayerReminderPlannerTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val date = LocalDate.of(2026, 11, 20)
    private val epochDay = date.toEpochDay()

    private val times = mapOf(
        PrayerType.FAJR to LocalTime.of(5, 0),
        PrayerType.SUNRISE to LocalTime.of(6, 20),
        PrayerType.DHUHR to LocalTime.of(11, 40),
        PrayerType.ASR to LocalTime.of(15, 10),
        PrayerType.MAGHRIB to LocalTime.of(17, 25),
        PrayerType.ISHA to LocalTime.of(19, 0)
    )
    private val nextDayTimes = times + (PrayerType.FAJR to LocalTime.of(5, 1))
    private val obligatory = listOf(
        PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB, PrayerType.ISHA
    )

    private fun millis(time: LocalTime, day: LocalDate = date): Long =
        ZonedDateTime.of(day, time, zone).toInstant().toEpochMilli()

    private fun nowAt(time: LocalTime, day: LocalDate = date): Long = millis(time, day)

    @Test
    fun triggerSitsAtNinetyPercentOfTheWindow() {
        val plan = LatePrayerReminderPlanner.plan(
            prayer = PrayerType.FAJR,
            epochDay = epochDay,
            todayTimes = times,
            nextDayTimes = nextDayTimes,
            zone = zone,
            offsetMinutes = 0L,
            nowMillis = nowAt(LocalTime.of(5, 0))
        )
        assertNotNull(plan)
        // Fajr 05:00 → Sunrise 06:20 (80 min); 90% = 72 min → 06:12.
        assertEquals(millis(LocalTime.of(5, 0)), plan!!.windowStartMillis)
        assertEquals(millis(LocalTime.of(6, 20)), plan.windowEndMillis)
        assertEquals(millis(LocalTime.of(6, 12)), plan.triggerMillis)
        assertEquals(epochDay, plan.epochDay)
    }

    @Test
    fun eachPrayerUsesTheSameWindowEndAsTheCountdown() {
        val now = nowAt(LocalTime.of(4, 0))
        fun triggerFor(prayer: PrayerType) = LatePrayerReminderPlanner.plan(
            prayer = prayer,
            epochDay = epochDay,
            todayTimes = times,
            nextDayTimes = nextDayTimes,
            zone = zone,
            offsetMinutes = 0L,
            nowMillis = now
        )!!

        assertEquals(millis(LocalTime.of(6, 20)), triggerFor(PrayerType.FAJR).windowEndMillis)
        assertEquals(millis(LocalTime.of(15, 10)), triggerFor(PrayerType.DHUHR).windowEndMillis)
        assertEquals(millis(LocalTime.of(17, 25)), triggerFor(PrayerType.ASR).windowEndMillis)
        assertEquals(millis(LocalTime.of(19, 0)), triggerFor(PrayerType.MAGHRIB).windowEndMillis)
    }

    @Test
    fun ishaWindowWrapsToNextDaysFajr() {
        val plan = LatePrayerReminderPlanner.plan(
            prayer = PrayerType.ISHA,
            epochDay = epochDay,
            todayTimes = times,
            nextDayTimes = nextDayTimes,
            zone = zone,
            offsetMinutes = 0L,
            nowMillis = nowAt(LocalTime.of(19, 0))
        )
        assertNotNull(plan)
        // 19:00 → 05:01 next day (601 min); 90% = 540.9 min → ~04:00:54 next day.
        assertEquals(millis(LocalTime.of(5, 1), date.plusDays(1)), plan!!.windowEndMillis)
        assertEquals(epochDay, plan.epochDay)
        assertEquals(date.plusDays(1), ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(plan.triggerMillis), zone
        ).toLocalDate())
        assertEquals(
            millis(LocalTime.of(19, 0)) +
                ((millis(LocalTime.of(5, 1), date.plusDays(1)) - millis(LocalTime.of(19, 0))) * 0.9).toLong(),
            plan.triggerMillis
        )
    }

    @Test
    fun offsetShiftsStartButNotWindowEnd() {
        val plan = LatePrayerReminderPlanner.plan(
            prayer = PrayerType.DHUHR,
            epochDay = epochDay,
            todayTimes = times,
            nextDayTimes = nextDayTimes,
            zone = zone,
            offsetMinutes = 30L,
            nowMillis = nowAt(LocalTime.of(11, 0))
        )
        assertNotNull(plan)
        assertEquals(millis(LocalTime.of(12, 10)), plan!!.windowStartMillis)
        assertEquals(millis(LocalTime.of(15, 10)), plan.windowEndMillis)
        // 180 min window → trigger 162 min after the shifted start (14:52).
        assertEquals(millis(LocalTime.of(14, 52)), plan.triggerMillis)
    }

    @Test
    fun elapsedOrStillUnresolvedWindowsAreSkipped() {
        val fajrTrigger = millis(LocalTime.of(6, 12))
        assertNull(
            LatePrayerReminderPlanner.plan(
                prayer = PrayerType.FAJR,
                epochDay = epochDay,
                todayTimes = times,
                nextDayTimes = nextDayTimes,
                zone = zone,
                offsetMinutes = 0L,
                nowMillis = fajrTrigger
            )
        )
        assertNull(
            LatePrayerReminderPlanner.plan(
                prayer = PrayerType.FAJR,
                epochDay = epochDay,
                todayTimes = times,
                nextDayTimes = nextDayTimes,
                zone = zone,
                offsetMinutes = 0L,
                nowMillis = fajrTrigger + 1L
            )
        )
        assertNull(
            LatePrayerReminderPlanner.plan(
                prayer = PrayerType.FAJR,
                epochDay = epochDay,
                todayTimes = times - PrayerType.SUNRISE,
                nextDayTimes = nextDayTimes,
                zone = zone,
                offsetMinutes = 0L,
                nowMillis = nowAt(LocalTime.of(4, 0))
            )
        )
        assertNull(
            LatePrayerReminderPlanner.plan(
                prayer = PrayerType.SUNRISE,
                epochDay = epochDay,
                todayTimes = times,
                nextDayTimes = nextDayTimes,
                zone = zone,
                offsetMinutes = 0L,
                nowMillis = nowAt(LocalTime.of(4, 0))
            )
        )
    }

    @Test
    fun urgentRequestCodeIsOffsetFromItsDailyPrayerSlot() {
        obligatory.forEach { prayer ->
            val daily = AlarmIdentity.requestCode(AlarmIdentity.prayerKey(prayer.name))
            val urgent = UrgentReminderAlarmIdentity.requestCode(prayer, epochDay)
            assertTrue("non-negative code for $prayer", urgent >= 0)
            // base slot + the constant offset + the bounded date salt (no overflow today).
            assertEquals(
                daily + UrgentReminderAlarmIdentity.REQUEST_CODE_OFFSET + epochDay.toInt(),
                urgent
            )
            assertNotEquals("must not overwrite the daily alarm for $prayer", daily, urgent)
        }
    }

    @Test
    fun urgentRequestCodesAreDeterministicAndUniquePerDayAndPrayer() {
        val codes = listOf(epochDay, epochDay + 1, epochDay + 7).flatMap { day ->
            obligatory.map { UrgentReminderAlarmIdentity.requestCode(it, day) }
        }
        assertEquals(codes.size, codes.toSet().size)
        obligatory.forEach { prayer ->
            assertEquals(
                UrgentReminderAlarmIdentity.requestCode(prayer, epochDay),
                UrgentReminderAlarmIdentity.requestCode(prayer, epochDay)
            )
            assertNotEquals(
                UrgentReminderAlarmIdentity.requestCode(prayer, epochDay),
                UrgentReminderAlarmIdentity.requestCode(prayer, epochDay + 1)
            )
        }
    }

    @Test
    fun urgentKeyIsDateStampedAndRoundTrips() {
        val key = UrgentReminderAlarmIdentity.key(PrayerType.ISHA, epochDay)
        assertEquals("late/ISHA/$epochDay", key)
        assertEquals(PrayerType.ISHA to epochDay, UrgentReminderAlarmIdentity.parseKey(key))
        assertNotEquals(key, UrgentReminderAlarmIdentity.key(PrayerType.ISHA, epochDay + 1))
        assertNull(UrgentReminderAlarmIdentity.parseKey("prayer/ISHA"))
    }

    @Test
    fun urgentFreshnessRequiresTheFinalTenthAndTheRightDay() {
        val plan = LatePrayerReminderPlanner.plan(
            prayer = PrayerType.DHUHR,
            epochDay = epochDay,
            todayTimes = times,
            nextDayTimes = nextDayTimes,
            zone = zone,
            offsetMinutes = 0L,
            nowMillis = nowAt(LocalTime.of(4, 0))
        )!!
        fun fresh(now: Long, today: Long, grace: Boolean = false) =
            UrgentReminderAlarmIdentity.isFresh(
                nowMillis = now,
                todayEpochDay = today,
                epochDay = plan.epochDay,
                windowStartMillis = plan.windowStartMillis,
                windowEndMillis = plan.windowEndMillis,
                triggerMillis = plan.triggerMillis,
                zone = zone,
                allowNextDayGrace = grace
            )

        assertTrue(fresh(plan.triggerMillis, epochDay))
        assertTrue(fresh(plan.windowEndMillis - 1, epochDay))
        assertFalse("before the 90% mark", fresh(plan.triggerMillis - 1, epochDay))
        assertFalse("at the window end", fresh(plan.windowEndMillis, epochDay))
        assertFalse("stale day", fresh(plan.triggerMillis, epochDay + 1))
        assertTrue("Isha after midnight gets the one-day grace", fresh(plan.triggerMillis, epochDay + 1, grace = true))
    }
}
