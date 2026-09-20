package com.openconverter.app.ui.settings

import com.openconverter.app.decoders.kgg.KggImportResult
import com.openconverter.app.decoders.kgg.KggImportState
import com.openconverter.app.decoders.kgg.KggKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exposes_initial_count_and_successful_import_stats() = runTest(dispatcher) {
        val keyState = MutableStateFlow<KggImportState>(KggImportState.Ready(total = 2))
        val viewModel = SettingsViewModel(fakeStore(), keyState, dispatcher)
        assertEquals(2, viewModel.state.value.kggKeyCount)

        val result = KggImportResult(added = 1, updated = 1, total = 3)
        keyState.value = KggImportState.Ready(result.total, result)
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.kggKeyCount)
        assertEquals(result, viewModel.state.value.kggLastResult)
        assertFalse(viewModel.state.value.kggImporting)
        assertNull(viewModel.state.value.kggImportError)
    }

    @Test
    fun exposes_failure_without_losing_count_then_clears_it_on_success() = runTest(dispatcher) {
        val keyState = MutableStateFlow<KggImportState>(KggImportState.Ready(total = 2))
        val viewModel = SettingsViewModel(fakeStore(), keyState, dispatcher)

        keyState.value = KggImportState.Failed("invalid key file", total = 2)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.kggKeyCount)
        assertEquals("invalid key file", viewModel.state.value.kggImportError)
        assertEquals(null, viewModel.state.value.kggLastResult)

        keyState.value = KggImportState.Ready(3, KggImportResult(1, 0, 3))
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.kggKeyCount)
        assertNull(viewModel.state.value.kggImportError)
    }

    // ViewModel maps KggImportState → SettingsUiState. Store I/O is not exercised here
    // because android.net.Uri is a JVM stub outside Robolectric.
    private fun fakeStore(): KggKeyStore {
        val files = kotlin.io.path.createTempDirectory("kgg-files").toFile()
        val cache = kotlin.io.path.createTempDirectory("kgg-cache").toFile()
        return KggKeyStore(files, cache) { java.io.ByteArrayInputStream(ByteArray(0)) }
    }
}
