'use strict';

function formatTs(millis) {
  const totalCs = Math.max(0, Math.floor(Number(millis) / 10));
  const minutes = Math.floor(totalCs / 6000);
  const seconds = Math.floor((totalCs % 6000) / 100);
  const cs = totalCs % 100;
  return `[${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(cs).padStart(2, '0')}]`;
}

function isBlank(value) {
  return value == null || String(value).trim() === '';
}

function decodeHtmlEntities(text) {
  if (text == null || typeof text !== 'string') return text;
  return text
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, code) => String.fromCharCode(Number(code)))
    .replace(/&#x([0-9a-fA-F]+);/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
    .replace(/&amp;/g, '&');
}

const PURE_MUSIC_PATTERNS = [
  /纯音乐[，,\s]*请欣赏/i,
  /本歌曲为纯音乐[，,\s]*无歌词/i,
  /该歌曲为纯音乐/i,
  /暂无歌词/i,
  /没有填词/i,
  /没有歌词/i,
  /纯音乐/i,
];

function isPureMusicPlaceholder(text) {
  if (isBlank(text)) return false;
  const lines = splitLines(text)
    .map((l) => l.trim())
    .filter((l) => l.length > 0);
  if (lines.length === 0) return false;

  let hasContent = false;
  for (const line of lines) {
    const content = line.replace(/\[\d{2}:\d{2}(?:\.\d{1,3})?\]/g, '').trim();
    if (!content) continue;
    hasContent = true;
    const isPlaceholder = PURE_MUSIC_PATTERNS.some((pat) => pat.test(content));
    if (!isPlaceholder) {
      return false;
    }
  }
  return hasContent;
}

function lyricText(node) {
  if (node && typeof node === 'object' && !Array.isArray(node)) {
    const lyric = node.lyric;
    if (typeof lyric === 'string' && !isBlank(lyric)) return lyric;
    return null;
  }
  if (typeof node === 'string' && !isBlank(node)) return node;
  return null;
}

function extractLrcInfo(text) {
  if (!text.startsWith('{')) return null;
  try {
    const obj = JSON.parse(text);
    if (obj == null || typeof obj !== 'object' || Array.isArray(obj)) return null;
    if (obj.nolyric === true || obj.uncollected === true) return { nolyric: true };
    if (!Object.prototype.hasOwnProperty.call(obj, 'lrc') || obj.lrc == null) return null;
    const lrcNode = obj.lrc;
    const body = lyricText(lrcNode);
    if (body == null) return null;
    let translated = null;
    if (lrcNode && typeof lrcNode === 'object' && !Array.isArray(lrcNode)) {
      translated = lyricText(lrcNode.tlyric);
    }
    if (translated == null) translated = lyricText(obj.tlyric);
    return { body, translated };
  } catch {
    return null;
  }
}

function splitLines(body) {
  return body.split(/\r\n|\n|\r/);
}

function looksLikeLrc(body) {
  const lines = splitLines(body).map((line) => line.trim()).filter((line) => line.length > 0);
  if (lines.length === 0) return false;
  if (lines.some((line) => line.startsWith('{'))) return false;
  return lines.some((line) => /^\[\d{2}:\d{2}/.test(line));
}

function parseNestedLine(line) {
  if (!line.startsWith('{')) return null;
  try {
    const obj = JSON.parse(line);
    if (obj == null || typeof obj !== 'object' || Array.isArray(obj)) return null;
    const t = obj.t;
    const millis = typeof t === 'number' ? t : Number(t);
    if (!Number.isFinite(millis) || millis < 0) return null;
    const parts = Array.isArray(obj.c) ? obj.c : [];
    let text = '';
    for (const part of parts) {
      if (part == null || typeof part !== 'object' || Array.isArray(part)) continue;
      if (part.tx == null) continue;
      text += String(part.tx);
    }
    return `${formatTs(millis)}${text}`;
  } catch {
    return null;
  }
}

function convertBody(body) {
  const trimmed = body.trim();
  if (looksLikeLrc(trimmed)) return trimmed.replace(/\r\n/g, '\n');
  const lines = splitLines(trimmed).map((line) => line.trim()).filter((line) => line.length > 0);
  if (lines.length === 0) return null;
  const out = [];
  for (const line of lines) {
    const parsed = parseNestedLine(line);
    if (parsed == null) return out.length === 0 ? null : out.join('\n');
    out.push(parsed);
  }
  return out.join('\n');
}

function parseLrcLineTimestamp(line) {
  const match = /^\[(\d{2}):(\d{2}(?:\.\d{1,3})?)\]/.exec(line);
  if (!match) return null;
  const min = parseInt(match[1], 10);
  const sec = parseFloat(match[2]);
  return Math.round((min * 60 + sec) * 1000);
}

function mergeLrcWithTranslation(origLrc, transLrc) {
  if (!transLrc || isBlank(transLrc)) return origLrc;
  const origLines = splitLines(origLrc).map((l) => l.trim()).filter((l) => l.length > 0);
  const transLines = splitLines(transLrc).map((l) => l.trim()).filter((l) => l.length > 0);
  if (transLines.length === 0) return origLrc;

  const entries = [];
  origLines.forEach((raw, idx) => {
    const ts = parseLrcLineTimestamp(raw);
    entries.push({ raw, ts, isTrans: false, order: idx });
  });

  transLines.forEach((raw, idx) => {
    const ts = parseLrcLineTimestamp(raw);
    entries.push({ raw, ts, isTrans: true, order: idx });
  });

  const headerLines = [];
  const timedLines = [];

  for (const entry of entries) {
    if (entry.ts == null) {
      if (!entry.isTrans) headerLines.push(entry.raw);
    } else {
      timedLines.push(entry);
    }
  }

  timedLines.sort((a, b) => {
    if (a.ts !== b.ts) return a.ts - b.ts;
    if (a.isTrans !== b.isTrans) return a.isTrans ? 1 : -1;
    return a.order - b.order;
  });

  const merged = [...headerLines, ...timedLines.map((e) => e.raw)];
  return merged.join('\n');
}

function toLrc(input) {
  if (input == null) return null;
  let text;
  if (Buffer.isBuffer(input)) {
    if (input.length === 0) return null;
    text = input.toString('utf8').trim();
  } else {
    text = String(input).trim();
  }
  if (text.length === 0) return null;

  const lrcInfo = extractLrcInfo(text);
  if (lrcInfo?.nolyric) return null;

  const body = lrcInfo ? lrcInfo.body : text;
  const converted = convertBody(body);
  if (converted == null || isBlank(converted)) return null;

  let finalLrc = converted;
  if (lrcInfo?.translated) {
    const convertedTrans = convertBody(lrcInfo.translated);
    finalLrc = mergeLrcWithTranslation(finalLrc, convertedTrans);
  }

  finalLrc = decodeHtmlEntities(finalLrc);
  if (isPureMusicPlaceholder(finalLrc)) return null;

  return finalLrc;
}

module.exports = { toLrc };
