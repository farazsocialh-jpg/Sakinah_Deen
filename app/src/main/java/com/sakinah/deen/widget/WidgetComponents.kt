package com.sakinah.deen.widget

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sakinah.deen.MainActivity

/**
 * The four responsive buckets every widget declares. A widget is composed for the bucket
 * matching the host's cells, so one widget definition covers 2x1 through 4x2.
 */
object WidgetSizes {
    val COMPACT = DpSize(130.dp, 60.dp) // 2x1
    val WIDE = DpSize(250.dp, 60.dp) // 4x1
    val SQUARE = DpSize(130.dp, 130.dp) // 2x2
    val EXPANDED = DpSize(250.dp, 130.dp) // 4x2

    val ALL: Set<DpSize> = setOf(COMPACT, WIDE, SQUARE, EXPANDED)
}

enum class WidgetShape { COMPACT, WIDE, SQUARE, EXPANDED }

/** Maps the host-reported size to the closest declared bucket. */
fun shapeOf(size: DpSize): WidgetShape = when {
    size.width >= 200.dp && size.height >= 100.dp -> WidgetShape.EXPANDED
    size.height >= 100.dp -> WidgetShape.SQUARE
    size.width >= 180.dp -> WidgetShape.WIDE
    else -> WidgetShape.COMPACT
}

/** The shared 24dp frosted container used by every widget. */
@Composable
fun WidgetShell(
    palette: WidgetPalette,
    onClickAction: Action,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(onClickAction)
            .then(modifier),
        content = content
    )
}

@Composable
fun WidgetTitle(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = palette.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
fun WidgetBody(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: Int = 13,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 3
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = palette.primaryText,
            fontSize = fontSize.sp,
            fontWeight = weight
        )
    )
}

@Composable
fun WidgetCaption(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 2
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = palette.secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    )
}

@Composable
fun WidgetAccentValue(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: Int = 15,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = palette.accent,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

/** A one-pixel divider; on the transparent skin it is the only structure on screen. */
@Composable
fun WidgetDivider(palette: WidgetPalette) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.divider)
    ) {}
}

/** A subtle rounded chip used for badges and inline values. */
@Composable
fun WidgetPill(
    text: String,
    palette: WidgetPalette,
    highlighted: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
) {
    Text(
        text = text,
        modifier = modifier
            .background(palette.pillBackground)
            .cornerRadius(999.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
        style = TextStyle(
            color = if (highlighted) palette.accent else palette.primaryText,
            fontSize = 11.sp,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium
        )
    )
}

@Composable
fun WidgetMetric(
    label: String,
    value: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        WidgetAccentValue(text = value, palette = palette, fontSize = 14)
        WidgetCaption(text = label, palette = palette, maxLines = 1)
    }
}

@Composable
fun RowScope.WidgetSpacer() {
    Spacer(GlanceModifier.defaultWeight())
}

@Composable
fun WidgetRow(
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    Row(modifier = modifier, verticalAlignment = verticalAlignment, content = content)
}

/** Resolves the saved style/theme for one placed widget instance. */
suspend fun widgetPaletteFor(
    context: Context,
    appWidgetId: Int,
    window: PrayerWindow
): WidgetPalette {
    val style = WidgetPrefsStore.getStyle(context, appWidgetId)
    val mode = WidgetPrefsStore.getThemeMode(context, appWidgetId)
    val systemDark = (context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return resolveWidgetPalette(style, resolveDarkMode(mode, systemDark), window)
}

object WidgetRouteKey {
    val ROUTE = ActionParameters.Key<String>("route")
}

/**
 * Opens the app, optionally deep-linked to a screen the activity accepts.
 * Glance writes the parameters into the launch Intent as extras.
 */
fun openAppAction(context: Context, route: String? = null): Action {
    val parameters = if (route == null) {
        actionParametersOf()
    } else {
        actionParametersOf(WidgetRouteKey.ROUTE to route)
    }
    return actionStartActivity(ComponentName(context, MainActivity::class.java), parameters)
}

/** Every widget instance, used by the style picker and the app-wide refresh. */
object WidgetCatalog {
    val widgets: List<GlanceAppWidget> by lazy {
        listOf(
            NextPrayerWidget(),
            PrayerStripWidget(),
            SalahTrackerWidget(),
            TasbihWidget(),
            AdhkarWidget(),
            DailyDuaWidget(),
            DailyAyahWidget(),
            DailyHadithWidget(),
            AiScholarWidget(),
            QuranStreakWidget(),
            HijriDateWidget(),
            QiblaWidget(),
            RamadanWidget(),
            DuhaWindowWidget(),
            QiyamWidget(),
            SunnahFastWidget(),
            IslamicEventWidget(),
            JumuahWidget(),
            NisabWidget()
        )
    }

    /** Maps the registered receiver back to its Glance widget after the picker saves. */
    fun widgetForReceiver(className: String): GlanceAppWidget? = when (className.substringAfterLast('.')) {
        "NextPrayerWidgetReceiver" -> NextPrayerWidget()
        "PrayerStripWidgetReceiver" -> PrayerStripWidget()
        "SalahTrackerWidgetReceiver" -> SalahTrackerWidget()
        "TasbihWidgetReceiver" -> TasbihWidget()
        "AdhkarWidgetReceiver" -> AdhkarWidget()
        "DailyDuaWidgetReceiver" -> DailyDuaWidget()
        "DailyAyahWidgetReceiver" -> DailyAyahWidget()
        "DailyHadithWidgetReceiver" -> DailyHadithWidget()
        "AiScholarWidgetReceiver" -> AiScholarWidget()
        "QuranStreakWidgetReceiver" -> QuranStreakWidget()
        "HijriDateWidgetReceiver" -> HijriDateWidget()
        "QiblaWidgetReceiver" -> QiblaWidget()
        "RamadanWidgetReceiver" -> RamadanWidget()
        "DuhaWindowWidgetReceiver" -> DuhaWindowWidget()
        "QiyamWidgetReceiver" -> QiyamWidget()
        "SunnahFastWidgetReceiver" -> SunnahFastWidget()
        "IslamicEventWidgetReceiver" -> IslamicEventWidget()
        "JumuahWidgetReceiver" -> JumuahWidget()
        "NisabWidgetReceiver" -> NisabWidget()
        else -> null
    }
}

/** Repaints every placed widget, e.g. after a location, prayer log or theme change. */
suspend fun updateAllWidgets(context: Context) {
    WidgetCatalog.widgets.forEach { widget ->
        runCatching { widget.updateAll(context) }
    }
}

/** Repaints a single placed widget instance. */
suspend fun updateWidget(context: Context, appWidgetId: Int, widget: GlanceAppWidget) {
    val glanceId = runCatching {
        GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    }.getOrNull() ?: return
    runCatching { widget.update(context, glanceId) }
}

/** Shared empty-state line, e.g. no location configured yet. */
@Composable
fun WidgetEmptyState(text: String, palette: WidgetPalette) {
    WidgetCaption(text = text, palette = palette, maxLines = 2)
}

@Composable
fun WidgetGap(size: Int = 6) {
    Spacer(GlanceModifier.height(size.dp))
}

@Composable
fun WidgetInlineGap(size: Int = 6) {
    Spacer(GlanceModifier.width(size.dp))
}
