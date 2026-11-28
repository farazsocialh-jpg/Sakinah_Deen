package com.sakinah.deen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.receiver.CarBluetoothReceiver
import com.sakinah.deen.service.AdhanPlaybackService
import com.sakinah.deen.service.AzaanScheduling
import com.sakinah.deen.service.PrayerAlertSilencer
import com.sakinah.deen.ui.navigation.RootNavHost
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.SakinahTheme
import com.sakinah.deen.util.enableTapjackingProtection
import com.sakinah.deen.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Screen requested by a notification tap (e.g. the car handshake's Travel prompt). */
    private val pendingNavigation = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(120L)
                .withEndAction { provider.remove() }
                .start()
        }
        enableEdgeToEdge()
        // An overlay must not be able to aim a tap at a destructive or financial control.
        enableTapjackingProtection()
        pendingNavigation.value = navigationTarget(intent)
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
                        // New coordinates mean new timings: refresh every widget.
                        com.sakinah.deen.widget.updateAllWidgets(applicationContext)
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
            // A theme change must repaint every placed widget, not just the app.
            LaunchedEffect(appearanceConfig.themeMode, appearanceConfig.palette) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.sakinah.deen.widget.updateAllWidgets(applicationContext)
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
                // Font scale is Quran-reading scoped; it must never resize the whole app.
                fontScaleMultiplier = 1f
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootNavHost(
                        navigateToExtra = pendingNavigation.value,
                        onNavigateToExtraHandled = { pendingNavigation.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigation.value = navigationTarget(intent)
        // Only an explicit stop-Adhan action/extra may interrupt playback.
        val stopRequested = intent.action == AdhanPlaybackService.ACTION_STOP_ADHAN ||
            intent.getBooleanExtra("stop_adhan", false)
        if (stopRequested) sendStopAdhanIntent()
    }

    /**
     * Only the actions this app actually understands are honoured: an exported activity can
     * receive an Intent from any app, and an unknown target must not steer navigation.
     * Accepts both the legacy `navigate_to` extra and the FCM `route` extra.
     */
    private fun navigationTarget(intent: Intent?): String? {
        val requested = intent?.getStringExtra("route")
            ?: intent?.getStringExtra(CarBluetoothReceiver.EXTRA_NAVIGATE_TO)
            ?: return null
        return requested.takeIf { it in ALLOWED_NOTIFICATION_ROUTES }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Any hardware volume key mutes a playing Azaan. The key is consumed so pressing
        // volume does not also change the notification stream level while the Adhan rings.
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        if (isVolumeKey && AdhanPlaybackService.isAdhanActive) {
            PrayerAlertSilencer.muteAzaan(this)
            return true
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
            Log.w(TAG, "Could not start Azaan playback service to stop it", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        /** Route values an FCM notification is allowed to steer the app to. */
        val ALLOWED_NOTIFICATION_ROUTES: Set<String> = setOf(
            "quran",
            "ai_scholar",
            "scholar",
            "hadith",
            "salah",
            "prayer",
            "travel",
            "tools",
            "wealth",
            com.sakinah.deen.receiver.CarBluetoothReceiver.ACTION_OPEN_TRAVEL
        )
    }
}
