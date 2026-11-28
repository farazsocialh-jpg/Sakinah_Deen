package com.sakinah.deen.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import com.sakinah.deen.data.model.PrayerType

/**
 * PendingIntent factory for the date-stamped urgent reminders.
 *
 * Kept separate from [AlarmPendingIntents] because these alarms deliberately use an offset
 * request code and a date in their identity. Both arming and cancellation go through here, so
 * the same PendingIntent is rebuilt byte-identically and no slot can be orphaned. A tiny registry
 * records the armed (prayer, day) keys, which cannot be derived from the prayer name alone.
 */
internal object UrgentReminderPendingIntents {

    private const val REGISTRY_PREFS = "sakinah_urgent_alarm_registry"
    private const val KEY_OUTSTANDING = "outstanding_urgent_keys"

    private fun intentFor(context: Context, prayer: PrayerType, epochDay: Long): Intent =
        Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmScheduler.ACTION_PRAYER_ALARM
            data = AlarmIdentity.uri(UrgentReminderAlarmIdentity.key(prayer, epochDay)).toUri()
        }

    /** Creates (or refreshes) the nudge PendingIntent for one (prayer, day) slot. */
    fun create(
        context: Context,
        prayer: PrayerType,
        epochDay: Long,
        fill: (Intent) -> Unit
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        UrgentReminderAlarmIdentity.requestCode(prayer, epochDay),
        intentFor(context, prayer, epochDay).apply(fill),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** The already-armed PendingIntent, without creating a new one. */
    fun existing(context: Context, prayer: PrayerType, epochDay: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            UrgentReminderAlarmIdentity.requestCode(prayer, epochDay),
            intentFor(context, prayer, epochDay),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    private fun registry(context: Context) = context.applicationContext
        .getSharedPreferences(REGISTRY_PREFS, Context.MODE_PRIVATE)

    /** Armed (prayer, day) keys, reused from the shared deterministic key encoding. */
    fun registeredKeys(context: Context): List<String> =
        AlarmIdentity.decode(registry(context).getString(KEY_OUTSTANDING, null))

    fun register(context: Context, key: String) {
        val current = registeredKeys(context)
        if (key in current) return
        registry(context).edit {
            putString(KEY_OUTSTANDING, AlarmIdentity.encode(current + key))
        }
    }

    fun clearRegistry(context: Context) {
        registry(context).edit { remove(KEY_OUTSTANDING) }
    }
}
