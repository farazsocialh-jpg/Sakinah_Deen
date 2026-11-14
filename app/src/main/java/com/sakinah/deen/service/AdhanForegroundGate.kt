package com.sakinah.deen.service

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException

/**
 * Promotes the Azaan service to the foreground, or degrades to an ordinary notification.
 *
 * Android 12+ may refuse a background foreground-service start (and Android 14+ adds media
 * playback requirements); the platform throws instead of returning, which used to crash the
 * process mid-adhan. This gate turns every refusal into a visible reminder notification so the
 * prayer alert is never lost and the service can stop cleanly.
 *
 * Extracted from [AdhanPlaybackService] so instrumented tests can drive the refusal path with a
 * real [android.app.NotificationManager] without relying on background-start restrictions.
 */
internal class AdhanForegroundGate(
    private val context: Context,
    private val notificationId: Int = AdhanPlaybackService.NOTIFICATION_ID
) {

    /**
     * Runs [attemptForeground]; returns true when the service is now in the foreground.
     * On any refusal the reminder is posted as a normal notification and false is returned.
     */
    fun enter(notification: Notification, attemptForeground: () -> Unit): Boolean =
        try {
            attemptForeground()
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            postReminderNotification(notification)
            false
        }

    /** Returns false when notifications are blocked, so callers can skip extra work. */
    fun postReminderNotification(notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }.isSuccess
    }
}
