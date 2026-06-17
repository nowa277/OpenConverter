cmake_minimum_required(VERSION 3.22.1)
project("openconverter_ffmpeg_jni")

add_library(openconverter_ffmpeg_jni SHARED
    ffmpeg_jni.cpp
    ffmpeg_transcode.c
)

# Include ffmpeg headers from the cross-compile build cache. These are
# produced by scripts/build-ffmpeg.sh and live under
# ~/.cache/ffmpeg-android-build/build-${ANDROID_ABI}/include/.
# The .so files in jniLibs/ are runtime-loaded via System.loadLibrary()
# in FfmpegBridge.kt; the headers here are only needed at compile time.
set(FFMPEG_INCLUDE_DIR "$ENV{HOME}/.cache/ffmpeg-android-build/build-${ANDROID_ABI}/include")
target_include_directories(openconverter_ffmpeg_jni PRIVATE "${FFMPEG_INCLUDE_DIR}")

# Find Android log lib (for __android_log_print)
find_library(log-lib log)

# Import the prebuilt libav*.so from the parent jniLibs/<abi>/ directory.
# CMake's IMPORTED_LOCATION lets us link against a .so at a known path
# without rebuilding. We use ${ANDROID_ABI} (set by AGP per-ABI).
add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavformat.so"
)
add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavcodec.so"
)
add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavutil.so"
)
add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libswresample.so"
)
add_library(avfilter SHARED IMPORTED)
set_target_properties(avfilter PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libavfilter.so"
)

target_link_libraries(openconverter_ffmpeg_jni
    avformat
    avcodec
    avutil
    swresample
    avfilter
    ${log-lib}
)
