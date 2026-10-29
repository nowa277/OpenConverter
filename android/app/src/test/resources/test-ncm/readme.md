# NCM fixtures

`sample.ncm` is a generated 440 Hz, one-second mono MP3 wrapped as NCM by `tests/gen-ncm-synthetic.js`. It contains no title, artist, song identifier or cover. The fixture is generated from source mathematics, with a fixed synthetic key, and may be redistributed under this repository's Apache-2.0 license.

`src/sharedTest/kotlin/com/openconverter/app/decoders/SyntheticNcm.kt` builds a separate one-second PCM WAV with fictitious title, artists, album and musicId, and a 1x1 PNG. Metadata tests compare literal fields, image bytes and decoded audio bytes at multiple chunk sizes.

Optional private input comparison uses `OPENCONVERTER_NCM_SAMPLE` and `OPENCONVERTER_NCM_REFERENCE_AUDIO`. Keep those files outside the repository and use only authorized materials. The optional test reports a skip when the environment variables are absent.
