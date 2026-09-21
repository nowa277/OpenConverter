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

function lyricText(node) {
  if (node && typeof node === 'object' && !Array.isArray(node)) {
    const lyric = node.lyric;
    if (typeof lyric === 'string' && !isBlank(lyric)) return lyric;
    return null;
  }
  if (typeof node === 'string' && !isBlank(node)) return node;
  return null;
}

function extractLrcField(text) {
  if (!text.startsWith('{')) return null;
  try {
    const obj = JSON.parse(text);
    if (obj == null || typeof obj !== 'object' || Array.isArray(obj)) return null;
    if (!Object.prototype.hasOwnProperty.call(obj, 'lrc') || obj.lrc == null) return null;
    const lrcNode = obj.lrc;
    const body = lyricText(lrcNode);
    if (body == null) return null;
    let translated = null;
    if (lrcNode && typeof lrcNode === 'object' && !Array.isArray(lrcNode)) {
      translated = lyricText(lrcNode.tlyric);
    }
    if (translated == null) translated = lyricText(obj.tlyric);
    if (isBlank(translated)) return body;
    return `${body}\n${translated}`;
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
  const lrcField = extractLrcField(text);
  const body = lrcField != null ? lrcField : text;
  const converted = convertBody(body);
  if (converted == null || isBlank(converted)) return null;
  return converted;
}

module.exports = { toLrc };
