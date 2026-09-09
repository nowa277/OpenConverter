const fs = require('node:fs');
const path = require('node:path');

async function fetchEkeyFromApi(songMid, fileMid, cookie, opts = {}) {
  if (!cookie) return null;
  let uin = opts.qqUin || '';
  const uinMatch = cookie.match(/qqmusic_uin=o?(\d+)/) || cookie.match(/(?:^|;\s*)uin=o?(\d+)/) || cookie.match(/qm_hideuin=o?(\d+)/) || cookie.match(/uid=o?(\d+)/) || cookie.match(/ptui_loginuin=o?([^;]+)/);
  if (uinMatch) uin = uinMatch[1].replace(/^o0*/, ''); // strip leading o and zeros
  uin = String(uin).replace(/^o?0*/, '');
  if (!uin) return null;

  let guid = opts.qqGuid;
  if (!guid) {
    const guidMatch = cookie.match(/qqmusic_guid=([^;]+)/);
    if (guidMatch) guid = guidMatch[1];
    else guid = '10000';
  }

  const ext = fileMid.startsWith('F0') ? '.mflac' : '.mgg';
  const requestData = {
    comm: {
      cv: 4747474, ct: 24, format: 'json',
      inCharset: 'utf-8', outCharset: 'utf-8',
      notice: 0, platform: 'yqq.json', needNewCode: 1,
      uin: 'UIN_PLACEHOLDER', g_tk_new_20200303: 5381, g_tk: 5381,
    },
    req_1: {
      module: 'vkey.GetVkeyServer',
      method: 'CgiGetVkey',
      param: {
        filename: [`${fileMid}${ext}`],
        guid: guid,
        songmid: [songMid],
        songtype: [0],
        uin: uin,
        loginflag: 1,
        platform: '20',
      },
    },
  };

  try {
    const res = await fetch('https://u.y.qq.com/cgi-bin/musicu.fcg', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Cookie': cookie,
        'User-Agent': 'QQMusic/21'
      },
      body: JSON.stringify(requestData).replace('"UIN_PLACEHOLDER"', uin)
    });
    const result = await res.json();
    const midurlinfo = result?.req_1?.data?.midurlinfo;
    if (midurlinfo && midurlinfo.length > 0) {
      const ekey = midurlinfo[0].ekey;
      if (ekey) return ekey;
      // If ekey is empty but midurlinfo exists, the API rejected us
      throw new Error(`API rejected request. Response: ${JSON.stringify(result.req_1.data)}`);
    }
    throw new Error(`Invalid API response: ${JSON.stringify(result)}`);
  } catch (err) {
    console.error('fetchEkeyFromApi error:', err);
    throw err;
  }
}

// =====================================================================
// Legacy / Shared Constants
// =====================================================================
const V1_OFFSET_BOUNDARY = 0x7fff;
const V2_KEY_SIZE = 128;
const KEY_COMPRESS_INDEX_OFFSET = 71214;
const EXT_MAP_V1 = { '.qmc0': 'mp3', '.qmc3': 'mp3', '.qmcflac': 'flac', '.qmcogg': 'ogg' };
const EXT_MAP_V2 = {
  '.mflac0': 'flac', '.mflac': 'flac', '.mgg1': 'ogg', '.mgg': 'ogg',
  '.mflac2': 'flac', '.mflac4': 'flac', '.mgg2': 'ogg', '.mgg4': 'ogg', '.mggl': 'ogg',
  '.bkc': 'mp3', '.bkcmp3': 'mp3', '.bkcflac': 'flac', '.bkcogg': 'ogg',
  '.bkcm4a': 'm4a', '.bkcwav': 'wav', '.bkcwma': 'wma', '.bkcape': 'ape'
};

const SEED_MAP = [
  [0x4a, 0xd6, 0xca, 0x90, 0x67, 0xf7, 0x52], [0x5e, 0x95, 0x23, 0x9f, 0x13, 0x11, 0x7e],
  [0x47, 0x74, 0x3d, 0x90, 0xaa, 0x3f, 0x51], [0xc6, 0x09, 0xd5, 0x9f, 0xfa, 0x66, 0xf9],
  [0xf3, 0xd6, 0xa1, 0x90, 0xa0, 0xf7, 0xf0], [0x1d, 0x95, 0xde, 0x9f, 0x84, 0x11, 0xf4],
  [0x0e, 0x74, 0xbb, 0x90, 0xbc, 0x3f, 0x92], [0x00, 0x09, 0x5b, 0x9f, 0x62, 0x66, 0xa1],
];

class QmcSeed {
  constructor() { this.x = -1; this.y = 8; this.dx = 1; this.index = -1; }
  nextMask() {
    let ret;
    this.index++;
    if (this.x < 0) { this.dx = 1; this.y = (8 - this.y) % 8; ret = 0xc3; }
    else if (this.x > 6) { this.dx = -1; this.y = 7 - this.y; ret = 0xd8; }
    else { ret = SEED_MAP[this.y][this.x]; }
    this.x += this.dx;
    if (this.index === 0x8000 || (this.index > 0x8000 && (this.index + 1) % 0x8000 === 0)) return this.nextMask();
    return ret;
  }
}

function shiftMix(byte, shift) {
  shift &= 7;
  if (shift === 0) return byte;
  return ((byte << shift) | (byte >>> shift)) & 0xff;
}

function keyCompress(ekey) {
  if (!ekey || ekey.length === 0) throw new Error('QMCv2: ekey is empty');
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
// Tencent TEA & QMC Key Derivation
// =====================================================================

class TeaCipher {
  static delta = 0x9e3779b9;
  constructor(key, rounds = 64) {
    if (key.length !== 16) throw Error('incorrect key size');
    if ((rounds & 1) !== 0) throw Error('odd number of rounds');
    this.k0 = key.readUInt32BE(0);
    this.k1 = key.readUInt32BE(4);
    this.k2 = key.readUInt32BE(8);
    this.k3 = key.readUInt32BE(12);
    this.rounds = rounds;
  }
  decryptBlock(dst, dstOffset, src, srcOffset) {
    let v0 = src.readUInt32BE(srcOffset);
    let v1 = src.readUInt32BE(srcOffset + 4);
    let sum = (TeaCipher.delta * this.rounds / 2) >>> 0;
    for (let i = 0; i < this.rounds / 2; i++) {
      v1 -= ((v0 << 4) + this.k2) ^ (v0 + sum) ^ ((v0 >>> 5) + this.k3);
      v1 >>>= 0;
      v0 -= ((v1 << 4) + this.k0) ^ (v1 + sum) ^ ((v1 >>> 5) + this.k1);
      v0 >>>= 0;
      sum = (sum - TeaCipher.delta) >>> 0;
    }
    dst.writeUInt32BE(v0, dstOffset);
    dst.writeUInt32BE(v1, dstOffset + 4);
  }
}

function decryptTencentTea(inBuf, key) {
  if (inBuf.length % 8 !== 0) throw Error('inBuf size not a multiple of the block size');
  if (inBuf.length < 16) throw Error('inBuf size too small');
  const blk = new TeaCipher(key, 32); // 32 rounds (16 pairs)
  
  const tmpBuf = Buffer.alloc(8);
  blk.decryptBlock(tmpBuf, 0, inBuf, 0);
  
  const nPadLen = tmpBuf[0] & 0x7;
  const SALT_LEN = 2, ZERO_LEN = 7;
  const outLen = inBuf.length - 1 - nPadLen - SALT_LEN - ZERO_LEN;
  if (outLen < 0) throw Error('invalid tea payload length');
  const outBuf = Buffer.alloc(outLen);
  
  let ivPrev = Buffer.alloc(8);
  let ivCur = inBuf.slice(0, 8);
  let inBufPos = 8;
  let tmpIdx = 1 + nPadLen;
  
  const cryptBlock = () => {
    ivPrev.set(ivCur);
    inBuf.copy(ivCur, 0, inBufPos, inBufPos + 8);
    for (let j = 0; j < 8; j++) tmpBuf[j] ^= ivCur[j];
    blk.decryptBlock(tmpBuf, 0, tmpBuf, 0);
    inBufPos += 8;
    tmpIdx = 0;
  };
  
  for (let i = 1; i <= SALT_LEN; ) {
    if (tmpIdx < 8) { tmpIdx++; i++; } else { cryptBlock(); }
  }
  
  let outBufPos = 0;
  while (outBufPos < outLen) {
    if (tmpIdx < 8) {
      outBuf[outBufPos++] = tmpBuf[tmpIdx] ^ ivPrev[tmpIdx];
      tmpIdx++;
    } else {
      cryptBlock();
    }
  }
  
  for (let i = 1; i <= ZERO_LEN; i++) {
    if (tmpIdx >= 8) cryptBlock();
    if (tmpBuf[tmpIdx] !== ivPrev[tmpIdx]) throw Error('zero check failed');
    tmpIdx++;
  }
  return outBuf;
}

const MIX_KEY_1 = Buffer.from([0x33, 0x38, 0x36, 0x5A, 0x4A, 0x59, 0x21, 0x40, 0x23, 0x2A, 0x24, 0x25, 0x5E, 0x26, 0x29, 0x28]);
const MIX_KEY_2 = Buffer.from([0x2A, 0x24, 0x25, 0x5E, 0x26, 0x29, 0x28, 0x23, 0x40, 0x21, 0x33, 0x38, 0x36, 0x5A, 0x4A, 0x59]);

function decryptV2Key(keyBuf) {
  if (keyBuf.length >= 18 && keyBuf.slice(0, 18).toString('ascii') === 'QQMusic EncV2,Key:') {
    let out = decryptTencentTea(keyBuf.slice(18), MIX_KEY_1);
    out = decryptTencentTea(out, MIX_KEY_2);
    const keyDec = Buffer.from(out.toString('ascii'), 'base64');
    if (keyDec.length < 16) throw Error('EncV2 key decode failed');
    return keyDec;
  }
  return keyBuf;
}

function simpleMakeKey(salt, length) {
  const keyBuf = Buffer.alloc(length);
  for (let i = 0; i < length; i++) {
    const tmp = Math.tan(salt + i * 0.1);
    keyBuf[i] = (Math.abs(tmp) * 100.0) & 0xff;
  }
  return keyBuf;
}

function qmcDeriveKey(b64String) {
  let rawDec = Buffer.from(b64String, 'base64');
  if (rawDec.length < 16) return rawDec;
  let originalRawDec = Buffer.from(rawDec);
  try {
    rawDec = decryptV2Key(rawDec);
    
    const simpleKey = simpleMakeKey(106, 8);
    const teaKey = Buffer.alloc(16);
    for (let i = 0; i < 8; i++) {
      teaKey[i << 1] = simpleKey[i];
      teaKey[(i << 1) + 1] = rawDec[i];
    }
    const sub = decryptTencentTea(rawDec.slice(8), teaKey);
    const finalKey = Buffer.alloc(8 + sub.length);
    rawDec.copy(finalKey, 0, 0, 8);
    sub.copy(finalKey, 8);
    return finalKey;
  } catch (e) {
    return originalRawDec;
  }
}

// =====================================================================
// QMC1 & QMC2 Mask Generators & RC4 Cipher
// =====================================================================

const QMC1_STATIC_BOX = new Uint8Array([
  0x77, 0x48, 0x32, 0x73, 0xDE, 0xF2, 0xC0, 0xC8, 0x95, 0xEC, 0x30, 0xB2, 0x51, 0xC3, 0xE1, 0xA0,
  0x9E, 0xE6, 0x9D, 0xCF, 0xFA, 0x7F, 0x14, 0xD1, 0xCE, 0xB8, 0xDC, 0xC3, 0x4A, 0x67, 0x93, 0xD6,
  0x28, 0xC2, 0x91, 0x70, 0xCA, 0x8D, 0xA2, 0xA4, 0xF0, 0x08, 0x61, 0x90, 0x7E, 0x6F, 0xA2, 0xE0,
  0xEB, 0xAE, 0x3E, 0xB6, 0x67, 0xC7, 0x92, 0xF4, 0x91, 0xB5, 0xF6, 0x6C, 0x5E, 0x84, 0x40, 0xF7,
  0xF3, 0x1B, 0x02, 0x7F, 0xD5, 0xAB, 0x41, 0x89, 0x28, 0xF4, 0x25, 0xCC, 0x52, 0x11, 0xAD, 0x43,
  0x68, 0xA6, 0x41, 0x8B, 0x84, 0xB5, 0xFF, 0x2C, 0x92, 0x4A, 0x26, 0xD8, 0x47, 0x6A, 0x7C, 0x95,
  0x61, 0xCC, 0xE6, 0xCB, 0xBB, 0x3F, 0x47, 0x58, 0x89, 0x75, 0xC3, 0x75, 0xA1, 0xD9, 0xAF, 0xCC,
  0x08, 0x73, 0x17, 0xDC, 0xAA, 0x9A, 0xA2, 0x16, 0x41, 0xD8, 0xA2, 0x06, 0xC6, 0x8B, 0xFC, 0x66,
  0x34, 0x9F, 0xCF, 0x18, 0x23, 0xA0, 0x0A, 0x74, 0xE7, 0x2B, 0x27, 0x70, 0x92, 0xE9, 0xAF, 0x37,
  0xE6, 0x8C, 0xA7, 0xBC, 0x62, 0x65, 0x9C, 0xC2, 0x08, 0xC9, 0x88, 0xB3, 0xF3, 0x43, 0xAC, 0x74,
  0x2C, 0x0F, 0xD4, 0xAF, 0xA1, 0xC3, 0x01, 0x64, 0x95, 0x4E, 0x48, 0x9F, 0xF4, 0x35, 0x78, 0x95,
  0x7A, 0x39, 0xD6, 0x6A, 0xA0, 0x6D, 0x40, 0xE8, 0x4F, 0xA8, 0xEF, 0x11, 0x1D, 0xF3, 0x1B, 0x3F,
  0x3F, 0x07, 0xDD, 0x6F, 0x5B, 0x19, 0x30, 0x19, 0xFB, 0xEF, 0x0E, 0x37, 0xF0, 0x0E, 0xCD, 0x16,
  0x49, 0xFE, 0x53, 0x47, 0x13, 0x1A, 0xBD, 0xA4, 0xF1, 0x40, 0x19, 0x60, 0x0E, 0xED, 0x68, 0x09,
  0x06, 0x5F, 0x4D, 0xCF, 0x3D, 0x1A, 0xFE, 0x20, 0x77, 0xE4, 0xD9, 0xDA, 0xF9, 0xA4, 0x2B, 0x76,
  0x1C, 0x71, 0xDB, 0x00, 0xBC, 0xFD, 0x0C, 0x6C, 0xA5, 0x47, 0xF7, 0xF6, 0x00, 0x79, 0x4A, 0x11,
]);

const V1_MASK = Buffer.allocUnsafe(32768);
for (let i = 0; i < 32768; i++) {
  V1_MASK[i] = QMC1_STATIC_BOX[(i * i + 27) & 0xff];
}

function getMapMask(derivedKey) {
  const wkey = keyCompress(derivedKey);
  const mask = Buffer.allocUnsafe(32768);
  for (let i = 0; i < 32768; i++) {
    mask[i] = wkey[i % 128];
  }
  return mask;
}

class QmcRC4Cipher {
  constructor(key) {
    this.key = key;
    this.N = key.length;
    this.S = Buffer.alloc(this.N);
    for (let i = 0; i < this.N; i++) this.S[i] = i & 0xff;
    let j = 0;
    for (let i = 0; i < this.N; i++) {
      j = (this.S[i] + j + this.key[i % this.N]) % this.N;
      const tmp = this.S[i]; this.S[i] = this.S[j]; this.S[j] = tmp;
    }
    this.hash = 1;
    for (let i = 0; i < this.N; i++) {
      let value = this.key[i];
      if (!value) continue;
      const next_hash = (this.hash * value) >>> 0;
      if (next_hash === 0 || next_hash <= this.hash) break;
      this.hash = next_hash;
    }
  }
  getSegmentKey(id) {
    const seed = this.key[id % this.N];
    if (seed === 0) return 0;
    const idx = Math.floor((this.hash / ((id + 1) * seed)) * 100.0);
    return idx % this.N;
  }
  decrypt(buf, offset) {
    const SEGMENT_SIZE = 5120;
    let toProcess = buf.length;
    let processed = 0;
    const postProcess = (len) => {
      toProcess -= len; processed += len; offset += len;
      return toProcess === 0;
    };

    if (offset < 128) {
      const len = Math.min(buf.length, 128 - offset);
      for (let i = 0; i < len; i++) {
        buf[processed + i] ^= this.key[this.getSegmentKey(offset + i)];
      }
      if (postProcess(len)) return;
    }

    const encSegment = (subBuf, off) => {
      const S = Buffer.from(this.S);
      const skipLen = (off % SEGMENT_SIZE) + this.getSegmentKey(Math.floor(off / SEGMENT_SIZE));
      let j = 0, k = 0;
      for (let i = -skipLen; i < subBuf.length; i++) {
        j = (j + 1) % this.N;
        k = (S[j] + k) % this.N;
        const tmp = S[j]; S[j] = S[k]; S[k] = tmp;
        if (i >= 0) subBuf[i] ^= S[(S[j] + S[k]) % this.N];
      }
    };

    if (offset % SEGMENT_SIZE !== 0) {
      const len = Math.min(SEGMENT_SIZE - (offset % SEGMENT_SIZE), toProcess);
      encSegment(buf.subarray(processed, processed + len), offset);
      if (postProcess(len)) return;
    }

    while (toProcess > SEGMENT_SIZE) {
      encSegment(buf.subarray(processed, processed + SEGMENT_SIZE), offset);
      postProcess(SEGMENT_SIZE);
    }
    if (toProcess > 0) encSegment(buf.subarray(processed), offset);
  }
}

// =====================================================================
// Decryption entry points
// =====================================================================

// Base64 alphabet (plus optional trailing whitespace) — every embedded ekey
// is a base64 string, so anything else is a false-positive container match.
const EKEY_RE = /^[A-Za-z0-9+/=]+$/;
const MAX_EKEY_LEN = 0xffff;
// Smallest buffer we are willing to interpret as a tail-marked container.
// Real QMC files are megabytes; this only guards against garbage input.
const MIN_TAIL_CONTAINER_LEN = 0x20;

/**
 * Locate the per-file ekey (or the metadata needed to fetch one) inside a
 * QMCv2 container.
 *
 * Returns `null` when no container marker is recognised (caller should fall
 * back to a user-provided ekey), otherwise an object with:
 *   - ekey        base64 ekey string (absent for `musicex`)
 *   - format      'musicex' when the key must be fetched online
 *   - audioOffset byte offset where the ciphertext starts
 *   - audioLen    ciphertext length in bytes
 *
 * Supported layouts:
 *   STag head  — "STag" @0, u32LE ekeyLen @0x14, ekey @0x18, then audio.
 *   QTag tail  — audio | meta("ekey,songid,ver") | u32BE metaLen | "QTag"
 *   musicex    — audio | tail | u32LE tailSize | ... | "musicex\0"
 *   raw tail   — audio | ekey | u32LE ekeyLen
 *   STag tail  — "STag" as the last 4 bytes: no embedded key (throws).
 */
function detectKey(buf) {
  const len = buf.length;
  if (len < 0x18) return null;

  // 1. STag at the head (newer clients: mflac2/mflac4/mgg2/mgg4/mggl).
  if (buf.toString('ascii', 0, 4) === 'STag') {
    const ekeyLen = buf.readUInt32LE(0x14);
    const audioOffset = 0x18 + ekeyLen;
    if (ekeyLen > 0 && ekeyLen <= MAX_EKEY_LEN && audioOffset < len) {
      const ekey = buf.toString('ascii', 0x18, audioOffset).trim();
      if (EKEY_RE.test(ekey)) {
        return { ekey, audioOffset, audioLen: len - audioOffset };
      }
    }
    return null;
  }

  const tail4 = buf.toString('ascii', len - 4);

  // 2. musicex tail (QQ Music ≥ 2023): key must be fetched with the user's cookie.
  if (buf.toString('ascii', len - 8) === 'musicex\x00') {
    const tailSize = buf.readUInt32LE(len - 16);
    if (tailSize > 0 && tailSize < len - 16) {
      const tail = buf.subarray(len - 16 - tailSize, len - 16);
      if (tail.length >= 184) {
        const songMid = tail.toString('utf16le', 28, 88).replace(/\0+$/, '');
        const filename = tail.toString('utf16le', 88, 184).replace(/\0+$/, '');
        const fileMid = filename.replace(/\.(mflac|mgg)$/i, '');
        const audioLen = len - 16 - tailSize;
        return { format: 'musicex', songMid, fileMid, audioOffset: 0, audioLen };
      }
    }
    throw new Error('This file was encrypted with a newer QQ Music client (musicex) without an embedded key. Please provide your QQ Music Cookie in Settings to unlock it.');
  }

  // 3. STag at the tail: container without an embedded key.
  if (tail4 === 'STag' && len >= MIN_TAIL_CONTAINER_LEN) {
    throw new Error('This file contains an STag but no embedded key. Please provide your QQ Music Cookie in Settings or downgrade your client.');
  }

  // 4. QTag tail: "<ekey>,<songid>,<version>" with a big-endian length.
  if (tail4 === 'QTag') {
    const metaLen = buf.readUInt32BE(len - 8);
    if (metaLen > 0 && metaLen < len - 8) {
      const audioLen = len - 8 - metaLen;
      const rawMeta = buf.toString('utf-8', audioLen, len - 8);
      const ekey = rawMeta.split(',')[0].trim();
      if (ekey && EKEY_RE.test(ekey)) return { ekey, audioOffset: 0, audioLen };
    }
    return null;
  }

  // 5. Raw ekey tail: ekey bytes followed by a little-endian length.
  const rawLen = buf.readUInt32LE(len - 4);
  if (rawLen > 0 && rawLen <= MAX_EKEY_LEN && rawLen < len - 4) {
    const audioLen = len - 4 - rawLen;
    const ekey = buf.toString('ascii', audioLen, len - 4).trim();
    if (EKEY_RE.test(ekey)) return { ekey, audioOffset: 0, audioLen };
  }
  return null;
}

function applyMask(buf, mask) {
  const len = buf.length;
  const limit1 = Math.min(len, 32768);
  for (let i = 0; i < limit1; i++) buf[i] ^= mask[i];
  for (let i = 32768; i < len; i++) buf[i] ^= mask[i % 32768];
}

function decryptV1Buffer(qmcBuf) {
  const out = Buffer.allocUnsafe(qmcBuf.length);
  qmcBuf.copy(out);
  applyMask(out, V1_MASK);
  return out;
}

function decryptV2Buffer(qmcBuf, ekeyB64) {
  if (!ekeyB64) throw new Error('QMCv2 requires an ekey string');
  const derivedKey = qmcDeriveKey(ekeyB64);
  const out = Buffer.allocUnsafe(qmcBuf.length);
  qmcBuf.copy(out);
  
  if (derivedKey.length > 300) {
    const rc4 = new QmcRC4Cipher(derivedKey);
    rc4.decrypt(out, 0);
  } else {
    const mask = getMapMask(derivedKey);
    applyMask(out, mask);
  }
  return out;
}

function inferFormat(audio, fallback = 'mp3') {
  return detectAudioFormat(audio) || fallback;
}

function detectAudioFormat(audio) {
  if (audio.length < 4) return null;
  if (audio[0] === 0x49 && audio[1] === 0x44 && audio[2] === 0x33) return 'mp3';
  if (audio[0] === 0x66 && audio[1] === 0x4c && audio[2] === 0x61 && audio[3] === 0x43) return 'flac';
  if (audio[0] === 0x4f && audio[1] === 0x67 && audio[2] === 0x67 && audio[3] === 0x53) return 'ogg';
  if (audio[0] === 0x52 && audio[1] === 0x49 && audio[2] === 0x46 && audio[3] === 0x46) return 'wav';
  if (audio[0] === 0xff && (audio[1] & 0xe0) === 0xe0) return 'mp3';
  if (audio.length >= 8 && audio[4] === 0x66 && audio[5] === 0x74 && audio[6] === 0x79 && audio[7] === 0x70) return 'm4a';
  return null;
}

function decodeV1File(inputPath, outputDir) {
  const ext = path.extname(inputPath).toLowerCase();
  const outExtHint = EXT_MAP_V1[ext] || 'mp3';
  const input = fs.readFileSync(inputPath);
  const audio = decryptV1Buffer(input);
  const fmt = inferFormat(audio, outExtHint);
  const base = inputPath.replace(/\.[^./]+$/, '');
  const name = base.split(/[\\/]/).pop();
  const outPath = outputDir ? path.join(outputDir, `${name}.${fmt}`) : `${base}.${fmt}`;
  fs.mkdirSync(outputDir || '.', { recursive: true });
  fs.writeFileSync(outPath, audio);
  return { outputPath: outPath, format: fmt };
}

async function decodeV2File(inputPath, outputDir, opts = {}) {
  const ext = path.extname(inputPath).toLowerCase();
  const outExtHint = EXT_MAP_V2[ext] || 'mp3';
  const input = fs.readFileSync(inputPath);
  
  const detected = detectKey(input);
  let audio;
  if (detected) {
    let ekey = detected.ekey;
    if (detected.format === 'musicex') {
      ekey = await fetchEkeyFromApi(detected.songMid, detected.fileMid, opts.qqCookie, opts);
      if (!ekey) {
        throw new Error('QQ Music VIP Cookie required (or cookie expired). Please log into QQ Music with a VIP account, play a VIP song, and click Settings -> Scan QQ Music Memory.');
      }
    }
    const cipherText = input.subarray(detected.audioOffset, detected.audioOffset + detected.audioLen);
    audio = decryptV2Buffer(cipherText, ekey);
  } else {
    if (!opts.ekey) {
      throw new Error('No embedded key found in this QMCv2 file. Paste your QQ Music ekey in Settings, or provide a Cookie so the key can be fetched.');
    }
    audio = decryptV2Buffer(input, opts.ekey);
  }

  const fmt = detectAudioFormat(audio);
  if (!fmt) {
    throw new Error(`QMCv2 decryption failed: decrypted data is not a recognized audio stream (expected ${outExtHint}). The ekey / QQ Music Cookie may be expired or mismatched.`);
  }
  const base = inputPath.replace(/\.[^./]+$/, '');
  const name = base.split(/[\\/]/).pop();
  const outPath = outputDir ? path.join(outputDir, `${name}.${fmt}`) : `${base}.${fmt}`;
  fs.mkdirSync(outputDir || '.', { recursive: true });
  fs.writeFileSync(outPath, audio);
  return { outputPath: outPath, format: fmt };
}

async function decodeFile(inputPath, outputDir, opts = {}) {
  const ext = path.extname(inputPath).toLowerCase();
  if (EXT_MAP_V2[ext]) return await decodeV2File(inputPath, outputDir, opts);
  return decodeV1File(inputPath, outputDir);
}

module.exports = {
  decodeFile,
  decryptV1Buffer, decodeV1File, QmcSeed, SEED_MAP, EXT_MAP_V1,
  decryptV2Buffer, decodeV2File, keyCompress, shiftMix, qmc1Transform, EXT_MAP_V2,
  inferFormat, detectKey, V1_OFFSET_BOUNDARY, V2_KEY_SIZE, KEY_COMPRESS_INDEX_OFFSET,
};
