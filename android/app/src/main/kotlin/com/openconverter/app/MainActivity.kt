package com.openconverter.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
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

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "system") ?: "system"
        val locale = when (lang) {
            "zh" -> java.util.Locale.CHINESE
            "en" -> java.util.Locale.ENGLISH
            else -> java.util.Locale.getDefault()
        }
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("prefs", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
            var languageMode by remember { mutableStateOf(prefs.getString("language", "system") ?: "system") }
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            OpenConverterTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var screen by remember { mutableStateOf("home") }
                    when (screen) {
                        "home" -> HomeScreen(
                            viewModel = homeVm,
                            onOpenSettings = { screen = "settings" },
                            onOpenHistory = { screen = "history" },
                        )
                        "settings" -> SettingsScreen(
                            onBack = { screen = "home" },
                            themeMode = themeMode,
                            languageMode = languageMode,
                            onThemeChanged = { newTheme ->
                                themeMode = newTheme
                                prefs.edit().putString("theme", newTheme).apply()
                            },
                            onLanguageChanged = { newLang ->
                                languageMode = newLang
                                prefs.edit().putString("language", newLang).apply()
                                recreate()
                            }
                        )
                        "history" -> HistoryScreen(onBack = { screen = "home" })
                    }
                }
            }
        }
    }
}