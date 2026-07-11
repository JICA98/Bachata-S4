# Portrait Console UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved portrait-only Library, Settings, and Setup screens, plus a fixed-landscape immersive session and controller overlay, in Kotlin/Compose without losing current app behavior.

**Architecture:** Existing view models, repositories, profile scopes, and navigation callbacks remain the behavioral layer. `core:designsystem` supplies the visual primitives, portrait routes consume those primitives, and a session-only Compose effect owns Android orientation/system-bar mutation for its composition lifetime.

**Tech Stack:** Kotlin 2.4, Jetpack Compose Material 3, AndroidX Core window insets, Navigation Compose, Hilt, JUnit 4, Gradle.

## Global Constraints

- Normal routes use `ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT`; session uses `ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE` only while `SessionScreen` is composed.
- Session hides `WindowInsetsCompat.Type.systemBars()` and restores visible system bars plus sensor portrait on dispose.
- Preserve game import, launch, `BachataRoutes.gameSettings(id)`, `ProfileScope.Game(id)`, inheritance, all settings utilities, controller mappings, touch-layout editor, and persisted touch inputs.
- Use the approved colors: `#0A0A0C`, `#1C1C20`, `#26262B`, `#F5F4F1`, `#8B8D94`, and `#E8A33D`.
- Do not add Share/PS runtime buttons merely to match the HTML; the restyled overlay emits the exact existing `ControllerSnapshot` semantics.
- Before any APK install or publication, follow the runtime packaging and `unzip` verification in `AGENTS.md`.

## File Map

| File | Responsibility |
| --- | --- |
| `core/designsystem/.../theme/Theme.kt` | Bachata Material color scheme. |
| `core/designsystem/.../BachataChrome.kt` | Header, panels, buttons, and bottom action hints. |
| `feature/session/.../SessionWindowMode.kt` | Testable portrait/immersive window policy. |
| `feature/session/.../SessionWindowModeEffect.kt` | Applies and restores Android window state. |
| `feature/library/.../LibraryScreen.kt` | Prototype-derived Library composition. |
| `feature/settings/.../SettingsScreen.kt` | Tabbed portrait settings cards. |
| `feature/setup/.../SetupScreen.kt` | Single-page onboarding. |
| `feature/session/.../controller/TouchControlVisualStyle.kt` | Explicit visual mapping for persisted controls. |
| `feature/session/.../controller/FixedControllerOverlay.kt` | Functional controller drawing and Fade affordance. |

---

### Task 1: Create the shared Bachata Compose language

**Files:**
- Modify: `android/BachataS4/core/designsystem/build.gradle.kts`
- Modify: `android/BachataS4/core/designsystem/src/main/kotlin/com/bachatas4/android/designsystem/theme/Theme.kt`
- Create: `android/BachataS4/core/designsystem/src/main/kotlin/com/bachatas4/android/designsystem/BachataChrome.kt`
- Create: `android/BachataS4/core/designsystem/src/test/kotlin/com/bachatas4/android/designsystem/theme/BachataPaletteTest.kt`

**Interfaces:** Produces `BachataPalette`, `BachataScreenHeader`, `BachataPanel`, `BachataPrimaryButton`, and `BachataActionBar` for feature modules; consumes only Compose/Material dependencies exported by the design-system module.

- [ ] **Step 1: Write the failing palette test**

```kotlin
class BachataPaletteTest {
    @Test fun usesApprovedStageColors() {
        assertEquals(Color(0xFF0A0A0C), BachataPalette.Canvas)
        assertEquals(Color(0xFF1C1C20), BachataPalette.Surface)
        assertEquals(Color(0xFF26262B), BachataPalette.RaisedSurface)
        assertEquals(Color(0xFFF5F4F1), BachataPalette.Primary)
        assertEquals(Color(0xFF8B8D94), BachataPalette.Secondary)
        assertEquals(Color(0xFFE8A33D), BachataPalette.Accent)
    }
}
```

- [ ] **Step 2: Verify RED**

Run: `cd android/BachataS4 && ./gradlew :core:designsystem:testDebugUnitTest`

Expected: FAIL because `BachataPalette` is unresolved.

- [ ] **Step 3: Add the minimum shared implementation**

Add `testImplementation(libs.junit)` to the design-system module and put this palette in `Theme.kt`:

```kotlin
object BachataPalette {
    val Canvas = Color(0xFF0A0A0C)
    val Surface = Color(0xFF1C1C20)
    val RaisedSurface = Color(0xFF26262B)
    val Primary = Color(0xFFF5F4F1)
    val Secondary = Color(0xFF8B8D94)
    val Accent = Color(0xFFE8A33D)
    val OnAccent = Color(0xFF1A1206)
}

private val BachataColors = darkColorScheme(
    primary = BachataPalette.Accent,
    onPrimary = BachataPalette.OnAccent,
    background = BachataPalette.Canvas,
    surface = BachataPalette.Surface,
    surfaceVariant = BachataPalette.RaisedSurface,
    onBackground = BachataPalette.Primary,
    onSurface = BachataPalette.Primary,
    onSurfaceVariant = BachataPalette.Secondary,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = BachataColors, content = content)
```

Implement `BachataChrome.kt` with a full-width `BachataScreenHeader(title, onBack, actions)`, `BachataPanel` backed by `Surface(color = BachataPalette.Surface, shape = RoundedCornerShape(12.dp))`, amber `BachataPrimaryButton`, and a bottom `BachataActionBar(vararg hints: String)`. Use 16–24 dp portrait spacing and these palette values; do not duplicate color constants in feature code.

- [ ] **Step 4: Verify GREEN**

Run: `cd android/BachataS4 && ./gradlew :core:designsystem:testDebugUnitTest`

Expected: `BachataPaletteTest` passes.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/core/designsystem
git commit -m "feat(ui): add Bachata portrait design system"
```

### Task 2: Make normal screens portrait and gameplay immersive landscape

**Files:**
- Modify: `android/BachataS4/app/src/main/AndroidManifest.xml`
- Modify: `android/BachataS4/feature/session/build.gradle.kts`
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/SessionWindowMode.kt`
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/SessionWindowModeEffect.kt`
- Create: `android/BachataS4/feature/session/src/test/kotlin/com/bachatas4/android/feature/session/SessionWindowModeTest.kt`
- Modify: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/SessionScreen.kt`

**Interfaces:** Produces `SessionWindowMode.Portrait`, `SessionWindowMode.ImmersiveLandscape`, and `SessionWindowModeEffect()`. The effect consumes the session composition lifetime, a host `Activity`, and AndroidX window insets.

- [ ] **Step 1: Write the failing window-policy tests**

```kotlin
class SessionWindowModeTest {
    @Test fun gameplayIsFixedLandscapeAndImmersive() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, SessionWindowMode.ImmersiveLandscape.orientation)
        assertTrue(SessionWindowMode.ImmersiveLandscape.hideSystemBars)
    }

    @Test fun normalRoutesUseSensorPortraitAndVisibleBars() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, SessionWindowMode.Portrait.orientation)
        assertFalse(SessionWindowMode.Portrait.hideSystemBars)
    }
}
```

- [ ] **Step 2: Verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:session:testDebugUnitTest`

Expected: FAIL because `SessionWindowMode` is unresolved.

- [ ] **Step 3: Implement the policy, effect, and portrait baseline**

Add `implementation(libs.androidx.core.ktx)` to `feature/session/build.gradle.kts`; set `android:screenOrientation="sensorPortrait"` on `MainActivity`; then add:

```kotlin
enum class SessionWindowMode(val orientation: Int, val hideSystemBars: Boolean) {
    Portrait(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, false),
    ImmersiveLandscape(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, true),
}
```

`SessionWindowModeEffect` must use `LocalContext`, `LocalView`, and `DisposableEffect`: find the host `Activity`, set `requestedOrientation` to immersive landscape, hide `WindowInsetsCompat.Type.systemBars()`, and configure `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Its `onDispose` must set sensor portrait and show system bars. Invoke it at the top of `SessionScreen`.

- [ ] **Step 4: Keep current session controls in the fullscreen layout**

Replace the outer `Column` of `SessionScreen` with a full-size `Box`. Keep `SurfaceView`, surface attachment, `FixedControllerOverlay`, telemetry, failure detail, optional Driver action, and stop-service intent as overlays. Do not remove `viewModel.launch(gameId)` or `ManagedSession::submitController`.

- [ ] **Step 5: Verify GREEN and commit**

Run: `cd android/BachataS4 && ./gradlew :feature:session:testDebugUnitTest`

Expected: `SessionWindowModeTest` and existing controller tests pass.

```bash
git add android/BachataS4/app/src/main/AndroidManifest.xml android/BachataS4/feature/session
git commit -m "feat(session): lock gameplay to immersive landscape"
```

### Task 3: Build the selected-game portrait Library

**Files:**
- Modify: `android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryViewModel.kt`
- Modify: `android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt`
- Modify: `android/BachataS4/feature/library/src/test/kotlin/com/bachatas4/android/feature/library/LibraryViewModelTest.kt`

**Interfaces:** `LibraryUiState.selectedGameId` identifies the first sorted game whenever the old selection disappears. `LibraryContent` gains `onSelectGame` while retaining its current import, launch, global-settings, and per-game-settings callbacks.

- [ ] **Step 1: Write the failing selection regression test**

```kotlin
@Test fun keepsSelectionWhenPresentOtherwiseSelectsFirstSortedGame() {
    val viewModel = LibraryViewModel()
    viewModel.setGames(listOf(game("B", "Beta"), game("A", "Alpha")))
    assertEquals("A", viewModel.state.value.selectedGameId)
    viewModel.selectGame("B")
    viewModel.setGames(listOf(game("B", "Beta"), game("A", "Alpha")))
    assertEquals("B", viewModel.state.value.selectedGameId)
}

private fun game(id: String, title: String) = Game(id, title, "games/$id")
```

- [ ] **Step 2: Verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:library:testDebugUnitTest`

Expected: FAIL because `setGames` leaves `selectedGameId` null.

- [ ] **Step 3: Implement selection and visual composition**

Sort once in `setGames`, retain the selected ID only if still present, otherwise assign `sorted.firstOrNull()?.id`. Replace `LibraryContent`'s plain `Column` with a `Scaffold` using the shared header/panels/action bar: branded header with Settings and Import, selected-game hero, Resume/Launch button, import/error panel, horizontal Continue Playing shelf, and All Games grid. A card selects on tap, launches through the primary action, and exposes **Game settings** as an overflow/context action. Keep Import primary in the empty state and do not add fake cover-art persistence or mutate `Game`.

- [ ] **Step 4: Verify GREEN and commit**

Run: `cd android/BachataS4 && ./gradlew :feature:library:testDebugUnitTest`

Expected: the existing sort test and new selection test pass.

```bash
git add android/BachataS4/feature/library
git commit -m "feat(library): add portrait game dashboard"
```

### Task 4: Recompose Settings into category tabs and cards

**Files:**
- Modify: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsViewModel.kt`
- Modify: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsScreen.kt`
- Modify: `android/BachataS4/feature/settings/src/test/kotlin/com/bachatas4/android/feature/settings/SettingsViewModelTest.kt`

**Interfaces:** Adds `SettingsUiState.selectedCategory: String?` and `SettingsViewModel.selectCategory(category: String?)`, where null means All. Scope/profile and all existing operation callbacks stay unchanged.

- [ ] **Step 1: Write the failing category test**

```kotlin
@Test fun selectedCategoryFiltersTheActiveRuntimeWithoutChangingScope() {
    val viewModel = viewModel()
    viewModel.selectCategory("Audio")
    assertTrue(viewModel.state.value.settings.isNotEmpty())
    assertTrue(viewModel.state.value.settings.all { it.category == "Audio" })
    assertEquals(ProfileScope.Global, viewModel.state.value.scope)
}
```

- [ ] **Step 2: Verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:settings:testDebugUnitTest`

Expected: FAIL because `selectCategory` does not exist.

- [ ] **Step 3: Implement category-aware state and screen**

Add `selectedCategory` to `SettingsUiState`. Extend `visible(runtime, query, category)` to keep the current text match and require `category == null || spec.category == category`; recalculate it from `selectRuntime`, `search`, and `selectCategory`. In `SettingsScreen`, retain the three early-return detail screens. Replace `SettingsContent` with a shared-chrome `Scaffold` containing header/back, a **Global** or **Game: <id>** scope chip, runtime selector, horizontal All/current-runtime category tabs, search, utility actions for Drivers/Raw/Controllers/Touch layout/JSON import/export, Box64 preset cards, and card-styled `SettingEditor` rows. A game-scope reset remains **Inherit** and invokes the exact existing `onReset` callback.

- [ ] **Step 4: Verify GREEN and commit**

Run: `cd android/BachataS4 && ./gradlew :feature:settings:testDebugUnitTest`

Expected: catalog/search tests and category test pass.

```bash
git add android/BachataS4/feature/settings
git commit -m "feat(settings): add portrait tabbed settings UI"
```

### Task 5: Replace setup with one-page onboarding

**Files:**
- Modify: `android/BachataS4/feature/setup/src/main/kotlin/com/bachatas4/android/feature/setup/SetupViewModel.kt`
- Modify: `android/BachataS4/feature/setup/src/main/kotlin/com/bachatas4/android/feature/setup/SetupScreen.kt`
- Modify: `android/BachataS4/feature/setup/src/test/kotlin/com/bachatas4/android/feature/setup/SetupViewModelTest.kt`

**Interfaces:** Adds `SetupReadiness` and `SetupUiState.readiness`; keeps existing asset extraction/download and `canEnterLibrary` as the only readiness authority.

- [ ] **Step 1: Write the failing readiness test**

```kotlin
@Test fun reportsMissingRuntimeBeforeTheContinueGateOpens() {
    val state = SetupUiState(
        deviceProfile = DeviceProfile("SM8650", "Adreno", true),
        runtimeInstalled = false,
        integrityVerified = false,
        legalNotice = "notice",
    )
    assertEquals(SetupReadiness.RuntimeRequired, state.readiness)
    assertFalse(state.canEnterLibrary)
}
```

- [ ] **Step 2: Verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:setup:testDebugUnitTest`

Expected: FAIL because `SetupReadiness` and `readiness` are unresolved.

- [ ] **Step 3: Implement model and branded screen**

```kotlin
enum class SetupReadiness { Ready, UnsupportedDevice, RuntimeRequired, IntegrityRequired }

val SetupUiState.readiness: SetupReadiness
    get() = when {
        !deviceProfile.supported -> SetupReadiness.UnsupportedDevice
        !runtimeInstalled -> SetupReadiness.RuntimeRequired
        !integrityVerified -> SetupReadiness.IntegrityRequired
        else -> SetupReadiness.Ready
    }
```

Replace the diagnostic column with a centered portrait `Scaffold`: show the current launcher icon with an `AndroidView` `ImageView` that calls `setImageResource(context.applicationInfo.icon)`; title; legal copy; a concise readiness panel; optional device detail; conditional **Download Emulation Assets** action; and existing enabled-only **Continue**. The screen consumes `readiness` but does not change the view model’s download/install flow.

- [ ] **Step 4: Verify GREEN and commit**

Run: `cd android/BachataS4 && ./gradlew :feature:setup:testDebugUnitTest`

Expected: readiness and existing unsupported-device tests pass.

```bash
git add android/BachataS4/feature/setup
git commit -m "feat(setup): add branded onboarding screen"
```

### Task 6: Restyle the functional touch controller

**Files:**
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/controller/TouchControlVisualStyle.kt`
- Modify: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/controller/FixedControllerOverlay.kt`
- Modify: `android/BachataS4/feature/session/src/test/kotlin/com/bachatas4/android/feature/session/controller/TouchControllerStateTest.kt`
- Create: `android/BachataS4/feature/session/src/test/kotlin/com/bachatas4/android/feature/session/controller/TouchControlVisualStyleTest.kt`

**Interfaces:** Produces `TouchControlVisualStyle.forControl(control)` for Canvas drawing. It consumes `TouchLayout` placement/style properties and the unchanged `TouchControllerState` event pipeline.

- [ ] **Step 1: Write failing style and input-preservation tests**

```kotlin
@Test fun mapsEveryDefaultControlToAVisualStyle() {
    TouchLayout.defaultControls().forEach { placement ->
        assertNotEquals(TouchControlVisualStyle.Unknown, TouchControlVisualStyle.forControl(placement.control))
    }
}

@Test fun touchpadAndOptionsRemainMappedAfterTheVisualRedesign() {
    val state = TouchControllerState()
    TouchLayout.defaultControls().filter { it.control in setOf("touchpad", "options") }.forEachIndexed { index, placement ->
        state.pointerDown(index.toLong(), placement.centerX * 1920f, placement.centerY * 1080f)
    }
    assertTrue(state.snapshot().buttons and Ps4Button.TOUCH_PAD != 0L)
    assertTrue(state.snapshot().buttons and Ps4Button.OPTIONS != 0L)
}
```

- [ ] **Step 2: Verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:session:testDebugUnitTest`

Expected: FAIL because `TouchControlVisualStyle` is unresolved.

- [ ] **Step 3: Add visual mapping and renderer**

```kotlin
enum class TouchControlVisualStyle { Face, Dpad, Stick, Shoulder, Trigger, Touchpad, Center, Unknown;
    companion object {
        fun forControl(control: String) = when (control) {
            "triangle", "circle", "cross", "square" -> Face
            "dpad_up", "dpad_right", "dpad_down", "dpad_left" -> Dpad
            "left_stick", "right_stick" -> Stick
            "l1", "r1" -> Shoulder
            "l2", "r2" -> Trigger
            "touchpad" -> Touchpad
            "options" -> Center
            else -> Unknown
        }
    }
}
```

Make `FixedControllerOverlay` a `Box` that keeps its pointer-input Canvas and adds a small Fade control. Preserve logical-coordinate conversion and all `pointerDown`, `pointerMove`, and `pointerUp` calls. Render the reference’s translucent dark shoulders/triggers, segmented D-pad, inset stick bases/knobs, touchpad/options controls, and colored outlined face symbols (green triangle, pink circle, blue cross, purple square). Apply saved opacity/scale everywhere. Fade changes render alpha only; do not persist it or add unsupported inputs.

- [ ] **Step 4: Verify GREEN and commit**

Run: `cd android/BachataS4 && ./gradlew :feature:session:testDebugUnitTest`

Expected: new style coverage and all existing touch input tests pass.

```bash
git add android/BachataS4/feature/session
git commit -m "feat(controller): restyle touch gamepad overlay"
```

### Task 7: Integrate and verify the Android build

**Files:** Modify only the preceding files for concrete compiler/lint findings.

**Interfaces:** Produces a debug APK that contains both managed runtime assets and the approved UI behavior.

- [ ] **Step 1: Run affected tests and lint**

```bash
cd android/BachataS4
./gradlew :core:designsystem:testDebugUnitTest :feature:library:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:setup:testDebugUnitTest :feature:session:testDebugUnitTest
./gradlew lintDebug
```

Expected: exit code 0, no test failures, no lint errors.

- [ ] **Step 2: Package runtime, assemble, and inspect APK contents**

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
cd android/BachataS4
./gradlew test lintDebug assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E 'assets/runtime/(manifest\.json|runtime\.zip)'
```

Expected: all commands succeed and `unzip` lists both `assets/runtime/manifest.json` and `assets/runtime/runtime.zip`.

- [ ] **Step 3: Manually verify device behavior**

Check Setup portrait/onboarding/logo/readiness; Library import, launch, global settings, and per-game settings; Settings category coverage and inherited game values; Session landscape/system-bar hiding, touch input, temporary fade, and restored portrait/system bars on exit.

- [ ] **Step 4: Commit integration fixes only when needed**

```bash
git add android/BachataS4
git commit -m "fix(ui): resolve portrait console integration"
```
