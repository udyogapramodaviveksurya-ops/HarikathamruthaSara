package com.example.harikathamruthasara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.harikathamruthasara.ui.App
import com.example.harikathamruthasara.ui.HkTheme
import com.example.harikathamruthasara.ui.isAppDark
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** the default scrims androidx uses behind 3-button navigation */
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // keep the logo up until the verses are loaded (usually well under a second)
        splash.setKeepOnScreenCondition { (application as HkApp).library.value == null }
        enableEdgeToEdge()
        setContent {
            val app = application as HkApp
            val settings by app.settings.flow.collectAsStateWithLifecycle()
            val dark = isAppDark(settings.darkMode)
            // status/navigation bar icons follow the app's own light/dark choice, not only the phone's
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            HkTheme(settings.colorStyle, settings.darkMode) { App(app) }
        }
    }

    /** Audio plays only while the app is on screen (user's choice): locking the phone or leaving the app stops it. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as HkApp).player.stopAndClose()
    }

    override fun onResume() {
        super.onResume()
        (application as HkApp).source.check()   // improvements on the source-of-truth server (throttled)
    }
}
