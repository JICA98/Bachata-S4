# Comprehensive Runtime Settings Design

**Date:** 2026-07-08
**Status:** Approved design

## Goal

Give Android users complete, searchable control over shadPS4 settings, documented Box64 behavior variables, Vulkan driver selection, controller mapping, and touch controls. Settings support global defaults and sparse per-game overrides. BachataS4 must not package a Turnip driver or Turnip archive.

## Scope

This design includes:

- Every persisted key read or written by the repository's current shadPS4 `EmulatorSettingsImpl`, including Settings/GUI values and the General, Log, Debug, Input, Audio, GPU, and Vulkan groups.
- Android-specific runtime settings and launch diagnostics.
- Every documented `BOX64_*` behavior variable in `runtime/sources/box64/docs/USAGE.md`.
- Upstream `BOX64_PROFILE` presets (`safest`, `safe`, `default`, `fast`, `fastest`) plus a Custom mode for individual flags.
- Global settings and per-game overrides.
- Typed settings screens and validated raw `config.json` and Box64 environment editors.
- Turnip release discovery from one trusted repository, ZIP import, installed-driver management, and global/per-game driver selection.
- Four physical controller slots, binding capture/remapping, and editable touch layouts.
- Removal of all bundled Turnip binaries and archives from managed runtime assets and APKs.

Launch-owned process wiring remains visible but read-only: generated `HOME`, `DISPLAY`, socket paths, runtime library paths, and storage paths. These are app invariants rather than user-tunable Box64 behavior.

## Architecture

### Module boundaries

- `core:data` owns versioned profile persistence, global/per-game merge semantics, migrations, and profile import/export.
- `core:runtime` owns setting schemas, typed values, validation, JSON and environment serialization, resolved launch snapshots, Turnip installation, and GitHub release access.
- `feature:settings` owns category navigation, search, typed editors, raw editors, scope selection, and diagnostics.
- A new `feature:drivers` Gradle module owns release browsing, download/import progress, installed-driver management, and selection.
- `feature:session` owns physical-controller binding UI, touch-layout editing, and application of resolved input profiles to the existing controller transport.
- `app` composes navigation and supplies the immutable resolved launch snapshot to `EmulationService`.

No Compose type crosses into `core:data` or `core:runtime`. Runtime serializers and resolvers remain plain Kotlin and unit-testable on the JVM.

### Declarative schemas

The settings engine uses versioned, checked-in declarative schemas. Each `SettingSpec` contains:

- stable ID and native key;
- namespace/category;
- value type;
- native default;
- valid range or enum choices;
- title, help, and search aliases;
- global/per-game scope support;
- restart requirement;
- risk level and optional warning;
- serialization metadata.

shadPS4 coverage tests extract persisted/overrideable keys from `src/core/emulator_settings.h` and `src/core/emulator_settings.cpp` and compare them with the schema. Box64 coverage tests extract `BOX64_*` headings from `runtime/sources/box64/docs/USAGE.md`. Missing, duplicate, or stale entries fail tests. Intentional exclusions require an explicit read-only entry with a reason; silent omissions are forbidden.

## Profile model

A profile contains:

- schema version;
- sparse typed setting values keyed by stable ID;
- unknown shadPS4 JSON fields;
- unknown Box64 environment entries;
- selected Box64 preset, inherited globally or overridden per game;
- selected driver ID;
- four controller-slot profiles;
- touch-layout reference.

The global profile stores only values changed from native defaults. A per-game profile stores only overrides. Resolution order is:

1. schema/native default;
2. global explicit value;
3. per-game explicit value;
4. required Android compatibility constraint.

Compatibility constraints are declared, visible, and explained. They cannot silently overwrite persisted user data. The UI displays effective value, source, and reason. Resetting a per-game field deletes its override and reveals the global value. Resetting a global field deletes its explicit value and reveals the native default.

Profile writes use a temporary file and atomic move. Migrations make a backup before rewriting. Unknown fields survive typed edits and migrations. Profile export/import contains settings, bindings, and touch layouts only; it excludes games, logs, drivers, downloaded files, and credentials.

## shadPS4 and Box64 serialization

At launch, the resolver creates one immutable `ResolvedRuntimeProfile`. `ShadPs4ConfigManager` writes its shadPS4 portion to runtime-home `.local/share/shadPS4/config.json` without discarding unknown JSON fields. The Box64 serializer adds documented user behavior variables to the app-owned base environment. User values cannot replace launch-owned paths or sockets.

The Box64 preset selector maps directly to upstream `BOX64_PROFILE` values. Custom omits `BOX64_PROFILE` and applies individual flags. No app-invented preset bundles are used.

Typed values are validated before persistence and again during resolution. Invalid profiles block launch and report the exact native key, value, and constraint. Values are never silently clamped.

Raw editors operate on drafts:

- `config.json` editor parses JSON, maps known keys into typed values, preserves unknown keys, and shows conflicts before save.
- Box64 editor accepts one `KEY=VALUE` per line, rejects malformed names/duplicates, maps known keys into typed values, and preserves unknown `BOX64_*` entries.
- Validate is available without saving. Save is explicit. Invalid drafts never reach launch.

Changes made while emulation runs apply to the next launch. Restart-required settings show that state in the UI.

## Settings UI

The settings home uses Material 3 category cards inspired by the supplied official-port screenshots:

- General
- Log
- Debug
- Input
- Audio
- GPU
- Vulkan
- Android
- Box64
- Drivers
- Raw Config

A persistent scope selector chooses Global or a game. Search indexes display names, native keys, aliases, and help. Search results retain category context.

Controls are generated from type metadata:

- booleans use switches;
- enums use selection dialogs;
- bounded numbers use a slider plus exact numeric entry;
- strings and paths use validated text or document/directory pickers;
- lists use add, remove, enable, disable, and reorder controls.

Every card can open an information panel containing native key, default, allowed values, effective source, effect, risk, and restart requirement. Inherited per-game values are marked; reset removes only the override. Valid typed changes autosave and offer Undo. Raw edits and destructive driver actions require explicit confirmation.

The UI uses minimum touch targets, screen-reader labels, dynamic text, and state indicators that do not rely on color. Duplicate controls visible in some reference screenshots are treated as capture defects and are not reproduced.

## Turnip driver management

### Distribution rule

BachataS4 does not bundle Turnip. Runtime packaging must stop reading or extracting Turnip build inputs. Runtime manifests, runtime ZIPs, Android assets, and APKs must contain no Turnip shared object, ICD manifest, or Turnip archive. System Vulkan remains available as the initial fallback.

Automated verification fails when a managed runtime or APK contains a Turnip-named archive/path or known Turnip Vulkan library. Existing verification still requires `assets/runtime/manifest.json` and `assets/runtime/runtime.zip`.

### Trusted source

The only remote source is `v3kt0r-87/Mesa-Turnip-Builder`. The app queries GitHub Releases and accepts only release assets ending in `-EMULATOR.zip`. Magisk/KSU assets are never offered. No user-defined repository URL is supported in this version.

Release metadata is cached with an expiry and conditional refresh. Offline use lists installed drivers. GitHub errors and rate limiting do not remove cached metadata or installed files.

### Installation

GitHub downloads and document-provider imports share one installer. It:

1. streams into app-private staging storage with compressed and expanded size limits;
2. rejects path traversal, absolute paths, symlinks, duplicate entries, excessive entry counts, and nested archive bombs;
3. discovers supported emulator-package layouts;
4. verifies required Vulkan library and ICD metadata;
5. verifies 64-bit little-endian ARM64 ELF identity and expected ABI;
6. rewrites the private ICD manifest to the final app-private library path;
7. computes SHA-256 and records source, tag, asset name, size, install time, and library identity;
8. atomically promotes staging to an immutable driver-version directory.

Failed installation removes staging and preserves prior versions. Installed driver IDs are stable and never based only on a display name. Drivers may be selected globally or per game. Deleting a referenced driver requires confirmation and changes affected profiles to System Vulkan.

## Physical controller mapping

Four slots are supported. Each slot selects an Android input device and a DualShock-compatible output profile. Device identity uses descriptor plus vendor/product ID, with name only as a fallback; transient Android device IDs are not persisted.

Bindings cover left/right sticks and clicks, D-pad, face buttons, L1/L2/R1/R2, Options, Share/touchpad, and motion where available. Users can run a sequential capture wizard or bind one control. Binding conflicts are shown before replacement. Controls support clear, rebind, automatic mapping, dead zone, inversion, trigger threshold, vibration, and motion enablement.

Resolved mappings transform Android key/axis events into controller snapshots. The control-socket protocol and runtime host gain an explicit slot index for slots 0-3; slot 0 retains backward-compatible behavior. UI mapping code remains separate from frame encoding and socket transport. Device disconnect neutralizes that slot and permits touch fallback.

## Touch layout

The current fixed overlay becomes schema-driven. A layout stores normalized safe-area-relative bounds, z-order, visibility, opacity, scale, vibration, analog-centering behavior, stick regions, and button-group order.

Edit mode supports drag, resize, hide/show, reorder, and reset. Bounds validation prevents unreachable controls and respects display cutouts/insets. Global layouts can be overridden per game. A preview uses the same renderer and hit-testing model as live emulation so editing and gameplay cannot diverge.

## Launch data flow

1. User launches a game.
2. `EmulationService` requests a resolved profile for the game ID.
3. Resolver merges defaults, global values, per-game overrides, and declared compatibility constraints.
4. Validator verifies setting values, binding integrity, selected driver availability, and raw unknown entries.
5. Config writer atomically updates runtime-home `.local/share/shadPS4/config.json`.
6. Environment builder combines app-owned launch wiring, Box64 values, and selected driver environment.
7. Session receives resolved controller/touch profiles and starts the backend.
8. Session log records profile schema version, explicit override IDs, compatibility constraints, and driver hash. Sensitive/raw values and unnecessary absolute user paths are omitted.

## Failure behavior

- Invalid setting: block save or launch and identify key plus constraint.
- Invalid raw config: retain unsaved draft and preserve active profile.
- Missing selected driver: block launch and navigate to driver selection.
- Download/network failure: preserve cache and installed drivers; offer retry.
- Invalid ZIP/ELF: remove staging and report the decisive validation failure.
- Persistence/migration failure: retain backup, avoid partial replacement, and expose recovery action.
- Controller conflict: require explicit replacement or cancellation.
- Device disconnect: emit neutral state and show reconnect/fallback state.

## Testing strategy

TDD is required for implementation. Primary tests:

- shadPS4 schema coverage against native settings definitions;
- Box64 schema coverage against `USAGE.md`;
- defaults, ranges, enums, unknown fields, merge precedence, resets, and migrations;
- JSON/environment golden round trips and atomic write failures;
- raw editor parsing and conflict handling;
- GitHub response parsing, cache behavior, release filtering, and rate-limit failures;
- hostile ZIP corpus, ELF validation, ICD rewriting, hashes, and atomic promotion;
- global/per-game driver references and deletion behavior;
- device identity, binding capture/conflicts, axis transforms, and disconnect neutralization;
- touch-layout bounds, serialization, hit testing, and global/per-game inheritance;
- ViewModel state, search, navigation, accessibility semantics, and generated controls.

Final verification runs runtime unit tests, Android unit tests, `lintDebug`, and `assembleDebug`. Before Gradle assembly, the managed runtime is rebuilt and packaged using the repository-mandated sequence. APK inspection must prove both required runtime assets exist and no Turnip payload exists.

## Compatibility and migration

Existing Vulkan preference values referencing bundled drivers migrate to System Vulkan unless a matching separately installed driver exists. Existing imported custom driver data is discovered and migrated into the versioned installed-driver registry after validation. Existing Android compatibility JSON values become explicit compatibility constraints, preserving current boot behavior while allowing the UI to explain them.

No game content or log migration is required.

## Acceptance criteria

- Every current shadPS4 exported setting and documented Box64 behavior variable is discoverable by UI search.
- Users can select every upstream Box64 preset or Custom globally and per game; Custom exposes every documented flag.
- Global values and per-game overrides resolve predictably and reset independently.
- Raw editors round-trip unknown valid values without corrupting typed settings.
- Users can browse the trusted repository, download an emulator ZIP, import a ZIP, select installed drivers globally/per game, and recover safely from invalid packages.
- APK and managed runtime contain no Turnip driver or archive.
- Four physical controllers can be mapped, and touch controls can be rearranged globally/per game.
- Invalid settings, missing drivers, and malformed packages never start a partially configured runtime.
- Required tests, lint, build, runtime verification, and APK content checks pass.
