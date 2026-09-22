'use strict';
const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { resolveLrc } = require('../src/main/netease-lyric-resolve');

test('disabled skips cache and http', async () => {
  let http = 0;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-r-'));
  fs.mkdirSync(path.join(dir, 'LrcDownload'));
  fs.writeFileSync(path.join(dir, 'LrcDownload', '1'), '{"lrc":"[00:00.00]A"}');
  const lrc = await resolveLrc({
    musicId: '1', enabled: false, extraRootDir: dir,
    fetchJson: async () => { http += 1; return '{"lrc":{"lyric":"[00:00.00]B"}}'; },
    listRoots: () => [],
  });
  assert.strictEqual(lrc, null);
  assert.strictEqual(http, 0);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('cache hit does not call http', async () => {
  let http = 0;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-r-'));
  fs.mkdirSync(path.join(dir, 'LrcDownload'));
  fs.writeFileSync(path.join(dir, 'LrcDownload', '1'), '{"lrc":"[00:00.00]A"}');
  const lrc = await resolveLrc({
    musicId: '1', enabled: true, extraRootDir: dir,
    fetchJson: async () => { http += 1; return 'nope'; },
    listRoots: () => [],
  });
  assert.strictEqual(lrc, '[00:00.00]A');
  assert.strictEqual(http, 0);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('transient fetch failure is retried', async () => {
  let calls = 0;
  const lrc = await resolveLrc({
    musicId: '1', enabled: true, attempts: 3, delayMs: 0,
    fetchJson: async () => {
      calls += 1;
      if (calls < 2) return null;
      return '{"lrc":{"lyric":"[00:00.00]Hi"}}';
    },
    listRoots: () => [],
  });
  assert.strictEqual(calls, 2);
  assert.strictEqual(lrc, '[00:00.00]Hi');
});

test('empty lyric JSON is not retried', async () => {
  let calls = 0;
  const lrc = await resolveLrc({
    musicId: '1', enabled: true, attempts: 3, delayMs: 0,
    fetchJson: async () => {
      calls += 1;
      return '{"code":200,"lrc":{"lyric":""}}';
    },
    listRoots: () => [],
  });
  assert.strictEqual(calls, 1);
  assert.strictEqual(lrc, null);
});

test('cache miss uses API lyric field', async () => {
  const lrc = await resolveLrc({
    musicId: '1', enabled: true,
    fetchJson: async () => '{"lrc":{"lyric":"[00:00.00]Hi"}}',
    listRoots: () => [],
  });
  assert.strictEqual(lrc, '[00:00.00]Hi');
});
