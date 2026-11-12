'use strict';

/**
 * NetEase Cloud Music PC .uc cache file restore.
 *
 * Algorithm references:
 * - 52pojie thread-1556228, thread-1560121, thread-1822588, thread-1457398.
 *
 * Each byte is XORed with 0xA3 (163) without skipping any headers.
 * Output format is determined by sniffing the decrypted header bytes (MP3, FLAC, etc.).
 */

const fs = require('node:fs');
const path = require('node:path');

const XOR_KEY = 0xa3;

function restoreUcBuffer(buffer) {
  if (!Buffer.isBuffer(buffer)) {
    throw new Error('Invalid UC buffer: expected Buffer');
  }
  const restored = Buffer.alloc(buffer.length);
  for (let i = 0; i < buffer.length; i++) {
    restored[i] = buffer[i] ^ XOR_KEY;
  }
  return restored;
}

function sniffUcFormat(probe) {
  if (!probe || probe.length < 4) return 'mp3';
  // fLaC
  if (probe[0] === 0x66 && probe[1] === 0x4c && probe[2] === 0x61 && probe[3] === 0x43) return 'flac';
  // ID3 (MP3)
  if (probe[0] === 0x49 && probe[1] === 0x44 && probe[2] === 0x33) return 'mp3';
  // MP3 Frame Sync: 0xFF followed by top 3 bits set (0xE0)
  if (probe[0] === 0xff && (probe[1] & 0xe0) === 0xe0) return 'mp3';
  // RIFF (WAV)
  if (probe[0] === 0x52 && probe[1] === 0x49 && probe[2] === 0x46 && probe[3] === 0x46) return 'wav';
  // OggS
  if (probe[0] === 0x4f && probe[1] === 0x67 && probe[2] === 0x67 && probe[3] === 0x53) return 'ogg';
  // M4A: ftyp at offset 4
  if (probe.length >= 8 && probe[4] === 0x66 && probe[5] === 0x74 && probe[6] === 0x79 && probe[7] === 0x70) return 'm4a';
  return 'mp3';
}

function parseUcFilename(filename) {
  const base = path.basename(filename);
  // Pattern: <songId>-<bitrate>-<32hex>.uc
  const match = /^(\d+)-(\d+)-([0-9a-fA-F]{32})\.uc$/i.exec(base);
  if (match) {
    return {
      songId: match[1],
      bitrate: match[2],
      hash: match[3],
    };
  }
  // Fallback: just songId
  const fallback = /^(\d+)/.exec(base);
  return {
    songId: fallback ? fallback[1] : path.parse(base).name,
    bitrate: null,
    hash: null,
  };
}

function restoreUcFile(inputPath, outputDir) {
  const base = path.basename(inputPath);
  if (base.endsWith('.uc!')) {
    throw new Error('.uc! 分块缓存文件暂未完全证实，跳过处理');
  }

  const stat = fs.statSync(inputPath);
  if (stat.size === 0) {
    throw new Error('UC 缓存文件为空');
  }

  const parsed = parseUcFilename(inputPath);
  const outDir = outputDir || path.dirname(inputPath);
  fs.mkdirSync(outDir, { recursive: true });

  const CHUNK = 64 * 1024;
  const inFd = fs.openSync(inputPath, 'r');
  const tempPath = path.join(outDir, `.${base}.oc-restore`);
  const outFd = fs.openSync(tempPath, 'w');

  let probe = Buffer.alloc(0);
  let totalRead = 0;
  const buf = Buffer.alloc(CHUNK);

  try {
    while (totalRead < stat.size) {
      const n = fs.readSync(inFd, buf, 0, Math.min(CHUNK, stat.size - totalRead), totalRead);
      if (n <= 0) break;
      for (let i = 0; i < n; i++) {
        buf[i] ^= XOR_KEY;
      }
      if (probe.length < 16) {
        probe = Buffer.concat([probe, buf.subarray(0, Math.min(n, 16 - probe.length))]);
      }
      fs.writeSync(outFd, buf, 0, n);
      totalRead += n;
    }
  } finally {
    fs.closeSync(inFd);
    fs.closeSync(outFd);
  }

  const format = sniffUcFormat(probe);
  const finalFilename = `${parsed.songId || path.parse(base).name}.${format}`;
  const finalPath = path.join(outDir, finalFilename);

  fs.renameSync(tempPath, finalPath);

  const isShort = stat.size < 500 * 1024; // Less than 500KB is usually a ~30s preview
  const note = isShort ? '文件尺寸较小（可能为网易云 VIP 试听缓存片段）' : null;

  return {
    outputPath: finalPath,
    format,
    songId: parsed.songId,
    note,
  };
}

module.exports = {
  XOR_KEY,
  restoreUcBuffer,
  sniffUcFormat,
  parseUcFilename,
  restoreUcFile,
};
