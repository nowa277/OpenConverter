#!/usr/bin/env bash
# Run JUnit + connectedAndroidTest on a local emulator.
# Before instrumented tests, pushes a real NCM test file to the emulator.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source ~/.bashrc
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/25.2.9519653}"
ADB="$ANDROID_HOME/platform-tools/adb"

# Start emulator if not running
if ! "$ADB" devices 2>/dev/null | grep -q "emulator"; then
    echo "Starting emulator test_avd..."
    "$ANDROID_HOME/emulator/emulator" -avd test_avd \
        -no-window -no-audio -no-snapshot -gpu swiftshader_indirect \
        > /tmp/emulator.log 2>&1 &
fi

"$ADB" wait-for-device
echo "Waiting for boot..."
"$ADB" shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'

# Push a real NCM test file from tests/ncm_format/ (or dist/) to the emulator
# so the EndToEndConversionTest can find it at /data/local/tmp/test-input.ncm
NCM_SOURCE=""
if [ -f "$ROOT/tests/ncm_format/01.ncm" ]; then
    NCM_SOURCE="$ROOT/tests/ncm_format/01.ncm"
elif [ -f "$ROOT/dist/$(ls -1 $ROOT/dist/*.ncm 2>/dev/null | head -1 | xargs -n1 basename)" ]; then
    NCM_SOURCE="$ROOT/dist/$(ls -1 $ROOT/dist/*.ncm 2>/dev/null | head -1 | xargs -n1 basename)"
fi

if [ -n "$NCM_SOURCE" ] && [ -f "$NCM_SOURCE" ]; then
    echo "Pushing NCM test input: $NCM_SOURCE"
    "$ADB" push "$NCM_SOURCE" /data/local/tmp/test-input.ncm
    "$ADB" shell chmod 644 /data/local/tmp/test-input.ncm
else
    echo "WARN: no NCM test file found in tests/ncm_format/ or dist/"
    echo "      EndToEndConversionTest will be skipped"
fi

cd "$ROOT/android"
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest

echo "All Android tests passed."
