package com.openconverter.app.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel : ViewModel() {
    val entries: StateFlow<List<HistoryEntry>> = HistoryRepository.entries
    fun clear() = HistoryRepository.clear()
}
