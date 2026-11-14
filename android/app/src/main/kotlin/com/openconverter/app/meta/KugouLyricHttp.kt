package com.openconverter.app.meta

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * KuGou lyrics HTTP client for Android.
 * References: 52pojie thread-453069, verified on 2026-11-11.
 * Keyword search discovered live on 2026-11-13 (server reveals a `keyword`
 * parameter; Chinese queries return candidates, English single words may not).
 * Mirrors the desktop client (`src/main/kugou-lyric-http.js`): keyword
 * ranking is loose — first hit may be a remix — so when the audio duration
 * is known the candidate with the nearest `duration` wins.
 */
object KugouLyricHttp {
    private const val USER_AGENT = "Mozilla/5.0"
    private const val DEFAULT_TIMEOUT_MS = 15_000

    data class Candidate(val id: String, val accesskey: String, val durationMs: Long?)
    data class HttpResponse(val status: Int, val body: String)

    /**
     * Test seam: when set, all HTTP goes through this instead of
     * HttpURLConnection. Must return null on transport failure.
     */
    @Volatile
    internal var httpGet: ((url: String, timeoutMs: Int) -> HttpResponse?)? = null

    private fun httpGet(url: String, timeoutMs: Int): HttpResponse? {
        httpGet?.let { return it(url, timeoutMs) }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", USER_AGENT)
                connect()
            }
            if (conn.responseCode !in 200..299) return null
            HttpResponse(conn.responseCode, conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun fetchLyricByHash(hash: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String? {
        val h = hash.trim()
        if (h.isEmpty()) return null
        val candidate = searchCandidate(h, timeoutMs) ?: return null
        return downloadLyric(candidate.id, candidate.accesskey, timeoutMs)
    }

    /**
     * Search by tags and download the matching lyric. Mirrors the desktop
     * `fetchKugouLyricByTags`: keyword = "artist title" (artist optional),
     * nearest-duration candidate preferred. Returns null on any failure —
     * same best-effort contract as the NetEase path.
     */
    fun fetchLyricByTags(
        title: String,
        artist: String? = null,
        durationSec: Double? = null,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): String? {
        val keyword = listOfNotNull(
            artist?.trim()?.takeIf { trimmed -> trimmed.isNotEmpty() },
            title.trim().takeIf { trimmed -> trimmed.isNotEmpty() },
        ).joinToString(" ")
        if (keyword.isBlank()) return null
        val candidates = searchCandidatesByKeyword(keyword, timeoutMs)
        if (candidates.isEmpty()) return null
        val durMs = durationSec?.times(1000)
        val best = if (durMs != null && durMs > 0) {
            candidates.filter { it.durationMs != null }
                .minByOrNull { kotlin.math.abs(it.durationMs!! - durMs) }
                ?: candidates.first()
        } else {
            candidates.first()
        }
        if (best.id.isBlank() || best.accesskey.isBlank()) return null
        return downloadLyric(best.id, best.accesskey, timeoutMs)
    }

    fun searchCandidatesByKeyword(keyword: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): List<Candidate> {
        val kw = keyword.trim()
        if (kw.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(kw, "UTF-8")
        val response = httpGet(
            "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=$encoded",
            timeoutMs,
        ) ?: return emptyList()
        if (response.status !in 200..299) return emptyList()
        return parseCandidates(response.body)
    }

    private fun parseCandidates(body: String): List<Candidate> {
        return try {
            val json = JSONObject(body)
            if (json.optInt("status") != 200) return emptyList()
            val arr = json.optJSONArray("candidates") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = c.optString("id")
                val accesskey = c.optString("accesskey")
                val duration = if (c.has("duration") && !c.isNull("duration")) c.optLong("duration") else null
                if (id.isNotBlank() && accesskey.isNotBlank()) Candidate(id, accesskey, duration) else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun searchCandidate(hash: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Candidate? {
        val encoded = URLEncoder.encode(hash.trim(), "UTF-8")
        val response = httpGet(
            "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$encoded",
            timeoutMs,
        ) ?: return null
        if (response.status !in 200..299) return null
        return parseCandidates(response.body).firstOrNull()
    }

    fun downloadLyric(id: String, accesskey: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String? {
        val encodedId = URLEncoder.encode(id.trim(), "UTF-8")
        val encodedKey = URLEncoder.encode(accesskey.trim(), "UTF-8")
        val response = httpGet(
            "https://lyrics.kugou.com/download?ver=1&client=pc&id=$encodedId&accesskey=$encodedKey&fmt=lrc&charset=utf8",
            timeoutMs,
        ) ?: return null
        if (response.status !in 200..299) return null
        return parseLyricContent(response.body)
    }

    private fun parseLyricContent(body: String): String? {
        return try {
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
        }
    }
}
