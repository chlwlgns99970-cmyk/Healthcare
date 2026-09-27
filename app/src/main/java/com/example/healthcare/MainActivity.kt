package com.example.healthcare

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.healthcare.ui.HealthcareApp
import com.example.healthcare.ui.theme.HealthCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            val appFontSize by (application as HealthcareApplication)
                .appFontSizeStore.fontSize.collectAsState()
            // The product opens in the requested bright palette; the dark scheme remains
            // available for explicit previews and accessibility regression tests.
            HealthCareTheme(darkTheme = false, appFontScale = appFontSize.scale) {
                HealthcareApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as HealthcareApplication).appUpdateManager.onHostResumed(this)
    }
}
