package com.openconverter.app.service

import android.util.Log
import com.openconverter.app.engine.BatchProgress
import com.openconverter.app.engine.ProgressEvent
import com.openconverter.app.engine.ProgressSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/** Keeps a recoverable batch snapshot and updates the foreground notification. */
class ServiceProgressSink(
    private val service: ConversionService,
    private val progress: MutableStateFlow<BatchProgress?>,
    private val total: Int,
    private val batchId: String,
) : ProgressSink {
    private val activeNames = ConcurrentHashMap<Int, String>()

    private fun record(event: ProgressEvent) {
        progress.update { it?.record(event) }
    }

    override fun onFileStart(index: Int, total: Int, name: String) {
        activeNames[index] = name
        Log.i(TAG, "start  i=$index/$total name=$name")
        record(ProgressEvent.Start(batchId, index, total, name))
        service.postNotification(service.buildNotification(index, total, name, null))
    }

    override fun onFileProgress(index: Int, percent: Int) {
        val name = activeNames[index] ?: return
        record(ProgressEvent.Progress(batchId, index, percent))
        service.postNotification(service.buildNotification(index, total, name, percent))
    }

    override fun onFileDone(index: Int, outputPath: String) {
        activeNames.remove(index)
        Log.i(TAG, "done   i=$index out=$outputPath")
        record(ProgressEvent.Done(batchId, index, outputPath))
    }

    override fun onFileError(index: Int, message: String) {
        activeNames.remove(index)
        Log.e(TAG, "FAIL   i=$index msg=$message")
        record(ProgressEvent.Failed(batchId, index, message))
    }

    fun onFileCancelled(index: Int) {
        activeNames.remove(index)
        record(ProgressEvent.FileCancelled(batchId, index))
    }

    fun onBatchDone(cancelled: Boolean) {
        Log.i(TAG, if (cancelled) "batch cancelled" else "batch done")
        record(if (cancelled) ProgressEvent.BatchCancelled(batchId) else ProgressEvent.BatchDone(batchId))
    }

    companion object { private const val TAG = "OC.sink" }
}
