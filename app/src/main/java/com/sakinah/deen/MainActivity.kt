package com.sakinah.deen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.service.AdhanPlaybackService
import com.sakinah.deen.service.AzaanScheduling
import com.sakinah.deen.ui.navigation.RootNavHost
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.SakinahTheme
import com.sakinah.deen.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearancePrefs = rememberInject<AppearancePreferences>()
            val appearanceConfig by appearancePrefs.effectiveConfig.collectAsStateWithLifecycle()
            val appPrefs = rememberInject<SakinahPreferences>()
            val locationProvider = rememberInject<UserLocationProvider>()
            val scope = rememberCoroutineScope()

            val firstLaunchPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                // Mark the prompt as handled only after the user has actually responded.
                appPrefs.firstLaunchPermissionsRequested = true
                val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (locationGranted) {
                    scope.launch {
                        AzaanScheduling.scheduleWithBestLocation(
                            context = applicationContext,
                            locationProvider = locationProvider
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (!appPrefs.firstLaunchPermissionsRequested) {
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    firstLaunchPermissionLauncher.launch(permissions.toTypedArray())
                }
            }
            val darkTheme = when (appearanceConfig.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(darkTheme) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    }
                )
            }

            SakinahTheme(
                darkTheme = darkTheme,
                palette = appearanceConfig.palette,
                fontScaleMultiplier = appearanceConfig.fontScale.multiplier
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Only an explicit stop-Adhan action/extra may interrupt playback.
        val stopRequested = intent.action == AdhanPlaybackService.ACTION_STOP_ADHAN ||
            intent.getBooleanExtra("stop_adhan", false)
        if (stopRequested) sendStopAdhanIntent()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            AdhanPlaybackService.isAdhanActive
        ) {
            sendStopAdhanIntent()
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun sendStopAdhanIntent() {
        val intent = Intent(this, AdhanPlaybackService::class.java).apply {
            action = AdhanPlaybackService.ACTION_STOP_ADHAN
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }
}
