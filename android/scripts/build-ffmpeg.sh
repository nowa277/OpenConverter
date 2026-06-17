#!/usr/bin/env bash
# Build libffmpeg.so for Android from source (M1: decoder-only).
#
# M1 strategy: NCM/KGM/KWM decryption produces raw audio bytes (e.g. MP3 or
# FLAC). The NCM→MP3 path is a passthrough — decrypt + ffmpeg copy codec, no
# re-encoding. So M1 only needs ffmpeg's built-in *decoders* (no external
# libs: no libmp3lame, no libfdk-aac, no libvorbis).
#
# Encoders (libmp3lame, libfdk-aac, libvorbis for OGG) will be added in
# Task 3.5 (real ffmpeg JNI for transcoding) when M3 actually needs to
# re-encode between formats.
#
# Output: android/app/src/main/jniLibs/<abi>/libffmpeg.so
set -euo pipefail

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME not set}"
ANDROID_API=26
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JNI_LIBS="${SCRIPT_DIR}/../app/src/main/jniLibs"
BUILD_CACHE="${HOME}/.cache/ffmpeg-android-build"

FFMPEG_VERSION="7.0.2"
FFMPEG_SHA256="8646515b638a3ad303e23af6a3587734447cb8fc0a0c064ecdb8e95c4fd8b389"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.xz"

TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
NDK_SYSROOT="${TOOLCHAIN}/sysroot"

mkdir -p "${BUILD_CACHE}" "${JNI_LIBS}"

# Download + extract ffmpeg (idempotent: skip if already extracted)
TARBALL="${BUILD_CACHE}/ffmpeg.tar.xz"
SRC_DIR="${BUILD_CACHE}/ffmpeg-${FFMPEG_VERSION}"
if [ ! -d "${SRC_DIR}" ]; then
    echo "Downloading ffmpeg ${FFMPEG_VERSION}..."
    # Quiet wget: log goes to stderr, file to stdout
    wget -q -O "${TARBALL}" "${FFMPEG_URL}" 2>/dev/null || {
        echo "ERROR: ffmpeg download failed from ${FFMPEG_URL}" >&2
        exit 1
    }
    echo "${FFMPEG_SHA256}  ${TARBALL}" | sha256sum -c - >&2 || {
        echo "ERROR: ffmpeg SHA256 mismatch" >&2
        exit 1
    }
    tar -xJf "${TARBALL}" -C "${BUILD_CACHE}" >&2
fi

# Cross-compile for each ABI
for ABI in arm64-v8a armeabi-v7a x86_64; do
    # ffmpeg's build produces multiple libav*.so files (libavformat, libavcodec,
    # libavutil, libswresample, libswscale, libavfilter, libavdevice) — NOT a
    # single libffmpeg.so. We check for libavformat.so as the "is this ABI built?"
    # sentinel since it's the one our JNI loads first.
    SENTINEL="${JNI_LIBS}/${ABI}/libavformat.so"
    if [ -f "${SENTINEL}" ]; then
        echo "Skipping ${ABI}: already built"
        continue
    fi
    mkdir -p "${JNI_LIBS}/${ABI}"

    case "${ABI}" in
        arm64-v8a)   TRIPLE="aarch64-linux-android";   ARCH="aarch64";   CPU="armv8-a"   ;;
        armeabi-v7a) TRIPLE="armv7a-linux-androideabi"; ARCH="arm";       CPU="armv7-a"   ;;
        x86_64)      TRIPLE="x86_64-linux-android";    ARCH="x86_64";    CPU="x86_64"    ;;
    esac

    PREFIX="${BUILD_CACHE}/build-${ABI}"
    LOG_DIR="${BUILD_CACHE}/logs-ffmpeg-${ABI}"
    mkdir -p "${PREFIX}" "${LOG_DIR}"

    echo ""
    echo "=== Configuring ffmpeg for ${ABI} (decoder-only) ==="
    cd "${SRC_DIR}"
    make distclean >/dev/null 2>&1 || true

    # DECODER-ONLY configuration for M1.
    # No --enable-encoder, no --enable-libmp3lame, no --enable-libfdk-aac,
    # no --enable-libvorbis — ffmpeg's built-in audio decoders are sufficient
    # for M1 (NCM decrypt + MP3/FLAC/etc passthrough). Encoders come in M3.
    ./configure \
        --prefix="${PREFIX}" \
        --enable-shared \
        --disable-static \
        --disable-programs \
        --disable-doc \
        --disable-everything \
        --enable-protocol=file \
        --enable-demuxer=mp3,flac,wav,ogg,m4a,aac,opus \
        --enable-parser=mpegaudio,flac,vorbis,aac \
        --enable-decoder=mp3,flac,vorbis,aac,opus \
        --enable-cross-compile \
        --cross-prefix="${TOOLCHAIN}/bin/${TRIPLE}-" \
        --nm="${TOOLCHAIN}/bin/llvm-nm" \
        --ar="${TOOLCHAIN}/bin/llvm-ar" \
        --ranlib="${TOOLCHAIN}/bin/llvm-ranlib" \
        --strip="${TOOLCHAIN}/bin/llvm-strip" \
        --sysroot="${NDK_SYSROOT}" \
        --target-os=android \
        --arch="${ARCH}" \
        --cpu="${CPU}" \
        --cc="${TOOLCHAIN}/bin/${TRIPLE}${ANDROID_API}-clang" \
        --cxx="${TOOLCHAIN}/bin/${TRIPLE}${ANDROID_API}-clang++" \
        --extra-cflags="-Os -fPIC" \
        --extra-ldflags="-Wl,-z,defs -Wl,--no-undefined" \
        >"${LOG_DIR}/configure.log" 2>&1

    echo "=== Building ffmpeg for ${ABI} ==="
    make -j"$(nproc)" >"${LOG_DIR}/build.log" 2>&1
    make install >"${LOG_DIR}/install.log" 2>&1

    # Copy all libav*.so files to jniLibs/<abi>/. JNI will loadLibrary() each
    # one in dependency order (avutil → swresample → avcodec → avformat).
    SO_COUNT=0
    for SO_FILE in "${PREFIX}"/lib/libav*.so "${PREFIX}"/lib/libsw*.so; do
        [ -f "$SO_FILE" ] || continue
        cp "$SO_FILE" "${JNI_LIBS}/${ABI}/"
        "${TOOLCHAIN}/bin/llvm-strip" --strip-unneeded "${JNI_LIBS}/${ABI}/$(basename "$SO_FILE")"
        SO_COUNT=$((SO_COUNT + 1))
    done
    echo "Built ${ABI}: copied ${SO_COUNT} .so files to ${JNI_LIBS}/${ABI}/"
done

echo ""
echo "ffmpeg (decoder-only, M1) build complete."
echo "Note: encoders (libmp3lame, libfdk-aac, libvorbis) are not built."
echo "Re-encoding (e.g. MP3→FLAC) will fail until M3 Task 3.5 adds them."
