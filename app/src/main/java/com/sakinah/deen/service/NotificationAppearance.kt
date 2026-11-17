package com.sakinah.deen.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.ThemeMode
import com.sakinah.deen.ui.theme.UserAppearanceConfig
import com.sakinah.deen.ui.theme.buildSakinahColorScheme

/**
 * Colours for the prayer notification, resolved from the user's saved appearance at build
 * time (palette + theme mode, with SYSTEM resolved from the current night configuration).
 *
 * Values are plain ARGB ints so they can be pushed straight into RemoteViews, which render
 * in the launcher/SystemUI process and cannot read Compose state.
 */
data class PrayerNotificationTheme(
    val isDark: Boolean,
    val palette: SakinahPalette,
    /** Card background surface. */
    val surface: Int,
    /** Primary text (title, countdown, Arabic reminder). */
    val onSurface: Int,
    /** Muted secondary text (window range, translation, source). */
    val secondaryText: Int,
    /** Accent used for the progress bar, countdown pill and action labels. */
    val accent: Int,
    /** Text drawn on an accent fill. */
    val onAccent: Int,
    /** Progress-bar track behind the accent fill. */
    val track: Int,
    val outline: Int
)

object NotificationAppearance {

    fun resolve(context: Context): PrayerNotificationTheme {
        val config = runCatching {
            AppearancePreferences(context).config.value
        }.getOrNull() ?: UserAppearanceConfig()
        // The OS notification card background always follows the phone's system dark/light
        // mode, regardless of the app's in-app themeMode. Generate all colours from the
        // system mode so text/track stay readable in every combination.
        val isDark = isSystemDark(context)
        return runCatching { themeFor(config.palette, isDark) }
            .getOrElse { fallbackTheme(context) }
    }

    /** Moments when SYSTEM must follow the device's current uiMode rather than a stored value. */
    fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun resolveIsDark(context: Context, mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark(context)
    }

    fun themeFor(palette: SakinahPalette, isDark: Boolean): PrayerNotificationTheme {
        val scheme = buildSakinahColorScheme(palette = palette, isDark = isDark)
        val surface = scheme.surface.toArgb()
        val onSurface = scheme.onSurface.toArgb()
        // A strict monochrome palette must not leak a colour-tinted accent.
        val accent = if (palette == SakinahPalette.PURE_MONOCHROME) {
            scheme.onSurface.toArgb()
        } else {
            scheme.primary.toArgb()
        }
        val onAccent = if (palette == SakinahPalette.PURE_MONOCHROME) {
            surface
        } else {
            scheme.onPrimary.toArgb()
        }
        return PrayerNotificationTheme(
            isDark = isDark,
            palette = palette,
            surface = surface,
            onSurface = onSurface,
            secondaryText = scheme.onSurfaceVariant.toArgb(),
            accent = accent,
            onAccent = onAccent,
            track = scheme.surfaceVariant.toArgb(),
            outline = scheme.outline.toArgb()
        )
    }

    /** Used only if palette resolution throws; keeps the reminder readable and never crashes. */
    private fun fallbackTheme(context: Context): PrayerNotificationTheme {
        val dark = isSystemDark(context)
        val surface = if (dark) AndroidColor.rgb(18, 18, 18) else AndroidColor.WHITE
        val onSurface = if (dark) AndroidColor.WHITE else AndroidColor.rgb(17, 17, 17)
        return PrayerNotificationTheme(
            isDark = dark,
            palette = SakinahPalette.EMERALD_SANCTUARY,
            surface = surface,
            onSurface = onSurface,
            secondaryText = if (dark) AndroidColor.rgb(170, 178, 174) else AndroidColor.rgb(95, 107, 102),
            accent = if (dark) AndroidColor.rgb(140, 224, 195) else AndroidColor.rgb(20, 98, 74),
            onAccent = surface,
            track = if (dark) AndroidColor.rgb(40, 44, 42) else AndroidColor.rgb(232, 238, 235),
            outline = if (dark) AndroidColor.rgb(58, 64, 61) else AndroidColor.rgb(217, 224, 220)
        )
    }
}
