package com.openconverter.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCheckTest {
    private val json = """
        [
          {"tag_name":"v0.3.8","assets":[{"name":"setup.exe","browser_download_url":"https://example/setup"}]},
          {"tag_name":"v1.4.2-android","assets":[
            {"name":"openconverter-v1.4.2-android-arm64-v8a.apk","browser_download_url":"https://example/1.4.2-arm64"},
            {"name":"openconverter-v1.4.2-android-x86_64.apk","browser_download_url":"https://example/1.4.2-x86"}
          ]},
          {"tag_name":"v1.4.3-android","assets":[
            {"name":"openconverter-v1.4.3-android-arm64-v8a.apk","browser_download_url":"https://example/1.4.3-arm64"},
            {"name":"openconverter-v1.4.3-android-x86_64.apk","browser_download_url":"https://example/1.4.3-x86"}
          ]}
        ]
    """.trimIndent()

    @Test
    fun picksTheNewestApkForThisAbi() {
        val asset = ReleaseCheck.newestApk(json, "arm64-v8a")
        assertEquals("1.4.3", asset?.version)
        assertEquals("https://example/1.4.3-arm64", asset?.apkUrl)
    }

    @Test
    fun ignoresReleasesWithoutAMatchingApk() {
        assertNull(ReleaseCheck.newestApk(json, "armeabi-v7a"))
        assertFalse(ReleaseCheck.isNewer("1.4.3", "1.4.3"))
        assertTrue(ReleaseCheck.isNewer("1.4.4", "1.4.3"))
    }
}
