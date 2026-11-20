package com.sakinah.deen.widget

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
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
import com.sakinah.deen.ui.theme.UserAppearanceConfig

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

/** Corner radius shared by every widget surface. */
internal val WidgetCorner = 24.dp

/**
 * The shared 24dp container used by every widget. The fill is a palette colour, except for
 * Living Sky which paints its prayer-window gradient. A skin that asks for a hairline gets a
 * 1dp outer layer with a 23dp inner surface, because Glance 1.1 has no border modifier.
 */
@Composable
fun WidgetShell(
    palette: WidgetPalette,
    onClickAction: Action,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val surface = if (palette.backgroundImageRes != 0) {
        GlanceModifier.background(ImageProvider(palette.backgroundImageRes))
    } else {
        GlanceModifier.background(palette.background)
    }
    val hairline = palette.hairline
    if (hairline == null) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .then(surface)
                .cornerRadius(WidgetCorner)
                .padding(16.dp)
                .clickable(onClickAction)
                .then(modifier),
            content = content
        )
    } else {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(hairline)
                .cornerRadius(WidgetCorner)
                .clickable(onClickAction)
                .then(modifier)
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .then(surface)
                    .cornerRadius(23.dp)
                    .padding(15.dp),
                content = content
            )
        }
    }
}

@Composable
fun WidgetTitle(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 1,
    fontSize: Int = 17
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = palette.primaryText,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
fun WidgetBody(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: Int = 14,
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
    maxLines: Int = 2,
    fontSize: Int = 13
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = palette.secondaryText,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal
        )
    )
}

@Composable
fun WidgetAccentValue(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: Int = 17,
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

/** A subtle rounded chip used for badges and inline buttons. */
@Composable
fun WidgetPill(
    text: String,
    palette: WidgetPalette,
    highlighted: Boolean = false,
    solid: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: Int = 13
) {
    val background = if (solid) palette.accent else palette.pillBackground
    val content = when {
        solid -> palette.onAccent
        highlighted -> palette.accent
        else -> palette.primaryText
    }
    Text(
        text = text,
        modifier = modifier
            .background(background)
            .cornerRadius(999.dp)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        maxLines = 1,
        style = TextStyle(
            color = content,
            fontSize = fontSize.sp,
            fontWeight = if (solid || highlighted) FontWeight.Bold else FontWeight.Medium
        )
    )
}

/**
 * An outlined pill: a hairline ring drawn by a 1dp outer layer, used for secondary actions
 * such as "Read Surah" and the Scholar search bar.
 */
@Composable
fun WidgetOutlinedPill(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: Int = 13
) {
    Box(
        modifier = modifier
            .background(palette.divider)
            .cornerRadius(999.dp)
    ) {
        Text(
            text = text,
            modifier = GlanceModifier
                .padding(1.dp)
                .background(palette.pillBackground)
                .cornerRadius(999.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            style = TextStyle(
                color = palette.accent,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/** The thin accent bar under a countdown or dhikr total. */
@Composable
fun WidgetProgress(
    progress: Float,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .cornerRadius(999.dp),
        color = palette.accent,
        backgroundColor = palette.track
    )
}

@Composable
fun RowScope.WidgetSpacer() {
    Spacer(GlanceModifier.defaultWeight())
}

@Composable
fun ColumnScope.WidgetVerticalSpacer() {
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

/** Resolves the saved style, theme override and active app palette for one widget instance. */
suspend fun widgetPaletteFor(
    context: Context,
    appWidgetId: Int,
    window: PrayerWindow
): WidgetPalette {
    val style = WidgetPrefsStore.getStyle(context, appWidgetId)
    val mode = WidgetPrefsStore.getThemeMode(context, appWidgetId)
    val systemDark = (context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val appearance = appAppearance(context) ?: UserAppearanceConfig()
    val isDark = resolveDarkMode(mode, appearance.themeMode, systemDark)
    return resolveWidgetPalette(style, isDark, window, appearance.palette)
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

/**
 * The six shipped widgets. Keeping the entries as plain data lets the catalog be asserted
 * in a JVM unit test without a launcher or a running Glance host.
 */
data class WidgetCatalogEntry(
    val label: String,
    val receiverClassName: String,
    val widget: GlanceAppWidget
)

/** Every widget instance, used by the style picker and the app-wide refresh. */
object WidgetCatalog {
    val entries: List<WidgetCatalogEntry> = listOf(
        WidgetCatalogEntry("Salah Tracker", "SalahTrackerWidgetReceiver", SalahTrackerWidget()),
        WidgetCatalogEntry("Tasbih Counter", "TasbihWidgetReceiver", TasbihWidget()),
        WidgetCatalogEntry("Ayah of the Day", "DailyAyahWidgetReceiver", DailyAyahWidget()),
        WidgetCatalogEntry("Hijri Date", "HijriDateWidgetReceiver", HijriDateWidget()),
        WidgetCatalogEntry("Qibla Direction", "QiblaWidgetReceiver", QiblaWidget()),
        WidgetCatalogEntry("Ask ImanCore Scholar", "AiScholarWidgetReceiver", AiScholarWidget())
    )

    val widgets: List<GlanceAppWidget> get() = entries.map { it.widget }

    /** Maps the registered receiver back to its Glance widget after the picker saves. */
    fun widgetForReceiver(className: String): GlanceAppWidget? =
        entries.firstOrNull { it.receiverClassName == className.substringAfterLast('.') }?.widget
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
