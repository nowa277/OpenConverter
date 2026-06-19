package com.openconverter.app.decoders

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSnifferTest {
    @Test fun flac_magic() = assertEquals("flac", FormatSniffer.sniff(byteArrayOf(0x66, 0x4c, 0x61, 0x43)))
    @Test fun mp3_id3() = assertEquals("mp3", FormatSniffer.sniff(byteArrayOf(0x49, 0x44, 0x33, 0x00)))
    @Test fun mp3_frame_sync() = assertEquals("mp3", FormatSniffer.sniff(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x00, 0x00)))
    @Test fun ogg_magic() = assertEquals("ogg", FormatSniffer.sniff(byteArrayOf(0x4f, 0x67, 0x67, 0x53)))
    @Test fun riff_wav() = assertEquals("wav", FormatSniffer.sniff(byteArrayOf(0x52, 0x49, 0x46, 0x46)))
    @Test fun m4a_ftyp_at_offset4() = assertEquals("m4a", FormatSniffer.sniff(byteArrayOf(0,0,0,0, 0x66,0x74,0x79,0x70)))
    @Test fun aac_adts() = assertEquals("aac", FormatSniffer.sniff(byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x00, 0x00)))
    @Test fun too_short_defaults_mp3() = assertEquals("mp3", FormatSniffer.sniff(byteArrayOf(0x00)))
    @Test fun unknown_defaults_mp3() = assertEquals("mp3", FormatSniffer.sniff(byteArrayOf(0x12, 0x34, 0x56, 0x78)))
}
