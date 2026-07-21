# Direct Game Launch Activity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a debug-only exported Activity that launches any imported PS4 game directly from ADB by title ID.

**Architecture:** A pure resolver validates the title ID and app-owned game directory. A debug-only `DirectLaunchActivity` hosts the existing `SessionScreen`, preserving its surface, controller, audio, driver-preference, and managed-service lifecycle.

**Tech Stack:** Kotlin, Android Activity/Compose, Hilt, JUnit 4, Gradle manifest merging.

## Global Constraints

- Accept only the `game_id` Intent extra; never accept an arbitrary path.
- Export the Activity only from debug variants and do not add a launcher intent filter.
- Reuse `SessionScreen` and its existing `SessionViewModel` launch path.
- Preserve the non-exported `EmulationService`.
- Do not commit without explicit user authorization.

---

### Task 1: Resolve a direct-launch request safely

**Files:**
- Create: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/DirectGameLaunchRequest.kt`
- Test: `android/BachataS4/app/src/test/kotlin/com/bachatas4/android/DirectGameLaunchRequestTest.kt`

**Interfaces:**
- Consumes: app `filesDir` and nullable `game_id`.
- Produces: `DirectGameLaunchRequest.resolve(filesDir, rawGameId): Resolution`, where success carries the normalized game ID.

- [ ] Write JUnit tests proving `CUSA07023` succeeds only when `files/games/CUSA07023/eboot.bin` exists, while null, lowercase, traversal, and missing content fail.
- [ ] Run `./gradlew :app:testPlaystoreDebugUnitTest --tests '*DirectGameLaunchRequestTest'` and confirm the missing implementation fails compilation.
- [ ] Implement a sealed success/failure result, the `^[A-Z]{4}[0-9]{5}$` check, canonical containment beneath `files/games`, and `eboot.bin` validation.
- [ ] Rerun the focused test and require all cases to pass.

### Task 2: Host the existing session from a debug Activity

**Files:**
- Create: `android/BachataS4/app/src/debug/kotlin/com/bachatas4/android/DirectLaunchActivity.kt`
- Modify: `android/BachataS4/app/src/debug/AndroidManifest.xml`
- Test: `runtime/tests/android-direct-launch-source.test.mjs`

**Interfaces:**
- Consumes: `DirectGameLaunchRequest.EXTRA_GAME_ID` and its resolver.
- Produces: exported debug component `com.bachatas4.android/.DirectLaunchActivity`.

- [ ] Write a source/manifest regression test requiring an exported Activity without `MAIN` or `LAUNCHER`, resolver use, `SessionScreen`, and gamepad event forwarding.
- [ ] Run the Node test and confirm it fails before the Activity exists.
- [ ] Implement `@AndroidEntryPoint DirectLaunchActivity`: resolve the request, log/Toast and finish on failure, otherwise render `AppTheme { SessionScreen(gameId) }`; forward key and motion events through `GamepadInputManager`.
- [ ] Add only the Activity declaration to the debug manifest and rerun the focused tests.

### Task 3: Build and prove direct Sonic launch

**Files:**
- Verify: `android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk`

**Interfaces:**
- Consumes: installed game ID `CUSA07023`.
- Produces: a live managed FEX session without library navigation.

- [ ] Run app unit tests and `assemblePlaystoreDebug` with Java 17 and the configured Android SDK.
- [ ] Verify `assets/runtime/manifest.json` and `assets/runtime/runtime.zip`, then run `runtime/tests/verify-apk-runtime.mjs`.
- [ ] Install with `adb install -r` and launch using `adb shell am start -n com.bachatas4.android/.DirectLaunchActivity --es game_id CUSA07023`.
- [ ] Confirm the merged component resolves, a new session log names `CUSA07023`, the backend is FEX, and the render surface presents frames.
- [ ] Resume title-driven Sonic diagnosis until gameplay, then validate 60 FPS, graphics, controller, audio, and ten-minute stability on the tablet.
