package com.sakinah.deen.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import java.time.LocalDate as JLocalDate
import java.time.LocalDateTime
import java.time.LocalTime as JLocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.todayIn
import kotlinx.datetime.TimeZone as KTimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime

class PrayerAlarmScheduler(
    private val context: Context,
    private val astronomyEngine: PrayerCalculationEngine,
    private val preferences: SakinahPreferences = SakinahPreferences(context)
) {

    companion object {
        const val ACTION_PRAYER_ALARM = "com.sakinah.deen.ACTION_PRAYER_ALARM"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TYPE = "extra_prayer_type"
        const val EXTRA_PRAYER_TIME_FORMATTED = "extra_prayer_time_formatted"
        const val EXTRA_TRIGGER_MILLIS = "extra_trigger_millis"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"

        private const val BASE_REQUEST_CODE = 1000
        private const val SNOOZE_REQUEST_CODE = 9500
        private const val SNOOZE_MINUTES = 10
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Registers one exact alarm for each of the five obligatory prayers, rolling
     * today's elapsed prayers onto tomorrow's astronomical table.
     */
    fun scheduleAllDailyAlarms(
        coordinates: Pair<Double, Double>? = null,
        method: CalculationMethod? = null,
        madhhab: Madhhab? = null
    ) {
        if (alarmManager == null) return
        val coords = coordinates ?: preferences.locationCoordinates ?: return
        val calculationMethod = method ?: preferences.calculationMethod
        val school = madhhab ?: preferences.madhhab

        val todayK = Clock.System.todayIn(KTimeZone.currentSystemDefault())
        val nowJ = JLocalTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        val timesToday = astronomyEngine.getPrayerTimes(coords, todayK, calculationMethod, school)
        val timesTomorrow = astronomyEngine.getPrayerTimes(
            coords,
            KLocalDate.parse(JLocalDate.now().plusDays(1).toString()),
            calculationMethod,
            school
        )

        val prayersToSchedule = listOf(
            PrayerType.FAJR,
            PrayerType.DHUHR,
            PrayerType.ASR,
            PrayerType.MAGHRIB,
            PrayerType.ISHA
        )

        for (prayer in prayersToSchedule) {
            val todayTime = timesToday[prayer] ?: continue
            val isFutureToday = todayTime.toJavaLocalTime().isAfter(nowJ)
            val triggerDateJava = if (isFutureToday) JLocalDate.now() else JLocalDate.now().plusDays(1)
            val effectiveTimeK = if (isFutureToday) todayTime else (timesTomorrow[prayer] ?: todayTime)

            val triggerMillis = LocalDateTime.of(triggerDateJava, effectiveTimeK.toJavaLocalTime())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            scheduleSingleAlarm(
                prayerType = prayer,
                prayerName = prayer.name.lowercase().replaceFirstChar { it.uppercase() },
                timeFormatted = effectiveTimeK.toJavaLocalTime().format(timeFormatter),
                triggerEpochMillis = triggerMillis
            )
        }
    }

    fun scheduleSingleAlarm(
        prayerType: PrayerType,
        prayerName: String,
        timeFormatted: String,
        triggerEpochMillis: Long
    ) {
        if (alarmManager == null) return

        val pendingIntent = buildPrayerPendingIntent(
            requestCode = BASE_REQUEST_CODE + prayerType.ordinal,
            prayerName = prayerName,
            prayerType = prayerType,
            timeFormatted = timeFormatted,
            triggerEpochMillis = triggerEpochMillis,
            isSnooze = false
        )
        setExactCompat(triggerEpochMillis, pendingIntent)
    }

    /** Re-fires a prayer reminder once, [minutes] from now. */
    fun scheduleSnooze(
        prayerType: PrayerType = PrayerType.DHUHR,
        prayerName: String = "Salah",
        minutes: Int = SNOOZE_MINUTES,
        timeFormatted: String = ""
    ) {
        if (alarmManager == null) return
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val pendingIntent = buildPrayerPendingIntent(
            requestCode = SNOOZE_REQUEST_CODE,
            prayerName = prayerName,
            prayerType = prayerType,
            timeFormatted = timeFormatted,
            triggerEpochMillis = triggerAt,
            isSnooze = true
        )
        setExactCompat(triggerAt, pendingIntent)
    }

    fun cancelAllAlarms() {
        val am = alarmManager ?: return
        for (prayer in PrayerType.entries) {
            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = ACTION_PRAYER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST_CODE + prayer.ordinal,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                am.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun buildPrayerPendingIntent(
        requestCode: Int,
        prayerName: String,
        prayerType: PrayerType,
        timeFormatted: String,
        triggerEpochMillis: Long,
        isSnooze: Boolean
    ): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_PRAYER_TYPE, prayerType.name)
            putExtra(EXTRA_PRAYER_TIME_FORMATTED, timeFormatted)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerEpochMillis)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Exact alarms are a runtime grant on Android 12+. When the user revokes it,
     * fall back to an inexact idle-safe alarm instead of losing the reminder.
     */
    private fun setExactCompat(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val am = alarmManager ?: return
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

        if (canScheduleExact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}
