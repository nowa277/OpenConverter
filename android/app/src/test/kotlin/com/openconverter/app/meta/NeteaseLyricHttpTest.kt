package com.openconverter.app.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class NeteaseLyricHttpTest {
    @Test
    fun blank_id_returns_null_without_opening_connection() {
        var opened = 0
        val open: (URL) -> HttpURLConnection = { url ->
            opened += 1
            FakeConnection(url, 200, "{}")
        }
        assertNull(NeteaseLyricHttp.fetchJson("", open = open))
        assertNull(NeteaseLyricHttp.fetchJson("   ", open = open))
        assertEquals(0, opened)
    }

    @Test
    fun get_uses_https_spec_url_headers_and_timeout() {
        var captured: FakeConnection? = null
        val body = NeteaseLyricHttp.fetchJson("186016", timeoutMs = 15_000) { url ->
            FakeConnection(url, 200, "{\"ok\":1}").also { captured = it }
        }
        assertEquals("{\"ok\":1}", body)
        val conn = captured!!
        assertEquals("GET", conn.requestMethod)
        assertEquals(15_000, conn.connectTimeout)
        assertEquals(15_000, conn.readTimeout)
        assertEquals("Mozilla/5.0", conn.getRequestProperty("User-Agent"))
        assertEquals("https://music.163.com/", conn.getRequestProperty("Referer"))
        assertEquals("https", conn.url.protocol)
        assertEquals(
            "https://music.163.com/api/song/lyric?id=186016&lv=-1&kv=-1&tv=-1",
            conn.url.toString(),
        )
        assertEquals(setOf("id", "lv", "kv", "tv"), queryKeys(conn.url))
        assertEquals("186016", queryValue(conn.url, "id"))
        assertEquals(1, conn.connectCount)
        assertFalse(conn.instanceFollowRedirects)
    }

    @Test
    fun default_timeout_is_15s() {
        var captured: FakeConnection? = null
        val body = NeteaseLyricHttp.fetchJson("1") { url ->
            FakeConnection(url, 200, "{}").also { captured = it }
        }
        assertEquals("{}", body)
        assertEquals(15_000, captured!!.connectTimeout)
        assertEquals(15_000, captured!!.readTimeout)
    }

    @Test
    fun status_2xx_returns_utf8_body() {
        val body = """{"lrc":{"lyric":"你好"}}"""
        assertEquals(body, NeteaseLyricHttp.fetchJson("1") { FakeConnection(it, 200, body) })
        assertEquals(body, NeteaseLyricHttp.fetchJson("1") { FakeConnection(it, 201, body) })
        assertEquals(body, NeteaseLyricHttp.fetchJson("1") { FakeConnection(it, 299, body) })
    }

    @Test
    fun non_2xx_returns_null() {
        assertNull(NeteaseLyricHttp.fetchJson("1") { FakeConnection(it, 404, "no") })
        assertNull(NeteaseLyricHttp.fetchJson("1") { FakeConnection(it, 500, "err") })
        assertNull(NeteaseLyricHttp.fetchJson("1") { FakeConnection(it, 199, "x") })
        assertNull(NeteaseLyricHttp.fetchJson("1") { FakeConnection(it, 300, "x") })
    }

    @Test
    fun exception_returns_null() {
        assertNull(
            NeteaseLyricHttp.fetchJson("1") {
                FakeConnection(it, 200, "{}", throwOnConnect = true)
            },
        )
        assertNull(
            NeteaseLyricHttp.fetchJson("1") {
                throw IOException("dns")
            },
        )
    }

    @Test
    fun one_attempt_only() {
        var opened = 0
        val result = NeteaseLyricHttp.fetchJson("1") {
            opened += 1
            FakeConnection(it, 500, "no")
        }
        assertNull(result)
        assertEquals(1, opened)
    }

    private fun queryKeys(url: URL): Set<String> =
        url.query.split("&").map { it.substringBefore("=") }.toSet()

    private fun queryValue(url: URL, key: String): String? =
        url.query.split("&").map { it.split("=", limit = 2) }
            .firstOrNull { it[0] == key }?.getOrNull(1)
}

private class FakeConnection(
    url: URL,
    private val status: Int,
    private val body: String,
    private val throwOnConnect: Boolean = false,
) : HttpURLConnection(url) {
    var connectCount = 0
        private set

    override fun connect() {
        connectCount += 1
        if (throwOnConnect) throw IOException("connect failed")
        connected = true
    }

    override fun disconnect() {
        connected = false
    }

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int {
        if (!connected) connect()
        return status
    }

    override fun getInputStream(): InputStream {
        if (!connected) connect()
        if (status !in 200..299) throw IOException("http $status")
        return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }
}
