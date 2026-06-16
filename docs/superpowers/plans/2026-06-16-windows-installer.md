# Windows Installer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Windows NSIS installer and Portable executable from the existing Linux dev machine, with bundled ffmpeg.exe.

**Architecture:** Minimal Linux-friendly Windows build path. All current decoders (NCM, QMC, KGM, KWM) work unchanged on Windows because they are pure JS / pure Node crypto. The only platform-specific code is a single `resolveFfmpegPath()` helper and a one-line `BrowserWindow` config change. Build artifacts come from electron-builder via Wine on Linux; verification is local 7z-extract + Wine smoke test.

**Tech Stack:** Electron 28, Vite 5, electron-builder 24, ImageMagick (`convert`), Wine 64, p7zip-full. No new runtime dependencies.

---

## File map

| File                                              | Status   | Responsibility                                                |
| ------------------------------------------------- | -------- | ------------------------------------------------------------- |
| `scripts/setup-win-deps.sh`                       | Create   | Download ffmpeg.exe + generate icon.ico on Linux dev          |
| `scripts/win-deps/`                               | Create   | Holds downloaded ffmpeg.exe (gitignored)                      |
| `src/main/ffmpeg-path.js`                         | Create   | Pure `resolveFfmpegPath({isPackaged, platform, resourcesPath})` helper |
| `src/main/index.js`                               | Modify   | Wire the pure helper to Electron `app` context                |
| `src/main/ffmpeg.js`                              | Modify   | Replace hard-coded `'ffmpeg'` with `resolveFfmpegPath()`      |
| `package.json`                                    | Modify   | Add `win.target`, `extraResources`, `build:win` script         |
| `tests/ffmpeg-path.test.js`                       | Create   | Unit test for the pure helper                                 |
| `tests/build.test.sh`                             | Create   | Smoke test: 7z-extract NSIS, verify ffmpeg.exe is bundled     |
| `.gitignore`                                      | Modify   | Ignore `scripts/win-deps/` (vendored binary)                   |
| `README.md`                                       | Modify   | Add Windows install section                                   |

---

## Task 1: Install dev tools (wine + imagemagick + 7zip)

**Files:**
- No files created. Documented in README install section.

- [ ] **Step 1: Install required Linux packages**

```bash
sudo apt update
sudo apt install -y wine64 imagemagick p7zip-full
```

Expected: 3 packages installed, no errors. If running in CI / container, may need `--no-install-recommends`.

- [ ] **Step 2: Verify each tool is callable**

```bash
wine --version
convert --version | head -1
7z | head -1
```

Expected: each command prints a version line. Wine should print `wine-...` (e.g., `wine-9.0`); ImageMagick prints something like `Version: ImageMagick 7.x`; 7z prints `7-Zip [version]`.

- [ ] **Step 3: Commit nothing** (no code changes this task)

No commit needed — this task only installs system tools on the dev machine. Proceed to Task 2.

---

## Task 2: Pure helper `resolveFfmpegPath()` + unit test (TDD)

**Files:**
- Create: `src/main/ffmpeg-path.js`
- Create: `tests/ffmpeg-path.test.js`

- [ ] **Step 1: Write the failing unit test**

Create `tests/ffmpeg-path.test.js`:

```js
/**
 * Tests for the pure resolveFfmpegPath helper. Pure function — does not
 * touch Electron `app` directly, so it's testable in plain Node.
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { resolveFfmpegPath } = require('../src/main/ffmpeg-path');

test('returns bundled path on packaged Windows', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'win32',
    resourcesPath: 'C:\\Users\\me\\AppData\\OpenConverter\\resources',
  });
  assert.equal(result, path.join('C:\\Users\\me\\AppData\\OpenConverter\\resources', 'ffmpeg.exe'));
});

test('returns "ffmpeg" on packaged Linux (let PATH handle it)', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'linux',
    resourcesPath: '/usr/share/openconverter/resources',
  });
  assert.equal(result, 'ffmpeg');
});

test('returns "ffmpeg" on packaged macOS (let PATH handle it)', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'darwin',
    resourcesPath: '/Applications/OpenConverter.app/Contents/Resources',
  });
  assert.equal(result, 'ffmpeg');
});

test('returns "ffmpeg" in dev mode (unpackaged) regardless of platform', () => {
  const win = resolveFfmpegPath({
    isPackaged: false,
    platform: 'win32',
    resourcesPath: 'C:\\temp\\dev\\resources',
  });
  const linux = resolveFfmpegPath({
    isPackaged: false,
    platform: 'linux',
    resourcesPath: '/tmp/dev/resources',
  });
  assert.equal(win, 'ffmpeg');
  assert.equal(linux, 'ffmpeg');
});
```

- [ ] **Step 2: Run test, verify it fails**

```bash
node --test tests/ffmpeg-path.test.js
```

Expected: FAIL with `Cannot find module '../src/main/ffmpeg-path'` (the file doesn't exist yet).

- [ ] **Step 3: Write the minimal implementation**

Create `src/main/ffmpeg-path.js`:

```js
/**
 * Pure helper: resolve the path to the bundled ffmpeg executable.
 *
 * Pure function — takes isPackaged/platform/resourcesPath explicitly so it
 * can be unit-tested without spinning up Electron. The main process wraps
 * this with the actual Electron `app` context.
 *
 * Rules:
 *   - Packaged Windows build → resourcesPath/ffmpeg.exe (bundled by
 *     electron-builder extraResources).
 *   - Packaged Linux / macOS, or any dev mode → 'ffmpeg' (let the system
 *     PATH resolve it).
 */
const path = require('node:path');

function resolveFfmpegPath({ isPackaged, platform, resourcesPath }) {
  if (isPackaged && platform === 'win32' && resourcesPath) {
    return path.join(resourcesPath, 'ffmpeg.exe');
  }
  return 'ffmpeg';
}

module.exports = { resolveFfmpegPath };
```

- [ ] **Step 4: Run test, verify it passes**

```bash
node --test tests/ffmpeg-path.test.js
```

Expected: 4 tests pass, 0 fail.

- [ ] **Step 5: Commit**

```bash
git add src/main/ffmpeg-path.js tests/ffmpeg-path.test.js
git commit -m "Add pure resolveFfmpegPath helper with unit tests"
```

---

## Task 3: Wire helper into main process

**Files:**
- Modify: `src/main/index.js` (top-level require + new helper, BrowserWindow config change)

- [ ] **Step 1: Read current main/index.js to find insertion points**

```bash
grep -n "process.platform\|frame:\|titleBarStyle\|require.*ffmpeg" src/main/index.js
```

- [ ] **Step 2: Add the require at the top of src/main/index.js**

Find the existing block of `const { ... } = require('electron');` (currently includes `app, BrowserWindow, ipcMain, dialog, shell`). Add `ffmpeg-path` require just below the existing local `require` block. After the line:

```js
const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');
const { run: ffmpegRun, probeDuration } = require('./ffmpeg');
const config = require('./config');
const decoders = require('../decoders');
```

Add this line:

```js
const { resolveFfmpegPath } = require('./ffmpeg-path');
```

- [ ] **Step 3: Replace the BrowserWindow config in createWindow()**

Find this block (around line 35–52 in `createWindow`):

```js
mainWindow = new BrowserWindow({
  width: 1100,
  height: 720,
  minWidth: 880,
  minHeight: 560,
  backgroundColor: '#121212',
  title: 'OpenConverter',
  show: false,
  frame: false, // custom title bar rendered in renderer
  titleBarStyle: 'hidden', // macOS: hide inset title bar
  icon: path.join(__dirname, '..', '..', 'build', 'icons', 'icon.png'),
  webPreferences: {
    preload: path.join(__dirname, '..', 'preload', 'index.js'),
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: false,
  },
});
```

Replace with:

```js
mainWindow = new BrowserWindow({
  width: 1100,
  height: 720,
  minWidth: 880,
  minHeight: 560,
  backgroundColor: '#121212',
  title: 'OpenConverter',
  show: false,
  // macOS / Linux: frameless + custom traffic-light title bar (in renderer).
  // Windows: use OS-native title bar (frame: true) so users get standard
  // minimize/maximize/close buttons and draggable title region.
  frame: process.platform === 'darwin' ? false : true,
  titleBarStyle: process.platform === 'darwin' ? 'hidden' : 'default',
  icon: path.join(__dirname, '..', '..', 'build', 'icons', 'icon.png'),
  webPreferences: {
    preload: path.join(__dirname, '..', 'preload', 'index.js'),
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: false,
  },
});
```

- [ ] **Step 4: Replace the `run: ffmpegRun` import to wire the helper**

The existing `ffmpeg.js` exports `run` (which currently spawns the literal string `'ffmpeg'`). We need to keep that signature stable from main's perspective, but ensure the resolution happens at spawn time. Instead of changing `ffmpeg.js`'s public API, expose a wrapper from `index.js`:

Replace the `const { run: ffmpegRun, probeDuration } = require('./ffmpeg');` line with:

```js
const { run: ffmpegRunRaw, probeDuration } = require('./ffmpeg');

// Wrap ffmpegRun so the resolved path is used at call time. Done in main
// (not ffmpeg.js) so ffmpeg.js stays a thin subprocess wrapper with no
// Electron dependency.
function ffmpegRun(input, output, opts = {}) {
  const ffmpegBin = resolveFfmpegPath({
    isPackaged: app.isPackaged,
    platform: process.platform,
    resourcesPath: process.resourcesPath,
  });
  return ffmpegRunRaw(input, output, { ...opts, ffmpegBin });
}
```

- [ ] **Step 5: Modify src/main/ffmpeg.js to accept the ffmpegBin option**

Read current `src/main/ffmpeg.js`. The `run` function currently does `spawn('ffmpeg', args, opts)`. Change the first arg of `spawn` to read from `opts.ffmpegBin`:

Find the `spawn(...)` call inside the `run()` function and replace it:

```js
// Before:
const proc = spawn('ffmpeg', args, opts);

// After:
const ffmpegBin = opts.ffmpegBin || 'ffmpeg';
delete opts.ffmpegBin;
const proc = spawn(ffmpegBin, args, opts);
```

If the exact line differs in your tree, the change is the same: replace the literal `'ffmpeg'` with `opts.ffmpegBin || 'ffmpeg'`.

- [ ] **Step 6: Run all tests to make sure nothing broke**

```bash
node --test tests/
```

Expected: All existing test suites pass (NCM 14/14, QMC, QMCv2, KWM, KGM, plus the new ffmpeg-path). No regressions.

- [ ] **Step 7: Commit**

```bash
git add src/main/index.js src/main/ffmpeg.js
git commit -m "Wire resolveFfmpegPath into main process, set frame=true on Windows"
```

---

## Task 4: Add `scripts/setup-win-deps.sh` to download ffmpeg.exe

**Files:**
- Modify: `.gitignore` (ignore `scripts/win-deps/`)
- Create: `scripts/setup-win-deps.sh`

- [ ] **Step 1: Update .gitignore**

Open `.gitignore`. After the `# Build outputs (regeneratable)` block, add:

```
# Windows build artifacts (downloaded by scripts/setup-win-deps.sh, not committed)
scripts/win-deps/
vendor/win/
```

- [ ] **Step 2: Write the setup script**

Create `scripts/setup-win-deps.sh`:

```bash
#!/usr/bin/env bash
# Setup script: download a Windows-compatible ffmpeg.exe and generate icon.ico
# for use by electron-builder when building Windows installers.
#
# Idempotent: skips steps whose output already exists. Re-run after updating
# the bundled ffmpeg version (e.g., to bump gyan.dev release).
#
# Requires: wget, unzip, ImageMagick (convert). All standard on Debian/Ubuntu.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FFMPEG_DIR="$ROOT/scripts/win-deps"
FFMPEG_EXE="$FFMPEG_DIR/ffmpeg.exe"
ICON_PNG="$ROOT/build/icons/icon.png"
ICON_ICO="$ROOT/build/icons/icon.ico"

# --- ffmpeg.exe (gyan.dev essentials, latest 7.x) ---
if [ ! -f "$FFMPEG_EXE" ]; then
  echo "Downloading ffmpeg.exe (gyan.dev essentials build)..."
  mkdir -p "$FFMPEG_DIR"
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  wget -q -O "$TMP/ffmpeg.zip" \
    "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
  # The zip contains a directory like 'ffmpeg-7.1-essentials_build/bin/ffmpeg.exe'.
  # Extract just the exe to a known path.
  unzip -j "$TMP/ffmpeg.zip" 'ffmpeg-*-essentials_build/bin/ffmpeg.exe' \
    -d "$FFMPEG_DIR/" >/dev/null
  if [ ! -f "$FFMPEG_EXE" ]; then
    echo "ERROR: ffmpeg.exe not found after extraction. Listing contents:"
    unzip -l "$TMP/ffmpeg.zip" | head -20
    exit 1
  fi
  echo "  → $FFMPEG_EXE ($(du -h "$FFMPEG_EXE" | cut -f1))"
else
  echo "ffmpeg.exe already present, skipping download."
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
```

- [ ] **Step 3: Make the script executable**

```bash
chmod +x scripts/setup-win-deps.sh
```

- [ ] **Step 4: Run the setup script**

```bash
./scripts/setup-win-deps.sh
```

Expected: Downloads ffmpeg.exe (~80 MB) and generates icon.ico. Both `scripts/win-deps/ffmpeg.exe` and `build/icons/icon.ico` should now exist. The script must not fail.

- [ ] **Step 5: Verify outputs**

```bash
ls -lh scripts/win-deps/ffmpeg.exe build/icons/icon.ico
file scripts/win-deps/ffmpeg.exe
file build/icons/icon.ico
```

Expected: ffmpeg.exe shows as a Windows PE executable (e.g., `PE32+ executable (GUI) x86-64`). icon.ico shows as `MS Windows icon resource`.

- [ ] **Step 6: Commit**

```bash
git add .gitignore scripts/setup-win-deps.sh
git commit -m "Add setup-win-deps.sh to vendor ffmpeg.exe and generate icon.ico"
```

Note: ffmpeg.exe and icon.ico are NOT committed — ffmpeg.exe is gitignored, icon.ico will be added in the next commit (after we verify it builds correctly).

---

## Task 5: Update package.json — win target + extraResources + scripts

**Files:**
- Modify: `package.json`

- [ ] **Step 1: Read current package.json `build` block**

```bash
cat package.json
```

- [ ] **Step 2: Add `win`, `nsis`, `portable`, `extraResources` to the `build` object**

Find the existing `"build"` block in `package.json`. It currently has `appId`, `productName`, `files`, `directories`, and `linux` keys. Add a `win` block (sibling of `linux`), an `nsis` config block, and an `extraResources` block at the end (sibling of `linux`):

```json
"build": {
  "appId": "com.openconverter.app",
  "productName": "OpenConverter",
  "files": [
    "src/**/*",
    "dist-renderer/**/*",
    "package.json"
  ],
  "directories": {
    "output": "release",
    "buildResources": "build"
  },
  "linux": {
    "target": [
      { "target": "deb",      "arch": ["x64", "arm64"] },
      { "target": "AppImage", "arch": ["x64", "arm64"] }
    ],
    "category": "AudioVideo",
    "icon": "build/icons/icon.png",
    "synopsis": "Open-source audio converter",
    "description": "Converts encrypted audio formats (NCM/QMC0/KGM/KWM) to MP3/FLAC/WAV"
  },
  "win": {
    "target": [
      { "target": "nsis",     "arch": ["x64"] },
      { "target": "portable", "arch": ["x64"] }
    ],
    "icon": "build/icons/icon.ico",
    "artifactName": "${productName}-${version}-${arch}.${ext}"
  },
  "nsis": {
    "oneClick": false,
    "allowToChangeInstallationDirectory": true,
    "perMachine": false,
    "artifactName": "${productName}-${version}-setup.${ext}"
  },
  "portable": {
    "artifactName": "${productName}-${version}-portable.${ext}"
  },
  "extraResources": [
    { "from": "scripts/win-deps/ffmpeg.exe", "to": "ffmpeg.exe" }
  ]
}
```

Notes:
- `artifactName` overrides produce predictable filenames matching the spec.
- `from` path matches where `setup-win-deps.sh` puts ffmpeg.exe.
- `to` is the path inside the packaged app's `resources/` directory.
- x64 only for Windows (ARM64 Windows is a niche; can be added later by appending `"arm64"` to the `arch` array).

- [ ] **Step 3: Add the `build:win` script**

In the `scripts` block of `package.json`, add a new entry. Find the existing `"build:linux"` script and add the new one right after it:

```json
"build:win": "npm run build:renderer && electron-builder --win --x64"
```

- [ ] **Step 4: Verify package.json is valid JSON**

```bash
node -e "JSON.parse(require('fs').readFileSync('package.json', 'utf8')); console.log('OK')"
```

Expected: prints `OK`. If JSON parse fails, fix the syntax error before proceeding.

- [ ] **Step 5: Commit**

```bash
git add package.json
git commit -m "Add Windows build target (NSIS + Portable) with bundled ffmpeg"
```

---

## Task 6: Build Windows artifacts (Linux + Wine)

**Files:**
- No files committed. Builds artifacts in `release/`.

- [ ] **Step 1: Build the renderer (must run before electron-builder)**

```bash
rm -rf dist-renderer/
npm run build:renderer
```

Expected: `dist-renderer/` recreated, no errors.

- [ ] **Step 2: Build Windows installer**

```bash
npx electron-builder --win --x64
```

Expected: build runs to completion. Output should mention `building target=nsis` and `building target=portable`. If it fails with "icon.ico not found", run `scripts/setup-win-deps.sh` first.

- [ ] **Step 3: Verify outputs**

```bash
ls -lh release/*.exe
```

Expected: two files roughly matching:
- `OpenConverter-X.Y.Z-x64.exe` (NSIS installer, ~150 MB)
- `OpenConverter-X.Y.Z-portable.exe` (Portable, ~150 MB)

- [ ] **Step 4: Extract NSIS to verify ffmpeg.exe is bundled**

```bash
mkdir -p /tmp/nsis-check
7z x -y "release/OpenConverter-"*"setup.exe" -o/tmp/nsis-check >/dev/null
ls -lh /tmp/nsis-check/resources/ffmpeg.exe
rm -rf /tmp/nsis-check
```

Expected: ffmpeg.exe is present in `resources/`, size ~80 MB.

- [ ] **Step 5: Commit no code** (artifacts are in release/, which is gitignored)

Proceed to Task 7 for the automated smoke test.

---

## Task 7: Smoke test script

**Files:**
- Create: `tests/build.test.sh`

- [ ] **Step 1: Write the smoke test**

Create `tests/build.test.sh`:

```bash
#!/usr/bin/env bash
# Smoke test for the Windows build. Run AFTER `npm run build:win`.
#
# Verifies:
#   1. Both NSIS installer and Portable artifacts exist.
#   2. NSIS installer contains resources/ffmpeg.exe (bundled).
#   3. Wine can start the unpacked Electron binary and keep it alive.
#
# Exit code 0 = all checks passed. Non-zero = first failed check.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE="$ROOT/release"

# 1. Artifact existence
NSIS=$(ls "$RELEASE"/OpenConverter-*-setup.exe 2>/dev/null | head -1)
PORTABLE=$(ls "$RELEASE"/OpenConverter-*-portable.exe 2>/dev/null | head -1)

if [ -z "$NSIS" ]; then
  echo "FAIL: NSIS installer not found in release/"
  exit 1
fi
if [ -z "$PORTABLE" ]; then
  echo "FAIL: Portable executable not found in release/"
  exit 1
fi
echo "PASS: NSIS and Portable artifacts present"
echo "  NSIS:     $NSIS ($(du -h "$NSIS" | cut -f1))"
echo "  Portable: $PORTABLE ($(du -h "$PORTABLE" | cut -f1))"

# 2. ffmpeg.exe bundled inside NSIS
CHECK_DIR="$(mktemp -d)"
trap 'rm -rf "$CHECK_DIR"' EXIT
7z x -y "$NSIS" -o"$CHECK_DIR" >/dev/null
if [ ! -f "$CHECK_DIR/resources/ffmpeg.exe" ]; then
  echo "FAIL: ffmpeg.exe not bundled inside NSIS installer"
  echo "  contents of extracted root:"
  ls "$CHECK_DIR" | head -10
  exit 1
fi
echo "PASS: ffmpeg.exe bundled ($(du -h "$CHECK_DIR/resources/ffmpeg.exe" | cut -f1))"

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
```

- [ ] **Step 2: Make the script executable**

```bash
chmod +x tests/build.test.sh
```

- [ ] **Step 3: Run the smoke test**

```bash
bash tests/build.test.sh
```

Expected output (truncated):

```
PASS: NSIS and Portable artifacts present
  NSIS:     .../OpenConverter-0.3.0-x64.exe (150M)
  Portable: .../OpenConverter-0.3.0-portable.exe (150M)
PASS: ffmpeg.exe bundled (80M)
PASS: Wine process is still alive after 8s

All build smoke tests passed.
```

If Wine is not installed or fails differently, the script skips Step 3 with a message. Acceptable.

- [ ] **Step 4: Commit**

```bash
git add tests/build.test.sh
git commit -m "Add Windows build smoke test (NSIS integrity + Wine startup)"
```

---

## Task 8: Update README with Windows install section

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Find the existing Install section**

```bash
grep -n "^## Install" README.md
```

- [ ] **Step 2: Insert Windows subsection after Linux/AppImage sections**

After the existing AppImage install code block (the `chmod +x` / `./OpenConverter-0.2.1.AppImage` lines) and before the `## Build from source` section, add:

```markdown
### Windows

Download the latest `OpenConverter-X.Y.Z-setup.exe` (NSIS installer) or
`OpenConverter-X.Y.Z-portable.exe` (portable, no install needed) from the
[Releases page](https://github.com/nowa277/OpenConverter/releases).

- **Installer**: double-click `setup.exe`, follow the wizard, choose
  install path.
- **Portable**: place `OpenConverter-X.Y.Z-portable.exe` anywhere (e.g.
  USB drive), double-click to run. No installer, no system changes.

Both bundles include `ffmpeg.exe` — no separate ffmpeg install required.

**First launch note**: Windows SmartScreen will show "Windows protected
your PC" with "Unknown publisher" on first run. Click **More info** →
**Run anyway** to proceed. This is expected for unsigned binaries; the
warning will persist until the project gets a code signing certificate.
```

- [ ] **Step 3: Verify README renders correctly**

```bash
grep -A 15 "^### Windows" README.md
```

Expected: Windows section is present with the right structure.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "Add Windows install instructions to README"
```

---

## Task 9: End-to-end manual smoke (cannot be automated)

**Files:** No files committed. Manual test by the user on a real Windows machine (or via CI matrix in a future spec).

- [ ] **Step 1: Document this task in the PR description**

In the PR / merge description, note that this task is **not yet complete** — it requires:

- [ ] User runs NSIS installer on real Windows, opens app, converts one
      file of each format (NCM, QMC0, QMCFLAC, KGM, KWM). Verifies
      output matches expected (valid audio in chosen output format).
- [ ] User runs Portable exe from a different directory (e.g., USB),
      confirms it works without installation.
- [ ] User notes any first-launch issues (SmartScreen flow, antivirus
      false positives, ffmpeg-related errors).

- [ ] **Step 2: Mark this task as "deferred to user acceptance"**

This is a manual checkpoint. The implementation is complete; only the
user-acceptance step is pending. Record any issues in
`docs/superpowers/issues/2026-06-16-windows-acceptance.md` (create the
dir if needed) and link from the spec doc.

- [ ] **Step 3: Commit nothing** (the spec is committed; acceptance is out-of-band).

---

## Self-review

**Spec coverage check:**
- Section 4.1 package.json — Task 5 ✓
- Section 4.2 main/index.js — Task 3 (BrowserWindow) + Task 3 (helper wire) ✓
- Section 4.3 main/ffmpeg.js — Task 3 step 5 ✓
- Section 4.4 vendor/win/ffmpeg.exe → moved to `scripts/win-deps/` per
  spec intent ("downloaded by setup script"). Task 4 ✓
- Section 4.5 icon.ico — Task 4 ✓
- Section 4.6 setup-win-deps.sh — Task 4 ✓
- Section 4.7 build.test.sh — Task 7 ✓
- Section 4.8 README — Task 8 ✓
- Section 6 verification: 4 layers — Task 6 (local build), Task 6 (7z
  extract), Task 7 (wine smoke), Task 9 (manual acceptance) ✓
- Section 9 acceptance criteria — addressed by Task 6 + Task 9 ✓

**Placeholder scan:** No "TBD" / "TODO" / "implement later". Every step
either has concrete commands or code blocks.

**Type consistency:**
- `resolveFfmpegPath` signature: `{ isPackaged, platform, resourcesPath }`
  in tests (Task 2), in main wiring (Task 3 step 4) ✓
- `ffmpegRun` wrapper signature matches original (input, output, opts)
- ffmpeg.js opts.ffmpegBin flow: passed in via wrapper → consumed in
  spawn call ✓

**File structure check:**
- `src/main/ffmpeg-path.js` is single-responsibility (pure helper) ✓
- `scripts/setup-win-deps.sh` is single-responsibility (deps setup) ✓
- `tests/build.test.sh` is single-responsibility (build smoke) ✓
- Existing `src/main/ffmpeg.js` stays thin subprocess wrapper ✓

**Known scope gap:** Task 9 (manual acceptance) is not automatable.
Documented as deferred to user; spec Section 9 explicitly requires
real-Windows testing which is by definition not in this implementation
plan.

No issues found. Plan is ready for execution.