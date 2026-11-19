package com.sakinah.deen.ui.screens

import com.sakinah.deen.R

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerModel
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
import com.sakinah.deen.ui.components.QiblaModesCard
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.components.StatusBadge
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.AmiriFontFamily
import com.sakinah.deen.ui.viewmodel.SalahUiIntent
import com.sakinah.deen.ui.viewmodel.SalahUiState
import com.sakinah.deen.ui.viewmodel.SalahViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalahScreen(
    onOpenGuide: () -> Unit,
    onOpenPrayerGuide: ((String, Int) -> Unit)? = null,
    focusMode: Boolean = false,
    onOpenNotificationSettings: (() -> Unit)? = null,
    onOpenPrayerSettings: (() -> Unit)? = null,
    viewModel: SalahViewModel? = null,
    modifier: Modifier = Modifier
) {
    val vmState = viewModel?.uiState?.collectAsStateWithLifecycle()?.value
    val state = vmState ?: remember {
        SalahUiState(
            prayersList = listOf(
                PrayerModel("fajr", "Fajr", "الفجر", "05:14 AM", 2, 2, 0, true, true, true),
                PrayerModel("sunrise", "Sunrise", "الشروق", "06:38 AM", 0, 0, 0, false, false, false),
                PrayerModel("dhuhr", "Dhuhr", "الظهر", "12:45 PM", 4, 4, 2, true, true, true, true, "2 Rakat Nafl"),
                PrayerModel("asr", "Asr", "العصر", "03:52 PM", 4, 4, 0, false, false, false),
                PrayerModel("maghrib", "Maghrib", "المغرب", "06:18 PM", 3, 0, 2, false, false, false, false, "Awwabin (6 Rakat)"),
                PrayerModel("isha", "Isha", "العشاء", "07:44 PM", 4, 4, 2, false, false, false, false, "Tahajjud & Witr")
            )
        )
    }

    // Coming back from system settings (permission just granted) or from a flight should not
    // leave the table on the previous permission/zone until a manual pull-to-refresh.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel?.onIntent(SalahUiIntent.SystemStateChanged)
    }

    val appearancePrefs = rememberInject<AppearancePreferences>()
    val appearanceConfig by appearancePrefs.effectiveConfig.collectAsStateWithLifecycle()
    val salahHomeLayout = appearanceConfig.salahHomeLayout

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel?.onIntent(SalahUiIntent.LocationPermissionResult(isGranted))
        // A permission grant alone does not switch a manual city back to GPS.
        if (isGranted) viewModel?.onIntent(SalahUiIntent.UseGpsLocation)
    }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var gpsPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var showGpsSettingsDialog by rememberSaveable { mutableStateOf(false) }

    var selectedSegment by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(focusMode) {
        if (focusMode) {
            selectedSegment = 0
        }
    }
    var showQadhaDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showManualCityDialog by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    // Refresh state lives in the view model: a failed or empty refresh still clears it.

    // Compass sensors now live inside QiblaModesCard: they start only while its Compass mode
    // is on screen and stop on pause, so the Salah tab itself registers no listeners.

    fun launchPrayerGuide(prayerTitle: String, rakats: Int) {
        if (onOpenPrayerGuide != null) {
            onOpenPrayerGuide(prayerTitle, rakats.coerceAtLeast(2))
        } else {
            onOpenGuide()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = {
            if (!state.isRefreshing) viewModel?.onIntent(SalahUiIntent.RefreshLocation)
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Deduplicated Salah header + layout-specific hero
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = if (salahHomeLayout == SalahHomeLayout.SEAMLESS_EDITORIAL) {
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        } else {
                            Modifier
                        },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SalahTopHeader(
                            state = state,
                            focusMode = focusMode,
                            onPickLocation = { showManualCityDialog = true },
                            onOpenNotifications = onOpenNotificationSettings,
                            onOpenSettings = { onOpenPrayerSettings?.invoke() ?: run { showSettingsDialog = true } },
                            onOpenQadha = { showQadhaDialog = true },
                            onRecalculate = { viewModel?.onIntent(SalahUiIntent.RecalculateToday) }
                        )

                        if (!focusMode && state.showLocationWarningBanner) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SalahAmber.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, SalahAmber.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showManualCityDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = SalahAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.salah_enable_location),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (!focusMode && state.showExactAlarmWarningBanner) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SalahAmber.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, SalahAmber.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            val intent = Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.parse("package:${context.packageName}")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            val opened = runCatching { context.startActivity(intent) }.isSuccess
                                            if (!opened) onOpenNotificationSettings?.invoke()
                                        } else {
                                            onOpenNotificationSettings?.invoke()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = SalahAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.salah_exact_alarm_warning),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (state.isLocationPending) {
                            // Never show another city's solar times against the device clock:
                            // stay pending until a saved location or a GPS fix exists.
                            SakinahCard(contentPadding = 16.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.salah_set_location),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.salah_no_location),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            ) {
                                                viewModel?.onIntent(SalahUiIntent.UseGpsLocation)
                                            } else {
                                                gpsPermissionRequested = true
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            }
                                        }) {
                                            Text(stringResource(R.string.salah_use_my_location))
                                        }
                                        OutlinedButton(onClick = { showManualCityDialog = true }) {
                                            Text(stringResource(R.string.salah_choose_city))
                                        }
                                    }
                                }
                            }
                        } else {
                            SalahHomeHero(
                                layout = salahHomeLayout,
                                state = state,
                                onToggleCurrent = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel?.onIntent(SalahUiIntent.ToggleCurrentPrayerCompleted)
                                }
                            )
                        }
                    }

                    SalahTabBar(
                        selectedSegment = selectedSegment,
                        onSelect = { selectedSegment = it },
                        underline = salahHomeLayout == SalahHomeLayout.CIRCULAR_RING
                    )
                }
            }

            when (selectedSegment) {
                0 -> {
                    // Daily Prayer Matrix (5-prayer checklist in Focus Mode)
                    val visiblePrayers = if (focusMode) {
                        state.prayersList.filter { it.id != "sunrise" }
                    } else {
                        state.prayersList
                    }
                    items(visiblePrayers, key = { it.id }) { prayer ->
                        val isExpanded = !focusMode && expandedId == prayer.id
                        val isSunrise = prayer.id == "sunrise"

                        SakinahCard(contentPadding = 14.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = prayer.nameEnglish,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = prayer.nameArabic,
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = if (!isSunrise) stringResource(R.string.salah_rakat_fard, prayer.fardRakats) else stringResource(R.string.salah_no_prayer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (prayer.endTime.isNotBlank()) "${prayer.time} – ${prayer.endTime}" else prayer.time,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (prayer.id == "isha" && prayer.endTime.isNotBlank()) {
                                            Text(
                                                text = stringResource(R.string.salah_necessity_until_fajr),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (prayer.id == "sunrise") {
                                            Text(
                                                text = stringResource(R.string.salah_prohibited_window),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (prayer.id == "asr") {
                                            Text(
                                                text = stringResource(R.string.salah_preferred_sunset),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (!isSunrise) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    prayerTypeOf(prayer)?.let { prayerType ->
                                                        viewModel?.onIntent(
                                                            SalahUiIntent.ToggleSalahCompletion(prayerType, prayer.isJamaah)
                                                        )
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (prayer.isFardCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (prayer.isFardCompleted) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = stringResource(R.string.salah_completed_cd),
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (!focusMode && !isSunrise) {
                                        IconButton(
                                            onClick = { expandedId = if (isExpanded) null else prayer.id }
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = stringResource(R.string.salah_expand_cd),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isExpanded && !isSunrise) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (prayer.sunnahBefore > 0) {
                                            StatusBadge(
                                                text = "${prayer.sunnahBefore} Sunnah Before",
                                                accentColor = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (prayer.sunnahAfter > 0) {
                                            StatusBadge(
                                                text = "${prayer.sunnahAfter} Sunnah After",
                                                accentColor = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (prayer.nawafilName != null) {
                                        Text(
                                            text = stringResource(R.string.salah_nawafil_recommended, prayer.nawafilName),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { launchPrayerGuide(prayer.nameEnglish, prayer.fardRakats) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.salah_open_guide, prayer.nameEnglish, prayer.fardRakats), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    item { TahajjudCard(state) }
                }

                1 -> {
                    // One shared Qibla card (Compass · Map · Sun). Interference, calibration
                    // and permission guidance are owned by the card itself.
                    item {
                        QiblaModesCard(
                            latitude = state.userCoordinates.first,
                            longitude = state.userCoordinates.second,
                            cityName = state.locationName,
                            locationAvailable = !state.isLocationPending
                        )
                    }
                }

                else -> {
                    // Guide segment: prayer-specific Rakat guides
                    items(state.prayersList.filter { it.id != "sunrise" }, key = { "guide-${it.id}" }) { prayer ->
                        SakinahCard(
                            onClick = { launchPrayerGuide(prayer.nameEnglish, prayer.fardRakats) },
                            contentPadding = 14.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${prayer.nameEnglish} (${prayer.nameArabic})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${prayer.fardRakats} Rakat Fard posture & recitation walkthrough",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusBadge(
                                    text = "${prayer.fardRakats} Rakats",
                                    accentColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Prayer Calculation & Regional Settings Dialog (Draft-buffered until Apply)
        if (showSettingsDialog) {
            var draftIsAutomatic by remember(showSettingsDialog) { mutableStateOf(state.isAutomaticMethod) }
            var draftMethod by remember(showSettingsDialog) { mutableStateOf(state.calculationMethod) }
            var draftMadhhab by remember(showSettingsDialog) { mutableStateOf(state.madhhab) }
            var methodSearch by remember(showSettingsDialog) { mutableStateOf("") }

            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.salah_calc_settings),
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showSettingsDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.salah_close_cd))
                            }
                        }

                        Text(
                            text = stringResource(R.string.salah_calc_authority),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (draftIsAutomatic) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (draftIsAutomatic) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { draftIsAutomatic = true }
                        ) {
                            Text(
                                text = stringResource(R.string.salah_auto_authority, state.authorityName),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = if (draftIsAutomatic) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val query = methodSearch.trim().lowercase()
                        val filteredMethods = CalculationMethod.entries.filter { m ->
                            if (query.isBlank()) true else m.name.replace('_', ' ').lowercase().contains(query)
                        }

                        OutlinedTextField(
                            value = methodSearch,
                            onValueChange = { methodSearch = it },
                            placeholder = { Text(stringResource(R.string.salah_search_city), fontSize = 12.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (methodSearch.isNotBlank()) {
                                    IconButton(onClick = { methodSearch = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.salah_clear_cd))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredMethods.size) { idx ->
                                val m = filteredMethods[idx]
                                val label = m.name.replace('_', ' ')
                                val isSelected = !draftIsAutomatic && draftMethod == m
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            draftIsAutomatic = false
                                            draftMethod = m
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.salah_asr_method),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SegmentedPillBar(
                            options = listOf("Standard (1:1)", "Hanafi (2:1)"),
                            selectedIndex = if (draftMadhhab == Madhhab.HANAFI) 1 else 0,
                            onSelect = { idx ->
                                draftMadhhab = if (idx == 1) Madhhab.HANAFI else Madhhab.SHAFI_HANBALI_MALIKI
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = { showSettingsDialog = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.salah_cancel))
                            }
                            Button(
                                onClick = {
                                    if (draftIsAutomatic) {
                                        viewModel?.onIntent(SalahUiIntent.UseAutomaticMethod)
                                    } else {
                                        viewModel?.onIntent(SalahUiIntent.ChangeCalculationMethod(draftMethod))
                                    }
                                    if (draftMadhhab != state.madhhab) {
                                        viewModel?.onIntent(SalahUiIntent.ChangeMadhhab(draftMadhhab))
                                    }
                                    showSettingsDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = stringResource(R.string.salah_apply),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Unified Searchable City & GPS Selector Dialog
        if (showManualCityDialog) {
            HomeLocationCityPickerDialog(
                title = stringResource(R.string.salah_select_location_title),
                subtitle = stringResource(R.string.salah_select_location_subtitle),
                onUseDeviceGps = {
                    showManualCityDialog = false
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel?.onIntent(SalahUiIntent.UseGpsLocation)
                    } else {
                        val activity = context as? Activity
                        val rationale = activity != null && (
                            ActivityCompat.shouldShowRequestPermissionRationale(
                                activity,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) || ActivityCompat.shouldShowRequestPermissionRationale(
                                activity,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        if (!gpsPermissionRequested || rationale) {
                            gpsPermissionRequested = true
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            showGpsSettingsDialog = true
                        }
                    }
                },
                onDismiss = { showManualCityDialog = false },
                onCitySelected = { city ->
                    viewModel?.onIntent(
                        SalahUiIntent.SetManualLocation(
                            lat = city.latitude,
                            lon = city.longitude,
                            name = city.name,
                            countryCode = city.countryCode.ifBlank { null },
                            timeZoneId = city.timeZoneId.ifBlank { null }
                        )
                    )
                    showManualCityDialog = false
                }
            )
        }

        if (showGpsSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showGpsSettingsDialog = false },
                title = { Text(stringResource(R.string.salah_location_permission_title)) },
                text = {
                    Text(
                        stringResource(R.string.salah_location_permission_body)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showGpsSettingsDialog = false
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            runCatching { context.startActivity(intent) }
                        }
                    ) { Text(stringResource(R.string.salah_open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { showGpsSettingsDialog = false }) { Text(stringResource(R.string.salah_cancel)) }
                }
            )
        }

        // 7. Qadha Ledger Modal
        if (showQadhaDialog) {
            Dialog(onDismissRequest = { showQadhaDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.salah_qadha_title),
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showQadhaDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.salah_close_cd))
                            }
                        }

                        val deficits = state.qadhaDeficits
                        val totalMissed = deficits.values.sum()
                        val daysRemaining = if (totalMissed == 0) 0 else (totalMissed + 1) / 2
                        Text(
                            text = if (totalMissed == 0) {
                                stringResource(R.string.salah_qadha_none)
                            } else {
                                stringResource(R.string.salah_qadha_summary, totalMissed, daysRemaining)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        listOf(
                            PrayerType.FAJR,
                            PrayerType.DHUHR,
                            PrayerType.ASR,
                            PrayerType.MAGHRIB,
                            PrayerType.ISHA
                        ).forEach { p ->
                            val count = deficits[p] ?: 0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${p.name} ($count missed)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Correct a mistaken entry by adding a missed prayer back.
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel?.onIntent(SalahUiIntent.IncrementQadha(p))
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("+1 Missed", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel?.onIntent(SalahUiIntent.DecrementQadha(p))
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        // Cannot make up more prayers than are outstanding.
                                        enabled = count > 0
                                    ) {
                                        Text("+1 Prayed", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { showQadhaDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.salah_done), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Maps a displayed row to a recordable prayer. Sunrise has no Salah record, so it returns
 * null instead of silently defaulting to Dhuhr (which made tapping Sunrise tick Dhuhr off).
 */
internal fun prayerTypeOf(prayer: PrayerModel): PrayerType? = when (prayer.id.lowercase()) {
    "fajr" -> PrayerType.FAJR
    "dhuhr" -> PrayerType.DHUHR
    "asr" -> PrayerType.ASR
    "maghrib" -> PrayerType.MAGHRIB
    "isha" -> PrayerType.ISHA
    else -> null
}

/**
 * Tahajjud window: the last third of the night, from Maghrib + 2/3 of the night until Fajr.
 * Shown on the home screen instead of being buried in a subtitle so the time is easy to find.
 */
@Composable
private fun TahajjudCard(state: SalahUiState) {
    if (state.tahajjudStartTimeFormatted.isBlank() || state.tahajjudEndTimeFormatted.isBlank()) {
        return
    }
    SakinahCard(contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.salah_tahajjud),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${state.tahajjudStartTimeFormatted} — ${state.tahajjudEndTimeFormatted}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (state.tahajjudDurationText.isNotBlank()) {
                Text(
                    text = state.tahajjudDurationText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
