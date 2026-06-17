<div align="center">

# OpenConverter

### 跨平台音频格式转换工具链

<p align="center">
面向音频工作流的轻量级格式转换与解码工具<br/>
基于 JavaScript 解码管线 + FFmpeg 转码后端构建
</p>


[![Linux](https://img.shields.io/badge/Linux-FCC624?style=for-the-badge&logo=linux&logoColor=black)](#supported-platforms)
[![Windows](https://img.shields.io/badge/Windows-0078D4?style=for-the-badge&logo=windows11&logoColor=white)](#supported-platforms)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=black)](#supported-platforms)

[![License: MIT](https://img.shields.io/badge/License-MIT-green?style=for-the-badge&color=2ea44f)](LICENSE)

</div>


---

## 支持的格式

| 格式                 | 来源       |
| -------------------- | ---------- |
| `.ncm`               | 网易云   |
| `.kwm`               | 酷我   |
| `.kgm` / `.kgma` / `.vpr` 等 | 酷狗   |
| `.mgg` / `.mgg1` / `.bkc` 等 | QQ 音乐 |
| `.mp3` / `.flac` / `.wav` 等 | 明文   |

QMCv2 系列（`.mflac`、`.mgg`、`.bkc*`）的解密需要 **ekey** ——
从 QQ 音乐客户端本地数据库提取的 base64 字符串。在 **设置 → QQ
Music ekey** 里填一次，通过 `electron-store` 持久化。

## 安装

从 [Releases 页面](https://github.com/nowa277/OpenConverter/releases)
下载对应最新版本：

### Debian / Ubuntu

```bash
# AppImage（推荐）
chmod +x release/openconverter-v***-linux-x64.AppImage
./release/openconverter-v***-linux-x64.AppImage

# Deb包安装
sudo apt install ffmpeg   # OpenConverter 需要 ffmpeg 在 PATH 中
sudo dpkg -i release/openconverter-v***-linux-amd64.deb
sudo apt install -f       # 解决可能的依赖缺失
openconverter
```

### Windows

- **NSIS 安装包**：`openconverter-v***-windows-x64-setup.exe`
  双击 `setup.exe`，按向导选择安装路径。
- **便携版**（推荐）：`openconverter-v***-windows-x64-portable.exe`
  可以放在任何目录（U 盘等），双击直接运行，无需安装。

两个包都内置了 `ffmpeg.exe` 和 `ffprobe.exe`，**不需要**单独安装
ffmpeg。

**首次运行提示**：Windows SmartScreen 可能会显示 "Windows 已保护你的
电脑" 和 "未知发布者"。点击 **更多信息** → **仍要运行** 即可。
这是未签名二进制文件的正常现象，因为作者没钱购买代码签名证书。

## 从源码构建

```bash
npm install
npm run build:renderer
npm run build:linux    # 在 release/ 生成 4 个 Linux 产物
npm run build:win      # 在 release/ 生成 NSIS + Portable（需要 wine64）
```

### 声明

本项目仅作为音频/文件格式转换的技术工具使用，不涉及任何版权内容的提供或分发。使用者在使用过程中应尊重音乐作品及相关内容的版权与合法权益，不得将本工具用于侵犯任何著作权或相关权利人的行为。因使用本工具产生的任何法律责任或纠纷均由使用者自行承担，与项目作者无关。

### 产物命名规范

`openconverter-v<版本>-<平台>-<架构>[-<变体>].<扩展名>`

| 平台 / 架构 | 产物 |
|------------|------|
| Linux x64 | `openconverter-vX.Y.Z-linux-amd64.deb` / `openconverter-vX.Y.Z-linux-x64.AppImage` |
| Linux arm64 | `openconverter-vX.Y.Z-linux-arm64.deb` / `openconverter-vX.Y.Z-linux-arm64.AppImage` |
| Windows x64 | `openconverter-vX.Y.Z-windows-x64-setup.exe`（NSIS）/ `openconverter-vX.Y.Z-windows-x64-portable.exe` |

在 Linux 上构建 Windows 包时，需要先安装 `wine64 imagemagick
p7zip-full`，然后运行一次 `./scripts/setup-win-deps.sh` 下载
ffmpeg.exe + ffprobe.exe。

## 运行测试

```bash
node tests/ncm.test.js        # 14/14 NCM 样本，与 Python ncmdump 逐字节对比
node tests/qmc.test.js        # QMCv1 round-trip
node tests/qmc-v2.test.js     # QMCv2 key_compress 对照 pyqmc-rust 测试向量
node tests/kwm.test.js        # KWM round-trip
node tests/kgm.test.js        # KGM / KGMA / VPR round-trip
```

5 个测试套件都必须通过，验证：
- 算法字节级正确性（对照公开的 Rust/C++/Python 参考实现）
- 真实 MP3 音频的 round-trip 恒等性（编码 → 解码 = 原始字节）
- `ffprobe` 在输出上报告有效时长（音频未损坏）

测试通过 **round-trip 自验证** —— 用同一算法加密一段已知明文，再
用我们的解码器解密，逐字节比对恒等。这证明了算法的自洽性，但不
**证明**算法匹配 QQ / 网易云 / 酷狗 / 酷我客户端实际使用的算法；那
需要真实样本。

## 架构

- **主进程** (`src/main/`)：Electron 主进程，单一 `process-message`
  IPC 通道处理所有渲染进程请求，`electron-store` 保存用户配置，
  `ffmpeg` 子进程做格式转换。在 Windows 上，ffmpeg/ffprobe 通过
  `src/main/ffmpeg-path.js` 解析为 `resources/` 中内置的二进制。
- **预加载脚本** (`src/preload/`)：向渲染进程暴露沙箱化的
  `window.api`
- **渲染进程** (`src/renderer/`)：原生 JS + CSS，无框架，Spotify
  风格深色主题 + macOS 红黄绿窗口控制按钮（Linux / macOS）；
  Windows 使用系统原生标题栏
- **解码器** (`src/decoders/`)：纯 Node `crypto` 实现，无原生 FFI，
  无第三方加密库。设计上跨平台 —— 同一份代码在 Linux、macOS、
  Windows 上运行

## 多平台开发

- **每个平台独立分支** — 每个平台的工作在独立 feature 分支上
  （`windows-installer`、未来的 `macos-installer`、
  `linux-arm64-fix`）。
- **只增不删** — 新平台分支不修改其他平台的配置。平台相关的
  `extraResources` 和 `artifactName` 嵌套在各自的 target 块内。
- **平台分支判断只在主进程** — 渲染进程保持完全平台无关；
  所有 `process.platform` 检查都在 `src/main/`。
- **每次 commit 跑 Linux 回归检查** — `windows-installer` 分支上的
  每次 commit 都必须保持 `npm run build:linux --dir` 绿色。`macos-installer`
  分支上同样要保证不破坏 Linux 和 Windows 构建。

### Android

从 [Releases 页面](https://github.com/nowa277/OpenConverter/releases)
下载最新版本：

- **arm64-v8a**：`openconverter-v0.2.2-android-arm64-v8a.apk`（~3.5 MB，现代手机）
- **armeabi-v7a**：`openconverter-v0.2.2-android-armeabi-v7a.apk`（~3.1 MB，老年机/低端机）
- **x86_64**：`openconverter-v0.2.2-android-x86_64.apk`（~3.9 MB，模拟器测试用）

下载后在手机上点击 APK 安装（首次需开启"安装未知来源应用"权限）。
**APK 已用 v0.2.2 专用 keystore 签名**（非 debug 签名），手机可能提示"未知发布者"，
点"仍要安装"即可。

**功能**：11 种加密音频格式 → MP3 / FLAC / WAV / M4A / OGG。
**ekey 设置**：QQ Music v2 加密（MFLAC / MGG / BKC*）需要在 App 内"设置 → QQ Music ekey"里填一次。
**首次运行**：会请求"通知"权限（用于显示转换进度）。

**架构**：原生 Kotlin + Jetpack Compose，调用 NDK 构建的 ffmpeg 7.0.2 共享库
（音频 codec only，decoder-only），4 个 decoder 全部字节级 TDD 通过
（与桌面端输出 sha256 完全一致）。

**已知限制**：
- 重新编码（MP3→FLAC 等）暂未启用，原因是 ffmpeg 自编译时缺少 libmp3lame
  /libfdk-aac/libvorbis 外部依赖（参见 plan §12.1 的失败记录）。当前
  NCM 解密后是 MP3 字节（passthrough），可正常播放；要转码为 FLAC/OGG 等
  需要先用桌面端转换，或等待 v0.3.0 启用 ffmpeg encoder。
- APK 暂未上架 Play Store / F-Droid（v0.2.2 范围外）。

## 许可证

MIT
