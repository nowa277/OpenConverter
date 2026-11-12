package com.openconverter.app.meta

import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * KuGou lyrics HTTP client for Android.
 * References: 52pojie thread-453069, verified on 2026-11-11.
 */
object KugouLyricHttp {
    private const val USER_AGENT = "Mozilla/5.0"

    data class Candidate(val id: String, val accesskey: String)

    fun fetchLyricByHash(hash: String, timeoutMs: Int = 15_000): String? {
        val h = hash.trim()
        if (h.isEmpty()) return null
        val candidate = searchCandidate(h, timeoutMs) ?: return null
        return downloadLyric(candidate.id, candidate.accesskey, timeoutMs)
    }

    fun searchCandidate(hash: String, timeoutMs: Int = 15_000): Candidate? {
        val encoded = URLEncoder.encode(hash.trim(), "UTF-8")
        val url = URL("http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$encoded")
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", USER_AGENT)
                connect()
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("status") != 200) return null
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val first = candidates.optJSONObject(0) ?: return null
            val id = first.optString("id")
            val accesskey = first.optString("accesskey")
            if (id.isNotBlank() && accesskey.isNotBlank()) Candidate(id, accesskey) else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun downloadLyric(id: String, accesskey: String, timeoutMs: Int = 15_000): String? {
        val encodedId = URLEncoder.encode(id.trim(), "UTF-8")
        val encodedKey = URLEncoder.encode(accesskey.trim(), "UTF-8")
        val url = URL("http://lyrics.kugou.com/download?ver=1&client=pc&id=$encodedId&accesskey=$encodedKey&fmt=lrc&charset=utf8")
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", USER_AGENT)
                connect()
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("status") != 200) return null
            val content = json.optString("content")
            if (content.isBlank()) return null
            val raw = java.util.Base64.getDecoder().decode(content)
            if (raw.size >= 4 && raw[0] == 'k'.code.toByte() && raw[1] == 'r'.code.toByte() && raw[2] == 'c'.code.toByte() && raw[3] == '1'.code.toByte()) {
                KrcDecoder.decodeKrcToLrc(raw)
            } else {
                raw.toString(Charsets.UTF_8).removePrefix("﻿").trim()
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
