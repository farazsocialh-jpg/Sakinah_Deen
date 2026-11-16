package com.sakinah.deen.service

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.sakinah.deen.R
import com.sakinah.deen.data.model.PrayerAlertMode

/**
 * Notification channels for prayer reminders.
 *
 * Android 8+ freezes a channel's sound, importance and vibration the first time it is
 * created: reinstalling code cannot change them. Every behaviour therefore gets its own
 * versioned channel id, and the previous ids are deleted so the user never sees duplicates.
 */
object NotificationChannels {

    const val GROUP_PRAYER = "sakinah_prayer_group"
    const val GROUP_TRAVEL = "sakinah_travel_group"
    const val GROUP_HABIT = "sakinah_habit_group"

    /** IMPORTANCE_HIGH + standard notification chime + vibration (Short Chime / Standard sound). */
    const val CHANNEL_PRAYER_SOUND = "sakinah_prayer_sound_v2"

    /** IMPORTANCE_HIGH, no sound, vibration on (Vibrate Only, and Full Azaan's card). */
    const val CHANNEL_PRAYER_VIBRATE = "sakinah_prayer_vibrate_v2"

    /** IMPORTANCE_DEFAULT, no sound, no vibration (Silent). */
    const val CHANNEL_PRAYER_SILENT = "sakinah_prayer_silent_v2"

    const val CHANNEL_TRAVEL_SAFAR = "travel_safar_channel"
    const val CHANNEL_HABIT_REMINDERS = "habit_reminders_channel"

    /** Pre-versioning ids, kept only so they can be removed from the user's settings. */
    private const val LEGACY_CHANNEL_SALAH_ADHAN = "salah_adhan_channel"
    private const val LEGACY_CHANNEL_SALAH_SILENT = "salah_silent_channel"

    /** Bundled Adhan URI, playable even when the app process was killed. */
    fun adhanSoundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.adhan_makkah}")

    fun fajrAdhanSoundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.adhan_fajr}")

    fun duaSafarSoundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.dua_al_safar}")

    /**
     * Channel for one alert mode. Full Azaan posts on the vibrate channel on purpose: its
     * audio is the selected Muezzin recording played by [AdhanPlaybackService], so a channel
     * sound here would ring a second, different sound over the Azaan.
     */
    fun channelFor(mode: PrayerAlertMode): String = when (mode) {
        PrayerAlertMode.FULL_AZAAN -> CHANNEL_PRAYER_VIBRATE
        PrayerAlertMode.SHORT_TONE -> CHANNEL_PRAYER_SOUND
        PrayerAlertMode.VIBRATE_ONLY -> CHANNEL_PRAYER_VIBRATE
        PrayerAlertMode.SILENT, PrayerAlertMode.OFF -> CHANNEL_PRAYER_SILENT
    }

    /** True when the user silenced any prayer channel in Android settings. */
    fun prayerChannelBlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return listOf(CHANNEL_PRAYER_SOUND, CHANNEL_PRAYER_VIBRATE, CHANNEL_PRAYER_SILENT)
            .all { id ->
                manager.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
            }
    }

    /** Removes the single prayer reminder card (used by Mark as Prayed / swipe dismissal). */
    fun cancelPrayerNotification(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(AdhanPlaybackService.NOTIFICATION_ID)
        }
    }

    private fun standardNotificationSound(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI

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

        val notificationAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val soundChannel = NotificationChannel(
            CHANNEL_PRAYER_SOUND,
            context.getString(R.string.channel_prayer_sound_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_PRAYER
            description = context.getString(R.string.channel_prayer_sound_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            setSound(standardNotificationSound(), notificationAudioAttributes)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val vibrateChannel = NotificationChannel(
            CHANNEL_PRAYER_VIBRATE,
            context.getString(R.string.channel_prayer_vibrate_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_PRAYER
            description = context.getString(R.string.channel_prayer_vibrate_desc)
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val silentChannel = NotificationChannel(
            CHANNEL_PRAYER_SILENT,
            context.getString(R.string.channel_prayer_silent_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            group = GROUP_PRAYER
            description = context.getString(R.string.channel_prayer_silent_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
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
            listOf(soundChannel, vibrateChannel, silentChannel, safarChannel, habitChannel)
        )

        // Old prayer channels carried alarm-type sound/vibration, which Android will not let
        // us change. Deleting them drops the stale entries from the user's channel list.
        runCatching {
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_SALAH_ADHAN)
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_SALAH_SILENT)
        }
    }
}
