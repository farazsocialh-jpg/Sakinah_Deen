package com.sakinah.deen.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SakinahLightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = MintSubtle,
    onPrimaryContainer = EmeraldDeep,
    secondary = MintGreen,
    onSecondary = EmeraldDeep,
    secondaryContainer = MintSubtle,
    onSecondaryContainer = EmeraldDeep,
    background = WarmCream,
    onBackground = DarkCharcoal,
    surface = CardSurface,
    onSurface = DarkCharcoal,
    surfaceVariant = Color(0xFFF1F5F3),
    onSurfaceVariant = SubtitleGray,
    outline = BorderHairline
)

private val SakinahDarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightBackground,
    primaryContainer = NightSurfaceVariant,
    onPrimaryContainer = NightPrimary,
    secondary = MintGreen,
    onSecondary = NightBackground,
    secondaryContainer = NightSurfaceVariant,
    onSecondaryContainer = NightTextPrimary,
    background = NightBackground,
    onBackground = NightTextPrimary,
    surface = NightSurface,
    onSurface = NightTextPrimary,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightTextSecondary,
    outline = NightBorder
)

@Composable
fun SakinahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SakinahDarkColorScheme else SakinahLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SakinahTypography,
        content = content
    )
}
