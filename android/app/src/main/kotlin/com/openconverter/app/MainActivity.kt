package com.openconverter.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.openconverter.app.ui.about.AboutScreen
import com.openconverter.app.ui.dock.GlyphDock
import com.openconverter.app.ui.history.HistoryScreen
import com.openconverter.app.ui.home.HomeScreen
import com.openconverter.app.ui.home.HomeViewModel
import com.openconverter.app.ui.settings.SettingsScreen
import com.openconverter.app.ui.settings.SettingsViewModel
import com.openconverter.app.ui.theme.OpenConverterTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val homeVm: HomeViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels {
        SettingsViewModel.Factory((application as OpenConverterApp).kggKeyStore)
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "system") ?: "system"
        val locale = when (lang) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动时静默同步 KuGou 密钥（Shizuku 或 Root 授权场景）
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("auto_sync_kugou", true)) {
            settingsVm.autoSyncKuGouSilently()
        }

        setContent {
            val appPrefs = remember { getSharedPreferences("prefs", Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(appPrefs.getString("theme", "system") ?: "system") }
            var languageMode by remember { mutableStateOf(appPrefs.getString("language", "system") ?: "system") }

            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            OpenConverterTheme(darkTheme = darkTheme) {
                val settingsState by settingsVm.state.collectAsState()
                var tab by rememberSaveable { mutableStateOf("convert") }
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            "history" -> HistoryScreen()
                            "settings" -> SettingsScreen(
                                viewModel = settingsVm,
                                themeMode = themeMode,
                                languageMode = languageMode,
                                onThemeChanged = { newTheme ->
                                    themeMode = newTheme
                                    appPrefs.edit().putString("theme", newTheme).apply()
                                },
                                onLanguageChanged = { newLang ->
                                    languageMode = newLang
                                    appPrefs.edit().putString("language", newLang).apply()
                                    recreate()
                                },
                            )
                            "about" -> AboutScreen(versionName = settingsState.versionName)
                            else -> HomeScreen(viewModel = homeVm)
                        }
                    }
                    GlyphDock(selected = tab, onSelect = { tab = it })
                }
            }
        }
    }
}
