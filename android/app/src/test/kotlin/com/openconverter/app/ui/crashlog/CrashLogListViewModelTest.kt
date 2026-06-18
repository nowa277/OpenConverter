package com.openconverter.app.ui.crashlog

import com.openconverter.app.crash.CrashLogStore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashLogListViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `init populates entries from the store`() {
        val dir = tmp.newFolder("crash")
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")
        val vm = CrashLogListViewModel(CrashLogStore(dir))
        val names = vm.entries.value.map { it.fileName }
        assertEquals(setOf("a.txt", "b.txt"), names.toSet())
    }

    @Test
    fun `refresh re-reads after on-disk changes`() {
        val dir = tmp.newFolder("crash")
        val vm = CrashLogListViewModel(CrashLogStore(dir))
        assertTrue(vm.entries.value.isEmpty())
        File(dir, "a.txt").writeText("a")
        vm.refresh()
        assertEquals(listOf("a.txt"), vm.entries.value.map { it.fileName })
    }

    @Test
    fun `clear empties the store and reloads`() {
        val dir = tmp.newFolder("crash")
        File(dir, "a.txt").writeText("a")
        val vm = CrashLogListViewModel(CrashLogStore(dir))
        assertTrue(vm.entries.value.isNotEmpty())
        vm.clear()
        assertEquals(emptyList(), vm.entries.value)
    }
}
