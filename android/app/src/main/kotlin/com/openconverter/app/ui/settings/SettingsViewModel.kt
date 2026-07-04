package com.openconverter.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openconverter.app.BuildConfig
import com.openconverter.app.decoders.kgg.KggImportResult
import com.openconverter.app.decoders.kgg.KggImportState
import com.openconverter.app.decoders.kgg.KggKeyImporter
import com.openconverter.app.decoders.kgg.KggKeyStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
    val supportedInputs: List<String> = listOf(
        ".ncm (网易云)", ".qmc0/.qmc3/.qmcflac/.qmcogg (QQ音乐 v1)",
        ".kgm/.kgma/.vpr/.kgg (酷狗)", ".kwm (酷我)",
        ".mp3/.flac/.wav/.m4a/.aac/.ogg/.opus (明文)",
    ),
    val supportedOutputs: List<String> = listOf("MP3", "FLAC", "WAV", "M4A (AAC)", "OGG (Vorbis)"),
    val deferred: String = "QMCv2 (.mflac/.mgg/.bkc) — v2 (需 ekey)",
    val githubUrl: String = "https://github.com/nowa277/OpenConverter",
    val issuesUrl: String = "https://github.com/nowa277/OpenConverter/issues/new",
    val license: String = "Apache-2.0",
    val kggKeyCount: Int = 0,
    val kggImporting: Boolean = false,
    val kggLastResult: KggImportResult? = null,
    val kggImportError: String? = null,
) {
    companion object {
        const val REPO_URL = "https://github.com/nowa277/OpenConverter"
        const val ISSUES_URL = "https://github.com/nowa277/OpenConverter/issues/new"
    }
}

class SettingsViewModel(
    private val importer: KggKeyImporter,
    keyState: StateFlow<KggImportState>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = keyState
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(keyState.value))

    fun importKggKeys(uri: String) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { importer.import(uri) }
        }
    }

    class Factory(private val store: KggKeyStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(store, store.state) as T
        }
    }

    companion object {
        private fun toUiState(state: KggImportState): SettingsUiState = when (state) {
            is KggImportState.Ready -> SettingsUiState(
                kggKeyCount = state.total,
                kggLastResult = state.lastResult,
            )
            KggImportState.Importing -> SettingsUiState(kggImporting = true)
            is KggImportState.Failed -> SettingsUiState(
                kggKeyCount = state.total,
                kggImportError = state.message,
            )
        }
    }
}
