package com.sakinah.deen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AlarmManager pending intents do not survive a reboot, a manual clock change,
 * or a timezone change. Recompute the astronomical table and re-register every
 * exact alarm whenever the OS reports one of those events.
 */
class BootAndAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        // Alarm rescheduling touches SharedPreferences and AlarmManager; keep it off the
        // main broadcast thread while the receiver keeps its process alive.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                restoreAlarms(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun restoreAlarms(context: Context) {
        val preferences = SakinahPreferences(context)
        NotificationChannels.createChannels(context)

        val scheduler = PrayerAlarmScheduler(
            context = context,
            astronomyEngine = PrayerCalculationEngineImpl(),
            preferences = preferences
        )

        if (!preferences.alarmsEnabled) {
            scheduler.cancelAllAlarms()
            return
        }

        // Never silently substitute Makkah (or another city) after a reboot: use the
        // location the user selected. If none is saved yet, the app will schedule after
        // onboarding/location resolution instead of firing alarms for the wrong place.
        val coordinates = preferences.locationCoordinates ?: return
        scheduler.scheduleAllDailyAlarms(
            coordinates = coordinates,
            method = preferences.calculationMethodOverride,
            madhhab = preferences.madhhab
        )
    }
}
