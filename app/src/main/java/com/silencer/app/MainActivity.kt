package com.silencer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.silencer.app.ui.SettingsScreen
import com.silencer.app.ui.theme.SilencerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SilencerTheme {
                SettingsScreen()
            }
        }
    }
}
