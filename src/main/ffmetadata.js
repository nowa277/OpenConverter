'use strict';

const fs = require('node:fs');

/** Escape `\`, `=`, `;`, `#`, and newline for an ffmpeg FFMETADATA1 value. */
function escapeFfmeta(value) {
  return String(value).replace(/[\\=;#\n]/g, (ch) => `\\${ch}`);
}

function writeFfmetadata(filePath, meta = {}) {
  const lines = [';FFMETADATA1'];
  for (const [key, value] of Object.entries(meta)) {
    if (value === undefined || value === null || String(value).length === 0) continue;
    lines.push(`${key}=${escapeFfmeta(String(value))}`);
  }
  fs.writeFileSync(filePath, `${lines.join('\n')}\n`);
}

module.exports = { escapeFfmeta, writeFfmetadata };
