package com.sakinah.deen.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sakinah.deen.MainActivity
import com.sakinah.deen.data.model.Muezzin
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerAlarmScheduler.ACTION_PRAYER_ALARM) return

        val prefs = SakinahPreferences(context)
        val prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) ?: "Salah"
        val prayerTime = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME_FORMATTED) ?: ""
        val prayerTypeName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TYPE)
        val prayerType = prayerTypeName
            ?.let { name -> runCatching { PrayerType.valueOf(name) }.getOrNull() }
            ?: PrayerType.DHUHR
        val isSnooze = intent.getBooleanExtra(PrayerAlarmScheduler.EXTRA_IS_SNOOZE, false)
        val alertMode = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_ALERT_MODE)
            ?.let { name -> runCatching { PrayerAlertMode.valueOf(name) }.getOrNull() }
            ?: prefs.alertModeFor(prayerType)
        val muezzin = Muezzin.fromId(intent.getStringExtra(PrayerAlarmScheduler.EXTRA_MUEZZIN_ID))

        NotificationChannels.createChannels(context)

        if (prefs.alarmsEnabled && alertMode != PrayerAlertMode.OFF) {
            val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
                action = AdhanPlaybackService.ACTION_PLAY_ADHAN
                putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
                putExtra(AdhanPlaybackService.EXTRA_PRAYER_TYPE, prayerType.name)
                putExtra(AdhanPlaybackService.EXTRA_ALERT_MODE, alertMode.name)
                putExtra(AdhanPlaybackService.EXTRA_MUEZZIN_ID, muezzin.id)
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

        // A fired PendingIntent is consumed. Re-register the full daily table so the
        // next occurrence of this prayer stays scheduled. Snoozes must not reset the table.
        if (!isSnooze) {
            runCatching {
                PrayerAlarmScheduler(
                    context = context,
                    astronomyEngine = PrayerCalculationEngineImpl(),
                    preferences = prefs
                ).scheduleAllDailyAlarms()
            }
        }
    }

    private fun showAdhanNotificationFallback(
        context: Context,
        prayerName: String,
        prayerTime: String,
        alertMode: PrayerAlertMode
    ) {
        // POST_NOTIFICATIONS is a runtime permission on Android 13+. If the user
        // denied it, degrade silently instead of crashing or spamming logs.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
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
