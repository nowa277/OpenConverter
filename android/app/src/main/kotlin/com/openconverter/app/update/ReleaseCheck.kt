package com.openconverter.app.update

import org.json.JSONArray

data class ReleaseAsset(val version: String, val apkUrl: String)

object ReleaseCheck {
    fun newestApk(releasesJson: String, abi: String): ReleaseAsset? {
        val releases = JSONArray(releasesJson)
        var best: ReleaseAsset? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            val version = versionOf(release.optString("tag_name")) ?: continue
            val url = pickApk(release.optJSONArray("assets"), abi) ?: continue
            if (best == null || compare(version, best.version) > 0) {
                best = ReleaseAsset(version, url)
            }
        }
        return best
    }

    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    fun versionOf(tag: String): String? {
        val match = Regex("""(\d+)\.(\d+)\.(\d+)""").find(tag) ?: return null
        return match.value
    }

    fun compare(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val d = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    private fun parts(version: String): List<Int> =
        versionOf(version)?.split('.')?.map { it.toInt() } ?: emptyList()

    private fun pickApk(assets: JSONArray?, abi: String): String? {
        if (assets == null) return null
        val apks = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk") }
        val match = apks.firstOrNull { it.optString("name").contains(abi, ignoreCase = true) }
            ?: return null
        return match.optString("browser_download_url").ifBlank { null }
    }
}
