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
import java.time.DayOfWeek
import java.time.LocalTime

/** One prayer cell on the tracker. */
data class PrayerChipUi(
    val key: String,
    val label: String,
    val time: String,
    val isCompleted: Boolean,
    val isNext: Boolean,
    val isCurrent: Boolean = false,
    val isJamaah: Boolean = false,
    val isMuted: Boolean = false
)

/** Everything the Salah widget paints, resolved once per update. */
data class SalahBoardState(
    val times: WidgetTimes,
    val now: LocalTime,
    val next: PrayerType?,
    val current: PrayerType? = null,
    val completed: Set<String>,
    val jamaah: Set<String> = emptySet(),
    val leaveBufferMinutes: Int = 10,
    val travelMode: Boolean = false,
    val isRamadan: Boolean = false,
    val missedToday: Int = 0,
    val streakCount: Int = 0,
    val longestStreakCount: Int = 0,
    val nearestMasjid: NearestMasjid? = null,
    val mutedPrayers: Set<String> = emptySet(),
    val calculationMethodLabel: String = "",
    val qiblaBearingLabel: String = "",
    val nextEventLabel: String = ""
) {
    val chips: List<PrayerChipUi> = OBLIGATORY_PRAYERS.map { (type, label) ->
        PrayerChipUi(
            key = type.name,
            label = if (times.date.dayOfWeek == DayOfWeek.FRIDAY && type == PrayerType.DHUHR) {
                "Jumuah"
            } else {
                label
            },
            time = times.label(type),
            isCompleted = completed.contains(type.name),
            isNext = type == next,
            isCurrent = type == current,
            isJamaah = jamaah.contains(type.name),
            isMuted = mutedPrayers.contains(type.name)
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
    val epochDay = times.date.toEpochDay()
    val completed = loadCompletedPrayers(context, epochDay)
    val jamaah = loadJamaahPrayers(context, epochDay)
    val missedToday = OBLIGATORY_PRAYERS.count { (type, _) ->
        val t = times.times[type] ?: return@count false
        !completed.contains(type.name) && now.isAfter(t)
    }
    val allComplete = completed.size >= 5
    WidgetPrefsStore.bumpStreakIfComplete(context, allComplete)
    val streak = WidgetPrefsStore.streakCount(context)
    val longestStreak = WidgetPrefsStore.longestStreakCount(context)
    val leaveBuffer = WidgetPrefsStore.leaveBufferMinutes(context)
    val travelMode = WidgetPrefsStore.travelMode(context)
    val isRamadan = hijriFor(times.date).month == 9
    val prefs = appPreferences(context)
    val coords = prefs?.locationCoordinates
    val masjid = if (coords != null) {
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(6_000) {
                loadNearestMasjid(context, coords.first, coords.second)
            }
        }.getOrNull()
    } else null
    val muted = prefs?.azaanSettings?.modes?.filterValues { it == com.sakinah.deen.data.model.PrayerAlertMode.OFF }?.keys?.map { it.name }?.toSet() ?: emptySet()
    val methodLabel = prefs?.calculationMethodOverride?.name ?: "Auto"
    val qiblaLabel = coords?.let { qiblaSummary(it.first, it.second, prefs.locationLabel).let { q -> "${q.bearing}° ${q.cardinal}" } } ?: ""
    val nextEvent = runCatching { nextIslamicEvent(times.date) }.getOrNull()?.let { "${it.name} ${relativeDays(it.daysAway)}" } ?: ""
    return SalahBoardState(
        times = times,
        now = now,
        next = times.nextToday(now),
        current = currentPrayer(times, now),
        completed = completed,
        jamaah = jamaah,
        leaveBufferMinutes = leaveBuffer,
        travelMode = travelMode,
        isRamadan = isRamadan,
        missedToday = missedToday,
        streakCount = streak,
        longestStreakCount = longestStreak,
        nearestMasjid = masjid,
        mutedPrayers = muted,
        calculationMethodLabel = methodLabel,
        qiblaBearingLabel = qiblaLabel,
        nextEventLabel = nextEvent
    )
}

private fun countdownFor(state: SalahBoardState): String {
    val target = state.next ?: PrayerType.FAJR
    val time = state.times.times[target] ?: return "No timetable"
    val mins = minutesUntil(state.now, time)
    val name = if (state.isRamadan && target == PrayerType.MAGHRIB) "Iftar" else prayerLabel(target)
    val leaveTime = time.minusMinutes(state.leaveBufferMinutes.toLong())
    val leaveMins = minutesUntil(state.now, leaveTime)
    val leaveText = if (leaveMins <= 0) "leave now" else "leave in ${leaveMins}m"
    return "$name in ${durationLabel(mins)} · $leaveText"
}

/** The prayer the compact widget should name: the next one, or Fajr after Isha. */
private fun nextPrayerName(state: SalahBoardState): String {
    val next = state.next ?: PrayerType.FAJR
    if (state.times.date.dayOfWeek == DayOfWeek.FRIDAY && next == PrayerType.DHUHR) {
        return "Jumu'ah"
    }
    return prayerLabel(next)
}

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
                        WidgetTitle(text = nextPrayerName(state), palette = palette, fontSize = 17)
                        WidgetInlineGap(5)
                        WidgetAccentValue(
                            text = nextPrayerTime(state),
                            palette = palette,
                            fontSize = 15
                        )
                        WidgetSpacer()
                        if (state.hasTimes) {
                                                            WidgetCaption(
                                text = countdownFor(state),
                                palette = palette,
                                maxLines = 1,
                                fontSize = 15
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
                            fontSize = 14
                        )
                        WidgetInlineGap(6)
                        WidgetPill(
                            text = if (state.travelMode) "Travel ✓" else "Travel",
                            palette = palette,
                            highlighted = state.travelMode,
                            modifier = GlanceModifier.clickable(actionRunCallback<ToggleTravelModeAction>()),
                            fontSize = 11
                        )
                        WidgetSpacer()
                        if (state.streakCount > 0) {
                            WidgetPill(text = "🔥 ${state.streakCount}" + if (state.longestStreakCount > state.streakCount) " (best ${state.longestStreakCount})" else "", palette = palette, fontSize = 11)
                            WidgetInlineGap(4)
                        }
                        if (state.missedToday > 0) {
                            WidgetPill(text = "${state.missedToday} missed", palette = palette, fontSize = 11)
                            WidgetInlineGap(4)
                        }
                        WidgetPill(
                            text = "${state.completedCount} / 5",
                            palette = palette,
                            solid = true,
                            fontSize = 13
                        )
                    }
                    if (state.times.isEstimated) {
                        WidgetGap(2)
                        WidgetCaption(text = "Estimated times (offline)", palette = palette, maxLines = 1, fontSize = 11)
                    }
                    state.nearestMasjid?.let { m ->
                        WidgetGap(2)
                        WidgetCaption(
                            text = "Masjid: ${m.name} · ${m.distanceLabel} · ${m.walkingLabel}",
                            palette = palette,
                            maxLines = 1,
                            fontSize = 11
                        )
                    }
                    if (state.calculationMethodLabel.isNotBlank()) {
                        WidgetGap(2)
                        WidgetCaption(
                            text = "Method: ${state.calculationMethodLabel} · Qibla ${state.qiblaBearingLabel}",
                            palette = palette,
                            maxLines = 1,
                            fontSize = 11
                        )
                    }
                    if (state.nextEventLabel.isNotBlank()) {
                        WidgetGap(2)
                        WidgetCaption(
                            text = "Next: ${state.nextEventLabel}",
                            palette = palette,
                            maxLines = 1,
                            fontSize = 11
                        )
                    }
                    if (state.missedToday > 0) {
                        WidgetGap(2)
                        WidgetCaption(
                            text = "Recovery: +1 prayer/day to clear ${state.missedToday} missed",
                            palette = palette,
                            maxLines = 1,
                            fontSize = 11
                        )
                    }
                    if (state.leaveBufferMinutes > 0 && state.next != null) {
                        // smart hint already in countdown; nothing extra
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
                                WidgetGap(6)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One prayer in the 4x1 strip: a clean 1-line name + 1-line time, highlighted pill when current. */
@Composable
private fun PrayerStripCell(
    chip: PrayerChipUi,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    val fill = if (chip.isCurrent) palette.accent else TRANSPARENT_FILL
    val labelColor = if (chip.isCurrent) palette.onAccent else palette.primaryText
    val timeColor = if (chip.isCurrent) palette.onAccent else palette.secondaryText
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .background(fill)
            .cornerRadius(12.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = chip.label.uppercase(),
            maxLines = 1,
            style = TextStyle(
                color = labelColor,
                fontSize = 14.sp,
                fontWeight = if (chip.isCurrent) FontWeight.Bold else FontWeight.Medium
            )
        )
        Text(
            text = chip.time,
            maxLines = 1,
            style = TextStyle(color = timeColor, fontSize = 15.sp, fontWeight = FontWeight.Normal)
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
            .background(if (chip.isCurrent) palette.pillBackground else TRANSPARENT_FILL)
            .cornerRadius(10.dp)
            .padding(horizontal = 12.dp, vertical = if (dense) 2.dp else 4.dp)
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
                fontSize = (if (dense) 13 else 14).sp,
                fontWeight = FontWeight.Bold
            )
        )
        WidgetInlineGap(6)
        Text(
            text = "· ${chip.time}",
            maxLines = 1,
            style = TextStyle(
                color = palette.secondaryText,
                fontSize = (if (dense) 13 else 14).sp,
                fontWeight = FontWeight.Normal
            )
        )
        WidgetSpacer()
        Text(
            text = if (chip.isMuted) "🔇" else "🔊",
            maxLines = 1,
            modifier = GlanceModifier.clickable(
                actionRunCallback<MutePrayerAction>(
                    actionParametersOf(SalahActionKeys.PRAYER_KEY to chip.key)
                )
            ),
            style = TextStyle(
                color = if (chip.isMuted) palette.secondaryText else palette.accent,
                fontSize = (if (dense) 12 else 13).sp,
                fontWeight = FontWeight.Normal
            )
        )
        WidgetInlineGap(4)
        if (chip.isCompleted) {
            Text(
                text = if (chip.isJamaah) "JJ" else "J",
                maxLines = 1,
                modifier = GlanceModifier.clickable(
                    actionRunCallback<ToggleJamaahAction>(
                        actionParametersOf(SalahActionKeys.PRAYER_KEY to chip.key)
                    )
                ),
                style = TextStyle(
                    color = if (chip.isJamaah) palette.accent else palette.secondaryText,
                    fontSize = (if (dense) 11 else 12).sp,
                    fontWeight = FontWeight.Bold
                )
            )
            WidgetInlineGap(6)
        }
        Text(
            text = if (chip.isCompleted) "✓" else "○",
            maxLines = 1,
            style = TextStyle(
                color = if (chip.isCompleted) palette.accent else palette.secondaryText,
                fontSize = (if (dense) 14 else 15).sp,
                fontWeight = FontWeight.Bold
            )
        )
        WidgetInlineGap(4)
        Text(
            text = "›",
            maxLines = 1,
            modifier = GlanceModifier.clickable(openAppAction(LocalContext.current, "salah")),
            style = TextStyle(
                color = palette.secondaryText,
                fontSize = (if (dense) 12 else 13).sp,
                fontWeight = FontWeight.Normal
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

/** Toggles jama'ah status for one prayer, then repaints. */
class ToggleJamaahAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prayerKey = parameters[SalahActionKeys.PRAYER_KEY] ?: return
        runCatching {
            WidgetPrefsStore.toggleJamaahForWidget(context, prayerKey)
            SalahTrackerWidget().updateAll(context)
        }
    }
}

/** Toggles travel mode, then repaints. */
class ToggleTravelModeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        runCatching {
            WidgetPrefsStore.setTravelMode(context, !WidgetPrefsStore.travelMode(context))
            SalahTrackerWidget().updateAll(context)
        }
    }
}

/** Toggles per-prayer adhan mute, then repaints and reschedules alarms. */
class MutePrayerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prayerKey = parameters[SalahActionKeys.PRAYER_KEY] ?: return
        val prayerType = runCatching { com.sakinah.deen.data.model.PrayerType.valueOf(prayerKey) }.getOrNull() ?: return
        runCatching {
            val prefs = appPreferences(context) ?: return@runCatching
            val current = prefs.azaanSettings
            val currentlyOff = current.modeFor(prayerType) == com.sakinah.deen.data.model.PrayerAlertMode.OFF
            val newMode = if (currentlyOff) com.sakinah.deen.data.model.PrayerAlertMode.FULL_AZAAN else com.sakinah.deen.data.model.PrayerAlertMode.OFF
            prefs.azaanSettings = current.copy(modes = current.modes + (prayerType to newMode))
            // Reschedule alarms so the muted prayer loses (or regains) its PendingIntent.
            runCatching {
                val scheduler = com.sakinah.deen.service.PrayerAlarmScheduler(
                    context,
                    com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl()
                )
                scheduler.scheduleAllDailyAlarmsRefreshed()
            }
            SalahTrackerWidget().updateAll(context)
        }
    }
}

class SalahTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalahTrackerWidget()
}
