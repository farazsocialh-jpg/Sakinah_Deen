package com.sakinah.deen.ui.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.ResolvedLocation
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import kotlinx.coroutines.launch

/**
 * Returns a callback that stores the current GPS fix as the shared Home anchor, requesting the
 * location permission first. Settings and Travel used to carry their own copy of this flow.
 *
 * [onSaved] runs after the anchor is written so each screen can refresh the state it shows.
 */
@Composable
fun rememberGpsHomeSaver(
    locationProvider: UserLocationProvider,
    preferences: SakinahPreferences,
    fallbackLabel: String,
    onSaved: (ResolvedLocation) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(false) }

    fun saveCurrentLocation() {
        scope.launch {
            val resolved = locationProvider.getFreshOrCachedLocation()
            val label = listOf(resolved.cityName, resolved.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { fallbackLabel }
            preferences.saveHomeAnchor(resolved.latitude, resolved.longitude, label)
            onSaved(resolved)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isGranted && pending) saveCurrentLocation()
        pending = false
    }

    return {
        pending = true
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}
