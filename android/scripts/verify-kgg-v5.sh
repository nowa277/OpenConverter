#!/usr/bin/env bash
set -euo pipefail

required=(KGG_FIXTURE_DIR KGG_KEY_SOURCE KGG_EXPECTED_DIR)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Usage: KGG_FIXTURE_DIR=/path/to/kgg KGG_KEY_SOURCE=/path/to/KGMusicV3.db KGG_EXPECTED_DIR=/path/to/reference %s\n' "$0" >&2
    exit 2
  fi
done

[[ -d "$KGG_FIXTURE_DIR" ]] || { printf 'KGG fixture directory does not exist\n' >&2; exit 2; }
[[ -f "$KGG_KEY_SOURCE" ]] || { printf 'KGG key source does not exist\n' >&2; exit 2; }
[[ -d "$KGG_EXPECTED_DIR" ]] || { printf 'KGG expected directory does not exist\n' >&2; exit 2; }

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ANDROID_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
PACKAGE=com.openconverter.app
REMOTE=/data/local/tmp/openconverter-kgg
STAGE=$(mktemp -d)

cleanup() {
  adb shell "rm -rf '$REMOTE'" >/dev/null 2>&1 || true
  adb shell "run-as '$PACKAGE' sh -c 'rm -rf files/kgg-real'" >/dev/null 2>&1 || true
  rm -rf -- "$STAGE"
}
trap cleanup EXIT

mkdir -p "$STAGE/fixtures" "$STAGE/expected" "$STAGE/keys"
fixture_count=0
while IFS= read -r -d '' source; do
  relative=${source#"$KGG_FIXTURE_DIR"/}
  mkdir -p -- "$STAGE/fixtures/$(dirname -- "$relative")"
  cp -L -- "$source" "$STAGE/fixtures/$relative"
  fixture_count=$((fixture_count + 1))
done < <(find -L "$KGG_FIXTURE_DIR" -type f \( -iname '*.kgg' -o -iname '*.kgg.*' \) -print0)
[[ $fixture_count -gt 0 ]] || { printf 'No KGG fixtures found\n' >&2; exit 2; }

while IFS= read -r -d '' source; do
  relative=${source#"$KGG_EXPECTED_DIR"/}
  mkdir -p -- "$STAGE/expected/$(dirname -- "$relative")"
  cp -L -- "$source" "$STAGE/expected/$relative"
done < <(find -L "$KGG_EXPECTED_DIR" -type f -print0)
cp -- "$KGG_KEY_SOURCE" "$STAGE/keys/source"

cd "$ANDROID_DIR"
./gradlew assembleDebug assembleDebugAndroidTest

adb wait-for-device
for _ in $(seq 1 60); do
  boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')
  package_service=$(adb shell service check package 2>/dev/null || true)
  [[ "$boot" == 1 && "$package_service" == *found* ]] && break
  sleep 2
done
[[ $(adb shell getprop sys.boot_completed | tr -d '\r\n') == 1 ]] || { printf 'Android device did not finish booting\n' >&2; exit 1; }

case $(adb shell getprop ro.product.cpu.abi | tr -d '\r\n') in
  x86_64) app_apk=app/build/outputs/apk/debug/app-x86_64-debug.apk ;;
  arm64-v8a) app_apk=app/build/outputs/apk/debug/app-arm64-v8a-debug.apk ;;
  *) printf 'Unsupported device ABI\n' >&2; exit 1 ;;
esac
test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb install -r -t "$app_apk" >/dev/null
adb install -r -t "$test_apk" >/dev/null

adb shell "rm -rf '$REMOTE'; mkdir -p '$REMOTE'"
adb push "$STAGE/." "$REMOTE/" >/dev/null
adb shell "run-as '$PACKAGE' sh -c 'rm -rf files/kgg-real; mkdir -p files/kgg-real; cp -R $REMOTE/fixtures files/kgg-real/; cp -R $REMOTE/expected files/kgg-real/; cp -R $REMOTE/keys files/kgg-real/'"

printf 'Running Pixel/device KGG parity for %d fixture(s)\n' "$fixture_count"
adb shell am instrument -w -r \
  -e class com.openconverter.app.decoders.kgg.KggRealFixtureTest \
  -e kggFixtureDir kgg-real/fixtures \
  -e kggKeySource kgg-real/keys/source \
  -e kggExpectedDir kgg-real/expected \
  "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
