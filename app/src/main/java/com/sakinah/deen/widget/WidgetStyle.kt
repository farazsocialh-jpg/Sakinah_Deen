package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.unit.ColorProvider
import com.sakinah.deen.R
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.ThemeMode
import com.sakinah.deen.ui.theme.buildSakinahColorScheme
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
    val divider: Long,
    /** Hairline drawn around the 24dp surface; 0 means no border. */
    val border: Long = 0L,
    /** Track colour behind progress bars. */
    val track: Long = 0L,
    /** Text that sits on top of a solid [accent] fill. */
    val onAccent: Long = 0xFFFFFFFF
)

data class WidgetPalette(
    val background: ColorProvider,
    val primaryText: ColorProvider,
    val secondaryText: ColorProvider,
    val accent: ColorProvider,
    val onAccent: ColorProvider,
    val pillBackground: ColorProvider,
    val divider: ColorProvider,
    val track: ColorProvider,
    /** Rounded `<shape>` drawable painted behind the widget; 0 means a flat colour. */
    val backgroundImageRes: Int = 0,
    /** Hairline border colour, or null for a borderless skin. */
    val hairline: ColorProvider? = null
)

/** The settled look of the shape drawables Living Sky still paints per prayer window. */
internal fun skyBaseColor(window: PrayerWindow): Long = when (window) {
    PrayerWindow.FAJR -> 0xFF2B4C7E
    PrayerWindow.DHUHR -> 0xFF1D6FA5
    PrayerWindow.ASR -> 0xFF9A5B25
    PrayerWindow.MAGHRIB -> 0xFF4C2A6A
    PrayerWindow.ISHA -> 0xFF131B3A
}

internal fun skySecondaryColor(window: PrayerWindow): Long = when (window) {
    PrayerWindow.FAJR -> 0xFFD6E4F0
    PrayerWindow.DHUHR -> 0xFFE0F2FE
    PrayerWindow.ASR -> 0xFFFEF3C7
    PrayerWindow.MAGHRIB -> 0xFFE9D5FF
    PrayerWindow.ISHA -> 0xFFC7D2FE
}

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun Long.withAlpha(alpha: Float): Long =
    (this and 0x00FFFFFFL) or
        ((alpha.coerceIn(0f, 1f) * 255f).toInt().toLong() shl 24)

/**
 * The palette rules for all four skins, derived from the user's active [SakinahPalette].
 *
 * Every colour comes from [buildSakinahColorScheme], the same scheme the app itself uses,
 * so a widget can never drift from the theme the user picked. Frosted Glass keeps the
 * palette's translucent surface, Monochrome stays matte black/white with the palette
 * primary as its single accent, Transparent drops the surface entirely, and Living Sky
 * keeps its prayer-window sky but takes a bright palette primary for its pills.
 */
fun resolveWidgetColors(
    style: WidgetStyle,
    isDark: Boolean,
    window: PrayerWindow,
    palette: SakinahPalette = SakinahPalette.EMERALD_SANCTUARY
): WidgetColors {
    val scheme = buildSakinahColorScheme(palette, isDark)
    val primary = scheme.primary.argb()
    val onPrimary = scheme.onPrimary.argb()
    val onSurface = scheme.onSurface.argb()
    val onSurfaceVariant = scheme.onSurfaceVariant.argb()
    val surface = scheme.surface.argb()
    val outline = scheme.outline.argb()

    return when (style) {
        WidgetStyle.FROSTED_GLASS -> WidgetColors(
            background = surface.withAlpha(0.85f),
            primaryText = onSurface,
            secondaryText = onSurfaceVariant,
            accent = primary,
            pill = primary.withAlpha(0.12f),
            divider = outline.withAlpha(0.45f),
            border = outline.withAlpha(0.55f),
            track = onSurface.withAlpha(0.14f),
            onAccent = onPrimary
        )

        WidgetStyle.MONOCHROME -> {
            val ink = if (isDark) 0xFFFFFFFF else 0xFF0C0C0C
            WidgetColors(
                background = if (isDark) 0xFF0C0C0C else 0xFFFFFFFF,
                primaryText = ink,
                secondaryText = if (isDark) 0xFF9CA3AF else 0xFF525252,
                accent = primary,
                pill = primary.withAlpha(0.16f),
                divider = if (isDark) 0xFF262626 else 0xFFE5E7EB,
                border = if (isDark) 0xFF262626 else 0xFFE5E7EB,
                track = ink.withAlpha(0.12f),
                onAccent = onPrimary
            )
        }

        // The sky is always a dark surface, so pills borrow the palette's dark-scheme
        // primary — the bright variant of the same accent, readable on every window.
        WidgetStyle.LIVING_SKY -> WidgetColors(
            background = skyBaseColor(window),
            primaryText = 0xFFFFFFFF,
            secondaryText = skySecondaryColor(window),
            accent = buildSakinahColorScheme(palette, isDark = true).primary.argb(),
            pill = 0x29FFFFFF,
            divider = 0x33FFFFFF,
            border = 0x33FFFFFF,
            track = 0x33FFFFFF,
            onAccent = 0xFF101614
        )

        // No surface at all: only hairlines, pills and palette-derived text on the wallpaper.
        WidgetStyle.TRANSPARENT -> WidgetColors(
            background = 0x00000000,
            primaryText = if (isDark) 0xFFFFFFFF else 0xFF0C0C0C,
            secondaryText = if (isDark) 0xFFD1D5DB else 0xFF374151,
            accent = primary,
            pill = primary.withAlpha(0.18f),
            divider = if (isDark) 0x40FFFFFF else 0x30000000,
            border = if (isDark) 0x40FFFFFF else 0x30000000,
            track = (if (isDark) 0xFFFFFFFF else 0xFF0C0C0C).withAlpha(0.16f),
            onAccent = onPrimary
        )
    }
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

/** Wraps [resolveWidgetColors] in Glance colour providers for the current theme. */
fun resolveWidgetPalette(
    style: WidgetStyle,
    isDark: Boolean,
    window: PrayerWindow,
    palette: SakinahPalette = SakinahPalette.EMERALD_SANCTUARY
): WidgetPalette {
    val colors = resolveWidgetColors(style, isDark, window, palette)
    return WidgetPalette(
        background = ColorProvider(Color(colors.background)),
        primaryText = ColorProvider(Color(colors.primaryText)),
        secondaryText = ColorProvider(Color(colors.secondaryText)),
        accent = ColorProvider(Color(colors.accent)),
        onAccent = ColorProvider(Color(colors.onAccent)),
        pillBackground = ColorProvider(Color(colors.pill)),
        divider = ColorProvider(Color(colors.divider)),
        track = ColorProvider(Color(colors.track)),
        // Frosted Glass, Monochrome and Living Sky paint their bordered shape drawable;
        // Transparent stays a flat transparent surface with only a hairline border.
        backgroundImageRes = if (style == WidgetStyle.TRANSPARENT) {
            0
        } else {
            widgetBackgroundDrawable(style, isDark, window)
        },
        hairline = if (style == WidgetStyle.TRANSPARENT) {
            colors.border.takeIf { it != 0L }?.let { ColorProvider(Color(it)) }
        } else {
            null
        }
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
    private val leaveBufferKey = intPreferencesKey("leave_buffer_minutes")
    private val travelModeKey = booleanPreferencesKey("travel_mode")
    private val streakCountKey = intPreferencesKey("streak_count")
    private val streakDateKey = intPreferencesKey("streak_date")

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
        context.widgetPrefs.edit { prefs ->
            prefs[defaultStyleKey] = style.name
            // Push the new default to every placed widget so Settings changes apply immediately.
            prefs.asMap().keys.filter { it.name.startsWith("style_") }.forEach { key ->
                @Suppress("UNCHECKED_CAST")
                prefs[key as androidx.datastore.preferences.core.Preferences.Key<String>] = style.name
            }
        }
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
        context.widgetPrefs.edit { prefs ->
            prefs[defaultThemeModeKey] = mode.name
            prefs.asMap().keys.filter { it.name.startsWith("theme_mode_") }.forEach { key ->
                @Suppress("UNCHECKED_CAST")
                prefs[key as androidx.datastore.preferences.core.Preferences.Key<String>] = mode.name
            }
        }
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

    suspend fun leaveBufferMinutes(context: Context): Int =
        context.widgetPrefs.data.map { it[leaveBufferKey] ?: 10 }.first().coerceIn(0, 60)

    suspend fun setLeaveBufferMinutes(context: Context, minutes: Int) {
        context.widgetPrefs.edit { it[leaveBufferKey] = minutes.coerceIn(0, 60) }
    }

    suspend fun travelMode(context: Context): Boolean =
        context.widgetPrefs.data.map { it[travelModeKey] ?: false }.first()

    suspend fun setTravelMode(context: Context, enabled: Boolean) {
        context.widgetPrefs.edit { it[travelModeKey] = enabled }
    }

    suspend fun streakCount(context: Context): Int =
        context.widgetPrefs.data.map { it[streakCountKey] ?: 0 }.first()

    /** Bumps the streak when all five prayers are done today, once per day. */
    suspend fun bumpStreakIfComplete(context: Context, allComplete: Boolean) {
        if (!allComplete) return
        val today = LocalDate.now(activeZone(context)).toEpochDay().toInt()
        context.widgetPrefs.edit { prefs ->
            if (prefs[streakDateKey] != today) {
                prefs[streakCountKey] = (prefs[streakCountKey] ?: 0) + 1
                prefs[streakDateKey] = today
            }
        }
    }

    suspend fun toggleJamaahForWidget(context: Context, prayerKey: String) {
        val dao = salahDao(context) ?: return
        val epochDay = LocalDate.now(activeZone(context)).toEpochDay()
        val existing = dao.getSalahRecord(epochDay, prayerKey) ?: return
        dao.updateSalahRecord(existing.copy(isJamaah = !existing.isJamaah))
    }

    const val TASBIH_MAX = 33
}

/**
 * Theme mode -> dark flag. A widget set to System follows the app's own [appThemeMode] first,
 * so forcing the app light while the phone is dark still gives a light widget.
 */
internal fun resolveDarkMode(
    mode: WidgetThemeMode,
    appThemeMode: ThemeMode = ThemeMode.SYSTEM,
    systemDark: Boolean
): Boolean = when (mode) {
    WidgetThemeMode.SYSTEM -> when (appThemeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    WidgetThemeMode.LIGHT -> false
    WidgetThemeMode.DARK -> true
}

internal fun weekdayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
