package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Thu, 19 Nov" for the date lines under the Hijri day. */
private val WEEKDAY_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

internal fun weekdayDate(date: LocalDate): String = date.format(WEEKDAY_DATE_FORMAT)

internal fun shortWeekday(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)

/** "Today", "Tomorrow" or "in N days" for a badge. */
internal fun relativeDays(days: Int): String = when (days) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> "in $days days"
}

// ---------------------------------------------------------------------------
// Hijri date
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
        val fastToday = fast.daysAway == 0
        val badge = if (fastToday) "Sunnah Fast" else "${event.name} ${relativeDays(event.daysAway)}"
        val dayLine = weekdayDate(today)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            val openApp = openAppAction(LocalContext.current)
            when (shape) {
                WidgetShape.COMPACT -> WidgetShell(palette, openApp) {
                    WidgetTitle(
                        text = hijri.compactLabel,
                        palette = palette,
                        fontSize = 12
                    )
                    WidgetGap(2)
                    WidgetCaption(
                        text = if (fastToday) "${shortWeekday(today)} · Sunnah Fast" else dayLine,
                        palette = palette,
                        maxLines = 1,
                        fontSize = 10
                    )
                }

                WidgetShape.SQUARE -> WidgetShell(palette, openApp) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WidgetAccentValue(
                            text = hijri.day.toString(),
                            palette = palette,
                            fontSize = 36
                        )
                        WidgetTitle(
                            text = hijri.monthYearLabel,
                            palette = palette,
                            fontSize = 11
                        )
                        WidgetCaption(text = dayLine, palette = palette, maxLines = 1, fontSize = 10)
                        WidgetVerticalSpacer()
                        WidgetPill(
                            text = badge,
                            palette = palette,
                            highlighted = fastToday,
                            solid = fastToday,
                            fontSize = 10
                        )
                    }
                }

                WidgetShape.WIDE, WidgetShape.EXPANDED -> WidgetShell(palette, openApp) {
                    WidgetRow {
                        WidgetAccentValue(
                            text = hijri.day.toString(),
                            palette = palette,
                            fontSize = 32
                        )
                        WidgetInlineGap(8)
                        Column {
                            WidgetTitle(
                                text = hijri.monthYearLabel,
                                palette = palette,
                                fontSize = 13
                            )
                            WidgetCaption(
                                text = "${hijri.year} AH",
                                palette = palette,
                                maxLines = 1,
                                fontSize = 9
                            )
                        }
                        WidgetSpacer()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WidgetTitle(text = dayLine, palette = palette, fontSize = 11)
                            WidgetGap(3)
                            WidgetPill(
                                text = badge,
                                palette = palette,
                                highlighted = fastToday,
                                solid = fastToday,
                                fontSize = 9
                            )
                        }
                    }
                    if (shape == WidgetShape.EXPANDED) {
                        WidgetVerticalSpacer()
                        WidgetRow {
                            WidgetCaption(
                                text = "Mon & Thu · white days 13–15",
                                palette = palette,
                                maxLines = 1,
                                fontSize = 9
                            )
                            WidgetSpacer()
                            WidgetCaption(
                                text = hijri.label,
                                palette = palette,
                                maxLines = 1,
                                fontSize = 9
                            )
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
// Qibla direction
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
        val bearing = summary?.let { "${it.bearing}° ${it.cardinal}" }
        val city = summary?.city ?: "Set your location"

        provideContent {
            val shape = shapeOf(LocalSize.current)
            val openQibla = openAppAction(LocalContext.current, "travel")
            when (shape) {
                WidgetShape.COMPACT -> WidgetShell(palette, openQibla) {
                    WidgetTitle(
                        text = bearing?.let { "QIBLA · $it" } ?: "QIBLA",
                        palette = palette,
                        fontSize = 12
                    )
                    WidgetGap(2)
                    WidgetCaption(text = city, palette = palette, maxLines = 1, fontSize = 10)
                }

                WidgetShape.SQUARE -> WidgetShell(palette, openQibla) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WidgetCaption(
                            text = "QIBLA DIRECTION",
                            palette = palette,
                            maxLines = 1,
                            fontSize = 9
                        )
                        WidgetGap(4)
                        WidgetAccentValue(
                            text = bearing ?: "--",
                            palette = palette,
                            fontSize = 26
                        )
                        WidgetCaption(text = city, palette = palette, maxLines = 1, fontSize = 10)
                        WidgetVerticalSpacer()
                        MapPill(palette, openQibla)
                    }
                }

                WidgetShape.WIDE, WidgetShape.EXPANDED -> WidgetShell(palette, openQibla) {
                    WidgetRow {
                        Column {
                            WidgetCaption(
                                text = "QIBLA DIRECTION",
                                palette = palette,
                                maxLines = 1,
                                fontSize = 9
                            )
                            WidgetGap(2)
                            WidgetAccentValue(
                                text = bearing ?: "--",
                                palette = palette,
                                fontSize = 26
                            )
                            WidgetCaption(text = city, palette = palette, maxLines = 1, fontSize = 10)
                        }
                        WidgetSpacer()
                        MapPill(palette, openQibla)
                    }
                    if (shape == WidgetShape.EXPANDED) {
                        WidgetVerticalSpacer()
                        WidgetCaption(
                            text = "Face the Kaaba — tap to open the compass",
                            palette = palette,
                            maxLines = 1,
                            fontSize = 9
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapPill(palette: WidgetPalette, openQibla: Action) {
    WidgetPill(
        text = "Open Map",
        palette = palette,
        solid = true,
        fontSize = 10,
        modifier = GlanceModifier.clickable(openQibla)
    )
}

class QiblaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QiblaWidget()
}
