package com.sakinah.deen.service

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
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
import com.sakinah.deen.data.repository.PrayerTimeRepositoryImpl
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

/**
 * Arms the exact alarms that wake the app at prayer time.
 *
 * Every entry point returns an [AlarmScheduleResult]: the UI is never allowed to say
 * "scheduled" unless an alarm was really registered. Identity handling lives in
 * [AlarmIdentity]/[AlarmPendingIntents], so scheduling and cancellation always rebuild the
 * same PendingIntent and no alarm can be orphaned.
 */
class PrayerAlarmScheduler(
    private val context: Context,
    private val astronomyEngine: PrayerCalculationEngine,
    private val preferences: SakinahPreferences = SakinahPreferences(context),
    /** Optional resolver (Room cache → AlAdhan → local). Without it the local table is used. */
    private val prayerTimes: PrayerTimeRepository? = null
) {

    companion object {
        private const val TAG = "PrayerAlarmScheduler"

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

        private const val SNOOZE_MINUTES = 10

        /** The test card uses a short window so the countdown and progress bar are visible. */
        private const val TEST_WINDOW_MS = 3 * 60_000L

        private val PRAYERS_TO_SCHEDULE = AzaanSettings.ALERT_PRAYERS

        /** The five obligatory entries a timetable must contain before alarms are replaced. */
        private val REQUIRED_PRAYERS = listOf(
            PrayerType.FAJR,
            PrayerType.DHUHR,
            PrayerType.ASR,
            PrayerType.MAGHRIB,
            PrayerType.ISHA
        )

        /**
         * True only when every obligatory prayer has a time. A partial table (an offline
         * refresh that only resolved Fajr, for example) must never replace working alarms.
         */
        internal fun isCompleteSchedule(times: Map<PrayerType, JLocalTime>?): Boolean =
            times != null && REQUIRED_PRAYERS.all { times[it] != null }
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
    ): AlarmScheduleResult {
        alarmManager ?: return record(AlarmScheduleResult.Unsupported)
        if (!preferences.azaanSettings.enabled) {
            cancelAllAlarms()
            return record(AlarmScheduleResult.Failed("Azaan notifications are turned off"))
        }
        val coords = coordinates ?: preferences.locationCoordinates
            ?: return record(AlarmScheduleResult.Failed("No prayer location saved"))
        val school = madhhab ?: preferences.madhhab
        val authority = authorityFor(method)

        val today = JLocalDate.now(activeZone())
        val todayTimes = resolvedTimes(today, coords, authority, school) ?: engineTimes(today, coords, school)
        val tomorrow = today.plusDays(1)
        val tomorrowTimes = resolvedTimes(tomorrow, coords, authority, school) ?: engineTimes(tomorrow, coords, school)

        return record(scheduleTable(todayTimes, tomorrowTimes))
    }

    /**
     * Resolves today's and tomorrow's timings through the offline-first repository (Room cache,
     * then AlAdhan, then a local calculation) and re-registers the alarm table from them. Safe
     * to call at startup: it never throws and a failure leaves the existing alarms untouched.
     */
    suspend fun scheduleAllDailyAlarmsRefreshed(
        coordinates: Pair<Double, Double>? = null
    ): AlarmScheduleResult {
        alarmManager ?: return record(AlarmScheduleResult.Unsupported)
        if (!preferences.azaanSettings.enabled) {
            cancelAllAlarms()
            return record(AlarmScheduleResult.Failed("Azaan notifications are turned off"))
        }
        val repository = prayerTimes ?: return scheduleAllDailyAlarms(coordinates)
        val coords = coordinates ?: preferences.locationCoordinates
            ?: return record(AlarmScheduleResult.Failed("No prayer location saved"))
        val school = preferences.madhhab
        // The saved location's zone, not the device's: a manual city in another timezone must
        // still trigger at its own local prayer time.
        val zone = activeZone()

        val today = JLocalDate.now(zone)
        val tomorrow = today.plusDays(1)

        val rule = preferences.highLatitudeRule
        val todayTimes = runCatching {
            repository.resolveDay(today, coords, school, zone, highLatitudeRule = rule).times
        }.getOrDefault(emptyMap()).ifEmpty { engineTimes(today, coords, school) }

        val tomorrowTimes = runCatching {
            repository.resolveDay(tomorrow, coords, school, zone, highLatitudeRule = rule).times
        }.getOrDefault(emptyMap()).ifEmpty { engineTimes(tomorrow, coords, school) }

        return record(scheduleTable(todayTimes, tomorrowTimes))
    }

    /** Registers the five prayers, rolling elapsed ones onto tomorrow's table. */
    private fun scheduleTable(
        todayTimes: Map<PrayerType, JLocalTime>,
        tomorrowTimes: Map<PrayerType, JLocalTime>
    ): AlarmScheduleResult {
        // Validate the replacement timetable BEFORE cancelling anything. A failed or partial
        // refresh must leave the alarms that already work untouched.
        if (!isCompleteSchedule(todayTimes) || !isCompleteSchedule(tomorrowTimes)) {
            return recordPreservingExistingAlarms(
                AlarmScheduleResult.Failed(
                    "Incomplete prayer schedule; existing alarms were left unchanged"
                )
            )
        }

        val settings = preferences.azaanSettings
        if (!settings.enabled) {
            return AlarmScheduleResult.Failed("Azaan notifications are turned off")
        }
        if (!notificationsGranted()) {
            // A reminder that can never be posted must not be reported as pending.
            return AlarmScheduleResult.PermissionMissing
        }

        // Re-register the complete table so a prayer switched to OFF loses its previous
        // PendingIntent instead of firing an alert the user just disabled.
        cancelAllAlarms()

        val zone = activeZone()
        val nowZoned = ZonedDateTime.now(zone)
        val todayJ = nowZoned.toLocalDate()
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        var earliestTrigger: Long? = null
        var earliestPrayer: PrayerType? = null
        var armedCount = 0
        var anyInexact = false
        var lastFailure: AlarmScheduleResult? = null

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
            val triggerMillis = triggerZoned.toInstant().toEpochMilli()

            val result = scheduleSingleAlarm(
                prayerType = prayer,
                prayerName = prayer.name.lowercase().replaceFirstChar { it.uppercase() },
                timeFormatted = triggerZoned.toLocalTime().format(timeFormatter),
                triggerEpochMillis = triggerMillis,
                alertMode = mode,
                muezzin = settings.muezzin,
                windowEndMillis = windowEnd
            )

            if (result.isArmed) {
                armedCount++
                if (result is AlarmScheduleResult.ExactAlarmDenied) anyInexact = true
                if (earliestTrigger == null || triggerMillis < earliestTrigger) {
                    earliestTrigger = triggerMillis
                    earliestPrayer = prayer
                }
            } else {
                lastFailure = result
                Log.w(TAG, "Could not arm $prayer: ${result.code}")
            }
        }

        if (armedCount == 0) {
            return lastFailure ?: AlarmScheduleResult.Failed("No prayer time could be resolved")
        }

        val trigger = earliestTrigger ?: System.currentTimeMillis()
        preferences.nextAlarmAtMillis = trigger
        preferences.nextAlarmPrayer = earliestPrayer?.name.orEmpty()
        return if (anyInexact) AlarmScheduleResult.ExactAlarmDenied else AlarmScheduleResult.Scheduled(trigger)
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
            schoolId = SunniMethodMapper.schoolId(school),
            // Must match the key the repository recorded, otherwise a chosen high-latitude
            // rule would silently fall back to the local astronomical table.
            latitudeRule = PrayerTimeRepositoryImpl.latitudeAdjustmentMethod(preferences.highLatitudeRule)
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
            timeZone = KTimeZone.of(activeZone().id),
            adjustments = preferences.prayerAdjustments,
            highLatitudeRule = preferences.highLatitudeRule
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
    ): AlarmScheduleResult = arm(
        key = AlarmIdentity.prayerKey(prayerType.name),
        prayerName = prayerName,
        prayerType = prayerType,
        timeFormatted = timeFormatted,
        triggerEpochMillis = triggerEpochMillis,
        isSnooze = false,
        alertMode = alertMode,
        muezzin = muezzin,
        isTest = false,
        windowEndMillis = windowEndMillis
    )

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

    /**
     * Re-fires a prayer reminder once, [minutes] from now.
     *
     * Each snooze gets its own identity (trigger instant + monotonic sequence), so two snoozes
     * requested in the same millisecond cannot overwrite each other, and [cancelAllAlarms]
     * finds them again through the one-off registry.
     */
    fun scheduleSnooze(
        prayerType: PrayerType = PrayerType.DHUHR,
        prayerName: String = "Salah",
        minutes: Int = SNOOZE_MINUTES,
        timeFormatted: String = ""
    ): AlarmScheduleResult {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        return arm(
            key = AlarmIdentity.snoozeKey(prayerType.name, triggerAt),
            prayerName = prayerName,
            prayerType = prayerType,
            timeFormatted = timeFormatted,
            triggerEpochMillis = triggerAt,
            isSnooze = true,
            alertMode = preferences.azaanSettings.modeFor(prayerType),
            muezzin = preferences.azaanSettings.muezzin,
            isTest = false,
            windowEndMillis = triggerAt + SNOOZE_MINUTES * 60_000L
        )
    }

    /**
     * Fires one real prayer alarm a few seconds from now so the user can verify the
     * notification, sound and alert mode without waiting for the next prayer.
     *
     * The returned [AlarmScheduleResult] is what the settings screen must display: a denied
     * permission or a refused alarm is reported instead of a false "scheduled".
     */
    fun scheduleTestAlarmInSeconds(
        seconds: Long = 5,
        prayerType: PrayerType = PrayerType.DHUHR
    ): AlarmScheduleResult {
        val triggerAt = System.currentTimeMillis() + seconds * 1_000L
        return arm(
            key = AlarmIdentity.testKey(prayerType.name, triggerAt),
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
    }

    /**
     * Cancels every alarm this app may have armed: the five stable prayer slots plus any
     * one-off test/snooze keys still recorded in the registry.
     *
     * Safe to call repeatedly and safe to call after code changes, because each intent is
     * rebuilt from its key instead of a hard-coded request-code table.
     */
    fun cancelAllAlarms() {
        for (prayer in PrayerType.entries) {
            cancelKey(AlarmIdentity.prayerKey(prayer.name))
        }
        AlarmPendingIntents.registeredKeys(context).forEach { cancelKey(it) }
        AlarmPendingIntents.clearRegistry(context)
        preferences.nextAlarmAtMillis = 0L
        preferences.nextAlarmPrayer = ""
    }

    /**
     * Cancels one alarm by key, using the exact same factory that armed it. Returns true when a
     * PendingIntent was actually found, which makes idempotent cancellation observable.
     */
    internal fun cancelKey(key: String): Boolean {
        val am = alarmManager ?: return false
        val pendingIntent = AlarmPendingIntents.existing(context, key) ?: return false
        am.cancel(pendingIntent)
        pendingIntent.cancel()
        AlarmPendingIntents.unregister(context, key)
        return true
    }

    /** Arms one alarm and reports the truth about whether it is actually registered. */
    private fun arm(
        key: String,
        prayerName: String,
        prayerType: PrayerType,
        timeFormatted: String,
        triggerEpochMillis: Long,
        isSnooze: Boolean,
        alertMode: PrayerAlertMode,
        muezzin: Muezzin,
        isTest: Boolean,
        windowEndMillis: Long?
    ): AlarmScheduleResult {
        val preflight = AlarmSchedulePreflight.evaluate(
            alarmManagerAvailable = alarmManager != null,
            alarmsEnabled = preferences.alarmsEnabled,
            notificationsGranted = notificationsGranted(),
            exactAlarmsAllowed = canScheduleExactAlarms(),
            triggerAtMillis = triggerEpochMillis,
            nowMillis = System.currentTimeMillis()
        )
        // PermissionMissing/Unsupported/Failed mean "do not arm"; ExactAlarmDenied still arms
        // an inexact fallback so the reminder is not lost.
        if (preflight != null && preflight !is AlarmScheduleResult.ExactAlarmDenied) {
            return preflight
        }

        val pendingIntent = AlarmPendingIntents.create(context, key) { intent ->
            intent.putExtra(EXTRA_PRAYER_NAME, prayerName)
            intent.putExtra(EXTRA_PRAYER_TYPE, prayerType.name)
            intent.putExtra(EXTRA_PRAYER_TIME_FORMATTED, timeFormatted)
            intent.putExtra(EXTRA_TRIGGER_MILLIS, triggerEpochMillis)
            intent.putExtra(EXTRA_IS_SNOOZE, isSnooze)
            intent.putExtra(EXTRA_ALERT_MODE, alertMode.name)
            intent.putExtra(EXTRA_MUEZZIN_ID, muezzin.id)
            intent.putExtra(EXTRA_IS_TEST, isTest)
            intent.putExtra(EXTRA_WINDOW_START_MILLIS, triggerEpochMillis)
            intent.putExtra(EXTRA_WINDOW_END_MILLIS, windowEndMillis ?: triggerEpochMillis)
        }

        // One-off alarms cannot be rebuilt from the prayer name alone, so remember them.
        if (isSnooze || isTest) AlarmPendingIntents.register(context, key)

        val exact = setAlarmCompat(triggerEpochMillis, pendingIntent)
        if (!exact && preflight is AlarmScheduleResult.ExactAlarmDenied) {
            return preflight
        }
        return if (exact) {
            AlarmScheduleResult.Scheduled(triggerEpochMillis)
        } else {
            // setAndAllowWhileIdle succeeded but the platform refused exactness.
            AlarmScheduleResult.ExactAlarmDenied
        }
    }

    /**
     * Prayer reminders are ordinary notification-type alerts, not alarm-clock alarms: they must
     * not take over the screen or the alarm stream. The exact-and-idle-safe window keeps the
     * timing precise through Doze, and falls back to an inexact idle-safe alarm when the user
     * has not granted exact-alarm scheduling.
     *
     * Returns true when the exact API was used.
     */
    private fun setAlarmCompat(triggerAtMillis: Long, pendingIntent: PendingIntent): Boolean {
        val am = alarmManager ?: return false

        if (canScheduleExactAlarms()) {
            try {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return true
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call: use the idle-safe path.
                Log.w(TAG, "Exact alarm refused; falling back to the idle-safe alarm", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "AlarmManager rejected the alarm time", e)
                return false
            }
        }
        return runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            false
        }.getOrDefault(false)
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** Persists the outcome so the in-app self-test can show what really happened. */
    private fun record(result: AlarmScheduleResult): AlarmScheduleResult {
        preferences.lastAlarmScheduleResult = result.code
        if (!result.isArmed) {
            preferences.nextAlarmAtMillis = 0L
            preferences.nextAlarmPrayer = ""
        }
        return result
    }

    /**
     * Records a refusal without touching the already-armed alarms: next-alarm metadata is
     * only cleared when cancellation really happened.
     */
    private fun recordPreservingExistingAlarms(result: AlarmScheduleResult): AlarmScheduleResult {
        preferences.lastAlarmScheduleResult = result.code
        return result
    }
}
