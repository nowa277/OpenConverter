package com.openconverter.app.engine

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseLyricResolverTest {
    private val temps = mutableListOf<File>()

    @After
    fun cleanup() {
        for (dir in temps.asReversed()) {
            runCatching { dir.setReadable(true, false); dir.setWritable(true, false); dir.setExecutable(true, false) }
            dir.walkBottomUp().forEach { f ->
                runCatching { f.setReadable(true, false); f.setWritable(true, false); f.setExecutable(true, false) }
                f.delete()
            }
        }
        temps.clear()
    }

    @Test
    fun disabled_returns_null_without_fetchJson() = runTest {
        val root = cacheRoot("1", """{"lrc":"[00:00.00]A"}""")
        var fetches = 0
        val resolver = NeteaseLyricResolver(
            enabled = false,
            extraRoots = listOf(root),
            fetchJson = { fetches += 1; """{"lrc":{"lyric":"[00:00.00]B"}}""" },
            open = ::openFile,
        )
        val port: LyricResolverPort = resolver
        assertNull(port.resolveLrc("1"))
        assertEquals(0, fetches)
    }

    @Test
    fun blank_id_returns_null_without_fetchJson() = runTest {
        var fetches = 0
        val resolver = NeteaseLyricResolver(
            enabled = true,
            extraRoots = emptyList(),
            fetchJson = { fetches += 1; "{}" },
            open = ::openFile,
        )
        assertNull(resolver.resolveLrc(""))
        assertNull(resolver.resolveLrc("   "))
        assertEquals(0, fetches)
    }

    @Test
    fun cache_hit_does_not_call_fetchJson() = runTest {
        val root = cacheRoot("1", """{"lrc":"[00:00.00]A"}""")
        var fetches = 0
        val resolver = NeteaseLyricResolver(
            enabled = true,
            extraRoots = listOf(root),
            fetchJson = { fetches += 1; "nope" },
            open = ::openFile,
        )
        assertEquals("[00:00.00]A", resolver.resolveLrc("1"))
        assertEquals(0, fetches)
    }

    @Test
    fun cache_miss_parses_fetchJson_lyric_field() = runTest {
        var fetchedId: String? = null
        val resolver = NeteaseLyricResolver(
            enabled = true,
            extraRoots = emptyList(),
            fetchJson = { fetchedId = it; """{"lrc":{"lyric":"[00:00.00]Hi"}}""" },
            open = { _, _ -> error("open must not be used when extraRoots is empty") },
        )
        assertEquals("[00:00.00]Hi", resolver.resolveLrc("1"))
        assertEquals("1", fetchedId)
    }

    @Test
    fun scanRoots_named_LrcDownload_uses_parent() {
        val parent = tempDir()
        val lrcDownload = File(parent, "LrcDownload").also { assertTrue(it.mkdirs()) }
        val roots = PublicStorageLyricScanner.scanRoots(listOf(lrcDownload))
        assertEquals(listOf(parent.canonicalFile), roots.map { it.canonicalFile })
    }

    @Test
    fun scanRoots_dir_containing_LrcCache_is_root() {
        val start = tempDir()
        val nested = File(start, "ncm-lyrics").also { assertTrue(it.mkdirs()) }
        assertTrue(File(nested, "LrcCache").mkdirs())
        val roots = PublicStorageLyricScanner.scanRoots(listOf(start))
        assertEquals(listOf(nested.canonicalFile), roots.map { it.canonicalFile })
    }

    @Test
    fun scanRoots_skips_missing_and_unreadable() {
        val missing = File(tempDir(), "gone")
        val readable = cacheRoot("9", """{"lrc":"[00:00.00]X"}""")
        val blocked = File(tempDir(), "secret").also { assertTrue(it.mkdirs()) }
        assertTrue(File(blocked, "LrcDownload").mkdirs())
        assertTrue(blocked.setReadable(false, false))
        assertTrue(blocked.setExecutable(false, false))

        val roots = PublicStorageLyricScanner.scanRoots(listOf(missing, blocked, readable))
        assertEquals(listOf(readable.canonicalFile), roots.map { it.canonicalFile })
    }

    private fun cacheRoot(musicId: String, body: String): File {
        val dir = tempDir()
        val download = File(dir, "LrcDownload").also { assertTrue(it.mkdirs()) }
        File(download, musicId).writeText(body, Charsets.UTF_8)
        return dir
    }

    private fun tempDir(): File {
        val dir = File.createTempFile("oc-lrc-", "").also {
            it.delete()
            assertTrue(it.mkdirs())
        }
        temps += dir
        return dir
    }

    private fun openFile(root: File, rel: String): ByteArray? {
        val file = File(root, rel)
        return if (file.isFile) file.readBytes() else null
    }
}
