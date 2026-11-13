'use strict';
const { test } = require('node:test');
const assert = require('node:assert');
const {
  searchKugouCandidatesByKeyword,
  fetchKugouLyricByTags,
} = require('../src/main/kugou-lyric-http');

const SEARCH_BODY = (candidates) => JSON.stringify({ status: 200, candidates });
const DOWNLOAD_BODY = (text) => JSON.stringify({ status: 200, content: Buffer.from(text, 'utf8').toString('base64') });

function fakeFetch(routes) {
  // routes: [{ match(url) → bool, respond(url) → { status, body } }]
  const calls = [];
  const impl = async (url) => {
    calls.push(url);
    const route = routes.find((r) => r.match(url));
    if (!route) throw new Error(`unexpected fetch: ${url}`);
    return route.respond(url);
  };
  impl.calls = calls;
  return impl;
}

test('fetchKugouLyricByTags searches by keyword and returns LRC text', async () => {
  const impl = fakeFetch([
    { match: (u) => u.includes('/search'), respond: () => ({ status: 200, body: SEARCH_BODY([{ id: '699230137', accesskey: 'AK1', duration: 116000, song: '晴天', singer: '周杰伦' }]) }) },
    { match: (u) => u.includes('/download'), respond: () => ({ status: 200, body: DOWNLOAD_BODY('[ti:晴天]\n[00:01.00]歌词') }) },
  ]);
  const lrc = await fetchKugouLyricByTags({ title: '晴天', artist: '周杰伦', durationSec: 269.5 }, { fetchImpl: impl });
  assert.ok(lrc && lrc.includes('[ti:晴天]'));

  const searchUrl = impl.calls[0];
  assert.ok(searchUrl.includes('keyword=' + encodeURIComponent('周杰伦 晴天')), searchUrl);
  assert.ok(searchUrl.includes('ver=1&man=yes&client=pc'), searchUrl);
  assert.ok(impl.calls[1].includes('id=699230137'), impl.calls[1]);
});

test('duration-aware pick chooses the candidate closest to audio duration', async () => {
  const impl = fakeFetch([
    { match: (u) => u.includes('/search'), respond: () => ({ status: 200, body: SEARCH_BODY([
      { id: 'REMIX', accesskey: 'A', duration: 116000, song: '晴天', singer: '周杰伦' },
      { id: 'ORIGINAL', accesskey: 'B', duration: 269300, song: '晴天', singer: '周杰伦' },
    ]) }) },
    { match: (u) => u.includes('/download'), respond: () => ({ status: 200, body: DOWNLOAD_BODY('[ti:晴天]') }) },
  ]);
  const lrc = await fetchKugouLyricByTags({ title: '晴天', artist: '周杰伦', durationSec: 269.5 }, { fetchImpl: impl });
  assert.ok(lrc);
  assert.ok(impl.calls[1].includes('id=ORIGINAL'), 'download must target the nearest-duration candidate');
});

test('falls back to the first candidate when duration is unknown', async () => {
  const impl = fakeFetch([
    { match: (u) => u.includes('/search'), respond: () => ({ status: 200, body: SEARCH_BODY([{ id: 'FIRST', accesskey: 'A', duration: 116000 }]) }) },
    { match: (u) => u.includes('/download'), respond: () => ({ status: 200, body: DOWNLOAD_BODY('[ti:x]') }) },
  ]);
  const lrc = await fetchKugouLyricByTags({ title: '晴天' }, { fetchImpl: impl });
  assert.ok(lrc);
  assert.ok(impl.calls[1].includes('id=FIRST'));
});

test('returns null when keyword is empty', async () => {
  const impl = fakeFetch([]);
  assert.strictEqual(await fetchKugouLyricByTags({ title: '' }, { fetchImpl: impl }), null);
  assert.strictEqual(await fetchKugouLyricByTags({}, { fetchImpl: impl }), null);
  assert.strictEqual(impl.calls.length, 0, 'no network call for empty keyword');
});

test('returns null when search returns no candidates', async () => {
  const impl = fakeFetch([
    { match: () => true, respond: () => ({ status: 200, body: SEARCH_BODY([]) }) },
  ]);
  assert.strictEqual(await fetchKugouLyricByTags({ title: 'Impossible', artist: 'James Arthur' }, { fetchImpl: impl }), null);
});

test('returns null when search HTTP errors', async () => {
  const impl = fakeFetch([
    { match: () => true, respond: () => ({ status: 500, body: 'oops' }) },
  ]);
  assert.strictEqual(await fetchKugouLyricByTags({ title: '晴天' }, { fetchImpl: impl }), null);
});

test('returns null when download HTTP errors', async () => {
  const impl = fakeFetch([
    { match: (u) => u.includes('/search'), respond: () => ({ status: 200, body: SEARCH_BODY([{ id: '1', accesskey: 'K', duration: 200000 }]) }) },
    { match: (u) => u.includes('/download'), respond: () => ({ status: 403, body: '' }) },
  ]);
  assert.strictEqual(await fetchKugouLyricByTags({ title: '晴天' }, { fetchImpl: impl }), null);
});

test('returns null when krc1 payload fails to decode', async () => {
  // krc1 magic with a corrupt tail — decodeKrcToLrc must not crash the caller.
  const badKrc = Buffer.concat([Buffer.from('krc1'), Buffer.alloc(32, 0x00)]);
  const impl = fakeFetch([
    { match: (u) => u.includes('/search'), respond: () => ({ status: 200, body: SEARCH_BODY([{ id: '1', accesskey: 'K', duration: 200000 }]) }) },
    { match: (u) => u.includes('/download'), respond: () => ({ status: 200, body: JSON.stringify({ status: 200, content: badKrc.toString('base64') }) }) },
  ]);
  assert.strictEqual(await fetchKugouLyricByTags({ title: '晴天' }, { fetchImpl: impl }), null);
});

test('searchKugouCandidatesByKeyword propagates aborted signal', async () => {
  const controller = new AbortController();
  controller.abort();
  const impl = async () => { throw new Error('boom'); };
  await assert.rejects(
    () => searchKugouCandidatesByKeyword('晴天', { fetchImpl: impl, signal: controller.signal }),
    /aborted/,
  );
});
