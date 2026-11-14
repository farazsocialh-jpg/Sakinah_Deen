package com.sakinah.deen.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakinah.deen.BuildConfig
import kotlinx.coroutines.launch

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
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showEmailFallback by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        SimpleInfoScreen(
            title = "Help, Feedback & Bug Report",
            onBack = onBack,
            modifier = Modifier.padding(innerPadding)
        ) {
            Text("Contact support and report issues to support@sakinah.app.")
            Button(onClick = {
                val opened = sendFeedbackEmail(context)
                if (!opened) {
                    showEmailFallback = true
                }
            }) {
                Text("Send Feedback / Report a Bug")
            }
        }
    }

    if (showEmailFallback) {
        AlertDialog(
            onDismissRequest = { showEmailFallback = false },
            title = { Text("No Email App Found") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No email client is configured on this device. Send feedback directly to:")
                    SelectionContainer {
                        Text(
                            text = "support@sakinah.app",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString("support@sakinah.app"))
                    showEmailFallback = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Copied support@sakinah.app")
                    }
                }) {
                    Text("Copy Email")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailFallback = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        SimpleInfoScreen(
            title = "Privacy Policy & Terms",
            onBack = onBack,
            modifier = Modifier.padding(innerPadding)
        ) {
            Text("Location: used only to calculate prayer times and Qibla; stored locally on device.")
            Text("Notifications: scheduled locally for prayer reminders; no data is shared.")
            Text("Bluetooth: used only to detect vehicle connection for travel mode; no data is transmitted.")
            Text("AI Scholar queries: sent to the AI proxy only when you ask a question; not stored beyond the session.")
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sakinah.app/privacy"))
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    scope.launch {
                        snackbarHostState.showSnackbar("No browser app found to open https://sakinah.app/privacy")
                    }
                }
            }) { Text("Open Full Privacy Policy") }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen("About Sakinah", onBack, modifier) {
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleInfoScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

private fun sendFeedbackEmail(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:support@sakinah.app")
        putExtra(Intent.EXTRA_SUBJECT, "Sakinah Feedback v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        putExtra(
            Intent.EXTRA_TEXT,
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}"
        )
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
