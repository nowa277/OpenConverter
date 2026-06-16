# Windows Installer — Design Spec

**Date:** 2026-06-16
**Status:** Draft (awaiting user review)
**Author:** Brainstorming session
**Target version:** v0.3.0

## 1. Goal

Ship a working Windows installer for OpenConverter so Windows users can
run the app without going through Linux tooling. Scope is limited to
**Windows only** — macOS / Android / iOS support are explicitly out of
scope and will be addressed in future specs.

## 2. Constraints

- **Source code is unchanged at the algorithm level.** All current
  decoders (NCM, QMCv1, QMCv2, KGM, KWM) work as-is on Windows because
  they are pure Node `crypto` / pure JS.
- **ffmpeg must be bundled.** Windows users almost never have ffmpeg in
  PATH; we cannot rely on system-provided ffmpeg.
- **No code signing.** v0.3.0 will ship unsigned; SmartScreen warnings
  are an accepted cost.
- **Linux-friendly build path.** The user primarily develops on Linux;
  the build must run on Linux with `wine64` (no Windows machine
  required for local builds).
- **No renderer / UI changes.** The Windows look-and-feel is delivered
  via OS-native title bar (one-line change to `BrowserWindow` config).
  Traffic lights, IPC, CSS changes are all out of scope.

## 3. Output artifacts

| Artifact                       | Format   | Size estimate | Target user                |
| ------------------------------ | -------- | ------------- | -------------------------- |
| `OpenConverter-X.Y.Z-setup.exe`     | NSIS    | ~150 MB       | Default install            |
| `OpenConverter-X.Y.Z-portable.exe` | Portable| ~150 MB       | No-install, U盘携带        |

Both ship a single static binary plus the bundled `ffmpeg.exe`.

## 4. File changes

### 4.1 `package.json` (modified)

Add the following to the existing `build` block:

```json
"win": {
  "target": [
    { "target": "nsis",    "arch": ["x64"] },
    { "target": "portable", "arch": ["x64"] }
  ],
  "icon": "build/icons/icon.ico"
},
"nsis": {
  "oneClick": false,
  "allowToChangeInstallationDirectory": true,
  "perMachine": false
},
"extraResources": [
  { "from": "vendor/win/ffmpeg.exe", "to": "ffmpeg.exe" }
]
```

Add scripts:

```json
"build:win": "npm run build:renderer && electron-builder --win --x64"
```

### 4.2 `src/main/index.js` (modified)

Add helper:

```js
function getFfmpegPath() {
  if (app.isPackaged && process.platform === 'win32') {
    return path.join(process.resourcesPath, 'ffmpeg.exe');
  }
  return 'ffmpeg'; // dev / Linux: 走 PATH
}
```

Modify `BrowserWindow` construction:

```js
mainWindow = new BrowserWindow({
  // ...existing options...
  frame: process.platform === 'darwin' ? false : true,
  titleBarStyle: process.platform === 'darwin' ? 'hidden' : 'default',
});
```

Import `getFfmpegPath` from `./ffmpeg.js` (or define inline in main; see 4.3).

### 4.3 `src/main/ffmpeg.js` (modified)

Change the hard-coded `'ffmpeg'` string in `spawn()` to call `getFfmpegPath()`:

```js
const { spawn } = require('node:child_process');
// ...
function run(input, output, opts = {}) {
  // ...existing code...
  const proc = spawn(getFfmpegPath(), args, opts);
  // ...
}
```

`getFfmpegPath` lives in `src/main/index.js` and is imported here.

### 4.4 `vendor/win/ffmpeg.exe` (new, gitignored)

- Source: gyan.dev "essentials" static build, version 7.x
- URL: https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip
- Expected file size: ~80 MB
- **gitignored** — not committed; downloaded manually by the developer
  during initial setup. A `scripts/setup-win-deps.sh` helper script
  automates the download.

### 4.5 `build/icons/icon.ico` (new, committed)

- Generated from existing `build/icons/icon.png` via ImageMagick:
  ```bash
  convert build/icons/icon.png \
    -define icon:auto-resize=256,128,96,64,48,32,24,16 \
    build/icons/icon.ico
  ```
- Committed to repo (small icon, ~10–50 KB)

### 4.6 `scripts/setup-win-deps.sh` (new)

One-shot setup script that downloads ffmpeg.exe and generates icon.ico
on Linux. Idempotent — safe to re-run.

```bash
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ffmpeg.exe
if [ ! -f "$ROOT/vendor/win/ffmpeg.exe" ]; then
  mkdir -p "$ROOT/vendor/win"
  wget -q -O /tmp/ffmpeg.zip \
    https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip
  unzip -j /tmp/ffmpeg.zip 'ffmpeg-*-essentials_build/bin/ffmpeg.exe' \
    -d "$ROOT/vendor/win/"
  rm /tmp/ffmpeg.zip
fi

# icon.ico
if [ ! -f "$ROOT/build/icons/icon.ico" ]; then
  convert "$ROOT/build/icons/icon.png" \
    -define icon:auto-resize=256,128,96,64,48,32,24,16 \
    "$ROOT/build/icons/icon.ico"
fi

echo "Setup OK."
```

### 4.7 `tests/build.test.sh` (new)

Smoke test for the Windows build:

```bash
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# 1. Build artifact must exist
test -f "$ROOT/release/OpenConverter-"*"setup.exe"     || { echo "NSIS missing"; exit 1; }
test -f "$ROOT/release/OpenConverter-"*"portable.exe" || { echo "Portable missing"; exit 1; }

# 2. ffmpeg.exe must be bundled inside the NSIS installer
mkdir -p /tmp/nsis-check
# Use 7z to extract without running the installer
7z x -y "$ROOT/release/OpenConverter-"*"setup.exe" -o/tmp/nsis-check >/dev/null
test -f /tmp/nsis-check/resources/ffmpeg.exe || { echo "ffmpeg.exe not bundled"; exit 1; }
rm -rf /tmp/nsis-check

# 3. Wine smoke test — Electron should at least start
wine "$ROOT/release/win-unpacked/OpenConverter.exe" --no-sandbox &
PID=$!
sleep 8
kill $PID 2>/dev/null || true
wait $PID 2>/dev/null || true

echo "Smoke test OK."
```

### 4.8 README.md (modified)

Add Windows install section to existing README:

```markdown
### Windows

Download `OpenConverter-X.Y.Z-setup.exe` (NSIS installer) or
`OpenConverter-X.Y.Z-portable.exe` (portable) from the Releases page.
Double-click to install or run. SmartScreen will show a warning on
first launch — click "More info" → "Run anyway". No ffmpeg install
required (bundled).
```

## 5. Build flow (developer view)

```bash
# One-time setup on Linux dev machine
sudo apt install wine64 imagemagick p7zip-full
./scripts/setup-win-deps.sh

# Build (Linux dev → Windows installer)
npm run build:win

# Output:
#   release/OpenConverter-X.Y.Z-setup.exe      (NSIS)
#   release/OpenConverter-X.Y.Z-portable.exe   (Portable)
```

## 6. Verification strategy

| Layer              | Method                                                          | Acceptance                                          |
| ------------------ | --------------------------------------------------------------- | --------------------------------------------------- |
| Local build        | `npm run build:win` succeeds                                    | Both artifacts in `release/`                         |
| Bundle integrity   | `tests/build.test.sh` (7z extract, check `ffmpeg.exe` location) | `ffmpeg.exe` present in `resources/`                 |
| Wine smoke         | `wine OpenConverter.exe` starts and stays alive for 8s           | Process exits 0 after `kill`                         |
| Format conversion  | Manual: user runs app on Windows, converts 1 file of each format | 4 formats (NCM / QMC0 / QMCFLAC / KGM) succeed       |

CI matrix (`windows-latest` runner) is **deferred** — not in this spec.
Local Wine smoke is sufficient for v0.3.0; CI runner adds value only
when we want official Windows builds without depending on the user's
local Wine setup.

## 7. Risk and mitigations

| Risk                                              | Mitigation                                                              |
| ------------------------------------------------- | ----------------------------------------------------------------------- |
| Wine render glitches (cosmetic only)              | Documented in README as "expected", not a blocker for Windows release   |
| ImageMagick `convert` produces invalid .ico       | Manual visual check during first generation; re-run if icon shows wrong sizes |
| ffmpeg.exe version drift breaks conversion        | Pin to gyan.dev 7.x; bump deliberately with a release notes line        |
| NSIS shows Chinese on Chinese-locale Windows      | Acceptable; users can switch installer language via control panel        |
| `frame: true` on Windows breaks macOS-style traffic lights behavior | traffic-light CSS already `pointer-events: auto` and click handlers work either way; verified on macOS first |
| User has existing Windows SmartScreen reputation | Out of scope — requires code signing certificate (separate decision)    |

## 8. Out of scope (deferred)

- macOS support (next cross-platform spec)
- Android / iOS support (architecture-level spec first)
- Code signing certificate (decision deferred)
- Renderer / IPC / CSS changes for cross-platform UI consistency
- GitHub Actions `windows-latest` matrix (deferred to v0.4.0 or later)
- Microsoft Store distribution
- Auto-update mechanism
- ARM64 Windows build (separate effort if needed)

## 9. Acceptance criteria

The spec is complete when ALL of the following are true:

1. `npm run build:win` on a Linux dev machine produces both
   `OpenConverter-X.Y.Z-setup.exe` and `OpenConverter-X.Y.Z-portable.exe`.
2. Both artifacts contain a `resources/ffmpeg.exe` of expected size
   (~80 MB).
3. `wine OpenConverter.exe` (unpacked) starts and stays alive for 8s.
4. A user on real Windows can download, install (or extract portable),
   run, and successfully convert at least one file of each format
   (NCM, QMC0, QMCFLAC, KGM, KWM).
5. SmartScreen warning is documented in the release notes; the user
   has accepted that as a known limitation.

## 10. Open questions (none blocking)

None — all design decisions resolved during brainstorming session on
2026-06-16. The single recommended approach (frame: true on Windows,
otherwise minimal-change) was approved.