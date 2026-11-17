package com.sakinah.deen.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import java.time.LocalDate
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
 * Nothing here requests a full-screen intent or the alarm stream any more.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "PrayerAlarmReceiver"
        const val DEFAULT_WINDOW_MS = 10 * 60_000L
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

        val triggerMillis = intent.getLongExtra(
            PrayerAlarmScheduler.EXTRA_TRIGGER_MILLIS,
            System.currentTimeMillis()
        )
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

                val alertMode = prefs.alertModeFor(prayerType)
                val notificationsAllowed = prefs.alarmsEnabled && canPostNotifications(appContext)
                val alreadyCompleted = !isTest && isPrayerAlreadyCompleted(appContext, prefs, prayerType)

                when {
                    !notificationsAllowed || alertMode == PrayerAlertMode.OFF -> {
                        // Respect a disabled reminder: drop any card still sitting in the shade.
                        NotificationChannels.cancelPrayerNotification(appContext)
                    }

                    alreadyCompleted -> NotificationChannels.cancelPrayerNotification(appContext)

                    alertMode == PrayerAlertMode.FULL_AZAAN -> startAdhanPlayback(
                        context = appContext,
                        prayerName = prayerName,
                        prayerType = prayerType,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        alertMode = alertMode
                    )

                    else -> postPrayerNotification(
                        context = appContext,
                        prayerName = prayerName,
                        prayerType = prayerType,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        alertMode = alertMode
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

    /** Full Azaan: the service owns the card so it can detach it cleanly when muted. */
    private fun startAdhanPlayback(
        context: Context,
        prayerName: String,
        prayerType: PrayerType,
        windowStart: Long,
        windowEnd: Long,
        alertMode: PrayerAlertMode
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
            postPrayerNotification(context, prayerName, prayerType, windowStart, windowEnd, PrayerAlertMode.SHORT_TONE)
        }
    }

    private fun postPrayerNotification(
        context: Context,
        prayerName: String,
        prayerType: PrayerType,
        windowStart: Long,
        windowEnd: Long,
        alertMode: PrayerAlertMode
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
                alertMode = alertMode
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
        prayerType: PrayerType
    ): Boolean {
        if (prayerType == PrayerType.SUNRISE) return false
        val salahDao = runCatching {
            GlobalContext.getOrNull()?.get<SalahDao>()
        }.getOrNull() ?: return false
        val epochDay = LocalDate.now(AzaanScheduling.activeZoneId(prefs)).toEpochDay()
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
