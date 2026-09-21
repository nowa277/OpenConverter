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
}
