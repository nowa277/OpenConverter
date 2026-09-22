package com.openconverter.app.update

import java.net.HttpURLConnection
import java.net.URL

object ApkUpdate {
    const val RELEASES =
        "https://api.github.com/repos/nowa277/OpenConverter/releases?per_page=30"

    fun fetchNewest(abi: String): ReleaseAsset? {
        val connection = open(RELEASES)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return ReleaseCheck.newestApk(body, abi)
    }

    fun download(url: String, dest: java.io.File) {
        dest.parentFile?.mkdirs()
        val connection = open(url)
        connection.setRequestProperty("Accept", "application/octet-stream")
        connection.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
        if (dest.length() < 1024L) {
            dest.delete()
            error("empty package")
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "OpenConverter")
        return connection
    }
}
