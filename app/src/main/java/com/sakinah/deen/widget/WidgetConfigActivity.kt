package com.sakinah.deen.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            var style by remember { mutableStateOf<WidgetStyle?>(null) }
            var themeMode by remember { mutableStateOf<WidgetThemeMode?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(appWidgetId) {
                style = WidgetPrefsStore.getStyle(this@WidgetConfigActivity, appWidgetId)
                themeMode = WidgetPrefsStore.getThemeMode(this@WidgetConfigActivity, appWidgetId)
            }

            MaterialTheme {
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
                        WidgetStyle.entries.forEach { option ->
                            StyleOptionRow(
                                style = option,
                                selected = style == option,
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

@Composable
private fun StyleOptionRow(style: WidgetStyle, selected: Boolean, onClick: () -> Unit) {
    val colors = resolveWidgetColors(style, isDark = false, window = PrayerWindow.DHUHR)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(colors.background))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(colors.accent) else Color(0x33000000),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (selected) "●" else "○",
            color = Color(colors.accent),
            fontWeight = FontWeight.Bold
        )
    }
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
