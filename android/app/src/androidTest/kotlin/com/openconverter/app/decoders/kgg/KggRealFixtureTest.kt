package com.openconverter.app.decoders.kgg

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KggRealFixtureTest {
    @Test
    fun decrypts_real_fixtures_equal_to_reference() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val fixtureArg = arguments.getString("kggFixtureDir")
        val keyArg = arguments.getString("kggKeySource")
        val expectedArg = arguments.getString("kggExpectedDir")
        val supplied = listOf(fixtureArg, keyArg, expectedArg).count { !it.isNullOrBlank() }
        assumeTrue("real KGG fixture arguments are not configured", supplied != 0)
        require(supplied == 3) { "kggFixtureDir, kggKeySource and kggExpectedDir must be provided together" }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fixtureRoot = resolve(context.filesDir, requireNotNull(fixtureArg))
        val keySource = resolve(context.filesDir, requireNotNull(keyArg))
        val expectedRoot = resolve(context.filesDir, requireNotNull(expectedArg))
        require(fixtureRoot.isDirectory) { "KGG fixture directory does not exist" }
        require(keySource.isFile) { "KGG key source does not exist" }
        require(expectedRoot.isDirectory) { "KGG expected directory does not exist" }

        val workRoot = File(context.cacheDir, "kgg-real-${System.nanoTime()}").apply { mkdirs() }
        try {
            val store = KggKeyStore(File(workRoot, "store"), File(workRoot, "import")) { uri ->
                FileInputStream(requireNotNull(uri.path))
            }
            store.import(keySource.toURI().toString())
            val decoder = KggV5Decoder(store)
            val fixtures = fixtureRoot.walkTopDown()
                .filter { it.isFile && KGG_NAME.containsMatchIn(it.name) }
                .sortedBy(File::getPath)
                .toList()
            assertTrue("No KGG fixtures discovered", fixtures.isNotEmpty())

            fixtures.forEachIndexed { index, encrypted ->
                val relative = encrypted.relativeTo(fixtureRoot).invariantSeparatorsPath
                val output = File(workRoot, "decoded-$index")
                val format = encrypted.inputStream().use { input ->
                    output.outputStream().use { decoded -> decoder.decrypt(input, decoded) }
                }
                val expectedRelative = KGG_NAME.replace(relative, "") + ".$format"
                val expected = File(expectedRoot, expectedRelative)
                require(expected.isFile) { "Missing expected output for $relative: $expectedRelative" }
                assertArrayEquals("SHA-256 mismatch for $relative", expected.sha256(), output.sha256())
                output.delete()
            }
        } finally {
            workRoot.deleteRecursively()
        }
    }

    private fun resolve(filesDir: File, value: String): File =
        File(value).let { if (it.isAbsolute) it else File(filesDir, value) }

    private fun File.sha256(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    companion object {
        private val KGG_NAME = Regex("(?i)\\.kgg(?:\\.[^/]+)?$")
    }
}
