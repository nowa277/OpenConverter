# Android KGG v5 Implementation Plan

> **For Codex:** REQUIRED NEXT SKILL: Use `executing-plans` for inline execution or `subagent-driven-development` if the user explicitly chooses delegated execution. Follow `systematic-debugging` and `test-driven-development` for each defect slice.

**Goal:** Add general Android KGG v5 support by importing a user-selected `KGMusicV3.db` or `kgg.key`, decrypting KGG audio as a stream, correctly routing compound extensions, and proving the complete flow on the Pixel 8 emulator without committing user audio, databases, or keys.

**Architecture:** Pure Kotlin components parse KGG headers, unwrap QMC2 ekeys, decrypt MAP/RC4 payloads, parse portable key maps, and decrypt database pages. An Android `KggKeyStore` imports SAF documents into an atomically persisted private key map and exposes only key lookup plus status. `ConversionEngine` gains a narrow streaming-decoder path while all existing byte decoders and plain-audio paths remain intact.

**Tech Stack:** Kotlin 1.9/JVM 17, Android SDK 34/minSdk 24, Jetpack Compose, coroutines/StateFlow, Android `AtomicFile`, `SQLiteDatabase`, SAF, JUnit 4, AndroidX instrumentation tests, Gradle, adb, ffprobe.

---

## Assumptions And Constraints

- KGG v5 is identified from the KGM magic and header crypto version `5`; version `3` remains owned by `KgmDecoder`.
- A KGG v5 payload cannot be decrypted without the matching `EncryptionKeyId -> EncryptionKey` mapping. The app imports mappings explicitly selected by the user and never performs network lookup or account login.
- `kgg.key` is deterministic UTF-8 text with one `<id>$<ekey>` mapping per line. Blank lines are ignored; any malformed nonblank line rejects the complete import.
- Real audio, real databases, and extracted real keys are local acceptance inputs only. Git contains deterministic synthetic vectors and metadata, not copyrighted or user-secret material.
- Streaming is limited to the new KGG path. Existing NCM/QMC/KGM/KWM byte-array decoders are not refactored.
- The branch remains `fix/android-kgg-v5`; untracked `.codex/` and `upload_all.sh` remain untouched.

## Completion Evidence

- `./gradlew testDebugUnitTest connectedDebugAndroidTest` passes.
- The local 23-file KGG corpus decrypts byte-for-byte equal to the established reference output.
- Pixel 8 imports both supported key-source types, persists merged mappings across force-stop/relaunch, and converts standard, high-quality, Hi-Res, and `.kgg.flac` inputs.
- Pixel 8 regression covers `.kgm`, `.kgma`, and plain MP3/FLAC.
- Missing-key and invalid-import cases show actionable errors and preserve the prior key map.
- `git diff --check`, secret/path scans, and repository status show only intentional tracked changes.

---

### Task 1: Portable Key Map Parsing And Merge Semantics

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggKeyMap.kt`
- Test: `android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggKeyMapTest.kt`

**Step 1: Write failing parser and merge tests**

Cover LF/CRLF, blank lines, duplicate IDs with last value winning, missing/multiple `$`, empty fields, stable sorted serialization, new IDs, changed IDs, and identical IDs. The public API is fixed as:

```kotlin
data class KggKeyMergeResult(
    val merged: Map<String, String>,
    val added: Int,
    val updated: Int,
)

object KggKeyMap {
    fun parse(text: String): Map<String, String>
    fun serialize(keys: Map<String, String>): String
    fun merge(current: Map<String, String>, incoming: Map<String, String>): KggKeyMergeResult
}
```

Assert that `parse("id$a$b")` throws `IllegalArgumentException`, `serialize` ends each nonempty map with `\n`, and merge does not mutate either input map.

**Step 2: Run the focused test and confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggKeyMapTest'`

Expected: compilation fails because `KggKeyMap` does not exist.

**Step 3: Implement the minimum pure Kotlin map logic**

- Normalize CRLF through `lineSequence()` and `trimEnd('\r')`.
- Split only after confirming exactly one `$`.
- Preserve ID and ekey bytes as text except surrounding line terminators; reject whitespace-only fields.
- Serialize by sorted ID for deterministic atomic storage.
- Count `added` when absent and `updated` only when the incoming value differs.

**Step 4: Run focused and decoder tests**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggKeyMapTest' --tests '*DecoderContractTest'`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggKeyMap.kt android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggKeyMapTest.kt
git commit -m "feat(android): add portable KGG key maps"
```

### Task 2: KGG Database Page Decryption

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggDatabaseCipher.kt`
- Test: `android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggDatabaseCipherTest.kt`

**Step 1: Write deterministic failing tests**

Use synthetic 1024-byte pages generated inside the test from a non-secret test master key. Cover first-page SQLite-header restoration, later-page decryption, truncated input, and non-1024-aligned input. Fix the API as:

```kotlin
object KggDatabaseCipher {
    const val PAGE_SIZE = 1024
    fun decrypt(input: InputStream, output: OutputStream)
    internal fun decrypt(input: InputStream, output: OutputStream, masterKey: ByteArray)
    internal fun pageKey(masterKey: ByteArray, pageNumber: Int): ByteArray
    internal fun pageIv(pageNumber: Int): ByteArray
}
```

The production object owns the known 16-byte Kugou database master key privately. Tests verify output structure and round-trip against a test-side encryptor rather than exposing production key material in logs.

**Step 2: Confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggDatabaseCipherTest'`

Expected: compilation fails because the cipher is absent.

**Step 3: Implement the page algorithm**

- Derive each AES key with `MD5(master || pageNumberLE || 0x546c4173LE)`.
- Derive each IV with the reference LCG byte sequence followed by MD5.
- Use `AES/CBC/NoPadding` one page at a time.
- Restore the first page's SQLite header exactly as required by the format.
- Reject empty, truncated, or non-page-aligned inputs before returning a usable output.
- Never log keys or page plaintext.

**Step 4: Verify**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggDatabaseCipherTest'`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggDatabaseCipher.kt android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggDatabaseCipherTest.kt
git commit -m "feat(android): decrypt KGG key databases"
```

### Task 3: Android Key Import And Atomic Persistence

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggKeyProvider.kt`
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggKeyStore.kt`
- Create: `android/app/src/androidTest/kotlin/com/openconverter/app/decoders/kgg/KggKeyStoreTest.kt`

**Step 1: Add instrumentation dependencies and failing tests**

Add:

```kotlin
androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.test:runner:1.6.2")
```

Define the narrow interfaces and state:

```kotlin
fun interface KggKeyProvider {
    fun find(encryptionKeyId: String): String?
}

fun interface KggKeyImporter {
    suspend fun import(uri: Uri): KggImportResult
}

data class KggImportResult(val added: Int, val updated: Int, val total: Int)

sealed interface KggImportState {
    data class Ready(val total: Int, val lastResult: KggImportResult? = null) : KggImportState
    data object Importing : KggImportState
    data class Failed(val message: String, val total: Int) : KggImportState
}

class KggKeyStore(context: Context) : KggKeyProvider, KggKeyImporter {
    val state: StateFlow<KggImportState>
    override fun find(encryptionKeyId: String): String?
    override suspend fun import(uri: Uri): KggImportResult
}
```

Instrumentation tests create private temporary documents and cover:

- plaintext synthetic SQLite with `PRAGMA page_size=1024` and `ShareFileItems(EncryptionKeyId, EncryptionKey)` imports correctly;
- portable key text imports correctly;
- content detection ignores misleading filename extensions;
- second import adds and updates without dropping unrelated IDs;
- recreating the store restores the deterministic private map;
- malformed text and missing database table fail without modifying memory or disk;
- temporary decrypted SQLite files are deleted on success and failure.

Expose a package-internal constructor accepting `filesDir`, `cacheDir`, and a stream opener so tests do not depend on external storage providers.

**Step 2: Confirm RED on the emulator**

Run: `cd android && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.openconverter.app.decoders.kgg.KggKeyStoreTest`

Expected: compilation fails because the store is absent.

**Step 3: Implement import and persistence**

- Sniff the first bytes for SQLite/encrypted-database shape versus UTF-8 key text.
- Copy/decrypt database input into a unique cache file, open read-only, validate the table and exact two columns, and reject an empty result.
- For synthetic plaintext SQLite, skip page decryption after detecting `SQLite format 3\u0000`; for Kugou encrypted pages, call `KggDatabaseCipher.decrypt`.
- Query only nonblank ID/key rows and close the cursor/database with `use`/`try-finally`.
- Parse all input before merging.
- Persist `KggKeyMap.serialize(merged)` with `AtomicFile.startWrite`, `finishWrite`, and `failWrite`.
- Publish new in-memory state only after `finishWrite` succeeds.
- Delete the temporary plaintext database in `finally`.

**Step 4: Verify instrumentation and JVM tests**

Run: `cd android && ./gradlew testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.openconverter.app.decoders.kgg.KggKeyStoreTest`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/build.gradle.kts android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggKeyProvider.kt android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggKeyStore.kt android/app/src/androidTest/kotlin/com/openconverter/app/decoders/kgg/KggKeyStoreTest.kt
git commit -m "feat(android): import and persist KGG keys"
```

### Task 4: Strict KGG Header Parsing

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggHeader.kt`
- Test: `android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggHeaderTest.kt`

**Step 1: Write failing header-vector tests**

Fix the model and parser contract:

```kotlin
data class KggHeader(
    val headerLength: Int,
    val cryptoVersion: Int,
    val encryptionKeyId: String,
)

object KggHeaderParser {
    const val PREFIX_SIZE = 1024
    fun parse(prefix: ByteArray): KggHeader
}
```

Build synthetic headers from byte fields and cover valid version 5, valid version 3, wrong magic, unknown version, short prefix, header length below required fields, header length beyond available prefix, zero/oversized ID length, and non-UTF-8 ID bytes.

**Step 2: Confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggHeaderTest'`

Expected: compilation fails.

**Step 3: Implement bounded little-endian parsing**

- Compare the full 16-byte KGM magic.
- Read integer fields only after checking bounds.
- Decode the ID using a UTF-8 decoder configured with `CodingErrorAction.REPORT`.
- Return version 3 as a valid parsed header so routing can deliberately leave it with `KgmDecoder`.
- Reject unknown versions with an actionable `IllegalArgumentException`.

**Step 4: Verify**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggHeaderTest' --tests '*DecoderParityTest'`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggHeader.kt android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggHeaderTest.kt
git commit -m "feat(android): parse KGG v5 headers"
```

### Task 5: QMC2 MAP And Segmented RC4 Streaming Ciphers

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggQmc2.kt`
- Test: `android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggQmc2Test.kt`

**Step 1: Write failing offset and chunk-boundary tests**

Use the contract:

```kotlin
fun interface KggStreamCipher {
    fun apply(buffer: ByteArray, length: Int, absoluteOffset: Long)
}

object KggQmc2 {
    fun cipher(key: ByteArray): KggStreamCipher
}
```

Tests compare one-shot output with chunk sizes `1`, `7`, `4096`, and `65537`, including offsets across RC4 segment boundaries. A key of length `<= 300` selects MAP; a key of length `> 300` selects segmented RC4. Applying either cipher twice at identical offsets restores the original bytes.

**Step 2: Confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggQmc2Test'`

Expected: compilation fails.

**Step 3: Port only the reference cipher primitives**

- Implement MAP mask selection as a function of absolute payload offset.
- Implement the QMC2 segmented RC4 key schedule and segment seek logic without retaining state between calls.
- Treat `length` as the valid prefix of the buffer and reject negative offsets or invalid lengths.
- Keep all arithmetic deterministic across JVM/Android by using explicit unsigned byte conversions.

**Step 4: Verify**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggQmc2Test'`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggQmc2.kt android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggQmc2Test.kt
git commit -m "feat(android): add KGG QMC2 stream ciphers"
```

### Task 6: Tencent TEA Ekey Unwrap

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggEkey.kt`
- Test: `android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggEkeyTest.kt`

**Step 1: Write failing V1/V2 and malformed-input tests**

Use deterministic synthetic encrypted ekeys derived by a test-side encryptor and assert exact unwrapped key bytes. Cover V1, V2, invalid Base64 alphabet/padding, ciphertext not divisible by the TEA block size, invalid Tencent padding, and empty final key.

```kotlin
object KggEkey {
    fun unwrap(encoded: String): ByteArray
}
```

**Step 2: Confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggEkeyTest'`

Expected: compilation fails.

**Step 3: Implement the minimal decoder**

- Add a small strict RFC 4648 Base64 decoder compatible with minSdk 24; do not use `java.util.Base64` in production.
- Implement Tencent TEA decryption with 32 rounds and the exact V1/V2 framing from the verified reference.
- Validate framing and padding before returning key bytes.
- Avoid changing the existing `QmcDecoder` Base64 implementation in this task.

**Step 4: Verify**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggEkeyTest' --tests '*DecoderParityTest'`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggEkey.kt android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggEkeyTest.kt
git commit -m "feat(android): unwrap KGG ekeys"
```

### Task 7: Streaming KGG v5 Decoder

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/StreamingDecoder.kt`
- Create: `android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggV5Decoder.kt`
- Test: `android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggV5DecoderTest.kt`

**Step 1: Write failing decoder tests**

Define:

```kotlin
interface StreamingDecoder : Decoder {
    fun decrypt(input: InputStream, output: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): String
}
```

`KggV5Decoder` is constructed with `KggKeyProvider`, supports only `.kgg`, and its byte-array `decrypt` delegates to the streaming method for `Decoder` compatibility. Tests cover:

- valid synthetic version-5 MAP and RC4 files;
- chunk sizes crossing cipher segment boundaries;
- version 3 rejected so the old decoder remains authoritative;
- no keys versus a missing specific ID produce distinct messages;
- invalid ekey and unknown decrypted format fail before FFmpeg;
- input/output streams are not closed by the decoder.

Expected message forms:

```text
No KGG keys imported; import KGMusicV3.db or kgg.key in Settings
Missing KGG key for <id>; import a database or key file containing this ID
```

**Step 2: Confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggV5DecoderTest'`

Expected: compilation fails.

**Step 3: Implement streaming decode**

- Read exactly the fixed header prefix, parse it, and reject non-v5 input.
- Resolve the ekey before writing output.
- Unwrap the ekey and create the MAP/RC4 cipher.
- Skip any remaining header bytes, then read payload chunks, XOR using the absolute payload offset, and write immediately.
- Sniff the first decrypted chunk through `FormatSniffer` and return the detected extension.
- Do not retain the complete encrypted or decrypted audio in the streaming path.

**Step 4: Verify all decoder tests**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*KggV5DecoderTest' --tests '*decoders*'`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/decoders/StreamingDecoder.kt android/app/src/main/kotlin/com/openconverter/app/decoders/kgg/KggV5Decoder.kt android/app/src/test/kotlin/com/openconverter/app/decoders/kgg/KggV5DecoderTest.kt
git commit -m "feat(android): stream KGG v5 decryption"
```

### Task 8: Compound Extension Routing And Streamed File I/O

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/decoders/DecoderRegistry.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/engine/ports.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/engine/AndroidFileSystemPort.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/engine/ConversionEngine.kt`
- Modify: `android/app/src/test/kotlin/com/openconverter/app/decoders/DecoderRegistryTest.kt`
- Modify: `android/app/src/test/kotlin/com/openconverter/app/engine/Fakes.kt`
- Modify: `android/app/src/test/kotlin/com/openconverter/app/engine/ConversionEngineTest.kt`

**Step 1: Add failing registry, routing, naming, and memory-path tests**

Add:

```kotlin
data class DecoderMatch(val encryptedExtension: String, val decoder: Decoder)

fun DecoderRegistry.findForName(displayName: String): DecoderMatch?
```

The match is the longest registered encrypted extension where the lowercased name ends with that extension or contains `"$extension."`. Tests prove `.kgg.flac` resolves `.kgg` before plain `.flac`, `.kgma` does not resolve as `.kgm`, and normal `.flac` has no decoder match.

Extend `FileSystemPort` exactly with:

```kotlin
fun openInput(uri: String): InputStream
fun openCacheOutput(path: String): OutputStream
fun writeOutputFromCache(folderUri: String, displayName: String, mime: String, cachePath: String): String
```

Engine tests prove:

- `.kgg.flac` uses a fake `StreamingDecoder` before the plain extension branch;
- direct same-format output copies from cache without calling `readCache`;
- transcode input is the decrypted cache path;
- output names are `song.mp3` for `song.kgg` and `song.kgg.flac`;
- ordinary `archive.live.flac` becomes `archive.live.mp3`;
- old byte decoders and plain inputs retain prior behavior;
- cache files are cleaned after success, failure, and cancellation.

**Step 2: Confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*DecoderRegistryTest' --tests '*ConversionEngineTest'`

Expected: new compound-routing tests fail under last-extension routing.

**Step 3: Implement the minimal streamed engine branch**

- Resolve `registry.findForName(displayName)` before checking `plainInputExts`.
- For `StreamingDecoder`, stream `fs.openInput(uri)` to a unique decrypted cache via `fs.openCacheOutput(path)`.
- For direct output, stream that cache into the SAF destination through `writeOutputFromCache`.
- For transcoding, pass the decrypted cache path directly to FFmpeg.
- Leave existing `readBytes`/`writeOutput` behavior unchanged for all byte decoders and plain input.
- Change output naming to accept the matched encrypted extension and strip it plus at most one disguise suffix.
- Factor SAF document creation in `AndroidFileSystemPort` so byte and cache writes share identical creation behavior.

**Step 4: Verify engine regression**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*DecoderRegistryTest' --tests '*ConversionEngineTest' --tests '*PortsTest'`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/decoders/DecoderRegistry.kt android/app/src/main/kotlin/com/openconverter/app/engine/ports.kt android/app/src/main/kotlin/com/openconverter/app/engine/AndroidFileSystemPort.kt android/app/src/main/kotlin/com/openconverter/app/engine/ConversionEngine.kt android/app/src/test/kotlin/com/openconverter/app/decoders/DecoderRegistryTest.kt android/app/src/test/kotlin/com/openconverter/app/engine/Fakes.kt android/app/src/test/kotlin/com/openconverter/app/engine/ConversionEngineTest.kt
git commit -m "fix(android): route compound KGG inputs"
```

### Task 9: Application And Conversion-Service Wiring

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/decoders/DefaultDecoders.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/OpenConverterApp.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/service/ConversionService.kt`
- Modify: `android/app/src/test/kotlin/com/openconverter/app/decoders/DecoderRegistryTest.kt`

**Step 1: Write the failing default-registry test**

Replace the global property with dependency injection:

```kotlin
object DefaultDecoders {
    fun registry(kggKeys: KggKeyProvider): DecoderRegistry
}
```

Test that `.kgg` resolves to `KggV5Decoder` while `.kgm`, `.kgma`, and `.vpr` still resolve to `KgmDecoder`.

**Step 2: Confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*DecoderRegistryTest'`

Expected: test fails because `.kgg` is not registered.

**Step 3: Wire one process-wide store**

```kotlin
class OpenConverterApp : Application() {
    val kggKeyStore: KggKeyStore by lazy { KggKeyStore(this) }
}
```

- Register `KggV5Decoder(kggKeys)` alongside existing decoders.
- In `ConversionService`, obtain `(application as OpenConverterApp).kggKeyStore` and pass it to `DefaultDecoders.registry`.
- Do not create a per-conversion key store.

**Step 4: Verify build and unit tests**

Run: `cd android && ./gradlew testDebugUnitTest assembleDebug`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/decoders/DefaultDecoders.kt android/app/src/main/kotlin/com/openconverter/app/OpenConverterApp.kt android/app/src/main/kotlin/com/openconverter/app/service/ConversionService.kt android/app/src/test/kotlin/com/openconverter/app/decoders/DecoderRegistryTest.kt
git commit -m "feat(android): wire KGG v5 decoder"
```

### Task 10: Settings SAF Import UI And State

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/MainActivity.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh/strings.xml`
- Create: `android/app/src/test/kotlin/com/openconverter/app/ui/settings/SettingsViewModelTest.kt`

**Step 1: Write failing ViewModel state tests**

Use a fake of the existing `KggKeyImporter` interface:

```kotlin
class SettingsViewModel(
    private val importer: KggKeyImporter,
    keyState: StateFlow<KggImportState>,
) : ViewModel() {
    val state: StateFlow<SettingsUiState>
    fun importKggKeys(uri: Uri)
}
```

Tests use `StandardTestDispatcher` and cover initial count, importing state, success added/updated/total, failure retaining total, and a second successful import replacing the prior status message.

**Step 2: Confirm RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*SettingsViewModelTest'`

Expected: compilation fails under the current read-only ViewModel.

**Step 3: Implement ViewModel and Compose import card**

- `MainActivity` constructs the ViewModel through a small `ViewModelProvider.Factory` using the application store.
- `SettingsScreen` receives the ViewModel rather than creating it with `remember`.
- Register `ActivityResultContracts.OpenDocument()` and launch with `arrayOf("application/octet-stream", "text/plain", "*/*")`.
- Add a KGG key card showing the import button, current total, latest added/updated values, and localized failure text.
- Add `.kgg` to supported inputs; do not claim universal key-free decryption.
- Put all new visible strings in English and Chinese resources.
- Disable the import button while an import is active.

**Step 4: Verify unit tests and APK build**

Run: `cd android && ./gradlew testDebugUnitTest assembleDebug`

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/MainActivity.kt android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsViewModel.kt android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt android/app/src/main/res/values/strings.xml android/app/src/main/res/values-zh/strings.xml android/app/src/test/kotlin/com/openconverter/app/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(android): import KGG keys from settings"
```

### Task 11: Environment-Gated Real Fixture Verification

**Files:**
- Create: `android/scripts/verify-kgg-v5.sh`
- Create: `android/app/src/androidTest/kotlin/com/openconverter/app/decoders/kgg/KggRealFixtureTest.kt`
- Modify: `README.md`
- Modify: `README_EN.md`

**Step 1: Add a skipped-without-input real-fixture test**

The instrumentation test reads these runner arguments:

```text
kggFixtureDir
kggKeySource
kggExpectedDir
```

It requires all three together, recursively finds `.kgg` and `.kgg.<disguise>` files, imports the selected database/key source, streams each file through `KggV5Decoder`, and compares SHA-256 with same-relative-path expected plaintext. It fails if zero files are discovered. If all arguments are absent, it uses `Assume.assumeTrue` to skip without weakening normal CI.

**Step 2: Write the adb harness**

`verify-kgg-v5.sh` must:

- require `KGG_FIXTURE_DIR`, `KGG_KEY_SOURCE`, and `KGG_EXPECTED_DIR`;
- reject missing paths and print usage without exposing key contents;
- build/install the debug and test APKs;
- push inputs to `/data/local/tmp/openconverter-kgg/`;
- copy them into app-accessible storage with `run-as com.openconverter.app`;
- invoke only `KggRealFixtureTest` with runner arguments;
- remove staged device files through a shell trap on success or failure.

Document setup and the privacy boundary in both root READMEs, plus the exact command:

```bash
KGG_FIXTURE_DIR=/local/kgg \
KGG_KEY_SOURCE=/local/KGMusicV3.db \
KGG_EXPECTED_DIR=/local/reference \
android/scripts/verify-kgg-v5.sh
```

**Step 3: Run normal instrumentation without private inputs**

Run: `cd android && ./gradlew connectedDebugAndroidTest`

Expected: synthetic tests PASS and the real fixture test is reported skipped.

**Step 4: Run the local 23-file reference comparison**

Run the script with the local verified corpus/database/reference directories outside the repository.

Expected: 23 discovered, 23 matched, 0 failed.

**Step 5: Commit only code and documentation**

Before staging, run:

```bash
git status --short
git diff -- android/scripts/verify-kgg-v5.sh android/app/src/androidTest/kotlin/com/openconverter/app/decoders/kgg/KggRealFixtureTest.kt README.md README_EN.md
```

Then:

```bash
git add android/scripts/verify-kgg-v5.sh android/app/src/androidTest/kotlin/com/openconverter/app/decoders/kgg/KggRealFixtureTest.kt README.md README_EN.md
git commit -m "test(android): verify real KGG v5 fixtures"
```

### Task 12: Pixel 8 End-To-End Acceptance And Repository Audit

**Files:**
- Modify only if a verified defect requires it: files directly responsible for that defect
- Create locally, never stage: emulator screenshots, ffprobe logs, hashes, copied audio, databases, and key files

**Step 1: Establish a clean verification baseline**

Run:

```bash
cd android
./gradlew clean testDebugUnitTest connectedDebugAndroidTest assembleDebug
adb devices
```

Expected: all automated tests pass and the Pixel 8 emulator is the single selected Android target.

**Step 2: Verify key-import UI behavior**

- Install the debug APK and open Settings.
- Import the real `KGMusicV3.db` through the SAF picker.
- Confirm added/updated/total values and no key material in UI/logcat.
- Import a synthetic `kgg.key` that adds one ID and updates one synthetic ID.
- Force-stop and relaunch; confirm total and lookups persist.
- Import malformed text and an invalid database; confirm the prior total remains unchanged.

Record only counts and pass/fail evidence outside Git.

**Step 3: Verify conversions on Pixel 8**

Convert at least:

- one standard-quality `.kgg`;
- one high-quality `.kgg`;
- one Hi-Res `.kgg`;
- one original `.kgg.flac`;
- one old `.kgm`;
- one old `.kgma`;
- one plain MP3 and one plain FLAC.

Confirm `.kgg.flac` produces a clean stem without `.kgg`. For each output run:

```bash
ffprobe -v error -show_entries format=format_name,duration -show_entries stream=codec_type,codec_name -of json /local/output
```

Expected: readable container, positive duration, and at least one audio stream.

**Step 4: Verify recovery from a missing key**

- Replace the app key map with a synthetic/nonmatching source through normal app-data reset and import.
- Attempt a known KGG and verify the visible error includes its key ID plus import guidance.
- Import a source containing that ID and retry the same selected input.
- Confirm conversion succeeds without renaming or recopying the source audio.

**Step 5: Fix only reproduced defects with RED/GREEN tests**

For every E2E defect, first add the smallest JVM or instrumentation regression test, run it to prove RED, implement the narrow fix, rerun the focused test, then rerun the complete command from Step 1. Commit each independent fix as `fix(android): <specific behavior>`.

**Step 6: Audit repository contents and history**

Run:

```bash
git diff --check origin/main...HEAD
git status --short
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD
git ls-files | rg -i '(KGMusicV3|kgg\.key|\.kgg(\.|$)|\.kgm(a)?$|\.db$)'
git grep -n -I -E '(/tmp/kgg|C:\\KuGou|EncryptionKey(Id)?\$|BEGIN PRIVATE)' -- . ':!docs/superpowers/specs/*' ':!docs/superpowers/plans/*'
```

Expected:

- `git diff --check` has no output.
- Status contains only intentional branch changes plus the pre-existing untracked `.codex/` and `upload_all.sh`.
- No real audio/database/key artifacts are tracked.
- No local absolute acceptance path or secret content appears in production/test sources.

**Step 7: Final verification commit if documentation changed**

If acceptance found no code defect, do not create an empty commit. If either root README needed verified command corrections, stage only the corrected README files and commit:

```bash
git add README.md README_EN.md
git commit -m "docs(android): document KGG v5 verification"
```

---

## Execution Order And Checkpoints

1. Tasks 1-3 establish key ingestion and persistence without touching conversion behavior.
2. Tasks 4-7 establish independently testable KGG parsing and streaming cryptography.
3. Tasks 8-10 integrate routing, process ownership, and user interaction while preserving old paths.
4. Task 11 proves byte parity against private fixtures without putting them in Git.
5. Task 12 is the release gate: full automated suite, Pixel 8 E2E, recovery behavior, and repository audit.

Stop at the first unexplained RED result. Diagnose it with evidence before changing implementation or expected vectors.
