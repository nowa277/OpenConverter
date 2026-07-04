# OpenConverter - Database Scanning & UI Polish Design Spec

## Goal
Improve the user experience of OpenConverter by enhancing the intelligence of the `kugou.db` scanning mechanism and polishing the user interface for the main conversion button.

## Proposed Approaches & Decisions

### 1. Database Auto-Scan Scope
**Decision:** Implement an aggressive full-disk scan (Option A).
**Details:**
* Currently, the app only scans standard paths (e.g., `C:\ProgramData\KuGou...`). This fails for users who install the software on secondary drives like `D:\` or `E:\`.
* The new approach will fetch all available drive letters on Windows (e.g., C, D, E, F) when standard paths fail.
* It will scan the root level of each drive (and possibly common subdirectories like `Program Files`) for the `KuGou` folder containing `KGMusicV3.db`.
* **Tradeoffs:** High coverage and zero-config for the user, at the cost of slightly increased scan time if the DB is buried deep (though limiting the depth/scope of the scan can mitigate this).

### 2. UI Tweak: "Start Conversion" Button
**Decision:** Implement the "Glassmorphism Footer" effect (Option B).
**Details:**
* The current UI has a solid white sticky footer that obscures the track list behind it.
* The new design will remove the solid white background and replace it with a transparent gradient (`linear-gradient` from transparent to white) combined with a `backdrop-filter: blur(2px)` or similar to create a glassmorphism effect.
* The button itself remains prominent and floating, while the list items can gracefully slide underneath it, remaining partially visible.

## Components & Data Flow

1. **`src/main/kgg-keys.js`**
   * Enhance `autoScanKeys` to utilize `child_process` (e.g., `wmic logicaldisk get name` on Windows) to dynamically retrieve all drive letters.
   * Add a fallback loop that iterates over these drives to search for `KuGou` directories if the initial standard path checks return nothing.

2. **`src/renderer/style.css`**
   * Modify the `.queue-footer` CSS class to adopt the glassmorphism aesthetic.
   * `background: linear-gradient(180deg, rgba(250,250,250,0) 0%, rgba(250,250,250,0.8) 40%, rgba(250,250,250,1) 100%);`
   * `backdrop-filter: blur(2px);`

## Testing
* **DB Scanning:** Verify that if the DB is artificially placed on a `D:\KuGou` drive, the app successfully finds it.
* **UI Polish:** Verify visually that scrolling a long list of files results in the items passing behind the blurred footer rather than being abruptly cut off by a solid white block.

## Open Questions / Ambiguities
* Depth of full-disk scan: To prevent the scan from hanging the app for minutes on massive disks, the scan should be restricted to a depth of 1 or 2 levels from the root of each drive (e.g., `D:\KuGou`, `D:\Program Files\KuGou`), rather than a full recursive crawl of the entire filesystem.

