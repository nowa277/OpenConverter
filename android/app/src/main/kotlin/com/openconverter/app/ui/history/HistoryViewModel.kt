package com.openconverter.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openconverter.app.engine.HistoryPort
import com.openconverter.app.engine.HistoryRecord
import com.openconverter.app.engine.JsonHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val records: List<HistoryRecord> = emptyList(),
    val loading: Boolean = true,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val store: HistoryPort = JsonHistoryStore(app.filesDir)
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(records = store.readAll(), loading = false) }
    }

    fun clear() = viewModelScope.launch(Dispatchers.IO) {
        store.clear()
        refresh()
    }
}