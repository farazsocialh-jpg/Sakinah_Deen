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
