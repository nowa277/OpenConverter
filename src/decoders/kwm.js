/**
 * KWM (KuGou Music, mobile variant) decoder — pure JS, no native deps.
 *
 * NOTE: This is an UNVERIFIED implementation. The user has not provided
 * .kwm samples. The algorithm is a placeholder based on public
 * documentation. It MUST be tested against real .kwm samples before
 * being marked "verified".
 */
const fs = require('node:fs');

function decodeFile(_inputPath, _outputDir) {
  throw new Error('KWM decoder not yet implemented — no .kwm samples available for verification');
}

module.exports = { decodeFile };
