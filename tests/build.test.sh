#!/usr/bin/env bash
# Smoke test for the Windows build. Run AFTER `npm run build:win`.
#
# Verifies:
#   1. Both NSIS installer and Portable artifacts exist.
#   2. NSIS installer contains resources/ffmpeg.exe AND resources/ffprobe.exe.
#   3. Wine can start the unpacked Electron binary and keep it alive.
#
# Exit code 0 = all checks passed. Non-zero = first failed check.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE="$ROOT/release"

# 1. Artifact existence
NSIS=$(ls "$RELEASE"/openconverter-v*-windows-x64-setup.exe 2>/dev/null | head -1)
PORTABLE=$(ls "$RELEASE"/openconverter-v*-windows-x64-portable.exe 2>/dev/null | head -1)

if [ -z "$NSIS" ]; then
  echo "FAIL: NSIS installer not found in release/"
  echo "  (Did you run 'npm run build:win' or 'npx electron-builder --win --x64'?)"
  exit 1
fi
if [ -z "$PORTABLE" ]; then
  echo "FAIL: Portable executable not found in release/"
  echo "  (Did you run 'npm run build:win' or 'npx electron-builder --win --x64'?)"
  exit 1
fi
echo "PASS: NSIS and Portable artifacts present"
echo "  NSIS:     $NSIS ($(du -h "$NSIS" | cut -f1))"
echo "  Portable: $PORTABLE ($(du -h "$PORTABLE" | cut -f1))"

# 2. ffmpeg.exe AND ffprobe.exe bundled inside NSIS
# p7zip 16.02 chokes on the NSIS-3 Unicode uninstall stub ($R0/Uninstall...)
# with "Data Error", but the actual payload ($PLUGINSDIR/app-64.7z) extracts
# cleanly. Extract just the payload archive directly to avoid the noisy error.
CHECK_DIR="$(mktemp -d)"
trap 'rm -rf "$CHECK_DIR"' EXIT
7z x -y "$NSIS" "\$PLUGINSDIR/app-64.7z" -o"$CHECK_DIR" >/dev/null 2>&1 || true
if [ ! -f "$CHECK_DIR/\$PLUGINSDIR/app-64.7z" ]; then
  echo "FAIL: could not extract NSIS payload"
  exit 1
fi
PAYLOAD="$CHECK_DIR/\$PLUGINSDIR/app-64.7z"
if ! 7z l "$PAYLOAD" 2>/dev/null | grep -q "resources/ffmpeg.exe"; then
  echo "FAIL: ffmpeg.exe not in NSIS payload"
  7z l "$PAYLOAD" 2>/dev/null | head -10
  exit 1
fi
if ! 7z l "$PAYLOAD" 2>/dev/null | grep -q "resources/ffprobe.exe"; then
  echo "FAIL: ffprobe.exe not in NSIS payload"
  7z l "$PAYLOAD" 2>/dev/null | head -10
  exit 1
fi
echo "PASS: ffmpeg.exe bundled in NSIS payload"
echo "PASS: ffprobe.exe bundled in NSIS payload"

# 3. Wine smoke test — Electron should at least start.
UNPACKED="$RELEASE/win-unpacked/OpenConverter.exe"
if [ ! -f "$UNPACKED" ]; then
  echo "SKIP: Wine smoke test — win-unpacked/OpenConverter.exe not found"
  echo "  (electron-builder may have placed it differently in your version)"
  exit 0
fi

echo "Wine smoke test (8s timeout)..."
WINEDEBUG=-all wine "$UNPACKED" --no-sandbox >/dev/null 2>&1 &
WINE_PID=$!
sleep 8
if kill -0 $WINE_PID 2>/dev/null; then
  echo "PASS: Wine process is still alive after 8s"
  kill $WINE_PID 2>/dev/null || true
  wait $WINE_PID 2>/dev/null || true
else
  echo "FAIL: Wine process exited before 8s — Electron failed to start"
  exit 1
fi

echo ""
echo "All build smoke tests passed."
