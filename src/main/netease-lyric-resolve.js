'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { findLrc } = require('./netease-lyric-lookup');
const { getNeteaseLyricJson } = require('./netease-lyric-http');
const { toLrc } = require('./netease-lyric-parser');

function exists(p) {
  try {
    return Boolean(p) && fs.existsSync(p);
  } catch {
    return false;
  }
}

function defaultLyricRoots() {
  const candidates = [];
  if (process.env.LOCALAPPDATA) {
    candidates.push(path.join(process.env.LOCALAPPDATA, 'Netease', 'CloudMusic'));
  }
  candidates.push(path.join(os.homedir(), 'Library', 'Containers', 'com.netease.163music', 'Data'));
  candidates.push(path.join(os.homedir(), '.config', 'NetEase', 'Cloud Music'));
  return candidates.filter(exists);
}

function findRootForCandidate(candidate) {
  if (!exists(candidate)) return null;
  const base = path.basename(candidate);
  if (base === 'LrcDownload' || base === 'LrcCache') {
    return path.dirname(candidate);
  }
  return candidate;
}

async function resolveLrc(opts = {}) {
  const { musicId, enabled, extraRootDir } = opts;
  if (enabled === false) return null;

  const fetchJson = typeof opts.fetchJson === 'function' ? opts.fetchJson : getNeteaseLyricJson;
  const listRoots = typeof opts.listRoots === 'function' ? opts.listRoots : defaultLyricRoots;

  const candidates = [];
  if (extraRootDir) candidates.push(extraRootDir);
  try {
    const listed = listRoots();
    if (Array.isArray(listed)) candidates.push(...listed);
  } catch {
    // Fail-open: a throwing listRoots must not abort resolve.
  }

  for (const candidate of candidates) {
    try {
      const root = findRootForCandidate(candidate);
      if (!root) continue;
      const hit = findLrc(root, musicId);
      if (hit != null) return hit;
    } catch {
      // Fail-open: missing or unreadable roots must not throw.
    }
  }

  try {
    const body = await fetchJson(musicId);
    return toLrc(body);
  } catch {
    return null;
  }
}

module.exports = { resolveLrc, defaultLyricRoots };
