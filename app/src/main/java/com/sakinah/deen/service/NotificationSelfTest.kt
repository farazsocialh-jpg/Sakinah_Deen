package com.sakinah.deen.service

import androidx.core.net.toUri

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.sakinah.deen.data.prefs.SakinahPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Visual weight of one self-test line. */
enum class SelfTestStatus { OK, WARNING, PROBLEM, INFO }

/** Follow-up the user can trigger straight from a self-test line. */
enum class SelfTestAction {
    REQUEST_NOTIFICATIONS,
    REQUEST_EXACT_ALARMS,
    OPEN_CHANNEL_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_DND_ACCESS,
    OPEN_APP_NOTIFICATION_SETTINGS
}

data class SelfTestRow(
    val id: String,
    val title: String,
    val status: SelfTestStatus,
    val detail: String,
    val action: SelfTestAction? = null,
    val actionLabel: String? = null
)

/**
 * Live, honest state of everything an Adhan needs in order to fire.
 *
 * The audit's #28–#31 finding was that the UI reported success without checking the platform.
 * Every line here is read from the real system services at the moment the card is rendered, so
 * "next alarm" only appears when [SakinahPreferences.nextAlarmAtMillis] was actually armed, and
 * a silenced channel, Doze restriction or missing permission is shown as a warning with a
 * one-tap route to the exact settings page that fixes it.
 */
object NotificationSelfTest {

    // Resolved per use so a language change in Settings is reflected without a restart.
    private val ALARM_TIME_FORMAT: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.getDefault())

    fun rows(
        context: Context,
        prefs: SakinahPreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): List<SelfTestRow> {
        val zone = runCatching { AzaanScheduling.activeZoneId(prefs) }
            .getOrDefault(ZoneId.systemDefault())
        val settings = runCatching { prefs.azaanSettings }
            .getOrDefault(com.sakinah.deen.data.model.AzaanSettings())

        return listOf(
            notificationsRow(context),
            exactAlarmRow(context),
            channelRow(context, settings.criticalFajrChannel),
            dndRow(context, settings.criticalFajrChannel),
            batteryRow(context),
            nextAlarmRow(prefs, settings.enabled, zone, nowMillis)
        )
    }

    private fun notificationsRow(context: Context): SelfTestRow {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            SelfTestRow(
                id = "notifications",
                title = "Notification permission",
                status = SelfTestStatus.OK,
                detail = "Granted."
            )
        } else {
            SelfTestRow(
                id = "notifications",
                title = "Notification permission",
                status = SelfTestStatus.PROBLEM,
                detail = "Blocked — no Adhan card can be posted.",
                action = SelfTestAction.REQUEST_NOTIFICATIONS,
                actionLabel = "Allow"
            )
        }
    }

    private fun exactAlarmRow(context: Context): SelfTestRow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return SelfTestRow(
                id = "exact_alarms",
                title = "Exact alarms",
                status = SelfTestStatus.OK,
                detail = "Not required on this Android version (exact alarms are always allowed)."
            )
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val allowed = runCatching { manager?.canScheduleExactAlarms() == true }.getOrDefault(false)
        return if (allowed) {
            SelfTestRow(
                id = "exact_alarms",
                title = "Exact alarms",
                status = SelfTestStatus.OK,
                detail = "Alarms and reminders are allowed — timing can be exact."
            )
        } else {
            SelfTestRow(
                id = "exact_alarms",
                title = "Exact alarms",
                status = SelfTestStatus.WARNING,
                detail = "Denied — reminders are armed as inexact alarms and can arrive late.",
                action = SelfTestAction.REQUEST_EXACT_ALARMS,
                actionLabel = "Allow"
            )
        }
    }

    private fun channelRow(context: Context, criticalFajr: Boolean): SelfTestRow {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager == null) {
            return SelfTestRow(
                id = "channel",
                title = "Prayer channel",
                status = SelfTestStatus.PROBLEM,
                detail = "Notification service unavailable on this device."
            )
        }
        val ids = if (criticalFajr) {
            NotificationChannels.prayerChannelIds()
        } else {
            listOf(
                NotificationChannels.CHANNEL_PRAYER_SOUND,
                NotificationChannels.CHANNEL_PRAYER_VIBRATE,
                NotificationChannels.CHANNEL_PRAYER_SILENT
            )
        }
        val channels = ids.mapNotNull { id -> manager.getNotificationChannel(id) }
        val silenced = channels.filter { it.importance == NotificationManager.IMPORTANCE_NONE }
        if (channels.isEmpty()) {
            return SelfTestRow(
                id = "channel",
                title = "Prayer channel",
                status = SelfTestStatus.INFO,
                detail = "Channels are created on the first start; reload this screen after an alert."
            )
        }
        val detail = buildString {
            append(channels.joinToString(", ") { channel -> "${channelLabel(channel)}: ${importanceLabel(channel.importance)}" })
            val withSound = channels.count { it.sound != null }
            append(if (withSound > 0) " · $withSound with sound" else " · all channels silent")
        }
        return when {
            silenced.size == channels.size -> SelfTestRow(
                id = "channel",
                title = "Prayer channel",
                status = SelfTestStatus.PROBLEM,
                detail = "Every prayer channel is silenced in Android settings — Adhan cannot ring.",
                action = SelfTestAction.OPEN_CHANNEL_SETTINGS,
                actionLabel = "Open"
            )

            silenced.isNotEmpty() -> SelfTestRow(
                id = "channel",
                title = "Prayer channel",
                status = SelfTestStatus.WARNING,
                detail = "$detail · ${silenced.size} silenced",
                action = SelfTestAction.OPEN_CHANNEL_SETTINGS,
                actionLabel = "Open"
            )

            else -> SelfTestRow(
                id = "channel",
                title = "Prayer channel",
                status = SelfTestStatus.OK,
                detail = detail,
                action = SelfTestAction.OPEN_CHANNEL_SETTINGS,
                actionLabel = "Open"
            )
        }
    }

    private fun channelLabel(channel: NotificationChannel): String {
        val name = channel.name?.toString().orEmpty()
        return when {
            name.contains("sound", ignoreCase = true) -> "Sound"
            name.contains("vibrate", ignoreCase = true) -> "Vibrate"
            name.contains("silent", ignoreCase = true) -> "Silent"
            name.contains("fajr", ignoreCase = true) -> "Fajr"
            name.isNotBlank() -> name
            else -> channel.id
        }
    }

    private fun importanceLabel(importance: Int): String = when (importance) {
        NotificationManager.IMPORTANCE_NONE -> "blocked"
        NotificationManager.IMPORTANCE_MIN -> "min"
        NotificationManager.IMPORTANCE_LOW -> "low"
        NotificationManager.IMPORTANCE_DEFAULT -> "default"
        NotificationManager.IMPORTANCE_HIGH -> "high"
        else -> "importance $importance"
    }

    private fun dndRow(context: Context, criticalFajr: Boolean): SelfTestRow {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val filter = runCatching {
            notificationManager?.currentInterruptionFilter
        }.getOrNull()
        val ringer = runCatching { audioManager?.ringerMode }.getOrNull()
        val dndOn = filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL
        val ringerText = when (ringer) {
            AudioManager.RINGER_MODE_SILENT -> "ringer silent"
            AudioManager.RINGER_MODE_VIBRATE -> "ringer vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "ringer normal"
            else -> "ringer unknown"
        }
        val filterText = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "Do Not Disturb off"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Do Not Disturb: priority only"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Do Not Disturb: alarms only"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "Do Not Disturb: total silence"
            else -> "Do Not Disturb state unknown"
        }
        val policyAccess = NotificationChannels.hasDndPolicyAccess(context)
        val critical = if (criticalFajr) {
            if (policyAccess) {
                "Critical Fajr channel is on and may override DND."
            } else {
                "Critical Fajr channel is on, but DND still applies until you allow notification policy access."
            }
        } else {
            "Critical Fajr channel is off; DND and silent mode will silence reminders."
        }
        return when {
            !dndOn -> SelfTestRow(
                id = "dnd",
                title = "Do Not Disturb / volume",
                status = SelfTestStatus.OK,
                detail = "$filterText · $ringerText · $critical"
            )

            criticalFajr && policyAccess -> SelfTestRow(
                id = "dnd",
                title = "Do Not Disturb / volume",
                status = SelfTestStatus.WARNING,
                detail = "$filterText · $ringerText · $critical"
            )

            else -> SelfTestRow(
                id = "dnd",
                title = "Do Not Disturb / volume",
                status = SelfTestStatus.PROBLEM,
                detail = "$filterText · $ringerText · $critical",
                action = if (criticalFajr && !policyAccess) SelfTestAction.OPEN_DND_ACCESS else null,
                actionLabel = if (criticalFajr && !policyAccess) "Allow DND override" else null
            )
        }
    }

    private fun batteryRow(context: Context): SelfTestRow {
        val optimized = BatteryOptimizationGuidance.isBatteryOptimizationActive(context)
        val oem = BatteryOptimizationGuidance.oemLabel()
        val detail = BatteryOptimizationGuidance.guidanceText(context)
        return SelfTestRow(
            id = "battery",
            title = if (oem != null) "Battery & auto-start ($oem)" else "Battery optimisation",
            status = if (optimized || oem != null) SelfTestStatus.WARNING else SelfTestStatus.OK,
            detail = detail,
            action = if (optimized || oem != null) SelfTestAction.OPEN_BATTERY_SETTINGS else null,
            actionLabel = if (optimized || oem != null) "Fix" else null
        )
    }

    private fun nextAlarmRow(
        prefs: SakinahPreferences,
        alarmsEnabled: Boolean,
        zone: ZoneId,
        nowMillis: Long
    ): SelfTestRow {
        val nextAt = prefs.nextAlarmAtMillis
        val prayer = prefs.nextAlarmPrayer
            .lowercase()
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Prayer" }
        val lastCode = prefs.lastAlarmScheduleResult
        if (nextAt <= 0L) {
            // The next prayer time stays visible in-app even when nothing is armed (#34).
            val reason = when (lastCode) {
                AlarmScheduleResult.CODE_PERMISSION_MISSING ->
                    "Notifications are blocked, so no alarm is armed."

                AlarmScheduleResult.CODE_EXACT_DENIED ->
                    "Only an inexact alarm could be armed."

                AlarmScheduleResult.CODE_FAILED ->
                    "The last scheduling attempt failed."

                else -> "No alarm is currently armed."
            }
            return SelfTestRow(
                id = "next_alarm",
                title = "Next scheduled alarm",
                status = if (alarmsEnabled) SelfTestStatus.WARNING else SelfTestStatus.INFO,
                detail = "$reason Prayer times stay visible on the Salah tab."
            )
        }
        val formatted = runCatching {
            Instant.ofEpochMilli(nextAt).atZone(zone).format(ALARM_TIME_FORMAT)
        }.getOrDefault("")
        val overdue = nextAt <= nowMillis
        return SelfTestRow(
            id = "next_alarm",
            title = "Next scheduled alarm",
            status = if (overdue) SelfTestStatus.WARNING else SelfTestStatus.OK,
            detail = if (overdue) {
                "$prayer at $formatted is in the past — the table is being re-armed."
            } else {
                "$prayer at $formatted ($zone)."
            }
        )
    }

    /** Launches the settings page that fixes [action]. Returns false when nothing opened. */
    fun perform(context: Context, action: SelfTestAction): Boolean {
        val appContext = context.applicationContext
        val intent: Intent? = when (action) {
            SelfTestAction.REQUEST_EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    "package:${appContext.packageName}".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                null
            }

            SelfTestAction.OPEN_CHANNEL_SETTINGS -> Intent(
                Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            ).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.CHANNEL_PRAYER_SOUND)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            SelfTestAction.OPEN_APP_NOTIFICATION_SETTINGS -> Intent(
                Settings.ACTION_APP_NOTIFICATION_SETTINGS
            ).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            SelfTestAction.OPEN_DND_ACCESS -> Intent(
                Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            SelfTestAction.OPEN_BATTERY_SETTINGS ->
                return BatteryOptimizationGuidance.openBestSettings(appContext)

            // Runtime permission requests are owned by the Compose launcher.
            SelfTestAction.REQUEST_NOTIFICATIONS -> null
        }
        if (intent == null) return false
        return runCatching { appContext.startActivity(intent) }.isSuccess
    }
}
