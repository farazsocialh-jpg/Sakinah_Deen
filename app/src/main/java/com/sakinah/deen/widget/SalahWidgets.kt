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

/** One prayer cell on the strip / tracker. */
data class PrayerChipUi(
    val key: String,
    val label: String,
    val time: String,
    val isCompleted: Boolean,
    val isNext: Boolean
)

/** Everything the three Salah widgets paint, resolved once per update. */
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
    val time = state.times.times[target] ?: return "No timetable yet"
    return countdownLabel(minutesUntil(state.now, time))
}

// ---------------------------------------------------------------------------
// Next prayer (2x1 / 4x1)
// ---------------------------------------------------------------------------

class NextPrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadSalahBoard(context)
        val window = currentPrayerWindow(state.times.times, state.now)
        val palette = widgetPaletteFor(context, appWidgetId, window)
        val city = cityLabel(context)
        val nextType = state.next ?: PrayerType.FAJR
        val nextName = prayerLabel(nextType)
        val nextTime = state.times.label(nextType)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "salah")) {
                WidgetRow {
                    Column {
                        WidgetCaption(text = "NEXT PRAYER", palette = palette)
                        if (shape == WidgetShape.COMPACT || shape == WidgetShape.WIDE) {
                            WidgetRow {
                                WidgetTitle(text = nextName, palette = palette)
                                WidgetInlineGap()
                                WidgetAccentValue(text = nextTime, palette = palette)
                            }
                        }
                    }
                    WidgetSpacer()
                    WidgetPill(text = city, palette = palette)
                }
                WidgetGap(4)
                if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) {
                    WidgetAccentValue(text = nextTime, palette = palette, fontSize = 26)
                }
                if (!state.hasTimes) {
                    WidgetEmptyState("Add your location to see prayer times", palette)
                } else {
                    WidgetCaption(
                        text = countdownFor(state) + " · " + state.completedCount + "/5 today",
                        palette = palette,
                        maxLines = 1
                    )
                }
                if (shape == WidgetShape.EXPANDED) {
                    WidgetGap(8)
                    WidgetDivider(palette)
                    WidgetGap(8)
                    WidgetRow {
                        state.chips.forEach { chip ->
                            Column(
                                modifier = GlanceModifier.defaultWeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                WidgetCaption(
                                    text = if (chip.isCompleted) "✓ ${chip.label}" else chip.label,
                                    palette = palette,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}

// ---------------------------------------------------------------------------
// Prayer strip (4x1 / 4x2)
// ---------------------------------------------------------------------------

class PrayerStripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadSalahBoard(context)
        val window = currentPrayerWindow(state.times.times, state.now)
        val palette = widgetPaletteFor(context, appWidgetId, window)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "salah")) {
                if (shape == WidgetShape.SQUARE || shape == WidgetShape.EXPANDED) {
                    WidgetRow {
                        WidgetTitle(text = "Today's Prayers", palette = palette)
                        WidgetSpacer()
                        WidgetAccentValue(
                            text = "${state.completedCount}/5",
                            palette = palette,
                            fontSize = 13
                        )
                    }
                    WidgetGap(8)
                }
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    state.chips.forEach { chip ->
                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (chip.isCompleted) "✓" else "○",
                                style = TextStyle(
                                    color = if (chip.isCompleted) palette.accent else palette.secondaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            WidgetBody(
                                text = chip.label,
                                palette = palette,
                                fontSize = 11,
                                weight = if (chip.isNext) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                            WidgetCaption(text = chip.time, palette = palette, maxLines = 1)
                        }
                    }
                }
                if (shape == WidgetShape.EXPANDED) {
                    WidgetGap(8)
                    WidgetDivider(palette)
                    WidgetGap(8)
                    WidgetRow {
                        state.chips.filter { it.isNext }.forEach { chip ->
                            WidgetPill(
                                text = "${chip.label} in ${countdownFor(state)}",
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

class PrayerStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerStripWidget()
}

// ---------------------------------------------------------------------------
// Interactive Salah tracker (4x2 / 4x1)
// ---------------------------------------------------------------------------

object SalahActionKeys {
    val PRAYER_KEY = ActionParameters.Key<String>("salah_prayer_key")
}

class SalahTrackerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val state = loadSalahBoard(context)
        val window = currentPrayerWindow(state.times.times, state.now)
        val palette = widgetPaletteFor(context, appWidgetId, window)

        provideContent {
            val shape = shapeOf(LocalSize.current)
            WidgetShell(palette, openAppAction(LocalContext.current, "salah")) {
                WidgetRow {
                    WidgetTitle(text = "Today's Salah", palette = palette)
                    WidgetSpacer()
                    WidgetAccentValue(
                        text = "${state.completedCount} / 5",
                        palette = palette,
                        fontSize = 15
                    )
                }
                WidgetGap(if (shape == WidgetShape.COMPACT) 4 else 10)
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    state.chips.forEach { chip ->
                        TrackerChip(
                            chip = chip,
                            palette = palette,
                            compact = shape == WidgetShape.COMPACT,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
                if (shape == WidgetShape.EXPANDED) {
                    WidgetGap(8)
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
                        if (state.next != null) {
                            WidgetPill(text = countdownFor(state), palette = palette, highlighted = true)
                        }
                    }
                }
            }
        }
    }
}

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

/** Toggles one prayer for today in the app's Salah table, then repaints the widget. */
class ToggleSalahAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prayerKey = parameters[SalahActionKeys.PRAYER_KEY] ?: return
        togglePrayerForWidget(context, prayerKey)
        SalahTrackerWidget().update(context, glanceId)
        PrayerStripWidget().updateAll(context)
        NextPrayerWidget().updateAll(context)
    }
}

class SalahTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalahTrackerWidget()
}
