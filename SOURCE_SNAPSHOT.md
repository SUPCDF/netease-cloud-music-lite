# Source snapshot - NeteaseCloudMusicLite 1.1.16

Generated: 2026-10-23 01:47:35

| Field | Value |
| --- | --- |
| App version | 1.1.16 |
| versionCode | 18 |
| Source root | `F:\PJ\NeteaseCloudMusicLite` |
| Snapshot | `F:\PJ\NeteaseCloudMusicLite-source-1.1.16` |
| Archive | `NeteaseCloudMusicLite-source-1.1.16.zip` |

This is a filtered source snapshot of the project. It contains the Android
source, Gradle wrapper/configuration, README, and the promo source (HTML/CSS/JS,
scripts, docs, and promo assets).

## Build

```powershell
cd F:\PJ\NeteaseCloudMusicLite-source-1.1.16
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
```

`local.properties` is intentionally excluded because it contains a
machine-specific Android SDK path. Create it locally if Gradle needs it:

```properties
sdk.dir=C:/Android/sdk
```

(or set `ANDROID_HOME` / `ANDROID_SDK_ROOT`).

## Exclusions

The copy excludes generated and machine-local data:

- `.gradle`, `.kotlin`, `.idea`
- every `build` output directory, `captures`, `.externalNativeBuild`, `.cxx`
- `promo/frames`, `promo/out`, `promo/preview`, `promo/build`
- `local.properties`, `hs_err_pid*.log`, `*.iml`, `*.apk`, `*.aab`, `*.pyc`
- `promo/scripts/__pycache__` and other Python caches

The promo's generated 2,280 PNG frames and final MP4 are not in this source
archive; regenerate them with `promo/scripts/render_frames.py` and
`promo/scripts/encode.py`. The promo music excerpt and fonts remain in
`promo/assets/`.

## Notes

- The APK QR target and promo docs are unchanged.
- Do not publish the third-party promo music as standalone content; see
  `promo/docs/music.md`.
- This snapshot is for personal study and backup use.
