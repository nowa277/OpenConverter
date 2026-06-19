package com.openconverter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.openconverter.app.ui.history.HistoryScreen
import com.openconverter.app.ui.home.HomeScreen
import com.openconverter.app.ui.home.HomeViewModel
import com.openconverter.app.ui.settings.SettingsScreen
import com.openconverter.app.ui.theme.OpenConverterTheme

class MainActivity : ComponentActivity() {
    private val homeVm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenConverterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var screen by remember { mutableStateOf("home") }
                    when (screen) {
                        "home" -> HomeScreen(
                            viewModel = homeVm,
                            onOpenSettings = { screen = "settings" },
                            onOpenHistory = { screen = "history" },
                        )
                        "settings" -> SettingsScreen(onBack = { screen = "home" })
                        "history" -> HistoryScreen(onBack = { screen = "home" })
                    }
                }
            }
        }
    }
}