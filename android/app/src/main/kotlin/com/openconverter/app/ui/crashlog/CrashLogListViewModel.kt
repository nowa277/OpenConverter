package com.openconverter.app.ui.crashlog

import androidx.lifecycle.ViewModel
import com.openconverter.app.crash.CrashLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reads the crash dir on demand, exposes the entries as a StateFlow.
 * Stateless beyond the cached snapshot — every call to [refresh]
 * re-reads from disk.
 */
class CrashLogListViewModel(
    private val store: CrashLogStore,
) : ViewModel() {
    private val _entries = MutableStateFlow<List<CrashLogStore.Entry>>(emptyList())
    val entries: StateFlow<List<CrashLogStore.Entry>> = _entries

    init { refresh() }

    fun refresh() {
        _entries.value = store.list()
    }

    fun clear() {
        store.clear()
        refresh()
    }
}
