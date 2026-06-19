# Android v1 UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the OpenConverter Android Home and Settings Compose UI so the queue is the visual hero, FileCards show file size and failure reason, Settings exposes version/repo/disclaimer, the waveform logo becomes the launcher icon, and no emoji appears anywhere.

**Architecture:** Pure-UI refactor on top of the already-working conversion engine (commit 60f37ab). New pieces: a `formatBytes` pure function, a `FileEntry.sizeBytes` field sourced from `OpenableColumns.SIZE`, a `ModalBottomSheet` for format/folder controls, an adaptive launcher icon set, and a rewritten Settings screen. Engine/FGS/SAF-write/decoders are untouched.

**Tech Stack:** Kotlin 1.9.24, Compose BOM 2024.10.00, Material 3 (`ModalBottomSheet`, `FilterChip`, adaptive icons), minSdk 24 / targetSdk 34 / compileSdk 34, JDK 17. JVM unit tests with JUnit 4 + kotlinx-coroutines-test.

## Global Constraints

- Branch: `android-v1`. Never touch `main`.
- No emoji anywhere in `strings.xml` or UI Kotlin (spec Decision: no-emoji gate).
- Spotify dark palette is locked (see `Color.kt`); `OcPrimary = #1ED760` is the only accent.
- Output filename is auto-derived from input stem (desktop parity); never a manual input field, never a preview on the card.
- One format + one bitrate for the whole batch (matches desktop `src/main/index.js`).
- Version read from `BuildConfig.VERSION_NAME` (currently `1.0.0`), not hardcoded.
- Repo URL constant: `https://github.com/nowa277/OpenConverter`.
- Commits carry `Co-Authored-By: Claude <noreply@anthropic.com>`.
- Do NOT regress: NCM to MP3, NCM to FLAC, MP3 to FLAC must still produce real files on emulator-5554 after the redesign; JVM tests stay 38+/0/0 plus the new ones.
- Build env: `cd android && ./gradlew <task>`, JDK 17 at `/home/user/.local/jdk/jdk-17`, `ANDROID_HOME=/home/user/Android/Sdk`, adb at `/home/user/Android/Sdk/platform-tools/adb`.

**Confirmed baseline (do not regress):** On emulator-5554 (x86_64), commit 60f37ab produces real files for NCM to MP3, NCM to FLAC, MP3 to FLAC. JVM tests 38/0/0.

---

## File Structure

**Created:**
- `android/app/src/main/kotlin/com/openconverter/app/ui/components/FormatBytes.kt` — pure `formatBytes(Long): String` + `formatBadge` helpers, JVM-testable.
- `android/app/src/test/kotlin/com/openconverter/app/ui/components/FormatBytesTest.kt` — boundary cases.
- `android/app/src/main/res/drawable/ic_logo.xml` — 5-bar waveform vector (foreground), 24dp, for the top bar.
- `android/app/src/main/res/drawable/ic_launcher_foreground.xml` — waveform bars for the adaptive foreground.
- `android/app/src/main/res/drawable/ic_launcher_background.xml` — solid `#121212`.
- `android/app/src/main/res/drawable/ic_launcher_monochrome.xml` — white silhouette of the bars (themed icons, API 33+).
- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — adaptive icon referencing the three layers.
- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — same, round.

**Modified:**
- `android/app/src/main/kotlin/com/openconverter/app/saf/SafAdapter.kt` — add `querySize(context, uri): Long`.
- `android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeViewModel.kt` — `FileEntry.sizeBytes`, populate it in `setFiles`.
- `android/app/src/main/kotlin/com/openconverter/app/ui/components/FileCard.kt` — size + failure reason, text-only badges.
- `android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeScreen.kt` — summary strip, bottom sheet, plain-text Settings, logo in bar (Layout A).
- `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt` — hero + project rows + about.
- `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsViewModel.kt` — add `aboutText`, `repoUrl`, `issuesUrl`.
- `android/app/src/main/kotlin/com/openconverter/app/MainActivity.kt` — no change expected (already switches home/settings); revisit only if needed.
- `android/app/src/main/AndroidManifest.xml` — point `android:icon` / `android:roundIcon` at the adaptive icon.
- `android/app/src/main/res/values/strings.xml` — add `settings_about` placeholder, `settings_source_code`, `settings_report_issue`, `settings_project`, `settings_about_title`; strip emoji from `state_done`/`state_failed`.

---

### Task 1: formatBytes pure function

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/ui/components/FormatBytes.kt`
- Test: `android/app/src/test/kotlin/com/openconverter/app/ui/components/FormatBytesTest.kt`

**Interfaces:**
- Produces: `fun formatBytes(bytes: Long): String` — pure JVM function, no Android imports.

- [ ] **Step 1: Write the failing test**

Create `FormatBytesTest.kt`:

```kotlin
package com.openconverter.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatBytesTest {
    @Test fun negative_returns_empty() = assertEquals("", formatBytes(-1L))
    @Test fun zero() = assertEquals("0 B", formatBytes(0L))
    @Test fun under_1k() = assertEquals("512 B", formatBytes(512L))
    @Test fun just_under_1k() = assertEquals("1023 B", formatBytes(1023L))
    @Test fun exactly_1k() = assertEquals("1.0 KB", formatBytes(1024L))
    @Test fun one_and_half_k() = assertEquals("1.5 KB", formatBytes(1536L))
    @Test fun one_mb() = assertEquals("1.0 MB", formatBytes(1_048_576L))
    @Test fun four_point_two_mb() = assertEquals("4.2 MB", formatBytes(4_482_658L))
    @Test fun one_gb() = assertEquals("1.0 GB", formatBytes(1_073_741_824L))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.openconverter.app.ui.components.FormatBytesTest"`
Expected: FAIL (unresolved reference `formatBytes`).

- [ ] **Step 3: Write minimal implementation**

Create `FormatBytes.kt`:

```kotlin
package com.openconverter.app.ui.components

/** Human-readable byte size. Negative or unknown (SAF providers that omit SIZE) -> empty. */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return ""
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val rounded = (value * 10.0).toLong().toDouble() / 10.0
    val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() + ".0" else rounded.toString()
    return "$text ${units[unit]}"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.openconverter.app.ui.components.FormatBytesTest"`
Expected: PASS (9/9).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/ui/components/FormatBytes.kt android/app/src/test/kotlin/com/openconverter/app/ui/components/FormatBytesTest.kt
git commit -m "feat(android-v1): formatBytes pure helper + unit tests"
```

---

### Task 2: FileEntry.sizeBytes + SafAdapter.querySize

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/saf/SafAdapter.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeViewModel.kt`
- Test: `android/app/src/test/kotlin/com/openconverter/app/ui/home/HomeViewModelSizeTest.kt` (new)

**Interfaces:**
- Consumes: `SafAdapter.queryDisplayName` (existing).
- Produces: `SafAdapter.querySize(context: Context, uri: Uri): Long` (returns -1 when unknown); `FileEntry.sizeBytes: Long = -1L`; `HomeViewModel.setFiles` now also reads size.

- [ ] **Step 1: Write the failing test**

Create `HomeViewModelSizeTest.kt`. Because `HomeViewModel` is an `AndroidViewModel`, the size plumbing is tested at the `SafAdapter.querySize` contract level using a fake resolver is heavy; instead test the mapping logic via a thin pure helper. Add to `HomeViewModel` a companion `mapSize(raw: Long): Long = if (raw > 0) raw else -1L` and test that:

```kotlin
package com.openconverter.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelSizeTest {
    @Test fun positive_size_passes_through() = assertEquals(4481054L, HomeViewModel.mapSize(4481054L))
    @Test fun zero_becomes_unknown() = assertEquals(-1L, HomeViewModel.mapSize(0L))
    @Test fun negative_becomes_unknown() = assertEquals(-1L, HomeViewModel.mapSize(-1L))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.openconverter.app.ui.home.HomeViewModelSizeTest"`
Expected: FAIL (unresolved `mapSize`).

- [ ] **Step 3: Add querySize to SafAdapter**

In `SafAdapter.kt`, add after `queryDisplayName`:

```kotlin
    /** Best-effort byte size for a SAF document/content uri, or -1 if the provider omits SIZE. */
    fun querySize(context: Context, uri: Uri): Long {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !c.isNull(idx)) {
                    val size = c.getLong(idx)
                    if (size > 0) return size
                }
            }
        }
        return -1L
    }
```

- [ ] **Step 4: Add sizeBytes to FileEntry + mapSize + populate in setFiles**

In `HomeViewModel.kt`:

- Add `val sizeBytes: Long = -1L,` to the `FileEntry` data class (after `displayName`).
- Add companion: `companion object { fun mapSize(raw: Long): Long = if (raw > 0) raw else -1L }`.
- In `setFiles`, build each entry with the size:

```kotlin
    fun setFiles(uris: List<Uri>) {
        val ctx = getApplication<Application>()
        val entries = uris.map { uri ->
            FileEntry(
                uri = uri.toString(),
                displayName = SafAdapter.queryDisplayName(ctx, uri),
                sizeBytes = mapSize(SafAdapter.querySize(ctx, uri)),
            )
        }
        _state.update { it.copy(files = entries) }
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.openconverter.app.ui.home.HomeViewModelSizeTest"`
Expected: PASS (3/3). Then run the whole suite to confirm no regression: `./gradlew :app:testDebugUnitTest` — expect all green (38 prior + 9 formatBytes + 3 size = 50).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/saf/SafAdapter.kt android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeViewModel.kt android/app/src/test/kotlin/com/openconverter/app/ui/home/HomeViewModelSizeTest.kt
git commit -m "feat(android-v1): FileEntry.sizeBytes via OpenableColumns.SIZE"
```

---

### Task 3: Waveform logo + adaptive launcher icon

**Files:**
- Create: `android/app/src/main/res/drawable/ic_logo.xml`
- Create: `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `android/app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Create: `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `@drawable/ic_logo` (24dp vector), the adaptive launcher set referenced by the manifest.

The source SVG (project logo) uses a `#121212`/`#181818` background gradient, an inner ring, and 5 green (`#1ED760` to `#1DB954`) wave bars. For the launcher we render: solid `#121212` background, the 5 bars as foreground centered in the 108dp adaptive canvas with the 66dp safe zone, and a white silhouette as the monochrome layer.

- [ ] **Step 1: Create ic_logo (top-bar mark, 24dp)**

`drawable/ic_logo.xml` — 5 bars, viewport 24x24, no background (transparent so it sits on the app bar). Bars are 2 wide, gap 1, heights 4/6/8/6/4 centered on y=12 — the tallest middle bar matches the SVG's peak:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF1ED760" android:pathData="M5,10h2v4h-2z"/>
    <path android:fillColor="#FF1ED760" android:pathData="M8,9h2v6h-2z"/>
    <path android:fillColor="#FF1ED760" android:pathData="M11,8h2v8h-2z"/>
    <path android:fillColor="#FF1ED760" android:pathData="M14,9h2v6h-2z"/>
    <path android:fillColor="#FF1ED760" android:pathData="M17,10h2v4h-2z"/>
</vector>
```

(The same 5-bar geometry, scaled, is reused for the launcher foreground so the in-app mark and the launcher icon read as one brand.)

- [ ] **Step 2: Create adaptive icon layers**

`drawable/ic_launcher_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FF121212" android:pathData="M0,0h108v108h-108z"/>
</vector>
```

`drawable/ic_launcher_foreground.xml` — 5 bars centered, viewport 108x108 (safe zone radius 33 around center 54,54). Bars are 7 wide, gap 4, heights 24/36/48/36/24, single fill `#FF1ED760` (matches the SVG's brighter green stop):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FF1ED760" android:pathData="M27,42h7v24h-7z"/>
    <path android:fillColor="#FF1ED760" android:pathData="M38,36h7v36h-7z"/>
    <path android:fillColor="#FF1ED760" android:pathData="M49,30h7v48h-7z"/>
    <path android:fillColor="#FF1ED760" android:pathData="M60,36h7v36h-7z"/>
    <path android:fillColor="#FF1ED760" android:pathData="M71,42h7v24h-7z"/>
</vector>
```

(All 5 bars: leftmost edge x=27, rightmost edge x=78 — well inside the 12.5..95.5 safe zone for a 108-canvas with 33-radius safe circle. Heights symmetric around the center.)

`drawable/ic_launcher_monochrome.xml` — same geometry, white, for themed icons:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="M27,42h7v24h-7z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M38,36h7v36h-7z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M49,30h7v48h-7z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M60,36h7v36h-7z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M71,42h7v24h-7z"/>
</vector>
```

- [ ] **Step 3: Create the adaptive icon XMLs**

`mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
```

`mipmap-anydpi-v26/ic_launcher_round.xml`: identical content to `ic_launcher.xml`.

- [ ] **Step 4: Wire the manifest**

In `AndroidManifest.xml`, add to the `<application>` tag (it has no icon attr today):

```xml
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
```

- [ ] **Step 5: Build to verify resources compile**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (minSdk 24: API 24/25 lack adaptive icons and fall back to the system default icon since there are no per-density PNGs — acceptable for v1 per spec; the vast majority of devices are API 26+.)

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/res/drawable/ic_logo.xml android/app/src/main/res/drawable/ic_launcher_foreground.xml android/app/src/main/res/drawable/ic_launcher_background.xml android/app/src/main/res/drawable/ic_launcher_monochrome.xml android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml android/app/src/main/AndroidManifest.xml
git commit -m "feat(android-v1): waveform logo + adaptive launcher icon"
```

---

### Task 4: FileCard — size + failure reason, text-only badges

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/components/FileCard.kt`
- Modify: `android/app/src/main/res/values/strings.xml` (strip emoji from state strings)

**Interfaces:**
- Consumes: `FileEntry` (has `displayName`, `sizeBytes`, `state`, `percent`, `error`), `formatBytes`.
- Produces: `FileCard(name, sizeBytes, state, percent, error)`.

- [ ] **Step 1: Strip emoji from state strings**

In `strings.xml`, replace:

```xml
    <string name="state_done">Done ✓</string>
    <string name="state_failed">Failed ✗</string>
```

with:

```xml
    <string name="state_done">Done</string>
    <string name="state_failed">Failed</string>
```

(`state_running` stays `Converting...`; the live percent is shown on the card, not from this string. Keep `state_pending = Pending`.)

- [ ] **Step 2: Rewrite FileCard**

Replace the whole `FileCard.kt` body with a version taking `sizeBytes` and `error`, showing the size on row 1 (right of the name) and the failure reason as a second red line when `error != null`. Text-only badges, no emoji:

```kotlin
package com.openconverter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class FileState { Pending, Running, Done, Failed }

@Composable
fun FileCard(
    name: String,
    sizeBytes: Long,
    state: FileState,
    percent: Int,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                val sizeText = formatBytes(sizeBytes)
                if (sizeText.isNotEmpty()) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = labelFor(state, percent),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorFor(state),
                )
            }
            if (state == FileState.Running) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (percent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            if (state == FileState.Failed && !error.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun labelFor(state: FileState, percent: Int) = when (state) {
    FileState.Pending -> "Pending"
    FileState.Running -> "$percent%"
    FileState.Done    -> "Done"
    FileState.Failed  -> "Failed"
}

@Composable
private fun colorFor(state: FileState) = when (state) {
    FileState.Done    -> MaterialTheme.colorScheme.primary
    FileState.Failed  -> MaterialTheme.colorScheme.error
    FileState.Running -> MaterialTheme.colorScheme.primary
    FileState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (HomeScreen still calls the old signature; that breaks compilation until Task 5. So run `:app:compileDebugKotlin` after Task 5 instead if this step fails — but since Task 5 is the consumer, temporarily the build is red. To keep TDD honest, do Task 5 immediately after and compile together.)

Note: Tasks 4 and 5 are paired on the compile gate. If Step 3 fails because HomeScreen passes the old args, proceed directly to Task 5 and compile at the end of Task 5.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/ui/components/FileCard.kt android/app/src/main/res/values/strings.xml
git commit -m "feat(android-v1): FileCard shows size + failure reason, text-only badges"
```

---

### Task 5: HomeScreen Layout A — summary strip + bottom sheet + plain-text Settings + logo

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeScreen.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeViewModel.kt` (folder display name + sheet state)

**Interfaces:**
- Consumes: `FileCard` (new signature), `SafAdapter`, `FormatChip`, `PillButton`, `GreenCta`, `@drawable/ic_logo`, `HomeUiState`.
- Produces: a Layout A `HomeScreen` with a sticky bottom CTA, a compact summary strip, and a `ModalBottomSheet` for format/folder.

- [ ] **Step 1: Add folder display name + bottom-sheet open state to HomeViewModel**

In `HomeViewModel.kt`:

- Add to `HomeUiState`: `val outputFolderName: String? = null` and `val showControlsSheet: Boolean = false`.
- In `setOutputFolder`, after updating `outputFolderUri`, also derive a display name:

```kotlin
    fun setOutputFolder(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = runCatching {
            DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').ifBlank { uri.lastPathSegment }
        }.getOrNull() ?: "folder"
        _state.update { it.copy(outputFolderUri = uri.toString(), outputFolderName = name) }
    }
```

(Add imports: `android.provider.DocumentsContract`.)

- Add sheet toggles:

```kotlin
    fun openControlsSheet() = _state.update { it.copy(showControlsSheet = true) }
    fun closeControlsSheet() = _state.update { it.copy(showControlsSheet = false) }
```

- [ ] **Step 2: Rewrite HomeScreen as Layout A**

Replace `HomeScreen.kt` entirely:

```kotlin
package com.openconverter.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openconverter.app.R
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.ui.components.FileCard
import com.openconverter.app.ui.components.FormatChip
import com.openconverter.app.ui.components.GreenCta
import com.openconverter.app.ui.components.PillButton

private val FORMATS = listOf("mp3", "flac", "wav", "m4a", "ogg")
private val BITRATES = listOf("128k", "192k", "320k", null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    DisposableEffect(Unit) {
        viewModel.bind(ctx)
        onDispose { viewModel.unbind(ctx) }
    }

    val pickFiles = rememberLauncherForActivityResult(SafAdapter.openMultipleAudioFilesContract()) { uris ->
        if (uris.isNotEmpty()) viewModel.setFiles(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(SafAdapter.openOutputFolderContract()) { uri ->
        if (uri != null) viewModel.setOutputFolder(uri)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            val canStart = state.files.isNotEmpty() && state.outputFolderUri != null && !state.running
            GreenCta(
                text = if (state.running) stringResource(R.string.notif_cancel) else stringResource(R.string.home_start),
                enabled = canStart || state.running,
                onClick = { if (state.running) viewModel.cancel(ctx) else viewModel.start(ctx) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            PillButton(
                text = "+ ${stringResource(R.string.home_pick_files)}",
                onClick = { pickFiles.launch(arrayOf("audio/*", "*/*")) },
            )
            // Output folder row
            SummaryRow(
                label = stringResource(R.string.home_pick_folder),
                value = state.outputFolderName ?: stringResource(R.string.home_no_folder),
                muted = state.outputFolderUri == null,
                onClick = { pickFolder.launch(null) },
            )
            // Format / bitrate row -> opens the bottom sheet
            val fmtLabel = state.targetFormat.uppercase()
            val brLabel = state.bitrate ?: stringResource(R.string.home_bitrate_lossless)
            SummaryRow(
                label = stringResource(R.string.home_target_format),
                value = "$fmtLabel · $brLabel",
                muted = false,
                onClick = { viewModel.openControlsSheet() },
            )
            Spacer(Modifier.height(4.dp))

            if (state.files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.home_no_files),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.files, key = { it.uri }) { f ->
                        FileCard(
                            name = f.displayName,
                            sizeBytes = f.sizeBytes,
                            state = f.state,
                            percent = f.percent,
                            error = f.error,
                        )
                    }
                }
            }
        }
    }

    if (state.showControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeControlsSheet() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.home_target_format),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    FORMATS.forEach { fmt ->
                        FormatChip(
                            label = fmt.uppercase(),
                            selected = fmt == state.targetFormat,
                            onClick = { viewModel.setTargetFormat(fmt) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.home_bitrate),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    BITRATES.forEach { b ->
                        FormatChip(
                            label = b ?: stringResource(R.string.home_bitrate_lossless),
                            selected = b == state.bitrate,
                            onClick = { viewModel.setBitrate(b) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, muted: Boolean, onClick: () -> Unit) {
    val valueColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 3: Compile together (Tasks 4 + 5)**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `IconButton`/`Icons.Default.Settings` import is now unused, remove it (the new bar uses `TextButton`). The `width` import is needed for `Spacer(Modifier.width(...))` — ensure `androidx.compose.foundation.layout.width` is imported.

- [ ] **Step 4: Build the APK**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeScreen.kt android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeViewModel.kt
git commit -m "feat(android-v1): Home Layout A — summary strip + controls bottom sheet + logo bar"
```

---

### Task 6: Settings screen — hero + project rows + about placeholder

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/res/values/strings.xml` (new keys)

**Interfaces:**
- Consumes: `BuildConfig.VERSION_NAME`, `@drawable/ic_logo` (reused at 96dp via `ic_launcher_foreground`), `SettingsUiState`.
- Produces: a Settings screen with hero (logo + name + version), a PROJECT section with two tappable rows (source code, report an issue), and an ABOUT section with a placeholder text the user replaces.

- [ ] **Step 1: Add strings**

In `strings.xml`, add:

```xml
    <string name="settings_project">Project</string>
    <string name="settings_source_code">Source code</string>
    <string name="settings_report_issue">Report an issue</string>
    <string name="settings_about_title">About</string>
    <string name="settings_about">TODO: disclaimer text to be authored by the project maintainer</string>
```

- [ ] **Step 2: Extend SettingsUiState**

In `SettingsViewModel.kt`, add fields and a companion for URLs:

```kotlin
data class SettingsUiState(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
    val supportedInputs: List<String> = listOf(
        ".ncm (网易云)", ".qmc0/.qmc3/.qmcflac/.qmcogg (QQ音乐 v1)",
        ".kgm/.kgma/.vpr (酷狗)", ".kwm (酷我)",
        ".mp3/.flac/.wav/.m4a/.aac/.ogg/.opus (明文)",
    ),
    val supportedOutputs: List<String> = listOf("MP3", "FLAC", "WAV", "M4A (AAC)", "OGG (Vorbis)"),
    val deferred: String = "QMCv2 (.mflac/.mgg/.bkc) — v2 (需 ekey)",
    val githubUrl: String = "https://github.com/nowa277/OpenConverter",
    val issuesUrl: String = "https://github.com/nowa277/OpenConverter/issues/new",
    val license: String = "MIT",
) {
    companion object {
        const val REPO_URL = "https://github.com/nowa277/OpenConverter"
        const val ISSUES_URL = "https://github.com/nowa277/OpenConverter/issues/new"
    }
}
```

- [ ] **Step 3: Rewrite SettingsScreen**

Replace `SettingsScreen.kt` with a hero + two tappable rows + about, opening URLs via `Intent.ACTION_VIEW`:

```kotlin
package com.openconverter.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openconverter.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = remember { SettingsViewModel() }
    val s = vm.state
    val ctx = LocalContext.current

    fun openUrl(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(96.dp),
                    )
                    Text("OpenConverter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Version ${s.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(stringResource(R.string.settings_project), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openUrl(s.githubUrl) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_source_code), style = MaterialTheme.typography.bodyLarge)
                                Text(s.githubUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openUrl(s.issuesUrl) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_report_issue), style = MaterialTheme.typography.bodyLarge)
                                Text(s.issuesUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Build**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsViewModel.kt android/app/src/main/res/values/strings.xml
git commit -m "feat(android-v1): Settings hero + project links + about placeholder"
```

---

### Task 7: No-emoji gate + full JVM test suite

**Files:**
- Verify only.

- [ ] **Step 1: Grep for emoji in UI strings and Kotlin**

Run:
```bash
cd android
grep -rPn "[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{2190}-\x{21FF}\x{2B00}-\x{2BFF}\x{2B50}-\x{2B55}]" app/src/main/res/values app/src/main/kotlin/com/openconverter/app/ui app/src/main/kotlin/com/openconverter/app/service 2>/dev/null || echo "no emoji found"
```
Expected: `no emoji found`. (The `›` chevron and `+` in "Add files" are plain punctuation, not emoji — allowed. The `✓`/`✗` were removed in Task 4.)

- [ ] **Step 2: Run the full JVM test suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (38 prior + 9 formatBytes + 3 size = 50).

- [ ] **Step 3: Commit any incidental string fixes (likely none)**

If the grep found emoji, fix and commit; otherwise no commit here.

---

### Task 8: On-device acceptance (emulator-5554)

**Files:**
- Verify only (no source change). Installs the rebuilt APK.

- [ ] **Step 1: Install the debug APK**

Run:
```bash
/home/user/Android/Sdk/platform-tools/adb -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-x86_64-debug.apk
```
Expected: `Success`.

- [ ] **Step 2: Confirm the launcher icon is the waveform**

Run:
```bash
/home/user/Android/Sdk/platform-tools/adb -s emulator-5554 shell pm list packages | grep openconverter
/home/user/Android/Sdk/platform-tools/adb -s emulator-5554 shell monkey -p com.openconverter.app -c android.intent.category.LAUNCHER 1
```
Expected: app launches. Visually confirm (screenshot via MCP `mcp__android-emulator__screenshot`) the home screen top bar shows the green waveform + "OpenConverter" + plain-text "Settings", and the launcher entry shows the waveform (adaptive icon renders on the emulator's API 34 launcher).

- [ ] **Step 3: Regression — NCM to MP3 / FLAC and MP3 to FLAC still produce files**

Using MCP android-emulator tools (or manual taps): add `sample.ncm` (already at `/sdcard/Download/sample.ncm`) and `test.mp3` (`/sdcard/Download/test.mp3`), pick the Music folder, switch format via the bottom sheet, start. Confirm each produces a real, non-empty file in the picked folder (check via `adb shell ls -la` on the tree, or the `Done` badge on every card with no `Failed`).

Expected: all cards reach `Done`, files exist and are non-empty.

- [ ] **Step 4: Cancel mid-run**

Start a multi-file conversion, tap the bottom CTA (now "Cancel") while a file is `Running`. Expect the run to stop, remaining files stay `Pending`, no crash.

- [ ] **Step 5: Settings links**

Open Settings (plain-text button). Confirm hero shows logo + "Version 1.0.0". Tap "Source code" — system browser opens the repo. Tap "Report an issue" — opens the issues page.

- [ ] **Step 6: Record acceptance in the progress ledger**

Append to `.git/sdd/progress.md`:
```
## UI redesign acceptance (2026-06-19)
- Layout A Home + bottom-sheet controls + logo bar: verified on emulator-5554
- FileCard size + failure reason, text-only badges: verified
- Settings hero + repo/issue links + about placeholder: verified
- Launcher icon = waveform adaptive icon: verified
- No emoji in strings/UI: grep clean
- Regression: NCM->MP3, NCM->FLAC, MP3->FLAC produce real files; cancel works
- JVM tests: 50/0/0
```

- [ ] **Step 7: Commit ledger**

```bash
git add .git/sdd/progress.md 2>/dev/null || true
git commit --allow-empty -m "docs(android-v1): UI redesign acceptance recorded" || true
```

(If `.git/sdd/progress.md` is git-ignored, skip the commit — the ledger is still the recovery map on disk.)

---

## Success criteria (from spec)

1. Home shows the current output folder by name without opening anything.
2. Queue occupies the majority of the screen; controls are one tap away via bottom sheet.
3. A failed file shows its reason on the card; a done file shows its size.
4. Settings shows version (1.0.0), tappable repo + issue links, and the about placeholder.
5. Launcher icon is the waveform, not the Android robot.
6. No emoji anywhere in the UI.
7. NCM to MP3/FLAC and MP3 to FLAC still produce files on emulator after the redesign.

## User-authored slots (the user edits these before release)

| Content | File | Key / location |
|---|---|---|
| Disclaimer / 致谢 / 声明 (long-form) | `android/app/src/main/res/values/strings.xml` | `settings_about` (replace the TODO placeholder) |
| (optional English mirror) | `android/app/src/main/res/values-en/strings.xml` | `settings_about` |
| Repo / issue URLs (if they change) | `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsViewModel.kt` | `REPO_URL`, `ISSUES_URL` |

The plan creates `settings_about` with a `TODO:` placeholder string; the user replaces it before the v1.0.0 release.
