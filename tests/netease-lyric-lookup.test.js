'use strict';
const { test } = require('node:test');
const assert = require('node:assert');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');
const { findLrc } = require('../src/main/netease-lyric-lookup');

test('prefers LrcDownload then LrcCache then root', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-lrc-'));
  fs.mkdirSync(path.join(root, 'LrcDownload'));
  fs.mkdirSync(path.join(root, 'LrcCache'));
  fs.writeFileSync(path.join(root, 'LrcDownload', '1'), JSON.stringify({ lrc: '[00:00.00]A' }));
  fs.writeFileSync(path.join(root, 'LrcCache', '2'), JSON.stringify({ lrc: '[00:00.00]B' }));
  fs.writeFileSync(path.join(root, '3'), '[00:00.00]C');
  assert.strictEqual(findLrc(root, '1'), '[00:00.00]A');
  assert.strictEqual(findLrc(root, '2'), '[00:00.00]B');
  assert.strictEqual(findLrc(root, '3'), '[00:00.00]C');
  assert.strictEqual(findLrc(root, 'missing'), null);
  fs.rmSync(root, { recursive: true, force: true });
});

test('unparseable file returns null', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-lrc-'));
  fs.mkdirSync(path.join(root, 'LrcDownload'));
  fs.writeFileSync(path.join(root, 'LrcDownload', '1'), '????');
  assert.strictEqual(findLrc(root, '1'), null);
  fs.rmSync(root, { recursive: true, force: true });
});

test('blank id or blank root returns null', () => {
  assert.strictEqual(findLrc('/tmp', ''), null);
  assert.strictEqual(findLrc('', '1'), null);
  assert.strictEqual(findLrc(null, '1'), null);
});

test('LrcDownload wins over LrcCache and root for the same id', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-lrc-'));
  fs.mkdirSync(path.join(root, 'LrcDownload'));
  fs.mkdirSync(path.join(root, 'LrcCache'));
  fs.writeFileSync(path.join(root, 'LrcDownload', '1'), JSON.stringify({ lrc: '[00:00.00]A' }));
  fs.writeFileSync(path.join(root, 'LrcCache', '1'), JSON.stringify({ lrc: '[00:00.00]B' }));
  fs.writeFileSync(path.join(root, '1'), '[00:00.00]C');
  assert.strictEqual(findLrc(root, '1'), '[00:00.00]A');
  fs.rmSync(root, { recursive: true, force: true });
});

test('unparseable LrcDownload falls through to LrcCache', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-lrc-'));
  fs.mkdirSync(path.join(root, 'LrcDownload'));
  fs.mkdirSync(path.join(root, 'LrcCache'));
  fs.writeFileSync(path.join(root, 'LrcDownload', '1'), '????');
  fs.writeFileSync(path.join(root, 'LrcCache', '1'), JSON.stringify({ lrc: '[00:00.00]B' }));
  assert.strictEqual(findLrc(root, '1'), '[00:00.00]B');
  fs.rmSync(root, { recursive: true, force: true });
});

test('injected readFile map is used instead of disk', () => {
  const root = '/virtual-lyrics';
  const files = new Map([
    [path.join(root, 'LrcDownload', '1'), Buffer.from(JSON.stringify({ lrc: '[00:00.00]A' }))],
    [path.join(root, 'LrcCache', '2'), Buffer.from(JSON.stringify({ lrc: '[00:00.00]B' }))],
    [path.join(root, '3'), Buffer.from('[00:00.00]C')],
  ]);
  const readFile = (filePath) => {
    if (!files.has(filePath)) {
      const err = new Error('ENOENT');
      err.code = 'ENOENT';
      throw err;
    }
    return files.get(filePath);
  };
  assert.strictEqual(findLrc(root, '1', { readFile }), '[00:00.00]A');
  assert.strictEqual(findLrc(root, '2', { readFile }), '[00:00.00]B');
  assert.strictEqual(findLrc(root, '3', { readFile }), '[00:00.00]C');
  assert.strictEqual(findLrc(root, 'missing', { readFile }), null);
});
