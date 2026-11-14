package com.sakinah.deen.ui.screens

import android.Manifest
import android.location.Location
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.domain.sensor.QiblaSensorManager
import com.sakinah.deen.domain.travel.TravelStatusManager
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.StatusBadge
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen
import kotlinx.coroutines.launch

@Composable
fun TravelScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appPrefs = rememberInject<SakinahPreferences>()
    val locationProvider = rememberInject<UserLocationProvider>()
    val travelManager = rememberInject<TravelStatusManager>()
    val qiblaManager = rememberInject<QiblaSensorManager>()

    val compassData by qiblaManager.compassFlow.collectAsState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(compassData.isAligned, compassData.isFlat) {
        if (compassData.isAligned && compassData.isFlat) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    var distanceKm by remember { mutableFloatStateOf(0f) }
    val thresholdKm = 88f
    var isSafar by remember { mutableStateOf(false) }
    var homeCoordinates by remember { mutableStateOf(appPrefs.homeCoordinates) }
    var homeCity by remember { mutableStateOf(appPrefs.homeCityName) }
    var showHomeOptions by remember { mutableStateOf(false) }
    var showHomeCityPicker by remember { mutableStateOf(false) }
    var gpsHomeRequested by remember { mutableStateOf(false) }

    var duaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isDuaPlaying by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun saveCurrentGpsAsHome() {
        scope.launch {
            val resolved = locationProvider.getFreshOrCachedLocation()
            val label = listOf(resolved.cityName, resolved.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "Saved Location" }
            appPrefs.saveHomeAnchor(resolved.latitude, resolved.longitude, label)
            homeCoordinates = appPrefs.homeCoordinates
            homeCity = appPrefs.homeCityName
            // A home set from the current position means zero distance and no active concession.
            distanceKm = 0f
            isSafar = false
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

    LaunchedEffect(Unit) {
        qiblaManager.startListening()
        val resolved = locationProvider.getFreshOrCachedLocation()
        val loc = Location("").apply {
            latitude = resolved.latitude
            longitude = resolved.longitude
        }
        qiblaManager.updateLocation(loc)
        val travelState = travelManager.evaluateTravelState(Pair(resolved.latitude, resolved.longitude))
        distanceKm = travelState.distanceKm.toFloat()
        isSafar = travelState.isTraveler
    }
    DisposableEffect(Unit) {
        onDispose {
            qiblaManager.stopListening()
            runCatching {
                duaPlayer?.stop()
                duaPlayer?.release()
            }
            duaPlayer = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Travel Status Header Banner
        SakinahCard(contentPadding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            val homeDisplay = if (homeCoordinates != null) {
                                homeCity ?: "Saved Location"
                            } else {
                                "Not set"
                            }
                            Text(
                                text = "Home Anchor: $homeDisplay",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = homeCoordinates?.let { "(${it.first.toInt()}, ${it.second.toInt()})" }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    StatusBadge(
                        text = if (isSafar) "Safar Active" else "Resident Mode",
                        accentColor = if (isSafar) MintGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${distanceKm.toInt()} km / ${thresholdKm.toInt()} km threshold",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { (distanceKm / thresholdKm).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (isSafar) MintGreen else MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = {
                        distanceKm = if (distanceKm >= 88f) 45f else 95f
                        isSafar = distanceKm >= thresholdKm
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        text = if (isSafar) "Simulate Local Return (<88km)" else "Simulate Travel Departure (>88km)",
                        fontSize = 11.sp
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = { showHomeOptions = true },
            modifier = Modifier.widthIn(min = 240.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Change Home", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        // Qasr & Jam' Prayer Matrix
        SakinahCard(contentPadding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Qasr & Jam‘ Prayer Matrix",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val matrix = if (isSafar) {
                        listOf("Fajr" to "2 R", "Dhuhr" to "2 R", "Asr" to "2 R", "Maghrib" to "3 R", "Isha" to "2 R")
                    } else {
                        listOf("Fajr" to "2 R", "Dhuhr" to "4 R", "Asr" to "4 R", "Maghrib" to "3 R", "Isha" to "4 R")
                    }
                    matrix.forEach { (title, count) ->
                        val activeQasr = isSafar && title != "Fajr" && title != "Maghrib"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (activeQasr) MintGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = count,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Permissible Combining (Jam‘): Dhuhr & Asr can be prayed together during either Dhuhr (Taqdim) or Asr (Ta'khir) time. Maghrib & Isha may likewise be combined.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Qibla Compass
        QiblaCompassCard(compassData)

        if (compassData.isMagneticInterference) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFC107))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF4A3200))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Magnetic interference detected — move away from metal, speakers, or magnetic cases",
                        color = Color(0xFF4A3200),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Bluetooth Dua card (Explicit Deep Emerald Surface)
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
                        Text("Vehicle Audio Ready", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Dua al-Safar", color = MintGreen, fontSize = 10.sp)
                }

                ArabicVerseBlock(
                    arabicText = "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ ، وَإِنَّا إِلَىٰ رَبِّنَا لَمُنْقَلِبُونَ",
                    fontSizeSp = 19,
                    color = Color.White,
                    isQuran = false
                )

                Text(
                    text = "\"Subḥānal-ladhī sakh-khara lanā hādhā wa mā kunnā lahū muqrinīn...\"",
                    color = MintGreen,
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        if (isDuaPlaying) {
                            runCatching {
                                duaPlayer?.stop()
                                duaPlayer?.release()
                            }
                            duaPlayer = null
                            isDuaPlaying = false
                        } else {
                            val resId = context.resources.getIdentifier("dua_al_safar", "raw", context.packageName)
                            if (resId != 0) {
                                runCatching {
                                    duaPlayer?.release()
                                    duaPlayer = MediaPlayer.create(context, resId)?.apply {
                                        setOnCompletionListener { isDuaPlaying = false }
                                        start()
                                    }
                                    isDuaPlaying = duaPlayer != null
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isDuaPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = EmeraldDeep
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDuaPlaying) "Stop Dua al-Safar" else "Play Dua al-Safar via Audio",
                        color = EmeraldDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

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
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = homeCity
                            ?: if (homeCoordinates != null) "Saved location" else "Not set (normal 4 rakat)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            gpsHomeRequested = true
                            showHomeOptions = false
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use Current GPS as Home", fontSize = 12.sp)
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
                                distanceKm = 0f
                                isSafar = false
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
