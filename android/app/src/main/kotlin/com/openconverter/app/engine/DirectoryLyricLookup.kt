package com.openconverter.app.engine

import com.openconverter.app.meta.NeteaseLyricParser

class DirectoryLyricLookup(
    private val open: (relativePath: String) -> ByteArray?,
) : LyricLookupPort {
    override fun findLrcBytes(musicId: String): ByteArray? {
        if (musicId.isBlank()) return null
        val raw = open("LrcDownload/$musicId") ?: open("LrcCache/$musicId") ?: open(musicId) ?: return null
        val lrc = NeteaseLyricParser.toLrc(raw) ?: return null
        return lrc.toByteArray(Charsets.UTF_8)
    }
}
