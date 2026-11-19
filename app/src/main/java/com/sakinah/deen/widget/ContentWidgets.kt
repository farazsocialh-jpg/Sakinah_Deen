package com.sakinah.deen.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import java.time.LocalTime

// ---------------------------------------------------------------------------
// Ayah of the day (rotates on unlock and on the ↻ button)
// ---------------------------------------------------------------------------

class DailyAyahWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val index = WidgetPrefsStore.ayahIndex(context)
        val ayah = loadDailyAyah(context, index)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            val openQuran = openAppAction(LocalContext.current, "quran")
            when (shape) {
                WidgetShape.COMPACT -> WidgetShell(palette, openQuran) {
                    WidgetRow {
                        WidgetBody(
                            text = ayah.snippet(),
                            palette = palette,
                            fontSize = 12,
                            maxLines = 2,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        WidgetInlineGap()
                        WidgetPill(text = ayah.reference, palette = palette, highlighted = true)
                    }
                }

                WidgetShape.WIDE -> WidgetShell(palette, openQuran) {
                    WidgetRow {
                        WidgetBody(
                            text = ayah.translation,
                            palette = palette,
                            fontSize = 12,
                            maxLines = 2,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        WidgetInlineGap()
                        WidgetPill(text = ayah.reference, palette = palette)
                        WidgetInlineGap()
                        NextAyahPill(palette)
                    }
                }

                WidgetShape.SQUARE, WidgetShape.EXPANDED -> WidgetShell(palette, openQuran) {
                    WidgetRow {
                        WidgetCaption(text = "AYAH OF THE DAY", palette = palette, maxLines = 1)
                        WidgetSpacer()
                        NextAyahPill(palette)
                    }
                    WidgetGap(6)
                    WidgetBody(
                        text = ayah.translation,
                        palette = palette,
                        fontSize = 13,
                        maxLines = 5,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    WidgetGap(4)
                    WidgetRow {
                        WidgetCaption(text = ayah.surahLabel(), palette = palette, maxLines = 1)
                        WidgetSpacer()
                        WidgetCaption(text = "Tap to read", palette = palette, maxLines = 1)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NextAyahPill(palette: WidgetPalette) {
    WidgetPill(
        text = "↻ Next",
        palette = palette,
        highlighted = true,
        modifier = GlanceModifier.clickable(actionRunCallback<NextAyahAction>())
    )
}

/** Cycles to the next curated verse, so the widget rotates without an unlock. */
class NextAyahAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetPrefsStore.advanceAyahIndex(context)
        DailyAyahWidget().updateAll(context)
    }
}

class DailyAyahWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyAyahWidget()
}

// ---------------------------------------------------------------------------
// Ask the Scholar (2x1 / 4x1)
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
            val openScholar = openAppAction(LocalContext.current, "ai_scholar")
            when (shape) {
                WidgetShape.COMPACT -> WidgetShell(palette, openScholar) {
                    WidgetRow {
                        WidgetPill(text = "Ask Scholar", palette = palette, highlighted = true)
                        WidgetSpacer()
                        WidgetAccentValue(text = "→", palette = palette, fontSize = 14)
                    }
                }

                WidgetShape.WIDE -> WidgetShell(palette, openScholar) {
                    WidgetRow {
                        WidgetCaption(text = "IMANCORE SCHOLAR", palette = palette, maxLines = 1)
                        WidgetSpacer()
                        WidgetPill(
                            text = "Ask ImanCore Scholar…",
                            palette = palette,
                            highlighted = true
                        )
                    }
                }

                WidgetShape.SQUARE, WidgetShape.EXPANDED -> WidgetShell(palette, openScholar) {
                    WidgetCaption(text = "IMANCORE SCHOLAR", palette = palette, maxLines = 1)
                    WidgetGap(2)
                    WidgetTitle(text = "Ask a question", palette = palette)
                    WidgetVerticalSpacer()
                    WidgetPill(
                        text = "Ask ImanCore Scholar…",
                        palette = palette,
                        highlighted = true
                    )
                    if (shape == WidgetShape.EXPANDED) {
                        WidgetGap(4)
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
}

class AiScholarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AiScholarWidget()
}
