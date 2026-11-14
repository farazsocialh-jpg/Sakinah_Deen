package com.sakinah.deen.ui.screens

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.model.AzaanSettings
import com.sakinah.deen.data.model.CalculationMethod as AppMethod
import com.sakinah.deen.data.model.Madhhab as AppMadhhab
import com.sakinah.deen.data.model.Muezzin
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.service.AdhanAudio
import com.sakinah.deen.service.AzaanScheduling
import com.sakinah.deen.settings.AsrMethod
import com.sakinah.deen.settings.CalculationMethod
import com.sakinah.deen.settings.SettingsRepository
import com.sakinah.deen.settings.ThemeMode
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.PrayerGuideGender
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.ThemeMode as UiThemeMode
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRerunOnboarding: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repo = rememberInject<SettingsRepository>()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appPrefs = remember { SakinahPreferences(context) }
    val appearancePrefs = remember(context) {
        GlobalContext.getOrNull()?.getOrNull<AppearancePreferences>() ?: AppearancePreferences(context)
    }
    val appearanceConfig by appearancePrefs.config.collectAsStateWithLifecycle()
    val locationProvider = rememberInject<UserLocationProvider>()
    val tafseerRepository = rememberInject<TafseerDownloadRepository>()
    val tafseerPackages by tafseerRepository.packages.collectAsStateWithLifecycle(initialValue = emptyList())
    var downloadingPackId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tafseerRepository) {
        tafseerRepository.refreshDownloadStatus()
    }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasHomeLocation by remember { mutableStateOf(appPrefs.homeCoordinates != null) }
    var translationExpanded by remember { mutableStateOf(false) }
    var homeCityName by remember { mutableStateOf(appPrefs.homeCityName) }
    var showHomeCityPicker by remember { mutableStateOf(false) }
    var gpsHomeRequested by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(repo.cacheSizeBytes()) }

    fun saveCurrentGpsAsHome() {
        scope.launch {
            val resolved = locationProvider.getFreshOrCachedLocation()
            val label = listOf(resolved.cityName, resolved.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "Saved Location" }
            appPrefs.saveHomeAnchor(resolved.latitude, resolved.longitude, label)
            hasHomeLocation = appPrefs.homeCoordinates != null
            homeCityName = appPrefs.homeCityName
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isGranted && gpsHomeRequested) saveCurrentGpsAsHome()
        gpsHomeRequested = false
    }

    var azaanSettings by remember { mutableStateOf(appPrefs.azaanSettings) }
    val previewPlayer = remember(context) { MuezzinPreviewPlayer(context) }
    var previewingMuezzin by remember { mutableStateOf<Muezzin?>(null) }
    previewPlayer.onFinished = { previewingMuezzin = null }
    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    fun applyAzaanSettings(updated: AzaanSettings) {
        azaanSettings = updated
        appPrefs.azaanSettings = updated
        AzaanScheduling.scheduleStoredLocation(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "App Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Prayer & Qibla
                Text("Prayer & Qibla", style = MaterialTheme.typography.titleMedium)
                var calcExpanded by remember { mutableStateOf(false) }
                var methodOverride by remember { mutableStateOf(appPrefs.calculationMethodOverride) }
                ExposedDropdownMenuBox(expanded = calcExpanded, onExpandedChange = { calcExpanded = it }) {
                    OutlinedTextField(
                        value = methodOverride?.name?.replace('_', ' ') ?: "Automatic (by country)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Calculation method") },
                        supportingText = { Text("Automatic uses your country's Sunni authority.") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calcExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = calcExpanded, onDismissRequest = { calcExpanded = false }) {
                        DropdownMenuItem(text = { Text("Automatic (by country)") }, onClick = {
                            appPrefs.calculationMethodOverride = null
                            methodOverride = null
                            scope.launch {
                                repo.updatePrayer(
                                    settings.prayer.copy(calculationMethod = CalculationMethod.MWL)
                                )
                            }
                            calcExpanded = false
                        })
                        AppMethod.entries.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name.replace('_', ' ')) }, onClick = {
                                appPrefs.calculationMethodOverride = m
                                methodOverride = m
                                sharedMethodFor(m)?.let { shared ->
                                    scope.launch { repo.updatePrayer(settings.prayer.copy(calculationMethod = shared)) }
                                }
                                calcExpanded = false
                            })
                        }
                    }
                }

                // Home Location
                SakinahCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Home Location",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = homeCityName
                                ?: if (hasHomeLocation) "Saved location" else "Not set (normal 4 rakat)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    gpsHomeRequested = true
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Use GPS", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { showHomeCityPicker = true },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Choose City", fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = {
                                    appPrefs.clearHomeAnchor()
                                    hasHomeLocation = false
                                    homeCityName = null
                                },
                                enabled = hasHomeLocation
                            ) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Asr juristic school",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = settings.prayer.asrMethod == AsrMethod.HANAFI,
                        onCheckedChange = { hanafi ->
                            appPrefs.madhhab = if (hanafi) {
                                AppMadhhab.HANAFI
                            } else {
                                AppMadhhab.SHAFI_HANBALI_MALIKI
                            }
                            scope.launch {
                                repo.updatePrayer(
                                    settings.prayer.copy(asrMethod = if (hanafi) AsrMethod.HANAFI else AsrMethod.SHAFI)
                                )
                            }
                        }
                    )
                    Text(
                        text = if (settings.prayer.asrMethod == AsrMethod.HANAFI) "Hanafi" else "Shafi'i",
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "High latitude rule",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(
                                settings.prayer.highLatitudeRule.name.replace('_', ' '),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            com.sakinah.deen.settings.HighLatitudeRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.name.replace('_', ' ')) },
                                    onClick = {
                                        expanded = false
                                        scope.launch {
                                            repo.updatePrayer(settings.prayer.copy(highLatitudeRule = rule))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hijri offset: ${settings.prayer.hijriOffset}")
                    Slider(
                        value = settings.prayer.hijriOffset.toFloat(),
                        onValueChange = { v ->
                            scope.launch { repo.updatePrayer(settings.prayer.copy(hijriOffset = v.toInt())) }
                        },
                        valueRange = -2f..2f,
                        steps = 3,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Azaan & Notifications
                Text("Azaan & Notifications", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Azaan & Notifications →", fontSize = 13.sp)
                }
                SakinahCard(contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Azaan Notifications",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Exact alarm-clock reminders that survive Doze and reboot",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = azaanSettings.enabled,
                            onCheckedChange = { enabled ->
                                applyAzaanSettings(azaanSettings.copy(enabled = enabled))
                            }
                        )
                    }
                }

                AzaanSettings.ALERT_PRAYERS.forEach { prayer ->
                    PrayerAlertCard(
                        prayer = prayer,
                        mode = azaanSettings.modeFor(prayer),
                        offsetMinutes = azaanSettings.offsetFor(prayer),
                        enabled = azaanSettings.enabled,
                        onModeChange = { mode ->
                            applyAzaanSettings(
                                azaanSettings.copy(
                                    modes = azaanSettings.modes + (prayer to mode)
                                )
                            )
                        },
                        onOffsetChange = { offset ->
                            applyAzaanSettings(
                                azaanSettings.copy(
                                    offsetsMinutes = azaanSettings.offsetsMinutes + (prayer to offset)
                                )
                            )
                        }
                    )
                }

                SakinahCard(contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Special Fajr Azaan",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Includes “As-Salatu Khayrun Minan-Nawm”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = azaanSettings.fajrSpecialAdhan,
                            onCheckedChange = { enabled ->
                                applyAzaanSettings(azaanSettings.copy(fajrSpecialAdhan = enabled))
                            }
                        )
                    }
                }

                Text("Muezzin (Moazan) Voice", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Choose the recitation used for Full Azaan. Preview before selecting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Muezzin.entries.forEach { muezzin ->
                    val selected = azaanSettings.muezzin == muezzin
                    SakinahCard(contentPadding = 12.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    applyAzaanSettings(azaanSettings.copy(muezzin = muezzin))
                                },
                                label = { Text(muezzin.displayName, fontSize = 12.sp) }
                            )
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = {
                                    if (previewingMuezzin == muezzin) {
                                        previewPlayer.stop()
                                        previewingMuezzin = null
                                    } else {
                                        previewingMuezzin = if (previewPlayer.play(muezzin)) muezzin else null
                                    }
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    if (previewingMuezzin == muezzin) "Stop Preview" else "Play Preview",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Car Bluetooth Dua al-Safar auto-play", modifier = Modifier.weight(1f))
                    Switch(checked = settings.adhan.carBluetoothAutoPlay, onCheckedChange = { v ->
                        scope.launch { repo.updateAdhan(settings.adhan.copy(carBluetoothAutoPlay = v)) }
                    })
                }

                // Appearance & Focus
                Text("Appearance & Focus", style = MaterialTheme.typography.titleMedium)
                SakinahCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SegmentedPillBar(
                            options = UiThemeMode.entries,
                            selected = appearanceConfig.themeMode,
                            labelProvider = { it.label },
                            onSelect = { mode ->
                                appearancePrefs.update { it.copy(themeMode = mode) }
                                val sharedMode = when (mode) {
                                    UiThemeMode.LIGHT -> ThemeMode.LIGHT
                                    UiThemeMode.DARK -> ThemeMode.DARK
                                    UiThemeMode.SYSTEM -> ThemeMode.SYSTEM
                                }
                                scope.launch {
                                    repo.updateAppearance(settings.appearance.copy(theme = sharedMode))
                                }
                            }
                        )

                        Text(
                            text = "Prayer Guide Style",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SegmentedPillBar(
                            options = PrayerGuideGender.entries,
                            selected = appearanceConfig.prayerGuideGender,
                            labelProvider = { if (it == PrayerGuideGender.MALE) "Male" else "Female" },
                            onSelect = { gender ->
                                appearancePrefs.update { it.copy(prayerGuideGender = gender) }
                            }
                        )

                        Text(
                            text = "Color Palette",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SakinahPalette.entries.forEach { palette ->
                                val selected = appearanceConfig.palette == palette
                                FilterChip(
                                    selected = selected,
                                    onClick = { appearancePrefs.update { it.copy(palette = palette) } },
                                    label = { Text(palette.label, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(palette.lightPreviewPrimary)
                                        )
                                    }
                                )
                            }
                        }

                        Text(
                            text = "App & Typography Scale",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SegmentedPillBar(
                            options = FontScalePreset.entries,
                            selected = appearanceConfig.fontScale,
                            labelProvider = { it.label },
                            onSelect = { preset ->
                                appearancePrefs.update { it.copy(fontScale = preset) }
                            }
                        )

                        Text("Arabic font size: ${settings.appearance.arabicFontSize}")
                        Slider(
                            value = settings.appearance.arabicFontSize.toFloat(),
                            onValueChange = { v ->
                                scope.launch { repo.updateAppearance(settings.appearance.copy(arabicFontSize = v.toInt())) }
                            },
                            valueRange = 16f..40f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Focus Mode",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Distraction-free Mus-haf and minimal Salah view",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearanceConfig.focusModeHideDistractions,
                                onCheckedChange = { enabled ->
                                    appearancePrefs.update { it.copy(focusModeHideDistractions = enabled) }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Hide Bars on Scroll",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hide top & bottom navigation while scrolling down",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearanceConfig.autoHideBarsOnScroll,
                                onCheckedChange = { enabled ->
                                    appearancePrefs.update { it.copy(autoHideBarsOnScroll = enabled) }
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                appearancePrefs.resetOnboarding()
                                onRerunOnboarding()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Re-run Personalized Onboarding")
                        }
                    }
                }

                // Quran translation
                Text("Quran Translation", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Current: ${appPrefs.quranTranslationLanguage} (ID ${appPrefs.quranTranslationId})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val currentTranslation = quranTranslationOptions.firstOrNull {
                    it.id == appPrefs.quranTranslationId
                } ?: quranTranslationOptions.first()
                ExposedDropdownMenuBox(
                    expanded = translationExpanded,
                    onExpandedChange = { translationExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${currentTranslation.name} (${currentTranslation.language})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Quran translation edition") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = translationExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = translationExpanded,
                        onDismissRequest = { translationExpanded = false }
                    ) {
                        quranTranslationOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.name} · ${option.language}") },
                                onClick = {
                                    appPrefs.quranTranslationId = option.id
                                    appPrefs.quranTranslationLanguage = option.name
                                    scope.launch {
                                        repo.updateAppearance(
                                            settings.appearance.copy(
                                                translationId = option.id,
                                                translationLanguage = option.isoCode,
                                                translationName = option.name
                                            )
                                        )
                                    }
                                    translationExpanded = false
                                }
                            )
                        }
                    }
                }

                // Offline storage
                Text("Offline Storage", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Cache size: ${formatBytes(cacheBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    scope.launch {
                        repo.clearCache()
                        tafseerRepository.clearCachedTafsir()
                        cacheBytes = repo.cacheSizeBytes()
                        snackbarHostState.showSnackbar("Offline cache cleared.")
                    }
                }) { Text("Clear cache") }

                Text("Tafseer Packs", style = MaterialTheme.typography.titleSmall)
                tafseerPackages.forEach { pack ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pack.author, fontSize = 12.sp)
                            Text(
                                text = pack.languageCode.uppercase() +
                                    " · " + formatBytes(pack.sizeBytes) +
                                    if (pack.isDownloaded) " · Downloaded" else "",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (pack.isDownloaded) {
                            TextButton(
                                enabled = downloadingPackId == null,
                                onClick = {
                                    scope.launch {
                                        tafseerRepository.removeTafseerPackage(pack.id)
                                        tafseerRepository.refreshDownloadStatus()
                                        snackbarHostState.showSnackbar("${pack.author} removed.")
                                    }
                                }
                            ) {
                                Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            TextButton(
                                enabled = downloadingPackId == null,
                                onClick = {
                                    downloadingPackId = pack.id
                                    scope.launch {
                                        val result = tafseerRepository.downloadTafseerPackage(pack.id)
                                        tafseerRepository.refreshDownloadStatus()
                                        downloadingPackId = null
                                        snackbarHostState.showSnackbar(
                                            if (result.isSuccess) {
                                                "${pack.author} downloaded for offline use."
                                            } else {
                                                "Tafseer download failed: ${result.exceptionOrNull()?.message ?: "network error"}"
                                            }
                                        )
                                    }
                                }
                            ) {
                                if (downloadingPackId == pack.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Download", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }

    if (showHomeCityPicker) {
        HomeLocationCityPickerDialog(
            onDismiss = { showHomeCityPicker = false },
            onCitySelected = { city ->
                appPrefs.saveHomeAnchor(city.latitude, city.longitude, city.name)
                homeCityName = appPrefs.homeCityName
                hasHomeLocation = appPrefs.homeCoordinates != null
                showHomeCityPicker = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrayerAlertCard(
    prayer: PrayerType,
    mode: PrayerAlertMode,
    offsetMinutes: Int,
    enabled: Boolean,
    onModeChange: (PrayerAlertMode) -> Unit,
    onOffsetChange: (Int) -> Unit
) {
    val prayerName = prayer.name.lowercase().replaceFirstChar { it.uppercase() }
    SakinahCard(contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = prayerName,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (!enabled) {
                    Text(
                        text = "Master switch off",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Alert style",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PrayerAlertMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { onModeChange(option) },
                        label = { Text(option.label, fontSize = 11.sp) }
                    )
                }
            }

            Text(
                text = "Pre-Azaan / Timing offset",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AzaanSettings.OFFSET_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = offsetMinutes == option,
                        onClick = { onOffsetChange(option) },
                        label = { Text(offsetLabel(option), fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

private fun offsetLabel(minutes: Int): String = when {
    minutes == 0 -> "Exact (0 min)"
    minutes > 0 -> "+$minutes min"
    else -> "$minutes min"
}

/** Small local MediaPlayer used only for the Settings Muezzin audition buttons. */
private class MuezzinPreviewPlayer(private val context: Context) {
    var onFinished: (() -> Unit)? = null
    private var player: MediaPlayer? = null

    fun play(muezzin: Muezzin): Boolean {
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

    fun stop() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
        }
        player = null
    }

    fun release() = stop()
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
    else -> "$bytes bytes"
}

private data class QuranTranslationOption(
    val id: Int,
    val name: String,
    val language: String,
    val isoCode: String
)

/** Quran.com / QuranEnc translation editions that the sync layer can actually fetch. */
private val quranTranslationOptions = listOf(
    QuranTranslationOption(20, "Saheeh International", "English", "en"),
    QuranTranslationOption(234, "Fatah Muhammad Jalandhari", "Urdu", "ur"),
    QuranTranslationOption(54, "Maulana Muhammad Junagarhi", "Urdu", "ur"),
    QuranTranslationOption(131, "Dr. Mustafa Khattab (The Clear Quran)", "English", "en"),
    QuranTranslationOption(85, "Muhammad Hamidullah", "French", "fr"),
    QuranTranslationOption(27, "Sheikh Muhammad Rida", "Arabic", "ar")
)

/** Mirrors an Android override into the shared settings store (closest equivalent authority). */
private fun sharedMethodFor(method: AppMethod): CalculationMethod? = when (method) {
    AppMethod.UMM_AL_QURA, AppMethod.QATAR, AppMethod.DUBAI -> CalculationMethod.UMM_AL_QURA
    AppMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN
    AppMethod.KARACHI -> CalculationMethod.KARACHI
    AppMethod.NORTH_AMERICA -> CalculationMethod.ISNA
    AppMethod.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MWL
}