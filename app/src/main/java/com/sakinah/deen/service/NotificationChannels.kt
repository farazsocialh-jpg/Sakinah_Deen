package com.sakinah.deen.service

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings

object NotificationChannels {

    const val GROUP_PRAYER = "sakinah_prayer_group"
    const val GROUP_TRAVEL = "sakinah_travel_group"
    const val GROUP_HABIT = "sakinah_habit_group"

    const val CHANNEL_SALAH_ADHAN = "salah_adhan_channel"
    const val CHANNEL_SALAH_SILENT = "salah_silent_channel"
    const val CHANNEL_TRAVEL_SAFAR = "travel_safar_channel"
    const val CHANNEL_HABIT_REMINDERS = "habit_reminders_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(GROUP_PRAYER, "Prayer & Salah Alerts"),
                NotificationChannelGroup(GROUP_TRAVEL, "Travel & Mobility (Safar)"),
                NotificationChannelGroup(GROUP_HABIT, "Habits & Adhkar")
            )
        )

        // 1. Salah Adhan: HIGH importance, audible, lock-screen visible.
        // The bundled raw Adhan is optional; fall back to the system alarm tone so the
        // channel is never silent just because res/raw/adhan_call is absent.
        val adhanSoundUri = resolveRawSound(context, "adhan_call")
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

        val adhanChannel = NotificationChannel(
            CHANNEL_SALAH_ADHAN,
            "Adhan & Exact Salah Call",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_PRAYER
            description = "High-priority Adhan reminders at astronomical prayer entry times"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(
                adhanSoundUri,
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        // 2. Silent countdown channel
        val silentChannel = NotificationChannel(
            CHANNEL_SALAH_SILENT,
            "Prayer Countdown (Ongoing)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_PRAYER
            description = "Subtle, non-audible ongoing countdown to the next prayer"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        // 3. Travel Safar: heads-up requires IMPORTANCE_HIGH. DEFAULT cannot produce a
        // heads-up banner on Android 8+, so HIGH is used deliberately here.
        val safarChannel = NotificationChannel(
            CHANNEL_TRAVEL_SAFAR,
            "Travel Mode & Dua al-Safar",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_TRAVEL
            description = "Heads-up in-vehicle prompts on car Bluetooth connection and Qasr travel mode"
            enableVibration(true)
            setShowBadge(true)
        }

        // 4. Habit reminders
        val habitChannel = NotificationChannel(
            CHANNEL_HABIT_REMINDERS,
            "Daily Sunnah & Adhkar Habits",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_HABIT
            description = "Morning/Evening Adhkar, Surah Al-Kahf on Fridays, and Sunnah fasting"
            setShowBadge(true)
            enableVibration(false)
        }

        notificationManager.createNotificationChannels(
            listOf(adhanChannel, silentChannel, safarChannel, habitChannel)
        )
    }

    /** Resolves a raw resource by name without generating an R reference. */
    fun resolveRawSound(context: Context, resourceName: String): Uri? {
        val resId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        return if (resId != 0) {
            Uri.parse("android.resource://${context.packageName}/$resId")
        } else {
            null
        }
    }
}
