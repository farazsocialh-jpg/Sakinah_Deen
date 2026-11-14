package com.sakinah.deen.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sakinah.deen.MainActivity
import com.sakinah.deen.data.model.AzaanSettings
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.Muezzin
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prayer.PrayerTimeSnapshot
import com.sakinah.deen.data.prayer.SunniMethodMapper
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import java.time.LocalDate as JLocalDate
import java.time.LocalDateTime
import java.time.LocalTime as JLocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.toJavaLocalTime

class PrayerAlarmScheduler(
    private val context: Context,
    private val astronomyEngine: PrayerCalculationEngine,
    private val preferences: SakinahPreferences = SakinahPreferences(context),
    /** Optional resolver (Room cache → AlAdhan → local). Without it the local table is used. */
    private val prayerTimes: PrayerTimeRepository? = null
) {

    companion object {
        const val ACTION_PRAYER_ALARM = "com.sakinah.deen.ACTION_PRAYER_ALARM"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TYPE = "extra_prayer_type"
        const val EXTRA_PRAYER_TIME_FORMATTED = "extra_prayer_time_formatted"
        const val EXTRA_TRIGGER_MILLIS = "extra_trigger_millis"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val EXTRA_ALERT_MODE = "extra_alert_mode"
        const val EXTRA_MUEZZIN_ID = "extra_muezzin_id"

        private const val BASE_REQUEST_CODE = 1000
        private const val SNOOZE_REQUEST_CODE = 9500
        private const val TEST_REQUEST_CODE = 9600
        private const val ALARM_CLOCK_SHOW_REQUEST_CODE = 9700
        private const val SNOOZE_MINUTES = 10

        private val PRAYERS_TO_SCHEDULE = AzaanSettings.ALERT_PRAYERS
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Registers one exact alarm for each of the five obligatory prayers, rolling
     * today's elapsed prayers onto tomorrow's table.
     *
     * Resolution order: the timings already resolved for this place/method (cache or online,
     * see [PrayerTimeSnapshot]), then the local astronomical calculation. Callers that can
     * suspend should prefer [scheduleAllDailyAlarmsRefreshed], which can go to the network.
     */
    fun scheduleAllDailyAlarms(
        coordinates: Pair<Double, Double>? = null,
        method: CalculationMethod? = null,
        madhhab: Madhhab? = null
    ) {
        if (alarmManager == null) return
        val settings = preferences.azaanSettings
        if (!settings.enabled) {
            cancelAllAlarms()
            return
        }
        val coords = coordinates ?: preferences.locationCoordinates ?: return
        val school = madhhab ?: preferences.madhhab
        val authority = authorityFor(method)

        val today = JLocalDate.now()
        val todayTimes = resolvedTimes(today, coords, authority, school) ?: engineTimes(today, coords, school)
        val tomorrow = today.plusDays(1)
        val tomorrowTimes = resolvedTimes(tomorrow, coords, authority, school) ?: engineTimes(tomorrow, coords, school)

        scheduleTable(todayTimes, tomorrowTimes)
    }

    /**
     * Resolves today's and tomorrow's timings through the offline-first repository (Room cache,
     * then AlAdhan, then a local calculation) and re-registers the alarm table from them. Safe
     * to call at startup: it never throws and a failure leaves the existing alarms untouched.
     */
    suspend fun scheduleAllDailyAlarmsRefreshed(coordinates: Pair<Double, Double>? = null) {
        if (alarmManager == null) return
        if (!preferences.azaanSettings.enabled) {
            cancelAllAlarms()
            return
        }
        val repository = prayerTimes ?: return scheduleAllDailyAlarms(coordinates)
        val coords = coordinates ?: preferences.locationCoordinates ?: return
        val school = preferences.madhhab
        val zone = ZoneId.systemDefault()

        val today = JLocalDate.now()
        val tomorrow = today.plusDays(1)

        val todayTimes = runCatching {
            repository.resolveDay(today, coords, school, zone).times
        }.getOrDefault(emptyMap()).ifEmpty { engineTimes(today, coords, school) }

        val tomorrowTimes = runCatching {
            repository.resolveDay(tomorrow, coords, school, zone).times
        }.getOrDefault(emptyMap()).ifEmpty { engineTimes(tomorrow, coords, school) }

        scheduleTable(todayTimes, tomorrowTimes)
    }

    /** Registers the five prayers, rolling elapsed ones onto tomorrow's table. */
    private fun scheduleTable(
        todayTimes: Map<PrayerType, JLocalTime>,
        tomorrowTimes: Map<PrayerType, JLocalTime>
    ) {
        // Re-register the complete table so turning a prayer off removes its old alarm.
        cancelAllAlarms()

        val settings = preferences.azaanSettings
        if (!settings.enabled) return

        val nowJ = JLocalTime.now()
        val todayJ = JLocalDate.now()
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        for (prayer in PRAYERS_TO_SCHEDULE) {
            val mode = settings.modeFor(prayer)
            if (mode == PrayerAlertMode.OFF) continue

            val todayTime = todayTimes[prayer] ?: continue
            val offset = settings.offsetFor(prayer).toLong()
            val todayAlertTime = todayTime.plusMinutes(offset)
            val isFutureToday = todayAlertTime.isAfter(nowJ)
            val triggerDate = if (isFutureToday) todayJ else todayJ.plusDays(1)
            val baseTime = if (isFutureToday) todayTime else (tomorrowTimes[prayer] ?: todayTime)
            val alertTime = baseTime.plusMinutes(offset)

            val triggerMillis = LocalDateTime.of(triggerDate, alertTime)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            scheduleSingleAlarm(
                prayerType = prayer,
                prayerName = prayer.name.lowercase().replaceFirstChar { it.uppercase() },
                timeFormatted = alertTime.format(timeFormatter),
                triggerEpochMillis = triggerMillis,
                alertMode = mode,
                muezzin = settings.muezzin
            )
        }
    }

    /** Authority the repository would use for these settings (manual override or country). */
    private fun authorityFor(method: CalculationMethod?): com.sakinah.deen.data.prayer.SunniAuthority =
        SunniMethodMapper.resolve(
            countryCode = preferences.locationCountryCode,
            override = method ?: preferences.calculationMethodOverride
        )

    /** Timings already resolved from Room/network for this context, if still memoised. */
    private fun resolvedTimes(
        date: JLocalDate,
        coordinates: Pair<Double, Double>,
        authority: com.sakinah.deen.data.prayer.SunniAuthority,
        school: Madhhab
    ): Map<PrayerType, JLocalTime>? = PrayerTimeSnapshot.timesFor(
        PrayerTimeSnapshot.keyOf(
            date = date,
            coordinates = coordinates,
            methodId = authority.methodId,
            schoolId = SunniMethodMapper.schoolId(school)
        )
    )

    /** Local astronomical table for one day, using the authority's twilight angles. */
    private fun engineTimes(
        date: JLocalDate,
        coordinates: Pair<Double, Double>,
        school: Madhhab
    ): Map<PrayerType, JLocalTime> {
        val authority = authorityFor(null)
        val method = preferences.calculationMethodOverride
            ?: SunniMethodMapper.localCalculationMethod(authority)
        return astronomyEngine.getPrayerTimes(
            coordinates = coordinates,
            date = KLocalDate.parse(date.toString()),
            method = method,
            madhhab = school
        ).mapValues { (_, time) -> time.toJavaLocalTime() }
    }

    fun scheduleSingleAlarm(
        prayerType: PrayerType,
        prayerName: String,
        timeFormatted: String,
        triggerEpochMillis: Long,
        alertMode: PrayerAlertMode = preferences.azaanSettings.modeFor(prayerType),
        muezzin: Muezzin = preferences.azaanSettings.muezzin
    ) {
        if (alarmManager == null) return

        val pendingIntent = buildPrayerPendingIntent(
            requestCode = BASE_REQUEST_CODE + prayerType.ordinal,
            prayerName = prayerName,
            prayerType = prayerType,
            timeFormatted = timeFormatted,
            triggerEpochMillis = triggerEpochMillis,
            isSnooze = false,
            alertMode = alertMode,
            muezzin = muezzin
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
            isSnooze = true,
            alertMode = preferences.azaanSettings.modeFor(prayerType),
            muezzin = preferences.azaanSettings.muezzin
        )
        setExactCompat(triggerAt, pendingIntent)
    }

    /**
     * Fires one real prayer alarm a few seconds from now so the user can verify the
     * notification, sound and alert mode without waiting for the next prayer.
     */
    fun scheduleTestAlarmInSeconds(
        seconds: Long = 5,
        prayerType: PrayerType = PrayerType.DHUHR
    ) {
        if (alarmManager == null) return
        val triggerAt = System.currentTimeMillis() + seconds * 1_000L
        val pendingIntent = buildPrayerPendingIntent(
            requestCode = TEST_REQUEST_CODE,
            prayerName = "Test ${prayerType.name.lowercase().replaceFirstChar { it.uppercase() }}",
            prayerType = prayerType,
            timeFormatted = "now",
            triggerEpochMillis = triggerAt,
            isSnooze = false,
            alertMode = preferences.azaanSettings.modeFor(prayerType),
            muezzin = preferences.azaanSettings.muezzin
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
        isSnooze: Boolean,
        alertMode: PrayerAlertMode,
        muezzin: Muezzin
    ): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_PRAYER_TYPE, prayerType.name)
            putExtra(EXTRA_PRAYER_TIME_FORMATTED, timeFormatted)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerEpochMillis)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EXTRA_ALERT_MODE, alertMode.name)
            putExtra(EXTRA_MUEZZIN_ID, muezzin.id)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * An alarm clock is the strongest user-visible alarm: it wakes the device out of Doze,
     * shows the time, and lets the alarm receiver start its media-playback foreground service.
     * If exact alarms were disabled, keep the reminder with an idle-safe fallback.
     */
    private fun setExactCompat(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val am = alarmManager ?: return
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val showIntent = PendingIntent.getActivity(
            context,
            ALARM_CLOCK_SHOW_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (canScheduleExact) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pendingIntent)
                return
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}
