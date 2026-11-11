package com.openconverter.app.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class UpdatePhase { Idle, Checking, Current, Available, Downloading, Ready, NoPackage, Failed }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val asset: ReleaseAsset? = null,
    val pendingInstall: File? = null,
    val progressBytes: Long = 0L,
    val totalBytes: Long = 0L,
) {
    val progressPercent: Int?
        get() = if (totalBytes > 0L) ((progressBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else null
    val busy: Boolean get() = phase == UpdatePhase.Checking || phase == UpdatePhase.Downloading
}

class UpdateViewModel(
    private val currentVersion: String,
    private val fetch: suspend () -> ReleaseAsset?,
    private val download: suspend (ReleaseAsset, (Long, Long) -> Unit) -> File,
) : ViewModel() {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()

    fun check() {
        if (state.value.busy) return
        viewModelScope.launch { lookup() }
    }

    fun update() {
        if (state.value.busy || state.value.pendingInstall != null) return
        viewModelScope.launch {
            val asset = state.value.asset ?: lookup() ?: return@launch
            mutable.value = state.value.copy(phase = UpdatePhase.Downloading, progressBytes = 0L, totalBytes = 0L)
            try {
                val file = download(asset) { downloaded, total ->
                    mutable.value = state.value.copy(
                        phase = UpdatePhase.Downloading,
                        progressBytes = downloaded,
                        totalBytes = total,
                    )
                }
                mutable.value = state.value.copy(phase = UpdatePhase.Ready, pendingInstall = file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: java.io.IOException) {
                mutable.value = state.value.copy(phase = UpdatePhase.Failed)
            }
        }
    }

    fun installLaunched() { mutable.value = state.value.copy(pendingInstall = null) }
    fun installFailed() { mutable.value = state.value.copy(phase = UpdatePhase.Failed, pendingInstall = null) }

    private suspend fun lookup(): ReleaseAsset? {
        mutable.value = state.value.copy(phase = UpdatePhase.Checking)
        return try {
            val asset = fetch()
            val phase = when {
                asset == null -> UpdatePhase.NoPackage
                ReleaseCheck.isNewer(asset.version, currentVersion) -> UpdatePhase.Available
                else -> UpdatePhase.Current
            }
            mutable.value = UpdateState(phase, asset.takeIf { phase == UpdatePhase.Available })
            mutable.value.asset
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            mutable.value = UpdateState(UpdatePhase.Failed)
            null
        } catch (_: org.json.JSONException) {
            mutable.value = UpdateState(UpdatePhase.Failed)
            null
        }
    }
}
