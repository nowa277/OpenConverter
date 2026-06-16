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
CHECK_DIR="$(mktemp -d)"
trap 'rm -rf "$CHECK_DIR"' EXIT
7z x -y "$NSIS" -o"$CHECK_DIR" >/dev/null
if [ ! -f "$CHECK_DIR/resources/ffmpeg.exe" ]; then
  echo "FAIL: ffmpeg.exe not bundled inside NSIS installer"
  echo "  contents of extracted root:"
  ls "$CHECK_DIR" | head -10
  exit 1
fi
if [ ! -f "$CHECK_DIR/resources/ffprobe.exe" ]; then
  echo "FAIL: ffprobe.exe not bundled inside NSIS installer"
  echo "  contents of resources/:"
  ls "$CHECK_DIR/resources/" | head -10
  exit 1
fi
echo "PASS: ffmpeg.exe bundled ($(du -h "$CHECK_DIR/resources/ffmpeg.exe" | cut -f1))"
echo "PASS: ffprobe.exe bundled ($(du -h "$CHECK_DIR/resources/ffprobe.exe" | cut -f1))"

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
