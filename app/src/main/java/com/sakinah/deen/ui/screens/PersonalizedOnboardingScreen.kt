package com.sakinah.deen.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.model.QuranTranslationResource
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.data.repository.TafseerPackage
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.data.prayer.SunniMethodMapper
import com.sakinah.deen.data.prayer.englishName
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.ui.components.HomeCity
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.PrayerGuideGender
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.SakinahTheme
import com.sakinah.deen.ui.theme.ThemeMode
import com.sakinah.deen.ui.theme.UserAppearanceConfig
import com.sakinah.deen.ui.theme.UserObjective
import com.sakinah.deen.util.TimeZoneResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.GlobalContext
import java.util.Locale

private const val TOTAL_STEPS = 8
private const val ARABIC_FONT_MIN = 18
private const val ARABIC_FONT_MAX = 36
private const val TRANSLATION_FONT_MIN = 12
private const val TRANSLATION_FONT_MAX = 24

private val LogoGreen = Color(0xFF0F6B5E)
private val LogoIvory = Color(0xFFF7F8F5)
private val LogoGold = Color(0xFFC5A059)

/** Goals shown in Step 1. */
private data class OnboardingGoal(
    val label: String,
    val objective: UserObjective,
    val icon: ImageVector
)

private val GOALS = listOf(
    OnboardingGoal("Learn more about Islam", UserObjective.ALL_IN_ONE, Icons.Default.Explore),
    OnboardingGoal("Become more proficient in prayers", UserObjective.PRAYER_COMPASS, Icons.Default.Mosque),
    OnboardingGoal("Find references in Quran & Hadith", UserObjective.HADITH_SCHOLAR, Icons.AutoMirrored.Filled.LibraryBooks),
    OnboardingGoal("Memorize Quran or Hadith", UserObjective.QURAN_STUDY, Icons.AutoMirrored.Filled.MenuBook),
    OnboardingGoal("Learn daily practices of Islam", UserObjective.PRAYER_COMPASS, Icons.Default.SelfImprovement)
)

private data class LanguageOption(
    val displayName: String,
    val isoCode: String
)

private data class EditionOption(
    val label: String,
    val languageName: String,
    val isoCode: String,
    val quranTranslation: QuranTranslationResource?
)

@Composable
fun SakinahAnimatedLogo(
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    onAssembled: () -> Unit = {}
) {
    val crescentAnim = remember { Animatable(0f) }
    val domeAnim = remember { Animatable(0f) }
    val quranAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { crescentAnim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch {
            delay(450)
            domeAnim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(900)
            quranAnim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        delay(2100)
        onAssembled()
    }

    val crescentPath = remember {
        PathParser().parsePathString(
            "M55,27 C37,29 27,44 31,60 C34,71 45,76 58,74 C67,72 73,66 76,58 C70,66 59,69 49,65 C39,60 36,48 41,38 C44,32 49,28 55,27 Z"
        ).toPath()
    }
    val domePath = remember {
        PathParser().parsePathString(
            "M57,33 C54,37 47,41 47,49 L47,52 L45,52 L45,59 C49,62 65,62 69,59 L69,52 L67,52 L67,49 C67,41 60,37 57,33 Z"
        ).toPath()
    }
    val qiblaOuterPath = remember {
        PathParser().parsePathString("M57,41 L61,49 L57,57 L53,49 Z").toPath()
    }
    val qiblaInnerPath = remember {
        PathParser().parsePathString("M57,46.5 L59,49 L57,51.5 L55,49 Z").toPath()
    }
    val quranTopPath = remember {
        PathParser().parsePathString(
            "M31,68 C40,67 48,70 54,75 C60,70 68,67 77,68 C69,70 61,74 54,79 C47,74 39,70 31,68 Z"
        ).toPath()
    }
    val quranBottomPath = remember {
        PathParser().parsePathString(
            "M35,72 C42,72 49,75 54,80 C59,75 66,72 73,72 C66,74 60,78 54,82 C48,78 42,74 35,72 Z"
        ).toPath()
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(26.dp))
            .background(LogoGreen)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleFactor = this.size.minDimension / 108f
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                val cProg = crescentAnim.value
                translate(left = (1f - cProg) * -22f) {
                    drawPath(crescentPath, color = LogoIvory.copy(alpha = cProg))
                }
                val dProg = domeAnim.value
                translate(top = (1f - dProg) * 18f) {
                    drawPath(domePath, color = LogoIvory.copy(alpha = dProg))
                    drawPath(qiblaOuterPath, color = LogoGreen.copy(alpha = dProg))
                    drawPath(qiblaInnerPath, color = LogoIvory.copy(alpha = dProg))
                }
                val qProg = quranAnim.value
                translate(top = (1f - qProg) * 20f) {
                    drawPath(quranTopPath, color = LogoGold.copy(alpha = qProg))
                    drawPath(quranBottomPath, color = LogoGold.copy(alpha = qProg))
                }
            }
        }
    }
}

private val UserAppearanceConfigSaver = listSaver<UserAppearanceConfig, String>(
    save = { config ->
        listOf(
            config.userName,
            config.experienceLevel,
            config.objective.name,
            config.themeMode.name,
            config.palette.name,
            config.fontScale.name,
            config.focusModeHideDistractions.toString(),
            config.autoHideBarsOnScroll.toString(),
            config.prayerGuideGender.name,
            config.salahHomeLayout.name,
            config.quranReadingLayout.name,
            config.hadithReadingLayout.name,
            config.onboardingCompleted.toString(),
            config.arabicFontSize.toString(),
            config.translationFontSize.toString()
        )
    },
    restore = { values ->
        UserAppearanceConfig(
            userName = values[0],
            experienceLevel = values[1],
            objective = runCatching { UserObjective.valueOf(values[2]) }.getOrDefault(UserObjective.ALL_IN_ONE),
            themeMode = runCatching { ThemeMode.valueOf(values[3]) }.getOrDefault(ThemeMode.SYSTEM),
            palette = runCatching { SakinahPalette.valueOf(values[4]) }.getOrDefault(SakinahPalette.EMERALD_SANCTUARY),
            fontScale = runCatching { FontScalePreset.valueOf(values[5]) }.getOrDefault(FontScalePreset.STANDARD),
            focusModeHideDistractions = values[6].toBoolean(),
            autoHideBarsOnScroll = values[7].toBoolean(),
            prayerGuideGender = runCatching { PrayerGuideGender.valueOf(values[8]) }.getOrDefault(PrayerGuideGender.MALE),
            salahHomeLayout = values.getOrNull(9)
                ?.let { runCatching { SalahHomeLayout.valueOf(it) }.getOrNull() }
                ?: SalahHomeLayout.CLASSIC_COMPACT,
            quranReadingLayout = QuranReadingLayout.fromStoredName(values.getOrNull(10))
                ?: QuranReadingLayout.THUMB_BOTTOM_DOCK,
            hadithReadingLayout = values.getOrNull(11)
                ?.let { runCatching { HadithReadingLayout.valueOf(it) }.getOrNull() }
                ?: HadithReadingLayout.COMPACT_DROPDOWN,
            onboardingCompleted = values.getOrNull(15)?.toBoolean()
                ?: values.getOrNull(12)?.toBoolean()
                ?: false,
            arabicFontSize = values.getOrNull(13)?.toIntOrNull()
                ?.coerceIn(ARABIC_FONT_MIN, ARABIC_FONT_MAX)
                ?: 24,
            translationFontSize = values.getOrNull(14)?.toIntOrNull()
                ?.coerceIn(TRANSLATION_FONT_MIN, TRANSLATION_FONT_MAX)
                ?: 16
        )
    }
)

private data class PermissionState(
    val location: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryOptimizationIgnored: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizedOnboardingScreen(
    modifier: Modifier = Modifier,
    initialConfig: UserAppearanceConfig = UserAppearanceConfig(),
    onConfigPreviewChange: (UserAppearanceConfig) -> Unit = {},
    onComplete: (UserAppearanceConfig) -> Unit,
    onDismiss: (() -> Unit)? = null,
    appPrefs: SakinahPreferences? = null,
    locationProvider: UserLocationProvider? = null
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var config by rememberSaveable(initialConfig, stateSaver = UserAppearanceConfigSaver) {
        mutableStateOf(initialConfig)
    }
    var showGreeting by rememberSaveable { mutableStateOf(false) }
    var selectedGoalIndices by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var languageSearch by rememberSaveable { mutableStateOf("") }
    var selectedLanguageIso by rememberSaveable { mutableStateOf("") }
    var selectedLanguageName by rememberSaveable { mutableStateOf("") }
    var selectedEditionKey by rememberSaveable { mutableStateOf("") }
    var selectedEditionLabel by rememberSaveable { mutableStateOf("") }
    var translationResources by remember { mutableStateOf<List<QuranTranslationResource>>(emptyList()) }
    var quranRepository by remember { mutableStateOf<IslamicDataRepository?>(null) }
    var translationDownloadLabel by remember { mutableStateOf<String?>(null) }
    var translationDownloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var tafseerRepository by remember { mutableStateOf<TafseerDownloadRepository?>(null) }
    var tafseerPackages by remember { mutableStateOf<List<TafseerPackage>>(emptyList()) }
    var selectedTafsirId by rememberSaveable { mutableIntStateOf(0) }
    var selectedTafsirTitle by rememberSaveable { mutableStateOf("") }
    var tafsirDownloadProgress by remember { mutableStateOf(0f) }
    var tafsirDownloading by remember { mutableStateOf(false) }
    var tafsirDownloadError by remember { mutableStateOf<String?>(null) }
    var availableLanguages by remember { mutableStateOf<List<LanguageOption>>(emptyList()) }
    var permissionTick by remember { mutableIntStateOf(0) }
    var prefsTick by remember { mutableIntStateOf(0) }
    var showCityPicker by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val quranReaderPrefs = remember(context) { QuranReaderPreferences(context) }

    BackHandler(enabled = step > 0) { step-- }

    fun updateConfig(updated: UserAppearanceConfig) {
        config = updated
        onConfigPreviewChange(updated)
    }

    LaunchedEffect(Unit) {
        val repo = runCatching { GlobalContext.get().get<IslamicDataRepository>() }.getOrNull()
        quranRepository = repo
        val fetched = runCatching { repo?.fetchTranslationCatalog().orEmpty() }.getOrDefault(emptyList())
        val resources = fetched.ifEmpty { defaultTranslationResources() }
        translationResources = resources
        availableLanguages = buildLanguageOptions(resources)
        // Preselect existing language if present
        val existingIso = appPrefs?.quranTranslationLanguage?.takeIf { it.isNotBlank() }
        if (selectedLanguageIso.isBlank() && !existingIso.isNullOrBlank()) {
            val match = availableLanguages.firstOrNull {
                it.displayName.equals(existingIso, true) || it.isoCode.equals(existingIso, true)
            }
            if (match != null) {
                selectedLanguageIso = match.isoCode
                selectedLanguageName = match.displayName
            }
        }
    }

    LaunchedEffect(Unit) {
        val repo = runCatching { GlobalContext.get().get<TafseerDownloadRepository>() }.getOrNull()
        tafseerRepository = repo
        repo?.packages?.collect { packages -> tafseerPackages = packages }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissions = remember(permissionTick) { readPermissionState(context) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionTick++
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                val resolved = (locationProvider ?: UserLocationProvider(context)).getFreshOrCachedLocation()
                val label = if (resolved.countryName.isNotBlank()) {
                    "${resolved.cityName}, ${resolved.countryName}"
                } else {
                    resolved.cityName
                }
                val zone = TimeZoneResolver.resolveZoneIdString(
                    cityLabel = label,
                    countryCode = resolved.countryCode,
                    longitude = resolved.longitude
                )
                appPrefs?.saveGpsLocation(
                    latitude = resolved.latitude,
                    longitude = resolved.longitude,
                    label = label,
                    countryCode = resolved.countryCode,
                    timeZoneId = zone
                )
                prefsTick++
            }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> permissionTick++ }
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissionTick++ }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissionTick++ }

    fun applyLanguageAndEdition(language: LanguageOption, edition: EditionOption?) {
        val iso = language.isoCode.ifBlank { "en" }
        val langName = language.displayName.ifBlank { "English" }
        selectedLanguageIso = iso
        selectedLanguageName = langName
        selectedEditionKey = edition?.quranTranslation?.editionKey ?: edition?.label ?: ""
        selectedEditionLabel = edition?.label ?: ""

        val quranRes = edition?.quranTranslation
        appPrefs?.quranTranslationLanguage = langName
        if (quranRes != null) {
            appPrefs?.quranTranslationId = quranRes.id
            appPrefs?.quranTranslationEditionKey = quranRes.editionKey
            appPrefs?.quranTranslationSource = quranRes.source
            // The reader observes this exact preference flow; appPrefs alone is not enough.
            quranReaderPrefs.setSelectedTranslation(
                translationId = quranRes.id,
                languageName = langName,
                isoCode = iso,
                editionKey = quranRes.editionKey,
                source = quranRes.source
            )
            // Start the offline download here instead of making the user wait for the
            // Quran screen. Selecting another edition cancels the previous download.
            translationDownloadJob?.cancel()
            val repo = quranRepository
            if (repo != null) {
                translationDownloadJob = scope.launch {
                    translationDownloadLabel = "Preparing $langName translation…"
                    val result = repo.downloadTranslationPack(
                        translationId = quranRes.id,
                        editionKey = quranRes.editionKey,
                        source = quranRes.source
                    ) { done, total ->
                        translationDownloadLabel = "$langName translation $done/$total surahs"
                    }
                    translationDownloadLabel = if (result.isSuccess) {
                        "$langName translation ready offline"
                    } else {
                        "$langName translation will finish when you open the Quran"
                    }
                }
            }
        }
        // WBW - map to iso code; fallback to en
        quranReaderPrefs.wbwLanguage = normalizeWbwLanguage(iso)
        // Hadith - use iso2-like code; fallback to eng for English
        appPrefs?.hadithLanguageCode = normalizeHadithLanguage(iso)
        // Tafseer - map to language code; fallback to en
        appPrefs?.selectedTafseerLanguage = normalizeTafsirLanguage(iso)
        val tafsirId = defaultTafsirIdForLanguage(normalizeTafsirLanguage(iso))
        if (tafsirId != null && selectedTafsirId <= 0) appPrefs?.selectedTafsirId = tafsirId
    }

    fun selectTafsirBook(pkg: TafseerPackage) {
        selectedTafsirId = pkg.tafsirId
        selectedTafsirTitle = pkg.title
        tafsirDownloadError = null
        tafsirDownloadProgress = 0f
        quranReaderPrefs.setSelectedTafsir(pkg.tafsirId, pkg.languageCode, pkg.title)
        appPrefs?.selectedTafsirId = pkg.tafsirId
        appPrefs?.selectedTafseerLanguage = pkg.languageCode

        val repo = tafseerRepository ?: run {
            tafsirDownloadError = "Tafsir service is unavailable. You can retry or continue."
            return
        }
        if (pkg.isDownloaded) {
            tafsirDownloading = false
            tafsirDownloadProgress = 1f
            return
        }
        tafsirDownloading = true
        repo.launchTafseerPackageDownload(
            tafsirId = pkg.tafsirId,
            prioritySurah = quranReaderPrefs.lastReadPosition?.surahNumber ?: 1,
            onProgress = { progress -> tafsirDownloadProgress = progress },
            onComplete = { result ->
                tafsirDownloading = false
                tafsirDownloadProgress = if (result.isSuccess) 1f else 0f
                tafsirDownloadError = if (result.isSuccess) {
                    null
                } else {
                    "Couldn't download ${pkg.title}. Retry or continue."
                }
            }
        )
    }

    val editionsForSelectedLanguage = remember(translationResources, selectedLanguageIso, selectedLanguageName) {
        editionsForLanguage(translationResources, selectedLanguageIso, selectedLanguageName)
    }
    val selectedTafsirLanguageCode = normalizeTafsirLanguage(selectedLanguageIso)
    val availableTafsirPackages = remember(tafseerPackages, selectedTafsirLanguageCode, selectedLanguageName) {
        tafseerPackages.filter { pkg ->
            pkg.languageCode.equals(selectedTafsirLanguageCode, true) ||
                pkg.languageName.equals(selectedLanguageName, true)
        }
    }
    val selectedTafsirPackage = availableTafsirPackages.firstOrNull { it.tafsirId == selectedTafsirId }

    val darkTheme = when (config.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val accentSoft = MaterialTheme.colorScheme.primaryContainer
    val onAccentSoft = MaterialTheme.colorScheme.onPrimaryContainer

    fun finish() {
        val saved = config.copy(onboardingCompleted = true)
        appPrefs?.alarmsEnabled = true
        onComplete(saved)
    }

    SakinahTheme(
        darkTheme = darkTheme,
        palette = config.palette,
        fontScaleMultiplier = config.fontScale.multiplier
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val bodyMaxHeight = (maxHeight * 0.52f).coerceAtLeast(200.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
            ) {
                HeroZone(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    step = step,
                    userName = config.userName,
                    showGreeting = showGreeting,
                    selectedLanguageName = selectedLanguageName,
                    accent = accent,
                    accentSoft = accentSoft,
                    onAccentSoft = onAccentSoft,
                    onAssembled = { showGreeting = true },
                    onBack = { if (step > 0) step-- },
                    onSkip = { if (onDismiss != null) onDismiss() else finish() }
                )

                OnboardingSheet(
                    step = step,
                    bodyMaxHeight = bodyMaxHeight,
                    sheetColor = MaterialTheme.colorScheme.surface,
                    accent = accent,
                    accentSoft = accentSoft
                ) { currentStep ->
                    when (currentStep) {
                        0 -> WelcomeStep(
                            config = config,
                            showGreeting = showGreeting,
                            accent = accent,
                            onConfigChange = ::updateConfig
                        )
                        1 -> AppearanceStep(
                            draft = config,
                            accent = accent,
                            accentSoft = accentSoft,
                            onDraftChange = ::updateConfig
                        )
                        2 -> GoalsStep(
                            userName = config.userName,
                            selectedGoalIndices = selectedGoalIndices,
                            accent = accent,
                            accentSoft = accentSoft,
                            onAccent = onAccent,
                            onToggle = { index ->
                                selectedGoalIndices = if (index in selectedGoalIndices) {
                                    selectedGoalIndices - index
                                } else {
                                    selectedGoalIndices + index
                                }
                                updateConfig(config.copy(objective = objectiveForGoals(selectedGoalIndices)))
                            },
                            onSelectAll = {
                                selectedGoalIndices = GOALS.indices.toSet()
                                updateConfig(config.copy(objective = UserObjective.ALL_IN_ONE))
                            }
                        )
                        3 -> LanguageStep(
                            search = languageSearch,
                            onSearchChange = { languageSearch = it },
                            languages = availableLanguages,
                            selectedIso = selectedLanguageIso,
                            accent = accent,
                            accentSoft = accentSoft,
                            onSelect = { lang ->
                                selectedLanguageIso = lang.isoCode
                                selectedLanguageName = lang.displayName
                                selectedEditionKey = ""
                                selectedEditionLabel = ""
                            }
                        )
                        4 -> EditionStep(
                            languageName = selectedLanguageName.ifBlank { "English" },
                            editions = editionsForSelectedLanguage,
                            selectedEditionKey = selectedEditionKey,
                            accent = accent,
                            accentSoft = accentSoft,
                            onSelect = { edition ->
                                val lang = LanguageOption(selectedLanguageName.ifBlank { "English" }, selectedLanguageIso.ifBlank { "en" })
                                applyLanguageAndEdition(lang, edition)
                            }
                        )
                        5 -> TafsirStep(
                            languageName = selectedLanguageName.ifBlank { "English" },
                            packages = availableTafsirPackages,
                            selectedTafsirId = selectedTafsirId,
                            downloading = tafsirDownloading,
                            progress = tafsirDownloadProgress,
                            error = tafsirDownloadError,
                            accent = accent,
                            accentSoft = accentSoft,
                            onSelect = ::selectTafsirBook,
                            onRetry = ::selectTafsirBook,
                            onDownloadLater = { step = 6 }
                        )
                        6 -> PermissionsStep(
                            permissions = permissions,
                            accent = accent,
                            onAccent = onAccent,
                            accentSoft = accentSoft,
                            onAllowLocation = {
                                locationLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            },
                            onEnterCityManually = { showCityPicker = true },
                            onAllowNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    permissionTick++
                                }
                            },
                            onAllowExactAlarms = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    exactAlarmLauncher.launch(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    )
                                } else {
                                    permissionTick++
                                }
                            },
                            onAllowBattery = {
                                batteryLauncher.launch(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            }
                        )
                        else -> FirstWinStep(
                            userName = config.userName,
                            selectedLanguageName = selectedLanguageName.ifBlank { appPrefs?.quranTranslationLanguage ?: "English" },
                            selectedEditionLabel = selectedEditionLabel.ifBlank { selectedEditionKey.ifBlank { "Default" } },
                            translationDownloadLabel = translationDownloadLabel,
                            appPrefs = appPrefs,
                            prefsTick = prefsTick,
                            accent = accent,
                            accentSoft = accentSoft
                        )
                    }
                }

                val ctaLabel = when (step) {
                    0 -> "Continue"
                    1, 2, 4, 5, 6 -> "Continue"
                    else -> "Start Using Sakinah"
                }
                val showCta = step > 0 || showGreeting
                AnimatedVisibility(
                    visible = showCta,
                    enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 4 }),
                    exit = fadeOut(tween(220)) + slideOutVertically(targetOffsetY = { it / 4 })
                ) {
                    OnboardingCta(
                        label = ctaLabel,
                        accent = accent,
                        onAccent = onAccent,
                        onClick = {
                            when (step) {
                                0 -> step = 1
                                1 -> step = 2
                                2 -> {
                                    val lang = LanguageOption(
                                        selectedLanguageName.ifBlank { "English" },
                                        selectedLanguageIso.ifBlank { "en" }
                                    )
                                    if (selectedLanguageName.isBlank()) {
                                        selectedLanguageName = "English"
                                        selectedLanguageIso = "en"
                                    }
                                    val editions = editionsForLanguage(translationResources, lang.isoCode, lang.displayName)
                                    if (editions.size > 1) {
                                        step = 4
                                    } else {
                                        applyLanguageAndEdition(lang, editions.firstOrNull())
                                        step = if (availableTafsirPackages.isEmpty()) 6 else 5
                                    }
                                }
                                4 -> {
                                    if (selectedEditionKey.isBlank()) {
                                        val lang = LanguageOption(selectedLanguageName.ifBlank { "English" }, selectedLanguageIso.ifBlank { "en" })
                                        val editions = editionsForLanguage(translationResources, lang.isoCode, lang.displayName)
                                        applyLanguageAndEdition(lang, editions.firstOrNull())
                                    }
                                    step = if (availableTafsirPackages.isEmpty()) 6 else 5
                                }
                                5 -> step = 6
                                6 -> step = 7
                                else -> finish()
                            }
                        }
                    )
                }
            }

            if (showCityPicker) {
                HomeLocationCityPickerDialog(
                    title = "Choose your city",
                    subtitle = "Used for accurate Salah times and Qibla.",
                    onDismiss = { showCityPicker = false },
                    onCitySelected = { city ->
                        appPrefs?.saveManualLocation(
                            latitude = city.latitude,
                            longitude = city.longitude,
                            label = city.name,
                            countryCode = city.countryCode,
                            timeZoneId = city.timeZoneId
                        )
                        prefsTick++
                        permissionTick++
                        showCityPicker = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HeroZone(
    modifier: Modifier,
    step: Int,
    userName: String,
    showGreeting: Boolean,
    selectedLanguageName: String,
    accent: Color,
    accentSoft: Color,
    onAccentSoft: Color,
    onAssembled: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val name = userName.trim()
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = accent
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }

            if (step == 0) {
                AnimatedVisibility(
                    visible = showGreeting,
                    enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -it / 4 }),
                    exit = fadeOut(tween(220)) + slideOutVertically(targetOffsetY = { -it / 4 })
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accentSoft,
                        modifier = Modifier.clickable(onClick = onSkip)
                    ) {
                        Text(
                            text = "Skip",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onAccentSoft
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (step) {
                0 -> {
                    SakinahAnimatedLogo(onAssembled = onAssembled)
                    Text(
                        text = "Sakinah",
                        fontFamily = FontFamily.Serif,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    AnimatedVisibility(
                        visible = showGreeting,
                        enter = fadeIn(tween(600)),
                        exit = fadeOut(tween(220))
                    ) {
                        Text(
                            text = "As-salamu alaykum",
                            fontFamily = FontFamily.Serif,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                1 -> {
                    Text(
                        text = if (name.isNotBlank()) "Nice to meet you, $name" else "Welcome",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "What brings you here? Select one or more.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                2 -> {
                    StepHeroIcon(Icons.Default.Search, accent, accentSoft)
                    Text(
                        text = "Choose your default language",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Applies across Quran, Word-by-Word, Hadith, and Tafseer.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                3 -> {
                    StepHeroIcon(Icons.AutoMirrored.Filled.MenuBook, accent, accentSoft)
                    Text(
                        text = "Choose your ${selectedLanguageName.ifBlank { "English" }} edition",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Select your preferred translation style across Quran, WBW, Hadith, and Tafseer.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                4 -> {
                    StepHeroIcon(Icons.Default.Notifications, accent, accentSoft)
                    Text(
                        text = "Enable helpful permissions",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Only what's needed for accurate Salah times and reliable reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    StepHeroIcon(Icons.Default.Mosque, accent, accentSoft)
                    Text(
                        text = if (name.isNotBlank()) "You're all set, $name" else "You're all set",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeroIcon(icon: ImageVector, accent: Color, accentSoft: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(accentSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
    }
}


@Composable
private fun OnboardingSheet(
    step: Int,
    bodyMaxHeight: Dp,
    sheetColor: Color,
    accent: Color,
    accentSoft: Color,
    body: @Composable (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = sheetColor,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f))
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(TOTAL_STEPS) { index ->
                    val active = index == step
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (active) 22.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) accent else accentSoft)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally { it / 3 } + fadeIn(tween(300)) togetherWith
                        slideOutHorizontally { -it / 3 } + fadeOut(tween(220))
                },
                label = "onboarding_step"
            ) { animatedStep ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = bodyMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    body(animatedStep)
                }
            }
        }
    }
}

@Composable
private fun OnboardingCta(
    label: String,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp)
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppearanceStep(
    draft: UserAppearanceConfig,
    accent: Color,
    accentSoft: Color,
    onDraftChange: (UserAppearanceConfig) -> Unit
) {
    val name = draft.userName.trim()
    val palettes = remember { SakinahPalette.entries }
    val initialIndex = remember { palettes.indexOf(draft.palette).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()

    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }?.index ?: initialIndex
        }
    }
    LaunchedEffect(centeredIndex) {
        val selected = palettes.getOrNull(centeredIndex) ?: return@LaunchedEffect
        if (selected != draft.palette) {
            onDraftChange(draft.copy(palette = selected))
        }
    }

    val darkPreview = when (draft.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (name.isBlank()) "Make it yours" else "Make it yours, $name",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = "Choose your color theme and reading comfort.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Theme",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SegmentedPillBar(
            options = ThemeMode.entries.map { it.label },
            selectedIndex = draft.themeMode.ordinal,
            onSelect = { index -> onDraftChange(draft.copy(themeMode = ThemeMode.entries[index])) }
        )

        Text(
            text = "Palette",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(modifier = Modifier.fillMaxWidth().height(176.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 58.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                flingBehavior = flingBehavior
            ) {
                itemsIndexed(palettes) { index, palette ->
                    val centered = index == centeredIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (centered) accentSoft else Color.Transparent
                            )
                            .clickable {
                                if (!centered) {
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val primary = if (darkPreview) palette.darkPreviewPrimary else palette.lightPreviewPrimary
                        val secondary = if (darkPreview) palette.darkPreviewPrimary else palette.lightPreviewAccent
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(secondary)
                            )
                        }
                        Text(
                            text = stringResource(palette.titleRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (centered) FontWeight.Bold else FontWeight.Medium,
                            color = if (centered) accent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (centered) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = accent)
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(
                        width = 1.dp,
                        color = accent.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp)
                    )
            )
        }

        Text(
            text = "Text size",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val scalePresets = FontScalePreset.entries.take(4)
        scalePresets.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { preset ->
                    val selected = preset == draft.fontScale
                    Surface(
                        onClick = { onDraftChange(draft.copy(fontScale = preset)) },
                        shape = RoundedCornerShape(50),
                        color = if (selected) accent else accentSoft,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = preset.label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density,
                fontScale = (baseDensity.fontScale * draft.fontScale.multiplier).coerceIn(0.8f, 2.0f)
            )
        ) {
            SakinahCard(containerColor = accentSoft.copy(alpha = 0.55f), contentPadding = 12.dp) {
                Text(
                    text = "Sample reading text — Calm, clear, and easy on the eyes.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    config: UserAppearanceConfig,
    showGreeting: Boolean,
    accent: Color,
    onConfigChange: (UserAppearanceConfig) -> Unit
) {
    AnimatedVisibility(
        visible = showGreeting,
        enter = fadeIn(tween(600)),
        exit = fadeOut(tween(220))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Welcome to Sakinah. What should we call you?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = config.userName,
                onValueChange = { onConfigChange(config.copy(userName = it)) },
                label = { Text("Your name") },
                placeholder = { Text("Enter your name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    cursorColor = accent,
                    focusedLabelColor = accent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "You can skip this and continue anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoalsStep(
    userName: String,
    selectedGoalIndices: Set<Int>,
    accent: Color,
    accentSoft: Color,
    onAccent: Color,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit
) {
    val allSelected = selectedGoalIndices.size == GOALS.size
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GOALS.forEachIndexed { index, goal ->
            val selected = index in selectedGoalIndices
            GoalRow(
                label = goal.label,
                icon = goal.icon,
                selected = selected,
                accent = accent,
                accentSoft = accentSoft,
                onAccent = onAccent,
                onClick = { onToggle(index) }
            )
        }
        GoalRow(
            label = "Select all of the above",
            icon = Icons.Default.Check,
            selected = allSelected,
            accent = accent,
            accentSoft = accentSoft,
            onAccent = onAccent,
            onClick = onSelectAll
        )
    }
}

@Composable
private fun GoalRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    accentSoft: Color,
    onAccent: Color,
    onClick: () -> Unit
) {
    SakinahCard(
        onClick = onClick,
        containerColor = if (selected) accentSoft else MaterialTheme.colorScheme.surface,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LanguageStep(
    search: String,
    onSearchChange: (String) -> Unit,
    languages: List<LanguageOption>,
    selectedIso: String,
    accent: Color,
    accentSoft: Color,
    onSelect: (LanguageOption) -> Unit
) {
    val filtered = remember(search, languages) {
        val q = search.trim().lowercase(Locale.getDefault())
        if (q.isBlank()) languages
        else languages.filter {
            it.displayName.lowercase(Locale.getDefault()).contains(q) ||
                it.isoCode.lowercase(Locale.getDefault()).contains(q)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text("Search languages...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                cursorColor = accent,
                focusedLabelColor = accent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filtered.forEach { lang ->
                val selected = lang.isoCode.equals(selectedIso, true)
                SakinahCard(
                    onClick = { onSelect(lang) },
                    containerColor = if (selected) accentSoft else MaterialTheme.colorScheme.surface,
                    contentPadding = 14.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditionStep(
    languageName: String,
    editions: List<EditionOption>,
    selectedEditionKey: String,
    accent: Color,
    accentSoft: Color,
    onSelect: (EditionOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Select your preferred translation style across Quran, WBW, Hadith, and Tafseer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (editions.isEmpty()) {
            SakinahCard(containerColor = accentSoft) {
                Text(
                    text = "No editions found for $languageName. English will be used as a fallback where needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            editions.forEach { edition ->
                val key = edition.quranTranslation?.editionKey ?: edition.label
                val selected = key == selectedEditionKey || (selectedEditionKey.isBlank() && edition == editions.firstOrNull())
                SakinahCard(
                    onClick = { onSelect(edition) },
                    containerColor = if (selected) accentSoft else MaterialTheme.colorScheme.surface,
                    contentPadding = 14.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = edition.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                            )
                            val author = edition.quranTranslation?.authorName.orEmpty()
                            if (author.isNotBlank() && !author.equals(edition.label, true)) {
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TafsirStep(
    languageName: String,
    packages: List<TafseerPackage>,
    selectedTafsirId: Int,
    downloading: Boolean,
    progress: Float,
    error: String?,
    accent: Color,
    accentSoft: Color,
    onSelect: (TafseerPackage) -> Unit,
    onRetry: (TafseerPackage) -> Unit,
    onDownloadLater: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Choose your $languageName Tafsir",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = "Downloads immediately for offline reading across the Quran.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (packages.isEmpty()) {
            SakinahCard(containerColor = accentSoft, contentPadding = 14.dp) {
                Text(
                    text = "No Tafsir editions are available for $languageName yet. " +
                        "You can choose one later from the Reader menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            packages.forEach { pkg ->
                val selected = pkg.tafsirId == selectedTafsirId
                SakinahCard(
                    onClick = { onSelect(pkg) },
                    containerColor = if (selected) accentSoft else MaterialTheme.colorScheme.surface,
                    contentPadding = 14.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pkg.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${pkg.author} · ${pkg.statusLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selected && pkg.isDownloaded && !downloading) {
                            Text(
                                text = "Offline Ready ✓",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        } else if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = accent)
                        }
                    }
                    if (selected && downloading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Downloading offline pack… ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = accent
                        )
                    }
                    if (selected && error != null && !downloading) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { onRetry(pkg) }) {
                                Text("Retry", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onDownloadLater,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Download later", color = accent)
        }
    }
}

@Composable
private fun PermissionsStep(
    permissions: PermissionState,
    accent: Color,
    onAccent: Color,
    accentSoft: Color,
    onAllowLocation: () -> Unit,
    onEnterCityManually: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowExactAlarms: () -> Unit,
    onAllowBattery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionCard(
            icon = Icons.Default.LocationOn,
            title = "Location",
            benefit = "Accurate Salah times and Qibla",
            granted = permissions.location,
            accent = accent,
            onAccent = onAccent,
            accentSoft = accentSoft,
            onAllow = onAllowLocation
        )
        TextButton(onClick = onEnterCityManually, modifier = Modifier.padding(start = 4.dp)) {
            Text("Enter city manually", color = accent)
        }
        PermissionCard(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            benefit = "Daily prayer alerts",
            granted = permissions.notifications,
            accent = accent,
            onAccent = onAccent,
            accentSoft = accentSoft,
            onAllow = onAllowNotifications
        )
        PermissionCard(
            icon = Icons.Default.Alarm,
            title = "Exact Alarms",
            benefit = "On-time Azaan scheduling",
            granted = permissions.exactAlarms,
            accent = accent,
            onAccent = onAccent,
            accentSoft = accentSoft,
            onAllow = onAllowExactAlarms
        )
        PermissionCard(
            icon = Icons.Default.BatterySaver,
            title = "Battery Optimization",
            benefit = "Keep Azaan reliable in background",
            granted = permissions.batteryOptimizationIgnored,
            accent = accent,
            onAccent = onAccent,
            accentSoft = accentSoft,
            onAllow = onAllowBattery
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    benefit: String,
    granted: Boolean,
    accent: Color,
    onAccent: Color,
    accentSoft: Color,
    onAllow: () -> Unit
) {
    SakinahCard(
        containerColor = if (granted) accentSoft.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (granted) accent else accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (granted) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = if (granted) onAccent else accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = benefit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = { if (!granted) onAllow() },
                shape = RoundedCornerShape(50),
                color = if (granted) accentSoft else accent,
                border = if (granted) BorderStroke(1.dp, accent.copy(alpha = 0.35f)) else null
            ) {
                Text(
                    text = if (granted) "Granted" else "Allow",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (granted) accent else onAccent
                )
            }
        }
    }
}

@Composable
private fun FirstWinStep(
    userName: String,
    selectedLanguageName: String,
    selectedEditionLabel: String,
    translationDownloadLabel: String?,
    appPrefs: SakinahPreferences?,
    prefsTick: Int,
    accent: Color,
    accentSoft: Color
) {
    val name = userName.trim()
    var countdown by remember { mutableStateOf<String?>(null) }
    var nextPrayerName by remember { mutableStateOf<String?>(null) }
    var nextPrayerTime by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(prefsTick, appPrefs?.locationLatitude, appPrefs?.locationLongitude) {
        while (true) {
            val preview = appPrefs?.let(::nextPrayerCountdown)
            nextPrayerName = preview?.prayerName
            nextPrayerTime = preview?.timeLabel
            countdown = preview?.countdownLabel
            delay(1000L)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SakinahCard(containerColor = accent, contentPadding = 18.dp) {
            Text(
                text = "Next prayer",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.82f)
            )
            Spacer(Modifier.height(4.dp))
            if (nextPrayerName != null && countdown != null) {
                Text(
                    text = "$nextPrayerName · $nextPrayerTime",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "in $countdown",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            } else {
                Text(
                    text = "Set your location to see the next prayer",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = accentSoft
        ) {
            Text(
                text = "$selectedLanguageName (${selectedEditionLabel}) · Quran, WBW, Hadith & Tafseer",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                textAlign = TextAlign.Center
            )
        }

        translationDownloadLabel?.let { label ->
            Text(
                text = label,
                fontSize = 11.sp,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = if (name.isNotBlank()) "Welcome aboard, $name. Your preferences are ready." else "Your preferences are ready.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class NextPrayerPreview(
    val prayerName: String,
    val timeLabel: String,
    val countdownLabel: String
)

private fun nextPrayerCountdown(prefs: SakinahPreferences): NextPrayerPreview? {
    val coordinates = prefs.locationCoordinates ?: return null
    val zone = prefs.locationTimeZoneId
        ?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() }
        ?: TimeZone.currentSystemDefault()
    val nowInstant = Clock.System.now()
    val now = nowInstant.toLocalDateTime(zone)
    val method = SunniMethodMapper.localCalculationMethod(
        SunniMethodMapper.resolve(prefs.locationCountryCode, prefs.calculationMethodOverride)
    )
    val engine = PrayerCalculationEngineImpl()
    val prayers = listOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB, PrayerType.ISHA)

    fun timesFor(date: LocalDate) = engine.getPrayerTimes(
        coordinates = coordinates,
        date = date,
        method = method,
        madhhab = prefs.madhhab,
        timeZone = zone,
        adjustments = prefs.prayerAdjustments,
        highLatitudeRule = prefs.highLatitudeRule
    )

    val todayTimes = timesFor(now.date)
    val nextToday = prayers.mapNotNull { type ->
        todayTimes[type]?.takeIf { it >= now.time }?.let { type to it }
    }.minByOrNull { it.second }

    val (type, time, date) = if (nextToday != null) {
        Triple(nextToday.first, nextToday.second, now.date)
    } else {
        val tomorrow = LocalDate.fromEpochDays(now.date.toEpochDays() + 1)
        val tomorrowFajr = timesFor(tomorrow)[PrayerType.FAJR] ?: return null
        Triple(PrayerType.FAJR, tomorrowFajr, tomorrow)
    }

    val targetInstant = kotlinx.datetime.LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, time.hour, time.minute)
        .toInstant(zone)
    val diffSeconds = (targetInstant - nowInstant).inWholeSeconds.coerceAtLeast(0)
    val h = diffSeconds / 3600
    val m = (diffSeconds % 3600) / 60
    val s = diffSeconds % 60
    val countdown = if (h > 0) String.format(Locale.getDefault(), "%dh %02dm %02ds", h, m, s)
    else String.format(Locale.getDefault(), "%dm %02ds", m, s)
    val formatted = String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute)
    return NextPrayerPreview(type.englishName, formatted, countdown)
}

private fun readPermissionState(context: Context): PermissionState {
    val location = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    val exactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        am.canScheduleExactAlarms()
    } else true
    val batteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true
    return PermissionState(location, notifications, exactAlarms, batteryOptimizationIgnored)
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun objectiveForGoals(selected: Set<Int>): UserObjective {
    if (selected.isEmpty()) return UserObjective.ALL_IN_ONE
    if (selected.size == GOALS.size) return UserObjective.ALL_IN_ONE
    if (selected.size > 1) return UserObjective.ALL_IN_ONE
    val idx = selected.first()
    return when (idx) {
        0 -> UserObjective.ALL_IN_ONE
        1 -> UserObjective.PRAYER_COMPASS
        2 -> UserObjective.HADITH_SCHOLAR
        3 -> UserObjective.QURAN_STUDY
        4 -> UserObjective.PRAYER_COMPASS
        else -> UserObjective.ALL_IN_ONE
    }
}

private fun buildLanguageOptions(resources: List<QuranTranslationResource>): List<LanguageOption> {
    val byIso = linkedMapOf<String, String>()
    resources.forEach { r ->
        val iso = r.isoCode.trim().lowercase(Locale.getDefault())
        val name = r.languageName.trim().ifBlank { iso.uppercase(Locale.getDefault()) }
        if (iso.isNotBlank()) {
            val existing = byIso[iso]
            if (existing == null || (existing == iso.uppercase(Locale.getDefault()) && name.isNotBlank())) {
                byIso[iso] = name
            }
        }
    }
    // Ensure common languages exist even if catalog is sparse
    listOf(
        "en" to "English", "ar" to "Arabic", "ur" to "Urdu", "id" to "Indonesian",
        "fr" to "French", "tr" to "Turkish", "bn" to "Bengali", "ru" to "Russian",
        "es" to "Spanish", "de" to "German", "hi" to "Hindi", "fa" to "Persian",
        "ms" to "Malay", "sw" to "Swahili", "ta" to "Tamil", "te" to "Telugu",
        "th" to "Thai", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
        "pt" to "Portuguese", "it" to "Italian", "nl" to "Dutch", "so" to "Somali",
        "uz" to "Uzbek", "ku" to "Kurdish", "az" to "Azerbaijani", "sq" to "Albanian",
        "bs" to "Bosnian", "ha" to "Hausa", "yo" to "Yoruba", "ml" to "Malayalam",
        "pa" to "Punjabi", "ps" to "Pashto", "sd" to "Sindhi"
    ).forEach { (iso, name) -> byIso.putIfAbsent(iso, name) }

    return byIso.entries
        .map { LanguageOption(displayName = it.value.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }, isoCode = it.key) }
        .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
}

private fun defaultTranslationResources(): List<QuranTranslationResource> = listOf(
    QuranTranslationResource(20, "Saheeh International", "Saheeh International", "English", "en", "20", QuranTranslationResource.SOURCE_QURAN_COM),
    QuranTranslationResource(131, "The Clear Quran", "Dr. Mustafa Khattab", "English", "en", "131", QuranTranslationResource.SOURCE_QURAN_COM),
    QuranTranslationResource(97, "Tafheem-ul-Quran", "Abul Ala Maududi", "Urdu", "ur", "97", QuranTranslationResource.SOURCE_QURAN_COM),
    QuranTranslationResource(234, "Fatah Muhammad Jalandhari", "Fatah Muhammad Jalandhari", "Urdu", "ur", "234", QuranTranslationResource.SOURCE_QURAN_COM),
    QuranTranslationResource(158, "Bayan-ul-Quran", "Dr. Israr Ahmad", "Urdu", "ur", "158", QuranTranslationResource.SOURCE_QURAN_COM),
    QuranTranslationResource(54, "Maududi", "Abul Ala Maududi", "English", "en", "54", QuranTranslationResource.SOURCE_QURAN_COM)
)

private fun editionsForLanguage(
    resources: List<QuranTranslationResource>,
    iso: String,
    languageName: String
): List<EditionOption> {
    val isoNorm = iso.trim().lowercase(Locale.getDefault())
    val nameNorm = languageName.trim().lowercase(Locale.getDefault())
    val matches = resources.filter { r ->
        val rIso = r.isoCode.trim().lowercase(Locale.getDefault())
        val rName = r.languageName.trim().lowercase(Locale.getDefault())
        (isoNorm.isNotBlank() && rIso == isoNorm) || (nameNorm.isNotBlank() && rName == nameNorm)
    }.distinctBy { it.editionKey.ifBlank { "${it.source}:${it.id}" } }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
    return matches.map { r ->
        EditionOption(
            label = r.name.ifBlank { r.authorName.ifBlank { "Edition ${r.id}" } },
            languageName = r.languageName.ifBlank { languageName },
            isoCode = r.isoCode.ifBlank { iso },
            quranTranslation = r
        )
    }
}

private fun normalizeWbwLanguage(iso: String): String {
    val code = iso.trim().lowercase(Locale.getDefault())
    return when (code) {
        "en", "eng", "english" -> "en"
        "ur", "urd", "urdu" -> "ur"
        "bn", "ben", "bengali" -> "bn"
        "id", "ind", "indonesian" -> "id"
        "tr", "tur", "turkish" -> "tr"
        "fr", "fra", "french" -> "fr"
        "ru", "rus", "russian" -> "ru"
        "fa", "per", "fas", "persian" -> "fa"
        "hi", "hin", "hindi" -> "hi"
        "ta", "tam", "tamil" -> "ta"
        else -> code.ifBlank { "en" }
    }
}

private fun normalizeHadithLanguage(iso: String): String {
    val code = iso.trim().lowercase(Locale.getDefault())
    return when (code) {
        "en", "eng", "english" -> "eng"
        "ar", "ara", "arabic" -> "ara"
        "ur", "urd", "urdu" -> "urd"
        "bn", "ben", "bengali" -> "ben"
        "tr", "tur", "turkish" -> "tur"
        "id", "ind", "indonesian" -> "ind"
        "fr", "fra", "french" -> "fra"
        "ru", "rus", "russian" -> "rus"
        "es", "spa", "spanish" -> "spa"
        "fa", "per", "fas", "persian" -> "fas"
        else -> "eng"
    }
}

private fun normalizeTafsirLanguage(iso: String): String {
    val code = iso.trim().lowercase(Locale.getDefault())
    return when (code) {
        "en", "eng", "english" -> "en"
        "ar", "ara", "arabic" -> "ar"
        "ur", "urd", "urdu" -> "ur"
        "bn", "ben", "bengali" -> "bn"
        "ru", "rus", "russian" -> "ru"
        "ku", "kur", "kurdish" -> "ku"
        "fa", "per", "fas", "persian" -> "fa"
        "tr", "tur", "turkish" -> "tr"
        "id", "ind", "indonesian" -> "id"
        else -> "en"
    }
}

private fun defaultTafsirIdForLanguage(lang: String): Int? = when (lang) {
    "ar" -> 16
    "ur" -> 160
    "bn" -> 165
    "ru" -> 170
    "ku" -> 804
    "en" -> 169
    else -> null
}
