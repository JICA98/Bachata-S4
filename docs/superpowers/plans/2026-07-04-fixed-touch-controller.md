# Fixed Touch Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fixed multitouch PlayStation-style overlay that controls the running shadPS4 game on Android.

**Architecture:** Compose produces complete normalized controller snapshots and sends them from the foreground service over the existing bidirectional Unix socket. The Bachata-only C++ runtime reader validates ordered snapshots and updates controller zero directly, leaving desktop SDL input unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Android local sockets, C++20, shadPS4 `Input::GameController`, GoogleTest.

## Global Constraints

- Execute sequentially; do not use parallel agents.
- Fixed layout first; configurable layout is a later enhancement.
- Support simultaneous pointers, release all controls on cancellation/disconnect, and preserve desktop behavior.
- Clamp sticks to `[-1, 1]`, triggers to `[0, 1]`, and use stick deadzone `0.08`.
- Test and install only on the currently connected Android device.

---

### Task 1: Shared Controller Snapshot Contract

**Files:**
- Create: `src/platform/bachata/controller_snapshot.h`
- Create: `src/platform/bachata/controller_snapshot.cpp`
- Modify: `tests/platform/test_bachata_runtime_client.cpp`

**Interfaces:**
- Produces: `ControllerSnapshot`, `ParseControllerSnapshot(std::string_view)`, and strict range/sequence validation.

- [ ] **Step 1: Write failing parser tests**

Cover a complete valid frame, malformed numbers, unknown fields, out-of-range axes, and stale sequence numbers.

- [ ] **Step 2: Run test to verify it fails**

Run: `cmake --build build-tests --target unit_test && build-tests/tests/unit_test --gtest_filter='BachataControllerSnapshot.*'`
Expected: FAIL because the snapshot parser does not exist.

- [ ] **Step 3: Implement the snapshot parser**

Use one newline-delimited frame:

```text
BACHATA/1 INPUT seq=42 buttons=0x0000000000000001 lx=128 ly=128 rx=128 ry=128 l2=0 r2=0 touch=0 tx=0 ty=0
```

Reject duplicate/unknown fields; clamp nothing in the parser so invalid transport data is observable.

- [ ] **Step 4: Run focused tests**

Expected: all `BachataControllerSnapshot.*` tests pass.

### Task 2: Bachata Core Input Receiver

**Files:**
- Modify: `src/platform/bachata/runtime_client.h`
- Modify: `src/platform/bachata/runtime_client.cpp`
- Modify: `src/emulator.cpp`
- Test: `tests/platform/test_bachata_runtime_client.cpp`

**Interfaces:**
- Consumes: validated `ControllerSnapshot`.
- Produces: `RuntimeClient::StartInputReader(Input::GameController&)` and `StopInputReader()`.

- [ ] **Step 1: Write failing socket-pair tests**

Verify button press/release, all six axes, ordered frames, malformed-frame rejection, EOF release, and reader shutdown.

- [ ] **Step 2: Run tests and confirm RED**

Expected: compile failure for missing reader API.

- [ ] **Step 3: Implement reader and controller application**

Read newline-delimited input on a dedicated joinable thread. Map PS4 button bits to `OrbisPadButtonDataOffset`, apply axes without smoothing, mark controller zero connected for Bachata, and neutralize all state on EOF.

- [ ] **Step 4: Integrate only under `ENABLE_BACHATA_RUNTIME`**

Start the reader after runtime connection and stop it before controller/runtime destruction.

- [ ] **Step 5: Run focused and existing C++ tests**

Run the Bachata tests first, then `ctest --test-dir build-tests --output-on-failure -E GcnTest`.

### Task 3: Android Input Transport

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/input/ControllerSnapshot.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/input/ControllerFrameEncoder.kt`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/session/ManagedSession.kt`
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/input/ControllerFrameEncoderTest.kt`

**Interfaces:**
- Produces: immutable normalized snapshot and `ManagedSession.submitController(snapshot)`.

- [ ] **Step 1: Write failing encoder/state tests**

Verify exact wire bytes, monotonic sequence, clamping, deadzone, deduplication, and neutral frame.

- [ ] **Step 2: Run focused Gradle test and confirm RED**

Run: `./gradlew :core:runtime:testDebugUnitTest --tests '*ControllerFrameEncoderTest'`.

- [ ] **Step 3: Implement encoder and service-owned writer**

Serialize writes on the service IO dispatcher. Send changed snapshots and a neutral snapshot during stop, socket loss, or UI disposal.

- [ ] **Step 4: Run core runtime tests**

Expected: encoder and existing runtime tests pass.

### Task 4: Fixed Multitouch Overlay

**Files:**
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/controller/TouchControllerState.kt`
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/controller/FixedControllerOverlay.kt`
- Modify: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/SessionScreen.kt`
- Test: `android/BachataS4/feature/session/src/test/kotlin/com/bachatas4/android/feature/session/controller/TouchControllerStateTest.kt`

**Interfaces:**
- Consumes: Compose pointer events.
- Produces: complete `ControllerSnapshot` updates through `ManagedSession.submitController`.

- [ ] **Step 1: Write failing gesture-state tests**

Verify simultaneous stick/button pointers, stick radius/deadzone, sliding between face buttons, trigger behavior, pointer cancellation, and full neutralization.

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew :feature:session:testDebugUnitTest --tests '*TouchControllerStateTest'`.

- [ ] **Step 3: Implement fixed scalable layout**

Place the overlay in a `Box` above `SurfaceView`; scale a 1920x1080 logical layout to the viewport. Draw translucent controls and consume pointer events per pointer ID.

- [ ] **Step 4: Add lifecycle release behavior**

Send neutral state on disposal, backgrounding, session stop, and loss of focus.

- [ ] **Step 5: Run session and Android suites**

Run focused tests, then `./gradlew testDebugUnitTest lintDebug assembleDebug`.

### Task 5: Package and Device Gate 7 Controller Validation

**Files:**
- Modify: `runtime/tests/verify-runtime.mjs` only if a new runtime artifact is introduced.
- Create: `runtime/evidence/sm8650/gate-7-touch-controller.md`

**Interfaces:**
- Produces: installed APK and reproducible evidence.

- [ ] **Step 1: Rebuild and package shadPS4 runtime**

Run `runtime/scripts/build-shadps4-x86_64.sh`, `node runtime/scripts/package-runtime.mjs`, and runtime verification.

- [ ] **Step 2: Build APK from a clean state**

Run `./gradlew clean test lintDebug assembleDebug`.

- [ ] **Step 3: Install on the connected device**

Confirm exactly one authorized device, install the debug APK, and launch Sonic through MainActivity.

- [ ] **Step 4: Validate gameplay input**

Confirm movement, jump, simultaneous direction+jump, both sticks, triggers, Options, clean neutral state after touch release, stop, and relaunch.

- [ ] **Step 5: Record evidence and commit**

Record APK/runtime hashes, device profile without serial, tested actions, logs, and result. Commit only intended files with a conventional commit.
