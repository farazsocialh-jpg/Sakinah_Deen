package com.sakinah.deen.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.components.ThemePalettePicker
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.PrayerGuideGender
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.SakinahTheme
import com.sakinah.deen.ui.theme.ThemeMode
import com.sakinah.deen.ui.theme.UserAppearanceConfig
import com.sakinah.deen.ui.theme.UserObjective
import com.sakinah.deen.ui.theme.UthmanicFontFamily

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
            quranReadingLayout = values.getOrNull(10)
                ?.let { runCatching { QuranReadingLayout.valueOf(it) }.getOrNull() }
                ?: QuranReadingLayout.DIVIDER_HAIRLINE,
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
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val battery: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizedOnboardingScreen(
    modifier: Modifier = Modifier,
    initialConfig: UserAppearanceConfig = UserAppearanceConfig(),
    onConfigPreviewChange: (UserAppearanceConfig) -> Unit = {},
    onComplete: (UserAppearanceConfig) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var config by rememberSaveable(initialConfig, stateSaver = UserAppearanceConfigSaver) {
        mutableStateOf(initialConfig)
    }

    BackHandler(enabled = step > 0) { step-- }

    fun updateConfig(updated: UserAppearanceConfig) {
        config = updated
        onConfigPreviewChange(updated)
    }

    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Returning from a system settings screen must flip the tiles without a manual refresh.
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
    ) { permissionTick++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }

    val darkTheme = when (config.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // Once the user picks another palette or Dark mode, the live theme owns every colour.
    val themePreviewActive = config.themeMode != initialConfig.themeMode ||
        config.palette != initialConfig.palette
    val defaultLook = !darkTheme && !themePreviewActive

    val heroColor = if (defaultLook) AlabasterHero else MaterialTheme.colorScheme.background
    val sheetColor = if (defaultLook) ElevatedSheet else MaterialTheme.colorScheme.surface
    val accent = if (defaultLook) ForestEmerald else MaterialTheme.colorScheme.primary
    val onAccent = if (defaultLook) Color.White else MaterialTheme.colorScheme.onPrimary
    val accentSoft = if (defaultLook) SoftSage else MaterialTheme.colorScheme.primaryContainer
    val onAccentSoft = if (defaultLook) ForestEmerald else MaterialTheme.colorScheme.onPrimaryContainer

    val finish = { onComplete(config.copy(onboardingCompleted = true)) }

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
                    accent = accent,
                    accentSoft = accentSoft,
                    onAccentSoft = onAccentSoft,
                    arabicFontSize = (config.arabicFontSize * config.fontScale.multiplier).sp,
                    translationFontSize = (config.translationFontSize * config.fontScale.multiplier).sp,
                    sheetColor = sheetColor,
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
                        2 -> ThemeStep(
                            config = config,
                            accent = accent,
                            accentSoft = accentSoft,
                            onConfigChange = ::updateConfig
                        )
                        else -> SetupStep(
                            permissions = permissions,
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
                            onAllowNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onAllowExactAlarms = { openExactAlarmSettings(context) },
                            onAllowBattery = { openBatteryOptimizationSettings(context) }
                        )
                    }
                }

                OnboardingCta(
                    label = if (step < TOTAL_STEPS - 1) {
                        stringResource(R.string.onboarding_continue)
                    } else {
                        stringResource(R.string.onboarding_open_app)
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

@Composable
private fun HeroZone(
    modifier: Modifier,
    step: Int,
    userName: String,
    accent: Color,
    accentSoft: Color,
    onAccentSoft: Color,
    arabicFontSize: androidx.compose.ui.unit.TextUnit,
    translationFontSize: androidx.compose.ui.unit.TextUnit,
    sheetColor: Color,
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
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mosque,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.onboarding_brand),
                        fontFamily = FontFamily.Serif,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        text = stringResource(R.string.onboarding_welcome_greeting),
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = sheetColor,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_live_preview),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = accent
                            )
                            Text(
                                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                fontFamily = UthmanicFontFamily,
                                fontSize = arabicFontSize,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(R.string.onboarding_bismillah_translation),
                                fontSize = translationFontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
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
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = if (name.isNotBlank()) {
                            stringResource(R.string.onboarding_setup_heading_named, name)
                        } else {
                            stringResource(R.string.onboarding_setup_heading)
                        },
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.onboarding_setup_subtitle),
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
    bodyMaxHeight: androidx.compose.ui.unit.Dp,
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
                    val enter = slideInHorizontally { width -> if (forward) width / 3 else -width / 3 } + fadeIn()
                    val exit = slideOutHorizontally { width -> if (forward) -width / 3 else width / 3 } + fadeOut()
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
    accent: Color,
    accentSoft: Color,
    onConfigChange: (UserAppearanceConfig) -> Unit
) {
    Text(
        text = stringResource(R.string.onboarding_mission),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
        text = stringResource(R.string.onboarding_name_prompt),
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

    Spacer(Modifier.height(2.dp))
    // Soft sage rule keeps the sheet's emerald identity visible even in monochrome palettes.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(accentSoft)
    )
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
private fun ThemeStep(
    config: UserAppearanceConfig,
    accent: Color,
    accentSoft: Color,
    onConfigChange: (UserAppearanceConfig) -> Unit
) {
    Text(
        text = stringResource(R.string.onboarding_color_theme),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    // Labels are resolved here: a label provider lambda is not a composable scope.
    SegmentedPillBar(
        options = ThemeMode.entries.map { themeModeLabel(it) },
        selectedIndex = config.themeMode.ordinal,
        onSelect = { index ->
            ThemeMode.entries.getOrNull(index)?.let { onConfigChange(config.copy(themeMode = it)) }
        }
    )
    ThemePalettePicker(
        selected = config.palette,
        onSelect = { onConfigChange(config.copy(palette = it)) }
    )

    Text(
        text = stringResource(R.string.onboarding_font_size),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    SegmentedPillBar(
        options = FontScalePreset.entries.map { fontScaleLabel(it) },
        selectedIndex = config.fontScale.ordinal,
        onSelect = { index ->
            FontScalePreset.entries.getOrNull(index)?.let { onConfigChange(config.copy(fontScale = it)) }
        }
    )

    SizeStepperRow(
        label = stringResource(R.string.onboarding_arabic_size),
        value = config.arabicFontSize,
        min = ARABIC_FONT_MIN,
        max = ARABIC_FONT_MAX,
        accent = accent,
        accentSoft = accentSoft,
        onValueChange = { onConfigChange(config.copy(arabicFontSize = it)) }
    )
    SizeStepperRow(
        label = stringResource(R.string.onboarding_translation_size),
        value = config.translationFontSize,
        min = TRANSLATION_FONT_MIN,
        max = TRANSLATION_FONT_MAX,
        accent = accent,
        accentSoft = accentSoft,
        onValueChange = { onConfigChange(config.copy(translationFontSize = it)) }
    )
}

@Composable
private fun SizeStepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    accent: Color,
    accentSoft: Color,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        StepperButton(
            symbol = "−",
            description = stringResource(R.string.onboarding_decrease),
            enabled = value > min,
            accent = accent,
            accentSoft = accentSoft,
            onClick = { onValueChange((value - 1).coerceAtLeast(min)) }
        )
        Text(
            text = value.toString(),
            modifier = Modifier.width(40.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        StepperButton(
            symbol = "+",
            description = stringResource(R.string.onboarding_increase),
            enabled = value < max,
            accent = accent,
            accentSoft = accentSoft,
            onClick = { onValueChange((value + 1).coerceAtMost(max)) }
        )
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    accent: Color,
    accentSoft: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(accentSoft)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) accent else accent.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun SetupStep(
    permissions: PermissionState,
    accent: Color,
    onAccent: Color,
    accentSoft: Color,
    onAccentSoft: Color,
    onAllowLocation: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowExactAlarms: () -> Unit,
    onAllowBattery: () -> Unit
) {
    val tiles = listOf(
        PermissionTileModel(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.onboarding_perm_location_title),
            reason = stringResource(R.string.onboarding_perm_location_reason),
            granted = permissions.location,
            onAllow = onAllowLocation
        ),
        PermissionTileModel(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.onboarding_perm_notifications_title),
            reason = stringResource(R.string.onboarding_perm_notifications_reason),
            granted = permissions.notifications,
            onAllow = onAllowNotifications
        ),
        PermissionTileModel(
            icon = Icons.Default.Alarm,
            title = stringResource(R.string.onboarding_perm_alarms_title),
            reason = stringResource(R.string.onboarding_perm_alarms_reason),
            granted = permissions.exactAlarms,
            onAllow = onAllowExactAlarms
        ),
        PermissionTileModel(
            icon = Icons.Default.BatteryChargingFull,
            title = stringResource(R.string.onboarding_perm_battery_title),
            reason = stringResource(R.string.onboarding_perm_battery_reason),
            granted = permissions.battery,
            onAllow = onAllowBattery
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { tile ->
                    PermissionTile(
                        model = tile,
                        accent = accent,
                        onAccent = onAccent,
                        accentSoft = accentSoft,
                        onAccentSoft = onAccentSoft,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    Text(
        text = stringResource(R.string.onboarding_no_ads),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private data class PermissionTileModel(
    val icon: ImageVector,
    val title: String,
    val reason: String,
    val granted: Boolean,
    val onAllow: () -> Unit
)

@Composable
private fun PermissionTile(
    model: PermissionTileModel,
    accent: Color,
    onAccent: Color,
    accentSoft: Color,
    onAccentSoft: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        color = if (model.granted) accentSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (model.granted) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = model.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = model.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = model.reason,
                fontSize = 11.sp,
                minLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (model.granted) {
                            Modifier.background(accent)
                        } else {
                            Modifier.border(1.dp, accent, shape)
                        }
                    )
                    .clickable(enabled = !model.granted, onClick = model.onAllow)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (model.granted) {
                        stringResource(R.string.onboarding_perm_allowed)
                    } else {
                        stringResource(R.string.onboarding_perm_allow)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (model.granted) onAccent else accent
                )
            }
        }
    }
}

@Composable
private fun experienceLabel(level: String): String = when (level) {
    EXPERIENCE_NEW -> stringResource(R.string.onboarding_experience_new)
    EXPERIENCE_STUDENT -> stringResource(R.string.onboarding_experience_student)
    else -> stringResource(R.string.onboarding_experience_practicing)
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.onboarding_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.onboarding_theme_light)
    ThemeMode.DARK -> stringResource(R.string.onboarding_theme_dark)
}

@Composable
private fun fontScaleLabel(preset: FontScalePreset): String = when (preset) {
    FontScalePreset.COMPACT -> stringResource(R.string.onboarding_font_compact)
    FontScalePreset.STANDARD -> stringResource(R.string.onboarding_font_standard)
    FontScalePreset.COMFORTABLE -> stringResource(R.string.onboarding_font_comfort)
    FontScalePreset.LARGE -> stringResource(R.string.onboarding_font_large)
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
        hasPermission(context, Manifest.permission.POST_NOTIFICATIONS),
    exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
            ?.canScheduleExactAlarms() == true,
    battery = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
)

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val direct = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        "package:${context.packageName}".toUri()
    )
    launchOrFallback(context, direct, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
}

private fun openBatteryOptimizationSettings(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${context.packageName}".toUri()
    )
    launchOrFallback(context, direct, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

private fun launchOrFallback(context: Context, primary: Intent, fallback: Intent) {
    val launched = runCatching {
        context.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!launched) {
        runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
