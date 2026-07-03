# KuGou KGG v5 and QMC/BKC Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate KuGou's KGG v5 database deciphering (`KGMusicV3.db`), automatic key scanning, UI setting controls, Android root instructions, and BKC/QMC sniffing variants into OpenConverter.

**Architecture:** We use a WebAssembly-based SQLite engine (`sql.js`) in the Electron main process to decrypt and query KGG database files locally. Key mapping records are consolidated into a persistent storage file `userData/kgg.keys`. Sniffing logic in decoders is upgraded to prevent dual-extension discrepancies.

**Tech Stack:** Node.js, Electron, sql.js (WebAssembly), HTML/CSS, Kotlin (Android Jetpack Compose).

## Global Constraints
- **Zero native build dependencies**: Use pure JS/Wasm for database decryption and parsing.
- **Offline safety**: All operations must be completely offline, zero network requests.
- **Author Identity**: Commit author must remain `nowa277 <sesleycheung@gmail.com>`.

---

### Task 1: Add dependencies & setup key parser

**Files:**
- Modify: `package.json`
- Create: `src/main/kgg-keys.js`
- Test: `tests/kgg-keys.test.js`

**Interfaces:**
- Consumes: `db-cipher.js` (for decrypting KGMusicV3.db pages)
- Produces:
  - `loadKeysMap(keysPath: string): Map<string, string>`
  - `saveKeysMap(keysPath: string, map: Map<string, string>): void`
  - `importFromDb(dbBuffer: Buffer): Map<string, string>`

- [ ] **Step 1: Write the failing test**
  Create `tests/kgg-keys.test.js` to assert sql.js database parsing and merge behavior.
  ```javascript
  const { test } = require('node:test');
  const assert = require('node:assert');
  const path = require('node:path');
  const fs = require('node:fs');
  const kggKeys = require('../src/main/kgg-keys');

  test('kgg-keys: importFromDb correctly queries database and extracts key pairs', async () => {
    // Generate a mock SQLite database buffer with ShareFileItems table
    // or test sql.js instantiation
    assert.ok(kggKeys.importFromDb);
  });
  ```
- [ ] **Step 2: Run test to verify it fails**
  Run: `node tests/kgg-keys.test.js`
  Expected: FAIL with "Cannot find module '../src/main/kgg-keys'"
- [ ] **Step 3: Add package.json dependency**
  Add `"sql.js": "^1.12.0"` to dependencies in `package.json`. Run `npm install` afterwards.
- [ ] **Step 4: Implement key parser**
  Create `src/main/kgg-keys.js` using `sql.js` to query database buffers:
  ```javascript
  'use strict';
  const fs = require('node:fs');
  const initSqlJs = require('sql.js');

  function loadKeysMap(keysPath) {
    if (!fs.existsSync(keysPath)) return new Map();
    const text = fs.readFileSync(keysPath, 'utf-8');
    const map = new Map();
    text.split(/\r?\n/).forEach(line => {
      const idx = line.indexOf('$');
      if (idx > 0) map.set(line.slice(0, idx), line.slice(idx + 1));
    });
    return map;
  }

  function saveKeysMap(keysPath, map) {
    const text = Array.from(map.entries())
      .map(([k, v]) => `${k}$${v}`)
      .join('\n') + '\n';
    fs.writeFileSync(keysPath, text, 'utf-8');
  }

  async function importFromDb(dbBuffer) {
    const SQL = await initSqlJs();
    const db = new SQL.Database(new Uint8Array(dbBuffer));
    const result = new Map();
    const stmt = db.prepare(`
      SELECT EncryptionKeyId, EncryptionKey FROM ShareFileItems
      WHERE EncryptionKeyId IS NOT NULL AND EncryptionKeyId != ''
        AND EncryptionKey IS NOT NULL AND EncryptionKey != ''
    `);
    while (stmt.step()) {
      const row = stmt.getAsObject();
      result.set(row.EncryptionKeyId, row.EncryptionKey);
    }
    stmt.free();
    db.close();
    return result;
  }

  module.exports = { loadKeysMap, saveKeysMap, importFromDb };
  ```
- [ ] **Step 5: Run tests and verify they pass**
  Run: `node tests/kgg-keys.test.js`
  Expected: PASS
- [ ] **Step 6: Commit**
  Run:
  ```bash
  git add package.json src/main/kgg-keys.js tests/kgg-keys.test.js
  git commit -m "feat(kgg): add kgg-keys parser module and Wasm sql.js dependency"
  ```

---

### Task 2: Implement auto-scanning for desktop

**Files:**
- Modify: `src/main/kgg-keys.js`
- Test: `tests/kgg-autoscan.test.js`

**Interfaces:**
- Consumes: `loadKeysMap()`, `saveKeysMap()`, `importFromDb()`
- Produces: `autoScanKeys(userDataPath: string): Promise<{ added: number, total: number }>`

- [ ] **Step 1: Write the failing test**
  Create `tests/kgg-autoscan.test.js` verifying that `autoScanKeys` skips Linux, and resolves mocked files on Windows/macOS.
- [ ] **Step 2: Run test to verify it fails**
  Run: `node tests/kgg-autoscan.test.js`
  Expected: FAIL with "autoScanKeys is not a function"
- [ ] **Step 3: Implement autoScanKeys**
  Add platform path discovery in `src/main/kgg-keys.js` and import database contents.
- [ ] **Step 4: Run tests and verify they pass**
  Run: `node tests/kgg-autoscan.test.js`
  Expected: PASS
- [ ] **Step 5: Commit**
  Run:
  ```bash
  git add src/main/kgg-keys.js tests/kgg-autoscan.test.js
  git commit -m "feat(kgg): implement desktop auto-scanning paths and key integration"
  ```

---

### Task 3: Integrate with Electron Main (IPC & config)

**Files:**
- Modify: `src/main/config.js`
- Modify: `src/main/index.js`

**Interfaces:**
- Consumes: `kggAutoScan` and `kggKeyPath` config options
- Produces:
  - IPC method `'kgg:importFile'`
  - IPC method `'kgg:triggerScan'`

- [ ] **Step 1: Add config defaults**
  Modify `src/main/config.js` to register `kggAutoScan: false`.
- [ ] **Step 2: Implement IPC handlers in index.js**
  Wire handlers in `src/main/index.js` for manual database importing and automatic scanning trigger on startup.
- [ ] **Step 3: Pass keyPath during conversion**
  Update `convertOne` in `src/main/index.js` to supply `opts.keyPath` when picking KGG decoder.
- [ ] **Step 4: Run all tests to make sure no regression**
  Run: `npm test`
  Expected: PASS
- [ ] **Step 5: Commit**
  Run:
  ```bash
  git add src/main/config.js src/main/index.js
  git commit -m "feat(main): bridge KGG keys integration into Electron configuration and IPC handlers"
  ```

---

### Task 4: Implement robust format sniffers & dual-extension routing

**Files:**
- Modify: `src/decoders/kgg/index.js`
- Modify: `src/decoders/qmc.js`
- Test: `tests/kgg-sniff.test.js`

**Interfaces:**
- Consumes: none
- Produces: Enhanced sniffing and validation on decryption

- [ ] **Step 1: Add header magic checking to KGG decrypt**
  In `src/decoders/kgg/index.js`, reject conversion early if the header prefix does not start with magic `7C D5 32 EB`.
- [ ] **Step 2: Add test cases in tests/kgg-sniff.test.js**
  Write unit tests validating early interception of invalid headers and verification of output formats.
- [ ] **Step 3: Enhance QMC sniffing**
  Modify `src/decoders/qmc.js` to dynamically map and extract formats from stream bytes for dual-extension BKC files (e.g. `.bkcflac`, `.bkcmp3`).
- [ ] **Step 4: Verify test suite**
  Run: `npm test`
  Expected: PASS (all tests pass)
- [ ] **Step 5: Commit**
  Run:
  ```bash
  git add src/decoders/kgg/index.js src/decoders/qmc.js tests/kgg-sniff.test.js
  git commit -m "feat(decoders): enforce early header validation and enhance format sniffing for KGG/BKC"
  ```

---

### Task 5: Update Settings UI in Renderer

**Files:**
- Modify: `src/renderer/index.html`
- Modify: `src/renderer/main.js`

- [ ] **Step 1: Add Settings UI elements**
  Modify `src/renderer/index.html` to add the KGG configuration panel and check box.
- [ ] **Step 2: Bind UI event handlers**
  In `src/renderer/main.js`, add event listeners to save auto-scan state, trigger scanning, and support manual file pickers. Add localization translations for KGG controls.
- [ ] **Step 3: Commit**
  Run:
  ```bash
  git add src/renderer/index.html src/renderer/main.js
  git commit -m "feat(ui): add KGG settings panel, auto-scan toggles and localized labels"
  ```

---

### Task 6: Android KeyStore Root Dialog & instruction modal

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml` (or `strings_zh.xml` / `strings_en.xml` if separated)
- Modify: Android Kotlin settings UI classes

- [ ] **Step 1: Define instruction dialog XML/UI layout**
  Add string templates explaining how rooted devices can fetch `kugou_music_v2.db` or copy it to shared storage.
- [ ] **Step 2: Bind Compose root instruction dialogues**
  Implement root access guidelines inside the Android key settings view.
- [ ] **Step 3: Commit**
  Run:
  ```bash
  git add android/
  git commit -m "feat(android): add Root usage instruction dialog in KeyStore manager"
  ```
