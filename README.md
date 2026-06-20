<!--
╔══════════════════════════════════════════════════════════════════════╗
║  DreamSeed 种梦计划 — AI创造者大赛  官方 README 模板                ║
║                                                                      ║
║  使用说明：                                                          ║
║  1. 将本模板放在参赛仓库根目录 README.md 的顶部                       ║
║  2. 头图使用 DreamField 官方公开活动图片地址                         ║
║  3. 请保留 DREAMFIELD_README_HEADER_START / END 标识                 ║
║  4. 分割线以下供创作者自由编写项目内容                               ║
╚══════════════════════════════════════════════════════════════════════╝
-->

<!-- DREAMFIELD_README_HEADER_START -->

<p align="center">
  <a href="https://www.dreamfield.top">
    <img src="https://www.dreamfield.top/dream-field/contest-readme/assets/dreamseed-readme-banner.png" alt="DreamSeed 种梦计划参赛作品" width="100%" />
  </a>
</p>

<!-- DREAMFIELD_README_HEADER_END -->

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

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-D22128?style=for-the-badge)](LICENSE)

</div>

---

## 介绍

**功能**：11 种加密音频格式 → MP3 / FLAC / WAV / M4A / OGG 真实转码。

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

### Android

从 [Releases 页面](https://github.com/nowa277/OpenConverter/releases)
下载最新版本：

- **arm64-v8a**：`openconverter-v0.3.0-android-arm64-v8a.apk`（~28 MB，现代手机）
- **armeabi-v7a**：`openconverter-v0.3.0-android-armeabi-v7a.apk`（~52 MB，老年机/低端机）
- **x86_64**：`openconverter-v0.3.0-android-x86_64.apk`（~34 MB，模拟器测试用）

下载后在手机上点击 APK 安装（首次需开启"安装未知来源应用"权限）。
**APK 已用 v0.3.0 专用 keystore 签名**（非 debug 签名），手机可能提示"未知发布者"，
点"仍要安装"即可。
**首次运行**：会请求"通知"权限（用于显示转换进度）。

## 从源码构建

```bash
npm install
npm run build:renderer
npm run build:linux    # 在 release/ 生成 4 个 Linux 产物
npm run build:win      # 在 release/ 生成 NSIS + Portable（需要 wine64）
```

### 声明

本项目仅作为音频/文件格式转换的技术工具使用，不涉及任何版权内容的提供或分发。使用者在使用过程中应尊重音乐作品及相关内容的版权与合法权益，不得将本工具用于侵犯任何著作权或相关权利人的行为。因使用本工具产生的任何法律责任或纠纷均由使用者自行承担，与项目作者无关。

## LICENSE

[Apache License 2.0](./LICENSE)
