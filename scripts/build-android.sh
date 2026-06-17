#!/usr/bin/env bash
# One-shot: build signed release APKs for all 3 ABIs.
# Usage: bash scripts/build-android.sh
#
# Steps:
#   1. Verify environment (JDK 17, Android SDK, NDK)
#   2. Build ffmpeg .so for 3 ABIs (decoder-only, no external libs)
#   3. Build signed release APKs
#   4. Copy to release/ for distribution
#
# v0.3.0 changes from v0.2.2:
#   - Step 2 switched from self-build ffmpeg (decoder-only) to
#     install-ffmpeg-kit 6.0 full-gpl (decoder + 5 encoders)
#   - APK artifact names: v0.2.2 -> v0.3.x
#   - Per-ABI APK size grows from ~3-4 MB to ~28-52 MB (ffmpeg-kit)
#
# v0.3.1 (hotfix): add ALOGE logging + SafAdapter MIME fix + output path UI
#
# Output: release/openconverter-v<version>-android-{arm64-v8a,armeabi-v7a,x86_64}.apk
VERSION="${VERSION:-v0.3.1}"
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# 1. Verify environment
source ~/.bashrc
: "${ANDROID_HOME:=$HOME/Android/Sdk}"
: "${ANDROID_NDK_HOME:=$ANDROID_HOME/ndk/25.2.9519653}"
export ANDROID_HOME ANDROID_NDK_HOME

if [ ! -x "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]; then
    echo "ERROR: NDK not found at $ANDROID_NDK_HOME" >&2
    exit 1
fi
if [ ! -d "$ANDROID_HOME/platforms/android-34" ]; then
    echo "ERROR: Android SDK platform 34 not found at $ANDROID_HOME" >&2
    exit 1
fi

# 2. Install ffmpeg-kit 6.0 .so for 3 ABIs (decoder + encoder + libmp3lame)
echo "[$(date +%H:%M:%S)] Step 1/3: Installing ffmpeg-kit 6.0 .so (3 ABIs)..."
cd "$ROOT/android"
"$ROOT/android/scripts/build-ffmpeg.sh"
echo "ffmpeg-kit install complete."

# 3. Build signed release APKs
echo "[$(date +%H:%M:%S)] Step 2/3: Building signed release APKs..."
export JAVA_HOME="$HOME/.local/jdk/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleRelease

# 4. Copy to release/ for distribution
echo "[$(date +%H:%M:%S)] Step 3/3: Copying APKs to release/..."
APK_DIR="$ROOT/android/app/build/outputs/apk/release"
mkdir -p "$ROOT/release"
cp "$APK_DIR"/openconverter-v${VERSION}-android-*.apk "$ROOT/release/"

echo ""
echo "========================================"
echo "BUILD COMPLETE (${VERSION})"
echo "========================================"
ls -lh "$ROOT/release"/openconverter-v${VERSION}-android-*.apk
