package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.EmeraldSurface
import com.sakinah.deen.ui.theme.MintGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TravelScreen(
    modifier: Modifier = Modifier
) {
    var distanceKm by remember { mutableFloatStateOf(74f) }
    val thresholdKm = 88f
    val isSafar = distanceKm >= thresholdKm
    var vehicleHeading by remember { mutableFloatStateOf(42f) }
    val kaabaAzimuth = 118f // Kaaba bearing
    val relativeAngle = (kaabaAzimuth - vehicleHeading + 360f) % 360f

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Travel Status Header Banner
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Car",
                            tint = EmeraldDeep,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Home Anchor: London, UK",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = EmeraldDeep
                            )
                            Text(
                                text = "M40 Motorway, Milepost 46",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSafar) MintGreen else Color.LightGray.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = if (isSafar) "Safar Concession Active" else "Local Resident Mode",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSafar) EmeraldDeep else Color.DarkGray
                        )
                    }
                }

                Text(
                    text = "${distanceKm.toInt()} km / ${thresholdKm.toInt()} km threshold",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                LinearProgressIndicator(
                    progress = { (distanceKm / thresholdKm).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (isSafar) MintGreen else EmeraldDeep
                )

                // Toggle simulation
                Button(
                    onClick = { distanceKm = if (distanceKm >= 88f) 45f else 95f },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = if (isSafar) "Simulate Local Return (<88km)" else "Simulate Travel Departure (>88km)",
                        color = EmeraldDeep,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 2. Qasr & Jam' Prayer Timeline
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Qasr & Jam‘ Prayer Matrix",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = EmeraldDeep
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Fajr: 2" to "2 R", "Dhuhr: 4" to "2 R", "Asr: 4" to "2 R", "Maghrib: 3" to "3 R", "Isha: 4" to "2 R").forEach { (title, count) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSafar && title != "Fajr: 2" && title != "Maghrib: 3") MintGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = title.split(":")[0], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                                Text(text = count, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Text(
                    text = "Permissible Combining (Jam‘): Dhuhr & Asr can be prayed together during either Dhuhr (Taqdim) or Asr (Ta'khir) time. Maghrib & Isha may likewise be combined.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // 3. In-Vehicle Qibla Compass (Smooth Canvas Drawing)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Explore, contentDescription = null, tint = EmeraldDeep)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Road Qibla Compass",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = EmeraldDeep
                        )
                    }
                    Text(
                        text = "Kaaba: ${kaabaAzimuth.toInt()}° ESE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintGreen
                    )
                }

                // Compass Dial Canvas
                Canvas(
                    modifier = Modifier
                        .size(180.dp)
                        .padding(10.dp)
                ) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Outer dial circle
                    drawCircle(Color.LightGray.copy(alpha = 0.3f), radius = radius, center = center)
                    drawCircle(Color(0xFF0B3B2C), radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))

                    // Pointer to Kaaba relative to car
                    val angleRad = (relativeAngle - 90f) * (PI / 180f).toFloat()
                    val targetX = center.x + (radius - 20f) * cos(angleRad)
                    val targetY = center.y + (radius - 20f) * sin(angleRad)

                    drawLine(
                        color = Color(0xFF10B981),
                        start = center,
                        end = Offset(targetX, targetY),
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(Color(0xFF0B3B2C), radius = 12f, center = center)
                }

                Text(
                    text = "Steering Heading: ${vehicleHeading.toInt()}° · Kaaba Offset: ${relativeAngle.toInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )

                Slider(
                    value = vehicleHeading,
                    onValueChange = { vehicleHeading = it },
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 4. Bluetooth Dua al-Safar Quick Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = EmeraldDeep)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MintGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bluetooth Connected", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Auto-Play on Drive", color = MintGreen, fontSize = 10.sp)
                }

                Text(
                    text = "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ ، وَإِنَّا إِلَىٰ رَبِّنَا لَمُنْقَلِبُونَ",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "\"Subḥānal-ladhī sakh-khara lanā hādhā wa mā kunnā lahū muqrinīn...\"",
                    color = MintGreen,
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = { /* Play Dua on car speakers */ },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EmeraldDeep)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play Dua al-Safar via Vehicle Audio", color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
