package com.openconverter.app.meta

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NeteaseLyricHttp {
    private const val USER_AGENT = "Mozilla/5.0"
    private const val REFERER = "https://music.163.com/"

    private val defaultOpen: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }

    fun fetchJson(
        musicId: String,
        timeoutMs: Int = 15_000,
        open: (URL) -> HttpURLConnection = defaultOpen,
    ): String? {
        val id = musicId.trim()
        if (id.isEmpty()) return null
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val url = URL("https://music.163.com/api/song/lyric?id=$encodedId&lv=-1&kv=-1&tv=-1")
        var conn: HttpURLConnection? = null
        return try {
            conn = open(url)
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", REFERER)
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
