package com.sakinah.deen.service

import com.sakinah.deen.data.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure planner for the end-of-window "have you prayed?" nudge.
 *
 * The window boundaries mirror the countdown shown on the prayer card/widget: each obligatory
 * prayer runs until the next prayer begins (Fajr→Sunrise, Dhuhr→Asr, Asr→Maghrib,
 * Maghrib→Isha, Isha→the following Fajr). The alert sits at [DEFAULT_FRACTION] of that window
 * and is dropped when the instant is already in the past, so a late refresh never posts a
 * reminder for an elapsed window. Uses only java.time, so it is unit-testable on the JVM.
 */
object LatePrayerReminderPlanner {

    const val DEFAULT_FRACTION = 0.9

    data class Plan(
        val prayer: PrayerType,
        /** Epoch day of the prayer (the day the window starts, even when it ends after midnight). */
        val epochDay: Long,
        val windowStartMillis: Long,
        val windowEndMillis: Long,
        val triggerMillis: Long
    )

    /**
     * Returns the reminder plan for [prayer] on [epochDay], or null when there is no window, a
     * boundary is missing/out of order, or the trigger has already elapsed relative to
     * [nowMillis].
     *
     * [offsetMinutes] is the user's alert offset: it shifts the window start exactly as the
     * normal alarm does, while the window end stays at the raw next-prayer time (the same rule
     * [PrayerAlarmScheduler.prayerWindowEndMillis] uses for the visible countdown).
     */
    fun plan(
        prayer: PrayerType,
        epochDay: Long,
        todayTimes: Map<PrayerType, LocalTime>,
        nextDayTimes: Map<PrayerType, LocalTime>?,
        zone: ZoneId,
        offsetMinutes: Long,
        nowMillis: Long,
        fraction: Double = DEFAULT_FRACTION
    ): Plan? {
        if (prayer == PrayerType.SUNRISE) return null
        val date = LocalDate.ofEpochDay(epochDay)

        fun instantOf(time: LocalTime, day: LocalDate): Long =
            ZonedDateTime.of(day, time, zone).toInstant().toEpochMilli()

        val start = ZonedDateTime.of(date, todayTimes[prayer] ?: return null, zone)
            .plusMinutes(offsetMinutes)
            .toInstant().toEpochMilli()

        val end = when (prayer) {
            PrayerType.FAJR -> todayTimes[PrayerType.SUNRISE]?.let { instantOf(it, date) }
            PrayerType.DHUHR -> todayTimes[PrayerType.ASR]?.let { instantOf(it, date) }
            PrayerType.ASR -> todayTimes[PrayerType.MAGHRIB]?.let { instantOf(it, date) }
            PrayerType.MAGHRIB -> todayTimes[PrayerType.ISHA]?.let { instantOf(it, date) }
            // Isha closes at the next Fajr, which belongs to the following calendar day.
            PrayerType.ISHA -> {
                val fajr = nextDayTimes?.get(PrayerType.FAJR) ?: todayTimes[PrayerType.FAJR]
                fajr?.let { instantOf(it, date.plusDays(1)) }
            }
            PrayerType.SUNRISE -> return null
        } ?: return null

        val span = end - start
        if (span <= 0L) return null
        val trigger = start + (span.toDouble() * fraction.coerceIn(0.0, 1.0)).toLong()
        if (trigger <= nowMillis) return null

        return Plan(prayer, epochDay, start, end, trigger)
    }
}
