'use strict';
const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {
  XOR_KEY,
  restoreUcBuffer,
  sniffUcFormat,
  parseUcFilename,
  restoreUcFile,
} = require('../src/decoders/uc');

test('restoreUcBuffer correctly inverts 0xA3 bytes', () => {
  const plain = Buffer.from('fLaC_test_header_bytes_12345');
  const uc = Buffer.alloc(plain.length);
  for (let i = 0; i < plain.length; i++) {
    uc[i] = plain[i] ^ XOR_KEY;
  }
  const restored = restoreUcBuffer(uc);
  assert.deepStrictEqual(restored, plain);
});

test('sniffUcFormat identifies flac and mp3 headers', () => {
  assert.strictEqual(sniffUcFormat(Buffer.from([0x66, 0x4c, 0x61, 0x43])), 'flac');
  assert.strictEqual(sniffUcFormat(Buffer.from([0x49, 0x44, 0x33, 0x03])), 'mp3');
  assert.strictEqual(sniffUcFormat(Buffer.from([0xff, 0xfb, 0x90, 0x64])), 'mp3');
  assert.strictEqual(sniffUcFormat(Buffer.from([0x52, 0x49, 0x46, 0x46])), 'wav');
});

test('parseUcFilename extracts songId, bitrate and hash', () => {
  const parsed = parseUcFilename('491513022-128-558a62ab3da3e7024550eae011ec11da.uc');
  assert.strictEqual(parsed.songId, '491513022');
  assert.strictEqual(parsed.bitrate, '128');
  assert.strictEqual(parsed.hash, '558a62ab3da3e7024550eae011ec11da');

  const fallback = parseUcFilename('123456.uc');
  assert.strictEqual(fallback.songId, '123456');
});

test('restoreUcFile restores encrypted file and detects short preview', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'oc-uc-test-'));
  try {
    const plainHeader = Buffer.from('fLaC_dummy_audio_content_test_bytes');
    const ucBytes = Buffer.alloc(plainHeader.length);
    for (let i = 0; i < plainHeader.length; i++) {
      ucBytes[i] = plainHeader[i] ^ XOR_KEY;
    }
    const testFile = path.join(tmpDir, '12345678-320-abcdef1234567890abcdef1234567890.uc');
    fs.writeFileSync(testFile, ucBytes);

    const out = restoreUcFile(testFile, tmpDir);
    assert.strictEqual(out.format, 'flac');
    assert.strictEqual(out.songId, '12345678');
    assert.ok(out.note.includes('VIP 试听缓存片段'));
    assert.ok(fs.existsSync(out.outputPath));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('restoreUcFile rejects .uc! partial files', () => {
  assert.throws(() => restoreUcFile('test.uc!'), /\.uc!/);
});

// ---- routing integration (registered in decoders/index.js) ----

test('.uc is routed through the decoder registry', () => {
  const decoders = require('../src/decoders/index');
  const ucRaw = require('../src/decoders/uc');

  const routed = decoders.pickDecoder('song/1786453-320-abc.uc');
  assert.strictEqual(routed, decoders.uc); // router exports the same adapter
  assert.strictEqual(routed.decodeFile, ucRaw.restoreUcFile); // contract fulfilled
  assert.strictEqual(decoders.pickDecoder('song/tune.UC'), decoders.uc); // case-insensitive

  const meta = decoders.getMetadata('.uc');
  assert.ok(meta, 'EXT_METADATA entry for .uc');
  assert.strictEqual(meta.platform, '网易云');
  assert.strictEqual(meta.requiresKey, false);

  assert.ok(decoders.listImplemented().includes('.uc'));
  assert.ok(decoders.listByKeyRequirement().withoutKey.includes('.uc'));
});
