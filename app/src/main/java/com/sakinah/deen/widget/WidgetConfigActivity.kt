package com.sakinah.deen.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.ui.theme.SakinahTheme
import com.sakinah.deen.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Lets the user pick the visual style and light/dark mode for one placed widget.
 * Everything is stored per `appWidgetId`; the global default is only a fallback.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            val appearance = remember {
                AppearancePreferences(this@WidgetConfigActivity).config.value
            }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            var style by remember { mutableStateOf<WidgetStyle?>(null) }
            var themeMode by remember { mutableStateOf<WidgetThemeMode?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(appWidgetId) {
                style = WidgetPrefsStore.getStyle(this@WidgetConfigActivity, appWidgetId)
                themeMode = WidgetPrefsStore.getThemeMode(this@WidgetConfigActivity, appWidgetId)
            }

            SakinahTheme(
                darkTheme = darkTheme,
                palette = appearance.palette,
                fontScaleMultiplier = appearance.fontScale.multiplier
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Widget appearance",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Choose a look and a theme for this widget only.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "Visual style",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        val accent = appearance.palette.widgetAccent(darkTheme)
                        WidgetStyle.entries.forEach { option ->
                            StyleOptionCard(
                                style = option,
                                selected = style == option,
                                isDark = darkTheme,
                                accent = accent,
                                onClick = { style = option }
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Theme mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WidgetThemeMode.entries.forEach { option ->
                                ThemeChip(
                                    label = option.label,
                                    selected = themeMode == option,
                                    onClick = { themeMode = option }
                                )
                            }
                        }

                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = {
                                val chosenStyle = style ?: WidgetStyle.FROSTED_GLASS
                                val chosenMode = themeMode ?: WidgetThemeMode.SYSTEM
                                scope.launch {
                                    WidgetPrefsStore.setStyle(this@WidgetConfigActivity, appWidgetId, chosenStyle)
                                    WidgetPrefsStore.setThemeMode(this@WidgetConfigActivity, appWidgetId, chosenMode)
                                    refreshPlacedWidget(appWidgetId)
                                    setResult(
                                        RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    )
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save widget")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                setResult(RESULT_CANCELED)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    /** Repaints the widget the user just configured; other instances keep their own style. */
    private suspend fun refreshPlacedWidget(appWidgetId: Int) {
        val provider = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)?.provider?.className
        val widget = provider?.let { WidgetCatalog.widgetForReceiver(it) } ?: return
        updateWidget(this, appWidgetId, widget)
    }
}

/**
 * A real swatch of the skin: the widget's own background drawable (gradient for Living Sky,
 * hairline for Transparent) with the resolved text and accent colours on top.
 */
@Composable
private fun StyleOptionCard(
    style: WidgetStyle,
    selected: Boolean,
    isDark: Boolean,
    accent: Long,
    onClick: () -> Unit
) {
    val colors = resolveWidgetColors(style, isDark, PrayerWindow.DHUHR, accent)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(colors.accent) else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(widgetBackgroundDrawable(style, isDark, PrayerWindow.DHUHR)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text(
                text = style.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = Color(colors.primaryText)
            )
            Text(
                text = style.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(colors.secondaryText)
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Swatch(color = Color(colors.background), bordered = true)
                Swatch(color = Color(colors.accent))
                Swatch(color = Color(colors.primaryText))
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (selected) "Selected" else "Tap to choose",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = Color(colors.accent)
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, bordered: Boolean = false) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (bordered) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    Modifier
                }
            )
    )
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        color = content,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
}
