/**
 * OpenConverter main process.
 *
 * Single IPC channel "process-message" carrying { method, data }. Methods:
 *
 *   convert:start        { files: [paths], format, outputDir, quality }
 *   convert:cancel       { jobId }
 *   convert:cancelAll
 *   file:pickInput       { multi }
 *   file:pickOutputDir
 *   file:showInFolder    { path }
 *   file:openPath        { path }
 *   config:get / config:set { patch }
 *   history:get / history:clear
 *   ffmpeg:check
 *   qqmusic:extractCookie
 *   kgg:importFile / kgg:triggerScan
 *   os:info
 *   win:minimize / win:toggleMaximize / win:close / win:isMaximized
 *   decoders:list
 *
 * Push events to the renderer: convert:progress, win:maximizedChanged.
 */
const { app, BrowserWindow, ipcMain, dialog, shell, nativeTheme } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');
const config = require('./config');
const decoders = require('../decoders');
const { resolveFfmpegPath, resolveFfprobePath } = require('./ffmpeg-path');
const ffmpeg = require('./ffmpeg');
const pipeline = require('./pipeline');
const HistoryStore = require('./history');
const kggKeys = require('./kgg-keys');
const dbCipher = require('../decoders/kgg/db-cipher');
const qqmusicAuth = require('./qqmusic-auth');

let historyStore = null;
function getHistoryStore() {
  if (!historyStore) historyStore = new HistoryStore(app.getPath('userData'));
  return historyStore;
}

function binPaths() {
  const env = { isPackaged: app.isPackaged, platform: process.platform, resourcesPath: process.resourcesPath };
  return { ffmpegBin: resolveFfmpegPath(env), ffprobeBin: resolveFfprobePath(env) };
}

const isDev = process.env.NODE_ENV === 'development';
let mainWindow = null;

// Active conversion jobs (for cancel)
const activeJobs = new Map();
let jobCounter = 0;

function send(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(channel, payload);
}

function createWindow() {
  if (mainWindow) return mainWindow;
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 720,
    minWidth: 880,
    minHeight: 560,
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#121212' : '#fafafa',
    title: 'OpenConverter',
    show: false,
    // Linux + macOS: custom traffic-light title bar. Windows: OS-native bar.
    frame: process.platform === 'win32',
    titleBarStyle: process.platform === 'win32' ? 'default' : 'hidden',
    icon: path.join(__dirname, '..', '..', 'build', 'icons', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, '..', 'preload', 'index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL);
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  } else {
    mainWindow.loadFile(path.join(__dirname, '..', '..', 'dist-renderer', 'index.html'));
  }
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('maximize', emitMaximizedChanged);
  mainWindow.on('unmaximize', emitMaximizedChanged);
  mainWindow.on('closed', () => { mainWindow = null; });
  return mainWindow;
}

/** Build decoder options (keys / cookies) for one input file. */
async function decodeOptsFor(inputPath) {
  const ext = path.extname(inputPath).toLowerCase();
  const cfg = config.get();
  const opts = {};

  if (decoders.listRequiresEkey().includes(ext)) {
    Object.assign(opts, { ekey: cfg.qmcEkey, qqCookie: cfg.qqCookie, qqGuid: cfg.qqGuid, qqUin: cfg.qqUin });
    if (!opts.qqCookie && process.platform === 'win32') {
      try {
        const authRes = await qqmusicAuth.extractCookie();
        if (authRes.ok && authRes.cookie) {
          Object.assign(opts, { qqCookie: authRes.cookie, qqGuid: authRes.guid, qqUin: authRes.uin });
          config.set({ qqCookie: authRes.cookie, qqGuid: authRes.guid, qqUin: authRes.uin });
        }
      } catch (err) {
        console.error('Auto-extract cookie failed:', err);
      }
    }
  }
  const lower = inputPath.toLowerCase();
  if (lower.endsWith('.kgg') || lower.endsWith('.kgg.flac')) {
    opts.keyPath = path.join(app.getPath('userData'), 'kgg.keys');
  }
  return opts;
}

// Every extension the app can ingest, without the leading dot.
function allInputExtensions() {
  const enc = decoders.listSupported().map((e) => e.replace(/^\./, ''));
  const plain = [...pipeline.PLAIN_AUDIO_EXTS].map((e) => e.slice(1));
  // ".kgg.flac" → the picker only understands single extensions; "flac" is already covered.
  return { enc: enc.filter((e) => !e.includes('.')), plain };
}

const HANDLERS = {
  'convert:start': async (data) => {
    const { files, format = 'mp3', outputDir, quality = '320k' } = data || {};
    if (!Array.isArray(files) || files.length === 0) throw new Error('files array required');
    if (!outputDir) throw new Error('outputDir required');

    fs.mkdirSync(outputDir, { recursive: true });
    const results = new Array(files.length);
    const history = getHistoryStore();
    const { ffmpegBin, ffprobeBin } = binPaths();
    const limit = Math.min(4, Math.max(2, os.cpus()?.length || 2));

    let historyChain = Promise.resolve();
    const appendToHistory = (record) => { historyChain = historyChain.then(() => history.append(record)); };

    const tasks = files.map((inputPath, index) => async () => {
      const jobId = `job-${++jobCounter}`;
      const controller = new AbortController();
      activeJobs.set(jobId, { controller, inputPath });
      send('convert:progress', { jobId, filePath: inputPath, stage: 'queued', percent: 0 });
      const started = Date.now();
      try {
        const decodeOpts = await decodeOptsFor(inputPath);
        const r = await pipeline.convertOne({
          inputPath, outputDir, format, quality, decodeOpts, ffmpegBin, ffprobeBin,
          signal: controller.signal,
          onProgress: ({ stage, percent }) => send('convert:progress', { jobId, filePath: inputPath, stage, percent }),
        });
        results[index] = { jobId, inputPath, outputPath: r.outputPath, format: r.format, durationMs: r.durationMs };
        appendToHistory({
          ts: Date.now(), inputName: path.basename(inputPath), targetFormat: format, status: 'success',
          outputName: path.basename(r.outputPath), outputPath: r.outputPath, durationMs: r.durationMs, error: null,
        });
      } catch (e) {
        const cancelled = e.message === 'aborted';
        results[index] = { jobId, inputPath, error: cancelled ? 'Cancelled' : e.message, cancelled };
        if (!cancelled) {
          appendToHistory({
            ts: Date.now(), inputName: path.basename(inputPath), targetFormat: format, status: 'failed',
            outputName: null, outputPath: null, durationMs: Date.now() - started, error: e.message,
          });
        }
      } finally {
        activeJobs.delete(jobId);
      }
    });

    await pipeline.parallelLimit(limit, tasks);
    await historyChain;
    return { results };
  },

  'convert:cancel': async (data) => {
    const job = activeJobs.get(data?.jobId);
    if (job) job.controller.abort();
    return { cancelled: !!job };
  },

  'convert:cancelAll': async () => {
    let n = 0;
    for (const job of activeJobs.values()) { job.controller.abort(); n++; }
    return { cancelled: n };
  },

  'history:get': async () => getHistoryStore().readAll(),
  'history:clear': async () => { await getHistoryStore().clear(); return { ok: true }; },

  'file:pickInput': async (data) => {
    const { multi = true } = data || {};
    const { enc, plain } = allInputExtensions();
    const r = await dialog.showOpenDialog(mainWindow, {
      title: 'Select audio files',
      properties: [multi ? 'multiSelections' : 'openFile', 'openFile'],
      filters: [
        // The Linux GTK picker uses the FIRST filter as default, so it must
        // include every format the app can convert.
        { name: 'Audio files (encrypted + common)', extensions: [...enc, ...plain] },
        { name: 'Encrypted audio only', extensions: enc },
        { name: 'Common audio', extensions: plain },
        { name: 'All files', extensions: ['*'] },
      ],
    });
    return { files: r.canceled ? [] : r.filePaths };
  },

  'file:pickOutputDir': async () => {
    const r = await dialog.showOpenDialog(mainWindow, {
      title: 'Select output directory',
      properties: ['openDirectory', 'createDirectory'],
    });
    return { dir: r.canceled ? null : r.filePaths[0] };
  },

  'file:showInFolder': async (data) => {
    const p = data?.path;
    if (!p || typeof p !== 'string') return { ok: false };
    if (fs.existsSync(p)) { shell.showItemInFolder(p); return { ok: true }; }
    // File may have been moved; fall back to opening its directory.
    const dir = path.dirname(p);
    if (fs.existsSync(dir)) { await shell.openPath(dir); return { ok: true, fallback: true }; }
    return { ok: false };
  },

  'file:openPath': async (data) => {
    const p = data?.path;
    if (!p || typeof p !== 'string' || !fs.existsSync(p)) return { ok: false };
    const err = await shell.openPath(p);
    return { ok: !err, error: err || undefined };
  },

  'config:get': async () => config.get(),
  'config:set': async (data) => { config.set(data?.patch || {}); return config.get(); },

  'ffmpeg:check': async () => ffmpeg.checkFfmpeg({ ffmpegBin: binPaths().ffmpegBin }),

  'qqmusic:extractCookie': async () => qqmusicAuth.extractCookie(),

  'kgg:importFile': async () => {
    const r = await dialog.showOpenDialog(mainWindow, {
      title: 'Select KGG key file or KGMusicV3.db',
      properties: ['openFile'],
      filters: [
        { name: 'KGG key sources (*.key, *V3.db)', extensions: ['key', 'db'] },
        { name: 'All files', extensions: ['*'] },
      ],
    });
    if (r.canceled || r.filePaths.length === 0) return { imported: false };
    const filePath = r.filePaths[0];
    const buf = fs.readFileSync(filePath);

    const keysPath = path.join(app.getPath('userData'), 'kgg.keys');
    const currentMap = kggKeys.loadKeysMap(keysPath);
    const initialSize = currentMap.size;

    const isDb = filePath.endsWith('.db') || buf.subarray(0, 15).toString().includes('SQLite') || dbCipher.isEncryptedHeader(buf);
    const incoming = isDb ? await kggKeys.importFromDb(buf) : decoders.kgg.parseKeyMap(buf.toString('utf-8'));
    for (const [id, val] of incoming.entries()) currentMap.set(id, val);

    const newSize = currentMap.size;
    if (newSize > initialSize) kggKeys.saveKeysMap(keysPath, currentMap);
    return { imported: true, added: newSize - initialSize, total: newSize };
  },

  'kgg:triggerScan': async () => kggKeys.autoScanKeys(app.getPath('userData')),

  'os:info': async () => ({
    platform: process.platform,
    arch: process.arch,
    nodeVersion: process.versions.node,
    electronVersion: process.versions.electron,
    appVersion: app.getVersion(),
    cpus: os.cpus()?.length || 0,
    homedir: os.homedir(),
    tmpdir: os.tmpdir(),
    release: os.release(),
    systemDark: nativeTheme.shouldUseDarkColors,
  }),

  'win:minimize': async () => { if (mainWindow) mainWindow.minimize(); return { ok: true }; },
  'win:toggleMaximize': async () => {
    if (!mainWindow) return { ok: false };
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
    return { ok: true, maximized: mainWindow.isMaximized() };
  },
  'win:close': async () => { if (mainWindow) mainWindow.close(); return { ok: true }; },
  'win:isMaximized': async () => ({ maximized: mainWindow?.isMaximized() || false }),

  'decoders:list': async () => ({
    implemented: decoders.listImplemented(),
    supported: decoders.listSupported(),
    requiresKey: decoders.listByKeyRequirement().withKey,
    plain: [...pipeline.PLAIN_AUDIO_EXTS],
  }),
};

ipcMain.handle('process-message', async (_evt, { method, data }) => {
  const handler = HANDLERS[method];
  if (!handler) throw new Error(`Unknown method: ${method}`);
  return handler(data);
});

function emitMaximizedChanged() {
  if (mainWindow) send('win:maximizedChanged', { maximized: mainWindow.isMaximized() });
}

nativeTheme.on('updated', () => send('theme:systemChanged', { dark: nativeTheme.shouldUseDarkColors }));

app.whenReady().then(() => {
  createWindow();
  if (config.get().kggAutoScan) {
    kggKeys.autoScanKeys(app.getPath('userData')).catch(() => {});
  }
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

// Security: deny all new-window / navigation requests to remote URLs
app.on('web-contents-created', (_e, contents) => {
  contents.setWindowOpenHandler(() => ({ action: 'deny' }));
  contents.on('will-navigate', (e, url) => {
    if (!url.startsWith('file://') && !url.startsWith('http://127.0.0.1:3344')) e.preventDefault();
  });
});
