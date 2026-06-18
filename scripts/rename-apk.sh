#!/usr/bin/env bash
# Rename AGP output APKs to the "openconverter-v<ver>-android-<abi>.apk" pattern
# used by GitHub Releases, and write a sha256 next to each.
set -euo pipefail
VERSION="${1:?usage: $0 VERSION}"
SRC="${2:-android/app/build/outputs/apk/release}"
DST="${3:-release}"
mkdir -p "${DST}"
for f in "${SRC}"/app-*-release.apk; do
    abi="$(echo "${f}" | sed -E 's#.*app-(.+)-release\.apk#\1#')"
    out="${DST}/openconverter-v${VERSION}-android-${abi}.apk"
    cp -f "${f}" "${out}"
    (cd "${DST}" && sha256sum "$(basename "${out}")" > "$(basename "${out}").sha256")
done
echo "Wrote 3 APKs + 3 .sha256 to ${DST}/"
ls -lh "${DST}/"
