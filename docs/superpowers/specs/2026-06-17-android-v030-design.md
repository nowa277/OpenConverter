# Android v0.3.0 — FormatDetector 补完 + 真实转码 设计 Spec

**Date:** 2026-06-17
**Status:** Draft (awaiting user review)
**Author:** Brainstorming session
**Target version:** v0.3.0 (Android only; desktop 仍 v0.2.2)
**Branch:** `android-port` (off `main` at v0.2.2, 不合 main)
**Inherits from:** `2026-06-17-android-port-design.md` (v0.2.2 spec)

---

## 1. Goal

v0.2.2 已经把 11 个输入格式的 decoder、5 个输出格式的 UI、SAF picker、
Foreground Service 都实现了；v0.2.2 的 3 个 APK 已经在 GitHub Release 上
可下载。本 spec 在此之上加 3 件事：

1. **FormatDetector 补完**：11 个输入格式全部正确 dispatch
2. **ffmpeg encoder 真实转码**：5 个输出格式（MP3/FLAC/WAV/M4A/OGG）真转
3. **真机测试 + 静态文件列表 UI**：1-2 台 OEM ROM 真机覆盖

v0.3.0 是 v0.2.2 之上的功能追加 release, **Android 先行**, desktop 端
后续单独 bump。

---

## 2. v0.2.2 现状（继承部分）

| 项 | 状态 | 文件 |
|----|------|------|
| NCM / QMC v1 / QMC v2 / KGM / KWM / VPR decoder | ✅ TDD 通过 (synthetic) | `decoders/*.kt` |
| NCM 14/14 字节级 sha256 vs desktop | ✅ | `NcmDecoderTest.kt` |
| SAF picker + 多文件选择 | ✅ | `saf/UriReader.kt` |
| Foreground Service + 通知 | ✅ | `service/ConversionService.kt` |
| 5 个输出格式 chips UI | ✅ | `ui/FileListScreen.kt` |
| 3 APK (arm64-v8a / armeabi-v7a / x86_64) GitHub Release | ✅ | release v0.2.2 |
| ffmpeg decoder-only 自编 NDK .so | ✅ | `scripts/build-ffmpeg.sh` |
| **FormatDetector 11/11 magic** | ❌ 仅 3/11 | `decoders/FormatDetector.kt` |
| **ffmpeg encoder 转码 (5 输出格式)** | ❌ passthrough only | `cpp/ffmpeg_jni.cpp` |
| **libmp3lame 静态链接** | ❌ 未编 | — |
| **静态文件列表 UI** | ❌ 无 | `ui/FileListScreen.kt` |
| **真机测试** | ❌ 仅模拟器 | — |

---

## 3. v0.3.0 目标

### 3.1 Tier 1: FormatDetector 补完

**关键认知**（基于 `src/decoders/*.js` 反推）:

| Format | Magic @ 0 | Bytes | 备注 |
|--------|-----------|-------|------|
| NCM | `CTENFDAM` ASCII | `43 54 45 4E 46 44 41 4D` | ✅ magic 唯一 |
| QMC0 / QMC3 / QMCFLAC / QMCOGG | (none) | — | ❌ **headerless**, 必须 extension hint |
| MFLAC / MFLAC0 / MGG / MGG1 | (none) | — | ❌ **headerless QMCv2**, 必须 extension |
| BKC* (8 变体) | (none) | — | ❌ **headerless**, extension + post-decrypt sniff |
| KGM | `KgmHeader` (16B) | `7C D5 32 EB 86 02 7F 4B A8 AF A6 8E 0F FF 99 14` | magic 跟 KGMA 相同 |
| KGMA | (same as KGM) | `7C D5 32 EB ...` | ❌ **跟 KGM magic 完全一样**, extension 区分 |
| VPR | `VprHeader` (16B) | `05 28 BC 96 E9 E4 5A 43 91 AA BD D0 7A F5 36 31` | ✅ magic 唯一 |
| KWM | `yeelion-kuwo` (10B ASCII) | `79 65 65 6C 69 6F 6E 2D 6B 75 77 6F` | ✅ magic 唯一 |

**结论**：
- 4 个 magic 唯一: NCM, VPR, KWM, KGM/KGMA 共享
- 7 个 headerless: QMC v1 全 4 个, QMC v2 全 5 个
- KGM vs KGMA: **同 magic, 必须 extension 区分**

**FormatDetector 重构策略**:

```kotlin
object FormatDetector {
    /**
     * Detect format from first 16 bytes AND filename hint.
     * @param firstBytes at least 16 bytes (or null → extension-only)
     * @param fileName for extension hint (e.g. "song1.qmcflac", "track.mflac")
     * @return format identifier, or null if undetermined
     */
    fun detect(firstBytes: ByteArray?, fileName: String?): String?
}
```

**Dispatch 优先级** (v0.3.0):

1. **NCM magic** (`CTENFDAM`) → `"ncm"` (绝对可靠)
2. **VPR magic** (`VprHeader`) → `"vpr"` (绝对可靠)
3. **KWM magic** (`yeelion-kuwo`) → `"kwm"` (绝对可靠)
4. **KGM/KGMA magic** (`KgmHeader`) → 用 extension 区分 → `"kgm"` 或 `"kgma"`
5. **Extension hint** (headerless) → 从 fileName 提取后缀
   - `.qmc0` → `"qmc0"`
   - `.qmc3` → `"qmc3"`
   - `.qmcflac` → `"qmcflac"`
   - `.qmcogg` → `"qmcoogg"`
   - `.mflac` / `.mflac0` → `"mflac"` / `"mflac0"`
   - `.mgg` / `.mgg1` → `"mgg"` / `"mgg1"`
   - `.bkc` / `bkcm*` → `"bkc"` / sub-variant
6. **返回 null** → 不支持

**UI 适配**: SAF picker 返回的 `OpenableColumns.DISPLAY_NAME` 提供 fileName,
传入 FormatDetector. Orchestrator 调用处变为:

```kotlin
val sourceFormat = FormatDetector.detect(
    input.copyOfRange(0, minOf(16, input.size)),
    fileName = displayName
) ?: throw IllegalArgumentException("Unknown format")
```

**KGM/KGMA extension 缺失时**: 如果用户上传的 KGM 文件没扩展名, magic 识别
为 KGM family 但无法确定 v0.2.2 vs v0.3.0; 默认按 KGMA 处理
(desktop 的 kgm.js 内部用同一条 cipher, 实际无差别)。

### 3.2 Tier 2: ffmpeg encoder 真实转码

#### 3.2.1 ffmpeg encoder 启用 (configure flags)

```bash
./configure \
    --disable-everything \
    --enable-demuxer=mp3,flac,wav,ogg,m4a,aac,opus \
    --enable-decoder=mp3,flac,vorbis,aac,opus \
    --enable-muxer=mp3,wav,flac,ogg,m4a,ipod \
    --enable-parser=mpegaudio,flac,vorbis,aac \
    --enable-encoder=aac,flac,vorbis,pcm_s16le \
    --enable-libmp3lame \
    --extra-ldflags="-L${LAME_STATIC_DIR} -lmp3lame" \
    --extra-cflags="-I${LAME_INCLUDE_DIR}"
```

| 输出 | encoder | 路径 |
|------|---------|------|
| MP3 | libmp3lame (external) | 自编 NDK static lib |
| FLAC | flac (built-in) | 0 extra dep |
| WAV | pcm_s16le (built-in) | 0 extra dep |
| M4A | aac (built-in) | 0 extra dep |
| OGG | vorbis (built-in since ffmpeg 3.0) | 0 extra dep |

**MP3 必须 external lib** — ffmpeg 没有原生 MP3 encoder。

#### 3.2.2 libmp3lame 3.100 自编

新增 `scripts/build-lame.sh`:

```bash
LAME_VERSION=3.100
# 下载源: https://sourceforge.net/projects/lame/files/lame/3.100/lame-3.100.tar.gz
# Fallback 镜像: https://github.com/RetroSoftwareRepository/mp3lame-3.100-mirror

# 3 ABI NDK 静态编译 → ${BUILD_CACHE}/build-lame-${ABI}/lib/libmp3lame.a
# ffmpeg configure 时 --extra-ldflags 指向这个 .a
```

**风险**:
1. SourceForge 5xx/超时 → 改 GitHub 镜像
2. lame configure 缺 `automake` → 装 `automake libtool`
3. ffmpeg 不认 lame → 用 `--pkg-config` + `PKG_CONFIG_PATH`

**Fallback (3 次失败后)**:
- 切 ffmpeg-kit 6.0 预编译 .so (mp3lame-full-gpl)
- 仅当 self-build 3 次失败才启动, 默认走 self-build
- ⚠️ 违反 v0.2.2 spec §4 constraint #5 (NDK ffmpeg must build from source)
- **user 已明确接受此 fallback 作为最后手段**

#### 3.2.3 JNI transcode 实现

`cpp/ffmpeg_jni.cpp` 改造:

```cpp
// 真正 transcode: avcodec_send_packet/receive_frame 循环
// 1. avformat_open_input(inputBytes) → 解出 PCM
// 2. avcodec_find_encoder(targetCodecId) + avcodec_alloc_context3
// 3. avcodec_open2 + avcodec_send_frame
// 4. avcodec_receive_packet + av_write_frame 到 output
// 5. avio_open_dyn_buf + avio_close_dyn_buf → 返回 ByteArray
```

**Transcode 函数签名**:

```kotlin
// FfmpegBridge.kt
external fun transcode(
    inputBytes: ByteArray,
    inputFormat: String,
    targetFormat: String,
    bitrateKbps: Int
): ByteArray
```

**错误处理**:
- `transcode` 返回空 byte[0] → Orchestrator 抛异常 → FailureLog
- `avcodec_open2` 失败 → 抛到 JNI 层 → Orchestrator 抛
- 任何 ffmpeg 函数失败 → 释放所有 AVCodecContext / AVPacket / AVFrame

### 3.3 Tier 3: 真机测试

#### 3.3.1 测试机

| 设备 | ROM | 用途 |
|------|-----|------|
| 主力 1 (Android 13+) | Pixel 原生 / 类原生 | 标准 Android 行为 |
| 主力 2 (Android 10-12) | MIUI/EMUI/OneUI | OEM 兼容性 |

(具体机型 user 提供)

#### 3.3.2 测试覆盖

- **11 格式** × **5 输出格式** (MP3/FLAC/WAV/M4A/OGG) = 55 个端到端
- 实际跑: 11 格式 × 1 输出格式 (MP3) = 11 个必跑 + 4 个补充
- **真实文件 vs 字节级 sha256** (desktop 端 ffmpeg 同 cmd 跑对照)
- OEM SAF picker 兼容性
- Foreground Service 通知行为
- MIUI 后台杀进程 → 用 `setForeground(true)` 持续

#### 3.3.3 检查清单

`tests/manual/real-device-checklist.md`:

- [ ] 11 格式 × 1 输出 = 11 必跑
- [ ] NCM × 5 输出 = 5 必跑
- [ ] 列表显示: 源格式 chip 全对 (FormatDetector 11/11 验证)
- [ ] OEM SAF picker: 主流 ROM 都能选
- [ ] Foreground 通知: 常驻 + 进度
- [ ] 失败恢复: 杀进程后无 .lock / .tmp 残留
- [ ] APK 体积: 3 ABI 各 < 12MB (vs v0.2.2 3-4MB)

### 3.4 UI 静态文件列表

#### 3.4.1 当前 UI (M3)

```
输出格式: [MP3][FLAC][WAV][M4A][OGG]
未选择文件
[选文件]  [开始转换]  [设置]
```

#### 3.4.2 v0.3.0 UI

```
输出格式: [MP3][FLAC][WAV][M4A][OGG]
已选 3 个文件 (12.4 MB)
┌──────────────────────────────┐
│ 1. song1.ncm       [NCM]    │ ← LazyColumn
│    4.2 MB                    │
├──────────────────────────────┤
│ 2. song2.qmcflac   [QMCFLAC]│
│    3.8 MB                    │
├──────────────────────────────┤
│ 3. song3.kgm       [KGM]     │
│    4.4 MB                    │
└──────────────────────────────┘
[+ 添加文件]  [清空列表]
[开始转换]   [设置]
```

#### 3.4.3 实现要点

- `LazyColumn` 滚动 (10+ 文件不卡)
- 单行: 序号 + 文件名 (截断 24 字符 + ellipsis) + 源格式 `AssistChip` + 大小
- `[+ 添加文件]` 复用 SAF picker
- `[清空列表]` 列表非空时显示
- 源格式 chip: `FormatDetector.detect(bytes, fileName)` (不依赖 filename hint 单用)
- 缺失文件: `ContentResolver.canRead() == false` → 红行 + 灰掉
- **进度条/取消按钮 v0.3.0 不加** (v0.3.1 再说)

#### 3.4.4 FileEntry

```kotlin
data class FileEntry(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val sourceFormat: String?,
    val readable: Boolean
)
```

---

## 4. Architecture

```
┌──────────────────────────────────────────────────────────────┐
│ Android App v0.3.0 (com.openconverter.app)                  │
│                                                              │
│ ┌──────────────┐ ┌──────────────┐ ┌────────────────┐       │
│ │ UI Layer     │◄►│ ViewModel    │◄►│ Orchestrator   │       │
│ │ Compose      │  │ StateFlow    │  │ (pure logic)   │       │
│ │ - FileList   │  │              │  │                │       │
│ │   LazyColumn │  │              │  │                │       │
│ │ - FormatChip │  │              │  │                │       │
│ │ - Convert    │  │              │  │                │       │
│ │   button     │  │              │  │                │       │
│ └──────────────┘ └──────────────┘ └────────────────┘       │
│        │                                       │             │
│        │ SAF picker                            │             │
│        ▼                                       ▼             │
│ ┌──────────────┐                  ┌────────────────┐        │
│ │ Content      │                  │ Decoders       │        │
│ │ Resolver     │                  │ (NCM/QMC/KGM/  │        │
│ │              │                  │  KWM/...)      │        │
│ └──────────────┘                  └────────────────┘        │
│                                              │              │
│                                              ▼              │
│                                  ┌────────────────┐        │
│                                  │ ffmpeg JNI     │        │
│                                  │ (real transcode│        │
│                                  │  aac/flac/     │        │
│                                  │  vorbis/       │        │
│                                  │  pcm_s16le +   │        │
│                                  │  libmp3lame)   │        │
│                                  └────────────────┘        │
│                                                              │
│ Service Layer: ConversionService (Foreground)               │
└──────────────────────────────────────────────────────────────┘
```

**核心变化 (vs v0.2.2)**:
- `FormatDetector.detect()` 加 `fileName` 参数 → headerless 格式用 extension
- `FfmpegBridge.transcode()` 真转 (不再 passthrough)
- ffmpeg 自带 4 encoders + 静态链接 libmp3lame 3.100
- `FileListScreen` 加静态 `LazyColumn` 文件列表

**保留不变**:
- 11 decoder (TDD 通过)
- `ConversionOrchestrator` 接口 (内部接 `FfmpegTranscoder`)
- `ConversionService` Foreground Service 框架
- SAF picker / FailureLog
- 3 APK 命名 (openconverter-v0.3.0-android-<abi>.apk)

---

## 5. Component 分解

### Tier 1: FormatDetector 补完

| 文件 | 状态 | 职责 |
|------|------|------|
| `decoders/FormatDetector.kt` | 改 | 加 KWM/VPR/KGM magic 检测, 重构 detect() 接 fileName |
| `test/.../FormatDetectorTest.kt` | 改 | 加 8 个新 magic + extension 路由测试 |

### Tier 2: ffmpeg encoder + JNI

| 文件 | 状态 | 职责 |
|------|------|------|
| `scripts/build-lame.sh` | 新增 | NDK 编 libmp3lame 3.100 static .a |
| `scripts/build-ffmpeg.sh` | 改 | 启 4 encoders + --enable-libmp3lame |
| `app/src/main/cpp/ffmpeg_jni.cpp` | 改 | 真 transcode (avcodec_send_packet) |
| `app/src/main/cpp/ffmpeg_transcode.c` | 新增 | 纯 C 单元 transcode_main() |
| `ffmpeg/FfmpegBridge.kt` | 改 | 移除 passthrough 注释, 调真 JNI |
| `test/.../FfmpegTranscodeIT.kt` | 新增 | Instrumented: 5 输出格式 E2E |

### Tier 3: 真机测试

| 文件 | 状态 | 职责 |
|------|------|------|
| `tests/manual/real-device-checklist.md` | 新增 | 11×1 + NCM×5 真机清单 |
| `release/openconverter-v0.3.0-*.apk` | 新增 | 3 ABI 产物 |

### UI 改进

| 文件 | 状态 | 职责 |
|------|------|------|
| `ui/FileListScreen.kt` | 改 | LazyColumn 文件列表 |
| `ui/FileListViewModel.kt` | 新增 | StateFlow<List<FileEntry>> |

### Orchestrator 适配

| 文件 | 状态 | 职责 |
|------|------|------|
| `service/ConversionOrchestrator.kt` | 改 | detect() 多传一个 fileName |
| `service/ConversionService.kt` | 改 | 传 SAF picker 的 DISPLAY_NAME |

---

## 6. Data flow

### 一次 NCM→MP3 完整数据流

```
1. SAF picker → URI 列表
        ↓
2. ContentResolver.openInputStream(uri) → InputStream
   + DISPLAY_NAME 列查询 → "song1.ncm"
        ↓
3. ViewModel 转 List<FileEntry>
   - read all bytes
   - bytes.copyOfRange(0, 16) → firstBytes
   - FormatDetector.detect(firstBytes, displayName) → "ncm"
        ↓
4. UI LazyColumn 渲染 10 行 (源格式 chip 正确)
        ↓
5. 用户点 [开始转换] + 选 MP3 chip
        ↓
6. ConversionService.onStartCommand(intent)
        ↓
7. Orchestrator.convertOneInMemory(bytes, "ncm", "mp3", 256)
   ┌────────────────────────────────────┐
   │ FormatDetector.detect(bytes, name) │ → "ncm"
   │        ↓                           │
   │ NcmDecoder.decrypt(bytes)          │ → AudioData(MP3 bytes)
   │        ↓                           │
   │ ffmpeg.probeDuration(mp3, "mp3")   │ → 234.5s
   │        ↓                           │
   │ ffmpeg.transcode(mp3, "mp3", "mp3", 256)
   │   └─ JNI:                          │
   │      avformat_open_input           │
   │      avcodec_find_decoder          │
   │      avcodec_send_packet           │
   │      avcodec_receive_frame → PCM   │
   │      avcodec_find_encoder (libmp3lame)│
   │      avcodec_send_frame            │
   │      avcodec_receive_packet        │
   │      av_write_frame                │
   │      avio_close_dyn_buf → ByteArray│
   │        ↓                           │
   │ return Result(mp3_bytes, "ncm", "mp3", 234.5)
   └────────────────────────────────────┘
        ↓
8. ContentResolver.openOutputStream(targetUri) → 写入
        ↓
9. FailureLog 持久化 (成功/失败)
        ↓
10. Service 通知 Foreground notification "完成 5/10"
```

### 异常路径

| 异常 | 来源 | 处理 |
|------|------|------|
| `FormatDetector.detect` 返回 null | 不支持 | Orchestrator 抛 `IllegalArgumentException` → FailureLog → skip |
| `NcmDecoder.decrypt` 抛异常 | 解密失败 | FailureLog → skip |
| `ffmpeg.probeDuration` < 0 | 解密后不是合法音频 | Orchestrator 抛 → FailureLog |
| `ffmpeg.transcode` 返回空 byte[0] | encoder 失败 | Orchestrator 抛 → FailureLog |
| `openOutputStream` null | SAF 拒绝写 | FailureLog + 用户重选输出路径 |

---

## 7. Testing strategy

### 测试金字塔

```
              ┌──────────────┐
              │ Manual E2E   │ ← 真机 (1-2 台 × 11 格式)
              │  on device   │
           ┌──┴──────────────┴──┐
           │ Instrumented       │ ← 模拟器: 完整 transcode pipeline
           │  (E2E pipeline)    │   NCM → decrypt → ffmpeg → MP3/FLAC/...
        ┌──┴────────────────────┴──┐
        │ JNI C unit tests         │ ← ffmpeg_transcode.c 单测
        │  (transcode_main 函数)   │   PCM→MP3 / PCM→FLAC / PCM→AAC / PCM→OGG
     ┌──┴──────────────────────────┴──┐
     │ Kotlin JUnit                    │ ← 11 decoder + FormatDetector
     │  (algorithm truth)              │   byte-level sha256 vs desktop
  ┌──┴──────────────────────────────────┴──┐
  │  Synthetic test vectors                │
  │  (magic bytes, format dispatch)        │
  └─────────────────────────────────────────┘
```

### 关键测试 (byte-level TDD)

| 测试 | 文件 | 状态 |
|------|------|------|
| NCM 14/14 字节级 sha256 | `NcmDecoderTest.kt` | ✅ v0.2.2 |
| QMC v1 4/4 synthetic TDD | `QmcDecoderTest.kt` | ✅ v0.2.2 |
| QMC v2 MFLAC + MGG TDD | `QmcV2DecoderTest.kt` | ✅ v0.2.2 (synthetic) |
| KGM/KGMA/VPR TDD | `KgmDecoderTest.kt` | ✅ v0.2.2 (synthetic) |
| KWM TDD | `KwmDecoderTest.kt` | ✅ v0.2.2 (synthetic) |
| **FormatDetector 11/11 dispatch** | `FormatDetectorTest.kt` | ❌ v0.3.0 |
| **transcode MP3→MP3** byte-identical (passthrough) | `FfmpegTranscodeIT.kt` | ❌ v0.3.0 |
| **transcode MP3→FLAC** valid FLAC stream | `FfmpegTranscodeIT.kt` | ❌ v0.3.0 |
| **transcode MP3→M4A** valid AAC stream | `FfmpegTranscodeIT.kt` | ❌ v0.3.0 |
| **transcode MP3→OGG** valid OGG/Vorbis | `FfmpegTranscodeIT.kt` | ❌ v0.3.0 |
| **transcode MP3→WAV** valid PCM/WAV | `FfmpegTranscodeIT.kt` | ❌ v0.3.0 |

### transcode 输出验证

不直接比较 sha256 (不同 ffmpeg build LAME header 略不同), 改比较:
- 时长 (允许 ±0.1s)
- 采样率 (严格相等)
- 通道数 (严格相等)
- 输出 stream 头: FLAC `fLaC` / M4A `ftyp` / OGG `OggS` / WAV `RIFF` / MP3 ID3 头

### 运行命令

```bash
# Kotlin JUnit
./gradlew test

# Instrumented (ffmpeg transcode E2E)
./gradlew connectedAndroidTest
```

---

## 8. Distribution

### GitHub Release v0.3.0

- **Tag**: `v0.3.0` 直接打在 `android-port` branch 上
- **Branch 状态**: 不合 main
- **资产 (3 APK)**:
  - `openconverter-v0.3.0-android-arm64-v8a.apk`
  - `openconverter-v0.3.0-android-armeabi-v7a.apk`
  - `openconverter-v0.3.0-android-x86_64.apk`
- **附 `.sha256` 文件**

### Release notes

```markdown
## v0.3.0 — FormatDetector 补完 + 真实转码

- FormatDetector: 11 个输入格式全部支持 dispatch (4 magic 唯一 + 7 headerless extension)
- ffmpeg: 真实 transcoding (MP3/FLAC/WAV/M4A/OGG 互转)
- libmp3lame 3.100 自编 NDK static lib
- UI: 静态文件列表 (LazyColumn + 源格式 chip)
- Real-device testing on 1-2 台 OEM ROM

不发布到 Play Store (仅 GitHub Release)。
Desktop 端仍 v0.2.2, 不与 Android 同步 bump。
```

### README 改动

```diff
 ## Android

 下载: [v0.3.0 Release](https://github.com/.../releases/tag/v0.3.0)

+ v0.3.0 起: 支持 11 个加密格式真实转码 (MP3/FLAC/WAV/M4A/OGG)
+ 选文件时不需要文件名后缀 (FormatDetector 自动识别)
```

---

## 9. 硬约束 (继承 v0.2.2 spec §4 + 调整)

1. ✅ **不修改 desktop 代码** (package.json, src/, scripts/ 都不动)
2. ✅ **不修改 main 分支** (v0.3.0 在 android-port 上 release)
3. ✅ **NDK ffmpeg 自编** (不切 ffmpeg-kit 作为主要路径, **3 次失败 fallback**)
4. ✅ **统一 Android 内部 API level** (minSdk 26 / targetSdk 34)
5. ⚠️ **跨平台版本号 v0.3.0 ≠ v0.2.2** (Android 先行, user 明确接受)

### 与 v0.2.2 spec 的差异

| 项 | v0.2.2 spec | v0.3.0 spec |
|----|-------------|-------------|
| Version | 统一 v0.2.2 | Android v0.3.0, desktop v0.2.2 |
| FormatDetector | 3/11 magic | 11/11 dispatch (magic + extension) |
| ffmpeg | decoder-only | decoder + 4 encoders + libmp3lame |
| UI | 极简 | 加静态文件列表 |
| Release | v0.2.2 tag | v0.3.0 tag (独立) |
| Play Store | 明确不做 | 明确不做 |
| 真实样本覆盖 | 仅模拟器 | 1-2 台真机 |

---

## 10. Out of scope (v0.3.1 / 后续)

| 项 | 推迟到 | 备注 |
|----|--------|------|
| 进度条 (per-file + total) | v0.3.1 | StateFlow + Compose progress bar |
| 取消按钮 | v0.3.1 | Service 双向 IPC + coroutine cancellation |
| 转换历史记录 | v0.3.1 | Room DB schema |
| 主题切换 (深/浅/跟随) | v0.3.1 | Material 3 dynamicColor |
| 文件夹批量选择 | v0.3.1 | DocumentFile tree walker |
| 自动更新 | v0.4.0+ | GMS / 自建 server |
| Play Store | 不做 | user 明确默认不上架 |
| Desktop v0.3.0 同步 bump | 等桌面端 | 独立 PR |
| BKC 8 变体真实文件验证 | v0.3.1+ | 等用户反馈或样本 |
| 16KB page alignment (Android 15+) | v0.3.1+ | 独立 ABI 配置 |
| iOS | 未来 spec | SwiftUI + ffmpeg-kit |

---

## 11. 风险

| 风险 | 严重度 | 应对 |
|------|--------|------|
| SourceForge lame 下载失败 | 中 | 改 GitHub 镜像 |
| lame NDK configure 失败 | 中 | 装 automake/libtool |
| ffmpeg 不识 lame headers | 中 | 用 PKG_CONFIG_PATH |
| ffmpeg 编译 3 次失败 | 高 | fallback ffmpeg-kit 6.0 (mp3lame-full-gpl) |
| KGM vs KGMA 同 magic | 低 | extension 区分 (默认 KGMA) |
| QMC v1 magic `QTag` 实际不属于 v0.2.2 QMC | 中 | v0.3.0 移除 `QTag` magic (误判), 改用 extension |
| MIUI 杀 Service | 中 | setForeground(true) 持续 + START_NOT_STICKY |
| OEM SAF picker URI scheme 差异 | 中 | ContentResolver.canRead() 检查 |
| APK 体积超 12MB | 低 | 接受 12-15MB (encoder .so 增大) |

---

## 12. Self-review (placeholder / 一致性 / 歧义)

- [x] placeholder 扫描: 0 个 TBD / TODO
- [x] 内部一致: FormatDetector 签名在 §3.1, §5, §6 引用一致
- [x] scope 合理: 1 spec → 1 plan
- [x] 歧义检查: "QMC v1 magic" 已修正为 "headerless, extension only"
- [x] KGM vs KGMA 区分策略明确: 同 magic, extension hint
- [x] 失败 fallback 明确: ffmpeg-kit 仅 3 次失败后启动

---

## 13. 验收标准

v0.3.0 release 完成的判定:

1. ✅ FormatDetector 11/11 dispatch 正确 (4 magic 唯一 + 7 extension 路由)
2. ✅ ffmpeg 5 个输出格式 (MP3/FLAC/WAV/M4A/OGG) 真转码, 输出 stream 合法
3. ✅ 1-2 台 OEM ROM 真机通过 11 格式 × MP3 端到端测试
4. ✅ 静态文件列表 UI 渲染正确 (源格式 chip 11/11)
5. ✅ 3 APK 挂在 v0.3.0 GitHub Release, 不合 main
6. ✅ desktop 代码 0 改动 (git diff main -- package.json src/ 为空)
