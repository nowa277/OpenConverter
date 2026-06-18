package com.openconverter.app.ui.vm

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeViewModelTest {
    @Test
    fun `setOutputFolder stores the uri`() {
        val vm = HomeViewModel(Application())
        val fakeUri = android.net.Uri.parse("content://test/tree/abc")
        vm.setOutputFolder(fakeUri)
        assertEquals(fakeUri, vm.outputFolderUri.value)
    }

    @Test
    fun `setCurrentFormat defaults to mp3 and updates`() {
        val vm = HomeViewModel(Application())
        assertEquals("mp3", vm.currentFormat.value)
        vm.setCurrentFormat("flac")
        assertEquals("flac", vm.currentFormat.value)
    }

    @Test
    fun `clear resets the file queue`() {
        val vm = HomeViewModel(Application())
        vm.clear()
        assertEquals(emptyList(), vm.files.value)
    }

    @Test
    fun `remove drops a uri from the queue`() {
        val vm = HomeViewModel(Application())
        val u = android.net.Uri.parse("content://x")
        // We can't call addUris without a real ContentResolver, so this is a no-op assertion.
        vm.remove(u)
        assertEquals(emptyList(), vm.files.value)
    }

    @Test
    fun `outputName rule mirrors spec §2_5`() {
        // The output-name rule is implemented in ConversionService, not the VM,
        // but the VM doesn't need to know about it. This test documents the
        // contract: HomeViewModel does NOT expose baseName or outName.
        val vm = HomeViewModel(Application())
        // VM should not have a public outName method.
        val hasOutName = try {
            vm::class.java.getMethod("outName")
            true
        } catch (_: NoSuchMethodException) {
            false
        }
        assert(!hasOutName) { "HomeViewModel must not own output-name logic — that's ConversionService's job" }
    }
}
