package com.sakinah.deen.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sakinah.deen.MainActivity
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

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerAlarmScheduler.ACTION_PRAYER_ALARM) return

        val appContext = context.applicationContext
        val prefs = SakinahPreferences(appContext)
        val prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) ?: "Salah"
        val prayerTime = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME_FORMATTED) ?: ""
        val prayerTypeName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TYPE)
        val prayerType = prayerTypeName
            ?.let { name -> runCatching { PrayerType.valueOf(name) }.getOrNull() }
            ?: PrayerType.DHUHR
        val isSnooze = intent.getBooleanExtra(PrayerAlarmScheduler.EXTRA_IS_SNOOZE, false)
        val isTest = intent.getBooleanExtra(PrayerAlarmScheduler.EXTRA_IS_TEST, false)

        // Work (preference reads, Room lookup, rescheduling) happens off the main thread, so
        // keep the receiver alive with goAsync() until it finishes.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationChannels.createChannels(appContext)

                // Intent extras are only hints from scheduling time. The user may have changed
                // the alert mode, muezzin or master switch since the alarm was armed.
                val alertMode = prefs.alertModeFor(prayerType)
                val muezzin = prefs.azaanSettings.muezzin
                val notificationsAllowed = prefs.alarmsEnabled && canPostNotifications(appContext)
                val alreadyCompleted = !isTest && isPrayerAlreadyCompleted(appContext, prefs, prayerType)

                if (notificationsAllowed && alertMode != PrayerAlertMode.OFF && !alreadyCompleted) {
                    startAdhanPlayback(
                        context = appContext,
                        prayerName = prayerName,
                        prayerType = prayerType,
                        prayerTime = prayerTime,
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

    private fun startAdhanPlayback(
        context: Context,
        prayerName: String,
        prayerType: PrayerType,
        prayerTime: String,
        alertMode: PrayerAlertMode
    ) {
        val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
            action = AdhanPlaybackService.ACTION_PLAY_ADHAN
            putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AdhanPlaybackService.EXTRA_PRAYER_TYPE, prayerType.name)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Background-start restrictions (Android 14+) or a revoked FGS permission:
            // fall back to a high-priority full-screen alarm notification.
            showAdhanNotificationFallback(context, prayerName, prayerTime, alertMode)
        }
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

    private fun showAdhanNotificationFallback(
        context: Context,
        prayerName: String,
        prayerTime: String,
        alertMode: PrayerAlertMode
    ) {
        // POST_NOTIFICATIONS is a runtime permission on Android 13+. If the user
        // denied it, degrade silently instead of crashing or spamming logs.
        if (!canPostNotifications(context)) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            200,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            context,
            201,
            Intent(context, AdhanPlaybackService::class.java).apply {
                action = AdhanPlaybackService.ACTION_STOP_ADHAN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val silent = alertMode == PrayerAlertMode.SILENT
        val channel = if (silent) {
            NotificationChannels.CHANNEL_SALAH_SILENT
        } else {
            NotificationChannels.CHANNEL_SALAH_ADHAN
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Adhan: Time for $prayerName · $prayerTime")
            .setContentText("حي على الصلاة — Come to Prayer, Come to Success")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Azaan", stopIntent)

        if (!silent) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        notificationManager.notify(prayerName.hashCode(), builder.build())
    }
}
