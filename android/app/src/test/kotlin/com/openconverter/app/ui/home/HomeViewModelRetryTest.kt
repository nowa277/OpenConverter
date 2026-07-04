package com.openconverter.app.ui.home

import com.openconverter.app.ui.components.FileState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelRetryTest {
    @Test
    fun retry_entries_keep_only_failed_files_and_reset_them() {
        val done = FileEntry("done", "done.kgg", state = FileState.Done, percent = 100)
        val failed = FileEntry(
            "failed",
            "failed.kgg",
            state = FileState.Failed,
            percent = 42,
            error = "Missing KGG key",
        )

        assertEquals(
            listOf(failed.copy(state = FileState.Pending, percent = 0, error = null)),
            HomeViewModel.retryEntries(listOf(done, failed)),
        )
    }
}
