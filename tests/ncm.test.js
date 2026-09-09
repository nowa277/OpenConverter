/**
 * NCM decoder test.
 *
 * Two modes:
 *
 *  1. Synthetic (always runs, no external data): builds a minimal valid NCM
 *     (AES-128-ECB key block + modified-RC4 audio + meta + cover) from a
 *     generated MP3 and checks that decodeFile() reproduces the audio
 *     byte-for-byte and surfaces tags + cover.
 *
 *  2. Real samples (optional): for each .ncm in dist/test-ncm/ (gitignored,
 *     bring your own), decode and — when the Python `ncmdump` package is
 *     installed — byte-diff the audio against it. Otherwise just validate
 *     with ffprobe.
 */
const { test } = require('node:test');
const assert = require('node:assert');
const { execFileSync } = require('node:child_process');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const ncm = require('../src/decoders/ncm');

const SAMPLE_DIR = path.join(__dirname, '..', 'dist', 'test-ncm');
const OUT_DIR = path.join(__dirname, 'output', 'ncm');
const PY_REF = path.join(OUT_DIR, 'py-ref');

const CORE_KEY = Buffer.from('687A4852416D736F356B496E62617857', 'hex');
const META_KEY = Buffer.from('2331346C6A6B5F215C5D2630553C2728', 'hex');
// Real files: key plaintext = "neteasecloudmusic" (17 bytes) + RC4 key.
const PREFIX = Buffer.from('neteasecloudmusic', 'ascii');

// ---------- synthetic NCM builder (mirrors src/decoders/ncm.js layout) ----------

function aesEcbEncrypt(block, key) {
  const c = crypto.createCipheriv('aes-128-ecb', key, null);
  c.setAutoPadding(false);
  return Buffer.concat([c.update(block), c.final()]);
}
function pkcs7Pad(buf) {
  const pad = 16 - (buf.length % 16);
  return Buffer.concat([buf, Buffer.alloc(pad, pad)]);
}
function buildRc4Sbox(key) {
  const S = Buffer.alloc(256);
  for (let i = 0; i < 256; i++) S[i] = i;
  let j = 0;
  for (let i = 0; i < 256; i++) {
    j = (j + S[i] + key[i % key.length]) & 0xff;
    const tmp = S[i]; S[i] = S[j]; S[j] = tmp;
  }
  return S;
}
function rc4Transform(S, data) {
  const k = Buffer.alloc(256);
  for (let i = 0; i < 256; i++) k[i] = S[(S[i] + S[(i + S[i]) & 0xff]) & 0xff];
  const out = Buffer.alloc(data.length);
  for (let i = 0; i < data.length; i++) out[i] = data[i] ^ k[(i + 1) % 256];
  return out;
}
function u32(n) { const b = Buffer.alloc(4); b.writeUInt32LE(n); return b; }

function buildSyntheticNcm(audio, metaObj, image) {
  const rc4Key = crypto.randomBytes(16);
  let keyEnc = aesEcbEncrypt(pkcs7Pad(Buffer.concat([PREFIX, rc4Key])), CORE_KEY);
  for (let i = 0; i < keyEnc.length; i++) keyEnc[i] ^= 0x64;

  let metaBlock = Buffer.alloc(0);
  if (metaObj) {
    const json = Buffer.from('music:' + JSON.stringify(metaObj), 'utf8');
    const enc = aesEcbEncrypt(pkcs7Pad(json), META_KEY).toString('base64');
    metaBlock = Buffer.from('163 key(Don\'t modify):' + enc, 'utf8');
    for (let i = 0; i < metaBlock.length; i++) metaBlock[i] ^= 0x63;
  }
  const img = image || Buffer.alloc(0);
  return {
    ncm: Buffer.concat([
      Buffer.from('CTENFDAM', 'ascii'), Buffer.alloc(2), u32(keyEnc.length), keyEnc,
      u32(metaBlock.length), metaBlock, Buffer.alloc(5),
      u32(img.length), u32(img.length), img,
      rc4Transform(buildRc4Sbox(rc4Key), audio),
    ]),
  };
}

function haveFfmpeg() {
  try { execFileSync('ffmpeg', ['-version'], { stdio: 'pipe' }); return true; } catch { return false; }
}
function makeSineMp3(dest) {
  execFileSync('ffmpeg', ['-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
    '-ac', '1', '-ar', '22050', '-c:a', 'libmp3lame', '-b:a', '64k', dest], { stdio: 'pipe' });
  return fs.readFileSync(dest);
}
function ffprobeDuration(filePath) {
  try {
    return parseFloat(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration',
      '-of', 'default=noprint_wrappers=1:nokey=1', filePath], { stdio: ['pipe', 'pipe', 'pipe'] }).toString().trim());
  } catch { return -1; }
}

fs.rmSync(OUT_DIR, { recursive: true, force: true });
fs.mkdirSync(PY_REF, { recursive: true });

// ---------- synthetic tests ----------

test('ncm: synthetic round-trip restores audio bytes, tags and cover', { skip: !haveFfmpeg() && 'ffmpeg not available' }, () => {
  const audio = makeSineMp3(path.join(OUT_DIR, '_sine.mp3'));
  // 1x1 PNG
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');
  const meta = { musicName: 'Sine Test', artist: [['Synth', 1], ['Wave', 2]], album: 'Unit Tests', format: 'mp3' };
  const { ncm: file } = buildSyntheticNcm(audio, meta, png);
  const inPath = path.join(OUT_DIR, 'synthetic.ncm');
  fs.writeFileSync(inPath, file);

  const r = ncm.decodeFile(inPath, OUT_DIR);
  assert.strictEqual(r.format, 'mp3');
  assert.ok(fs.readFileSync(r.outputPath).equals(audio), 'decrypted audio must equal original');
  assert.deepStrictEqual(r.tags, { title: 'Sine Test', artist: 'Synth / Wave', album: 'Unit Tests' });
  assert.ok(r.coverPath && r.coverPath.endsWith('.cover.png'));
  assert.ok(fs.readFileSync(r.coverPath).equals(png));
  assert.ok(ffprobeDuration(r.outputPath) > 0.5);
});

test('ncm: no meta / no image yields null tags and cover', () => {
  const audio = Buffer.alloc(4096, 0x5a);
  const { ncm: file } = buildSyntheticNcm(audio, null, null);
  const inPath = path.join(OUT_DIR, 'bare.ncm');
  fs.writeFileSync(inPath, file);
  const r = ncm.decodeFile(inPath, OUT_DIR);
  assert.strictEqual(r.tags, null);
  assert.strictEqual(r.coverPath, null);
  assert.ok(fs.readFileSync(r.outputPath).equals(audio));
});

test('ncm: rejects non-NCM input', () => {
  assert.throws(() => ncm.decryptBuffer(Buffer.from('definitely not an ncm file')), /CTENFDAM/);
});

// ---------- optional: real samples ----------

const realSamples = fs.existsSync(SAMPLE_DIR) ? fs.readdirSync(SAMPLE_DIR).filter((f) => f.endsWith('.ncm')) : [];
let havePyRef = false;
try { execFileSync('python3', ['-c', 'from ncmdump import dump'], { stdio: 'pipe' }); havePyRef = true; } catch {}

test(`ncm: real samples (${realSamples.length} found in dist/test-ncm)`, { skip: realSamples.length === 0 && 'no local samples' }, () => {
  const readId3v2Size = (buf) => (buf[0] === 0x49 && buf[1] === 0x44 && buf[2] === 0x33)
    ? 10 + ((buf[6] << 21) | (buf[7] << 14) | (buf[8] << 7) | buf[9]) : 0;
  for (const s of realSamples) {
    const r = ncm.decodeFile(path.join(SAMPLE_DIR, s), OUT_DIR, { extractCover: false });
    const nodeBuf = fs.readFileSync(r.outputPath);
    assert.ok(nodeBuf.length - readId3v2Size(nodeBuf) > 1000, `${s}: output too small`);
    assert.ok(ffprobeDuration(r.outputPath) > 0, `${s}: ffprobe could not read output`);
    if (havePyRef) {
      const pyOut = path.join(PY_REF, path.basename(r.outputPath));
      execFileSync('python3', ['-c', `from ncmdump import dump\ndump(${JSON.stringify(path.join(SAMPLE_DIR, s))}, ${JSON.stringify(pyOut)})`], { stdio: 'pipe' });
      const pyBuf = fs.readFileSync(pyOut);
      assert.ok(nodeBuf.subarray(readId3v2Size(nodeBuf)).equals(pyBuf.subarray(readId3v2Size(pyBuf))), `${s}: audio differs from python ncmdump`);
    }
  }
});
