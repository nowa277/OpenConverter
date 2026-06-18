/**
 * Generate a synthetic NCM test vector for the Android port.
 *
 * NCM is AES-128-ECB (key/meta blocks) + modified-RC4 (audio). AES and RC4
 * are both symmetric here (the "RC4" keystream is precomputed from the s-box
 * and XOR'd, so encrypt == decrypt). We build a minimal valid NCM from a
 * 1-second sine MP3, then verify by round-tripping through the real desktop
 * decoder (src/decoders/ncm.js) and recording its SHA-256 as the oracle.
 *
 * Output:
 *   - sample.ncm            (synthetic, ~10 KB)
 *   - expected-sha256.json  (sha256 of the decrypted audio)
 *
 * Layout (mirrors src/decoders/ncm.js):
 *   "CTENFDAM" | 2 gap | u32 keyLen | keyEnc | u32 metaLen | metaEnc |
 *   5 gap | u32 imgSpace | u32 imgSize | image | (imgSpace-imgSize) pad | audio
 *
 * metaLen=0 (no meta block) — keeps the synthetic minimal; the audio path
 * is what the parity test guards.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const MAGIC = Buffer.from('CTENFDAM', 'ascii');
const CORE_KEY = Buffer.from('687A4852416D736F356B496E62617857', 'hex');
const PREFIX = Buffer.from('neteasecloudmusic\0', 'ascii'); // 17 bytes

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

// Symmetric: same transform decrypt applies.
function rc4Transform(S, data) {
  const k = Buffer.alloc(256);
  for (let i = 0; i < 256; i++) k[i] = S[(S[i] + S[(i + S[i]) & 0xff]) & 0xff];
  const out = Buffer.alloc(data.length);
  for (let i = 0; i < data.length; i++) out[i] = data[i] ^ k[(i + 1) % 256];
  return out;
}

const OUT_DIR = path.join(__dirname, '..', 'android', 'app', 'src', 'test', 'resources', 'test-ncm');
fs.mkdirSync(OUT_DIR, { recursive: true });

const mp3Path = path.join(OUT_DIR, '_sample.mp3');
if (!fs.existsSync(mp3Path)) {
  execFileSync('ffmpeg', ['-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
    '-ac', '1', '-ar', '22050', '-c:a', 'libmp3lame', '-b:a', '64k', mp3Path], { stdio: 'pipe' });
}
const audio = fs.readFileSync(mp3Path);

const rc4Key = Buffer.from([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]);
const encAudio = rc4Transform(buildRc4Sbox(rc4Key), audio);

// Key block: "neteasecloudmusic\0" + rc4Key, PKCS7 pad, AES-128-ECB(CORE_KEY), XOR 0x64.
let keyPlain = pkcs7Pad(Buffer.concat([PREFIX, rc4Key]));
let keyEnc = aesEcbEncrypt(keyPlain, CORE_KEY);
for (let i = 0; i < keyEnc.length; i++) keyEnc[i] ^= 0x64;

const keyLen = Buffer.alloc(4); keyLen.writeUInt32LE(keyEnc.length);
const metaLen = Buffer.alloc(4); metaLen.writeUInt32LE(0); // no meta
const imgSpace = Buffer.alloc(4); imgSpace.writeUInt32LE(0);
const imgSize = Buffer.alloc(4); imgSize.writeUInt32LE(0);

const ncm = Buffer.concat([
  MAGIC, Buffer.alloc(2, 0), keyLen, keyEnc, metaLen,
  Buffer.alloc(5, 0), imgSpace, imgSize, encAudio,
]);

const outPath = path.join(OUT_DIR, 'sample.ncm');
fs.writeFileSync(outPath, ncm);
fs.unlinkSync(mp3Path);

// Verify via the real decoder, record oracle.
const { decryptBuffer } = require('../src/decoders/ncm');
const { audio: decAudio } = decryptBuffer(ncm);
const sha = crypto.createHash('sha256').update(decAudio).digest('hex');
fs.writeFileSync(path.join(OUT_DIR, 'expected-sha256.json'),
  JSON.stringify({ 'sample.ncm': sha, format: 'mp3' }, null, 2) + '\n');
console.log(`synthetic ncm: ${ncm.length} bytes — plaintext sha256=${sha}`);
