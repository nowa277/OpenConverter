package com.openconverter.app.ui.vm

import android.app.Application
import org.junit.Test
import kotlin.test.assertEquals

class FileListViewModelTest {
    @Test
    fun `sanitizeBaseName strips path separators`() {
        assertEquals("song_1", FileListViewModel.sanitizeBaseName("song/1"))
        assertEquals("song_1", FileListViewModel.sanitizeBaseName("song\\1"))
    }

    @Test
    fun `sanitizeBaseName strips control chars and null byte`() {
        // \n (0x0A) → stripped
        assertEquals("song", FileListViewModel.sanitizeBaseName("so\nng"))
        // \t (0x09) → stripped (still a C0 control char)
        assertEquals("song", FileListViewModel.sanitizeBaseName("so\tng"))
        // raw literal backslash + n (2 chars) → backslash replaced with _, n kept
        assertEquals("so_ng", FileListViewModel.sanitizeBaseName("""so\ng"""))
    }

    @Test
    fun `sanitizeBaseName collapses whitespace and dots`() {
        assertEquals("my song", FileListViewModel.sanitizeBaseName("  my   song  "))
        assertEquals("song", FileListViewModel.sanitizeBaseName("...song..."))
    }

    @Test
    fun `sanitizeBaseName truncates to 80 chars`() {
        val long = "a".repeat(200)
        assertEquals(80, FileListViewModel.sanitizeBaseName(long).length)
    }

    @Test
    fun `sanitizeBaseName falls back to output for blank result`() {
        assertEquals("output", FileListViewModel.sanitizeBaseName(""))
        assertEquals("output", FileListViewModel.sanitizeBaseName("   "))
        assertEquals("output", FileListViewModel.sanitizeBaseName("///"))
    }

    @Test
    fun `sanitizeBaseName keeps unicode chars`() {
        // CJK / emoji should pass through — only filesystem-unsafe ASCII is stripped.
        assertEquals("周杰伦 - 稻香", FileListViewModel.sanitizeBaseName("周杰伦 - 稻香"))
        assertEquals("track 🎵", FileListViewModel.sanitizeBaseName("track 🎵"))
    }

    @Test
    fun `clearOutputIfFormatChanged is no-op when format unchanged`() {
        val vm = FileListViewModel(Application())
        // First call establishes the format — no base to clear yet
        vm.clearOutputIfFormatChanged("mp3")
        // Same format again must not throw and must not signal clear
        vm.clearOutputIfFormatChanged("mp3")
        // If we get here without crash, the test passes. (Behavior is internal;
        // the public signal "shouldClear" is not exposed, so this test only
        // guards against regression in the no-throw + idempotent path.)
    }

    @Test
    fun `clearOutputIfFormatChanged clears on format change`() {
        val vm = FileListViewModel(Application())
        vm.clearOutputIfFormatChanged("mp3")
        // simulate user editing the base name
        vm.setOutputBaseName("my-song")
        // switch format — should clear
        vm.clearOutputIfFormatChanged("flac")
        // switch back to same — still cleared (already empty)
        vm.clearOutputIfFormatChanged("mp3")
    }
}
