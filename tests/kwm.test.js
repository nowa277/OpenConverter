/**
 * KWM decoder test — self-verifying via round-trip.
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const kwm = require('../src/decoders/kwm');

const OUT_DIR = path.join(__dirname, 'output', 'kwm');
fs.mkdirSync(OUT_DIR, { recursive: true });

function test() {
  let failed = 0;

  // --- Test 1: magic + size validation ---
  console.log('Test 1: magic + size validation...');
  try {
    kwm.decryptBuffer(Buffer.alloc(512)); // too small
    console.log('  FAIL: should have thrown for too-small file');
    failed++;
  } catch (e) {
    if (e.message.includes('too small')) console.log('  ✓ PASS (rejected < 1024 bytes)');
    else { console.log('  FAIL: wrong error:', e.message); failed++; }
  }
  try {
    const badMagic = Buffer.alloc(2048);
    badMagic.write('NOT-Kuwo\x00\x00\x00\x00\x00\x00', 0, 'ascii');
    kwm.decryptBuffer(badMagic);
    console.log('  FAIL: should have thrown for bad magic');
    failed++;
  } catch (e) {
    if (e.message.includes('bad magic')) console.log('  ✓ PASS (rejected bad magic)');
    else { console.log('  FAIL: wrong error:', e.message); failed++; }
  }

  // --- Test 2: buildMask is deterministic and correct ---
  console.log('Test 2: buildMask with known seed...');
  // Seed = 12345 → decimal "12345" → [0x31,0x32,0x33,0x34,0x35,0,0,...,0]
  // Then XOR the entire 32-byte buffer (including zero pad) with ROOT[0..31]
  //   = "MoOtOiTvINGwd2E6n0E1i7L5t2IoOoNk"
  // So mask[i] = seed_bytes[i] ^ ROOT[i] for i in 0..31
  const mask = kwm.buildMask(12345);
  const expected = Buffer.alloc(32);
  for (let i = 0; i < 32; i++) {
    const seedByte = i < 5 ? '12345'.charCodeAt(i) : 0;
    expected[i] = seedByte ^ kwm.ROOT[i];
  }
  if (mask.equals(expected)) {
    console.log('  ✓ PASS');
  } else {
    failed++;
    for (let i = 0; i < 32; i++) {
      if (mask[i] !== expected[i]) console.log(`    MISMATCH at ${i}: got 0x${mask[i].toString(16)} expected 0x${expected[i].toString(16)}`);
    }
  }

  // --- Test 3: round-trip on synthetic plaintext ---
  console.log('Test 3: round-trip on synthetic plaintext...');
  // Build a fake KWM file: header (1024 bytes) + plaintext (encrypted in place)
  const header = Buffer.alloc(1024);
  Buffer.from('yeelion-kuwo').copy(header, 0);
  header.writeUInt32LE(99999, 0x10);
  const plaintext = Buffer.from('ID3' + '\x04'.repeat(7) + 'fake MP3 audio for round-trip test. '.repeat(50), 'utf8');
  const cipher = Buffer.alloc(plaintext.length);
  const m = kwm.buildMask(99999);
  for (let i = 0; i < plaintext.length; i++) {
    cipher[i] = plaintext[i] ^ m[i % 32];
  }
  const fakeKwm = Buffer.concat([header, cipher]);
  const decrypted = kwm.decryptBuffer(fakeKwm);
  if (decrypted.equals(plaintext)) {
    console.log(`  ✓ PASS (${plaintext.length} bytes round-tripped)`);
  } else {
    failed++;
    let firstDiff = -1;
    for (let i = 0; i < plaintext.length; i++) {
      if (decrypted[i] !== plaintext[i]) { firstDiff = i; break; }
    }
    console.log(`  FAIL: first diff at byte ${firstDiff}`);
  }

  // --- Test 4: format detection ---
  console.log('Test 4: format detection...');
  const cases = [
    { audio: Buffer.from('ID3' + '\x00'.repeat(20)), expected: 'mp3' },
    { audio: Buffer.from('fLaC' + '\x00'.repeat(20)), expected: 'flac' },
    { audio: Buffer.from('OggS' + '\x00'.repeat(20)), expected: 'ogg' },
    { audio: Buffer.from('RIFF' + '\x00'.repeat(20)), expected: 'wav' },
    { audio: Buffer.from([0xff, 0xfb, 0x90, 0x44]), expected: 'mp3' },
    { audio: Buffer.concat([Buffer.alloc(4), Buffer.from('ftypM4A ')]), expected: 'm4a' },
  ];
  let fmtOk = true;
  for (const c of cases) {
    const got = kwm.inferFormat(c.audio);
    if (got !== c.expected) { fmtOk = false; console.log(`    FAIL: ${c.audio.slice(0, 8).toString('hex')} → got ${got}, expected ${c.expected}`); }
  }
  if (fmtOk) console.log('  ✓ PASS (6/6 cases)');
  else failed++;

  // --- Test 5: round-trip on real MP3 via ffmpeg + decodeFile ---
  console.log('Test 5: round-trip on real MP3...');
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
    const fakeHdr = Buffer.alloc(1024);
    Buffer.from('yeelion-kuwo').copy(fakeHdr, 0);
    fakeHdr.writeUInt32LE(42, 0x10);
    const m42 = kwm.buildMask(42);
    const cipher = Buffer.alloc(original.length);
    for (let i = 0; i < original.length; i++) {
      cipher[i] = original[i] ^ m42[i % 32];
    }
    const fakePath = path.join(OUT_DIR, 'sample.kwm');
    fs.writeFileSync(fakePath, Buffer.concat([fakeHdr, cipher]));

    const { outputPath, format } = kwm.decodeFile(fakePath, OUT_DIR);
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
      else { console.log(`  FAIL: ffprobe reported duration=${dur}`); failed++; }
    } else {
      console.log(`  FAIL: format=${format}, recovered equals original=${recovered.equals(original)}`);
      failed++;
    }
  }

  if (failed > 0) {
    console.log(`\n${failed} test(s) failed.`);
    process.exit(1);
  }
  console.log('\nAll KWM tests passed.');
}

test();
