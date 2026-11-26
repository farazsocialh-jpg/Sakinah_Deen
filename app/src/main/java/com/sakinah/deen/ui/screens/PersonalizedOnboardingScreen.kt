package com.sakinah.deen.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sakinah.deen.R
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.data.prayer.SunniMethodMapper
import com.sakinah.deen.data.prayer.englishName
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.domain.astronomy.UserLocationProvider
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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Emerald & Alabaster default look, used until the live theme preview takes over. */
private val AlabasterHero = Color(0xFFFBF8F3)
private val ElevatedSheet = Color(0xFFFFFFFF)
private val ForestEmerald = Color(0xFF1F5E4B)
private val SoftSage = Color(0xFFE8F3EE)

private const val TOTAL_STEPS = 4
private const val ARABIC_FONT_MIN = 18
private const val ARABIC_FONT_MAX = 36
private const val TRANSLATION_FONT_MIN = 12
private const val TRANSLATION_FONT_MAX = 24

private const val EXPERIENCE_NEW = "New to Islam"
private const val EXPERIENCE_PRACTICING = "Practicing Muslim"
private const val EXPERIENCE_STUDENT = "Student of Knowledge"

private val LogoGreen = Color(0xFF0F6B5E)
private val LogoIvory = Color(0xFFF7F8F5)
private val LogoGold = Color(0xFFC5A059)

/**
 * The adaptive-icon mark assembling itself: crescent from the left, dome and Qibla diamond
 * from the centre, then the gold Quran sweep from the bottom. Every path is the exact path
 * used by the launcher icon, scaled from the shared 108f viewport.
 */
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
        launch { crescentAnim.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        launch {
            delay(160)
            domeAnim.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        launch {
            delay(300)
            quranAnim.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        delay(900)
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
                // 1. Crescent slides in from left.
                val crescentProgress = crescentAnim.value
                translate(left = (1f - crescentProgress) * -22f) {
                    drawPath(crescentPath, color = LogoIvory.copy(alpha = crescentProgress))
                }
                // 2. Mosque dome and its negative-space Qibla diamond rise into the centre.
                val domeProgress = domeAnim.value
                translate(top = (1f - domeProgress) * 18f) {
                    drawPath(domePath, color = LogoIvory.copy(alpha = domeProgress))
                    drawPath(qiblaOuterPath, color = LogoGreen.copy(alpha = domeProgress))
                    drawPath(qiblaInnerPath, color = LogoIvory.copy(alpha = domeProgress))
                }
                // 3. Gold Quran sweep unfolds upward.
                val quranProgress = quranAnim.value
                translate(top = (1f - quranProgress) * 20f) {
                    drawPath(quranTopPath, color = LogoGold.copy(alpha = quranProgress))
                    drawPath(quranBottomPath, color = LogoGold.copy(alpha = quranProgress))
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
            objective = UserObjective.valueOf(values[2]),
            themeMode = ThemeMode.valueOf(values[3]),
            palette = SakinahPalette.valueOf(values[4]),
            fontScale = FontScalePreset.valueOf(values[5]),
            focusModeHideDistractions = values[6].toBoolean(),
            autoHideBarsOnScroll = values[7].toBoolean(),
            prayerGuideGender = PrayerGuideGender.valueOf(values[8]),
            salahHomeLayout = values.getOrNull(9)
                ?.let { runCatching { SalahHomeLayout.valueOf(it) }.getOrNull() }
                ?: SalahHomeLayout.CLASSIC_COMPACT,
            quranReadingLayout = QuranReadingLayout.fromStoredName(values.getOrNull(10))
                ?: QuranReadingLayout.THUMB_BOTTOM_DOCK,
            hadithReadingLayout = values.getOrNull(11)
                ?.let { runCatching { HadithReadingLayout.valueOf(it) }.getOrNull() }
                ?: HadithReadingLayout.COMPACT_DROPDOWN,
            // Older saved states stop at index 12; the default stays safe when absent.
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
    val notifications: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizedOnboardingScreen(
    modifier: Modifier = Modifier,
    initialConfig: UserAppearanceConfig = UserAppearanceConfig(),
    onConfigPreviewChange: (UserAppearanceConfig) -> Unit = {},
    onComplete: (UserAppearanceConfig) -> Unit,
    onDismiss: (() -> Unit)? = null,
    /** Set by RootNavHost so Step 2 can confirm the calculation method and madhhab. */
    appPrefs: SakinahPreferences? = null,
    locationProvider: UserLocationProvider? = null
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var config by rememberSaveable(initialConfig, stateSaver = UserAppearanceConfigSaver) {
        mutableStateOf(initialConfig)
    }
    var showGreeting by rememberSaveable { mutableStateOf(false) }
    var remindersEnabled by rememberSaveable(appPrefs) {
        mutableStateOf(appPrefs?.alarmsEnabled == true)
    }
    var remindersTouched by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = step > 0) { step-- }

    fun updateConfig(updated: UserAppearanceConfig) {
        config = updated
        onConfigPreviewChange(updated)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionTick by remember { mutableIntStateOf(0) }
    var prefsTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Returning from a system settings screen must refresh the location state.
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
        if (!granted) return@rememberLauncherForActivityResult
        scope.launch {
            val resolved = (locationProvider ?: UserLocationProvider(context))
                .getFreshOrCachedLocation()
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
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionTick++
        // A denied prompt must not leave the toggle claiming reminders are on.
        if (!granted) remindersEnabled = false
    }

    val darkTheme = when (config.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val themePreviewActive = config.themeMode != initialConfig.themeMode ||
        config.palette != initialConfig.palette
    val defaultLook = !darkTheme && !themePreviewActive

    val heroColor = if (defaultLook) AlabasterHero else MaterialTheme.colorScheme.background
    val sheetColor = if (defaultLook) ElevatedSheet else MaterialTheme.colorScheme.surface
    val accent = if (defaultLook) ForestEmerald else MaterialTheme.colorScheme.primary
    val onAccent = if (defaultLook) Color.White else MaterialTheme.colorScheme.onPrimary
    val accentSoft = if (defaultLook) SoftSage else MaterialTheme.colorScheme.primaryContainer
    val onAccentSoft = if (defaultLook) ForestEmerald else MaterialTheme.colorScheme.onPrimaryContainer

    val finish = {
        // Only an explicit Step 3 opt-in (or opt-out) touches the background alarm flag.
        if (remindersTouched) appPrefs?.alarmsEnabled = remindersEnabled
        onComplete(config.copy(onboardingCompleted = true))
    }

    // The live draft drives the theme so Dark mode and palette changes render immediately.
    SakinahTheme(
        darkTheme = darkTheme,
        palette = config.palette,
        fontScaleMultiplier = config.fontScale.multiplier
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(heroColor)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val bodyMaxHeight = (maxHeight * 0.50f).coerceAtLeast(180.dp)

            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
            ) {
                HeroZone(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    step = step,
                    userName = config.userName,
                    showGreeting = showGreeting,
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
                    sheetColor = sheetColor,
                    accent = accent,
                    accentSoft = accentSoft
                ) { currentStep ->
                    when (currentStep) {
                        0 -> WelcomeStep(
                            config = config,
                            showGreeting = showGreeting,
                            accent = accent,
                            accentSoft = accentSoft,
                            onConfigChange = ::updateConfig
                        )
                        1 -> JourneyStep(
                            config = config,
                            accent = accent,
                            accentSoft = accentSoft,
                            onAccent = onAccent,
                            onConfigChange = ::updateConfig
                        )
                        2 -> LocationAndMethodStep(
                            appPrefs = appPrefs,
                            prefsTick = prefsTick,
                            locationGranted = permissions.location,
                            accent = accent,
                            onAccent = onAccent,
                            accentSoft = accentSoft,
                            onAccentSoft = onAccentSoft,
                            onAllowLocation = {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onCitySelected = { city ->
                                appPrefs?.saveManualLocation(
                                    latitude = city.latitude,
                                    longitude = city.longitude,
                                    label = city.name,
                                    countryCode = city.countryCode,
                                    timeZoneId = city.timeZoneId
                                )
                                prefsTick++
                            },
                            onPrefsChanged = { prefsTick++ }
                        )
                        else -> FirstWinAndRemindersStep(
                            appPrefs = appPrefs,
                            prefsTick = prefsTick,
                            remindersEnabled = remindersEnabled,
                            onRemindersChange = { enabled ->
                                remindersTouched = true
                                remindersEnabled = enabled
                                if (enabled && !permissions.notifications &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = step > 0 || showGreeting,
                    enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 4 }),
                    exit = fadeOut(tween(220)) + slideOutVertically(targetOffsetY = { it / 4 })
                ) {
                    OnboardingCta(
                        label = when {
                            step < TOTAL_STEPS - 1 -> stringResource(R.string.onboarding_continue)
                            remindersEnabled -> stringResource(R.string.onboarding_enable_reminders)
                            else -> stringResource(R.string.onboarding_enter_app)
                        },
                        accent = accent,
                        onAccent = onAccent,
                        onClick = {
                            if (step < TOTAL_STEPS - 1) step++ else finish()
                        }
                    )
                }
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
                        contentDescription = stringResource(R.string.onboarding_back),
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
                            text = stringResource(R.string.onboarding_skip),
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
                        text = stringResource(R.string.onboarding_brand),
                        fontFamily = FontFamily.Serif,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    AnimatedVisibility(
                        visible = showGreeting,
                        enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 4 }),
                        exit = fadeOut(tween(220)) + slideOutVertically(targetOffsetY = { it / 4 })
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_welcome_salam),
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
                        text = if (name.isNotBlank()) {
                            stringResource(R.string.onboarding_journey_greeting_named, name)
                        } else {
                            stringResource(R.string.onboarding_journey_greeting)
                        },
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.onboarding_journey_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                2 -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.onboarding_location_title),
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.onboarding_next_prayer),
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.onboarding_reminders_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(animationSpec = tween(300)) { width ->
                        if (forward) width / 3 else -width / 3
                    } + fadeIn(tween(300))
                    val exit = slideOutHorizontally(animationSpec = tween(220)) { width ->
                        if (forward) -width / 3 else width / 3
                    } + fadeOut(tween(220))
                    enter togetherWith exit
                },
                label = "onboarding_sheet_step"
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
private fun WelcomeStep(
    config: UserAppearanceConfig,
    showGreeting: Boolean,
    accent: Color,
    accentSoft: Color,
    onConfigChange: (UserAppearanceConfig) -> Unit
) {
    AnimatedVisibility(
        visible = showGreeting,
        enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut(tween(220)) + slideOutVertically(targetOffsetY = { it / 4 })
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.onboarding_mission),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.onboarding_welcome_headline),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = config.userName,
                onValueChange = { onConfigChange(config.copy(userName = it)) },
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                placeholder = { Text(stringResource(R.string.onboarding_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = accent,
                    focusedLabelColor = accent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.onboarding_privacy_microcopy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Soft sage rule keeps the sheet's emerald identity visible in every palette.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentSoft)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JourneyStep(
    config: UserAppearanceConfig,
    accent: Color,
    accentSoft: Color,
    onAccent: Color,
    onConfigChange: (UserAppearanceConfig) -> Unit
) {
    val experiences = listOf(EXPERIENCE_NEW, EXPERIENCE_PRACTICING, EXPERIENCE_STUDENT)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        experiences.forEach { level ->
            val selected = config.experienceLevel == level
            FilterChip(
                selected = selected,
                onClick = { onConfigChange(config.copy(experienceLevel = level)) },
                label = { Text(experienceLabel(level), fontSize = 12.sp) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentSoft,
                    selectedLabelColor = accent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    selectedBorderColor = accent
                )
            )
        }
    }

    Text(
        text = stringResource(R.string.onboarding_focus_question),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )

    val objectives = UserObjective.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        objectives.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { objective ->
                    FocusTile(
                        objective = objective,
                        selected = config.objective == objective,
                        accent = accent,
                        accentSoft = accentSoft,
                        onAccent = onAccent,
                        modifier = Modifier.weight(1f),
                        // Tapping a focus must not overwrite an already chosen palette or Focus Mode.
                        onClick = { onConfigChange(config.copy(objective = objective)) }
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FocusTile(
    objective: UserObjective,
    selected: Boolean,
    accent: Color,
    accentSoft: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accentSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = objectiveIcon(objective),
                    contentDescription = null,
                    tint = if (selected) onAccent else accent,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(
                text = objectiveLabel(objective),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun LocationAndMethodStep(
    appPrefs: SakinahPreferences?,
    prefsTick: Int,
    locationGranted: Boolean,
    accent: Color,
    onAccent: Color,
    accentSoft: Color,
    onAccentSoft: Color,
    onAllowLocation: () -> Unit,
    onCitySelected: (HomeCity) -> Unit,
    onPrefsChanged: () -> Unit
) {
    var showCityPicker by rememberSaveable { mutableStateOf(false) }
    var showMethodPicker by rememberSaveable { mutableStateOf(false) }

    val method = remember(prefsTick) {
        appPrefs?.calculationMethodOverride ?: appPrefs?.calculationMethod
        ?: CalculationMethod.MUSLIM_WORLD_LEAGUE
    }
    val school = remember(prefsTick) { appPrefs?.madhhab ?: Madhhab.HANAFI }
    val savedLocation = remember(prefsTick) { appPrefs?.locationLabel?.takeIf(String::isNotBlank) }

    SakinahCard(containerColor = accentSoft) {
        Text(
            text = stringResource(R.string.onboarding_location_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = onAccentSoft
        )
        Text(
            text = stringResource(R.string.onboarding_location_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onAllowLocation,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)
        ) {
            Text(
                text = if (savedLocation != null && locationGranted) {
                    stringResource(R.string.onboarding_location_allowed)
                } else {
                    stringResource(R.string.onboarding_location_allow)
                }
            )
        }
        TextButton(onClick = { showCityPicker = true }) {
            Text(stringResource(R.string.onboarding_location_manual))
        }
        if (savedLocation != null) {
            Text(
                text = stringResource(R.string.onboarding_location_saved, savedLocation),
                style = MaterialTheme.typography.bodySmall,
                color = onAccentSoft
            )
        }
    }

    SakinahCard {
        Text(
            text = stringResource(R.string.onboarding_prayer_settings),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_calculation_method),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = methodDisplayName(method),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = { showMethodPicker = true }) {
                Text(stringResource(R.string.profile_change))
            }
        }
        Text(
            text = stringResource(R.string.onboarding_madhhab),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SegmentedPillBar(
            options = listOf(
                stringResource(R.string.onboarding_madhhab_hanafi),
                stringResource(R.string.onboarding_madhhab_other)
            ),
            selectedIndex = if (school == Madhhab.HANAFI) 0 else 1,
            onSelect = { index ->
                appPrefs?.madhhab = if (index == 0) Madhhab.HANAFI else Madhhab.SHAFI_HANBALI_MALIKI
                onPrefsChanged()
            }
        )
    }

    if (showCityPicker) {
        HomeLocationCityPickerDialog(
            title = stringResource(R.string.onboarding_location_title),
            subtitle = stringResource(R.string.onboarding_location_privacy),
            onDismiss = { showCityPicker = false },
            onCitySelected = { city ->
                onCitySelected(city)
                showCityPicker = false
            }
        )
    }
    if (showMethodPicker) {
        AlertDialog(
            onDismissRequest = { showMethodPicker = false },
            title = { Text(stringResource(R.string.settings_calculation_method)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val autoLabel = stringResource(R.string.settings_calculation_automatic_option)
                    Text(
                        text = autoLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appPrefs?.calculationMethodOverride = null
                                onPrefsChanged()
                                showMethodPicker = false
                            }
                            .padding(vertical = 10.dp),
                        fontSize = 13.sp,
                        color = accent
                    )
                    CalculationMethod.entries.forEach { option ->
                        Text(
                            text = methodDisplayName(option),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appPrefs?.calculationMethod = option
                                    appPrefs?.calculationMethodOverride = option
                                    onPrefsChanged()
                                    showMethodPicker = false
                                }
                                .padding(vertical = 10.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMethodPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FirstWinAndRemindersStep(
    appPrefs: SakinahPreferences?,
    prefsTick: Int,
    remindersEnabled: Boolean,
    onRemindersChange: (Boolean) -> Unit
) {
    val emerald = SakinahPalette.EMERALD_SANCTUARY.lightPreviewPrimary
    val nextPrayer = remember(prefsTick) { appPrefs?.let(::nextPrayerPreview) }

    SakinahCard(containerColor = emerald) {
        Text(
            text = stringResource(R.string.onboarding_next_prayer),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = Color.White.copy(alpha = 0.82f)
        )
        if (nextPrayer != null) {
            Text(
                text = "${nextPrayer.first} · ${nextPrayer.second}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        } else {
            Text(
                text = stringResource(R.string.onboarding_next_prayer_hint),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }

    SakinahCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_reminders),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.onboarding_reminders_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = remindersEnabled, onCheckedChange = onRemindersChange)
        }
    }

    Text(
        text = stringResource(R.string.onboarding_no_ads),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun methodDisplayName(method: CalculationMethod): String =
    method.name.lowercase(Locale.US).split('_')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.US) } }

@Composable
private fun experienceLabel(level: String): String = when (level) {
    EXPERIENCE_NEW -> stringResource(R.string.onboarding_experience_new)
    EXPERIENCE_STUDENT -> stringResource(R.string.onboarding_experience_student)
    else -> stringResource(R.string.onboarding_experience_practicing)
}

@Composable
private fun objectiveLabel(objective: UserObjective): String = when (objective) {
    UserObjective.PRAYER_COMPASS -> stringResource(R.string.onboarding_focus_prayer)
    UserObjective.QURAN_STUDY -> stringResource(R.string.onboarding_focus_quran)
    UserObjective.HADITH_SCHOLAR -> stringResource(R.string.onboarding_focus_learning)
    UserObjective.ALL_IN_ONE -> stringResource(R.string.onboarding_focus_everything)
}

private fun objectiveIcon(objective: UserObjective): ImageVector = when (objective) {
    UserObjective.PRAYER_COMPASS -> Icons.Default.Mosque
    UserObjective.QURAN_STUDY -> Icons.AutoMirrored.Filled.MenuBook
    UserObjective.HADITH_SCHOLAR -> Icons.AutoMirrored.Filled.LibraryBooks
    UserObjective.ALL_IN_ONE -> Icons.Default.Explore
}

private fun readPermissionState(context: Context): PermissionState = PermissionState(
    location = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
)

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Live preview of the next Salah for the saved location. Uses the same offline engine and
 * method mapping as the timetable, and returns null until a location has been chosen.
 */
private fun nextPrayerPreview(prefs: SakinahPreferences): Pair<String, String>? {
    val coordinates = prefs.locationCoordinates ?: return null
    val zone = prefs.locationTimeZoneId
        ?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() }
        ?: TimeZone.currentSystemDefault()
    val now = Clock.System.now().toLocalDateTime(zone)
    val method = SunniMethodMapper.localCalculationMethod(
        SunniMethodMapper.resolve(prefs.locationCountryCode, prefs.calculationMethodOverride)
    )
    val engine = PrayerCalculationEngineImpl()
    val prayers = listOf(
        PrayerType.FAJR,
        PrayerType.DHUHR,
        PrayerType.ASR,
        PrayerType.MAGHRIB,
        PrayerType.ISHA
    )

    fun timesFor(date: LocalDate) = engine.getPrayerTimes(
        coordinates = coordinates,
        date = date,
        method = method,
        madhhab = prefs.madhhab,
        timeZone = zone,
        adjustments = prefs.prayerAdjustments,
        highLatitudeRule = prefs.highLatitudeRule
    )

    val nextToday = prayers.mapNotNull { type ->
        timesFor(now.date)[type]?.takeIf { it >= now.time }?.let { type to it }
    }.minByOrNull { it.second }
    val (type, time) = nextToday ?: run {
        val tomorrow = LocalDate.fromEpochDays(now.date.toEpochDays() + 1)
        val tomorrowFajr = timesFor(tomorrow)[PrayerType.FAJR] ?: return null
        PrayerType.FAJR to tomorrowFajr
    }
    val formatted = String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute)
    return type.englishName to formatted
}
