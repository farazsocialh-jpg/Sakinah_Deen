package com.sakinah.deen.ui.screens

import androidx.core.net.toUri

import com.sakinah.deen.R

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sakinah.deen.BuildConfig
import kotlinx.coroutines.launch

@Composable
fun DataSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen(stringResource(R.string.info_data_sources_title), onBack, modifier) {
        Text("• AlAdhan API & Islamic authorities — prayer times and methods.")
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
            title = stringResource(R.string.info_help_title),
            onBack = onBack,
            modifier = Modifier.padding(innerPadding)
        ) {
            Text(stringResource(R.string.info_help_contact))
            Button(onClick = {
                val opened = sendFeedbackEmail(context)
                if (!opened) {
                    showEmailFallback = true
                }
            }) {
                Text(stringResource(R.string.info_help_send_feedback))
            }
        }
    }

    if (showEmailFallback) {
        AlertDialog(
            onDismissRequest = { showEmailFallback = false },
            title = { Text(stringResource(R.string.info_help_no_email_app)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.info_help_no_email_client))
                    SelectionContainer {
                        Text(
                            text = "support@imancore.app",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString("support@imancore.app"))
                    showEmailFallback = false
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.info_help_copied))
                    }
                }) {
                    Text(stringResource(R.string.info_help_copy_email))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailFallback = false }) {
                    Text(stringResource(R.string.info_close))
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
            title = stringResource(R.string.info_privacy_title),
            onBack = onBack,
            modifier = Modifier.padding(innerPadding)
        ) {
            Text(stringResource(R.string.info_privacy_location))
            Text(stringResource(R.string.info_privacy_notifications))
            Text(stringResource(R.string.info_privacy_bluetooth))
            Text(stringResource(R.string.info_privacy_ai))
            Text(
                stringResource(R.string.info_privacy_on_device)
            )
            // Honesty about a failed decryption: the old file was kept, not deleted.
            com.sakinah.deen.data.database.SakinahEncryptedDatabase.lastRecovery?.let { recovery ->
                Text(
                    text = stringResource(R.string.info_privacy_recovery, recovery.backupPath)
                )
            }
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, "https://imancore.app/privacy".toUri())
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.info_privacy_no_browser))
                    }
                }
            }) { Text(stringResource(R.string.info_privacy_open_full)) }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SimpleInfoScreen(stringResource(R.string.info_about_title), onBack, modifier) {
        Text(stringResource(R.string.info_about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
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
                        contentDescription = stringResource(R.string.info_back_cd)
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
        data = "mailto:support@imancore.app".toUri()
        putExtra(Intent.EXTRA_SUBJECT, "ImanCore Feedback v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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
