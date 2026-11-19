package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.util.Locale

// ---------------------------------------------------------------------------
// Jumuah checklist (4x2 / 4x1)
// ---------------------------------------------------------------------------

object JumuahActionKeys {
    val INDEX = ActionParameters.Key<Int>("jumuah_index")
}

private val JUMUAH_ITEMS = listOf("Ghusl", "Al-Kahf", "Early masjid", "Durood")

class JumuahWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val checklist = WidgetPrefsStore.jumuahChecklist(context)
        val done = checklist.count { it }

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "salah")) {
                WidgetRow {
                    Column {
                        WidgetCaption(text = "JUMUAH", palette = palette)
                        WidgetTitle(text = "Friday checklist", palette = palette, maxLines = 1)
                    }
                    WidgetSpacer()
                    WidgetAccentValue(text = "$done / 4", palette = palette, fontSize = 14)
                }
                WidgetGap(if (shape == WidgetShape.COMPACT) 4 else 8)

                if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) {
                    // 2x2 grid keeps every label readable at 4x2.
                    JumuahRow(checklist, listOf(0, 1), palette)
                    WidgetGap(6)
                    JumuahRow(checklist, listOf(2, 3), palette)
                } else {
                    JumuahRow(checklist, listOf(0, 1, 2, 3), palette)
                }
            }
        }
    }
}

@Composable
private fun JumuahRow(checklist: List<Boolean>, indices: List<Int>, palette: WidgetPalette) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        indices.forEach { index ->
            val done = checklist.getOrElse(index) { false }
            Text(
                text = (if (done) "✓ " else "○ ") + JUMUAH_ITEMS[index],
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(horizontal = 3.dp, vertical = 6.dp)
                    .background(palette.pillBackground)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .clickable(
                        actionRunCallback<ToggleJumuahAction>(
                            actionParametersOf(JumuahActionKeys.INDEX to index)
                        )
                    ),
                maxLines = 1,
                style = TextStyle(
                    color = if (done) palette.accent else palette.primaryText,
                    fontSize = 11.sp,
                    fontWeight = if (done) FontWeight.Bold else FontWeight.Medium
                )
            )
        }
    }
}

class ToggleJumuahAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val index = parameters[JumuahActionKeys.INDEX] ?: return
        if (index !in JUMUAH_ITEMS.indices) return
        WidgetPrefsStore.toggleJumuah(context, index)
        JumuahWidget().update(context, glanceId)
    }
}

class JumuahWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JumuahWidget()
}

// ---------------------------------------------------------------------------
// Nisab / zakat reminder (2x1 / 4x1)
// ---------------------------------------------------------------------------

class NisabWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val times = loadWidgetTimes(context)
        val window = currentPrayerWindow(times.times, LocalTime.now(times.zone))
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val preferences = appPreferences(context)
        val rates = nisabRepository()?.rates?.value
        val status = if (preferences != null && rates != null) nisabStatus(preferences, rates) else null

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "wealth")) {
                WidgetRow {
                    WidgetCaption(text = "ZAKAT", palette = palette)
                    WidgetSpacer()
                    WidgetPill(
                        text = when {
                            status == null -> "Set up"
                            status.aboveNisab -> "Nisab met"
                            else -> "Below nisab"
                        },
                        palette = palette,
                        highlighted = status?.aboveNisab == true
                    )
                }
                WidgetGap(4)
                if (status == null) {
                    WidgetTitle(text = "Wealth reminder", palette = palette)
                    WidgetCaption(text = "Add your assets in Zakat", palette = palette, maxLines = 1)
                } else {
                    WidgetTitle(
                        text = "Nisab " + money(status.threshold, status.currency),
                        palette = palette,
                        maxLines = 1
                    )
                    WidgetCaption(
                        text = if (status.aboveNisab) {
                            "Zakat due " + money(status.zakatDue, status.currency)
                        } else {
                            money(status.shortfall, status.currency) + " below threshold"
                        },
                        palette = palette,
                        maxLines = 1
                    )
                    if (shape == WidgetShape.WIDE || shape == WidgetShape.EXPANDED) {
                        WidgetGap(4)
                        WidgetCaption(
                            text = "Assets " + money(status.assets, status.currency) + " · 2.5%",
                            palette = palette,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun money(value: Double, currency: String): String =
    String.format(Locale.US, "%,.0f %s", value, currency)

class NisabWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NisabWidget()
}
