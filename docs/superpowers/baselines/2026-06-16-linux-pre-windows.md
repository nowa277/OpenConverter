# Linux Pre-Windows Baseline (2026-06-16)

Captured before the windows-installer branch started, to ensure every
subsequent commit on the branch does not regress the v0.2.1 Linux build.

## Test baseline

```
$ node --test tests/
TAP version 13
# Test 1: magic + size validation...
#   ✓ PASS (rejected < 0x2C bytes)
#   ✓ PASS (rejected bad magic)
# Test 2: round-trip KGM with synthetic plaintext...
#   ✓ PASS (2910 bytes round-tripped)
# Test 3: round-trip VPR with synthetic plaintext...
#   ✓ PASS (3024 bytes round-tripped)
# Test 4: format detection...
#   ✓ PASS (5/5)
# Test 5: round-trip on real MP3 → fake .kgm → MP3...
#   SKIP (ffmpeg not available)
# All KGM tests passed.
# Subtest: <repo-root>/tests/kgm.test.js
ok 1 - <repo-root>/tests/kgm.test.js
  ---
  duration_ms: 170.466339
  ...
# Test 1: magic + size validation...
#   ✓ PASS (rejected < 1024 bytes)
#   ✓ PASS (rejected bad magic)
# Test 2: buildMask with known seed...
#   ✓ PASS
# Test 3: round-trip on synthetic plaintext...
#   ✓ PASS (1810 bytes round-tripped)
# Test 4: format detection...
#   ✓ PASS (6/6 cases)
# Test 5: round-trip on real MP3...
#   SKIP (ffmpeg not available)
# All KWM tests passed.
# Subtest: <repo-root>/tests/kwm.test.js
ok 2 - <repo-root>/tests/kwm.test.js
  ---
  duration_ms: 168.696765
  ...
# Found 14 samples. Python reference: yes
#   ✓ Chappell Roan - After Midnight.ncm: 8005KB audio, 204.9s, 0 byte diff
#   ✓ Chappell Roan - California.ncm: 7741KB audio, 198.1s, 0 byte diff
#   ✓ Chappell Roan - Casual.ncm: 9096KB audio, 232.8s, 0 byte diff
#   ✓ Chappell Roan - Coffee.ncm: 8031KB audio, 205.6s, 0 byte diff
#   ✓ Chappell Roan - Femininomenon.ncm: 8585KB audio, 219.7s, 0 byte diff
#   ✓ Chappell Roan - Guilty Pleasure.ncm: 8776KB audio, 224.6s, 0 byte diff
#   ✓ Chappell Roan - HOT TO GO!.ncm: 7222KB audio, 184.9s, 0 byte diff
#   ✓ Chappell Roan - Kaleidoscope.ncm: 8700KB audio, 222.7s, 0 byte diff
#   ✓ Chappell Roan - My Kink Is Karma.ncm: 8697KB audio, 222.6s, 0 byte diff
#   ✓ Chappell Roan - Naked In Manhattan.ncm: 8247KB audio, 211.1s, 0 byte diff
#   ✓ Chappell Roan - Picture You.ncm: 7312KB audio, 187.2s, 0 byte diff
#   ✓ Chappell Roan - Pink Pony Club.ncm: 10082KB audio, 258.1s, 0 byte diff
#   ✓ Chappell Roan - Red Wine Supernova.ncm: 7531KB audio, 192.8s, 0 byte diff
#   ✓ Chappell Roan - Super Graphic Ultra Modern Girl.ncm: 7186KB audio, 183.9s, 0 byte diff
# Result: 14 passed, 0 failed (14 total)
# Subtest: <repo-root>/tests/ncm.test.js
ok 3 - <repo-root>/tests/ncm.test.js
  ---
  duration_ms: 5609.304899
  ...
# Test 1: shiftMix (shl|shr, not a rotation)...
#   ✓ PASS
# Test 2: keyCompress against pyqmc-rust test vector...
#   ✓ PASS (128/128 bytes match pyqmc-rust test vector)
# Test 3: keyCompress with empty ekey throws...
#   ✓ PASS
# Test 4: decryptV2Buffer without ekey throws...
#   ✓ PASS
# Test 5: round-trip on real MP3 with arbitrary ekey...
#   SKIP (ffmpeg not available)
# All QMCv2 tests passed.
# Subtest: <repo-root>/tests/qmc-v2.test.js
ok 4 - <repo-root>/tests/qmc-v2.test.js
  ---
  duration_ms: 166.755657
  ...
# Test 1: mask sequence matches C++ reference (positions 0..63)...
#   ✓ PASS (64/64 bytes match)
# Test 2: round-trip on synthetic plaintext...
#   ✓ PASS (9210 bytes round-tripped identically)
# Test 3: round-trip on real MP3 → fake .qmc0 → MP3...
#   SKIP (ffmpeg not available)
# Test 4: extension mapping...
#   ✓ PASS
# All QMC tests passed.
# Subtest: <repo-root>/tests/qmc.test.js
ok 5 - <repo-root>/tests/qmc.test.js
  ---
  duration_ms: 165.93782
  ...
1..5
# tests 5
# suites 0
# pass 5
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 5616.961851
```

**Pass count:** 5 tests, 0 suites.
**Expected after every task:** pass count does not decrease.

## Build baseline

```
$ npm run build:linux --dir
npm warn Unknown cli config "--dir". This will stop working in the next major version of npm.

> openconverter@0.2.1 build:linux
> npm run build:renderer && electron-builder --linux

npm warn Unknown env config "dir". This will stop working in the next major version of npm. See `npm help npmrc` for supported config options.

> openconverter@0.2.1 build:renderer
> vite build

[33mThe CJS build of Vite's Node API is deprecated. See https://vite.dev/guide/troubleshooting.html#vite-cjs-node-api-deprecated for more details.[39m
vite v5.4.21 building for production...
transforming...
✓ 4 modules transformed.
rendering chunks...
computing gzip size...
../../dist-renderer/index.html                 7.47 kB │ gzip: 2.26 kB
../../dist-renderer/assets/index-DPr9QDR8.css  9.39 kB │ gzip: 2.46 kB
../../dist-renderer/assets/index-ClPgQSAP.js   6.52 kB │ gzip: 2.62 kB
✓ built in 71ms
  • electron-builder  version=24.13.3 os=6.8.0-124-generic
  • loaded configuration  file=package.json ("build" field)
  • packaging       platform=linux arch=x64 electron=28.3.3 appOutDir=release/linux-unpacked
  • building        target=AppImage arch=x64 file=release/OpenConverter-0.2.1.AppImage
  • packaging       platform=linux arch=arm64 electron=28.3.3 appOutDir=release/linux-arm64-unpacked
  • building        target=deb arch=x64 file=release/openconverter_0.2.1_amd64.deb
  • adding autoupdate files for: deb. (Beta feature)  resourceDir=<repo-root>/release/linux-unpacked/resources
  • building        target=AppImage arch=x64 file=release/OpenConverter-0.2.1.AppImage
  • building        target=AppImage arch=arm64 file=release/OpenConverter-0.2.1-arm64.AppImage
  • building        target=deb arch=x64 file=release/openconverter_0.2.1_amd64.deb
  • building        target=deb arch=arm64 file=release/openconverter_0.2.1_arm64.deb
  • adding autoupdate files for: deb. (Beta feature)  resourceDir=<repo-root>/release/linux-unpacked/resources
  • adding autoupdate files for: deb. (Beta feature)  resourceDir=<repo-root>/release/linux-arm64-unpacked/resources
```

**Result:** Linux unpacked build produced at `release/linux-unpacked/`.
**Expected after every task:** build still succeeds.
