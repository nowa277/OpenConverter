cmake_minimum_required(VERSION 3.22.1)
project("openconverter_ffmpeg_jni")

# The prebuilt libav*.so files live in jniLibs/<abi>/. We build a tiny JNI
# bridge .so that loads the ffmpeg symbols (avcodec_send_packet, etc.)
# and exposes them to Kotlin via the FfmpegBridge.external funcs.

add_library(openconverter_ffmpeg_jni SHARED ffmpeg_jni.cpp)

# Link to Android log library (for __android_log_print)
find_library(log-lib log)
target_link_libraries(openconverter_ffmpeg_jni ${log-lib})

# For Task 3.5: the real ffmpeg integration will use avcodec. The prebuilt
# .so files (libavformat.so, libavcodec.so, etc.) are linked at runtime by
# System.loadLibrary() calls in FfmpegBridge.kt — Android's dynamic linker
# resolves the dependencies automatically.
