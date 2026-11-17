package com.sakinah.deen.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.auth.AuthState
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.StatusBadge
import com.sakinah.deen.ui.navigation.SakinahScreen
import com.sakinah.deen.ui.rememberInject

/**
 * The single hub for tools, settings entry points and support pages.
 *
 * Account lifecycle actions (sign out, delete) live only on the profile screen; each Settings
 * section has its own destination so nothing is duplicated here.
 */
@Composable
fun ToolsHubScreen(
    onNavigate: (SakinahScreen) -> Unit,
    modifier: Modifier = Modifier,
    /** Re-opens the onboarding wizard so the tour is always discoverable again. */
    onReplayTour: (() -> Unit)? = null
) {
    val authRepo = rememberInject<AuthRepository>()
    val profileRepo = rememberInject<UserProfileRepository>()
    val authState by authRepo.authState.collectAsStateWithLifecycle()
    val profile by profileRepo.profile.collectAsStateWithLifecycle()
    val signedInUser = (authState as? AuthState.Authenticated)?.user

    var isLoadingProfile by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showWhatsNew by remember { mutableStateOf(false) }

    LaunchedEffect(signedInUser?.id) {
        val user = signedInUser ?: return@LaunchedEffect
        isLoadingProfile = true
        errorMessage = null
        // A failed profile read must not crash the hub; surface it instead.
        runCatching { profileRepo.loadForUser(user.id) }
            .onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) throw err
                errorMessage = err.message ?: "Could not load your saved profile."
            }
        isLoadingProfile = false
    }

    val effectiveName = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: signedInUser?.displayName?.takeIf { it.isNotBlank() }
        ?: "Guest Seeker"

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account card: the only entry point to the profile (or auth when signed out).
            SakinahCard(
                onClick = {
                    onNavigate(if (signedInUser != null) SakinahScreen.Profile else SakinahScreen.Auth)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = effectiveName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = signedInUser?.email?.ifBlank { "Signed in locally" }
                                ?: "Tap to sign in or create an account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isLoadingProfile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        StatusBadge(
                            text = if (signedInUser != null) "Profile" else "Sign In",
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            errorMessage?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            HubSectionTitle("Islamic Tools")
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.Outlined.AccessibilityNew,
                    title = "Visual Salah Posture Guide",
                    subtitle = "Step-by-step Rakat sequence & recitations",
                    onClick = { onNavigate(SakinahScreen.VisualGuide) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.MonetizationOn,
                    title = "Zakat & Sadaqah Jar",
                    subtitle = "Gold/Silver Nisab, PDF summary & monthly charity log",
                    onClick = { onNavigate(SakinahScreen.Wealth) }
                )
            }

            HubSectionTitle("Discover")
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.Outlined.Palette,
                    title = "Replay Quick Tour",
                    subtitle = "Re-run the setup wizard without losing your settings",
                    onClick = { onReplayTour?.invoke() }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Info,
                    title = "What's New",
                    subtitle = "Recent changes in this version",
                    onClick = { showWhatsNew = true }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.DownloadForOffline,
                    title = "Offline Capabilities",
                    subtitle = "Quran text, Adhkar, Hadith and Zakat work without internet; " +
                        "new tafseer, translations and live Nisab rates need a connection",
                    onClick = { onNavigate(SakinahScreen.SettingsQuran) }
                )
            }

            HubSectionTitle("Settings")
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.Outlined.Place,
                    title = "Prayer & Location",
                    subtitle = "Calculation method, home location, Asr school, travel & car",
                    onClick = { onNavigate(SakinahScreen.SettingsPrayer) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Azaan & Notifications",
                    subtitle = "Per-prayer alerts, Fajr Azaan, muezzin voice & test alert",
                    onClick = { onNavigate(SakinahScreen.NotificationSettings) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Palette,
                    title = "Appearance & Reading",
                    subtitle = "Theme, palette, typography & reader layouts",
                    onClick = { onNavigate(SakinahScreen.SettingsAppearance) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.DownloadForOffline,
                    title = "Quran & Offline Packs",
                    subtitle = "Translation edition, tafseer downloads & cache",
                    onClick = { onNavigate(SakinahScreen.SettingsQuran) }
                )
            }

            HubSectionTitle("Support & About")
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "Help & Feedback",
                    subtitle = "Contact support@imancore.app or report a bug",
                    onClick = { onNavigate(SakinahScreen.Help) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.MenuBook,
                    title = "Islamic Data Sources",
                    subtitle = "AlAdhan, Quran.com v4, Hadith CDN & offline seeds",
                    onClick = { onNavigate(SakinahScreen.DataSources) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy & Terms",
                    subtitle = "On-device encryption & permissions disclosure",
                    onClick = { onNavigate(SakinahScreen.Privacy) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Info,
                    title = "About ImanCore",
                    subtitle = "Version & build details",
                    onClick = { onNavigate(SakinahScreen.About) }
                )
            }
        }
    }

    if (showWhatsNew) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showWhatsNew = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("What's new", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    listOf(
                        "Salah times now follow your real location, timezone and high-latitude rule.",
                        "Zakat shows its Nisab source, currency and last update time.",
                        "Hadith cards name their collection, number, grading and edition.",
                        "Qibla, Azaan scheduling and the Quran reader got reliability fixes."
                    ).forEach { line ->
                        Text("•  $line", style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.TextButton(onClick = { showWhatsNew = false }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun HubSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun HubDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
