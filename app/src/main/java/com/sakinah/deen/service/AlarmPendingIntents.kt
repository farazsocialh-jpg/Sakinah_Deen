package com.sakinah.deen.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Single factory for every prayer-alarm [PendingIntent].
 *
 * Scheduling and cancellation both go through [intentFor], so the action, component and data
 * URI are guaranteed to be identical. That is what makes [cancel] able to find an armed alarm
 * even after the app's code changes: the identity is derived from the key, not from a
 * hand-maintained integer table.
 *
 * One-off alarms (test/snooze) are also recorded in a small registry, because their identities
 * are unique per firing and therefore cannot be rebuilt from the prayer name alone.
 */
internal object AlarmPendingIntents {

    private const val REGISTRY_PREFS = "sakinah_alarm_registry"
    private const val KEY_OUTSTANDING = "outstanding_alarm_keys"

    /** How long a fired one-off key is kept so a late cancel can still find it. */
    private const val REGISTRY_GRACE_MS = 24 * 60 * 60_000L

    private fun intentFor(context: Context, key: String): Intent =
        Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmScheduler.ACTION_PRAYER_ALARM
            data = Uri.parse(AlarmIdentity.uri(key))
        }

    /** Creates (or updates) the PendingIntent for [key], applying [fill] to a fresh Intent. */
    fun create(
        context: Context,
        key: String,
        fill: (Intent) -> Unit
    ): PendingIntent {
        val intent = intentFor(context, key).apply(fill)
        return PendingIntent.getBroadcast(
            context,
            AlarmIdentity.requestCode(key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The already-armed PendingIntent for [key], or null when nothing is registered. */
    fun existing(context: Context, key: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            AlarmIdentity.requestCode(key),
            intentFor(context, key),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    private fun registry(context: Context) = context.applicationContext
        .getSharedPreferences(REGISTRY_PREFS, Context.MODE_PRIVATE)

    /** Outstanding one-off keys, with already-fired entries pruned. */
    fun registeredKeys(context: Context, nowMillis: Long = System.currentTimeMillis()): List<String> {
        val raw = registry(context).getString(KEY_OUTSTANDING, null)
        val keys = AlarmIdentity.decode(raw)
        val live = AlarmIdentity.pruneExpired(keys, nowMillis, REGISTRY_GRACE_MS)
        if (live.size != keys.size) write(context, live)
        return live
    }

    fun register(context: Context, key: String) {
        val current = registeredKeys(context)
        if (key in current) return
        write(context, current + key)
    }

    fun unregister(context: Context, key: String) {
        val current = registeredKeys(context)
        if (key !in current) return
        write(context, current - key)
    }

    fun clearRegistry(context: Context) {
        registry(context).edit().remove(KEY_OUTSTANDING).apply()
    }

    private fun write(context: Context, keys: Collection<String>) {
        registry(context).edit()
            .putString(KEY_OUTSTANDING, AlarmIdentity.encode(keys))
            .apply()
    }
}
