package com.sakinah.deen.service

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import com.sakinah.deen.R
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

/**
 * High-contrast text colours for the ongoing prayer card. Unlike [PrayerNotificationTheme]
 * they are not derived from the app palette: a tinted palette colour can lose contrast on
 * a dark or translucent shade (One UI), so these come from `values/` and `values-night/`.
 */
data class NotificationTextColors(
    val primary: Int,
    val secondary: Int,
    val accent: Int
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

    /**
     * The notification shade follows the device's night mode, which the app cannot override
     * with its own AppCompat night mode. Read the system configuration first so text stays
     * light in the shade even when the app itself is forced to a light theme.
     */
    fun isSystemDark(context: Context): Boolean {
        val uiMode = runCatching { Resources.getSystem().configuration.uiMode }
            .getOrDefault(context.resources.configuration.uiMode)
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Palette-independent, high-contrast text colours for the prayer notification card. */
    fun textColors(context: Context): NotificationTextColors {
        val dark = isSystemDark(context)
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        val nightAware = context.createConfigurationContext(config)
        return NotificationTextColors(
            primary = nightAware.getColor(R.color.notif_text_primary),
            secondary = nightAware.getColor(R.color.notif_text_secondary),
            accent = nightAware.getColor(R.color.notif_text_accent)
        )
    }

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
