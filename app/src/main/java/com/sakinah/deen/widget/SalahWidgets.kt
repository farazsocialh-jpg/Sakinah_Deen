package com.sakinah.deen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.unit.ColorProvider
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

    /** How far the current prayer window has run; feeds the compact progress bar. */
    val windowProgress: Float = prayerProgress(times.times, now)
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

/** The prayer the compact widget should name: the next one, or Fajr after Isha. */
private fun nextPrayerName(state: SalahBoardState): String =
    prayerLabel(state.next ?: PrayerType.FAJR)

private fun nextPrayerTime(state: SalahBoardState): String =
    state.times.label(state.next ?: PrayerType.FAJR)

private val TRANSPARENT_FILL = ColorProvider(Color(0x00000000))

/**
 * Today's Salah in one widget: the next prayer plus window progress at 2x1, the full
 * five-prayer strip at 4x1, and a one-tap checklist at 2x2 and 4x2.
 */
class SalahTrackerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadSalahBoard(context)
        val window = currentPrayerWindow(state.times.times, state.now)
        val palette = widgetPaletteFor(context, appWidgetId, window)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            val openSalah = openAppAction(LocalContext.current, "salah")
            when (shape) {
                WidgetShape.COMPACT -> WidgetShell(palette, openSalah) {
                    WidgetRow {
                        WidgetTitle(text = nextPrayerName(state), palette = palette, fontSize = 15)
                        WidgetInlineGap(5)
                        WidgetAccentValue(
                            text = nextPrayerTime(state),
                            palette = palette,
                            fontSize = 13
                        )
                        WidgetSpacer()
                        if (state.hasTimes) {
                            WidgetCaption(
                                text = countdownFor(state),
                                palette = palette,
                                maxLines = 1,
                                fontSize = 10
                            )
                        }
                    }
                    WidgetGap(7)
                    WidgetProgress(progress = state.windowProgress, palette = palette)
                }

                WidgetShape.WIDE -> WidgetShell(palette, openSalah) {
                    if (!state.hasTimes) {
                        WidgetTitle(text = "Today's Salah", palette = palette)
                        WidgetGap(2)
                        WidgetEmptyState("Add your location to see prayer times", palette)
                    } else {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            state.chips.forEach { chip ->
                                PrayerStripCell(
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
                        WidgetTitle(
                            text = "TODAY'S SALAH",
                            palette = palette,
                            fontSize = 10
                        )
                        WidgetSpacer()
                        WidgetPill(
                            text = "${state.completedCount} / 5",
                            palette = palette,
                            solid = true,
                            fontSize = 10
                        )
                    }
                    WidgetGap(if (shape == WidgetShape.SQUARE) 5 else 8)
                    if (!state.hasTimes) {
                        WidgetEmptyState("Add your location to see prayer times", palette)
                    } else {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            state.chips.forEach { chip ->
                                PrayerRow(
                                    chip = chip,
                                    palette = palette,
                                    dense = shape == WidgetShape.SQUARE
                                )
                                WidgetGap(2)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One prayer in the 4x1 strip: label, time and a tick, wrapped in an accent pill when next. */
@Composable
private fun PrayerStripCell(
    chip: PrayerChipUi,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    val fill = if (chip.isNext) palette.accent else TRANSPARENT_FILL
    val labelColor = if (chip.isNext) palette.onAccent else palette.primaryText
    val timeColor = if (chip.isNext) palette.onAccent else palette.secondaryText
    val tickColor = if (chip.isNext) palette.onAccent else palette.accent
    Column(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .background(fill)
            .cornerRadius(12.dp)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = chip.label.uppercase(),
            maxLines = 1,
            style = TextStyle(
                color = labelColor,
                fontSize = 9.sp,
                fontWeight = if (chip.isNext) FontWeight.Bold else FontWeight.Medium
            )
        )
        Text(
            text = chip.time,
            maxLines = 1,
            style = TextStyle(color = timeColor, fontSize = 9.sp, fontWeight = FontWeight.Normal)
        )
        Text(
            text = if (chip.isCompleted) "✓" else " ",
            maxLines = 1,
            style = TextStyle(color = tickColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/** A full-width checklist row: `FAJR · 05:12 ✓`, tappable to toggle completion. */
@Composable
private fun PrayerRow(
    chip: PrayerChipUi,
    palette: WidgetPalette,
    dense: Boolean
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(if (chip.isNext) palette.pillBackground else TRANSPARENT_FILL)
            .cornerRadius(10.dp)
            .padding(horizontal = 9.dp, vertical = if (dense) 1.dp else 2.dp)
            .clickable(
                actionRunCallback<ToggleSalahAction>(
                    actionParametersOf(SalahActionKeys.PRAYER_KEY to chip.key)
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chip.label.uppercase(),
            maxLines = 1,
            style = TextStyle(
                color = palette.primaryText,
                fontSize = (if (dense) 9 else 10).sp,
                fontWeight = FontWeight.Bold
            )
        )
        WidgetInlineGap(6)
        Text(
            text = "· ${chip.time}",
            maxLines = 1,
            style = TextStyle(
                color = palette.secondaryText,
                fontSize = (if (dense) 9 else 10).sp,
                fontWeight = FontWeight.Normal
            )
        )
        WidgetSpacer()
        Text(
            text = if (chip.isCompleted) "✓" else "○",
            maxLines = 1,
            style = TextStyle(
                color = if (chip.isCompleted) palette.accent else palette.secondaryText,
                fontSize = (if (dense) 10 else 12).sp,
                fontWeight = FontWeight.Bold
            )
        )
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
        runCatching {
            togglePrayerForWidget(context, prayerKey)
            SalahTrackerWidget().updateAll(context)
        }
    }
}

class SalahTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalahTrackerWidget()
}
