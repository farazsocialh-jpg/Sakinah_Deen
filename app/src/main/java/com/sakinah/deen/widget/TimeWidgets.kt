package com.sakinah.deen.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GREGORIAN_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun gregorianShort(date: LocalDate): String = date.format(GREGORIAN_FORMAT)

/** "Today", "Tomorrow" or "in N days" for a badge. */
internal fun relativeDays(days: Int): String = when (days) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> "in $days days"
}

// ---------------------------------------------------------------------------
// Hijri date (2x1 / 2x2 / 4x1 / 4x2)
// ---------------------------------------------------------------------------

class HijriDateWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val today = LocalDate.now(times.zone)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val hijri = hijriFor(today)
        val fast = nextSunnahFast(today)
        val event = nextIslamicEvent(today)
        val badge = if (fast.daysAway == 0) "Sunnah fast today" else "${event.name} ${relativeDays(event.daysAway)}"

        provideContent {
            val shape = shapeOf(LocalSize.current)
            val openApp = openAppAction(LocalContext.current)
            if (shape == WidgetShape.COMPACT) {
                WidgetShell(palette, openApp) {
                    WidgetRow {
                        Column {
                            WidgetCaption(text = "HIJRI DATE", palette = palette, maxLines = 1)
                            WidgetTitle(text = hijri.label, palette = palette)
                        }
                        WidgetSpacer()
                        WidgetCaption(text = weekdayName(today), palette = palette, maxLines = 1)
                    }
                }
            } else {
                WidgetShell(palette, openApp) {
                    WidgetRow {
                        Column {
                            WidgetCaption(text = "HIJRI DATE", palette = palette, maxLines = 1)
                            WidgetRow(verticalAlignment = androidx.glance.layout.Alignment.CenterVertically) {
                                WidgetAccentValue(
                                    text = hijri.day.toString(),
                                    palette = palette,
                                    fontSize = 34
                                )
                                WidgetInlineGap()
                                Column {
                                    WidgetTitle(
                                        text = hijri.monthName,
                                        palette = palette,
                                        fontSize = 14
                                    )
                                    WidgetCaption(
                                        text = "${hijri.year} AH",
                                        palette = palette,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        WidgetSpacer()
                        Column(horizontalAlignment = androidx.glance.layout.Alignment.CenterHorizontally) {
                            WidgetBody(
                                text = weekdayName(today),
                                palette = palette,
                                fontSize = 12,
                                weight = androidx.glance.text.FontWeight.Bold,
                                maxLines = 1
                            )
                            WidgetCaption(
                                text = gregorianShort(today),
                                palette = palette,
                                maxLines = 1
                            )
                        }
                    }
                    WidgetVerticalSpacer()
                    WidgetRow {
                        WidgetPill(
                            text = badge,
                            palette = palette,
                            highlighted = fast.daysAway == 0
                        )
                        if (shape == WidgetShape.EXPANDED) {
                            WidgetSpacer()
                            WidgetCaption(text = "Mon & Thu · white days 13–15", palette = palette, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class HijriDateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriDateWidget()
}

// ---------------------------------------------------------------------------
// Qibla direction (2x1 / 2x2 / 4x1 / 4x2)
// ---------------------------------------------------------------------------

class QiblaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val preferences = appPreferences(context)
        val coordinates = preferences?.locationCoordinates
        val summary = coordinates?.let {
            qiblaSummary(it.first, it.second, preferences.locationLabel.ifBlank { "Saved location" })
        }

        provideContent {
            val shape = shapeOf(LocalSize.current)
            val openQibla = openAppAction(LocalContext.current, "travel")
            if (shape == WidgetShape.COMPACT) {
                WidgetShell(palette, openQibla) {
                    WidgetRow {
                        WidgetTitle(text = "Qibla", palette = palette)
                        WidgetSpacer()
                        if (summary == null) {
                            WidgetCaption(text = "Set location", palette = palette, maxLines = 1)
                        } else {
                            WidgetAccentValue(
                                text = "${summary.bearing}° ${summary.cardinal}",
                                palette = palette
                            )
                        }
                    }
                }
            } else {
                WidgetShell(palette, openQibla) {
                    WidgetRow {
                        WidgetCaption(text = "QIBLA DIRECTION", palette = palette, maxLines = 1)
                        WidgetSpacer()
                        WidgetPill(text = "Compass", palette = palette)
                    }
                    WidgetGap(4)
                    if (summary == null) {
                        WidgetTitle(text = "Set your location", palette = palette)
                        WidgetCaption(text = "Tap to open the Qibla compass", palette = palette, maxLines = 1)
                    } else {
                        WidgetRow(
                            verticalAlignment = androidx.glance.layout.Alignment.CenterVertically
                        ) {
                            WidgetAccentValue(
                                text = "${summary.bearing}°",
                                palette = palette,
                                fontSize = if (shape == WidgetShape.SQUARE) 30 else 34
                            )
                            WidgetInlineGap(8)
                            Column {
                                WidgetTitle(text = summary.cardinal, palette = palette, fontSize = 14)
                                WidgetCaption(text = summary.city, palette = palette, maxLines = 1)
                            }
                        }
                        WidgetVerticalSpacer()
                        WidgetCaption(
                            text = "Tap to open the Qibla map",
                            palette = palette,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

class QiblaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QiblaWidget()
}
