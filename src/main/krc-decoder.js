'use strict';

/**
 * KuGou KRC lyric decoder and LRC converter.
 *
 * Algorithm reference:
 * - 52pojie thread-286822 (2014-08-24) & C++ verified implementations.
 *
 * Structure:
 * 1. 4-byte magic: 'krc1'
 * 2. XOR encryption with 16-byte repeating key:
 *    [64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105]
 * 3. Zlib decompress (inflate) -> UTF-8 plaintext with timed lyrics & tags.
 * 4. Convert KRC syllable timestamps to standard LRC format:
 *    [startMs,durationMs]<offset,duration,0>word... -> [mm:ss.xx]words
 */

const zlib = require('node:zlib');

const KRC_MAGIC = Buffer.from('krc1');
const XOR_KEY = Buffer.from([64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105]);

function decryptKrc(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < KRC_MAGIC.length) {
    throw new Error('KRC 文件头无效 (文件长度过短或非 Buffer)');
  }

  if (!buffer.subarray(0, KRC_MAGIC.length).equals(KRC_MAGIC)) {
    throw new Error('KRC 文件头无效 (需以 krc1 开头)');
  }

  const payload = buffer.subarray(KRC_MAGIC.length);
  const decrypted = Buffer.alloc(payload.length);
  for (let i = 0; i < payload.length; i++) {
    decrypted[i] = payload[i] ^ XOR_KEY[i % XOR_KEY.length];
  }

  try {
    const decompressed = zlib.inflateSync(decrypted);
    return decompressed.toString('utf8');
  } catch (err) {
    throw new Error(`KRC zlib 解压失败 (可能非合法的 KRC 密文): ${err.message}`);
  }
}

function formatTs(millis) {
  const totalCs = Math.max(0, Math.floor(Number(millis) / 10));
  const minutes = Math.floor(totalCs / 6000);
  const seconds = Math.floor((totalCs % 6000) / 100);
  const cs = totalCs % 100;
  return `[${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(cs).padStart(2, '0')}]`;
}

function krcToLrc(krcText) {
  if (typeof krcText !== 'string' || krcText.trim().length === 0) {
    return null;
  }

  const lines = krcText.split(/\r?\n/);
  const headers = [];
  const timedLines = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    // Filter out [language:...] base64 content
    if (/^\[language:/i.test(trimmed)) {
      continue;
    }

    // Standard metadata tags like [ti:Title], [ar:Artist], [al:Album], [by:...], [offset:0]
    if (/^\[(ti|ar|al|by|offset):/i.test(trimmed)) {
      headers.push(trimmed);
      continue;
    }

    // Ignore other bracket metadata headers (hash, id, total, sign)
    if (/^\[(id|hash|total|sign):/i.test(trimmed)) {
      continue;
    }

    // Match timed syllable line: [startMs,durationMs]<0,100,0>word...
    const match = /^\[(\d+),(\d+)\](.*)$/.exec(trimmed);
    if (match) {
      const startMs = parseInt(match[1], 10);
      const text = match[3].replace(/<\d+,\d+,\d+>/g, '').trim();
      if (text.length > 0) {
        timedLines.push(`${formatTs(startMs)}${text}`);
      }
    }
  }

  const resultLines = [...headers, ...timedLines];
  return resultLines.length > 0 ? resultLines.join('\n') : null;
}

function decodeKrcToLrc(buffer) {
  const text = decryptKrc(buffer);
  return krcToLrc(text);
}

module.exports = {
  KRC_MAGIC,
  XOR_KEY,
  decryptKrc,
  krcToLrc,
  decodeKrcToLrc,
};
