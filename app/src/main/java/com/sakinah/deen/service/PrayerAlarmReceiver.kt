package com.sakinah.deen.service

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sakinah.deen.R
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.prayer.englishName
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Fires the prayer reminder when an alarm triggers.
 *
 * The alert mode is read from live preferences at fire time — never from the extras baked in
 * when the alarm was armed — so switching to Vibrate/Silent/Off takes effect immediately:
 *
 *  - Full Azaan: start [AdhanPlaybackService], which posts the rich card and plays the chosen
 *    Muezzin recording.
 *  - Short Tone / Vibrate Only / Silent: post the rich card directly, on the matching channel.
 *  - Off (or notifications blocked / prayer already marked): no card at all.
 *
 * A date-stamped urgent reminder ([UrgentReminderAlarmIdentity]) takes a separate path: it
 * re-validates its window, posts one silent card on the late channel (never starts audio) and
 * advances the reminder rotation only after that post succeeded.
 *
 * Nothing here requests a full-screen intent or the alarm stream any more.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "PrayerAlarmReceiver"
        const val DEFAULT_WINDOW_MS = 10 * 60_000L

        /** Separate slot so the silent nudge never replaces the main prayer card. */
        const val LATE_NOTIFICATION_ID = 4301
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerAlarmScheduler.ACTION_PRAYER_ALARM) return

        val appContext = context.applicationContext
        val prefs = SakinahPreferences(appContext)
        val prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) ?: "Salah"
        val prayerTypeName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TYPE)
        // Never guess the prayer: defaulting to Dhuhr used to fire a wrong Dhuhr alert for any
        // malformed/legacy alarm. Without a valid type there is nothing correct to show.
        val prayerType = prayerTypeName
            ?.let { name -> runCatching { PrayerType.valueOf(name) }.getOrNull() }
        if (prayerType == null) {
            Log.w(TAG, "Ignoring prayer alarm with missing/invalid EXTRA_PRAYER_TYPE=$prayerTypeName")
            return
        }
        val isSnooze = intent.getBooleanExtra(PrayerAlarmScheduler.EXTRA_IS_SNOOZE, false)
        val isTest = intent.getBooleanExtra(PrayerAlarmScheduler.EXTRA_IS_TEST, false)
        val isUrgent = intent.getBooleanExtra(
            UrgentReminderAlarmIdentity.EXTRA_IS_URGENT_REMINDER,
            false
        )

        val triggerMillis = intent.getLongExtra(
            PrayerAlarmScheduler.EXTRA_TRIGGER_MILLIS,
            System.currentTimeMillis()
        )
        // Read at fire time so enabling the optional critical Fajr channel applies at once.
        val criticalFajr = runCatching { prefs.azaanSettings.criticalFajrChannel }
            .getOrDefault(false)
        val windowStart = intent.getLongExtra(
            PrayerAlarmScheduler.EXTRA_WINDOW_START_MILLIS,
            triggerMillis
        )
        val windowEnd = intent.getLongExtra(
            PrayerAlarmScheduler.EXTRA_WINDOW_END_MILLIS,
            windowStart + DEFAULT_WINDOW_MS
        )

        // Work (preference reads, Room lookup, rescheduling) happens off the main thread, so
        // keep the receiver alive with goAsync() until it finishes.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationChannels.createChannels(appContext)

                if (isUrgent) {
                    // A one-shot nudge is separate from the daily table: it never starts audio,
                    // never consumes a normal alarm slot, and must not be re-armed here.
                    handleUrgentReminder(
                        context = appContext,
                        prefs = prefs,
                        intent = intent,
                        prayerType = prayerType,
                        triggerMillis = triggerMillis,
                        windowStart = windowStart,
                        windowEnd = windowEnd
                    )
                    return@launch
                }

                val alertMode = prefs.alertModeFor(prayerType)
                val notificationsAllowed = prefs.alarmsEnabled && canPostNotifications(appContext)
                val alreadyCompleted = !isTest && isPrayerAlreadyCompleted(appContext, prefs, prayerType)

                when {
                    !notificationsAllowed || alertMode == PrayerAlertMode.OFF -> {
                        // Respect a disabled reminder: drop any card still sitting in the shade.
                        NotificationChannels.cancelPrayerNotification(appContext)
                    }

                    alreadyCompleted -> NotificationChannels.cancelPrayerNotification(appContext)

                    alertMode == PrayerAlertMode.FULL_AZAAN -> {
                        // Post the high-importance reminder card first, independently of the
                        // (possibly restricted) foreground-service start, so Fajr is visible
                        // even if the full Athan cannot play.
                        postPrayerNotification(
                            context = appContext,
                            prayerName = prayerName,
                            prayerType = prayerType,
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            alertMode = PrayerAlertMode.VIBRATE_ONLY,
                            criticalFajr = criticalFajr
                        )
                        startAdhanPlayback(
                            context = appContext,
                            prayerName = prayerName,
                            prayerType = prayerType,
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            alertMode = alertMode,
                            criticalFajr = criticalFajr
                        )
                    }

                    else -> postPrayerNotification(
                        context = appContext,
                        prayerName = prayerName,
                        prayerType = prayerType,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        alertMode = alertMode,
                        criticalFajr = criticalFajr
                    )
                }

                // A fired PendingIntent is consumed. Re-register the full daily table so the
                // next occurrence of this prayer stays scheduled. Snoozes must not reset it.
                if (!isSnooze) reschedule(context = appContext, prefs = prefs)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Silent end-of-window nudge. Every guard returns before the rotation counter is touched, so
     * a stale, completed, disabled or blocked alarm never consumes a reminder reference.
     */
    private suspend fun handleUrgentReminder(
        context: Context,
        prefs: SakinahPreferences,
        intent: Intent,
        prayerType: PrayerType,
        triggerMillis: Long,
        windowStart: Long,
        windowEnd: Long
    ) {
        // Checked first, as required: a prayer already marked done gets no card and no sound.
        // Use the window's own date so an Isha nudge after midnight still sees the record
        // the user created before midnight.
        val windowEpochDay = intent.getLongExtra(
            UrgentReminderAlarmIdentity.EXTRA_PRAYER_EPOCH_DAY,
            LocalDate.now(AzaanScheduling.activeZoneId(prefs)).toEpochDay()
        )
        if (isPrayerAlreadyCompleted(context, prefs, prayerType, windowEpochDay)) return

        if (!prefs.latePrayerReminderEnabled || !prefs.alarmsEnabled ||
            prefs.alertModeFor(prayerType) == PrayerAlertMode.OFF
        ) {
            return
        }
        if (!isUrgentReminderFresh(
            prefs = prefs,
            prayerType = prayerType,
            intent = intent,
            nowMillis = System.currentTimeMillis(),
            triggerMillis = triggerMillis,
            windowStart = windowStart,
            windowEnd = windowEnd
        )) {
            return
        }
        if (!canPostNotifications(context) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager?.getNotificationChannel(NotificationChannels.CHANNEL_PRAYER_LATE)?.importance ==
            NotificationManager.IMPORTANCE_NONE
        ) {
            return
        }

        val index = prefs.lateReminderIndex
        val notification = PrayerNotificationFactory.build(
            context = context,
            spec = PrayerNotificationFactory.Spec(
                prayerType = prayerType,
                prayerName = prayerType.englishName,
                startMillis = windowStart,
                endMillis = windowEnd,
                // SILENT + the dedicated late channel: no Adhan service, no audio. The factory
                // still adds the Mark-as-Prayed action, tagged with the active-zone date.
                alertMode = PrayerAlertMode.SILENT,
                lateReminder = PrayerReminderContent.lateReminderFor(index),
                titleOverride = context.getString(R.string.notif_late_title, prayerType.englishName),
                bodyOverride = context.getString(R.string.notif_late_body),
                channelIdOverride = NotificationChannels.CHANNEL_PRAYER_LATE,
                autoCancel = true
            )
        )
        // Explicit call-site permission guard: the user may revoke POST_NOTIFICATIONS
        // between the earlier check and the actual post.
        if (!canPostNotifications(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(LATE_NOTIFICATION_ID, notification)
        }.onSuccess {
            // Only a successfully posted notification consumes a rotation slot.
            prefs.lateReminderIndex = index + 1
        }.onFailure { error ->
            Log.w(TAG, "Could not post the late prayer reminder", error)
        }
    }

    /** Re-validates the baked-in window in the prayer location's zone at fire time. */
    private fun isUrgentReminderFresh(
        prefs: SakinahPreferences,
        prayerType: PrayerType,
        intent: Intent,
        nowMillis: Long,
        triggerMillis: Long,
        windowStart: Long,
        windowEnd: Long
    ): Boolean {
        val zone = runCatching { AzaanScheduling.activeZoneId(prefs) }
            .getOrDefault(ZoneId.systemDefault())
        return UrgentReminderAlarmIdentity.isFresh(
            nowMillis = nowMillis,
            todayEpochDay = LocalDate.now(zone).toEpochDay(),
            epochDay = intent.getLongExtra(UrgentReminderAlarmIdentity.EXTRA_PRAYER_EPOCH_DAY, -1L),
            windowStartMillis = windowStart,
            windowEndMillis = windowEnd,
            triggerMillis = triggerMillis,
            zone = zone,
            // Isha's window can close after midnight; the other four cannot.
            allowNextDayGrace = prayerType == PrayerType.ISHA
        )
    }

    /** Full Azaan: the service owns the card so it can detach it cleanly when muted. */
    private fun startAdhanPlayback(
        context: Context,
        prayerName: String,
        prayerType: PrayerType,
        windowStart: Long,
        windowEnd: Long,
        alertMode: PrayerAlertMode,
        criticalFajr: Boolean = false
    ) {
        val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
            action = AdhanPlaybackService.ACTION_PLAY_ADHAN
            putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AdhanPlaybackService.EXTRA_PRAYER_TYPE, prayerType.name)
            putExtra(AdhanPlaybackService.EXTRA_WINDOW_START_MILLIS, windowStart)
            putExtra(AdhanPlaybackService.EXTRA_WINDOW_END_MILLIS, windowEnd)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Background-start restrictions (Android 12/14+) or a revoked FGS permission:
            // keep the reminder visible as an ordinary notification instead.
            Log.w(TAG, "Foreground Azaan start refused; posting the reminder card instead", e)
            // Fall back to the sound channel so the user still hears a notification.
            postPrayerNotification(
                context = context,
                prayerName = prayerName,
                prayerType = prayerType,
                windowStart = windowStart,
                windowEnd = windowEnd,
                alertMode = PrayerAlertMode.SHORT_TONE,
                criticalFajr = criticalFajr
            )
        }
    }

    private fun postPrayerNotification(
        context: Context,
        prayerName: String,
        prayerType: PrayerType,
        windowStart: Long,
        windowEnd: Long,
        alertMode: PrayerAlertMode,
        criticalFajr: Boolean = false
    ) {
        // Checked again in place so the notification call is always guarded by a permission
        // test the platform (and the linter) can see.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = PrayerNotificationFactory.build(
            context = context,
            spec = PrayerNotificationFactory.Spec(
                prayerType = prayerType,
                prayerName = prayerName,
                startMillis = windowStart,
                endMillis = windowEnd,
                alertMode = alertMode,
                criticalFajrChannel = criticalFajr
            )
        )
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(AdhanPlaybackService.NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "Could not post the prayer notification", it) }
    }

    private suspend fun reschedule(context: Context, prefs: SakinahPreferences) {
        val repository = runCatching {
            GlobalContext.getOrNull()?.get<PrayerTimeRepository>()
        }.getOrNull()
        runCatching {
            PrayerAlarmScheduler(
                context = context,
                astronomyEngine = PrayerCalculationEngineImpl(),
                preferences = prefs,
                prayerTimes = repository
            ).scheduleAllDailyAlarmsRefreshed()
        }
    }

    /** True when the user already marked this prayer as prayed today at the active location. */
    private suspend fun isPrayerAlreadyCompleted(
        context: Context,
        prefs: SakinahPreferences,
        prayerType: PrayerType,
        epochDay: Long = LocalDate.now(AzaanScheduling.activeZoneId(prefs)).toEpochDay()
    ): Boolean {
        if (prayerType == PrayerType.SUNRISE) return false
        val salahDao = runCatching {
            GlobalContext.getOrNull()?.get<SalahDao>()
        }.getOrNull() ?: return false
        return runCatching {
            salahDao.getSalahRecord(epochDay, prayerType.name)?.isCompleted == true
        }.getOrDefault(false)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
