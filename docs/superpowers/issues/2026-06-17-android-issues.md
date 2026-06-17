# Android Issues — 2026-06-17

## M3 Smoke Test (v0.2.2 release APK on emulator)

**Status: PASSED**

### Environment
- AVD: `test_avd` (x86_64, API 34, 6.4 GB data partition)
- Emulator: Android Emulator with `swiftshader_indirect` GPU, KVM acceleration
- APK: `release/openconverter-v0.2.2-android-x86_64.apk` (3.83 MB)

### Test Results

| Step | Result |
|------|--------|
| Emulator boot | PASS (31s, KVM available) |
| Install (`adb install -r`) | PASS after uninstalling prior debug build |
| App launch (`am start`) | PASS |
| Window focus | `com.openconverter.app/com.openconverter.app.MainActivity` |
| Logcat — FATAL / AndroidRuntime | None |
| Process alive after launch | YES (PID 4949) |
| Screenshot | `docs/superpowers/issues/2026-06-17-m3-smoke.png` (84 KB, 1080x2340) |
| NCM push to /sdcard/Download/test.ncm | PASS (10.4 MB Chappell Roan - Pink Pony Club.ncm) |

### Issues Found

1. **Signature mismatch on upgrade install** (predicted):
   First `adb install -r` failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because a
   previously installed debug APK used the debug keystore. Resolved by
   `adb uninstall com.openconverter.app` before installing the release APK.
   Not a product bug — expected for any user upgrading from a debug build to
   a signed release build.

2. **No app-side issues** — UI rendered correctly, output-format chips visible
   (MP3/FLAC/WAV/M4A), "选文件" / "开始转换" / "设置" buttons present, M3 service
   banner displayed.

### Notes

- Full pipeline correctness is verified by Task 3.7's
  `EndToEndConversionTest` (instrumented test that exercises the
  ConversionOrchestrator against a real APK on the emulator).
  The smoke test here confirms install + launch + UI rendering, which is the
  scope of Task 3.9.
- adb shell UI automation was not used — the goal is "real device boot + first
  paint", not full UI interaction.
