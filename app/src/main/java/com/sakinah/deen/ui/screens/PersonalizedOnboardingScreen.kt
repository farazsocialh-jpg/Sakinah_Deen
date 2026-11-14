package com.sakinah.deen.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.PrayerGuideGender
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.ThemeMode
import com.sakinah.deen.ui.theme.UserAppearanceConfig
import com.sakinah.deen.ui.theme.UserObjective
import com.sakinah.deen.ui.theme.UthmanicFontFamily

private val SageBackground = Color(0xFFEEF2EE)
private val SageCard = Color(0xFFF7F9F7)
private val SageSelected = Color(0xFFE0EBE4)
private val SageInk = Color(0xFF1E2622)
private val SageMuted = Color(0xFF5A6860)
private val SageAccent = Color(0xFF2F6B55)
private val SageBorder = Color(0xFFD3DDD6)

private val OnboardingSageScheme = lightColorScheme(
    primary = SageAccent,
    onPrimary = Color.White,
    primaryContainer = SageSelected,
    onPrimaryContainer = SageInk,
    secondary = SageAccent,
    onSecondary = Color.White,
    secondaryContainer = SageSelected,
    onSecondaryContainer = SageInk,
    background = SageBackground,
    onBackground = SageInk,
    surface = SageCard,
    onSurface = SageInk,
    surfaceVariant = SageSelected,
    onSurfaceVariant = SageMuted,
    outline = SageBorder
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizedOnboardingScreen(
    initialConfig: UserAppearanceConfig = UserAppearanceConfig(),
    onConfigPreviewChange: (UserAppearanceConfig) -> Unit = {},
    onComplete: (UserAppearanceConfig) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var config by remember(initialConfig) { mutableStateOf(initialConfig) }

    fun updateConfig(updated: UserAppearanceConfig) {
        config = updated
        onConfigPreviewChange(updated)
    }

    val totalSteps = 3
    val experienceLevels = remember {
        listOf("New to Islam", "Practicing Muslim", "Student of Knowledge")
    }

    MaterialTheme(colorScheme = OnboardingSageScheme) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar with progress and optional dismiss
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sakinah",
                            fontFamily = FontFamily.Serif,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = SageAccent
                        )
                        Text(
                            text = "Step ${step + 1} of $totalSteps · Personalize Your Companion",
                            style = MaterialTheme.typography.bodySmall,
                            color = SageMuted
                        )
                    }
                    if (onDismiss != null) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = SageMuted
                            )
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { (step + 1) / totalSteps.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SageAccent,
                    trackColor = SageSelected
                )
            }

            // Step Content
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "OnboardingStepTransition"
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (currentStep) {
                        0 -> {
                            Text(
                                text = "Assalamu Alaikum",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = SageInk
                            )
                            Text(
                                text = "Tell us a little about your journey so Sakinah can tailor your daily experience.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SageMuted
                            )

                            OutlinedTextField(
                                value = config.userName,
                                onValueChange = { updateConfig(config.copy(userName = it)) },
                                label = { Text("Preferred name (optional)") },
                                placeholder = { Text("e.g., Abdullah, Amina") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "Where are you in your journey?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SageAccent
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                experienceLevels.forEach { level ->
                                    FilterChip(
                                        selected = config.experienceLevel == level,
                                        onClick = { updateConfig(config.copy(experienceLevel = level)) },
                                        label = { Text(level) },
                                        leadingIcon = if (config.experienceLevel == level) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }

                            Text(
                                text = "Visual Guide & Prayer Style",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SageAccent
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PrayerGuideGender.entries.forEach { gender ->
                                    val selected = config.prayerGuideGender == gender
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                updateConfig(config.copy(prayerGuideGender = gender))
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        contentColor = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        border = BorderStroke(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            }
                                        )
                                    ) {
                                        Text(
                                            text = gender.label,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 14.dp),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            SakinahCard(
                                containerColor = SageSelected
                            ) {
                                Text(
                                    text = "Offline-First & Private",
                                    fontWeight = FontWeight.SemiBold,
                                    color = SageAccent,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Your preferences, prayer logs, and reading bookmarks remain stored locally on your device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SageMuted
                                )
                            }
                        }

                        1 -> {
                            val greeting = config.userName.trim().takeIf { it.isNotBlank() }?.let { "Welcome, $it" }
                                ?: "Choose Your Primary Focus"
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = SageInk
                            )
                            Text(
                                text = "We will open Sakinah directly to the space that serves you most.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SageMuted
                            )

                            UserObjective.entries.forEach { objective ->
                                val selected = config.objective == objective
                                val icon = objectiveIcon(objective)
                                SakinahCard(
                                    onClick = {
                                        val recommendedPalette = when (objective) {
                                            UserObjective.PRAYER_COMPASS -> SakinahPalette.EMERALD_SANCTUARY
                                            UserObjective.QURAN_STUDY -> SakinahPalette.ANDALUSIAN_SAPPHIRE
                                            UserObjective.HADITH_SCHOLAR -> SakinahPalette.MADINAH_SAND
                                            UserObjective.ALL_IN_ONE -> SakinahPalette.EMERALD_SANCTUARY
                                        }
                                        updateConfig(
                                            config.copy(
                                                objective = objective,
                                                palette = recommendedPalette,
                                                focusModeHideDistractions =
                                                    objective == UserObjective.QURAN_STUDY
                                            )
                                        )
                                    },
                                    containerColor = if (selected) {
                                        SageSelected
                                    } else {
                                        SageCard
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (selected) {
                                                SageAccent
                                            } else {
                                                SageSelected
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (selected) {
                                                        Color.White
                                                    } else {
                                                        SageAccent
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = objective.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = SageInk
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = objective.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SageMuted
                                            )
                                        }
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = SageAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = "Appearance & Focus",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = SageInk
                            )
                            Text(
                                text = "Adjust colors, typography scale, and distraction-free reading in real time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SageMuted
                            )

                            // Live Preview Card
                            SakinahCard(
                                containerColor = SageCard
                            ) {
                                Text(
                                    text = "LIVE PREVIEW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = SageAccent
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                    fontFamily = UthmanicFontFamily,
                                    fontSize = 24.sp,
                                    color = SageInk,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SageMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Text(
                                text = "Theme Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SageAccent
                            )
                            SegmentedPillBar(
                                options = ThemeMode.entries,
                                selected = config.themeMode,
                                labelProvider = { it.label },
                                onSelect = { updateConfig(config.copy(themeMode = it)) }
                            )

                            Text(
                                text = "Color Palette",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SageAccent
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SakinahPalette.entries.forEach { palette ->
                                    val selected = config.palette == palette
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { updateConfig(config.copy(palette = palette)) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) {
                                            SageSelected
                                        } else {
                                            SageCard
                                        },
                                        border = BorderStroke(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) {
                                                SageAccent
                                            } else {
                                                SageBorder
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(palette.lightPreviewPrimary)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(palette.lightPreviewAccent)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = palette.label,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    color = SageInk
                                                )
                                                Text(
                                                    text = palette.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SageMuted
                                                )
                                            }
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = SageAccent
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Text & Arabic Font Size",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SageAccent
                            )
                            SegmentedPillBar(
                                options = FontScalePreset.entries,
                                selected = config.fontScale,
                                labelProvider = { it.label },
                                onSelect = { updateConfig(config.copy(fontScale = it)) }
                            )

                            SakinahCard(contentPadding = 14.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Focus Mode",
                                                fontWeight = FontWeight.SemiBold,
                                                color = SageInk
                                            )
                                            Text(
                                                text = "Default Quran to distraction-free Mus-haf and keep Salah clean.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SageMuted
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Switch(
                                            checked = config.focusModeHideDistractions,
                                            onCheckedChange = {
                                                updateConfig(config.copy(focusModeHideDistractions = it))
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
                                                color = SageInk
                                            )
                                            Text(
                                                text = "Hide top & bottom navigation bars while scrolling down.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SageMuted
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Switch(
                                            checked = config.autoHideBarsOnScroll,
                                            onCheckedChange = {
                                                updateConfig(config.copy(autoHideBarsOnScroll = it))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Back")
                    }
                } else if (onDismiss != null) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Cancel")
                    }
                }

                Button(
                    onClick = {
                        if (step < totalSteps - 1) {
                            step++
                        } else {
                            onComplete(config.copy(onboardingCompleted = true))
                        }
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (step < totalSteps - 1) "Continue" else "Enter Sakinah",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    }
}

private fun objectiveIcon(objective: UserObjective): ImageVector = when (objective) {
    UserObjective.PRAYER_COMPASS -> Icons.Default.Mosque
    UserObjective.QURAN_STUDY -> Icons.AutoMirrored.Filled.MenuBook
    UserObjective.HADITH_SCHOLAR -> Icons.AutoMirrored.Filled.LibraryBooks
    UserObjective.ALL_IN_ONE -> Icons.Default.Explore
}
