package com.openconverter.app.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

    @Test fun download_reports_progress_and_writes_full_package() {
        val root = java.nio.file.Files.createTempDirectory("oc-update-test-").toFile()
        val destination = File(root, "update.apk")
        val progressReports = mutableListOf<Pair<Long, Long>>()
        val testData = ByteArray(2048) { (it % 128).toByte() }
        try {
            val connection = object : HttpURLConnection(URL("https://example.test/update.apk")) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getContentLengthLong() = 2048L
                override fun getInputStream(): InputStream = ByteArrayInputStream(testData)
            }
            ApkUpdate.download(
                "https://example.test/update.apk",
                destination,
                onProgress = { cur, total -> progressReports.add(cur to total) },
                connect = { connection }
            )
            assertArrayEquals(testData, destination.readBytes())
            assertTrue(progressReports.isNotEmpty())
            assertEquals(2048L, progressReports.last().first)
            assertEquals(2048L, progressReports.last().second)
        } finally { root.deleteRecursively() }
    }

    @Test fun download_falls_back_to_mirror_when_primary_fails() {
        val root = java.nio.file.Files.createTempDirectory("oc-update-test-").toFile()
        val destination = File(root, "update.apk")
        val testData = ByteArray(1500) { 42 }
        val attemptedUrls = mutableListOf<String>()
        try {
            ApkUpdate.download(
                "https://github.com/nowa277/OpenConverter/releases/download/v1.4.5/app.apk",
                destination,
                connect = { urlStr ->
                    attemptedUrls.add(urlStr)
                    if (urlStr.startsWith("https://github.com/")) {
                        throw IOException("GitHub timeout")
                    }
                    object : HttpURLConnection(URL(urlStr)) {
                        override fun connect() {}
                        override fun disconnect() {}
                        override fun usingProxy() = false
                        override fun getResponseCode() = 200
                        override fun getContentLengthLong() = 1500L
                        override fun getInputStream(): InputStream = ByteArrayInputStream(testData)
                    }
                }
            )
            assertArrayEquals(testData, destination.readBytes())
            assertTrue(attemptedUrls.size >= 2)
            assertEquals("https://github.com/nowa277/OpenConverter/releases/download/v1.4.5/app.apk", attemptedUrls[0])
            assertTrue(attemptedUrls[1].contains("ghfast.top") || attemptedUrls[1].contains("ghproxy"))
        } finally { root.deleteRecursively() }
    }
}

