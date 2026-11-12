package com.sakinah.deen.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.sakinah.deen.data.model.CalculationMethod as AppMethod
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.settings.*
import com.sakinah.deen.ui.rememberInject
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repo = rememberInject<SettingsRepository>()
    val settings by repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
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
            val context = LocalContext.current
            val appPrefs = remember { SakinahPreferences(context) }
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
            // Home Location row
            val homeCoords = appPrefs.homeCoordinates
            val homeCity = appPrefs.homeCityName
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Home Location", modifier = Modifier.weight(1f))
                if (homeCoords != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MintGreen.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "${homeCity ?: "Saved"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldDeep
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        }),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Use Current GPS")
                    }
                }
                OutlinedButton(
                    onClick = { showManualCityDialog = true },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text("Choose City")
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
}

/** Mirrors an Android override into the shared settings store (closest equivalent authority). */
private fun sharedMethodFor(method: AppMethod): CalculationMethod? = when (method) {
    AppMethod.UMM_AL_QURA, AppMethod.QATAR, AppMethod.DUBAI -> CalculationMethod.UMM_AL_QURA
    AppMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN
    AppMethod.KARACHI -> CalculationMethod.KARACHI
    AppMethod.NORTH_AMERICA -> CalculationMethod.ISNA
    AppMethod.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MWL
}