'use strict';

const fs = require('node:fs');
const path = require('node:path');
const initSqlJs = require('sql.js');
const dbCipher = require('../decoders/kgg/db-cipher');

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
 * Decrypts encrypted KGMusicV3.db buffer in memory.
 *
 * @param {Buffer} buffer
 * @returns {Buffer} decrypted buffer
 */
function decryptDatabaseBuffer(buffer) {
  const pageSize = 1024;
  if (buffer.length < pageSize) {
    throw new Error('Database buffer is too small');
  }

  // Work on a copy of the buffer
  const dec = Buffer.from(buffer);
  if (dbCipher.isPlaintextHeader(dec)) {
    return dec;
  }

  dbCipher.decryptFirstPage(dec.subarray(0, pageSize), dbCipher.MASTER_KEY);

  for (let offset = pageSize, pageNum = 2; offset < dec.length; offset += pageSize, pageNum++) {
    const page = dec.subarray(offset, offset + pageSize);
    if (page.length < pageSize) break;
    dbCipher.decryptPage(page, pageNum, dbCipher.MASTER_KEY);
  }

  return dec;
}

/**
 * Imports key maps from decrypted SQLite database buffer.
 *
 * @param {Buffer} dbBuffer - Decrypted KGMusicV3.db bytes
 * @returns {Promise<Map<string, string>>}
 */
async function importFromDb(dbBuffer) {
  const SQL = await initSqlJs();
  const decrypted = decryptDatabaseBuffer(dbBuffer);
  const db = new SQL.Database(new Uint8Array(decrypted));
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

/**
 * Auto scans default folders for KGMusicV3.db, extracts keys and merges them into kgg.keys file.
 *
 * @param {string} userDataPath
 * @param {object} [opts] - Mock platform options for testing
 * @returns {Promise<{ added: number, total: number }>}
 */
async function autoScanKeys(userDataPath, opts = {}) {
  const platform = opts.mockPlatform || process.platform;
  const env = opts.mockEnv || process.env;

  const pathsToScan = [];
  if (opts.mockPaths) {
    pathsToScan.push(...opts.mockPaths);
  } else if (platform === 'win32') {
    const allUsers = env.ALLUSERSPROFILE || 'C:\\ProgramData';
    const appData = env.APPDATA || '';
    pathsToScan.push(
      path.join(allUsers, 'KuGou', 'KGMusic', 'KGMusicV3.db'),
      path.join(allUsers, 'KuGou', 'KGMusicV3.db'),
      'C:\\Users\\Public\\KuGou\\KGMusic\\KGMusicV3.db'
    );
    if (appData) {
      pathsToScan.push(path.join(appData, 'KuGou', 'KGMusicV3.db'));
      pathsToScan.push(path.join(appData, 'KuGou8', 'KGMusicV3.db'));
    }
  } else if (platform === 'darwin') {
    const home = env.HOME || '';
    if (home) {
      pathsToScan.push(
        path.join(home, 'Library', 'Application Support', 'KuGou', 'KGMusicV3.db'),
        path.join(home, 'Library', 'Containers', 'com.kugou.mac', 'Data', 'Documents', 'KuGou', 'KGMusicV3.db')
      );
    }
  }

  let mergedCount = 0;
  const targetKeysPath = path.join(userDataPath, 'kgg.keys');
  const currentMap = loadKeysMap(targetKeysPath);
  const initialSize = currentMap.size;

  for (const dbPath of pathsToScan) {
    if (fs.existsSync(dbPath)) {
      try {
        const buf = fs.readFileSync(dbPath);
        const incoming = await importFromDb(buf);
        for (const [id, val] of incoming.entries()) {
          currentMap.set(id, val);
        }
      } catch (err) {
        // Log or suppress error to proceed scanning other locations
      }
    }
  }

  const newSize = currentMap.size;
  if (newSize > initialSize) {
    saveKeysMap(targetKeysPath, currentMap);
  }

  return {
    added: newSize - initialSize,
    total: newSize,
  };
}

module.exports = {
  loadKeysMap,
  saveKeysMap,
  importFromDb,
  autoScanKeys,
};
