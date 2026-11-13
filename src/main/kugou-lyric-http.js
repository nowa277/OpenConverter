'use strict';

/**
 * KuGou lyrics search & download client.
 *
 * References:
 * - 52pojie thread-453069 (2016-02-07), verified active on 2026-11-11.
 *
 * Note on 2026 verification:
 * - `http://lyrics.kugou.com/search` requires `hash=<audioHash>` to return matching candidates.
 * - `http://lyrics.kugou.com/download` returns base64 encoded LRC or KRC text.
 */

const http = require('node:http');
const https = require('node:https');
const { URL } = require('node:url');
const { decodeKrcToLrc } = require('./krc-decoder');

const DEFAULT_TIMEOUT = 15000;

function defaultFetchImpl(url, { headers, timeoutMs, signal } = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const lib = u.protocol === 'https:' ? https : http;
    const req = lib.request(u, { method: 'GET', headers, signal }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ status: res.statusCode || 0, body: Buffer.concat(chunks).toString('utf8') }));
    });
    req.setTimeout(timeoutMs || DEFAULT_TIMEOUT, () => { req.destroy(new Error('timeout')); });
    req.on('error', reject);
    req.end();
  });
}

async function searchKugouCandidate(hash, opts = {}) {
  if (!hash || String(hash).trim() === '') return null;
  const fetchImpl = opts.fetchImpl || defaultFetchImpl;
  const url = `http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=${encodeURIComponent(String(hash).trim())}`;
  try {
    const res = await fetchImpl(url, {
      headers: { 'User-Agent': 'Mozilla/5.0' },
      timeoutMs: opts.timeoutMs || DEFAULT_TIMEOUT,
      signal: opts.signal,
    });
    if (!res || res.status < 200 || res.status > 299 || !res.body) return null;
    const data = JSON.parse(res.body);
    if (data.status === 200 && Array.isArray(data.candidates) && data.candidates.length > 0) {
      return data.candidates[0];
    }
  } catch (err) {
    if (opts.signal?.aborted) throw new Error('aborted');
  }
  return null;
}

async function downloadKugouLyric(id, accesskey, opts = {}) {
  if (!id || !accesskey) return null;
  const fetchImpl = opts.fetchImpl || defaultFetchImpl;
  const url = `http://lyrics.kugou.com/download?ver=1&client=pc&id=${encodeURIComponent(id)}&accesskey=${encodeURIComponent(accesskey)}&fmt=lrc&charset=utf8`;
  try {
    const res = await fetchImpl(url, {
      headers: { 'User-Agent': 'Mozilla/5.0' },
      timeoutMs: opts.timeoutMs || DEFAULT_TIMEOUT,
      signal: opts.signal,
    });
    if (!res || res.status < 200 || res.status > 299 || !res.body) return null;
    const data = JSON.parse(res.body);
    if (data.status === 200 && data.content) {
      const rawBuf = Buffer.from(data.content, 'base64');
      if (rawBuf.length >= 4 && rawBuf.subarray(0, 4).toString() === 'krc1') {
        return decodeKrcToLrc(rawBuf);
      }
      return rawBuf.toString('utf8').replace(/^﻿/, '').trim();
    }
  } catch (err) {
    if (opts.signal?.aborted) throw new Error('aborted');
  }
  return null;
}

async function fetchKugouLyricByHash(hash, opts = {}) {
  const candidate = await searchKugouCandidate(hash, opts);
  if (!candidate || !candidate.id || !candidate.accesskey) return null;
  return downloadKugouLyric(candidate.id, candidate.accesskey, opts);
}

/**
 * Keyword search on the same endpoint. Discovered live on 2026-11-13: the
 * server's paramter_error message ("empty hash and keyword") reveals a
 * `keyword` parameter; verified returning candidates for Chinese queries
 * ("晴天 周杰伦" → 10 hits). English single-word queries may return 0 hits.
 */
async function searchKugouCandidatesByKeyword(keyword, opts = {}) {
  const kw = String(keyword || '').trim();
  if (!kw) return [];
  const fetchImpl = opts.fetchImpl || defaultFetchImpl;
  const url = `http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${encodeURIComponent(kw)}`;
  try {
    const res = await fetchImpl(url, {
      headers: { 'User-Agent': 'Mozilla/5.0' },
      timeoutMs: opts.timeoutMs || DEFAULT_TIMEOUT,
      signal: opts.signal,
    });
    if (!res || res.status < 200 || res.status > 299 || !res.body) return [];
    const data = JSON.parse(res.body);
    if (data.status === 200 && Array.isArray(data.candidates)) return data.candidates;
  } catch (err) {
    if (opts.signal?.aborted) throw new Error('aborted');
  }
  return [];
}

/**
 * Search by decoded tags and download the matching lyric. Keyword ranking is
 * loose (first hit may be a remix), so when the audio duration is known we
 * pick the candidate with the closest `duration` (milliseconds) instead of
 * blindly taking the first.
 */
async function fetchKugouLyricByTags({ title, artist, durationSec } = {}, opts = {}) {
  const kw = [artist, title].filter((p) => p && String(p).trim()).map((p) => String(p).trim()).join(' ');
  if (!kw) return null;
  const candidates = await searchKugouCandidatesByKeyword(kw, opts);
  if (candidates.length === 0) return null;

  let best = candidates[0];
  const durMs = Number(durationSec);
  if (Number.isFinite(durMs) && durMs > 0) {
    let bestGap = Infinity;
    for (const c of candidates) {
      const cd = Number(c && c.duration);
      if (!Number.isFinite(cd)) continue;
      const gap = Math.abs(cd - durMs * 1000);
      if (gap < bestGap) { bestGap = gap; best = c; }
    }
  }
  if (!best || !best.id || !best.accesskey) return null;
  return downloadKugouLyric(best.id, best.accesskey, opts);
}

module.exports = {
  searchKugouCandidate,
  downloadKugouLyric,
  fetchKugouLyricByHash,
  searchKugouCandidatesByKeyword,
  fetchKugouLyricByTags,
  defaultFetchImpl,
};
