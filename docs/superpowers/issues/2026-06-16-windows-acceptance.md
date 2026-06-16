# Windows Installer Acceptance (2026-06-16)

This issue tracks the **manual end-to-end acceptance** step that cannot be
automated. All implementation tasks (Task 0-8) are complete on the
`windows-installer` branch. Task 9 requires the user to run the artifacts
on a real Windows machine.

## Prerequisites for user

The user must:

1. Have a real Windows 10/11 machine (or VM).
2. Install `wine64` on the build machine OR run `npm run build:win` on a
   machine with native Windows/macOS, because the final NSIS packaging
   step requires `wine` when cross-compiling from Linux.
   (See Task 6: win-unpacked directory is produced without wine, but
   `setup.exe`/`portable.exe` need wine to package.)
3. Have at least 1 sample file of each format for conversion testing:
   - `.ncm` (NetEase)
   - `.qmc0` (QQ Music v1)
   - `.qmcflac` (QQ Music v1 FLAC)
   - `.kgm` (KuGou)
   - `.kwm` (Kuwo)
   For QMCv2 formats (`.mflac`, `.mgg`, `.bkc`), the user also needs a
   valid base64 ekey from QQ Music's local DB (instructions in the
   app's "QQ Music ekey" panel).

## Acceptance checklist

- [ ] User runs NSIS installer on real Windows, follows wizard, completes install.
- [ ] User runs `OpenConverter-X.Y.Z-setup.exe` from a Downloads folder.
- [ ] App opens, shows v0.3.0 version in sidebar.
- [ ] SmartScreen warning shows on first run — user clicks "More info" → "Run anyway".
- [ ] User opens ekey panel, pastes QQ Music ekey (if testing QMCv2), saves.
- [ ] User picks output directory, drops a sample .ncm file, clicks Convert.
- [ ] Conversion completes, output .mp3 plays correctly.
- [ ] Repeat for .qmc0, .qmcflac, .kgm, .kwm (and QMCv2 if ekey available).
- [ ] User runs `OpenConverter-X.Y.Z-portable.exe` from a different
      directory (e.g. USB), confirms it works without installation.
- [ ] User notes any first-launch issues (SmartScreen flow, antivirus
      false positives, ffmpeg-related errors) and records them below.

## Known limitations to verify

- Wine render glitches are cosmetic only and do not affect Windows real-machine usage.
- ffmpeg.exe / ffprobe.exe are bundled — no separate ffmpeg install needed.

## Status

⏳ **Pending user acceptance** — to be completed on real Windows.

## Implementation status

All 8 implementation tasks complete (commits 29d51f7 through d6f96e7 on
the `windows-installer` branch). Branch has 8 commits ahead of main.
