package com.openconverter.app.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ApkUpdate {
    const val RELEASES = "https://api.github.com/repos/nowa277/OpenConverter/releases?per_page=30"

    fun fetchNewest(abi: String): ReleaseAsset? {
        val connection = open(RELEASES)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode !in 200..299) throw IOException("release response ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return ReleaseCheck.newestApk(body, abi)
        } finally {
            connection.disconnect()
        }
    }

    fun download(url: String, dest: File, connect: (String) -> HttpURLConnection = ::open) {
        dest.parentFile?.mkdirs()
        val partial = File(dest.path + ".part")
        val connection = connect(url)
        try {
            connection.setRequestProperty("Accept", "application/octet-stream")
            if (connection.responseCode !in 200..299) throw IOException("download response ${connection.responseCode}")
            connection.inputStream.use { input -> partial.outputStream().use { output -> input.copyTo(output, 64 * 1024) } }
            if (partial.length() < 1024L) throw IOException("empty package")
            if (!partial.renameTo(dest)) throw IOException("Cannot replace downloaded package")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("User-Agent", "OpenConverter")
    }
}
