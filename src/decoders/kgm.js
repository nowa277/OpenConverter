/**
 * KGM / KWM (KuGou Music) decoder — pure JS, no native deps.
 *
 * NOTE: This is an UNVERIFIED implementation. The user has not provided
 * .kgm or .kwm samples. The algorithm is a placeholder based on public
 * documentation. It MUST be tested against real .kgm/.kwm samples before
 * being marked "verified".
 */
const fs = require('node:fs');

function decodeFile(_inputPath, _outputDir) {
  throw new Error('KGM decoder not yet implemented — no .kgm samples available for verification');
}

module.exports = { decodeFile };
