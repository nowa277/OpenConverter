/**
 * ffmpeg wrapper. Uses system-installed ffmpeg/ffprobe (we explicitly
 * don't bundle the binary — the user must have ffmpeg installed).
 */
const { spawn } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');

function runFfmpeg(args, { onProgress, signal, totalDurationSec } = {}) {
  return new Promise((resolve, reject) => {
    const proc = spawn('ffmpeg', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let stderr = '';
    let lastPct = -1;
    const onAbort = () => { try { proc.kill('SIGTERM'); } catch {} };
    if (signal) {
      if (signal.aborted) { onAbort(); reject(new Error('aborted')); return; }
      signal.addEventListener('abort', onAbort, { once: true });
    }
    proc.stderr.on('data', (chunk) => {
      const s = chunk.toString();
      stderr += s;
      // ffmpeg writes "time=HH:MM:SS.MS" to stderr
      const m = s.match(/time=(\d+):(\d+):(\d+\.\d+)/);
      if (m && onProgress && totalDurationSec) {
        const cur = (+m[1]) * 3600 + (+m[2]) * 60 + (+m[3]);
        const pct = Math.min(100, (cur / totalDurationSec) * 100);
        if (Math.abs(pct - lastPct) >= 0.5) { lastPct = pct; onProgress({ percent: pct }); }
      }
    });
    proc.on('error', (e) => { reject(e); });
    proc.on('close', (code) => {
      if (signal?.aborted) { reject(new Error('aborted')); return; }
      if (code === 0) { resolve({ stderr }); } else { reject(new Error(`ffmpeg exited ${code}: ${stderr.slice(-500)}`)); }
    });
  });
}

function runFfprobeDuration(filePath) {
  return new Promise((resolve) => {
    const proc = spawn('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=noprint_wrappers=1:nokey=1', filePath], { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    proc.stdout.on('data', (c) => out += c.toString());
    proc.on('close', () => { resolve(parseFloat(out) || 0); });
    proc.on('error', () => resolve(0));
  });
}

async function run(inputPath, outputPath, options = {}) {
  const { format = 'mp3', quality = '320k', onProgress, signal } = options;
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const args = ['-y', '-i', inputPath, '-vn'];
  // Choose codec by format
  if (format === 'mp3') { args.push('-codec:a', 'libmp3lame', '-b:a', quality); }
  else if (format === 'flac') { args.push('-codec:a', 'flac'); }
  else if (format === 'wav') { args.push('-codec:a', 'pcm_s16le'); }
  else if (format === 'm4a' || format === 'aac') { args.push('-codec:a', 'aac', '-b:a', quality); }
  else if (format === 'ogg' || format === 'opus') { args.push('-codec:a', 'libopus', '-b:a', quality); }
  else { throw new Error(`Unsupported output format: ${format}`); }
  args.push(outputPath);

  const totalDuration = await runFfprobeDuration(inputPath);
  await runFfmpeg(args, { onProgress, signal, totalDurationSec: totalDuration });
  return { outputPath };
}

module.exports = { run, probeDuration: runFfprobeDuration };
