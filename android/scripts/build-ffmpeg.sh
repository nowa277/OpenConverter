#!/usr/bin/env bash
# Build libffmpeg.so for Android from source.
# Output: android/app/src/main/jniLibs/<abi>/libffmpeg.so
set -euo pipefail

FFMPEG_VERSION="7.0.2"
FFMPEG_SHA256="5f0fb39e7e822ea9737fa9b4c19cf52d9aacf5b3bf2f4b0c1bdef7cdd5ce6fa6"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.xz"

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME not set}"
ANDROID_API=26
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JNI_LIBS="${SCRIPT_DIR}/../app/src/main/jniLibs"
BUILD_CACHE="${HOME}/.cache/ffmpeg-android-build"
SRC_DIR="${BUILD_CACHE}/ffmpeg-${FFMPEG_VERSION}"

mkdir -p "${BUILD_CACHE}" "${JNI_LIBS}"

# Download + extract if not cached
if [ ! -d "${SRC_DIR}" ]; then
    echo "Downloading ffmpeg ${FFMPEG_VERSION}..."
    TARBALL="${BUILD_CACHE}/ffmpeg.tar.xz"
    wget -q -O "${TARBALL}" "${FFMPEG_URL}"
    echo "${FFMPEG_SHA256}  ${TARBALL}" | sha256sum -c -
    tar -xJf "${TARBALL}" -C "${BUILD_CACHE}"
fi

# Cross-compile per ABI
for ABI in arm64-v8a armeabi-v7a x86_64; do
    OUTPUT="${JNI_LIBS}/${ABI}/libffmpeg.so"
    if [ -f "${OUTPUT}" ]; then
        echo "Skipping ${ABI}: already built"
        continue
    fi
    mkdir -p "${JNI_LIBS}/${ABI}"

    case "${ABI}" in
        arm64-v8a)   TRIPLE="aarch64-linux-android";   ARCH="aarch64";   CPU="armv8-a"   ;;
        armeabi-v7a) TRIPLE="armv7a-linux-androideabi"; ARCH="arm";       CPU="armv7-a"   ;;
        x86_64)      TRIPLE="x86_64-linux-android";    ARCH="x86_64";    CPU="x86_64"    ;;
    esac

    TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
    PREFIX="${BUILD_CACHE}/build-${ABI}"
    mkdir -p "${PREFIX}"

    echo "Configuring ffmpeg for ${ABI}..."
    cd "${SRC_DIR}"
    ./configure \
        --prefix="${PREFIX}" \
        --enable-shared \
        --disable-static \
        --disable-programs \
        --disable-doc \
        --disable-everything \
        --enable-protocol=file \
        --enable-demuxer=mp3,flac,wav,ogg,m4a,aac,opus \
        --enable-muxer=mp3,flac,wav,ogg,m4a,mp4 \
        --enable-parser=mpegaudio,flac,vorbis,aac \
        --enable-encoder=libmp3lame,libvorbis,libfdk_aac,flac \
        --enable-decoder=mp3,flac,vorbis,aac,opus \
        --enable-libmp3lame \
        --enable-libvorbis \
        --enable-libfdk-aac \
        --enable-cross-compile \
        --cross-prefix="${TOOLCHAIN}/bin/${TRIPLE}-" \
        --sysroot="${TOOLCHAIN}/sysroot" \
        --target-os=android \
        --arch="${ARCH}" \
        --cpu="${CPU}" \
        --cc="${TOOLCHAIN}/bin/${TRIPLE}${ANDROID_API}-clang" \
        --cxx="${TOOLCHAIN}/bin/${TRIPLE}${ANDROID_API}-clang++" \
        --extra-cflags="-Os -fPIC" \
        --extra-ldflags="-Wl,-z,defs -Wl,--no-undefined" \
        >"${BUILD_CACHE}/configure-${ABI}.log" 2>&1

    echo "Building for ${ABI}..."
    make -j"$(nproc)" clean >/dev/null 2>&1
    make -j"$(nproc)" >"${BUILD_CACHE}/build-${ABI}.log" 2>&1
    make install >"${BUILD_CACHE}/install-${ABI}.log" 2>&1

    cp "${PREFIX}/lib/libffmpeg.so" "${OUTPUT}"
    "${TOOLCHAIN}/bin/llvm-strip" --strip-unneeded "${OUTPUT}"
    echo "Built: ${OUTPUT} ($(du -h "${OUTPUT}" | cut -f1))"
done

echo "ffmpeg build complete."
