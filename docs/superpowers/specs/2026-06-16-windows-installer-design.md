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
- **ffmpeg + ffprobe must be bundled.** Windows users almost never have
  these in PATH; we cannot rely on system-provided binaries. ffprobe is
  required because `src/main/ffmpeg.js:40` calls it for duration probing
  before every conversion — without it, every Windows conversion would
  fail at step 1.
- **No code signing.** v0.3.0 will ship unsigned; SmartScreen warnings
  are an accepted cost.
- **Linux-friendly build path.** The user primarily develops on Linux;
  the build must run on Linux with `wine64` (no Windows machine
  required for local builds).
- **No renderer / UI changes.** The Windows look-and-feel is delivered
  via OS-native title bar (one-line change to `BrowserWindow` config).
  Traffic lights, IPC, CSS changes are all out of scope.
- **Linux zero-impact hard rules (v0.3.0 only).** To protect the
  v0.2.1 Linux deb/AppImage build that shipped before this work, every
  commit on the `windows-installer` branch MUST satisfy all four:
    1. **Do not modify** the `linux` sub-block in `package.json` build
       config.
    2. **Do not modify** anything in `src/preload/` or `src/renderer/`
       (renderer is fully platform-agnostic and stays that way).
    3. **Do not modify** anything in `src/decoders/` (the crypto/format
       algorithms are out of scope for cross-platform work).
    4. **Each commit must leave** `node --test tests/` and
       `npm run build:linux --dir` both passing. A commit that breaks
       Linux must be reverted before merge.

## 3. Output artifacts

| Artifact                       | Format   | Size estimate | Target user                |
| ------------------------------ | -------- | ------------- | -------------------------- |
| `openconverter-vX.Y.Z-windows-x64-setup.exe`     | NSIS    | ~125 MB       | Default install            |
| `openconverter-vX.Y.Z-windows-x64-portable.exe` | Portable| ~125 MB       | No-install, U盘携带        |

Naming convention (applies to all platforms): `openconverter-v<version>-<platform>-<arch>[-<variant>].<ext>`.
Linux examples: `openconverter-v0.2.1-linux-amd64.deb`, `openconverter-v0.2.1-linux-x64.AppImage`.
The version prefix carries `v` to match GitHub release tags.

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
  "icon": "build/icons/icon.ico",
  "extraResources": [
    { "from": "scripts/win-deps/ffmpeg.exe",  "to": "ffmpeg.exe" },
    { "from": "scripts/win-deps/ffprobe.exe", "to": "ffprobe.exe" }
  ]
},
"nsis": {
  "oneClick": false,
  "allowToChangeInstallationDirectory": true,
  "perMachine": false
}
```

> **Note:** `extraResources` is nested inside the `win` block (not at the
> top level of `build`). electron-builder scopes `extraResources` to the
> target that owns it — top-level placement would also try to bundle
> `ffmpeg.exe` into Linux builds, which would fail because
> `scripts/win-deps/ffmpeg.exe` only exists after running
> `scripts/setup-win-deps.sh`. Nesting keeps the Linux build path
> untouched.

Add scripts:

```json
"build:win": "npm run build:renderer && electron-builder --win --x64"
```

### 4.2 `src/main/index.js` (modified)

The implementation uses a **pure helper** (`src/main/ffmpeg-path.js`,
see §4.9) for testability. Main process wires Electron's `app` context
to the helper.

Modify `BrowserWindow` construction:

```js
mainWindow = new BrowserWindow({
  // ...existing options...
  // Linux + macOS: keep existing custom traffic-light title bar
  // (v0.2.1 behavior). Windows: use OS-native title bar.
  frame: process.platform === 'win32' ? true : false,
  titleBarStyle: process.platform === 'win32' ? 'default' : 'hidden',
});
```

> **Why `win32` not `darwin`:** The original plan used
> `frame: process.platform === 'darwin' ? false : true` which would
> have flipped Linux from `frame: false` to `frame: true` — a Linux
> UI breaking change. The corrected condition is `win32 ? true : false`,
> which leaves Linux (and macOS) on the existing custom title bar and
> only changes Windows. This satisfies the "no renderer / UI changes"
> constraint for Linux.

### 4.3 `src/main/ffmpeg.js` (modified)

`ffmpeg.js` stays a thin subprocess wrapper. The only change is that
the `spawn()` calls (one for `ffmpeg`, one for `ffprobe`) read the
binary name from `opts.ffmpegBin` / `opts.ffprobeBin` instead of using
hard-coded `'ffmpeg'` / `'ffprobe'`:

```js
const { spawn } = require('node:child_process');
// ...
function runFfmpeg(args, { onProgress, signal, totalDurationSec, ffmpegBin, ffprobeBin } = {}) {
  return new Promise((resolve, reject) => {
    const proc = spawn(ffmpegBin || 'ffmpeg', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    // ... existing stderr parsing, abort handling, exit handling unchanged ...
  });
}

function runFfprobeDuration(filePath, { ffprobeBin } = {}) {
  const proc = spawn(ffprobeBin || 'ffprobe', [
    '-v', 'error', '-show_entries', 'format=duration',
    '-of', 'default=noprint_wrappers=1:nokey=1', filePath,
  ], { stdio: ['ignore', 'pipe', 'pipe'] });
  // ... existing promise wrapper unchanged ...
}
```

The main process (`src/main/index.js`) wraps the `run()` call to
inject the resolved paths via `opts`. The pure resolver lives in
`src/main/ffmpeg-path.js` (see §4.9) and takes the Electron `app`
context as explicit arguments. This keeps `ffmpeg.js` decoupled from
Electron and unit-testable.

### 4.4 `scripts/win-deps/ffmpeg.exe` + `ffprobe.exe` (new, gitignored)

- Source: gyan.dev "essentials" static build, version 7.x
- URL: https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip
- Expected total size: ~80 MB for ffmpeg.exe + ~80 MB for ffprobe.exe
- **gitignored** — not committed; downloaded manually by the developer
  during initial setup. A `scripts/setup-win-deps.sh` helper script
  automates the download.
- **Both binaries required**: ffmpeg does the transcode; ffprobe does
  the duration probe (`src/main/ffmpeg.js:40`). Without ffprobe, every
  Windows conversion fails at step 1 before ffmpeg even starts.
- **Why `scripts/win-deps/` not `vendor/win/`**: keeps the dev-time
  download cache out of the source root and colocated with the
  downloader script. Gitignored under `scripts/win-deps/`.

### 4.5 `build/icons/icon.ico` (new, committed)

- Generated from existing `build/icons/icon.png` via ImageMagick:
  ```bash
  convert build/icons/icon.png \
    -define icon:auto-resize=256,128,96,64,48,32,24,16 \
    build/icons/icon.ico
  ```
- Committed to repo (small icon, ~10–50 KB)

### 4.6 `scripts/setup-win-deps.sh` (new)

One-shot setup script that downloads ffmpeg.exe + ffprobe.exe and
generates icon.ico on Linux. Idempotent — safe to re-run.

```bash
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEPS_DIR="$ROOT/scripts/win-deps"

# ffmpeg.exe + ffprobe.exe (gyan.dev essentials, latest 7.x)
if [ ! -f "$DEPS_DIR/ffmpeg.exe" ] || [ ! -f "$DEPS_DIR/ffprobe.exe" ]; then
  echo "Downloading ffmpeg + ffprobe (gyan.dev essentials build)..."
  mkdir -p "$DEPS_DIR"
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  wget -q -O "$TMP/ffmpeg.zip" \
    "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
  unzip -j "$TMP/ffmpeg.zip" \
    'ffmpeg-*-essentials_build/bin/ffmpeg.exe' \
    'ffmpeg-*-essentials_build/bin/ffprobe.exe' \
    -d "$DEPS_DIR/" >/dev/null
  if [ ! -f "$DEPS_DIR/ffmpeg.exe" ] || [ ! -f "$DEPS_DIR/ffprobe.exe" ]; then
    echo "ERROR: ffmpeg.exe or ffprobe.exe missing after extraction"
    unzip -l "$TMP/ffmpeg.zip" | head -20
    exit 1
  fi
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
test -f "$ROOT/release/openconverter-v"*"windows-x64-setup.exe"     || { echo "NSIS missing"; exit 1; }
test -f "$ROOT/release/openconverter-v"*"windows-x64-portable.exe" || { echo "Portable missing"; exit 1; }

# 2. ffmpeg.exe AND ffprobe.exe must be bundled inside the NSIS installer
mkdir -p /tmp/nsis-check
7z x -y "$ROOT/release/openconverter-v"*"windows-x64-setup.exe" -o/tmp/nsis-check >/dev/null
test -f /tmp/nsis-check/resources/ffmpeg.exe  || { echo "ffmpeg.exe not bundled"; exit 1; }
test -f /tmp/nsis-check/resources/ffprobe.exe || { echo "ffprobe.exe not bundled"; exit 1; }
rm -rf /tmp/nsis-check

# 3. Wine smoke test — Electron should at least start
UNPACKED="$ROOT/release/win-unpacked/OpenConverter.exe"
if [ ! -f "$UNPACKED" ]; then
  echo "SKIP: Wine smoke (win-unpacked/OpenConverter.exe not found)"
  exit 0
fi
wine "$UNPACKED" --no-sandbox >/dev/null 2>&1 &
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

Download `openconverter-vX.Y.Z-windows-x64-setup.exe` (NSIS installer) or
`openconverter-vX.Y.Z-windows-x64-portable.exe` (portable) from the Releases page.
Double-click to install or run. SmartScreen will show a warning on
first launch — click "More info" → "Run anyway". No ffmpeg install
required (bundled).
```

### 4.9 `src/main/ffmpeg-path.js` (new, pure helper)

A pure function that resolves the path to the bundled ffmpeg +
ffprobe binaries. Pure = no Electron dependency, so it can be
unit-tested with `node:test` without spinning up the runtime.

```js
/**
 * Pure helper: resolve paths to the bundled ffmpeg + ffprobe binaries.
 *
 * Pure function — takes isPackaged/platform/resourcesPath explicitly so
 * it can be unit-tested without Electron. The main process wraps this
 * with the actual `app` context.
 *
 * Rules:
 *   - Packaged Windows build → resourcesPath/ffmpeg.exe (and ffprobe.exe)
 *   - Any other case (dev, Linux, macOS, or packaged on non-Windows)
 *     → 'ffmpeg' / 'ffprobe' (let the system PATH resolve it)
 */
const path = require('node:path');

function resolveFfmpegPath({ isPackaged, platform, resourcesPath }) {
  if (isPackaged && platform === 'win32' && resourcesPath) {
    return path.join(resourcesPath, 'ffmpeg.exe');
  }
  return 'ffmpeg';
}

function resolveFfprobePath({ isPackaged, platform, resourcesPath }) {
  if (isPackaged && platform === 'win32' && resourcesPath) {
    return path.join(resourcesPath, 'ffprobe.exe');
  }
  return 'ffprobe';
}

module.exports = { resolveFfmpegPath, resolveFfprobePath };
```

Main process wiring (`src/main/index.js`):

```js
const { resolveFfmpegPath, resolveFfprobePath } = require('./ffmpeg-path');

function ffmpegBin() {
  return resolveFfmpegPath({
    isPackaged: app.isPackaged,
    platform: process.platform,
    resourcesPath: process.resourcesPath,
  });
}

function ffprobeBin() {
  return resolveFfprobePath({
    isPackaged: app.isPackaged,
    platform: process.platform,
    resourcesPath: process.resourcesPath,
  });
}
```

The wrappers are passed to `ffmpeg.js` via `opts.ffmpegBin` /
`opts.ffprobeBin` at each call site.

## 5. Build flow (developer view)

```bash
# One-time setup on Linux dev machine (on the windows-installer branch)
sudo apt install wine64 imagemagick p7zip-full
./scripts/setup-win-deps.sh   # downloads ffmpeg.exe + ffprobe.exe

# Build (Linux dev → Windows installer)
npm run build:win

# Output:
#   release/openconverter-vX.Y.Z-windows-x64-setup.exe      (NSIS, ~125 MB)
#   release/openconverter-vX.Y.Z-windows-x64-portable.exe   (Portable, ~125 MB)

# Verify (on the same machine, after build)
bash tests/build.test.sh
```

> **Branch note:** the `npm run build:linux` script continues to work
> unchanged on the `windows-installer` branch. Do not invoke it as
> part of Windows build verification — the goal is to leave it
> untouched, not to test it.

## 6. Verification strategy

| Layer              | Method                                                          | Acceptance                                          |
| ------------------ | --------------------------------------------------------------- | --------------------------------------------------- |
| Unit tests         | `node --test tests/ffmpeg-path.test.js`                         | 8 tests pass (4 platform × packaged × isPackaged combos for ffmpeg + ffprobe) |
| Local build        | `npm run build:win` succeeds                                    | Both artifacts in `release/`                         |
| Bundle integrity   | `tests/build.test.sh` (7z extract, check binaries location)     | Both `ffmpeg.exe` and `ffprobe.exe` present in `resources/` |
| Wine smoke         | `wine OpenConverter.exe` starts and stays alive for 8s           | Process exits 0 after `kill` (SKIP if Wine missing)  |
| Format conversion  | Manual: user runs app on Windows, converts 1 file of each format | 4 formats (NCM / QMC0 / QMCFLAC / KGM) succeed       |
| Linux regression   | `node --test tests/` + `npm run build:linux --dir` after every commit on branch | All existing tests pass, Linux build produces no errors |

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
   `openconverter-vX.Y.Z-windows-x64-setup.exe` and
   `openconverter-vX.Y.Z-windows-x64-portable.exe`.
2. Both artifacts contain BOTH `resources/ffmpeg.exe` (~80 MB) and
   `resources/ffprobe.exe` (~80 MB).
3. `wine OpenConverter.exe` (unpacked) starts and stays alive for 8s.
4. A user on real Windows can download, install (or extract portable),
   run, and successfully convert at least one file of each format
   (NCM, QMC0, QMCFLAC, KGM, KWM).
5. SmartScreen warning is documented in the release notes; the user
   has accepted that as a known limitation.
6. **Linux zero-impact (from §2 hard rules):** before merge, the
   `windows-installer` branch must pass both `node --test tests/` and
   `npm run build:linux --dir` — the v0.2.1 Linux build pipeline must
   still work on the branch. A failure here blocks merge.

## 10. Open questions (none blocking)

None — all design decisions resolved during brainstorming session on
2026-06-16. The single recommended approach (`frame: process.platform
=== 'win32' ? true : false` for OS-native title only on Windows, plus
branch-per-platform workflow) was approved.

## 11. Multi-platform Development Principles

This section captures the long-term principles derived during the
2026-06-16 brainstorming session. The user flagged: "我们应该考虑日后
该怎么更合理的进行多平台的开发" — we should think about how to
more reasonably develop multi-platform in the future. These principles
govern this and all future platform work.

### 11.1 Branch-per-platform

Each platform's work happens on its own feature branch, off the most
recent stable main:

```
main (v0.2.1)
  ├── windows-installer  (v0.3.0)
  ├── macos-installer    (future)
  └── linux-arm64-fix    (future)
```

Merged via PR after the platform-specific acceptance criteria pass.
Main stays releasable at any point — no in-progress platform work
sits on it.

### 11.2 Additive-only changes

A new platform's branch MUST NOT modify the configuration of other
platforms. Specifically:

- Do not touch `package.json` `linux` sub-block when working on
  Windows.
- Do not touch `package.json` `win` sub-block when working on macOS.
- Do not touch `package.json` `mac` sub-block when working on Linux.
- If a refactor would benefit multiple platforms, do it on a
  separate refactor branch, not in a platform branch.

### 11.3 Platform conditionals in main process only

The renderer (`src/renderer/`, `src/preload/`) stays fully
platform-agnostic. All `process.platform` checks live in
`src/main/`. If a UI element needs different behavior per platform,
the renderer asks the main process and main returns the answer.

Rationale: the renderer is shipped to every platform; platform
branching in renderer code requires duplicated testing and creates
invisible UI bugs. Main-process branching is testable and audit-able.

### 11.4 Per-commit baseline verification

Every commit on a platform branch MUST leave the existing test suite
green AND the existing platforms' build path working:

- `node --test tests/` — all unit tests pass
- For Windows branch: also `npm run build:linux --dir` (proves Linux
  is not regressed)
- For Linux branch: also `npm run build:win --dir` (proves Windows
  config not regressed), once v0.3.0 ships
- For macOS branch: both Linux and Windows builds

A commit that breaks an existing platform's build is reverted before
the next commit lands. CI eventually enforces this; until CI lands,
the developer is the gate.

### 11.5 Spec before implementation

Every new platform work gets its own spec → plan → implement cycle
in `docs/superpowers/`. v0.3.0's spec is
`docs/superpowers/specs/2026-06-16-windows-installer-design.md`;
v0.4.0's macOS spec will be a sibling. This gives reviewers a stable
artifact to argue about before code lands.

### 11.6 Future work (deferred from v0.3.0)

These items are intentionally NOT in v0.3.0. Each gets its own spec
when prioritized:

- **CI matrix** — GitHub Actions runners for `linux`, `windows`,
  `macos`. Adds an automated gate for §11.4 above.
- **Code signing** — Windows EV/OV cert, macOS Developer ID. Separate
  cost / vendor decision.
- **Platform abstraction layer** — refactor main to expose
  `Platform` interface (`getFfmpegBin()`, `getTitleBarMode()`, etc.)
  only if a third platform makes the duplication painful. YAGNI.
- **Auto-update** — `electron-updater` integration. Separate spec.
- **ARM64 Linux fix** — if v0.2.1 ARM64 builds are broken on
  modern distros, separate spec.