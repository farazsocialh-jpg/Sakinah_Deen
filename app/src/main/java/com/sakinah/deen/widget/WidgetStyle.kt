package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** The four selectable widget skins. */
enum class WidgetStyle(val label: String, val description: String) {
    FROSTED_GLASS("Frosted Glass", "Translucent surface with an emerald accent"),
    MONOCHROME("Bold Monochrome", "High-contrast matte black or white"),
    LIVING_SKY("Living Sky", "Atmospheric colour that follows the prayer window"),
    TRANSPARENT("Minimal Transparent", "Hairlines over your wallpaper")
}

/** Per-widget light/dark override, falling back to the device setting. */
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
    val divider: ColorProvider
)

/**
 * The palette rules for all four skins. `isDark` is already resolved from the widget's
 * theme mode, so a widget can stay dark while the phone is light and vice versa.
 */
fun resolveWidgetColors(
    style: WidgetStyle,
    isDark: Boolean,
    window: PrayerWindow
): WidgetColors = when (style) {
    WidgetStyle.FROSTED_GLASS -> if (isDark) {
        WidgetColors(
            background = 0xCC0F1715,
            primaryText = 0xFFF1F5F9,
            secondaryText = 0xFFA7B5B0,
            accent = 0xFF34D399,
            pill = 0x26FFFFFF,
            divider = 0x33FFFFFF
        )
    } else {
        WidgetColors(
            background = 0xD9F7F9F8,
            primaryText = 0xFF0B3B2C,
            secondaryText = 0xFF4B635B,
            accent = 0xFF10B981,
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
        accent = 0xFF10B981,
        pill = if (isDark) 0x22FFFFFF else 0x18000000,
        divider = if (isDark) 0x40FFFFFF else 0x30000000
    )
}

/** Wraps [resolveWidgetColors] in Glance colour providers. */
fun resolveWidgetPalette(
    style: WidgetStyle,
    isDark: Boolean,
    window: PrayerWindow
): WidgetPalette {
    val colors = resolveWidgetColors(style, isDark, window)
    return WidgetPalette(
        background = ColorProvider(Color(colors.background)),
        primaryText = ColorProvider(Color(colors.primaryText)),
        secondaryText = ColorProvider(Color(colors.secondaryText)),
        accent = ColorProvider(Color(colors.accent)),
        pillBackground = ColorProvider(Color(colors.pill)),
        divider = ColorProvider(Color(colors.divider))
    )
}

private val Context.widgetPrefs by preferencesDataStore(name = "imancore_widget_prefs")

/** Per-widget-instance style choice, with an app-wide default. */
object WidgetPrefsStore {
    private fun styleKey(appWidgetId: Int) = stringPreferencesKey("style_$appWidgetId")
    private fun themeKey(appWidgetId: Int) = stringPreferencesKey("theme_mode_$appWidgetId")
    private val defaultStyleKey = stringPreferencesKey("style_default")

    private val tasbihKey = intPreferencesKey("tasbih_count")
    private val jumuahWeekKey = stringPreferencesKey("jumuah_week")
    private val jumuahGhuslKey = booleanPreferencesKey("jumuah_ghusl")
    private val jumuahKahfKey = booleanPreferencesKey("jumuah_kahf")
    private val jumuahMasjidKey = booleanPreferencesKey("jumuah_masjid")
    private val jumuahDuroodKey = booleanPreferencesKey("jumuah_durood")
    private val adhkarDayKey = intPreferencesKey("adhkar_day")
    private val adhkarDoneKey = intPreferencesKey("adhkar_done")

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
        val raw = context.widgetPrefs.data.map { it[themeKey(appWidgetId)] }.first()
        return runCatching { WidgetThemeMode.valueOf(raw ?: "") }.getOrDefault(WidgetThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(context: Context, appWidgetId: Int, mode: WidgetThemeMode) {
        context.widgetPrefs.edit { prefs -> prefs[themeKey(appWidgetId)] = mode.name }
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

    /** Friday checklist state; everything resets automatically when a new week starts. */
    suspend fun jumuahChecklist(context: Context): List<Boolean> {
        val week = currentWeekKey()
        val prefs = context.widgetPrefs.data.first()
        if (prefs[jumuahWeekKey] != week) return List(4) { false }
        return listOf(
            prefs[jumuahGhuslKey] == true,
            prefs[jumuahKahfKey] == true,
            prefs[jumuahMasjidKey] == true,
            prefs[jumuahDuroodKey] == true
        )
    }

    suspend fun toggleJumuah(context: Context, index: Int): List<Boolean> {
        val week = currentWeekKey()
        var updated = List(4) { false }
        context.widgetPrefs.edit { prefs ->
            val sameWeek = prefs[jumuahWeekKey] == week
            val current = if (sameWeek) {
                listOf(
                    prefs[jumuahGhuslKey] == true,
                    prefs[jumuahKahfKey] == true,
                    prefs[jumuahMasjidKey] == true,
                    prefs[jumuahDuroodKey] == true
                )
            } else {
                List(4) { false }
            }
            updated = current.toMutableList().also { it[index] = !it[index] }
            prefs[jumuahWeekKey] = week
            prefs[jumuahGhuslKey] = updated[0]
            prefs[jumuahKahfKey] = updated[1]
            prefs[jumuahMasjidKey] = updated[2]
            prefs[jumuahDuroodKey] = updated[3]
        }
        return updated
    }

    /** Morning/evening adhkar progress for today, reset when the day rolls over. */
    suspend fun adhkarProgress(context: Context, epochDay: Long): Int {
        val prefs = context.widgetPrefs.data.first()
        return if (prefs[adhkarDayKey] == epochDay.toInt()) (prefs[adhkarDoneKey] ?: 0) else 0
    }

    suspend fun advanceAdhkar(context: Context, epochDay: Long, total: Int): Int {
        var done = 0
        context.widgetPrefs.edit { prefs ->
            val today = epochDay.toInt()
            val current = if (prefs[adhkarDayKey] == today) (prefs[adhkarDoneKey] ?: 0) else 0
            done = if (total <= 0 || current >= total) 0 else current + 1
            prefs[adhkarDayKey] = today
            prefs[adhkarDoneKey] = done
        }
        return done
    }

    private fun currentWeekKey(): String {
        val weekFields = WeekFields.ISO
        val today = LocalDate.now()
        return "${today.get(weekFields.weekBasedYear())}-${today.get(weekFields.weekOfWeekBasedYear())}"
    }

    const val TASBIH_MAX = 33
}

/** The calendar week key is locale-independent: ISO weeks everywhere. */
internal fun isoWeekKey(date: LocalDate): String {
    val weekFields = WeekFields.ISO
    return "${date.get(weekFields.weekBasedYear())}-${date.get(weekFields.weekOfWeekBasedYear())}"
}

/** Theme mode -> dark flag, honouring the widget's own override. */
internal fun resolveDarkMode(mode: WidgetThemeMode, systemDark: Boolean): Boolean = when (mode) {
    WidgetThemeMode.SYSTEM -> systemDark
    WidgetThemeMode.LIGHT -> false
    WidgetThemeMode.DARK -> true
}

internal fun weekdayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
