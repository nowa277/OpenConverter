'use strict';
const { test } = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const { getNeteaseLyricJson } = require('../src/main/netease-lyric-http');

test('blank id does not fetch', async () => {
  let calls = 0;
  const fetchImpl = async () => { calls += 1; return { status: 200, body: '{}' }; };
  assert.strictEqual(await getNeteaseLyricJson('', { fetchImpl }), null);
  assert.strictEqual(await getNeteaseLyricJson(null, { fetchImpl }), null);
  assert.strictEqual(calls, 0);
});

test('2xx returns body; 404 and throw return null', async () => {
  assert.strictEqual(
    await getNeteaseLyricJson('1', { fetchImpl: async () => ({ status: 200, body: '{"lrc":{"lyric":"x"}}' }) }),
    '{"lrc":{"lyric":"x"}}',
  );
  assert.strictEqual(
    await getNeteaseLyricJson('1', { fetchImpl: async () => ({ status: 404, body: 'no' }) }),
    null,
  );
  assert.strictEqual(
    await getNeteaseLyricJson('1', { fetchImpl: async () => { throw new Error('econnreset'); } }),
    null,
  );
});

test('real timeout aborts', async () => {
  const server = http.createServer((req, res) => { /* never respond */ });
  await new Promise((r) => server.listen(0, r));
  const { port } = server.address();
  const fetchImpl = require('../src/main/netease-lyric-http').makeHttpFetch(`http://127.0.0.1:${port}/lyric`);
  const t0 = Date.now();
  try {
    const body = await getNeteaseLyricJson('1', { fetchImpl, timeoutMs: 200 });
    assert.strictEqual(body, null);
    assert.ok(Date.now() - t0 < 2000);
  } finally {
    server.close();
  }
});
