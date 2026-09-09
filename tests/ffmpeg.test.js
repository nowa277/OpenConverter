'use strict';

/**
 * ffmpeg wrapper tests: argument building (pure) and, when ffmpeg is
 * available, a real conversion with progress events, tag + cover embedding.
 */
const { test } = require('node:test');
const assert = require('node:assert');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const ffmpeg = require('../src/main/ffmpeg');

const OUT_DIR = path.join(__dirname, 'output', 'ffmpeg');
fs.rmSync(OUT_DIR, { recursive: true, force: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

function haveFfmpeg() {
  try { execFileSync('ffmpeg', ['-version'], { stdio: 'pipe' }); return true; } catch { return false; }
}
const skipNoFfmpeg = !haveFfmpeg() && 'ffmpeg not available';

test('buildArgs: mp3 target keeps attached picture and writes id3v2.3', () => {
  const args = ffmpeg.buildArgs({ inputPath: 'in.flac', outputPath: 'out.mp3', format: 'mp3', quality: '256k' });
  assert.deepStrictEqual(args.slice(0, 3), ['-y', '-i', 'in.flac']);
  assert.ok(args.includes('-map') && args.includes('0:v?'));
  assert.ok(args.includes('libmp3lame'));
  assert.strictEqual(args[args.indexOf('-b:a') + 1], '256k');
  assert.ok(args.includes('-id3v2_version'));
  assert.strictEqual(args[args.length - 1], 'out.mp3');
});

test('buildArgs: ogg/wav targets drop video streams', () => {
  for (const format of ['ogg', 'wav']) {
    const args = ffmpeg.buildArgs({ inputPath: 'in.mp3', outputPath: `out.${format}`, format });
    assert.ok(args.includes('-vn'), `${format} should use -vn`);
    assert.ok(!args.includes('0:v?'));
  }
});

test('buildArgs: external cover is mapped as attached_pic; metadata is appended', () => {
  const cover = path.join(OUT_DIR, 'cover.png');
  fs.writeFileSync(cover, Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64'));
  const args = ffmpeg.buildArgs({
    inputPath: 'in.mp3', outputPath: 'out.flac', format: 'flac', coverPath: cover,
    metadata: { title: 'T', artist: 'A', album: '' },
  });
  assert.strictEqual(args.filter((a) => a === '-i').length, 2);
  assert.ok(args.includes('1:v:0'));
  assert.ok(args.includes('attached_pic'));
  assert.ok(args.includes('title=T') && args.includes('artist=A'));
  assert.ok(!args.some((a) => a.startsWith('album=')), 'empty metadata values are skipped');
});

test('buildArgs: opus bitrate is clamped to the libopus maximum (256k)', () => {
  const args = ffmpeg.buildArgs({ inputPath: 'in.mp3', outputPath: 'out.ogg', format: 'ogg', quality: '320k' });
  assert.strictEqual(args[args.indexOf('-b:a') + 1], '256k');
  const low = ffmpeg.buildArgs({ inputPath: 'in.mp3', outputPath: 'out.ogg', format: 'ogg', quality: '128k' });
  assert.strictEqual(low[low.indexOf('-b:a') + 1], '128k');
  assert.strictEqual(ffmpeg.clampBitrate('weird', 256), 'weird');
});

test('buildArgs: copyAudio uses -c:a copy', () => {
  const args = ffmpeg.buildArgs({ inputPath: 'in.mp3', outputPath: 'out.mp3', format: 'mp3', copyAudio: true });
  assert.strictEqual(args[args.indexOf('-c:a') + 1], 'copy');
  assert.ok(!args.includes('libmp3lame'));
});

test('buildArgs: rejects unknown format', () => {
  assert.throws(() => ffmpeg.buildArgs({ inputPath: 'a', outputPath: 'b', format: 'xyz' }), /Unsupported output format/);
});

test('checkFfmpeg reports version or a clear error', async () => {
  const r = await ffmpeg.checkFfmpeg();
  if (haveFfmpeg()) { assert.strictEqual(r.ok, true); assert.ok(r.version); }
  else { assert.strictEqual(r.ok, false); assert.ok(r.error); }
  const missing = await ffmpeg.checkFfmpeg({ ffmpegBin: '/definitely/not/here/ffmpeg' });
  assert.strictEqual(missing.ok, false);
});

test('run: converts, reports monotonic progress ending at 100, embeds tags + cover', { skip: skipNoFfmpeg }, async () => {
  const src = path.join(OUT_DIR, 'sine.mp3');
  execFileSync('ffmpeg', ['-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=3', '-ac', '1', '-ar', '22050', '-c:a', 'libmp3lame', '-b:a', '64k', src], { stdio: 'pipe' });
  const cover = path.join(OUT_DIR, 'cover.png');
  const out = path.join(OUT_DIR, 'sine-out.flac');

  const seen = [];
  await ffmpeg.run(src, out, {
    format: 'flac', coverPath: cover, metadata: { title: 'Sine Title', artist: 'Sine Artist' },
    onProgress: ({ percent }) => seen.push(percent),
  });
  assert.ok(fs.existsSync(out));
  assert.ok(seen.length >= 1, 'progress should be reported');
  for (let i = 1; i < seen.length; i++) assert.ok(seen[i] >= seen[i - 1], 'progress must be monotonic');
  assert.strictEqual(seen[seen.length - 1], 100);

  const probe = execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format_tags=title,artist:stream=codec_type',
    '-of', 'default=noprint_wrappers=1', out], { stdio: 'pipe' }).toString();
  assert.match(probe, /TAG:title=Sine Title/i);
  assert.match(probe, /TAG:artist=Sine Artist/i);
  assert.match(probe, /codec_type=video/, 'cover art should be present as an attached picture');
});

test('run: abort signal kills ffmpeg and rejects with "aborted"', { skip: skipNoFfmpeg }, async () => {
  const src = path.join(OUT_DIR, 'long.wav');
  execFileSync('ffmpeg', ['-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=30', '-ac', '1', '-ar', '8000', src], { stdio: 'pipe' });
  const controller = new AbortController();
  const p = ffmpeg.run(src, path.join(OUT_DIR, 'long.mp3'), { format: 'mp3', signal: controller.signal });
  setTimeout(() => controller.abort(), 50);
  await assert.rejects(p, /aborted/);
});

test('run: missing ffmpeg binary gives an actionable error', async () => {
  await assert.rejects(
    ffmpeg.run('nope.mp3', path.join(OUT_DIR, 'x.mp3'), { ffmpegBin: '/definitely/not/here/ffmpeg', ffprobeBin: '/definitely/not/here/ffprobe' }),
    /ffmpeg not found/,
  );
});
