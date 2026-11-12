package com.sakinah.deen.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.EmeraldSurface
import com.sakinah.deen.ui.theme.MintGreen
import kotlinx.coroutines.launch
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

    // Saved Home Location is the source of truth for the Qasr distance check.
    val appPrefs = rememberInject<SakinahPreferences>()
    val locationProvider = rememberInject<UserLocationProvider>()
    val scope = rememberCoroutineScope()
    var homeCoordinates by remember { mutableStateOf(appPrefs.homeCoordinates) }
    var homeCity by remember { mutableStateOf(appPrefs.homeCityName) }
    var showHomeOptions by remember { mutableStateOf(false) }
    var showHomeCityPicker by remember { mutableStateOf(false) }
    var gpsHomeRequested by remember { mutableStateOf(false) }

    fun saveCurrentGpsAsHome() {
        scope.launch {
            val resolved = locationProvider.getFreshOrCachedLocation()
            val label = listOf(resolved.cityName, resolved.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "Saved Location" }
            // Invalid fixes are ignored and any stale Travel Mode is cleared.
            appPrefs.saveHomeAnchor(resolved.latitude, resolved.longitude, label)
            homeCoordinates = appPrefs.homeCoordinates
            homeCity = appPrefs.homeCityName
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isGranted && gpsHomeRequested) saveCurrentGpsAsHome()
        gpsHomeRequested = false
    }
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
                            // Show saved home city or "Not set"
                            val homeDisplay = if (homeCoordinates != null) {
                                homeCity ?: "Saved Location"
                            } else {
                                "Not set"
                            }
                            Text(
                                text = "Home Anchor: ${homeDisplay}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = EmeraldDeep
                            )
                            Text(
                                text = homeCoordinates?.let { "(${it.first.toInt()}, ${it.second.toInt()})" } ?: "",
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

        // Change Home opens the same GPS / city picker options as Settings.
        FilledTonalButton(
            onClick = { showHomeOptions = true },
            modifier = Modifier.widthIn(280.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Change Home", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(18.dp))
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


    // Home Location options — same GPS / city picker as Settings.
    if (showHomeOptions) {
        Dialog(onDismissRequest = { showHomeOptions = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Home Location",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = EmeraldDeep
                    )
                    Text(
                        text = homeCity
                            ?: if (homeCoordinates != null) "Saved location" else "Not set (normal 4 rakat)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Button(
                        onClick = {
                            gpsHomeRequested = true
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use Current GPS as Home", color = Color.White, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            showHomeOptions = false
                            showHomeCityPicker = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose Home City", fontSize = 12.sp)
                    }
                    if (homeCoordinates != null) {
                        TextButton(
                            onClick = {
                                appPrefs.clearHomeAnchor()
                                homeCoordinates = null
                                homeCity = null
                                showHomeOptions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Home Location", fontSize = 12.sp)
                        }
                    }
                    TextButton(
                        onClick = { showHomeOptions = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showHomeCityPicker) {
        HomeLocationCityPickerDialog(
            onDismiss = { showHomeCityPicker = false },
            onCitySelected = { city ->
                appPrefs.saveHomeAnchor(city.latitude, city.longitude, city.name)
                homeCoordinates = appPrefs.homeCoordinates
                homeCity = appPrefs.homeCityName
                showHomeCityPicker = false
            }
        )
    }

}
