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

    /**
     * Probe input audio bytes; return duration in seconds, or -1.0 if the
     * input is not valid audio. Production impl uses real ffmpeg; test
     * fakes return a fixed duration.
     */
    fun probeDuration(inputBytes: ByteArray, inputFormat: String): Double
}

/**
 * Production ffmpeg transcoder — Kotlin wrapper around the native libraries
 * (libavformat, libavcodec, libavutil, libswresample, libswscale,
 * libavfilter, libavdevice). The native implementation is in
 * app/src/main/cpp/ffmpeg_jni.cpp.
 *
 * M3.5: real ffmpeg calls via JNI. transcode() probes the input and
 * returns the input bytes unchanged (passthrough; re-encoding requires
 * rebuilding ffmpeg with --enable-encoder, deferred).
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
        System.loadLibrary("openconverter_ffmpeg_jni")
    }

    /**
     * Probe input audio bytes; return duration in seconds.
     * Returns -1.0 if input is not valid audio.
     */
    override fun probeDuration(inputBytes: ByteArray, inputFormat: String): Double =
        probeDurationNative(inputBytes, inputFormat)

    /**
     * JNI symbol: Java_com_openconverter_app_ffmpeg_FfmpegBridge_transcode.
     * M3 passthrough: probes input, then returns input bytes unchanged.
     */
    external override fun transcode(
        inputBytes: ByteArray,
        inputFormat: String,
        outputFormat: String,
        bitrateKbps: Int,
    ): ByteArray

    private external fun probeDurationNative(
        inputBytes: ByteArray,
        inputFormat: String,
    ): Double
}
