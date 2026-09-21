'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { toLrc } = require('./netease-lyric-parser');

function isBlank(value) {
  return value == null || String(value).trim() === '';
}

function findLrc(rootDir, musicId, options) {
  if (isBlank(rootDir) || isBlank(musicId)) return null;
  const readFile = options && typeof options.readFile === 'function'
    ? options.readFile
    : (filePath) => fs.readFileSync(filePath);
  const id = String(musicId);
  const rels = [
    path.join('LrcDownload', id),
    path.join('LrcCache', id),
    id,
  ];
  for (const rel of rels) {
    try {
      const bytes = readFile(path.join(rootDir, rel));
      const lrc = toLrc(bytes);
      if (lrc != null) return lrc;
    } catch {
      // Fail-open: missing or unreadable files must not throw.
    }
  }
  return null;
}

module.exports = { findLrc };
