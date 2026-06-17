# Windows Installer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Branch:** All work happens on the `windows-installer` feature branch, off `main` (v0.2.1). Do not commit to `main` until the v0.3.0 release PR is reviewed and merged.

**Goal:** Ship a Windows NSIS installer and Portable executable from the existing Linux dev machine, with bundled `ffmpeg.exe` AND `ffprobe.exe`.

**Architecture:** Minimal Linux-friendly Windows build path. All current decoders (NCM, QMC, KGM, KWM) work unchanged on Windows because they are pure JS / pure Node crypto. Platform-specific code is a small set of pure helpers (`src/main/ffmpeg-path.js`) and a one-line `BrowserWindow` config change. Build artifacts come from electron-builder via Wine on Linux; verification is local 7z-extract + Wine smoke test.

**Linux zero-impact hard rules (per spec §2):** Every commit on this branch MUST:
1. Not modify `package.json` `linux` sub-block
2. Not modify anything in `src/preload/` or `src/renderer/`
3. Not modify anything in `src/decoders/`
4. Leave `node --test tests/` AND `npm run build:linux --dir` both green

Run both verifications after every task. If a commit breaks Linux, revert before continuing.

**Tech Stack:** Electron 28, Vite 5, electron-builder 24, ImageMagick (`convert`), Wine 64, p7zip-full. No new runtime dependencies.

---

## File map

| File                                              | Status   | Responsibility                                                |
| ------------------------------------------------- | -------- | ------------------------------------------------------------- |
| `docs/superpowers/baselines/2026-06-16-linux-pre-windows.md` | Create   | Task 0 baseline: record Linux test pass count + Linux build success |
| `scripts/setup-win-deps.sh`                       | Create   | Download ffmpeg.exe + ffprobe.exe + generate icon.ico on Linux dev |
| `scripts/win-deps/`                               | Create   | Holds downloaded ffmpeg.exe + ffprobe.exe (gitignored)         |
| `src/main/ffmpeg-path.js`                         | Create   | Pure `resolveFfmpegPath` + `resolveFfprobePath` helpers        |
| `src/main/index.js`                               | Modify   | Wire the pure helpers to Electron `app` context; fix BrowserWindow frame |
| `src/main/ffmpeg.js`                              | Modify   | Read `ffmpegBin` / `ffprobeBin` from `opts` instead of hard-coded strings |
| `package.json`                                    | Modify   | Add `win.target`, `win.extraResources`, `build:win` script     |
| `tests/ffmpeg-path.test.js`                       | Create   | Unit tests for both pure helpers (8 tests)                    |
| `tests/build.test.sh`                             | Create   | Smoke test: 7z-extract NSIS, verify both binaries are bundled  |
| `.gitignore`                                      | Modify   | Ignore `scripts/win-deps/` (vendored binaries)                 |
| `README.md`                                       | Modify   | Add Windows install section                                   |

---

## Task 0: Linux baseline + branch setup (DO THIS FIRST)

**Files:**
- Create: `docs/superpowers/baselines/2026-06-16-linux-pre-windows.md`
- No code changes — this is a measurement, not an edit.

- [ ] **Step 1: Verify you are on the `windows-installer` branch**

```bash
git branch --show-current
```

Expected: `windows-installer`. If not, run `git checkout -b windows-installer` from `main` first.

- [ ] **Step 2: Verify working tree is clean**

```bash
git status
```

Expected: `nothing to commit, working tree clean`.

- [ ] **Step 3: Capture Linux test baseline**

```bash
node --test tests/ 2>&1 | tee /tmp/baseline-tests.log
```

Expected: 5 existing test suites + the new ffmpeg-path suite all pass. The new ffmpeg-path tests don't exist yet, so the baseline is just the 5 existing suites. Record the pass count and any skips.

- [ ] **Step 4: Capture Linux build baseline**

```bash
npm run build:linux --dir 2>&1 | tee /tmp/baseline-build.log
```

Expected: `release/linux-unpacked/` (or similar) is produced. The `--dir` flag skips packaging (no .deb / .AppImage) so the baseline is fast.

- [ ] **Step 5: Write baseline doc**

Create `docs/superpowers/baselines/2026-06-16-linux-pre-windows.md` (create the `baselines/` directory if needed):

```markdown
# Linux Pre-Windows Baseline (2026-06-16)

Captured before the windows-installer branch started, to ensure every
subsequent commit on the branch does not regress the v0.2.1 Linux build.

## Test baseline

\`\`\`
$ node --test tests/
<PASTE /tmp/baseline-tests.log>
\`\`\`

**Pass count:** <N> tests, <M> suites.
**Expected after every task:** pass count does not decrease.

## Build baseline

\`\`\`
$ npm run build:linux --dir
<PASTE /tmp/baseline-build.log>
\`\`\`

**Result:** Linux unpacked build produced at `release/linux-unpacked/`.
**Expected after every task:** build still succeeds.
```

- [ ] **Step 6: Commit baseline doc**

```bash
git add docs/superpowers/baselines/2026-06-16-linux-pre-windows.md
git commit -m "Record Linux baseline before windows-installer work begins"
```

This commit establishes the "do not regress" measurement. Every
subsequent commit on this branch can be checked against it.

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

## Task 2: Pure helpers `resolveFfmpegPath()` + `resolveFfprobePath()` + unit tests (TDD)

**Files:**
- Create: `src/main/ffmpeg-path.js`
- Create: `tests/ffmpeg-path.test.js`

- [ ] **Step 1: Write the failing unit tests**

Create `tests/ffmpeg-path.test.js` with 8 tests (4 platform × packaged for ffmpeg, mirrored for ffprobe):

```js
/**
 * Tests for the pure resolveFfmpegPath / resolveFfprobePath helpers.
 * Pure functions — do not touch Electron `app` directly, so they are
 * testable in plain Node.
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { resolveFfmpegPath, resolveFfprobePath } = require('../src/main/ffmpeg-path');

// --- ffmpeg path ---

test('resolveFfmpegPath: bundled path on packaged Windows', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'win32',
    resourcesPath: 'C:\\Users\\me\\AppData\\OpenConverter\\resources',
  });
  assert.equal(result, path.join('C:\\Users\\me\\AppData\\OpenConverter\\resources', 'ffmpeg.exe'));
});

test('resolveFfmpegPath: "ffmpeg" on packaged Linux (let PATH handle it)', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'linux',
    resourcesPath: '/usr/share/openconverter/resources',
  });
  assert.equal(result, 'ffmpeg');
});

test('resolveFfmpegPath: "ffmpeg" on packaged macOS (let PATH handle it)', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'darwin',
    resourcesPath: '/Applications/OpenConverter.app/Contents/Resources',
  });
  assert.equal(result, 'ffmpeg');
});

test('resolveFfmpegPath: "ffmpeg" in dev mode (unpackaged) regardless of platform', () => {
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

// --- ffprobe path (mirrored) ---

test('resolveFfprobePath: bundled path on packaged Windows', () => {
  const result = resolveFfprobePath({
    isPackaged: true,
    platform: 'win32',
    resourcesPath: 'C:\\Users\\me\\AppData\\OpenConverter\\resources',
  });
  assert.equal(result, path.join('C:\\Users\\me\\AppData\\OpenConverter\\resources', 'ffprobe.exe'));
});

test('resolveFfprobePath: "ffprobe" on packaged Linux', () => {
  const result = resolveFfprobePath({
    isPackaged: true,
    platform: 'linux',
    resourcesPath: '/usr/share/openconverter/resources',
  });
  assert.equal(result, 'ffprobe');
});

test('resolveFfprobePath: "ffprobe" on packaged macOS', () => {
  const result = resolveFfprobePath({
    isPackaged: true,
    platform: 'darwin',
    resourcesPath: '/Applications/OpenConverter.app/Contents/Resources',
  });
  assert.equal(result, 'ffprobe');
});

test('resolveFfprobePath: "ffprobe" in dev mode regardless of platform', () => {
  const win = resolveFfprobePath({
    isPackaged: false,
    platform: 'win32',
    resourcesPath: 'C:\\temp\\dev\\resources',
  });
  const linux = resolveFfprobePath({
    isPackaged: false,
    platform: 'linux',
    resourcesPath: '/tmp/dev/resources',
  });
  assert.equal(win, 'ffprobe');
  assert.equal(linux, 'ffprobe');
});
```

- [ ] **Step 2: Run tests, verify they fail**

```bash
node --test tests/ffmpeg-path.test.js
```

Expected: FAIL with `Cannot find module '../src/main/ffmpeg-path'` (the file doesn't exist yet).

- [ ] **Step 3: Write the minimal implementation**

Create `src/main/ffmpeg-path.js`:

```js
/**
 * Pure helpers: resolve paths to the bundled ffmpeg + ffprobe executables.
 *
 * Pure functions — take isPackaged/platform/resourcesPath explicitly so
 * they can be unit-tested without spinning up Electron. The main process
 * wraps these with the actual Electron `app` context.
 *
 * Rules:
 *   - Packaged Windows build → resourcesPath/ffmpeg.exe (and ffprobe.exe),
 *     bundled by electron-builder extraResources.
 *   - Packaged Linux / macOS, or any dev mode → 'ffmpeg' / 'ffprobe' (let
 *     the system PATH resolve them).
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

- [ ] **Step 4: Run tests, verify they pass**

```bash
node --test tests/ffmpeg-path.test.js
```

Expected: 8 tests pass, 0 fail.

- [ ] **Step 5: Verify Linux baseline NOT broken**

```bash
node --test tests/ 2>&1 | tail -20
```

Expected: existing 5 test suites still pass (the new ffmpeg-path suite is now part of the 6 total). Pass count compared to Task 0 baseline: should be baseline + 8.

- [ ] **Step 6: Commit**

```bash
git add src/main/ffmpeg-path.js tests/ffmpeg-path.test.js
git commit -m "Add pure resolveFfmpegPath + resolveFfprobePath helpers with unit tests"
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
const { resolveFfmpegPath, resolveFfprobePath } = require('./ffmpeg-path');
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
  // Linux + macOS: keep existing custom traffic-light title bar (v0.2.1
  // behavior preserved). Windows: use OS-native title bar.
  frame: process.platform === 'win32' ? true : false,
  titleBarStyle: process.platform === 'win32' ? 'default' : 'hidden',
  icon: path.join(__dirname, '..', '..', 'build', 'icons', 'icon.png'),
  webPreferences: {
    preload: path.join(__dirname, '..', 'preload', 'index.js'),
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: false,
  },
});
```

> **Why `win32` not `darwin`:** the original draft used
> `frame: process.platform === 'darwin' ? false : true` which would have
> flipped Linux from `frame: false` to `frame: true` — a Linux UI
> breaking change. The corrected condition leaves Linux (and macOS) on
> the existing custom title bar and only changes Windows. This
> satisfies the "no renderer / UI changes" constraint for Linux.

- [ ] **Step 4: Replace the `run: ffmpegRun` import to wire the helpers**

The existing `ffmpeg.js` exports `run` (which currently spawns the literal string `'ffmpeg'` and `'ffprobe'`). We need to keep that signature stable from main's perspective, but ensure the resolution happens at spawn time. Instead of changing `ffmpeg.js`'s public API, expose a wrapper from `index.js`:

Replace the `const { run: ffmpegRun, probeDuration } = require('./ffmpeg');` line with:

```js
const { run: ffmpegRunRaw, probeDuration: probeDurationRaw } = require('./ffmpeg');

// Wrap ffmpegRun so the resolved paths are used at call time. Done in
// main (not ffmpeg.js) so ffmpeg.js stays a thin subprocess wrapper
// with no Electron dependency.
function ffmpegRun(input, output, opts = {}) {
  const ffmpegBin = resolveFfmpegPath({
    isPackaged: app.isPackaged,
    platform: process.platform,
    resourcesPath: process.resourcesPath,
  });
  return ffmpegRunRaw(input, output, { ...opts, ffmpegBin });
}

function probeDuration(filePath) {
  const ffprobeBin = resolveFfprobePath({
    isPackaged: app.isPackaged,
    platform: process.platform,
    resourcesPath: process.resourcesPath,
  });
  return probeDurationRaw(filePath, { ffprobeBin });
}
```

- [ ] **Step 5: Modify src/main/ffmpeg.js to accept the binary path options**

Read current `src/main/ffmpeg.js`. There are two `spawn(...)` calls:
- line 11: `spawn('ffmpeg', args, ...)`
- line 40: `spawn('ffprobe', [...], ...)`

Change both to read from `opts`:

```js
// ffmpeg spawn (around line 11):
const ffmpegBin = opts.ffmpegBin || 'ffmpeg';
delete opts.ffmpegBin;
const proc = spawn(ffmpegBin, args, { stdio: ['ignore', 'pipe', 'pipe'] });

// ffprobe spawn (around line 40):
function runFfprobeDuration(filePath, opts = {}) {
  const ffprobeBin = opts.ffprobeBin || 'ffprobe';
  const proc = spawn(ffprobeBin, [
    '-v', 'error', '-show_entries', 'format=duration',
    '-of', 'default=noprint_wrappers=1:nokey=1', filePath,
  ], { stdio: ['ignore', 'pipe', 'pipe'] });
  // ... existing promise wrapper unchanged ...
}
```

Note: `runFfprobeDuration` is the internal name. The public export
`probeDuration` is what main calls. The change is at the `spawn`
call, not at the export — match the local variable name in your tree.

- [ ] **Step 6: Run all tests to make sure nothing broke**

```bash
node --test tests/ 2>&1 | tail -10
npm run build:linux --dir 2>&1 | tail -5
```

Expected: 5 existing test suites + 8 ffmpeg-path tests all pass. Pass count compared to Task 0 baseline: baseline + 8. Linux build still succeeds (look for `release/linux-unpacked/`).

- [ ] **Step 7: Commit**

```bash
git add src/main/index.js src/main/ffmpeg.js
git commit -m "Wire resolveFfmpegPath/Path helpers, fix Linux frame (win32-only)"
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
```

The `vendor/win/` path from the original draft is replaced — the
gitignore stays tight to the actual download location.

- [ ] **Step 2: Write the setup script**

Create `scripts/setup-win-deps.sh`:

```bash
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
```

- [ ] **Step 3: Make the script executable**

```bash
chmod +x scripts/setup-win-deps.sh
```

- [ ] **Step 4: Run the setup script**

```bash
./scripts/setup-win-deps.sh
```

Expected: Downloads ffmpeg.exe (~80 MB) AND ffprobe.exe (~80 MB) and generates icon.ico. Both `scripts/win-deps/ffmpeg.exe`, `scripts/win-deps/ffprobe.exe`, and `build/icons/icon.ico` should now exist. The script must not fail.

- [ ] **Step 5: Verify outputs**

```bash
ls -lh scripts/win-deps/ffmpeg.exe scripts/win-deps/ffprobe.exe build/icons/icon.ico
file scripts/win-deps/ffmpeg.exe scripts/win-deps/ffprobe.exe build/icons/icon.ico
```

Expected: ffmpeg.exe and ffprobe.exe show as Windows PE executables (`PE32+ executable (GUI) x86-64`). icon.ico shows as `MS Windows icon resource`.

- [ ] **Step 6: Verify Linux baseline NOT broken**

```bash
node --test tests/ 2>&1 | tail -5
```

Expected: same pass count as before this task (baseline + 8 from Task 2, no new changes). The .gitignore and script additions don't affect tests.

- [ ] **Step 7: Commit**

```bash
git add .gitignore scripts/setup-win-deps.sh
git commit -m "Add setup-win-deps.sh to vendor ffmpeg.exe + ffprobe.exe, gitignore scripts/win-deps/"
```

Note: ffmpeg.exe and ffprobe.exe are NOT committed (gitignored). icon.ico will be added in a future commit (after we verify it builds correctly).

---

## Task 5: Update package.json — win target + extraResources + scripts

**Files:**
- Modify: `package.json`

- [ ] **Step 1: Read current package.json `build` block**

```bash
cat package.json
```

- [ ] **Step 2: Add `win`, `nsis`, `portable` to the `build` object (with `extraResources` nested inside `win`)**

Find the existing `"build"` block in `package.json`. It currently has `appId`, `productName`, `files`, `directories`, and `linux` keys. Add a `win` block (sibling of `linux`) with `extraResources` nested INSIDE the `win` block (not at the top level), and add `nsis` and `portable` config blocks as siblings of `win`:

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
    "artifactName": "${productName}-${version}-${arch}.${ext}",
    "extraResources": [
      { "from": "scripts/win-deps/ffmpeg.exe",  "to": "ffmpeg.exe" },
      { "from": "scripts/win-deps/ffprobe.exe", "to": "ffprobe.exe" }
    ]
  },
  "nsis": {
    "oneClick": false,
    "allowToChangeInstallationDirectory": true,
    "perMachine": false,
    "artifactName": "${productName}-${version}-setup.${ext}"
  },
  "portable": {
    "artifactName": "${productName}-${version}-portable.${ext}"
  }
}
```

> **Why `extraResources` inside `win`, not top-level:** if `extraResources`
> is at the top level of `build`, electron-builder applies it to ALL
> targets (including Linux). On Linux dev, `scripts/win-deps/ffmpeg.exe`
> only exists after `setup-win-deps.sh` runs, so the Linux build would
> fail or produce a broken artifact. Nesting inside `win` keeps the
> Linux build path completely untouched.

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

- [ ] **Step 5: Verify Linux baseline NOT broken (CRITICAL)**

```bash
node --test tests/ 2>&1 | tail -5
npm run build:linux --dir 2>&1 | tail -5
```

Expected: same pass count as before this task. Linux build still produces `release/linux-unpacked/`. If Linux build fails because electron-builder complains about a missing ffmpeg.exe, you forgot to nest `extraResources` inside `win` — go back to step 2.

- [ ] **Step 6: Commit**

```bash
git add package.json
git commit -m "Add Windows build target (NSIS + Portable) with bundled ffmpeg+ffprobe"
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

Expected: two files matching the unified naming convention:
- `openconverter-vX.Y.Z-windows-x64-setup.exe` (NSIS installer, ~125 MB)
- `openconverter-vX.Y.Z-windows-x64-portable.exe` (Portable, ~125 MB)

- [ ] **Step 4: Extract NSIS to verify ffmpeg.exe AND ffprobe.exe are bundled**

```bash
mkdir -p /tmp/nsis-check
7z x -y "release/openconverter-v"*"windows-x64-setup.exe" -o/tmp/nsis-check >/dev/null
ls -lh /tmp/nsis-check/resources/ffmpeg.exe /tmp/nsis-check/resources/ffprobe.exe
rm -rf /tmp/nsis-check
```

Expected: BOTH ffmpeg.exe AND ffprobe.exe are present in `resources/`, each ~80 MB.

- [ ] **Step 5: Verify Linux baseline NOT broken (sanity)**

```bash
node --test tests/ 2>&1 | tail -3
npm run build:linux --dir 2>&1 | tail -3
```

Expected: tests pass count unchanged from baseline, Linux build still works. (This task only runs `electron-builder --win`, so Linux shouldn't be touched, but verify anyway.)

- [ ] **Step 6: Commit no code** (artifacts are in release/, which is gitignored)

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
#   2. NSIS installer contains resources/ffmpeg.exe AND resources/ffprobe.exe.
#   3. Wine can start the unpacked Electron binary and keep it alive.
#
# Exit code 0 = all checks passed. Non-zero = first failed check.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE="$ROOT/release"

# 1. Artifact existence
NSIS=$(ls "$RELEASE"/openconverter-v"*"windows-x64-setup.exe" 2>/dev/null | head -1)
PORTABLE=$(ls "$RELEASE"/openconverter-v"*"windows-x64-portable.exe" 2>/dev/null | head -1)

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
  NSIS:     .../openconverter-v0.2.1-windows-x64-setup.exe (125M)
  Portable: .../openconverter-v0.2.1-windows-x64-portable.exe (125M)
PASS: ffmpeg.exe bundled (97M)
PASS: ffprobe.exe bundled (97M)
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

After the existing AppImage install code block (the `chmod +x` / `./OpenConverter-0.2.0.AppImage` lines) and before the `## Build from source` section, add:

```markdown
### Windows

Download the latest `openconverter-vX.Y.Z-windows-x64-setup.exe` (NSIS installer) or
`openconverter-vX.Y.Z-windows-x64-portable.exe` (portable, no install needed) from the
[Releases page](https://github.com/nowa277/OpenConverter/releases).

- **Installer**: double-click `setup.exe`, follow the wizard, choose
  install path.
- **Portable**: place `OpenConverter-X.Y.Z-portable.exe` anywhere (e.g.
  USB drive), double-click to run. No installer, no system changes.

Both bundles include `ffmpeg.exe` and `ffprobe.exe` — no separate
ffmpeg install required.

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

- [ ] **Step 4: Verify Linux baseline NOT broken**

```bash
node --test tests/ 2>&1 | tail -3
```

Expected: same pass count as before this task. README changes don't affect tests, but verify anyway per the §2 hard rules.

- [ ] **Step 5: Commit**

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
- Section 4.2 main/index.js — Task 3 step 3 (BrowserWindow `win32` fix) + Task 3 step 4 (helper wire, both ffmpeg+ffprobe) ✓
- Section 4.3 main/ffmpeg.js — Task 3 step 5 (both spawns take opts) ✓
- Section 4.4 scripts/win-deps/ffmpeg.exe + ffprobe.exe — Task 4 ✓
- Section 4.5 icon.ico — Task 4 ✓
- Section 4.6 setup-win-deps.sh — Task 4 (both binaries) ✓
- Section 4.7 build.test.sh — Task 7 (checks both binaries) ✓
- Section 4.8 README — Task 8 ✓
- Section 4.9 ffmpeg-path.js — Task 2 (8 tests) + Task 3 step 2 (wiring) ✓
- Section 6 verification: 5 layers (unit tests, local build, bundle
  integrity, wine smoke, manual acceptance) — Task 2 (unit), Task 6
  (build), Task 6 (7z extract), Task 7 (smoke), Task 9 (manual) ✓
- Section 9 acceptance criteria — Task 6 (binary check) + Task 7
  (smoke) + Task 9 (manual) ✓
- Section 11 multi-platform principles — reflected throughout
  (Task 0 baseline, per-task Linux verify, additive-only changes) ✓

**Linux zero-impact verification:** every code-modifying task (Tasks
2, 3, 4, 5, 7, 8) has a "verify Linux baseline NOT broken" step that
runs `node --test tests/` and compares to Task 0 baseline. A
regression is a blocker.

**Placeholder scan:** No "TBD" / "TODO" / "implement later". Every step
either has concrete commands or code blocks.

**Type consistency:**
- `resolveFfmpegPath` / `resolveFfprobePath` signatures: `{ isPackaged,
  platform, resourcesPath }` in tests (Task 2) and in main wiring
  (Task 3 step 4) ✓
- `ffmpegRun` / `probeDuration` wrapper signatures match original
  (input, output, opts) and (filePath) ✓
- ffmpeg.js opts flow: `ffmpegBin` + `ffprobeBin` passed in via
  wrappers → consumed in spawn calls (then deleted from opts so
  child_process doesn't choke) ✓

**File structure check:**
- `src/main/ffmpeg-path.js` is single-responsibility (pure helpers) ✓
- `scripts/setup-win-deps.sh` is single-responsibility (deps setup) ✓
- `tests/build.test.sh` is single-responsibility (build smoke) ✓
- Existing `src/main/ffmpeg.js` stays thin subprocess wrapper ✓
- New `src/main/ffmpeg-path.js` keeps ffmpeg.js Electron-free ✓

**Branch strategy:** all work happens on `windows-installer` branch
(off main v0.2.1). Each task = 1 commit. Each commit is independently
reviewable.

**Known scope gap:** Task 9 (manual acceptance) is not automatable.
Documented as deferred to user; spec Section 9 explicitly requires
real-Windows testing which is by definition not in this implementation
plan.

**Refinements from brainstorming (vs. original plan):**
1. Task 0 added (Linux baseline + branch setup)
2. Q1 fix: BrowserWindow uses `win32 ? true : false` (not
   `darwin ? false : true`) — preserves v0.2.1 Linux UI
3. Q2 fix: `extraResources` nested inside `win` block — keeps Linux
   build path completely untouched
4. ffprobe.exe added throughout (plan originally only mentioned ffmpeg)
5. Per-task Linux baseline verification added (not just at end)
6. `vendor/win/` gitignore removed (replaced by `scripts/win-deps/`)
7. Spec §11 added with long-term multi-platform principles

No issues found. Plan is ready for execution on the `windows-installer`
branch.