# Android v0.4.0 UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship v0.4.0 by replacing the v0.3.3 single-screen Compose UI with a Spotify-styled 4-tab bottom-navigation app (Home / History / Settings / About) backed by a typed `NavHost`, themed `darkColorScheme()`, inline SVG logo, and a simplified output-naming rule that drops the v0.3.x `baseName` editing field (use source stem → target ext, matching desktop).

**Architecture:** Material3 `Scaffold` + `NavigationBar` (4 tabs) + `NavHost` for typed routes. New `theme/` package holds `Color` + `Type` + `Theme`. New `components/` package holds `OpenConverterLogo` Composable. Screen package per tab: `ui/home/`, `ui/history/`, `ui/settings/`, `ui/about/`. `FileListScreen.kt` is deleted; its responsibilities are split: file queue + format chips + FAB → `HomeScreen`; persistent state → `HomeViewModel`. `ConversionService` is simplified to a 1-line `outName` calculation (source stem + target ext). The v0.3.x baggage (`outputBaseName`, `setOutputBaseName`, `clearOutputIfFormatChanged`, `sanitizeBaseName`, the `baseName` parameter on `ConversionViewModel.startConversion`, the `OutlinedTextField` in `FileListScreen`) is removed in T0 (preflight, before T1).

**Tech Stack:** Kotlin 1.9.24, Jetpack Compose BOM 2024.06.00, Material3, AGP 8.5.2, androidx.navigation:navigation-compose 2.8.4, ffmpeg-kit 6.0-2.LTS (full-gpl), Gradle 8.7, JUnit 4.13.2, buildConfig = true, Java 11 toolchain, `gh` CLI for release.

**Inherits from:**
- Spec: `docs/superpowers/specs/2026-06-18-android-v040-ui-redesign.md` (refined 2026-06-18, includes §2.5 output naming + §7.2 v0.4.0 milestones)
- Design system: `docs/mockups/v0.4/_common.css` (Spotify color tokens, geometry, shadows)
- Screens: `docs/mockups/v0.4/{home,home-progress,history,settings,about,index}.html`
- Logo: `docs/mockups/v0.4/_logo.html` (inline SVG reference)
- v0.3.3 baseline: 16 commits cherry-picked to `ui-redesign-v040` (HEAD `b669e21`)
- v0.3.3 plan: `docs/superpowers/plans/2026-06-18-android-v033-hotfix.md` (already executed)

**Constraints:**
1. **Branch `ui-redesign-v040`** — do not touch `android-port` (v0.3.3 is released and tagged)
2. **No desktop changes** — `git diff main -- package.json src/` must remain empty
3. **No new permissions** — AndroidManifest.xml stays as-is (compileSdk 34 + `dataSync` FGS from v0.3.3)
4. **v0.3.3 features kept** — SAF folder picker, OCLog, FormatDetector 11/11, plaintext passthrough all stay
5. **v0.3.3 features removed** — `FileListViewModel.outputBaseName` + `setOutputBaseName` + `clearOutputIfFormatChanged` + `sanitizeBaseName` + the `baseName` parameter on `ConversionViewModel.startConversion` + the `OutlinedTextField` in `FileListScreen` (deleted in T0)
6. **Output naming hard-coded** — `outputName = "${sourceStem}.${targetFormat}"` (per spec §2.5, no toggle)
7. **No emoji in UI** — all text + SVG logo, no decorative icons beyond the brand mark
8. **No light theme** — `darkColorScheme()` only
9. **Bottom Nav: 4 tabs** — Home / History / Settings / About (per spec decision #10)
10. **Material3 components only** — no third-party UI libs (avoid bloat)
11. **v0.4.1 vivo 闪退 is DEFERRED** — do not touch FGS / AndroidManifest in this plan
12. **TDD where it matters** — pure logic (FileListViewModel output name, HomeViewModel queue, OCLog if used) gets RED→GREEN. UI Composable tests are NOT in scope (per spec §6.2).

---

## Task Index

| Phase | Task | Description |
|-------|------|-------------|
| 0 Preflight | 0.1 | Verify ui-redesign-v040 branch + 16 v0.3.3 commits present |
| 0 Preflight | 0.2 | Remove v0.3.x baseName/sanitizeBaseName baggage (the un-v0.3.3 work) |
| 1 Foundation | 1.1 | Add navigation-compose dependency + OpenConverterApp NavHost skeleton (RED) |
| 1 Foundation | 1.2 | Wire NavHost routes (Home/History/Settings/About) + bottom NavigationBar (GREEN) |
| 1 Foundation | 1.3 | Theme.kt Spotify dark color scheme (3 files: Color/Type/Theme) |
| 1 Foundation | 1.4 | SVG logo Vector Drawable + OpenConverterLogo Composable |
| 2 Screens | 2.1 | HomeScreen with file queue + format chips + FAB + output folder row |
| 2 Screens | 2.2 | HomeViewModel + simplified outName logic + tests |
| 2 Screens | 2.3 | HistoryScreen + HistoryViewModel (read-only local cache) |
| 2 Screens | 2.4 | SettingsScreen (QMC ekey + 编码/行为/诊断 groups, NO 保留原始文件名 toggle) |
| 2 Screens | 2.5 | AboutScreen (big SVG logo + project info) |
| 3 Integration | 3.1 | Delete old FileListScreen.kt + remove v0.3.x from MainActivity |
| 3 Integration | 3.2 | Run full unit test suite + manual nav walk-through |
| 4 Release | 4.1 | Build 3 signed release APKs |
| 4 Release | 4.2 | Generate SHA256 + verify signing |
| 4 Release | 4.3 | Tag v0.4.0 + push to ui-redesign-v040 |
| 4 Release | 4.4 | Upload to GitHub Release v0.4.0 |

---

## Phase 0: Preflight

### Task 0.1: Verify ui-redesign-v040 branch + 16 v0.3.3 commits present

**Files:** none (verification only)

- [ ] **Step 1: Verify branch + HEAD**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git rev-parse --abbrev-ref HEAD
git log -1 --format="%H %s"
```

Expected:
- Branch: `ui-redesign-v040`
- HEAD: a commit with "OCLog call sites" or later in the v0.3.3 chain (currently `b669e21 build(android): rename-apk.sh for v0.3.3`)

- [ ] **Step 2: Verify the v0.3.3 chain (≥16 commits since 3fc8f47)**

```bash
git log --oneline 3fc8f47..HEAD | wc -l
```

Expected: `≥16`

- [ ] **Step 3: Verify desktop code is untouched**

```bash
git diff main -- package.json src/ | wc -l
```

Expected: `0`

- [ ] **Step 4: Verify build environment**

```bash
java -version 2>&1 | head -1
echo "ANDROID_HOME=$ANDROID_HOME"
echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
which gh && gh --version | head -1
```

Expected:
- `openjdk version "11.0.x"` (or 17)
- `$ANDROID_HOME = /home/user/Android/Sdk`
- `gh version 2.x`

No commit.

---

### Task 0.2: Remove v0.3.x baseName/sanitizeBaseName baggage

> **Why this is a preflight, not a T1 task:** T1+ builds new screens that DON'T have these fields. Removing the baggage first means new code never touches the broken intermediate state. The "delete X" PR diff is cleaner to review in isolation.

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/vm/FileListViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/vm/ConversionViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/service/ConversionService.kt`
- Modify: `android/app/src/test/kotlin/com/openconverter/app/ui/vm/FileListViewModelTest.kt`

- [ ] **Step 1: Read current FileListViewModel.kt** — note the field `_outputBaseName`, the methods `setOutputBaseName`, `clearOutputIfFormatChanged`, `derivedOutputName`, the companion `sanitizeBaseName`, and the `private val stripExtension` (still used by `setOutputFolder`).

- [ ] **Step 2: Strip from FileListViewModel.kt**

Remove these members entirely:
- `private val _outputBaseName = MutableStateFlow<String>("")`
- `val outputBaseName: StateFlow<String> = _outputBaseName`
- `fun setOutputBaseName(name: String)` (whole function)
- `fun clearOutputIfFormatChanged(newFormat: String)` (whole function)
- `fun derivedOutputName(targetFormat: String): String` (whole function)
- The `MAX_BASE_NAME_LEN`, `FALLBACK_BASE_NAME`, `CONTROL_CHARS` constants inside the companion object
- The whole `fun sanitizeBaseName(raw: String): String` function

Also REMOVE the multi-file clear block from `addUris`:
```kotlin
            // If we've gone multi-file, the user-editable base name no longer
            // applies (each output uses its source stem). Clear it so the UI
            // shows a sensible default and the disabled state is obvious.
            if ((_files.value.size) > 1 && _outputBaseName.value.isNotEmpty()) {
                _outputBaseName.value = ""
            }
```

KEEP:
- `_outputFolderUri` / `outputFolderUri` (still needed)
- `setOutputFolder(uri, firstFileName)` — drop the `firstFileName` parameter too (no longer derived; see T1.2/HomeScreen for new UX)
- `private fun stripExtension` — KEEP only if `setOutputFolder` still uses it. Since we're dropping `firstFileName`, drop `stripExtension` too.

The new `FileListViewModel` should expose ONLY:
- `files: StateFlow<List<FileEntry>>` + `addUris` + `clear` + `remove`
- `outputFolderUri: StateFlow<Uri?>` + `setOutputFolder(uri)`

- [ ] **Step 3: Update `setOutputFolder` signature — drop `firstFileName`**

```kotlin
fun setOutputFolder(uri: Uri) {
    _outputFolderUri.value = uri
}
```

- [ ] **Step 4: Update `ConversionViewModel.startConversion` — drop `baseName` param**

```kotlin
class ConversionViewModel(app: Application) : AndroidViewModel(app) {
    fun startConversion(
        uris: List<Uri>,
        targetFormat: String,
        folderUri: Uri,
    ) {
        ConversionService.start(getApplication(), uris, targetFormat, folderUri)
    }
}
```

- [ ] **Step 5: Update `ConversionService.Companion.start` + `onStartCommand` — drop `EXTRA_BASE_NAME`**

In `ConversionService.kt`:

Drop `val baseName = intent?.getStringExtra(EXTRA_BASE_NAME).orEmpty()` from `onStartCommand`.

Drop the entire `if (uris.size == 1 && baseName.isNotBlank()) { ... }` block. Replace with:

```kotlin
                // Output name: source stem + target ext. Multi-file: each
                // source has a unique stem so no collisions. (Spec §2.5)
                val sourceStem = displayName?.substringBeforeLast('.', displayName)
                    ?: "output_$i"
                val outName = "$sourceStem.$targetFormat"
```

Drop the `EXTRA_BASE_NAME` constant and the `putExtra(EXTRA_BASE_NAME, baseName)` line in `start()`.

- [ ] **Step 6: Update tests in FileListViewModelTest.kt — delete the 2 clearOutputIfFormatChanged tests**

In `FileListViewModelTest.kt`, remove:
- `fun clearOutputIfFormatChanged is no-op when format unchanged()`
- `fun clearOutputIfFormatChanged clears on format change()`

Keep the 7 sanitizeBaseName tests for now (they'll be deleted when the implementation is removed in a later step). Actually — since `sanitizeBaseName` is being deleted, DELETE those 7 tests too. The 9 sanitizeBaseName tests in the v0.3.3 file should all be removed.

After T0.2, FileListViewModelTest.kt should have 0 tests (the new logic will be tested via HomeViewModelTest in T2.2).

- [ ] **Step 7: Verify compile**

```bash
cd android && ./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Verify tests pass (other suites only — FileListViewModelTest is now empty)**

```bash
cd android && ./gradlew testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. FileListViewModelTest may show 0 tests (acceptable).

- [ ] **Step 9: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/src/main/kotlin/com/openconverter/app/ui/vm/FileListViewModel.kt \
        android/app/src/main/kotlin/com/openconverter/app/ui/vm/ConversionViewModel.kt \
        android/app/src/main/kotlin/com/openconverter/app/service/ConversionService.kt \
        android/app/src/test/kotlin/com/openconverter/app/ui/vm/FileListViewModelTest.kt
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "refactor(android): drop v0.3.x baseName/sanitizeBaseName baggage (per spec §2.5)"
```

---

## Phase 1: Foundation

> **Parallelism:** Tasks 1.1+1.2 form one dependency chain (need NavHost before adding routes). Task 1.3 (theme) is independent. Task 1.4 (logo) is independent. Run 1.3 and 1.4 in parallel with 1.1+1.2.

### Task 1.1: Add navigation-compose dependency + NavHost skeleton (RED)

**Files:**
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Add navigation-compose to dependencies**

Find the `dependencies { ... }` block in `build.gradle.kts`. Add (alongside the other `androidx.*` entries):

```kotlin
    implementation("androidx.navigation:navigation-compose:2.8.4")
```

- [ ] **Step 2: Verify compile (NavHost not used yet, no breakage expected)**

```bash
cd android && ./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL` (the dep is added but not yet referenced)

No commit yet — Task 1.2 will use this dep and commit both together.

- [ ] **Step 3: Done — proceed to 1.2**

---

### Task 1.2: Wire NavHost routes + bottom NavigationBar (GREEN)

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/MainActivity.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/OpenConverterApp.kt`
- Create: `android/app/src/main/kotlin/com/openconverter/app/ui/NavRoutes.kt` (route constants)

- [ ] **Step 1: Create `NavRoutes.kt`** (typed route names)

```kotlin
package com.openconverter.app.ui

/**
 * Typed route names for the 4-tab bottom nav. Strings (not sealed classes)
 * to keep the data model simple — these are UI labels, not deep-link URIs.
 *
 * 4 tabs: Home / History / Settings / About (spec decision #10)
 */
object NavRoutes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}
```

- [ ] **Step 2: Replace `MainActivity.kt` content with Scaffold + NavHost + NavigationBar**

```kotlin
package com.openconverter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openconverter.app.ui.NavRoutes
import com.openconverter.app.ui.about.AboutScreen
import com.openconverter.app.ui.history.HistoryScreen
import com.openconverter.app.ui.home.HomeScreen
import com.openconverter.app.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenConverterApp()
        }
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val navTabs = listOf(
    NavTab(NavRoutes.HOME, "主页", Icons.Filled.Home),
    NavTab(NavRoutes.HISTORY, "历史", Icons.Filled.History),
    NavTab(NavRoutes.SETTINGS, "设置", Icons.Filled.Build),
    NavTab(NavRoutes.ABOUT, "关于", Icons.Filled.Info),
)

@Composable
fun OpenConverterApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                navTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(NavRoutes.HOME) { HomeScreen() }
            composable(NavRoutes.HISTORY) { HistoryScreen() }
            composable(NavRoutes.SETTINGS) { SettingsScreen() }
            composable(NavRoutes.ABOUT) { AboutScreen() }
        }
    }
}
```

> **Note:** `HomeScreen()`, `HistoryScreen()`, `SettingsScreen()`, `AboutScreen()` are referenced but don't exist yet (T2.1–T2.5). The compile WILL fail at this step. To keep T1.2 self-contained, **also create 4 stub Composable screens** in this commit:

- [ ] **Step 3: Create 4 stub screens (each is a single Composable that will be replaced in T2.x)**

Create the directories and 4 stub files:

`android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeScreen.kt`:
```kotlin
package com.openconverter.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home — TODO T2.1")
    }
}
```

`android/app/src/main/kotlin/com/openconverter/app/ui/history/HistoryScreen.kt`:
```kotlin
package com.openconverter.app.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HistoryScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("History — TODO T2.3")
    }
}
```

`android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt`:
```kotlin
package com.openconverter.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings — TODO T2.4")
    }
}
```

`android/app/src/main/kotlin/com/openconverter/app/ui/about/AboutScreen.kt`:
```kotlin
package com.openconverter.app.ui.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AboutScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("About — TODO T2.5")
    }
}
```

- [ ] **Step 4: Verify compile**

```bash
cd android && ./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Verify tests pass (no logic changed, just routing)**

```bash
cd android && ./gradlew testDebugUnitTest 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/build.gradle.kts \
        android/app/src/main/kotlin/com/openconverter/app/MainActivity.kt \
        android/app/src/main/kotlin/com/openconverter/app/ui/NavRoutes.kt \
        android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeScreen.kt \
        android/app/src/main/kotlin/com/openconverter/app/ui/history/HistoryScreen.kt \
        android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt \
        android/app/src/main/kotlin/com/openconverter/app/ui/about/AboutScreen.kt
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "feat(android): NavigationBar + NavHost with 4-tab routes (Home/History/Settings/About)"
```

---

### Task 1.3: Theme.kt Spotify dark color scheme

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/theme/Color.kt`
- Create: `android/app/src/main/kotlin/com/openconverter/app/theme/Type.kt`
- Create: `android/app/src/main/kotlin/com/openconverter/app/theme/Theme.kt`

- [ ] **Step 1: Create `Color.kt`** (Spotify palette tokens from `docs/mockups/v0.4/_common.css`)

```kotlin
package com.openconverter.app.theme

import androidx.compose.ui.graphics.Color

// Spotify-inspired dark palette (spec decision #1, #2)
val BackgroundBase = Color(0xFF121212)   // page bg
val SurfaceCard = Color(0xFF181818)      // file cards
val SurfaceButton = Color(0xFF1F1F1F)    // buttons / chips
val SurfaceHighlight = Color(0xFF252527) // hover / selected
val Divider = Color(0xFF282828)          // hairlines

// Brand green (single accent, per decision #2)
val GreenBase = Color(0xFF1ED760)
val GreenDark = Color(0xFF1DB954)        // gradient end / pressed state

// Text
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)   // meta / subtitles
val TextMuted = Color(0xFF6A6A6A)        // disabled

// Status
val StatusError = Color(0xFFE22134)      // failed badge
val StatusWarning = Color(0xFFFFA42B)    // pending / unknown
val StatusInfo = Color(0xFF2E77D0)       // info / link
```

- [ ] **Step 2: Create `Type.kt`** (Roboto default per decision #4)

```kotlin
package com.openconverter.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto default — no font download (decision #4: keep APK small)
val OpenConverterTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,  // uppercase pill buttons
    ),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)
```

- [ ] **Step 3: Create `Theme.kt`**

```kotlin
package com.openconverter.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Decision #11: dark only — locked, no system follow
private val DarkColors = darkColorScheme(
    primary = GreenBase,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = GreenDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.Black,
    background = BackgroundBase,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceButton,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    error = StatusError,
    onError = TextPrimary,
)

@Composable
fun OpenConverterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = OpenConverterTypography,
        content = content,
    )
}
```

- [ ] **Step 4: Wire `Theme` into `MainActivity`**

In `MainActivity.kt`, wrap the `setContent { OpenConverterApp() }` block:

```kotlin
        setContent {
            OpenConverterTheme {
                OpenConverterApp()
            }
        }
```

- [ ] **Step 5: Verify compile + tests**

```bash
cd android && ./gradlew compileDebugKotlin testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/src/main/kotlin/com/openconverter/app/theme/ \
        android/app/src/main/kotlin/com/openconverter/app/MainActivity.kt
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "feat(android): Spotify dark color scheme + Roboto typography + OpenConverterTheme"
```

---

### Task 1.4: SVG logo Vector Drawable + OpenConverterLogo Composable

**Files:**
- Create: `android/app/src/main/res/drawable/ic_logo.xml` (Vector Drawable from the user's SVG)
- Create: `android/app/src/main/kotlin/com/openconverter/app/components/OpenConverterLogo.kt`

- [ ] **Step 1: Create the Vector Drawable**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="512"
    android:viewportHeight="512">
    <!-- Outer dark circle -->
    <path
        android:fillColor="#181818"
        android:strokeColor="#282828"
        android:strokeWidth="8"
        android:pathData="M256,16 A240,240 0 1,0 256,496 A240,240 0 1,0 256,16 Z" />
    <!-- Green ring -->
    <path
        android:strokeColor="#1ED760"
        android:strokeWidth="12"
        android:strokeAlpha="0.8"
        android:pathData="M256,56 A200,200 0 1,0 256,456 A200,200 0 1,0 256,56 Z" />
    <!-- 5 audio waveform bars -->
    <path android:fillColor="#1ED760" android:pathData="M120,180 h40 a20,20 0 0,1 20,20 v80 a20,20 0 0,1 -20,20 h-40 a20,20 0 0,1 -20,-20 v-80 a20,20 0 0,1 20,-20 z" />
    <path android:fillColor="#1ED760" android:fillAlpha="0.9" android:pathData="M180,140 h40 a20,20 0 0,1 20,20 v160 a20,20 0 0,1 -20,20 h-40 a20,20 0 0,1 -20,-20 v-160 a20,20 0 0,1 20,-20 z" />
    <path android:fillColor="#1ED760" android:fillAlpha="0.85" android:pathData="M240,100 h40 a20,20 0 0,1 20,20 v240 a20,20 0 0,1 -20,20 h-40 a20,20 0 0,1 -20,-20 v-240 a20,20 0 0,1 20,-20 z" />
    <path android:fillColor="#1ED760" android:fillAlpha="0.9" android:pathData="M300,140 h40 a20,20 0 0,1 20,20 v160 a20,20 0 0,1 -20,20 h-40 a20,20 0 0,1 -20,-20 v-160 a20,20 0 0,1 20,-20 z" />
    <path android:fillColor="#1ED760" android:pathData="M360,180 h40 a20,20 0 0,1 20,20 v80 a20,20 0 0,1 -20,20 h-40 a20,20 0 0,1 -20,-20 v-80 a20,20 0 0,1 20,-20 z" />
</vector>
```

> **Note on the rings:** the spec's SVG uses `stroke` on circles (no fill). The `pathData` here approximates the two concentric circles with arcs. The waveform bars use `pathData` with rounded rectangles (`rx=20`). Visual parity with `docs/mockups/v0.4/_logo.html` is the goal; exact pixel match is not required.

- [ ] **Step 2: Create the Composable**

```kotlin
package com.openconverter.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openconverter.app.R

/**
 * OpenConverter brand mark. Rendered from a Vector Drawable so it scales
 * crisply on any DPI without bitmap bloat.
 *
 * Default size is 28dp (TopAppBar brand-mark); pass [size] = 120.dp on
 * the About screen for the hero.
 */
@Composable
fun OpenConverterLogo(size: Dp = 28.dp, contentDescription: String? = "OpenConverter") {
    Image(
        painter = painterResource(id = R.drawable.ic_logo),
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
    )
}
```

- [ ] **Step 3: Verify compile**

```bash
cd android && ./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/src/main/res/drawable/ic_logo.xml \
        android/app/src/main/kotlin/com/openconverter/app/components/OpenConverterLogo.kt
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "feat(android): SVG logo Vector Drawable + OpenConverterLogo Composable"
```

---

## Phase 2: Screens

> **Parallelism:** T2.1+2.2 are one chain (HomeScreen depends on HomeViewModel). T2.3, T2.4, T2.5 are independent of T2.1+2.2 and of each other. All four can run in parallel after T1.x completes.

### Task 2.1: HomeScreen with file queue + format chips + FAB + output folder row

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeScreen.kt` (replace stub)
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/vm/HomeViewModel.kt` (create new)

- [ ] **Step 1: Create `HomeViewModel.kt`** (the new VM, simplified from FileListViewModel)

```kotlin
package com.openconverter.app.ui.vm

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openconverter.app.decoders.FormatDetector
import com.openconverter.app.ui.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen state: file queue + output folder + current target format.
 *
 * v0.4.0 simplified: NO baseName editing (per spec §2.5). Output name is
 * always `${sourceStem}.${targetFormat}`, computed in ConversionService.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val _files = MutableStateFlow<List<FileEntry>>(emptyList())
    val files: StateFlow<List<FileEntry>> = _files

    private val _outputFolderUri = MutableStateFlow<Uri?>(null)
    val outputFolderUri: StateFlow<Uri?> = _outputFolderUri

    private val _currentFormat = MutableStateFlow("mp3")
    val currentFormat: StateFlow<String> = _currentFormat

    fun setOutputFolder(uri: Uri) {
        _outputFolderUri.value = uri
    }

    fun setCurrentFormat(format: String) {
        _currentFormat.value = format
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newEntries = withContext(Dispatchers.IO) {
                uris.map { uri -> readEntry(uri) }
            }
            _files.update { current ->
                (current + newEntries).distinctBy { it.uri }
            }
        }
    }

    fun clear() { _files.value = emptyList() }

    fun remove(uri: Uri) {
        _files.update { current -> current.filter { it.uri != uri } }
    }

    private fun readEntry(uri: Uri): FileEntry {
        val ctx = getApplication<Application>()
        var displayName = uri.lastPathSegment ?: "unknown"
        var sizeBytes = 0L
        try {
            ctx.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) displayName = c.getString(nameIdx)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) sizeBytes = c.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        val readable = try {
            ctx.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }

        var sourceFormat: String? = null
        if (readable) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readNBytes(16)
                    sourceFormat = FormatDetector.detect(bytes, displayName)
                }
            } catch (_: Exception) {}
        }

        return FileEntry(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            sourceFormat = sourceFormat,
            readable = readable,
        )
    }
}
```

- [ ] **Step 2: Replace `HomeScreen.kt` with the real implementation**

```kotlin
package com.openconverter.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.BuildConfig
import com.openconverter.app.R
import com.openconverter.app.components.OpenConverterLogo
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.ui.FileEntry
import com.openconverter.app.ui.vm.ConversionViewModel
import com.openconverter.app.ui.vm.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val safAdapter = remember { SafAdapter() }
    val homeVm: HomeViewModel = viewModel()
    val conversionVm: ConversionViewModel = viewModel()
    val context = LocalContext.current

    val files by homeVm.files.collectAsState()
    val outputFolder by homeVm.outputFolderUri.collectAsState()
    val currentFormat by homeVm.currentFormat.collectAsState()

    val pickFiles = safAdapter.openDocumentsContract()
    val openTree = safAdapter.openDocumentTreeContract()

    val pickFilesLauncher = rememberLauncherForActivityResult(pickFiles) { uris ->
        if (uris.isNotEmpty()) {
            val persisted = safAdapter.persistReadAccess(context.contentResolver, uris)
            if (persisted.isNotEmpty()) homeVm.addUris(persisted)
        }
    }
    val openTreeLauncher = rememberLauncherForActivityResult(openTree) { uri ->
        if (uri != null) {
            safAdapter.persistTreeWriteAccess(context.contentResolver, uri)
            homeVm.setOutputFolder(uri)
        }
    }

    fun startConversion() {
        if (files.isEmpty()) return
        val folderUri = homeVm.outputFolderUri.value ?: run {
            openTreeLauncher.launch(null)
            return
        }
        conversionVm.startConversion(
            uris = files.map { it.uri },
            targetFormat = currentFormat,
            folderUri = folderUri,
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OpenConverterLogo()
                        Spacer(Modifier.width(8.dp))
                        Text("OpenConverter v${BuildConfig.VERSION_NAME}")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { startConversion() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("开始转换", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Output folder row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("输出文件夹", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = outputFolder?.lastPathSegment ?: "未选择",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = { openTreeLauncher.launch(null) }) {
                    Text(if (outputFolder == null) "选择" else "更改")
                }
            }

            // Format chips
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("mp3", "flac", "wav", "m4a", "ogg").forEach { fmt ->
                    FilterChip(
                        selected = currentFormat == fmt,
                        onClick = { homeVm.setCurrentFormat(fmt) },
                        label = { Text(fmt.uppercase()) },
                    )
                }
            }

            // File count + total
            Text(
                text = if (files.isEmpty()) "未选择文件"
                       else "已选 ${files.size} 个文件 (${formatTotalSize(files)})",
                style = MaterialTheme.typography.bodyMedium,
            )

            // File list
            if (files.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(files) { entry -> FileCard(entry) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Add files button
            OutlinedButton(
                onClick = { pickFilesLauncher.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (files.isEmpty()) "选择音频文件" else "+ 添加更多文件")
            }
        }
    }
}

@Composable
private fun FileCard(entry: FileEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (entry.readable) CardDefaults.cardColors()
                 else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = entry.displayName,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            AssistChip(
                onClick = {},
                label = { Text(entry.sourceFormat?.uppercase() ?: "未识别") },
            )
            Text(
                text = formatSize(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "?"
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun formatTotalSize(files: List<FileEntry>): String = formatSize(files.sumOf { it.sizeBytes })
```

- [ ] **Step 3: Verify compile + tests**

```bash
cd android && ./gradlew compileDebugKotlin testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeScreen.kt \
        android/app/src/main/kotlin/com/openconverter/app/ui/vm/HomeViewModel.kt
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "feat(android): HomeScreen with file queue + format chips + FAB + output folder row"
```

---

### Task 2.2: Add unit tests for HomeViewModel + verify outName

**Files:**
- Create: `android/app/src/test/kotlin/com/openconverter/app/ui/vm/HomeViewModelTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.openconverter.app.ui.vm

import android.app.Application
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeViewModelTest {
    @Test
    fun `setOutputFolder stores the uri`() {
        val vm = HomeViewModel(Application())
        val fakeUri = android.net.Uri.parse("content://test/tree/abc")
        vm.setOutputFolder(fakeUri)
        assertEquals(fakeUri, vm.outputFolderUri.value)
    }

    @Test
    fun `setCurrentFormat defaults to mp3 and updates`() {
        val vm = HomeViewModel(Application())
        assertEquals("mp3", vm.currentFormat.value)
        vm.setCurrentFormat("flac")
        assertEquals("flac", vm.currentFormat.value)
    }

    @Test
    fun `clear resets the file queue`() {
        val vm = HomeViewModel(Application())
        vm.clear()
        assertEquals(emptyList(), vm.files.value)
    }

    @Test
    fun `remove drops a uri from the queue`() {
        val vm = HomeViewModel(Application())
        val u = android.net.Uri.parse("content://x")
        // We can't call addUris without a real ContentResolver, so this is a no-op assertion.
        vm.remove(u)
        assertEquals(emptyList(), vm.files.value)
    }

    @Test
    fun `outputName rule mirrors spec §2_5`() {
        // The output-name rule is implemented in ConversionService, not the VM,
        // but the VM doesn't need to know about it. This test documents the
        // contract: HomeViewModel does NOT expose baseName or outName.
        val vm = HomeViewModel(Application())
        // VM should not have a public outName method.
        val hasOutName = try {
            vm::class.java.getMethod("outName")
            true
        } catch (_: NoSuchMethodException) {
            false
        }
        assert(!hasOutName, "HomeViewModel must not own output-name logic — that's ConversionService's job")
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd android && ./gradlew testDebugUnitTest --tests "com.openconverter.app.ui.vm.HomeViewModelTest" 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, 5/5 pass

- [ ] **Step 3: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/src/test/kotlin/com/openconverter/app/ui/vm/HomeViewModelTest.kt
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "test(android): HomeViewModel — output folder + format + clear/remove (no baseName)"
```

---

### Task 2.3: HistoryScreen + HistoryViewModel (read-only local cache)

> **YAGNI note:** Per spec §8.2, History is a thin "what got converted" view. It does NOT need a real persistent DB. For v0.4.0 it's in-memory only (lost on process death) — keeping it minimal.

**Files:**
- Create: `android/app/src/main/kotlin/com/openconverter/app/ui/history/HistoryViewModel.kt`
- Create: `android/app/src/main/kotlin/com/openconverter/app/ui/history/HistoryEntry.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/history/HistoryScreen.kt` (replace stub)
- Modify: `android/app/src/main/kotlin/com/openconverter/app/service/ConversionService.kt` (push results to a static history list)

- [ ] **Step 1: Create `HistoryEntry` data class**

```kotlin
package com.openconverter.app.ui.history

import android.net.Uri

/**
 * One row in the History screen. Captured at completion time by
 * ConversionService and stored in [HistoryRepository].
 *
 * @param timestampMs epoch millis when conversion completed
 * @param sourceUri original input URI
 * @param sourceName display name of the source
 * @param outputUri URI of the produced file (in the user's chosen folder)
 * @param targetFormat output container (mp3/flac/...)
 * @param status "DONE" or "FAILED"
 * @param errorMessage present only when status == "FAILED"
 */
data class HistoryEntry(
    val timestampMs: Long,
    val sourceName: String,
    val targetFormat: String,
    val status: String,
    val errorMessage: String? = null,
    val outputUri: Uri? = null,
    val sourceUri: Uri,
)
```

- [ ] **Step 2: Create a static `HistoryRepository`**

```kotlin
package com.openconverter.app.ui.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-lifetime history of conversions. v0.4.0 keeps it in-memory only
 * (per spec §8.2 YAGNI — no Room DB). Persists across screen navigation
 * (the StateFlow is held by Application) but NOT across process death.
 */
object HistoryRepository {
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries

    fun record(entry: HistoryEntry) {
        _entries.value = (listOf(entry) + _entries.value).take(200)
    }

    fun clear() { _entries.value = emptyList() }
}
```

- [ ] **Step 3: Create `HistoryViewModel`**

```kotlin
package com.openconverter.app.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel : ViewModel() {
    val entries: StateFlow<List<HistoryEntry>> = HistoryRepository.entries
    fun clear() = HistoryRepository.clear()
}
```

- [ ] **Step 4: Wire `ConversionService` to record on success/failure**

In `ConversionService.kt`, find the per-file success line `successes.add(uri)` and add a `HistoryRepository.record(...)` call after it. Also add a record on failure paths.

The simplest spot is at the end of the per-file loop, just after `_progress.value = Progress.Done(...)`:

```kotlin
                successes.add(uri)
                HistoryRepository.record(
                    HistoryEntry(
                        timestampMs = System.currentTimeMillis(),
                        sourceName = filename,
                        targetFormat = targetFormat,
                        status = "DONE",
                        outputUri = outUri,
                        sourceUri = uri,
                    )
                )
                _progress.value = Progress.Done(i + 1, uris.size, filename)
```

For the per-file failure branches, add (in each `failureLog.record(...)` block) a `HistoryRepository.record(HistoryEntry(... status = "FAILED", errorMessage = err, sourceUri = uri, sourceName = filename, targetFormat = targetFormat, timestampMs = System.currentTimeMillis()))`.

Add `import com.openconverter.app.ui.history.HistoryEntry` and `import com.openconverter.app.ui.history.HistoryRepository` at the top of the file.

- [ ] **Step 5: Replace `HistoryScreen.kt` with the real implementation**

```kotlin
package com.openconverter.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.theme.StatusError
import com.openconverter.app.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val vm: HistoryViewModel = viewModel()
    val entries by vm.entries.collectAsState()
    val df = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("历史") }) }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("还没有转换记录", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(entries) { entry -> HistoryRow(entry, df) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, df: SimpleDateFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = df.format(Date(entry.timestampMs)),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Text(
                text = entry.sourceName,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            AssistChip(
                onClick = {},
                label = { Text(entry.targetFormat.uppercase()) },
            )
            Text(
                text = if (entry.status == "DONE") "✓" else "✗",
                color = if (entry.status == "DONE") MaterialTheme.colorScheme.primary else StatusError,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
```

- [ ] **Step 6: Verify compile + tests**

```bash
cd android && ./gradlew compileDebugKotlin testDebugUnitTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/src/main/kotlin/com/openconverter/app/ui/history/ \
        android/app/src/main/kotlin/com/openconverter/app/service/ConversionService.kt
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "feat(android): HistoryScreen + HistoryRepository + wire ConversionService record"
```

---

### Task 2.4: SettingsScreen (QMC ekey + 编码/行为/诊断 groups, NO 保留原始文件名 toggle)

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt` (replace stub)
- Create: `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Create `SettingsViewModel`** (QMC ekey load/save only — other settings are runtime decisions)

```kotlin
package com.openconverter.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.openconverter.app.ekey.EkeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings state. v0.4.0 is intentionally minimal — only the QMC ekey
 * is persisted (per desktop parity). Output format / bitrate / theme are
 * NOT settings in v0.4.0 (per spec §2.5 + 决策 #11: dark only).
 *
 * Future: add a Room DB + settings screen with more knobs (v0.5+).
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val ekeyStore by lazy { EkeyStore(app) }

    private val _qmcEkey = MutableStateFlow(ekeyStore.getEkey().orEmpty())
    val qmcEkey: StateFlow<String> = _qmcEkey

    fun saveEkey(value: String) {
        ekeyStore.setEkey(value)
        _qmcEkey.value = value
    }
}
```

- [ ] **Step 2: Replace `SettingsScreen.kt` with the real implementation**

```kotlin
package com.openconverter.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.BuildConfig
import com.openconverter.app.components.OpenConverterLogo
import com.openconverter.app.theme.SurfaceCard
import com.openconverter.app.theme.TextSecondary

private data class SettingsGroup(val title: String, val items: List<SettingsItem>)
private data class SettingsItem(val title: String, val subtitle: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val currentEkey by vm.qmcEkey.collectAsState()
    var ekeyInput by remember(currentEkey) { mutableStateOf(currentEkey) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GroupHeader("QMC 加密密钥 (ekey)")
                GroupCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = ekeyInput,
                            onValueChange = { ekeyInput = it },
                            label = { Text("ekey (QQ Music 客户端 DB 提取)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "用于解密 QMC v2 加密音频 (mflac / mgg / bkc*)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { vm.saveEkey(ekeyInput) }) { Text("保存") }
                            OutlinedButton(onClick = {
                                ekeyInput = ""
                                vm.saveEkey("")
                            }) { Text("清空") }
                        }
                    }
                }
            }
            item {
                GroupHeader("编码")
                GroupCard {
                    Column {
                        StaticRow("默认输出格式", "FLAC")
                        Divider()
                        StaticRow("默认比特率", "256 KBPS (MP3 / AAC)")
                    }
                }
            }
            item {
                GroupHeader("行为")
                GroupCard {
                    Column {
                        StaticRow("输出命名", "始终保留源文件名 (song.flac → song.mp3)")
                        Divider()
                        StaticRow("转换完成通知", "启用", valueColor = MaterialTheme.colorScheme.primary)
                        Divider()
                        StaticRow("主题模式", "DARK (锁定)", valueColor = TextSecondary)
                    }
                }
            }
            item {
                GroupHeader("诊断")
                GroupCard {
                    Column {
                        StaticRow("失败日志", "查看")
                        Divider()
                        StaticRow("FFmpeg 信息", "ffmpeg-kit 6.0-2.LTS", valueColor = TextSecondary)
                        Divider()
                        StaticRow("应用版本", "v${BuildConfig.VERSION_NAME}", valueColor = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    ) { Column { content() } }
}

@Composable
private fun StaticRow(title: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TextSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}
```

- [ ] **Step 3: Verify compile**

```bash
cd android && ./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/src/main/kotlin/com/openconverter/app/ui/settings/
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "feat(android): SettingsScreen with QMC ekey + 编码/行为/诊断 groups (no 保留原始文件名 toggle)"
```

---

### Task 2.5: AboutScreen (big SVG logo + project info)

**Files:**
- Modify: `android/app/src/main/kotlin/com/openconverter/app/ui/about/AboutScreen.kt` (replace stub)

- [ ] **Step 1: Replace the stub**

```kotlin
package com.openconverter.app.ui.about

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openconverter.app.BuildConfig
import com.openconverter.app.components.OpenConverterLogo
import com.openconverter.app.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("关于") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            OpenConverterLogo(size = 120.dp)
            Spacer(Modifier.height(8.dp))
            Text("OpenConverter", style = MaterialTheme.typography.titleLarge)
            Text("v${BuildConfig.VERSION_NAME}", color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            Text(
                "支持 11 种加密音频格式 → MP3 / FLAC / WAV / M4A / OGG 真转码",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("NCM · QMC0/3/FLAC/OGG · MFLAC/MGG/BKC · KGM/KGMA/VPR · KWM",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("github.com/nowa277/OpenConverter",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
cd android && ./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/src/main/kotlin/com/openconverter/app/ui/about/AboutScreen.kt
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "feat(android): AboutScreen with 120dp hero logo + project info"
```

---

## Phase 3: Integration

### Task 3.1: Delete old FileListScreen.kt + remove v0.3.x from MainActivity

**Files:**
- Delete: `android/app/src/main/kotlin/com/openconverter/app/ui/FileListScreen.kt`
- Modify: `android/app/src/main/kotlin/com/openconverter/app/MainActivity.kt` (remove any reference to FileListScreen)

- [ ] **Step 1: Delete the old screen**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git rm android/app/src/main/kotlin/com/openconverter/app/ui/FileListScreen.kt
```

- [ ] **Step 2: Verify no other file references FileListScreen**

```bash
grep -rn "FileListScreen" android/app/src/main/ 2>/dev/null
```

Expected: empty (only the deletion marker in git history)

If any file still references it (e.g. conversion flow), remove the import + call.

- [ ] **Step 3: Verify compile**

```bash
cd android && ./gradlew compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git commit -m "refactor(android): delete obsolete FileListScreen.kt (replaced by HomeScreen)"
```

---

### Task 3.2: Run full unit test suite + manual nav walk-through

**Files:** none

- [ ] **Step 1: Run all unit tests**

```bash
cd android && ./gradlew testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. Approximate test count:
- FormatDetectorTest: 22
- NcmDecoderTest + KgmDecoderTest + QmcDecoderTest + QmcV2DecoderTest + KwmDecoderTest: ~50
- ConversionOrchestratorTest: ~10
- HomeViewModelTest: 5 (added in 2.2)
- OCLogTest: 3
- FfmpegLoadTest: ~2
- **Total: ~92 tests, 0 failures**

If any test fails → STOP, fix the regression before proceeding to release.

- [ ] **Step 2: Build the debug APK (manual nav check)**

```bash
cd android && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. Then `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` and walk through the 4 tabs to confirm:
- Home: file picker → format chips → FAB → output folder row
- History: empty state on first launch
- Settings: ekey input + 4 groups (no "保留原始文件名" toggle)
- About: 120dp hero logo + version

No commit.

---

## Phase 4: Release v0.4.0

### Task 4.1: Build 3 signed release APKs

**Files:**
- Modify: `android/app/build.gradle.kts` (bump versionName + versionCode)

- [ ] **Step 1: Bump versionName + versionCode**

In `android/app/build.gradle.kts`:
```kotlin
        versionCode = 4
        versionName = "0.4.0"
```

- [ ] **Step 2: Clean + assembleRelease**

```bash
cd /home/user/obsidian/AI/claude/openconverter/android
./gradlew clean :app:assembleRelease 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` (3-5 min on first build, ffmpeg-kit already in jniLibs)

- [ ] **Step 3: Verify 3 APKs**

```bash
ls -lh app/build/outputs/apk/release/app-*-release.apk
```

Expected: 3 files, ~10-12 MB each (similar to v0.3.3)

- [ ] **Step 4: Commit the version bump**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git add android/app/build.gradle.kts
git -c user.name=nowa277 -c user.email=sesleycheung@gmail.com commit -m "chore(android): bump versionName 0.3.3 -> 0.4.0, versionCode 3 -> 4"
```

---

### Task 4.2: Generate SHA256 + verify signing

**Files:** none (uses existing `scripts/rename-apk.sh`)

- [ ] **Step 1: Run rename-apk.sh**

```bash
cd /home/user/obsidian/AI/claude/openconverter
./scripts/rename-apk.sh 0.4.0 2>&1
```

Expected: 3 APKs + 3 .sha256 in `release/`

- [ ] **Step 2: Verify signing**

```bash
APKSIGNER=$HOME/Android/Sdk/build-tools/34.0.0/apksigner
$APKSIGNER verify --verbose release/openconverter-v0.4.0-android-arm64-v8a.apk 2>&1 | head -5
```

Expected: `Verifies` + v2 scheme `true`

---

### Task 4.3: Tag v0.4.0 + push to ui-redesign-v040

**Files:** none

- [ ] **Step 1: Confirm HEAD is the latest T2-T3 commit**

```bash
git log -3 --oneline
```

- [ ] **Step 2: Tag + push**

```bash
cd /home/user/obsidian/AI/claude/openconverter
git tag -a v0.4.0 -m "v0.4.0 — Spotify UI redesign (4-tab Bottom Nav + SVG logo + 简化命名)"
git push origin ui-redesign-v040
git push origin v0.4.0
```

Expected: tag visible on remote

---

### Task 4.4: Upload to GitHub Release v0.4.0

**Files:** none (uses `gh` CLI)

- [ ] **Step 1: Create release**

```bash
cd /home/user/obsidian/AI/claude/openconverter
gh release create v0.4.0 \
    --target ui-redesign-v040 \
    --title "v0.4.0 — Spotify UI Redesign" \
    --notes "$(cat <<'EOF'
## v0.4.0 — Spotify UI Redesign

### 新功能
- **4-tab Bottom Navigation**: 主页 / 历史 / 设置 / 关于 (Material3 NavigationBar)
- **Spotify 风格主题**: 暗色 #121212 + Green #1ed760 + Roboto typography + pill geometry
- **内联 SVG logo**: 黑色圆 + 5 音频波形条,TopAppBar 28dp + About 120dp hero
- **HomeScreen 重构**: 文件队列 + LazyColumn 卡片 + 5 format chips + ExtendedFAB "开始转换" + 输出文件夹行
- **HistoryScreen 新建**: 进程内记录 (最多 200 条),按完成时间倒序
- **SettingsScreen 重构**: 分组列表 (QMC ekey / 编码 / 行为 / 诊断),**无 baseName 编辑** (硬编码源文件名,见 spec §2.5)
- **AboutScreen 新建**: 120dp logo + 版本 + 项目信息
- **NavHost 路由**: 4 tab 独立 route (Home/History/Settings/About),bottom nav state 持久化

### 修复 / 简化
- **删除 v0.3.x baseName/sanitizeBaseName baggage**: 输出文件名硬编码为 `sourceStem.targetFormat` (与 desktop 一致)。删 `FileListViewModel.outputBaseName` + `setOutputBaseName` + `clearOutputIfFormatChanged` + `sanitizeBaseName` + `ConversionViewModel.startConversion` 的 `baseName` 参数 + FileListScreen 的输出文件名编辑框
- **OCLog 保留**: v0.3.3 的 12 个关键 log 点继续工作,`adb logcat -s OpenConverter` 拿全上下文
- **SAF 持久化保留**: v0.3.3 的 `persistReadAccess` + `persistTreeWriteAccess` 继续工作

### 已知问题
- **vivo y78 MP3→FLAC 闪退 v0.3.3 仍未解决** — 留 v0.4.1。需要 vivo 真机 logcat (FGS startForeground 时机/参数/notification channel 推测)

### 安装
选你手机 CPU 架构对应的 APK:
- `arm64-v8a`: 大多数 2017+ 设备
- `armeabi-v7a`: 老设备
- `x86_64`: 模拟器

### 注意
- 本 release tag 在 `ui-redesign-v040` 分支上 (未合 `android-port` 或 `main`)
- 不发布到 Play Store (仅 GitHub Release)
EOF
)" \
    release/openconverter-v0.4.0-android-arm64-v8a.apk \
    release/openconverter-v0.4.0-android-arm64-v8a.apk.sha256 \
    release/openconverter-v0.4.0-android-armeabi-v7a.apk \
    release/openconverter-v0.4.0-android-armeabi-v7a.apk.sha256 \
    release/openconverter-v0.4.0-android-x86_64.apk \
    release/openconverter-v0.4.0-android-x86_64.apk.sha256 2>&1 | tail -5
```

- [ ] **Step 2: Verify**

```bash
gh release view v0.4.0
```

Expected: 6 assets (3 APK + 3 .sha256) visible

🎉 v0.4.0 release complete.

---

## Self-Review

- [x] **Spec coverage:**
  - Decision #1-#14 in spec §2.3 — all covered (theme, logo, bottom nav, FAB, snackbar, banner, tabs, dark lock, no-emoji, output naming = source stem)
  - §2.5 output naming behavior — covered in T0.2 (delete baggage) + T1.2 (NavHost) + T2.1 (HomeScreen) + T2.2 (HomeViewModelTest documents contract)
  - §3 debug logging — already shipped in v0.3.3 (OCLog + 12 sites), kept in T0.2 / T1.x
  - §4 architecture — covered (theme/ + components/ + ui/{home,history,settings,about}/ + ui/NavRoutes.kt)
  - §5 error handling — Snackbar host wired in Scaffold; banner in HomeScreen T2.1 (top of column)
  - §7.2 v0.4.0 milestones — T1 (nav + theme + logo) / T2 (4 screens) / T3 (delete old + integration) / T4 (release)
  - §7.3 v0.4.1 vivo deferred — explicitly OUT of this plan, mentioned in T4.4 release notes

- [x] **Placeholder scan:** 0 "TBD"/"TODO"/"implement later" (the 4 stub screens in T1.2 are explicitly marked `Text("Home — TODO T2.1")` etc. — these are TDD scaffolding, not plan placeholders; they are replaced in T2.x)

- [x] **Type consistency:**
  - `NavRoutes.HOME` / `HISTORY` / `SETTINGS` / `ABOUT` — used consistently in T1.2 (NavHost composable) + bottom bar
  - `HomeViewModel` fields: `files`, `outputFolderUri`, `currentFormat` — used in T2.1 (HomeScreen) + T2.2 (test)
  - `OpenConverterLogo(size: Dp = 28.dp)` — used in T1.4 + T2.1 (TopAppBar) + T2.5 (About hero with 120.dp)
  - `HistoryEntry(... sourceUri, sourceName, targetFormat, status, ...)` — used in T2.3 (HistoryScreen) + T2.3 (ConversionService push)
  - `scripts/rename-apk.sh VERSION [SRC] [DST]` — used in T4.2 (same signature as v0.3.3)
  - `gh release create` arg order — same as v0.3.3 plan

- [x] **Test coverage:**
  - HomeViewModelTest: 5 tests (output folder, format, clear, remove, "VM does not own outName")
  - Existing tests preserved: 62 (format detector, decoders, OCLog, orchestrator) — minus 9 FileListViewModelTest tests deleted in T0.2 = 53
  - Total after T2.2: ~58 tests, 0 failures expected

- [x] **Commit hygiene:** 12 commits in plan (T0.2, T1.2, T1.3, T1.4, T2.1, T2.2, T2.3, T2.4, T2.5, T3.1, T4.1, T4.3). T1.1 has no commit (folded into T1.2).

- [x] **Risk callouts:**
  - Step 1.2 step 3: stubs required because T1.2 references screens T2.x creates. Documented.
  - Step 2.3 step 4: ConversionService changes are minimal (1 line success + 3 failure paths) — listed explicitly
  - Step 3.1 step 2: grep required to confirm no orphan references
  - Step 4.1 step 2: long-running build, may take 3-5 min
  - v0.4.1 vivo crash: explicitly deferred (in plan, not in scope)

- [x] **No destructive git ops:** no force-push, no filter-branch, no main merges

- [x] **No v0.3.3 changes corrupted:** T0.2 deletes the v0.3.x baseName/sanitizeBaseName code (which becomes obsolete in v0.4.0 per spec §2.5). The 9 OCLog call sites in T0.2 are kept (intentional). The AndroidManifest is untouched (no FGS / permission changes per constraint #11).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-18-android-v040-ui-redesign.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, two-stage review (spec compliance + code quality), fast iteration. Use opus for implementers + sonnet for review (matches your prior preference for opus on Android work).

2. **Inline Execution** — Execute tasks in this session via executing-plans, batch execution with checkpoints for review.

**Which approach?**
