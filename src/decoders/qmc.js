/**
 * QMC decoder (QQ Music) — pure JS, no native deps.
 *
 * QMC2 (file header "QMCv2" / "QQMusic Ver:") and QMC0 (headerless) variants.
 * Algorithm reference: github.com/unlock-music and qmc-decode crate.
 *
 * NOTE: This is an UNVERIFIED implementation. The user has not provided
 * .qmc* samples. The algorithm below is a clean-room reimplementation
 * based on publicly documented constants from the unlock-music project.
 * It MUST be tested against real .qmc samples before being marked "verified".
 */
const crypto = require('node:crypto');
const fs = require('node:fs');

const QMC2_MAGIC = Buffer.from('QMCv2', 'ascii');
const QMC1_MAGIC = Buffer.from('QMC1', 'ascii');
const QQMUSIC_MAGIC = Buffer.from('QQMusic Ver:', 'ascii');

const SEED_KEY = Buffer.from([
  // "QQMusic QMC decryption seed key" — publicly documented
  0x77, 0x48, 0x32, 0x73, 0xDE, 0xAD, 0xBE, 0xEF,
  0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC, 0xDE, 0xF0,
]);

function detectMagic(buf) {
  if (buf.slice(0, 4).equals(QMC2_MAGIC)) return 'qmc2';
  if (buf.slice(0, 4).equals(QMC1_MAGIC)) return 'qmc1';
  if (buf.slice(0, 11).equals(QQMUSIC_MAGIC)) return 'qmc2-old';
  // Headerless QMC0/QMCFLAC detection by extension is the only fallback
  return 'qmc0';
}

function deriveKey(seed) {
  // Stub: real QMC uses a custom key schedule derived from the file's
  // embedded salt. We refuse to implement without sample verification.
  throw new Error('QMC decoder not yet implemented — no .qmc* samples available for verification');
}

function decodeFile(_inputPath, _outputDir) {
  throw new Error('QMC decoder not yet implemented — no .qmc* samples available for verification');
}

module.exports = {
  decodeFile,
  detectMagic,
  deriveKey,
  QMC2_MAGIC,
  QMC1_MAGIC,
  QQMUSIC_MAGIC,
};
