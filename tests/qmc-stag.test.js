'use strict';

const { test } = require('node:test');
const assert = require('node:assert');
const {
  detectKey,
  decryptV2Buffer,
  keyCompress,
  EXT_MAP_V2,
} = require('../src/decoders/qmc');

// Build a synthetic STag-headed buffer:
//   0x00..0x04  "STag"
//   0x14..0x18  uint32 LE: ekey byte length
//   0x18..N     ekey bytes
//   N..end      encrypted audio
function makeStagBuffer(ekey, audioSize = 32) {
  const buf = Buffer.alloc(0x18 + ekey.length + audioSize);
  buf.write('STag', 0, 4, 'ascii');
  buf.writeUInt32LE(ekey.length, 0x14);
  buf.write(ekey, 0x18, ekey.length, 'ascii');
  buf.fill(0xab, 0x18 + ekey.length);
  return buf;
}

test('detectKey recognises STag head and returns ekey + audioLen', () => {
  const ekey = 'AAAAAAAAAAAAAAAA'; // 16 base64 chars
  const buf = makeStagBuffer(ekey);
  const result = detectKey(buf);
  assert.ok(result, 'should detect STag');
  assert.strictEqual(result.ekey, ekey);
  assert.strictEqual(result.audioLen, 0x18 + ekey.length);
});

test('detectKey honours shorter ekey (8 chars)', () => {
  const ekey = 'AAAAAAAA';
  const buf = makeStagBuffer(ekey);
  const result = detectKey(buf);
  assert.ok(result);
  assert.strictEqual(result.ekey, ekey);
  assert.strictEqual(result.audioLen, 0x18 + 8);
});

test('detectKey rejects STag with implausibly large ekey length', () => {
  const buf = Buffer.alloc(64);
  buf.write('STag', 0, 4, 'ascii');
  buf.writeUInt32LE(0xFFFFFF, 0x14); // absurd
  assert.strictEqual(detectKey(buf), null);
});

test('detectKey rejects STag with non-base64 ekey bytes', () => {
  const buf = Buffer.alloc(64);
  buf.write('STag', 0, 4, 'ascii');
  buf.writeUInt32LE(8, 0x14);
  buf.write('!@#\$%^&*', 0x18, 8, 'ascii');
  assert.strictEqual(detectKey(buf), null);
});

test('detectKey returns null on too-short buffer', () => {
  assert.strictEqual(detectKey(Buffer.alloc(0)), null);
  assert.strictEqual(detectKey(Buffer.from('STag', 'ascii')), null);
  assert.strictEqual(detectKey(Buffer.alloc(0x10)), null);
});

test('detectKey falls through to QTag tail when no STag head', () => {
  const meta = 'ekey,extra';
  const metaLen = Buffer.byteLength(meta, 'utf-8');
  const total = 8 + metaLen + 16;
  const qbuf = Buffer.alloc(total);
  qbuf.writeUInt32BE(metaLen, total - 8);
  qbuf.write('QTag', total - 4, 4, 'ascii');
  qbuf.write(meta, total - 8 - metaLen, metaLen, 'utf-8');
  const result = detectKey(qbuf);
  assert.ok(result);
  assert.strictEqual(result.ekey, 'ekey');
});

test('EXT_MAP_V2 covers STag variants', () => {
  for (const ext of ['.mflac2', '.mflac4', '.mgg2', '.mgg4', '.mggl']) {
    assert.ok(EXT_MAP_V2[ext], `EXT_MAP_V2 should include ${ext}`);
  }
});

// =====================================================================
// End-to-end: STag detect + V2 decrypt round-trip
// =====================================================================

test('QMC v2 STag: full detect + decrypt chain with synthetic data', () => {
  // 1. Pick a 16-byte raw ekey (the algorithm accepts arbitrary bytes)
  const RAW_EKEY = Buffer.alloc(16);
  for (let i = 0; i < 16; i++) RAW_EKEY[i] = (i * 23 + 7) & 0xff;

  // 2. Encrypt a known plaintext with the V2 cipher
  const PLAINTEXT = Buffer.from('abcdefghijklmnopqrstuvwxyz012345'); // 32 bytes
  const derivedKey = keyCompress(RAW_EKEY);
  const ciphertext = Buffer.alloc(PLAINTEXT.length);
  for (let i = 0; i < PLAINTEXT.length; i++) {
    const o = i > 0x7fff ? i % 0x7fff : i;
    ciphertext[i] = PLAINTEXT[i] ^ derivedKey[o % 128];
  }

  // 3. Build a STag-headed mflac2 file with the ekey embedded
  const ekeyB64 = RAW_EKEY.toString('base64');
  const totalSize = 0x18 + ekeyB64.length + ciphertext.length;
  const file = Buffer.alloc(totalSize);
  file.write('STag', 0, 4, 'ascii');
  file.writeUInt32LE(ekeyB64.length, 0x14);
  file.write(ekeyB64, 0x18, ekeyB64.length, 'ascii');
  ciphertext.copy(file, 0x18 + ekeyB64.length);

  // 4. Detect + decrypt
  const detected = detectKey(file);
  assert.ok(detected, 'STag detection must succeed');
  assert.strictEqual(detected.ekey, ekeyB64, 'ekey should round-trip');
  assert.strictEqual(
    detected.audioLen,
    0x18 + ekeyB64.length,
    'audioLen should start right after the STag header',
  );

  const audio = decryptV2Buffer(file.subarray(detected.audioLen), detected.ekey);
  assert.deepStrictEqual(audio, PLAINTEXT, 'decrypted audio must match original');
});

test('QMC v2 STag: handle small ekey (8-byte raw) by key_compress wrap-around', () => {
  // A short ekey (8 bytes) is legal — keyCompress just wraps the index.
  const SHORT_EKEY = Buffer.from('ABCDEFGH', 'ascii');
  const derivedKey = keyCompress(SHORT_EKEY);
  assert.strictEqual(derivedKey.length, 128, 'keyCompress always returns 128 bytes');

  const PLAINTEXT = Buffer.alloc(64, 0x42);
  const ciphertext = Buffer.alloc(PLAINTEXT.length);
  for (let i = 0; i < PLAINTEXT.length; i++) {
    const o = i > 0x7fff ? i % 0x7fff : i;
    ciphertext[i] = PLAINTEXT[i] ^ derivedKey[o % 128];
  }
  const ekeyB64 = SHORT_EKEY.toString('base64');

  const file = Buffer.alloc(0x18 + ekeyB64.length + ciphertext.length);
  file.write('STag', 0, 4, 'ascii');
  file.writeUInt32LE(ekeyB64.length, 0x14);
  file.write(ekeyB64, 0x18, ekeyB64.length, 'ascii');
  ciphertext.copy(file, 0x18 + ekeyB64.length);

  const detected = detectKey(file);
  assert.ok(detected);
  const audio = decryptV2Buffer(file.subarray(detected.audioLen), detected.ekey);
  assert.deepStrictEqual(audio, PLAINTEXT);
});
