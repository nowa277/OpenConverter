#!/usr/bin/env bash
# Post-build rename: standardize Linux AppImage arch to x64 / arm64.
#
# electron-builder 24's ${arch} macro resolves to "x86_64" for AppImage
# (not "x64" as it does for deb). The artifactName template cannot
# override this per-target — the schema only allows `target` and `arch`
# in linux.target[]. This script normalizes the produced filenames.
#
# Idempotent: re-running is a no-op if files already have the desired name.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE="$ROOT/release"

shopt -s nullglob
renamed=0
for f in "$RELEASE"/openconverter-v*-linux-x86_64.AppImage "$RELEASE"/openconverter-v*-linux-aarch64.AppImage; do
  if [[ "$f" == *x86_64* ]]; then
    newname="${f/-x86_64.AppImage/-x64.AppImage}"
  else
    newname="${f/-aarch64.AppImage/-arm64.AppImage}"
  fi
  if [ "$f" != "$newname" ]; then
    mv "$f" "$newname"
    echo "renamed: $(basename "$f") -> $(basename "$newname")"
    renamed=$((renamed+1))
  fi
done
shopt -u nullglob

if [ "$renamed" -eq 0 ]; then
  echo "rename-linux-artifacts: no changes needed"
fi