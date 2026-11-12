package com.sakinah.deen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl

/**
 * AlarmManager pending intents do not survive a reboot, a manual clock change,
 * or a timezone change. Recompute the astronomical table and re-register every
 * exact alarm whenever the OS reports one of those events.
 */
class BootAndAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> restoreAlarms(context)
        }
    }

    private fun restoreAlarms(context: Context) {
        val preferences = SakinahPreferences(context)
        val coordinates = preferences.locationCoordinates
            ?: Pair(SakinahPreferences.DEFAULT_LATITUDE, SakinahPreferences.DEFAULT_LONGITUDE)

        NotificationChannels.createChannels(context)

        val scheduler = PrayerAlarmScheduler(
            context = context,
            astronomyEngine = PrayerCalculationEngineImpl(),
            preferences = preferences
        )
        scheduler.cancelAllAlarms()
        scheduler.scheduleAllDailyAlarms(coordinates = coordinates)
    }
}
