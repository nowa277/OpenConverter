'use strict';

const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const Module = require('node:module');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'openconverter-queue-test-'));
after(() => fs.rmSync(temp, { recursive: true, force: true }));

function barrier() {
  let resolve;
  const promise = new Promise((r) => { resolve = r; });
  return { promise, resolve };
}

function loadMain(convertOne, onProgress = () => {}) {
  let ipc;
  const electron = {
    app: { getPath: () => temp, whenReady: () => Promise.resolve(), on() {}, getVersion: () => 'test' },
    BrowserWindow: class {
      constructor() { this.webContents = { send: (_channel, event) => onProgress(event) }; }
      loadFile() {}
      once() {}
      on() {}
      isDestroyed() { return false; }
    },
    ipcMain: { handle: (_channel, handler) => { ipc = handler; } },
    dialog: {}, shell: {}, nativeTheme: { on() {}, shouldUseDarkColors: false }, Menu: { setApplicationMenu() {} },
  };
  const config = { get: () => ({ ncmLyricsEnabled: false }), set() {} };
  const pipeline = { ...require('../src/main/pipeline'), convertOne };
  const originalLoad = Module._load;
  Module._load = function (request, parent, isMain) {
    if (request === 'electron') return electron;
    if (parent?.filename.endsWith('/src/main/index.js') && request === './config') return config;
    if (parent?.filename.endsWith('/src/main/index.js') && request === './pipeline') return pipeline;
    return originalLoad.call(this, request, parent, isMain);
  };
  const mainPath = require.resolve('../src/main/index');
  try {
    delete require.cache[mainPath];
    require(mainPath);
  } finally {
    Module._load = originalLoad;
  }
  return async (method, data) => {
    await Promise.resolve();
    return ipc(null, { method, data });
  };
}

test('cancelAll prevents queued jobs from starting and a later batch converts', async () => {
  const started = [];
  const enoughStarted = barrier();
  let hold = true;
  const limit = Math.min(4, Math.max(2, os.cpus().length));
  const invoke = loadMain(async ({ inputPath, outputDir, format, signal }) => {
    started.push(inputPath);
    if (hold && started.length <= limit) {
      if (started.length === limit) enoughStarted.resolve();
      await new Promise((resolve, reject) => {
        if (signal.aborted) return reject(new Error('aborted'));
        signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true });
      });
    }
    return { outputPath: path.join(outputDir, `${inputPath}.${format}`), format, durationMs: 1 };
  });

  const files = Array.from({ length: limit + 2 }, (_, i) => `input-${i}.mp3`);
  const batch = invoke('convert:start', { files, outputDir: temp, format: 'flac' });
  await enoughStarted.promise;
  await invoke('convert:cancelAll');
  const result = await batch;

  assert.deepEqual(started, files.slice(0, limit));
  assert.equal(result.results.length, files.length);
  assert.ok(result.results.every((r) => r.cancelled));

  hold = false;
  const next = await invoke('convert:start', { files: ['next.mp3'], outputDir: temp, format: 'flac' });
  assert.equal(next.results[0].outputPath, path.join(temp, 'next.mp3.flac'));
  assert.equal(next.results[0].error, undefined);
});

test('cancelling one active item leaves the rest of the batch running', async () => {
  const began = barrier();
  const releaseSecond = barrier();
  const events = [];
  const invoke = loadMain(async ({ inputPath, outputDir, signal }) => {
    if (inputPath === 'first.mp3') {
      began.resolve();
      await new Promise((resolve, reject) => {
        if (signal.aborted) return reject(new Error('aborted'));
        signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true });
      });
    } else {
      await releaseSecond.promise;
    }
    return { outputPath: path.join(outputDir, 'second.flac'), format: 'flac', durationMs: 1 };
  }, (event) => events.push(event));

  const batch = invoke('convert:start', { files: ['first.mp3', 'second.mp3'], outputDir: temp, format: 'flac' });
  await began.promise;
  const jobId = events.find((event) => event.filePath === 'first.mp3').jobId;
  assert.equal((await invoke('convert:cancel', { jobId })).cancelled, true);
  releaseSecond.resolve();
  const result = await batch;
  assert.equal(result.results[0].cancelled, true);
  assert.equal(result.results[1].outputPath, path.join(temp, 'second.flac'));
  assert.equal(result.results[1].error, undefined);
});
