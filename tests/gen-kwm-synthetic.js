/**
 * Generate synthetic KWM test vector for the Android port.
 *
 * The KWM cipher is a 32-byte circular XOR mask. Since XOR is self-inverse,
 * encrypt == decrypt. We use the real desktop decoder to round-trip and
 * compute the expected plaintext sha256, then re-encrypt to produce the
 * synthetic .kwm file. The Kotlin decoder must reproduce the exact same bytes.
 *
 * Output:
 *   - synthetic-kwm.kwm   (yeelion-kuwo magic, seed=123456, encrypted MP3)
 *   - expected-sha256.json (sha256 of the decrypted audio)
 *
 * File layout (mirrors src/decoders/kwm.js):
 *   0x000..0x00A  Magic "yeelion-kuwo" (10 bytes)
 *   0x00A..0x010  6 bytes padding
 *   0x010..0x014  4-byte uint32 LE seed
 *   0x014..0x400  Reserved / padding
 *   0x400..end   Encrypted audio (XOR with derived 32-byte mask)
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { decryptBuffer, MAGIC, AUDIO_OFFSET, SEED_OFFSET, buildMask } = require('../src/decoders/kwm');

const OUT_DIR = path.join(__dirname, 'output', 'kwm-synthetic');
const ANDROID_RES_DIR = path.join(__dirname, '..', 'android', 'app', 'src', 'test', 'resources', 'test-kwm');
fs.mkdirSync(OUT_DIR, { recursive: true });
fs.mkdirSync(ANDROID_RES_DIR, { recursive: true });

// Seed for the XOR mask. Choose 123456 so the decimal string ("123456") is
// exactly 6 chars — well under MASK_SIZE=32 — keeping the rest of the mask
// driven entirely by ROOT. The Kotlin decoder must derive the mask from the
// file's seed (uint32 LE at offset 0x10), not hard-code any value.
const SEED = 123456;

function genAudio(fmt, codec, outPath) {
  if (fs.existsSync(outPath)) return;
  execFileSync('ffmpeg', [
    '-y',
    '-f', 'lavfi',
    '-i', 'sine=frequency=440:duration=1',
    '-ac', '1',
    '-ar', '22050',
    '-c:a', codec,
    '-b:a', '64k',
    outPath,
  ], { stdio: 'pipe' });
  console.log(`  generated ${fmt} (${fs.statSync(outPath).size} bytes)`);
}

console.log('Generating synthetic KWM vector...');

const mp3Path = path.join(OUT_DIR, 'sample.mp3');
genAudio('mp3', 'libmp3lame', mp3Path);
const mp3Bytes = fs.readFileSync(mp3Path);

// Build synthetic kwm: header + (mp3 XOR mask)
// AES-style header: magic + padding + seed (uint32 LE) + reserved up to AUDIO_OFFSET
function buildFakeKwm(plaintext, seed) {
  const header = Buffer.alloc(AUDIO_OFFSET);
  MAGIC.copy(header, 0);
  // offset 0x0A..0x10 left as zero (padding)
  header.writeUInt32LE(seed, SEED_OFFSET);
  // offset 0x14..0x400 left as zero (reserved)

  const mask = buildMask(seed);
  const cipher = Buffer.alloc(plaintext.length);
  for (let i = 0; i < plaintext.length; i++) {
    cipher[i] = plaintext[i] ^ mask[i % 32];
  }
  return Buffer.concat([header, cipher]);
}

const kwmCipher = buildFakeKwm(mp3Bytes, SEED);
const kwmOut = path.join(ANDROID_RES_DIR, 'synthetic-kwm.kwm');
fs.writeFileSync(kwmOut, kwmCipher);
const kwmDec = decryptBuffer(kwmCipher);
if (!kwmDec.equals(mp3Bytes)) throw new Error('KWM round-trip mismatch');
const kwmSha = crypto.createHash('sha256').update(mp3Bytes).digest('hex');
console.log(`  ${kwmOut} (${kwmCipher.length} bytes) — plaintext sha256=${kwmSha}`);

const expectedJson = {
  'synthetic-kwm.kwm': kwmSha,
  'seed': SEED,
  'notes': 'Seed is a uint32 LE at offset 0x10 in the header. The Android decoder must derive the 32-byte XOR mask from this seed, not hard-code it.',
};
const expectedPath = path.join(ANDROID_RES_DIR, 'expected-sha256.json');
fs.writeFileSync(expectedPath, JSON.stringify(expectedJson, null, 2) + '\n');
console.log(`\nWrote ${expectedPath}:`);
console.log(fs.readFileSync(expectedPath, 'utf8'));