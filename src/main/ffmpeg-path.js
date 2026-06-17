/**
 * Pure helpers: resolve paths to the bundled ffmpeg + ffprobe executables.
 *
 * Pure functions — take isPackaged/platform/resourcesPath explicitly so
 * they can be unit-tested without spinning up Electron. The main process
 * wraps these with the actual Electron `app` context.
 *
 * Rules:
 *   - Packaged Windows build → resourcesPath/ffmpeg.exe (and ffprobe.exe),
 *     bundled by electron-builder extraResources.
 *   - Packaged Linux / macOS, or any dev mode → 'ffmpeg' / 'ffprobe' (let
 *     the system PATH resolve them).
 */
const path = require('node:path');

function resolveFfmpegPath({ isPackaged, platform, resourcesPath }) {
  if (isPackaged && platform === 'win32' && resourcesPath) {
    return path.join(resourcesPath, 'ffmpeg.exe');
  }
  return 'ffmpeg';
}

function resolveFfprobePath({ isPackaged, platform, resourcesPath }) {
  if (isPackaged && platform === 'win32' && resourcesPath) {
    return path.join(resourcesPath, 'ffprobe.exe');
  }
  return 'ffprobe';
}

module.exports = { resolveFfmpegPath, resolveFfprobePath };
