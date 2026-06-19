# Android v1 UI Redesign — Design Spec

> Status: brainstorming output, awaiting user review → then `writing-plans`.

## Goal

Redesign the OpenConverter Android **Home** and **Settings** screens so that:

1. The file **queue is the visual hero** of the Home screen (Layout A).
2. Each **FileCard shows actionable detail**: file size, status, and — on failure — the reason.
3. The **currently selected output folder is always visible** (the original "doesn't show output folder" complaint).
4. **Settings** exposes version, project repo, and a user-authored disclaimer.
5. The project **waveform logo** is used as both the in-app top-bar mark and the **app launcher icon**.
6. **No emoji** anywhere in the UI (strings or status badges).

The conversion engine, FGS service, SAF layer, and decoders are **out of scope** — they already produce files on-device. This redesign only touches the Compose UI, theme assets, the launcher icon, and string resources.

## Confirmed working baseline (do not regress)

On emulator-5554 (x86_64), commit `60f37ab`: NCM→MP3, NCM→FLAC, MP3→FLAC all produce real files in the picked SAF folder. JVM tests 38/0/0. The redesign must keep these green and working.

---

## Decision 1 — Home layout: queue-dominant (Layout A)

The controls (output folder, target format, bitrate) collapse into a **compact summary strip** at the top. The queue fills the remaining vertical space (`Modifier.weight(1f)`). The bottom CTA is sticky.

```
┌─ [波形logo] OpenConverter            Settings ─┐  ← TopAppBar, plain-text action
├────────────────────────────────────────────────┤
│  [+ Add files]                                 │  ← compact outlined button
│  Output:  Music                          ›     │  ← tappable row (folder display name)
│  MP3 · 320k                              ›     │  ← tappable row (format · bitrate)
├────────────────────────────────────────────────┤
│  ┌─ Queue (weight 1f, scrolls) ──────────────┐ │
│  │ sample.ncm        4.2 MB          Done    │ │  ← green badge
│  │ track02.ncm       3.1 MB          67%     │ │  ← progress bar
│  │ track03.ncm       5.0 MB          Failed  │ │  ← red badge
│  │   Invalid URI: content://…                │ │  ← 2nd line, red, 2-line ellipsis
│  └───────────────────────────────────────────┘ │
├────────────────────────────────────────────────┤
│              [ Start conversion ]              │  ← sticky bottom CTA
└────────────────────────────────────────────────┘
```

**Tapping "Output: Music" or "MP3 · 320k"** opens a `ModalBottomSheet` (Material 3) containing:
- a **Pick output folder** button → launches `ACTION_OPEN_DOCUMENT_TREE` (system picker),
- the **format chips** (mp3 / flac / wav / m4a / ogg),
- the **bitrate chips** (128k / 192k / 320k / Lossless),
- an **Apply** button that dismisses the sheet.

When no output folder is set, the row reads `Output: not set — tap to choose` and the CTA is disabled (existing `canStart` logic preserved).

**Why a bottom sheet and not inline chips / a full page:** inline chips cost vertical space the queue needs (contradicts Layout A); a full page is too heavy for two chip rows. `ModalBottomSheet` is the standard M3 component for exactly this — one tap to reveal, one tap to choose, queue stays visible underneath. *If during spec review you'd rather keep chips inline (truer to the old UI, one-tap format change at the cost of queue space), say so — it's a small swap.*

### "+ Add files" placement

Compact outlined button on its own row at the top of the strip (not a FAB — a FAB would collide with the sticky bottom CTA). Launches `OpenMultipleDocuments` as today.

---

## Decision 2 — FileCard fields

One card per file. Fields, left to right / top to bottom:

| Field | Source | Shown in states | Format |
|---|---|---|---|
| File name | `FileEntry.displayName` | all | `maxLines = 1`, ellipsis |
| File size | `FileEntry.sizeBytes` (NEW) | all | human-readable (`4.2 MB`, `812 KB`) |
| Status badge | `FileState` | all | plain text, color-coded (see below) |
| Progress bar | `FileEntry.percent` | Running only | `LinearProgressIndicator` |
| Failure reason | `FileEntry.error` (NEW exposure) | Failed only | 2nd line, red, `maxLines = 2`, ellipsis |

**Status badges — text only, no emoji:**

| State | Badge text | Color |
|---|---|---|
| Pending | `Pending` | `onSurfaceVariant` (grey) |
| Running | `67%` (live percent) | `primary` (green) |
| Done | `Done` | `primary` (green) |
| Failed | `Failed` | `error` (red) |

**Explicitly NOT shown:** the output filename preview (user decision — stem is auto-derived from input, no need to preview).

### File size plumbing (NEW)

`HomeViewModel.setFiles` must resolve each input Uri's size. SAF exposes size via `OpenableColumns.SIZE` from the same cursor query already used for `DISPLAY_NAME`. Extend `SafAdapter.queryDisplayName` (or add `querySize`) to also read `OpenableColumns.SIZE` and store it on `FileEntry.sizeBytes: Long`. Falls back to `-1` (→ render nothing) when the provider doesn't report size.

`FileEntry` gains `val sizeBytes: Long = -1L`.

---

## Decision 3 — Top app bar

- Leading: the waveform logo vector drawable (`ic_logo`, 24dp), then the title text `OpenConverter`.
- Trailing action: **plain text "Settings"** as a `TextButton` (no gear `Icon`). Opens the Settings screen.

This replaces the current `IconButton { Icon(Icons.Default.Settings) }`.

---

## Decision 4 — Settings screen

```
┌─ ←  Settings                                   ─┐  ← TopAppBar with back nav
├─────────────────────────────────────────────────┤
│            [波形logo, 96dp]                      │
│            OpenConverter                         │
│            Version 1.0.0                         │  ← from BuildConfig.VERSION_NAME
├─────────────────────────────────────────────────┤
│  PROJECT                                        │
│  ┌───────────────────────────────────────────┐  │
│  │ Source code                          ›    │  │  → ACTION_VIEW repo URL
│  │ github.com/nowa277/OpenConverter          │  │
│  ├───────────────────────────────────────────┤  │
│  │ Report an issue                      ›    │  │  → ACTION_VIEW /issues/new
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  ABOUT                                          │
│  <user-authored disclaimer / 致谢 / 声明>       │  ← long-form text, scrollable
│                                                 │
├─────────────────────────────────────────────────┤
│  Open-source licenses                           │  → (optional, deferred — see below)
└─────────────────────────────────────────────────┘
```

- **Version**: read `BuildConfig.VERSION_NAME` (currently `1.0.0`), not hardcoded.
- **Source code** row → `Intent(ACTION_VIEW, Uri.parse("https://github.com/nowa277/OpenConverter"))`.
- **Report an issue** row → `https://github.com/nowa277/OpenConverter/issues/new`.
- Both open the system browser; no new permissions needed.
- **ABOUT** text comes from `strings.xml` key `settings_about` — **the user authors this manually** (see "User-authored slots" below).
- **Open-source licenses**: deferred. ffmpeg-kit bundles many GPL libs; a proper OSS licenses screen is its own task. For v1 we link nothing or omit the row. (Flagged here so it's a conscious cut, not an accident.)

### Navigation

The app currently switches Home/Settings via a simple `when` on screen state in `MainActivity` (no NavHost). Keep that pattern — add a `Settings` screen to the existing enum/state. No new navigation dependency.

---

## Decision 5 — Launcher icon (waveform logo)

Replace the default Android icon with an **adaptive icon** derived from the provided SVG:

- **Background layer** (`ic_launcher_background.xml`): solid `#121212` (the SVG's gradient deep stop) — full-bleed 108×108dp.
- **Foreground layer** (`ic_launcher_foreground.xml`): the 5 green wave bars (+ optional inner ring), centered, within the 66dp safe-zone circle. Green gradient `#1ed760`→`#1db954`.
- **Monochrome layer** (`ic_launcher_monochrome.xml`): white silhouette of the 5 bars — enables themed icons on Android 13+.
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` reference the three layers.
- Keep the existing per-density PNGs out of scope (adaptive XML covers v26+; minSdk 24 means v24/v25 fall back to a `mipmap-mdpi` PNG — generate one 48dp PNG raster from the SVG for that fallback, or accept the system default on the two oldest API levels).

The same `ic_logo` vector (foreground bars only) is reused as the in-app top-bar mark at 24dp.

---

## User-authored slots (paths the user edits manually)

| Content | File | Key / location |
|---|---|---|
| Disclaimer / 致谢 / 声明 (long-form Chinese) | `android/app/src/main/res/values/strings.xml` | `settings_about` |
| (optional English mirror) | `android/app/src/main/res/values-en/strings.xml` | `settings_about` |
| Repo / issue URLs | `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt` | `const val REPO_URL`, `const val ISSUES_URL` |

The implementation will create these keys/constants with **placeholder text** (`settings_about = "TODO: 声明文字由项目维护者填写"`), and the plan will call out that the user replaces the placeholder before release.

---

## Files touched

**Modified:**
- `android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeScreen.kt` — summary strip, bottom sheet, plain-text Settings action, logo in bar.
- `android/app/src/main/kotlin/com/openconverter/app/ui/home/HomeViewModel.kt` — `FileEntry.sizeBytes`, bottom-sheet open state, size query.
- `android/app/src/main/kotlin/com/openconverter/app/ui/components/FileCard.kt` — size + failure-reason fields, text-only badges.
- `android/app/src/main/kotlin/com/openconverter/app/ui/settings/SettingsScreen.kt` — hero + project rows + about.
- `android/app/src/main/kotlin/com/openconverter/app/MainActivity.kt` — Settings screen in the `when` switch.
- `android/app/src/main/kotlin/com/openconverter/app/saf/SafAdapter.kt` — `querySize` (or extend the display-name query to also return size).
- `android/app/src/main/res/values/strings.xml` — `settings_about` placeholder; verify no emoji in existing strings.

**Created:**
- `android/app/src/main/res/drawable/ic_logo.xml` — waveform vector (foreground bars), used in top bar.
- `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- `android/app/src/main/res/drawable/ic_launcher_background.xml`
- `android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Possibly removed:** the default `mipmap-*` Android-robot PNGs if present (replaced by adaptive icon) — confirm against current tree during implementation.

---

## Out of scope (v1)

- OSS licenses screen (ffmpeg-kit GPL attributions) — separate task.
- Drag-to-reorder, swipe-to-remove in the queue — not requested.
- Per-file format override — batch uses one format/bitrate for all files (matches desktop).
- Themed-icon dynamic color tuning beyond the monochrome silhouette.
- Real-device (vivo Y78) acceptance — that's M8.3, run **after** this UI lands.

---

## Testing

- **JVM unit tests:** `HomeViewModel` size mapping (input Uri → `sizeBytes`) is testable with a fake `SafAdapter`. Add a test that a Uri reporting `SIZE=4481054` yields `FileEntry.sizeBytes = 4481054` and that the size formatter renders `4.2 MB`.
- **Size formatter:** pure function `formatBytes(Long): String` — unit-test boundary cases (0, 512, 1023, 1024, 1536, 1_048_576, negative → "").
- **Build:** `:app:assembleDebug` must succeed; APK installs; launcher icon shows the waveform.
- **On-device smoke (emulator):** add sample.ncm, pick folder, open the bottom sheet, switch format, start — files still produce (no regression from Decision 1's refactor). Cancel mid-run still works.
- **No-emoji gate:** grep the final `strings.xml` + UI Kotlin for emoji ranges; expect zero.

## Success criteria

1. Home screen shows the current output folder by name without opening anything.
2. Queue occupies the majority of the screen; controls are one tap away via bottom sheet.
3. A failed file shows its reason on the card; a done file shows its size.
4. Settings shows version (1.0.0), tappable repo + issue links, and the about placeholder.
5. Launcher icon is the waveform, not the Android robot.
6. No emoji anywhere in the UI.
7. NCM→MP3/FLAC and MP3→FLAC still produce files on emulator after the redesign.
