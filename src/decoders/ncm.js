/**
 * NCM (NetEase Cloud Music) decoder — pure Node crypto, no native deps.
 *
 * Reference algorithm: ncmdump (Nzix/python-ncmdump) — the format is:
 *
 *   Offset  Size  Content
 *   ------  ----  --------------------------------------------------------
 *   0       8     Magic "CTENFDAM"
 *   8       2     Gap (skip)
 *   10      4     key_length (LE)
 *   14      N     Encrypted key data (XOR each byte with 0x64, then
 *                  AES-128-ECB decrypt with core_key, PKCS7 unpad,
 *                  skip 17 bytes "neteasecloudmusic\0")
 *   ...     4     meta_length (LE)  [0 if no meta]
 *   ...     M     Meta JSON (XOR 0x63, base64 decode, AES-128-ECB with meta_key, PKCS7 unpad)
 *   ...     5     Gap
 *   ...     4     image_space (LE)
 *   ...     4     image_size (LE)
 *   ...     S     image bytes
 *   ...     ?     (image_space - image_size) bytes of padding
 *   ...     rest  Audio data (RC4-encrypted with the S-box built from the key)
 *
 * core_key = "687A4852416D736F356B496E62617857" (hex of "hzHRAmso5kInbaxW")
 * meta_key = "2331346C6A6B5F215C5D2630553C2728" (hex of "#14ljk_!\\]&0U<'(")
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const MAGIC = Buffer.from('CTENFDAM', 'ascii');
const CORE_KEY = Buffer.from('687A4852416D736F356B496E62617857', 'hex');
const META_KEY = Buffer.from('2331346C6A6B5F215C5D2630553C2728', 'hex');
const PREFIX_LEN = 17; // "neteasecloudmusic" + "\0"

function aesEcbDecrypt(block, key) {
  const decipher = crypto.createDecipheriv('aes-128-ecb', key, null);
  decipher.setAutoPadding(false);
  return Buffer.concat([decipher.update(block), decipher.final()]);
}

function pkcs7Unpad(buf) {
  const pad = buf[buf.length - 1];
  if (pad < 1 || pad > 16) throw new Error('Invalid PKCS7 padding');
  return buf.slice(0, buf.length - pad);
}

function xorInPlace(buf, byte) {
  for (let i = 0; i < buf.length; i++) buf[i] ^= byte;
  return buf;
}

function buildRc4Sbox(key) {
  const S = Buffer.alloc(256);
  for (let i = 0; i < 256; i++) S[i] = i;
  let j = 0;
  for (let i = 0; i < 256; i++) {
    j = (j + S[i] + key[i % key.length]) & 0xff;
    const tmp = S[i]; S[i] = S[j]; S[j] = tmp;
  }
  return S;
}

function rc4Decrypt(S, data) {
  // Modified RC4 (from ncmdump): the 256-byte keystream is built from S,
  // then repeated to length data.length + 1, and the first byte is skipped.
  // So for data[i], the keystream byte used is keystream[(i + 1) % 256].
  const out = Buffer.alloc(data.length);
  const k = Buffer.alloc(256);
  for (let i = 0; i < 256; i++) {
    k[i] = S[(S[i] + S[(i + S[i]) & 0xff]) & 0xff];
  }
  for (let i = 0; i < data.length; i++) {
    out[i] = data[i] ^ k[(i + 1) % 256];
  }
  return out;
}

function decryptBuffer(ncmBuf) {
  if (!ncmBuf.slice(0, 8).equals(MAGIC)) {
    throw new Error('not a valid NCM file: missing CTENFDAM magic');
  }
  let off = 10; // skip 8 magic + 2 gap

  const keyLength = ncmBuf.readUInt32LE(off);
  off += 4;
  if (keyLength <= 0 || keyLength > ncmBuf.length) {
    throw new Error(`Invalid key length: ${keyLength}`);
  }
  const keyEnc = Buffer.from(ncmBuf.slice(off, off + keyLength));
  off += keyLength;
  xorInPlace(keyEnc, 0x64);
  let keyPlain = aesEcbDecrypt(keyEnc, CORE_KEY);
  keyPlain = pkcs7Unpad(keyPlain);
  if (keyPlain.length < PREFIX_LEN || keyPlain.slice(0, PREFIX_LEN).toString('ascii') !== 'neteasecloudmusic') {
    throw new Error('Key block did not start with neteasecloudmusic');
  }
  const rc4Key = keyPlain.slice(PREFIX_LEN);
  const S = buildRc4Sbox(rc4Key);

  // Meta
  const metaLength = ncmBuf.readUInt32LE(off);
  off += 4;
  let meta = null;
  if (metaLength > 0) {
    if (off + metaLength > ncmBuf.length) {
      throw new Error('Meta length exceeds file size');
    }
    const metaEnc = Buffer.from(ncmBuf.slice(off, off + metaLength));
    off += metaLength;
    xorInPlace(metaEnc, 0x63);
    const b64 = metaEnc.slice(22).toString('utf8');
    const metaAes = aesEcbDecrypt(Buffer.from(b64, 'base64'), META_KEY);
    const metaJson = pkcs7Unpad(metaAes).toString('utf8').slice(6);
    try {
      meta = JSON.parse(metaJson);
    } catch {
      meta = null;
    }
  }

  // 5-byte gap
  off += 5;

  // Image: 4-byte space, 4-byte size, then image_size bytes, then (image_space - image_size) padding
  const imageSpace = ncmBuf.readUInt32LE(off);
  off += 4;
  const imageSize = ncmBuf.readUInt32LE(off);
  off += 4;
  const imageData = imageSize > 0 ? ncmBuf.slice(off, off + imageSize) : null;
  off += imageSize;
  off += imageSpace - imageSize;

  if (off > ncmBuf.length) {
    throw new Error('Header parsing ran past end of file');
  }
  const encryptedAudio = ncmBuf.slice(off);
  const audio = rc4Decrypt(S, encryptedAudio);

  return { audio, meta, imageData };
}

function inferExtension(meta) {
  if (!meta) return 'mp3';
  const fmt = (meta.format || '').toLowerCase();
  if (fmt === 'flac') return 'flac';
  return 'mp3';
}

/**
 * Flatten the NCM meta JSON into a small set of common tags that can be
 * handed to ffmpeg's `-metadata` (title/artist/album). Returns null when
 * nothing useful is present.
 */
function extractTags(meta) {
  if (!meta || typeof meta !== 'object') return null;
  const tags = {};
  if (meta.musicName) tags.title = String(meta.musicName);
  if (Array.isArray(meta.artist) && meta.artist.length) {
    // artist is [[name, id], ...]
    tags.artist = meta.artist.map((a) => (Array.isArray(a) ? a[0] : a)).filter(Boolean).join(' / ');
  }
  if (meta.album) tags.album = String(meta.album);
  return Object.keys(tags).length ? tags : null;
}

function imageExtension(imageData) {
  if (!imageData || imageData.length < 4) return 'jpg';
  if (imageData[0] === 0x89 && imageData[1] === 0x50) return 'png';
  return 'jpg';
}

/**
 * Decrypt an .ncm file to `<outputDir>/<name>.<mp3|flac>`.
 *
 * When `opts.extractCover` is true (default) and the container carries a
 * cover image, it is written next to the audio as `<name>.cover.<jpg|png>`
 * and returned as `coverPath` so the caller can embed it and delete it.
 */
function readExact(fd, size, pos) {
  const buf = Buffer.alloc(size);
  let got = 0;
  while (got < size) {
    const n = fs.readSync(fd, buf, got, size - got, pos + got);
    if (n <= 0) return buf.subarray(0, got);
    got += n;
  }
  return buf;
}

function decodeFile(inputPath, outputDir, opts = {}) {
  const stat = fs.statSync(inputPath);
  const stagingDir = outputDir || path.dirname(inputPath);
  fs.mkdirSync(stagingDir, { recursive: true });
  const tmpPath = path.join(stagingDir, `.${path.basename(inputPath)}.oc-partial`);
  let fd;
  let outFd;
  try {
    fd = fs.openSync(inputPath, 'r');
    const magic = readExact(fd, 8, 0);
    if (!magic.equals(MAGIC)) throw new Error('not a valid NCM file: missing CTENFDAM magic');
    let pos = 10;
    const keyLength = readExact(fd, 4, pos).readUInt32LE(0);
    pos += 4;
    if (keyLength <= 0 || keyLength > stat.size) throw new Error(`Invalid key length: ${keyLength}`);
    const keyEnc = readExact(fd, keyLength, pos);
    pos += keyLength;
    xorInPlace(keyEnc, 0x64);
    let keyPlain = pkcs7Unpad(aesEcbDecrypt(keyEnc, CORE_KEY));
    if (keyPlain.length < PREFIX_LEN || keyPlain.slice(0, PREFIX_LEN).toString('ascii') !== 'neteasecloudmusic') {
      throw new Error('Key block did not start with neteasecloudmusic');
    }
    const S = buildRc4Sbox(keyPlain.slice(PREFIX_LEN));
    const k = Buffer.alloc(256);
    for (let i = 0; i < 256; i++) k[i] = S[(S[i] + S[(i + S[i]) & 0xff]) & 0xff];

    const metaLength = readExact(fd, 4, pos).readUInt32LE(0);
    pos += 4;
    let meta = null;
    if (metaLength > 0) {
      if (pos + metaLength > stat.size) throw new Error('Meta length exceeds file size');
      const metaEnc = readExact(fd, metaLength, pos);
      pos += metaLength;
      xorInPlace(metaEnc, 0x63);
      const b64 = metaEnc.slice(22).toString('utf8');
      const metaAes = aesEcbDecrypt(Buffer.from(b64, 'base64'), META_KEY);
      const metaJson = pkcs7Unpad(metaAes).toString('utf8').slice(6);
      try { meta = JSON.parse(metaJson); } catch { meta = null; }
    }

    pos += 5;
    const imageSpace = readExact(fd, 4, pos).readUInt32LE(0);
    pos += 4;
    const imageSize = readExact(fd, 4, pos).readUInt32LE(0);
    pos += 4;
    let imageData = null;
    if (imageSize > 0) imageData = readExact(fd, imageSize, pos);
    pos += imageSize;
    pos += imageSpace - imageSize;
    if (pos > stat.size) throw new Error('Header parsing ran past end of file');

    outFd = fs.openSync(tmpPath, 'w');
    const CHUNK = 64 * 1024;
    const buf = Buffer.alloc(CHUNK);
    let audioOffset = 0;
    let filePos = pos;
    let probe = Buffer.alloc(0);
    while (filePos < stat.size) {
      const n = fs.readSync(fd, buf, 0, Math.min(CHUNK, stat.size - filePos), filePos);
      if (n <= 0) break;
      for (let i = 0; i < n; i++) buf[i] ^= k[(audioOffset + i + 1) % 256];
      if (probe.length < 16) probe = Buffer.concat([probe, buf.subarray(0, Math.min(n, 16 - probe.length))]);
      fs.writeSync(outFd, buf, 0, n);
      audioOffset += n;
      filePos += n;
    }
    fs.closeSync(outFd);
    outFd = null;

    const ext = inferExtension(meta);
    const base = inputPath.replace(/\.ncm$/i, '');
    const name = base.split(/[\\/]/).pop();
    const outPath = path.join(stagingDir, `${name}.${ext}`);
    fs.renameSync(tmpPath, outPath);

    let coverPath = null;
    if (imageData && imageData.length > 0 && opts.extractCover !== false) {
      coverPath = path.join(stagingDir, `${name}.cover.${imageExtension(imageData)}`);
      fs.writeFileSync(coverPath, imageData);
    }
    return { outputPath: outPath, format: ext, hasImage: !!imageData, coverPath, tags: extractTags(meta) };
  } catch (err) {
    try { if (outFd != null) fs.closeSync(outFd); } catch {}
    try { fs.unlinkSync(tmpPath); } catch {}
    throw err;
  } finally {
    try { if (fd != null) fs.closeSync(fd); } catch {}
  }
}

module.exports = {
  decryptBuffer,
  decodeFile,
  inferExtension,
  extractTags,
  MAGIC,
};
