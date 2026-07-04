'use strict';

const { test } = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const initSqlJs = require('sql.js');
const kggKeys = require('../src/main/kgg-keys');

test('kgg-keys: loadKeysMap and saveKeysMap round-trip works', () => {
  const tmpFile = path.join(__dirname, 'temp_kgg_test.keys');
  try {
    const original = new Map([
      ['hash1', 'ekey1'],
      ['hash2', 'ekey2'],
    ]);
    kggKeys.saveKeysMap(tmpFile, original);

    const loaded = kggKeys.loadKeysMap(tmpFile);
    assert.strictEqual(loaded.size, 2);
    assert.strictEqual(loaded.get('hash1'), 'ekey1');
    assert.strictEqual(loaded.get('hash2'), 'ekey2');
  } finally {
    if (fs.existsSync(tmpFile)) fs.unlinkSync(tmpFile);
  }
});

test('kgg-keys: importFromDb correctly extracts keys from a real SQLite DB buffer', async () => {
  // 1. Initialize a blank sql.js database and write the required schema
  const SQL = await initSqlJs();
  const db = new SQL.Database();
  
  db.run(`
    CREATE TABLE ShareFileItems (
      EncryptionKeyId TEXT,
      EncryptionKey TEXT
    )
  `);

  db.run(`
    INSERT INTO ShareFileItems (EncryptionKeyId, EncryptionKey)
    VALUES 
      ('id1', 'key1'),
      ('id2', 'key2'),
      ('', 'invalid_id'),
      ('id3', '')
  `);

  // 2. Export database to Uint8Array buffer
  const dbData = db.export();
  db.close();

  // 3. Test importFromDb
  const extracted = await kggKeys.importFromDb(Buffer.from(dbData));
  assert.strictEqual(extracted.size, 2);
  assert.strictEqual(extracted.get('id1'), 'key1');
  assert.strictEqual(extracted.get('id2'), 'key2');
  assert.strictEqual(extracted.has('id3'), false);
});
