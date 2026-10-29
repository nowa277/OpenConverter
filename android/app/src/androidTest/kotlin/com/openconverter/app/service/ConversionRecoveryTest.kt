package com.openconverter.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.openconverter.app.MainActivity
import com.openconverter.app.decoders.SyntheticNcm
import com.openconverter.app.ui.home.HomeViewModel
import com.openconverter.app.ui.components.FileState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ConversionRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val authority = "com.openconverter.app.test.documents"
    private val tree = DocumentsContract.buildTreeDocumentUri(authority, "root")
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: HomeViewModel

    @Before fun prepare() {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_sync_kugou", false).putBoolean("ncm_lyrics_enabled", false)
            .remove("output_folder_uri").commit()
        context.contentResolver.call(Uri.parse("content://$authority"), "prepare", context.packageName, null)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            vm = ViewModelProvider(activity)[HomeViewModel::class.java]
            vm.setOutputFolder(tree)
        }
        assertEquals(null, vm.state.value.folderError)
    }

    @After fun stop() {
        if (::scenario.isInitialized) {
            scenario.onActivity { vm.cancel() }
            context.stopService(Intent(context, ConversionService::class.java))
            scenario.close()
        }
    }

    private fun uri(name: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, name)
    private fun outputBytes(name: String) = context.contentResolver.openInputStream(uri(name))!!.use { it.readBytes() }
    private fun outputNames(): List<String> = context.contentResolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root"),
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
    )!!.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    private fun start(vararg names: String) {
        scenario.onActivity {
            vm.setFiles(names.map(::uri))
            vm.setTargetFormat("mp3")
            vm.setBitrate("128k")
            vm.start(it)
        }
    }
    private fun awaitStopped() = runBlocking {
        withTimeout(60_000) { vm.state.first { !it.running } }
    }

    @Test fun cancel_then_convert_again_and_recreate_activity() {
        repeat(3) {
            start("long.wav")
            runBlocking { withTimeout(10_000) { vm.state.first { s -> s.running && s.files.any { it.state == FileState.Running } } } }
            scenario.onActivity { vm.cancel() }
            val stopped = awaitStopped()
            assertEquals(FileState.Cancelled, stopped.files.single().state)
            start("small.wav")
            val done = awaitStopped()
            assertEquals(FileState.Done, done.files.single().state)
            assertTrue(outputBytes("small.mp3").size > 1000)
            check(DocumentsContract.deleteDocument(context.contentResolver, uri("small.mp3")))
        }
        scenario.recreate()
        scenario.onActivity { vm = ViewModelProvider(it)[HomeViewModel::class.java] }
        start("tagged.ncm")
        val done = awaitStopped()
        assertEquals(FileState.Done, done.files.single().state)
        val output = File(context.cacheDir, "verified-output.mp3").apply { writeBytes(outputBytes("tagged.mp3")) }
        assertTrue(output.length() > 1000)
        val information = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(output.path).mediaInformation
        assertTrue(information.duration.toDouble() in 0.9..1.3)
        assertEquals("Synthetic tone", information.tags.getString("title"))
        assertEquals("Synth / Wave", information.tags.getString("artist"))
        assertTrue(information.streams.any { it.type == "video" })
        assertFalse(vm.state.value.running)
    }

    @Test fun one_cancel_leaves_second_file_converting_and_page_changes_preserve_state() {
        start("long.wav", "small.wav")
        runBlocking { withTimeout(10_000) { vm.state.first { s -> s.files.first().state == FileState.Running } } }
        val connected = CountDownLatch(1)
        var service: ConversionService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as ConversionService.LocalBinder).service
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        context.bindService(Intent(context, ConversionService::class.java), connection, Context.BIND_AUTO_CREATE)
        assertTrue(connected.await(10, TimeUnit.SECONDS))
        try {
            instrumentation.runOnMainSync { assertTrue(service!!.cancelFile(0)) }
            // Rebinding exercises the lifecycle path used when navigating away and back.
            scenario.onActivity { vm.unbind(it); vm.bind(it) }
            val done = awaitStopped()
            assertEquals(FileState.Cancelled, done.files[0].state)
            assertEquals(FileState.Done, done.files[1].state)
            assertTrue(outputBytes("small.mp3").size > 1000)
            assertFalse(outputNames().contains("long.mp3"))
        } finally {
            context.unbindService(connection)
        }
    }
}
