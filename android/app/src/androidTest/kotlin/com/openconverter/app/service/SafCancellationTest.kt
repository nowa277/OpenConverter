package com.openconverter.app.service

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconverter.app.engine.AndroidFileSystemPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SafCancellationTest {
    @Test fun cancelled_output_copy_deletes_the_created_document() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authority = "com.openconverter.app.test.documents"
        context.contentResolver.call(Uri.parse("content://$authority"), "prepare", context.packageName, null)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root")
        val source = File(context.cacheDir, "saf-source.bin").apply { writeBytes(ByteArray(1024 * 1024)) }
        val job = Job()
        var cancelled = false
        val fs = AndroidFileSystemPort(context)
        TestDocumentsProvider.onCreateDocument = { name -> if (name == "cancelled.wav") job.cancel() }
        try {
            launch(job) {
                try {
                    fs.writeOutputFromCache(tree.toString(), "cancelled.wav", "audio/wav", source.path)
                } catch (_: CancellationException) {
                    cancelled = true
                }
            }.join()
            assertTrue(cancelled)
            val children = context.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root"),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
            )!!.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            assertEquals(false, children.contains("cancelled.wav"))
        } finally {
            TestDocumentsProvider.onCreateDocument = null
            source.delete()
        }
    }
}
