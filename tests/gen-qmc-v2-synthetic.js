/**
 * Generate synthetic QMCv2 test vectors (.mflac0, .mgg) for the Android port.
 *
 * Picks an arbitrary ekey (base64 string), generates a small FLAC and an OGG
 * audio file with ffmpeg, then encrypts each with the QMCv2 working key
 * and writes:
 *   - synthetic-mflac.mflac   (encrypted FLAC, expected → "flac")
 *   - synthetic-mgg.mgg       (encrypted OGG,  expected → "ogg")
 *   - expected-sha256.json    (the sha256 of the *decrypted* audio, for byte-
 *                              level matching with the Kotlin decoder)
 *
 * The ekey is the same one used in tests/qmc-v2.test.js for round-tripping.
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { keyCompress } = require('../src/decoders/qmc');

const OUT_DIR = path.join(__dirname, 'output', 'qmc-v2-synthetic');
const ANDROID_RES_DIR = path.join(__dirname, '..', 'android', 'app', 'src', 'test', 'resources', 'test-qmc-v2');
fs.mkdirSync(OUT_DIR, { recursive: true });
fs.mkdirSync(ANDROID_RES_DIR, { recursive: true });

// Same ekey the desktop test uses, for consistency.
const TEST_EKEY_B64 = 'Y2lwaGVydGV4dHN0cmluZw=='; // "ciphertextstring"

function encrypt(plain, ekeyB64) {
  const ekey = Buffer.from(ekeyB64, 'base64');
  const wkey = keyCompress(ekey);
  const out = Buffer.alloc(plain.length);
  for (let i = 0; i < plain.length; i++) {
    const off = i > 0x7fff ? i % 0x7fff : i;
    out[i] = plain[i] ^ wkey[off % 128];
  }
  return out;
}

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

console.log('Generating synthetic QMCv2 vectors...');

// FLAC → .mflac0 (with Numeric tail)
const flacPath = path.join(OUT_DIR, 'sample.flac');
genAudio('flac', 'flac', flacPath);
const flacBytes = fs.readFileSync(flacPath);
const mflacCipher = encrypt(flacBytes, TEST_EKEY_B64);
const flacEkeyBytes = Buffer.from(TEST_EKEY_B64, 'ascii');
const flacKeyLenBuf = Buffer.alloc(4);
flacKeyLenBuf.writeUInt32LE(flacEkeyBytes.length);
const mflacWithTail = Buffer.concat([mflacCipher, flacEkeyBytes, flacKeyLenBuf]);
const mflacOut = path.join(ANDROID_RES_DIR, 'synthetic-mflac.mflac');
fs.writeFileSync(mflacOut, mflacWithTail);
const flacSha = crypto.createHash('sha256').update(flacBytes).digest('hex');
console.log(`  ${mflacOut} (${mflacWithTail.length} bytes) — plaintext sha256=${flacSha}`);

// OGG → .mgg (with QTag tail)
const oggPath = path.join(OUT_DIR, 'sample.ogg');
genAudio('ogg', 'libvorbis', oggPath);
const oggBytes = fs.readFileSync(oggPath);
const mggCipher = encrypt(oggBytes, TEST_EKEY_B64);
const rawMeta = `${TEST_EKEY_B64},12345,extra`;
const rawMetaBytes = Buffer.from(rawMeta, 'utf-8');
const metaLenBuf = Buffer.alloc(4);
metaLenBuf.writeUInt32BE(rawMetaBytes.length);
const qtagBuf = Buffer.from('QTag', 'ascii');
const mggWithTail = Buffer.concat([mggCipher, rawMetaBytes, metaLenBuf, qtagBuf]);
const mggOut = path.join(ANDROID_RES_DIR, 'synthetic-mgg.mgg');
fs.writeFileSync(mggOut, mggWithTail);
const oggSha = crypto.createHash('sha256').update(oggBytes).digest('hex');
console.log(`  ${mggOut} (${mggWithTail.length} bytes) — plaintext sha256=${oggSha}`);

const expectedJson = {
  'synthetic-mflac.mflac': flacSha,
  'synthetic-mgg.mgg': oggSha,
  'ekey': TEST_EKEY_B64,
};
const expectedPath = path.join(ANDROID_RES_DIR, 'expected-sha256.json');
fs.writeFileSync(expectedPath, JSON.stringify(expectedJson, null, 2) + '\n');
console.log(`\nWrote ${expectedPath}:`);
console.log(fs.readFileSync(expectedPath, 'utf8'));
