'use strict';

const fs = require('node:fs');
const initSqlJs = require('sql.js');

/**
 * Loads the keys mapping from a kgg.keys text file.
 * Format is "id$ekey" per line.
 *
 * @param {string} keysPath
 * @returns {Map<string, string>}
 */
function loadKeysMap(keysPath) {
  const map = new Map();
  if (!fs.existsSync(keysPath)) return map;
  const text = fs.readFileSync(keysPath, 'utf-8');
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.length === 0) continue;
    const sep = line.indexOf('$');
    if (sep <= 0) continue; // skip malformed lines or comments
    const id = line.slice(0, sep);
    const key = line.slice(sep + 1);
    if (id.length > 0 && key.length > 0) {
      map.set(id, key);
    }
  }
  return map;
}

/**
 * Saves the keys mapping to a kgg.keys text file.
 * Sorted by key ID for consistency.
 *
 * @param {string} keysPath
 * @param {Map<string, string>} map
 */
function saveKeysMap(keysPath, map) {
  const sorted = Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b));
  const text = sorted.map(([id, key]) => `${id}$${key}`).join('\n') + '\n';
  fs.writeFileSync(keysPath, text, 'utf-8');
}

/**
 * Imports key maps from decrypted SQLite database buffer.
 *
 * @param {Buffer} dbBuffer - Decrypted KGMusicV3.db bytes
 * @returns {Promise<Map<string, string>>}
 */
async function importFromDb(dbBuffer) {
  const SQL = await initSqlJs();
  const db = new SQL.Database(new Uint8Array(dbBuffer));
  const result = new Map();
  try {
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
  } catch (err) {
    db.close();
    throw new Error(`Failed to query KGG keys from database: ${err.message}`);
  }
  db.close();
  return result;
}

module.exports = {
  loadKeysMap,
  saveKeysMap,
  importFromDb,
};
