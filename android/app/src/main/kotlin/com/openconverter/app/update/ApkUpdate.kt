package com.openconverter.app.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ApkUpdate {
    const val RELEASES = "https://api.github.com/repos/nowa277/OpenConverter/releases?per_page=30"

    fun mirrorUrls(originalUrl: String): List<String> {
        val urls = mutableListOf(originalUrl)
        if (originalUrl.startsWith("https://github.com/")) {
            urls.add("https://ghfast.top/$originalUrl")
            urls.add("https://ghproxy.net/$originalUrl")
        }
        return urls
    }

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

    fun download(
        url: String,
        dest: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> },
        connect: (String) -> HttpURLConnection = ::open,
    ) {
        dest.parentFile?.mkdirs()
        val partial = File(dest.path + ".part")
        val candidates = mirrorUrls(url)
        var lastException: IOException? = null

        for (candidateUrl in candidates) {
            var connection: HttpURLConnection? = null
            try {
                val conn = connect(candidateUrl)
                connection = conn
                conn.setRequestProperty("Accept", "application/octet-stream")
                if (conn.responseCode !in 200..299) {
                    throw IOException("download response ${conn.responseCode} from $candidateUrl")
                }
                val totalLength = conn.contentLengthLong.takeIf { it > 0 } ?: conn.contentLength.toLong()
                var downloaded = 0L
                val buffer = ByteArray(64 * 1024)

                conn.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, totalLength)
                        }
                    }
                }
                if (partial.length() < 1024L) throw IOException("empty package from $candidateUrl")
                if (!partial.renameTo(dest)) throw IOException("Cannot replace downloaded package")
                return
            } catch (ioe: IOException) {
                lastException = ioe
            } finally {
                connection?.disconnect()
                partial.delete()
            }
        }
        throw lastException ?: IOException("Failed to download package from all sources")
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("User-Agent", "OpenConverter")
    }
}
