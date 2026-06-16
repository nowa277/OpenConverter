# OpenConverter

Open-source audio format converter for Linux. Converts encrypted audio formats
(NCM, QMC, KGM, KWM) to common formats (MP3, FLAC, WAV, M4A, OGG) using pure
JavaScript decoders and `ffmpeg`.

## Status

| Format | Verified | Notes |
|--------|----------|-------|
| `.ncm` (NetEase Cloud Music) | ✅ **14/14 samples** | Byte-diff against Python `ncmdump` reference = 0 |
| `.qmc0` / `.qmc1` / `.qmc2` / `.qmcflac` | ❌ | No samples available; algorithm not yet implemented |
| `.kgm` / `.kwm` (KuGou) | ❌ | No samples available; algorithm not yet implemented |
| `.bkc*` / `.vpr` / `.mflac*` / `.mgg*` | ❌ | Not implemented |

**Do not trust this README's "✅" rows as verified unless you have actually
listened to the output yourself.** The byte-diff confirms the audio data is
identical to a known-good reference, but listening verifies there's no
subtle corruption in the audio path.

## Install

### Debian / Ubuntu (apt)
```bash
sudo apt install ffmpeg  # OpenConverter needs ffmpeg in PATH
sudo dpkg -i release/openconverter_0.1.0_amd64.deb
sudo apt install -f   # resolve any missing deps
openconverter
```

### AppImage (any Linux distro)
```bash
chmod +x release/OpenConverter-0.1.0.AppImage
./release/OpenConverter-0.1.0.AppImage
```

## Build from source

```bash
npm install
npm run build:renderer
npx electron-builder --linux      # produce deb + AppImage in release/
```

## CLI mode (for testing)

```bash
node src/cli.js path/to/file.ncm --output-dir=./out
node src/cli.js --format=flac --quality=320k path/to/file.ncm path/to/another.ncm
```

## Run tests

```bash
node tests/ncm.test.js   # byte-diff against Python ncmdump
```

## Architecture

- **Main process** (`src/main/`): Electron main, single `process-message` IPC channel
  for all requests, `electron-store` for user preferences (only non-commercial
  fields), `ffmpeg` for format conversion
- **Preload** (`src/preload/`): exposes a safe `window.api` to the renderer
- **Renderer** (`src/renderer/`): vanilla JS + CSS, no framework, Spotify-inspired
  dark theme
- **Decoders** (`src/decoders/`): pure Node `crypto` implementations
  - `ncm.js` — NetEase NCM, fully verified
  - `qmc.js`, `kgm.js`, `kwm.js` — algorithm stubs, throw on use

## What was removed (commercial elements)

This is a clean-room reimplementation; the following are **completely absent**
from the codebase (verified by `tests/check-no-commercial.sh`):

- All VIP-related fields: `vip_type`, `use_num`, `trialData`, `LoadUrl`, `loadUrl`,
  `doLoadInfo`, `adInfo`, `package_validity`, `ol_token`, `is_online`,
  `appStoreProductConfig`, `permanentMemberId`, `subscriptionId`, `role`,
  `receiptData`, `webviewUrl`
- All Chinese commercial strings: `永久`, `试用次数`, `升级`, `购买`, `会员`,
  `授权`, `许可证`
- All remote commercial endpoints (jiangxiatech.com, onlinedo.cn,
  callmysoft.com, buy.itunes.apple.com/verifyReceipt, etc.)
- The `@inigolabs/ffi-napi` native library (macOS-only dylib) — replaced
  entirely by pure-JS implementations
- `@sentry/electron`, `electron-updater` (telemetry, auto-update)
- `macos-version`, `plist` (macOS-only)
- Any license-file / serial-number / activation logic

## License

MIT
