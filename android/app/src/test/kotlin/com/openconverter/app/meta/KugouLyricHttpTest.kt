package com.openconverter.app.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KugouLyricHttp keyword-search tests — all HTTP through the [KugouLyricHttp.httpGet]
 * test seam (no network). Mirrors the desktop `tests/kugou-lyric-http.test.js`.
 */
class KugouLyricHttpTest {

    private val urls = mutableListOf<String>()

    private fun fakeFetch(respond: (String) -> KugouLyricHttp.HttpResponse?): (String, Int) -> KugouLyricHttp.HttpResponse? {
        return { url: String, _: Int ->
            urls.add(url)
            respond(url)
        }
    }

    private fun searchBody(vararg candidates: Triple<String, String, Long>): String {
        val arr = candidates.joinToString(",") { (id, key, dur) ->
            """{"id":"$id","accesskey":"$key","duration":$dur,"song":"晴天","singer":"周杰伦"}"""
        }
        return """{"status":200,"candidates":[$arr]}"""
    }

    private fun downloadBody(text: String): String {
        val b64 = java.util.Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        return """{"status":200,"content":"$b64"}"""
    }

    private fun reset() {
        urls.clear()
        KugouLyricHttp.httpGet = null
    }

    @Test fun `fetchByTags searches by keyword and returns LRC`() {
        reset()
        KugouLyricHttp.httpGet = fakeFetch({ url ->
            when {
                url.contains("/search") -> KugouLyricHttp.HttpResponse(200, searchBody(Triple("699230137", "AK1", 116000L)))
                url.contains("/download") -> KugouLyricHttp.HttpResponse(200, downloadBody("[ti:晴天]\n[00:01.00]歌词"))
                else -> null
            }
        })
        val lrc = KugouLyricHttp.fetchLyricByTags("晴天", "周杰伦", 269.5)
        assertTrue(lrc!!.contains("[ti:晴天]"))
        assertTrue(urls.any { it.contains("keyword=" + java.net.URLEncoder.encode("周杰伦 晴天", "UTF-8")) })
        assertTrue(urls.any { it.contains("ver=1&man=yes&client=pc") })
        reset()
    }

    @Test fun `duration aware pick chooses nearest candidate`() {
        reset()
        KugouLyricHttp.httpGet = fakeFetch({ url ->
            when {
                url.contains("/search") -> KugouLyricHttp.HttpResponse(200, searchBody(
                    Triple("REMIX", "A", 116000L), Triple("ORIGINAL", "B", 269300L)))
                url.contains("/download") -> KugouLyricHttp.HttpResponse(200, downloadBody("[ti:晴天]"))
                else -> null
            }
        })
        KugouLyricHttp.fetchLyricByTags("晴天", "周杰伦", 269.5)
        assertTrue(urls.last().contains("id=ORIGINAL"))
        reset()
    }

    @Test fun `falls back to first candidate when duration unknown`() {
        reset()
        KugouLyricHttp.httpGet = fakeFetch({ url ->
            when {
                url.contains("/search") -> KugouLyricHttp.HttpResponse(200, searchBody(Triple("FIRST", "A", 116000L)))
                url.contains("/download") -> KugouLyricHttp.HttpResponse(200, downloadBody("[ti:x]"))
                else -> null
            }
        })
        KugouLyricHttp.fetchLyricByTags("晴天")
        assertTrue(urls.last().contains("id=FIRST"))
        reset()
    }

    @Test fun `empty keyword makes no network call`() {
        reset()
        var called = false
        KugouLyricHttp.httpGet = { _, _ -> called = true; null }
        assertNull(KugouLyricHttp.fetchLyricByTags(""))
        assertNull(KugouLyricHttp.fetchLyricByTags(" ", " "))
        assertEquals(false, called)
        reset()
    }

    @Test fun `search and download failures return null`() {
        reset()
        KugouLyricHttp.httpGet = fakeFetch({ _ -> KugouLyricHttp.HttpResponse(500, "oops") })
        assertNull(KugouLyricHttp.fetchLyricByTags("晴天"))
        KugouLyricHttp.httpGet = fakeFetch({ _ -> KugouLyricHttp.HttpResponse(200, searchBody()) })
        assertNull(KugouLyricHttp.fetchLyricByTags("晴天"))
        reset()
    }

    @Test fun `krc1 payload delegates to decoder without crashing`() {
        reset()
        val badKrc = java.util.Base64.getEncoder().encodeToString(ByteArray(32))
        KugouLyricHttp.httpGet = fakeFetch({ url ->
            when {
                url.contains("/search") -> KugouLyricHttp.HttpResponse(200, searchBody(Triple("1", "K", 200000L)))
                url.contains("/download") -> KugouLyricHttp.HttpResponse(200, """{"status":200,"content":"$badKrc"}""")
                else -> null
            }
        })
        // KrcDecoder owns the decode contract; the client must simply not throw.
        KugouLyricHttp.fetchLyricByTags("晴天")
        reset()
    }

    @Test fun `search failures return null without throwing`() {
        reset()
        KugouLyricHttp.httpGet = { _, _ -> null }
        assertEquals(0, KugouLyricHttp.searchCandidatesByKeyword("晴天").size)
        assertNull(KugouLyricHttp.fetchLyricByTags("晴天"))
        reset()
    }
}
