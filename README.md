# OpenConverter

开源加密音频格式转换器。把网易云、QQ 音乐、酷狗、酷我等平台的
**加密音频**（NCM / QMC / KGM / KWM / MFLAC / MGG / BKC 等）转换为
通用格式（MP3 / FLAC / WAV / M4A / OGG），全部使用纯 JavaScript
解码器 + `ffmpeg` 转码。

Spotify 风格的深色 UI，原生支持 **Linux**（.deb / AppImage）和
**Windows**（NSIS 安装包 / 便携版）。

## 支持的格式

| 格式                 | 来源       | 说明                                                                                              |
| -------------------- | ---------- | ------------------------------------------------------------------------------------------------- |
| `.ncm`               | 网易云     | 与 Python `ncmdump` 参考实现逐字节对比（14/14 样本，0 差异）                                      |
| `.qmc0` / `.qmc3`    | QQ 音乐    | 算法对照 C++/Rust 参考实现；真实 MP3 round-trip（ffprobe 2.06s）                                |
| `.qmcflac`           | QQ 音乐    | 与 QMC0 相同算法；round-trip 已验证                                                                |
| `.qmcogg`            | QQ 音乐    | 与 QMC0 相同算法；round-trip 已验证                                                                |
| `.kwm`               | 酷我       | 从 `davidxuang/MusicDecrypto`（LGPL）重写；round-trip 已验证                                       |
| `.kgm` / `.kgma`     | 酷狗       | 从 `huangbao/MyKgmWasm`（MIT）重写；round-trip 已验证                                              |
| `.vpr`               | 酷狗       | 与 KGM 相同算法（仅 post-mask 不同）；round-trip 已验证                                            |
| `.mflac` / `.mflac0` | QQ 音乐    | QMCv2 加密；密钥来自用户提供的 ekey。`key_compress` 对照 pyqmc-rust                                |
| `.mgg` / `.mgg1`     | QQ 音乐    | QMCv2 加密；需要用户提供的 ekey                                                                    |
| `.bkc*`              | QQ 音乐    | QMCv2 加密；与 MFLAC 共用 dispatcher。`.bkc, .bkcmp3, .bkcflac, .bkcogg, .bkcm4a, .bkcwav, .bkcwma, .bkcape` |
| `.mp3` / `.flac` / `.wav` / `.m4a` / `.aac` / `.ogg` / `.opus` | 明文   | 直接复制，或用 `ffmpeg` 重编码为目标格式                                                          |

QMCv2 系列（`.mflac`、`.mgg`、`.bkc*`）的解密需要 **ekey** ——
从 QQ 音乐客户端本地数据库提取的 base64 字符串。在 **设置 → QQ
Music ekey** 里填一次，通过 `electron-store` 持久化。

其他格式的解密**不需要**任何用户提供的密钥。

## 安装

### Debian / Ubuntu

```bash
sudo apt install ffmpeg   # OpenConverter 需要 ffmpeg 在 PATH 中
sudo dpkg -i release/openconverter-v0.2.2-linux-amd64.deb
sudo apt install -f       # 解决可能的依赖缺失
openconverter
```

### AppImage（任何 Linux 发行版）

```bash
chmod +x release/openconverter-v0.2.2-linux-x64.AppImage
./release/openconverter-v0.2.2-linux-x64.AppImage
```

最新发布：https://github.com/nowa277/OpenConverter/releases

### Windows

从 [Releases 页面](https://github.com/nowa277/OpenConverter/releases)
下载最新版本：

- **NSIS 安装包**：`openconverter-v0.2.2-windows-x64-setup.exe`
  双击 `setup.exe`，按向导选择安装路径。
- **便携版**：`openconverter-v0.2.2-windows-x64-portable.exe`
  可以放在任何目录（U 盘等），双击直接运行，无需安装。

两个包都内置了 `ffmpeg.exe` 和 `ffprobe.exe`，**不需要**单独安装
ffmpeg。

**首次运行提示**：Windows SmartScreen 会显示 "Windows 已保护你的
电脑" 和 "未知发布者"。点击 **更多信息** → **仍要运行** 即可。
这是未签名二进制文件的正常现象，未来添加代码签名证书后警告会消失。

## 从源码构建

```bash
npm install
npm run build:renderer
npm run build:linux    # 在 release/ 生成 4 个 Linux 产物
npm run build:win      # 在 release/ 生成 NSIS + Portable（需要 wine64）
```

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

## 许可证

MIT
