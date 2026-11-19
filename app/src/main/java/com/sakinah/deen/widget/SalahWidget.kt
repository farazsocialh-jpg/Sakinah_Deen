package com.sakinah.deen.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sakinah.deen.MainActivity
import com.sakinah.deen.data.database.DatabasePassphraseProvider
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.service.AzaanScheduling
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** One prayer cell in the strip / tracker. */
data class PrayerChipUi(
    val key: String,
    val label: String,
    val time: String,
    val isPrayed: Boolean,
    val isNext: Boolean
)

/** Everything the widget paints, resolved once per update. */
data class SalahWidgetUiState(
    val nextName: String,
    val nextTime: String,
    val remainingLabel: String,
    val window: PrayerWindow,
    val prayers: List<PrayerChipUi>
)

/** The five obligatory prayers, in order, with their display labels. */
private val OBLIGATORY_PRAYERS = listOf(
    PrayerType.FAJR to "Fajr",
    PrayerType.DHUHR to "Dhuhr",
    PrayerType.ASR to "Asr",
    PrayerType.MAGHRIB to "Maghrib",
    PrayerType.ISHA to "Isha"
)

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** Koin-backed access to the app's existing Salah/prayer-time singletons. */
private object WidgetRepositories : KoinComponent {
    val salahDao: SalahDao by inject()
    val prayerTimes: PrayerTimeRepository by inject()
}

private fun salahDao(context: Context): SalahDao? =
    runCatching { WidgetRepositories.salahDao }.getOrNull()
        ?: runCatching {
            val appContext = context.applicationContext
            SakinahEncryptedDatabase
                .getInstance(appContext, DatabasePassphraseProvider.getPassphrase(appContext))
                .salahDao()
        }.getOrNull()

/** The saved location's zone, so a manual city keeps its own prayer times. */
private fun activeZone(context: Context): ZoneId =
    runCatching { AzaanScheduling.activeZoneId(SakinahPreferences(context)) }
        .getOrDefault(ZoneId.systemDefault())

/**
 * Resolves today's timings from the existing offline-first repository
 * (Room timetable cache → AlAdhan → local astronomy) and joins them with the
 * prayers the user already marked complete.
 */
suspend fun loadTodaySalahState(context: Context): SalahWidgetUiState {
    val zone = activeZone(context)
    val today = LocalDate.now(zone)
    val epochDay = today.toEpochDay()

    val dao = salahDao(context)
    val records = dao?.let { runCatching { it.observeTodaySalah(epochDay).first() }.getOrNull() }
        .orEmpty()
        .associateBy { it.prayerType }

    val times = resolveTodayTimes(context, today, zone)
    val now = LocalTime.now(zone)

    val nextType = OBLIGATORY_PRAYERS
        .map { it.first }
        .firstOrNull { type -> times[type]?.isAfter(now) == true }
        ?: PrayerType.FAJR
    val nextIsTomorrow = times[nextType]?.isAfter(now) != true && times.isNotEmpty()

    val chips = OBLIGATORY_PRAYERS.map { (type, label) ->
        PrayerChipUi(
            key = type.name,
            label = label,
            time = times[type]?.format(TIME_FORMATTER) ?: "--:--",
            isPrayed = records[type.name]?.isCompleted == true,
            isNext = type == nextType
        )
    }

    return SalahWidgetUiState(
        nextName = OBLIGATORY_PRAYERS.first { it.first == nextType }.second,
        nextTime = times[nextType]?.format(TIME_FORMATTER) ?: "--:--",
        remainingLabel = remainingLabel(times[nextType], now, nextIsTomorrow),
        window = nextType.toPrayerWindow(),
        prayers = chips
    )
}

private suspend fun resolveTodayTimes(
    context: Context,
    date: LocalDate,
    zone: ZoneId
): Map<PrayerType, LocalTime> {
    val preferences = runCatching { SakinahPreferences(context) }.getOrNull() ?: return emptyMap()
    val coordinates = preferences.locationCoordinates ?: return emptyMap()
    val repository = runCatching { WidgetRepositories.prayerTimes }.getOrNull() ?: return emptyMap()

    return try {
        // A widget update must not hang on the network; the cache answers instantly.
        withTimeoutOrNull(8_000) {
            repository.resolveDay(
                date = date,
                coordinates = coordinates,
                madhhab = preferences.madhhab,
                zoneId = zone,
                highLatitudeRule = preferences.highLatitudeRule
            ).times
        } ?: emptyMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun remainingLabel(nextTime: LocalTime?, now: LocalTime, tomorrow: Boolean): String {
    if (nextTime == null) return "No timetable yet"
    val target = if (tomorrow) nextTime.plusHours(24) else nextTime
    val minutes = Duration.between(now, target).toMinutes().coerceAtLeast(0)
    val hours = minutes / 60
    return when {
        hours > 0 -> "in ${hours}h ${minutes % 60}m"
        minutes > 0 -> "in $minutes min"
        else -> "now"
    }
}

private fun PrayerType.toPrayerWindow(): PrayerWindow = when (this) {
    PrayerType.FAJR -> PrayerWindow.FAJR
    PrayerType.DHUHR -> PrayerWindow.DHUHR
    PrayerType.ASR -> PrayerWindow.ASR
    PrayerType.MAGHRIB -> PrayerWindow.MAGHRIB
    PrayerType.ISHA -> PrayerWindow.ISHA
    PrayerType.SUNRISE -> PrayerWindow.FAJR
}

/** Marks the prayer prayed, or clears it again when it already was. */
private suspend fun toggleTodayPrayerInRepo(context: Context, prayerKey: String) {
    val dao = salahDao(context) ?: return
    val epochDay = LocalDate.now(activeZone(context)).toEpochDay()
    val existing = dao.getSalahRecord(epochDay, prayerKey)

    if (existing == null) {
        dao.upsertSalahRecord(
            SalahRecordEntity(
                dateEpochDay = epochDay,
                prayerType = prayerKey,
                isCompleted = true,
                isJamaah = false,
                isQadha = false,
                completedTimestamp = System.currentTimeMillis()
            )
        )
    } else {
        dao.updateSalahRecord(
            existing.copy(
                isCompleted = !existing.isCompleted,
                completedTimestamp = if (existing.isCompleted) null else System.currentTimeMillis()
            )
        )
    }
}

class SalahWidget : GlanceAppWidget() {
    companion object {
        private val COMPACT = DpSize(130.dp, 60.dp)
        private val WIDE = DpSize(250.dp, 60.dp)
        private val EXPANDED = DpSize(250.dp, 130.dp)
        val PRAYER_KEY = ActionParameters.Key<String>("prayer_key")
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE, EXPANDED))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val style = WidgetPrefsStore.getStyle(context, appWidgetId)
        val isDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val state = loadTodaySalahState(context)
        val palette = resolveWidgetPalette(style, isDark, state.window)

        provideContent {
            SalahWidgetContent(LocalSize.current, state, palette)
        }
    }
}

@Composable
private fun SalahWidgetContent(
    size: DpSize,
    state: SalahWidgetUiState,
    palette: WidgetPalette
) {
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(palette.background)
        .cornerRadius(24.dp)
        .padding(14.dp)
        .clickable(actionStartActivity<MainActivity>())

    when {
        size.height >= 120.dp && size.width >= 220.dp -> {
            // 4x2: interactive tracker — tap a pill to mark that prayer prayed.
            val doneCount = state.prayers.count { it.isPrayed }
            Column(modifier = rootModifier) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Salah",
                        style = TextStyle(
                            color = palette.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = "$doneCount / 5",
                        style = TextStyle(
                            color = palette.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(GlanceModifier.height(10.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    state.prayers.forEach { prayer ->
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .padding(horizontal = 3.dp, vertical = 6.dp)
                                .background(palette.pillBackground)
                                .cornerRadius(12.dp)
                                .clickable(
                                    actionRunCallback<TogglePrayerAction>(
                                        actionParametersOf(SalahWidget.PRAYER_KEY to prayer.key)
                                    )
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (prayer.isPrayed) "✓" else "○",
                                style = TextStyle(
                                    color = if (prayer.isPrayed) palette.accent else palette.secondaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = prayer.label,
                                style = TextStyle(
                                    color = if (prayer.isNext) palette.accent else palette.primaryText,
                                    fontSize = 11.sp,
                                    fontWeight = if (prayer.isNext) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                            Text(
                                text = prayer.time,
                                style = TextStyle(color = palette.secondaryText, fontSize = 10.sp)
                            )
                        }
                    }
                }
            }
        }

        size.width >= 220.dp -> {
            // 4x1: the five prayers in one row.
            Row(
                modifier = rootModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.prayers.forEach { prayer ->
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (prayer.isPrayed) "✓ ${prayer.label}" else prayer.label,
                            style = TextStyle(
                                color = if (prayer.isNext) palette.accent else palette.primaryText,
                                fontSize = 11.sp,
                                fontWeight = if (prayer.isNext) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        Text(
                            text = prayer.time,
                            style = TextStyle(color = palette.secondaryText, fontSize = 11.sp)
                        )
                    }
                }
            }
        }

        else -> {
            // 2x1: next prayer and its countdown.
            Column(
                modifier = rootModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.nextName,
                        style = TextStyle(
                            color = palette.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = state.nextTime,
                        style = TextStyle(
                            color = palette.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = state.remainingLabel,
                    style = TextStyle(color = palette.secondaryText, fontSize = 12.sp)
                )
            }
        }
    }
}

/** Toggles one prayer for today in the app's Salah table, then repaints every widget. */
class TogglePrayerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prayerKey = parameters[SalahWidget.PRAYER_KEY] ?: return
        toggleTodayPrayerInRepo(context, prayerKey)
        SalahWidget().update(context, glanceId)
    }
}

class SalahWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalahWidget()
}
