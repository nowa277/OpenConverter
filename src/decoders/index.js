/**
 * Decoder router: pick decoder by file extension.
 *
 * Each decoder module exports `decodeFile(inputPath, outputDir, opts?)`.
 * For MFLAC0/MGG1/BKC*, opts.ekey is required (a base64 string from the
 * QQ Music client DB).
 */
const ncm = require('./ncm');
const qmc = require('./qmc');
const kgm = require('./kgm');
const kwm = require('./kwm');
const kgg = require('./kgg');

const EXT_TO_DECODER = {
  // NCM (NetEase)
  '.ncm': ncm,

  // QMCv1 — headerless, no key needed
  '.qmc0': qmc,
  '.qmc3': qmc,
  '.qmcflac': qmc,
  '.qmcogg': qmc,
  '.qmc1': qmc,
  '.qmc2': qmc, // may not be true QMCv1; best-effort
  '.tkm': qmc,

  // QMCv2 — QQ Music mobile/desktop, requires user-provided ekey
  '.mflac': qmc,
  '.mflac0': qmc,
  '.mgg': qmc,
  '.mgg1': qmc,
  '.bkc': qmc,
  '.bkcmp3': qmc,
  '.bkcflac': qmc,
  '.bkcogg': qmc,
  '.bkcm4a': qmc,
  '.bkcwav': qmc,
  '.bkcwma': qmc,
  '.bkcape': qmc,

  // KGM/KGMA/VPR (KuGou)
  '.kgm': kgm,
  '.kgma': kgm,
  '.vpr': kgm,

  // KWM (Kuwo)
  '.kwm': kwm,

  // KGG v5 (KuGou mobile) — needs opts.keyPath (a kgg.key file path)
  '.kgg': kgg,
  '.kgg.flac': kgg, // double extension, picked below
};

function pickDecoder(filePath) {
  const path = require('node:path');
  const base = path.basename(filePath).toLowerCase();
  
  // Sort by length descending to match double extensions (e.g. .kgg.flac) before single (.flac)
  const knownExts = Object.keys(EXT_TO_DECODER).sort((a, b) => b.length - a.length);
  for (const ext of knownExts) {
    if (base.endsWith(ext)) {
      return EXT_TO_DECODER[ext];
    }
  }
  return undefined;
}

function listSupported() {
  return Object.keys(EXT_TO_DECODER);
}

function listImplemented() {
  // Only formats where decodeFile actually does something (not stubs)
  return [
    '.ncm',
    '.qmc0', '.qmc3', '.qmcflac', '.qmcogg', '.qmc1', '.qmc2', '.tkm',
    '.mflac', '.mflac0', '.mgg', '.mgg1',
    '.kgm', '.kgma', '.vpr',
    '.kwm',
    '.bkc',
  ];
}

function listRequiresEkey() {
  // Formats that need a user-provided ekey to decrypt
  return ['.mflac', '.mflac0', '.mflac2', '.mflac4', '.mgg', '.mgg1', '.mgg2', '.mgg4', '.mggl', '.bkc'];
}

/**
 * Metadata for each supported format extension. Used by the UI to show
 * "this format requires importing a key" hints and to list formats in
 * human-readable form.
 *
 * Each entry has:
 *   - platform:    short Chinese label of the source platform
 *   - version:     human-readable version (e.g. "v1", "v2 STag")
 *   - requiresKey: true if the decoder needs a per-file ekey or kdb
 *   - keySource:   where the key comes from ("ekey" / "KGMusicV3.db")
 *   - note:        optional free-form note for best-effort formats
 */
const EXT_METADATA = {
  // NCM (NetEase Cloud Music)
  '.ncm':      { platform: '网易云',           version: 'all',  requiresKey: false },

  // KWM (Kuwo Music)
  '.kwm':      { platform: '酷我',             version: 'all',  requiresKey: false },

  // KGM/KGMA/VPR (KuGou legacy, no key needed — key is per-file in header)
  '.kgm':      { platform: '酷狗',             version: 'v1-v4', requiresKey: false },
  '.kgma':     { platform: '酷狗',             version: 'v3-v4', requiresKey: false },
  '.vpr':      { platform: '酷狗',             version: 'v1-v4', requiresKey: false },

  // QMC v1 (QQ Music cache, headerless, no key)
  '.qmc0':     { platform: 'QQ 音乐',          version: 'v1',   requiresKey: false },
  '.qmc3':     { platform: 'QQ 音乐',          version: 'v1',   requiresKey: false },
  '.qmcflac':  { platform: 'QQ 音乐',          version: 'v1',   requiresKey: false },
  '.qmcogg':   { platform: 'QQ 音乐',          version: 'v1',   requiresKey: false },
  '.qmc1':     { platform: 'QQ 音乐',          version: 'v1',   requiresKey: false },
  '.qmc2':     { platform: 'QQ 音乐',          version: 'v1',   requiresKey: false, note: 'best-effort' },
  '.tkm':      { platform: 'QQ 音乐',          version: 'v1',   requiresKey: false },

  // QMC v2 (QQ Music mobile/desktop, requires user-provided ekey)
  '.mflac':    { platform: 'QQ 音乐',          version: 'v2',        requiresKey: true, keySource: 'ekey' },
  '.mflac0':   { platform: 'QQ 音乐',          version: 'v2',        requiresKey: true, keySource: 'ekey' },
  '.mflac2':   { platform: 'QQ 音乐',          version: 'v2 STag',   requiresKey: true, keySource: 'ekey' },
  '.mflac4':   { platform: 'QQ 音乐',          version: 'v2 STag',   requiresKey: true, keySource: 'ekey' },
  '.mgg':      { platform: 'QQ 音乐',          version: 'v2',        requiresKey: true, keySource: 'ekey' },
  '.mgg1':     { platform: 'QQ 音乐',          version: 'v2',        requiresKey: true, keySource: 'ekey' },
  '.mgg2':     { platform: 'QQ 音乐',          version: 'v2 STag',   requiresKey: true, keySource: 'ekey' },
  '.mgg4':     { platform: 'QQ 音乐',          version: 'v2 STag',   requiresKey: true, keySource: 'ekey' },
  '.mggl':     { platform: 'QQ 音乐',          version: 'v2',        requiresKey: true, keySource: 'ekey' },

  // BKC variants (QQ Music BKC family)
  '.bkc':      { platform: 'QQ 音乐 (BKC)',    version: 'v2',   requiresKey: true,  keySource: 'ekey' },
  '.bkcmp3':   { platform: 'QQ 音乐 (BKC)',    version: 'v2',   requiresKey: true,  keySource: 'ekey' },
  '.bkcflac':  { platform: 'QQ 音乐 (BKC)',    version: 'v2',   requiresKey: true,  keySource: 'ekey' },
  '.bkcogg':   { platform: 'QQ 音乐 (BKC)',    version: 'v2',   requiresKey: true,  keySource: 'ekey' },
  '.bkcm4a':   { platform: 'QQ 音乐 (BKC)',    version: 'v2',   requiresKey: true,  keySource: 'ekey' },
  '.bkcwav':   { platform: 'QQ 音乐 (BKC)',    version: 'v2',   requiresKey: true,  keySource: 'ekey' },
  '.bkcwma':   { platform: 'QQ 音乐 (BKC)',    version: 'v2',   requiresKey: true,  keySource: 'ekey' },
  '.bkcape':   { platform: 'QQ 音乐 (BKC)',    version: 'v2',   requiresKey: true,  keySource: 'ekey' },

  // KGG v5 (KuGou mobile, requires KGMusicV3.db or kgg.key)
  '.kgg':      { platform: '酷狗',             version: 'v5',   requiresKey: true,  keySource: 'KGMusicV3.db' },
  '.kgg.flac': { platform: '酷狗',             version: 'v5',   requiresKey: true,  keySource: 'KGMusicV3.db' },
};

/**
 * Look up metadata for a file extension.
 * @param {string} ext - extension with leading dot (e.g. ".kgg")
 * @returns {object|null} metadata object or null if unknown
 */
function getMetadata(ext) {
  if (!ext) return null;
  return EXT_METADATA[ext.toLowerCase()] || null;
}

/**
 * Group supported extensions by whether they require a key.
 * @returns {{withKey: string[], withoutKey: string[]}}
 */
function listByKeyRequirement() {
  const withKey = [];
  const withoutKey = [];
  for (const [ext, meta] of Object.entries(EXT_METADATA)) {
    (meta.requiresKey ? withKey : withoutKey).push(ext);
  }
  return { withKey, withoutKey };
}

module.exports = {
  pickDecoder,
  listSupported,
  listImplemented,
  listRequiresEkey,
  getMetadata,
  listByKeyRequirement,
  EXT_METADATA,
  ncm,
  qmc,
  kgm,
  kwm,
  kgg,
};
