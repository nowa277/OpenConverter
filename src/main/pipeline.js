/**
 * Conversion pipeline — Electron-free so it can be unit tested.
 *
 * One call to `convertOne()` takes a single input file through:
 *
 *   1. plain audio  → copy (same container) or ffmpeg re-encode
 *   2. encrypted    → decoder (in a worker thread) → ffmpeg re-encode / re-mux
 *
 * Progress is reported through `onProgress({ stage, percent })` where stage
 * is one of 'decrypt' | 'encode' | 'done'. Decoders don't stream, so the
 * decrypt stage is indeterminate (percent = null); the renderer shows a
 * shimmer for it instead of a fake number.
 */
const path = require('node:path');
const fs = require('node:fs');
const { Worker } = require('node:worker_threads');
const decoders = require('../decoders');
const ffmpeg = require('./ffmpeg');

// Plain (non-encrypted) audio containers we accept as input.
const PLAIN_AUDIO_EXTS = new Set(['.mp3', '.flac', '.wav', '.m4a', '.aac', '.ogg', '.opus']);

const WORKER_PATH = path.join(__dirname, 'decode-worker.js');

/**
 * Run a decoder in a worker thread; falls back to running it inline if the
 * worker cannot be started (e.g. an exotic packaging setup).
 */
function decodeInWorker(inputPath, outputDir, opts, signal) {
  return new Promise((resolve, reject) => {
    let worker;
    try {
      worker = new Worker(WORKER_PATH, { workerData: { inputPath, outputDir, opts } });
    } catch (e) {
      return decodeInline(inputPath, outputDir, opts).then(resolve, reject);
    }
    let settled = false;
    const finish = (fn, v) => { if (!settled) { settled = true; cleanup(); fn(v); } };
    const onAbort = () => { worker.terminate().catch(() => {}); finish(reject, new Error('aborted')); };
    const cleanup = () => { if (signal) signal.removeEventListener('abort', onAbort); };
    if (signal) {
      if (signal.aborted) return onAbort();
      signal.addEventListener('abort', onAbort, { once: true });
    }
    worker.once('message', (msg) => {
      if (msg.ok) finish(resolve, msg.result);
      else finish(reject, new Error(msg.error));
    });
    worker.once('error', (e) => {
      // Worker failed to boot or crashed: run inline as a last resort.
      decodeInline(inputPath, outputDir, opts).then((r) => finish(resolve, r), (err) => finish(reject, err || e));
    });
    worker.once('exit', (code) => {
      if (!settled && code !== 0) finish(reject, new Error(`decoder worker exited with code ${code}`));
    });
  });
}

async function decodeInline(inputPath, outputDir, opts) {
  const decoder = decoders.pickDecoder(inputPath);
  if (!decoder) throw new Error(`No decoder for file: ${path.basename(inputPath)}`);
  return decoder.decodeFile(inputPath, outputDir, opts);
}

function safeUnlink(p) { if (p) { try { fs.unlinkSync(p); } catch {} } }

/**
 * @param {object} job
 * @param {string} job.inputPath
 * @param {string} job.outputDir
 * @param {string} job.format          target container: mp3|flac|wav|m4a|ogg
 * @param {string} job.quality         e.g. "320k"
 * @param {object} [job.decodeOpts]    passed to the decoder (ekey, cookie, keyPath...)
 * @param {AbortSignal} [job.signal]
 * @param {function} [job.onProgress]  ({ stage, percent }) => void
 * @param {string} [job.ffmpegBin]
 * @param {string} [job.ffprobeBin]
 * @returns {Promise<{ outputPath: string, format: string, durationMs: number, reencoded: boolean }>}
 */
async function convertOne(job) {
  const { inputPath, outputDir, format = 'mp3', quality = '320k', decodeOpts = {}, signal, ffmpegBin, ffprobeBin } = job;
  const onProgress = job.onProgress || (() => {});
  const started = Date.now();
  fs.mkdirSync(outputDir, { recursive: true });

  const ext = path.extname(inputPath).toLowerCase();
  const baseName = path.basename(inputPath, path.extname(inputPath));
  const targetPath = path.join(outputDir, `${baseName}.${format}`);
  const ffmpegOpts = { format, quality, signal, ffmpegBin, ffprobeBin, onProgress: ({ percent }) => onProgress({ stage: 'encode', percent }) };
  const finish = (outputPath, reencoded) => {
    onProgress({ stage: 'done', percent: 100 });
    return { outputPath, format, durationMs: Date.now() - started, reencoded };
  };

  // Path 1: plain audio. Same container → straight copy (never lossy
  // re-encode an mp3 into an mp3); otherwise re-encode.
  if (PLAIN_AUDIO_EXTS.has(ext)) {
    if (ext.slice(1) === format) {
      if (path.resolve(inputPath) !== path.resolve(targetPath)) fs.copyFileSync(inputPath, targetPath);
      return finish(targetPath, false);
    }
    await ffmpeg.run(inputPath, targetPath, ffmpegOpts);
    return finish(targetPath, true);
  }

  // Path 2: encrypted audio.
  if (!decoders.pickDecoder(inputPath)) {
    throw new Error(`No decoder for file: ${path.basename(inputPath)} (unsupported format: ${ext})`);
  }
  onProgress({ stage: 'decrypt', percent: null });
  let decoded;
  try {
    decoded = await decodeInWorker(inputPath, outputDir, decodeOpts, signal);
  } catch (e) {
    if (e.message === 'aborted') throw e;
    throw new Error(`Decryption failed: ${e.message}`);
  }
  const decryptedPath = decoded.outputPath;
  const coverPath = decoded.coverPath || null;
  const tags = decoded.tags || null;
  const decryptedExt = path.extname(decryptedPath).slice(1).toLowerCase();

  try {
    if (decryptedExt === format) {
      // Already the wanted container. If the decoder gave us tags or a cover,
      // re-mux with `-c:a copy` so they get embedded losslessly.
      if (!tags && !coverPath) return finish(decryptedPath, false);
      const tmpOut = decryptedPath.replace(/\.[^.]+$/, '') + `.tagged.${format}`;
      try {
        await ffmpeg.run(decryptedPath, tmpOut, { ...ffmpegOpts, copyAudio: true, coverPath, metadata: tags });
        fs.renameSync(tmpOut, decryptedPath);
      } catch (e) {
        safeUnlink(tmpOut);
        if (e.message === 'aborted') throw e;
        // Tagging is best-effort: keep the untagged decrypted file.
      }
      return finish(decryptedPath, false);
    }

    // ffmpeg refuses to read and write the same path, and stripping the
    // extension could collide with the decrypted intermediate — use a
    // distinct suffix when that happens.
    let ffmpegOut = decryptedPath.replace(/\.[^.]+$/, '') + `.${format}`;
    if (ffmpegOut === decryptedPath) ffmpegOut = decryptedPath.replace(/\.[^.]+$/, '') + `.converted.${format}`;
    await ffmpeg.run(decryptedPath, ffmpegOut, { ...ffmpegOpts, coverPath, metadata: tags });
    safeUnlink(decryptedPath);
    return finish(ffmpegOut, true);
  } catch (e) {
    if (e.message === 'aborted') safeUnlink(decryptedPath);
    throw e;
  } finally {
    safeUnlink(coverPath);
  }
}

/**
 * Run `tasks` (functions returning promises) with at most `limit` in flight.
 * Resolves with the results in input order.
 */
async function parallelLimit(limit, tasks) {
  const executing = new Set();
  const results = [];
  for (const task of tasks) {
    const p = Promise.resolve().then(() => task());
    results.push(p);
    // Swallow rejections here so Promise.race never throws; Promise.all(results) still surfaces them.
    const e = p.then(() => executing.delete(e), () => executing.delete(e));
    executing.add(e);
    if (executing.size >= limit) await Promise.race(executing);
  }
  return Promise.all(results);
}

module.exports = { convertOne, parallelLimit, PLAIN_AUDIO_EXTS };
