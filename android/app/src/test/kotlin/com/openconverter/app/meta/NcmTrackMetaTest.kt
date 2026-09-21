package com.openconverter.app.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NcmTrackMetaTest {
    @Test fun maps_title_artist_album_and_musicId() {
        val json = """{"musicName":"Hello","artist":[["A",1],["B",2]],"album":"X","musicId":1406472218}"""
        val meta = NcmTrackMeta.fromJson(json)
        assertEquals("Hello", meta.tags["title"])
        assertEquals("A / B", meta.tags["artist"])
        assertEquals("X", meta.tags["album"])
        assertEquals("1406472218", meta.musicId)
    }

    @Test fun artist_may_be_plain_strings() {
        val meta = NcmTrackMeta.fromJson("""{"artist":["Only"]}""")
        assertEquals("Only", meta.tags["artist"])
        assertNull(meta.musicId)
    }

    @Test fun blank_or_invalid_json_is_empty() {
        assertTrue(NcmTrackMeta.fromJson(null).tags.isEmpty())
        assertNull(NcmTrackMeta.fromJson(null).musicId)
        assertTrue(NcmTrackMeta.fromJson("{").tags.isEmpty())
        assertTrue(NcmTrackMeta.fromJson("{}").tags.isEmpty())
    }

    @Test fun musicId_may_be_string() {
        assertEquals("99", NcmTrackMeta.fromJson("""{"musicId":"99"}""").musicId)
    }

    @Test fun cover_extension_sniffs_png_else_jpg() {
        assertEquals("png", NcmTrackMeta.coverExtension(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertEquals("jpg", NcmTrackMeta.coverExtension(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertEquals("jpg", NcmTrackMeta.coverExtension(byteArrayOf()))
    }
}
