package com.openconverter.app.meta

import org.json.JSONArray
import org.json.JSONObject

data class NcmTrackMeta(
    val tags: Map<String, String>,
    val musicId: String?,
) {
    companion object {
        fun fromJson(json: String?): NcmTrackMeta {
            if (json.isNullOrBlank()) return NcmTrackMeta(emptyMap(), null)
            val obj = try { JSONObject(json) } catch (_: Exception) {
                return NcmTrackMeta(emptyMap(), null)
            }
            val tags = linkedMapOf<String, String>()
            obj.optString("musicName").trim().takeIf { it.isNotEmpty() }?.let { tags["title"] = it }
            parseArtists(obj.opt("artist"))?.let { tags["artist"] = it }
            obj.optString("album").trim().takeIf { it.isNotEmpty() }?.let { tags["album"] = it }
            val musicId = when {
                obj.has("musicId") && !obj.isNull("musicId") -> obj.get("musicId").toString().trim()
                else -> ""
            }.takeIf { it.isNotEmpty() }
            return NcmTrackMeta(tags, musicId)
        }

        fun coverExtension(image: ByteArray): String =
            if (image.size >= 2 && image[0] == 0x89.toByte() && image[1] == 0x50.toByte()) "png" else "jpg"

        private fun parseArtists(raw: Any?): String? {
            if (raw == null || raw == JSONObject.NULL) return null
            if (raw is JSONArray) {
                val names = ArrayList<String>(raw.length())
                for (i in 0 until raw.length()) {
                    val item = raw.opt(i)
                    val name = when (item) {
                        is JSONArray -> item.optString(0)
                        else -> item?.toString().orEmpty()
                    }.trim()
                    if (name.isNotEmpty()) names += name
                }
                return names.takeIf { it.isNotEmpty() }?.joinToString(" / ")
            }
            return raw.toString().trim().takeIf { it.isNotEmpty() }
        }
    }
}
