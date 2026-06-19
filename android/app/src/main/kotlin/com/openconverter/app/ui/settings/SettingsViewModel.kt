package com.openconverter.app.ui.settings

import androidx.lifecycle.ViewModel
import com.openconverter.app.BuildConfig

/** Read-only settings data. Version comes from BuildConfig (git-tagged at build time). */
data class SettingsUiState(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
    val supportedInputs: List<String> = listOf(
        ".ncm (网易云)", ".qmc0/.qmc3/.qmcflac/.qmcogg (QQ音乐 v1)",
        ".kgm/.kgma/.vpr (酷狗)", ".kwm (酷我)",
        ".mp3/.flac/.wav/.m4a/.aac/.ogg/.opus (明文)",
    ),
    val supportedOutputs: List<String> = listOf("MP3", "FLAC", "WAV", "M4A (AAC)", "OGG (Vorbis)"),
    val deferred: String = "QMCv2 (.mflac/.mgg/.bkc) — v2 (需 ekey)",
    val githubUrl: String = "https://github.com/nowa277/OpenConverter",
    val issuesUrl: String = "https://github.com/nowa277/OpenConverter/issues/new",
    val license: String = "MIT",
) {
    companion object {
        const val REPO_URL = "https://github.com/nowa277/OpenConverter"
        const val ISSUES_URL = "https://github.com/nowa277/OpenConverter/issues/new"
    }
}

class SettingsViewModel : ViewModel() {
    val state: SettingsUiState = SettingsUiState()
}
