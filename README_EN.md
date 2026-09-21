<div align="center">

# OpenConverter

### Cross-platform lightweight audio format converter and local decoding toolchain

English | [简体中文](./README.md)

<p align="center">
A lightweight format conversion and decryption tool for audio workflows.<br/>
<b>Desktop client</b> built with JavaScript decoding pipeline + Electron + FFmpeg transcoding backend;<br/>
<b>Android client</b> implemented with Jetpack Compose + pure Kotlin decoders + FFmpegKit architecture.
</p>

[![Linux](https://img.shields.io/badge/Linux-FCC624?style=for-the-badge&logo=linux&logoColor=black)](#desktop-installation)
[![Windows](https://img.shields.io/badge/Windows-0078D4?style=for-the-badge&logo=windows11&logoColor=white)](#desktop-installation)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=black)](#android-installation)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-D22128?style=for-the-badge)](LICENSE)

</div>

---

## Project Highlights

* **Privacy First, Fully Offline**: All decryption, transcoding, and processing run completely on the local device. No audio data is uploaded, zero network interaction, safe and secure.
* **True Audio Transcoding (FFmpeg)**: Not a simple rename or extraction. Built-in FFmpeg / FFmpegKit transcoding backend supports converting to MP3, FLAC, WAV, M4A, and OGG, with customizable output bitrates (e.g., 320 kbps, 256 kbps, etc.).

### What's new in v1.4.3 (Android)
* **Tags and cover from the .ncm file**: title / artist / album and the embedded picture are written into MP3 / FLAC / M4A (WAV/OGG skip cover). Same-format output is remuxed so tags are not dropped.
* **Optional local NetEase lyrics**: Settings → NetEase lyrics cache. Pick `files` or a copy of `LrcDownload` / `LrcCache`. Matching uses the song id inside the .ncm and writes a sibling `.lrc`. Missing lyrics do not fail conversion. No network, no root.

### What's new in v1.4.2 (Android)
* **Streaming decrypt for the remaining formats**: `.ncm` / `.kwm` / `.qmc*` / `.mflac` / `.mgg` no longer load the whole file into the heap, so large NetEase / Kuwo / QQ Music tracks also avoid OOM.
* **KGM / KGG stay streamed**: `.kgm` / `.kgma` / `.vpr` / `.kgg` keep the 1.4.1 chunked path.

### What's new in v1.4.1 (Android)
* **Large-file OOM fix**: `.kgm` / `.kgma` / `.vpr` now decrypt in a stream, so 35MB+ tracks no longer load the whole file into the heap.
* **Newer KuGou key locations**: scans `mggkey*`, `KGMusicV3.db`, and `kugou_music_v2.db` across official / lite / HiFi packages, with a 20MB per-file cap.

### What's new in v1.4.0 (Android)
* **Direct Root Sync (No Shizuku needed)**: Native Root extraction pipeline adapting across `su 0`, `su -c`, KernelSU, APatch, and toybox to extract KuGou MMKV and SQLite decryption keys in one click.
* **Public Storage Key Auto-Scanner**: Automatically scans `/sdcard/Download`, `/sdcard/Music`, and `/sdcard/kgmusic` for `kgg.key`, `mggkey*`, and database files, parsing and merging keys without manual intervention.
* **Conversion Engine Missing Key Auto-Healing**: When encountering unindexed audio files during batch conversion, the engine immediately initiates on-demand key scanning and retries decryption.
* **SAF Document Picker Path Memory**: Implements `EXTRA_INITIAL_URI` support to automatically open the picker in the last selected or output folder.
* **Persistent Settings & SAF Permissions**: Preserves output folder SAF write access (via `takePersistableUriPermission`), format preferences, and bitrates across app restarts.
* **Redesigned Settings Screen**: KuGou key status card now clearly displays readiness (Direct Root / Ready) and total indexed keys.

### What's new in v0.3.8 (desktop / CLI)
* **Streaming decrypt for every cipher**: `.ncm` / `.kwm` / `.qmc*` / `.mflac` / `.mgg` / `.kgg` now follow the same 64KiB chunked path as `.kgm`, so large files are not read entirely into memory.

### What's new in v0.3.7 (desktop / CLI)
* **Streaming KGM decrypt**: desktop and CLI read/write `.kgm` / `.kgma` / `.vpr` in 64KiB chunks instead of loading the whole file.

### What's new in v0.3.6 (desktop)
* **Smoother UI**: the queue is now rendered incrementally (progress bars really animate), with view transitions, a decrypt-stage shimmer, an overall progress bar, stacked toasts and a full-window drop overlay. Adds **System theme**, *Reduce motion* and *auto-clear finished items*.
* **More control**: cancel a running batch, remove single files, *Show in folder* for finished items. Same-container inputs are copied instead of being lossy re-encoded (mp3→mp3); OGG/Opus at 320k is clamped to libopus' 256k limit (previously always failed).
* **Tags & cover art preserved**: NCM title / artist / album / cover are embedded into the output (FLAC / MP3 / M4A); existing cover art survives plain-audio transcodes.
* **Performance**: decryption runs in a worker thread so the main process and UI stay responsive on large files; ffmpeg progress is parsed from machine-readable `-progress` output.
* **QMCv2 fixes**: STag-headed files were sliced incorrectly and failed to decrypt; QTag length endianness, ekey field index and base64 validation are fixed, with 100+ unit/integration tests and GitHub Actions CI.

---

## UI Preview

### Desktop Application UI
<p align="center">
  <img src="assets/screenshots/new_linux_app.png" alt="Desktop App UI" width="85%" />
</p>

### Android Mobile UI
<p align="center">
  <img src="assets/screenshots/android_success_queue.png" alt="Android Queue" width="40%" />
  &nbsp;&nbsp;
  <img src="assets/screenshots/android_history_screen.png" alt="Android History" width="40%" />
  &nbsp;&nbsp;
</p>

---

## Supported Encrypted Formats

| Extension | Source Platform | Decryption Method & Requirements |
|:---|:---|:---|
| `.ncm` | NetEase Cloud Music | Direct decryption via local algorithm |
| `.kwm` | KuWo Music | Direct decryption via local algorithm |
| `.kgm` / `.kgma` / `.vpr` | KuGou Music / Viper | Direct decryption via local algorithm |
| `.kgg` / `.kgg.flac` | KuGou Music | Desktop auto-scans disks; Android supports Native Root sync, public storage scanning, and manual import of `kgg.key` / `KGMusicV3.db` |
| `.mgg` / `.mgg1` / `.bkc` | QQ Music | Requires configuring the **ekey** once in **Settings/More** (a base64 string extracted from the local QQ Music client database), which the app persists via secure storage |
| `.mp3` / `.flac` / `.wav` | Any Platform | Import directly for general format or bitrate transcoding |

---

## Installation Guide

### Desktop Installation

Download the latest installer package for your operating system from the [Releases page](https://github.com/nowa277/OpenConverter/releases):

#### Debian / Ubuntu
```bash
# AppImage installation and execution (Recommended)
chmod +x release/openconverter-v***-linux-x64.AppImage
./release/openconverter-v***-linux-x64.AppImage

# Deb package installation (Note: OpenConverter on Linux requires ffmpeg in the system PATH)
sudo apt install ffmpeg
sudo dpkg -i release/openconverter-v***-linux-amd64.deb
sudo apt install -f  # Fix potentially missing dependencies
openconverter
```

#### Windows
* **Portable Version (Recommended)**: `openconverter-v***-windows-x64-portable.exe`
  Double-click to run directly, portable.
* **NSIS Installer**: `openconverter-v***-windows-x64-setup.exe`
  Double-click to install via wizard.
* *Note: The Windows client has built-in `ffmpeg.exe` and `ffprobe.exe`, so no manual FFmpeg installation is required. If the Windows Defender unsigned prompt pops up on first launch, click "More info" -> "Run anyway".*

---

### Android Installation

Download the latest APK files from the [Releases page](https://github.com/nowa277/OpenConverter/releases) to install:

* **arm64-v8a**: `openconverter-v1.4.3-android-arm64-v8a.apk` (Recommended, suitable for the vast majority of modern smartphones)
* **x86_64**: `openconverter-v1.4.3-android-x86_64.apk` (Suitable for running and debugging on Android Emulators)

KGG v5 requires per-track keys. In **v1.4.3**, several automatic mechanisms are available:
1. **Rooted Devices**: One-click Direct Root sync via Settings (supports KernelSU / APatch / Magisk) directly extracts MMKV and SQLite keys;
2. **Non-Rooted Devices**: Automatic public storage scanner scans `/sdcard/Download` or `/sdcard/Music` for `kgg.key` or `KGMusicV3.db`. You can also manually import keys via the system document picker. All keys remain safely on-device.

### Auto Key Fetch Guide and Success Demo
Supports one-click automatic key acquisition for KuGou and QQ Music formats. In the "Settings" page, you can automatically scan memory to get the QQ Music Cookie and fetch decryption info. KuGou Music also supports one-click full-disk automatic key scanning:
<p align="center">
  <img src="assets/screenshots/example.png" alt="Auto Key Fetch Guide" width="85%" />
</p>

Once successfully configured, you can simply drag and drop encrypted files for fully automated batch decryption and conversion:
<p align="center">
  <img src="assets/screenshots/history_presentation.png" alt="Successful Conversion History" width="85%" />
</p>

> [!IMPORTANT]
> **Android KGG Decryption Guide:**
> 1. **Rooted Phones**: One-tap Native Root sync in Settings unlocks all downloaded KGG tracks immediately.
> 2. **Non-Rooted Phones**: Copy PC KuGou's `KGMusicV3.db` (usually at `C:\Users\Public\KuGou\KGMusic\KGMusicV3.db`) or exported `kgg.key` text file into your phone's `Download` or `Music` directory. OpenConverter auto-discovers and imports keys upon launch. Manual import via Settings is also supported.
> 3. **Downgrade Alternative**: Older KuGou versions download tracks as `.kgm` / `.kgma` without database requirements, allowing instant decryption without root or imports.

---

## Build and Development

If you need to build the project from source, please ensure Node.js 18+ and Android SDK (for compiling the Android version) are configured on your computer.

### Compile Desktop Client (Electron)
```bash
# Install dependencies
npm install

# Compile frontend static assets
npm run build:renderer

# Package Linux binaries (AppImage/Deb)
npm run build:linux

# Package Windows binaries (requires wine64 environment)
npm run build:win
```

### Compile Android Client
```bash
cd android

# Run local unit tests
./gradlew testDebugUnitTest connectedDebugAndroidTest

# Compile and package Debug/Release APK
./gradlew :app:assembleRelease
```

Real KGG fixtures are not committed. Maintainers can run the environment-gated byte-equivalence check on a booted Android device or emulator:

```bash
KGG_FIXTURE_DIR=/local/kgg \
KGG_KEY_SOURCE=/local/KGMusicV3.db \
KGG_EXPECTED_DIR=/local/reference \
android/scripts/verify-kgg-v5.sh
```

`reference` must contain plaintext produced from the same inputs by an independent reference implementation, preserving relative paths and using the actual container extension. The script stages private material only for the test and removes host/device staging on exit.

---

## Disclaimer

This project is intended solely as a technical tool for personal audio learning, file format organization, and compatibility research. It does not provide, distribute, or store any copyrighted audio content. Users must strictly abide by relevant laws and regulations, respect the legitimate rights of music copyright holders, and must not use this tool for any copyright-infringing behaviors. Any legal disputes or controversies arising from the use of this tool shall be borne by the user, and have no association with the authors and contributors of this project.

---

## License

This project is open-source under the [Apache License 2.0](./LICENSE) agreement.
