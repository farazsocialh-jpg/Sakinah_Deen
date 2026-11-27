package com.sakinah.deen.ui.screens

import com.sakinah.deen.R

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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
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
    val context = LocalContext.current
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
                errorMessage = err.message ?: context.getString(R.string.tools_load_profile_failed)
            }
        isLoadingProfile = false
    }

    val effectiveName = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: signedInUser?.displayName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.tools_guest_seeker)

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
                            text = signedInUser?.email?.ifBlank { stringResource(R.string.tools_signed_in_locally) }
                                ?: stringResource(R.string.tools_tap_to_sign_in),
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
                            text = if (signedInUser != null) stringResource(R.string.tools_status_profile) else stringResource(R.string.tools_status_sign_in),
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

            HubSectionTitle(stringResource(R.string.tools_section_islamic))
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.Outlined.AccessibilityNew,
                    title = stringResource(R.string.tools_visual_salat_title),
                    subtitle = stringResource(R.string.tools_visual_salat_subtitle),
                    onClick = { onNavigate(SakinahScreen.VisualGuide) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Place,
                    title = "Qibla Compass",
                    subtitle = "Find the direction of the Kaaba",
                    onClick = { onNavigate(SakinahScreen.Qibla) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.MonetizationOn,
                    title = stringResource(R.string.tools_zakat_title),
                    subtitle = stringResource(R.string.tools_zakat_subtitle),
                    onClick = { onNavigate(SakinahScreen.Wealth) }
                )
            }

            HubSectionTitle(stringResource(R.string.tools_section_settings))
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.Outlined.Place,
                    title = stringResource(R.string.tools_prayer_location_title),
                    subtitle = stringResource(R.string.tools_prayer_location_subtitle),
                    onClick = { onNavigate(SakinahScreen.SettingsPrayer) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.tools_prayer_notifications_title),
                    subtitle = stringResource(R.string.tools_prayer_notifications_subtitle),
                    onClick = { onNavigate(SakinahScreen.NotificationSettings) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.tools_appearance_title),
                    subtitle = stringResource(R.string.tools_appearance_subtitle),
                    onClick = { onNavigate(SakinahScreen.SettingsAppearance) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.DownloadForOffline,
                    title = stringResource(R.string.tools_quran_offline_title),
                    subtitle = stringResource(R.string.tools_quran_offline_subtitle),
                    onClick = { onNavigate(SakinahScreen.SettingsQuran) }
                )
            }

            HubSectionTitle(stringResource(R.string.tools_section_discover))
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.tools_replay_tour_title),
                    subtitle = stringResource(R.string.tools_replay_tour_subtitle),
                    onClick = { onReplayTour?.invoke() }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.tools_whats_new_title),
                    subtitle = stringResource(R.string.tools_whats_new_subtitle),
                    onClick = { showWhatsNew = true }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.DownloadForOffline,
                    title = stringResource(R.string.tools_offline_title),
                    subtitle = stringResource(R.string.tools_offline_subtitle),
                    onClick = { onNavigate(SakinahScreen.SettingsQuran) }
                )
            }

            HubSectionTitle(stringResource(R.string.tools_section_support))
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = stringResource(R.string.tools_help_title),
                    subtitle = stringResource(R.string.tools_help_subtitle),
                    onClick = { onNavigate(SakinahScreen.Help) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = stringResource(R.string.tools_data_sources_title),
                    subtitle = stringResource(R.string.tools_data_sources_subtitle),
                    onClick = { onNavigate(SakinahScreen.DataSources) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = stringResource(R.string.tools_privacy_title),
                    subtitle = stringResource(R.string.tools_privacy_subtitle),
                    onClick = { onNavigate(SakinahScreen.Privacy) }
                )
                HubDivider()
                HubRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.tools_about_title),
                    subtitle = stringResource(R.string.tools_about_subtitle),
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
                    Text(stringResource(R.string.tools_whats_new_dialog), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    listOf(
                        stringResource(R.string.tools_whats_new_1),
                        stringResource(R.string.tools_whats_new_2),
                        stringResource(R.string.tools_whats_new_3),
                        stringResource(R.string.tools_whats_new_4)
                    ).forEach { line ->
                        Text("•  $line", style = MaterialTheme.typography.bodySmall)
                    }
                                            androidx.compose.material3.TextButton(onClick = { showWhatsNew = false }) {
                        Text(stringResource(R.string.tools_close))
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
