package com.openconverter.app.ui.home

import com.openconverter.app.engine.ProgressEvent
import com.openconverter.app.ui.components.FileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeProgressTest {
    @Test fun cancelled_batch_stops_running_and_preserves_finished_files() {
        val state = HomeUiState(
            files = listOf(
                FileEntry("a", "a.ncm", state = FileState.Done, percent = 100),
                FileEntry("b", "b.ncm", state = FileState.Running, percent = 40),
                FileEntry("c", "c.ncm", state = FileState.Pending),
            ),
            running = true,
            batchId = "batch",
        )
        val stopped = HomeViewModel.applyProgress(state, ProgressEvent.BatchCancelled("batch"))
        assertFalse(stopped.running)
        assertEquals(FileState.Done, stopped.files[0].state)
        assertEquals(FileState.Cancelled, stopped.files[1].state)
        assertEquals(FileState.Cancelled, stopped.files[2].state)
    }

    @Test fun rebind_recovers_terminal_states_after_many_progress_updates() {
        var snapshot = com.openconverter.app.engine.BatchProgress("batch")
        snapshot = snapshot.record(ProgressEvent.Done("batch", 0, "output"))
        repeat(150) { snapshot = snapshot.record(ProgressEvent.Progress("batch", 1, it % 100)) }
        snapshot = snapshot.record(ProgressEvent.FileCancelled("batch", 1))
        snapshot = snapshot.record(ProgressEvent.BatchDone("batch"))
        val restored = HomeViewModel.applySnapshot(
            HomeUiState(files = listOf(FileEntry("a", "a.ncm"), FileEntry("b", "b.ncm")), running = true, batchId = "batch"),
            snapshot,
        )
        assertFalse(restored.running)
        assertEquals(listOf(FileState.Done, FileState.Cancelled), restored.files.map { it.state })
    }

    @Test fun old_batch_events_cannot_update_a_new_batch() {
        val state = HomeUiState(
            files = listOf(FileEntry("new", "new.ncm", state = FileState.Running, percent = 10)),
            running = true,
            batchId = "new",
        )
        assertEquals(state, HomeViewModel.applyProgress(state, ProgressEvent.Done("old", 0, "old-output")))
        assertEquals(state, HomeViewModel.applyProgress(state, ProgressEvent.BatchDone("old")))
    }
}
