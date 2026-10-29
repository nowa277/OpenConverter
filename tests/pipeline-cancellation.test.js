'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');
const pipeline = require('../src/main/pipeline');

function workspace(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-pipeline-cancel-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  return dir;
}

test('pre-aborted plain same-format copy produces no output', async (t) => {
  const root = workspace(t);
  const inputPath = path.join(root, 'input.mp3');
  const outputDir = path.join(root, 'out');
  fs.writeFileSync(inputPath, 'ID3 synthetic');
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(pipeline.convertOne({ inputPath, outputDir, format: 'mp3', signal: controller.signal }), /aborted/);
  assert.equal(fs.existsSync(path.join(outputDir, 'input.mp3')), false);
});

test('failed encode preserves existing destination and removes staged partials', async (t) => {
  const root = workspace(t);
  const inputPath = path.join(root, 'input.wav');
  const outputDir = path.join(root, 'out');
  fs.mkdirSync(outputDir);
  fs.writeFileSync(inputPath, 'RIFF synthetic');
  const previous = path.join(outputDir, 'input.mp3');
  fs.writeFileSync(previous, 'previous output');
  const probe = path.join(root, 'probe');
  const encode = path.join(root, 'encode');
  fs.writeFileSync(probe, '#!/bin/sh\nprintf "1\\n"\n', { mode: 0o755 });
  fs.writeFileSync(encode, '#!/usr/bin/env node\nconst fs=require("fs");fs.writeFileSync(process.argv.at(-1),"partial");process.exit(1);\n', { mode: 0o755 });
  await assert.rejects(pipeline.convertOne({ inputPath, outputDir, format: 'mp3', ffprobeBin: probe, ffmpegBin: encode }), /ffmpeg exited/);
  assert.equal(fs.readFileSync(previous, 'utf8'), 'previous output');
  assert.deepEqual(fs.readdirSync(outputDir), ['input.mp3']);
});

test('cancel a real encoder, remove partials, then convert another file', { timeout: 15000 }, async (t) => {
  const root = workspace(t);
  const inputPath = path.join(root, 'tone.wav');
  execFileSync('ffmpeg', ['-v', 'error', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=8', '-ar', '8000', inputPath]);
  const realFfmpeg = execFileSync('which', ['ffmpeg'], { encoding: 'utf8' }).trim();
  const slowEncoder = path.join(root, 'slow-encoder');
  fs.writeFileSync(slowEncoder, `#!/bin/sh\nexec '${realFfmpeg}' -re "$@"\n`, { mode: 0o755 });
  const controller = new AbortController();
  const outputDir = path.join(root, 'out');
  await assert.rejects(pipeline.convertOne({
    inputPath, outputDir, format: 'mp3', signal: controller.signal, ffmpegBin: slowEncoder,
    onProgress: ({ stage }) => { if (stage === 'encode') controller.abort(); },
  }), /aborted/);
  assert.deepEqual(fs.readdirSync(outputDir), []);
  const next = await pipeline.convertOne({ inputPath, outputDir, format: 'mp3' });
  const duration = Number(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', next.outputPath], { encoding: 'utf8' }));
  assert.ok(duration >= 8 && duration < 8.4);
  assert.deepEqual(fs.readdirSync(outputDir), ['tone.mp3']);
});

test('compound KGG output uses the decrypted stem and preserves a same-folder input', async (t) => {
  const root = workspace(t);
  const inputPath = path.join(root, 'song.kgg.flac');
  const original = Buffer.from('synthetic encrypted source');
  fs.writeFileSync(inputPath, original);
  const Module = require('node:module');
  const { EventEmitter } = require('node:events');
  const originalLoad = Module._load;
  const modulePath = require.resolve('../src/main/pipeline');
  const savedModule = require.cache[modulePath];
  Module._load = function (name, parent, isMain) {
    if (name === 'node:worker_threads' && parent?.filename === modulePath) {
      return { Worker: class extends EventEmitter {
        constructor(_script, { workerData }) {
          super();
          queueMicrotask(() => {
            const outputPath = path.join(workerData.outputDir, 'song.kgg.flac');
            fs.writeFileSync(outputPath, 'fLaC synthetic output');
            this.emit('message', { ok: true, result: { outputPath, format: 'flac' } });
          });
        }
      } };
    }
    return originalLoad.call(this, name, parent, isMain);
  };
  let isolatedPipeline;
  try {
    delete require.cache[modulePath];
    isolatedPipeline = require(modulePath);
  } finally {
    Module._load = originalLoad;
    require.cache[modulePath] = savedModule;
  }
  const result = await isolatedPipeline.convertOne({ inputPath, outputDir: root, format: 'flac' });
  assert.deepEqual(fs.readFileSync(inputPath), original);
  assert.equal(result.outputPath, path.join(root, 'song.flac'));
  assert.equal(fs.readFileSync(result.outputPath, 'utf8'), 'fLaC synthetic output');
});

test('compound encrypted extension reaches the decoder before plain FLAC routing', async (t) => {
  const root = workspace(t);
  const inputPath = path.join(root, 'invalid.kgg.flac');
  fs.writeFileSync(inputPath, 'not a valid KGG file');
  await assert.rejects(pipeline.convertOne({ inputPath, outputDir: path.join(root, 'out'), format: 'flac' }), /Decryption failed/);
});
