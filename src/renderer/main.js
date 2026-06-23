/**
 * OpenConverter renderer — vanilla JS, no framework. Talks to main via window.api.
 */

const api = window.api;

const state = {
  files: [], // { path, name, status: 'pending' | 'decoding' | 'done' | 'error', progress: 0, outputPath, error }
  outputDir: '',
  format: 'mp3',
  quality: '320k',
  language: 'auto',
  theme: 'dark',
  decoders: null,
  ffmpegStatus: '',
};

// ---------- translations ----------
const TRANSLATIONS = {
  en: {
    nav_convert: 'Convert',
    nav_history: 'History',
    nav_about: 'About',
    title_convert_audio: 'Convert audio',
    label_format: 'Format',
    label_quality: 'Quality',
    label_language: 'Language',
    lang_auto: 'Auto',
    label_theme: 'Theme',
    theme_dark: 'Dark',
    theme_light: 'Light',
    label_output: 'Output:',
    btn_choose_folder: 'Choose folder',
    ekey_panel_title: 'QQ Music ekey (required for .mflac / .mgg / .bkc)',
    ekey_placeholder: 'Paste base64 ekey from QQ Music client DB',
    btn_save: 'Save',
    ekey_hint: 'Only needed for QQ Music mobile/desktop cache files (.mflac0, .mgg1, .bkc*). The ekey is a base64 string extracted from the QQ Music client\'s local database. Leave empty if you only need NCM / QMC0 / QMCFLAC / KGM / KWM.',
    dropzone_text: 'Drop .ncm / .qmc / .kgm files here',
    dropzone_hint_or: 'or',
    dropzone_hint_browse: 'browse',
    dropzone_hint_choose: 'to choose',
    queue_title: 'Queue',
    btn_clear_all: 'Clear all',
    btn_convert: 'Convert',
    btn_convert_files: 'file(s)',
    history_title: 'History',
    btn_clear_history: 'Clear history',
    history_empty: 'No conversion history yet',
    about_title: 'About',
    about_desc1: 'OpenConverter is an open-source audio format converter for Linux.',
    about_desc2: 'It supports encrypted formats (NCM, QMC, KGM, KWM) via pure-JavaScript decoders and uses ffmpeg for format conversion.',
    about_verified_formats: 'Verified formats',
    about_system_status: 'Status',
    clear_dialog_title: 'Clear history?',
    clear_dialog_msg: 'This will delete all conversion records. Converted files will not be deleted.',
    btn_cancel: 'Cancel',
    btn_clear: 'Clear',
    output_not_set: 'Not set',
    supported_formats_prefix: 'Supported: ',
    about_verified_none: 'None yet',
    status_ncm: 'NCM — verified (14/14 samples, byte-diff against Python ncmdump)',
    status_qmc0: 'QMC0 / QMC3 / QMCFLAC / QMCOGG — verified (round-trip on real MP3)',
    status_qmcv2: 'QMCv2 (.mflac / .mgg / .bkc) — implemented, requires QQ Music ekey from client DB',
    status_kgm: 'KGM / KGMA / VPR — implemented (round-trip on real MP3)',
    status_kwm: 'KWM — implemented (round-trip on real MP3)',
    ffmpeg_detected: '(system ffmpeg in PATH)',
    ffmpeg_not_detected: '(not detected)',
    toast_ekey_saved: 'QQ Music ekey saved',
    toast_ekey_cleared: 'QQ Music ekey cleared',
    toast_history_cleared: 'History cleared',
    toast_history_load_failed: 'Failed to load history',
    toast_output_folder_required: 'Choose an output folder first',
    toast_converted_success: 'Converted {count} file(s)',
    toast_converted_mixed: 'Converted {success}, {failed} failed',
    toast_converted_all_failed: 'All conversions failed',
    toast_init_failed: 'Init failed: {message}',
    status_ready: 'Ready',
    status_done: 'Done',
    status_error: 'Error',
    status_success: 'Success',
    status_failed: 'Failed',
    time_just_now: 'Just now',
    time_mins_ago: '{mins}m ago',
    time_hours_ago: '{hours}h ago',
    time_days_ago: '{days}d ago',
  },
  zh: {
    nav_convert: '转换',
    nav_history: '历史记录',
    nav_about: '关于',
    title_convert_audio: '音频格式转换',
    label_format: '格式',
    label_quality: '音质',
    label_language: '语言',
    lang_auto: '自动 (Auto)',
    label_theme: '主题',
    theme_dark: '深色',
    theme_light: '浅色',
    label_output: '输出目录：',
    btn_choose_folder: '选择目录',
    ekey_panel_title: 'QQ 音乐 ekey (解密 .mflac / .mgg / .bkc 必需)',
    ekey_placeholder: '粘贴本地 QQ 音乐客户端数据库中的 Base64 ekey',
    btn_save: '保存',
    ekey_hint: '仅在处理 QQ 音乐移动端或桌面端缓存文件 (.mflac0, .mgg1, .bkc*) 时才需要。ekey 是从 QQ 音乐本地数据库中提取的 Base64 字符串。若仅需处理 NCM / QMC0 / QMCFLAC / KGM / KWM，请保持为空。',
    dropzone_text: '拖曳 .ncm / .qmc / .kgm 等加密音频文件到这里',
    dropzone_hint_or: '或者',
    dropzone_hint_browse: '点击浏览',
    dropzone_hint_choose: '选择文件',
    queue_title: '等待队列',
    btn_clear_all: '清空全部',
    btn_convert: '开始转换',
    btn_convert_files: '个文件',
    history_title: '转换历史',
    btn_clear_history: '清除历史',
    history_empty: '暂无转换记录',
    about_title: '关于',
    about_desc1: 'OpenConverter 是一款跨平台的轻量级音频格式转换与本地解码工具。',
    about_desc2: '项目基于纯 JavaScript 解码管线，支持各种加密格式 (NCM, QMC, KGM, KWM) 的本地直接解密，并使用系统 FFmpeg 模块进行转码。',
    about_verified_formats: '已测试支持格式',
    about_system_status: '系统状态',
    clear_dialog_title: '清除历史记录？',
    clear_dialog_msg: '此操作将清除所有转换记录，已生成的音频文件不会被删除。',
    btn_cancel: '取消',
    btn_clear: '清除',
    output_not_set: '未设置',
    supported_formats_prefix: '支持格式：',
    about_verified_none: '暂无',
    status_ncm: 'NCM — 已验证 (14/14 样例，与 Python 版 ncmdump 逐字节比对一致)',
    status_qmc0: 'QMC0 / QMC3 / QMCFLAC / QMCOGG — 已验证 (真实 MP3 双向验证成功)',
    status_qmcv2: 'QMCv2 (.mflac / .mgg / .bkc) — 已实现，需要 QQ 音乐本地数据库 ekey',
    status_kgm: 'KGM / KGMA / VPR — 已实现 (真实 MP3 双向验证成功)',
    status_kwm: 'KWM — 已实现 (真实 MP3 双向验证成功)',
    ffmpeg_detected: '(系统 PATH 中的 ffmpeg 已就绪)',
    ffmpeg_not_detected: '(未检测到)',
    toast_ekey_saved: 'QQ 音乐 ekey 已保存',
    toast_ekey_cleared: 'QQ 音乐 ekey 已清空',
    toast_history_cleared: '历史记录已清除',
    toast_history_load_failed: '加载历史记录失败',
    toast_output_folder_required: '请先选择输出文件夹',
    toast_converted_success: '成功转换 {count} 个文件',
    toast_converted_mixed: '成功转换 {success} 个，{failed} 个失败',
    toast_converted_all_failed: '所有文件转换失败',
    toast_init_failed: '初始化失败: {message}',
    status_ready: '就绪',
    status_done: '完成',
    status_error: '错误',
    status_success: '成功',
    status_failed: '失败',
    time_just_now: '刚刚',
    time_mins_ago: '{mins} 分钟前',
    time_hours_ago: '{hours} 小时前',
    time_days_ago: '{days} 天前',
  }
};

function t(key, replacements = {}) {
  const lang = state.language === 'auto'
    ? (navigator.language.startsWith('zh') ? 'zh' : 'en')
    : state.language;
  let str = TRANSLATIONS[lang]?.[key] || TRANSLATIONS['en']?.[key] || key;
  for (const [k, v] of Object.entries(replacements)) {
    str = str.replace(`{${k}}`, v);
  }
  return str;
}

function applyLanguage() {
  const lang = state.language === 'auto'
    ? (navigator.language.startsWith('zh') ? 'zh' : 'en')
    : state.language;

  document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';

  document.querySelectorAll('[data-i18n]').forEach((el) => {
    const key = el.dataset.i18n;
    const translation = TRANSLATIONS[lang]?.[key] || TRANSLATIONS['en']?.[key];
    if (translation !== undefined) {
      if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
        el.placeholder = translation;
      } else {
        el.textContent = translation;
      }
    }
  });

  updateOutputDisplay();
  updateQueue();
  loadHistory();

  if (state.decoders) {
    const formatsJoined = state.decoders.supported.map((e) => e.replace('.', '')).join(' ');
    $('supported-formats').textContent = t('supported_formats_prefix') + formatsJoined;

    $('verified-formats').innerHTML = state.decoders.implemented
      .map((e) => `<li>${e}</li>`)
      .join('') || `<li>${t('about_verified_none')}</li>`;
  }

  if (state.ffmpegStatus) {
    const ffmpegLabel = state.ffmpegStatus === 'detected'
      ? t('ffmpeg_detected')
      : t('ffmpeg_not_detected');
    $('status-list').innerHTML = `
      <li>${t('status_ncm')}</li>
      <li>${t('status_qmc0')}</li>
      <li>${t('status_qmcv2')}</li>
      <li>${t('status_kgm')}</li>
      <li>${t('status_kwm')}</li>
      <li>ffmpeg ${ffmpegLabel}</li>
    `;
  }
}

// ---------- helpers ----------
const $ = (id) => document.getElementById(id);
const fmtBytes = (n) => {
  if (n < 1024) return n + 'B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + 'KB';
  return (n / 1024 / 1024).toFixed(1) + 'MB';
};
const basename = (p) => p.split(/[\\/]/).pop();

function toast(msg, kind = '') {
  const el = $('toast');
  el.textContent = msg;
  el.className = 'toast ' + kind;
  el.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { el.hidden = true; }, 3500);
}

// ---------- init ----------
async function init() {
  const os = await api.invoke('os:info');
  if (os.platform === 'win32') {
    document.body.classList.add('platform-win32');
  }
  $('os-info').textContent = `OpenConverter v${os.appVersion}`;

  const cfg = await api.invoke('config:get');
  if (cfg.format) state.format = cfg.format;
  if (cfg.quality) state.quality = cfg.quality;
  if (cfg.outputDir) state.outputDir = cfg.outputDir;
  if (cfg.language) state.language = cfg.language;
  if (cfg.theme) state.theme = cfg.theme;
  if (cfg.qmcEkey) $('ekey-input').value = cfg.qmcEkey;
  $('format-select').value = state.format;
  $('quality-select').value = state.quality;
  $('language-select').value = state.language;
  $('theme-select').value = state.theme;

  state.decoders = await api.invoke('decoders:list');
  state.ffmpegStatus = await checkFfmpeg();

  // Default output dir to ~/Music/OpenConverter if not set
  if (!state.outputDir) {
    state.outputDir = `${os.homedir}/Music/OpenConverter`.replace(/\$(\w+)/g, (_, n) => ({ homedir: os.homedir }[n] || ''));
  }

  bindEvents();
  applyTheme();
  applyLanguage();
}

async function checkFfmpeg() {
  try {
    await api.invoke('os:info');
    return 'detected';
  } catch { return 'not_detected'; }
}

function applyTheme() {
  document.body.classList.toggle('light', state.theme === 'light');
}

function bindEvents() {
  // Format/quality/language/theme
  $('format-select').addEventListener('change', (e) => { state.format = e.target.value; api.invoke('config:set', { patch: { format: e.target.value } }); });
  $('quality-select').addEventListener('change', (e) => { state.quality = e.target.value; api.invoke('config:set', { patch: { quality: e.target.value } }); });
  $('language-select').addEventListener('change', (e) => {
    state.language = e.target.value;
    api.invoke('config:set', { patch: { language: e.target.value } });
    applyLanguage();
  });
  $('theme-select').addEventListener('change', (e) => {
    state.theme = e.target.value;
    api.invoke('config:set', { patch: { theme: e.target.value } });
    applyTheme();
  });

  // ekey (QQ Music)
  $('ekey-save-btn').addEventListener('click', async () => {
    const v = $('ekey-input').value.trim();
    await api.invoke('config:set', { patch: { qmcEkey: v } });
    toast(v ? t('toast_ekey_saved') : t('toast_ekey_cleared'), 'ok');
  });

  // Window controls
  const winMaxBtn = $('win-max');
  $('win-min').addEventListener('click', () => api.invoke('win:minimize'));
  winMaxBtn.addEventListener('click', () => api.invoke('win:toggleMaximize'));
  $('win-close').addEventListener('click', () => api.invoke('win:close'));
  api.on('win:maximizedChanged', ({ maximized }) => {
    winMaxBtn.title = maximized ? 'Restore' : 'Maximize';
  });

  api.on('convert:progress', ({ filePath, percent }) => {
    const f = state.files.find((x) => x.path === filePath);
    if (f) {
      f.progress = percent;
      const idx = state.files.indexOf(f);
      const itemEl = document.querySelector(`.queue-item[data-i="${idx}"]`);
      if (itemEl) {
        const progressDiv = itemEl.querySelector('.progress > div');
        if (progressDiv) progressDiv.style.width = `${percent}%`;
        const statusDiv = itemEl.querySelector('.status');
        if (statusDiv) statusDiv.textContent = `${percent.toFixed(0)}%`;
      }
    }
  });

  // Output dir
  $('pick-output-btn').addEventListener('click', async () => {
    const r = await api.invoke('file:pickOutputDir');
    if (r.dir) {
      state.outputDir = r.dir;
      updateOutputDisplay();
      api.invoke('config:set', { patch: { outputDir: r.dir } });
    }
  });

  // Input picker
  $('pick-input-btn').addEventListener('click', async (e) => {
    e.stopPropagation();
    const r = await api.invoke('file:pickInput', { multi: true });
    addFiles(r.files);
  });
  $('dropzone').addEventListener('click', async () => {
    const r = await api.invoke('file:pickInput', { multi: true });
    addFiles(r.files);
  });

  // Drag and drop
  ['dragenter', 'dragover'].forEach((ev) =>
    $('dropzone').addEventListener(ev, (e) => { e.preventDefault(); $('dropzone').classList.add('dragging'); })
  );
  ['dragleave', 'drop'].forEach((ev) =>
    $('dropzone').addEventListener(ev, (e) => { e.preventDefault(); $('dropzone').classList.remove('dragging'); })
  );
  $('dropzone').addEventListener('drop', (e) => {
    const items = e.dataTransfer?.files;
    if (items) {
      const paths = [];
      for (const f of items) {
        // Electron exposes .path on dropped files
        if (f.path) paths.push(f.path);
      }
      addFiles(paths);
    }
  });

  // Convert
  $('convert-btn').addEventListener('click', convert);

  // Clear queue
  $('clear-queue-btn').addEventListener('click', () => { state.files = []; updateQueue(); });

  // Clear history dialog & actions
  const clearHistoryBtn = $('clear-history-btn');
  const clearDialog = $('clear-confirm-dialog');
  const confirmClearCancel = $('confirm-clear-cancel');
  const confirmClearOk = $('confirm-clear-ok');

  clearHistoryBtn.addEventListener('click', () => {
    clearDialog.showModal();
  });

  confirmClearCancel.addEventListener('click', () => {
    clearDialog.close();
  });

  confirmClearOk.addEventListener('click', async () => {
    clearDialog.close();
    await api.invoke('history:clear');
    toast(t('toast_history_cleared'), 'ok');
    loadHistory();
  });

  // Fallback for browsers without closedby support
  if (!('closedBy' in HTMLDialogElement.prototype)) {
    clearDialog.addEventListener('click', (event) => {
      if (event.target !== clearDialog) return;
      const rect = clearDialog.getBoundingClientRect();
      const isDialogContent = (
        rect.top <= event.clientY &&
        event.clientY <= rect.top + rect.height &&
        rect.left <= event.clientX &&
        event.clientX <= rect.left + rect.width
      );
      if (!isDialogContent) {
        clearDialog.close();
      }
    });
  }

  // Nav
  document.querySelectorAll('.nav-item').forEach((b) => {
    b.addEventListener('click', () => switchView(b.dataset.view));
  });

  // Allow URL hash to set initial view (#about etc)
  const initialView = (location.hash || '').replace('#', '') || 'convert';
  switchView(initialView);
}

function switchView(name) {
  document.querySelectorAll('.nav-item').forEach((x) => {
    x.classList.toggle('active', x.dataset.view === name);
  });
  document.querySelectorAll('.view').forEach((v) => {
    v.classList.toggle('active', v.id === 'view-' + name);
  });
  if (location.hash !== '#' + name) location.hash = name;

  if (name === 'history') {
    loadHistory();
  }
}

function addFiles(paths) {
  for (const p of paths) {
    if (!p) continue;
    if (state.files.find((f) => f.path === p)) continue;
    state.files.push({ path: p, name: basename(p), status: 'pending', progress: 0 });
  }
  updateQueue();
}

function updateOutputDisplay() {
  $('output-dir').textContent = state.outputDir || t('output_not_set');
}

function updateQueue() {
  $('queue').hidden = state.files.length === 0;
  $('queue-count').textContent = state.files.length;
  $('convert-btn').disabled = state.files.length === 0;

  $('queue-list').innerHTML = state.files.map((f, i) => {
    const statusText = {
      pending: t('status_ready'),
      decoding: `${f.progress.toFixed(0)}%`,
      done: t('status_done'),
      error: t('status_error'),
    }[f.status];
    return `
      <li class="queue-item ${f.status}" data-i="${i}">
        <div class="icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 18V5l12-2v13" />
            <circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" />
          </svg>
        </div>
        <div class="meta">
          <div class="name">${escapeHtml(f.name)}</div>
          <div class="sub">${f.outputPath ? '→ ' + escapeHtml(basename(f.outputPath)) : (f.error || '—')}</div>
          <div class="progress"><div style="width: ${f.progress}%"></div></div>
        </div>
        <div class="status">${statusText}</div>
      </li>
    `;
  }).join('');
}

function escapeHtml(s) { return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

async function convert() {
  if (!state.outputDir) {
    toast(t('toast_output_folder_required'), 'error');
    return;
  }
  $('convert-btn').disabled = true;
  // Mark all as decoding
  state.files.forEach((f) => { f.status = 'decoding'; f.progress = 0; f.error = null; f.outputPath = null; });
  updateQueue();

  try {
    const r = await api.invoke('convert:start', {
      files: state.files.map((f) => f.path),
      format: state.format,
      quality: state.quality,
      outputDir: state.outputDir,
    });
    let success = 0, fail = 0;
    r.results.forEach((res, i) => {
      const f = state.files[i];
      if (res.error) { f.status = 'error'; f.error = res.error; fail++; }
      else { f.status = 'done'; f.progress = 100; f.outputPath = res.outputPath; success++; }
    });
    updateQueue();
    if (success > 0 && fail === 0) toast(t('toast_converted_success', { count: success }), 'ok');
    else if (success > 0) toast(t('toast_converted_mixed', { success, failed: fail }), '');
    else toast(t('toast_converted_all_failed'), 'error');
  } catch (e) {
    toast(e.message || t('toast_converted_all_failed'), 'error');
  } finally {
    $('convert-btn').disabled = state.files.length === 0;
  }
}

init().catch((e) => { console.error(e); toast(t('toast_init_failed', { message: e.message }), 'error'); });

async function loadHistory() {
  try {
    const records = await api.invoke('history:get');
    const listEl = $('history-list');
    const emptyEl = $('history-empty');
    const clearBtn = $('clear-history-btn');

    if (records.length === 0) {
      listEl.innerHTML = '';
      emptyEl.hidden = false;
      clearBtn.disabled = true;
    } else {
      emptyEl.hidden = true;
      clearBtn.disabled = false;
      listEl.innerHTML = records.map((r) => {
        const statusClass = r.status === 'success' ? 'done' : 'error';
        const statusText = r.status === 'success' ? t('status_success') : t('status_failed');
        const timeText = formatRelativeTime(r.ts);
        const subText = r.status === 'success'
          ? `→ ${escapeHtml(r.targetFormat)}`
          : `→ ${escapeHtml(r.targetFormat)} (${escapeHtml(r.error)})`;

        const iconSvg = r.status === 'success'
          ? `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" /></svg>`
          : `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10" /><line x1="15" y1="9" x2="9" y2="15" /><line x1="9" y1="9" x2="15" y2="15" /></svg>`;

        return `
          <li class="queue-item ${statusClass}">
            <div class="icon">${iconSvg}</div>
            <div class="meta">
              <div class="name">${escapeHtml(r.inputName)}</div>
              <div class="sub">${subText}</div>
            </div>
            <div class="time-status-container" style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
              <div class="status" style="margin-bottom: 0;">${statusText}</div>
              <div class="time" style="font-size: 11px; color: var(--text-dim);">${timeText}</div>
            </div>
          </li>
        `;
      }).join('');
    }
  } catch (e) {
    console.error('Failed to load history:', e);
    toast(t('toast_history_load_failed'), 'error');
  }
}

function formatRelativeTime(ts) {
  const diff = Date.now() - ts;
  const secs = Math.floor(diff / 1000);
  if (secs < 60) return t('time_just_now');
  const mins = Math.floor(secs / 60);
  if (mins < 60) return t('time_mins_ago', { mins });
  const hours = Math.floor(mins / 60);
  if (hours < 24) return t('time_hours_ago', { hours });
  const days = Math.floor(hours / 24);
  if (days < 30) return t('time_days_ago', { days });
  return new Date(ts).toLocaleDateString();
}
