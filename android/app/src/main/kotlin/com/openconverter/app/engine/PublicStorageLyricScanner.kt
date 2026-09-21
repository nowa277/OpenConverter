package com.openconverter.app.engine

import java.io.File

object PublicStorageLyricScanner {
    fun scanRoots(dirs: List<File>, maxDepth: Int = 3): List<File> {
        val found = LinkedHashSet<File>()
        for (dir in dirs) {
            collect(dir, maxDepth, found)
        }
        return found.toList()
    }

    private fun collect(start: File, maxDepth: Int, found: MutableSet<File>) {
        if (!start.exists() || !start.canRead()) return
        runCatching {
            start.walkTopDown()
                .maxDepth(maxDepth)
                .onEnter { it.isDirectory && it.canRead() }
                .forEach { file ->
                    if (!file.isDirectory) return@forEach
                    when {
                        file.name == "LrcDownload" || file.name == "LrcCache" -> {
                            file.parentFile?.let { found += it }
                        }
                        hasLyricChild(file) -> found += file
                    }
                }
        }
    }

    private fun hasLyricChild(dir: File): Boolean {
        val children = dir.listFiles() ?: return false
        return children.any { it.isDirectory && (it.name == "LrcDownload" || it.name == "LrcCache") }
    }
}
