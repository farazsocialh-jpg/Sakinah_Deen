package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen

enum class JourneyTrack {
    NEW_TO_ISLAM,
    ISTIQAMAH
}

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTrack by remember { mutableStateOf(JourneyTrack.NEW_TO_ISLAM) }
    var isHanafiAsr by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Section
        Text(
            text = "Sakinah",
            fontFamily = FontFamily.Serif,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldDeep
        )
        Text(
            text = "Your Sunni Deen Companion · Step 1 of 3",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        // 2. Interactive Track Selection Cards
        // Track Card A
        TrackCardItem(
            title = "New to Islam & Foundational Basics",
            subtitle = "Interactive visual Salah guides, Wudu walk-throughs, clear Aqeedah basics, and gradual habit building without overwhelm.",
            icon = Icons.Outlined.Eco,
            isSelected = selectedTrack == JourneyTrack.NEW_TO_ISLAM,
            onClick = { selectedTrack = JourneyTrack.NEW_TO_ISLAM }
        )

        // Track Card B
        TrackCardItem(
            title = "Al-Istiqamah: Complete Practice & Study",
            subtitle = "Advanced Salah & Qadha matrix, 9 Canonical Hadith books, multi-Tafseer split screen, and Nisab/Zakat calculator.",
            icon = Icons.Outlined.MenuBook,
            isSelected = selectedTrack == JourneyTrack.ISTIQAMAH,
            onClick = { selectedTrack = JourneyTrack.ISTIQAMAH }
        )

        // 3. Madhhab & Calculation Settings Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Settings",
                        tint = EmeraldDeep,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Calculation & Juristic Settings",
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldDeep
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Asr Juristic Method",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isHanafiAsr) "Hanafi (Double Shadow 2:1)" else "Standard: Shafi'i, Maliki, Hanbali (1:1)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isHanafiAsr,
                        onCheckedChange = { isHanafiAsr = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Bottom Action Button
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
        ) {
            Text(
                text = "Continue to Daily Home",
                color = EmeraldDeep,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun TrackCardItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MintGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MintGreen else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) EmeraldDeep else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldDeep
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}
