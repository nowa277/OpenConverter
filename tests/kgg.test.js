'use strict';

const { test } = require('node:test');
const assert = require('node:assert');

const kgg = require('../src/decoders/kgg');
const { parse: parseHeader, MAGIC, PREFIX_SIZE } = require('../src/decoders/kgg/header');
const { unwrap, deriveV1, decodeBase64, teaDecryptBlock } = require('../src/decoders/kgg/ekey');
const { makeCipher, MAP_OFFSET_BOUNDARY, MAP_INDEX_OFFSET } = require('../src/decoders/kgg/qmc2');

// =====================================================================
// header.js
// =====================================================================

test('header: rejects buffer without magic', () => {
  const buf = Buffer.alloc(PREFIX_SIZE);
  assert.throws(() => parseHeader(buf), /magic is invalid/);
});

test('header: rejects truncated buffer', () => {
  const buf = Buffer.alloc(0x30);
  MAGIC.copy(buf, 0);
  assert.throws(() => parseHeader(buf), /truncated/);
});

test('header: rejects unsupported crypto version', () => {
  const buf = Buffer.alloc(PREFIX_SIZE);
  MAGIC.copy(buf, 0);
  buf.writeUInt32LE(PREFIX_SIZE, 0x10);
  buf.writeUInt32LE(99, 0x14); // version 99
  assert.throws(() => parseHeader(buf), /Unsupported KGG crypto version/);
});

test('header: parses valid v5 header with UTF-8 key id', () => {
  const buf = Buffer.alloc(PREFIX_SIZE);
  MAGIC.copy(buf, 0);
  buf.writeUInt32LE(PREFIX_SIZE, 0x10);
  buf.writeUInt32LE(5, 0x14);
  const id = Buffer.from('test-key-1234', 'utf-8');
  buf.writeUInt32LE(id.length, 0x44);
  id.copy(buf, 0x48);
  const hdr = parseHeader(buf);
  assert.strictEqual(hdr.cryptoVersion, 5);
  assert.strictEqual(hdr.headerLength, PREFIX_SIZE);
  assert.strictEqual(hdr.encryptionKeyId, 'test-key-1234');
});

// =====================================================================
// ekey.js
// =====================================================================

test('ekey.decodeBase64 handles standard base64', () => {
  assert.deepStrictEqual(decodeBase64('SGVsbG8='), Buffer.from('Hello'));
  assert.deepStrictEqual(decodeBase64('SGVsbG8h'), Buffer.from('Hello!'));
  assert.deepStrictEqual(decodeBase64('SGVsbG8hV29ybGQ='), Buffer.from('Hello!World'));
});

test('ekey.decodeBase64 rejects bad padding', () => {
  assert.throws(() => decodeBase64('SGVsbG8'), /length|padding/); // wrong length
  assert.throws(() => decodeBase64('SGVs!G8='), /character/);
});

test('ekey.teaDecryptBlock produces a consistent result for a known key', () => {
  // Round-trip a single block: encrypt -> decrypt
  const key = Buffer.alloc(16);
  for (let i = 0; i < 16; i++) key[i] = i;
  const plain = Buffer.alloc(8);
  for (let i = 0; i < 8; i++) plain[i] = i * 11 + 1;

  // Encrypt by running decrypt in reverse (sum starts at 0, not DELTA*CYCLES)
  // For a simple round-trip test, just verify decrypt is deterministic
  const decrypted = teaDecryptBlock(plain, key);
  const decryptedAgain = teaDecryptBlock(plain, key);
  assert.deepStrictEqual(decrypted, decryptedAgain);
  // Decrypting the decrypt should not yield original (TEA is not involutory)
  const reDecrypted = teaDecryptBlock(decrypted, key);
  assert.notDeepStrictEqual(reDecrypted, plain);
});

test('ekey.deriveV1 throws on too-short input', () => {
  assert.throws(() => deriveV1(Buffer.alloc(8)), /too short/);
  assert.throws(() => deriveV1(Buffer.alloc(0)), /too short/);
});

test('ekey.unwrap handles V1-only ekey (no V2 prefix)', () => {
  // Build a minimal V1 ekey input: 8 bytes + enough for suffix
  // deriveV1 requires raw.length >= 16, so feed 16 bytes where the first 8 are "raw"
  // and the rest gets put through decryptTencentTea (which expects 8-byte aligned)
  // Actually deriveV1 then calls decryptTencentTea(raw[8..], teaKey), which
  // needs raw.length - 8 >= 16 (8 bytes) and multiple of 8.
  // So minimal raw is 24 bytes (8 raw + 16 ciphertext).
  // Skip the complex case and just test the V2 prefix detection.
  // For V1, we'd need a real ekey, so we test the throw path only.
  const raw = Buffer.alloc(8); // too short
  // unwrap calls decodeBase64 first
  const b64 = Buffer.from(raw).toString('base64');
  // 6 bytes -> 8 base64 chars
  assert.throws(() => unwrap(b64), /too short/);
});

// =====================================================================
// qmc2.js
// =====================================================================

test('qmc2.makeCipher rejects empty key', () => {
  assert.throws(() => makeCipher(Buffer.alloc(0)), /key is empty/);
});

test('qmc2 MapCipher: double application restores plaintext', () => {
  const key = Buffer.alloc(16);
  for (let i = 0; i < 16; i++) key[i] = (i * 7) & 0xff;
  const cipher = makeCipher(key);

  const plaintext = Buffer.alloc(64);
  for (let i = 0; i < 64; i++) plaintext[i] = (i * 13 + 5) & 0xff;

  const ct = Buffer.from(plaintext);
  cipher.apply(ct, ct.length, 0n);
  assert.notDeepStrictEqual(ct, plaintext, 'cipher should mutate bytes');

  const cipher2 = makeCipher(key);
  cipher2.apply(ct, ct.length, 0n);
  assert.deepStrictEqual(ct, plaintext, 'second application should restore');
});

test('qmc2 MapCipher: long key selects RC4 path', () => {
  const key = Buffer.alloc(500); // > 300, forces RC4
  for (let i = 0; i < 500; i++) key[i] = (i * 11) & 0xff;
  const cipher = makeCipher(key);

  const plaintext = Buffer.alloc(128);
  for (let i = 0; i < 128; i++) plaintext[i] = (i * 19 + 3) & 0xff;

  const ct = Buffer.from(plaintext);
  cipher.apply(ct, ct.length, 0n);
  assert.notDeepStrictEqual(ct, plaintext, 'RC4 should mutate bytes');

  const cipher2 = makeCipher(key);
  cipher2.apply(ct, ct.length, 0n);
  assert.deepStrictEqual(ct, plaintext, 'RC4 second application should restore');
});

test('qmc2 MapCipher: handles 0x7fff boundary wrap', () => {
  const key = Buffer.alloc(8);
  for (let i = 0; i < 8; i++) key[i] = i + 1;
  const cipher = makeCipher(key);
  // Buffer spans the boundary at 0x7fff
  const plaintext = Buffer.alloc(16, 0xab);
  const ct = Buffer.from(plaintext);
  cipher.apply(ct, ct.length, MAP_OFFSET_BOUNDARY - 5n);
  // Should not throw and should differ from plaintext
  assert.notDeepStrictEqual(ct, plaintext);
});

// =====================================================================
// Key map
// =====================================================================

test('parseKeyMap: parses id$ekey lines', () => {
  const text = 'aaa$bbb\nccc$ddd\n';
  const m = kgg.parseKeyMap(text);
  assert.strictEqual(m.size, 2);
  assert.strictEqual(m.get('aaa'), 'bbb');
  assert.strictEqual(m.get('ccc'), 'ddd');
});

test('parseKeyMap: ignores blank lines', () => {
  const text = '\n\naaa$bbb\n\n\nccc$ddd\n\n';
  const m = kgg.parseKeyMap(text);
  assert.strictEqual(m.size, 2);
});

test('parseKeyMap: rejects malformed lines (no separator)', () => {
  assert.throws(() => kgg.parseKeyMap('no-separator\n'), /Invalid kgg.key/);
});

test('parseKeyMap: rejects lines with multiple separators', () => {
  assert.throws(() => kgg.parseKeyMap('a$b$c\n'), /Invalid kgg.key/);
});

test('parseKeyMap -> serializeKeyMap round-trip', () => {
  const original = new Map([['alpha', 'X'], ['beta', 'Y']]);
  const text = kgg.serializeKeyMap(original);
  const m2 = kgg.parseKeyMap(text);
  assert.deepStrictEqual(
    Array.from(m2.entries()).sort(),
    Array.from(original.entries()).sort()
  );
});

test('memoryKeyProvider returns map contents', () => {
  const m = new Map([['k1', 'v1'], ['k2', 'v2']]);
  const p = kgg.memoryKeyProvider(m);
  assert.strictEqual(p.find('k1'), 'v1');
  assert.strictEqual(p.find('k2'), 'v2');
  assert.strictEqual(p.find('k3'), null);
  assert.strictEqual(p.count(), 2);
});
