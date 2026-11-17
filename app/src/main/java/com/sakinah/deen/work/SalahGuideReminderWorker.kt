package com.sakinah.deen.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sakinah.deen.MainActivity
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.SalahGuideProgressStore
import com.sakinah.deen.service.NotificationChannels

/**
 * Optional daily nudge to continue the Visual Salah Guide.
 *
 * No-ops (success, no notification) whenever the user has not opted in or has nothing left
 * to learn, so an unfinished guide never nags someone who turned reminders off.
 */
class SalahGuideReminderWorker(
    appContext: Context,
    params: WorkerParameters,
    private val progressStore: SalahGuideProgressStore = SalahGuideProgressStore(appContext)
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "sakinah_salah_guide_reminder"
        const val NOTIFICATION_ID = 4262
    }

    override suspend fun doWork(): Result {
        val progress = progressStore.progress.value
        if (!progress.practiceReminderEnabled) return Result.success()

        val learned = progress.learnedStepKeys.size

        // NotificationManagerCompat.notify() is a no-op without POST_NOTIFICATIONS on API
        // 33+, but lint (rightly) wants the runtime grant checked explicitly.
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val notificationsEnabled = permissionGranted && runCatching {
            NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        }.getOrDefault(false)
        if (!notificationsEnabled) return Result.success()

        val content = if (learned > 0) {
            applicationContext.getString(R.string.guide_reminder_body_progress, learned)
        } else {
            applicationContext.getString(R.string.guide_reminder_body)
        }

        val openGuide = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationChannels.CHANNEL_HABIT_REMINDERS
        )
            .setSmallIcon(R.drawable.ic_stat_app)
            .setContentTitle(applicationContext.getString(R.string.guide_reminder_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openGuide)
            .build()

        return runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
