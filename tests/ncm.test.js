/**
 * NCM decoder test — byte-for-byte diff against Python `ncmdump` reference.
 *
 * For each .ncm sample in dist/test-ncm/:
 *  1. Run our pure-JS NCM decoder
 *  2. Run the Python `ncmdump` reference
 *  3. Compare audio bytes (after ID3 tag) — must be 0 diff
 *  4. Verify the output is a valid MP3 (ffprobe shows duration > 0)
 *
 * If Python ncmdump is not installed, falls back to checking that the
 * output is a valid MP3 file (no byte-diff).
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const ncm = require('../src/decoders/ncm');

const SAMPLE_DIR = path.join(__dirname, '..', 'dist', 'test-ncm');
const OUT_DIR = path.join(__dirname, 'output');
const PY_REF = path.join(OUT_DIR, 'py-ref');

function havePythonRef() {
  try {
    execFileSync('python3', ['-c', 'from ncmdump import dump'], { stdio: 'pipe' });
    return true;
  } catch {
    return false;
  }
}

function pythonRefDecrypt(ncmPath, outPath) {
  execFileSync('python3', ['-c', `
from ncmdump import dump
dump('${ncmPath}', '${outPath}')
`], { stdio: 'pipe' });
}

function readId3v2Size(buf) {
  if (buf[0] !== 0x49 || buf[1] !== 0x44 || buf[2] !== 0x33) return 0;
  return (buf[6] << 21) | (buf[7] << 14) | (buf[8] << 7) | buf[9];
}

function ffprobeDuration(filePath) {
  try {
    const out = execFileSync('ffprobe', [
      '-v', 'error',
      '-show_entries', 'format=duration',
      '-of', 'default=noprint_wrappers=1:nokey=1',
      filePath,
    ], { stdio: ['pipe', 'pipe', 'pipe'] }).toString().trim();
    return parseFloat(out);
  } catch (e) {
    return -1;
  }
}

function run() {
  fs.rmSync(OUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.mkdirSync(PY_REF, { recursive: true });

  const samples = fs.readdirSync(SAMPLE_DIR).filter((f) => f.endsWith('.ncm'));
  if (samples.length === 0) {
    console.error(`No .ncm samples in ${SAMPLE_DIR}`);
    process.exit(1);
  }

  const usePyRef = havePythonRef();
  console.log(`Found ${samples.length} samples. Python reference: ${usePyRef ? 'yes' : 'no'}`);

  let pass = 0;
  let fail = 0;
  const results = [];

  for (const s of samples) {
    const input = path.join(SAMPLE_DIR, s);
    const baseName = s.replace(/\.ncm$/i, '');
    const nodeOut = path.join(OUT_DIR, `${baseName}.mp3`);

    let nodeResult;
    try {
      nodeResult = ncm.decodeFile(input, OUT_DIR);
    } catch (e) {
      console.error(`  ✗ ${s}: decode failed — ${e.message}`);
      fail++;
      results.push({ sample: s, ok: false, error: e.message });
      continue;
    }

    const nodeBuf = fs.readFileSync(nodeOut);
    const nodeAudioStart = 10 + readId3v2Size(nodeBuf);
    const nodeAudioLen = nodeBuf.length - nodeAudioStart;

    if (nodeAudioLen < 1000) {
      console.error(`  ✗ ${s}: node output too small (${nodeAudioLen} bytes audio)`);
      fail++;
      results.push({ sample: s, ok: false, error: 'audio too small' });
      continue;
    }

    if (!usePyRef) {
      // Without Python reference, just check it's a valid MP3 with reasonable duration
      const dur = ffprobeDuration(nodeOut);
      if (dur > 0) {
        console.log(`  ✓ ${s}: ${(nodeAudioLen / 1024).toFixed(0)}KB audio, ${dur.toFixed(1)}s (no byte-diff: python ncmdump unavailable)`);
        pass++;
        results.push({ sample: s, ok: true, audioBytes: nodeAudioLen, duration: dur, byteDiff: null });
      } else {
        console.error(`  ✗ ${s}: ffprobe failed to detect duration`);
        fail++;
        results.push({ sample: s, ok: false, error: 'ffprobe failed' });
      }
      continue;
    }

    // Python reference
    const pyOut = path.join(PY_REF, `${baseName}.mp3`);
    try {
      pythonRefDecrypt(input, pyOut);
    } catch (e) {
      console.error(`  ✗ ${s}: python reference failed — ${e.message}`);
      fail++;
      results.push({ sample: s, ok: false, error: 'python ref failed' });
      continue;
    }

    const pyBuf = fs.readFileSync(pyOut);
    const pyAudioStart = 10 + readId3v2Size(pyBuf);
    const pyAudioLen = pyBuf.length - pyAudioStart;

    if (pyAudioLen !== nodeAudioLen) {
      console.error(`  ✗ ${s}: audio length mismatch (node=${nodeAudioLen}, py=${pyAudioLen})`);
      fail++;
      results.push({ sample: s, ok: false, error: `length mismatch` });
      continue;
    }

    // Byte-diff
    let diffs = 0;
    for (let i = 0; i < nodeAudioLen; i++) {
      if (nodeBuf[nodeAudioStart + i] !== pyBuf[pyAudioStart + i]) diffs++;
    }

    if (diffs === 0) {
      const dur = ffprobeDuration(nodeOut);
      console.log(`  ✓ ${s}: ${(nodeAudioLen / 1024).toFixed(0)}KB audio, ${dur.toFixed(1)}s, 0 byte diff`);
      pass++;
      results.push({ sample: s, ok: true, audioBytes: nodeAudioLen, duration: dur, byteDiff: 0 });
    } else {
      console.error(`  ✗ ${s}: ${diffs} byte diffs in audio (${(diffs / nodeAudioLen * 100).toFixed(2)}%)`);
      fail++;
      results.push({ sample: s, ok: false, error: `${diffs} byte diffs` });
    }
  }

  console.log(`\nResult: ${pass} passed, ${fail} failed (${samples.length} total)`);
  fs.writeFileSync(path.join(OUT_DIR, 'ncm-test-results.json'), JSON.stringify(results, null, 2));
  process.exit(fail > 0 ? 1 : 0);
}

run();
