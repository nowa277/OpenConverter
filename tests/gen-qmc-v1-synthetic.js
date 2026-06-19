/**
 * Generate synthetic QMCv1 test vector for the Android port.
 *
 * QMCv1 (.qmc0/.qmc3/.qmcflac/.qmcogg) is a headerless XOR cipher: the whole
 * file is XOR'd with a 64-byte keystream from an 8x7 state machine (with a
 * 0x8000-position skip). No per-file key. XOR is self-inverse, so encrypt ==
 * decrypt. We encrypt a known MP3 with the JS keystream; the Kotlin decoder
 * must reproduce the exact same keystream to recover the plaintext.
 *
 * Output:
 *   - synthetic-qmc0.qmc0   (encrypted MP3)
 *   - expected-sha256.json  (sha256 of the plaintext MP3)
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { decryptV1Buffer } = require('../src/decoders/qmc');

const OUT_DIR = path.join(__dirname, 'output', 'qmc-v1-synthetic');
const ANDROID_RES_DIR = path.join(__dirname, '..', 'android', 'app', 'src', 'test', 'resources', 'test-qmc');
fs.mkdirSync(OUT_DIR, { recursive: true });
fs.mkdirSync(ANDROID_RES_DIR, { recursive: true });

console.log('Generating synthetic QMCv1 vector...');

const mp3Path = path.join(OUT_DIR, 'sample.mp3');
if (!fs.existsSync(mp3Path)) {
  execFileSync('ffmpeg', [
    '-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
    '-ac', '1', '-ar', '22050', '-c:a', 'libmp3lame', '-b:a', '64k', mp3Path,
  ], { stdio: 'pipe' });
}
const plain = fs.readFileSync(mp3Path);
const plainSha = crypto.createHash('sha256').update(plain).digest('hex');

// Encrypt: XOR is self-inverse, so decryptV1Buffer(plaintext) == ciphertext.
const encrypted = decryptV1Buffer(plain);
const outPath = path.join(ANDROID_RES_DIR, 'synthetic-qmc0.qmc0');
fs.writeFileSync(outPath, encrypted);

const oracle = {
  'synthetic-qmc0.qmc0': plainSha,
  notes: 'QMCv1 is a headerless XOR cipher with a deterministic keystream (no per-file key). The Android decoder must reproduce the exact 8x7 state-machine keystream including the 0x8000-position skip.',
};
fs.writeFileSync(
  path.join(ANDROID_RES_DIR, 'expected-sha256.json'),
  JSON.stringify(oracle, null, 2) + '\n',
);
console.log(`  ${outPath} (${encrypted.length} bytes) — plaintext sha256=${plainSha}`);
