/**
 * OpenConverter main process.
 *
 * Single IPC channel "process-message" (matches original's architecture but
 * with all commercial/IPC handlers removed).
 *
 * Methods supported (in order of use):
 *   - convert:start      { files: [paths], format, outputDir, quality }
 *   - convert:cancel     { jobId }
 *   - file:pickInput     { multi }
 *   - file:pickOutputDir
 *   - config:get
 *   - config:set         { patch }
 *   - os:info
 *
 * NO commercial fields anywhere. NO macOS-only calls. NO native FFI.
 */
const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');
const { run: ffmpegRun, probeDuration } = require('./ffmpeg');
const config = require('./config');
const decoders = require('../decoders');

const isDev = process.env.NODE_ENV === 'development';
let mainWindow = null;

// Active conversion jobs (for cancel)
const activeJobs = new Map();
let jobCounter = 0;

function createWindow() {
  if (mainWindow) return mainWindow;
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 720,
    minWidth: 880,
    minHeight: 560,
    backgroundColor: '#121212',
    title: 'OpenConverter',
    show: false,
    frame: false, // custom title bar rendered in renderer
    titleBarStyle: 'hidden', // macOS: hide inset title bar
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
  return mainWindow;
}

async function convertOne(jobId, inputPath, format, outputDir, quality) {
  const decoder = decoders.pickDecoder(inputPath);
  if (!decoder) {
    throw new Error(`No decoder for file: ${path.basename(inputPath)}`);
  }

  // Decrypt (or pass through if already a plain format)
  const { outputPath: decryptedPath, format: detectedFormat } = decoder.decodeFile(inputPath, outputDir);

  // If format already matches what user wants, we're done
  if (detectedFormat === format && !quality) {
    return { jobId, inputPath, outputPath: decryptedPath, format: detectedFormat };
  }

  // Otherwise convert with ffmpeg
  const ffmpegOut = decryptedPath.replace(/\.[^.]+$/, '') + `.${format}`;
  await ffmpegRun(decryptedPath, ffmpegOut, {
    format,
    quality,
    onProgress: ({ percent }) => {
      if (mainWindow) {
        mainWindow.webContents.send('convert:progress', { jobId, percent });
      }
    },
    signal: activeJobs.get(jobId)?.signal,
  });

  // If we re-encoded, remove the intermediate decrypted file
  if (ffmpegOut !== decryptedPath) {
    try { fs.unlinkSync(decryptedPath); } catch {}
  }
  return { jobId, inputPath, outputPath: ffmpegOut, format };
}

const HANDLERS = {
  'convert:start': async (data) => {
    const { files, format = 'mp3', outputDir, quality = '320k' } = data || {};
    if (!Array.isArray(files) || files.length === 0) throw new Error('files array required');
    if (!outputDir) throw new Error('outputDir required');

    fs.mkdirSync(outputDir, { recursive: true });
    const results = [];
    for (const f of files) {
      const jobId = `job-${++jobCounter}`;
      const controller = new AbortController();
      activeJobs.set(jobId, { signal: controller.signal });
      try {
        const r = await convertOne(jobId, f, format, outputDir, quality);
        results.push(r);
      } catch (e) {
        results.push({ jobId, inputPath: f, error: e.message });
      } finally {
        activeJobs.delete(jobId);
      }
    }
    return { results };
  },

  'convert:cancel': async (data) => {
    const job = activeJobs.get(data?.jobId);
    if (job) job.signal.abort();
    return { cancelled: true };
  },

  'file:pickInput': async (data) => {
    const { multi = true } = data || {};
    const r = await dialog.showOpenDialog(mainWindow, {
      title: 'Select audio files',
      properties: [multi ? 'multiSelections' : 'openFile', 'openFile'],
      filters: [
        { name: 'Encrypted audio', extensions: ['ncm', 'qmc0', 'qmc1', 'qmc2', 'qmcflac', 'kgm', 'kwm'] },
        { name: 'All audio', extensions: ['mp3', 'flac', 'wav', 'm4a', 'ogg'] },
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

  'config:get': async () => config.get(),
  'config:set': async (data) => { config.set(data?.patch || {}); return config.get(); },

  'os:info': async () => ({
    platform: process.platform,
    arch: process.arch,
    nodeVersion: process.versions.node,
    electronVersion: process.versions.electron,
    cpus: os.cpus()?.length || 0,
    homedir: os.homedir(),
    tmpdir: os.tmpdir(),
    release: os.release(),
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
  }),
};

ipcMain.handle('process-message', async (_evt, { method, data }) => {
  const handler = HANDLERS[method];
  if (!handler) throw new Error(`Unknown method: ${method}`);
  return handler(data);
});

// Notify renderer when maximize state changes (for icon swap)
function emitMaximizedChanged() {
  if (mainWindow) {
    mainWindow.webContents.send('win:maximizedChanged', { maximized: mainWindow.isMaximized() });
  }
}

app.whenReady().then(() => {
  createWindow();
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
