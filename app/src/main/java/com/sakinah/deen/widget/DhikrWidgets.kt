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
import androidx.glance.layout.Column

/**
 * The 33-count dhikr counter: a compact one-line counter at 2x1, and the large
 * count with both buttons at 2x2 and 4x2.
 */
class TasbihWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val count = WidgetPrefsStore.tasbihCount(context)
        val palette = widgetPaletteFor(context, appWidgetId, PrayerWindow.ISHA)
        val dhikr = "SubhanAllah"

        provideContent {
            val shape = shapeOf(LocalSize.current)
            when (shape) {
                WidgetShape.COMPACT, WidgetShape.WIDE -> WidgetShell(
                    palette,
                    openAppAction(LocalContext.current, "tools")
                ) {
                    WidgetRow {
                        Column {
                            WidgetCaption(text = "TASBIH", palette = palette, maxLines = 1)
                            WidgetBody(
                                text = "$dhikr · $count/${WidgetPrefsStore.TASBIH_MAX}",
                                palette = palette,
                                fontSize = 12,
                                weight = androidx.glance.text.FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        WidgetSpacer()
                        WidgetPill(
                            text = "+1",
                            palette = palette,
                            highlighted = true,
                            modifier = GlanceModifier.clickable(actionRunCallback<IncrementTasbihAction>())
                        )
                        if (shape == WidgetShape.WIDE) {
                            WidgetInlineGap()
                            WidgetPill(
                                text = "↺",
                                palette = palette,
                                modifier = GlanceModifier.clickable(actionRunCallback<ResetTasbihAction>())
                            )
                        }
                    }
                }

                WidgetShape.SQUARE, WidgetShape.EXPANDED -> WidgetShell(
                    palette,
                    openAppAction(LocalContext.current, "tools")
                ) {
                    WidgetCaption(text = "TASBIH", palette = palette, maxLines = 1)
                    WidgetAccentValue(
                        text = "$count / ${WidgetPrefsStore.TASBIH_MAX}",
                        palette = palette,
                        fontSize = if (shape == WidgetShape.EXPANDED) 34 else 30
                    )
                    WidgetGap(2)
                    WidgetTitle(text = dhikr, palette = palette, fontSize = 14)
                    WidgetVerticalSpacer()
                    WidgetRow {
                        WidgetPill(
                            text = "+1",
                            palette = palette,
                            highlighted = true,
                            modifier = GlanceModifier
                                .clickable(actionRunCallback<IncrementTasbihAction>())
                        )
                        WidgetInlineGap(8)
                        WidgetPill(
                            text = "↺ Reset",
                            palette = palette,
                            modifier = GlanceModifier
                                .clickable(actionRunCallback<ResetTasbihAction>())
                        )
                        WidgetSpacer()
                        WidgetCaption(
                            text = if (count >= WidgetPrefsStore.TASBIH_MAX) "Round complete" else "${WidgetPrefsStore.TASBIH_MAX - count} to go",
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
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetPrefsStore.incrementTasbih(context)
        TasbihWidget().update(context, glanceId)
    }
}

class ResetTasbihAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetPrefsStore.resetTasbih(context)
        TasbihWidget().update(context, glanceId)
    }
}

class TasbihWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasbihWidget()
}
