package com.sakinah.deen.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sakinah.deen.MainActivity
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                // Device rebooted or time-zone altered: recalculate astronomical ephemeris and restore alarms
                val scheduler = PrayerAlarmScheduler(context, PrayerCalculationEngineImpl())
                val defaultCoords = Pair(51.5074, -0.1278) // Restores stored or default coordinates
                scheduler.scheduleAllDailyAlarms(defaultCoords)
            }

            PrayerAlarmScheduler.ACTION_PRAYER_ALARM -> {
                val prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) ?: "Salah"
                val prayerTime = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME_FORMATTED) ?: ""

                // Ensure notification channels exist
                NotificationChannels.createChannels(context)

                // Start Foreground Adhan Playback Service
                val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
                    this.action = AdhanPlaybackService.ACTION_PLAY_ADHAN
                    putExtra(AdhanPlaybackService.EXTRA_PRAYER_NAME, prayerName)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // Fallback to notification alone if foreground service launch restricted by OS
                    showAdhanNotificationFallback(context, prayerName, prayerTime)
                }

                // Reschedule for next day cycle
                val scheduler = PrayerAlarmScheduler(context, PrayerCalculationEngineImpl())
                scheduler.scheduleAllDailyAlarms(Pair(51.5074, -0.1278))
            }
        }
    }

    private fun showAdhanNotificationFallback(context: Context, prayerName: String, prayerTime: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            200,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_SALAH_ADHAN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Adhan: Time for $prayerName · $prayerTime")
            .setContentText("حي على الصلاة ، حي على الفلاح — Come to Prayer, Come to Success")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        notificationManager.notify(prayerName.hashCode(), notification)
    }
}
