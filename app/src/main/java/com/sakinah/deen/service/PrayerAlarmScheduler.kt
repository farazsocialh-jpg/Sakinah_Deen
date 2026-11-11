package com.sakinah.deen.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PrayerAlarmScheduler(
    private val context: Context,
    private val astronomyEngine: PrayerCalculationEngine
) {

    companion object {
        const val ACTION_PRAYER_ALARM = "com.sakinah.deen.ACTION_PRAYER_ALARM"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TYPE = "extra_prayer_type"
        const val EXTRA_PRAYER_TIME_FORMATTED = "extra_prayer_time_formatted"
        const val EXTRA_TRIGGER_MILLIS = "extra_trigger_millis"
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun scheduleAllDailyAlarms(
        coordinates: Pair<Double, Double>,
        method: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
        madhhab: Madhhab = Madhhab.HANAFI
    ) {
        if (alarmManager == null) return

        val today = LocalDate.now()
        val timesToday = astronomyEngine.getPrayerTimes(coordinates, today, method, madhhab)
        val now = LocalTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        val prayersToSchedule = listOf(
            PrayerType.FAJR,
            PrayerType.DHUHR,
            PrayerType.ASR,
            PrayerType.MAGHRIB,
            PrayerType.ISHA
        )

        for (prayer in prayersToSchedule) {
            val prayerTime = timesToday[prayer] ?: continue
            val isFutureToday = prayerTime.isAfter(now)

            val triggerDate = if (isFutureToday) today else today.plusDays(1)
            val effectiveTime = if (isFutureToday) {
                prayerTime
            } else {
                val timesTomorrow = astronomyEngine.getPrayerTimes(coordinates, triggerDate, method, madhhab)
                timesTomorrow[prayer] ?: prayerTime
            }

            val triggerDateTime = LocalDateTime.of(triggerDate, effectiveTime)
            val triggerMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            scheduleSingleAlarm(
                prayerType = prayer,
                prayerName = prayer.name.lowercase().replaceFirstChar { it.uppercase() },
                timeFormatted = effectiveTime.format(timeFormatter),
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

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_PRAYER_TYPE, prayerType.name)
            putExtra(EXTRA_PRAYER_TIME_FORMATTED, timeFormatted)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerEpochMillis)
        }

        val requestCode = 1000 + prayerType.ordinal
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                // Runtime permission was revoked; fallback gracefully to inexact trigger
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            }
        } else {
            // Android 12+ runtime fallback when SCHEDULE_EXACT_ALARM is not granted
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerEpochMillis,
                pendingIntent
            )
        }
    }

    fun cancelAllAlarms() {
        if (alarmManager == null) return

        for (prayer in PrayerType.entries) {
            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = ACTION_PRAYER_ALARM
            }
            val requestCode = 1000 + prayer.ordinal
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
