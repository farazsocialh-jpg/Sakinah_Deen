package com.sakinah.deen.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Composable
fun ToolsHubScreen(
    onNavigate: (SakinahScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val authRepo = rememberInject<AuthRepository>()
    val profileRepo = rememberInject<UserProfileRepository>()
    val authState by authRepo.authState.collectAsStateWithLifecycle()
    val profile by profileRepo.profile.collectAsStateWithLifecycle()
    val signedInUser = (authState as? AuthState.Authenticated)?.user
    val scope = rememberCoroutineScope()

    LaunchedEffect(signedInUser?.id) {
        if (signedInUser != null) {
            profileRepo.loadForUser(signedInUser.id)
        }
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
            // Account Header Card
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
                    StatusBadge(
                        text = if (signedInUser != null) "Profile" else "Sign In",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Worship & Journey
            HubSectionTitle("Worship & Journey")
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.Outlined.AccessibilityNew,
                    title = "Visual Salah Posture Guide",
                    subtitle = "Step-by-step Rakat sequence & recitations",
                    onClick = { onNavigate(SakinahScreen.VisualGuide) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                HubRow(
                    icon = Icons.Outlined.Explore,
                    title = "Safar Travel Mode & Qibla",
                    subtitle = "Home anchor distance, Qasr (2 rakats) & Dua al-Safar",
                    onClick = { onNavigate(SakinahScreen.Travel) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                HubRow(
                    icon = Icons.Outlined.MonetizationOn,
                    title = "Zakat & Sadaqah Jar",
                    subtitle = "Gold/Silver Nisab, PDF summary & monthly charity log",
                    onClick = { onNavigate(SakinahScreen.Wealth) }
                )
            }

            // Preferences & Attribution
            HubSectionTitle("Preferences & Sources")
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.Outlined.Settings,
                    title = "App Settings",
                    subtitle = "Prayer authority, Asr juristic school, theme & cache",
                    onClick = { onNavigate(SakinahScreen.Settings) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                HubRow(
                    icon = Icons.Outlined.MenuBook,
                    title = "Islamic Data Sources",
                    subtitle = "AlAdhan, Quran.com v4, Hadith CDN & offline seeds",
                    onClick = { onNavigate(SakinahScreen.DataSources) }
                )
            }

            // Support & Legal
            HubSectionTitle("Support & Information")
            SakinahCard(contentPadding = 0.dp) {
                HubRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "Help & Feedback",
                    subtitle = "Contact support@sakinah.app or report a bug",
                    onClick = { onNavigate(SakinahScreen.Help) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                HubRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy & Terms",
                    subtitle = "On-device encryption & permissions disclosure",
                    onClick = { onNavigate(SakinahScreen.Privacy) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                HubRow(
                    icon = Icons.Outlined.Info,
                    title = "About Sakinah",
                    subtitle = "Version & build details",
                    onClick = { onNavigate(SakinahScreen.About) }
                )
            }

            if (signedInUser != null) {
                OutlinedButton(
                    onClick = { scope.launch { authRepo.signOut() } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
            Spacer(Modifier.height(8.dp))
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
