/**
 * ffmpeg wrapper — a thin subprocess layer with no Electron dependency.
 *
 * Callers pass the resolved `ffmpegBin` / `ffprobeBin` (system PATH on
 * Linux/macOS, bundled binaries on Windows). Progress is read from
 * `-progress pipe:1` (machine-readable key=value blocks) rather than by
 * regex-scraping the human log on stderr, which is locale/format dependent.
 */
const { spawn } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');

// Formats whose containers can carry an attached picture (cover art).
const COVER_CAPABLE = new Set(['mp3', 'flac', 'm4a', 'aac']);
// Lossless targets: bitrate is meaningless, so `quality` is ignored.
const LOSSLESS = new Set(['flac', 'wav']);

/** "320k" → "256k" when above `maxKbps`; passes unknown strings through. */
function clampBitrate(quality, maxKbps) {
  const m = /^(\d+)\s*k$/i.exec(String(quality || '').trim());
  if (!m) return quality;
  return `${Math.min(parseInt(m[1], 10), maxKbps)}k`;
}

function parseProgressBlock(block) {
  const out = {};
  for (const line of block.split(/\r?\n/)) {
    const i = line.indexOf('=');
    if (i > 0) out[line.slice(0, i).trim()] = line.slice(i + 1).trim();
  }
  return out;
}

function runFfmpeg(args, opts = {}) {
  const { onProgress, signal, totalDurationSec } = opts;
  const ffmpegBin = opts.ffmpegBin || 'ffmpeg';
  return new Promise((resolve, reject) => {
    const proc = spawn(ffmpegBin, ['-hide_banner', '-nostdin', '-loglevel', 'error', '-progress', 'pipe:1', '-nostats', ...args], {
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stderr = '';
    let stdoutBuf = '';
    let lastPct = -1;
    const onAbort = () => { try { proc.kill('SIGTERM'); } catch {} };
    if (signal) {
      if (signal.aborted) { onAbort(); reject(new Error('aborted')); return; }
      signal.addEventListener('abort', onAbort, { once: true });
    }
    const emit = (pct) => {
      if (!onProgress) return;
      const clamped = Math.max(0, Math.min(100, pct));
      if (Math.abs(clamped - lastPct) >= 0.5 || clamped === 100) { lastPct = clamped; onProgress({ percent: clamped }); }
    };
    proc.stdout.on('data', (chunk) => {
      stdoutBuf += chunk.toString();
      // Each progress block ends with a "progress=continue|end" line.
      let idx;
      while ((idx = stdoutBuf.indexOf('progress=')) !== -1) {
        const end = stdoutBuf.indexOf('\n', idx);
        if (end === -1) break;
        const block = parseProgressBlock(stdoutBuf.slice(0, end));
        stdoutBuf = stdoutBuf.slice(end + 1);
        if (block.progress === 'end') { emit(100); continue; }
        if (!totalDurationSec) continue;
        // out_time_us (newer) / out_time_ms (older, despite the name it is µs)
        const us = Number(block.out_time_us ?? block.out_time_ms);
        if (Number.isFinite(us) && us >= 0) emit((us / 1e6 / totalDurationSec) * 100);
      }
    });
    proc.stderr.on('data', (chunk) => { stderr += chunk.toString(); });
    proc.on('error', (e) => {
      if (signal) signal.removeEventListener('abort', onAbort);
      if (e.code === 'ENOENT') reject(new Error(`ffmpeg not found (${ffmpegBin}). Install ffmpeg and make sure it is on your PATH.`));
      else reject(e);
    });
    proc.on('close', (code) => {
      if (signal) signal.removeEventListener('abort', onAbort);
      if (signal?.aborted) { reject(new Error('aborted')); return; }
      if (code === 0) { emit(100); resolve({ stderr }); }
      else reject(new Error(`ffmpeg exited ${code}: ${stderr.trim().slice(-500)}`));
    });
  });
}

function runFfprobeDuration(filePath, opts = {}) {
  const ffprobeBin = opts.ffprobeBin || 'ffprobe';
  return new Promise((resolve) => {
    const proc = spawn(ffprobeBin, ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=noprint_wrappers=1:nokey=1', filePath], { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    proc.stdout.on('data', (c) => out += c.toString());
    proc.on('close', () => { resolve(parseFloat(out) || 0); });
    proc.on('error', () => resolve(0));
  });
}

/**
 * Check that ffmpeg is runnable and return its version string.
 * Resolves `{ ok: true, version }` or `{ ok: false, error }` — never rejects.
 */
function checkFfmpeg(opts = {}) {
  const ffmpegBin = opts.ffmpegBin || 'ffmpeg';
  return new Promise((resolve) => {
    let out = '';
    let proc;
    try {
      proc = spawn(ffmpegBin, ['-version'], { stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (e) { resolve({ ok: false, error: e.message }); return; }
    proc.stdout.on('data', (c) => out += c.toString());
    proc.on('error', (e) => resolve({ ok: false, error: e.code === 'ENOENT' ? 'ffmpeg not found in PATH' : e.message }));
    proc.on('close', (code) => {
      if (code !== 0) { resolve({ ok: false, error: `ffmpeg exited ${code}` }); return; }
      const m = out.match(/ffmpeg version (\S+)/);
      resolve({ ok: true, version: m ? m[1] : 'unknown', bin: ffmpegBin });
    });
  });
}

/**
 * Build the ffmpeg argument list for a conversion.
 * Exported for unit testing.
 *
 * @param {object} p
 * @param {string} p.inputPath
 * @param {string} p.outputPath
 * @param {string} p.format        mp3|flac|wav|m4a|aac|ogg|opus
 * @param {string} [p.quality]     bitrate such as "320k" (ignored for lossless)
 * @param {boolean} [p.copyAudio]  true → `-c:a copy` (just re-mux / tag)
 * @param {string} [p.coverPath]   image file to embed as cover art
 * @param {object} [p.metadata]    { title, artist, album, ... }
 */
function buildArgs({ inputPath, outputPath, format = 'mp3', quality = '320k', copyAudio = false, coverPath, metadata }) {
  const args = ['-y', '-i', inputPath];
  const wantCover = COVER_CAPABLE.has(format);
  const hasExternalCover = wantCover && coverPath && fs.existsSync(coverPath);
  if (hasExternalCover) args.push('-i', coverPath);

  // Stream mapping: always take audio from input 0; keep an attached picture
  // when the target container supports it (either the input's own, or the
  // external cover extracted by a decoder).
  args.push('-map', '0:a:0');
  if (hasExternalCover) args.push('-map', '1:v:0', '-c:v', 'copy', '-disposition:v:0', 'attached_pic');
  else if (wantCover) args.push('-map', '0:v?', '-c:v', 'copy', '-disposition:v:0', 'attached_pic');
  else args.push('-vn');

  if (copyAudio) {
    args.push('-c:a', 'copy');
  } else if (format === 'mp3') {
    args.push('-c:a', 'libmp3lame', '-b:a', quality);
  } else if (format === 'flac') {
    args.push('-c:a', 'flac');
  } else if (format === 'wav') {
    args.push('-c:a', 'pcm_s16le');
  } else if (format === 'm4a' || format === 'aac') {
    args.push('-c:a', 'aac', '-b:a', quality);
  } else if (format === 'ogg' || format === 'opus') {
    // libopus refuses bitrates above 256k (the UI default is 320k).
    args.push('-c:a', 'libopus', '-b:a', clampBitrate(quality, 256));
  } else {
    throw new Error(`Unsupported output format: ${format}`);
  }

  if (format === 'mp3') args.push('-id3v2_version', '3');
  args.push('-map_metadata', '0');
  for (const [k, v] of Object.entries(metadata || {})) {
    if (v !== undefined && v !== null && String(v).length > 0) args.push('-metadata', `${k}=${v}`);
  }
  args.push(outputPath);
  return args;
}

async function run(inputPath, outputPath, options = {}) {
  const { format = 'mp3', quality = '320k', onProgress, signal, ffmpegBin, ffprobeBin, copyAudio, coverPath, metadata } = options;
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const args = buildArgs({ inputPath, outputPath, format, quality, copyAudio, coverPath, metadata });
  const totalDuration = await runFfprobeDuration(inputPath, { ffprobeBin });
  await runFfmpeg(args, { onProgress, signal, totalDurationSec: totalDuration, ffmpegBin });
  return { outputPath };
}

module.exports = { run, buildArgs, clampBitrate, checkFfmpeg, probeDuration: runFfprobeDuration, COVER_CAPABLE, LOSSLESS };
