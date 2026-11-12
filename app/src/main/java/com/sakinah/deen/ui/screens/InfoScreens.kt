package com.sakinah.deen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DataSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen("Islamic Data Sources & Attribution", "Quran text, tafsir, Hadith collections, and audio credits.", onBack, modifier)
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
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body)
        TextButton(onClick = onBack) { Text("Back") }
    }
}