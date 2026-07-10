# Bachata S4 Android

The Android app is scaffolded with the `create-android@0.1.0` multi-module
template in `android/BachataS4`.

## Prerequisites

- JDK 17
- Android SDK 37
- Android NDK 29.0.14206865
- CMake
- Ninja
- Node.js 18 or newer

## Build

```sh
git submodule update --init --recursive --jobs 8
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
cd android/BachataS4
./gradlew test lintDebug assemblePlaystoreDebug
cd ../..
node runtime/tests/verify-apk-runtime.mjs android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
```

The Gradle build packages existing runtime assets; it does not generate them. Always build and verify the managed runtime first.

## Runtime settings

- Global defaults and sparse per-game overrides cover all exported shadPS4 JSON settings and all supported `BOX64_*` variables.
- Box64 offers the official `safest`, `safe`, `default`, `fast`, and `fastest` profiles. `custom` exposes every individual Box64 flag for fine-tuning.
- Profiles support typed editing, search, explicit raw shadPS4 JSON/Box64 validation, and JSON-only import/export.
- Four controller slots support persistent device identity, remapping, dead zones, inversion, triggers, vibration, and motion preferences.
- Touch controls support saved global/per-game layouts, movement, resizing, visibility, z-order, opacity, scale, vibration, and stick centering.

## Turnip drivers

Turnip is not bundled. The driver manager lists trusted emulator ZIP releases from [`JICA98/bachata-s4-drivers`](https://github.com/JICA98/bachata-s4-drivers), downloads them with size/hash/package validation, and supports local ZIP import. Installed versions can be selected globally or per game and remain usable offline.

## Legal content

Use only game content, firmware, keys, and other assets that you are legally
authorized to use. Do not commit or distribute copyrighted third-party content
with this project.
