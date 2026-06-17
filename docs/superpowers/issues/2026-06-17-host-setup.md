# Android Dev Host Setup — 2026-06-17

**Recorded by:** Claude (Task 0.1)
**Branch:** android-port
**Purpose:** Document the dev machine environment for the v0.2.2 Android port.

## Environment baseline

| Item                       | Value                                                        |
| -------------------------- | ------------------------------------------------------------ |
| OS                         | Linux 6.8.0-124-generic (x86_64)                             |
| CPU virtualization         | ✅ supported (32 cores, `vmx` flag)                           |
| /dev/kvm                   | ✅ available (`crw-rw---- root kvm`)                          |
| Java (default)             | OpenJDK 11.0.31 (apt)                                        |
| Java 17 (for AGP 8.5)      | ✅ Temurin 17.0.13 at `$HOME/.local/jdk/jdk-17` (user-space, no sudo) |
| JAVA_HOME                  | `/home/user/.local/jdk/jdk-17`                               |
| ANDROID_HOME               | `/home/user/Android/Sdk`                                     |
| ANDROID_NDK_HOME           | `/home/user/Android/Sdk/ndk/25.2.9519653`                    |
| Android cmdline-tools      | ✅ 12.0 (in `$ANDROID_HOME/cmdline-tools/latest/`)            |
| platform-tools (adb)       | ✅ 37.0.0 — adb 1.0.41                                        |
| build-tools                | ✅ 34.0.0                                                     |
| platforms                  | ✅ android-34 (API 34)                                        |
| NDK                        | ✅ 25.2.9519653 (r25c)                                        |
| Emulator                   | ✅ 36.6.11                                                    |
| system-image               | ✅ android-34 google_apis x86_64                              |

## Environment persistence (.bashrc)

Added 2026-06-17:

```bash
# JDK 17 (user-space, no sudo needed) for Android Gradle Plugin 8.5
export JAVA_HOME=$HOME/.local/jdk/jdk-17
export PATH=$JAVA_HOME/bin:$PATH

# Android SDK
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.2.9519653
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin
```

Verify after `source ~/.bashrc`:

```bash
java -version 2>&1                  # → 17.0.13
echo $ANDROID_HOME                  # → /home/user/Android/Sdk
adb --version                       # → 1.0.41 / 37.0.0
ls $ANDROID_HOME/ndk/25.2.9519653/  # → contains source.properties, toolchains/
```

## Disk usage

| Path                            | Size  |
| ------------------------------- | ----- |
| `$ANDROID_HOME/` total          | 4.2 G |
|   ├─ `ndk/25.2.9519653`         | 1.7 G |
|   ├─ `emulator/`                | 1.0 G |
|   ├─ `system-images/android-34` | 0.9 G |
|   ├─ `platforms/android-34`     | 0.1 G |
|   ├─ `build-tools/34.0.0`       | 0.1 G |
|   └─ `cmdline-tools/latest`     | 0.1 G |
| `$HOME/.local/jdk/jdk-17`       | 0.4 G |

## Install notes (history)

### What worked
- `wget cmdline-tools zip` + extract to `$ANDROID_HOME/cmdline-tools/latest/`
- `sdkmanager --licenses` (auto-yes)
- `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;25.2.9519653" "emulator"`
- `sdkmanager "system-images;android-34;google_apis;x86_64"` (succeeded on retry)

### What failed
- Initial combined install (`sdkmanager "platforms;android-34" "build-tools;34.0.0" "ndk;25.2.9519653" "emulator" "system-images;android-34;google_apis;x86_64"`):
  - First attempt silently failed because `sdkmanager` was launched with Java 11 (default), but AGP/sdkmgr 12.0 requires Java 17
  - Got `UnsupportedClassVersionError: ... class file version 61.0 ... up to 55.0` (Java 11 max)
  - Fix: install OpenJDK 17 in user space (no sudo) via Temurin tarball, prepend to `PATH`
- `system-images;android-34;google_apis;x86_64`:
  - First attempt failed with `https://dl.google.com/android/repository/sys-img/google_apis/x86_64-34_r14.zip` SHA mismatch
  - Succeeded on retry
- `platform-tools`:
  - Initial combined install failed
  - Succeeded when run alone

## Workaround for future failures

If `sdkmanager` keeps failing on a particular package, install the rest first then retry the failing one separately. Google download servers occasionally serve inconsistent bytes; retry usually fixes it.

For packages that absolutely won't download, manual alternative:
- Platform tools: https://developer.android.com/tools/releases/platform-tools → extract to `$ANDROID_HOME/platform-tools/`
- System images: https://developer.android.com/studio → command line tools only

## Ready state

Task 0.1 complete. Ready for Task 0.2 (Gradle skeleton).
