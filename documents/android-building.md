# Android Runtime Build

## Requirements

- Linux or WSL2 x86-64, JDK 17, Node.js 20+
- Android SDK platform/build-tools 37
- Android NDK `30.0.14904198`, CMake `3.22.1`, Ninja
- AArch64 GNU cross compiler for host-glibc Box64

All runtime inputs and upstream revisions are pinned under `runtime/locks`. Runtime packaging never downloads artifacts; fetch inputs separately, verify their lock hashes, then build from the repository root:

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs
```

CI uses `--locks-only` because packaged binaries are intentionally excluded from Git. Release/local packaging must run full verification against generated assets as shown above.

Build APK from scratch:

```bash
cd android/BachataS4
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew clean test lintDebug assemblePlaystoreDebug
cd ../..
node runtime/tests/verify-apk-runtime.mjs android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
```

The APK verifier requires both `assets/runtime/manifest.json` and `assets/runtime/runtime.zip`, then inspects the nested runtime ZIP. It fails if any Turnip driver, archive, Freedreno ICD, `vulkan.ad07xx.so`, or `libvulkan_freedreno.so` is bundled. Vulkan loader libraries such as `libvulkan.so.1` remain part of the managed runtime.

Turnip drivers are always installed after app installation. The only trusted remote feed is GitHub Releases from `JICA98/bachata-s4-drivers`; users may also import a local emulator-driver ZIP. The APK never packages a Turnip driver.

## Device Gates

1. Install/launch on arm64-v8a API 31+; scaffold remains responsive.
2. Verify locked Box64/runtime probes execute from app-private storage.
3. Verify embedded X surface survives resize/recreation.
4. Verify audio bridge and Turnip Vulkan probe.
5. Launch shadPS4 without content; require `HELLO`, `Starting`, `CONTENT_INVALID`, `Stopped`.
6. Import user-owned legal homebrew and verify typed validation or title/serial. Paths are argument-list entries, never shell text.
7. Verify first frame/audio/controller, stop/relaunch, one surface recreation, sanitized diagnostics.
8. Record five cold and five warm sessions per qualified SoC using `runtime/qualification/qualification-schema.json`.

Only user-owned legal homebrew or legally dumped content may be imported. Firmware, keys, games, and copyrighted system files are never bundled.
