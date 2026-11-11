package com.sakinah.deen.service

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build

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

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel Groups
        val prayerGroup = NotificationChannelGroup(GROUP_PRAYER, "Prayer & Salah Alerts")
        val travelGroup = NotificationChannelGroup(GROUP_TRAVEL, "Travel & Mobility (Safar)")
        val habitGroup = NotificationChannelGroup(GROUP_HABIT, "Habits & Adhkar")
        notificationManager.createNotificationChannelGroups(listOf(prayerGroup, travelGroup, habitGroup))

        // 1. Salah Adhan Channel: High importance, sound, vibration, bypass DND configured
        val adhanChannel = NotificationChannel(
            CHANNEL_SALAH_ADHAN,
            "Adhan & Exact Salah Call",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = GROUP_PRAYER
            description = "High-priority Adhan reminders and full-screen prayer calls at astronomical entry times"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(
                Uri.parse("android.resource://${context.packageName}/raw/adhan_call"),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        // 2. Salah Silent Channel: Low importance, ongoing countdown badges
        val silentChannel = NotificationChannel(
            CHANNEL_SALAH_SILENT,
            "Prayer Countdown (Ongoing)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_PRAYER
            description = "Subtle, non-audible ongoing countdown to next prayer"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        // 3. Travel Safar Channel: Default importance, heads-up prompt for Dua al-Safar
        val safarChannel = NotificationChannel(
            CHANNEL_TRAVEL_SAFAR,
            "Travel Mode & Dua al-Safar",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            group = GROUP_TRAVEL
            description = "Heads-up vehicular alerts upon car Bluetooth connection or 80 km travel threshold exit"
            enableVibration(true)
            setShowBadge(true)
        }

        // 4. Habit Reminders Channel: Low priority daily prompts
        val habitChannel = NotificationChannel(
            CHANNEL_HABIT_REMINDERS,
            "Daily Sunnah & Adhkar Habits",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            group = GROUP_HABIT
            description = "Reminders for Morning/Evening Adhkar, Surah Al-Kahf on Fridays, and Sunnah Fasting"
            setShowBadge(true)
            enableVibration(false)
        }

        notificationManager.createNotificationChannels(
            listOf(adhanChannel, silentChannel, safarChannel, habitChannel)
        )
    }
}
