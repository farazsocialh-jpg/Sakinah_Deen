package com.sakinah.deen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sakinah.deen.settings.*
import com.sakinah.deen.ui.rememberInject
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repo = rememberInject<SettingsRepository>()
    val settings by repo.settings.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("App Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Prayer & Qibla", style = MaterialTheme.typography.titleMedium)
        Text("Method: ${settings.prayer.calculationMethod}")
        Text("Asr: ${settings.prayer.asrMethod}")
        Text("High latitude: ${settings.prayer.highLatitudeRule}")
        Text("Hijri offset: ${settings.prayer.hijriOffset}")
        Text("Adhan & Notifications", style = MaterialTheme.typography.titleMedium)
        Text("Adhan: ${settings.adhan.adhanChoice}")
        Text("Car Bluetooth auto-play: ${settings.adhan.carBluetoothAutoPlay}")
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Text("Theme: ${settings.appearance.theme}")
        Text("Arabic font size: ${settings.appearance.arabicFontSize}")
        Text("Translation: ${settings.appearance.translationLanguage}")
        Text("Offline storage cache: ${repo.cacheSizeBytes()} bytes")
        OutlinedButton(onClick = { scope.launch { repo.clearCache() } }) { Text("Clear cache") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}