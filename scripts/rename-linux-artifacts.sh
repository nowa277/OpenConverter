#!/usr/bin/env bash
# Rename Linux AppImage artifacts to use x64 instead of x86_64.
#
# electron-builder's ${arch} resolves to:
#   - deb:      amd64, arm64
#   - AppImage: x86_64, aarch64
# We standardize on x64 / arm64 for both formats.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE="$ROOT/release"

shopt -s nullglob
for f in "$RELEASE"/openconverter-*-x86_64.AppImage; do
  newname="${f//-x86_64.AppImage/-x64.AppImage}"
  mv "$f" "$newname"
  echo "renamed: $(basename "$f") -> $(basename "$newname")"
done
shopt -u nullglob
