package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.unit.ColorProvider
import com.sakinah.deen.R
import com.sakinah.deen.ui.theme.SakinahPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale

/** The four selectable widget skins. */
enum class WidgetStyle(val label: String, val description: String) {
    FROSTED_GLASS("Frosted Glass", "Translucent surface with an emerald accent"),
    MONOCHROME("Bold Monochrome", "High-contrast matte black or white"),
    LIVING_SKY("Living Sky", "Atmospheric colour that follows the prayer window"),
    TRANSPARENT("Minimal Transparent", "Hairlines over your wallpaper")
}

/** Per-widget light/dark override, falling back to the app-wide default. */
enum class WidgetThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

/** Prayer whose window is currently active; drives the Living Sky colour. */
enum class PrayerWindow { FAJR, DHUHR, ASR, MAGHRIB, ISHA }

/** Raw ARGB colours, kept free of Android types so they are unit-testable. */
data class WidgetColors(
    val background: Long,
    val primaryText: Long,
    val secondaryText: Long,
    val accent: Long,
    val pill: Long,
    val divider: Long
)

data class WidgetPalette(
    val background: ColorProvider,
    val primaryText: ColorProvider,
    val secondaryText: ColorProvider,
    val accent: ColorProvider,
    val pillBackground: ColorProvider,
    val divider: ColorProvider,
    /** Rounded `<shape>` drawable painted behind the widget; 0 means a flat colour. */
    val backgroundImageRes: Int = 0
)

/**
 * The palette rules for all four skins. `isDark` is already resolved from the widget's
 * theme mode, so a widget can stay dark while the phone is light and vice versa.
 *
 * [accentOverride] carries the active [SakinahPalette] accent into the two skins that
 * follow the app theme (Frosted Glass and Transparent); Living Sky and Monochrome keep
 * their own fixed accents so their identity survives a palette change.
 */
fun resolveWidgetColors(
    style: WidgetStyle,
    isDark: Boolean,
    window: PrayerWindow,
    accentOverride: Long? = null
): WidgetColors = when (style) {
    WidgetStyle.FROSTED_GLASS -> if (isDark) {
        WidgetColors(
            background = 0xCC0F1715,
            primaryText = 0xFFF1F5F9,
            secondaryText = 0xFFA7B5B0,
            accent = accentOverride ?: 0xFF34D399,
            pill = 0x26FFFFFF,
            divider = 0x33FFFFFF
        )
    } else {
        WidgetColors(
            background = 0xD9F7F9F8,
            primaryText = 0xFF0B3B2C,
            secondaryText = 0xFF4B635B,
            accent = accentOverride ?: 0xFF10B981,
            pill = 0x140B3B2C,
            divider = 0x1F0B3B2C
        )
    }

    WidgetStyle.MONOCHROME -> if (isDark) {
        WidgetColors(
            background = 0xFF0C0C0C,
            primaryText = 0xFFFFFFFF,
            secondaryText = 0xFF9CA3AF,
            accent = 0xFF10B981,
            pill = 0xFF1A1A1A,
            divider = 0xFF262626
        )
    } else {
        WidgetColors(
            background = 0xFFFFFFFF,
            primaryText = 0xFF0C0C0C,
            secondaryText = 0xFF525252,
            accent = 0xFF10B981,
            pill = 0xFFF3F4F6,
            divider = 0xFFE5E7EB
        )
    }

    WidgetStyle.LIVING_SKY -> when (window) {
        PrayerWindow.FAJR -> WidgetColors(
            background = 0xFF2B4C7E,
            primaryText = 0xFFFFFFFF,
            secondaryText = 0xFFD6E4F0,
            accent = 0xFFFDE68A,
            pill = 0x29FFFFFF,
            divider = 0x33FFFFFF
        )
        PrayerWindow.DHUHR -> WidgetColors(
            background = 0xFF1D6FA5,
            primaryText = 0xFFFFFFFF,
            secondaryText = 0xFFE0F2FE,
            accent = 0xFFFBBF24,
            pill = 0x29FFFFFF,
            divider = 0x33FFFFFF
        )
        PrayerWindow.ASR -> WidgetColors(
            background = 0xFF9A5B25,
            primaryText = 0xFFFFFFFF,
            secondaryText = 0xFFFEF3C7,
            accent = 0xFFFCD34D,
            pill = 0x29FFFFFF,
            divider = 0x33FFFFFF
        )
        PrayerWindow.MAGHRIB -> WidgetColors(
            background = 0xFF4C2A6A,
            primaryText = 0xFFFFFFFF,
            secondaryText = 0xFFE9D5FF,
            accent = 0xFFF9A8D4,
            pill = 0x29FFFFFF,
            divider = 0x33FFFFFF
        )
        PrayerWindow.ISHA -> WidgetColors(
            background = 0xFF131B3A,
            primaryText = 0xFFFFFFFF,
            secondaryText = 0xFFC7D2FE,
            accent = 0xFF34D399,
            pill = 0x26FFFFFF,
            divider = 0x33FFFFFF
        )
    }

    // Minimal transparent: no surface, only hairlines and a faint pill highlight.
    WidgetStyle.TRANSPARENT -> WidgetColors(
        background = 0x00000000,
        primaryText = if (isDark) 0xFFFFFFFF else 0xFF0B3B2C,
        secondaryText = if (isDark) 0xFFD1D5DB else 0xFF374151,
        accent = accentOverride ?: 0xFF10B981,
        pill = if (isDark) 0x22FFFFFF else 0x18000000,
        divider = if (isDark) 0x40FFFFFF else 0x30000000
    )
}

/** Rounded, bordered background drawable for a skin; Living Sky uses real gradients. */
fun widgetBackgroundDrawable(style: WidgetStyle, isDark: Boolean, window: PrayerWindow): Int =
    when (style) {
        WidgetStyle.FROSTED_GLASS ->
            if (isDark) R.drawable.widget_bg_frosted_dark else R.drawable.widget_bg_frosted_light
        WidgetStyle.MONOCHROME ->
            if (isDark) R.drawable.widget_bg_monochrome_dark else R.drawable.widget_bg_monochrome_light
        WidgetStyle.TRANSPARENT ->
            if (isDark) R.drawable.widget_bg_transparent_dark else R.drawable.widget_bg_transparent_light
        WidgetStyle.LIVING_SKY -> when (window) {
            PrayerWindow.FAJR -> R.drawable.widget_bg_sky_fajr
            PrayerWindow.DHUHR -> R.drawable.widget_bg_sky_dhuhr
            PrayerWindow.ASR -> R.drawable.widget_bg_sky_asr
            PrayerWindow.MAGHRIB -> R.drawable.widget_bg_sky_maghrib
            PrayerWindow.ISHA -> R.drawable.widget_bg_sky_isha
        }
    }

/** The active app palette's accent, as the ARGB value a widget palette expects. */
fun SakinahPalette.widgetAccent(isDark: Boolean): Long {
    val color = if (isDark) darkPreviewPrimary else lightPreviewAccent
    return color.toArgb().toLong() and 0xFFFFFFFFL
}

/** Wraps [resolveWidgetColors] in Glance colour providers. */
fun resolveWidgetPalette(
    style: WidgetStyle,
    isDark: Boolean,
    window: PrayerWindow,
    accentOverride: Long? = null
): WidgetPalette {
    val colors = resolveWidgetColors(style, isDark, window, accentOverride)
    return WidgetPalette(
        background = ColorProvider(Color(colors.background)),
        primaryText = ColorProvider(Color(colors.primaryText)),
        secondaryText = ColorProvider(Color(colors.secondaryText)),
        accent = ColorProvider(Color(colors.accent)),
        pillBackground = ColorProvider(Color(colors.pill)),
        divider = ColorProvider(Color(colors.divider)),
        backgroundImageRes = widgetBackgroundDrawable(style, isDark, window)
    )
}

private val Context.widgetPrefs by preferencesDataStore(name = "imancore_widget_prefs")

/** Per-widget-instance style choice, with an app-wide default. */
object WidgetPrefsStore {
    private fun styleKey(appWidgetId: Int) = stringPreferencesKey("style_$appWidgetId")
    private fun themeKey(appWidgetId: Int) = stringPreferencesKey("theme_mode_$appWidgetId")
    private val defaultStyleKey = stringPreferencesKey("style_default")
    private val defaultThemeModeKey = stringPreferencesKey("theme_mode_default")

    private val tasbihKey = intPreferencesKey("tasbih_count")
    private val ayahIndexKey = intPreferencesKey("ayah_index")

    suspend fun getStyle(context: Context, appWidgetId: Int): WidgetStyle {
        val raw = context.widgetPrefs.data.map { prefs ->
            prefs[styleKey(appWidgetId)] ?: prefs[defaultStyleKey]
        }.first()
        return runCatching { WidgetStyle.valueOf(raw ?: "") }.getOrDefault(WidgetStyle.FROSTED_GLASS)
    }

    suspend fun setStyle(context: Context, appWidgetId: Int, style: WidgetStyle) {
        context.widgetPrefs.edit { prefs -> prefs[styleKey(appWidgetId)] = style.name }
    }

    suspend fun getDefaultStyle(context: Context): WidgetStyle {
        val raw = context.widgetPrefs.data.map { it[defaultStyleKey] }.first()
        return runCatching { WidgetStyle.valueOf(raw ?: "") }.getOrDefault(WidgetStyle.FROSTED_GLASS)
    }

    suspend fun setDefaultStyle(context: Context, style: WidgetStyle) {
        context.widgetPrefs.edit { prefs -> prefs[defaultStyleKey] = style.name }
    }

    suspend fun getThemeMode(context: Context, appWidgetId: Int): WidgetThemeMode {
        val raw = context.widgetPrefs.data.map { prefs ->
            prefs[themeKey(appWidgetId)] ?: prefs[defaultThemeModeKey]
        }.first()
        return runCatching { WidgetThemeMode.valueOf(raw ?: "") }.getOrDefault(WidgetThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(context: Context, appWidgetId: Int, mode: WidgetThemeMode) {
        context.widgetPrefs.edit { prefs -> prefs[themeKey(appWidgetId)] = mode.name }
    }

    suspend fun getDefaultThemeMode(context: Context): WidgetThemeMode {
        val raw = context.widgetPrefs.data.map { it[defaultThemeModeKey] }.first()
        return runCatching { WidgetThemeMode.valueOf(raw ?: "") }.getOrDefault(WidgetThemeMode.SYSTEM)
    }

    suspend fun setDefaultThemeMode(context: Context, mode: WidgetThemeMode) {
        context.widgetPrefs.edit { prefs -> prefs[defaultThemeModeKey] = mode.name }
    }

    suspend fun tasbihCount(context: Context): Int =
        context.widgetPrefs.data.map { it[tasbihKey] ?: 0 }.first().coerceIn(0, TASBIH_MAX)

    /** Advances the dhikr counter, looping back to zero after the 33rd repetition. */
    suspend fun incrementTasbih(context: Context): Int {
        var next = 0
        context.widgetPrefs.edit { prefs ->
            val current = (prefs[tasbihKey] ?: 0).coerceIn(0, TASBIH_MAX)
            next = if (current >= TASBIH_MAX) 0 else current + 1
            prefs[tasbihKey] = next
        }
        return next
    }

    suspend fun resetTasbih(context: Context) {
        context.widgetPrefs.edit { prefs -> prefs[tasbihKey] = 0 }
    }

    /** Where the rotating Ayah currently sits in the curated list. */
    suspend fun ayahIndex(context: Context): Int =
        context.widgetPrefs.data.map { it[ayahIndexKey] ?: 0 }.first().coerceAtLeast(0)

    /** Moves to the next verse; called on phone unlock and by the widget's ↻ button. */
    suspend fun advanceAyahIndex(context: Context): Int {
        var next = 0
        context.widgetPrefs.edit { prefs ->
            next = (prefs[ayahIndexKey] ?: 0) + 1
            prefs[ayahIndexKey] = next
        }
        return next
    }

    const val TASBIH_MAX = 33
}

/** Theme mode -> dark flag, honouring the widget's own override. */
internal fun resolveDarkMode(mode: WidgetThemeMode, systemDark: Boolean): Boolean = when (mode) {
    WidgetThemeMode.SYSTEM -> systemDark
    WidgetThemeMode.LIGHT -> false
    WidgetThemeMode.DARK -> true
}

internal fun weekdayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
