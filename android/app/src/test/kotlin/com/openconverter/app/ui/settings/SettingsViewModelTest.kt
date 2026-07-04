package com.openconverter.app.ui.settings

import com.openconverter.app.decoders.kgg.KggImportResult
import com.openconverter.app.decoders.kgg.KggImportState
import com.openconverter.app.decoders.kgg.KggKeyImporter
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
import org.junit.Assert.assertTrue
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
        var importedUri: String? = null
        val result = KggImportResult(added = 1, updated = 1, total = 3)
        val importer = KggKeyImporter { uri ->
            importedUri = uri
            keyState.value = KggImportState.Importing
            keyState.value = KggImportState.Ready(result.total, result)
            result
        }
        val viewModel = SettingsViewModel(importer, keyState, dispatcher)

        assertEquals(2, viewModel.state.value.kggKeyCount)
        viewModel.importKggKeys("content://keys/source")
        advanceUntilIdle()

        assertEquals("content://keys/source", importedUri)
        assertEquals(3, viewModel.state.value.kggKeyCount)
        assertEquals(result, viewModel.state.value.kggLastResult)
        assertFalse(viewModel.state.value.kggImporting)
        assertNull(viewModel.state.value.kggImportError)
    }

    @Test
    fun exposes_failure_without_losing_count_then_clears_it_on_success() = runTest(dispatcher) {
        val keyState = MutableStateFlow<KggImportState>(KggImportState.Ready(total = 2))
        var fail = true
        val importer = KggKeyImporter {
            keyState.value = KggImportState.Importing
            if (fail) {
                keyState.value = KggImportState.Failed("invalid key file", total = 2)
                throw IllegalArgumentException("invalid key file")
            }
            KggImportResult(1, 0, 3).also { keyState.value = KggImportState.Ready(3, it) }
        }
        val viewModel = SettingsViewModel(importer, keyState, dispatcher)

        viewModel.importKggKeys("content://keys/bad")
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.kggKeyCount)
        assertEquals("invalid key file", viewModel.state.value.kggImportError)
        assertTrue(viewModel.state.value.kggLastResult == null)

        fail = false
        viewModel.importKggKeys("content://keys/good")
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.kggKeyCount)
        assertNull(viewModel.state.value.kggImportError)
    }
}
