'use strict';

/**
 * KGG QMC2 stream cipher.
 *
 * Reference: 1:1 port of android/.../kgg/KggQmc2.kt
 * Public algorithm spec — see unlock-music.
 *
 * Two implementations, selected by key length:
 *   - MapCipher: short keys (<= 300 bytes), pure XOR with
 *     pseudo-random mask derived from (offset, key).
 *   - Rc4Cipher: long keys (> 300 bytes), RC4-like stream cipher
 *     segmented by 5120-byte blocks with segment skips.
 *
 * Constants are deliberately identical to the public spec.
 */

const MAP_OFFSET_BOUNDARY = 0x7fffn;
const MAP_INDEX_OFFSET = 71214n;
const RC4_FIRST_SEGMENT_SIZE = 128n;
const RC4_SEGMENT_SIZE = 5120n;

function makeMapCipher(key) {
  const k = Buffer.from(key); // defensive copy
  return {
    apply(buffer, length, absoluteOffset) {
      if (length < 0 || length > buffer.length) throw new Error('QMC2 buffer length is invalid');
      if (absoluteOffset < 0n) throw new Error('QMC2 offset is negative');
      const keyLen = BigInt(k.length);
      for (let i = 0; i < length; i++) {
        let offset = absoluteOffset + BigInt(i);
        if (offset > MAP_OFFSET_BOUNDARY) offset = offset % MAP_OFFSET_BOUNDARY;
        const keyIndex = Number((offset * offset + MAP_INDEX_OFFSET) % keyLen);
        const value = k[keyIndex] & 0xff;
        const shift = BigInt((keyIndex & 7) + 4) % 8n;
        const shifted = shift === 0n ? value : Number(((BigInt(value) << shift) | (BigInt(value) >> shift)) & 0xffn);
        buffer[i] = (buffer[i] ^ shifted) & 0xff;
      }
    },
  };
}

function makeRc4Cipher(key) {
  const k = Buffer.from(key);
  const box = new Uint8Array(k.length);
  for (let i = 0; i < k.length; i++) box[i] = i;

  // KSA
  let swapIndex = 0;
  for (let i = 0; i < k.length; i++) {
    swapIndex = (swapIndex + box[i] + (k[i] & 0xff)) % k.length;
    const t = box[i];
    box[i] = box[swapIndex];
    box[swapIndex] = t;
  }

  // Hash: multiply all non-zero key bytes mod 2^32
  let hash = 1n;
  for (const b of k) {
    const u = BigInt(b & 0xff);
    if (u === 0n) continue;
    const next = (hash * u) & 0xffffffffn;
    if (next === 0n || next <= hash) break;
    hash = next;
  }

  function segmentSkip(segmentId) {
    const keyLen = BigInt(k.length);
    const seed = BigInt(k[Number(segmentId % keyLen)] & 0xff);
    if (seed === 0n) return 0;
    // hash / ((segmentId + 1) * seed) * 100
    const numerator = hash;
    const denominator = ((segmentId + 1n) * seed);
    const idx = BigInt(Math.floor((Number(numerator) / Number(denominator)) * 100));
    return Number(idx % keyLen);
  }

  function applySegment(buffer, start, segLen, segOffset) {
    const state = new Uint8Array(box);
    let j = 0;
    let kIdx = 0;
    const skip = Number(segOffset % RC4_SEGMENT_SIZE) + segmentSkip(segOffset / RC4_SEGMENT_SIZE);
    for (let step = 0; step < skip + segLen; step++) {
      j = (j + 1) % state.length;
      kIdx = ((state[j] & 0xff) + kIdx) % state.length;
      const tmp = state[j];
      state[j] = state[kIdx];
      state[kIdx] = tmp;
      if (step >= skip) {
        const stream = state[((state[j] & 0xff) + (state[kIdx] & 0xff)) % state.length];
        buffer[start + step - skip] = buffer[start + step - skip] ^ stream;
      }
    }
  }

  return {
    apply(buffer, length, absoluteOffset) {
      if (length < 0 || length > buffer.length) throw new Error('QMC2 buffer length is invalid');
      if (absoluteOffset < 0n) throw new Error('QMC2 offset is negative');
      let offset = absoluteOffset;
      let processed = 0;
      let remaining = length;

      // First 128-byte segment
      if (offset < RC4_FIRST_SEGMENT_SIZE) {
        const count = Number(BigInt(Math.min(remaining, Number(RC4_FIRST_SEGMENT_SIZE - offset))));
        for (let i = 0; i < count; i++) {
          const keyIndex = segmentSkip(offset + BigInt(i));
          buffer[processed + i] = buffer[processed + i] ^ k[keyIndex];
        }
        offset += BigInt(count);
        processed += count;
        remaining -= count;
      }

      // Align to 5120 boundary
      if (remaining > 0 && offset % RC4_SEGMENT_SIZE !== 0n) {
        const toBoundary = Number(RC4_SEGMENT_SIZE - (offset % RC4_SEGMENT_SIZE));
        const count = Math.min(remaining, toBoundary);
        applySegment(buffer, processed, count, offset);
        offset += BigInt(count);
        processed += count;
        remaining -= count;
      }

      // Full 5120-byte segments
      while (remaining > Number(RC4_SEGMENT_SIZE)) {
        applySegment(buffer, processed, Number(RC4_SEGMENT_SIZE), offset);
        offset += RC4_SEGMENT_SIZE;
        processed += Number(RC4_SEGMENT_SIZE);
        remaining -= Number(RC4_SEGMENT_SIZE);
      }

      // Trailing partial segment
      if (remaining > 0) {
        applySegment(buffer, processed, remaining, offset);
      }
    },
  };
}

function makeCipher(key) {
  if (!key || key.length === 0) throw new Error('QMC2 key is empty');
  return key.length <= 300 ? makeMapCipher(key) : makeRc4Cipher(key);
}

module.exports = {
  makeCipher,
  MAP_OFFSET_BOUNDARY,
  MAP_INDEX_OFFSET,
  RC4_FIRST_SEGMENT_SIZE,
  RC4_SEGMENT_SIZE,
};
