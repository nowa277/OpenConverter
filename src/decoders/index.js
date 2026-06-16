/**
 * Decoder router: pick decoder by file extension.
 */
const ncm = require('./ncm');
const qmc = require('./qmc');
const kgm = require('./kgm');
const kwm = require('./kwm');

const EXT_TO_DECODER = {
  '.ncm': ncm,
  '.qmc0': qmc,
  '.qmc1': qmc,
  '.qmc2': qmc,
  '.qmc3': qmc,
  '.qmcflac': qmc,
  '.qmcogg': qmc,
  '.qmcogg': qmc,
  '.tkm': qmc,
  '.kgm': kgm,
  '.kgma': kgm,
  '.kwm': kwm,
  '.bkc': null, // unimplemented
  '.vpr': null, // unimplemented
  '.mflac': null, // unimplemented
  '.mgg': null, // unimplemented
  '.mgg1': null, // unimplemented
};

function pickDecoder(filePath) {
  const ext = (filePath.match(/\.[^./]+$/) || [''])[0].toLowerCase();
  return EXT_TO_DECODER[ext];
}

function listSupported() {
  return Object.entries(EXT_TO_DECODER)
    .filter(([, d]) => d !== null)
    .map(([ext]) => ext);
}

function listImplemented() {
  // Only formats where decodeFile actually does something (not stubs)
  return ['.ncm', '.qmc0', '.qmc3', '.qmcflac', '.qmcogg'];
}

module.exports = {
  pickDecoder,
  listSupported,
  listImplemented,
  ncm,
  qmc,
  kgm,
  kwm,
};
