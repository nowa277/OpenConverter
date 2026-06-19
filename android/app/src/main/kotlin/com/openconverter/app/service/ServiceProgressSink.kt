package com.openconverter.app.service

import android.util.Log
import com.openconverter.app.engine.ProgressEvent
import com.openconverter.app.engine.ProgressSink
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Bridges [ProgressSink] events into a [MutableSharedFlow] (for the UI to
 * collect) AND rebuilds the FGS notification on each event.
 */
class ServiceProgressSink(
    private val service: ConversionService,
    private val flow: MutableSharedFlow<ProgressEvent>,
    private val total: Int,
) : ProgressSink {

    private var lastIndex = 0
    private var lastName = ""
    private var lastPercent = -1

    override fun onFileStart(index: Int, total: Int, name: String) {
        lastIndex = index; lastName = name; lastPercent = -1
        Log.i(TAG, "start  i=$index/$total name=$name")
        flow.tryEmit(ProgressEvent.Start(index, total, name))
        service.postNotification(service.buildNotification(index, total, name, null))
    }
    override fun onFileProgress(index: Int, percent: Int) {
        lastPercent = percent
        flow.tryEmit(ProgressEvent.Progress(index, percent))
        service.postNotification(service.buildNotification(index, total, lastName, percent))
    }
    override fun onFileDone(index: Int, outputPath: String) {
        Log.i(TAG, "done   i=$index out=$outputPath")
        flow.tryEmit(ProgressEvent.Done(index, outputPath))
    }
    override fun onFileError(index: Int, message: String) {
        Log.e(TAG, "FAIL   i=$index msg=$message")
        flow.tryEmit(ProgressEvent.Failed(index, message))
    }
    fun onBatchDone() {
        Log.i(TAG, "batch done")
        flow.tryEmit(ProgressEvent.BatchDone)
    }

    companion object { private const val TAG = "OC.sink" }
}
