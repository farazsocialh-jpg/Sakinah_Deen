package com.sakinah.deen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sakinah.deen.data.model.PrayerModel
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.EmeraldSurface
import com.sakinah.deen.ui.theme.MintGreen

@Composable
fun SalahScreen(
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var prayers by remember {
        mutableStateOf(
            listOf(
                PrayerModel("fajr", "Fajr", "الفجر", "05:14 AM", 2, 2, 0, true, true, true),
                PrayerModel("sunrise", "Sunrise", "الشروق", "06:38 AM", 0, 0, 0, false, false, false),
                PrayerModel("dhuhr", "Dhuhr", "الظهر", "12:45 PM", 4, 4, 2, true, true, true, true, "2 Rakat Nafl"),
                PrayerModel("asr", "Asr", "العصر", "03:52 PM", 4, 4, 0, false, false, false),
                PrayerModel("maghrib", "Maghrib", "المغرب", "06:18 PM", 3, 0, 2, false, false, false, false, "Awwabin (6 Rakat)"),
                PrayerModel("isha", "Isha", "العشاء", "07:44 PM", 4, 4, 2, false, false, false, false, "Tahajjud & Witr")
            )
        )
    }

    var showQadhaDialog by remember { mutableStateOf(false) }
    var qadhaFajr by remember { mutableIntStateOf(12) }
    var qadhaDhuhr by remember { mutableIntStateOf(8) }
    var qadhaAsr by remember { mutableIntStateOf(14) }
    var qadhaMaghrib by remember { mutableIntStateOf(3) }
    var qadhaIsha by remember { mutableIntStateOf(9) }
    var qadhaWitr by remember { mutableIntStateOf(12) }
    var expandedId by remember { mutableStateOf<String?>("asr") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Bar & Dynamic Date Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "14 Jumada al-Ula 1448 AH",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EmeraldDeep
                )
                Text(
                    text = "Tuesday · 11 November 2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Quick Mode Switcher pill
            Button(
                onClick = { showQadhaDialog = true },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintGreen.copy(alpha = 0.15f),
                    contentColor = EmeraldDeep
                )
            ) {
                Text("Qadha Ledger", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }

        // 2. Hero Active Prayer Card (Deep Emerald Gradient)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(EmeraldDeep, EmeraldSurface)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENT / NEXT PRAYER",
                            color = MintGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        // Live countdown timer chip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = MintGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Starts in 01h 24m",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "ASR · العصر",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Button(
                            onClick = onOpenGuide,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Visual Guide", fontSize = 11.sp)
                        }
                    }

                    // Progress bar showing elapsed window between Dhuhr & Asr
                    LinearProgressIndicator(
                        progress = { 0.65f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MintGreen,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { /* Play Adhan motif */ },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hear Adhan", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Daily Prayer Matrix (Vertical List of Cards)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(prayers) { prayer ->
                val isExpanded = expandedId == prayer.id
                val isSunrise = prayer.id == "sunrise"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Details
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        expandedId = if (isExpanded) null else prayer.id
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = prayer.nameEnglish,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = EmeraldDeep
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = prayer.nameArabic,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                Text(
                                    text = "${prayer.time} · ${if (isSunrise) "Ishraq" else "${prayer.fardRakats} Rakat Fard"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            // Center Action Icons (Fard Check & Jama'ah Toggle)
                            if (!isSunrise) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Jama'ah Mosque Toggle
                                    IconButton(
                                        onClick = {
                                            prayers = prayers.map {
                                                if (it.id == prayer.id) it.copy(isJamaah = !it.isJamaah) else it
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mosque,
                                            contentDescription = "Jama'ah toggle",
                                            tint = if (prayer.isJamaah) EmeraldDeep else Color.LightGray
                                        )
                                    }

                                    // Fard Checkbox Button
                                    Button(
                                        onClick = {
                                            prayers = prayers.map {
                                                if (it.id == prayer.id) it.copy(isFardCompleted = !it.isFardCompleted) else it
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (prayer.isFardCompleted) MintGreen else Color.LightGray.copy(alpha = 0.4f),
                                            contentColor = if (prayer.isFardCompleted) Color.White else EmeraldDeep
                                        ),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        if (prayer.isFardCompleted) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { expandedId = if (isExpanded) null else prayer.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                        }

                        // Expandable Bottom Row: Sunan Mu'akkadah & Nawafil
                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Sunnah & Nawafil Prayers:",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = EmeraldDeep
                                )
                                if (prayer.sunnahBefore > 0 || prayer.sunnahAfter > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${prayer.sunnahBefore + prayer.sunnahAfter} Rakat Sunnah Mu'akkadah",
                                            fontSize = 12.sp
                                        )
                                        Button(
                                            onClick = {
                                                prayers = prayers.map {
                                                    if (it.id == prayer.id) it.copy(isSunnahCompleted = !it.isSunnahCompleted) else it
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (prayer.isSunnahCompleted) MintGreen else Color.LightGray.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Text(if (prayer.isSunnahCompleted) "Completed" else "Mark Done", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Qadha al-Umri Ledger Modal / Bottom Sheet
    if (showQadhaDialog) {
        Dialog(onDismissRequest = { showQadhaDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Qadha al-Umri Ledger",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = EmeraldDeep
                        )
                        IconButton(onClick = { showQadhaDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Total Missed & Pace calculation
                    val totalMissed = qadhaFajr + qadhaDhuhr + qadhaAsr + qadhaMaghrib + qadhaIsha + qadhaWitr
                    Text(
                        text = "Total Missed Prayers: $totalMissed · Pace: 2/day (${totalMissed / 2} days remaining)",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Medium
                    )

                    // Stepper rows for missed prayers
                    QadhaRow("Fajr", qadhaFajr, onDecrement = { if (qadhaFajr > 0) qadhaFajr-- })
                    QadhaRow("Dhuhr", qadhaDhuhr, onDecrement = { if (qadhaDhuhr > 0) qadhaDhuhr-- })
                    QadhaRow("Asr", qadhaAsr, onDecrement = { if (qadhaAsr > 0) qadhaAsr-- })
                    QadhaRow("Maghrib", qadhaMaghrib, onDecrement = { if (qadhaMaghrib > 0) qadhaMaghrib-- })
                    QadhaRow("Isha", qadhaIsha, onDecrement = { if (qadhaIsha > 0) qadhaIsha-- })
                    QadhaRow("Witr", qadhaWitr, onDecrement = { if (qadhaWitr > 0) qadhaWitr-- })

                    Button(
                        onClick = { showQadhaDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep)
                    ) {
                        Text("Save & Close", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun QadhaRow(
    title: String,
    count: Int,
    onDecrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$title ($count missed)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Button(
            onClick = onDecrement,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
            enabled = count > 0
        ) {
            Text("+1 Prayed", color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}
