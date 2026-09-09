/**
 * OpenConverter renderer — vanilla JS, no framework. Talks to main via window.api.
 *
 * Rendering model: the queue is *keyed* — each file owns one <li> that is
 * created once and updated in place, so CSS transitions (progress bars,
 * status colour, enter/leave) actually animate instead of being torn down
 * on every state change.
 */

const api = window.api;

const state = {
  files: [], // { id, path, name, status: 'pending'|'queued'|'decrypt'|'encode'|'done'|'error'|'cancelled', progress, outputPath, error, durationMs }
  outputDir: '',
  format: 'mp3',
  quality: '320k',
  language: 'auto',
  theme: 'system',
  systemDark: true,
  reduceMotion: false,
  autoClearDone: false,
  decoders: null,
  ffmpeg: null, // { ok, version, error }
  converting: false,
  view: 'convert',
};

// ---------- translations ----------
const TRANSLATIONS = {
  en: {
    nav_convert: 'Convert',
    nav_history: 'History',
    nav_settings: 'Settings',
    nav_about: 'About',
    title_convert_audio: 'Convert audio',
    title_history: 'History',
    title_settings: 'Settings',
    title_about: 'About',
    label_format: 'Format',
    label_quality: 'Quality',
    label_language: 'Language',
    lang_auto: 'Auto',
    label_theme: 'Theme',
    theme_system: 'System',
    theme_dark: 'Dark',
    theme_light: 'Light',
    label_output: 'Output:',
    btn_choose_folder: 'Choose folder',
    btn_open_folder: 'Open output folder',
    ekey_panel_title: 'QQ Music ekey & Cookie (required for .mflac / .mgg / .bkc)',
    ekey_placeholder: 'Paste base64 ekey from QQ Music client DB',
    btn_save: 'Save',
    qq_cookie_placeholder: 'Paste QQ Music Cookie (for mgg/mflac API fetch)',
    btn_scan_qq_memory: 'Auto Scan Memory',
    ekey_hint: 'Only needed for QQ Music cache files (.mflac0, .mgg). Use Auto Scan while QQ Music is running to extract the Cookie automatically. Leave empty if you only need NCM / QMC0 / KGM / KWM.',
    dropzone_text: 'Drop .ncm / .qmc / .kgm files here',
    dropzone_hint_or: 'or',
    dropzone_hint_browse: 'browse',
    dropzone_hint_choose: 'to choose',
    drop_overlay_text: 'Release to add files',
    queue_title: 'Queue',
    btn_add_more: 'Add files',
    btn_clear_all: 'Clear all',
    btn_clear_done: 'Clear finished',
    btn_convert: 'Convert',
    btn_convert_files: 'file(s)',
    btn_converting: 'Converting…',
    btn_cancel: 'Cancel',
    btn_remove: 'Remove',
    btn_show_in_folder: 'Show in folder',
    btn_retry: 'Retry',
    summary_pending: '{n} waiting',
    summary_running: '{done}/{total} done',
    summary_done: '{done} done, {failed} failed',
    history_title: 'History',
    btn_clear_history: 'Clear history',
    history_empty: 'No conversion history yet',
    about_title: 'About',
    about_desc1: 'OpenConverter is an open-source, fully offline audio format converter.',
    about_desc2: 'It supports encrypted formats (NCM, QMC, KGM, KWM, KGG) via pure-JavaScript decoders and uses ffmpeg for format conversion.',
    about_verified_formats: 'Supported formats',
    about_system_status: 'Status',
    appearance_title: 'Appearance & behaviour',
    reduce_motion_label: 'Reduce motion',
    auto_clear_label: 'Remove finished files from the queue automatically',
    clear_dialog_title: 'Clear history?',
    clear_dialog_msg: 'This will delete all conversion records. Converted files will not be deleted.',
    btn_clear: 'Clear',
    output_not_set: 'Not set',
    supported_formats_prefix: 'Supported: ',
    about_verified_none: 'None yet',
    status_ncm: 'NCM — verified (byte-diff against Python ncmdump); tags & cover art embedded',
    status_qmc0: 'QMC0 / QMC3 / QMCFLAC / QMCOGG — verified (round-trip on real MP3)',
    status_qmcv2: 'QMCv2 (.mflac / .mgg / .bkc) — embedded keys (STag/QTag) or QQ Music Cookie',
    status_kgm: 'KGM / KGMA / VPR — implemented (round-trip on real MP3)',
    status_kwm: 'KWM — implemented (round-trip on real MP3)',
    status_kgg: 'KGG — implemented (v5 decrypter, requires database keys)',
    ffmpeg_detected: 'ffmpeg {version} ready',
    ffmpeg_not_detected: 'ffmpeg not found — install it and restart',
    ffmpeg_pill_ok: 'ffmpeg {version}',
    ffmpeg_pill_missing: 'ffmpeg missing',
    toast_ekey_saved: 'QQ Music settings saved',
    toast_ekey_cleared: 'QQ Music settings cleared',
    toast_qq_scan_success: 'Cookie found! uin: {uin}',
    toast_history_cleared: 'History cleared',
    toast_history_load_failed: 'Failed to load history',
    toast_output_folder_required: 'Choose an output folder first',
    toast_ffmpeg_missing: 'ffmpeg was not found. Install ffmpeg to convert files.',
    toast_converted_success: 'Converted {count} file(s)',
    toast_converted_mixed: 'Converted {success}, {failed} failed',
    toast_converted_all_failed: 'All conversions failed',
    toast_cancelled: 'Conversion cancelled',
    toast_added: 'Added {count} file(s)',
    toast_skipped_unsupported: '{count} unsupported file(s) skipped',
    toast_init_failed: 'Init failed: {message}',
    toast_settings_saved: 'Settings saved',
    status_ready: 'Ready',
    status_queued: 'Waiting',
    status_decrypt: 'Decrypting',
    status_encode: 'Encoding',
    status_done: 'Done',
    status_error: 'Error',
    status_cancelled: 'Cancelled',
    status_success: 'Success',
    status_failed: 'Failed',
    time_just_now: 'Just now',
    time_mins_ago: '{mins}m ago',
    time_hours_ago: '{hours}h ago',
    time_days_ago: '{days}d ago',
    kgg_panel_title: 'KuGou Music KGG Settings (required for .kgg / .kgg.flac)',
    kgg_autoscan_label: 'Auto-scan database on startup',
    kgg_scan_now_btn: 'Scan now',
    kgg_import_btn: 'Import DB / Key',
    kgg_linux_warning: 'KuGou decryption is not supported on Linux natively. Import a keys file if needed.',
    kgg_autoscan_hint: 'Scan local client\'s database to extract ekeys automatically. Or manually import your KGMusicV3.db / kgg.key file.',
    toast_kgg_scan_success: 'Scan complete: found {count} new keys (total {total})',
    toast_kgg_import_success: 'Import complete: added {count} keys (total {total})',
    toast_kgg_import_none: 'No new keys imported',
    needs_key: 'needs key',
  },
  zh: {
    nav_convert: '转换',
    nav_history: '历史记录',
    nav_settings: '设置',
    nav_about: '关于',
    title_convert_audio: '音频格式转换',
    title_history: '转换历史',
    title_settings: '设置',
    title_about: '关于',
    label_format: '格式',
    label_quality: '音质',
    label_language: '语言',
    lang_auto: '自动 (Auto)',
    label_theme: '主题',
    theme_system: '跟随系统',
    theme_dark: '深色',
    theme_light: '浅色',
    label_output: '输出目录：',
    btn_choose_folder: '选择目录',
    btn_open_folder: '打开输出目录',
    ekey_panel_title: 'QQ 音乐 ekey 与 Cookie (解密 .mflac / .mgg / .bkc 必需)',
    ekey_placeholder: '粘贴本地 QQ 音乐客户端数据库中的 Base64 ekey',
    btn_save: '保存',
    qq_cookie_placeholder: '粘贴 QQ 音乐 Cookie（用于 mgg/mflac 接口拉取）',
    btn_scan_qq_memory: '自动扫描内存',
    ekey_hint: '仅在处理 QQ 音乐缓存文件 (.mflac0, .mgg) 时才需要。在 QQ 音乐运行时点击自动扫描即可提取。若仅需处理 NCM / QMC0 / KGM / KWM，请保持为空。',
    dropzone_text: '拖曳 .ncm / .qmc / .kgm 等加密音频文件到这里',
    dropzone_hint_or: '或者',
    dropzone_hint_browse: '点击浏览',
    dropzone_hint_choose: '选择文件',
    drop_overlay_text: '松开即可添加文件',
    queue_title: '等待队列',
    btn_add_more: '添加文件',
    btn_clear_all: '清空全部',
    btn_clear_done: '清除已完成',
    btn_convert: '开始转换',
    btn_convert_files: '个文件',
    btn_converting: '转换中…',
    btn_cancel: '取消',
    btn_remove: '移除',
    btn_show_in_folder: '在文件夹中显示',
    btn_retry: '重试',
    summary_pending: '{n} 个待处理',
    summary_running: '已完成 {done}/{total}',
    summary_done: '成功 {done} 个，失败 {failed} 个',
    history_title: '转换历史',
    btn_clear_history: '清除历史',
    history_empty: '暂无转换记录',
    about_title: '关于',
    about_desc1: 'OpenConverter 是一款跨平台、完全离线的轻量级音频格式转换与本地解码工具。',
    about_desc2: '项目基于纯 JavaScript 解码管线，支持各种加密格式 (NCM, QMC, KGM, KWM, KGG) 的本地直接解密，并使用 FFmpeg 进行转码。',
    about_verified_formats: '支持格式',
    about_system_status: '系统状态',
    appearance_title: '外观与行为',
    reduce_motion_label: '减弱动画效果',
    auto_clear_label: '转换完成后自动从队列移除',
    clear_dialog_title: '清除历史记录？',
    clear_dialog_msg: '此操作将清除所有转换记录，已生成的音频文件不会被删除。',
    btn_clear: '清除',
    output_not_set: '未设置',
    supported_formats_prefix: '支持格式：',
    about_verified_none: '暂无',
    status_ncm: 'NCM — 已验证 (与 Python 版 ncmdump 逐字节比对一致)；自动写入歌曲信息与封面',
    status_qmc0: 'QMC0 / QMC3 / QMCFLAC / QMCOGG — 已验证 (真实 MP3 双向验证成功)',
    status_qmcv2: 'QMCv2 (.mflac / .mgg / .bkc) — 支持内嵌密钥 (STag/QTag) 或 QQ 音乐 Cookie 拉取',
    status_kgm: 'KGM / KGMA / VPR — 已实现 (真实 MP3 双向验证成功)',
    status_kwm: 'KWM — 已实现 (真实 MP3 双向验证成功)',
    status_kgg: 'KGG — 已实现 (v5 解密，需要导入密钥或 KGMusicV3.db)',
    ffmpeg_detected: 'ffmpeg {version} 已就绪',
    ffmpeg_not_detected: '未检测到 ffmpeg — 请安装后重启应用',
    ffmpeg_pill_ok: 'ffmpeg {version}',
    ffmpeg_pill_missing: '缺少 ffmpeg',
    toast_ekey_saved: 'QQ 音乐设置已保存',
    toast_ekey_cleared: 'QQ 音乐设置已清空',
    toast_qq_scan_success: '已成功提取 Cookie！uin: {uin}',
    toast_history_cleared: '历史记录已清除',
    toast_history_load_failed: '加载历史记录失败',
    toast_output_folder_required: '请先选择输出文件夹',
    toast_ffmpeg_missing: '未找到 ffmpeg，请先安装后再转换。',
    toast_converted_success: '成功转换 {count} 个文件',
    toast_converted_mixed: '成功转换 {success} 个，{failed} 个失败',
    toast_converted_all_failed: '所有文件转换失败',
    toast_cancelled: '已取消转换',
    toast_added: '已添加 {count} 个文件',
    toast_skipped_unsupported: '已跳过 {count} 个不支持的文件',
    toast_init_failed: '初始化失败: {message}',
    toast_settings_saved: '设置已保存',
    status_ready: '就绪',
    status_queued: '等待中',
    status_decrypt: '解密中',
    status_encode: '转码中',
    status_done: '完成',
    status_error: '错误',
    status_cancelled: '已取消',
    status_success: '成功',
    status_failed: '失败',
    time_just_now: '刚刚',
    time_mins_ago: '{mins} 分钟前',
    time_hours_ago: '{hours} 小时前',
    time_days_ago: '{days} 天前',
    kgg_panel_title: '酷狗音乐 KGG 设置 (解密 .kgg / .kgg.flac 必需)',
    kgg_autoscan_label: '自动扫描本地播放器数据库',
    kgg_scan_now_btn: '立即扫描',
    kgg_import_btn: '导入密钥或数据库',
    kgg_linux_warning: 'Linux 端暂不支持直接解析酷狗本地播放器。如需转换，可手动导入从其他平台抽取的密钥文件。',
    kgg_autoscan_hint: '自动扫描酷狗客户端的本地数据库并提取歌曲密钥。或者手动导入您的 KGMusicV3.db / kgg.key 文件。',
    toast_kgg_scan_success: '扫描完成：发现 {count} 个新密钥 (总计 {total} 个)',
    toast_kgg_import_success: '导入完成：新增 {count} 个密钥 (总计 {total} 个)',
    toast_kgg_import_none: '未发现新密钥',
    needs_key: '需要密钥',
  },
};

function currentLang() {
  return state.language === 'auto' ? (navigator.language.startsWith('zh') ? 'zh' : 'en') : state.language;
}

function t(key, replacements = {}) {
  const lang = currentLang();
  let str = TRANSLATIONS[lang]?.[key] ?? TRANSLATIONS.en[key] ?? key;
  for (const [k, v] of Object.entries(replacements)) str = str.replaceAll(`{${k}}`, v);
  return str;
}

const VIEW_TITLES = { convert: 'title_convert_audio', history: 'title_history', settings: 'title_settings', about: 'title_about' };

function applyLanguage() {
  const lang = currentLang();
  document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';

  document.querySelectorAll('[data-i18n]').forEach((el) => {
    const translation = TRANSLATIONS[lang]?.[el.dataset.i18n] ?? TRANSLATIONS.en[el.dataset.i18n];
    if (translation === undefined) return;
    if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') el.placeholder = translation;
    else el.textContent = translation;
  });
  document.querySelectorAll('[data-i18n-title]').forEach((el) => { el.title = t(el.dataset.i18nTitle); });

  $('page-title').textContent = t(VIEW_TITLES[state.view] || 'title_convert_audio');
  updateOutputDisplay();
  renderQueue();
  if (state.view === 'history') loadHistory();
  renderAbout();
  renderFfmpegStatus();
}

function renderAbout() {
  if (!state.decoders) return;
  const formatsJoined = state.decoders.supported.map((e) => e.replace('.', '')).join(' · ');
  $('supported-formats').textContent = t('supported_formats_prefix') + formatsJoined;

  const needKey = new Set(state.decoders.requiresKey || []);
  const all = [...state.decoders.supported, ...(state.decoders.plain || [])];
  $('verified-formats').replaceChildren(...all.map((e) => {
    const chip = el('span', { class: 'chip' + (needKey.has(e) ? ' chip-key' : '') }, e);
    if (needKey.has(e)) chip.title = t('needs_key');
    return chip;
  }));

  const ffmpegLabel = state.ffmpeg?.ok ? t('ffmpeg_detected', { version: state.ffmpeg.version }) : t('ffmpeg_not_detected');
  $('status-list').replaceChildren(
    ...['status_ncm', 'status_qmc0', 'status_qmcv2', 'status_kgg', 'status_kgm', 'status_kwm'].map((k) => el('li', {}, t(k))),
    el('li', { class: state.ffmpeg?.ok ? 'ok' : 'bad' }, ffmpegLabel),
  );
}

function renderFfmpegStatus() {
  const pill = $('ffmpeg-pill');
  if (!state.ffmpeg) return;
  pill.classList.toggle('ok', !!state.ffmpeg.ok);
  pill.classList.toggle('bad', !state.ffmpeg.ok);
  $('ffmpeg-pill-text').textContent = state.ffmpeg.ok ? t('ffmpeg_pill_ok', { version: state.ffmpeg.version }) : t('ffmpeg_pill_missing');
  pill.title = state.ffmpeg.ok ? (state.ffmpeg.bin || '') : (state.ffmpeg.error || '');
}

// ---------- helpers ----------
const $ = (id) => document.getElementById(id);
const basename = (p) => p.split(/[\\/]/).pop();
const motionOK = () => !state.reduceMotion && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Tiny element builder: el('div', { class: 'x', onclick: fn }, child, 'text') */
function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2), v);
    else if (k === 'class') node.className = v;
    else if (k === 'hidden') node.hidden = !!v;
    else node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children) {
    if (c === undefined || c === null || c === false) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

function icon(name, cls = 'ico') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', cls);
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', `#${name}`);
  svg.append(use);
  return svg;
}

/** Animate an element out then remove it (respects reduced motion). */
function removeWithAnimation(node, done) {
  if (!motionOK()) { node.remove(); done?.(); return; }
  const h = node.getBoundingClientRect().height;
  node.style.setProperty('--h', `${h}px`);
  node.classList.add('leaving');
  node.addEventListener('animationend', () => { node.remove(); done?.(); }, { once: true });
}

// ---------- toasts (stacked, animated) ----------
function toast(msg, kind = '', ms = 3500) {
  const stack = $('toast-stack');
  const node = el('div', { class: `toast ${kind}`, role: 'status' }, msg);
  stack.append(node);
  requestAnimationFrame(() => node.classList.add('show'));
  const dismiss = () => {
    node.classList.remove('show');
    node.classList.add('hide');
    node.addEventListener('transitionend', () => node.remove(), { once: true });
    setTimeout(() => node.remove(), 400);
  };
  const timer = setTimeout(dismiss, ms);
  node.addEventListener('click', () => { clearTimeout(timer); dismiss(); });
  // Keep at most 4 visible.
  while (stack.children.length > 4) stack.firstElementChild.remove();
}

// ---------- init ----------
async function init() {
  const os = await api.invoke('os:info');
  if (os.platform === 'win32') document.body.classList.add('platform-win32');
  if (os.platform === 'linux') {
    $('kgg-linux-warning').hidden = false;
    $('kgg-autoscan-label').hidden = true;
    $('kgg-scan-btn').hidden = true;
    $('kgg-autoscan-hint').hidden = true;
  }
  state.systemDark = !!os.systemDark;
  $('os-info').textContent = `v${os.appVersion} · ${os.platform}/${os.arch}`;
  $('about-version').textContent = `v${os.appVersion}`;

  const cfg = await api.invoke('config:get');
  if (cfg.format) state.format = cfg.format;
  if (cfg.quality) state.quality = cfg.quality;
  if (cfg.outputDir) state.outputDir = cfg.outputDir;
  if (cfg.language) state.language = cfg.language;
  if (cfg.theme) state.theme = cfg.theme;
  state.reduceMotion = !!cfg.reduceMotion;
  state.autoClearDone = !!cfg.autoClearDone;
  if (cfg.qmcEkey) $('ekey-input').value = cfg.qmcEkey;
  if (cfg.qqCookie) $('qq-cookie-input').value = cfg.qqCookie;
  $('kgg-autoscan-checkbox').checked = !!cfg.kggAutoScan;
  $('reduce-motion-checkbox').checked = state.reduceMotion;
  $('auto-clear-checkbox').checked = state.autoClearDone;
  $('format-select').value = state.format;
  $('quality-select').value = state.quality;
  $('language-select').value = state.language;
  $('theme-select').value = state.theme;

  // Default output dir to ~/Music/OpenConverter if not set
  if (!state.outputDir) state.outputDir = `${os.homedir}/Music/OpenConverter`;

  applyTheme();
  applyMotion();
  bindEvents();
  updateQualityVisibility();
  applyLanguage();

  // Non-blocking: these can arrive after first paint.
  api.invoke('decoders:list').then((d) => { state.decoders = d; renderAbout(); });
  api.invoke('ffmpeg:check').then((r) => { state.ffmpeg = r; renderFfmpegStatus(); renderAbout(); });
}

function applyTheme() {
  const dark = state.theme === 'system' ? state.systemDark : state.theme === 'dark';
  document.body.classList.add('theme-anim');
  document.body.classList.toggle('light', !dark);
  setTimeout(() => document.body.classList.remove('theme-anim'), 400);
}

function applyMotion() {
  document.body.classList.toggle('reduce-motion', state.reduceMotion);
}

function updateQualityVisibility() {
  // Bitrate is meaningless for lossless targets.
  const lossless = state.format === 'flac' || state.format === 'wav';
  $('quality-field').classList.toggle('disabled', lossless);
  $('quality-select').disabled = lossless;
}

function bindEvents() {
  $('format-select').addEventListener('change', (e) => {
    state.format = e.target.value;
    api.invoke('config:set', { patch: { format: e.target.value } });
    updateQualityVisibility();
  });
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
  api.on('theme:systemChanged', ({ dark }) => { state.systemDark = dark; if (state.theme === 'system') applyTheme(); });

  $('reduce-motion-checkbox').addEventListener('change', (e) => {
    state.reduceMotion = e.target.checked;
    api.invoke('config:set', { patch: { reduceMotion: state.reduceMotion } });
    applyMotion();
  });
  $('auto-clear-checkbox').addEventListener('change', (e) => {
    state.autoClearDone = e.target.checked;
    api.invoke('config:set', { patch: { autoClearDone: state.autoClearDone } });
  });

  // ekey (QQ Music)
  $('ekey-save-btn').addEventListener('click', async () => {
    const v = $('ekey-input').value.trim();
    const c = $('qq-cookie-input').value.trim();
    await api.invoke('config:set', { patch: { qmcEkey: v, qqCookie: c } });
    toast(v || c ? t('toast_ekey_saved') : t('toast_ekey_cleared'), 'ok');
  });

  $('qq-scan-btn').addEventListener('click', () => withBusy($('qq-scan-btn'), async () => {
    const res = await api.invoke('qqmusic:extractCookie');
    if (res.ok) {
      $('qq-cookie-input').value = res.cookie;
      await api.invoke('config:set', { patch: { qqCookie: res.cookie, qqGuid: res.guid, qqUin: res.uin } });
      toast(t('toast_qq_scan_success', { uin: res.uin }), 'ok');
    } else {
      toast(res.error || 'Failed to scan memory', 'error');
    }
  }));

  // KGG Settings (KuGou)
  $('kgg-autoscan-checkbox').addEventListener('change', async (e) => {
    const checked = e.target.checked;
    await api.invoke('config:set', { patch: { kggAutoScan: checked } });
    if (checked) {
      const res = await api.invoke('kgg:triggerScan').catch(() => null);
      if (res && res.added > 0) toast(t('toast_kgg_scan_success', { count: res.added, total: res.total }), 'ok');
    }
  });
  $('kgg-scan-btn').addEventListener('click', () => withBusy($('kgg-scan-btn'), async () => {
    const res = await api.invoke('kgg:triggerScan');
    toast(t('toast_kgg_scan_success', { count: res.added, total: res.total }), 'ok');
  }));
  $('kgg-import-btn').addEventListener('click', () => withBusy($('kgg-import-btn'), async () => {
    const res = await api.invoke('kgg:importFile');
    if (!res.imported) return;
    if (res.added > 0) toast(t('toast_kgg_import_success', { count: res.added, total: res.total }), 'ok');
    else toast(t('toast_kgg_import_none'));
  }));

  // Window controls
  const winMaxBtn = $('win-max');
  $('win-min').addEventListener('click', () => api.invoke('win:minimize'));
  winMaxBtn.addEventListener('click', () => api.invoke('win:toggleMaximize'));
  $('win-close').addEventListener('click', () => api.invoke('win:close'));
  $('window-bar').addEventListener('dblclick', (e) => { if (e.target.closest('.window-bar-drag')) api.invoke('win:toggleMaximize'); });
  api.on('win:maximizedChanged', ({ maximized }) => { winMaxBtn.title = maximized ? 'Restore' : 'Maximize'; });

  api.on('convert:progress', ({ filePath, stage, percent }) => {
    const f = state.files.find((x) => x.path === filePath);
    if (!f || f.status === 'done' || f.status === 'error' || f.status === 'cancelled') return;
    if (stage === 'done') return; // final state comes from the convert:start result
    f.status = stage; // queued | decrypt | encode
    if (typeof percent === 'number') f.progress = percent;
    renderQueueItem(f);
    renderQueueSummary();
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
  $('open-output-btn').addEventListener('click', () => { if (state.outputDir) api.invoke('file:openPath', { path: state.outputDir }); });

  // Input picker
  const pick = async () => { const r = await api.invoke('file:pickInput', { multi: true }); addFiles(r.files); };
  $('pick-input-btn').addEventListener('click', (e) => { e.stopPropagation(); pick(); });
  $('add-more-btn').addEventListener('click', pick);
  $('dropzone').addEventListener('click', pick);
  $('dropzone').addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } });

  // Drag and drop — accepted anywhere in the window, with a full-screen overlay.
  let dragDepth = 0;
  const overlay = $('drop-overlay');
  const hasFiles = (e) => Array.from(e.dataTransfer?.types || []).includes('Files');
  window.addEventListener('dragenter', (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    dragDepth++;
    overlay.hidden = false;
    $('dropzone').classList.add('dragging');
    requestAnimationFrame(() => overlay.classList.add('show'));
  });
  window.addEventListener('dragover', (e) => { if (hasFiles(e)) e.preventDefault(); });
  const endDrag = () => {
    dragDepth = 0;
    overlay.classList.remove('show');
    $('dropzone').classList.remove('dragging');
    setTimeout(() => { if (!overlay.classList.contains('show')) overlay.hidden = true; }, 200);
  };
  window.addEventListener('dragleave', (e) => { if (!hasFiles(e)) return; dragDepth = Math.max(0, dragDepth - 1); if (dragDepth === 0) endDrag(); });
  window.addEventListener('drop', (e) => {
    e.preventDefault();
    endDrag();
    const paths = [];
    for (const f of e.dataTransfer?.files || []) if (f.path) paths.push(f.path);
    addFiles(paths);
    if (state.view !== 'convert') switchView('convert');
  });

  // Convert / cancel
  $('convert-btn').addEventListener('click', convert);
  $('cancel-btn').addEventListener('click', cancelAll);

  // Queue actions
  $('clear-queue-btn').addEventListener('click', () => {
    if (state.converting) return;
    const items = [...$('queue-list').children];
    state.files = [];
    items.forEach((n) => removeWithAnimation(n));
    renderQueue();
  });
  $('clear-done-btn').addEventListener('click', clearFinished);

  // Clear history dialog & actions
  const clearDialog = $('clear-confirm-dialog');
  $('clear-history-btn').addEventListener('click', () => clearDialog.showModal());
  $('confirm-clear-cancel').addEventListener('click', () => clearDialog.close());
  $('confirm-clear-ok').addEventListener('click', async () => {
    clearDialog.close();
    await api.invoke('history:clear');
    toast(t('toast_history_cleared'), 'ok');
    loadHistory();
  });
  clearDialog.addEventListener('click', (event) => {
    if (event.target !== clearDialog) return;
    const rect = clearDialog.getBoundingClientRect();
    const inside = rect.top <= event.clientY && event.clientY <= rect.bottom && rect.left <= event.clientX && event.clientX <= rect.right;
    if (!inside) clearDialog.close();
  });

  // Nav
  document.querySelectorAll('.nav-item').forEach((b) => b.addEventListener('click', () => switchView(b.dataset.view)));
  window.addEventListener('resize', () => positionNavIndicator(false));

  // Keyboard shortcuts
  window.addEventListener('keydown', (e) => {
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.key === 'o') { e.preventDefault(); pick(); }
    if (mod && e.key === 'Enter' && state.view === 'convert' && !state.converting) { e.preventDefault(); convert(); }
    if (e.key === 'Escape' && state.converting) cancelAll();
    if (mod && ['1', '2', '3', '4'].includes(e.key)) { e.preventDefault(); switchView(['convert', 'history', 'settings', 'about'][+e.key - 1]); }
  });

  const initialView = (location.hash || '').replace('#', '') || 'convert';
  switchView(initialView, false);
}

/** Disable a button and show a spinner while `fn` runs; toast on error. */
async function withBusy(btn, fn) {
  btn.disabled = true;
  btn.classList.add('busy');
  try { await fn(); } catch (err) { toast(err.message || 'Error', 'error'); }
  finally { btn.disabled = false; btn.classList.remove('busy'); }
}

// ---------- views ----------
function positionNavIndicator(animate = true) {
  const active = document.querySelector('.nav-item.active');
  const ind = $('nav-indicator');
  if (!active) return;
  ind.style.transition = animate && motionOK() ? '' : 'none';
  ind.style.transform = `translateY(${active.offsetTop}px)`;
  ind.style.height = `${active.offsetHeight}px`;
  if (!animate) requestAnimationFrame(() => { ind.style.transition = ''; });
}

function switchView(name, animate = true) {
  if (!VIEW_TITLES[name]) name = 'convert';
  const prev = state.view;
  state.view = name;
  document.body.dataset.view = name;
  document.querySelectorAll('.nav-item').forEach((x) => x.classList.toggle('active', x.dataset.view === name));
  positionNavIndicator(animate);

  const order = Object.keys(VIEW_TITLES);
  const dir = order.indexOf(name) >= order.indexOf(prev) ? 'fwd' : 'back';
  document.querySelectorAll('.view').forEach((v) => {
    const isActive = v.id === 'view-' + name;
    v.classList.remove('enter-fwd', 'enter-back');
    v.classList.toggle('active', isActive);
    if (isActive && animate && prev !== name && motionOK()) {
      v.classList.add(dir === 'fwd' ? 'enter-fwd' : 'enter-back');
      v.addEventListener('animationend', () => v.classList.remove('enter-fwd', 'enter-back'), { once: true });
    }
  });
  const title = $('page-title');
  title.textContent = t(VIEW_TITLES[name]);
  if (animate && motionOK()) { title.classList.remove('swap'); void title.offsetWidth; title.classList.add('swap'); }
  $('content').scrollTop = 0;
  if (location.hash !== '#' + name) history.replaceState(null, '', '#' + name);
  if (name === 'history') loadHistory();
  renderQueueSummary();
}

// ---------- queue ----------
let fileSeq = 0;

function isSupportedPath(p) {
  if (!state.decoders) return true; // not loaded yet — let main decide
  const lower = p.toLowerCase();
  return [...state.decoders.supported, ...(state.decoders.plain || [])].some((ext) => lower.endsWith(ext));
}

function addFiles(paths) {
  let added = 0, skipped = 0;
  for (const p of paths) {
    if (!p) continue;
    if (state.files.find((f) => f.path === p)) continue;
    if (!isSupportedPath(p)) { skipped++; continue; }
    state.files.push({ id: ++fileSeq, path: p, name: basename(p), status: 'pending', progress: 0, outputPath: null, error: null });
    added++;
  }
  if (added) renderQueue();
  if (skipped) toast(t('toast_skipped_unsupported', { count: skipped }), 'warn');
}

function removeFile(f) {
  if (state.converting && !['done', 'error', 'cancelled', 'pending'].includes(f.status)) return;
  state.files = state.files.filter((x) => x !== f);
  const node = $('queue-list').querySelector(`[data-id="${f.id}"]`);
  if (node) removeWithAnimation(node, () => renderQueue());
  else renderQueue();
}

function clearFinished() {
  const gone = state.files.filter((f) => f.status === 'done' || f.status === 'cancelled');
  state.files = state.files.filter((f) => !gone.includes(f));
  gone.forEach((f) => { const n = $('queue-list').querySelector(`[data-id="${f.id}"]`); if (n) removeWithAnimation(n); });
  renderQueue();
}

function updateOutputDisplay() {
  $('output-dir').textContent = state.outputDir || t('output_not_set');
  $('output-dir').title = state.outputDir || '';
  $('open-output-btn').hidden = !state.outputDir;
}

const STATUS_KEYS = { pending: 'status_ready', queued: 'status_queued', decrypt: 'status_decrypt', encode: 'status_encode', done: 'status_done', error: 'status_error', cancelled: 'status_cancelled' };
const ACTIVE = new Set(['queued', 'decrypt', 'encode']);

function buildQueueItem(f) {
  const li = el('li', { class: 'queue-item', 'data-id': f.id },
    el('div', { class: 'icon' }, icon('i-music', 'ico ico-music'), icon('i-check', 'ico ico-done'), icon('i-error', 'ico ico-error')),
    el('div', { class: 'meta' },
      el('div', { class: 'name', title: f.path }, f.name),
      el('div', { class: 'sub' }),
      el('div', { class: 'progress' }, el('div', { class: 'progress-fill' })),
    ),
    el('div', { class: 'status' }),
    el('div', { class: 'actions' },
      el('button', { class: 'btn btn-icon act-show', title: t('btn_show_in_folder'), onclick: () => api.invoke('file:showInFolder', { path: f.outputPath }) }, icon('i-folder')),
      el('button', { class: 'btn btn-icon act-remove', title: t('btn_remove'), onclick: () => removeFile(f) }, icon('i-x')),
    ),
  );
  if (motionOK()) li.classList.add('entering');
  li.addEventListener('animationend', () => li.classList.remove('entering'), { once: true });
  return li;
}

function renderQueueItem(f, node) {
  node = node || $('queue-list').querySelector(`[data-id="${f.id}"]`);
  if (!node) return;
  node.className = `queue-item ${f.status}` + (node.classList.contains('entering') ? ' entering' : '');
  node.querySelector('.sub').textContent = f.outputPath ? '→ ' + basename(f.outputPath) : (f.error || (f.status === 'pending' ? '—' : ''));
  const fill = node.querySelector('.progress-fill');
  const indeterminate = f.status === 'decrypt' || f.status === 'queued';
  node.querySelector('.progress').classList.toggle('indeterminate', indeterminate);
  fill.style.width = indeterminate ? '100%' : `${f.status === 'done' ? 100 : (f.progress || 0)}%`;
  const statusEl = node.querySelector('.status');
  let statusText = t(STATUS_KEYS[f.status] || 'status_ready');
  if (f.status === 'encode') statusText = `${statusText} ${Math.round(f.progress || 0)}%`;
  if (f.status === 'done' && f.durationMs != null) statusText += ` · ${(f.durationMs / 1000).toFixed(1)}s`;
  statusEl.textContent = statusText;
  node.querySelector('.act-show').hidden = f.status !== 'done' || !f.outputPath;
  node.querySelector('.act-remove').hidden = ACTIVE.has(f.status);
  node.querySelector('.act-show').title = t('btn_show_in_folder');
  node.querySelector('.act-remove').title = t('btn_remove');
}

/** Reconcile the DOM list with state.files (keyed by id). */
function renderQueue() {
  const list = $('queue-list');
  const byId = new Map(Array.from(list.children).map((n) => [Number(n.dataset.id), n]));
  const wanted = new Set(state.files.map((f) => f.id));
  for (const [id, node] of byId) if (!wanted.has(id) && !node.classList.contains('leaving')) node.remove();

  let prev = null;
  for (const f of state.files) {
    let node = byId.get(f.id);
    if (!node) node = buildQueueItem(f);
    const anchor = prev ? prev.nextSibling : list.firstChild;
    if (node !== anchor) list.insertBefore(node, anchor);
    renderQueueItem(f, node);
    prev = node;
  }

  const n = state.files.length;
  const convertible = state.files.filter((f) => f.status === 'pending' || f.status === 'error' || f.status === 'cancelled').length;
  const hasQueue = n > 0 || list.querySelector('.leaving');
  $('queue').hidden = !hasQueue;
  $('dropzone').classList.toggle('compact', n > 0);
  $('queue-count').textContent = state.converting ? state.files.filter((f) => ACTIVE.has(f.status)).length : convertible;
  $('convert-bar').hidden = n === 0;
  $('convert-btn').disabled = n === 0 || state.converting || convertible === 0;
  $('cancel-btn').hidden = !state.converting;
  $('clear-queue-btn').disabled = state.converting;
  $('add-more-btn').disabled = state.converting;
  $('clear-done-btn').hidden = !state.files.some((f) => f.status === 'done' || f.status === 'cancelled');
  renderQueueSummary();
}

function renderQueueSummary() {
  const total = state.files.length;
  const done = state.files.filter((f) => f.status === 'done').length;
  const failed = state.files.filter((f) => f.status === 'error').length;
  const finished = done + failed + state.files.filter((f) => f.status === 'cancelled').length;
  const summary = $('queue-summary');
  const badge = $('nav-badge-convert');
  const overall = $('queue-overall');

  if (state.converting) {
    summary.textContent = t('summary_running', { done: finished, total });
    // Aggregate progress: finished items count 100, active items their own %.
    let sum = 0;
    for (const f of state.files) sum += (f.status === 'done' || f.status === 'error' || f.status === 'cancelled') ? 100 : (f.status === 'encode' ? f.progress : (f.status === 'decrypt' ? 30 : 0));
    overall.hidden = false;
    $('queue-overall-fill').style.width = `${total ? sum / total : 0}%`;
    $('convert-bar-info').textContent = t('summary_running', { done: finished, total });
  } else {
    overall.hidden = true;
    const pending = state.files.filter((f) => f.status === 'pending').length;
    summary.textContent = finished ? t('summary_done', { done, failed }) : (pending ? t('summary_pending', { n: pending }) : '');
    $('convert-bar-info').textContent = '';
  }
  const activeCount = state.files.filter((f) => ACTIVE.has(f.status)).length;
  badge.hidden = !(state.view !== 'convert' && (activeCount > 0 || state.files.some((f) => f.status === 'pending')));
  badge.textContent = activeCount || state.files.filter((f) => f.status === 'pending').length;
}

async function convert() {
  if (state.converting) return;
  if (!state.outputDir) { toast(t('toast_output_folder_required'), 'error'); switchView('convert'); return; }
  if (state.ffmpeg && !state.ffmpeg.ok) toast(t('toast_ffmpeg_missing'), 'warn', 6000);

  const targets = state.files.filter((f) => f.status === 'pending' || f.status === 'error' || f.status === 'cancelled');
  if (targets.length === 0) return;
  state.converting = true;
  targets.forEach((f) => { f.status = 'queued'; f.progress = 0; f.error = null; f.outputPath = null; f.durationMs = null; });
  renderQueue();
  $('convert-btn').classList.add('busy');

  try {
    const r = await api.invoke('convert:start', {
      files: targets.map((f) => f.path),
      format: state.format,
      quality: state.quality,
      outputDir: state.outputDir,
    });
    let success = 0, fail = 0, cancelled = 0;
    r.results.forEach((res, i) => {
      const f = targets[i];
      if (!f) return;
      if (res.cancelled) { f.status = 'cancelled'; f.error = null; cancelled++; }
      else if (res.error) { f.status = 'error'; f.error = res.error; fail++; }
      else { f.status = 'done'; f.progress = 100; f.outputPath = res.outputPath; f.durationMs = res.durationMs; success++; }
    });
    if (cancelled && !success && !fail) toast(t('toast_cancelled'), 'warn');
    else if (success > 0 && fail === 0) toast(t('toast_converted_success', { count: success }), 'ok');
    else if (success > 0) toast(t('toast_converted_mixed', { success, failed: fail }), 'warn');
    else toast(t('toast_converted_all_failed'), 'error');
    if (state.autoClearDone) setTimeout(clearFinished, 1200);
  } catch (e) {
    targets.forEach((f) => { if (ACTIVE.has(f.status)) { f.status = 'error'; f.error = e.message; } });
    toast(e.message || t('toast_converted_all_failed'), 'error');
  } finally {
    state.converting = false;
    $('convert-btn').classList.remove('busy');
    renderQueue();
  }
}

async function cancelAll() {
  if (!state.converting) return;
  $('cancel-btn').disabled = true;
  try { await api.invoke('convert:cancelAll'); } finally { setTimeout(() => { $('cancel-btn').disabled = false; }, 500); }
}

// ---------- history ----------
async function loadHistory() {
  try {
    const records = await api.invoke('history:get');
    const listEl = $('history-list');
    const emptyEl = $('history-empty');
    $('clear-history-btn').disabled = records.length === 0;
    emptyEl.hidden = records.length !== 0;

    listEl.replaceChildren(...records.map((r, i) => {
      const ok = r.status === 'success';
      const li = el('li', { class: `queue-item ${ok ? 'done' : 'error'}`, style: motionOK() && i < 12 ? `animation-delay:${i * 30}ms` : '' },
        el('div', { class: 'icon' }, icon(ok ? 'i-check' : 'i-error', 'ico')),
        el('div', { class: 'meta' },
          el('div', { class: 'name', title: r.outputPath || '' }, r.inputName),
          el('div', { class: 'sub' }, ok ? `→ ${r.outputName || r.targetFormat}` : `→ ${r.targetFormat} (${r.error})`),
        ),
        el('div', { class: 'time-status' },
          el('div', { class: 'status' }, ok ? t('status_success') : t('status_failed'), r.durationMs ? ` · ${(r.durationMs / 1000).toFixed(1)}s` : ''),
          el('div', { class: 'time' }, formatRelativeTime(r.ts)),
        ),
        el('div', { class: 'actions' },
          ok && r.outputPath ? el('button', { class: 'btn btn-icon', title: t('btn_show_in_folder'), onclick: () => api.invoke('file:showInFolder', { path: r.outputPath }) }, icon('i-folder')) : null,
        ),
      );
      if (motionOK() && i < 12) li.classList.add('entering');
      return li;
    }));
  } catch (e) {
    console.error('Failed to load history:', e);
    toast(t('toast_history_load_failed'), 'error');
  }
}

function formatRelativeTime(ts) {
  const secs = Math.floor((Date.now() - ts) / 1000);
  if (secs < 60) return t('time_just_now');
  const mins = Math.floor(secs / 60);
  if (mins < 60) return t('time_mins_ago', { mins });
  const hours = Math.floor(mins / 60);
  if (hours < 24) return t('time_hours_ago', { hours });
  const days = Math.floor(hours / 24);
  if (days < 30) return t('time_days_ago', { days });
  return new Date(ts).toLocaleDateString();
}

init().catch((e) => { console.error(e); toast(t('toast_init_failed', { message: e.message }), 'error'); });
