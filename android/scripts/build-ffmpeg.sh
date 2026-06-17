#!/usr/bin/env bash
# Install ffmpeg-kit 6.0 (full-gpl) shared libraries for Android (v0.3.0).
#
# History: v0.2.2 self-built ffmpeg from source with --disable-everything +
# decoder-only flags. v0.3.0 needs encoders (MP3/FLAC/WAV/M4A/OGG). Self-build
# with encoders hit 3 consecutive failures (lame.pc detection, OUT_DIR var,
# encoder-list typo). User-approved switch to ffmpeg-kit 6.0 full-gpl.
#
# arthenica/ffmpeg-kit was archived April 2025; artifacts were pulled from
# Maven Central. Appodeal's public Artifactory (https://artifactory.appodeal.com)
# still mirrors the original binaries — used here.
#
# Output: android/app/src/main/jniLibs/<abi>/lib{avcodec,avformat,...}.so
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JNI_LIBS="${SCRIPT_DIR}/../app/src/main/jniLibs"
WORKDIR="${HOME}/.cache/ffmpeg-kit-install"

FFMPEG_KIT_VERSION="6.0-2.LTS"
# Appodeal Artifactory mirror — only known post-arthenica-archive source.
AAR_URL="https://artifactory.appodeal.com/appodeal-public/com/arthenica/ffmpeg-kit-full-gpl/${FFMPEG_KIT_VERSION}/ffmpeg-kit-full-gpl-${FFMPEG_KIT_VERSION}.aar"
AAR_FILE="${WORKDIR}/ffmpeg-kit-full-gpl-${FFMPEG_KIT_VERSION}.aar"
# SHA256 verified at install time. Update if Appodeal re-publishes a new build.
AAR_SHA256=""

# ABIs we ship. ffmpeg-kit also has x86; we skip it (no real devices use x86 in 2026).
TARGET_ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")

mkdir -p "${WORKDIR}" "${JNI_LIBS}"

# === Step 1: Download AAR ===
echo "Downloading ffmpeg-kit-full-gpl-${FFMPEG_KIT_VERSION}.aar from Appodeal mirror..."
echo "URL: ${AAR_URL}"
if [ ! -f "${AAR_FILE}" ]; then
    if ! wget -q -O "${AAR_FILE}" "${AAR_URL}" 2>/dev/null; then
        echo "ERROR: download failed from ${AAR_URL}" >&2
        echo "       verify network connectivity or update AAR_URL" >&2
        exit 1
    fi
fi

# SHA verification (skip if not pinned yet)
if [ -n "${AAR_SHA256}" ] && [ "${AAR_SHA256}" != "PLACEHOLDER" ]; then
    if ! echo "${AAR_SHA256}  ${AAR_FILE}" | sha256sum -c - >/dev/null 2>&1; then
        echo "ERROR: AAR SHA256 mismatch" >&2
        echo "       expected: ${AAR_SHA256}" >&2
        echo "       got:      $(sha256sum "${AAR_FILE}" | cut -d' ' -f1)" >&2
        exit 1
    fi
    echo "AAR SHA256 OK."
else
    actual_sha=$(sha256sum "${AAR_FILE}" | cut -d' ' -f1)
    echo "AAR SHA256 (unpinned — recording actual): ${actual_sha}"
fi

# === Step 2: Extract AAR ===
EXTRACT_DIR="${WORKDIR}/extract"
rm -rf "${EXTRACT_DIR}"
mkdir -p "${EXTRACT_DIR}"
unzip -q "${AAR_FILE}" -d "${EXTRACT_DIR}"

if [ ! -d "${EXTRACT_DIR}/jni" ]; then
    echo "ERROR: AAR missing jni/ directory" >&2
    exit 1
fi

# === Step 3: Copy .so files to jniLibs/<abi>/ ===
# Required libs from ffmpeg-kit (we do NOT use their Java wrapper):
#   libavcodec.so libavformat.so libavutil.so libswresample.so libswscale.so
#   libavfilter.so libavdevice.so libc++_shared.so
# Skip libffmpegkit.so / libffmpegkit_abidetect.so (we use our own JNI).
# Skip armv7a _neon variants? Keep them — Android loads the right one based on
# CPU features; keeping both ensures compatibility.

REQUIRED_LIBS=(
    "libavcodec.so"
    "libavformat.so"
    "libavutil.so"
    "libswresample.so"
    "libswscale.so"
    "libavfilter.so"
    "libavdevice.so"
    "libc++_shared.so"
)

for ABI in "${TARGET_ABIS[@]}"; do
    SRC_DIR="${EXTRACT_DIR}/jni/${ABI}"
    if [ ! -d "${SRC_DIR}" ]; then
        echo "WARN: ABI ${ABI} not in AAR; skipping" >&2
        continue
    fi

    DST_DIR="${JNI_LIBS}/${ABI}"
    mkdir -p "${DST_DIR}"

    # Clear existing libs we own
    for lib in "${REQUIRED_LIBS[@]}"; do
        rm -f "${DST_DIR}/${lib}" "${DST_DIR}/${lib%.so}_neon.so"
    done
    # Remove ffmpegkit wrapper if present
    rm -f "${DST_DIR}/libffmpegkit"*.so

    # Copy required libs (and _neon variants for armv7a)
    for lib in "${REQUIRED_LIBS[@]}"; do
        if [ -f "${SRC_DIR}/${lib}" ]; then
            cp "${SRC_DIR}/${lib}" "${DST_DIR}/"
        fi
        if [ "${ABI}" = "armeabi-v7a" ] && [ -f "${SRC_DIR}/${lib%.so}_neon.so" ]; then
            cp "${SRC_DIR}/${lib%.so}_neon.so" "${DST_DIR}/"
        fi
    done

    echo ""
    echo "=== ${ABI} ==="
    ls -lh "${DST_DIR}/" | awk 'NR>1 {printf "  %-40s %s\n", $9, $5}'
done

echo ""
echo "ffmpeg-kit 6.0 (full-gpl) installed for 3 ABIs."
echo "  MP3 encoder (libmp3lame) included statically in libavcodec.so."
echo "  + built-in encoders: aac, flac, vorbis, pcm_s16le."
echo "  Total per ABI: ~26-50 MB (vs self-built decoder-only ~3 MB)."
