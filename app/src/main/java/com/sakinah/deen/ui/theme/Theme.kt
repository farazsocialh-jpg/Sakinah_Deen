package com.sakinah.deen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SakinahLightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = MintGreen,
    onPrimaryContainer = EmeraldDeep,
    secondary = MintGreen,
    onSecondary = EmeraldDeep,
    background = WarmCream,
    onBackground = DarkCharcoal,
    surface = CardSurface,
    onSurface = DarkCharcoal,
    surfaceVariant = WarmCream,
    onSurfaceVariant = SubtitleGray,
    outline = BorderHairline
)

@Composable
fun SakinahTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SakinahLightColorScheme,
        typography = SakinahTypography,
        content = content
    )
}
