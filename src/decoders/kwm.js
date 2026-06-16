/**
 * KWM decoder (Kuwo Music, 酷我) — pure JS, no native deps.
 *
 * Algorithm: 32-byte circular XOR cipher. Re-implemented from scratch
 * based on davidxuang/MusicDecrypto (LGPL-2.1, do not copy).
 *
 * File layout:
 *   0x000..0x00A  Magic "yeelion-kuwo" (10 bytes)
 *   0x00A..0x010  6 bytes padding
 *   0x010..0x014  4-byte uint32 LE seed
 *   0x014..0x400  Reserved / padding
 *   0x400..end   Encrypted audio (XOR with derived 32-byte mask)
 *
 * Decryption:
 *   1. seed_decimal = uint32_to_decimal_string(seed)
 *   2. mask[0..len(seed_decimal)] = ASCII bytes of seed_decimal
 *   3. mask[len..32] = 0
 *   4. for i in 0..32: mask[i] ^= ROOT[i]
 *   5. for i in 0..audio_len: audio[i] ^= mask[(i + audio_offset) % 32]
 */
const fs = require('node:fs');
const path = require('node:path');

const MAGIC = Buffer.from('yeelion-kuwo', 'ascii');
const MAGIC_LEN = MAGIC.length; // 10
const ROOT = Buffer.from('MoOtOiTvINGwd2E6n0E1i7L5t2IoOoNk', 'ascii'); // 32 bytes
const MASK_SIZE = 32;
const AUDIO_OFFSET = 0x400; // 1024
const SEED_OFFSET = 0x10; // 16

/**
 * Build the 32-byte XOR mask from a seed uint32.
 */
function buildMask(seed) {
  const decimal = seed.toString(10);
  const mask = Buffer.alloc(MASK_SIZE);
  const len = Math.min(decimal.length, MASK_SIZE);
  for (let i = 0; i < len; i++) {
    mask[i] = decimal.charCodeAt(i);
  }
  // mask[len..32] is already 0 from Buffer.alloc
  for (let i = 0; i < MASK_SIZE; i++) {
    mask[i] ^= ROOT[i];
  }
  return mask;
}

/**
 * Decrypt a KWM file in-place. Returns { audio, format }.
 * Pure function — does not touch the filesystem.
 */
function decryptBuffer(kwmBuf) {
  if (kwmBuf.length < AUDIO_OFFSET) {
    throw new Error(`KWM: file too small (${kwmBuf.length} < ${AUDIO_OFFSET})`);
  }
  if (!kwmBuf.slice(0, MAGIC_LEN).equals(MAGIC)) {
    throw new Error('KWM: bad magic, expected "yeelion-kuwo" at offset 0');
  }
  const seed = kwmBuf.readUInt32LE(SEED_OFFSET);
  const mask = buildMask(seed);

  const audio = Buffer.from(kwmBuf.slice(AUDIO_OFFSET));
  for (let i = 0; i < audio.length; i++) {
    audio[i] ^= mask[i % MASK_SIZE];
  }
  return audio;
}

/**
 * Detect audio format by sniffing first 4 bytes of decrypted audio.
 * Kuwo uses: MP3 ("ID3" or 0xFF 0xFB/0xFA/0xF3/0xF2), FLAC ("fLaC"),
 * M4A ("ftyp" at offset 4), OGG ("OggS"), WAV ("RIFF").
 */
function inferFormat(audio) {
  if (audio.length < 4) return 'mp3';
  if (audio[0] === 0x49 && audio[1] === 0x44 && audio[2] === 0x33) return 'mp3'; // "ID3"
  if (audio[0] === 0x66 && audio[1] === 0x4c && audio[2] === 0x61 && audio[3] === 0x43) return 'flac'; // "fLaC"
  if (audio[0] === 0x4f && audio[1] === 0x67 && audio[2] === 0x67 && audio[3] === 0x53) return 'ogg'; // "OggS"
  if (audio[0] === 0x52 && audio[1] === 0x49 && audio[2] === 0x46 && audio[3] === 0x46) return 'wav'; // "RIFF"
  if (audio[0] === 0xff && (audio[1] & 0xe0) === 0xe0) return 'mp3'; // MP3 frame sync
  if (audio.length >= 8 && audio[4] === 0x66 && audio[5] === 0x74 && audio[6] === 0x79 && audio[7] === 0x70) return 'm4a'; // "ftyp"
  return 'mp3'; // default
}

function decodeFile(inputPath, outputDir) {
  const input = fs.readFileSync(inputPath);
  const audio = decryptBuffer(input);
  const format = inferFormat(audio);

  const base = inputPath.replace(/\.kwm$/i, '');
  const name = base.split(/[\\/]/).pop();
  const outName = `${name}.${format}`;
  const outPath = outputDir ? path.join(outputDir, outName) : `${base}.${format}`;
  fs.mkdirSync(outputDir || '.', { recursive: true });
  fs.writeFileSync(outPath, audio);
  return { outputPath: outPath, format };
}

module.exports = {
  decryptBuffer,
  decodeFile,
  buildMask,
  inferFormat,
  MAGIC,
  ROOT,
  AUDIO_OFFSET,
  SEED_OFFSET,
};
