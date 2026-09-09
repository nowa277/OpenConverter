#!/usr/bin/env node
/**
 * OpenConverter CLI mode — for testing and headless use.
 *
 * Usage:
 *   node src/cli.js <input.ncm> [more inputs...]
 *   node src/cli.js --format=flac --output-dir=/tmp/out <input1> <input2>...
 *
 * Runs exactly the same pipeline as the GUI (src/main/pipeline.js); the only
 * difference is that progress is printed to stderr instead of the queue UI.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const pipeline = require('./main/pipeline');

function parseArgs(argv) {
  const opts = { files: [], format: 'mp3', quality: '320k', outputDir: null, jobs: 0 };
  for (const a of argv.slice(2)) {
    if (a.startsWith('--format=')) opts.format = a.slice(9);
    else if (a.startsWith('--quality=')) opts.quality = a.slice(10);
    else if (a.startsWith('--output-dir=')) opts.outputDir = a.slice(13);
    else if (a.startsWith('--key-path=')) opts.keyPath = a.slice(11);
    else if (a.startsWith('--qq-cookie=')) opts.qqCookie = a.slice(12);
    else if (a.startsWith('--ekey=')) opts.ekey = a.slice(7);
    else if (a.startsWith('--jobs=')) opts.jobs = parseInt(a.slice(7), 10) || 0;
    else if (a === '--help' || a === '-h') { printHelp(); process.exit(0); }
    else opts.files.push(a);
  }
  return opts;
}

function printHelp() {
  console.log(`OpenConverter CLI

Usage:
  node src/cli.js [options] <input1> [input2...]

Options:
  --format=mp3|flac|wav|m4a|ogg  Output format (default: mp3)
  --quality=320k|256k|...        Audio bitrate (default: 320k; ignored for flac/wav)
  --output-dir=PATH              Output directory (default: same as input)
  --jobs=N                       Parallel conversions (default: min(4, CPU count))
  --key-path=PATH                Path to kgg.keys (for .kgg files)
  --ekey=BASE64                  QQ Music ekey for keyless .mflac/.mgg files
  --qq-cookie=COOKIE             QQ Music cookie for fetching ekey (musicex files)
  -h, --help                     Show this help
`);
}

function defaultKggKeyPath() {
  return process.platform === 'win32'
    ? path.join(os.homedir(), 'AppData', 'Roaming', 'OpenConverter', 'kgg.keys')
    : path.join(os.homedir(), '.config', 'OpenConverter', 'kgg.keys');
}

async function processOne(inputPath, opts) {
  const name = path.basename(inputPath);
  const lower = inputPath.toLowerCase();
  const decodeOpts = {};
  if (lower.endsWith('.kgg') || lower.endsWith('.kgg.flac')) decodeOpts.keyPath = opts.keyPath || defaultKggKeyPath();
  if (opts.qqCookie) decodeOpts.qqCookie = opts.qqCookie;
  if (opts.ekey) decodeOpts.ekey = opts.ekey;

  let lastStage = '';
  try {
    const r = await pipeline.convertOne({
      inputPath,
      outputDir: opts.outputDir || path.dirname(inputPath),
      format: opts.format,
      quality: opts.quality,
      decodeOpts,
      onProgress: ({ stage, percent }) => {
        if (stage !== lastStage) { lastStage = stage; if (stage === 'decrypt') process.stderr.write(`  ${name}: decrypting…\n`); }
        if (stage === 'encode' && percent != null) process.stderr.write(`  ${name}: ${percent.toFixed(0)}%\r`);
      },
    });
    if (lastStage === 'encode') process.stderr.write('\n');
    console.log(`✓ ${name} → ${path.basename(r.outputPath)} (${(r.durationMs / 1000).toFixed(1)}s)`);
    return true;
  } catch (e) {
    console.error(`✗ ${name}: ${e.message}`);
    return false;
  }
}

async function main() {
  const opts = parseArgs(process.argv);
  if (opts.files.length === 0) { printHelp(); process.exit(1); }
  const existing = opts.files.filter((f) => {
    if (fs.existsSync(f)) return true;
    console.error(`✗ not found: ${f}`);
    return false;
  });
  const limit = opts.jobs > 0 ? opts.jobs : Math.min(4, Math.max(1, os.cpus()?.length || 1));
  const outcomes = await pipeline.parallelLimit(limit, existing.map((f) => () => processOne(f, opts)));
  const ok = outcomes.filter(Boolean).length;
  const fail = opts.files.length - ok;
  console.log(`\n${ok} ok, ${fail} failed`);
  process.exit(fail > 0 ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });
