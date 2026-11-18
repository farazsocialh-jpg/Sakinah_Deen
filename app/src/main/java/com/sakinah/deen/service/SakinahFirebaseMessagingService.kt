package com.sakinah.deen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sakinah.deen.MainActivity
import com.sakinah.deen.R

/**
 * Receives FCM messages and posts them as local notifications. Data and notification payloads
 * are both handled so messages display whether the app is in the foreground or background.
 */
class SakinahFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Topic subscription is (re)established by SakinahApp; a refreshed token keeps it alive.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        ensureChannel()

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"].orEmpty()

        val mainRoute = message.data["route"]

        fun pendingIntentFor(targetRoute: String?, requestCode: Int): PendingIntent {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!targetRoute.isNullOrBlank()) {
                    putExtra("route", targetRoute)
                }
            }
            return PendingIntent.getActivity(
                this,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_app)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntentFor(mainRoute, 0))

        val action1Label = message.data["action1_label"]
        val action1Route = message.data["action1_route"]
        if (!action1Label.isNullOrBlank() && !action1Route.isNullOrBlank()) {
            builder.addAction(
                R.drawable.ic_stat_app,
                action1Label,
                pendingIntentFor(action1Route, 1)
            )
        }
        val action2Label = message.data["action2_label"]
        val action2Route = message.data["action2_route"]
        if (!action2Label.isNullOrBlank() && !action2Route.isNullOrBlank()) {
            builder.addAction(
                R.drawable.ic_stat_app,
                action2Label,
                pendingIntentFor(action2Route, 2)
            )
        }

        val notification = builder.build()

        // Android 13+ requires POST_NOTIFICATIONS at runtime; skip posting if it was denied.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(
                (message.hashCode() xor System.currentTimeMillis().toInt()),
                notification
            )
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.app_name)
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "sakinah_fcm_notifications"
    }
}