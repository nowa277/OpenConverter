'use strict';

/**
 * KGG v5 file header parser.
 *
 * Header layout (1024-byte prefix, little-endian):
 *   0x00..0x10  16-byte magic
 *   0x10..0x14  uint32 headerLength
 *   0x14..0x18  uint32 cryptoVersion (3 or 5)
 *   ...         reserved
 *   0x44..0x48  uint32 idLength
 *   0x48..N     idLength bytes of UTF-8 key id (looked up in kdb)
 *   N..headerLength  reserved
 *   headerLength..end  encrypted audio
 *
 * Reference: 1:1 port of android/.../kgg/KggHeader.kt
 *            Public algorithm spec — see unlock-music.
 */
const MAGIC = Buffer.from([
  0x7c, 0xd5, 0x32, 0xeb, 0x86, 0x02, 0x7f, 0x4b,
  0xa8, 0xaf, 0xa6, 0x8e, 0x0f, 0xff, 0x99, 0x14,
]);

const PREFIX_SIZE = 1024;
const ID_LENGTH_OFFSET = 0x44;
const ID_OFFSET = 0x48;
const MAX_ID_LENGTH = 256;

function readLeInt(buf, offset) {
  return (
    (buf[offset] & 0xff) |
    ((buf[offset + 1] & 0xff) << 8) |
    ((buf[offset + 2] & 0xff) << 16) |
    ((buf[offset + 3] & 0xff) << 24) >>> 0
  );
}

function parse(prefix) {
  if (prefix.length < ID_OFFSET) {
    throw new Error(`KGG header is truncated: ${prefix.length} < ${ID_OFFSET}`);
  }
  if (!prefix.subarray(0, MAGIC.length).equals(MAGIC)) {
    throw new Error('KGG magic is invalid');
  }
  const headerLength = readLeInt(prefix, 0x10);
  if (headerLength < ID_OFFSET || headerLength > prefix.length) {
    throw new Error(`KGG header length is invalid: ${headerLength}`);
  }
  const version = readLeInt(prefix, 0x14);
  if (version !== 3 && version !== 5) {
    throw new Error(`Unsupported KGG crypto version: ${version}`);
  }
  const idLength = readLeInt(prefix, ID_LENGTH_OFFSET);
  if (idLength < 1 || idLength > MAX_ID_LENGTH) {
    throw new Error(`KGG key id length is invalid: ${idLength}`);
  }
  if (ID_OFFSET + idLength > headerLength) {
    throw new Error('KGG key id exceeds header length');
  }
  if (ID_OFFSET + idLength > prefix.length) {
    throw new Error('KGG key id is truncated');
  }
  const idBytes = prefix.subarray(ID_OFFSET, ID_OFFSET + idLength);
  // Strict UTF-8 decode
  const id = idBytes.toString('utf-8');
  if (id.includes('�')) {
    throw new Error('KGG key id is not valid UTF-8');
  }
  if (id.trim().length === 0) {
    throw new Error('KGG key id is empty');
  }
  return { headerLength, cryptoVersion: version, encryptionKeyId: id };
}

module.exports = { parse, MAGIC, PREFIX_SIZE, ID_OFFSET, MAX_ID_LENGTH };
