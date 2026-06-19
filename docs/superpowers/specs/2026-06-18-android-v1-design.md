# OpenConverter Android v1 — Design Spec

**分支**: `android-v1`（从 `main` `fb59ab9` 干净切出，根目录无 `android/`）
**版本号**: `android-v1.0.0`（独立命名空间，不与桌面 `v0.2.x` 混）
**日期**: 2026-06-18
**作者**: nowa277 + Claude（brainstorming 锁定）
**前作归档**: `archive/ui-redesign-v040`（v0.3.x/v0.4.x 烂分支）

---

## 1. 目标

把桌面 Electron 版（v0.2.2）的音频转换功能**完整移植**为 Android app，最终交付可下载安装的 APK。

**功能等价性**：除 QMCv2（mflac/mgg/bkc，需 ekey）约定推迟到 v2 外，桌面已支持的所有格式与功能在 v1 全部到位。

**反目标**（明确不做）：
- ❌ 重写解密算法、自创新算法
- ❌ 自写 native C JNI 调 libavcodec（v0.3.1 的 0 字节 FLAC bug 来源）
- ❌ 上架 Play Store / F-Droid（只发 GitHub Releases）
- ❌ Light theme 切换（桌面 dark-only，Android 跟随）
- ❌ ekey UI 与 QMCv2 解密（架构留位，v2 实现）
- ❌ 依赖外部解密库（解密能力是项目核心，不绑外部维护节奏）

---

## 2. 功能范围（与桌面对齐）

### 2.1 输入格式

| 类型 | 扩展名 | 解密 | v1 支持 |
|---|---|---|---|
| NCM（网易云） | `.ncm` | RC4 + AES-128-ECB | ✅ |
| QMCv1（QQ音乐） | `.qmc0` `.qmc3` `.qmcflac` `.qmcogg` `.qmc1` `.qmc2` `.tkm` | 8×7 状态机 XOR | ✅ |
| KGM/KGMA/VPR（酷狗） | `.kgm` `.kgma` `.vpr` | 17 字节循环 + 查表 mask | ✅ |
| KWM（酷我） | `.kwm` | 32 字节循环 XOR | ✅ |
| 明文 | `.mp3` `.flac` `.wav` `.m4a` `.aac` `.ogg` `.opus` | 不需要 | ✅（直接转码） |
| **QMCv2** | `.mflac` `.mgg` `.bkc*` | 需 ekey | ⏸ **v2** |

### 2.2 输出格式
MP3 / FLAC / WAV / M4A (AAC) / OGG (Vorbis)。bitrate 可选（MP3 默认 320k；FLAC 无损）。

### 2.3 转换流程（与桌面 `convertOne` 同构）
1. 用户在 Android 选输入文件（多选，SAF）+ 输出文件夹（SAF tree）+ 目标格式 + bitrate
2. 点"开始转换" → 启动 Foreground Service
3. 对每个文件：
   - **明文输入**：扩展名 == 目标格式 且 bitrate 默认 → 直接 SAF copy；否则 ffmpeg-kit 转码
   - **加密输入**：扩展名查 decoder 注册表 → Kotlin 解密到中间 bytes → 嗅探格式（ID3/fLaC/OggS/RIFF/ftyp）→ 若已等于目标格式直接落盘；否则 ffmpeg-kit 转码
4. 每文件 percent 进度通过 StateFlow 推到 UI；FGS 通知更新"3/10：晴天.ncm 45%"
5. 取消按钮中止当前文件并跳过剩余

---

## 3. 架构

### 3.1 模块边界

```
android/
├── app/                       # 单 module，不引 Hilt 多模块 Clean Architecture
│   ├── build.gradle.kts       # AGP 8.5.2 + Kotlin 1.9 + minSdk 24 + targetSdk 34
│   └── src/main/
│       ├── kotlin/com/openconverter/app/
│       │   ├── decoders/      # 5 个解密器 + 注册表（核心 IP）
│       │   │   ├── NcmDecoder.kt
│       │   │   ├── QmcDecoder.kt        # 仅 v1 部分；v2 类骨架预留
│       │   │   ├── KgmDecoder.kt
│       │   │   ├── KwmDecoder.kt
│       │   │   └── DecoderRegistry.kt   # 扩展名 → Decoder 查表
│       │   ├── transcode/
│       │   │   └── FfmpegRunner.kt      # FFmpegKit.executeAsync 包装
│       │   ├── conversion/
│       │   │   ├── ConversionEngine.kt  # 纯 Kotlin per-file 循环（JVM 单测）
│       │   │   └── SafIo.kt             # SAF 读字节 / 写字节 facade
│       │   ├── service/
│       │   │   └── ConversionService.kt # FGS，调度 ConversionEngine
│       │   ├── ui/
│       │   │   ├── theme/               # Spotify 配色 + 字体
│       │   │   ├── HomeScreen.kt        # 主屏：文件列表 + 设置入口
│       │   │   ├── SettingsScreen.kt    # 关于 + 版本 + 支持格式
│       │   │   └── components/          # PillButton / GreenCta / FormatChip / FileCard
│       │   └── vm/
│       │       ├── HomeViewModel.kt
│       │       └── ConversionViewModel.kt
│       ├── AndroidManifest.xml
│       └── res/                          # 图标 + theme XML（极少，主要走 Compose）
└── scripts/
    └── build-android.sh                  # 单脚本：clean + assembleRelease + cp 到 release/
```

**单 module 而非多 module**：v0.4.x 教训——这是工具 app，不是 NowInAndroid。一个 module 文件树可读、Gradle 配置一份、新功能少绕弯。

### 3.2 关键接口

```kotlin
// decoders/Decoder.kt
interface Decoder {
    val supportedExtensions: Set<String>
    /** 输入加密 bytes，返回明文 bytes + 嗅探出的格式扩展名（mp3/flac/...）*/
    fun decrypt(input: ByteArray): DecryptResult
}
data class DecryptResult(val audio: ByteArray, val format: String)

// decoders/DecoderRegistry.kt
object DecoderRegistry {
    private val byExt: Map<String, Decoder> = buildMap {
        listOf(NcmDecoder, QmcDecoder, KgmDecoder, KwmDecoder).forEach { d ->
            d.supportedExtensions.forEach { put(it, d) }
        }
    }
    fun pick(filename: String): Decoder? = byExt[filename.substringAfterLast('.', "").lowercase()]
}
// 加新格式：写 NewDecoder.kt，加进 listOf。零主流程改动。

// conversion/ConversionEngine.kt（纯 Kotlin，JVM 可测）
class ConversionEngine(
    private val saf: SafIo,
    private val ffmpeg: FfmpegRunner,
) {
    suspend fun convertAll(
        inputs: List<Uri>,
        targetFormat: String,
        bitrate: String,
        outputFolder: Uri,
        onProgress: (fileIndex: Int, percent: Int) -> Unit,
    ): List<ConversionResult>
}
```

### 3.3 数据流

```
HomeScreen --(Intent)--> ConversionService.startForeground
                              │
                              ▼
                       ConversionEngine.convertAll
                              │
       ┌──────────────────────┼──────────────────────┐
       ▼                      ▼                      ▼
   SafIo.read           DecoderRegistry         FfmpegRunner.execute
  (Uri → bytes)        .pick(name)?.decrypt    ("ffmpeg -i tmp -b:a 320k out")
                              │
                              ▼
                       SafIo.write(outFolder, "<base>.<format>", bytes)
                              │
                              ▼
                       progress StateFlow ──► HomeScreen + Notification
```

---

## 4. 解密器移植策略

### 4.1 1:1 直译规则
- 桌面 `src/decoders/{ncm,qmc,kgm,kwm}.js` → `app/src/main/kotlin/com/openconverter/app/decoders/{Ncm,Qmc,Kgm,Kwm}Decoder.kt`
- 算法常量（`CORE_KEY`, `SEED_MAP`, `VPR_MASK_DIFF`, `MASK_V2_PRE_DEF`, `TABLE1`, `TABLE2`, `ROOT`）逐字节抄过来，**不重排不"优化"**
- 一个 `.js` 函数 → 一个 Kotlin 函数，名字一一对应（`buildRc4Sbox` → `buildRc4Sbox`）
- `Buffer.readUInt32LE(off)` → `ByteBuffer.wrap(...).order(LITTLE_ENDIAN).getInt(off).toLong() and 0xFFFFFFFFL`
- `crypto.createDecipheriv('aes-128-ecb', ...)` → `Cipher.getInstance("AES/ECB/NoPadding")`（PKCS7 unpad 自己写——和桌面一样）

### 4.2 正确性保证：逐字节对拍
- 桌面 `tests/{ncm,qmc,qmc-v2,kgm,kwm}.test.js` 已建立 5 套合成样本 + 真实 NCM 样本（test-ncm/，从 archive 取回）
- Android 端 JVM 单元测试：同样输入 → 同样输出 → SHA-256 必须等于桌面输出 SHA-256
- **CI 行为**：任一解密器测试 SHA-256 不匹配 → 构建失败
- 这是任何外部解密库都给不了的保证

### 4.3 v2 留位
QmcDecoder.kt 写两段：
```kotlin
object QmcDecoder : Decoder {
    override val supportedExtensions = QMC_V1_EXTS  // v1 只覆盖 v1 扩展名
    override fun decrypt(input: ByteArray) = decryptV1(input)

    fun decryptV1(input: ByteArray): DecryptResult { /* 已实现 */ }
    // fun decryptV2(input: ByteArray, ekey: ByteArray): DecryptResult { TODO("v2") }
}
```
v2 实现时只需：在 `supportedExtensions` 加进 v2 扩展名 + 实现 `decryptV2` + 设置页加 ekey 输入。

---

## 5. 转码（ffmpeg-kit）

### 5.1 依赖
```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.arthenica:ffmpeg-kit-full-gpl:6.0-2.LTS")
}
```
**不**自下载 .so，**不**自编 ffmpeg，**不**调 libavcodec C API。

### 5.2 调用模型
```kotlin
class FfmpegRunner {
    suspend fun execute(
        input: File, output: File,
        format: String, bitrate: String,
        onProgress: (percent: Int) -> Unit,
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val cmd = buildCommand(input, output, format, bitrate)  // 同桌面 ffmpeg.js 的 args
        val session = FFmpegKit.executeAsync(cmd) { sess ->
            cont.resume(if (sess.returnCode.isValueSuccess) Result.success(Unit)
                        else Result.failure(RuntimeException(sess.allLogsAsString)))
        }
        FFmpegKitConfig.enableStatisticsCallback { stats ->
            val pct = (stats.time * 100 / totalDurationMs).toInt()
            onProgress(pct)
        }
        cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }
}
```
**与桌面 `src/main/ffmpeg.js` 同构**：同样的 args 格式，同样的进度解析（time → percent），同样的 quality 参数（`-b:a 320k` 等）。

### 5.3 SAF 与临时文件
ffmpeg-kit 走文件路径，不能直接读 SAF Uri。每个文件的转码步骤：
1. 通过 SAF 读输入 Uri 字节 → 写到 `cacheDir/in_<rand>.<ext>`
2. ffmpeg-kit 读 `cacheDir/in_*` → 写到 `cacheDir/out_<rand>.<format>`
3. 读 `cacheDir/out_*` → 通过 SAF `DocumentsContract.createDocument(treeUri, mime, "<base>.<format>")` 写到用户选的输出文件夹
4. `try/finally` 删 in/out 两个临时文件；Service 启动时 `cacheDir.listFiles()` 清扫上次残留

加密输入的"先 Kotlin 解密到 bytes"步骤把 (1) 替换成"读 bytes → DecoderRegistry.decrypt → 中间 bytes 写到 cacheDir/in_*"；如果嗅探出的格式已等于目标格式且 bitrate 默认，则跳过 (2)，直接进 (3)。

```kotlin
class SafIo(private val context: Context) {
    fun readBytes(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    fun writeBytes(folderTreeUri: Uri, displayName: String, mime: String, bytes: ByteArray): Uri {
        val docUri = DocumentsContract.createDocument(context.contentResolver, folderTreeUri, mime, displayName)!!
        context.contentResolver.openOutputStream(docUri)!!.use { it.write(bytes) }
        return docUri
    }
    fun queryDisplayName(uri: Uri): String { /* OpenableColumns.DISPLAY_NAME */ }
}
```

---

## 6. UI（Spotify 深色）

### 6.1 主题钉死
参考 `~/obsidian/AI/design/awesome-design-md/design-md/spotify/DESIGN.md`。Compose Material 3 主题：

```kotlin
private val SpotifyDark = darkColorScheme(
    background      = Color(0xFF121212),  // 主背景
    surface         = Color(0xFF181818),  // 卡片
    surfaceVariant  = Color(0xFF1F1F1F),  // 按钮 / 输入
    primary         = Color(0xFF1ED760),  // CTA + 进度 + active（唯一 accent，不装饰）
    onPrimary       = Color(0xFF000000),
    onBackground    = Color(0xFFFFFFFF),
    onSurface       = Color(0xFFFFFFFF),
    onSurfaceVariant= Color(0xFFB3B3B3),  // 次级文本
    error           = Color(0xFFF3727F),
    outline         = Color(0xFF7C7C7C),
)
```
- **字体**：Roboto（系统默认；CircularSp 商用专字 Android 不能引）+ FontWeight Bold/Normal 二元节奏
- **圆角**：pill 按钮 9999.dp，圆形 50%，卡片 6-8.dp
- **阴影**：卡片 elevation 8.dp（Compose 自动深色阴影），dialog 24.dp
- **Light theme 不开**：`MaterialTheme(colorScheme = SpotifyDark)` 无条件

### 6.2 屏幕

**HomeScreen**（主屏）
```
┌──────────────────────────────────────────┐
│  OpenConverter             ⚙ (圆形 icon) │  ← TopAppBar
├──────────────────────────────────────────┤
│  ┌─[+ 添加文件]──┐  ┌─[ 选择输出文件夹 ]┐│
│  │ pill outlined │  │  pill outlined    ││  ← 输入控件区
│  └────────────── ┘  └───────────────────┘│
│                                          │
│  目标格式: [MP3] FLAC WAV M4A OGG        │  ← FilterChip 行（选中 #1ED760 描边）
│  比特率:   128k 192k [320k] 无损         │
│                                          │
│  ┌────── 文件列表 LazyColumn ──────┐     │
│  │ ▶ 晴天.ncm    待转换            │     │
│  │ ▶ 安和桥.qmcflac  转换中 65%   │     │  ← 卡片 #181818
│  │ ▶ 阴天.kgm    完成 ✓            │     │
│  │ ▶ 七里香.kwm  失败 ✗            │     │
│  └─────────────────────────────────┘     │
│                                          │
│  ┌────── 开始转换 (满宽 pill) ──────┐    │  ← 唯一绿底 #1ED760
│  └─────────────────────────────────┘     │
└──────────────────────────────────────────┘
```

**SettingsScreen**：返回按钮 + 关于（版本号、GitHub 链接、MIT license）+ 已支持格式列表。**v1 不放 ekey 输入**。

### 6.3 通知（FGS 必需）
```
[OpenConverter]  转换中 3/10: 晴天.ncm  ━━━━━━━━─── 45%
                 [取消]
```
点击通知：回到 HomeScreen。点击"取消"：发广播 → ConversionService 调 `FFmpegKit.cancel()` + 中止剩余文件循环。

---

## 7. Foreground Service

### 7.1 类型 & 权限
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- API 33+ 运行时申请 -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service android:name=".service.ConversionService"
         android:foregroundServiceType="specialUse"
         android:exported="false">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="audio_format_conversion" />
</service>
```
**为何 specialUse 而非 mediaProcessing**：compileSdk 34 还没 mediaProcessing flag——参考 memory `android-sdk34-fgs-workaround.md`，v0.3.x 已踩过的坑。

### 7.2 生命周期
1. `HomeViewModel.startConversion()` → `context.startForegroundService(intent)`
2. `ConversionService.onStartCommand()`：立刻 `startForeground(NOTIF_ID, buildNotification(...))`（< 5 秒强制要求）
3. 启 `CoroutineScope(Dispatchers.IO + SupervisorJob())`，跑 `ConversionEngine.convertAll`
4. 每文件进度更新 → 重发通知（同 NOTIF_ID 实现 in-place 更新）+ `_progress.value = ...` 驱动 UI
5. 全部完成 / 取消 → `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()`

### 7.3 进度通信
ViewModel 持有 service 的 binder（`ServiceConnection`）→ 暴露 `progress: StateFlow<ConversionState>`，HomeScreen `collectAsState()`。绑定 unbind 不停 service（FGS 自治）。

---

## 8. ABI 与构建

### 8.1 splits
```kotlin
android {
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")     // 不打 armeabi-v7a / x86
            isUniversalApk = false
        }
    }
}
```
出两个 APK：`openconverter-android-v1.0.0-arm64-v8a.apk` + `openconverter-android-v1.0.0-x86_64.apk`。

### 8.2 签名
v0.3.x 已有专用 keystore（android 命名空间 keystore，与桌面无关）。v1 沿用，CI 从环境变量读密码。

### 8.3 构建脚本
```bash
# scripts/build-android.sh —— 单脚本，无副作用
cd android
./gradlew clean :app:assembleRelease
mkdir -p ../release
cp app/build/outputs/apk/release/*.apk ../release/
( cd ../release && for f in *.apk; do sha256sum "$f" > "$f.sha256"; done )
```

### 8.4 发布红线
- ❌ **不**自动 `gh release create` —— 用户原话："你每次都直接发布,我们之前的linux/windows都被刷下去了"
- ❌ **不**打桌面 `v0.2.x` 命名空间 tag
- ✅ Android tag 用 `android-vX.Y.Z`（如 `android-v1.0.0`），独立空间
- ✅ 用户手动确认 release，构建产物只放本地 `release/`

---

## 9. 测试策略

| 层级 | 工具 | 谁跑 | 何时 |
|---|---|---|---|
| 解密算法正确性 | JUnit + 桌面对拍样本（SHA-256） | Claude 本地 | 每次改解密 |
| 转换循环逻辑 | JUnit + Fake FfmpegRunner / Fake SafIo | Claude 本地 | 每次改 ConversionEngine |
| UI 逻辑 | Compose UI test（Robolectric） | Claude 本地 | 每次改屏幕 |
| 真机端到端 | MCP `mcp-android-emulator` 驱动 vivo Y78 | Claude（用户插线即可） | 关键节点（每个 phase 收尾） |
| 用户手动验证 | Android Studio Run + 自选样本 | 用户 | release 前 |

**纪律**：每个解密器至少 3 个 SHA-256 对拍 case；ConversionEngine 至少覆盖"明文同格式直拷 / 明文转码 / 解密后等于目标格式直落 / 解密后再转码 / 失败跳过继续 / 取消中止" 6 条路径。**不**为每个微功能配 5 个测试（v0.4.x 教训）。

---

## 10. 风险与应对

| 风险 | 应对 |
|---|---|
| 解密器翻译偏移 | SHA-256 逐字节对拍，CI 强制 |
| ffmpeg-kit 6.0 下载源（arthenica 已归档） | 用 Appodeal mirror（v0.3.x 已验证）；SHA256 pin 在 build.gradle |
| SDK 34 FGS 类型 | specialUse + PROPERTY_SPECIAL_USE_FGS_SUBTYPE（memory 已记） |
| vivo Y78 闪退（v0.3.x 遗留） | 第一阶段就用 MCP 抓 logcat，不再凭猜 |
| APK 体积（30-55MB/ABI） | 用户已接受过；ProGuard/R8 默认开 minify |
| 临时文件清理 | try/finally + cacheDir，每次启动清一遍 |
| SAF 大文件 OOM | 当前先全内存读写（与桌面同构）；> 100MB 文件留 v1.1 改流式 |

---

## 11. 交付定义（验收清单）

v1.0.0 视为交付当且仅当：

1. ✅ 两个 APK 产物在 `release/`：`openconverter-android-v1.0.0-arm64-v8a.apk` + `…-x86_64.apk`，sha256 文件齐全
2. ✅ vivo Y78（arm64）真机：装 → 启动 → 不闪退 → 完成一次 NCM → MP3 转换并能用系统播放器播放
3. ✅ AVD x86_64：同上一次冒烟
4. ✅ 5 个解密器的 SHA-256 对拍单元测试全绿（CI 通过）
5. ✅ ConversionEngine 6 条路径单测全绿
6. ✅ 5 种目标格式（MP3/FLAC/WAV/M4A/OGG）每种至少跑通过一次真机
7. ✅ 切后台 / 锁屏 / 解锁后转换继续，最终通知显示完成
8. ✅ 转换中点取消，停在当前文件不留残骸
9. ✅ README 更新 Android 章节（怎么下载、装哪个 ABI、怎么用）

**不在交付范围**：QMCv2、ekey UI、armeabi-v7a APK、Play Store 上架、流式大文件优化。

---

## 12. 实施顺序（粗骨架，正式 plan 由 writing-plans 产出）

7 个里程碑，每个里程碑结束都能跑、能验、能 commit：

1. **Skeleton**：android/ 目录、build.gradle、空 MainActivity（Spotify 主题）、模拟器跑得起来
2. **Decoders**：5 个 Kotlin 解密器 + 注册表 + SHA-256 对拍测试
3. **FfmpegRunner**：ffmpeg-kit 接入 + 简单 demo（hard-coded 文件路径）跑通
4. **ConversionEngine + SafIo**：纯 Kotlin 循环 + JVM 单测 6 条路径
5. **HomeScreen UI**：文件选择 / 输出文件夹 / 格式 chip / 列表 / CTA
6. **ConversionService（FGS）**：整合 1-5，绑定 + 进度 + 取消 + 通知
7. **Polish + 真机验收**：SettingsScreen、错误吐司、签名 release 构建、按 §11 清单逐项验

每里程碑一个或多个 commit（带 `Co-Authored-By: Claude`），遇到 3 次连续失败暂停汇报用户。
