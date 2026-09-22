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

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isJsonBody(body) {
  if (body == null) return false;
  const text = String(body).trim();
  if (!text.startsWith('{') && !text.startsWith('[')) return false;
  try {
    JSON.parse(text);
    return true;
  } catch {
    return false;
  }
}

async function resolveLrc(opts = {}) {
  const { musicId, enabled, extraRootDir } = opts;
  if (enabled === false) return null;

  const fetchJson = typeof opts.fetchJson === 'function' ? opts.fetchJson : getNeteaseLyricJson;
  const listRoots = typeof opts.listRoots === 'function' ? opts.listRoots : defaultLyricRoots;
  const attempts = Number.isFinite(opts.attempts) ? Math.max(1, opts.attempts) : 3;
  const delayMs = Number.isFinite(opts.delayMs) ? opts.delayMs : 300;

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

  for (let i = 0; i < attempts; i++) {
    let body = null;
    try {
      body = await fetchJson(musicId);
    } catch {
      body = null;
    }
    const lrc = toLrc(body);
    if (lrc != null) return lrc;
    // A parsed JSON body is a real answer (including "this song has no lyric").
    // Null, HTML, and connection errors are not, so try again.
    if (isJsonBody(body)) return null;
    if (i + 1 < attempts && delayMs > 0) await delay(delayMs * (i + 1));
  }
  return null;
}

module.exports = { resolveLrc, defaultLyricRoots };
