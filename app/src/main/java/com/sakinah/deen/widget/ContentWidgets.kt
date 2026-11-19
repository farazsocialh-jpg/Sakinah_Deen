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
import com.sakinah.deen.data.prefs.QuranReaderPreferences
import java.time.LocalTime

// ---------------------------------------------------------------------------
// Daily Ayah (4x1 / 4x2)
// ---------------------------------------------------------------------------

class DailyAyahWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val ayah = loadDailyAyah(context)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "quran")) {
                WidgetRow {
                    WidgetCaption(text = "AYAH OF THE DAY", palette = palette)
                    WidgetSpacer()
                    WidgetPill(text = ayah.reference, palette = palette, highlighted = true)
                }
                WidgetGap(4)
                WidgetBody(
                    text = ayah.translation,
                    palette = palette,
                    fontSize = 13,
                    maxLines = if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) 4 else 2
                )
                if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) {
                    WidgetGap(6)
                    WidgetCaption(text = ayah.arabic, palette = palette, maxLines = 2)
                }
            }
        }
    }
}

class DailyAyahWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyAyahWidget()
}

// ---------------------------------------------------------------------------
// Daily Hadith (4x1 / 4x2)
// ---------------------------------------------------------------------------

class DailyHadithWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val hadith = loadDailyHadith(context)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "hadith")) {
                WidgetRow {
                    WidgetCaption(text = "HADITH OF THE DAY", palette = palette)
                    WidgetSpacer()
                    WidgetPill(text = hadith.source, palette = palette)
                }
                WidgetGap(6)
                WidgetBody(
                    text = hadith.text,
                    palette = palette,
                    fontSize = 13,
                    maxLines = if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) 5 else 2
                )
            }
        }
    }
}

class DailyHadithWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyHadithWidget()
}

// ---------------------------------------------------------------------------
// AI Scholar (4x1)
// ---------------------------------------------------------------------------

class AiScholarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "ai_scholar")) {
                WidgetRow {
                    Column {
                        WidgetCaption(text = "IMANCORE SCHOLAR", palette = palette)
                        WidgetTitle(text = "Ask a question", palette = palette)
                    }
                    if (shape == WidgetShape.EXPANDED || shape == WidgetShape.SQUARE) {
                        WidgetSpacer()
                        WidgetPill(text = "Sources cited", palette = palette)
                    }
                }
                WidgetGap(8)
                WidgetPill(
                    text = "Ask ImanCore Scholar…",
                    palette = palette,
                    highlighted = true
                )
                if (shape == WidgetShape.EXPANDED) {
                    WidgetGap(6)
                    WidgetCaption(
                        text = "Answers grounded in Qur'an, Hadith and fiqh",
                        palette = palette,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class AiScholarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AiScholarWidget()
}

// ---------------------------------------------------------------------------
// Qur'an streak (4x1 / 4x2)
// ---------------------------------------------------------------------------

class QuranStreakWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)

        val readerPrefs = runCatching { QuranReaderPreferences(context) }.getOrNull()
        val streak = readerPrefs?.currentStreak() ?: 0
        val position = readerPrefs?.lastReadPosition
        val surahName = position?.let { pos ->
            runCatching { quranDao(context)?.getAyah(pos.surahNumber, 1)?.surahNameEnglish }.getOrNull()
        }
        val resumeLabel = when {
            position == null -> "Start reading"
            else -> "${surahName ?: "Surah ${position.surahNumber}"} ${position.surahNumber}:${position.ayahNumber}"
        }

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "quran")) {
                WidgetRow {
                    Column {
                        WidgetCaption(text = "QUR'AN READING", palette = palette)
                        WidgetAccentValue(
                            text = if (streak == 0) "No streak yet" else "${streak}d Streak",
                            palette = palette,
                            fontSize = if (shape == WidgetShape.COMPACT) 14 else 18
                        )
                    }
                    WidgetSpacer()
                    WidgetPill(
                        text = if (streak == 0) "Begin today" else "Keep it up",
                        palette = palette,
                        highlighted = streak > 0
                    )
                }
                WidgetGap(if (shape == WidgetShape.COMPACT) 4 else 8)
                WidgetRow {
                    WidgetCaption(text = "Continue", palette = palette, maxLines = 1)
                    WidgetInlineGap(6)
                    WidgetBody(
                        text = resumeLabel,
                        palette = palette,
                        fontSize = 12,
                        weight = androidx.glance.text.FontWeight.Bold,
                        maxLines = 1
                    )
                    WidgetSpacer()
                    WidgetPill(text = "Resume", palette = palette, highlighted = true)
                }
            }
        }
    }
}

class QuranStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuranStreakWidget()
}
