package com.sakinah.deen.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakinah.deen.BuildConfig

@Composable
fun DataSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen("Islamic Data Sources & Attribution", onBack, modifier) {
        Text("• AlAdhan API & Sunni authorities — prayer times and methods.")
        Text("• Quran.com v4 API — Quran text, translations, audio.")
        Text("• fawazahmed0/hadith-api — Kutub al-Tis'ah hadith collections.")
        Text("• Juz Amma / Al-Fatihah, the 40 Nawawi hadith, and Hisn al-Muslim adhkar — offline seeds.")
        Text("• Amiri & Uthmanic Hafs fonts — Arabic typography.")
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SimpleInfoScreen("Help, Feedback & Bug Report", onBack, modifier) {
        Text("Contact support and report issues.")
        Button(onClick = { sendFeedbackEmail(context) }) {
            Text("Send Feedback / Report a Bug")
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SimpleInfoScreen("Privacy Policy & Terms", onBack, modifier) {
        Text("Location: used only to calculate prayer times and Qibla; stored locally on device.")
        Text("Notifications: scheduled locally for prayer reminders; no data is shared.")
        Text("Bluetooth: used only to detect vehicle connection for travel mode; no data is transmitted.")
        Text("AI Scholar queries: sent to the AI proxy only when you ask a question; not stored beyond the session.")
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sakinah.app/privacy"))
            context.startActivity(intent)
        }) { Text("Open Full Privacy Policy") }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen("About Sakinah", onBack, modifier) {
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}

@Composable
private fun SimpleInfoScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            content()
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

private fun sendFeedbackEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:support@sakinah.app")
        putExtra(Intent.EXTRA_SUBJECT, "Sakinah Feedback v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        putExtra(Intent.EXTRA_TEXT, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\\nAndroid: ${android.os.Build.VERSION.RELEASE}")
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No email client installed; ignore.
    }
}


