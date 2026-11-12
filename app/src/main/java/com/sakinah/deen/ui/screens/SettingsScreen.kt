package com.sakinah.deen.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sakinah.deen.data.model.CalculationMethod as AppMethod
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.settings.*
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repo = rememberInject<SettingsRepository>()
    val settings by repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appPrefs = remember { SakinahPreferences(context) }
    val locationProvider = rememberInject<UserLocationProvider>()

    // Mirrored into local state so the Home Location card refreshes right after a save.
    var hasHomeLocation by remember { mutableStateOf(appPrefs.homeCoordinates != null) }
    var homeCityName by remember { mutableStateOf(appPrefs.homeCityName) }
    var showHomeCityPicker by remember { mutableStateOf(false) }
    var gpsHomeRequested by remember { mutableStateOf(false) }

    fun saveCurrentGpsAsHome() {
        scope.launch {
            val resolved = locationProvider.getFreshOrCachedLocation()
            val label = listOf(resolved.cityName, resolved.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "Saved Location" }
            // saveHomeAnchor ignores invalid fixes and clears any stale Travel Mode.
            appPrefs.saveHomeAnchor(resolved.latitude, resolved.longitude, label)
            hasHomeLocation = appPrefs.homeCoordinates != null
            homeCityName = appPrefs.homeCityName
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

    val themes = ThemeMode.entries
    val adhanChoices = AdhanChoice.entries

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("App Settings", style = MaterialTheme.typography.headlineSmall)

            // Prayer & Qibla
            Text("Prayer & Qibla", style = MaterialTheme.typography.titleMedium)
            var calcExpanded by remember { mutableStateOf(false) }
            // Android resolves the country's Sunni authority (Umm Al-Qura, Qatar Awqaf, ...) by
            // default; a choice here is an explicit override that also drives online timings.
            var methodOverride by remember { mutableStateOf(appPrefs.calculationMethodOverride) }
            ExposedDropdownMenuBox(expanded = calcExpanded, onExpandedChange = { calcExpanded = it }) {
                OutlinedTextField(
                    value = methodOverride?.name?.replace('_', ' ') ?: "Automatic (by country)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Calculation method") },
                    supportingText = { Text("Automatic uses your country's Sunni authority.") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calcExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = calcExpanded, onDismissRequest = { calcExpanded = false }) {
                    DropdownMenuItem(text = { Text("Automatic (by country)") }, onClick = {
                        appPrefs.calculationMethodOverride = null
                        methodOverride = null
                        calcExpanded = false
                    })
                    AppMethod.entries.forEach { m ->
                        DropdownMenuItem(text = { Text(m.name.replace('_', ' ')) }, onClick = {
                            appPrefs.calculationMethodOverride = m
                            methodOverride = m
                            sharedMethodFor(m)?.let { shared ->
                                scope.launch { repo.updatePrayer(settings.prayer.copy(calculationMethod = shared)) }
                            }
                            calcExpanded = false
                        })
                    }
                }
            }
            // Home Location — the anchor Safar (Qasr) is measured from.
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Home Location", fontWeight = FontWeight.Bold, color = EmeraldDeep)
                    Text(
                        text = homeCityName
                            ?: if (hasHomeLocation) "Saved location" else "Not set (normal 4 rakat)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                gpsHomeRequested = true
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Use Current GPS as Home", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { showHomeCityPicker = true },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Choose Home City", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = {
                                appPrefs.clearHomeAnchor()
                                hasHomeLocation = false
                                homeCityName = null
                            },
                            enabled = hasHomeLocation
                        ) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Asr: Shafi'i", modifier = Modifier.weight(1f))
                Switch(checked = settings.prayer.asrMethod == AsrMethod.HANAFI, onCheckedChange = { hanafi ->
                    scope.launch { repo.updatePrayer(settings.prayer.copy(asrMethod = if (hanafi) AsrMethod.HANAFI else AsrMethod.SHAFI)) }
                })
                Text(if (settings.prayer.asrMethod == AsrMethod.HANAFI) "Hanafi" else "Shafi'i")
            }
            Text("High latitude: ${settings.prayer.highLatitudeRule}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hijri offset: ${settings.prayer.hijriOffset}")
                Slider(
                    value = settings.prayer.hijriOffset.toFloat(),
                    onValueChange = { v ->
                        scope.launch { repo.updatePrayer(settings.prayer.copy(hijriOffset = v.toInt())) }
                    },
                    valueRange = -2f..2f,
                    steps = 3,
                    modifier = Modifier.weight(1f)
                )
            }

            // Adhan & Notifications
            Text("Adhan & Notifications", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fajr", modifier = Modifier.weight(1f))
                Switch(checked = settings.adhan.fajrEnabled, onCheckedChange = { v ->
                    scope.launch { repo.updateAdhan(settings.adhan.copy(fajrEnabled = v)) }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dhuhr", modifier = Modifier.weight(1f))
                Switch(checked = settings.adhan.dhuhrEnabled, onCheckedChange = { v ->
                    scope.launch { repo.updateAdhan(settings.adhan.copy(dhuhrEnabled = v)) }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Asr", modifier = Modifier.weight(1f))
                Switch(checked = settings.adhan.asrEnabled, onCheckedChange = { v ->
                    scope.launch { repo.updateAdhan(settings.adhan.copy(asrEnabled = v)) }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Maghrib", modifier = Modifier.weight(1f))
                Switch(checked = settings.adhan.maghribEnabled, onCheckedChange = { v ->
                    scope.launch { repo.updateAdhan(settings.adhan.copy(maghribEnabled = v)) }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Isha", modifier = Modifier.weight(1f))
                Switch(checked = settings.adhan.ishaEnabled, onCheckedChange = { v ->
                    scope.launch { repo.updateAdhan(settings.adhan.copy(ishaEnabled = v)) }
                })
            }
            var adhanExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = adhanExpanded, onExpandedChange = { adhanExpanded = it }) {
                OutlinedTextField(
                    value = settings.adhan.adhanChoice.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Adhan sound") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = adhanExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = adhanExpanded, onDismissRequest = { adhanExpanded = false }) {
                    adhanChoices.forEach { a ->
                        DropdownMenuItem(text = { Text(a.name) }, onClick = {
                            scope.launch { repo.updateAdhan(settings.adhan.copy(adhanChoice = a)) }
                            adhanExpanded = false
                        })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Car Bluetooth Dua al-Safar auto-play", modifier = Modifier.weight(1f))
                Switch(checked = settings.adhan.carBluetoothAutoPlay, onCheckedChange = { v ->
                    scope.launch { repo.updateAdhan(settings.adhan.copy(carBluetoothAutoPlay = v)) }
                })
            }

            // Appearance
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            var themeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = it }) {
                OutlinedTextField(
                    value = settings.appearance.theme.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                    themes.forEach { t ->
                        DropdownMenuItem(text = { Text(t.name) }, onClick = {
                            scope.launch { repo.updateAppearance(settings.appearance.copy(theme = t)) }
                            themeExpanded = false
                        })
                    }
                }
            }
            Text("Arabic font size: ${settings.appearance.arabicFontSize}")
            Slider(
                value = settings.appearance.arabicFontSize.toFloat(),
                onValueChange = { v ->
                    scope.launch { repo.updateAppearance(settings.appearance.copy(arabicFontSize = v.toInt())) }
                },
                valueRange = 16f..40f
            )
            OutlinedTextField(
                value = settings.appearance.translationLanguage,
                onValueChange = { v -> scope.launch { repo.updateAppearance(settings.appearance.copy(translationLanguage = v)) } },
                label = { Text("Translation language") },
                modifier = Modifier.fillMaxWidth()
            )

            // Offline storage
            Text("Offline Storage", style = MaterialTheme.typography.titleMedium)
            Text("Cache size: ${repo.cacheSizeBytes()} bytes")
            Button(onClick = { scope.launch { repo.clearCache() } }) { Text("Clear cache") }

            TextButton(onClick = onBack) { Text("Back") }
        }
    }

    // Searchable Home City picker (shared with the Travel screen).
    if (showHomeCityPicker) {
        HomeLocationCityPickerDialog(
            onDismiss = { showHomeCityPicker = false },
            onCitySelected = { city ->
                appPrefs.saveHomeAnchor(city.latitude, city.longitude, city.name)
                homeCityName = appPrefs.homeCityName
                hasHomeLocation = appPrefs.homeCoordinates != null
                showHomeCityPicker = false
            }
        )
    }
}


/** Mirrors an Android override into the shared settings store (closest equivalent authority). */
private fun sharedMethodFor(method: AppMethod): CalculationMethod? = when (method) {
    AppMethod.UMM_AL_QURA, AppMethod.QATAR, AppMethod.DUBAI -> CalculationMethod.UMM_AL_QURA
    AppMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN
    AppMethod.KARACHI -> CalculationMethod.KARACHI
    AppMethod.NORTH_AMERICA -> CalculationMethod.ISNA
    AppMethod.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MWL
}