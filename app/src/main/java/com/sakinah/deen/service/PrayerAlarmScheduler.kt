package com.sakinah.deen.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import java.time.Duration
import java.time.LocalDate as JLocalDate
import java.time.LocalTime as JLocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.TimeZone as KTimeZone
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

        /** Start of the prayer window shown on the notification (prayer entry time). */
        const val EXTRA_WINDOW_START_MILLIS = "extra_window_start_millis"

        /** End of the prayer window: the countdown target and progress-bar anchor. */
        const val EXTRA_WINDOW_END_MILLIS = "extra_window_end_millis"

        /** Set by [scheduleTestAlarmInSeconds] so the completion guard never suppresses a test. */
        const val EXTRA_IS_TEST = "extra_is_test"

        private const val BASE_REQUEST_CODE = 1000
        private const val SNOOZE_REQUEST_CODE = 9500
        private const val TEST_REQUEST_CODE = 9600
        private const val SNOOZE_MINUTES = 10

        /** The test card uses a short window so the countdown and progress bar are visible. */
        private const val TEST_WINDOW_MS = 3 * 60_000L
        private const val KIND_SNOOZE = "snooze"
        private const val KIND_TEST = "test"

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

        val today = JLocalDate.now(activeZone())
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
        // The saved location's zone, not the device's: a manual city in another timezone must
        // still trigger at its own local prayer time.
        val zone = activeZone()

        val today = JLocalDate.now(zone)
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
        // Re-register the complete table so a prayer switched to OFF loses its previous
        // PendingIntent instead of firing an alert the user just disabled.
        cancelAllAlarms()

        val settings = preferences.azaanSettings
        if (!settings.enabled) return

        val zone = activeZone()
        val nowZoned = ZonedDateTime.now(zone)
        val todayJ = nowZoned.toLocalDate()
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        for (prayer in PRAYERS_TO_SCHEDULE) {
            val mode = settings.modeFor(prayer)
            // cancelAllAlarms() above already removed this prayer's stale PendingIntent.
            if (mode == PrayerAlertMode.OFF) continue

            val todayTime = todayTimes[prayer] ?: continue
            val offset = settings.offsetFor(prayer).toLong()
            // Compare full zoned instants: plusMinutes may wrap past midnight, which a
            // LocalTime comparison would treat as "earlier today" and skip or double-schedule.
            val todayAlert = ZonedDateTime.of(todayJ, todayTime, zone).plusMinutes(offset)
            val rollsToTomorrow = !todayAlert.isAfter(nowZoned)
            val triggerZoned = if (!rollsToTomorrow) {
                todayAlert
            } else {
                val tomorrowTime = tomorrowTimes[prayer] ?: todayTime
                ZonedDateTime.of(todayJ.plusDays(1), tomorrowTime, zone).plusMinutes(offset)
            }
            // The window a reminder covers always comes from the day it fires on: Fajr ends at
            // sunrise, Dhuhr at Asr, Asr at Maghrib, Maghrib at Isha, Isha at Islamic midnight.
            val triggerDate = triggerZoned.toLocalDate()
            val windowTimes = if (rollsToTomorrow) tomorrowTimes else todayTimes
            val followingDayTimes = if (rollsToTomorrow) null else tomorrowTimes
            val windowEnd = prayerWindowEndMillis(
                prayer = prayer,
                date = triggerDate,
                dayTimes = windowTimes,
                nextDayTimes = followingDayTimes,
                zone = zone
            )

            scheduleSingleAlarm(
                prayerType = prayer,
                prayerName = prayer.name.lowercase().replaceFirstChar { it.uppercase() },
                timeFormatted = triggerZoned.toLocalTime().format(timeFormatter),
                triggerEpochMillis = triggerZoned.toInstant().toEpochMilli(),
                alertMode = mode,
                muezzin = settings.muezzin,
                windowEndMillis = windowEnd
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
            madhhab = school,
            timeZone = KTimeZone.of(activeZone().id)
        ).mapValues { (_, time) -> time.toJavaLocalTime() }
    }

    /** Timezone in which the saved prayer location's timings are expressed. */
    private fun activeZone(): ZoneId = AzaanScheduling.activeZoneId(preferences)

    fun scheduleSingleAlarm(
        prayerType: PrayerType,
        prayerName: String,
        timeFormatted: String,
        triggerEpochMillis: Long,
        alertMode: PrayerAlertMode = preferences.azaanSettings.modeFor(prayerType),
        muezzin: Muezzin = preferences.azaanSettings.muezzin,
        windowEndMillis: Long? = null
    ) {
        if (alarmManager == null) return
        // Never arm an alarm for a time that has already passed.
        if (triggerEpochMillis <= System.currentTimeMillis()) return

        val pendingIntent = buildPrayerPendingIntent(
            requestCode = BASE_REQUEST_CODE + prayerType.ordinal,
            prayerName = prayerName,
            prayerType = prayerType,
            timeFormatted = timeFormatted,
            triggerEpochMillis = triggerEpochMillis,
            isSnooze = false,
            alertMode = alertMode,
            muezzin = muezzin,
            windowEndMillis = windowEndMillis
        )
        setExactCompat(triggerEpochMillis, pendingIntent)
    }

    /**
     * End of the Fard window for [prayer] on [date]: sunrise, Asr, Maghrib, Isha, or the
     * Islamic midnight that closes Isha (the midpoint between Maghrib and the next Fajr).
     */
    internal fun prayerWindowEndMillis(
        prayer: PrayerType,
        date: JLocalDate,
        dayTimes: Map<PrayerType, JLocalTime>,
        nextDayTimes: Map<PrayerType, JLocalTime>?,
        zone: ZoneId
    ): Long? {
        fun instantOf(time: JLocalTime, day: JLocalDate): Long =
            ZonedDateTime.of(day, time, zone).toInstant().toEpochMilli()

        return when (prayer) {
            PrayerType.FAJR -> dayTimes[PrayerType.SUNRISE]?.let { instantOf(it, date) }
            PrayerType.DHUHR -> dayTimes[PrayerType.ASR]?.let { instantOf(it, date) }
            PrayerType.ASR -> dayTimes[PrayerType.MAGHRIB]?.let { instantOf(it, date) }
            PrayerType.MAGHRIB -> dayTimes[PrayerType.ISHA]?.let { instantOf(it, date) }
            PrayerType.ISHA -> islamicMidnightMillis(date, dayTimes, nextDayTimes, zone)
            PrayerType.SUNRISE -> null
        }
    }

    /** Islamic midnight: half of the span between Maghrib and the following Fajr. */
    private fun islamicMidnightMillis(
        date: JLocalDate,
        dayTimes: Map<PrayerType, JLocalTime>,
        nextDayTimes: Map<PrayerType, JLocalTime>?,
        zone: ZoneId
    ): Long? {
        val maghribTime = dayTimes[PrayerType.MAGHRIB] ?: return null
        val fajrTime = nextDayTimes?.get(PrayerType.FAJR) ?: dayTimes[PrayerType.FAJR]
            ?: return null
        val maghrib = ZonedDateTime.of(date, maghribTime, zone)
        // The next Fajr always belongs to the following calendar day; when the next day's
        // table is unavailable the current table is used as a close approximation.
        val nextFajr = ZonedDateTime.of(date.plusDays(1), fajrTime, zone)
        val span = Duration.between(maghrib, nextFajr).toMillis()
        if (span <= 0) return null
        return maghrib.toInstant().toEpochMilli() + span / 2
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
            muezzin = preferences.azaanSettings.muezzin,
            windowEndMillis = triggerAt + SNOOZE_MINUTES * 60_000L
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
            muezzin = preferences.azaanSettings.muezzin,
            isTest = true,
            // A short window makes the live countdown and progress bar visible immediately.
            windowEndMillis = triggerAt + TEST_WINDOW_MS
        )
        setExactCompat(triggerAt, pendingIntent)
    }

    fun cancelAllAlarms() {
        val am = alarmManager ?: return
        for (prayer in PrayerType.entries) {
            val requestCode = BASE_REQUEST_CODE + prayer.ordinal
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                // The data URI is part of PendingIntent identity, so cancellation must
                // rebuild exactly the same intent or the alarm would leak.
                alarmIntent(requestCode, prayer.name),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                am.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        // Snooze and test alarms also need to be cleared.
        cancelByRequestCode(SNOOZE_REQUEST_CODE, KIND_SNOOZE)
        cancelByRequestCode(TEST_REQUEST_CODE, KIND_TEST)
    }

    private fun cancelByRequestCode(requestCode: Int, kind: String) {
        val am = alarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent(requestCode, kind),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            am.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Unique data URI per alarm. Together with the request code this guarantees AlarmManager
     * never merges two prayers' PendingIntents (which mixed extras and fired wrong alerts).
     */
    private fun alarmIntent(requestCode: Int, kind: String): Intent =
        Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            data = Uri.parse("sakinah://prayer_alarm/$requestCode/$kind")
        }

    private fun buildPrayerPendingIntent(
        requestCode: Int,
        prayerName: String,
        prayerType: PrayerType,
        timeFormatted: String,
        triggerEpochMillis: Long,
        isSnooze: Boolean,
        alertMode: PrayerAlertMode,
        muezzin: Muezzin,
        isTest: Boolean = false,
        windowEndMillis: Long? = null
    ): PendingIntent {
        val kind = when {
            isTest -> KIND_TEST
            isSnooze -> KIND_SNOOZE
            else -> prayerType.name
        }
        val intent = alarmIntent(requestCode, kind).apply {
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_PRAYER_TYPE, prayerType.name)
            putExtra(EXTRA_PRAYER_TIME_FORMATTED, timeFormatted)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerEpochMillis)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EXTRA_ALERT_MODE, alertMode.name)
            putExtra(EXTRA_MUEZZIN_ID, muezzin.id)
            putExtra(EXTRA_IS_TEST, isTest)
            putExtra(EXTRA_WINDOW_START_MILLIS, triggerEpochMillis)
            putExtra(
                EXTRA_WINDOW_END_MILLIS,
                windowEndMillis ?: triggerEpochMillis
            )
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Prayer reminders are ordinary notification-type alerts, not alarm-clock alarms: they must
     * not take over the screen or the alarm stream. The exact-and-idle-safe window keeps the
     * timing precise through Doze, and falls back to an inexact idle-safe alarm when the user
     * has not granted exact-alarm scheduling.
     */
    private fun setExactCompat(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val am = alarmManager ?: return
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

        if (canScheduleExact) {
            try {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call: use the idle-safe path.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}
