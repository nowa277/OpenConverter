/**
 * electron-store wrapper. ONLY non-commercial user preferences stored.
 */
const Store = require('electron-store');

const DEFAULTS = {
  format: 'mp3',
  quality: '320k',
  outputDir: '', // empty = let user pick each session
  theme: 'dark',
};

let store = null;
function getStore() {
  if (!store) store = new Store({ defaults: DEFAULTS, name: 'openconverter-config' });
  return store;
}

function get() {
  const s = getStore();
  return {
    format: s.get('format'),
    quality: s.get('quality'),
    outputDir: s.get('outputDir'),
    theme: s.get('theme'),
  };
}

function set(patch) {
  const s = getStore();
  for (const [k, v] of Object.entries(patch || {})) {
    if (k in DEFAULTS) s.set(k, v);
  }
}

module.exports = { get, set };
