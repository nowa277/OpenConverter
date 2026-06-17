package com.openconverter.ffmpeg

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sanity check that the NDK build produced the expected ffmpeg shared
 * libraries on disk. Runs in JVM (no Android); it just inspects files.
 *
 * ffmpeg's build produces 7 separate libav*.so / libsw*.so files per ABI
 * (libavformat, libavcodec, libavutil, libswresample, libswscale,
 * libavfilter, libavdevice). We check the sentinel (libavformat.so)
 * for each ABI.
 *
 * Skipped (not failed) if the .so files haven't been built yet — the
 * FfmpegBridgeTest is a developer convenience, not a release gate.
 */
class FfmpegLoadTest {
    @Test
    fun libavformat_exists_for_all_3_abis() {
        for (abi in listOf("arm64-v8a", "armeabi-v7a", "x86_64")) {
            val so = File("src/main/jniLibs/$abi/libavformat.so")
            assumeTrue("libavformat.so not built for $abi (run scripts/build-ffmpeg.sh)", so.exists())
            check(so.length() > 100_000) { "libavformat.so for $abi too small: ${so.length()} bytes" }
        }
    }

    @Test
    fun all_seven_ffmpeg_libs_present_per_abi() {
        val expectedLibs = listOf(
            "libavformat.so",
            "libavcodec.so",
            "libavutil.so",
            "libswresample.so",
            "libswscale.so",
            "libavfilter.so",
            "libavdevice.so",
        )
        for (abi in listOf("arm64-v8a", "armeabi-v7a", "x86_64")) {
            for (lib in expectedLibs) {
                val so = File("src/main/jniLibs/$abi/$lib")
                assumeTrue("$abi/$lib not built (run scripts/build-ffmpeg.sh)", so.exists())
                check(so.length() > 0) { "$abi/$lib is empty" }
            }
        }
    }
}
