'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const cliPath = path.join(__dirname, '..', 'src', 'cli.js');

test('CLI --help lists --ncm-lyrics-dir', () => {
  const out = execFileSync(process.execPath, [cliPath, '--help'], { encoding: 'utf8' });
  assert.match(out, /--ncm-lyrics-dir=/);
  assert.match(out, /NetEase LrcDownload \/ LrcCache directory \(optional\)/);
});

test('CLI maps --ncm-lyrics-dir to convertOne ncmLyricsDir', () => {
  const src = fs.readFileSync(cliPath, 'utf8');
  assert.match(src, /startsWith\('--ncm-lyrics-dir='\)/);
  assert.match(src, /ncmLyricsDir:\s*opts\.ncmLyricsDir/);
});

test('CLI --help lists --no-ncm-lyrics', () => {
  const out = execFileSync(process.execPath, [cliPath, '--help'], { encoding: 'utf8' });
  assert.match(out, /--no-ncm-lyrics/);
  assert.match(out, /Disable NetEase lyric fetch \(on by default\)/);
});

test('CLI maps --no-ncm-lyrics to convertOne ncmLyricsEnabled false', () => {
  const src = fs.readFileSync(cliPath, 'utf8');
  assert.match(src, /=== '--no-ncm-lyrics'/);
  assert.match(src, /opts\.ncmLyricsEnabled\s*=\s*false/);
  assert.match(src, /ncmLyricsEnabled:\s*opts\.ncmLyricsEnabled\s*!==\s*false/);
});
