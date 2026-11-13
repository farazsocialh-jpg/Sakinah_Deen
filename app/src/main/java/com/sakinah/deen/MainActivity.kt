package com.sakinah.deen

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.service.AdhanPlaybackService
import com.sakinah.deen.ui.navigation.RootNavHost
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.SakinahTheme
import com.sakinah.deen.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearancePrefs = rememberInject<AppearancePreferences>()
            val appearanceConfig by appearancePrefs.effectiveConfig.collectAsStateWithLifecycle()
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

    override fun onResume() {
        super.onResume()
        sendStopAdhanIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sendStopAdhanIntent()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
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
