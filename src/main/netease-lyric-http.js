'use strict';
const https = require('node:https');
const http = require('node:http');
const { URL } = require('node:url');

const DEFAULT_TIMEOUT = 15000;

function lyricUrl(musicId) {
  return `https://music.163.com/api/song/lyric?id=${encodeURIComponent(String(musicId).trim())}&lv=-1&kv=-1&tv=-1`;
}

function defaultFetchImpl(url, { headers, timeoutMs }) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const lib = u.protocol === 'http:' ? http : https;
    const req = lib.request(u, { method: 'GET', headers }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ status: res.statusCode || 0, body: Buffer.concat(chunks).toString('utf8') }));
    });
    req.setTimeout(timeoutMs, () => { req.destroy(new Error('timeout')); });
    req.on('error', reject);
    req.end();
  });
}

function makeHttpFetch(urlOverride) {
  return (url, opts) => defaultFetchImpl(urlOverride || url, opts);
}

async function getNeteaseLyricJson(musicId, opts = {}) {
  if (musicId == null || String(musicId).trim() === '') return null;
  const fetchImpl = opts.fetchImpl || defaultFetchImpl;
  const timeoutMs = opts.timeoutMs == null ? DEFAULT_TIMEOUT : opts.timeoutMs;
  try {
    const r = await fetchImpl(lyricUrl(musicId), {
      headers: { 'User-Agent': 'Mozilla/5.0', Referer: 'https://music.163.com/' },
      timeoutMs,
    });
    if (!r || r.status < 200 || r.status > 299) return null;
    return r.body == null ? null : String(r.body);
  } catch {
    return null;
  }
}

module.exports = { getNeteaseLyricJson, makeHttpFetch, lyricUrl };
