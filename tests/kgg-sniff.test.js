'use strict';

const { test } = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const kgg = require('../src/decoders/kgg');
const qmc = require('../src/decoders/qmc');

test('kgg-sniff: early magic validation throws user-friendly error', () => {
  const badInput = Buffer.from('bad_magic_bytes_here_long_enough_to_test');
  const dummyProvider = {};
  assert.throws(
    () => kgg.decrypt(badInput, dummyProvider),
    /Not a valid KGG v5 file/
  );
});

test('qmc-sniff: BKC formats are correctly routed to QMCv2 decryption', () => {
  // Mock file paths and verify they trigger v2 path.
  // Actually, we can test EXT_MAP_V2 entries directly.
  assert.strictEqual(qmc.EXT_MAP_V2['.bkcmp3'], 'mp3');
  assert.strictEqual(qmc.EXT_MAP_V2['.bkcflac'], 'flac');
  assert.strictEqual(qmc.EXT_MAP_V2['.bkcogg'], 'ogg');
});

test('qmc-sniff: inferFormat successfully detects header patterns', () => {
  // FLAC magic
  const flacBuf = Buffer.from([0x66, 0x4c, 0x61, 0x43, 0, 0, 0, 0]);
  assert.strictEqual(qmc.inferFormat(flacBuf, 'mp3'), 'flac');

  // MP3 ID3
  const id3Buf = Buffer.from([0x49, 0x4d, 0x33, 0, 0, 0, 0, 0]); // not ID3
  assert.strictEqual(qmc.inferFormat(id3Buf, 'flac'), 'flac'); // returns fallback
  
  const id3Real = Buffer.from([0x49, 0x44, 0x33, 0, 0, 0, 0, 0]);
  assert.strictEqual(qmc.inferFormat(id3Real, 'flac'), 'mp3');
});
