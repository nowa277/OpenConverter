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
# Output: release/openconverter-v0.2.2-android-{arm64-v8a,armeabi-v7a,x86_64}.apk
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

# 2. Build ffmpeg shared libraries (decoder-only; M1/M3 scope)
echo "[$(date +%H:%M:%S)] Step 1/3: Building ffmpeg .so (3 ABIs)..."
cd "$ROOT/android"
"$ROOT/android/scripts/build-ffmpeg.sh"
echo "ffmpeg build complete."

# 3. Build signed release APKs
echo "[$(date +%H:%M:%S)] Step 2/3: Building signed release APKs..."
export JAVA_HOME="$HOME/.local/jdk/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleRelease

# 4. Copy to release/ for distribution
echo "[$(date +%H:%M:%S)] Step 3/3: Copying APKs to release/..."
APK_DIR="$ROOT/android/app/build/outputs/apk/release"
mkdir -p "$ROOT/release"
cp "$APK_DIR"/openconverter-v0.2.2-android-*.apk "$ROOT/release/"

echo ""
echo "========================================"
echo "BUILD COMPLETE"
echo "========================================"
ls -lh "$ROOT/release"/openconverter-v0.2.2-android-*.apk
