package com.openconverter.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.openconverter.app.ekey.EkeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings state. v0.4.0 is intentionally minimal — only the QMC ekey
 * is persisted (per desktop parity). Output format / bitrate / theme are
 * NOT settings in v0.4.0 (per spec §2.5 + 决策 #11: dark only).
 *
 * Future: add a Room DB + settings screen with more knobs (v0.5+).
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val ekeyStore by lazy { EkeyStore(app) }

    private val _qmcEkey = MutableStateFlow(ekeyStore.getEkey().orEmpty())
    val qmcEkey: StateFlow<String> = _qmcEkey

    fun saveEkey(value: String) {
        ekeyStore.setEkey(value)
        _qmcEkey.value = value
    }
}
