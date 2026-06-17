package com.openconverter.app.ffmpeg

/**
 * Kotlin wrapper around the native ffmpeg libraries (libavformat, libavcodec,
 * libavutil, libswresample, libswscale, libavfilter, libavdevice).
 *
 * The native implementation is in app/src/main/cpp/ffmpeg_jni.cpp.
 * It links against the prebuilt .so files (in jniLibs/<abi>/).
 *
 * M1 scope: this is a STUB. The actual ffmpeg transcode logic is filled
 * in by Task 3.5. For M1, the stub returns the input bytes unchanged
 * so the NcmDecoder → FfmpegBridge pipeline can be exercised end-to-end.
 *
 * M1 test path: NcmDecoder.decrypt(ncm) → AudioData (raw MP3 bytes) →
 * FfmpegBridge.transcode(audio.bytes, "mp3", "mp3", 256) → byte[] (passthrough).
 * We then ffprobe the output to verify it's a valid MP3 with non-zero
 * duration (M1 acceptance).
 */
object FfmpegBridge {
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
     * Stub: returns input bytes unchanged. Real implementation in Task 3.5
     * will use ffmpeg's avcodec API to transcode between formats.
     */
    external fun transcode(
        inputBytes: ByteArray,
        inputFormat: String,
        outputFormat: String,
        bitrateKbps: Int,
    ): ByteArray
}
