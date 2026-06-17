# Android Port — Design Spec

**Date:** 2026-06-17
**Status:** Draft (awaiting user review)
**Author:** Brainstorming session
**Target version:** v0.2.2 (unified with desktop)
**Branch:** `android-port` (off `main` at v0.2.2)

## 1. Goal

Ship a working Android app for OpenConverter that achieves **functional
parity** with the desktop v0.2.2 core capability: convert 11
encrypted/plaintext audio formats to MP3 / FLAC / WAV / M4A / OGG. The
Android build is **not** an Electron port (Electron does not exist on
Android); it is a native rewrite in **Kotlin + Jetpack Compose** that
shares the **algorithm truth** (via test vectors) and the **functional
contract** (same 11 inputs → same 5 outputs) with the desktop client.

## 2. Scope (in v0.2.2 Android)

- **11 input formats**: NCM, QMC0/QMC3, QMCFLAC, QMCOGG, KWM, KGM/KGMA,
  VPR, MFLAC/MFLAC0, MGG/MGG1, BKC*
- **5 output formats**: MP3, FLAC, WAV, M4A, OGG (matching desktop)
- **ekey setup**: required for QMCv2 (MFLAC / MGG / BKC*); persisted
  via SharedPreferences
- **Single + batch file conversion**: user picks multiple files, serial
  conversion
- **Progress notification**: Foreground Service + notification bar
  with current file + total progress
- **FFmpeg**: self-built NDK .so (libmp3lame + libvorbis + libfdk-aac
  + libflac only; no video codecs)
- **Testing**: JVM JUnit unit tests (algorithm byte-level) + emulator
  instrumented tests (SAF / Service / JNI)
- **Distribution**: 3 APKs (arm64-v8a / armeabi-v7a / x86_64) on
  GitHub Releases **v0.2.2** (existing tag, no new version)

## 3. Out of scope (v0.2.2 Android)

- **Version bump** — Android ships as v0.2.2, same as desktop. No
  v0.3.x / v0.4.0 tag.
- Play Store distribution (compliance work deferred to a future spec)
- Auto-update (`in-app update` not implemented)
- iOS (future spec)
- CI-hosted emulator (v0.2.2 tests locally; CI runner deferred)
- Material 3 dynamic color (static dark theme)
- ABI optimization beyond standard splits (no 16KB page alignment
  separate build)
- User accounts, cloud sync
- Video formats
- Desktop v0.2.2's "CLI mode" (no command-line on mobile)

## 4. Constraints

1. **Unified version v0.2.2.** All platforms (Linux / Windows /
   Android) share the same v0.2.2 release tag. Android artifact
   filenames: `openconverter-v0.2.2-android-<abi>.apk`. A new platform
   is a reason for a new **release**, not a new **version**.
2. **Do not modify desktop code.** `src/renderer/`, `src/main/`,
   `src/decoders/`, `src/preload/` are **read-only** on the
   `android-port` branch. Kotlin port of decoders lives at
   `android/app/src/main/kotlin/com/openconverter/decoders/`, physically
   separate from `src/decoders/*.js`.
3. **Do not modify `package.json`.** `android-port` branch leaves
   `package.json` untouched. The Android project uses its own Gradle
   toolchain (`android/build.gradle.kts`), fully isolated from npm /
   Electron.
4. **Algorithm truth = test vectors.** The 14 NCM samples in
   `tests/ncm_format/` (gitignored, supplied locally) are copied to
   `android/app/src/test/resources/test-ncm/`. Kotlin JUnit runs the
   same samples and asserts byte-level sha256 matches the desktop
   reference. **Any algorithm change must pass tests on both ends
   before merge.**
5. **NDK ffmpeg must build from source.** No prebuilt binaries. Build
   script committed to `android/scripts/build-ffmpeg.sh`. CI-reproducible.
6. **Runs on Linux.** `./gradlew assembleDebug` + `./gradlew test` +
   emulator instrumented test — full dev loop on Ubuntu 22.04+.
7. **Multi-platform principles (inherited from windows spec §11).**
   `android-port` branch does not modify `package.json` `linux` /
   `win` / `nsis` blocks; desktop remains buildable.

## 5. Output artifacts

| Artifact                                          | Format | Size estimate | Target user                |
| ------------------------------------------------- | ------ | ------------- | -------------------------- |
| `openconverter-v0.2.2-android-arm64-v8a.apk`     | APK    | ~10 MB        | Real devices (90%+ of 2026 active fleet) |
| `openconverter-v0.2.2-android-armeabi-v7a.apk`   | APK    | ~9 MB         | Older / low-end phones     |
| `openconverter-v0.2.2-android-x86_64.apk`        | APK    | ~10 MB        | Emulator testing           |

**Naming convention** (inherited from desktop): `openconverter-v<version>-<platform>-<arch>[-<variant>].<ext>`.
**No universal APK** — splits configured in Gradle (`splits.abi`,
`isUniversalApk = false`).

## 6. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ Android App  (com.openconverter.app,  v0.2.2)              │
│                                                              │
│  ┌──────────────────┐   ┌─────────────────┐                │
│  │  UI Layer        │◄─►│  ViewModel      │                │
│  │  (Jetpack        │   │  (StateFlow +   │                │
│  │   Compose)       │   │   Coroutines)   │                │
│  └──────────────────┘   └────────┬────────┘                │
│                                  │                          │
│         ┌────────────────────────┼──────────────────────┐  │
│         ▼                        ▼                      ▼  │
│  ┌──────────────┐   ┌──────────────────┐   ┌──────────────┐ │
│  │  SAF         │   │  Conversion      │   │  ffmpeg      │ │
│  │  Adapter     │   │  Service         │   │  JNI Bridge  │ │
│  │  (Content-   │   │  (Foreground,    │   │  (Kotlin ↔ C)│ │
│  │   Resolver)  │   │   Notification)  │   └──────┬───────┘ │
│  └──────────────┘   └────────┬─────────┘          │        │
│                              │                    ▼        │
│                              │           ┌──────────────────┐
│                              │           │  libffmpeg.so    │
│                              ▼           │  (NDK build,     │
│                    ┌──────────────────┐   │   audio codecs   │
│                    │  Decoder         │   │   only)          │
│                    │  (Pure Kotlin    │   └──────────────────┘
│                    │   port of        │
│                    │   src/decoders/) │
│                    └──────────────────┘
└─────────────────────────────────────────────────────────────┘
```

## 7. Components

| Component              | Path                                                                                | Responsibility                       | Dependencies            |
| ---------------------- | ----------------------------------------------------------------------------------- | ------------------------------------ | ----------------------- |
| **UI Layer**           | `android/app/src/main/kotlin/com/openconverter/ui/`                                | Compose screens (FileList / ConversionList / Settings / EkeySetup) | ViewModel    |
| **ViewModel**          | `android/app/src/main/kotlin/com/openconverter/ui/vm/`                             | UI state (StateFlow), user actions, bridge to Service | UI / Service             |
| **SAF Adapter**        | `android/app/src/main/kotlin/com/openconverter/saf/`                                | Wrap `Intent.ACTION_OPEN_DOCUMENT` + `ContentResolver` | Android SDK |
| **Conversion Service** | `android/app/src/main/kotlin/com/openconverter/service/`                           | Foreground Service, serial Uri processing, progress notification | SAF / Decoder / JNI / NotificationManager |
| **Decoder**            | `android/app/src/main/kotlin/com/openconverter/decoders/`                          | 11 formats, **pure Kotlin, no Android deps** | Kotlin stdlib + `java.security` |
| **ffmpeg JNI Bridge**  | `android/app/src/main/kotlin/com/openconverter/ffmpeg/`                            | Kotlin wrapper around native `libffmpeg.so` | libffmpeg.so |
| **libffmpeg.so**       | `android/app/src/main/jniLibs/<abi>/`                                              | NDK build artifact, audio codecs only | NDK r25c+, Linux toolchain |
| **Test Resources**     | `android/app/src/test/resources/test-ncm/`                                         | 14 NCM samples (copied from `tests/ncm_format/`) | (none) |

## 8. Communication interfaces

### 8.1 UI → Service (start conversion)

```kotlin
class ConversionViewModel : ViewModel() {
  fun startConversion(uris: List<Uri>, targetFormat: String) {
    val intent = Intent(context, ConversionService::class.java).apply {
      putExtra(ConversionService.EXTRA_URIS, uris.map { it.toString() }.toTypedArray())
      putExtra(ConversionService.EXTRA_TARGET_FORMAT, targetFormat)
    }
    ContextCompat.startForegroundService(context, intent)
  }
}
```

### 8.2 Service → UI (progress reporting)

```kotlin
class ConversionService : Service() {
  private val _progress = MutableStateFlow<Progress>(Progress.Idle)
  val progress: StateFlow<Progress> = _progress.asStateFlow()

  override fun onStartCommand(intent: Intent?): Int {
    val uris = intent!!.getStringArrayExtra(EXTRA_URIS)!!.map(Uri::parse)
    val format = intent.getStringExtra(EXTRA_TARGET_FORMAT)!!
    startForeground(NOTIFICATION_ID, buildNotification("准备中...", 0, uris.size))
    scope.launch { convertAll(uris, format) }
    return START_NOT_STICKY  // process killed → no auto-retry
  }
}
```

### 8.3 Kotlin Decoder interface (mirrors JS)

```kotlin
object NcmDecoder {
  /** Decrypt NCM file to raw audio bytes. Pure function. */
  fun decrypt(input: ByteArray): AudioData
}

data class AudioData(
  val bytes: ByteArray,        // decrypted plaintext audio (typically MP3 or FLAC)
  val format: String,          // "mp3" / "flac" (source format, used as ffmpeg input)
  val durationSec: Double?     // ffprobe result (external; decoder does not call ffprobe)
)
```

### 8.4 ffmpeg JNI interface

```kotlin
object FfmpegBridge {
  init { System.loadLibrary("ffmpeg") }

  /** Transcode audio bytes. Returns encoded bytes. */
  external fun transcode(
    inputBytes: ByteArray,
    inputFormat: String,
    outputFormat: String,
    bitrateKbps: Int
  ): ByteArray
}
```

## 9. Data flow (end-to-end)

```
[1] User opens app
   → FileListScreen renders (empty state + FAB)

[2] User taps FAB
   → ActivityResultContracts.OpenMultipleDocuments() triggers SAF picker

[3] User selects N NCM files
   → ActivityResult returns List<Uri>
   → ViewModel.uris.addAll(uris)
   → FileListScreen shows filename + size + thumbnail (SAF Thumbnail API)

[4] User picks target format (MP3/FLAC/WAV/M4A/OGG) + bitrate
   → ViewModel.targetFormat = "mp3"
   → ViewModel.bitrateKbps = 320

[5] User taps "开始转换" button
   → ViewModel.startConversion()
   → ContextCompat.startForegroundService(intent)
   → Service starts + notification appears: "准备中... 0/N"

[6] Service main loop (per uri):
   ┌──────────────────────────────────────────────────────────────┐
   │ for (i, uri in uris) {                                      │
   │   updateNotification("处理 ${i+1}/N: ${filename}")          │
   │                                                             │
   │   // [a] Read encrypted file                                 │
   │   val input = contentResolver.openInputStream(uri)!!        │
   │   val encrypted = input.readBytes(); input.close()          │
   │                                                             │
   │   // [b] Detect format (magic bytes)                         │
   │   val format = FormatDetector.detect(encrypted)             │
   │   if (format == null) {                                     │
   │     failures.add(Failure(uri, "未知格式")); continue         │
   │   }                                                          │
   │                                                             │
   │   // [c] Decrypt (Kotlin decoder)                            │
   │   val audio = when (format) {                                │
   │     "ncm"     -> NcmDecoder.decrypt(encrypted)              │
   │     "qmc0"    -> QmcDecoder.decrypt(encrypted, "qmc0")      │
   │     "qmcflac" -> QmcDecoder.decrypt(encrypted, "qmcflac")   │
   │     /* ... 9 other formats ... */                            │
   │   }                                                          │
   │                                                             │
   │   // [d] ffmpeg transcode                                    │
   │   val encoded = FfmpegBridge.transcode(                     │
   │     audio.bytes, audio.format, targetFormat, bitrateKbps    │
   │   )                                                          │
   │                                                             │
   │   // [e] Write output (SAF ACTION_CREATE_DOCUMENT)          │
   │   val outputName = "${uri.nameWithoutExt}.${targetFormat}" │
   │   val targetUri = createOutputDocument(outputName)          │
   │   val output = contentResolver.openOutputStream(targetUri)!!│
   │   output.write(encoded); output.close()                     │
   │                                                             │
   │   successes.add(uri)                                        │
   │   updateNotification("已完成 ${i+1}/N: ${filename}")        │
   │ }                                                            │
   └──────────────────────────────────────────────────────────────┘

[7] Service cleanup
   → stopForeground(STOP_FOREGROUND_REMOVE)
   → stopSelf()
   → Final notification: "完成 X 成功 / Y 失败", tap → detail
```

**Key decisions**:
- **Output location**: first conversion triggers `ACTION_CREATE_DOCUMENT` once for user to pick directory; subsequent conversions default to that directory (SharedPreferences).
- **Failure tolerance**: a single file failure does not abort the batch.
- **Progress granularity**: file-level (X/N), not byte-level (avoids notification throttling, saves battery).

## 10. Error handling matrix

| Error source                                | Detection                            | Handling                                                       | User-visible              |
| ------------------------------------------- | ------------------------------------ | -------------------------------------------------------------- | ------------------------- |
| **File read failure** (SAF uri stale, permission revoked) | `openInputStream` throws `IOException` | `Failure(uri, "读取失败: ${e.message}")`, continue              | Notification "1 失败", tap → detail |
| **Format not recognized**                  | `FormatDetector.detect` returns null | `Failure(uri, "未知格式: ${hex(first16Bytes)}")`, continue     | hex dump aids debugging   |
| **ekey missing** (QMCv2 file)              | Decoder throws `MissingEkeyException` | `Failure(uri, "需要 ekey，请去设置")`, continue                 | Jump to EkeySetup screen  |
| **ekey invalid**                           | Decoder throws `InvalidEkeyException` | `Failure(uri, "ekey 无效")`, continue                          | Notify user to re-enter   |
| **Decryption failure**                     | Decoder throws other `RuntimeException` | `Failure(uri, "解密失败: ${e.message}")`, continue         | hex dump aids debugging   |
| **ffmpeg JNI failure**                     | `FfmpegBridge.transcode` throws `RuntimeException` | `Failure(uri, "转码失败: ${e.message}")`, continue    | Same as above             |
| **Disk write failure**                     | `openOutputStream` / `write` throws `IOException` | `Failure(uri, "保存失败: ${e.message}")`, continue    | Same as above             |
| **libffmpeg.so load failure**              | `System.loadLibrary` throws `UnsatisfiedLinkError` | App startup check, dialog "ffmpeg 加载失败，请重新安装" | App-wide error, conversion disabled |
| **Process killed** (system low memory)     | `START_NOT_STICKY` + ViewModel observation | No auto-retry, notification removed | User must re-trigger      |

**Persistent error log**:
- Path: `context.cacheDir/failures-${timestamp}.log`
- Format: JSON line `{"uri": "...", "filename": "...", "error": "...", "stack": "..."}`
- User can share via Settings → 失败日志 → 系统分享 sheet
- 7-day auto-cleanup

## 11. Testing strategy

### 11.1 Three-layer test pyramid

```
                ┌───────────────────────┐
                │  Manual on real       │  ← User real-device regression (pre-release)
                │  device               │
                └───────────────────────┘
              ┌─────────────────────────────┐
              │  Instrumented (emulator)    │  ← `./gradlew connectedDebugAndroidTest`
              │  SAF/Service/JNI integration│     ~30s~2min/case
              └─────────────────────────────┘
            ┌───────────────────────────────────┐
            │  JVM JUnit unit tests             │  ← `./gradlew test`
            │  Pure Kotlin decoders + format    │     <1s/case, edit-and-rerun
            │  detector                         │
            └───────────────────────────────────┘
```

### 11.2 JUnit (no Android)

| Test class              | Coverage                                                          | Sample source                                          |
| ----------------------- | ----------------------------------------------------------------- | ------------------------------------------------------ |
| `NcmDecoderTest`        | 14 NCM samples, decrypted sha256 byte-equality with desktop      | `tests/ncm_format/*.ncm` → `android/app/src/test/resources/test-ncm/` |
| `QmcDecoderTest`        | QMC0 / QMCFLAC / QMCOGG, 3 samples each                          | (same; from `tests/qmc_format/` if present)            |
| `QmcV2DecoderTest`      | MFLAC / MGG, 2 samples each + ekey verification                   | (same)                                                 |
| `KgmDecoderTest`        | KGM / KGMA / VPR, 2 samples each                                  | (same)                                                 |
| `KwmDecoderTest`        | KWM, 2 samples                                                    | (same)                                                 |
| `FormatDetectorTest`    | 11 formats magic-byte detection                                   | First 16 bytes of each encrypted sample                |

**Test vector reuse rule**:
- 14 NCM samples copied from `tests/ncm_format/` (gitignored, supplied locally)
- QMC / KGM / KWM samples: if `tests/` lacks them, **do not backfill in v0.2.2** — add during real-device test
- sha256 expected values: copy from `tests/ncm.test.js` output to Kotlin test
- Any "desktop passes / Android fails" = algorithm translation bug, **blocks release**

### 11.3 Instrumented (emulator)

| Test class                     | Coverage                                                       | Setup |
| ------------------------------ | -------------------------------------------------------------- | ----- |
| `SafAdapterTest`               | `ACTION_OPEN_DOCUMENT` → uri → `openInputStream` roundtrip     | Construct fake Uri |
| `ConversionServiceTest`        | Start Service → process 1 NCM → notification update → `stopSelf` | `adb push` 1 NCM sample |
| `FfmpegBridgeIT`               | `libffmpeg.so` load + ffmpeg transcode call success            | emulator x86_64 |
| `EndToEndConversionTest`       | NCM input → decrypt → ffmpeg → MP3 output → byte-diff desktop reference | `adb push` + `adb pull` |

**Emulator config**:
- API 34 (matches target SDK)
- ABI: x86_64
- Headless: `emulator -avd test_avd -no-window -no-audio -gpu swiftshader_indirect`
- KVM must be available (`ls -l /dev/kvm`)

### 11.4 Real-device (manual)

- Borrow 1~2 friends' real devices (Android 12/13/14)
- At least 1 real file per format (QQ Music / NetEase / KuGou / KuWo)
- Focus: notification progress, SAF picker UX, ekey setup flow
- Defects logged to `docs/superpowers/issues/2026-06-XX-android-issues.md`

### 11.5 Automation scripts

- `./gradlew test` → JUnit (CI-friendly, but no CI in v0.2.2)
- `./gradlew connectedDebugAndroidTest` → Instrumented (needs emulator)
- One-shot: `scripts/test-android.sh` (wraps both + auto-starts emulator)

## 12. Build & distribution

### 12.1 NDK ffmpeg build script

**File**: `android/scripts/build-ffmpeg.sh`

**Behavior**:
- Download ffmpeg 7.0.2 source (commit-locked tarball + SHA-256 check)
- Configure: `--enable-shared --disable-static --disable-programs --disable-doc`
- Enable codecs: `--enable-libmp3lame --enable-libvorbis --enable-libfdk-aac --enable-encoder=flac`
- Disable all non-audio codecs (size optimization)
- Cross-compile 3 ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Output to `android/app/src/main/jniLibs/<abi>/libffmpeg.so`
- Post-build `llvm-strip --strip-unneeded`

**Expected size**:
- arm64-v8a `libffmpeg.so`: ~3–5 MB
- armeabi-v7a: ~3–4 MB
- x86_64: ~4–6 MB

**Run command**:
```bash
cd android
ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.2.9519653 ./scripts/build-ffmpeg.sh
```

### 12.2 Gradle config highlights

```kotlin
android {
  namespace = "com.openconverter.app"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.openconverter.app"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "0.2.2"
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64")
      isUniversalApk = false
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")  // temporary, real keystore at M3
    }
  }

  applicationVariants.configureEach {
    outputs.configureEach {
      val abi = filters.find { it.filterType == FilterType.ABI }?.identifier ?: "universal"
      outputFileName = "openconverter-v0.2.2-android-${abi}.apk"
    }
  }
}
```

**Key dependencies**:
- `androidx.compose.bom:2024.10.00` + `androidx.compose.material3:material3`
- `androidx.activity:activity-compose:1.9.3`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`
- `androidx.core:core-ktx:1.13.1`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`
- Test: `junit:junit:4.13.2`, `kotlinx-coroutines-test:1.8.1`, `androidx.test.ext:junit:1.2.1`

### 12.3 Build commands

```bash
# One-time: download ffmpeg source + build 3 ABIs (~30–60min)
cd android && ./scripts/build-ffmpeg.sh

# One-time: generate gradle wrapper (commit to git)
gradle wrapper --gradle-version 8.7

# Daily dev
cd android
./gradlew test                          # JUnit
./gradlew assembleDebug                 # 3 debug APKs
./gradlew connectedDebugAndroidTest     # instrumented (start emulator first)

# Release
./gradlew assembleRelease               # 3 unsigned release APKs
# Manual sign (3 ABIs)
$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
  --ks ~/keystores/openconverter.jks \
  --ks-key-alias openconverter \
  --out release/openconverter-v0.2.2-android-arm64-v8a.apk \
  app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk

# One-shot scripts
./scripts/test-android.sh               # start emulator + test + connected
./scripts/build-android.sh              # ffmpeg build + assembleRelease + sign + copy to release/
```

### 12.4 GitHub Release asset allocation

**Reuse existing v0.2.2 release** (no new tag):
- URL: `https://github.com/nowa277/OpenConverter/releases/tag/v0.2.2`
- Existing 6 desktop assets preserved
- Append 3 Android APKs:
  - `openconverter-v0.2.2-android-arm64-v8a.apk` (~10 MB)
  - `openconverter-v0.2.2-android-armeabi-v7a.apk` (~9 MB)
  - `openconverter-v0.2.2-android-x86_64.apk` (~10 MB)
- Release notes: append Android install section

## 13. Risk and mitigations

| Risk                                              | Severity | Mitigation                                                                                       |
| ------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------ |
| **NDK ffmpeg first build slow** (30–60min)        | 🟡 M     | Script cache: incremental rebuilds of changed `.c` files; CI caches `~/.cache/ffmpeg-build`        |
| **ffmpeg 7.x API change breaks JNI bridge**       | 🟡 M     | JNI C++ wrapper is a stable middle layer (doesn't call ffmpeg API directly); uses new `avcodec_send_packet` API; commit-locked ffmpeg version |
| **libffmpeg.so larger than expected** (>10MB)     | 🟢 L     | Audio-only codec selection; `llvm-strip --strip-unneeded` post-build                            |
| **Decoder Kotlin translation bug** (byte mismatch with JS) | 🔴 H | JUnit byte-level tests are release gate; M1 validates with 14 NCM samples                  |
| **minSdk 26 device coverage insufficient**        | 🟢 L     | 94% coverage sufficient for v0.2.2; future spec may lower to 24                                  |
| **SAF picker misbehavior on some OEM ROMs**       | 🟡 M     | EndToEndConversionTest covers; real-device test on ≥2 brands (Xiaomi + Samsung/Huawei)            |
| **Process killed** — poor user perception         | 🟢 L     | Notification progress + explicit `START_NOT_STICKY` behavior; user re-triggers                    |
| **ABI fragmentation**                              | 🟢 L     | 3 ABIs cover 99.5%+ devices; Xiaomi/OPPO/Vivo etc. built-in emulator compatible                   |
| **Foreground Service restricted on Android 14**    | 🟡 M     | `mediaProcessing` type is Android 14 official; `targetSdk = 34` activates; older fallback `dataSync` |
| **User enters wrong ekey**                          | 🟢 L     | Clear error message points to Settings; "test ekey" button (verify against small QMCv2 file)       |
| **Play Store compliance**                          | 🟢 N/A   | v0.2.2 not on Play Store; out of scope                                                            |

## 14. Implementation milestones (Approach C: end-to-end MVP first)

### M1: End-to-end skeleton (1 week)

- 1.1 Gradle project skeleton (`android/` dir + wrapper + manifest)
- 1.2 `FormatDetector` (magic bytes, 11 formats) + JUnit
- 1.3 `NcmDecoder.kt` (NCM Kotlin port) + JUnit (**byte-level sha256**)
- 1.4 `build-ffmpeg.sh` + arm64-v8a `libffmpeg.so`
- 1.5 `FfmpegBridge` Kotlin + JNI C++ wrapper
- 1.6 Minimal `MainActivity` + `FileListScreen` + SAF picker
- 1.7 `ConversionService` (minimal: 1 file NCM→MP3, **no Foreground Service yet**, validate link)
- **M1 acceptance**: emulator converts 1 NCM→MP3, output byte-diff matches desktop

### M2: 11-format extension (1 week)

- 2.1 Remaining 10 decoders (QMCv1 / QMCv2 / KGM / KWM / VPR) + JUnit
- 2.2 ekey persistence (SharedPreferences) + `SettingsScreen`
- 2.3 Multi-format path + multi-file batch
- **M2 acceptance**: 11 formats each with 1 sample, JUnit all green

### M3: Service + distribution (1.5 weeks)

- 3.1 ConversionService → Foreground Service + notification progress
- 3.2 Failure log persistence
- 3.3 ABI splits (3 APKs)
- 3.4 Release signing (generate keystore, `.gitignore` keystore path)
- 3.5 EndToEndConversionTest instrumented
- 3.6 Real-device test (1~2 friends' phones)
- 3.7 Documentation: README Android section + branch history
- 3.8 GitHub Release v0.2.2 + 3 Android APKs
- **M3 acceptance**: v0.2.2 release public with 9 assets (4 Linux + 2 Windows + 3 Android)

**Total: 3.5 weeks**

## 15. Desktop zero-impact hard rules

`android-port` branch must satisfy:

1. `git diff main -- package.json` → **empty** (no package.json changes)
2. `git diff main -- src/` → **empty** (entire `src/` tree unchanged)
3. `git diff main -- scripts/setup-*.sh scripts/rename-*.sh` → **empty** (desktop scripts unchanged)
4. `git diff main -- tests/build.test.sh` → **empty**
5. Every commit must leave `cd .. && npm run build:linux --dir` green

Any violation = that commit blocks merge.

## 16. Multi-platform Development Principles (inherited from windows spec §11)

This work extends the principles established in
`docs/superpowers/specs/2026-06-16-windows-installer-design.md` §11:

### 16.1 Branch-per-platform

```
main (v0.2.2)
  ├── windows-installer  (merged at v0.2.2)
  ├── android-port       (this spec, v0.2.2)
  └── macos-installer    (future)
```

### 16.2 Additive-only changes

`android-port` MUST NOT modify the `linux` / `win` / `nsis` sub-blocks of `package.json`. Android lives in its own `android/` subdirectory with its own Gradle toolchain.

### 16.3 Platform conditionals in main process only

The Android app has no `process.platform` checks (it is itself the platform). The principle applies in reverse: desktop renderer (`src/renderer/`) stays unchanged; Android is a separate codebase.

### 16.4 Per-commit baseline verification

`android-port` branch commits MUST leave:
- `cd android && ./gradlew test` green
- Desktop `npm run build:linux --dir` green (no regressions in shared ground)

### 16.5 Spec before implementation

This spec (`docs/superpowers/specs/2026-06-17-android-port-design.md`) is the design artifact for review before code lands. Implementation plan (`docs/superpowers/plans/2026-06-17-android-port.md`) follows after spec approval.

### 16.6 Algorithm-truth synchronization

`src/decoders/*.js` (desktop) and `android/.../decoders/*.kt` (Android) are two implementations of the same algorithms. The 14 NCM samples and the sha256 expected values are the **single source of truth**. Any algorithm change must:
1. Update the JS implementation
2. Update the Kotlin implementation
3. Re-run both test suites; both must pass with byte-level equality
4. Update the shared sha256 expected values

## 17. Acceptance criteria (v0.2.2 Android release gate)

1. `./gradlew test` all green, 14 NCM samples byte-sha256 match desktop
2. `./gradlew connectedDebugAndroidTest` all green, end-to-end NCM→MP3 output byte-diff matches desktop reference
3. `./gradlew assembleDebug` produces 3 debug APKs
4. Manual emulator test: 11 formats × 1 file each, all succeed
5. Manual real-device test: at least 1 friend's phone (Android 13/14), 1+ files
6. APK < 15 MB each (arm64-v8a especially)
7. `git diff main -- package.json` empty
8. `git diff main -- src/` empty
9. GitHub Release v0.2.2 has 3 Android APKs appended
10. README has Android install section

## 18. Open questions

| # | Question                                                      | Resolution timing              |
| - | ------------------------------------------------------------- | ------------------------------ |
| 1 | ffmpeg bitrate default (192k / 256k / 320k)?                  | M1 validation, suggest 256k    |
| 2 | Output file naming (keep original name / suffix / user-rename)? | M1 validation                  |
| 3 | ekey encrypted storage or plain SharedPreferences?            | M2 (plain is sufficient for v0.2.2) |
| 4 | Multi-file batch cancel button (interrupt coroutine)?         | M3 implementation              |
| 5 | Notification icon (Spotify-style dark)?                       | M3 implementation              |
| 6 | Dark mode follow system or fixed dark?                        | M3 (suggest fixed dark)        |
| 7 | Real-device test samples (where to find NCMs)?                | M3 start, use `tests/ncm_format/` |

## 19. Decisions log (brainstorming 2026-06-17)

For traceability, the 10 clarifying questions answered in this
brainstorming session:

| #  | Decision                                                                                  |
| -- | ----------------------------------------------------------------------------------------- |
| Q1 | Code hosting: **same repo, new `android-port` branch** (off main at v0.2.2)                |
| Q2 | Scope: **B standard** (11 formats + ekey + progress + multi-file)                        |
| Q3 | UI strategy: **B native Kotlin + Jetpack Compose** (no WebView)                          |
| Q4 | ffmpeg integration: **B self-built NDK .so** (audio codecs only)                          |
| Q5 | minSdk 26 / targetSdk 34                                                                  |
| Q6 | Decoder sync: **A one-time Kotlin translation + test vectors as contract**                |
| Q7 | Distribution: **A GitHub Releases only** (no Play Store in v0.2.2)                        |
| Q8 | Testing: **A JVM unit + local emulator instrumented** (no CI in v0.2.2)                   |
| Q9 | ABI scope: **B multi-APK** (arm64-v8a + armeabi-v7a + x86_64)                             |
| Q10 | Background conversion: **A Foreground Service + notification** (`mediaProcessing` on 14+, `dataSync` fallback) |
| Q11 | Implementation path: **C end-to-end MVP first** (M1 unblocks all risks)                   |

User-imposed constraints during brainstorming:
- **Unified version v0.2.2**: a new platform is not a version-bump reason.
- **Minimize impact to other platforms' code**: reinforce windows spec §11.2.
