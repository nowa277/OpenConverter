/**
 * QMC decoder (QQ Music cache) — pure JS, no native deps.
 *
 * Supports two cipher variants:
 *
 * 1. QMCv1 (headerless) — for .qmc0, .qmc3, .qmcflac, .qmcogg
 *    The whole file is XOR'd with a 64-byte keystream produced by an 8x7
 *    state machine (with a 0x8000-position skip). No per-file key needed.
 *
 * 2. QMCv2 (mobile/desktop QQ Music) — for .mflac0, .mgg1, .bkc*
 *    Same XOR cipher as v1 but the 128-byte working key is derived from a
 *    user-provided ekey (base64 string from QQ Music client DB) via
 *    key_compress(ekey). The ekey must be supplied by the caller — we
 *    cannot extract it from the file or from a running client.
 *
 * Algorithm references:
 *   - QMCv1: presburger/qmc-decoder (C++ state machine, MIT)
 *            bczhc/qmc-decrypt     (Rust, MIT/Apache)
 *   - QMCv2 cipher: bczhc/qmc-decrypt (qmcflac.rs + qmc2_crypto crate)
 *   - key_compress:  ikun0014/pyqmc-rust (MIT, with cross-checked test vector)
 *
 * QMCv2 (QMCv2 with "QMCv2" / "STag" / "QTag" markers): NOT supported —
 * requires per-file ekey from the QQ Music client.
 */
const fs = require('node:fs');
const path = require('node:path');

// =====================================================================
// QMCv1: 8x7 state machine, no key
// =====================================================================

const SEED_MAP = [
  [0x4a, 0xd6, 0xca, 0x90, 0x67, 0xf7, 0x52], // y=0
  [0x5e, 0x95, 0x23, 0x9f, 0x13, 0x11, 0x7e], // y=1
  [0x47, 0x74, 0x3d, 0x90, 0xaa, 0x3f, 0x51], // y=2
  [0xc6, 0x09, 0xd5, 0x9f, 0xfa, 0x66, 0xf9], // y=3
  [0xf3, 0xd6, 0xa1, 0x90, 0xa0, 0xf7, 0xf0], // y=4
  [0x1d, 0x95, 0xde, 0x9f, 0x84, 0x11, 0xf4], // y=5
  [0x0e, 0x74, 0xbb, 0x90, 0xbc, 0x3f, 0x92], // y=6
  [0x00, 0x09, 0x5b, 0x9f, 0x62, 0x66, 0xa1], // y=7
];

const V1_OFFSET_BOUNDARY = 0x7fff;

class QmcSeed {
  constructor() {
    this.x = -1;
    this.y = 8;
    this.dx = 1;
    this.index = -1;
  }
  nextMask() {
    let ret;
    this.index++;
    if (this.x < 0) {
      this.dx = 1;
      this.y = (8 - this.y) % 8;
      ret = 0xc3;
    } else if (this.x > 6) {
      this.dx = -1;
      this.y = 7 - this.y;
      ret = 0xd8;
    } else {
      ret = SEED_MAP[this.y][this.x];
    }
    this.x += this.dx;
    if (this.index === 0x8000 || (this.index > 0x8000 && (this.index + 1) % 0x8000 === 0)) {
      return this.nextMask();
    }
    return ret;
  }
}

// =====================================================================
// QMCv2: 128-byte key derived from ekey via key_compress
// =====================================================================

const V2_KEY_SIZE = 128;
const KEY_COMPRESS_INDEX_OFFSET = 71214;

/**
 * shiftMix: (byte << shift) | (byte >> shift) — not a bit rotation.
 * Reference: ikun0014/pyqmc-rust src/map/key.rs uses exactly this
 * operation with shift = (idx + 4) % 8.
 */
function shiftMix(byte, shift) {
  shift &= 7;
  if (shift === 0) return byte;
  return ((byte << shift) | (byte >> shift)) & 0xff;
}

/**
 * key_compress: take an arbitrary-length ekey, return a 128-byte working
 * key. Reference: ikun0014/pyqmc-rust src/map/key.rs (MIT).
 */
function keyCompress(ekey) {
  if (!ekey || ekey.length === 0) {
    throw new Error('QMCv2: ekey is empty');
  }
  const n = ekey.length;
  const out = Buffer.alloc(V2_KEY_SIZE);
  for (let i = 0; i < V2_KEY_SIZE; i++) {
    const idx = (i * i + KEY_COMPRESS_INDEX_OFFSET) % n;
    const shift = (idx + 4) % 8;
    out[i] = shiftMix(ekey[idx], shift);
  }
  return out;
}

function qmc1Transform(key, value, offset) {
  const o = offset > V1_OFFSET_BOUNDARY ? offset % V1_OFFSET_BOUNDARY : offset;
  return value ^ key[o % V2_KEY_SIZE];
}

// =====================================================================
// Decryption entry points
// =====================================================================

const EXT_MAP_V1 = {
  '.qmc0': 'mp3',
  '.qmc3': 'mp3',
  '.qmcflac': 'flac',
  '.qmcogg': 'ogg',
};

const EXT_MAP_V2 = {
  '.mflac0': 'flac',
  '.mflac': 'flac',
  '.mgg1': 'ogg',
  '.mgg': 'ogg',
  '.bkc': 'mp3', // BKC variant — extension varies, sniffed
};

/**
 * Decrypt a QMCv1 file (.qmc0, .qmc3, .qmcflac, .qmcogg). No key needed.
 */
function decryptV1Buffer(qmcBuf) {
  const seed = new QmcSeed();
  const out = Buffer.allocUnsafe(qmcBuf.length);
  for (let i = 0; i < qmcBuf.length; i++) {
    out[i] = qmcBuf[i] ^ seed.nextMask();
  }
  return out;
}

/**
 * Decrypt a QMCv2 file (.mflac0, .mgg1, .bkc*). ekey is a base64 string
 * from the QQ Music client DB; key_compress(ekey) produces the 128-byte
 * working key.
 */
function decryptV2Buffer(qmcBuf, ekeyB64) {
  if (!ekeyB64) {
    throw new Error(
      'QMCv2 (.mflac/.mgg/.bkc) requires an ekey string from the QQ Music client. ' +
      'Set the ekey in Settings (or pass it as the ekey option to the CLI).'
    );
  }
  const ekey = Buffer.from(ekeyB64, 'base64');
  if (ekey.length === 0) {
    throw new Error('QMCv2: ekey base64 decoded to 0 bytes');
  }
  const key = keyCompress(ekey);
  const out = Buffer.allocUnsafe(qmcBuf.length);
  for (let i = 0; i < qmcBuf.length; i++) {
    out[i] = qmc1Transform(key, qmcBuf[i], i);
  }
  return out;
}

// =====================================================================
// File-level API
// =====================================================================

function inferFormat(audio, fallback = 'mp3') {
  if (audio.length < 4) return fallback;
  if (audio[0] === 0x49 && audio[1] === 0x44 && audio[2] === 0x33) return 'mp3'; // "ID3"
  if (audio[0] === 0x66 && audio[1] === 0x4c && audio[2] === 0x61 && audio[3] === 0x43) return 'flac'; // "fLaC"
  if (audio[0] === 0x4f && audio[1] === 0x67 && audio[2] === 0x67 && audio[3] === 0x53) return 'ogg'; // "OggS"
  if (audio[0] === 0x52 && audio[1] === 0x49 && audio[2] === 0x46 && audio[3] === 0x46) return 'wav'; // "RIFF"
  if (audio[0] === 0xff && (audio[1] & 0xe0) === 0xe0) return 'mp3';
  if (audio.length >= 8 && audio[4] === 0x66 && audio[5] === 0x74 && audio[6] === 0x79 && audio[7] === 0x70) return 'm4a';
  return fallback;
}

function decodeV1File(inputPath, outputDir) {
  const ext = path.extname(inputPath).toLowerCase();
  const outExt = EXT_MAP_V1[ext];
  if (!outExt) throw new Error(`QMCv1: unsupported extension ${ext}`);
  const input = fs.readFileSync(inputPath);
  const audio = decryptV1Buffer(input);
  const base = inputPath.replace(/\.[^./]+$/, '');
  const name = base.split(/[\\/]/).pop();
  const outPath = outputDir ? path.join(outputDir, `${name}.${outExt}`) : `${base}.${outExt}`;
  fs.mkdirSync(outputDir || '.', { recursive: true });
  fs.writeFileSync(outPath, audio);
  return { outputPath: outPath, format: outExt };
}

function decodeV2File(inputPath, outputDir, opts = {}) {
  const ext = path.extname(inputPath).toLowerCase();
  const outExtHint = EXT_MAP_V2[ext] || 'mp3';
  const input = fs.readFileSync(inputPath);
  const audio = decryptV2Buffer(input, opts.ekey);
  const fmt = inferFormat(audio, outExtHint);
  const base = inputPath.replace(/\.[^./]+$/, '');
  const name = base.split(/[\\/]/).pop();
  const outPath = outputDir ? path.join(outputDir, `${name}.${fmt}`) : `${base}.${fmt}`;
  fs.mkdirSync(outputDir || '.', { recursive: true });
  fs.writeFileSync(outPath, audio);
  return { outputPath: outPath, format: fmt };
}

/**
 * Unified decodeFile: routes to V1 or V2 based on file extension.
 */
function decodeFile(inputPath, outputDir, opts = {}) {
  const ext = path.extname(inputPath).toLowerCase();
  if (EXT_MAP_V2[ext]) {
    return decodeV2File(inputPath, outputDir, opts);
  }
  return decodeV1File(inputPath, outputDir);
}

module.exports = {
  // unified entry
  decodeFile,
  // v1
  decryptV1Buffer,
  decodeV1File,
  QmcSeed,
  SEED_MAP,
  EXT_MAP_V1,
  // v2
  decryptV2Buffer,
  decodeV2File,
  keyCompress,
  shiftMix,
  qmc1Transform,
  EXT_MAP_V2,
  // shared
  inferFormat,
  V1_OFFSET_BOUNDARY,
  V2_KEY_SIZE,
  KEY_COMPRESS_INDEX_OFFSET,
};
