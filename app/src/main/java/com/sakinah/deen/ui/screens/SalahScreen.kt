package com.sakinah.deen.ui.screens

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerModel
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.domain.sensor.QiblaSensorManager
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalahScreen(
    onOpenGuide: () -> Unit,
    onOpenPrayerGuide: ((String, Int) -> Unit)? = null,
    focusMode: Boolean = false,
    onOpenNotificationSettings: (() -> Unit)? = null,
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel?.onIntent(SalahUiIntent.LocationPermissionResult(isGranted))
    }

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
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    val qiblaManager = rememberInject<QiblaSensorManager>()
    val compassData by qiblaManager.compassFlow.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Start magnetometer/rotation sensors only while Qibla tab is active to save battery.
    DisposableEffect(selectedSegment) {
        if (selectedSegment == 1) {
            qiblaManager.startListening()
        }
        onDispose {
            qiblaManager.stopListening()
        }
    }
    LaunchedEffect(selectedSegment, state.userCoordinates) {
        if (selectedSegment == 1) {
            val loc = Location("").apply {
                latitude = state.userCoordinates.first
                longitude = state.userCoordinates.second
            }
            qiblaManager.updateLocation(loc)
        }
    }
    LaunchedEffect(selectedSegment, compassData.isAligned, compassData.isFlat) {
        if (selectedSegment == 1 && compassData.isAligned && compassData.isFlat) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun launchPrayerGuide(prayerTitle: String, rakats: Int) {
        if (onOpenPrayerGuide != null) {
            onOpenPrayerGuide(prayerTitle, rakats.coerceAtLeast(2))
        } else {
            onOpenGuide()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshScope.launch {
                viewModel?.onIntent(SalahUiIntent.RefreshLocation)
                isRefreshing = false
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Date & Location Header + Hero Card + Mode Segment
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.hijriDate,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (state.gregorianDate.isNotBlank()) {
                                Text(
                                    text = state.gregorianDate,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable { showManualCityDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Change Location",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${state.locationName} (${state.timeZoneAbbr}) · ${state.sourceLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onOpenNotificationSettings != null) {
                                IconButton(
                                    onClick = onOpenNotificationSettings,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notifications,
                                        contentDescription = "Azaan & Notifications",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        if (!focusMode) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = "Prayer Settings",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Button(
                                    onClick = { showQadhaDialog = true },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Text("Qadha Ledger", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                        }
                    }

                    // 2a. Always-available compact location controls (even after a city preset).
                    if (!focusMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.locationName.ifBlank { "Location not set" },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { showManualCityDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("City", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Enable GPS", fontSize = 10.sp)
                            }
                        }
                    }

                    // 2. Location Permission Warning Banner
                    if (!focusMode && state.showLocationWarningBanner) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Location Warning",
                                        tint = Color(0xFFB45309),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Enable location for exact local prayer times.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF78350F),
                                        lineHeight = 15.sp
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Text("Enable GPS", fontSize = 10.sp, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = { showManualCityDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Text("City", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Hero Active Prayer Card (palette-aware, so Black & White is monochrome)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CURRENT: ${state.currentPrayer.name} · NEXT: ${state.nextPrayer.name}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Timer,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = state.countdownText,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = "${state.nextPrayer.name} · الصلاة القادمة",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!focusMode) {
                                        val nextRakats = state.prayersList
                                            .firstOrNull { it.nameEnglish.equals(state.nextPrayer.name, ignoreCase = true) }
                                            ?.fardRakats
                                            ?: 2
                                        Button(
                                            onClick = { launchPrayerGuide(state.nextPrayer.name, nextRakats) },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text("Visual Guide", fontSize = 11.sp)
                                        }
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { state.elapsedWindowRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )

                                if (state.isTraveler) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            text = "Safar (Qasr) • ${state.distanceFromHomeKm.toInt()} km from Home",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Unified Sub-Mode Switcher (Times | Qibla | Guide) — always visible so
                    // the Qibla tab is never hidden, including in Focus Mode.
                    SegmentedPillBar(
                        options = listOf("Times", "Qibla", "Guide"),
                        selectedIndex = selectedSegment,
                        onSelect = { selectedSegment = it }
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
                                        text = if (!isSunrise) "${prayer.fardRakats} Rakat Fard" else "No Prayer (Prohibited Time)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = prayer.time,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (!isSunrise) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (prayer.isFardCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable {
                                                    val prayerType = runCatching {
                                                        PrayerType.valueOf(prayer.nameEnglish.uppercase())
                                                    }.getOrDefault(PrayerType.DHUHR)
                                                    viewModel?.onIntent(SalahUiIntent.ToggleSalahCompletion(prayerType, prayer.isJamaah))
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (prayer.isFardCompleted) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Completed",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (!focusMode && !isSunrise) {
                                        IconButton(
                                            onClick = { expandedId = if (isExpanded) null else prayer.id },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand",
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
                                            text = "Recommended Nafl: ${prayer.nawafilName}",
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
                                        Text("Open ${prayer.nameEnglish} Step Guide (${prayer.fardRakats} Rakats)", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            QiblaModesCard(
                                latitude = state.userCoordinates.first,
                                longitude = state.userCoordinates.second,
                                cityName = state.locationName
                            )
                            if (compassData.isMagneticInterference) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFFFC107),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF4A3200))
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Magnetic interference detected — move away from metal, speakers, or magnetic cases",
                                            color = Color(0xFF4A3200),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
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
                                text = "Prayer Calculation Settings",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showSettingsDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Text(
                            text = "Calculation Authority / Method",
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
                                text = "Automatic — ${state.authorityName} (by country)",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = if (draftIsAutomatic) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val methods = listOf(
                            CalculationMethod.UMM_AL_QURA to "Umm Al-Qura (Makkah & Gulf)",
                            CalculationMethod.QATAR to "Qatar Awqaf (18° / 90m)",
                            CalculationMethod.DUBAI to "Dubai / UAE Awqaf",
                            CalculationMethod.KARACHI to "Karachi (South Asia - 18°)",
                            CalculationMethod.NORTH_AMERICA to "North America (ISNA 15°)",
                            CalculationMethod.MUSLIM_WORLD_LEAGUE to "Muslim World League (Europe)",
                            CalculationMethod.EGYPTIAN to "Egyptian General Survey"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            methods.forEach { (m, label) ->
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
                            text = "Asr Juristic Method",
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
                                Text("Cancel")
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
                                Text("Apply Settings", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }

        // 6. Unified Searchable City & GPS Selector Dialog
        if (showManualCityDialog) {
            HomeLocationCityPickerDialog(
                title = "Select Prayer Location",
                subtitle = "Choose a city or switch back to device GPS.",
                onUseDeviceGps = {
                    viewModel?.onIntent(SalahUiIntent.UseGpsLocation)
                    showManualCityDialog = false
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
                                text = "Qadha al-Umri Ledger",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showQadhaDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        val deficits = state.qadhaDeficits
                        val totalMissed = deficits.values.sum()
                        val daysRemaining = if (totalMissed == 0) 0 else (totalMissed + 1) / 2
                        Text(
                            text = "Total Missed Prayers: $totalMissed · Pace: 2/day ($daysRemaining days remaining)",
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
                                Button(
                                    onClick = { viewModel?.onIntent(SalahUiIntent.DecrementQadha(p)) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    enabled = count > 0
                                ) {
                                    Text("+1 Prayed", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        Button(
                            onClick = { showQadhaDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Done", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
