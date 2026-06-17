package com.openconverter.app.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openconverter.app.ekey.EkeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = EkeyStore(app)
    private val _ekey = MutableStateFlow(store.getEkey() ?: "")
    val ekey: StateFlow<String> = _ekey.asStateFlow()

    fun saveEkey(value: String) {
        viewModelScope.launch {
            store.setEkey(value)
            _ekey.value = store.getEkey() ?: ""
        }
    }

    fun clearEkey() {
        viewModelScope.launch {
            store.clearEkey()
            _ekey.value = ""
        }
    }
}
