package com.sakinah.deen.ui.screens

import android.Manifest
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.ui.location.rememberGpsHomeSaver
import com.sakinah.deen.R
import com.sakinah.deen.data.model.CalculationMethod as AppMethod
import com.sakinah.deen.data.model.QuranArabicScript
import com.sakinah.deen.data.model.QuranTranslationResource
import com.sakinah.deen.data.model.Madhhab as AppMadhhab
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.data.repository.TafseerPackage
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.settings.AsrMethod
import com.sakinah.deen.settings.CalculationMethod
import com.sakinah.deen.settings.SettingsRepository
import com.sakinah.deen.settings.ThemeMode
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.ThemePalettePicker
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.data.quran.QuranOfflineStatus
import com.sakinah.deen.ui.components.QuranOfflineStatusBanner
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.widget.PrayerWindow
import com.sakinah.deen.widget.WidgetPrefsStore
import com.sakinah.deen.widget.WidgetStyle
import com.sakinah.deen.widget.WidgetThemeMode
import com.sakinah.deen.widget.resolveWidgetColors
import com.sakinah.deen.widget.updateAllWidgets
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.ThemeMode as UiThemeMode
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.Locale

/**
 * The three focused Settings sections. Each is its own navigation destination so no single
 * screen has to carry every preference.
 */
enum class SettingsSection(val title: String, val subtitle: String) {
    PRAYER_LOCATION(
        "Prayer & Location",
        "Calculation method, home location, Asr school, high-latitude rule and Hijri offset."
    ),
    APPEARANCE_READING(
        "Appearance & Reading",
        "Theme, palette, typography and the Salah, Quran & Hadith reader layouts."
    ),
    QURAN_OFFLINE(
        "Quran & Offline Packs",
        "Translation edition, offline tafseer packs and cached storage."
    )
}

/**
 * One Settings section at a time.
 *
 * Azaan and prayer notification controls live only in [NotificationSettingsScreen]; the Salah
 * guide's male/female style lives on the profile (and the guide itself), so neither is repeated
 * here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    section: SettingsSection,
    modifier: Modifier = Modifier,
    onRerunOnboarding: () -> Unit = {}
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
    val islamicRepository = rememberInject<IslamicDataRepository>()
    val tafseerPackages by tafseerRepository.packages.collectAsStateWithLifecycle(initialValue = emptyList())
    var downloadingPackId by remember { mutableStateOf<String?>(null) }
    var packDownloadProgress by remember { mutableFloatStateOf(0f) }

    var translationCatalog by remember { mutableStateOf<List<QuranTranslationResource>>(emptyList()) }
    var isLoadingCatalog by remember { mutableStateOf(false) }
    var catalogReloadNonce by remember { mutableIntStateOf(0) }
    var showTranslationSheet by remember { mutableStateOf(false) }
    var translationQuery by remember { mutableStateOf("") }
    val translationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var travelModeActive by remember { mutableStateOf(appPrefs.travelModeActive) }
    var isDuaPlaying by remember { mutableStateOf(false) }
    var duaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(section, tafseerRepository) {
        if (section == SettingsSection.QURAN_OFFLINE) {
            tafseerRepository.refreshDownloadStatus()
        }
    }

    LaunchedEffect(section, catalogReloadNonce) {
        if (section == SettingsSection.QURAN_OFFLINE && translationCatalog.isEmpty() && !isLoadingCatalog) {
            isLoadingCatalog = true
            translationCatalog = runCatching {
                islamicRepository.fetchTranslationCatalog()
            }.getOrDefault(emptyList())
            isLoadingCatalog = false
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            runCatching {
                duaPlayer?.stop()
                duaPlayer?.release()
            }
            duaPlayer = null
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasHomeLocation by remember { mutableStateOf(appPrefs.homeCoordinates != null) }
    var homeCityName by remember { mutableStateOf(appPrefs.homeCityName) }
    var showHomeCityPicker by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(repo.cacheSizeBytes()) }

    val saveGpsHome = rememberGpsHomeSaver(
        locationProvider = locationProvider,
        preferences = appPrefs,
        fallbackLabel = "Saved Location"
    ) {
        hasHomeLocation = appPrefs.homeCoordinates != null
        homeCityName = appPrefs.homeCityName
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
                    text = section.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Never let a keystore failure silently downgrade sensitive storage.
                appPrefs.storageSecurityWarning?.let { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                when (section) {
                    SettingsSection.PRAYER_LOCATION -> {
                        var calcExpanded by remember { mutableStateOf(false) }
                        var methodOverride by remember { mutableStateOf(appPrefs.calculationMethodOverride) }
                        ExposedDropdownMenuBox(
                            expanded = calcExpanded,
                            onExpandedChange = { calcExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = methodOverride?.name?.replace('_', ' ')
                                    ?: stringResource(R.string.settings_calculation_automatic_option),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_calculation_method)) },
                                supportingText = {
                                    Text(stringResource(R.string.settings_calculation_automatic))
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = calcExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = calcExpanded,
                                onDismissRequest = { calcExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_calculation_automatic_option)) },
                                    onClick = {
                                        appPrefs.calculationMethodOverride = null
                                        methodOverride = null
                                        scope.launch {
                                            repo.updatePrayer(
                                                settings.prayer.copy(
                                                    calculationMethod = CalculationMethod.MWL
                                                )
                                            )
                                        }
                                        calcExpanded = false
                                    }
                                )
                                AppMethod.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.name.replace('_', ' ')) },
                                        onClick = {
                                            appPrefs.calculationMethodOverride = m
                                            methodOverride = m
                                            sharedMethodFor(m)?.let { shared ->
                                                scope.launch {
                                                    repo.updatePrayer(
                                                        settings.prayer.copy(calculationMethod = shared)
                                                    )
                                                }
                                            }
                                            calcExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Home location is shared with the profile screen: both write the same
                        // saved anchor, so the city is only ever set in one place.
                        SakinahCard(contentPadding = 14.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Home Location",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = homeCityName
                                        ?: if (hasHomeLocation) "Saved location" else "Not set",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = saveGpsHome,
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

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Asr juristic school",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SegmentedPillBar(
                                options = listOf("Shafi‘i / Maliki / Hanbali", "Hanafi"),
                                selectedIndex = if (settings.prayer.asrMethod == AsrMethod.HANAFI) 1 else 0,
                                onSelect = { index ->
                                    val hanafi = index == 1
                                    appPrefs.madhhab = if (hanafi) {
                                        AppMadhhab.HANAFI
                                    } else {
                                        AppMadhhab.SHAFI_HANBALI_MALIKI
                                    }
                                    scope.launch {
                                        repo.updatePrayer(
                                            settings.prayer.copy(
                                                asrMethod = if (hanafi) AsrMethod.HANAFI else AsrMethod.SHAFI
                                            )
                                        )
                                    }
                                }
                            )
                            Text(
                                text = if (settings.prayer.asrMethod == AsrMethod.HANAFI) {
                                    "Asr begins when an object's shadow is twice its length."
                                } else {
                                    "Asr begins when an object's shadow equals its length."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                    repo.updatePrayer(
                                                        settings.prayer.copy(highLatitudeRule = rule)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = when (settings.prayer.highLatitudeRule) {
                                com.sakinah.deen.settings.HighLatitudeRule.MIDDLE_OF_NIGHT ->
                                    "Middle of the night: when the sun never reaches the Fajr/Isha angle, the night is halved between sunset and sunrise (the most common rule)."
                                com.sakinah.deen.settings.HighLatitudeRule.SEVENTH_OF_NIGHT ->
                                    "One seventh of the night: the stricter split used where summer nights never darken; Fajr is later and Isha earlier."
                                com.sakinah.deen.settings.HighLatitudeRule.TWILIGHT_ANGLE ->
                                    "Twilight angle: the same angle is expressed as a fraction of 60°, so the split follows the season."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Manual prayer offsets (minutes)",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Shift each time to match your local mosque. Offsets apply to online " +
                                "and offline calculations and to the Azaan alarms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val adjust = appPrefs.prayerAdjustments
                        listOf(
                            "Fajr" to adjust.fajrMinutes,
                            "Sunrise" to adjust.sunriseMinutes,
                            "Dhuhr" to adjust.dhuhrMinutes,
                            "Asr" to adjust.asrMinutes,
                            "Maghrib" to adjust.maghribMinutes,
                            "Isha" to adjust.ishaMinutes
                        ).forEach { (label, value) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, modifier = Modifier.width(80.dp), fontSize = 12.sp)
                                Slider(
                                    value = value.toFloat(),
                                    onValueChange = { v ->
                                        val current = appPrefs.prayerAdjustments
                                        appPrefs.prayerAdjustments = when (label) {
                                            "Fajr" -> current.copy(fajrMinutes = v.toInt())
                                            "Sunrise" -> current.copy(sunriseMinutes = v.toInt())
                                            "Dhuhr" -> current.copy(dhuhrMinutes = v.toInt())
                                            "Asr" -> current.copy(asrMinutes = v.toInt())
                                            "Maghrib" -> current.copy(maghribMinutes = v.toInt())
                                            else -> current.copy(ishaMinutes = v.toInt())
                                        }
                                    },
                                    valueRange = -30f..30f,
                                    steps = 60,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${value}m", fontSize = 12.sp, modifier = Modifier.width(40.dp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(stringResource(R.string.settings_hijri_offset, settings.prayer.hijriOffset))
                            Slider(
                                value = settings.prayer.hijriOffset.toFloat(),
                                onValueChange = { v ->
                                    scope.launch {
                                        repo.updatePrayer(
                                            settings.prayer.copy(hijriOffset = v.toInt())
                                        )
                                    }
                                },
                                valueRange = -2f..2f,
                                steps = 3,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Travel & Car",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SakinahCard(contentPadding = 14.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Travel Mode",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Keep Qasr prayer lengths and the Safar dua active while travelling",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = travelModeActive,
                                        onCheckedChange = { enabled ->
                                            appPrefs.travelModeActive = enabled
                                            appPrefs.qasrActive = enabled
                                            travelModeActive = enabled
                                        }
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Car Bluetooth Dua al-Safar auto-play",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Play the travel dua when your vehicle audio connects",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = settings.adhan.carBluetoothAutoPlay,
                                        onCheckedChange = { enabled ->
                                            scope.launch {
                                                repo.updateAdhan(
                                                    settings.adhan.copy(carBluetoothAutoPlay = enabled)
                                                )
                                            }
                                        }
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Dua al-Safar",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Safar (travel) supplication with its Arabic text",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isDuaPlaying) {
                                                runCatching {
                                                    duaPlayer?.stop()
                                                    duaPlayer?.release()
                                                }
                                                duaPlayer = null
                                                isDuaPlaying = false
                                            } else {
                                                runCatching {
                                                    duaPlayer?.release()
                                                    duaPlayer = MediaPlayer.create(
                                                        context,
                                                        R.raw.dua_al_safar
                                                    )?.apply {
                                                        setOnCompletionListener {
                                                            isDuaPlaying = false
                                                            release()
                                                        }
                                                        start()
                                                    }
                                                    isDuaPlaying = duaPlayer != null
                                                }.onFailure {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            "Dua al-Safar audio could not be played."
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isDuaPlaying) Icons.Default.Stop
                                            else Icons.Default.PlayArrow,
                                            contentDescription = if (isDuaPlaying) {
                                                "Stop Dua al-Safar"
                                            } else {
                                                "Play Dua al-Safar"
                                            },
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsSection.APPEARANCE_READING -> {
                        var themeExpanded by remember { mutableStateOf(true) }
                        var widgetsExpanded by remember { mutableStateOf(false) }
                        var widgetDefaultStyle by remember { mutableStateOf(WidgetStyle.FROSTED_GLASS) }
                        var widgetDefaultTheme by remember { mutableStateOf(WidgetThemeMode.SYSTEM) }
                        LaunchedEffect(Unit) {
                            widgetDefaultStyle = WidgetPrefsStore.getDefaultStyle(context)
                            widgetDefaultTheme = WidgetPrefsStore.getDefaultThemeMode(context)
                        }
                        var salahExpanded by remember { mutableStateOf(false) }
                        var quranExpanded by remember { mutableStateOf(false) }
                        var hadithExpanded by remember { mutableStateOf(false) }

                        AppearanceAccordion(
                            title = "Theme & Palette",
                            summary = "${appearanceConfig.themeMode.label} · ${stringResource(appearanceConfig.palette.titleRes)}",
                            expanded = themeExpanded,
                            onToggle = { themeExpanded = !themeExpanded },
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(appearanceConfig.palette.lightPreviewPrimary)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }
                        ) {
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
                                        repo.updateAppearance(
                                            settings.appearance.copy(theme = sharedMode)
                                        )
                                    }
                                }
                            )
                            ThemePalettePicker(
                                selected = appearanceConfig.palette,
                                onSelect = { palette ->
                                    appearancePrefs.update { it.copy(palette = palette) }
                                }
                            )
                        }

                        AppearanceAccordion(
                            title = "Home Screen Widgets",
                            summary = "Default style · ${widgetDefaultStyle.label}",
                            expanded = widgetsExpanded,
                            onToggle = { widgetsExpanded = !widgetsExpanded }
                        ) {
                            Text(
                                text = "Used by widgets you add from now on. Every placed widget can still be restyled from its own settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val widgetIsDark = when (appearanceConfig.themeMode) {
                                UiThemeMode.DARK -> true
                                UiThemeMode.LIGHT -> false
                                UiThemeMode.SYSTEM -> isSystemInDarkTheme()
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(WidgetStyle.entries) { style ->
                                    WidgetStyleSwatchCard(
                                        style = style,
                                        isDark = widgetIsDark,
                                        palette = appearanceConfig.palette,
                                        selected = widgetDefaultStyle == style,
                                        onClick = {
                                            widgetDefaultStyle = style
                                            scope.launch {
                                                WidgetPrefsStore.setDefaultStyle(context, style)
                                                updateAllWidgets(context)
                                            }
                                        }
                                    )
                                }
                            }
                            Text(
                                text = "Default theme mode",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            SegmentedPillBar(
                                options = WidgetThemeMode.entries,
                                selected = widgetDefaultTheme,
                                labelProvider = { it.label },
                                onSelect = { mode ->
                                    widgetDefaultTheme = mode
                                    scope.launch {
                                        WidgetPrefsStore.setDefaultThemeMode(context, mode)
                                        updateAllWidgets(context)
                                    }
                                }
                            )
                        }

                        AppearanceAccordion(
                            title = "Salah Home Layout",
                            summary = appearanceConfig.salahHomeLayout.displayName,
                            expanded = salahExpanded,
                            onToggle = { salahExpanded = !salahExpanded }
                        ) {
                            Text(
                                text = stringResource(R.string.salah_home_layout_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val salahLayoutState = rememberLazyListState()
                            LaunchedEffect(Unit) {
                                val index = SalahHomeLayout.entries.indexOf(appearanceConfig.salahHomeLayout)
                                if (index >= 0) salahLayoutState.animateScrollToItem(index)
                            }
                            LazyRow(
                                state = salahLayoutState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(SalahHomeLayout.entries) { layout ->
                                    PortraitTemplateCard(
                                        label = layout.displayName,
                                        selected = appearanceConfig.salahHomeLayout == layout,
                                        onClick = { appearancePrefs.updateSalahHomeLayout(layout) }
                                    ) {
                                        SalahLayoutPreview(layout)
                                    }
                                }
                            }
                        }

                        AppearanceAccordion(
                            title = "Quran Reading Layout",
                            summary = appearanceConfig.quranReadingLayout.localizedLabel(),
                            expanded = quranExpanded,
                            onToggle = { quranExpanded = !quranExpanded }
                        ) {
                            Text(
                                text = stringResource(R.string.quran_reading_layout_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val quranLayoutState = rememberLazyListState()
                            LaunchedEffect(Unit) {
                                val index = QuranReadingLayout.entries.indexOf(appearanceConfig.quranReadingLayout)
                                if (index >= 0) quranLayoutState.animateScrollToItem(index)
                            }
                            LazyRow(
                                state = quranLayoutState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(QuranReadingLayout.entries) { layout ->
                                    PortraitTemplateCard(
                                        label = layout.localizedLabel(),
                                        selected = appearanceConfig.quranReadingLayout == layout,
                                        onClick = { appearancePrefs.updateQuranReadingLayout(layout) }
                                    ) {
                                        QuranLayoutPreview(layout)
                                    }
                                }
                            }
                        }

                        AppearanceAccordion(
                            title = "Hadith Reading Layout",
                            summary = appearanceConfig.hadithReadingLayout.displayName,
                            expanded = hadithExpanded,
                            onToggle = { hadithExpanded = !hadithExpanded }
                        ) {
                            Text(
                                text = stringResource(R.string.hadith_reading_layout_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val hadithLayoutState = rememberLazyListState()
                            LaunchedEffect(Unit) {
                                val index = HadithReadingLayout.entries.indexOf(appearanceConfig.hadithReadingLayout)
                                if (index >= 0) hadithLayoutState.animateScrollToItem(index)
                            }
                            LazyRow(
                                state = hadithLayoutState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(HadithReadingLayout.entries) { layout ->
                                    PortraitTemplateCard(
                                        label = layout.displayName,
                                        selected = appearanceConfig.hadithReadingLayout == layout,
                                        onClick = { appearancePrefs.updateHadithReadingLayout(layout) }
                                    ) {
                                        HadithLayoutPreview(layout)
                                    }
                                }
                            }
                        }

                        SakinahCard(contentPadding = 14.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                                Text(stringResource(R.string.settings_arabic_font_size, settings.appearance.arabicFontSize))
                                Slider(
                                    value = settings.appearance.arabicFontSize.toFloat(),
                                    onValueChange = { v ->
                                        scope.launch {
                                            repo.updateAppearance(
                                                settings.appearance.copy(arabicFontSize = v.toInt())
                                            )
                                        }
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
                                            appearancePrefs.update {
                                                it.copy(focusModeHideDistractions = enabled)
                                            }
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
                                            appearancePrefs.update {
                                                it.copy(autoHideBarsOnScroll = enabled)
                                            }
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
                                    Text(stringResource(R.string.settings_rerun_onboarding))
                                }
                            }
                        }
                    }

                    SettingsSection.QURAN_OFFLINE -> {
                        val quranOfflineStatus by remember(islamicRepository) {
                            islamicRepository.observeOfflineStatus()
                        }.collectAsStateWithLifecycle<QuranOfflineStatus?>(initialValue = null)
                        quranOfflineStatus?.let { status ->
                            QuranOfflineStatusBanner(status = status, showPackDetails = true)
                        }

                        Text("Quran Translation", style = MaterialTheme.typography.titleMedium)
                        val currentTranslationLabel = translationCatalog
                            .firstOrNull { it.id == appPrefs.quranTranslationId }
                            ?.let { resource ->
                                "${resource.name} (${resource.languageName.ifBlank { resource.isoCode }})"
                            }
                            ?: appPrefs.quranTranslationLanguage
                        Text(
                            "Current: $currentTranslationLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                if (translationCatalog.isEmpty()) {
                                    catalogReloadNonce++
                                } else {
                                    showTranslationSheet = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoadingCatalog) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("  Loading all languages…", fontSize = 12.sp)
                            } else {
                                Text(
                                    if (translationCatalog.isEmpty()) {
                                        "Load translation catalog"
                                    } else {
                                        "Choose translation · ${translationCatalog.size} editions"
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (translationCatalog.isEmpty() && !isLoadingCatalog) {
                            Text(
                                text = "The full Quran.com translation catalog could not be loaded. Check your connection and retry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Text("Arabic Script", style = MaterialTheme.typography.titleMedium)
                        SegmentedPillBar(
                            options = QuranArabicScript.entries.map { it.label },
                            selectedIndex = appPrefs.quranArabicScript.ordinal,
                            onSelect = { index ->
                                appPrefs.quranArabicScript = QuranArabicScript.entries[index]
                            }
                        )
                        Text(
                            text = "Uthmani is the standard Madani script. IndoPak and Imlaei are cached with each downloaded surah.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

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
                        }) { Text(stringResource(R.string.settings_clear_cache)) }

                        Text("Tafseer Packs", style = MaterialTheme.typography.titleSmall)
                        tafseerPackages.forEach { pack ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pack.author, fontSize = 12.sp)
                                    Text(
                                        text = "${pack.languageCode.uppercase()} · Quran.com v4 · ${pack.statusLabel}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (downloadingPackId == pack.id) {
                                        LinearProgressIndicator(
                                            progress = { packDownloadProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                        )
                                    }
                                }
                                if (pack.downloadedChapters > 0) {
                                    TextButton(
                                        enabled = downloadingPackId == null,
                                        onClick = {
                                            scope.launch {
                                                tafseerRepository.removeTafseerPackage(pack.id)
                                                tafseerRepository.refreshDownloadStatus()
                                                snackbarHostState.showSnackbar(
                                                    "${pack.author} removed."
                                                )
                                            }
                                        }
                                    ) {
                                        Text(
                                            "Remove",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    TextButton(
                                        enabled = downloadingPackId == null,
                                        onClick = {
                                            downloadingPackId = pack.id
                                            packDownloadProgress = 0f
                                            scope.launch {
                                                val result = tafseerRepository
                                                    .downloadTafseerPackage(pack.id) { progress ->
                                                        packDownloadProgress = progress
                                                    }
                                                tafseerRepository.refreshDownloadStatus()
                                                downloadingPackId = null
                                                packDownloadProgress = 0f
                                                snackbarHostState.showSnackbar(
                                                    result.fold(
                                                        onSuccess = { chapters ->
                                                            "${pack.author}: $chapters surahs cached offline."
                                                        },
                                                        onFailure = { error ->
                                                            "Tafseer download failed: " +
                                                                (error.message ?: "network error")
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        if (downloadingPackId == pack.id) {
                                            Text(
                                                "${(packDownloadProgress * 100).toInt()}%",
                                                fontSize = 11.sp
                                            )
                                        } else {
                                            Text(
                                                if (pack.isPartiallyDownloaded) "Resume" else "Download",
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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

    if (showTranslationSheet) {
        val filtered = remember(translationCatalog, translationQuery) {
            val query = translationQuery.trim()
            if (query.isBlank()) {
                translationCatalog
            } else {
                translationCatalog.filter { resource ->
                    resource.name.contains(query, ignoreCase = true) ||
                        resource.authorName.contains(query, ignoreCase = true) ||
                        resource.languageName.contains(query, ignoreCase = true) ||
                        resource.isoCode.contains(query, ignoreCase = true)
                }
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                showTranslationSheet = false
                translationQuery = ""
            },
            sheetState = translationSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quran translation · all languages",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = translationQuery,
                    onValueChange = { translationQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.settings_search_language)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = "No translations match “$translationQuery”.",
                        modifier = Modifier.padding(vertical = 18.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            filtered,
                            key = { "${it.id}_${it.isoCode}_${it.editionKey}" }
                        ) { resource ->
                            val selected = resource.id == appPrefs.quranTranslationId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        appPrefs.quranTranslationId = resource.id
                                        appPrefs.quranTranslationLanguage = resource.languageName
                                            .ifBlank { resource.name }
                                        appPrefs.quranTranslationEditionKey = resource.editionKey
                                        appPrefs.quranTranslationSource = resource.source
                                        scope.launch {
                                            repo.updateAppearance(
                                                settings.appearance.copy(
                                                    translationId = resource.id,
                                                    translationLanguage = resource.isoCode
                                                        .ifBlank { settings.appearance.translationLanguage },
                                                    translationName = resource.languageName
                                                        .ifBlank { resource.name }
                                                )
                                            )
                                        }
                                        showTranslationSheet = false
                                        translationQuery = ""
                                    }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = resource.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = listOf(
                                            resource.languageName.ifBlank { resource.isoCode },
                                            resource.authorName
                                        ).filter { it.isNotBlank() }.joinToString(" · "),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Text(
                                        text = "Selected",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitTemplateCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(96.dp)
    ) {
        Box(
            modifier = Modifier
                .height(170.dp)
                .aspectRatio(0.55f)
                .shadow(
                    elevation = if (selected) 8.dp else 0.dp,
                    shape = shape,
                    spotColor = MaterialTheme.colorScheme.primary,
                    ambientColor = MaterialTheme.colorScheme.primary
                )
                .clip(shape)
                .background(
                    if (selected) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape
                )
                .clickable(onClick = onClick)
        ) {
            preview()
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SalahLayoutPreview(layout: SalahHomeLayout) {
    val style = when {
        layout.name.contains("BENTO") -> 1
        layout.name.contains("MINIMAL") || layout.name.contains("LIST") || layout.name.contains("COMPACT") -> 2
        else -> 0
    }
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxSize().padding(6.dp)) {
        when (style) {
            0 -> {
                Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                    val r = size.minDimension / 2f - 2f
                    drawArc(color = onSurface.copy(alpha = 0.2f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 3f))
                    drawArc(color = primary, startAngle = -90f, sweepAngle = 220f, useCenter = false, style = Stroke(width = 3f))
                }
                Column(Modifier.fillMaxWidth().padding(top = 36.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(4) {
                        Box(Modifier.fillMaxWidth(0.9f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.3f)))
                    }
                    repeat(5) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(4.dp).clip(CircleShape).background(primary))
                            Box(Modifier.weight(0.5f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.35f)))
                            Box(Modifier.weight(0.3f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.2f)))
                        }
                    }
                }
            }
            1 -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(onSurface.copy(alpha = 0.4f)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(6.dp)).background(primary.copy(alpha = 0.25f)))
                        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(6.dp)).background(primary.copy(alpha = 0.25f)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp)).background(onSurface.copy(alpha = 0.2f)))
                        Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp)).background(onSurface.copy(alpha = 0.2f)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp)).background(onSurface.copy(alpha = 0.2f)))
                        Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp)).background(onSurface.copy(alpha = 0.2f)))
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(onSurface.copy(alpha = 0.45f)))
                    repeat(6) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).border(1.dp, onSurface.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.25f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranLayoutPreview(layout: QuranReadingLayout) {
    val style = when (layout) {
        QuranReadingLayout.DIVIDER_HAIRLINE -> 0
        QuranReadingLayout.CONTINUOUS_MUSHAF -> 1
        QuranReadingLayout.SPLIT_COLUMN -> 2
        QuranReadingLayout.THUMB_BOTTOM_DOCK -> 3
    }
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val gold = MaterialTheme.colorScheme.tertiary
    Box(Modifier.fillMaxSize().padding(6.dp)) {
        when (style) {
            0 -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) {
                        Column(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth(0.6f).height(5.dp).align(Alignment.End).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.45f)))
                            Spacer(Modifier.height(2.dp))
                            Box(Modifier.fillMaxWidth(0.9f).height(3.dp).align(Alignment.Start).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.25f)))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }
            }
            1 -> {
                Box(Modifier.fillMaxSize().border(1.dp, onSurface.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(4.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(7) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(1.dp)).background(onSurface.copy(alpha = 0.35f)))
                                if (it % 2 == 0) Box(Modifier.size(5.dp).clip(CircleShape).background(gold))
                            }
                        }
                    }
                }
            }
            2 -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.weight(0.48f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(onSurface.copy(alpha = 0.18f)))
                            Box(Modifier.weight(0.52f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(primary.copy(alpha = 0.28f)))
                        }
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) {
                        Column(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth(0.75f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.4f)))
                            Spacer(Modifier.height(2.dp))
                            Box(Modifier.fillMaxWidth(0.95f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.25f)))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(primary.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

@Composable
private fun HadithLayoutPreview(layout: HadithReadingLayout) {
    val style = when {
        layout.name.contains("ENGLISH") -> 1
        layout.name.contains("BILINGUAL") || layout.name.contains("CARD") || layout.name.contains("SANAD") -> 2
        else -> 0
    }
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxSize().padding(6.dp)) {
        when (style) {
            0 -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.fillMaxWidth(0.5f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(primary.copy(alpha = 0.3f)))
                    repeat(4) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.4f)))
                                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.3f)))
                            }
                            Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.2f)))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }
            }
            1 -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(onSurface.copy(alpha = 0.12f)).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.5f)))
                            Box(Modifier.fillMaxWidth(0.85f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.25f)))
                            Box(Modifier.align(Alignment.End).width(24.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(primary.copy(alpha = 0.35f)))
                        }
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(onSurface.copy(alpha = 0.12f)).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.fillMaxWidth(0.7f).height(4.dp).align(Alignment.End).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.45f)))
                            Box(Modifier.fillMaxWidth(0.5f).height(4.dp).align(Alignment.End).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.3f)))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.25f)))
                            Box(Modifier.fillMaxWidth(0.9f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(onSurface.copy(alpha = 0.25f)))
                        }
                    }
                }
            }
        }
    }
}



/** Selectable preview card used by the Salah / Quran / Hadith layout rows. */
@Composable
fun AppearanceAccordion(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "accordion_chevron")
    SakinahCard(contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    leading?.invoke()
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            }
        }
    }
}

/** A widget skin shown with its real surface, accent and text swatches. */
@Composable
private fun WidgetStyleSwatchCard(
    style: WidgetStyle,
    isDark: Boolean,
    palette: com.sakinah.deen.ui.theme.SakinahPalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = resolveWidgetColors(style, isDark, PrayerWindow.DHUHR, palette)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(colors.background),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(colors.accent) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .width(190.dp)
            .heightIn(min = 104.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = style.label,
                color = Color(colors.primaryText),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = style.description,
                color = Color(colors.secondaryText),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                WidgetSwatchDot(color = Color(colors.background), bordered = true)
                WidgetSwatchDot(color = Color(colors.accent))
                WidgetSwatchDot(color = Color(colors.primaryText))
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (selected) "●" else "○",
                    color = Color(colors.accent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WidgetSwatchDot(color: Color, bordered: Boolean = false) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (bordered) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    Modifier
                }
            )
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
    else -> "$bytes bytes"
}

/** Mirrors an Android override into the shared settings store (closest equivalent authority). */
private fun sharedMethodFor(method: AppMethod): CalculationMethod? = when (method) {
    AppMethod.UMM_AL_QURA, AppMethod.QATAR, AppMethod.DUBAI -> CalculationMethod.UMM_AL_QURA
    AppMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN
    AppMethod.KARACHI -> CalculationMethod.KARACHI
    AppMethod.NORTH_AMERICA -> CalculationMethod.ISNA
    AppMethod.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MWL
    else -> null
}
