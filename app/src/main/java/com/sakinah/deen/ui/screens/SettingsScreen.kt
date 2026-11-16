package com.sakinah.deen.ui.screens

import android.Manifest
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.SakinahPalette
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
    onRerunOnboarding: () -> Unit = {},
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
    val islamicRepository = rememberInject<IslamicDataRepository>()
    val tafseerPackages by tafseerRepository.packages.collectAsStateWithLifecycle(initialValue = emptyList())
    var downloadingPackId by remember { mutableStateOf<String?>(null) }
    var packDownloadProgress by remember { mutableStateOf(0f) }

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
                                    ?: "Automatic (by country)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Calculation method") },
                                supportingText = {
                                    Text("Automatic uses your country's Islamic authority.")
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
                                    text = { Text("Automatic (by country)") },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Hijri offset: ${settings.prayer.hijriOffset}")
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
                                            repo.updateAppearance(
                                                settings.appearance.copy(theme = sharedMode)
                                            )
                                        }
                                    }
                                )

                                Text(
                                    text = stringResource(R.string.salah_home_layout_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                                        LayoutChoiceCard(
                                            title = layout.displayName,
                                            subtitle = layout.subtitle,
                                            selected = appearanceConfig.salahHomeLayout == layout,
                                            onClick = { appearancePrefs.updateSalahHomeLayout(layout) }
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.quran_reading_layout_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                                        LayoutChoiceCard(
                                            title = layout.displayName,
                                            subtitle = layout.subtitle,
                                            selected = appearanceConfig.quranReadingLayout == layout,
                                            onClick = { appearancePrefs.updateQuranReadingLayout(layout) }
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.hadith_reading_layout_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                                        LayoutChoiceCard(
                                            title = layout.displayName,
                                            subtitle = layout.subtitle,
                                            selected = appearanceConfig.hadithReadingLayout == layout,
                                            onClick = { appearancePrefs.updateHadithReadingLayout(layout) }
                                        )
                                    }
                                }

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
                                            onClick = {
                                                appearancePrefs.update { it.copy(palette = palette) }
                                            },
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
                                    Text("Re-run Personalized Onboarding")
                                }
                            }
                        }
                    }

                    SettingsSection.QURAN_OFFLINE -> {
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
                                        text = buildString {
                                            append(pack.languageCode.uppercase())
                                            append(" · Quran.com v4 · ")
                                            append("${pack.downloadedChapters}/${TafseerPackage.TOTAL_CHAPTERS} surahs")
                                            if (pack.isDownloaded) append(" · Downloaded")
                                        },
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
                    placeholder = { Text("Search language, translator or name") },
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

/** Selectable preview card used by the Salah / Quran / Hadith layout rows. */
@Composable
private fun LayoutChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .width(190.dp)
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
}
