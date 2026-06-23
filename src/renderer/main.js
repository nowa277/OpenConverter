/**
 * OpenConverter renderer — vanilla JS, no framework. Talks to main via window.api.
 */

const api = window.api;

const state = {
  files: [], // { path, name, status: 'pending' | 'decoding' | 'done' | 'error', progress: 0, outputPath, error }
  outputDir: '',
  format: 'mp3',
  quality: '320k',
};

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
  $('os-info').textContent = `OpenConverter v${os.appVersion}`;

  const cfg = await api.invoke('config:get');
  if (cfg.format) state.format = cfg.format;
  if (cfg.quality) state.quality = cfg.quality;
  if (cfg.outputDir) state.outputDir = cfg.outputDir;
  if (cfg.qmcEkey) $('ekey-input').value = cfg.qmcEkey;
  $('format-select').value = state.format;
  $('quality-select').value = state.quality;

  const decoders = await api.invoke('decoders:list');
  $('supported-formats').textContent =
    'Supported: ' + decoders.supported.map((e) => e.replace('.', '')).join(' ');
  $('verified-formats').innerHTML = decoders.implemented
    .map((e) => `<li>${e}</li>`)
    .join('') || '<li>None yet</li>';
  $('status-list').innerHTML = `
    <li>NCM — verified (14/14 samples, byte-diff against Python ncmdump)</li>
    <li>QMC0 / QMC3 / QMCFLAC / QMCOGG — verified (round-trip on real MP3)</li>
    <li>QMCv2 (.mflac / .mgg / .bkc) — implemented, requires QQ Music ekey from client DB</li>
    <li>KGM / KGMA / VPR — implemented (round-trip on real MP3)</li>
    <li>KWM — implemented (round-trip on real MP3)</li>
    <li>ffmpeg ${await checkFfmpeg()}</li>
  `;

  // Default output dir to ~/Music/OpenConverter if not set
  if (!state.outputDir) {
    state.outputDir = `${os.homedir}/Music/OpenConverter`.replace(/\$(\w+)/g, (_, n) => ({ homedir: os.homedir }[n] || ''));
  }
  updateOutputDisplay();

  bindEvents();
  updateQueue();
}

async function checkFfmpeg() {
  try {
    const os = await api.invoke('os:info');
    return `(system ffmpeg in PATH)`;
  } catch { return '(not detected)'; }
}

function bindEvents() {
  // Format/quality
  $('format-select').addEventListener('change', (e) => { state.format = e.target.value; api.invoke('config:set', { patch: { format: e.target.value } }); });
  $('quality-select').addEventListener('change', (e) => { state.quality = e.target.value; api.invoke('config:set', { patch: { quality: e.target.value } }); });

  // ekey (QQ Music)
  $('ekey-save-btn').addEventListener('click', async () => {
    const v = $('ekey-input').value.trim();
    await api.invoke('config:set', { patch: { qmcEkey: v } });
    toast(v ? 'QQ Music ekey saved' : 'QQ Music ekey cleared', 'ok');
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
    toast('History cleared', 'ok');
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
  $('output-dir').textContent = state.outputDir || 'Not set';
}

function updateQueue() {
  $('queue').hidden = state.files.length === 0;
  $('queue-count').textContent = state.files.length;
  $('convert-btn').disabled = state.files.length === 0;

  $('queue-list').innerHTML = state.files.map((f, i) => {
    const statusText = {
      pending: 'Ready',
      decoding: `${f.progress.toFixed(0)}%`,
      done: 'Done',
      error: 'Error',
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
    toast('Choose an output folder first', 'error');
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
    if (success > 0 && fail === 0) toast(`Converted ${success} file(s)`, 'ok');
    else if (success > 0) toast(`Converted ${success}, ${fail} failed`, '');
    else toast('All conversions failed', 'error');
  } catch (e) {
    toast(e.message || 'Conversion failed', 'error');
  } finally {
    $('convert-btn').disabled = state.files.length === 0;
  }
}

init().catch((e) => { console.error(e); toast('Init failed: ' + e.message, 'error'); });

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
        const statusText = r.status === 'success' ? 'Success' : 'Failed';
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
    toast('Failed to load history', 'error');
  }
}

function formatRelativeTime(ts) {
  const diff = Date.now() - ts;
  const secs = Math.floor(diff / 1000);
  if (secs < 60) return 'Just now';
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(ts).toLocaleDateString();
}
