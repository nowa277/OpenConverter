# Real Device Test Checklist — v0.3.0

**Date:** 2026-06-17+
**Build:** openconverter-v0.3.0-android-<abi>.apk
**Tester:** <user>
**Devices:** <device model + Android version>

## Setup

For each device:

- [ ] Install: `adb install -r openconverter-v0.3.0-android-<matching-abi>.apk`
- [ ] Grant: Storage permission (Android 13+: "All files"; older: "Storage")
- [ ] Open app, navigate to FileListScreen

## 11 format × MP3 (must all pass)

| # | Format | Test file | Result | Notes |
|---|--------|-----------|--------|-------|
| 1 | NCM | song1.ncm | [ ] | |
| 2 | QMC0 | song2.qmc0 | [ ] | |
| 3 | QMC3 | song3.qmc3 | [ ] | |
| 4 | QMCFLAC | song4.qmcflac | [ ] | |
| 5 | QMCOGG | song5.qmcogg | [ ] | |
| 6 | KGM | song6.kgm | [ ] | |
| 7 | KGMA | song7.kgma | [ ] | |
| 8 | VPR | song8.vpr | [ ] | |
| 9 | KWM | song9.kwm | [ ] | |
| 10 | MFLAC | song10.mflac | [ ] | |
| 11 | MGG | song11.mgg | [ ] | |

(For each: pick file → list shows correct chip → convert to MP3 → verify output is valid MP3 stream)

## NCM × 5 output formats

| # | Output | Result | Notes |
|---|--------|--------|-------|
| 1 | MP3 | [ ] | |
| 2 | FLAC | [ ] | |
| 3 | WAV | [ ] | |
| 4 | M4A | [ ] | |
| 5 | OGG | [ ] | |

## UI / UX

- [ ] File list shows source format chip for each entry (11/11)
- [ ] File list shows file size
- [ ] [+ 添加文件] button works (re-pick)
- [ ] [清空列表] button works
- [ ] Foreground notification appears during conversion
- [ ] Foreground notification updates with progress
- [ ] Output file is written to SAF-selected location

## OEM-specific (MIUI/EMUI/OneUI/etc.)

- [ ] SAF picker opens correctly
- [ ] Permission grant flow works
- [ ] App doesn't get killed by OEM "battery optimization" mid-conversion
- [ ] Notification channel "OpenConverter" appears in settings (Android 8+)

## Issues found

(Record any failures or unexpected behavior with device + Android version + steps to reproduce)
