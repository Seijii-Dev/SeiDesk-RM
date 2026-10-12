# SeiDesk RM — NOTICE

The `SeiDesk-RM` repository contains the **FreeRDP 3.32.0 source tree** (vendored, pinned via git) as the foundation of its Android RDP client, under the Apache License 2.0 (see `LICENSE`).

FreeRDP — A Remote Desktop Protocol Implementation
Copyright (C) 2012-2026 FreeRDP contributors (Thincast/FreeRDP project)
https://freerdp.com · https://github.com/FreeRDP/FreeRDP

## SeiDesk RM modifications to the FreeRDP tree

This list is intentionally kept minimal to ensure future FreeRDP tree upgrades remain straightforward:

1. `client/Android/Studio/build.gradle` — removed `com.gladed.androidgitversion` plugin (versionName/versionCode now read from `release.properties`); adjusted default ABI splits (arm64-v8a + x86_64, single universal APK).
2. `client/Android/Studio/settings.gradle` — replaced `:aFreeRDP` module with `:app` (SeiDesk RM application).
3. `client/Android/Studio/aFreeRDP/` — removed (replaced by `:app`).
4. `client/Android/Studio/release.properties` — new build configuration file.
5. `client/Android/Studio/app/` — SeiDesk RM application module (owned by Seijixinghe888).
6. `.github/workflows/build-apk.yml` — SeiDesk RM CI (upstream FreeRDP CI workflows removed).
7. `README.md` — SeiDesk RM README; upstream README archived as `FreeRDP-README.md`.
8. `.gitignore` — added Android/Gradle artifact entries.

All other files in the FreeRDP tree (including `client/Android/Studio/freeRDPCore/`) are preserved as vendored code.

## SeiDesk RM code license

Code in `client/Android/Studio/app/` and SeiDesk RM documentation (`README.md`, `docs/`, this notice) is copyright © Seijixinghe888.

## Additional changes (v0.2.1)
- `client/Android/Studio/freeRDPCore/src/main/cpp/CMakeLists.txt`: added "header ordering guard" — explicit file-level dependency on `freerdp/config.h` (generated header) so Ninja does not compile `freerdp-android` before the FreeRDP superbuild install step completes (resolving RelWithDebInfo build issues).
