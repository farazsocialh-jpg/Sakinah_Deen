package com.sakinah.deen.service

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import com.sakinah.deen.R

object NotificationChannels {

    const val GROUP_PRAYER = "sakinah_prayer_group"
    const val GROUP_TRAVEL = "sakinah_travel_group"
    const val GROUP_HABIT = "sakinah_habit_group"

    const val CHANNEL_SALAH_ADHAN = "salah_adhan_channel"
    const val CHANNEL_SALAH_SILENT = "salah_silent_channel"
    const val CHANNEL_TRAVEL_SAFAR = "travel_safar_channel"
    const val CHANNEL_HABIT_REMINDERS = "habit_reminders_channel"

    /** Bundled Adhan URI, playable even when the app process was killed. */
    fun adhanSoundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.adhan_makkah}")

    fun fajrAdhanSoundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.adhan_fajr}")

    fun duaSafarSoundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.dua_al_safar}")

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(GROUP_PRAYER, context.getString(R.string.channel_group_prayer)),
                NotificationChannelGroup(GROUP_TRAVEL, context.getString(R.string.channel_group_travel)),
                NotificationChannelGroup(GROUP_HABIT, context.getString(R.string.channel_group_habit))
            )
        )

        val adhanChannel = NotificationChannel(
            CHANNEL_SALAH_ADHAN,
            context.getString(R.string.channel_adhan_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_PRAYER
            description = context.getString(R.string.channel_adhan_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            // Playback is owned by AdhanPlaybackService so the selected Muezzin is heard.
            // A channel sound here would overlap that recording with the bundled Makkah Adhan.
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val silentChannel = NotificationChannel(
            CHANNEL_SALAH_SILENT,
            context.getString(R.string.channel_salah_silent_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_PRAYER
            description = context.getString(R.string.channel_salah_silent_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        // Heads-up banners require IMPORTANCE_HIGH on Android 8+.
        val safarChannel = NotificationChannel(
            CHANNEL_TRAVEL_SAFAR,
            context.getString(R.string.channel_safar_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_TRAVEL
            description = context.getString(R.string.channel_safar_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val habitChannel = NotificationChannel(
            CHANNEL_HABIT_REMINDERS,
            context.getString(R.string.channel_habit_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_HABIT
            description = context.getString(R.string.channel_habit_desc)
            setShowBadge(true)
            enableVibration(false)
        }

        notificationManager.createNotificationChannels(
            listOf(adhanChannel, silentChannel, safarChannel, habitChannel)
        )
    }
}
