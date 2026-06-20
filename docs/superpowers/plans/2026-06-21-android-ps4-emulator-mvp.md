# BachataS4 Android Emulator MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an ARM64-only Android MVP that boots legal PS4 homebrew on Snapdragon 8 Gen 3 through x86-64 shadPS4 translated by Box64, then qualify same flow on Snapdragon 8 Elite.

**Architecture:** Kotlin/Compose app owns content, UI, and foreground service. APK-managed ARM64 Box64 launches x86-64 shadPS4 against app-private glibc rootfs; vendored Winlator X/audio code and Mesa Turnip bridge display and audio. `EmulatorRuntime` hides Box64 so native ARM64 backend can replace it later.

**Tech Stack:** Kotlin 2.4, Compose, Hilt, Room 2.8.4, DataStore 1.2.1, Navigation 2.9.8, coroutines 1.11.0, serialization 1.11.0, SDK 37, minSdk 31, NDK 29.0.14206865, CMake, C++20, Box64, glibc, SDL3/X11, Mesa Turnip, AAudio.

**Spec:** `docs/superpowers/specs/2026-06-20-android-ps4-emulator-design.md`

---

## Rules and Gates

- Execute tasks in order. Failed feasibility gate blocks later tasks.
- Use only legal homebrew/test content. Never add Sony firmware, keys, or commercial game data.
- Keep unrelated untracked agent/tool files untouched.
- Android emulator proves UI only. Gates 2-8 require physical Snapdragon hardware.
- Record failures honestly; no mock can satisfy a device gate.

Gates: (1) scaffold, (2) Box64 translation, (3) SDL/X11 display, (4) Turnip/audio, (5) shadPS4 startup, (6) content inspection, (7) homebrew first frame, (8) two-device qualification.

## File Map

- `android/BachataS4/app`: activity, navigation, foreground service.
- `android/BachataS4/core/{model,data,database,runtime,testing,designsystem}`: bounded core modules.
- `android/BachataS4/feature/{setup,library,session,settings}`: screens and ViewModels.
- `runtime/{locks,scripts,probes,adapter,tests,evidence}`: reproducible runtime and proof.
- `src/platform/bachata`: shadPS4 structured lifecycle client.

## Locked Upstreams

`runtime/locks/components.lock.json` must contain:

```json
{
  "schemaVersion": 1,
  "components": [
    {"name":"box64","url":"https://github.com/ptitSeb/box64.git","revision":"50c8b90b09b433ab0767de44af2d0731cb0748b7","license":"MIT"},
    {"name":"winlator-app","url":"https://github.com/brunodev85/winlator-app.git","revision":"e113da42beefc39c69c8944b27c19c3703bfa856","license":"LGPL-2.1"},
    {"name":"winlator-components","url":"https://github.com/brunodev85/winlator.git","revision":"fb66541b93a4eb3ee585a433b4c7b20544d58e40","license":"MIT"},
    {"name":"glibc-packages","url":"https://github.com/termux-pacman/glibc-packages.git","revision":"26d89ba7a1f856b99f0d437bef54f558b2485075","license":"mixed"},
    {"name":"mesa","url":"https://gitlab.freedesktop.org/mesa/mesa.git","revision":"6984e91b5fe1d1c204e54954a4282fcdc0c44b78","license":"MIT"}
  ]
}
```

### Task 1: Scaffold and Harden Android Project

**Files:**
- Create: `android/BachataS4/**` via required scaffolder
- Modify: `android/BachataS4/app/build.gradle.kts`
- Modify: `.gitignore`
- Create: `android/README.md`

- [ ] **Step 1: Run pinned scaffolder**

```bash
npx --yes create-android@0.1.0 android --name BachataS4 --package com.bachatas4.android --arch multi --no-install
test -f android/BachataS4/settings.gradle.kts
```

Expected: generated project under `android/BachataS4`.

- [ ] **Step 2: Harden generated build**

Delete `android/BachataS4/keystore/test.jks` and generated release signing block. Set in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.bachatas4.android"
    minSdk = 31
    targetSdk = 37
    versionCode = 1
    versionName = "0.1.0-dev"
    ndk { abiFilters += "arm64-v8a" }
}
androidResources { noCompress += listOf("zip", "json") }
```

- [ ] **Step 3: Ignore outputs**

```gitignore
/android/BachataS4/.gradle/
/android/BachataS4/**/build/
/android/BachataS4/local.properties
/android/BachataS4/.idea/
/android/BachataS4/app/src/main/assets/runtime/
/android/BachataS4/core/runtime/src/main/jniLibs/
/runtime/build/
/runtime/sources/
```

- [ ] **Step 4: Document and verify Gate 1**

`android/README.md` records scaffolder, JDK 17, SDK 37, NDK 29.0.14206865, legal-content rule, and command:

```bash
cd android/BachataS4
./gradlew test assembleDebug
```

Expected: `BUILD SUCCESSFUL`; then commit:

```bash
git add .gitignore android
git commit -m "feat(android): scaffold BachataS4 app"
```

### Task 2: Establish Modules and Dependencies

**Files:**
- Modify: `android/BachataS4/settings.gradle.kts`
- Modify: `android/BachataS4/gradle/libs.versions.toml`
- Create: module `build.gradle.kts` files
- Delete: `android/BachataS4/feature/home`

- [ ] **Step 1: Register modules**

```kotlin
include(":app", ":core:model", ":core:data", ":core:database", ":core:runtime")
include(":core:testing", ":core:designsystem")
include(":feature:setup", ":feature:library", ":feature:session", ":feature:settings")
```

- [ ] **Step 2: Pin catalog**

Add Room `2.8.4`, DataStore `1.2.1`, Navigation `2.9.8`, Hilt Navigation `1.3.0`, Lifecycle `2.11.0`, coroutines `1.11.0`, serialization `1.11.0`, JUnit `4.13.2`, Turbine `1.2.1`, and Kotlin serialization plugin. Never use dynamic versions.

- [ ] **Step 3: Create module build files**

Base `core:model` file:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.bachatas4.android.model"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
```

Use namespaces matching module paths. Feature modules apply Compose/Hilt/KSP. Dependency direction: feature -> data/model/designsystem; data -> database/runtime/model; runtime -> model; database -> model; model -> none.

- [ ] **Step 4: Verify and commit**

```bash
cd android/BachataS4
./gradlew projects test
cd ../..
git add android/BachataS4
git commit -m "build(android): define emulator modules"
```

Expected: eleven modules listed; tests pass.

### Task 3: Define Runtime Boundary

**Files:**
- Create: `android/BachataS4/core/model/src/main/kotlin/com/bachatas4/android/model/RuntimeModels.kt`
- Test: `android/BachataS4/core/model/src/test/kotlin/com/bachatas4/android/model/RuntimeStateTest.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/EmulatorRuntime.kt`
- Create: `android/BachataS4/core/testing/src/main/kotlin/com/bachatas4/android/testing/FakeEmulatorRuntime.kt`

- [ ] **Step 1: Write failing model test**

```kotlin
@Test fun failedStateKeepsStableCode() {
    val state = RuntimeState.Failed(RuntimeErrorCode.RUNTIME_CORRUPT, "sha256 mismatch")
    assertEquals(RuntimeErrorCode.RUNTIME_CORRUPT, state.code)
}
```

Run `./gradlew :core:model:testDebugUnitTest`; expected compile failure.

- [ ] **Step 2: Add complete public models**

```kotlin
@Serializable enum class RuntimeErrorCode {
    UNSUPPORTED_DEVICE, RUNTIME_MISSING, RUNTIME_CORRUPT, INSUFFICIENT_STORAGE,
    CONTENT_PERMISSION_LOST, CONTENT_INVALID, VULKAN_UNSUPPORTED, DRIVER_BLOCKED,
    TRANSLATOR_START_FAILED, BACKEND_CRASHED, PROTOCOL_MISMATCH
}
sealed interface RuntimeState {
    data object Idle : RuntimeState
    data class Preparing(val stage: String) : RuntimeState
    data class Running(val sessionId: String) : RuntimeState
    data object Stopping : RuntimeState
    data class Stopped(val exitCode: Int) : RuntimeState
    data class Failed(val code: RuntimeErrorCode, val detail: String) : RuntimeState
}
@Serializable data class Game(val id: String, val title: String, val relativePath: String)
@Serializable data class DeviceProfile(val soc: String, val gpu: String, val supported: Boolean)
@Serializable data class LaunchRequest(val gameId: String, val ebootPath: String)
@Serializable data class DiagnosticEvent(val timestampMs: Long, val category: String, val message: String)
```

- [ ] **Step 3: Add boundary and fake**

```kotlin
interface EmulatorRuntime {
    val state: StateFlow<RuntimeState>
    val diagnostics: Flow<DiagnosticEvent>
    suspend fun verifyInstallation(): Result<Unit>
    suspend fun launch(request: LaunchRequest): Result<String>
    suspend fun stop(): Result<Unit>
}
```

Fake records request and transitions `Idle -> Running -> Stopped`.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew :core:model:testDebugUnitTest :core:testing:testDebugUnitTest
git add core
git commit -m "feat(android): define runtime contract"
```

Run from `android/BachataS4`.

### Task 4: Verify and Install Runtime Bundle

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/install/RuntimeManifest.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/install/RuntimeInstaller.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/install/RuntimeInstallerTest.kt`

- [ ] **Step 1: Write failing tests**

Test valid ZIP, wrong hash, `../escape`, duplicate path, interrupted install preserving current version.

```kotlin
@Test fun rejectsZipTraversal() = runTest {
    val result = installer.install(zipOf("../escape" to byteArrayOf(1)), manifest)
    assertTrue(result.exceptionOrNull() is SecurityException)
}
```

- [ ] **Step 2: Implement manifest and hash**

```kotlin
@Serializable data class RuntimeManifest(
    val schemaVersion: Int,
    val runtimeVersion: String,
    val protocolVersion: Int,
    val files: List<RuntimeFile>,
)
@Serializable data class RuntimeFile(val path: String, val size: Long, val sha256: String)

fun sha256(input: InputStream): String = MessageDigest.getInstance("SHA-256")
    .digest(input.readBytes()).joinToString("") { "%02x".format(it) }
```

- [ ] **Step 3: Implement atomic safe extraction**

Resolve every entry under `<filesDir>/runtime/.staging-<version>`, require `candidate.startsWith(staging)`, validate declared size/hash, then atomic rename to version directory. Never set executable bit; Android kernel executes only APK native libraries.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew :core:runtime:testDebugUnitTest
git add core/runtime
git commit -m "feat(android): verify runtime bundles"
```

### Task 5: Detect Supported Snapdragon Hardware

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/device/DeviceCapabilityProbe.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/device/DeviceCapabilityProbeTest.kt`

- [ ] **Step 1: Write classifier tests**

```kotlin
@Test fun acceptsTargets() {
    assertTrue(classify("SM8650", "Adreno 750").supported)
    assertTrue(classify("SM8750", "Adreno 830").supported)
}
@Test fun rejectsUnknown() = assertFalse(classify("Tensor G5", "Mali").supported)
```

- [ ] **Step 2: Implement explicit support policy**

```kotlin
internal fun classify(soc: String, gpu: String): DeviceProfile = when {
    soc.equals("SM8650", true) && gpu.contains("Adreno 750", true) -> DeviceProfile(soc, gpu, true)
    soc.equals("SM8750", true) && gpu.contains("Adreno 830", true) -> DeviceProfile(soc, gpu, true)
    else -> DeviceProfile(soc, gpu, false)
}
```

Production reads `Build.SOC_MODEL`; GPU/Vulkan comes from Gate 4 native probe. Before verified probe, GPU=`unverified` and game launch stays blocked.

- [ ] **Step 3: Verify and commit**

```bash
./gradlew :core:runtime:testDebugUnitTest
git add core/runtime
git commit -m "feat(android): gate supported Snapdragon devices"
```

Run from `android/BachataS4`.

### Task 6: Implement Protocol and Process Supervisor

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/protocol/RuntimeProtocol.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncher.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/Box64EmulatorRuntime.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/protocol/RuntimeProtocolTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncherTest.kt`

- [ ] **Step 1: Test framing and shell-free command**

Reject frames over 1 MiB and protocol versions other than 1. Assert command:

```kotlin
assertEquals(
    listOf("/apk/lib/libbox64.so", "/data/runtime/bin/shadps4", "--bachata-socket", socket),
    launcher.command(request),
)
```

- [ ] **Step 2: Implement message schema and codec**

```kotlin
const val PROTOCOL_VERSION = 1
const val MAX_FRAME_BYTES = 1_048_576
@Serializable sealed interface RuntimeMessage {
    @Serializable data class Hello(val version: Int, val runtimeVersion: String) : RuntimeMessage
    @Serializable data class State(val value: String, val detail: String = "") : RuntimeMessage
    @Serializable data class Log(val level: String, val category: String, val message: String) : RuntimeMessage
    @Serializable data class Metrics(val frameTimeMs: Double, val rssBytes: Long) : RuntimeMessage
    @Serializable data object Stop : RuntimeMessage
}
```

Codec writes four-byte big-endian length plus UTF-8 JSON; validates length before allocation.

- [ ] **Step 3: Implement secure launcher**

Resolve Box64 only as `<ApplicationInfo.nativeLibraryDir>/libbox64.so`; canonical parent must match. Use `ProcessBuilder(List<String>)`, clear inherited environment, allowlist `HOME`, `BOX64_PATH`, `BOX64_LD_LIBRARY_PATH`, `DISPLAY`, `TMPDIR`, `XDG_CACHE_HOME`, `MESA_SHADER_CACHE_DIR`, `VK_ICD_FILENAMES`.

- [ ] **Step 4: Implement one-session state machine**

Guard launch/stop with `Mutex`. Launch transitions `Idle -> Preparing -> Running`; second launch fails. Stop sends `Stop`, waits five seconds, `destroy()`, waits two seconds, then `destroyForcibly()` only for owned process.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :core:runtime:testDebugUnitTest
git add core/runtime
git commit -m "feat(android): supervise Box64 runtime"
```

### Task 7: Build Box64/glibc Runtime; Pass Gate 2

**Files:**
- Create: `runtime/locks/components.lock.json`
- Create: `runtime/scripts/{checkout-component.sh,build-box64.sh,package-runtime.mjs}`
- Create: `runtime/probes/hello.c`
- Create: `runtime/tests/verify-runtime.mjs`
- Generate ignored: `core/runtime/src/main/jniLibs/arm64-v8a/libbox64.so`
- Generate ignored: `app/src/main/assets/runtime/{runtime.zip,manifest.json}`

- [ ] **Step 1: Write failing lock/package verifier**

Verifier requires 40-character revisions, URL/license, lexical archive order, no duplicate paths, and matching SHA-256.

```bash
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
```

Expected: FAIL before lock/package exists.

- [ ] **Step 2: Add exact lock JSON from Locked Upstreams**

No moving branch or unpinned binary allowed.

- [ ] **Step 3: Add exact checkout helper**

```bash
#!/usr/bin/env bash
set -euo pipefail
name=$1; url=$2; revision=$3; dest="runtime/sources/$name"
if [[ ! -d "$dest/.git" ]]; then git clone --filter=blob:none "$url" "$dest"; fi
git -C "$dest" fetch --depth 1 origin "$revision"
git -C "$dest" checkout --detach "$revision"
test "$(git -C "$dest" rev-parse HEAD)" = "$revision"
```

- [ ] **Step 4: Build APK-managed Box64**

```bash
cmake -S runtime/sources/box64 -B runtime/build/box64 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=31 -DANDROID=ON -DARM64=ON \
  -DARM_DYNAREC=ON -DBAD_SIGNAL=ON -DNO_LIB_INSTALL=ON -DNO_CONF_INSTALL=ON \
  -DCMAKE_BUILD_TYPE=Release
cmake --build runtime/build/box64 --target box64
install -Dm755 runtime/build/box64/box64 \
  android/BachataS4/core/runtime/src/main/jniLibs/arm64-v8a/libbox64.so
```

`readelf -h` must report AArch64 PIE. `readelf -d` must contain no unavailable desktop library.

- [ ] **Step 5: Build minimal rootfs and probe**

Use pinned `glibc-packages` plus Winlator glibc patches. Rootfs contains dynamic loader, libc, libm, libdl, pthread compatibility, libstdc++, and certificates required by runtime. Probe:

```c
#include <stdio.h>
int main(void) { puts("BACHATA_TRANSLATION_OK"); return 0; }
```

Compile x86-64, package deterministic ZIP with zero timestamps and manifest hashes. Two builds must produce same archive SHA-256.

- [ ] **Step 6: Pass Gate 2 on SM8650**

Required output:

```text
BACHATA_TRANSLATION_OK
exitCode=0
box64Revision=50c8b90b09b433ab0767de44af2d0731cb0748b7
```

Save sanitized `runtime/evidence/sm8650/gate-2-translation.txt`; commit source/evidence, never generated binaries:

```bash
git add runtime/locks runtime/scripts runtime/probes runtime/tests runtime/evidence
git commit -m "feat(runtime): pass Box64 translation gate"
```

### Task 8: Embed X Server and Audio Bridge; Pass Gate 3

**Files:**
- Create: `runtime/scripts/vendor-winlator.sh`
- Create: `android/BachataS4/core/runtime/src/main/java/com/winlator/xserver/` from pinned upstream
- Create: `android/BachataS4/core/runtime/src/main/java/com/winlator/alsaserver/` from pinned upstream
- Create: `android/BachataS4/core/runtime/src/main/cpp/winlator/` from pinned upstream
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/display/EmbeddedXServer.kt`
- Create: `runtime/probes/sdl-window.cpp`
- Create: `NOTICE.android-runtime.md`

- [ ] **Step 1: Vendor pinned source, not binaries**

Script checks out Winlator app commit `e113da42beefc39c69c8944b27c19c3703bfa856`; copies X server, ALSA server, required renderer/support packages, and native `winlator` directory. Preserve license headers and generate upstream-path/SHA-256 manifest. Exclude Wine UI, installers, and bundled binaries.

- [ ] **Step 2: Add LGPL notice and wrapper**

Notice records URL, revision, copied paths, modifications, LGPL-2.1. Wrapper:

```kotlin
interface EmbeddedXServer {
    val display: String
    suspend fun start(surface: Surface, width: Int, height: Int)
    suspend fun resize(width: Int, height: Int)
    suspend fun stop()
}
```

One instance, display `:0`, idempotent stop. Test `surfaceDestroyed` stops once.

- [ ] **Step 3: Build SDL probe**

Probe creates `SDL_WINDOW_VULKAN`, handles resize/quit, remains visible three seconds, prints `BACHATA_SDL_OK`, exits zero.

- [ ] **Step 4: Pass Gate 3**

On SM8650 rotate once and background/foreground once. Require one X server, one SDL window, no crash, success marker. Save `runtime/evidence/sm8650/gate-3-display.txt`; commit vendored source, notices, probe, evidence.

### Task 9: Integrate Turnip and Probes; Pass Gate 4

**Files:**
- Create: `runtime/scripts/build-turnip.sh`
- Create: `runtime/probes/vulkan-info.cpp`
- Create: `runtime/probes/audio-tone.c`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/device/VulkanCapabilities.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/device/VulkanCapabilitiesTest.kt`

- [ ] **Step 1: Build pinned Turnip**

Checkout Mesa commit `6984e91b5fe1d1c204e54954a4282fcdc0c44b78` (26.1.3). Build only Freedreno/Turnip ARM64 using pinned Winlator KGSL patches and NDK 29. Package driver, ICD JSON, Box64 Vulkan wrapper. Verify ELF architecture/dependencies.

- [ ] **Step 2: Test capability policy**

Parser requires Vulkan 1.3, Adreno 750/830, `VK_KHR_swapchain`, nonzero device-local heap. Missing requirement -> `VULKAN_UNSUPPORTED`; explicit denylist -> `DRIVER_BLOCKED`.

- [ ] **Step 3: Build probes**

Vulkan probe creates X11 surface, renders alternating blue/black for 300 frames, emits API, UUID, device ID, heaps, frame time, `BACHATA_VULKAN_OK`. Audio probe sends 48 kHz stereo 440 Hz PCM for two seconds through ALSA bridge and emits underruns plus `BACHATA_AUDIO_OK`.

- [ ] **Step 4: Pass Gate 4**

On SM8650 require visible frames, audio tone, no validation error/crash, captured driver identity. Save `runtime/evidence/sm8650/gate-4-vulkan-audio.json`; run runtime unit tests and commit.

### Task 10: Add shadPS4 Runtime Mode; Pass Gates 5-6

**Files:**
- Modify: `CMakeLists.txt`
- Modify: `src/main.cpp`
- Modify: `src/emulator.cpp`
- Create: `src/platform/bachata/{runtime_client.h,runtime_client.cpp}`
- Create: `tests/platform/test_bachata_runtime_client.cpp`
- Create: `runtime/scripts/build-shadps4-x86_64.sh`

- [ ] **Step 1: Write C++ protocol tests**

Use `socketpair()` to test version handshake, `Starting`, `Running`, `Stopped`, malformed frame rejection, disconnected peer, and no behavior change without Bachata CLI flag.

- [ ] **Step 2: Add build mode**

```cmake
option(ENABLE_BACHATA_RUNTIME "Build x86-64 Android-container runtime mode" OFF)
if (ENABLE_BACHATA_RUNTIME)
    set(ENABLE_DISCORD_RPC OFF)
    set(ENABLE_UPDATER OFF)
    target_compile_definitions(shadps4 PRIVATE ENABLE_BACHATA_RUNTIME)
    target_sources(shadps4 PRIVATE
        src/platform/bachata/runtime_client.cpp
        src/platform/bachata/runtime_client.h)
endif()
```

- [ ] **Step 3: Add secure CLI socket**

`src/main.cpp` accepts `--bachata-socket <absolute-app-private-path>`. Reject relative paths and paths outside runtime root. Connect before emulator creation. Emit `Starting`; after SDL/Vulkan init emit `Running`; on validation error emit stable code; on shutdown emit `Stopped`.

- [ ] **Step 4: Build x86-64 backend**

```bash
cmake -S . -B runtime/build/shadps4-x86_64 -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DENABLE_BACHATA_RUNTIME=ON \
  -DENABLE_DISCORD_RPC=OFF -DENABLE_UPDATER=OFF -DENABLE_TESTS=OFF
cmake --build runtime/build/shadps4-x86_64 --target shadps4
```

Copy executable plus exact x86-64 dynamic dependencies into runtime ZIP; verify with `readelf`.

- [ ] **Step 5: Pass Gate 5**

Launch without game. Required events: `Hello(1)`, `Starting`, typed `CONTENT_INVALID`, `Stopped`; no uncaught signal. Save evidence.

- [ ] **Step 6: Pass Gate 6**

Use legal homebrew/test dump. Return title/serial or typed validation failure. Confirm no external path becomes command syntax. Save evidence.

- [ ] **Step 7: Regression test and commit**

```bash
cmake -S . -B build-tests -G Ninja -DENABLE_TESTS=ON -DCMAKE_BUILD_TYPE=Debug
cmake --build build-tests
ctest --test-dir build-tests --output-on-failure
git add CMakeLists.txt src tests runtime/scripts runtime/evidence
git commit -m "feat(runtime): add Bachata shadPS4 mode"
```

### Task 11: Persist Games and Import SAF Content

**Files:**
- Create: `android/BachataS4/core/database/src/main/kotlin/com/bachatas4/android/database/BachataDatabase.kt`
- Create: `android/BachataS4/core/database/src/main/kotlin/com/bachatas4/android/database/GameDao.kt`
- Create: `android/BachataS4/core/database/src/main/kotlin/com/bachatas4/android/database/SessionDao.kt`
- Create: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/ContentImporter.kt`
- Create: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/GameRepository.kt`
- Test: `android/BachataS4/core/database/src/androidTest/kotlin/com/bachatas4/android/database/BachataDatabaseTest.kt`
- Test: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/ContentImporterTest.kt`

- [ ] **Step 1: Write failing tests**

Cover unique game ID, stale-session recovery, delete ownership, byte mismatch, cancellation, atomic rename, lost URI permission. Destination must remain under `<filesDir>/games`.

- [ ] **Step 2: Define schema**

```kotlin
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val relativePath: String,
    val sourceUri: String,
    val importedAtMs: Long,
)
@Database(entities = [GameEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class BachataDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun sessionDao(): SessionDao
}
```

- [ ] **Step 3: Implement safe importer**

Use `ContentResolver`, copy into `.import-<uuid>`, count bytes, SHA-256, fsync, atomic rename. Persist relative path only. Cancellation removes staging, never current game.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew :core:data:testDebugUnitTest :core:database:connectedDebugAndroidTest
git add core/data core/database
git commit -m "feat(android): import and persist game dumps"
```

Run from `android/BachataS4`.

### Task 12: Build Setup, Library, and Settings UI

**Files:**
- Create: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/BachataNavHost.kt`
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/MainActivity.kt`
- Create: `android/BachataS4/feature/setup/src/main/kotlin/com/bachatas4/android/feature/setup/SetupScreen.kt`
- Create: `android/BachataS4/feature/setup/src/main/kotlin/com/bachatas4/android/feature/setup/SetupViewModel.kt`
- Create: `android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt`
- Create: `android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryViewModel.kt`
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsScreen.kt`
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsViewModel.kt`
- Test: matching `src/test` ViewModel tests and `src/androidTest` screen tests in each feature module

- [ ] **Step 1: Test ViewModels**

Setup shows detected SoC/GPU for unsupported device. Library sorts games by title. Settings diagnostic export includes runtime revision and Vulkan UUID but no absolute game path.

- [ ] **Step 2: Implement typed navigation**

Routes: `setup`, `library`, `game/{id}`, `session/{id}`, `settings`. Start setup until device/runtime verify, else library. Only internal IDs in arguments.

- [ ] **Step 3: Implement screens**

Setup: legal notice, device profile, integrity, install progress. Library: SAF picker, game list/details, launch. Settings: component revisions, Stability/Performance Box64 preset, driver profile, diagnostics export, confirmed app-owned data reset.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew test connectedDebugAndroidTest
git add app feature
git commit -m "feat(android): add setup and game library UI"
```

### Task 13: Add Foreground Session and Pass Gate 7

**Files:**
- Modify: `android/BachataS4/app/src/main/AndroidManifest.xml`
- Create: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt`
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/SessionScreen.kt`
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/SessionViewModel.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/input/ControllerMapper.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/diagnostics/DiagnosticExporter.kt`
- Test: `android/BachataS4/app/src/test/kotlin/com/bachatas4/android/service/EmulationServiceTest.kt`
- Test: `android/BachataS4/feature/session/src/test/kotlin/com/bachatas4/android/feature/session/SessionViewModelTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/input/ControllerMapperTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/diagnostics/DiagnosticExporterTest.kt`

- [ ] **Step 1: Declare service**

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<application android:extractNativeLibs="true">
  <service
      android:name=".service.EmulationService"
      android:exported="false"
      android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-requested local game emulation" />
  </service>
</application>
```

- [ ] **Step 2: Test lifecycle**

Explicit launch creates notification before runtime. Second launch rejected. Surface recreation creates no second process. Stop idempotent. Process death marks stale session failed.

- [ ] **Step 3: Implement service and surface**

Service owns `EmulatorRuntime`; activity binds/observes only. Session embeds `SurfaceView`, shows state/error/thermal status, provides Stop. Back while running asks stop confirmation.

- [ ] **Step 4: Implement controller**

Normalize DualShock/DualSense/Xbox keys and axes, clamp `[-1,1]`, deadzone `0.08`, preserve device/timestamp, forward protocol events. No touch controls.

- [ ] **Step 5: Implement sanitized diagnostics**

ZIP app/runtime versions, device/Vulkan profile, session summary, logs. Replace private root with `<APP_DATA>` and game path with `<GAME>`; exclude imported files/firmware.

- [ ] **Step 6: Pass Gate 7 and commit**

On SM8650 legal homebrew reaches first frame, audio, physical controller, clean stop/relaunch, one surface recreation, sanitized export. Save evidence.

```bash
git add android/BachataS4 runtime/evidence/sm8650
git commit -m "feat(android): run managed emulation sessions"
```

### Task 14: Qualify Devices, CI, and Compliance

**Files:**
- Create: `runtime/qualification/{qualification-schema.json,run-qualification.sh}`
- Create: `runtime/evidence/{sm8650,sm8750}/gate-8-qualification.json`
- Modify: `.github/workflows/build.yml`
- Create: `documents/android-building.md`
- Modify: `NOTICE.android-runtime.md`

- [ ] **Step 1: Define evidence schema**

Require hashed fingerprint, SoC/GPU/API, driver UUID, revisions, cold/warm startup, p50/p95/p99 frame time, peak RSS, thermal timeline, audio underruns, dropped input, exit code, crash-free duration. Exclude serial/user paths.

- [ ] **Step 2: Run qualification**

Five cold and five warm legal-homebrew sessions on SM8650 and SM8750. Use same archive first. Create Elite preset only when evidence improves frame time without new failure.

- [ ] **Step 3: Add Android CI**

JDK 17, SDK 37; no runtime binary download:

```bash
cd android/BachataS4
./gradlew test lintDebug assembleDebug
node ../../runtime/tests/verify-runtime.mjs ../../runtime/locks/components.lock.json
```

Keep C++ jobs unchanged. Trigger Android job for Android/runtime/Bachata platform/CMake changes.

- [ ] **Step 4: Document reproducible build and licenses**

`documents/android-building.md` records SDK/NDK, scaffold command, locks, runtime/APK commands, Gate 1-8, legal-content rule. Notice records GPL corresponding-source offer and every component license.

- [ ] **Step 5: Full verification**

```bash
cd android/BachataS4
./gradlew clean test lintDebug assembleDebug
cd ../..
cmake -S . -B build-tests -G Ninja -DENABLE_TESTS=ON -DCMAKE_BUILD_TYPE=Debug
cmake --build build-tests
ctest --test-dir build-tests --output-on-failure
reuse lint
```

Expected: all pass; Gate 8 evidence exists for both devices.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/build.yml documents/android-building.md NOTICE.android-runtime.md runtime/qualification runtime/evidence
git commit -m "test(android): qualify Snapdragon emulator MVP"
```

## Completion Check

- [ ] Gates 1-8 pass with evidence.
- [ ] No Sony content or unrestricted path bundled.
- [ ] Runtime revisions, hashes, notices, corresponding-source instructions complete.
- [ ] Android and existing C++ suites pass.
- [ ] SM8650 baseline and SM8750 profile recorded.
