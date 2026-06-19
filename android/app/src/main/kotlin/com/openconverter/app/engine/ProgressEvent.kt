package com.openconverter.app.engine

/**
 * Events the UI subscribes to via [com.openconverter.app.service.ConversionService.progress].
 * The engine itself only knows [ProgressSink]; the service translates sink callbacks into these.
 */
sealed interface ProgressEvent {
    data class Start(val index: Int, val total: Int, val name: String) : ProgressEvent
    data class Progress(val index: Int, val percent: Int) : ProgressEvent
    data class Done(val index: Int, val outputUri: String) : ProgressEvent
    data class Failed(val index: Int, val message: String) : ProgressEvent
    data object BatchDone : ProgressEvent
}
