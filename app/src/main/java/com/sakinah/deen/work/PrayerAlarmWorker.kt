package com.sakinah.deen.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sakinah.deen.R
import java.io.IOException

class PrayerAlarmWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val PRAYER_NAME_KEY = "prayer_name"
        const val PRAYER_TIME_KEY = "prayer_time"
        const val CHANNEL_ID = "sakinah_adhan_channel"

        /** Give transient failures a few runs before WorkManager gives up. */
        private const val MAX_ATTEMPTS = 3
    }

    private fun checkNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun doWork(): Result {
        val prayerName = inputData.getString(PRAYER_NAME_KEY) ?: "Prayer"
        val prayerTime = inputData.getString(PRAYER_TIME_KEY) ?: ""

        return try {
            // Android 13+ POST_NOTIFICATIONS permission check
            if (!checkNotificationsPermission()) {
                Result.success()
            } else {
                val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Daily Adhan & Salah Call",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Exact notifications and Adhan reminders for prayer times"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                // Build display prayer time without trailing " · " when blank
                val displayTime = if (prayerTime.isNotBlank()) "$prayerTime" else ""
                val prayerLabel = if (displayTime.isNotBlank()) "$prayerName · $displayTime" else prayerName

                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle("Time for $prayerLabel")
                    .setContentText("حي على الصلاة ، حي على الفلاح — Come to Prayer, Come to Success")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(prayerName.hashCode(), notification)
            }

            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // IPC/notification-transport failures are transient; retrying avoids dropping
            // a prayer reminder because a single post failed.
            if ((e is IOException || e is IllegalStateException) && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
