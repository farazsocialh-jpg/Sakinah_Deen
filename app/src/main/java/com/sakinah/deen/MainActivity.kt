package com.sakinah.deen

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sakinah.deen.service.AdhanPlaybackService
import com.sakinah.deen.ui.navigation.RootNavHost
import com.sakinah.deen.ui.theme.SakinahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system splash is dismissed correctly.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SakinahTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app immediately silences any Adhan that is still playing.
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
            // Ignore background-start restrictions; the service may already be stopped.
        }
    }
}
