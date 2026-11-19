package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The four selectable widget skins. */
enum class WidgetStyle(val label: String) {
    FROSTED_GLASS("Frosted Glass"),
    MONOCHROME("Bold Monochrome"),
    LIVING_SKY("Living Sky"),
    TRANSPARENT("Minimal Transparent")
}

/** Prayer whose window is currently active; drives the Living Sky gradient. */
enum class PrayerWindow { FAJR, DHUHR, ASR, MAGHRIB, ISHA }

data class WidgetPalette(
    val background: ColorProvider,
    val primaryText: ColorProvider,
    val secondaryText: ColorProvider,
    val accent: ColorProvider,
    val pillBackground: ColorProvider
)

fun resolveWidgetPalette(
    style: WidgetStyle,
    isDark: Boolean,
    window: PrayerWindow
): WidgetPalette = when (style) {
    WidgetStyle.FROSTED_GLASS -> if (isDark) {
        WidgetPalette(
            background = ColorProvider(Color(0xCC0F1715)),
            primaryText = ColorProvider(Color(0xFFF1F5F9)),
            secondaryText = ColorProvider(Color(0xFFA7B5B0)),
            accent = ColorProvider(Color(0xFF34D399)),
            pillBackground = ColorProvider(Color(0x26FFFFFF))
        )
    } else {
        WidgetPalette(
            background = ColorProvider(Color(0xD9F7F9F8)),
            primaryText = ColorProvider(Color(0xFF0B3B2C)),
            secondaryText = ColorProvider(Color(0xFF4B635B)),
            accent = ColorProvider(Color(0xFF10B981)),
            pillBackground = ColorProvider(Color(0x140B3B2C))
        )
    }

    WidgetStyle.MONOCHROME -> if (isDark) {
        WidgetPalette(
            background = ColorProvider(Color(0xFF0C0C0C)),
            primaryText = ColorProvider(Color(0xFFFFFFFF)),
            secondaryText = ColorProvider(Color(0xFF9CA3AF)),
            accent = ColorProvider(Color(0xFF10B981)),
            pillBackground = ColorProvider(Color(0xFF1A1A1A))
        )
    } else {
        WidgetPalette(
            background = ColorProvider(Color(0xFFFFFFFF)),
            primaryText = ColorProvider(Color(0xFF0A0A0A)),
            secondaryText = ColorProvider(Color(0xFF525252)),
            accent = ColorProvider(Color(0xFF10B981)),
            pillBackground = ColorProvider(Color(0xFFF3F4F6))
        )
    }

    WidgetStyle.LIVING_SKY -> when (window) {
        PrayerWindow.FAJR -> WidgetPalette(
            background = ColorProvider(Color(0xFF2B4C7E)),
            primaryText = ColorProvider(Color.White),
            secondaryText = ColorProvider(Color(0xFFD6E4F0)),
            accent = ColorProvider(Color(0xFFFDE68A)),
            pillBackground = ColorProvider(Color(0x29FFFFFF))
        )
        PrayerWindow.DHUHR -> WidgetPalette(
            background = ColorProvider(Color(0xFF1D6FA5)),
            primaryText = ColorProvider(Color.White),
            secondaryText = ColorProvider(Color(0xFFE0F2FE)),
            accent = ColorProvider(Color(0xFFFBBF24)),
            pillBackground = ColorProvider(Color(0x29FFFFFF))
        )
        PrayerWindow.ASR -> WidgetPalette(
            background = ColorProvider(Color(0xFF9A5B25)),
            primaryText = ColorProvider(Color.White),
            secondaryText = ColorProvider(Color(0xFFFEF3C7)),
            accent = ColorProvider(Color(0xFFFCD34D)),
            pillBackground = ColorProvider(Color(0x29FFFFFF))
        )
        PrayerWindow.MAGHRIB -> WidgetPalette(
            background = ColorProvider(Color(0xFF4C2A6A)),
            primaryText = ColorProvider(Color.White),
            secondaryText = ColorProvider(Color(0xFFE9D5FF)),
            accent = ColorProvider(Color(0xFFF9A8D4)),
            pillBackground = ColorProvider(Color(0x29FFFFFF))
        )
        PrayerWindow.ISHA -> WidgetPalette(
            background = ColorProvider(Color(0xFF131B3A)),
            primaryText = ColorProvider(Color.White),
            secondaryText = ColorProvider(Color(0xFFC7D2FE)),
            accent = ColorProvider(Color(0xFF34D399)),
            pillBackground = ColorProvider(Color(0x26FFFFFF))
        )
    }

    WidgetStyle.TRANSPARENT -> WidgetPalette(
        background = ColorProvider(Color.Transparent),
        primaryText = ColorProvider(if (isDark) Color.White else Color(0xFF0B3B2C)),
        secondaryText = ColorProvider(if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)),
        accent = ColorProvider(Color(0xFF10B981)),
        pillBackground = ColorProvider(if (isDark) Color(0x22FFFFFF) else Color(0x18000000))
    )
}

private val Context.widgetPrefs by preferencesDataStore(name = "imancore_widget_prefs")

/** Per-widget-instance style choice, with an app-wide default. */
object WidgetPrefsStore {
    private fun keyFor(appWidgetId: Int) = stringPreferencesKey("style_$appWidgetId")
    private val defaultKey = stringPreferencesKey("style_default")

    suspend fun getStyle(context: Context, appWidgetId: Int): WidgetStyle {
        val raw = context.widgetPrefs.data.map { prefs ->
            prefs[keyFor(appWidgetId)] ?: prefs[defaultKey]
        }.first()
        return runCatching { WidgetStyle.valueOf(raw ?: "") }.getOrDefault(WidgetStyle.FROSTED_GLASS)
    }

    suspend fun setStyle(context: Context, appWidgetId: Int, style: WidgetStyle) {
        context.widgetPrefs.edit { prefs -> prefs[keyFor(appWidgetId)] = style.name }
    }
}
