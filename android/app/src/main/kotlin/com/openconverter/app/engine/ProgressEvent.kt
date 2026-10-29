package com.openconverter.app.engine

/** Events emitted by a single conversion batch. */
sealed interface ProgressEvent {
    val batchId: String

    data class Start(override val batchId: String, val index: Int, val total: Int, val name: String) : ProgressEvent
    data class Progress(override val batchId: String, val index: Int, val percent: Int) : ProgressEvent
    data class Done(override val batchId: String, val index: Int, val outputUri: String) : ProgressEvent
    data class Failed(override val batchId: String, val index: Int, val message: String) : ProgressEvent
    data class FileCancelled(override val batchId: String, val index: Int) : ProgressEvent
    data class BatchDone(override val batchId: String) : ProgressEvent
    data class BatchCancelled(override val batchId: String) : ProgressEvent
}

/** Latest state for every file, retained while the screen is unbound. */
data class BatchProgress(
    val batchId: String,
    val files: Map<Int, ProgressEvent> = emptyMap(),
    val terminal: ProgressEvent? = null,
) {
    fun record(event: ProgressEvent): BatchProgress {
        if (event.batchId != batchId || terminal != null) return this
        val index = when (event) {
            is ProgressEvent.Start -> event.index
            is ProgressEvent.Progress -> event.index
            is ProgressEvent.Done -> event.index
            is ProgressEvent.Failed -> event.index
            is ProgressEvent.FileCancelled -> event.index
            is ProgressEvent.BatchDone, is ProgressEvent.BatchCancelled -> return copy(terminal = event)
        }
        val previous = files[index]
        if (previous is ProgressEvent.Done || previous is ProgressEvent.Failed || previous is ProgressEvent.FileCancelled) return this
        return copy(files = files + (index to event))
    }
}
