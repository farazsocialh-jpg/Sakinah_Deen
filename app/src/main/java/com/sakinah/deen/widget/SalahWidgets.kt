package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.sakinah.deen.data.model.PrayerType
import java.time.LocalTime

/** One prayer cell on the tracker. */
data class PrayerChipUi(
    val key: String,
    val label: String,
    val time: String,
    val isCompleted: Boolean,
    val isNext: Boolean
)

/** Everything the Salah widget paints, resolved once per update. */
data class SalahBoardState(
    val times: WidgetTimes,
    val now: LocalTime,
    val next: PrayerType?,
    val completed: Set<String>
) {
    val chips: List<PrayerChipUi> = OBLIGATORY_PRAYERS.map { (type, label) ->
        PrayerChipUi(
            key = type.name,
            label = label,
            time = times.label(type),
            isCompleted = completed.contains(type.name),
            isNext = type == next
        )
    }
    val completedCount: Int = chips.count { it.isCompleted }
    val hasTimes: Boolean = times.hasTimes
}

suspend fun loadSalahBoard(context: Context): SalahBoardState {
    val times = loadWidgetTimes(context)
    val now = LocalTime.now(times.zone)
    val completed = loadCompletedPrayers(context, times.date.toEpochDay())
    return SalahBoardState(
        times = times,
        now = now,
        next = times.nextToday(now),
        completed = completed
    )
}

private fun countdownFor(state: SalahBoardState): String {
    val target = state.next ?: PrayerType.FAJR
    val time = state.times.times[target] ?: return "No timetable"
    return countdownLabel(minutesUntil(state.now, time))
}

/**
 * Today's Salah in one widget: the next prayer at 2x1, the full strip at 4x1, and
 * one-tap checkboxes at 4x2 (2x2 gets the same tappable board).
 */
class SalahTrackerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadSalahBoard(context)
        val window = currentPrayerWindow(state.times.times, state.now)
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val nextType = state.next ?: PrayerType.FAJR
        val nextName = prayerLabel(nextType)
        val nextTime = state.times.label(nextType)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            val openSalah = openAppAction(LocalContext.current, "salah")
            when (shape) {
                WidgetShape.COMPACT -> WidgetShell(palette, openSalah) {
                    WidgetRow {
                        Column {
                            WidgetCaption(text = "NEXT PRAYER", palette = palette, maxLines = 1)
                            WidgetRow {
                                WidgetTitle(text = nextName, palette = palette)
                                WidgetInlineGap()
                                WidgetAccentValue(text = nextTime, palette = palette)
                            }
                        }
                        WidgetSpacer()
                        if (state.hasTimes) {
                            WidgetPill(
                                text = countdownFor(state),
                                palette = palette,
                                highlighted = true
                            )
                        }
                    }
                }

                WidgetShape.WIDE -> WidgetShell(palette, openSalah) {
                    if (!state.hasTimes) {
                        WidgetTitle(text = "Today's Salah", palette = palette)
                        WidgetGap(2)
                        WidgetEmptyState("Add your location to see prayer times", palette)
                    } else {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            state.chips.forEach { chip ->
                                PrayerColumn(
                                    chip = chip,
                                    palette = palette,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                            }
                        }
                    }
                }

                WidgetShape.SQUARE, WidgetShape.EXPANDED -> WidgetShell(palette, openSalah) {
                    WidgetRow {
                        WidgetTitle(text = "Today's Salah", palette = palette)
                        WidgetSpacer()
                        WidgetAccentValue(
                            text = "${state.completedCount} / 5",
                            palette = palette,
                            fontSize = 15
                        )
                    }
                    WidgetGap(if (shape == WidgetShape.SQUARE) 6 else 10)
                    if (!state.hasTimes) {
                        WidgetEmptyState("Add your location to see prayer times", palette)
                    } else {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            state.chips.forEach { chip ->
                                TrackerChip(
                                    chip = chip,
                                    palette = palette,
                                    compact = shape == WidgetShape.SQUARE,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                            }
                        }
                    }
                    if (shape == WidgetShape.EXPANDED) {
                        WidgetGap(6)
                        WidgetRow {
                            WidgetCaption(
                                text = if (state.completedCount == 5) {
                                    "All five complete — alhamdulillah"
                                } else {
                                    "Tap a prayer to mark it prayed"
                                },
                                palette = palette,
                                maxLines = 1
                            )
                            WidgetSpacer()
                            if (state.hasTimes && state.next != null) {
                                WidgetPill(
                                    text = countdownFor(state),
                                    palette = palette,
                                    highlighted = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Non-interactive prayer column used by the 4x1 strip. */
@Composable
private fun PrayerColumn(
    chip: PrayerChipUi,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        WidgetBody(
            text = if (chip.isCompleted) "✓ ${chip.label}" else chip.label,
            palette = palette,
            fontSize = 11,
            weight = if (chip.isNext) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        WidgetCaption(
            text = chip.time,
            palette = palette,
            maxLines = 1
        )
    }
}

/** Tappable prayer cell; the accent check mark is the whole affordance. */
@Composable
private fun TrackerChip(
    chip: PrayerChipUi,
    palette: WidgetPalette,
    compact: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 3.dp, vertical = 6.dp)
            .background(palette.pillBackground)
            .cornerRadius(12.dp)
            .clickable(
                actionRunCallback<ToggleSalahAction>(
                    actionParametersOf(SalahActionKeys.PRAYER_KEY to chip.key)
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (chip.isCompleted) "✓" else "○",
            style = TextStyle(
                color = if (chip.isCompleted) palette.accent else palette.secondaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        WidgetBody(
            text = chip.label,
            palette = palette,
            fontSize = if (compact) 10 else 11,
            weight = if (chip.isNext) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        if (!compact) {
            WidgetCaption(text = chip.time, palette = palette, maxLines = 1)
        }
    }
}

object SalahActionKeys {
    val PRAYER_KEY = ActionParameters.Key<String>("salah_prayer_key")
}

/** Toggles one prayer for today in the app's Salah table, then repaints the widget. */
class ToggleSalahAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prayerKey = parameters[SalahActionKeys.PRAYER_KEY] ?: return
        togglePrayerForWidget(context, prayerKey)
        SalahTrackerWidget().updateAll(context)
    }
}

class SalahTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalahTrackerWidget()
}
