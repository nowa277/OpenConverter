/**
 * Generate synthetic UC test vector for the Android port.
 *
 * NetEase PC .uc cache files are the whole file XORed with 0xA3 (self-inverse,
 * no headers, no per-file key) — mirrors gen-qmc-v1-synthetic.js structure.
 * The Kotlin UcDecoder must reproduce the same bytes and sniff the container.
 *
 * Output:
 *   - synthetic-tone.uc     (encrypted MP3)
 *   - expected-sha256.json  (sha256 of the plaintext MP3)
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const OUT_DIR = path.join(__dirname, 'output', 'uc-synthetic');
const ANDROID_RES_DIR = path.join(__dirname, '..', 'android', 'app', 'src', 'test', 'resources', 'test-uc');
fs.mkdirSync(OUT_DIR, { recursive: true });
fs.mkdirSync(ANDROID_RES_DIR, { recursive: true });

console.log('Generating synthetic UC vector...');

const mp3Path = path.join(OUT_DIR, 'sample.mp3');
if (!fs.existsSync(mp3Path)) {
  execFileSync('ffmpeg', [
    '-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
    '-ac', '1', '-ar', '22050', '-c:a', 'libmp3lame', '-b:a', '64k', mp3Path,
  ], { stdio: 'pipe' });
}
const plain = fs.readFileSync(mp3Path);
const plainSha = crypto.createHash('sha256').update(plain).digest('hex');

// XOR 0xA3 is self-inverse.
const encrypted = Buffer.from(plain.map((b) => b ^ 0xA3));
const outPath = path.join(ANDROID_RES_DIR, 'synthetic-tone.uc');
fs.writeFileSync(outPath, encrypted);

const oracle = {
  'synthetic-tone.uc': plainSha,
  notes: 'UC is a whole-file XOR 0xA3 (self-inverse, no headers, no key). The Android decoder must restore identical bytes and sniff the MP3 container.',
};
fs.writeFileSync(path.join(ANDROID_RES_DIR, 'expected-sha256.json'), JSON.stringify(oracle, null, 2) + '\n');
console.log(`  ${outPath} (${encrypted.length} bytes) — plaintext sha256=${plainSha}`);
