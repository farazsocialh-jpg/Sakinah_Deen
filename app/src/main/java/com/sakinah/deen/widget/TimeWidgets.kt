package com.sakinah.deen.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import com.sakinah.deen.data.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GREGORIAN_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun relativeDays(days: Int): String = when (days) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> "in $days days"
}

internal fun gregorianShort(date: LocalDate): String = date.format(GREGORIAN_FORMAT)

// ---------------------------------------------------------------------------
// Hijri date (2x1 / 2x2)
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
        val fastingToday = nextSunnahFast(today).daysAway == 0

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current)) {
                WidgetRow {
                    WidgetCaption(text = "HIJRI DATE", palette = palette)
                    WidgetSpacer()
                    if (fastingToday) {
                        WidgetPill(text = "Sunnah fast", palette = palette, highlighted = true)
                    }
                }
                WidgetGap(4)
                WidgetTitle(text = hijri.label, palette = palette, maxLines = 1)
                WidgetGap(2)
                WidgetCaption(
                    text = weekdayName(today) + " · " + gregorianShort(today),
                    palette = palette,
                    maxLines = 1
                )
                if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) {
                    WidgetGap(8)
                    WidgetDivider(palette)
                    WidgetGap(8)
                    WidgetRow {
                        WidgetPill(
                            text = "White days 13–15",
                            palette = palette
                        )
                        WidgetInlineGap(6)
                        WidgetCaption(text = "Mon & Thu fasts", palette = palette, maxLines = 1)
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
// Qibla (2x1 / 2x2)
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
            WidgetShell(palette, openAppAction(LocalContext.current, "travel")) {
                WidgetRow {
                    WidgetCaption(text = "QIBLA", palette = palette)
                    WidgetSpacer()
                    WidgetPill(text = "Compass", palette = palette)
                }
                WidgetGap(4)
                if (summary == null) {
                    WidgetTitle(text = "Set your location", palette = palette)
                    WidgetCaption(text = "Opens Safar & Qibla", palette = palette, maxLines = 1)
                } else {
                    WidgetAccentValue(
                        text = "${summary.bearing}° ${summary.cardinal}",
                        palette = palette,
                        fontSize = if (shape == WidgetShape.COMPACT) 16 else 22
                    )
                    WidgetCaption(text = summary.city, palette = palette, maxLines = 1)
                }
            }
        }
    }
}

class QiblaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QiblaWidget()
}

// ---------------------------------------------------------------------------
// Ramadan (4x1 / 4x2)
// ---------------------------------------------------------------------------

class RamadanWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val now = LocalTime.now(times.zone)
        val window = currentPrayerWindow(times.times, now)
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val hijri = hijriFor(times.date)
        val fajr = times.times[PrayerType.FAJR]
        val maghrib = times.times[PrayerType.MAGHRIB]

        val suhoorLabel = when {
            fajr == null -> "--:--"
            now.isBefore(fajr) -> "${countdownLabel(minutesUntil(now, fajr))}"
            else -> "Ended"
        }
        val iftarLabel = when {
            maghrib == null -> "--:--"
            now.isBefore(maghrib) -> countdownLabel(minutesUntil(now, maghrib))
            else -> "Iftar served"
        }

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "salah")) {
                WidgetRow {
                    WidgetCaption(text = "RAMADAN", palette = palette)
                    WidgetSpacer()
                    WidgetPill(text = hijri.label, palette = palette)
                }
                WidgetGap(6)
                WidgetRow {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetCaption(text = "Suhoor ends", palette = palette)
                        WidgetAccentValue(
                            text = times.label(PrayerType.FAJR),
                            palette = palette,
                            fontSize = 14
                        )
                        WidgetCaption(text = suhoorLabel, palette = palette, maxLines = 1)
                    }
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetCaption(text = "Iftar", palette = palette)
                        WidgetAccentValue(
                            text = times.label(PrayerType.MAGHRIB),
                            palette = palette,
                            fontSize = 14
                        )
                        WidgetCaption(text = iftarLabel, palette = palette, maxLines = 1)
                    }
                }
                if (shape == WidgetShape.EXPANDED) {
                    WidgetGap(6)
                    WidgetDivider(palette)
                    WidgetGap(6)
                    WidgetCaption(
                        text = "Suhoor is Fajr, iftar is Maghrib — times for " + cityLabel(context),
                        palette = palette,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class RamadanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RamadanWidget()
}

// ---------------------------------------------------------------------------
// Duha window (2x1 / 4x1)
// ---------------------------------------------------------------------------

class DuhaWindowWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val now = LocalTime.now(times.zone)
        val window = currentPrayerWindow(times.times, now)
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val sunrise = times.times[PrayerType.SUNRISE]
        val dhuhr = times.times[PrayerType.DHUHR]
        val status = if (sunrise != null && dhuhr != null) duhaStatus(now, sunrise, dhuhr) else null

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "salah")) {
                WidgetRow {
                    Column {
                        WidgetCaption(text = "DUHA", palette = palette)
                        WidgetTitle(
                            text = when {
                                status == null -> "No timetable"
                                status.isOpen -> "Window open"
                                else -> "Window closed"
                            },
                            palette = palette
                        )
                    }
                    WidgetSpacer()
                    WidgetPill(
                        text = if (status?.isOpen == true) "Open" else "Closed",
                        palette = palette,
                        highlighted = status?.isOpen == true
                    )
                }
                WidgetGap(4)
                WidgetCaption(text = status?.label ?: "Add your location", palette = palette, maxLines = 1)
                if (shape == WidgetShape.WIDE || shape == WidgetShape.EXPANDED) {
                    WidgetGap(4)
                    WidgetRow {
                        WidgetCaption(text = "Sunrise " + times.label(PrayerType.SUNRISE), palette = palette)
                        WidgetSpacer()
                        WidgetCaption(text = "Ishraq from sunrise", palette = palette, maxLines = 1)
                    }
                }
            }
        }
    }
}

class DuhaWindowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuhaWindowWidget()
}

// ---------------------------------------------------------------------------
// Qiyam / last third (2x1 / 4x1)
// ---------------------------------------------------------------------------

class QiyamWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimesWithTomorrowFajr(context)
        val now = LocalTime.now(times.zone)
        val window = currentPrayerWindow(times.times, now)
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val maghrib = times.times[PrayerType.MAGHRIB]
        val fajr = times.times[PrayerType.FAJR]
        val lastThird = if (maghrib != null && fajr != null) lastThirdStart(maghrib, fajr) else null
        val formatted = lastThird?.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "salah")) {
                WidgetRow {
                    WidgetCaption(text = "QIYAM", palette = palette)
                    WidgetSpacer()
                    WidgetPill(
                        text = if (lastThird != null) "Last third" else "No timetable",
                        palette = palette,
                        highlighted = lastThird != null
                    )
                }
                WidgetGap(4)
                WidgetTitle(text = formatted ?: "--:--", palette = palette)
                WidgetCaption(
                    text = if (lastThird == null) {
                        "Add your location to see Tahajjud"
                    } else if (shape == WidgetShape.COMPACT) {
                        "Fajr cutoff " + times.label(PrayerType.FAJR)
                    } else {
                        "Tahajjud begins · Fajr at " + times.label(PrayerType.FAJR)
                    },
                    palette = palette,
                    maxLines = 1
                )
            }
        }
    }
}

class QiyamWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QiyamWidget()
}

// ---------------------------------------------------------------------------
// Sunnah fast (2x1 / 2x2)
// ---------------------------------------------------------------------------

class SunnahFastWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val today = LocalDate.now(times.zone)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val fast = nextSunnahFast(today)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current)) {
                WidgetRow {
                    WidgetCaption(text = "SUNNAH FAST", palette = palette)
                    WidgetSpacer()
                    WidgetPill(text = relativeDays(fast.daysAway), palette = palette, highlighted = fast.daysAway == 0)
                }
                WidgetGap(4)
                WidgetTitle(text = fast.title, palette = palette)
                WidgetCaption(text = fast.hijriLabel, palette = palette, maxLines = 1)
                if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) {
                    WidgetGap(6)
                    WidgetCaption(text = "Monday, Thursday & white days 13–15", palette = palette, maxLines = 1)
                }
            }
        }
    }
}

class SunnahFastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SunnahFastWidget()
}

// ---------------------------------------------------------------------------
// Islamic event countdown (2x1 / 4x1)
// ---------------------------------------------------------------------------

class IslamicEventWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val today = LocalDate.now(times.zone)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val event = nextIslamicEvent(today)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current)) {
                WidgetRow {
                    WidgetCaption(text = "NEXT ISLAMIC DATE", palette = palette)
                    WidgetSpacer()
                    WidgetPill(text = relativeDays(event.daysAway), palette = palette, highlighted = true)
                }
                WidgetGap(4)
                WidgetTitle(text = event.name, palette = palette)
                WidgetCaption(
                    text = if (shape == WidgetShape.COMPACT) event.hijriLabel else "${event.hijriLabel} · ${gregorianShort(event.date)}",
                    palette = palette,
                    maxLines = 1
                )
            }
        }
    }
}

class IslamicEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IslamicEventWidget()
}
