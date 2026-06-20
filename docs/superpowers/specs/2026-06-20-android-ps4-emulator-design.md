# BachataS4 Android Emulator Design

**Date:** 2026-06-20  
**Status:** Approved design direction  
**Scope:** Android feasibility MVP and migration path

## Summary

BachataS4 will be a new, ARM64-only Android application that launches this shadPS4 fork as an x86-64 Linux process under Box64. A minimal glibc runtime supplies the Linux userspace expected by Box64 and shadPS4. An embedded Android X server presents the existing SDL frontend, while Box64 Vulkan wrappers forward graphics calls to a native ARM64 Vulkan driver. Wine and DXVK are excluded because the backend is already a Linux Vulkan application.

This whole-process translation design preserves shadPS4's current x86-64 assumptions and is the shortest credible path to booting software on Snapdragon hardware. The app will keep runtime control behind stable Kotlin interfaces so a later native ARM64 shadPS4 build with an embedded x86-64 guest translator can replace Box64 without rewriting the Android UI or storage layer.

## Goals

- Create `android/` in this repository using `npx create-android` with its multi-module template.
- Target ARM64 Snapdragon devices, with Snapdragon 8 Gen 3 as baseline and Snapdragon 8 Elite as tuned target.
- Install and validate a pinned Box64, glibc rootfs, X server, audio bridge, Vulkan wrapper, and x86-64 shadPS4 runtime.
- Let users select legally obtained PS4 game and firmware files through Android's Storage Access Framework.
- Provide a small game library, runtime setup flow, launch session, stop action, settings, diagnostics, and log export.
- Establish measured feasibility gates before investing in broad UI or compatibility work.
- Preserve a migration boundary for a future native ARM64 backend.

## Non-Goals

- Bundling PS4 firmware, encryption keys, games, or other Sony-owned content.
- Downloading copyrighted console content.
- General x86 or Windows application compatibility.
- Wine, DXVK, or Windows container support.
- Non-Snapdragon GPU support in the first release.
- Play Store distribution during feasibility work.
- Guaranteed game compatibility or target FPS before physical-device benchmarks.
- Native ARM64 guest execution in the first runtime.
- Touch-control editor, cloud sync, achievements, multiplayer services, or cover-art scraping in the MVP.

## Feasibility Gates

Development proceeds in order. Failure at a gate stops later product work until the blocker has a measured resolution.

1. **Scaffold:** Generated Android project builds and launches on an ARM64 emulator or device.
2. **Translation:** Packaged ARM64 Box64 runs a pinned x86-64 glibc hello-world binary from app-private storage.
3. **Display:** An x86-64 SDL test creates a window through the embedded X server and responds to lifecycle changes.
4. **Graphics:** An x86-64 Vulkan test renders through Box64's Vulkan wrapper on Snapdragon 8 Gen 3 using the selected native driver.
5. **Backend startup:** x86-64 shadPS4 starts, emits structured logs, reads config, and exits cleanly without a game.
6. **Content inspection:** shadPS4 reads a user-selected game dump from app-managed storage and returns metadata or a typed validation error.
7. **Homebrew boot:** A legal test/homebrew workload reaches its first rendered frame with audio and controller input.
8. **Device qualification:** Repeated runs on Snapdragon 8 Gen 3 and 8 Elite record startup time, frame pacing, memory, thermals, crashes, and driver identity.

Gate 7, not project compilation, defines MVP technical success.

## Architecture

### Android application

The generated app is named `BachataS4` with application ID `com.bachatas4.android`. It uses Kotlin, Jetpack Compose, coroutines, Hilt, Room, DataStore, and AndroidX Navigation from the pinned `create-android` stack. Scaffolding uses `npx create-android@0.1.0 android --name BachataS4 --package com.bachatas4.android --arch multi` so regeneration is reproducible.

Initial modules remain deliberately small:

- `app`: application, activity, navigation, dependency graph, and foreground-service declaration.
- `core:model`: pure Kotlin game, runtime, session, device, and diagnostic models.
- `core:data`: repositories coordinating Room, DataStore, document import, and runtime control.
- `core:database`: Room entities and DAOs for local game records and session summaries.
- `core:runtime`: runtime installation, capability probing, process supervision, IPC, and diagnostics.
- `core:testing`: fakes, fixtures, dispatcher rules, and test helpers.
- `feature:setup`: device check, legal notice, runtime installation, and validation.
- `feature:library`: imported-game list and game details.
- `feature:session`: launch progress, embedded display surface, stop control, and live diagnostics.
- `feature:settings`: driver profile, CPU preset, logging, storage, and data reset.

Feature modules depend on core interfaces. Core modules never depend on features. Runtime implementation details do not leak into ViewModels.

### Runtime bundle

Runtime artifacts are reproducible, pinned, and described by a manifest containing component name, upstream revision, license, ABI, compressed size, installed size, and SHA-256 digest. MVP archives are generated before the Android build and packaged as APK assets, so APK signing authenticates the manifest and archive together. Large generated binaries remain ignored source-tree build outputs.

Bundle contains:

- ARM64 Box64 executable and required wrapper libraries.
- Minimal ARM64 glibc userspace needed by Box64 and native wrappers.
- Pinned x86-64 glibc libraries needed by the shadPS4 executable.
- ARM64 Android X server adapted from Winlator's LGPL-2.1 `winlator-app` X server at a pinned revision, with retained notices and source modifications.
- ARM64 audio bridge backed by AAudio.
- ARM64 Box64 Vulkan wrapper, pinned Mesa Turnip driver, and device-specific driver profile.
- x86-64 shadPS4 executable built from this repository with desktop-only updater, Discord, RenderDoc, and developer UI disabled.
- Small x86-64 translation, SDL, Vulkan, audio, and IPC probes.

Build scripts fetch only pinned upstream revisions, verify hashes, apply repository-owned patches, emit license notices, and produce a deterministic archive plus manifest.

### Runtime process model

`EmulationService` is a foreground service started only by explicit user action. `RuntimeSupervisor` owns one session at a time and moves it through `Idle`, `Preparing`, `Starting`, `Running`, `Stopping`, `Stopped`, or `Failed` states.

The service starts the embedded X server and audio bridge, then launches Box64 with an explicit environment and the x86-64 shadPS4 executable. No shell command is constructed from user input. Arguments are passed as an array. Runtime files live under app-private storage and are not executable from shared storage.

Control and telemetry use a versioned local Unix-domain socket protocol. Messages use length-prefixed JSON for the MVP because traffic is low volume and logs remain human-readable. Protocol messages cover handshake, launch, stop, lifecycle, controller input, structured log events, frame statistics, fatal error, and clean exit. Unknown message versions fail closed with a reinstall-required error.

Android process death is expected. Room persists session summary and failure state. On next launch, stale PID/socket files are removed only after verifying no owned process remains.

### Display, graphics, audio, and input

The x86-64 SDL frontend uses X11. Embedded X server renders into a `SurfaceView` owned by session UI. Surface loss pauses presentation; recreation rebinds display without silently launching a second emulator process.

Vulkan calls pass through Box64 wrappers to the bundled ARM64 Mesa Turnip driver. System Qualcomm Vulkan drivers are excluded from the MVP because crossing Android's Bionic ABI from the glibc runtime adds another unstable bridge. Snapdragon 8 Gen 3 uses an Adreno 750 profile; Snapdragon 8 Elite uses an Adreno 830 profile. Capability probing records Vulkan API version, extensions, driver UUID, device ID, memory heaps, and known-blocked features before launch. Unsupported devices receive a clear diagnostic instead of attempting a game boot.

AAudio provides low-latency output through an ARM64 bridge. Android gamepad events are normalized in Kotlin and forwarded to the runtime protocol. MVP requires a physical controller; touch controls are excluded.

### Storage and content flow

Users choose game directories or package files with the Storage Access Framework. Persistable URI permission is retained when available. Because the Linux backend expects ordinary paths and random access, `ContentImporter` copies selected files into a versioned app-managed library using a temporary directory, verifies byte counts and hashes, then atomically renames the import. Interrupted imports resume only when source identity and length still match; otherwise they restart safely.

Firmware and runtime data use separate directories. Database stores display metadata and internal relative paths, never unrestricted external filesystem paths. Removing a game deletes only files owned by the app after explicit confirmation.

Launch flow:

1. UI requests launch by internal game ID.
2. Repository resolves game and active runtime records.
3. Capability checker validates device, free space, runtime integrity, driver profile, and game files.
4. Foreground service starts display, audio, socket, Box64, and shadPS4.
5. Structured events update session UI and persistent session summary.
6. User stop requests graceful shutdown, waits with a fixed timeout, then terminates only owned child processes.

## Error Model

All user-visible failures map to stable codes and recovery actions:

- `UNSUPPORTED_DEVICE`: show detected SoC/GPU and supported baseline.
- `RUNTIME_MISSING` or `RUNTIME_CORRUPT`: reinstall verified bundle.
- `INSUFFICIENT_STORAGE`: show required and available bytes before copying.
- `CONTENT_PERMISSION_LOST`: request source selection again.
- `CONTENT_INVALID`: show failed validation stage without claiming corruption beyond evidence.
- `VULKAN_UNSUPPORTED` or `DRIVER_BLOCKED`: show capability report and selected profile.
- `TRANSLATOR_START_FAILED`: preserve shortest decisive Box64 error and full exportable log.
- `BACKEND_CRASHED`: preserve exit signal/code, last structured events, driver identity, and runtime revision.
- `PROTOCOL_MISMATCH`: block launch and require matching runtime/app versions.

Secrets, full user paths, and firmware contents never enter analytics. MVP has no remote analytics; diagnostics leave device only through explicit export.

## Performance Strategy

Correctness and repeatability precede game-specific tuning. Release builds use ARM64 optimizations selected by runtime code, not a build tied to one phone. Device profiles may adjust Box64 dynarec settings, worker counts, shader cache, and driver workarounds. Every non-default option records provenance and benchmark evidence.

Qualification runs use fixed workloads and cold/warm trials. Recorded metrics include translation-cache hit rate when exposed, process CPU time, peak RSS, Android thermal status, startup stages, frame-time percentiles, audio underruns, dropped input events, and crash-free run duration. Snapdragon 8 Elite may receive a separate profile only when measurements outperform the baseline without compatibility loss.

## Security and Legal Constraints

- App ships no Sony firmware, keys, games, or URLs intended to obtain them.
- Imported content remains local and user-controlled.
- Runtime archives and extracted files are hash-verified before execution.
- Executable files are app-owned, non-world-writable, and never loaded from arbitrary shared paths.
- Child process environment is allowlisted; user data never becomes shell syntax.
- Local socket accepts only the app-owned session and validates protocol handshake.
- GPL-2.0-or-later obligations from this fork apply to distributed derivative binaries and corresponding source/build scripts.
- Every bundled dependency receives a recorded license and source revision. Incompatible licenses block distribution.

## Testing

- Pure Kotlin unit tests cover state machines, capability rules, manifest validation, error mapping, import planning, and repository behavior.
- Room tests cover migration, uniqueness, deletion ownership, and interrupted session recovery.
- Instrumented tests cover SAF import with fake document providers, foreground-service lifecycle, process death, surface recreation, and controller routing.
- Native/runtime tests cover manifest hashes, socket framing, process ownership, clean shutdown, and probe exit codes.
- Golden protocol fixtures prevent accidental app/runtime incompatibility.
- Physical-device gate tests run on at least one Snapdragon 8 Gen 3 and one Snapdragon 8 Elite device. Android emulator results never substitute for Vulkan/runtime qualification.
- Existing shadPS4 host tests remain unchanged and run separately to detect backend regressions.

## Delivery Sequence

1. Scaffold Android project and establish CI/build health.
2. Define models, runtime contract, manifest, and fake supervisor using TDD.
3. Build reproducible Box64/glibc runtime and pass translation probe.
4. Integrate X server, SDL probe, Vulkan wrapper, Turnip/system driver profile, and AAudio bridge.
5. Produce Android-focused x86-64 shadPS4 build and structured startup adapter.
6. Add content import, local database, setup flow, and library UI.
7. Add foreground emulation service, session UI, physical controller path, and diagnostics export.
8. Pass homebrew boot and two-device qualification gates.
9. Package source notices and reproducible runtime artifacts for an external test release.

## Native ARM64 Migration Boundary

Android code depends on `EmulatorRuntime`, not Box64 classes. Interface exposes capability probe, install/verify, launch, event stream, stop, and diagnostic export. `Box64EmulatorRuntime` is first implementation. Future `NativeArm64EmulatorRuntime` may replace it after shadPS4 gains:

- Embedded x86-64 guest JIT with PS4 SysV ABI handling.
- Guest-to-ARM64 HLE call bridge and callback bridge.
- Guest TLS and signal/exception translation.
- Fixed-address and executable-memory strategy compatible with Android.
- Self-modifying code invalidation and instruction-patch equivalents.
- ARM64 implementation of current x86-only entry, fiber, and CPU-patch paths.

Native migration is a separate project and starts only after feasibility data identifies whole-process translation as the dominant bottleneck.

## Acceptance Criteria

- Repository contains generated, buildable `android/` project created by the required scaffolder.
- Unsupported hardware is rejected with actionable local diagnostics.
- Runtime install is reproducible and hash-verified.
- Box64, SDL/X11, Vulkan, audio, and shadPS4 startup probes pass on Snapdragon 8 Gen 3.
- User can import legal test content, launch it, see first frame, hear audio, use a physical controller, stop session, and export logs.
- Same workflow runs on Snapdragon 8 Elite with a recorded device profile.
- App recovery after surface loss and process death does not duplicate sessions or delete user content.
- Distributed artifacts include required licenses and corresponding source/build instructions.
