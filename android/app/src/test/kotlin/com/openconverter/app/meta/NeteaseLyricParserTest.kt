package com.openconverter.app.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseLyricParserTest {
    @Test fun nested_json_lines_become_lrc() {
        val inner = """{"t":0,"c":[{"tx":"Hello "}]}
{"t":1230,"c":[{"tx":"World"}]}"""
        val outer = org.json.JSONObject().put("lrc", inner).toString()
        val lrc = NeteaseLyricParser.toLrc(outer.toByteArray(Charsets.UTF_8))
        assertEquals("[00:00.00]Hello \n[00:01.23]World", lrc)
    }

    @Test fun already_lrc_passthrough() {
        val body = "[00:00.00]Hello\n[00:01.23]World"
        val outer = org.json.JSONObject().put("lrc", body).toString()
        assertEquals(body, NeteaseLyricParser.toLrc(outer.toByteArray(Charsets.UTF_8)))
    }

    @Test fun raw_lrc_without_wrapper() {
        val body = "[00:00.00]Hello"
        assertEquals(body, NeteaseLyricParser.toLrc(body.toByteArray(Charsets.UTF_8)))
    }

    @Test fun empty_or_garbage_returns_null() {
        assertNull(NeteaseLyricParser.toLrc(ByteArray(0)))
        assertNull(NeteaseLyricParser.toLrc("{}".toByteArray()))
        assertNull(NeteaseLyricParser.toLrc("not lyrics".toByteArray()))
    }

    @Test fun lrc_object_lyric_field_becomes_lrc() {
        val outer = """{"lrc":{"lyric":"[00:00.00]Hello"}}"""
        assertEquals("[00:00.00]Hello", NeteaseLyricParser.toLrc(outer.toByteArray(Charsets.UTF_8)))
    }

    @Test fun nested_lrc_object_must_not_dump_json() {
        val outer = """{"lrc":{"version":1,"lyric":"[00:00.00]Hello"}}"""
        val lrc = NeteaseLyricParser.toLrc(outer.toByteArray(Charsets.UTF_8))
        assertEquals("[00:00.00]Hello", lrc)
        assertFalse(lrc!!.contains("{"))
        assertFalse(lrc.contains("\"lyric\""))
        assertFalse(lrc.contains("version"))
    }

    @Test fun decodes_html_entities_in_lyrics() {
        val input = "[00:01.00]Tom &amp; Jerry &quot;Show&quot; &#39;Special&#39; &lt;Live&gt;"
        assertEquals("[00:01.00]Tom & Jerry \"Show\" 'Special' <Live>", NeteaseLyricParser.toLrc(input.toByteArray()))
    }

    @Test fun returns_null_for_pure_music_placeholder_lyrics() {
        assertNull(NeteaseLyricParser.toLrc("""{"nolyric":true}""".toByteArray()))
        assertNull(NeteaseLyricParser.toLrc("""{"lrc":{"lyric":"[00:00.00]纯音乐，请欣赏"}}""".toByteArray()))
        assertNull(NeteaseLyricParser.toLrc("""{"lrc":{"lyric":"[00:00.00]本歌曲为纯音乐，无歌词\n[00:01.00]作词 : 纯音乐"}}""".toByteArray()))
        assertNull(NeteaseLyricParser.toLrc("""{"lrc":{"lyric":"[00:00.00]该歌曲为纯音乐，请欣赏"}}""".toByteArray()))
        assertNull(NeteaseLyricParser.toLrc("""{"lrc":{"lyric":"[00:00.00]暂无歌词"}}""".toByteArray()))
    }

    @Test fun merges_bilingual_lyrics_by_timestamp_in_chronological_order() {
        val input = """{
            "lrc":{"lyric":"[00:01.00]First line\n[00:05.00]Second line"},
            "tlyric":{"lyric":"[00:01.00]第一行\n[00:05.00]第二行"}
        }"""
        val expected = "[00:01.00]First line\n[00:01.00]第一行\n[00:05.00]Second line\n[00:05.00]第二行"
        assertEquals(expected, NeteaseLyricParser.toLrc(input.toByteArray()))
    }

    @Test fun bilingual_merges_with_varying_timestamp_precision_and_metadata_headers() {
        val input = """{
            "lrc":{"lyric":"[ti:Test Title]\n[ar:Artist]\n[00:01.5]First\n[00:04.120]Second"},
            "tlyric":{"lyric":"[00:01.50]第一\n[00:04.12]第二"}
        }"""
        val expected = "[ti:Test Title]\n[ar:Artist]\n[00:01.5]First\n[00:01.50]第一\n[00:04.120]Second\n[00:04.12]第二"
        assertEquals(expected, NeteaseLyricParser.toLrc(input.toByteArray()))
    }

    @Test fun decodes_hex_and_decimal_html_entities() {
        val input = "[00:01.00]&#65;&#66;&#67; &#x44;&#x45;&#x46; &apos;apostrophe&apos;"
        assertEquals("[00:01.00]ABC DEF 'apostrophe'", NeteaseLyricParser.toLrc(input.toByteArray()))
    }

    @Test fun out_of_order_lines_are_sorted_chronologically() {
        val input = """{
            "lrc":{"lyric":"[00:05.00]Second\n[00:01.00]First"},
            "tlyric":{"lyric":"[00:05.00]第二\n[00:01.00]第一"}
        }"""
        val expected = "[00:01.00]First\n[00:01.00]第一\n[00:05.00]Second\n[00:05.00]第二"
        assertEquals(expected, NeteaseLyricParser.toLrc(input.toByteArray()))
    }
}


