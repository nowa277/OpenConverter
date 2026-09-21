package com.openconverter.app.ffmpeg

/** FFMETADATA1 helper matching `src/main/ffmetadata.js`. */
object Ffmetadata {
    private val ESCAPE = Regex("""[\\=;#\n]""")

    fun escape(value: String): String = value.replace(ESCAPE) { "\\${it.value}" }

    fun bytes(meta: Map<String, String>): ByteArray {
        val lines = ArrayList<String>(meta.size + 1)
        lines += ";FFMETADATA1"
        for ((k, v) in meta) {
            if (v.isEmpty()) continue
            lines += "$k=${escape(v)}"
        }
        return (lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
    }
}
