package com.sakinah.deen.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sakinah.deen.data.model.AzaanSettings
import com.sakinah.deen.data.model.Muezzin
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.service.AdhanAudio
import com.sakinah.deen.service.AzaanScheduling
import com.sakinah.deen.service.PrayerAlarmScheduler
import com.sakinah.deen.ui.components.SakinahCard
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    previewPlayerFactory: (Context) -> AzaanPreviewController = { AzaanPreviewPlayer(it) }
) {
    val context = LocalContext.current
    val appPrefs = remember(context) { SakinahPreferences(context) }
    val scope = rememberCoroutineScope()

    var azaanSettings by remember { mutableStateOf(appPrefs.azaanSettings) }
    var notificationsGranted by remember { mutableStateOf(notificationsPermissionGranted(context)) }
    var exactAlarmsAllowed by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var testScheduled by remember { mutableStateOf(false) }

    val previewPlayer = remember(context, previewPlayerFactory) { previewPlayerFactory(context) }
    var previewingMuezzin by remember { mutableStateOf<Muezzin?>(null) }
    previewPlayer.onFinished = { previewingMuezzin = null }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(previewPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                previewPlayer.stop()
                previewingMuezzin = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
        appPrefs.azaanSettings = updated
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
                        text = "Azaan & Prayer Notifications",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Exact alarm-clock reminders that survive Doze and reboot.",
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
        if (needsNotificationPermission || needsExactAlarmPermission) {
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
                        text = "Permission health",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (needsNotificationPermission) {
                        Text(
                            text = "Notifications are blocked, so Azaan cannot appear.",
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
                            Text("Enable Notifications", fontSize = 12.sp)
                        }
                    }
                    if (needsExactAlarmPermission) {
                        Text(
                            text = "Exact alarms are disabled; reminders may arrive late.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(intent) }
                                }
                                exactAlarmsAllowed = canScheduleExactAlarms(context)
                            }
                        ) {
                            Text("Allow Exact Alarms", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Test notification
        Button(
            onClick = {
                runCatching {
                    PrayerAlarmScheduler(
                        context = context,
                        astronomyEngine = PrayerCalculationEngineImpl(),
                        preferences = appPrefs
                    ).scheduleTestAlarmInSeconds(5)
                }
                testScheduled = true
            },
            enabled = azaanSettings.enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Test Prayer Notification (5s)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        if (testScheduled) {
            Text(
                text = "Test alert scheduled — it should fire in about 5 seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 4. Per-prayer alert mode and offset
        Text("Per-Prayer Alerts", style = MaterialTheme.typography.titleMedium)
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
                        text = "Timing offset",
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

        // 5. Muezzin / Azaan voice picker with a single shared preview player
        Text("Muezzin / Azaan Voice", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Tap a voice to select it and hear a preview. Tapping the active voice again stops it.",
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
                            text = if (selected) "Selected" else "Tap to select & preview",
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
                                // Only ever one preview: stop the current recording before the next.
                                previewPlayer.stop()
                                previewingMuezzin = if (previewPlayer.play(muezzin)) muezzin else null
                            }
                        },
                        label = { Text(if (previewing) "Stop" else "Preview", fontSize = 11.sp) }
                    )
                }
            }
        }

        // 6. Done
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Done")
        }
    }
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

    override fun play(muezzin: Muezzin): Boolean {
        stop()
        return runCatching {
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
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

    override fun stop() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
        }
        player = null
    }

    override fun release() = stop()
}
