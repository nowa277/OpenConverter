'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadProgress() {
  const source = fs.readFileSync(path.join(__dirname, '../src/renderer/main.js'), 'utf8');
  const begin = source.indexOf("  api.on('convert:progress'");
  const end = source.indexOf('\n\n', begin);
  let listener;
  const state = { files: [{ path: 'a.wav', jobId: 'new', status: 'encode', progress: 1 }] };
  vm.runInNewContext(source.slice(begin, end), {
    api: { on: (_event, fn) => { listener = fn; } }, state,
    renderQueueItem() {}, renderQueueSummary() {},
  });
  return { state, listener };
}

test('desktop progress displays an item terminal result before batch completion', () => {
  const { state, listener } = loadProgress();
  listener({ filePath: 'a.wav', jobId: 'new', stage: 'done', percent: 100, outputPath: 'a.mp3', durationMs: 50 });
  assert.equal(state.files[0].status, 'done');
  assert.equal(state.files[0].outputPath, 'a.mp3');
});

test('desktop progress from an older job cannot overwrite a retried file', () => {
  const { state, listener } = loadProgress();
  listener({ filePath: 'a.wav', jobId: 'old', stage: 'encode', percent: 90 });
  assert.equal(state.files[0].progress, 1);
});
