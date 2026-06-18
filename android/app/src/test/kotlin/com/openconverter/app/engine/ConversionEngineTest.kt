package com.openconverter.app.engine

import android.net.Uri
import com.openconverter.app.service.ConversionOrchestrator
import com.openconverter.app.service.ConversionService.Progress
import com.openconverter.app.ui.history.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversionEngineTest {
    private val FOLDER = Uri.parse("content://folder")

    private class FakeResolver(
        val bytesByUri: Map<Uri, ByteArray> = emptyMap(),
        val newDocByFolder: Map<Pair<Uri, String>, Uri> = emptyMap(),
        val displayNames: Map<Uri, String> = emptyMap(),
    ) : ContentResolverFacade {
        override fun read(uri: Uri): ByteArray? = bytesByUri[uri]
        override fun write(uri: Uri, bytes: ByteArray): Boolean = true
        override fun createDocument(folderUri: Uri, name: String, mime: String): Uri? =
            newDocByFolder[folderUri to name]
        override fun queryDisplayName(uri: Uri): String? = displayNames[uri]
    }

    private class FakeOrchestrator(
        val encodedBytes: ByteArray = byteArrayOf(1, 2, 3),
    ) : Orchestrator {
        override fun convertOneInMemory(
            input: ByteArray, fileName: String?, ekey: String?, targetFormat: String, bitrateKbps: Int,
        ): ConversionOrchestrator.Result =
            ConversionOrchestrator.Result(
                encoded = encodedBytes,
                sourceFormat = "mp3",
                targetFormat = targetFormat,
                durationSec = 1.0,
            )
    }

    private class ThrowingOrchestrator(val msg: String) : Orchestrator {
        override fun convertOneInMemory(
            input: ByteArray, fileName: String?, ekey: String?, targetFormat: String, bitrateKbps: Int,
        ): ConversionOrchestrator.Result = throw IllegalStateException(msg)
    }

    private class CapturingHistory : HistorySink {
        val entries = mutableListOf<HistoryEntry>()
        override fun record(entry: HistoryEntry) { entries.add(entry) }
    }

    private class CapturingFailure : FailureSink {
        val records = mutableListOf<Triple<String, String, String>>()
        override fun record(uri: String, filename: String, error: String) {
            records.add(Triple(uri, filename, error))
        }
    }

    private class FixedClock(val ms: Long) : Clock { override fun nowMs() = ms }
    private val fakeEkey = object : EkeyProvider { override fun getEkey(): String? = "ekey" }

    private fun newEngine(
        resolver: ContentResolverFacade,
        orch: Orchestrator,
        clock: Clock = FixedClock(1_000L),
    ): Triple<ConversionEngine, CapturingHistory, CapturingFailure> {
        val history = CapturingHistory()
        val failure = CapturingFailure()
        return Triple(
            ConversionEngine(
                resolver = resolver,
                orchestrator = orch,
                ekey = fakeEkey,
                failureSink = failure,
                historySink = history,
                clock = clock,
            ),
            history,
            failure,
        )
    }

    @Test
    fun `single file success - DONE entry recorded, progress emits Done`() {
        val uri = Uri.parse("content://a/1")
        val outUri = Uri.parse("content://out/1")
        val resolver = FakeResolver(
            bytesByUri = mapOf(uri to byteArrayOf(0x10, 0x20)),
            newDocByFolder = mapOf((FOLDER to "x.mp3") to outUri),
            displayNames = mapOf(uri to "x.mp3"),
        )
        val (engine, history, _) = newEngine(resolver, FakeOrchestrator())
        val progress = MutableStateFlow<Progress>(Progress.Idle)

        val r = engine.convertAll(listOf(uri), "mp3", FOLDER, progress)

        assertEquals(EngineResult(ok = 1, fail = 0, total = 1), r)
        assertEquals(1, history.entries.size)
        val e = history.entries.single()
        assertEquals("DONE", e.status)
        assertEquals(outUri, e.outputUri)
        assertEquals("x.mp3", e.sourceName)
        assertTrue(progress.value is Progress.Done)
    }

    @Test
    fun `read failure - FAILED entry recorded with read error message, no orchestrator call`() {
        val uri = Uri.parse("content://a/missing")
        val resolver = FakeResolver(bytesByUri = emptyMap())
        val (engine, history, failure) = newEngine(resolver, FakeOrchestrator())
        val progress = MutableStateFlow<Progress>(Progress.Idle)

        val r = engine.convertAll(listOf(uri), "mp3", FOLDER, progress)

        assertEquals(EngineResult(ok = 0, fail = 1, total = 1), r)
        val e = history.entries.single()
        assertEquals("FAILED", e.status)
        assertEquals("读取失败", e.errorMessage)
        assertEquals(1, failure.records.size)
        assertEquals("读取失败", failure.records.single().third)
    }

    @Test
    fun `orchestrator throws - FAILED with error message, next file still runs`() {
        val uri1 = Uri.parse("content://a/1")
        val uri2 = Uri.parse("content://a/2")
        val resolver = FakeResolver(
            bytesByUri = mapOf(uri1 to byteArrayOf(1)),
            displayNames = mapOf(uri1 to "1.mp3"),
        )
        val (engine, history, failure) = newEngine(
            resolver = resolver,
            orch = ThrowingOrchestrator("decoder broken"),
        )
        engine.convertAll(listOf(uri1, uri2), "mp3", FOLDER, MutableStateFlow(Progress.Idle))
        val statuses = history.entries.map { it.status }
        assertEquals(listOf("FAILED", "FAILED"), statuses)
        assertTrue(failure.records.any { it.third == "decoder broken" })
        assertTrue(failure.records.any { it.third == "读取失败" })
    }

    @Test
    fun `createDocument returns null - FAILED with create-doc error`() {
        val uri = Uri.parse("content://a/1")
        val resolver = FakeResolver(
            bytesByUri = mapOf(uri to byteArrayOf(1)),
            newDocByFolder = emptyMap(),
            displayNames = mapOf(uri to "song.mp3"),
        )
        val (engine, history, failure) = newEngine(resolver, FakeOrchestrator())
        engine.convertAll(listOf(uri), "mp3", FOLDER, MutableStateFlow(Progress.Idle))
        val e = history.entries.single()
        assertEquals("FAILED", e.status)
        assertEquals("无法创建输出文件", e.errorMessage)
        assertEquals("无法创建输出文件", failure.records.single().third)
    }

    @Test
    fun `write throws - FAILED with save error`() {
        val uri = Uri.parse("content://a/1")
        val outUri = Uri.parse("content://out/1")
        val baseResolver = FakeResolver(
            bytesByUri = mapOf(uri to byteArrayOf(1)),
            newDocByFolder = mapOf((FOLDER to "1.mp3") to outUri),
            displayNames = mapOf(uri to "1.mp3"),
        )
        val throwingResolver = object : ContentResolverFacade {
            override fun read(u: Uri) = baseResolver.read(u)
            override fun write(u: Uri, bytes: ByteArray): Boolean = throw java.io.IOException("disk full")
            override fun createDocument(folderUri: Uri, name: String, mime: String) =
                baseResolver.createDocument(folderUri, name, mime)
            override fun queryDisplayName(u: Uri) = baseResolver.queryDisplayName(u)
        }
        val (engine, history, failure) = newEngine(throwingResolver, FakeOrchestrator())
        engine.convertAll(listOf(uri), "mp3", FOLDER, MutableStateFlow(Progress.Idle))
        val e = history.entries.single()
        assertEquals("FAILED", e.status)
        assertTrue(e.errorMessage!!.startsWith("保存失败"), "errorMessage=${e.errorMessage}")
        assertTrue(failure.records.single().third.startsWith("保存失败"))
    }

    @Test
    fun `outName regression - qmcflac stem with target flac yields stem dot flac`() {
        val uri = Uri.parse("content://a/1")
        val baseResolver = FakeResolver(
            bytesByUri = mapOf(uri to byteArrayOf(1)),
            displayNames = mapOf(uri to "周杰伦-晴天.qmcflac"),
        )
        var capturedName: String? = null
        val capturing = object : ContentResolverFacade {
            override fun read(u: Uri) = baseResolver.read(u)
            override fun write(u: Uri, bytes: ByteArray) = true
            override fun createDocument(folderUri: Uri, name: String, mime: String): Uri {
                capturedName = name
                return Uri.parse("content://out/1")
            }
            override fun queryDisplayName(u: Uri) = baseResolver.queryDisplayName(u)
        }
        val (engine, _, _) = newEngine(capturing, FakeOrchestrator())
        engine.convertAll(listOf(uri), "flac", FOLDER, MutableStateFlow(Progress.Idle))
        assertEquals("周杰伦-晴天.flac", capturedName)
    }

    @Test
    fun `outName regression - no extension keeps source as-is plus target ext`() {
        val uri = Uri.parse("content://a/1")
        val baseResolver = FakeResolver(
            bytesByUri = mapOf(uri to byteArrayOf(1)),
            displayNames = mapOf(uri to "无后缀"),
        )
        var capturedName: String? = null
        val capturing = object : ContentResolverFacade {
            override fun read(u: Uri) = baseResolver.read(u)
            override fun write(u: Uri, bytes: ByteArray) = true
            override fun createDocument(folderUri: Uri, name: String, mime: String): Uri {
                capturedName = name
                return Uri.parse("content://out/1")
            }
            override fun queryDisplayName(u: Uri) = baseResolver.queryDisplayName(u)
        }
        val (engine, _, _) = newEngine(capturing, FakeOrchestrator())
        engine.convertAll(listOf(uri), "mp3", FOLDER, MutableStateFlow(Progress.Idle))
        assertEquals("无后缀.mp3", capturedName)
    }

    @Test
    fun `outName regression - null displayName falls back to output_index dot target`() {
        val uri = Uri.parse("content://a/1")
        val baseResolver = FakeResolver(
            bytesByUri = mapOf(uri to byteArrayOf(1)),
            displayNames = emptyMap(),
        )
        var capturedName: String? = null
        val capturing = object : ContentResolverFacade {
            override fun read(u: Uri) = baseResolver.read(u)
            override fun write(u: Uri, bytes: ByteArray) = true
            override fun createDocument(folderUri: Uri, name: String, mime: String): Uri {
                capturedName = name
                return Uri.parse("content://out/1")
            }
            override fun queryDisplayName(u: Uri) = null
        }
        val (engine, _, _) = newEngine(capturing, FakeOrchestrator())
        engine.convertAll(listOf(uri), "mp3", FOLDER, MutableStateFlow(Progress.Idle))
        assertEquals("output_0.mp3", capturedName)
    }

    @Test
    fun `clock is honored - history timestampMs equals injected ms`() {
        val uri = Uri.parse("content://a/1")
        val outUri = Uri.parse("content://out/1")
        val resolver = FakeResolver(
            bytesByUri = mapOf(uri to byteArrayOf(1)),
            newDocByFolder = mapOf((FOLDER to "1.mp3") to outUri),
            displayNames = mapOf(uri to "1.mp3"),
        )
        val (engine, history, _) = newEngine(resolver, FakeOrchestrator(), clock = FixedClock(42_424_242L))
        engine.convertAll(listOf(uri), "mp3", FOLDER, MutableStateFlow(Progress.Idle))
        assertEquals(42_424_242L, history.entries.single().timestampMs)
    }

    @Test
    fun `progress final state is Done after a single successful file`() {
        val uri = Uri.parse("content://a/1")
        val outUri = Uri.parse("content://out/1")
        val resolver = FakeResolver(
            bytesByUri = mapOf(uri to byteArrayOf(1)),
            newDocByFolder = mapOf((FOLDER to "1.mp3") to outUri),
            displayNames = mapOf(uri to "1.mp3"),
        )
        val (engine, _, _) = newEngine(resolver, FakeOrchestrator())
        val progress = MutableStateFlow<Progress>(Progress.Idle)
        engine.convertAll(listOf(uri), "mp3", FOLDER, progress)
        val final = progress.value
        assertTrue(final is Progress.Done, "expected Done, got $final")
        assertEquals(1, (final as Progress.Done).completed)
        assertEquals(1, final.total)
    }
}
