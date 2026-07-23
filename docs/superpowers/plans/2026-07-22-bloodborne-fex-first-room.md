# Bloodborne FEX First-Room Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run Bloodborne (`CUSA00900`) through the title flow into the first controllable room with native ARM64 shadPS4 plus FEXCore, sustain at least 30 FPS for a controlled 30-second capture, preserve correct rendering/audio/controller behavior, and remain stable for ten minutes.

**Architecture:** Resolve the guest backend as part of the existing global/per-game runtime profile: game override, then global override, then FEX default. Pass that one resolved value through configuration constraints and process launch, while retaining Box64 as an explicit fallback. Bring Bloodborne up through the existing direct-launch activity, diagnose the earliest failure before editing emulator code, and validate each targeted fix on the tablet before moving to performance work.

**Tech Stack:** Kotlin, kotlinx.serialization, Jetpack Compose, JUnit, C++23, FEXCore, Vulkan/Turnip, CMake/Ninja, Node.js runtime verifiers, Gradle, ADB, Android logcat, simpleperf where permitted.

## Global Constraints

- Work only in `/home/jica/repo/Bachata-S4/.worktrees/fex-arm64-phase0-clean` on `feature/fex-arm64-phase0-clean`.
- Preserve dirty submodule worktrees `externals/sdl3` and `runtime/sources/box64`; never reset, clean, stage, or rewrite them.
- Do not modify `/home/jica/repo/GameNative`, replace FEX with Box64, introduce Wine/ARM64EC, or redesign the emulator.
- FEX is the default. Box64 is available only through an explicit global or per-game profile selection; never silently fall back after a FEX failure.
- Do not uninstall the app, clear app data, delete game/save/runtime data, or overwrite settings without a backup. Install only with `adb install -r`.
- Do not suppress guest-state, memory-ownership, GPU-ordering, signal, or Vulkan failures. Fix the earliest meaningful fault and retain strict invariants.
- Do not change resolution, graphics quality, CPU governor, thermal policy, or frame generation to meet the FPS target.
- Do not apply the old Box64 cache-maintenance fix to the FEX path by assumption. First prove an equivalent native ARM64 fault loop.
- Do not mark a tablet-gated step complete without evidence from device `7d6afed8`. If the device or required human controller/audio check is unavailable, leave the step in progress and request the user.
- Do not commit during this plan unless the user explicitly requests another commit.

## File Structure

| Path | Responsibility |
| --- | --- |
| `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/RuntimeProfile.kt` | Serializable backend selection and global/game inheritance input. |
| `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/RuntimeProfileResolver.kt` | Resolve game → global → FEX backend policy into the launch profile. |
| `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncher.kt` | Require an explicit resolved backend and build only its launch command. |
| `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/RuntimeLaunchProfileProvider.kt` | Apply FEX-only compatibility constraints from the resolved backend. |
| `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt` | Use the same resolved backend for config, environment, executable, logging, and launch. |
| `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/{SettingsViewModel.kt,SettingsScreen.kt}` | Global/per-game FEX and Box64 selector. |
| `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/session/FrameTelemetryReporter.kt` | Bounded FPS/frame-time samples for controlled device evidence. |
| `runtime/qualification/run-bloodborne-fex-first-room.sh` | Direct launch, focused log collection, screenshots, and bounded qualification capture. |
| `runtime/qualification/validate-bloodborne-fex-first-room.mjs` | Reject evidence missing FEX identity, 30-second FPS, correctness, or stability gates. |
| `runtime/tests/bloodborne-fex-regression.test.mjs` | Durable regression contracts added only for blockers proven by tablet evidence. |

---

## Task 1: Make FEX the resolved default and Box64 an explicit fallback

**Files:**

- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/RuntimeProfile.kt`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/RuntimeProfileResolver.kt`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncher.kt`
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/RuntimeLaunchProfileProvider.kt`
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt`
- Modify: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/settings/RuntimeProfileResolverTest.kt`
- Modify: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncherTest.kt`
- Modify: `android/BachataS4/app/src/test/kotlin/com/bachatas4/android/RuntimeLaunchProfileProviderTest.kt`

**Contract:** `RuntimeProfile.guestBackend` is nullable: `null` means inherit for a game profile and means use the product default for a global profile. Resolution order is game override → global override → `FEX`. `ResolvedRuntimeProfile.guestBackend` is never null. The process request has no backend default, preventing accidental Box64 fallback.

- [ ] Add resolver tests for default FEX, global Box64, per-game Box64, per-game FEX overriding global Box64, and null game inheritance.
- [ ] Update provider tests so both `CUSA07023` and `CUSA00900` resolve FEX without title allow-listing; verify `gpu.copy_gpu_buffers` has `ValueSource.COMPATIBILITY` only when the resolved backend is FEX.
- [ ] Update launcher tests so every request supplies a backend; verify FEX uses `host/shadps4-arm64-fex`, Box64 uses `bin/shadps4`, and neither branch can silently select the other executable.
- [ ] Run the focused tests before implementation and record the expected RED failures:

  ```bash
  cd android/BachataS4
  ./gradlew :core:runtime:test :app:testPlaystoreDebugUnitTest --tests '*RuntimeProfileResolverTest' --tests '*RuntimeProcessLauncherTest' --tests '*RuntimeLaunchProfileProviderTest'
  ```

- [ ] Move `RuntimeGuestBackend` into the settings/profile contract and mark it serializable; add `guestBackend: RuntimeGuestBackend? = null` to `RuntimeProfile` and a non-null field to `ResolvedRuntimeProfile`.
- [ ] Resolve the backend once from the loaded global and game profiles. Remove `FEX_SONIC_GAME_ID` and `guestBackend(gameId)`.
- [ ] Make `RuntimeLaunchProfileProvider.resolve()` apply `FEX_COMPATIBILITY_CONSTRAINTS` from that resolved backend, not from the title ID.
- [ ] Make `EmulationService` use `launchProfile.guestBackend` for the log, Box64 environment, executable, and `RuntimeProcessRequest`.
- [ ] Re-run the focused command. Expected: all named tests pass and logs retain the exact `guestBackend=fex|box64` value.

## Task 2: Expose global and per-game backend selection

**Files:**

- Modify: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsViewModel.kt`
- Modify: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsScreen.kt`
- Modify: `android/BachataS4/feature/settings/src/test/kotlin/com/bachatas4/android/feature/settings/SettingsViewModelTest.kt`
- Modify: `android/BachataS4/feature/settings/src/test/kotlin/com/bachatas4/android/feature/settings/ProfileTransferTest.kt`
- Modify: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/RuntimeProfileStoreTest.kt`

**Contract:** The Runtime settings tab shows “Guest CPU backend.” Global scope offers FEX and Box64 and displays FEX when no value is stored. Game scope offers Inherit, FEX, and Box64; Inherit persists null. Import/export and atomic profile storage preserve an explicit backend.

- [ ] Add ViewModel tests for setting FEX/Box64 and clearing a game override to inherit.
- [ ] Add store and transfer round-trip tests for `guestBackend`, including an older JSON profile with no field resolving safely.
- [ ] Run the focused tests and record RED:

  ```bash
  cd android/BachataS4
  ./gradlew :core:data:test :feature:settings:test --tests '*RuntimeProfileStoreTest' --tests '*ProfileTransferTest' --tests '*SettingsViewModelTest'
  ```

- [ ] Add `SettingsViewModel.setGuestBackend(RuntimeGuestBackend?)` using the existing atomic `RuntimeProfileStore.update()` path.
- [ ] Add the selector to the Runtime tab. Show Inherit only for `ProfileScope.Game`; keep the existing “changes apply next launch” behavior.
- [ ] Re-run the focused command. Expected: all tests pass; no migration rewrites an existing value or unrelated profile field.

## Task 3: Add bounded performance evidence

**Files:**

- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/session/FrameTelemetryReporter.kt`
- Create: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/session/FrameTelemetryReporterTest.kt`
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt`
- Create: `runtime/qualification/validate-bloodborne-fex-first-room.mjs`
- Create: `runtime/tests/validate-bloodborne-fex-first-room.test.mjs`

**Contract:** While frames arrive, write at most one `Performance` record every two seconds with monotonic elapsed time, FPS, and frame time. The validator requires at least 30 seconds of consecutive in-room samples whose minimum FPS is 30, plus explicit backend/render/audio/controller/stability evidence. It must reject missing, malformed, mixed-backend, short, or sub-30 captures.

- [ ] Write failing reporter tests for rate limiting, monotonic timestamps, reset, and locale-independent decimal output.
- [ ] Write failing Node fixtures for valid evidence and each rejection case.
- [ ] Run RED:

  ```bash
  cd android/BachataS4
  ./gradlew :core:runtime:test --tests '*FrameTelemetryReporterTest'
  cd ../..
  node --test runtime/tests/validate-bloodborne-fex-first-room.test.mjs
  ```

- [ ] Implement the pure reporter and call it from the existing `BACHATA/1 EVENT Frame` branch after `ManagedSession.recordPresentedFrame()`.
- [ ] Implement the validator with a documented JSON schema/version and no guessed audio/controller status.
- [ ] Re-run both commands. Expected: all reporter and validator cases pass; production logging is bounded to 0.5 Hz.

## Task 4: Build, package, verify, and replacement-install Play Store debug

**Files:**

- Consume: `runtime/scripts/build-shadps4-x86_64.sh`
- Consume: `runtime/scripts/build-box64-host.sh`
- Consume: `runtime/scripts/build-shadps4-arm64.sh`
- Consume: `runtime/scripts/stage-debian-runtime.mjs`
- Consume: `runtime/scripts/package-runtime.mjs`
- Generate (ignored): `android/BachataS4/app/src/main/assets/runtime/{manifest.json,runtime.zip}`
- Generate (ignored): `android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk`

- [ ] Confirm the only pre-existing dirt is the two protected submodule worktrees, then initialize submodules:

  ```bash
  git status --short
  git submodule update --init --recursive --jobs 8
  ```

- [ ] Build every managed-runtime component in the required order. Reuse Box64 only if its hash/ABI cache verifies; a cache failure requires a normal Box64 rebuild.

  ```bash
  runtime/scripts/build-shadps4-x86_64.sh
  BACHATA_SKIP_BOX64_BUILD=1 runtime/scripts/build-box64-host.sh
  bash runtime/scripts/build-shadps4-arm64.sh
  node runtime/scripts/stage-debian-runtime.mjs
  node runtime/scripts/package-runtime.mjs
  node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
  ```

- [ ] Run Android verification and build the requested flavor:

  ```bash
  cd android/BachataS4
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  export ANDROID_HOME=$HOME/Android/Sdk
  ./gradlew test lintDebug assemblePlaystoreDebug
  cd ../..
  ```

- [ ] Verify the APK gates before installation:

  ```bash
  unzip -l android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk \
    | grep -E 'assets/runtime/(manifest\.json|runtime\.zip)'
  node runtime/tests/verify-playstore-bundled-turnip.mjs \
    android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
  ```

  Expected: both managed-runtime assets are listed and the exact locked Play Store Turnip asset passes its hash check.

- [ ] Back up live global/game settings and identify the currently selected driver before replacement install:

  ```bash
  adb -s 7d6afed8 exec-out run-as com.bachatas4.android cat files/settings/global.json \
    > /tmp/bachata-global-before-bloodborne.json
  adb -s 7d6afed8 exec-out run-as com.bachatas4.android cat files/settings/games/CUSA00900.json \
    > /tmp/bachata-bloodborne-profile-before.json 2>/dev/null || true
  adb -s 7d6afed8 install -r \
    android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
  ```

- [ ] Restore `files/settings/global.pre-copy-test.json` only if it exists and differs from the diagnostic profile, then verify its audio/profile fields survived. Do not clear data. Expected: install reports `Success`, app settings remain readable, and absent backend fields resolve to FEX.

## Task 5: Capture Bloodborne’s earliest FEX failure boundary

**Files:**

- Create: `runtime/qualification/run-bloodborne-fex-first-room.sh`
- Generate outside Git: `/tmp/bloodborne-fex-baseline/{application.log,shadps4.log,shadps4-internal.log,logcat.txt,screen.png,metadata.json}`
- Append only after proof: `runtime/tests/bloodborne-fex-regression.test.mjs`

- [ ] Implement the qualification script with device/package/title arguments, `set -euo pipefail`, bounded waits, direct launch, latest-session discovery, log pulling, screenshot capture, and sanitized metadata. It must never uninstall, clear, delete, or mutate game/save data.
- [ ] Unit-check the script’s source contract and syntax:

  ```bash
  bash -n runtime/qualification/run-bloodborne-fex-first-room.sh
  node --test runtime/tests/android-direct-launch-source.test.mjs
  ```

- [ ] Stop any prior session, clear logcat only, and launch the title directly:

  ```bash
  adb -s 7d6afed8 shell am force-stop com.bachatas4.android
  adb -s 7d6afed8 logcat -c
  adb -s 7d6afed8 shell am start -S \
    -n com.bachatas4.android/.DirectLaunchActivity --es game_id CUSA00900
  ```

- [ ] Reproduce until either the first controllable room or the first fatal/stall. Capture focused logcat, the latest app-private session logs, a screenshot, and tombstone listing where accessible.
- [ ] Prove the session log says `guestBackend=fex`. If it says Box64 or omits the field, stop and return to Task 1.
- [ ] Identify the earliest meaningful failure—not the last cascade—and record function, thread, guest RIP/host PC when available, error code, and preceding successful stage in `metadata.json`.
- [ ] If Bloodborne reaches the room, skip directly to Task 7. Otherwise add one failing regression to `runtime/tests/bloodborne-fex-regression.test.mjs` that reproduces the proven boundary; do not encode a speculative symptom.

## Task 6: Fix proven blockers one at a time

**Files:**

- Modify only the exact source implicated by Task 5 evidence.
- Append: `runtime/tests/bloodborne-fex-regression.test.mjs`
- Common guest execution candidates, only when named by the trace: `src/core/fex/fex_guest_engine.{h,cpp}`, `src/core/{linker.cpp,module.cpp}`.
- Common native signal candidate, only for a proven ARM64 fault classification: `src/common/{signal_context.h,signal_context.cpp}`.
- Common presentation candidates, only when named by the trace: `src/video_core/renderer_vulkan/{vk_presenter.cpp,vk_swapchain.cpp}` and `src/video_core/amdgpu/liverpool.cpp`.

**Contract:** Every iteration has one captured failure, one failing regression, one minimal implementation, host verification, a rebuilt runtime, and tablet revalidation. No catch-all import, ignored return, arbitrary sleep, graphics downgrade, or Box64 fallback is permitted.

- [ ] Classify the first boundary as configuration/launch, guest execution/import, rendering/presentation, input/audio, or performance.
- [ ] Write the smallest failing test. Run it alone and confirm RED for the observed reason.
- [ ] Implement the minimal fix in the source file named by the trace; add stage/error logging only when it is bounded and needed to distinguish ownership or lifetime.
- [ ] Run the new regression plus the existing FEX suites:

  ```bash
  node --test runtime/tests/bloodborne-fex-regression.test.mjs
  node --test runtime/tests/fex-guest-engine-source.test.mjs \
    runtime/tests/fex-guest-cpu-source.test.mjs \
    runtime/tests/fex-guest-entry-source.test.mjs \
    runtime/tests/fex-sonic-guest-backend-source.test.mjs
  ```

- [ ] Repeat Task 4’s build/package/APK/install gates, then repeat Task 5 on the tablet.
- [ ] Continue until the existing save reaches the first controllable room. If a new failure appears, it becomes the next iteration; never mark this task complete on logo/title-only progress.

## Task 7: Profile the controlled first-room scene

**Files:**

- Generate outside Git: `/tmp/bloodborne-fex-profile-before/*`
- Conditionally modify after proof: `src/common/{signal_context.h,signal_context.cpp}`
- Conditionally create after proof: `tests/common/test_signal_context_arm64.cpp`
- Conditionally modify after proof: `tests/CMakeLists.txt`

- [ ] With the same save, camera, Turnip driver, resolution, and stationary scene, collect 30 seconds of bounded telemetry, start/end screenshots, eight seconds of page-fault samples, process/thread CPU, GPU busy where readable, scheduler counters, and thermal status.
- [ ] Record the minimum/median/95th-percentile FPS and frame time. Do not average away sub-30 intervals.
- [ ] Inspect native PCs and disassembly for repeated `dc cvac`/`dc civac` or an equivalent protected-page retry loop.
- [ ] Only if that loop is proven, add a pure AArch64 opcode-classification regression for exact `DC CVAC, Xt` and `DC CIVAC, Xt`, with `DC ISW` and ordinary loads as negatives. Integrate it into `Common::IsWriteError()` without widening security or signal handling.
- [ ] Require at least a 90% reduction in the proven fault site without stale frames or corruption. If FPS remains below 30, profile again and identify the newly exposed bottleneck before changing another subsystem.
- [ ] Repeat the evidence/fix loop until the stationary 30-second capture has minimum FPS ≥30 and screenshots show complete, current frames.

## Task 8: Tablet acceptance and regression closure

**Files:**

- Generate outside Git: `/tmp/bloodborne-fex-final/*`
- Produce: `/tmp/bloodborne-fex-final/evidence.json`

- [ ] Rebuild and replacement-install one final verified Play Store APK using Task 4’s complete gates.
- [ ] Confirm `guestBackend=fex`, title-to-room progress, and no automatic Box64 process in logs or process listings.
- [ ] Ask the user to verify controller movement/camera and audible, stable game audio while the first room is active. Leave this checkbox open until confirmed.
- [ ] Capture the fixed 30-second stationary scene. Require every qualified sample ≥30 FPS and no missing geometry, corruption, or stale presentation in start/end screenshots.
- [ ] Continue in the room for ten uninterrupted minutes. Reject any fatal guest, FEX, Vulkan, flip-order, ownership, process death, or surface failure.
- [ ] Run the evidence validator:

  ```bash
  node runtime/qualification/validate-bloodborne-fex-first-room.mjs \
    /tmp/bloodborne-fex-final/evidence.json
  ```

- [ ] Run final host verification:

  ```bash
  node --test runtime/tests/bloodborne-fex-regression.test.mjs \
    runtime/tests/validate-bloodborne-fex-first-room.test.mjs
  cd android/BachataS4
  ./gradlew test lintDebug
  ```

- [ ] Inspect `git diff --check` and `git status --short`. Confirm the two protected dirty submodules were not modified by this work. Report first-room scope accurately; do not claim whole-game compatibility.

## Plan Review

- Every acceptance item in `docs/superpowers/specs/2026-07-22-bloodborne-fex-first-room-design.md` maps to Task 8.
- Backend default, global fallback, and per-game fallback are persisted and tested before any game-specific fix.
- The plan does not assume the old Box64 cache fault exists in native FEX execution; Task 7 requires proof first.
- Build order includes every managed-runtime command required by `AGENTS.md`, plus the ARM64 FEX build/staging steps that Gradle does not perform.
- Device-dependent work is explicitly gated and cannot be marked complete from host tests alone.
- No placeholders, fake APIs, broad catches, hidden fallback, destructive device command, or speculative quality reduction is permitted.
