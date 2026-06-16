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
};

function pickDecoder(filePath) {
  const ext = (filePath.match(/\.[^./]+$/) || [''])[0].toLowerCase();
  return EXT_TO_DECODER[ext];
}

function listSupported() {
  return Object.keys(EXT_TO_DECODER);
}

function listImplemented() {
  // Only formats where decodeFile actually does something (not stubs)
  return [
    '.ncm',
    '.qmc0', '.qmc3', '.qmcflac', '.qmcogg',
    '.mflac', '.mflac0', '.mgg', '.mgg1',
    '.kgm', '.kgma', '.vpr',
    '.kwm',
    '.bkc',
  ];
}

function listRequiresEkey() {
  // Formats that need a user-provided ekey to decrypt
  return ['.mflac', '.mflac0', '.mgg', '.mgg1', '.bkc'];
}

module.exports = {
  pickDecoder,
  listSupported,
  listImplemented,
  listRequiresEkey,
  ncm,
  qmc,
  kgm,
  kwm,
};
