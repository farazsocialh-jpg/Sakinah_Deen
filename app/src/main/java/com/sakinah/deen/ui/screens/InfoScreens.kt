package com.sakinah.deen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DataSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen("Islamic Data Sources & Attribution", "Quran text: Tanzil / King Fahd Complex. Tafsir: Ibn Kathir, As-Sa'di, Al-Jalalayn. Hadith: nine canonical collections. Audio: Makkah adhan recordings.", onBack, modifier)
}

@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen("Help, Feedback & Bug Report", "Contact support and report issues.", onBack, modifier)
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen("Privacy Policy & Terms", "Your data and privacy terms.", onBack, modifier)
}

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen("About Sakinah", "Version 1.0.0", onBack, modifier)
}

@Composable
private fun SimpleInfoScreen(title: String, body: String, onBack: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body)
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}