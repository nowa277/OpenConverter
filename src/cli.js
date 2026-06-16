#!/usr/bin/env node
/**
 * OpenConverter CLI mode — for testing and headless use.
 *
 * Usage:
 *   node src/cli.js <input.ncm> [output.mp3]
 *   node src/cli.js --format=mp3 --quality=320k <input1> <input2>...
 *   node src/cli.js --output-dir=/tmp/out <input1> <input2>...
 *
 * The same code path used by the GUI is exercised; the only thing
 * different is there's no progress UI — progress is printed to stderr.
 */
const fs = require('node:fs');
const path = require('node:path');
const decoders = require('./decoders');
const ffmpeg = require('./main/ffmpeg');

function parseArgs(argv) {
  const opts = { files: [], format: 'mp3', quality: '320k', outputDir: null };
  for (const a of argv.slice(2)) {
    if (a.startsWith('--format=')) opts.format = a.slice(9);
    else if (a.startsWith('--quality=')) opts.quality = a.slice(10);
    else if (a.startsWith('--output-dir=')) opts.outputDir = a.slice(13);
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
  --quality=320k|256k|...        Audio bitrate (default: 320k)
  --output-dir=PATH              Output directory (default: same as input)
  -h, --help                     Show this help
`);
}

async function processOne(inputPath, opts) {
  const decoder = decoders.pickDecoder(inputPath);
  if (!decoder) {
    console.error(`✗ ${path.basename(inputPath)}: no decoder for this format`);
    return false;
  }
  const outDir = opts.outputDir || path.dirname(inputPath);
  fs.mkdirSync(outDir, { recursive: true });
  let decrypted;
  try {
    const r = decoder.decodeFile(inputPath, outDir);
    decrypted = r.outputPath;
    console.log(`✓ ${path.basename(inputPath)} → ${path.basename(decrypted)} (decrypted)`);
  } catch (e) {
    console.error(`✗ ${path.basename(inputPath)}: ${e.message}`);
    return false;
  }
  const wantExt = '.' + opts.format;
  if (decrypted.endsWith(wantExt)) return true;
  const finalOut = decrypted.replace(/\.[^.]+$/, '') + wantExt;
  try {
    process.stderr.write(`  converting to ${opts.format} @ ${opts.quality}...\n`);
    await ffmpeg.run(decrypted, finalOut, {
      format: opts.format,
      quality: opts.quality,
      onProgress: ({ percent }) => process.stderr.write(`  ${percent.toFixed(0)}%\r`),
    });
    process.stderr.write('\n');
    fs.unlinkSync(decrypted);
    console.log(`✓ ${path.basename(inputPath)} → ${path.basename(finalOut)}`);
    return true;
  } catch (e) {
    console.error(`✗ ${path.basename(inputPath)}: ffmpeg — ${e.message}`);
    return false;
  }
}

async function main() {
  const opts = parseArgs(process.argv);
  if (opts.files.length === 0) { printHelp(); process.exit(1); }
  let ok = 0, fail = 0;
  for (const f of opts.files) {
    if (!fs.existsSync(f)) { console.error(`✗ not found: ${f}`); fail++; continue; }
    if (await processOne(f, opts)) ok++; else fail++;
  }
  console.log(`\n${ok} ok, ${fail} failed`);
  process.exit(fail > 0 ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });
