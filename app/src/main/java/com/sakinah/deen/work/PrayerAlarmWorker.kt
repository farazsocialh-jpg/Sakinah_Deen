package com.sakinah.deen.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PrayerAlarmWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val PRAYER_NAME_KEY = "prayer_name"
        const val PRAYER_TIME_KEY = "prayer_time"
        const val CHANNEL_ID = "sakinah_adhan_channel"
    }

    override suspend fun doWork(): Result {
        val prayerName = inputData.getString(PRAYER_NAME_KEY) ?: "Prayer"
        val prayerTime = inputData.getString(PRAYER_TIME_KEY) ?: ""

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

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time for $prayerName · $prayerTime")
            .setContentText("حي على الصلاة ، حي على الفلاح — Come to Prayer, Come to Success")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(prayerName.hashCode(), notification)

        return Result.success()
    }
}
