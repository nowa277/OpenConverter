'use strict';
const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { EventEmitter } = require('node:events');
const {
  LinkError,
  parseLink,
  resolveBilibili,
  fetchBilibiliStream,
  downloadBilibiliAudio,
  resolveYouTube,
  resolveLink,
  sanitizeFilename,
} = require('../src/main/link-resolver');

// Fixtures shaped exactly like the live API responses recorded on
// 2026-11-13 (reports/openconverter_round3_20261113/link_audio_probe_report.md;
// original evidence in /tmp/openconverter_b1). CDN URLs are dummies.
const VIEW_FIXTURE = {
  code: 0,
  data: {
    title: '【经典】周杰伦全MV 【200P】',
    duration: 47766,
    pages: [
      { page: 1, part: '可爱女人【1st JAY】', cid: 2154848, duration: 238 },
      { page: 2, part: '第二支MV', cid: 2154849, duration: 240 },
    ],
  },
};

const PLAYURL_FIXTURE = {
  code: 0,
  data: {
    timelength: 238329,
    dash: {
      audio: [
        { id: 30216, bandwidth: 67211, codecs: 'mp4a.40.2', baseUrl: 'https://cdn.example/audio_30216.m4s', backupUrl: ['https://cdn.example/audio_30216_bak.m4s'] },
        { id: 30232, bandwidth: 129630, codecs: 'mp4a.40.2', baseUrl: 'https://cdn.example/audio_30232.m4s', backupUrl: [] },
      ],
    },
  },
};

const YTDLP_META = { id: 'jNQXAC9IVRw', title: 'Me at the zoo', duration: 19, extractor: 'youtube' };

function jsonFetch(routes) {
  // routes: [{ match(url), status?, body }]
  const calls = [];
  const impl = async (url, init = {}) => {
    calls.push({ url, init });
    const r = routes.find((x) => x.match(url));
    if (!r) throw new Error(`unexpected fetch: ${url}`);
    if (r.fail) throw r.fail;
    return new Response(typeof r.body === 'string' ? r.body : JSON.stringify(r.body), { status: r.status || 200 });
  };
  impl.calls = calls;
  return impl;
}

/** Minimal child-process double: scripted stdout/stderr/exit/error. */
function fakeSpawn(script) {
  const calls = [];
  const impl = (bin, args) => {
    calls.push({ bin, args: [...args] });
    const proc = new EventEmitter();
    proc.stdout = new EventEmitter();
    proc.stderr = new EventEmitter();
    proc.kill = () => {};
    queueMicrotask(() => {
      const step = script[calls.length - 1] || script[script.length - 1] || { exit: 0 };
      if (step.error) { proc.emit('error', step.error); return; }
      if (step.stdout) proc.stdout.emit('data', Buffer.from(step.stdout));
      if (step.stderr) proc.stderr.emit('data', Buffer.from(step.stderr));
      proc.emit('close', step.exit ?? 0);
    });
    return proc;
  };
  impl.calls = calls;
  return impl;
}

// ---------- link parsing ----------

test('parseLink recognizes bilibili / short / youtube / raw BV forms', () => {
  assert.deepEqual(parseLink('https://www.bilibili.com/video/BV1fx411N7bU?p=3'), { platform: 'bilibili', bvid: 'BV1fx411N7bU' });
  assert.deepEqual(parseLink('BV1fx411N7bU'), { platform: 'bilibili', bvid: 'BV1fx411N7bU' });
  assert.deepEqual(parseLink('https://b23.tv/abc123'), { platform: 'bilibili', expand: true, url: 'https://b23.tv/abc123' });
  assert.deepEqual(parseLink('https://www.youtube.com/watch?v=jNQXAC9IVRw'), { platform: 'youtube', videoId: 'jNQXAC9IVRw', url: 'https://www.youtube.com/watch?v=jNQXAC9IVRw' });
  assert.match(parseLink('https://youtu.be/jNQXAC9IVRw?t=3').videoId, /jNQXAC9IVRw/);
  assert.equal(parseLink('https://example.com/foo'), null);
  assert.equal(parseLink(''), null);
});

// ---------- bilibili resolve ----------

test('resolveBilibili maps the recorded view payload to pages', async () => {
  const impl = jsonFetch([{ match: (u) => u.includes('/x/web-interface/view'), body: VIEW_FIXTURE }]);
  const r = await resolveBilibili('BV1fx411N7bU', { fetchImpl: impl });
  assert.equal(r.platform, 'bilibili');
  assert.equal(r.title, '【经典】周杰伦全MV 【200P】');
  assert.equal(r.pages.length, 2);
  assert.deepEqual(r.pages[0], { page: 1, part: '可爱女人【1st JAY】', cid: 2154848, durationSec: 238 });
  assert.ok(impl.calls[0].url.includes('bvid=BV1fx411N7bU'));
  assert.ok(!impl.calls[0].init.headers, 'no User-Agent header (412 risk-control guard)');
});

test('resolveBilibili classifies recorded API error codes', async () => {
  const nf = await assert.rejects(
    () => resolveBilibili('BV1GJ411x7h7', { fetchImpl: jsonFetch([{ match: () => true, body: { code: -404, message: '啥都木有' } }]) }),
    (e) => e instanceof LinkError && e.kind === 'not_found',
  );
  await assert.rejects(
    () => resolveBilibili('BV1GJ411x7h7', { fetchImpl: jsonFetch([{ match: () => true, body: { code: -352 } }]) }),
    (e) => e.kind === 'risk_control',
  );
  await assert.rejects(
    () => resolveBilibili('BV1GJ411x7h7', { fetchImpl: jsonFetch([{ match: () => true, body: { code: -999 } }]) }),
    (e) => e.kind === 'platform' && /code=-999/.test(e.message),
  );
});

test('network and JSON failures classify as network / platform', async () => {
  await assert.rejects(
    () => resolveBilibili('BVx', { fetchImpl: jsonFetch([{ match: () => true, fail: new Error('ECONNREFUSED') }]) }),
    (e) => e.kind === 'network',
  );
  await assert.rejects(
    () => resolveBilibili('BVx', { fetchImpl: jsonFetch([{ match: () => true, body: '<html>412</html>' }]) }),
    (e) => e.kind === 'platform',
  );
  await assert.rejects(
    () => resolveBilibili('BVx', { fetchImpl: jsonFetch([{ match: () => true, status: 412, body: 'precondition' }]) }),
    (e) => e.kind === 'risk_control',
  );
});

// ---------- bilibili stream picking + download ----------

test('fetchBilibiliStream picks the highest-bandwidth audio for the given page', async () => {
  const impl = jsonFetch([{ match: (u) => u.includes('/x/player/playurl'), body: PLAYURL_FIXTURE }]);
  const view = { bvid: 'BV1fx411N7bU', pages: VIEW_FIXTURE.data.pages };
  const s = await fetchBilibiliStream(view, 2, { fetchImpl: impl });
  assert.equal(s.bandwidth, 129630, 'must pick 30232 (132k) over 30216 (64k)');
  assert.ok(impl.calls[0].url.includes('cid=2154849'), 'playurl must use the selected page cid');
  assert.ok(impl.calls[0].url.includes('fnval=16'));
  assert.ok(s.url.endsWith('audio_30232.m4s'));
  assert.equal(s.durationMs, 238329);
});

test('downloadBilibiliAudio sends Referer, streams to disk, reports progress', async () => {
  const bytes = Buffer.from('fake-m4s-bytes-'.repeat(64)); // 960 bytes
  const captured = { init: null };
  const impl = async (url, init = {}) => {
    captured.init = init;
    return new Response(bytes, { headers: { 'content-length': String(bytes.length) } });
  };
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bili-dl-'));
  const dest = path.join(tmpDir, 'out.m4a');
  const percents = [];
  try {
    const r = await downloadBilibiliAudio(
      { url: 'https://cdn.example/a.m4s', backupUrl: [], bandwidth: 129630, durationMs: 238329 },
      dest,
      { fetchImpl: impl, onProgress: (p) => percents.push(p.percent) },
    );
    assert.equal(r.bytes, bytes.length);
    assert.equal(fs.readFileSync(dest).length, bytes.length);
    assert.equal(captured.init.headers.Referer, 'https://www.bilibili.com', 'CDN requires Referer');
    assert.ok(percents.at(-1) > 99, `progress must reach ~100, got ${percents.at(-1)}`);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('downloadBilibiliAudio falls back to a backup URL on failure', async () => {
  const bytes = Buffer.from('backup-ok');
  let calls = 0;
  const impl = async () => {
    calls++;
    if (calls === 1) return new Response('nope', { status: 403 });
    return new Response(bytes, { headers: { 'content-length': String(bytes.length) } });
  };
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bili-dl2-'));
  try {
    const r = await downloadBilibiliAudio(
      { url: 'https://cdn.example/a.m4s', backupUrl: ['https://cdn.example/b.m4s'], bandwidth: 0, durationMs: null },
      path.join(tmpDir, 'out.m4a'),
      { fetchImpl: impl },
    );
    assert.equal(r.bytes, bytes.length);
    assert.equal(calls, 2);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

// ---------- youtube via yt-dlp sidecar ----------

test('resolveYouTube runs yt-dlp -J and maps the metadata', async () => {
  const spawn = fakeSpawn([
    { stdout: '2026.10.26\n', exit: 0 }, // --version
    { stdout: JSON.stringify(YTDLP_META), exit: 0 }, // -J
  ]);
  const r = await resolveYouTube('https://www.youtube.com/watch?v=jNQXAC9IVRw', { spawnImpl: spawn });
  assert.equal(r.platform, 'youtube');
  assert.equal(r.title, 'Me at the zoo');
  assert.equal(r.pages[0].durationSec, 19);
  assert.deepEqual(spawn.calls[1].args.slice(0, 3), ['-J', '--no-playlist', 'https://www.youtube.com/watch?v=jNQXAC9IVRw']);
});

test('missing yt-dlp classifies as component_missing with install guidance', async () => {
  const spawn = fakeSpawn([{ error: new Error('spawn yt-dlp ENOENT') }]);
  await assert.rejects(
    () => resolveYouTube('https://youtu.be/jNQXAC9IVRw', { spawnImpl: spawn }),
    (e) => e.kind === 'component_missing' && /yt-dlp/.test(e.message),
  );
});

test('yt-dlp login / unavailable stderr maps to classified errors', async () => {
  const login = fakeSpawn([
    { stdout: '2026.10.26\n', exit: 0 },
    { stderr: 'ERROR: Sign in to confirm your age. Use --cookies.', exit: 1 },
  ]);
  await assert.rejects(() => resolveYouTube('https://youtu.be/ageGate', { spawnImpl: login }), (e) => e.kind === 'forbidden');

  const gone = fakeSpawn([
    { stdout: '2026.10.26\n', exit: 0 },
    { stderr: 'ERROR: Video unavailable. This video has been removed by the uploader', exit: 1 },
  ]);
  await assert.rejects(() => resolveYouTube('https://youtu.be/gone', { spawnImpl: gone }), (e) => e.kind === 'not_found');
});

// ---------- resolveLink entry ----------

test('resolveLink rejects unsupported links with a clear kind', async () => {
  await assert.rejects(
    () => resolveLink('https://v.qq.com/x/cover/xyz'),
    (e) => e instanceof LinkError && e.kind === 'unsupported' && /仅支持/.test(e.message),
  );
});

// ---------- helpers ----------

test('sanitizeFilename strips path separators and control chars', () => {
  assert.equal(sanitizeFilename('  晴天/周杰伦*test  '), '晴天_周杰伦_test');
  assert.equal(sanitizeFilename('a\x00b:c"d'), 'a_b_c_d');
  assert.equal(sanitizeFilename(''), 'audio');
  assert.equal(sanitizeFilename('x'.repeat(200)).length, 120);
});
