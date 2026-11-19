package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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
                            fontSize = 11,
                            maxLines = 2,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        WidgetInlineGap()
                        WidgetPill(
                            text = ayah.reference,
                            palette = palette,
                            solid = true,
                            fontSize = 10
                        )
                    }
                }

                WidgetShape.WIDE -> WidgetShell(palette, openQuran) {
                    WidgetRow {
                        WidgetBody(
                            text = ayah.translation,
                            palette = palette,
                            fontSize = 11,
                            maxLines = 2,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        WidgetInlineGap()
                        WidgetPill(text = ayah.reference, palette = palette, fontSize = 10)
                        WidgetInlineGap()
                        NextAyahPill(palette)
                    }
                }

                WidgetShape.SQUARE -> WidgetShell(palette, openQuran) {
                    WidgetRow {
                        WidgetCaption(
                            text = "DAILY AYAH",
                            palette = palette,
                            maxLines = 1,
                            fontSize = 9
                        )
                        WidgetSpacer()
                        WidgetPill(text = ayah.reference, palette = palette, fontSize = 9)
                    }
                    WidgetGap(4)
                    WidgetBody(
                        text = ayah.translation,
                        palette = palette,
                        fontSize = 11,
                        maxLines = 5,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    WidgetRow {
                        WidgetCaption(
                            text = ayah.surahName,
                            palette = palette,
                            maxLines = 1,
                            fontSize = 9
                        )
                        WidgetSpacer()
                        NextAyahPill(palette)
                    }
                }

                WidgetShape.EXPANDED -> WidgetShell(palette, openQuran) {
                    WidgetRow {
                        WidgetCaption(
                            text = "DAILY AYAH · SURAH ${ayah.reference}",
                            palette = palette,
                            maxLines = 1,
                            fontSize = 10
                        )
                        WidgetSpacer()
                        NextAyahPill(palette)
                    }
                    WidgetGap(6)
                    WidgetBody(
                        text = ayah.translation,
                        palette = palette,
                        fontSize = 13,
                        maxLines = 4,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    WidgetGap(2)
                    WidgetRow {
                        WidgetPill(
                            text = "Next Ayah",
                            palette = palette,
                            solid = true,
                            modifier = GlanceModifier.clickable(actionRunCallback<NextAyahAction>())
                        )
                        WidgetInlineGap(8)
                        QuranPill(palette, openQuran)
                        WidgetSpacer()
                        WidgetCaption(
                            text = ayah.reference,
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
private fun NextAyahPill(palette: WidgetPalette) {
    WidgetPill(
        text = "↻",
        palette = palette,
        highlighted = true,
        modifier = GlanceModifier.clickable(actionRunCallback<NextAyahAction>())
    )
}

@Composable
private fun QuranPill(palette: WidgetPalette, openQuran: Action) {
    WidgetOutlinedPill(
        text = "Read Surah",
        palette = palette,
        modifier = GlanceModifier.clickable(openQuran)
    )
}

/** Cycles to the next curated verse, so the widget rotates without an unlock. */
class NextAyahAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        runCatching {
            WidgetPrefsStore.advanceAyahIndex(context)
            DailyAyahWidget().updateAll(context)
        }
    }
}

class DailyAyahWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyAyahWidget()
}

// ---------------------------------------------------------------------------
// Ask the Scholar
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
                    Column(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WidgetPill(text = "✦ Ask Scholar", palette = palette, solid = true)
                    }
                }

                WidgetShape.WIDE -> WidgetShell(palette, openScholar) {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        ScholarSearchBar(palette, fontSize = 11)
                    }
                }

                WidgetShape.SQUARE, WidgetShape.EXPANDED -> WidgetShell(palette, openScholar) {
                    WidgetCaption(
                        text = "IMANCORE SCHOLAR",
                        palette = palette,
                        maxLines = 1,
                        fontSize = 9
                    )
                    WidgetVerticalSpacer()
                    ScholarSearchBar(
                        palette,
                        fontSize = if (shape == WidgetShape.EXPANDED) 13 else 12
                    )
                    if (shape == WidgetShape.EXPANDED) {
                        WidgetGap(4)
                        WidgetCaption(
                            text = "Answers grounded in Qur'an, Hadith and fiqh",
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

/** The full-width search-bar pill the Scholar widget shows. */
@Composable
private fun ScholarSearchBar(palette: WidgetPalette, fontSize: Int) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(palette.pillBackground)
            .cornerRadius(999.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "✦",
            maxLines = 1,
            style = TextStyle(
                color = palette.accent,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold
            )
        )
        WidgetInlineGap()
        Text(
            text = "Ask ImanCore Scholar…",
            maxLines = 1,
            style = TextStyle(
                color = palette.primaryText,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

class AiScholarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AiScholarWidget()
}
