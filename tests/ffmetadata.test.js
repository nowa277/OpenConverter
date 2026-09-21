'use strict';
const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { escapeFfmeta, writeFfmetadata } = require('../src/main/ffmetadata');
const ffmpeg = require('../src/main/ffmpeg');

test('escapeFfmeta escapes specials and newlines', () => {
  assert.strictEqual(escapeFfmeta('a=b\nc'), 'a\\=b\\\nc');
});

test('escapeFfmeta escapes backslash semicolon and hash', () => {
  assert.strictEqual(escapeFfmeta('a\\b;c#d'), 'a\\\\b\\;c\\#d');
});

test('writeFfmetadata writes FFMETADATA1 with lyrics', () => {
  const p = path.join(os.tmpdir(), `oc-ffm-${Date.now()}.ffm`);
  writeFfmetadata(p, { title: 'T', lyrics: '[00:00.00]Hi\n[00:01.00]Yo' });
  const text = fs.readFileSync(p, 'utf8');
  fs.unlinkSync(p);
  assert.ok(text.startsWith(';FFMETADATA1\n'));
  assert.ok(text.includes('title=T'));
  assert.ok(text.includes('lyrics=[00:00.00]Hi\\\n[00:01.00]Yo'));
});

test('writeFfmetadata skips empty keys', () => {
  const p = path.join(os.tmpdir(), `oc-ffm-empty-${Date.now()}.ffm`);
  writeFfmetadata(p, { title: 'T', artist: '', album: undefined, lyrics: 'Hi' });
  const text = fs.readFileSync(p, 'utf8');
  fs.unlinkSync(p);
  assert.ok(text.includes('title=T'));
  assert.ok(text.includes('lyrics=Hi'));
  assert.ok(!/\nartist=/.test(text));
  assert.ok(!/\nalbum=/.test(text));
});

test('buildArgs maps metadataFile after cover', () => {
  const args = ffmpeg.buildArgs({
    inputPath: 'in.mp3', outputPath: 'out.mp3', format: 'mp3',
    metadataFile: '/tmp/meta.ffm',
  });
  const iFlags = args.map((a, i) => (a === '-i' ? i : -1)).filter((i) => i >= 0);
  assert.ok(args.includes('/tmp/meta.ffm'));
  assert.ok(args.includes('-map_metadata'));
  assert.strictEqual(args[args.indexOf('-map_metadata') + 1], '1');
  assert.strictEqual(args[iFlags[1] + 1], '/tmp/meta.ffm');
});

test('buildArgs maps metadataFile after cover at index 2', () => {
  const cover = path.join(os.tmpdir(), `oc-cover-${Date.now()}.png`);
  fs.writeFileSync(cover, Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
    'base64',
  ));
  const args = ffmpeg.buildArgs({
    inputPath: 'in.mp3', outputPath: 'out.mp3', format: 'mp3',
    coverPath: cover,
    metadataFile: '/tmp/meta.ffm',
  });
  fs.unlinkSync(cover);
  const iFlags = args.map((a, i) => (a === '-i' ? i : -1)).filter((i) => i >= 0);
  assert.strictEqual(iFlags.length, 3);
  assert.strictEqual(args[iFlags[1] + 1], cover);
  assert.strictEqual(args[iFlags[2] + 1], '/tmp/meta.ffm');
  assert.strictEqual(args[args.indexOf('-map_metadata') + 1], '2');
});

test('buildArgs with metadataFile does not emit -metadata lyrics=', () => {
  const args = ffmpeg.buildArgs({
    inputPath: 'in.mp3', outputPath: 'out.mp3', format: 'mp3',
    metadataFile: '/tmp/meta.ffm',
    metadata: { title: 'T', lyrics: '[00:00.00]Hi' },
  });
  assert.ok(!args.includes('-metadata'));
  assert.ok(!args.some((a) => String(a).startsWith('lyrics=')));
});

test('run forwards metadataFile into ffmpeg argv', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-ffm-run-'));
  const argLog = path.join(dir, 'ffmpeg-args.txt');
  const ffmpegBin = path.join(dir, 'ffmpeg');
  const ffprobeBin = path.join(dir, 'ffprobe');
  fs.writeFileSync(ffmpegBin, `#!/bin/sh\nprintf '%s\\0' "$@" > '${argLog}'\nexit 0\n`);
  fs.writeFileSync(ffprobeBin, '#!/bin/sh\necho 1\nexit 0\n');
  fs.chmodSync(ffmpegBin, 0o755);
  fs.chmodSync(ffprobeBin, 0o755);
  const metadataFile = path.join(dir, 'meta.ffm');
  fs.writeFileSync(metadataFile, ';FFMETADATA1\ntitle=T\n');
  const out = path.join(dir, 'out', 'out.mp3');
  await ffmpeg.run(path.join(dir, 'in.mp3'), out, { ffmpegBin, ffprobeBin, metadataFile });
  const dumped = fs.readFileSync(argLog, 'utf8').split('\0');
  assert.ok(dumped.includes(metadataFile));
  assert.strictEqual(dumped[dumped.indexOf('-map_metadata') + 1], '1');
});
