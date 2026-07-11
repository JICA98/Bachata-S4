# Portrait Console UI Design

## Goal

Rebuild Bachata S4's Library, Settings, Setup, and in-session touch-controller presentation in Kotlin/Compose using the supplied prototypes, while preserving every existing import, launch, runtime-profile, controller-mapping, and touch-layout capability.

## Scope and orientation

- **Library, Settings, and Setup:** portrait-only, including reverse portrait. They use the same near-black canvas, Spotlight White text, Stage Amber interaction color, dark raised surfaces, rounded cards, and bottom action/navigation chrome shown by the Library and Settings prototypes.
- **Session:** entering gameplay applies fixed primary landscape, hides the status and navigation bars with immersive behavior, and restores portrait/system-bar behavior when the session route is left. The session's touch controls remain fully functional and are restyled from `design/controller.html`.

## Shared presentation layer

Create a focused Compose UI layer shared by the three portrait screens: color tokens, typography, screen header, panel/card treatment, primary/secondary actions, compact controller-action bar, and input/focus styling. It changes rendering only: feature view models, repositories, runtime setting parsing, and navigation routes stay authoritative.

## Library

The portrait Library adopts the supplied structure: branded header, selected-game hero, game metadata, primary Resume/Launch action, Continue Playing shelf, All Games shelf, and bottom navigation/action hints. The selected game is an existing `LibraryUiState` concern. Imported games continue to come from the existing repository and folder picker. Settings, import, launch, and per-game settings remain discoverable as header or game-context actions; none is removed by the prototype composition. Empty, importing, and import-failure states use the same visual language.

## Settings and game scope

The Settings screen becomes a portrait header plus horizontal category tabs and scrollable settings cards, following the supplied prototype. Existing controls remain usable: runtime selection, search, drivers, raw configuration, controller mapping, touch-layout editor, JSON import/export, Box64 presets, and every catalog setting. The existing `ProfileScope.Global` and `ProfileScope.Game(id)` data flow is unchanged. A per-game entry point still opens `BachataRoutes.gameSettings(id)` and the UI clearly labels the active game scope plus inherited/default behavior.

## Setup onboarding

Replace the diagnostic column with one onboarding page. It shows the launcher icon, product title, concise legal/runtime readiness copy, device compatibility status, conditional runtime-download action, progress/failure feedback, and the existing gated Continue action. Its readiness logic stays in `SetupViewModel`.

## Session controller

Keep `TouchLayout`, `TouchControllerState`, and persistence as the source of control positions and input. Enhance `FixedControllerOverlay` so its controls visually match the reference: translucent dark shell, top shoulders/triggers, segmented D-pad, recessed analog sticks, colored outlined face buttons, center/touchpad controls, press feedback, and a fade affordance. Every control still emits the same runtime snapshot and honors saved opacity, scale, placement, visibility, and input behavior.

## Failure handling and testing

Import, runtime-download, and session-failure feedback remain visible in their redesigned locations. Automated tests will cover orientation/window-mode lifecycle as testable state, Library selection and preserved actions, settings scope/inheritance rendering behavior, setup gating, and controller layout/control preservation. UI changes will be validated with module tests, lint, and a debug APK build; the runtime packaging verification in `AGENTS.md` is required before any APK install or publishing.
