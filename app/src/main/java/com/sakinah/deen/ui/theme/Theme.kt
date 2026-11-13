package com.sakinah.deen.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

@Composable
fun SakinahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: SakinahPalette = SakinahPalette.EMERALD_SANCTUARY,
    fontScaleMultiplier: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = buildSakinahColorScheme(palette = palette, isDark = darkTheme)
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

    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * fontScaleMultiplier.coerceIn(0.8f, 1.4f)
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SakinahTypography,
            content = content
        )
    }
}
