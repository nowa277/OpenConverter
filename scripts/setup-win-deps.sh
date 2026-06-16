#!/usr/bin/env bash
# Setup script: download Windows-compatible ffmpeg.exe + ffprobe.exe
# and generate icon.ico for use by electron-builder when building
# Windows installers.
#
# Idempotent: skips steps whose output already exists. Re-run after
# updating the bundled ffmpeg version (e.g., to bump gyan.dev release).
#
# Requires: wget, unzip, ImageMagick (convert). All standard on Debian/Ubuntu.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPS_DIR="$ROOT/scripts/win-deps"
FFMPEG_EXE="$DEPS_DIR/ffmpeg.exe"
FFPROBE_EXE="$DEPS_DIR/ffprobe.exe"
ICON_PNG="$ROOT/build/icons/icon.png"
ICON_ICO="$ROOT/build/icons/icon.ico"

# --- ffmpeg.exe + ffprobe.exe (gyan.dev essentials, latest 7.x) ---
if [ ! -f "$FFMPEG_EXE" ] || [ ! -f "$FFPROBE_EXE" ]; then
  echo "Downloading ffmpeg + ffprobe (gyan.dev essentials build)..."
  mkdir -p "$DEPS_DIR"
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  wget -q -O "$TMP/ffmpeg.zip" \
    "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
  # The zip contains ffmpeg-7.x-essentials_build/bin/{ffmpeg,ffprobe}.exe.
  # Extract both to a known path.
  unzip -j "$TMP/ffmpeg.zip" \
    'ffmpeg-*-essentials_build/bin/ffmpeg.exe' \
    'ffmpeg-*-essentials_build/bin/ffprobe.exe' \
    -d "$DEPS_DIR/" >/dev/null
  if [ ! -f "$FFMPEG_EXE" ] || [ ! -f "$FFPROBE_EXE" ]; then
    echo "ERROR: ffmpeg.exe or ffprobe.exe not found after extraction. Listing contents:"
    unzip -l "$TMP/ffmpeg.zip" | head -20
    exit 1
  fi
  echo "  → $FFMPEG_EXE ($(du -h "$FFMPEG_EXE" | cut -f1))"
  echo "  → $FFPROBE_EXE ($(du -h "$FFPROBE_EXE" | cut -f1))"
else
  echo "ffmpeg.exe and ffprobe.exe already present, skipping download."
fi

# --- icon.ico (multi-resolution, from existing icon.png) ---
if [ ! -f "$ICON_ICO" ]; then
  if [ ! -f "$ICON_PNG" ]; then
    echo "ERROR: $ICON_PNG not found. Cannot generate icon.ico."
    exit 1
  fi
  echo "Generating icon.ico from icon.png..."
  convert "$ICON_PNG" \
    -define icon:auto-resize=256,128,96,64,48,32,24,16 \
    "$ICON_ICO"
  echo "  → $ICON_ICO"
else
  echo "icon.ico already present, skipping generation."
fi

echo "Setup OK."
