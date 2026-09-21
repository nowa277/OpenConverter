package com.openconverter.app.engine

import com.openconverter.app.meta.NeteaseLyricParser
import java.io.File

class NeteaseLyricResolver(
    private val enabled: Boolean,
    private val extraRoots: List<File>,
    private val fetchJson: (String) -> String?,
    private val open: (root: File, rel: String) -> ByteArray?,
) : LyricResolverPort {
    override suspend fun resolveLrc(musicId: String): String? {
        if (!enabled || musicId.isBlank()) return null
        for (root in extraRoots) {
            val hit = DirectoryLyricLookup { rel -> open(root, rel) }.findLrcBytes(musicId)
            if (hit != null) return hit.toString(Charsets.UTF_8)
        }
        val json = fetchJson(musicId) ?: return null
        return NeteaseLyricParser.toLrc(json.toByteArray())
    }
}
