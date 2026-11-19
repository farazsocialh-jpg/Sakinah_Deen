package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import java.time.LocalTime

// ---------------------------------------------------------------------------
// Tasbih (2x1 / 2x2)
// ---------------------------------------------------------------------------

class TasbihWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val count = WidgetPrefsStore.tasbihCount(context)
        val palette = widgetPaletteFor(context, appWidgetId, PrayerWindow.ISHA)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "tools")) {
                WidgetRow {
                    Column {
                        WidgetCaption(text = "TASBIH", palette = palette)
                        WidgetBody(
                            text = "SubhanAllah",
                            palette = palette,
                            fontSize = if (shape == WidgetShape.COMPACT) 12 else 14,
                            weight = androidx.glance.text.FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    WidgetSpacer()
                    WidgetAccentValue(
                        text = "$count / ${WidgetPrefsStore.TASBIH_MAX}",
                        palette = palette,
                        fontSize = if (shape == WidgetShape.COMPACT) 15 else 20
                    )
                }

                if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) {
                    WidgetGap(10)
                    WidgetDivider(palette)
                    WidgetGap(10)
                } else {
                    WidgetGap(6)
                }

                WidgetRow {
                    WidgetPill(
                        text = "+1",
                        palette = palette,
                        highlighted = true,
                        modifier = GlanceModifier.clickable(actionRunCallback<IncrementTasbihAction>())
                    )
                    WidgetInlineGap(8)
                    WidgetPill(
                        text = "↺ Reset",
                        palette = palette,
                        modifier = GlanceModifier.clickable(actionRunCallback<ResetTasbihAction>())
                    )
                    WidgetSpacer()
                    if (shape == WidgetShape.EXPANDED) {
                        WidgetCaption(
                            text = if (count >= WidgetPrefsStore.TASBIH_MAX) {
                                "Complete — start a new round"
                            } else {
                                "${WidgetPrefsStore.TASBIH_MAX - count} to finish"
                            },
                            palette = palette,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

class IncrementTasbihAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        WidgetPrefsStore.incrementTasbih(context)
        TasbihWidget().update(context, glanceId)
    }
}

class ResetTasbihAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        WidgetPrefsStore.resetTasbih(context)
        TasbihWidget().update(context, glanceId)
    }
}

class TasbihWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasbihWidget()
}

// ---------------------------------------------------------------------------
// Adhkar (4x1 / 4x2)
// ---------------------------------------------------------------------------

class AdhkarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val now = LocalTime.now(times.zone)
        val window = currentPrayerWindow(times.times, now)
        val palette = widgetPaletteFor(context, appWidgetId, window)

        val asr = times.times[com.sakinah.deen.data.model.PrayerType.ASR]
        val isEvening = asr != null && !now.isBefore(asr)
        val title = if (isEvening) "Evening Adhkar" else "Morning Adhkar"
        val subtitle = if (isEvening) "After Asr until midnight" else "After Fajr until Asr"
        val total = adhkarItemCount(context)
        val done = WidgetPrefsStore.adhkarProgress(context, times.date.toEpochDay())

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "tools")) {
                WidgetRow {
                    Column {
                        WidgetCaption(text = if (isEvening) "EVENING" else "MORNING", palette = palette)
                        WidgetTitle(text = title, palette = palette)
                    }
                    WidgetSpacer()
                    WidgetPill(text = "$done / $total", palette = palette, highlighted = done >= total)
                }
                WidgetGap(6)
                WidgetCaption(text = subtitle, palette = palette, maxLines = 1)
                if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) {
                    WidgetGap(8)
                    WidgetDivider(palette)
                    WidgetGap(8)
                    WidgetRow {
                        WidgetPill(
                            text = "Mark one recited",
                            palette = palette,
                            highlighted = true,
                            modifier = GlanceModifier.clickable(actionRunCallback<AdvanceAdhkarAction>())
                        )
                        WidgetInlineGap(8)
                        WidgetCaption(text = "Tap the card to open", palette = palette, maxLines = 1)
                    }
                }
            }
        }
    }
}

class AdvanceAdhkarAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val epochDay = java.time.LocalDate.now(activeZone(context)).toEpochDay()
        val total = adhkarItemCount(context)
        WidgetPrefsStore.advanceAdhkar(context, epochDay, total)
        AdhkarWidget().update(context, glanceId)
    }
}

class AdhkarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AdhkarWidget()
}

// ---------------------------------------------------------------------------
// Daily Du'a (4x1 / 4x2)
// ---------------------------------------------------------------------------

class DailyDuaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val dua = loadDailyDua(context)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "tools")) {
                WidgetRow {
                    WidgetCaption(text = "DU'A OF THE DAY", palette = palette)
                    WidgetSpacer()
                    WidgetPill(text = dua.reference, palette = palette)
                }
                WidgetGap(4)
                WidgetTitle(text = dua.title, palette = palette)
                WidgetGap(4)
                WidgetBody(
                    text = dua.translation,
                    palette = palette,
                    fontSize = 12,
                    maxLines = if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) 4 else 2
                )
            }
        }
    }
}

class DailyDuaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyDuaWidget()
}
