# Touch Controller Player-One Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route virtual touch-controller snapshots to player one so taps reach the emulator transport.

**Architecture:** Preserve the existing one-argument `ManagedSession.submitController` API as the player-one compatibility entry point. Delegate it to the slot-aware overload with slot `0`, which already forwards input to the emulation service's active transport. This keeps the Compose overlay independent of the multi-controller transport implementation.

**Tech Stack:** Kotlin, JUnit 4, Gradle Android unit tests.

## Global Constraints

- `ManagedSession.submitController(snapshot)` must remain source-compatible and target player one (slot `0`).
- `ManagedSession.submitController(slot, snapshot)` retains its existing four-slot validation and dispatch behavior.
- Do not add a second service callback or alter the touch-overlay UI.
- Write and observe the regression test fail before changing production code.

---

### Task 1: Route legacy controller submission to player one

**Files:**
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/session/ManagedSession.kt:50-59`
- Modify: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/input/ControllerFrameEncoderTest.kt:8-28`

**Interfaces:**
- Consumes: `ManagedSession.attachControllerSlotSink((Int, ControllerSnapshot) -> Unit)` and `ManagedSession.submitController(snapshot: ControllerSnapshot)`.
- Produces: `ManagedSession.submitController(snapshot: ControllerSnapshot)` dispatches the snapshot to `submitController(0, snapshot)`.

- [ ] **Step 1: Write the failing regression test**

Add this test to `ControllerFrameEncoderTest` after `managedSessionRoutesFourSlots`:

```kotlin
@Test fun legacySubmissionRoutesToSlotZero() {
    val received = mutableListOf<Pair<Int, ControllerSnapshot>>()
    val sink: (Int, ControllerSnapshot) -> Unit = { slot, snapshot -> received += slot to snapshot }
    val snapshot = ControllerSnapshot.normalized(buttons = Ps4Button.CROSS)
    ManagedSession.attachControllerSlotSink(sink)

    try {
        ManagedSession.submitController(snapshot)
    } finally {
        ManagedSession.detachControllerSlotSink(sink)
    }

    assertEquals(listOf(0 to snapshot), received)
}
```

- [ ] **Step 2: Run the regression test and verify it fails**

Run:

```bash
cd android/BachataS4
./gradlew :core:runtime:testDebugUnitTest \
  --tests com.bachatas4.android.runtime.input.ControllerFrameEncoderTest.legacySubmissionRoutesToSlotZero
```

Expected: the assertion fails because `received` is empty; the one-argument overload invokes only the unattached legacy sink.

- [ ] **Step 3: Implement the minimal compatibility delegation**

Replace the legacy overload in `ManagedSession.kt` with:

```kotlin
fun submitController(snapshot: ControllerSnapshot) { submitController(0, snapshot) }
```

Do not change `attachControllerSlotSink`, `detachControllerSlotSink`, or the two-argument overload.

- [ ] **Step 4: Re-run the regression test and verify it passes**

Run:

```bash
cd android/BachataS4
./gradlew :core:runtime:testDebugUnitTest \
  --tests com.bachatas4.android.runtime.input.ControllerFrameEncoderTest.legacySubmissionRoutesToSlotZero
```

Expected: `BUILD SUCCESSFUL`; the slot-aware sink receives exactly `(0, snapshot)`.

- [ ] **Step 5: Run the affected unit-test suites**

Run:

```bash
cd android/BachataS4
./gradlew :core:runtime:testDebugUnitTest :feature:session:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; no existing controller transport or touch-state test regresses.

- [ ] **Step 6: Commit the implementation**

```bash
git add android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/session/ManagedSession.kt \
  android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/input/ControllerFrameEncoderTest.kt
git commit -m "fix(controller): route legacy input to slot zero"
```
