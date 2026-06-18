package com.openconverter.app.ui.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-lifetime history of conversions. v0.4.0 keeps it in-memory only
 * (per spec §8.2 YAGNI — no Room DB). Persists across screen navigation
 * (the StateFlow is held by Application) but NOT across process death.
 */
object HistoryRepository {
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries

    fun record(entry: HistoryEntry) {
        _entries.value = (listOf(entry) + _entries.value).take(200)
    }

    fun clear() { _entries.value = emptyList() }
}
