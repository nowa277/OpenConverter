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

function decodeFile(inputPath, outputDir) {
  const ncm = fs.readFileSync(inputPath);
  const { audio, meta, imageData } = decryptBuffer(ncm);
  const ext = inferExtension(meta);
  const base = inputPath.replace(/\.ncm$/i, '');
  const name = base.split(/[\\/]/).pop();
  const outName = `${name}.${ext}`;
  const outPath = outputDir ? `${outputDir}/${outName}` : `${base}.${ext}`;
  fs.mkdirSync(outputDir || '.', { recursive: true });
  fs.writeFileSync(outPath, audio);
  return { outputPath: outPath, format: ext, hasImage: !!imageData };
}

module.exports = {
  decryptBuffer,
  decodeFile,
  inferExtension,
  MAGIC,
};
