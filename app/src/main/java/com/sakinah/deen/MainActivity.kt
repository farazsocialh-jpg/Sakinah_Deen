package com.sakinah.deen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sakinah.deen.ui.navigation.RootNavHost
import com.sakinah.deen.ui.theme.SakinahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
}
