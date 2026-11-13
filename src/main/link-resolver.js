'use strict';

/**
 * Link resolver — Bilibili direct API + yt-dlp sidecar for YouTube.
 *
 * Electron-free so it can be unit tested with injected fetch/spawn. All
 * endpoint behaviors verified live on 2026-11-13 (see
 * reports/openconverter_round3_20261113/link_audio_probe_report.md):
 *
 *   - view/playurl (fnval=16) return DASH audio without login for public
 *     videos; the audio CDN requires `Referer: https://www.bilibili.com`
 *     and non-HEAD reads (HEAD answers a non-standard 567).
 *   - A browser-like UA without cookies trips Bilibili's 412 risk control,
 *     so requests deliberately send no User-Agent at all.
 *   - yt-dlp's YouTube extraction works without a JS runtime for audio
 *     formats (WARNING only); its Bilibili extractor currently fails.
 *
 * Compliance boundary (B1 report): public, free, user-accessible content
 * only; no DRM / paywall / region bypass; cookies stay on the user's
 * machine and are never logged or uploaded.
 */

const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const BILI_REFERER = 'https://www.bilibili.com';

/** Classified failure — `kind` maps to a user-facing message in the renderer. */
class LinkError extends Error {
  constructor(kind, message) {
    super(message);
    this.name = 'LinkError';
    this.kind = kind; // unsupported | not_found | forbidden | risk_control | component_missing | network | platform
  }
}

function classifyBiliCode(code) {
  if (code === -404) return new LinkError('not_found', '视频不存在、已删除或为私密视频');
  if (code === -352 || code === -412) return new LinkError('risk_control', '平台风控或接口变更，请更新解析组件后重试');
  return new LinkError('platform', `平台返回未知错误（code=${code}），接口可能已变更，请更新解析组件`);
}

/** Recognize a supported video link. Returns { platform, bvid?, videoId?, expand? }. */
function parseLink(url) {
  const u = String(url || '').trim();
  if (!u) return null;

  const bvMatch = /bilibili\.com\/video\/(BV[0-9A-Za-z]+)/i.exec(u)
    || /^BV[0-9A-Za-z]{10}$/i.exec(u) && [u];
  if (bvMatch) return { platform: 'bilibili', bvid: bvMatch[1] || u };

  if (/^https?:\/\/b23\.tv\//i.test(u)) return { platform: 'bilibili', expand: true, url: u };

  const ytMatch = /youtube\.com\/(?:watch\?(?:.*&)?v=|shorts\/|live\/)([A-Za-z0-9_-]{6,})/i.exec(u)
    || /youtu\.be\/([A-Za-z0-9_-]{6,})/i.exec(u);
  if (ytMatch) return { platform: 'youtube', videoId: ytMatch[1], url: u.startsWith('http') ? u : `https://youtu.be/${ytMatch[1]}` };

  return null;
}

/** Follow b23.tv short links to their final URL. */
async function expandShortLink(url, opts = {}) {
  const fetchImpl = opts.fetchImpl || fetch;
  try {
    const res = await fetchImpl(url, { redirect: 'follow', signal: opts.signal });
    if (res && res.url && /bilibili\.com/i.test(res.url)) return res.url;
  } catch (err) {
    if (opts.signal?.aborted) throw new Error('aborted');
    throw new LinkError('network', '网络失败：短链接展开失败，请检查网络');
  }
  throw new LinkError('unsupported', '短链接未能展开为 Bilibili 视频页');
}

async function fetchJson(url, opts = {}) {
  const fetchImpl = opts.fetchImpl || fetch;
  let res;
  try {
    res = await fetchImpl(url, { signal: opts.signal });
  } catch (err) {
    if (opts.signal?.aborted) throw new Error('aborted');
    throw new LinkError('network', '网络失败：无法连接到 Bilibili，请检查网络');
  }
  if (!res.ok) {
    if (res.status === 412) throw new LinkError('risk_control', '平台风控或接口变更，请更新解析组件后重试');
    throw new LinkError('network', `网络失败：HTTP ${res.status}`);
  }
  let data;
  try {
    data = JSON.parse(await res.text());
  } catch {
    throw new LinkError('platform', '接口返回异常，平台接口可能已变更，请更新解析组件');
  }
  return data;
}

/** Bilibili view API → { platform, bvid, title, pages, view }. */
async function resolveBilibili(bvid, opts = {}) {
  const data = await fetchJson(`https://api.bilibili.com/x/web-interface/view?bvid=${encodeURIComponent(bvid)}`, opts);
  if (data.code !== 0 || !data.data) throw classifyBiliCode(data.code);

  const d = data.data;
  const pages = (d.pages || []).map((p) => ({
    page: p.page,
    part: p.part || d.title,
    cid: p.cid,
    durationSec: p.duration || d.duration || null,
  }));
  if (pages.length === 0) throw new LinkError('platform', '接口返回异常：未找到任何分P，平台接口可能已变更');
  return { platform: 'bilibili', bvid, title: d.title, pages, view: d };
}

/** Bilibili playurl (fnval=16) → highest-bandwidth DASH audio stream info. */
async function fetchBilibiliStream(view, page, opts = {}) {
  const pages = view.pages || [];
  const p = pages.find((x) => x.page === page) || pages[0];
  const cid = p && p.cid;
  if (!cid) throw new LinkError('platform', '接口返回异常：缺少分P cid，平台接口可能已变更');

  const data = await fetchJson(
    `https://api.bilibili.com/x/player/playurl?bvid=${encodeURIComponent(view.bvid)}&cid=${cid}&fnval=16`,
    opts,
  );
  if (data.code !== 0 || !data.data) throw classifyBiliCode(data.code);

  const audios = data.data.dash?.audio || [];
  if (audios.length === 0) throw new LinkError('platform', '接口返回异常：未找到可用音轨，平台接口可能已变更');
  const best = audios.reduce((a, b) => (Number(b.bandwidth || 0) > Number(a.bandwidth || 0) ? b : a));
  return {
    url: best.baseUrl || best.base_url,
    backupUrl: best.backupUrl || best.backup_url || [],
    bandwidth: Number(best.bandwidth || 0),
    codec: best.codecs || best.codec || 'mp4a',
    durationMs: Number(data.data.timelength || 0) || null,
  };
}

/** Download a Bilibili DASH audio stream (AAC/fMP4 → save as .m4a). */
async function downloadBilibiliAudio(stream, destPath, opts = {}) {
  const fetchImpl = opts.fetchImpl || fetch;
  const onProgress = opts.onProgress || (() => {});
  let res;
  try {
    // The CDN rejects HEAD (non-standard 567) and requests without Referer,
    // so this must stay a plain GET carrying the site Referer.
    res = await fetchImpl(stream.url, { headers: { Referer: BILI_REFERER }, signal: opts.signal });
    if (!res.ok) {
      // Try a backup mirror once; some CDNs answer differently per region.
      const backup = (stream.backupUrl || [])[0];
      if (!backup) throw new LinkError('network', `网络失败：音频下载 HTTP ${res.status}`);
      res = await fetchImpl(backup, { headers: { Referer: BILI_REFERER }, signal: opts.signal });
      if (!res.ok) throw new LinkError('network', `网络失败：音频下载 HTTP ${res.status}`);
    }
  } catch (err) {
    if (err instanceof LinkError) throw err;
    if (opts.signal?.aborted) throw new Error('aborted');
    throw new LinkError('network', '网络失败：音频下载连接中断，请检查网络');
  }

  const total = Number(res.headers.get('content-length') || 0);
  let received = 0;
  const out = fs.createWriteStream(destPath);
  const reader = res.body.getReader();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      received += value.length;
      if (opts.signal?.aborted) { reader.cancel(); throw new Error('aborted'); }
      if (!out.write(Buffer.from(value))) {
        await new Promise((resolve) => out.once('drain', resolve));
      }
      if (total > 0) onProgress({ received, total, percent: Math.min(100, (received / total) * 100) });
    }
  } finally {
    await new Promise((resolve) => out.end(resolve));
  }
  return { tempPath: destPath, bytes: received, durationMs: stream.durationMs || null };
}

function findYtDlp(opts = {}) {
  return new Promise((resolve, reject) => {
    const bin = opts.ytdlpPath || 'yt-dlp';
    const spawnFn = opts.spawnImpl || spawnImpl; // injectable for tests
    const proc = spawnFn(bin, ['--version'], opts.signal ? { signal: opts.signal } : undefined);
    proc.on('error', () => reject(new LinkError(
      'component_missing',
      '解析组件缺失（YouTube）：请安装 yt-dlp（https://github.com/yt-dlp/yt-dlp）并确保其在 PATH 中，或在设置中指定其路径',
    )));
    let out = '';
    proc.stdout.on('data', (c) => { out += c.toString(); });
    proc.on('close', (code) => {
      if (code === 0 && out.trim()) resolve({ bin, version: out.trim() });
      else reject(new LinkError('component_missing', '解析组件异常（YouTube）：yt-dlp 无法运行，请更新后重试'));
    });
  });
}

function runYtDlp(args, opts = {}) {
  return new Promise((resolve, reject) => {
    const spawnFn = opts.spawnImpl || spawnImpl; // injectable for tests
    const proc = spawnFn(opts.bin || 'yt-dlp', args, { windowsHide: true });
    let stdout = '', stderr = '';
    proc.stdout.on('data', (c) => { stdout += c.toString(); opts.onStdout?.(c.toString()); });
    proc.stderr.on('data', (c) => { stderr += c.toString(); });
    const onAbort = () => proc.kill('SIGTERM');
    if (opts.signal) {
      if (opts.signal.aborted) { proc.kill('SIGTERM'); reject(new Error('aborted')); return; }
      opts.signal.addEventListener('abort', onAbort, { once: true });
    }
    proc.on('close', (code) => {
      if (opts.signal) opts.signal.removeEventListener('abort', onAbort);
      if (opts.signal?.aborted) { reject(new Error('aborted')); return; }
      if (code === 0) { resolve({ stdout, stderr }); return; }
      const errLines = stderr.trim().split(/\r?\n/).filter((l) => /ERROR|error/i.test(l));
      const tail = (errLines.length ? errLines.slice(-2) : stderr.trim().split(/\r?\n/).filter(Boolean).slice(-3)).join(' | ');
      if (/Sign in to confirm|cookies|age.?restrict/i.test(stderr)) {
        reject(new LinkError('forbidden', '需要登录或年龄验证：该内容需登录态访问。请在设置中配置 yt-dlp cookie 文件（仅存本机，不上传）'));
      } else if (/Video unavailable|Private video|removed by the uploader/i.test(stderr)) {
        reject(new LinkError('not_found', '视频不存在、已删除或为私密视频'));
      } else if (/Unsupported URL|No video formats/i.test(tail)) {
        reject(new LinkError('unsupported', '链接不支持：yt-dlp 无法解析该地址'));
      } else {
        reject(new LinkError('platform', `YouTube 解析失败：${tail || `exit ${code}`}`));
      }
    });
    proc.on('error', (err) => {
      if (opts.signal) opts.signal.removeEventListener('abort', onAbort);
      reject(new LinkError('component_missing', `解析组件异常（YouTube）：${err.message}`));
    });
  });
}

/** YouTube via yt-dlp sidecar: dump metadata as JSON. */
async function resolveYouTube(url, opts = {}) {
  const { bin } = await findYtDlp(opts);
  const args = ['-J', '--no-playlist'];
  if (opts.ffmpegBin) args.push('--ffmpeg-location', opts.ffmpegBin);
  if (opts.cookiesPath) args.push('--cookies', opts.cookiesPath);
  args.push(url);
  const { stdout } = await runYtDlp(args, { ...opts, bin });
  let meta;
  try {
    meta = JSON.parse(stdout);
  } catch {
    throw new LinkError('platform', 'YouTube 解析结果异常，请更新 yt-dlp 后重试');
  }
  return {
    platform: 'youtube',
    videoId: meta.id,
    title: meta.title || 'YouTube audio',
    pages: [{
      page: 1,
      part: meta.title || 'audio',
      durationSec: Number(meta.duration) || null,
    }],
    _meta: { duration: meta.duration, uploader: meta.uploader },
  };
}

/** Download YouTube best-audio, extracted to .m4a by the sidecar's ffmpeg. */
async function downloadYouTubeAudio(url, destBase, opts = {}) {
  const { bin } = await findYtDlp(opts);
  const onProgress = opts.onProgress || (() => {});
  const args = ['-x', '--audio-format', 'm4a', '--audio-quality', '0', '--no-playlist', '--newline', '-o', `${destBase}.%(ext)s`];
  if (opts.ffmpegBin) args.push('--ffmpeg-location', opts.ffmpegBin);
  if (opts.cookiesPath) args.push('--cookies', opts.cookiesPath);
  args.push(url);
  await runYtDlp(args, {
    ...opts,
    bin,
    onStdout: (chunk) => {
      for (const m of chunk.matchAll(/\[download\]\s+([\d.]+)%/g)) {
        onProgress({ percent: Math.min(100, parseFloat(m[1])) });
      }
    },
  });
  const produced = fs.readdirSync(path.dirname(destBase)).find((f) => f.startsWith(path.basename(destBase)) && f.endsWith('.m4a'));
  if (!produced) throw new LinkError('platform', 'YouTube 音频提取失败：未产生输出文件，请更新 yt-dlp 后重试');
  return { tempPath: path.join(path.dirname(destBase), produced) };
}

/** Filename-safe title, for output naming. */
function sanitizeFilename(name) {
  return String(name || '')
    .replace(/[\\/:*?"<>|\x00-\x1f]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^[.\s]+|[.\s]+$/g, '')
    .slice(0, 120) || 'audio';
}

// Test seam — real spawn by default.
const spawnImpl = (...args) => spawn(...args);

module.exports = {
  LinkError,
  BILI_REFERER,
  classifyBiliCode,
  parseLink,
  expandShortLink,
  resolveLink: async (url, opts = {}) => {
    const parsed = parseLink(url);
    if (!parsed) throw new LinkError('unsupported', '链接不支持：目前仅支持 Bilibili 与 YouTube 视频链接');
    if (parsed.expand) {
      const finalUrl = await expandShortLink(parsed.url, opts);
      const reparsed = parseLink(finalUrl);
      if (!reparsed || reparsed.platform !== 'bilibili') throw new LinkError('unsupported', '短链接未能展开为受支持的视频页');
      return resolveBilibili(reparsed.bvid, opts);
    }
    if (parsed.platform === 'bilibili') return resolveBilibili(parsed.bvid, opts);
    return resolveYouTube(parsed.url, opts);
  },
  resolveBilibili,
  fetchBilibiliStream,
  downloadBilibiliAudio,
  resolveYouTube,
  downloadYouTubeAudio,
  findYtDlp,
  sanitizeFilename,
};
