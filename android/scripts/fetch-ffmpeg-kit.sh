#!/usr/bin/env bash
set -euo pipefail

# Fetches ffmpeg-kit-full-gpl 6.0-2.LTS from the Appodeal mirror.
# arthenica's official artifacts were archived 2025-04; Appodeal is the
# remaining public mirror. SHA-256 is verified against a pinned constant
# below; any drift fails the build and forces a manual review.
#
# Usage:
#   bash android/scripts/fetch-ffmpeg-kit.sh
#   # after first successful run, the printed SHA may be pinned by editing
#   # the EXPECTED_SHA256 constant in this script.

URL="https://artifactory.appodeal.com/appodeal-public/com/arthenica/ffmpeg-kit-full-gpl/6.0-2.LTS/ffmpeg-kit-full-gpl-6.0-2.LTS.aar"
DEST="$(dirname "$0")/../app/libs/ffmpeg-kit-full-gpl.aar"
EXPECTED_SHA256="2ec56561ec593c4c4ff3b3c892b49f271699ac01f09d0f0249a7c8952a44554a"

mkdir -p "$(dirname "$DEST")"

if [[ -f "$DEST" ]]; then
    echo "[fetch-ffmpeg-kit] AAR already present: $DEST"
else
    echo "[fetch-ffmpeg-kit] Downloading $URL"
    curl -fL --retry 3 -o "$DEST.partial" "$URL"
    mv "$DEST.partial" "$DEST"
fi

ACTUAL_SHA256="$(sha256sum "$DEST" | awk '{print $1}')"
echo "[fetch-ffmpeg-kit] sha256: $ACTUAL_SHA256"

if [[ -n "$EXPECTED_SHA256" ]]; then
    if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
        echo "[fetch-ffmpeg-kit] FATAL: sha256 mismatch"
        echo "  expected: $EXPECTED_SHA256"
        echo "  actual:   $ACTUAL_SHA256"
        exit 1
    fi
    echo "[fetch-ffmpeg-kit] sha256 OK"
else
    echo "[fetch-ffmpeg-kit] WARN: EXPECTED_SHA256 unset — pin this hash now:"
    echo "  Edit android/scripts/fetch-ffmpeg-kit.sh and set EXPECTED_SHA256=\"$ACTUAL_SHA256\""
fi

echo "[fetch-ffmpeg-kit] Done."
