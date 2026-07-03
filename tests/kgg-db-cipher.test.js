'use strict';

const { test } = require('node:test');
const assert = require('node:assert');
const { Readable } = require('node:stream');

const {
  PAGE_SIZE,
  MASTER_KEY,
  pageKey,
  pageIv,
  isPlaintextHeader,
  isEncryptedHeader,
  decryptStream,
} = require('../src/decoders/kgg/db-cipher');

const SQLITE_HEADER = Buffer.from('SQLite format 3\0', 'ascii');

test('PAGE_SIZE is 1024', () => {
  assert.strictEqual(PAGE_SIZE, 1024);
});

test('MASTER_KEY is 16 bytes', () => {
  assert.strictEqual(MASTER_KEY.length, 16);
});

test('pageKey is deterministic for same input', () => {
  const k1 = pageKey(MASTER_KEY, 1);
  const k2 = pageKey(MASTER_KEY, 1);
  assert.deepStrictEqual(k1, k2);
  assert.strictEqual(k1.length, 16, 'page key must be 128-bit for AES-128');
});

test('pageKey differs for different page numbers', () => {
  const k1 = pageKey(MASTER_KEY, 1);
  const k2 = pageKey(MASTER_KEY, 2);
  assert.notDeepStrictEqual(k1, k2);
});

test('pageIv is deterministic and 16 bytes', () => {
  const iv1 = pageIv(1);
  const iv2 = pageIv(1);
  assert.deepStrictEqual(iv1, iv2);
  assert.strictEqual(iv1.length, 16);
});

test('pageIv differs for different page numbers', () => {
  assert.notDeepStrictEqual(pageIv(1), pageIv(2));
});

test('isPlaintextHeader detects SQLite header', () => {
  const page = Buffer.alloc(PAGE_SIZE);
  Buffer.from('SQLite format 3\0', 'ascii').copy(page);
  assert.ok(isPlaintextHeader(page));
});

test('isPlaintextHeader rejects non-SQLite page', () => {
  const page = Buffer.alloc(PAGE_SIZE, 0xff);
  assert.ok(!isPlaintextHeader(page));
});

test('isEncryptedHeader accepts a page with the KuGou magic 0x20204000', () => {
  const page = Buffer.alloc(PAGE_SIZE, 0);
  // SQLite page size is at offset 16 (BE uint16, here 0x0400 = 1024).
  // The Kotlin code reads offset 16..19 as a LE int: 0x00000004
  // then reconstructs pageSize as (lowByte << 8) | (nextByte << 16).
  // For 1024 that gives 0x04 << 8 = 0x400.
  page[16] = 0x04; // SQLite pageSize low byte
  page[17] = 0x00; // SQLite pageSize high byte
  // Magic 0x20204000 sits at offset 20 (LE uint32) per the Kotlin source.
  page.writeUInt32LE(0x20204000, 20);
  assert.ok(isEncryptedHeader(page));
});

test('isEncryptedHeader rejects random page', () => {
  const page = Buffer.alloc(PAGE_SIZE, 0xab);
  assert.ok(!isEncryptedHeader(page));
});

test('decryptStream handles plaintext SQLite DB (passes through)', async () => {
  // Single plaintext page is enough to validate the pass-through path.
  const page = Buffer.alloc(PAGE_SIZE);
  Buffer.from('SQLite format 3\0', 'ascii').copy(page, 0);
  page.fill(0xcd, SQLITE_HEADER.length, PAGE_SIZE);
  const input = Readable.from([page]);
  const chunks = [];
  const output = new (require('node:stream').Writable)({
    write(chunk, _enc, cb) { chunks.push(chunk); cb(); },
  });
  await decryptStream(input, output);
  const out = Buffer.concat(chunks);
  assert.strictEqual(out.length, PAGE_SIZE);
  assert.deepStrictEqual(out.subarray(0, SQLITE_HEADER.length), SQLITE_HEADER);
});

test('decryptStream rejects empty input', async () => {
  const input = Readable.from([]);
  const output = new (require('node:stream').Writable)({
    write(_c, _e, cb) { cb(); },
  });
  await assert.rejects(
    decryptStream(input, output),
    /ended before first page/,
  );
});

test('decryptStream rejects truncated input', async () => {
  const input = Readable.from([Buffer.alloc(100)]); // less than PAGE_SIZE
  const output = new (require('node:stream').Writable)({
    write(_c, _e, cb) { cb(); },
  });
  await assert.rejects(
    decryptStream(input, output),
    /ended before first page|truncated/,
  );
});
