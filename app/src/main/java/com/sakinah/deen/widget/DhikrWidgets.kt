package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

private const val DHIKR_LABEL = "SUBHANALLAH"

/**
 * The 33-count dhikr counter: one line with the +1 pill at 2x1, the big centred count at
 * 2x2 and 4x2, and the wide bar with both controls at 4x1.
 */
class TasbihWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val count = WidgetPrefsStore.tasbihCount(context)
        val palette = widgetPaletteFor(context, appWidgetId, PrayerWindow.ISHA)
        val total = "$count / ${WidgetPrefsStore.TASBIH_MAX}"

        provideContent {
            val shape = shapeOf(LocalSize.current)
            val openTools = openAppAction(LocalContext.current, "tools")
            when (shape) {
                WidgetShape.COMPACT -> WidgetShell(palette, openTools) {
                    WidgetRow {
                        Column {
                            DhikrCaption(palette)
                            Text(
                                text = total,
                                maxLines = 1,
                                style = TextStyle(
                                    color = palette.primaryText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        WidgetSpacer()
                        IncrementPill(palette)
                    }
                }

                WidgetShape.WIDE -> WidgetShell(palette, openTools) {
                    WidgetRow {
                        Text(
                            text = "$DHIKR_LABEL · $total",
                            maxLines = 1,
                            style = TextStyle(
                                color = palette.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        WidgetSpacer()
                        ResetPill(palette)
                        WidgetInlineGap(6)
                        IncrementPill(palette)
                    }
                    WidgetGap(7)
                    WidgetProgress(
                        progress = count.toFloat() / WidgetPrefsStore.TASBIH_MAX,
                        palette = palette
                    )
                }

                WidgetShape.SQUARE, WidgetShape.EXPANDED -> WidgetShell(palette, openTools) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DhikrCaption(palette)
                        WidgetVerticalSpacer()
                        Text(
                            text = total,
                            maxLines = 1,
                            style = TextStyle(
                                color = palette.accent,
                                fontSize = (if (shape == WidgetShape.EXPANDED) 36 else 30).sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        WidgetVerticalSpacer()
                        WidgetRow {
                            IncrementPill(palette)
                            WidgetSpacer()
                            ResetPill(palette)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DhikrCaption(palette: WidgetPalette) {
    Text(
        text = DHIKR_LABEL,
        maxLines = 1,
        style = TextStyle(
            color = palette.secondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
private fun IncrementPill(palette: WidgetPalette) {
    WidgetPill(
        text = "+1",
        palette = palette,
        solid = true,
        modifier = GlanceModifier.clickable(actionRunCallback<IncrementTasbihAction>())
    )
}

@Composable
private fun ResetPill(palette: WidgetPalette) {
    WidgetPill(
        text = "↺",
        palette = palette,
        modifier = GlanceModifier.clickable(actionRunCallback<ResetTasbihAction>())
    )
}

class IncrementTasbihAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching {
            WidgetPrefsStore.incrementTasbih(context)
            TasbihWidget().update(context, glanceId)
        }
    }
}

class ResetTasbihAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching {
            WidgetPrefsStore.resetTasbih(context)
            TasbihWidget().update(context, glanceId)
        }
    }
}

class TasbihWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasbihWidget()
}
