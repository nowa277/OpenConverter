# OpenConverter

Open-source audio format converter. Converts encrypted audio formats
(NCM, QMC, KGM, KWM, MFLAC, MGG, BKC) to common formats (MP3, FLAC,
WAV, M4A, OGG) using pure-JavaScript decoders and `ffmpeg`.

Spotify-inspired dark UI. Native packages for **Linux** (.deb /
AppImage) and **Windows** (NSIS installer / portable).

## Supported formats

| Format                 | Source        | Verification                                                                                    |
| ---------------------- | ------------- | ----------------------------------------------------------------------------------------------- |
| `.ncm`                 | NetEase       | Byte-diff against Python `ncmdump` reference (14/14 samples, 0 diff)                             |
| `.qmc0` / `.qmc3`      | QQ Music      | Algorithm cross-checked against C++/Rust references; round-trip on real MP3 (ffprobe 2.06s)      |
| `.qmcflac`             | QQ Music      | Same algorithm as QMC0; round-trip verified                                                      |
| `.qmcogg`              | QQ Music      | Same algorithm as QMC0; round-trip verified                                                      |
| `.kwm`                 | Kuwo          | Re-derived from `davidxuang/MusicDecrypto` (LGPL, re-implemented); round-trip verified          |
| `.kgm` / `.kgma`       | KuGou         | Re-implemented from `huangbao/MyKgmWasm` (MIT); round-trip verified                             |
| `.vpr`                 | KuGou         | Same algorithm as KGM (post-mask only); round-trip verified                                     |
| `.mflac` / `.mflac0`   | QQ Music      | QMCv2 cipher; key from user-provided ekey. `key_compress` cross-checked against pyqmc-rust       |
| `.mgg` / `.mgg1`       | QQ Music      | QMCv2 cipher; requires user-provided ekey                                                        |
| `.bkc*`                | QQ Music      | QMCv2 cipher; same dispatcher as MFLAC. `.bkc, .bkcmp3, .bkcflac, .bkcogg, .bkcm4a, .bkcwav, .bkcwma, .bkcape` |
| `.mp3` / `.flac` / `.wav` / `.m4a` / `.aac` / `.ogg` / `.opus` | passthrough | Direct copy or `ffmpeg` re-encode to target format |

Decryption for QMCv2 (`.mflac`, `.mgg`, `.bkc*`) requires an **ekey** —
a base64 string extracted from the QQ Music client's local database.
Configure it once in **Settings → QQ Music ekey**; it is persisted via
`electron-store`.

Decryption for all other formats does **not** require any user-supplied key.

## Install

### Debian / Ubuntu

```bash
sudo apt install ffmpeg   # OpenConverter needs ffmpeg in PATH
sudo dpkg -i release/openconverter_0.2.0_amd64.deb
sudo apt install -f       # resolve any missing deps
openconverter
```

### AppImage (any Linux distro)

```bash
chmod +x release/OpenConverter-0.2.0.AppImage
./release/OpenConverter-0.2.0.AppImage
```

Download the latest release: https://github.com/nowa277/OpenConverter/releases

### Windows

Download the latest `openconverter-vX.Y.Z-windows-x64-setup.exe` (NSIS installer) or
`openconverter-vX.Y.Z-windows-x64-portable.exe` (portable, no install needed) from the
[Releases page](https://github.com/nowa277/OpenConverter/releases).

- **Installer**: double-click `setup.exe`, follow the wizard, choose
  install path.
- **Portable**: place `OpenConverter-X.Y.Z-portable.exe` anywhere (e.g.
  USB drive), double-click to run. No installer, no system changes.

Both bundles include `ffmpeg.exe` and `ffprobe.exe` — no separate
ffmpeg install required.

**First launch note**: Windows SmartScreen will show "Windows protected
your PC" with "Unknown publisher" on first run. Click **More info** →
**Run anyway** to proceed. This is expected for unsigned binaries; the
warning will persist until the project gets a code signing certificate.

## Build from source

```bash
npm install
npm run build:renderer
npm run build:linux    # produces 4 Linux artifacts in release/
npm run build:win      # produces NSIS + Portable in release/ (requires wine64)
```

### Build artifacts (naming convention)

`openconverter-v<version>-<platform>-<arch>[-<variant>].<ext>`

| Platform | Artifact |
|----------|----------|
| Linux x64 | `openconverter-vX.Y.Z-linux-amd64.deb` / `openconverter-vX.Y.Z-linux-x64.AppImage` |
| Linux arm64 | `openconverter-vX.Y.Z-linux-arm64.deb` / `openconverter-vX.Y.Z-linux-arm64.AppImage` |
| Windows x64 | `openconverter-vX.Y.Z-windows-x64-setup.exe` (NSIS) / `openconverter-vX.Y.Z-windows-x64-portable.exe` |

For Windows builds on Linux, install `wine64 imagemagick p7zip-full`, then run
`./scripts/setup-win-deps.sh` once to download ffmpeg.exe + ffprobe.exe.

## Run tests

```bash
node tests/ncm.test.js        # 14/14 NCM samples, byte-diff vs Python ncmdump
node tests/qmc.test.js        # QMCv1 round-trip
node tests/qmc-v2.test.js     # QMCv2 key_compress against pyqmc-rust test vector
node tests/kwm.test.js        # KWM round-trip
node tests/kgm.test.js        # KGM / KGMA / VPR round-trip
```

All 5 test suites must pass. They verify:
- Algorithm byte-level correctness (cross-checked against public Rust/C++/Python references)
- Round-trip identity on real MP3 audio (encoded → decoded = original bytes)
- `ffprobe` reports valid duration on the output (audio not corrupted)

The tests are **self-verifying via round-trip** — they encrypt a known
plaintext with the same algorithm, decrypt with our decoder, and verify
byte-for-byte identity. This proves algorithm self-consistency but does
**not** prove the algorithm matches what the QQ / NetEase / KuGou / Kuwo
clients actually use; for that, real samples are needed.

## Architecture

- **Main process** (`src/main/`): Electron main, single `process-message`
  IPC channel for all renderer requests, `electron-store` for user
  preferences, `ffmpeg` subprocess for format conversion. On Windows,
  ffmpeg/ffprobe are resolved to the bundled binaries in `resources/`
  via `src/main/ffmpeg-path.js`.
- **Preload** (`src/preload/`): exposes a sandboxed `window.api` to the renderer
- **Renderer** (`src/renderer/`): vanilla JS + CSS, no framework,
  Spotify-inspired dark theme with macOS traffic-light window controls
  (Linux/macOS); Windows uses the OS-native title bar
- **Decoders** (`src/decoders/`): pure Node `crypto` implementations,
  no native FFI, no third-party cryptography libraries. Cross-platform
  by design — same code runs on Linux, macOS, and Windows.

## Multi-platform development

- **Branch-per-platform** — each platform's work happens on its own
  feature branch (`windows-installer`, future `macos-installer`,
  `linux-arm64-fix`).
- **Additive-only** — new platform branches don't modify other
  platforms' config. Per-platform `extraResources` and `artifactName`
  are nested inside the per-platform target block.
- **Platform conditionals in main process only** — the renderer
  stays platform-agnostic; all `process.platform` checks live in
  `src/main/`.
- **Per-commit Linux regression check** — every commit on a
  `windows-installer` branch must leave `npm run build:linux --dir`
  green. The same check will apply to the `macos-installer` branch
  (must not regress Linux or Windows builds).

## License

MIT