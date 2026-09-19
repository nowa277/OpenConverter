<div align="center">

# OpenConverter

### 跨平台轻量级音频格式转换与本地解码工具链

[English](./README_EN.md) | 简体中文

<p align="center">
面向音频工作流的轻量级格式转换与解密工具。<br/>
<b>桌面端</b>基于 JavaScript 解码管线 + Electron + FFmpeg 转码后端构建；<br/>
<b>Android 端</b>基于 Jetpack Compose + 纯 Kotlin 解码器 + FFmpegKit 架构实现。
</p>

[![Linux](https://img.shields.io/badge/Linux-FCC624?style=for-the-badge&logo=linux&logoColor=black)](#desktop-安装)
[![Windows](https://img.shields.io/badge/Windows-0078D4?style=for-the-badge&logo=windows11&logoColor=white)](#desktop-安装)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=black)](#android-安装)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-D22128?style=for-the-badge)](LICENSE)

</div>

---

## 项目介绍

* **隐私至上，完全离线**：所有的解密、转码与处理均完全在本地设备上运行。不上传任何音频数据，零网络交互，安全可靠。
* **真实音频转码 (FFmpeg)**：并非简单重命名或提取，内置 FFmpeg / FFmpegKit 转码后端，支持转码为 MP3 / FLAC / WAV / M4A / OGG，并可根据需要自由选择输出码率（如 320k, 256k 等）。

### v1.4.1 最新更新（Android 端）：
* **大文件不再内存溢出**：`.kgm` / `.kgma` / `.vpr` 改为分块流式解密，35MB+ 歌曲不再整文件进堆。
* **新版酷狗密钥扫描**：除 `kugou_music_v2.db` 外，同时检索 `mggkey*`、`KGMusicV3.db`，覆盖官方包 / 极速版 / HiFi，并限制单文件读取上限以免二次 OOM。

### v1.4.0 更新（Android 端）：
* **原生 Root 免 Shizuku 一键同步**：新增直接 Root 提取链，自适应 `su 0`、`su -c`、KernelSU、APatch 及 toybox，已 Root 手机无需配置 Shizuku 即可一键拉取酷狗 MMKV/SQLite 解密密钥。
* **公共存储密钥自动扫描**：新增 `PublicStorageKeyScanner`，自动检索 `/sdcard/Download`、`/sdcard/Music`、`/sdcard/kgmusic` 等公共目录下的 `kgg.key`、`mggkey*` 及数据库文件并自动提取合并。
* **转换引擎缺失密钥即时自愈 (Auto-Healing)**：若遇到未提前索引密钥的音频，转换引擎即时触发全源深度扫描并重试解密，大幅提升批量转换成功率。
* **SAF 选歌路径记忆**：适配 Android DocumentsContract `EXTRA_INITIAL_URI`，选择音频时自动优先定位到上次选歌目录或输出目录，无需重复逐层寻找。
* **设置与权限持久化**：输出文件夹 SAF 写入权限（`takePersistableUriPermission` 校验维护）、目标音频格式与比特率持久化保存，应用重启不重置。
* **设置界面重构**：优化酷狗密钥状态看板，直观展示就绪状态（Direct Root / Ready）与已同步密钥总量。

### v0.3.7 更新（桌面端 / CLI）：
* **KGM 流式解密**：桌面与 CLI 按 64KiB 分块读写 `.kgm` / `.kgma` / `.vpr`，大文件不再整文件读入内存。

### v0.3.6 更新（桌面端）：
* **更流畅的界面**：队列改为增量渲染（进度条真正平滑过渡），新增页面切换动画、解密阶段闪烁进度、整体进度条、可堆叠的提示气泡、全窗口拖放遮罩；支持 **跟随系统主题**、"减弱动画" 与 "完成后自动清理队列"。
* **更可控的转换**：转换过程中可 **一键取消**、单文件移除、完成后 **在文件夹中显示**；不再对同格式文件做无意义的有损重编码（mp3→mp3 直接拷贝）；OGG/Opus 320k 会自动限制在 libopus 上限 256k（修复此前必然失败的问题）。
* **元数据与封面保留**：NCM 内嵌的歌名 / 歌手 / 专辑 / 封面会写入输出文件（含 FLAC / MP3 / M4A），明文音频转码时也会保留原有封面。
* **性能**：解密在独立 worker 线程中运行，主进程与界面在处理大文件时不再卡顿；ffmpeg 进度改用机器可读的 `-progress` 输出。
* **修复 QMCv2 解析**：STag 头部文件此前被错误切片导致解密失败；QTag 长度字节序、ekey 字段索引、Base64 校验均已修正，并补齐 100+ 单元 / 集成测试与 GitHub Actions CI。

---

## 界面预览

### 桌面端应用界面
<p align="center">
  <img src="assets/screenshots/new_linux_app.png" alt="Desktop App UI" width="85%" />
</p>

### Android 移动端应用界面
<p align="center">
  <img src="assets/screenshots/android_success_queue.png" alt="Android Queue" width="40%" />
  &nbsp;&nbsp;
  <img src="assets/screenshots/android_history_screen.png" alt="Android History" width="40%" />
  &nbsp;&nbsp;
</p>

---

## 支持的加密格式

| 格式扩展名 | 对应来源平台 |
|:---|:---|
| `.ncm` | 网易云音乐 |
| `.kwm` | 酷我音乐 |
| `.kgm` / `.kgma` / `.vpr` 等 | 酷狗音乐 |
| `.kgg` / `.kgg.flac` | 酷狗音乐（v5：桌面端支持全盘自动扫描/导入；Android 端支持原生 Root 同步、公共目录扫描及在设置中导入 `kgg.key` / `KGMusicV3.db`） |
| `.mgg` / `.mgg1` / `.bkc` 等 | QQ音乐 |
| `.mp3` / `.flac` / `.wav` 等明文音频 | 任何平台 |

---

## 安装指南

### Desktop 安装

从 [Releases 页面](https://github.com/nowa277/OpenConverter/releases) 下载对应系统的最新安装包：

#### Debian / Ubuntu
```bash
# AppImage 安装与运行（推荐）
chmod +x openconverter-v***-linux-x64.AppImage
./openconverter-v***-linux-x64.AppImage

# Deb包安装 (注意：OpenConverter 在 Linux 下需要系统 PATH 存在 ffmpeg)
sudo apt install ffmpeg
sudo apt install ./openconverter-v***-linux-amd64.deb
```

#### Windows
* **便携版（推荐）**：`openconverter-v***-windows-x64-portable.exe`
  双击直接运行，可随身携带。
* **NSIS 安装包**：`openconverter-v***-windows-x64-setup.exe`
  双击根据向导安装。
* *提示：Windows 端已内置 `ffmpeg.exe` 与 `ffprobe.exe`，无需手动安装 FFmpeg。首次启动若弹出 Windows Defender 未签名提示，点击“更多信息” -> “仍要运行”即可。*

---

### Android 安装

从 [Releases 页面](https://github.com/nowa277/OpenConverter/releases) 下载最新的 APK 文件安装：

* **arm64-v8a**：`openconverter-v1.4.1-android-arm64-v8a.apk` (推荐，适合绝大多数现代智能手机)
* **x86_64**：`openconverter-v1.4.1-android-x86_64.apk` (适合在 Android 模拟器上运行与调试)

Android 端 KGG v5 解密依赖对应歌曲的逐曲密钥。在 **v1.4.1** 中，应用支持多种获取途径：
1. **已 Root 手机**：进入设置点击“立即同步”，通过原生 Root（KernelSU/APatch/Magisk）全自动拉取本地酷狗 MMKV/SQLite 密钥；
2. **未 Root 手机**：支持公共存储自动扫描，将备份或导出的 `kgg.key`、`mggkey*` 或 `KGMusicV3.db` 放入 `/sdcard/Download` 或 `/sdcard/Music`，App 将自动发现并解析；也可在设置页通过 SAF 选择器手动导入。密钥仅在应用私有空间保存，绝不上载。

### 自动化解密设置引导与成功演示
支持酷狗与QQ音乐格式密钥的一键自动获取。在“Settings”页面中可自动扫描内存获取QQ音乐Cookie并拉取解密所需信息，酷狗音乐同样支持一键开启全盘自动扫描密钥功能：
<p align="center">
  <img src="assets/screenshots/example.png" alt="Auto Key Fetch Guide" width="85%" />
</p>

设置成功后，即可直接拖拽加密文件，实现全自动批量解密并转换为常规格式：
<p align="center">
  <img src="assets/screenshots/history_presentation.png" alt="Successful Conversion History" width="85%" />
</p>

> [!IMPORTANT]
> **Android 端 KGG 解密策略指南：**
> 1. **已 Root 设备**：设置页一键原生 Root 同步，即刻解密所有本地 KGG 歌曲。
> 2. **未 Root 设备**：可将 PC 端酷狗数据库 `KGMusicV3.db`（Windows 路径通常位于 `C:\Users\Public\KuGou\KGMusic\KGMusicV3.db`）或包含 `id,key` 的 `kgg.key` 文本放至手机“下载”或“音乐”目录，打开 App 即可自动扫描收录，亦可在设置页点击“导入数据库/密钥文件”手动选中。
> 3. **降级方案（可选）**：将手机酷狗音乐降级至早期版本，下载的文件格式为 `.kgm` / `.kgma`，此类格式完全免 Root、免导入，可在 OpenConverter 中直接批量转换。

| 格式后缀 | 对应版本及音质 | 手机端解密是否需要密钥数据库？ | 结论与使用建议 |
| :--- | :--- | :--- | :--- |
| **`.kgm`** | 早期格式，标准或高品质 MP3 | ❌ 不需要 (免 Root / 免导入) | 完全可解。可直接在手机版 OpenConverter 中进行一键转换。 |
| **`.kgma`** | 早期格式，超高品质或无损 FLAC | ❌ 不需要 (免 Root / 免导入) | 完全可解。可直接在手机版 OpenConverter 中进行一键转换。 |
| **`.vpr`** | 酷狗彩铃格式 | ❌ 不需要 (免 Root / 免导入) | 完全可解。可直接在手机版 OpenConverter 中进行一键转换。 |
| **`.kgg`** | 现代格式，酷狗专属加密 | ⚠️ 需要逐曲密钥 | 已支持。Root 设备一键直接提取；未 Root 设备可放 `kgg.key` / `KGMusicV3.db` 于公共目录或设置中导入。 |
| **`.kgg.flac`** | 现代格式，酷狗无损加密 | ⚠️ 需要逐曲密钥 | 已支持。Root 设备一键直接提取；未 Root 设备可放 `kgg.key` / `KGMusicV3.db` 于公共目录或设置中导入。 |


  ---

## 源码编译开发

如果你需要从源码构建本项目，请确保您的计算机上配置了 Node.js 18+ 与 Android SDK（如果编译 Android 版本）。

### 编译桌面端 (Electron)
```bash
# 安装依赖
npm install

# 编译前端静态资源
npm run build:renderer

# 编译 Linux 软件包 (AppImage/Deb)
npm run build:linux

# 编译 Windows 软件包 (需要 wine64 环境)
npm run build:win
```

### 编译 Android 端
```bash
cd android

# 运行本地单元测试
./gradlew testDebugUnitTest connectedDebugAndroidTest

# 编译并打包 Debug/Release APK
./gradlew :app:assembleRelease
```

真实 KGG 样本不会进入仓库。维护者可在已启动的 Android 设备或模拟器上运行环境门控的逐字节等价验证：

```bash
KGG_FIXTURE_DIR=/local/kgg \
KGG_KEY_SOURCE=/local/KGMusicV3.db \
KGG_EXPECTED_DIR=/local/reference \
android/scripts/verify-kgg-v5.sh
```

`reference` 必须是同一批输入经独立参考实现解密得到的明文目录，保持相同相对路径并使用真实容器扩展名。脚本只暂存本地材料用于测试，结束后自动清理设备和主机临时文件。

---

## 免责声明

本项目仅作为个人音频学习、文件格式整理及兼容性研究的技术工具使用，不涉及任何版权音频内容的提供、分发或存储。使用者在使用过程中应严格遵守相关法律法规，尊重音乐作品著作权人的合法权益，不得将本工具用于任何侵犯著作权的行为。由于使用本工具引发的任何法律争议或纠纷，均由使用者自行承担，与本项目作者及贡献者无关。

---

## 开源许可证

本项目基于 [Apache License 2.0](./LICENSE) 协议开源。
