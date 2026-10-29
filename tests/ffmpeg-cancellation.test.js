'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const ffmpeg = require('../src/main/ffmpeg');

function tools(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-process-cancel-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const probe = path.join(root, 'probe');
  fs.writeFileSync(probe, '#!/bin/sh\nprintf "1\\n"\n', { mode: 0o755 });
  return { root, probe };
}

test('pre-cancelled conversion never starts ffprobe or ffmpeg', async (t) => {
  const { root } = tools(t);
  const marker = path.join(root, 'started');
  const binary = path.join(root, 'process');
  fs.writeFileSync(binary, `#!/bin/sh\nprintf started > '${marker}'\nprintf '1\\n'\n`, { mode: 0o755 });
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(ffmpeg.run('in.wav', path.join(root, 'out.mp3'), {
    signal: controller.signal, ffprobeBin: binary, ffmpegBin: binary,
  }), /aborted/);
  assert.equal(fs.existsSync(marker), false);
});

test('cancel during probing kills the probe and never starts ffmpeg', { timeout: 10000 }, async (t) => {
  const { root } = tools(t);
  const started = path.join(root, 'probe-started');
  const encodeStarted = path.join(root, 'encode-started');
  const probe = path.join(root, 'probe');
  const encode = path.join(root, 'encode');
  fs.writeFileSync(probe, `#!/usr/bin/env node\nrequire('fs').writeFileSync(${JSON.stringify(started)},String(process.pid));setInterval(()=>{},1000);\n`, { mode: 0o755 });
  fs.writeFileSync(encode, `#!/bin/sh\nprintf started > '${encodeStarted}'\n`, { mode: 0o755 });
  const controller = new AbortController();
  const conversion = ffmpeg.run('in.wav', path.join(root, 'out.mp3'), { signal: controller.signal, ffprobeBin: probe, ffmpegBin: encode });
  await new Promise((resolve) => {
    fs.watchFile(started, { interval: 10 }, () => {
      if (fs.existsSync(started)) { fs.unwatchFile(started); resolve(); }
    });
    if (fs.existsSync(started)) { fs.unwatchFile(started); resolve(); }
  });
  const pid = Number(fs.readFileSync(started, 'utf8'));
  t.after(() => { try { process.kill(pid); } catch (e) { if (e.code !== 'ESRCH') throw e; } });
  controller.abort();
  await assert.rejects(conversion, /aborted/);
  assert.equal(fs.existsSync(encodeStarted), false);
  assert.throws(() => process.kill(pid, 0), { code: 'ESRCH' });
});

test('pre-cancel plus missing encoder leaves the process alive', async (t) => {
  const { root, probe } = tools(t);
  const script = `const f=require(${JSON.stringify(require.resolve('../src/main/ffmpeg'))});const c=new AbortController();c.abort();f.run('in.wav',${JSON.stringify(path.join(root, 'out.mp3'))},{signal:c.signal,ffprobeBin:${JSON.stringify(probe)},ffmpegBin:'/missing/ffmpeg'}).then(()=>process.exitCode=2,e=>{if(e.message!=='aborted')process.exitCode=3});`;
  const child = spawn(process.execPath, ['-e', script], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stderr = '';
  child.stderr.on('data', (data) => { stderr += data; });
  const code = await new Promise((resolve) => child.on('close', resolve));
  assert.equal(code, 0, stderr);
});
