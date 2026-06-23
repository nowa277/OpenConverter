<!--
╔══════════════════════════════════════════════════════════════════════╗
║  DreamSeed Project — AI Creator Contest Official README Template       ║
║                                                                      ║
║  Instructions:                                                       ║
║  1. Place this template at the top of the repository root README.     ║
║  2. Use the official DreamField public banner image URL.             ║
║  3. Please keep the DREAMFIELD_README_HEADER_START / END comments.    ║
║  4. Below the divider, feel free to write your own project content.  ║
╚══════════════════════════════════════════════════════════════════════╝
-->

<!-- DREAMFIELD_README_HEADER_START -->

<p align="center">
  <a href="https://www.dreamfield.top">
    <img src="https://www.dreamfield.top/dream-field/contest-readme/assets/dreamseed-readme-banner.png" alt="DreamSeed Contest Entry" width="100%" />
  </a>
</p>

<!-- DREAMFIELD_README_HEADER_END -->

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

---

## UI Preview

### Desktop Application UI
<p align="center">
  <img src="assets/screenshots/desktop_ui.png" alt="Desktop App UI" width="85%" />
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

* **arm64-v8a**: `openconverter-v***-android-arm64-v8a.apk` (Recommended, suitable for the vast majority of modern smartphones)
* **x86_64**: `openconverter-v***-android-x86_64.apk` (Suitable for running and debugging on Android Emulators)

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
./gradlew :app:test

# Compile and package Debug/Release APK
./gradlew :app:assembleRelease
```

---

## Disclaimer

This project is intended solely as a technical tool for personal audio learning, file format organization, and compatibility research. It does not provide, distribute, or store any copyrighted audio content. Users must strictly abide by relevant laws and regulations, respect the legitimate rights of music copyright holders, and must not use this tool for any copyright-infringing behaviors. Any legal disputes or controversies arising from the use of this tool shall be borne by the user, and have no association with the authors and contributors of this project.

---

## License

This project is open-source under the [Apache License 2.0](./LICENSE) agreement.
