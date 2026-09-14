# Implementation Plan - Fully Integrated Zero-Shizuku KuGou Auto-Sync & Self-Healing Pipeline

[Overview]
Enable seamless, end-to-end audio decryption in OpenConverter by integrating native multi-variant root synchronization, automated public storage key scanning, and in-pipeline auto-healing for missing keys, eliminating mandatory third-party Shizuku dependencies while providing fault-tolerant conversion for ordinary users.

Currently, OpenConverter v1.3.1 fails on modern KuGou tracks because it only searches for legacy `KGMusicV3.db` SQLite files and strictly requires an external Shizuku app or fragile `su -c` root commands. This implementation replaces that paradigm with: (1) native in-app root privilege execution adapting to toybox/KernelSU/Magisk/APatch environments; (2) recursive background scanning across public directories (`/sdcard/Download`, `/sdcard/Music`, `kgmusic`) for exported `kgg.key`, `mggkey*`, and `KGMusicV3.db` files; and (3) an auto-healing conversion engine that transparently queries and imports keys upon encountering unkeyed tracks before failing.

[Types]
Type system additions and modifications in `KugouKeySyncManager.kt` and `ConversionEngine.kt`:

- `sealed class KeySyncResult`:
  - `data class Success(val source: String, val count: Int, val added: Int)`
  - `data class Partial(val message: String, val currentTotal: Int)`
  - `data class Failed(val reason: String)`

- `enum class RootEnvironment`:
  - `MAGISK`, `KERNEL_SU`, `TOYBOX_SU`, `NONE`

- `data class ConversionAutoHealConfig`:
  - `val allowSilentSync: Boolean = true`
  - `val maxAutoSyncAttempts: Int = 1`

[Files]
1. New Files:
   - `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/PublicStorageKeyScanner.kt`:
     Scans standard public folders (`/sdcard/Download`, `/sdcard/Music`, `/sdcard/kgmusic`) for any key files (`kgg.key`, `mggkey_multi_process`, `*.db`), parses them safely, and returns extracted key maps.

2. Existing Files to Modify:
   - `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KugouKeySyncManager.kt`:
     Integrate direct root execution variants, remove mandatory Shizuku prerequisites, and add multi-source fallback (Root -> Public Storage Scanner -> Legacy DB).
   - `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggKeyStore.kt`:
     Add synchronous & non-blocking `tryResolveMissingKey(keyId: String): Boolean` method that triggers on-demand discovery.
   - `android/app/src/main/kotlin/com/openconverter/app/engine/ConversionEngine.kt`:
     Update `runOne` to intercept `Missing KGG key for <id>` exceptions, call `keyStore.tryResolveMissingKey(id)`, and retry decryption once before reporting failure.
   - `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt`:
     Update UI copy and diagnostics panel: display active detection mode ("Direct Root", "Storage Scanner", "Ready") without scaring users about missing Shizuku.
   - `android/app/src/main/res/values-zh/strings.xml` & `values/strings.xml`:
     Update translation strings to reflect the zero-dependency auto-sync behavior.

[Functions]
1. `PublicStorageKeyScanner.scanPublicKeys(context: Context): Map<String, String>`
   - File: `PublicStorageKeyScanner.kt`
   - Purpose: Traverses public directories for key files and parses both text and MMKV formats.
2. `KugouKeySyncManager.syncAllSources(context: Context): Map<String, String>`
   - File: `KugouKeySyncManager.kt`
   - Purpose: Tries direct Root MMKV first; if unavailable, scans public storage; merges all found keys into `KggKeyStore`.
3. `KggKeyStore.refreshIfKeyMissing(keyId: String): Boolean`
   - File: `KggKeyStore.kt`
   - Purpose: Check if `keyId` exists; if not, execute `syncAllSources` and return true if key was acquired.
4. `ConversionEngine.runOne(...)`
   - File: `ConversionEngine.kt`
   - Purpose: Intercept `Missing KGG key` during decryption, invoke auto-heal, and repeat `streamingDecoder.decrypt` if key is resolved.

[Classes]
1. `PublicStorageKeyScanner`
   - Location: `com.openconverter.app.decoders.kgg`
   - Key Methods: `scanKnownDirectories()`, `parseCandidateFile(file: File)`
2. `KugouKeySyncManager` (Modified)
   - Location: `com.openconverter.app.decoders.kgg`
   - Enhancements: Multi-variant SU runner, zero-dependency mode.
3. `ConversionEngine` (Modified)
   - Location: `com.openconverter.app.engine`
   - Enhancements: Self-healing retry handler.

[Dependencies]
No new third-party dependencies required. Utilizes existing `rikka.shizuku:api` for optional fallback, `kotlinx-coroutines`, and native Android File APIs.

[Testing]
1. JVM Unit Tests:
   - Validate `PublicStorageKeyScannerTest` parsing mock MMKV and kgg.key lines.
   - Validate `ConversionEngineTest` auto-healing behavior when a decoder throws a recoverable missing key error.
2. Device Integration Tests:
   - Test on emulator / real device: place unkeyed track in queue, trigger conversion, verify auto-sync pulls keys and converts track to playable FLAC.

[Implementation Order]
1. Create `PublicStorageKeyScanner.kt` with robust regex & MMKV parsing.
2. Refactor `KugouKeySyncManager.kt` to unify Root execution and Public Storage scanning.
3. Expose on-demand resolution in `KggKeyStore.kt`.
4. Wire auto-healing into `ConversionEngine.kt`.
5. Modernize Settings UI status cards and user guidance strings.
6. End-to-end device testing with freshly downloaded tracks.
