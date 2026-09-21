'use strict';

/**
 * Config-shape check without instantiating electron-store (which writes
 * to ~/.config/electron-store-nodejs when Electron is not running).
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'config.js'), 'utf8');

test('DEFAULTS includes empty ncmLyricsDir', () => {
  assert.match(src, /ncmLyricsDir:\s*''/);
});
