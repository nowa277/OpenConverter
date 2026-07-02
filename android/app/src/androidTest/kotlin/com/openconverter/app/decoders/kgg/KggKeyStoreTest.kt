package com.openconverter.app.decoders.kgg

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KggKeyStoreTest {
    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        root = File(context.cacheDir, "kgg-store-test-${System.nanoTime()}").apply { mkdirs() }
        filesDir = File(root, "files").apply { mkdirs() }
        cacheDir = File(root, "cache").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun imports_plain_sqlite_by_content_despite_text_extension() = runBlocking {
        val database = File(root, "misleading.txt")
        createDatabase(database, mapOf("db-id" to "db-key", "second" to "value"))
        val store = newStore()

        val result = store.import(Uri.fromFile(database))

        assertEquals(KggImportResult(added = 2, updated = 0, total = 2), result)
        assertEquals("db-key", store.find("db-id"))
        assertEquals(2, (store.state.value as KggImportState.Ready).total)
        assertNoImportTemps()
    }

    @Test
    fun imports_text_merges_updates_and_restores_after_recreation() = runBlocking {
        val first = File(root, "first.db").apply {
            writeText("same\$one\nchanged\$old\n")
        }
        val second = File(root, "second.key").apply {
            writeText("same\$one\nchanged\$new\nadded\$value\n")
        }
        val store = newStore()

        store.import(Uri.fromFile(first))
        val result = store.import(Uri.fromFile(second))
        val restored = newStore()

        assertEquals(KggImportResult(added = 1, updated = 1, total = 3), result)
        assertEquals("one", restored.find("same"))
        assertEquals("new", restored.find("changed"))
        assertEquals("value", restored.find("added"))
        assertEquals(3, (restored.state.value as KggImportState.Ready).total)
        assertNoImportTemps()
    }

    @Test
    fun malformed_text_fails_without_changing_memory_or_disk() = runBlocking {
        val valid = File(root, "valid.key").apply { writeText("kept\$value\n") }
        val malformed = File(root, "malformed.key").apply { writeText("broken-line\n") }
        val store = newStore()
        store.import(Uri.fromFile(valid))

        assertImportFails { store.import(Uri.fromFile(malformed)) }

        assertEquals("value", store.find("kept"))
        assertNull(store.find("broken-line"))
        assertEquals(1, (store.state.value as KggImportState.Failed).total)
        assertEquals("value", newStore().find("kept"))
        assertNoImportTemps()
    }

    @Test
    fun sqlite_without_key_table_fails_without_replacing_existing_keys() = runBlocking {
        val valid = File(root, "valid.key").apply { writeText("kept\$value\n") }
        val invalidDatabase = File(root, "invalid.db")
        createDatabase(invalidDatabase, emptyMap(), createTable = false)
        val store = newStore()
        store.import(Uri.fromFile(valid))

        assertImportFails { store.import(Uri.fromFile(invalidDatabase)) }

        assertEquals("value", store.find("kept"))
        assertEquals("value", newStore().find("kept"))
        assertNoImportTemps()
    }

    private fun newStore(): KggKeyStore = KggKeyStore(filesDir, cacheDir) { uri ->
        FileInputStream(requireNotNull(uri.path))
    }

    private fun createDatabase(
        file: File,
        entries: Map<String, String>,
        createTable: Boolean = true,
    ) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA page_size=1024")
            if (createTable) {
                db.execSQL("CREATE TABLE ShareFileItems (EncryptionKeyId TEXT, EncryptionKey TEXT)")
                entries.forEach { (id, key) ->
                    db.execSQL(
                        "INSERT INTO ShareFileItems (EncryptionKeyId, EncryptionKey) VALUES (?, ?)",
                        arrayOf(id, key),
                    )
                }
            } else {
                db.execSQL("CREATE TABLE Unrelated (value TEXT)")
            }
        } finally {
            db.close()
        }
    }

    private suspend fun assertImportFails(block: suspend () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue("Expected import to fail", failed)
    }

    private fun assertNoImportTemps() {
        assertTrue(
            cacheDir.listFiles().orEmpty().none { it.name.startsWith("kgg-import-") },
        )
    }
}
