#!/usr/bin/env bash
# Build libmp3lame.a for Android from source (v0.3.0).
#
# M3 limitation: ffmpeg built with --disable-libmp3lame (no MP3 encoder).
# v0.3.0 enables real MP3 encoding by statically linking libmp3lame into
# libavcodec. This script downloads + NDK cross-compiles lame 3.100 for
# all 3 ABIs as a static .a.
#
# Output: ~/.cache/ffmpeg-android-build/build-lame-<abi>/lib/libmp3lame.a
#
# Risk: SourceForge can be flaky. We try SourceForge first; if SHA
# mismatch → fall back to ubuntu archive. If both fail → user has pre-
# approved ffmpeg-kit 6.0 fallback (a separate, one-time swap).
set -euo pipefail

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME not set}"
ANDROID_API=26
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_CACHE="${HOME}/.cache/ffmpeg-android-build"
LAME_VERSION="3.100"

# Verified in Task 0.2:
#   SourceForge primary: SHA256 = ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e
#   ubuntu archive fallback: https://launchpad.net/ubuntu/+archive/primary/+sourcefiles/lame/3.100-3build2/
LAME_URL_PRIMARY="https://sourceforge.net/projects/lame/files/lame/${LAME_VERSION}/lame-${LAME_VERSION}.tar.gz/download"
LAME_SHA256_PRIMARY="ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e"
LAME_URL_FALLBACK="https://launchpad.net/ubuntu/+archive/primary/+sourcefiles/lame/3.100-3build2/lame_3.100.orig.tar.gz"

TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
NDK_SYSROOT="${TOOLCHAIN}/sysroot"

mkdir -p "${BUILD_CACHE}"

# Try primary URL, verify SHA, fall back if mismatch
TARBALL_PRIMARY="${BUILD_CACHE}/lame-${LAME_VERSION}.tar.gz"
TARBALL_FALLBACK="${BUILD_CACHE}/lame-3.100-orig.tar.gz"
SRC_DIR="${BUILD_CACHE}/lame-${LAME_VERSION}"

download_with_fallback() {
    echo "Trying SourceForge primary URL..."
    if wget -q -O "${TARBALL_PRIMARY}" "${LAME_URL_PRIMARY}" 2>/dev/null; then
        if echo "${LAME_SHA256_PRIMARY}  ${TARBALL_PRIMARY}" | sha256sum -c - >/dev/null 2>&1; then
            echo "SourceForge download OK and SHA matches."
            TARBALL="${TARBALL_PRIMARY}"
            return 0
        else
            echo "SourceForge SHA mismatch; trying ubuntu archive..."
        fi
    else
        echo "SourceForge download failed; trying ubuntu archive..."
    fi

    echo "Trying ubuntu archive..."
    if wget -q -O "${TARBALL_FALLBACK}" "${LAME_URL_FALLBACK}" 2>/dev/null; then
        echo "ubuntu archive download OK (skipping SHA check for mirror)."
        TARBALL="${TARBALL_FALLBACK}"
        return 0
    fi

    echo "ERROR: Both lame sources failed." >&2
    return 1
}

if [ ! -d "${SRC_DIR}" ]; then
    download_with_fallback
    # Extract (handle both naming conventions: lame-3.100/ and lame-3.100+ds/)
    tar -xzf "${TARBALL}" -C "${BUILD_CACHE}" 2>&1 || true
    # Some mirrors use different top-level dir names; normalize
    if [ ! -d "${SRC_DIR}" ]; then
        for d in "${BUILD_CACHE}"/lame-3.100*; do
            if [ -d "$d" ] && [ "$d" != "${SRC_DIR}" ]; then
                mv "$d" "${SRC_DIR}"
                break
            fi
        done
    fi
    if [ ! -d "${SRC_DIR}" ]; then
        echo "ERROR: lame source directory not found after extract" >&2
        exit 1
    fi
fi

# Cross-compile for each ABI
for ABI in arm64-v8a armeabi-v7a x86_64; do
    OUT_DIR="${BUILD_CACHE}/build-lame-${ABI}"
    LIB_FILE="${OUT_DIR}/lib/libmp3lame.a"

    if [ -f "${LIB_FILE}" ]; then
        echo "Skipping ${ABI}: already built (${LIB_FILE})"
        continue
    fi

    case "${ABI}" in
        arm64-v8a)   TRIPLE="aarch64-linux-android"  ;;
        armeabi-v7a) TRIPLE="armv7a-linux-androideabi" ;;
        x86_64)      TRIPLE="x86_64-linux-android"   ;;
    esac

    mkdir -p "${OUT_DIR}"

    cd "${SRC_DIR}"
    if [ -f Makefile ]; then make distclean >/dev/null 2>&1 || true; fi

    # lame uses autotools; standard cross-compile flags
    CC="${TOOLCHAIN}/bin/${TRIPLE}${ANDROID_API}-clang"
    CXX="${TOOLCHAIN}/bin/${TRIPLE}${ANDROID_API}-clang++"
    AR="${TOOLCHAIN}/bin/llvm-ar"
    RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"

    ./configure \
        --host="${TRIPLE}" \
        --prefix="${OUT_DIR}" \
        --enable-static \
        --disable-shared \
        --disable-frontend \
        --disable-decoder \
        --disable-analyzer-hooks \
        --disable-debug \
        --disable-gtktest \
        CC="${CC}" \
        CXX="${CXX}" \
        AR="${AR}" \
        RANLIB="${RANLIB}" \
        CFLAGS="-Os -fPIC" \
        CPPFLAGS="-I${NDK_SYSROOT}/usr/include" \
        >"${BUILD_CACHE}/lame-configure-${ABI}.log" 2>&1 || {
            echo "lame configure failed for ${ABI}; see ${BUILD_CACHE}/lame-configure-${ABI}.log" >&2
            exit 1
        }

    make -j"$(nproc)" \
        >"${BUILD_CACHE}/lame-build-${ABI}.log" 2>&1 || {
        echo "lame build failed for ${ABI}; see ${BUILD_CACHE}/lame-build-${ABI}.log" >&2
        exit 1
    }
    make install \
        >"${BUILD_CACHE}/lame-install-${ABI}.log" 2>&1 || {
        echo "lame install failed for ${ABI}; see ${BUILD_CACHE}/lame-install-${ABI}.log" >&2
        exit 1
    }

    if [ ! -f "${LIB_FILE}" ]; then
        echo "ERROR: libmp3lame.a not produced for ${ABI}" >&2
        exit 1
    fi

    echo "Built ${ABI}: ${LIB_FILE} ($(du -h "${LIB_FILE}" | cut -f1))"
done

echo ""
echo "libmp3lame 3.100 build complete for all 3 ABIs."
echo "Output: ${BUILD_CACHE}/build-lame-<abi>/lib/libmp3lame.a"
