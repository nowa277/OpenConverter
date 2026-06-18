package com.openconverter.app.engine

import android.net.Uri
import com.openconverter.app.service.ConversionOrchestrator
import com.openconverter.app.ui.history.HistoryEntry

/**
 * Surfaces the engine needs from the Android [android.content.ContentResolver].
 * Wrapped behind a facade so the engine is unit-testable in pure JVM.
 */
interface ContentResolverFacade {
    fun read(uri: Uri): ByteArray?
    fun write(uri: Uri, bytes: ByteArray): Boolean
    fun createDocument(folderUri: Uri, name: String, mime: String): Uri?
    fun queryDisplayName(uri: Uri): String?
}

/** Stub used by tests that don't exercise the content layer. */
object EmptyContentResolverFacade : ContentResolverFacade {
    override fun read(uri: Uri): ByteArray? = null
    override fun write(uri: Uri, bytes: ByteArray): Boolean = false
    override fun createDocument(folderUri: Uri, name: String, mime: String): Uri? = null
    override fun queryDisplayName(uri: Uri): String? = null
}

interface EkeyProvider { fun getEkey(): String? }

/**
 * Adapter from the engine's view of the orchestrator to the existing
 * [ConversionOrchestrator]. The engine only needs the synchronous
 * in-memory conversion call; decode/transcode internals stay where they are.
 */
fun interface Orchestrator {
    fun convertOneInMemory(
        input: ByteArray,
        fileName: String?,
        ekey: String?,
        targetFormat: String,
        bitrateKbps: Int,
    ): ConversionOrchestrator.Result
}

interface FailureSink { fun record(uri: String, filename: String, error: String) }
interface HistorySink { fun record(entry: HistoryEntry) }

interface Clock {
    fun nowMs(): Long
    companion object {
        val wallClock: Clock = object : Clock { override fun nowMs(): Long = System.currentTimeMillis() }
    }
}

data class EngineResult(val ok: Int, val fail: Int, val total: Int)
