package com.openconverter.app.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkUpdateTest {
    @Test fun failed_download_preserves_previous_package_and_closes_connection() {
        val root = java.nio.file.Files.createTempDirectory("oc-update-test-").toFile()
        val destination = File(root, "update.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        var disconnected = false
        try {
            val connection = object : HttpURLConnection(URL("https://example.test/update.apk")) {
                override fun connect() {}
                override fun disconnect() { disconnected = true }
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getInputStream(): InputStream = object : InputStream() {
                    override fun read(): Int = throw IOException("connection lost")
                }
            }
            assertThrows(IOException::class.java) { ApkUpdate.download(connection.url.toString(), destination) { connection } }
            assertArrayEquals(byteArrayOf(1, 2, 3), destination.readBytes())
            assertFalse(File(root, "update.apk.part").exists())
            assertTrue(disconnected)
        } finally { root.deleteRecursively() }
    }
}
