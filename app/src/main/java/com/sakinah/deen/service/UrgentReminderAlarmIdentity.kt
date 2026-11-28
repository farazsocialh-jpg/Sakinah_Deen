package com.sakinah.deen.service

import com.sakinah.deen.data.model.PrayerType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure identity, request-code and freshness rules for the end-of-window "urgent" reminders.
 *
 * The daily prayer alarms have one stable slot per prayer; the nudge needs one slot per
 * (prayer, date) so re-arming the table refreshes an existing alarm instead of stacking copies.
 * Keeping the arithmetic framework-free makes the offset/uniqueness rules JVM-testable.
 */
object UrgentReminderAlarmIdentity {

    /** Marks an intent as a silent end-of-window nudge instead of a normal prayer alarm. */
    const val EXTRA_IS_URGENT_REMINDER = "extra_is_urgent_reminder"

    /** Whole minutes left in the window when the alarm was armed (informational at fire time). */
    const val EXTRA_REMAINING_MINUTES = "extra_remaining_minutes"

    /** Epoch day of the window's start date in the active prayer zone. */
    const val EXTRA_PRAYER_EPOCH_DAY = "extra_prayer_epoch_day"

    /** Keeps every nudge request code clear of the five normal daily prayer slots. */
    const val REQUEST_CODE_OFFSET = 1000

    private const val KEY_PREFIX = "late/"
    private const val DATE_SALT_MODULUS = 1_000_000L

    /** Deterministic identity: the same (prayer, day) always rebuilds the same PendingIntent. */
    fun key(prayer: PrayerType, epochDay: Long): String =
        "$KEY_PREFIX${AlarmIdentity.normalize(prayer.name)}/$epochDay"

    /** The (prayer, day) encoded in [key], or null when the key is malformed/legacy. */
    fun parseKey(key: String): Pair<PrayerType, Long>? {
        if (!key.startsWith(KEY_PREFIX)) return null
        val parts = key.removePrefix(KEY_PREFIX).split('/')
        if (parts.size != 2) return null
        val prayer = runCatching { PrayerType.valueOf(parts[0]) }.getOrNull() ?: return null
        val epochDay = parts[1].toLongOrNull() ?: return null
        return prayer to epochDay
    }

    /**
     * Daily base code + [REQUEST_CODE_OFFSET] + a bounded date salt, masked non-negative.
     * The offset separates nudges from the normal alarms; the salt separates two days of the
     * same prayer. The data URI remains part of the identity, so even a hash collision cannot
     * merge two different slots.
     */
    fun requestCode(prayer: PrayerType, epochDay: Long): Int {
        val base = AlarmIdentity.requestCode(AlarmIdentity.prayerKey(prayer.name))
        val dateSalt = Math.floorMod(epochDay, DATE_SALT_MODULUS).toInt()
        return (base + REQUEST_CODE_OFFSET + dateSalt) and 0x7FFF_FFFF
    }

    /** Whole minutes between the 90% trigger and the window end. */
    fun remainingMinutes(triggerMillis: Long, windowEndMillis: Long): Long =
        ((windowEndMillis - triggerMillis) / 60_000L).coerceAtLeast(0L)

    /**
     * A delivered alarm is honoured only inside the final tenth of its own window, on the day
     * the window started. An Isha window may close after midnight, hence [allowNextDayGrace].
     * [todayEpochDay] is supplied by the caller (computed in the active zone) so this is pure.
     */
    fun isFresh(
        nowMillis: Long,
        todayEpochDay: Long,
        epochDay: Long,
        windowStartMillis: Long,
        windowEndMillis: Long,
        triggerMillis: Long,
        zone: ZoneId,
        allowNextDayGrace: Boolean
    ): Boolean {
        if (epochDay <= 0L || windowStartMillis <= 0L || windowEndMillis <= windowStartMillis ||
            triggerMillis <= windowStartMillis || triggerMillis >= windowEndMillis
        ) {
            return false
        }
        val startDay = Instant.ofEpochMilli(windowStartMillis).atZone(zone).toLocalDate().toEpochDay()
        if (epochDay != startDay) return false
        val grace = if (allowNextDayGrace) 1L else 0L
        if (epochDay != todayEpochDay && epochDay != todayEpochDay - grace) return false
        return nowMillis >= triggerMillis && nowMillis < windowEndMillis
    }
}
