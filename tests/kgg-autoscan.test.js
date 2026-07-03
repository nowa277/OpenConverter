'use strict';

const { test } = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const initSqlJs = require('sql.js');

// Mock db-cipher decryption methods before importing kgg-keys
const dbCipher = require('../src/decoders/kgg/db-cipher');

// Save original methods
const origDecryptFirstPage = dbCipher.decryptFirstPage;
const origDecryptPage = dbCipher.decryptPage;

// Override to mock decrypt as pass-through for plaintext testing database
dbCipher.decryptFirstPage = (page) => {
  // Replace header with SQLite format
  Buffer.from('SQLite format 3\0', 'ascii').copy(page, 0);
};
dbCipher.decryptPage = (page) => {
  // No-op for mock passthrough
};

const kggKeys = require('../src/main/kgg-keys');

test('kgg-autoscan: autoScanKeys skips scanning on linux platform', async () => {
  const tmpDir = path.join(__dirname, 'temp_autoscan_linux');
  fs.mkdirSync(tmpDir, { recursive: true });
  try {
    const res = await kggKeys.autoScanKeys(tmpDir, {
      mockPlatform: 'linux',
      mockEnv: {},
    });
    assert.deepStrictEqual(res, { added: 0, total: 0 });
    assert.strictEqual(fs.existsSync(path.join(tmpDir, 'kgg.keys')), false);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    // Restore
    dbCipher.decryptFirstPage = origDecryptFirstPage;
    dbCipher.decryptPage = origDecryptPage;
  }
});

test('kgg-autoscan: autoScanKeys scans, decrypts, and merges KGG keys on mocked platform paths', async () => {
  // Temporarily override decrypt logic for this test as well
  dbCipher.decryptFirstPage = (page) => {
    Buffer.from('SQLite format 3\0', 'ascii').copy(page, 0);
  };
  dbCipher.decryptPage = (page) => {};

  // 1. Setup mock keys file
  const tmpDir = path.join(__dirname, 'temp_autoscan_success');
  fs.mkdirSync(tmpDir, { recursive: true });

  const initialKeysPath = path.join(tmpDir, 'kgg.keys');
  fs.writeFileSync(initialKeysPath, 'preexisting_id$preexisting_key\n', 'utf-8');

  // 2. Generate a mock plaintext SQLite database
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  db.run('CREATE TABLE ShareFileItems (EncryptionKeyId TEXT, EncryptionKey TEXT)');
  db.run("INSERT INTO ShareFileItems VALUES ('new_id_1', 'new_key_1')");
  db.run("INSERT INTO ShareFileItems VALUES ('preexisting_id', 'new_overwritten_key')"); // merge test
  const dbData = db.export();
  db.close();

  const mockDbBuffer = Buffer.from(dbData);

  // 3. Write database to a temporary location and scan it
  const mockDbPath = path.join(tmpDir, 'mock_KGMusicV3.db');
  fs.writeFileSync(mockDbPath, mockDbBuffer);

  try {
    const res = await kggKeys.autoScanKeys(tmpDir, {
      mockPlatform: 'win32',
      mockPaths: [mockDbPath],
    });

    // We added 'new_id_1' (1 key). 'preexisting_id' is updated, size is still 2.
    assert.strictEqual(res.added, 1);
    assert.strictEqual(res.total, 2);

    const merged = kggKeys.loadKeysMap(initialKeysPath);
    assert.strictEqual(merged.get('preexisting_id'), 'new_overwritten_key');
    assert.strictEqual(merged.get('new_id_1'), 'new_key_1');
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    // Restore original methods
    dbCipher.decryptFirstPage = origDecryptFirstPage;
    dbCipher.decryptPage = origDecryptPage;
  }
});
