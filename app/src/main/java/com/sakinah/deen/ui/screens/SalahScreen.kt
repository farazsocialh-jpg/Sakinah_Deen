package com.sakinah.deen.ui.screens

import android.Manifest
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerModel
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.EmeraldSurface
import com.sakinah.deen.ui.theme.MintGreen
import com.sakinah.deen.ui.viewmodel.SalahUiIntent
import com.sakinah.deen.ui.viewmodel.SalahUiState
import com.sakinah.deen.ui.viewmodel.SalahViewModel

@Composable
fun SalahScreen(
    onOpenGuide: () -> Unit,
    viewModel: SalahViewModel? = null,
    modifier: Modifier = Modifier
) {
    // 1. Reactive state collection from ViewModel if available, or fallback local state
    val vmState = viewModel?.uiState?.collectAsState()?.value
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

    // 2. Permission Request Launcher for ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel?.onIntent(SalahUiIntent.LocationPermissionResult(isGranted))
    }

    var showQadhaDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showManualCityDialog by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>("asr") }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Top Bar: Dynamic Date & Active Location Badge with Settings Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = state.hijriDate,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EmeraldDeep
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showSettingsDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = MintGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        // Provenance of these timings: e.g. "Qatar Awqaf (Online)" / "Offline Calculation".
                        text = "${state.locationName} · ${state.sourceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Prayer Settings",
                        tint = EmeraldDeep,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Button(
                    onClick = { showQadhaDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintGreen.copy(alpha = 0.15f),
                        contentColor = EmeraldDeep
                    )
                ) {
                    Text("Qadha Ledger", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }

        // 2. Location Permission Warning Banner (if running on fallback Makkah or London without GPS)
        if (state.showLocationWarningBanner) {
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
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Enable GPS", fontSize = 10.sp, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = { showManualCityDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("City", fontSize = 10.sp, color = EmeraldDeep)
                        }
                    }
                }
            }
        }

        // 3. Hero Active Prayer Card (Deep Emerald Gradient with Live Countdown)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(EmeraldDeep, EmeraldSurface)
                        )
                    )
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
                            color = MintGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = MintGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = state.countdownText,
                                    color = Color.White,
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
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Button(
                            onClick = onOpenGuide,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Visual Guide", fontSize = 11.sp)
                        }
                    }

                    LinearProgressIndicator(
                        progress = { state.elapsedWindowRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MintGreen,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val resId = context.resources.getIdentifier("adhan_fajr", "raw", context.packageName)
                                if (resId != 0) {
                                    val mp = android.media.MediaPlayer.create(context, resId)
                                    mp?.start()
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hear Adhan", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = { viewModel?.onIntent(SalahUiIntent.RefreshLocation) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh GPS", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
            }
        }

        // 4. Daily Prayer Matrix (Interactive Cards)
        items(state.prayersList) { prayer ->
                val isExpanded = expandedId == prayer.id
                val isSunrise = prayer.id == "sunrise"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                        color = EmeraldDeep
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = prayer.nameArabic,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 16.sp,
                                        color = EmeraldDeep.copy(alpha = 0.8f)
                                    )
                                }
                                Text(
                                    text = if (!isSunrise) "${prayer.fardRakats} Rakat Fard" else "No Prayer (Prohibited Time)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = prayer.time,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = EmeraldDeep
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (!isSunrise) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (prayer.isFardCompleted) MintGreen else Color.LightGray.copy(alpha = 0.25f),
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
                                                Icon(Icons.Default.Check, contentDescription = "Completed", tint = EmeraldDeep, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { expandedId = if (isExpanded) null else prayer.id },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand"
                                    )
                                }
                            }
                        }

                        // Sunnah / Nafl Accordion
                        AnimatedVisibility(visible = isExpanded && !isSunrise) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (prayer.sunnahBefore > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = EmeraldDeep.copy(alpha = 0.08f),
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            Text(
                                                text = "${prayer.sunnahBefore} Rakat Sunnah Before",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontSize = 11.sp,
                                                color = EmeraldDeep,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    if (prayer.sunnahAfter > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = EmeraldDeep.copy(alpha = 0.08f),
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            Text(
                                                text = "${prayer.sunnahAfter} Rakat Sunnah After",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontSize = 11.sp,
                                                color = EmeraldDeep,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                if (prayer.nawafilName != null) {
                                    Text(
                                        text = "Recommended Nafl: ${prayer.nawafilName}",
                                        fontSize = 11.sp,
                                        color = EmeraldDeep,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    // 5. Prayer Calculation & Regional Settings Dialog
    if (showSettingsDialog) {
        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            color = EmeraldDeep
                        )
                        IconButton(onClick = { showSettingsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Text(
                        text = "Calculation Authority / Method",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = EmeraldDeep
                    )

                    // Country-mapped authority unless the user pinned a method by hand.
                    val isAutomatic = state.isAutomaticMethod
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAutomatic) MintGreen.copy(alpha = 0.2f) else Color.Transparent,
                        border = if (isAutomatic) BorderStroke(1.dp, MintGreen) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel?.onIntent(SalahUiIntent.UseAutomaticMethod) }
                    ) {
                        Text(
                            text = "Automatic — ${state.authorityName} (by country)",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = if (isAutomatic) FontWeight.Bold else FontWeight.Normal,
                            color = EmeraldDeep
                        )
                    }

                    // Presets selection
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
                            val isSelected = state.calculationMethod == m
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MintGreen.copy(alpha = 0.2f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, MintGreen) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel?.onIntent(SalahUiIntent.ChangeCalculationMethod(m)) }
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = EmeraldDeep
                                )
                            }
                        }
                    }

                    Text(
                        text = "Asr Juristic Method",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = EmeraldDeep
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel?.onIntent(SalahUiIntent.ChangeMadhhab(Madhhab.SHAFI_HANBALI_MALIKI)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.madhhab == Madhhab.SHAFI_HANBALI_MALIKI) EmeraldDeep else Color.LightGray.copy(alpha = 0.25f),
                                contentColor = if (state.madhhab == Madhhab.SHAFI_HANBALI_MALIKI) Color.White else EmeraldDeep
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Standard (1:1)", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel?.onIntent(SalahUiIntent.ChangeMadhhab(Madhhab.HANAFI)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.madhhab == Madhhab.HANAFI) EmeraldDeep else Color.LightGray.copy(alpha = 0.25f),
                                contentColor = if (state.madhhab == Madhhab.HANAFI) Color.White else EmeraldDeep
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hanafi (2:1)", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { showSettingsDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep)
                    ) {
                        Text("Apply Settings", color = Color.White)
                    }
                }
            }
        }
    }

    // 6. Manual City Selector Dialog (Quick Presets)
    if (showManualCityDialog) {
        Dialog(onDismissRequest = { showManualCityDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select City Preset",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = EmeraldDeep
                    )
                    listOf(
                        CityPreset(25.2854, 51.5310, "Doha, Qatar", "QA"),
                        CityPreset(21.4225, 39.8262, "Makkah, Saudi Arabia", "SA"),
                        CityPreset(24.4686, 39.6142, "Madinah, Saudi Arabia", "SA"),
                        CityPreset(25.2048, 55.2708, "Dubai, UAE", "AE"),
                        CityPreset(24.8607, 67.0011, "Karachi, Pakistan", "PK"),
                        CityPreset(51.5074, -0.1278, "London, UK", "GB"),
                        CityPreset(40.7128, -74.0060, "New York, USA", "US")
                    ).forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldDeep.copy(alpha = 0.05f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel?.onIntent(
                                        SalahUiIntent.SetManualLocation(
                                            lat = preset.lat,
                                            lon = preset.lon,
                                            name = preset.name,
                                            countryCode = preset.countryCode
                                        )
                                    )
                                    showManualCityDialog = false
                                }
                        ) {
                            Text(
                                text = preset.name,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = EmeraldDeep
                            )
                        }
                    }
                }
            }
        }
    }

    // 7. Qadha Ledger Modal
    if (showQadhaDialog) {
        Dialog(onDismissRequest = { showQadhaDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
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
                            color = EmeraldDeep
                        )
                        IconButton(onClick = { showQadhaDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    val deficits = state.qadhaDeficits
                    val totalMissed = deficits.values.sum()
                    Text(
                        text = "Total Missed Prayers: $totalMissed · Pace: 2/day (${(totalMissed / 2).coerceAtLeast(1)} days remaining)",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldDeep,
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
                            Text(text = "${p.name} ($count missed)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Button(
                                onClick = { viewModel?.onIntent(SalahUiIntent.DecrementQadha(p)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                enabled = count > 0
                            ) {
                                Text("+1 Prayed", color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    Button(
                        onClick = { showQadhaDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep)
                    ) {
                        Text("Save & Close", color = Color.White)
                    }
                }
            }
        }
    }
}

/** A quick manual city choice plus its ISO country code, used to map the Sunni authority. */
private data class CityPreset(
    val lat: Double,
    val lon: Double,
    val name: String,
    val countryCode: String
)
