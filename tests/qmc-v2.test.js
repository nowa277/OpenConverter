/**
 * QMCv2 (mflac/mgg/bkc) decoder test — verifies key_compress against
 * a cross-checked test vector from ikun0014/pyqmc-rust (MIT), then
 * round-trips encryption on real MP3 audio.
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const { keyCompress, shiftMix, decryptV2Buffer, decodeV2File } = require('../src/decoders/qmc');

const OUT_DIR = path.join(__dirname, 'output', 'qmc-v2');
fs.mkdirSync(OUT_DIR, { recursive: true });

function test() {
  let failed = 0;

  // --- Test 1: shiftMix (shl | shr, NOT a rotation) ---
  console.log('Test 1: shiftMix (shl|shr, not a rotation)...');
  // 0x4E = 01001110. shift=3: shl=01110000, shr=00001001, OR=01111001 = 0x79
  const mixCases = [
    { byte: 0x4e, shift: 3, expected: 0x79 },
    { byte: 0xab, shift: 4, expected: 0xee }, // 10101011 <<4 = 10110000, >>4 = 00001010, OR=10111010=0xba? hmm let me recheck
  ];
  // Recompute 0xab<<4 = 0xab0 & 0xff = 0xb0, 0xab>>4 = 0x0a, OR=0xba
  mixCases[1] = { byte: 0xab, shift: 4, expected: 0xba };
  // 0xff <<3 = 0xf8, >>3 = 0x1f, OR=0xff
  mixCases.push({ byte: 0xff, shift: 3, expected: 0xff });
  mixCases.push({ byte: 0x80, shift: 1, expected: 0xc0 }); // 1000_0000 <<1=0, >>1=0x40, OR=0x40? hmm
  // Actually 0x80<<1 = 0x100 & 0xff = 0x00, 0x80>>1 = 0x40, OR=0x40
  mixCases[3] = { byte: 0x80, shift: 1, expected: 0x40 };
  mixCases.push({ byte: 0x00, shift: 5, expected: 0x00 });
  mixCases.push({ byte: 0x42, shift: 0, expected: 0x42 }); // shift 0 → identity
  let mixOk = true;
  for (const c of mixCases) {
    if (shiftMix(c.byte, c.shift) !== c.expected) {
      mixOk = false;
      console.log(`    FAIL: shiftMix(0x${c.byte.toString(16)}, ${c.shift}) → 0x${shiftMix(c.byte, c.shift).toString(16)}, expected 0x${c.expected.toString(16)}`);
    }
  }
  if (mixOk) console.log('  ✓ PASS'); else failed++;

  // --- Test 2: keyCompress against pyqmc-rust test vector ---
  // Test vector: ekey = 'a-z' + 'A-Z' + '0-9' cycled to 325 bytes.
  // Expected 128-byte output is in ikun0014/pyqmc-rust src/map/key.rs.
  console.log('Test 2: keyCompress against pyqmc-rust test vector...');
  const ekey = [];
  for (let c = 0x61; c <= 0x7a; c++) ekey.push(c); // a-z
  for (let c = 0x41; c <= 0x5a; c++) ekey.push(c); // A-Z
  for (let c = 0x30; c <= 0x39; c++) ekey.push(c); // 0-9
  const ekeyBuf = Buffer.alloc(325);
  for (let i = 0; i < 325; i++) ekeyBuf[i] = ekey[i % ekey.length];
  const expected = Buffer.from([
    0x79, 0xf4, 0x00, 0x75, 0x9e, 0x36, 0x00, 0x14, 0x8a, 0x63, 0x00, 0xb4, 0xbe, 0x77,
    0x00, 0x17, 0xba, 0x00, 0x37, 0x00, 0x00, 0x00, 0xbf, 0x80, 0x41, 0xbf, 0x83, 0xdd,
    0xbc, 0x5c, 0x02, 0x43, 0x14, 0x82, 0x49, 0x02, 0x00, 0x55, 0xbe, 0x6d, 0xbf, 0x49,
    0x80, 0x8e, 0x43, 0x00, 0xfa, 0x41, 0x67, 0xa8, 0x17, 0xf4, 0xae, 0x16, 0x15, 0x00,
    0xc1, 0x37, 0x82, 0xdd, 0x36, 0x21, 0x38, 0x55, 0x00, 0x79, 0x41, 0x9e, 0x42, 0xc1,
    0x36, 0xfa, 0xcf, 0x35, 0x00, 0x00, 0x41, 0xdd, 0x43, 0x42, 0x17, 0x4d, 0x8e, 0x8a,
    0xdd, 0x00, 0xbe, 0xf5, 0x38, 0xb4, 0xbf, 0x00, 0x7a, 0xcc, 0x4d, 0x02, 0x00, 0xcf,
    0xc1, 0xc1, 0x02, 0xa8, 0x00, 0x16, 0xc1, 0xbf, 0xc2, 0x42, 0x00, 0x49, 0x00, 0xc1,
    0xc2, 0xf5, 0x00, 0x17, 0x41, 0xdc, 0x83, 0xc2, 0x00, 0x9e, 0x41, 0xc1, 0x71, 0x36,
    0x00, 0x80,
  ]);
  const actual = keyCompress(ekeyBuf);
  if (actual.equals(expected)) {
    console.log('  ✓ PASS (128/128 bytes match pyqmc-rust test vector)');
  } else {
    failed++;
    for (let i = 0; i < 128; i++) {
      if (actual[i] !== expected[i]) {
        console.log(`    MISMATCH at ${i}: got 0x${actual[i].toString(16)} expected 0x${expected[i].toString(16)}`);
        if (i > 5) { console.log('    (truncating further output)'); break; }
      }
    }
  }

  // --- Test 3: keyCompress with empty ekey throws ---
  console.log('Test 3: keyCompress with empty ekey throws...');
  try {
    keyCompress(Buffer.alloc(0));
    console.log('  FAIL: should have thrown');
    failed++;
  } catch (e) {
    if (e.message.includes('empty')) console.log('  ✓ PASS');
    else { console.log('  FAIL: wrong error:', e.message); failed++; }
  }

  // --- Test 4: decryptV2Buffer with no ekey throws ---
  console.log('Test 4: decryptV2Buffer without ekey throws...');
  try {
    decryptV2Buffer(Buffer.alloc(100), null);
    console.log('  FAIL: should have thrown');
    failed++;
  } catch (e) {
    if (e.message.includes('ekey')) console.log('  ✓ PASS');
    else { console.log('  FAIL: wrong error:', e.message); failed++; }
  }

  // --- Test 5: round-trip on real MP3 with arbitrary ekey ---
  console.log('Test 5: round-trip on real MP3 with arbitrary ekey...');
  const mp3Path = path.join(OUT_DIR, 'sample.mp3');
  if (!fs.existsSync(mp3Path)) {
    try {
      execFileSync('ffmpeg', [
        '-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=2',
        '-ac', '1', '-ar', '22050', '-b:a', '64k', mp3Path,
      ], { stdio: 'pipe' });
    } catch (e) { console.log('  SKIP (ffmpeg not available)'); }
  }
  if (fs.existsSync(mp3Path)) {
    const original = fs.readFileSync(mp3Path);
    // Pick an arbitrary test ekey (base64)
    const testEkey = Buffer.from('Y2lwaGVydGV4dHN0cmluZw==', 'base64').toString('base64'); // "ciphertextstring"
    // Encrypt: byte[i] ^= working_key[i % 128] (with 0x7fff boundary)
    const wkey = keyCompress(Buffer.from(testEkey, 'base64'));
    const cipher = Buffer.alloc(original.length);
    for (let i = 0; i < original.length; i++) {
      const off = i > 0x7fff ? i % 0x7fff : i;
      cipher[i] = original[i] ^ wkey[off % 128];
    }
    const fakePath = path.join(OUT_DIR, 'sample.mflac0');
    fs.writeFileSync(fakePath, cipher);
    // Move original out of the way so decodeV2File doesn't overwrite it
    const backup = path.join(OUT_DIR, 'sample.original.mp3');
    fs.copyFileSync(mp3Path, backup);

    const { outputPath, format } = decodeV2File(fakePath, OUT_DIR, { ekey: testEkey });
    const recovered = fs.readFileSync(outputPath);
    if (format === 'mp3' && recovered.equals(original)) {
      let dur = -1;
      try {
        dur = parseFloat(execFileSync('ffprobe', [
          '-v', 'error', '-show_entries', 'format=duration',
          '-of', 'default=noprint_wrappers=1:nokey=1', outputPath,
        ], { stdio: ['pipe', 'pipe', 'pipe'] }).toString().trim());
      } catch {}
      if (dur > 1.5) console.log(`  ✓ PASS (${original.length} bytes, ffprobe duration=${dur.toFixed(2)}s)`);
      else { console.log(`  FAIL: ffprobe duration=${dur}`); failed++; }
    } else {
      console.log(`  FAIL: format=${format}, recovered equals original=${recovered.equals(original)}`);
      failed++;
    }
  }

  if (failed > 0) {
    console.log(`\n${failed} test(s) failed.`);
    process.exit(1);
  }
  console.log('\nAll QMCv2 tests passed.');
}

test();
