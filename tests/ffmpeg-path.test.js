/**
 * Tests for the pure resolveFfmpegPath / resolveFfprobePath helpers.
 * Pure functions — do not touch Electron `app` directly, so they are
 * testable in plain Node.
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { resolveFfmpegPath, resolveFfprobePath } = require('../src/main/ffmpeg-path');

// --- ffmpeg path ---

test('resolveFfmpegPath: bundled path on packaged Windows', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'win32',
    resourcesPath: 'C:\\Users\\me\\AppData\\OpenConverter\\resources',
  });
  assert.equal(result, path.join('C:\\Users\\me\\AppData\\OpenConverter\\resources', 'ffmpeg.exe'));
});

test('resolveFfmpegPath: "ffmpeg" on packaged Linux (let PATH handle it)', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'linux',
    resourcesPath: '/usr/share/openconverter/resources',
  });
  assert.equal(result, 'ffmpeg');
});

test('resolveFfmpegPath: "ffmpeg" on packaged macOS (let PATH handle it)', () => {
  const result = resolveFfmpegPath({
    isPackaged: true,
    platform: 'darwin',
    resourcesPath: '/Applications/OpenConverter.app/Contents/Resources',
  });
  assert.equal(result, 'ffmpeg');
});

test('resolveFfmpegPath: "ffmpeg" in dev mode (unpackaged) regardless of platform', () => {
  const win = resolveFfmpegPath({
    isPackaged: false,
    platform: 'win32',
    resourcesPath: 'C:\\temp\\dev\\resources',
  });
  const linux = resolveFfmpegPath({
    isPackaged: false,
    platform: 'linux',
    resourcesPath: '/tmp/dev/resources',
  });
  assert.equal(win, 'ffmpeg');
  assert.equal(linux, 'ffmpeg');
});

// --- ffprobe path (mirrored) ---

test('resolveFfprobePath: bundled path on packaged Windows', () => {
  const result = resolveFfprobePath({
    isPackaged: true,
    platform: 'win32',
    resourcesPath: 'C:\\Users\\me\\AppData\\OpenConverter\\resources',
  });
  assert.equal(result, path.join('C:\\Users\\me\\AppData\\OpenConverter\\resources', 'ffprobe.exe'));
});

test('resolveFfprobePath: "ffprobe" on packaged Linux', () => {
  const result = resolveFfprobePath({
    isPackaged: true,
    platform: 'linux',
    resourcesPath: '/usr/share/openconverter/resources',
  });
  assert.equal(result, 'ffprobe');
});

test('resolveFfprobePath: "ffprobe" on packaged macOS', () => {
  const result = resolveFfprobePath({
    isPackaged: true,
    platform: 'darwin',
    resourcesPath: '/Applications/OpenConverter.app/Contents/Resources',
  });
  assert.equal(result, 'ffprobe');
});

test('resolveFfprobePath: "ffprobe" in dev mode regardless of platform', () => {
  const win = resolveFfprobePath({
    isPackaged: false,
    platform: 'win32',
    resourcesPath: 'C:\\temp\\dev\\resources',
  });
  const linux = resolveFfprobePath({
    isPackaged: false,
    platform: 'linux',
    resourcesPath: '/tmp/dev/resources',
  });
  assert.equal(win, 'ffprobe');
  assert.equal(linux, 'ffprobe');
});
