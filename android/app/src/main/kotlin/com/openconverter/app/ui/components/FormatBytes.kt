package com.openconverter.app.ui.components

/** Human-readable byte size. Negative or unknown (SAF providers that omit SIZE) -> empty. */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return ""
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val rounded = (value * 10.0).toLong().toDouble() / 10.0
    val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() + ".0" else rounded.toString()
    return "$text ${units[unit]}"
}
