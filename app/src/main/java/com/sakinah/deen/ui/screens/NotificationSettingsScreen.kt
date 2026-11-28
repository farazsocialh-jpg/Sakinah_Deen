package com.sakinah.deen.ui.screens

import com.sakinah.deen.R

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sakinah.deen.data.model.AzaanSettings
import com.sakinah.deen.data.model.Muezzin
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.service.AdhanAudio
import com.sakinah.deen.service.AlarmScheduleResult
import com.sakinah.deen.service.AzaanScheduling
import com.sakinah.deen.service.BatteryOptimizationGuidance
import com.sakinah.deen.service.NotificationChannels
import com.sakinah.deen.service.NotificationSelfTest
import com.sakinah.deen.service.PrayerAlertSilencer
import com.sakinah.deen.service.PrayerAlarmScheduler
import com.sakinah.deen.service.SelfTestAction
import com.sakinah.deen.service.SelfTestRow
import com.sakinah.deen.service.SelfTestStatus
import com.sakinah.deen.ui.components.SakinahCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Dedicated Azaan & notification centre.
 *
 * Everything here writes straight to [SakinahPreferences] and immediately re-arms the exact
 * alarm table, so toggling a prayer never leaves a stale alarm behind. Muezzin audition uses
 * a single [MediaPlayer] that is stopped before the next preview and released whenever the
 * screen is disposed or the app is paused/stopped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsScreen(
    modifier: Modifier = Modifier,
    previewPlayerFactory: (Context) -> AzaanPreviewController = { AzaanPreviewPlayer(it) }
) {
    val context = LocalContext.current
    val appPrefs = remember(context) { SakinahPreferences(context) }
    val scope = rememberCoroutineScope()

    // Corrupt/legacy preference types must not take the whole screen (and the app) down
    // when the prayer bell is opened.
    var azaanSettings by remember {
        mutableStateOf(runCatching { appPrefs.azaanSettings }.getOrDefault(AzaanSettings()))
    }
    var notificationsGranted by remember { mutableStateOf(notificationsPermissionGranted(context)) }
    var exactAlarmsAllowed by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var batteryOptimized by remember { mutableStateOf(isBatteryOptimizationActive(context)) }
    var azaanChannelBlocked by remember { mutableStateOf(isAzaanChannelBlocked(context)) }
    // Truthful outcome of the last test-alert request; null until the user asks for one.
    var testResult by remember { mutableStateOf<AlarmScheduleResult?>(null) }
    val selfTestRows = remember { mutableStateOf(emptyList<SelfTestRow>()) }
    var nextAlarmText by remember { mutableStateOf(describeNextAlarm(appPrefs)) }
    var expandedSection by remember { mutableStateOf<String?>(null) }

    fun refreshSelfTest() {
        runCatching { NotificationSelfTest.rows(context, appPrefs) }
            .onSuccess { selfTestRows.value = it }
        nextAlarmText = describeNextAlarm(appPrefs)
    }

    // previewPlayerFactory is a lambda supplied by callers/tests and is a new instance on
    // every recomposition; keying on it would tear down and recreate the MediaPlayer.
    val previewPlayer = remember(context) { previewPlayerFactory(context) }
    var previewingMuezzin by remember { mutableStateOf<Muezzin?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(previewPlayer, lifecycleOwner, context) {
        previewPlayer.onFinished = { previewingMuezzin = null }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    notificationsGranted = notificationsPermissionGranted(context)
                    exactAlarmsAllowed = canScheduleExactAlarms(context)
                    batteryOptimized = isBatteryOptimizationActive(context)
                    azaanChannelBlocked = isAzaanChannelBlocked(context)
                    // Coming back from a settings page must show the new state immediately.
                    runCatching { NotificationSelfTest.rows(context, appPrefs) }
                        .onSuccess { selfTestRows.value = it }
                    nextAlarmText = describeNextAlarm(appPrefs)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    previewPlayer.stop()
                    previewingMuezzin = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        runCatching { NotificationSelfTest.rows(context, appPrefs) }
            .onSuccess { selfTestRows.value = it }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            previewPlayer.onFinished = null
            previewPlayer.release()
            previewingMuezzin = null
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (granted) {
            scope.launch { runCatching { AzaanScheduling.scheduleWithBestLocation(context) } }
        }
    }

    fun applySettings(updated: AzaanSettings) {
        azaanSettings = updated
        runCatching { appPrefs.azaanSettings = updated }
        runCatching { AzaanScheduling.scheduleStoredLocation(context) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Master switch
        SakinahCard(contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notif_prayer_notifications),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.notif_exact_alarm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = azaanSettings.enabled,
                    onCheckedChange = { enabled -> applySettings(azaanSettings.copy(enabled = enabled)) }
                )
            }
        }

        
// 2. Permission health
        val needsNotificationPermission = !notificationsGranted
        val needsExactAlarmPermission = !exactAlarmsAllowed
        if (needsNotificationPermission || needsExactAlarmPermission || azaanChannelBlocked) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notif_permission_health),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (needsNotificationPermission) {
                        Text(
                            text = stringResource(R.string.notif_blocked),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notificationsGranted = notificationsPermissionGranted(context)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.notif_enable_notifications), fontSize = 12.sp)
                        }
                    }
                    if (azaanChannelBlocked) {
                        Text(
                            text = stringResource(R.string.notif_channel_silenced),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.CHANNEL_PRAYER_SOUND)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        ) { Text(stringResource(R.string.notif_open_channel_settings), fontSize = 12.sp) }
                    }
                    if (needsExactAlarmPermission) {
                        Text(
                            text = stringResource(R.string.notif_exact_disabled),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        "package:${context.packageName}".toUri()
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(intent) }
                                }
                                exactAlarmsAllowed = canScheduleExactAlarms(context)
                            }
                        ) {
                            Text(stringResource(R.string.notif_allow_exact_alarms), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        
        // 2. Per-Prayer Alerts (collapsible)
        AppearanceAccordion(
            title = stringResource(R.string.notif_per_prayer_alerts),
            summary = if (azaanSettings.enabled) stringResource(R.string.notif_customized) else stringResource(R.string.notif_disabled),
            expanded = expandedSection == "perprayer",
            onToggle = { expandedSection = if (expandedSection == "perprayer") null else "perprayer" }
        ) {
// 4. Per-prayer alert mode and offset
        Text(stringResource(R.string.notif_per_prayer_alerts), style = MaterialTheme.typography.titleMedium)
        AzaanSettings.ALERT_PRAYERS.forEach { prayer ->
            val mode = azaanSettings.modeFor(prayer)
            val offset = azaanSettings.offsetFor(prayer)
            SakinahCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = prayer.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrayerAlertMode.entries.forEach { option ->
                            FilterChip(
                                selected = mode == option,
                                enabled = azaanSettings.enabled,
                                onClick = {
                                    applySettings(
                                        azaanSettings.copy(
                                            modes = azaanSettings.modes + (prayer to option)
                                        )
                                    )
                                },
                                label = { Text(option.label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.notif_timing_offset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AzaanSettings.OFFSET_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = offset == option,
                                enabled = azaanSettings.enabled,
                                onClick = {
                                    applySettings(
                                        azaanSettings.copy(
                                            offsetsMinutes = azaanSettings.offsetsMinutes + (prayer to option)
                                        )
                                    )
                                },
                                label = { Text(notificationOffsetLabel(option), fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        
// 4b. Special Fajr Azaan — moved here so every Azaan option lives together.
        SakinahCard(contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notif_special_fajr),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.notif_fajr_includes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = azaanSettings.fajrSpecialAdhan,
                    enabled = azaanSettings.enabled,
                    onCheckedChange = { enabled ->
                        applySettings(azaanSettings.copy(fajrSpecialAdhan = enabled))
                    }
                )
            }
        }

        
// 4c. Optional critical Fajr channel, with the real Android limits spelled out.
        SakinahCard(contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notif_critical_fajr),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.notif_critical_fajr_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = azaanSettings.criticalFajrChannel,
                    enabled = azaanSettings.enabled,
                    onCheckedChange = { enabled ->
                        applySettings(azaanSettings.copy(criticalFajrChannel = enabled))
                        runCatching { NotificationChannels.createChannels(context) }
                        refreshSelfTest()
                    }
                )
            }
            if (azaanSettings.criticalFajrChannel) {
                Text(
                    text = if (NotificationChannels.hasDndPolicyAccess(context)) {
                        stringResource(R.string.notif_dnd_granted)
                    } else {
                        stringResource(R.string.notif_dnd_not_granted)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        
        }

        // 3. Muezzin Voice (collapsible)
        AppearanceAccordion(
            title = stringResource(R.string.notif_muezzin_voice),
            summary = notificationMuezzinLabel(azaanSettings.muezzin),
            expanded = expandedSection == "muezzin",
            onToggle = { expandedSection = if (expandedSection == "muezzin") null else "muezzin" }
        ) {
// 5. Muezzin / Azaan voice picker with a single shared preview player
        Text(stringResource(R.string.notif_muezzin_voice), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.notif_muezzin_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Muezzin.entries.forEach { muezzin ->
            val selected = azaanSettings.muezzin == muezzin
            val previewing = previewingMuezzin == muezzin
            SakinahCard(contentPadding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notificationMuezzinLabel(muezzin),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selected) stringResource(R.string.notif_selected) else stringResource(R.string.notif_tap_to_select),
                            fontSize = 11.sp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    FilterChip(
                        selected = previewing,
                        onClick = {
                            // Selecting a new voice immediately stops any current preview.
                            applySettings(azaanSettings.copy(muezzin = muezzin))
                            if (previewing) {
                                previewPlayer.stop()
                                previewingMuezzin = null
                            } else {
                                // Only ever one preview: stop the current recording before the next,
                                // but only when something is actually previewing, so a first tap
                                // does not record a spurious stop.
                                if (previewingMuezzin != null) previewPlayer.stop()
                                previewingMuezzin = if (previewPlayer.play(muezzin)) muezzin else null
                            }
                        },
                        label = { Text(if (previewing) stringResource(R.string.notif_stop) else stringResource(R.string.notif_preview), fontSize = 11.sp) }
                    )
                }
            }
        }
        }

        // 4. Alarm Health (collapsible, at end)
        AppearanceAccordion(
            title = stringResource(R.string.notif_alarm_health),
            summary = if (selfTestRows.value.any { it.status != SelfTestStatus.OK }) stringResource(R.string.notif_issues) else stringResource(R.string.notif_all_good),
            expanded = expandedSection == "health",
            onToggle = { expandedSection = if (expandedSection == "health") null else "health" },
            leading = if (selfTestRows.value.any { it.status != SelfTestStatus.OK }) {
                { androidx.compose.material3.Icon(Icons.Default.Warning, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFFFA000)) }
            } else null
        ) {
// 2b. Reliability health: a real location and unrestricted battery are required
        // for alarms to be both scheduled and delivered on time.
        val savedCoordinates = appPrefs.locationCoordinates
        SakinahCard(contentPadding = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.notif_reminder_reliability),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (savedCoordinates == null) {
                        stringResource(R.string.notif_no_location)
                    } else {
                        "Location: ${appPrefs.locationLabel} · ${appPrefs.locationTimeZoneId ?: "zone pending"}"
                    },
                    fontSize = 12.sp,
                    color = if (savedCoordinates == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                // The next prayer stays visible in-app even when notifications are blocked.
                Text(
                    text = nextAlarmText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = BatteryOptimizationGuidance.guidanceText(context),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val oemLabel = BatteryOptimizationGuidance.oemLabel()
                if (batteryOptimized || oemLabel != null) {
                    OutlinedButton(
                        onClick = { BatteryOptimizationGuidance.openBestSettings(context) }
                    ) {
                        Text(
                            text = if (oemLabel != null) "Open $oemLabel battery settings" else stringResource(R.string.notif_battery_settings),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        
// 3. Notification & Alarm Self-Test (live system state, never a cached guess)
        SakinahCard(contentPadding = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.notif_self_test),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.notif_self_test_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { refreshSelfTest() }) {
                        Text(stringResource(R.string.notif_refresh), fontSize = 11.sp)
                    }
                }
                selfTestRows.value.forEach { row ->
                    SelfTestLine(
                        row = row,
                        onAction = { action ->
                            when (action) {
                                SelfTestAction.REQUEST_NOTIFICATIONS -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notificationsGranted = notificationsPermissionGranted(context)
                                    }
                                }

                                else -> {
                                    NotificationSelfTest.perform(context, action)
                                    refreshSelfTest()
                                }
                            }
                        }
                    )
                }
            }
        }

        
        }

// 4. Test notification — the button reports what Android actually did.
        Button(
            onClick = {
                val scheduler = runCatching {
                    PrayerAlarmScheduler(
                        context = context,
                        astronomyEngine = PrayerCalculationEngineImpl(),
                        preferences = appPrefs
                    )
                }.getOrNull()
                testResult = scheduler?.scheduleTestAlarmInSeconds(5)
                    ?: AlarmScheduleResult.Unsupported
                refreshSelfTest()
            },
            enabled = azaanSettings.enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.notif_test_prayer), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        testResult?.let { result ->
            Text(
                text = alarmResultMessage(result),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = when (result) {
                    is AlarmScheduleResult.Scheduled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }

        
    }

}


/** One self-test line: status dot, title, honest detail and an optional fix button. */
@Composable
private fun SelfTestLine(row: SelfTestRow, onAction: (SelfTestAction) -> Unit) {
    val dotColor = when (row.status) {
        SelfTestStatus.OK -> MaterialTheme.colorScheme.primary
        SelfTestStatus.WARNING -> MaterialTheme.colorScheme.tertiary
        SelfTestStatus.PROBLEM -> MaterialTheme.colorScheme.error
        SelfTestStatus.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = dotColor,
            modifier = Modifier.size(8.dp)
        ) {}
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = row.detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val action = row.action
        val label = row.actionLabel
        if (action != null && label != null) {
            OutlinedButton(onClick = { onAction(action) }) {
                Text(label, fontSize = 11.sp)
            }
        }
    }
}

/** Plain-language description of a real scheduling outcome. */
private fun alarmResultMessage(result: AlarmScheduleResult): String = when (result) {
    is AlarmScheduleResult.Scheduled ->
        "Scheduled and armed with Android — it should fire in about 5 seconds."

    AlarmScheduleResult.ExactAlarmDenied ->
        "Armed, but without exact-alarm permission, so Android may delay it. Allow exact alarms for on-time reminders."

    AlarmScheduleResult.PermissionMissing ->
        "Not scheduled: notifications are blocked. Allow notifications, then try again."

    is AlarmScheduleResult.Failed -> "Not scheduled: ${result.reason.lowercase()}."

    AlarmScheduleResult.Unsupported -> "Not scheduled: this device exposes no alarm service."
}

/** Next armed prayer, or a clear statement that nothing is armed (never a blank line). */
private fun describeNextAlarm(prefs: SakinahPreferences): String {
    val nextAt = prefs.nextAlarmAtMillis
    if (nextAt <= 0L) {
        val code = prefs.lastAlarmScheduleResult
        return when (code) {
            AlarmScheduleResult.CODE_PERMISSION_MISSING -> "Next prayer: not armed — notifications are blocked."
            AlarmScheduleResult.CODE_EXACT_DENIED -> "Next prayer: armed as an inexact alarm (may be late)."
            AlarmScheduleResult.CODE_FAILED -> "Next prayer: not armed — the last scheduling attempt failed."
            else -> "Next prayer: not armed yet. Prayer times remain visible on the Salah tab."
        }
    }
    val zone = runCatching { AzaanScheduling.activeZoneId(prefs) }
        .getOrDefault(ZoneId.systemDefault())
    val formatted = runCatching {
        Instant.ofEpochMilli(nextAt).atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.getDefault()))
    }.getOrDefault("")
    val prayer = prefs.nextAlarmPrayer.lowercase().replaceFirstChar { it.uppercase() }
    return "Next prayer: $prayer at $formatted"
}

private fun notificationsPermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private fun isAzaanChannelBlocked(context: Context): Boolean =
    NotificationChannels.prayerChannelBlocked(context)

private fun isBatteryOptimizationActive(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return !power.isIgnoringBatteryOptimizations(context.packageName)
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return alarmManager.canScheduleExactAlarms()
}

private fun notificationOffsetLabel(minutes: Int): String = when {
    minutes == 0 -> "Exact"
    minutes > 0 -> "+$minutes min"
    else -> "$minutes min"
}

private fun notificationMuezzinLabel(muezzin: Muezzin): String = when (muezzin) {
    Muezzin.TAKBIR_AQSA -> "Fajr Special (Takbir al-Aqsa)"
    Muezzin.TAKBIR_CAIRO -> "Short Chime (Takbir Cairo)"
    else -> muezzin.displayName
}

/**
 * Audition surface for the muezzin picker. Extracted so instrumented tests can verify that
 * only one preview is ever active and that leaving the screen releases the player.
 */
interface AzaanPreviewController {
    var onFinished: (() -> Unit)?
    fun play(muezzin: Muezzin): Boolean
    fun stop()
    fun release()
}

/**
 * One preview player for the whole screen: starting a new preview always stops and releases
 * the previous one, and callers release it on dispose/pause so nothing keeps playing.
 */
internal class AzaanPreviewPlayer(private val context: Context) : AzaanPreviewController {
    override var onFinished: (() -> Unit)? = null
    private var player: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun play(muezzin: Muezzin): Boolean {
        stop()
        // A live Adhan playback service would otherwise overlap the preview: mute it first.
        stopActiveAdhanPlayback()
        if (!requestAudioFocus()) return false
        return runCatching {
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, AdhanAudio.muezzinUri(context, muezzin))
                setOnCompletionListener {
                    stop()
                    onFinished?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onFinished?.invoke()
                    true
                }
                prepare()
                start()
            }
            player = mediaPlayer
            true
        }.getOrElse {
            stop()
            false
        }
    }

    private fun stopActiveAdhanPlayback() {
        runCatching { PrayerAlertSilencer.stopAzaan(context) }
    }

    /** Transient focus so other audio ducks instead of playing over the preview. */
    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && player != null) {
                    runCatching { stop() }
                }
            }
            .build()
        val granted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) audioFocusRequest = request
        return granted
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { request ->
            runCatching { audioManager?.abandonAudioFocusRequest(request) }
        }
        audioFocusRequest = null
    }

    override fun stop() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
        }
        player = null
        abandonAudioFocus()
    }

    override fun release() = stop()
}
