package com.openconverter.app.ffmpeg

/**
 * Pure interface describing what the orchestrator needs from ffmpeg.
 *
 * Defined so JVM unit tests can inject a fake transcoder (e.g. a passthrough
 * stub) without ever loading the native libraries. The real production
 * implementation is [FfmpegBridge] (an `object` — JVM-instantiated once,
 * loads the .so files in its `init` block).
 *
 * Why an interface, not just a stub: the JNI load in FfmpegBridge's `init`
 * block runs on first class-init, and a pure JVM test that touches
 * FfmpegBridge.transcode will UnsatisfiedLinkError before the test body
 * executes. Routing the orchestrator through this interface keeps the test
 * JVM free of native deps.
 */
interface FfmpegTranscoder {
    fun transcode(
        inputBytes: ByteArray,
        inputFormat: String,
        outputFormat: String,
        bitrateKbps: Int,
    ): ByteArray
}

/**
 * Production ffmpeg transcoder — Kotlin wrapper around the native libraries
 * (libavformat, libavcodec, libavutil, libswresample, libswscale,
 * libavfilter, libavdevice). The native implementation is in
 * app/src/main/cpp/ffmpeg_jni.cpp.
 *
 * M3.1 scope: this is still a STUB. The actual ffmpeg transcode logic is
 * filled in by Task 3.5. The stub returns the input bytes unchanged so the
 * NcmDecoder → FfmpegBridge pipeline can be exercised end-to-end.
 */
object FfmpegBridge : FfmpegTranscoder {
    init {
        // Load in dependency order. Android's System.loadLibrary() resolves
        // transitive deps, but explicit ordering is documentation.
        System.loadLibrary("avutil")
        System.loadLibrary("swresample")
        System.loadLibrary("swscale")
        System.loadLibrary("avcodec")
        System.loadLibrary("avformat")
        System.loadLibrary("avfilter")
        System.loadLibrary("avdevice")
    }

    /**
     * JNI symbol: Java_com_openconverter_ffmpeg_FfmpegBridge_transcode.
     * Package in C signature is the historic `com.openconverter.ffmpeg` —
     * M3.5 will fix the JNI package binding to match our current
     * `com.openconverter.app.ffmpeg` location. Until then, the JNI stub
     * is not actually invoked at runtime; this code is reachable only on
     * an emulator build with a matching JNI package.
     */
    external override fun transcode(
        inputBytes: ByteArray,
        inputFormat: String,
        outputFormat: String,
        bitrateKbps: Int,
    ): ByteArray
}
