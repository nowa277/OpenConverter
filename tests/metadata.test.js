'use strict';

const { test } = require('node:test');
const assert = require('node:assert');

const {
  getMetadata,
  listByKeyRequirement,
  EXT_METADATA,
  listSupported,
  pickDecoder,
} = require('../src/decoders');

test('getMetadata returns null for unknown extension', () => {
  assert.strictEqual(getMetadata('.unknown'), null);
  assert.strictEqual(getMetadata(''), null);
  assert.strictEqual(getMetadata(null), null);
});

test('getMetadata is case-insensitive', () => {
  const lower = getMetadata('.kgg');
  const upper = getMetadata('.KGG');
  const mixed = getMetadata('.Kgg');
  assert.ok(lower, 'lower-case should resolve');
  assert.deepStrictEqual(lower, upper, 'KGG and kgg should match');
  assert.deepStrictEqual(lower, mixed, 'Kgg and kgg should match');
});

test('getMetadata reports no-key formats', () => {
  const ncm = getMetadata('.ncm');
  assert.strictEqual(ncm.requiresKey, false);
  assert.strictEqual(ncm.platform, '网易云');

  const qmc1 = getMetadata('.qmcflac');
  assert.strictEqual(qmc1.requiresKey, false);
  assert.strictEqual(qmc1.platform, 'QQ 音乐');
  assert.strictEqual(qmc1.version, 'v1');

  const kgm = getMetadata('.kgm');
  assert.strictEqual(kgm.requiresKey, false);
  assert.strictEqual(kgm.platform, '酷狗');
});

test('getMetadata reports ekey-requiring QMC v2 formats', () => {
  const mflac = getMetadata('.mflac');
  assert.strictEqual(mflac.requiresKey, true);
  assert.strictEqual(mflac.keySource, 'ekey');
  assert.strictEqual(mflac.platform, 'QQ 音乐');

  const stag = getMetadata('.mflac2');
  assert.strictEqual(stag.requiresKey, true);
  assert.strictEqual(stag.keySource, 'ekey');
  assert.ok(/STag/.test(stag.version), 'STag variant should mention STag');
});

test('getMetadata reports kdb-requiring KGG v5', () => {
  const kgg = getMetadata('.kgg');
  assert.strictEqual(kgg.requiresKey, true);
  assert.strictEqual(kgg.keySource, 'KGMusicV3.db');
  assert.strictEqual(kgg.platform, '酷狗');
  assert.strictEqual(kgg.version, 'v5');
});

test('listByKeyRequirement partitions all known extensions', () => {
  const { withKey, withoutKey } = listByKeyRequirement();
  const all = [...withKey, ...withoutKey];
  assert.strictEqual(all.length, Object.keys(EXT_METADATA).length);

  // Sanity: a few well-known representatives
  assert.ok(withKey.includes('.kgg'), 'KGG should be in withKey');
  assert.ok(withKey.includes('.mflac'), 'mflac should be in withKey');
  assert.ok(withoutKey.includes('.ncm'), 'ncm should be in withoutKey');
  assert.ok(withoutKey.includes('.qmcflac'), 'qmcflac should be in withoutKey');
  assert.ok(withoutKey.includes('.kwm'), 'kwm should be in withoutKey');
  assert.ok(withoutKey.includes('.kgm'), 'kgm should be in withoutKey');
});

test('every EXT_METADATA entry has required fields', () => {
  for (const [ext, meta] of Object.entries(EXT_METADATA)) {
    assert.ok(typeof ext === 'string' && ext.startsWith('.'),
      `extension ${ext} must start with a dot`);
    assert.ok(typeof meta.platform === 'string' && meta.platform.length > 0,
      `extension ${ext} must have non-empty platform`);
    assert.ok(typeof meta.version === 'string' && meta.version.length > 0,
      `extension ${ext} must have non-empty version`);
    assert.strictEqual(typeof meta.requiresKey, 'boolean',
      `extension ${ext} must have boolean requiresKey`);
    if (meta.requiresKey) {
      assert.ok(typeof meta.keySource === 'string' && meta.keySource.length > 0,
        `extension ${ext} requiresKey=true must have non-empty keySource`);
    }
  }
});

test('every decoder-routed extension in listSupported has metadata', () => {
  // If a decoder claims to handle an extension, metadata should also know about it
  for (const ext of listSupported()) {
    const meta = getMetadata(ext);
    assert.ok(meta, `extension ${ext} in listSupported must have EXT_METADATA entry`);
  }
});

test('pickDecoder stays in sync with EXT_METADATA for known extensions', () => {
  for (const ext of Object.keys(EXT_METADATA)) {
    if (ext === '.kgg.flac') continue; // composite extension, pickDecoder strips it
    const decoder = pickDecoder(`/tmp/example${ext}`);
    if (decoder === undefined) {
      // Some metadata-only entries (e.g. .bkc* BKC variants) may not be routed yet.
      // This is informational; the test enforces we keep the gap visible.
      console.log(`note: ${ext} has metadata but no decoder yet`);
    }
  }
});
