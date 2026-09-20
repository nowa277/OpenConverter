'use strict';

/**
 * KGG v5 decoder — Node/Electron port.
 *
 * Reference: 1:1 port of android/.../kgg/{KggV5Decoder, KggKeyMap, KggKeyProvider}.kt
 * Public algorithm spec — see unlock-music.
 *
 * End-to-end: read a .kgg file, look up the ekey by header id in the
 * user-provided key map (from a .kgg.key file or KGMusicV3.db), run the
 * KGG-specific QMC2 stream cipher, return the decrypted audio bytes.
 *
 * Pure functions only; I/O is the caller's job.
 */

const fs = require('node:fs');
const path = require('node:path');

const header = require('./header');
const ekey = require('./ekey');
const qmc2 = require('./qmc2');

const PROBE_SIZE = 16;

function decrypt(input, keyProvider) {
  if (input.length < header.MAGIC.length || !input.subarray(0, header.MAGIC.length).equals(header.MAGIC)) {
    throw new Error('Not a valid KGG v5 file (invalid magic)');
  }
  // 1. Read header prefix
  const prefix = Buffer.from(input.subarray(0, header.PREFIX_SIZE));
  const hdr = header.parse(prefix);
  if (hdr.cryptoVersion !== 5) {
    throw new Error(`KGG crypto version ${hdr.cryptoVersion} belongs to the legacy decoder`);
  }

  // 2. Look up ekey for this id
  const encoded = keyProvider.find(hdr.encryptionKeyId);
  if (!encoded) {
    const total = keyProvider.count();
    if (total === 0 || total === -1) {
      throw new Error('No KGG keys imported; import a kgg.key file or KGMusicV3.db in Settings');
    }
    throw new Error(`Missing KGG key for ${hdr.encryptionKeyId}`);
  }

  // 3. Unwrap ekey and build the stream cipher
  const v1Key = ekey.unwrap(encoded);
  const cipher = qmc2.makeCipher(v1Key);

  // 4. Decrypt audio bytes, probe first 16 for format sniffing
  const audioLen = input.length - hdr.headerLength;
  if (audioLen <= 0) throw new Error('KGG audio data is empty');

  const audio = Buffer.alloc(audioLen);
  input.copy(audio, 0, hdr.headerLength, input.length);
  cipher.apply(audio, audio.length, 0n);

  return { audio, format: sniffFormat(audio.subarray(0, Math.min(PROBE_SIZE, audioLen))) };
}

function sniffFormat(probe) {
  if (probe.length < 4) return 'mp3';
  // ID3 (MP3 with metadata header)
  if (probe[0] === 0x49 && probe[1] === 0x44 && probe[2] === 0x33) return 'mp3';
  // fLaC
  if (probe[0] === 0x66 && probe[1] === 0x4c && probe[2] === 0x61 && probe[3] === 0x43) return 'flac';
  // OggS
  if (probe[0] === 0x4f && probe[1] === 0x67 && probe[2] === 0x67 && probe[3] === 0x53) return 'ogg';
  // RIFF (WAV)
  if (probe[0] === 0x52 && probe[1] === 0x49 && probe[2] === 0x46 && probe[3] === 0x46) return 'wav';
  // MP3 frame sync
  if (probe[0] === 0xff && (probe[1] & 0xe0) === 0xe0) return 'mp3';
  // ftyp at offset 4 (M4A)
  if (probe.length >= 8 && probe[4] === 0x66 && probe[5] === 0x74 && probe[6] === 0x79 && probe[7] === 0x70) return 'm4a';
  return 'mp3';
}

// === Key map parser (kgg.key file format: "id$ekey" per line) ===
function parseKeyMap(text) {
  const map = new Map();
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.length === 0) continue;
    const sep = line.indexOf('$');
    if (sep <= 0 || sep !== line.lastIndexOf('$')) {
      throw new Error(`Invalid kgg.key line ${i + 1}: ${line.slice(0, 40)}`);
    }
    const id = line.slice(0, sep);
    const key = line.slice(sep + 1);
    if (id.length === 0 || key.length === 0) {
      throw new Error(`Invalid kgg.key line ${i + 1}: ${line.slice(0, 40)}`);
    }
    map.set(id, key);
  }
  return map;
}

function serializeKeyMap(map) {
  const sorted = Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b));
  return sorted.map(([id, key]) => `${id}$${key}`).join('\n') + '\n';
}

// === Simple in-memory key provider ===
function memoryKeyProvider(map) {
  return {
    find(id) { return map.get(id) || null; },
    count() { return map.size; },
  };
}

// === File-level convenience: read .kgg and write decrypted audio ===
function decodeFile(inputPath, outputDir, opts) {
  const keyPath = opts.keyPath;
  if (!keyPath) throw new Error('decodeFile requires opts.keyPath pointing to a kgg.key file or KGMusicV3.db');
  let keyText = '';
  try {
    keyText = fs.readFileSync(keyPath, 'utf-8');
  } catch (e) {
    // If file doesn't exist yet, we just pass an empty map to provider,
    // and let decrypt() throw the friendly 'No KGG keys imported' error.
  }
  const map = parseKeyMap(keyText);
  const provider = memoryKeyProvider(map);
  const stat = fs.statSync(inputPath);
  const stagingDir = outputDir || path.dirname(inputPath);
  fs.mkdirSync(stagingDir, { recursive: true });
  const tmpPath = path.join(stagingDir, `.${path.basename(inputPath)}.oc-partial`);
  const fd = fs.openSync(inputPath, 'r');
  let outFd;
  try {
    const prefixLen = Math.min(header.PREFIX_SIZE, stat.size);
    const prefix = Buffer.alloc(prefixLen);
    if (prefixLen < header.MAGIC.length || fs.readSync(fd, prefix, 0, prefixLen, 0) < prefixLen) {
      throw new Error('Not a valid KGG v5 file (invalid magic)');
    }
    if (!prefix.subarray(0, header.MAGIC.length).equals(header.MAGIC)) {
      throw new Error('Not a valid KGG v5 file (invalid magic)');
    }
    const hdr = header.parse(prefix);
    if (hdr.cryptoVersion !== 5) {
      throw new Error(`KGG crypto version ${hdr.cryptoVersion} belongs to the legacy decoder`);
    }
    const encoded = provider.find(hdr.encryptionKeyId);
    if (!encoded) {
      const total = provider.count();
      if (total === 0 || total === -1) {
        throw new Error('No KGG keys imported; import a kgg.key file or KGMusicV3.db in Settings');
      }
      throw new Error(`Missing KGG key for ${hdr.encryptionKeyId}`);
    }
    const v1Key = ekey.unwrap(encoded);
    const cipher = qmc2.makeCipher(v1Key);
    const audioLen = stat.size - hdr.headerLength;
    if (audioLen <= 0) throw new Error('KGG audio data is empty');
    outFd = fs.openSync(tmpPath, 'w');
    const CHUNK = 64 * 1024;
    const buf = Buffer.alloc(CHUNK);
    let offset = 0;
    let probe = Buffer.alloc(0);
    while (offset < audioLen) {
      const n = fs.readSync(fd, buf, 0, Math.min(CHUNK, audioLen - offset), hdr.headerLength + offset);
      if (n <= 0) break;
      cipher.apply(buf, n, BigInt(offset));
      if (probe.length < PROBE_SIZE) probe = Buffer.concat([probe, buf.subarray(0, Math.min(n, PROBE_SIZE - probe.length))]);
      fs.writeSync(outFd, buf, 0, n);
      offset += n;
    }
    fs.closeSync(outFd);
    outFd = null;
    const format = sniffFormat(probe.subarray(0, Math.min(PROBE_SIZE, probe.length)));
    const base = inputPath.replace(/\.[^./]+$/, '');
    const name = base.split(/[\\/]/).pop();
    const outPath = outputDir ? `${outputDir}/${name}.${format}` : `${base}.${format}`;
    fs.renameSync(tmpPath, outPath);
    return { outputPath: outPath, format };
  } catch (err) {
    try { if (outFd != null) fs.closeSync(outFd); } catch {}
    try { fs.unlinkSync(tmpPath); } catch {}
    throw err;
  } finally {
    fs.closeSync(fd);
  }
}

module.exports = {
  decrypt,
  parseKeyMap,
  serializeKeyMap,
  memoryKeyProvider,
  decodeFile,
  PROBE_SIZE,
  // Re-exports for testability
  header,
  ekey,
  qmc2,
};
