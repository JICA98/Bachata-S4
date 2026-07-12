# Embedded FEX Guest CPU Backend Design

**Date:** 2026-07-12  
**Branch:** `feature/fex-arm64-sonic`  
**Status:** Approved for implementation planning

## Summary

BachataS4 will add a native AArch64 shadPS4 build that embeds FEXCore and uses it only to execute PS4 x86-64 guest CPU code. Rendering, shader recompilation, HLE, audio, input, networking, storage, and runtime supervision remain native AArch64 code. The existing x86-64 shadPS4-under-Box64 path remains available as a manually selected compatibility backend.

Both paths ship in one managed runtime. FEX stays opt-in during development and becomes the default for every game only after Sonic Mania passes the agreed playable-first-level gate. FEX failures never trigger an automatic Box64 retry.

## Context

The PS4 and its software use x86-64. Current shadPS4 executes guest functions directly because its supported host builds are also x86-64. On Android ARM64, BachataS4 currently runs the entire x86-64 shadPS4 process through Box64. This is functional but translates emulator code as well as game code.

Upstream shadPS4 can compile for ARM64, but game execution is deliberately unimplemented: main entry, module-start functions, callbacks, thread entrypoints, and several exception paths still assume native x86-64 execution. The source contains no guest CPU translator.

FEXCore provides an embeddable x86-64-to-AArch64 translator with explicit context, thread, register, syscall, callback, and code-cache APIs. Its integration is substantial but gives shadPS4 a proper guest CPU boundary while keeping non-guest work native.

## Goals

- Build and run minimal FEXCore-translated x86-64 code on the connected SM8650 tablet.
- Add a typed shadPS4 CPU-backend interface without regressing native x86-64 execution.
- Run PS4 main entrypoints, threads, callbacks, TLS, and HLE calls through FEXCore on AArch64.
- Coordinate guest memory, translated-code invalidation, signals, and GPU protected-page faults safely.
- Ship FEX and Box64 in one verified managed runtime with explicit global and per-game selection.
- Make Sonic Mania's first level playable with correct rendering, controller input, audio, and ten minutes of stability.
- After Sonic passes, make FEX the global default while preserving explicit Box64 selection.
- Bring Bloodborne through correct boot and playable first-room rendering, input, and audio before performance optimization.

## Non-Goals

- Removing Box64.
- Automatically retrying failed FEX sessions under Box64.
- Separating FEX and Box64 into independent downloads during this alpha phase.
- Integrating full FEX userspace, RootFS management, FEXInterpreter, or FEX library thunks.
- Supporting every Android page size in the first milestone.
- Setting an FPS acceptance target before Sonic and Bloodborne are correct and stable.
- Fixing unrelated upstream or baseline test failures.

## Architecture

### shadPS4 CPU boundary

Introduce a `CpuBackend` boundary owned by shadPS4. Its contract covers:

- running the main executable entrypoint;
- calling guest functions and nested callbacks;
- attaching, executing, and destroying guest thread contexts;
- registering typed host/HLE entrypoints;
- setting and restoring guest FS/TLS state;
- observing guest memory map, protection, unmap, and executable-write events;
- invalidating translated code; and
- participating in process signal and fault routing.

The interface will be restored from the intent of shadPS4's historical typed guest-execution abstraction rather than layered around raw function-pointer casts.

`NativeX64CpuBackend` preserves current direct execution on x86-64. The existing Box64 runtime continues to launch the x86-64 shadPS4 executable; from shadPS4's perspective it still uses `NativeX64CpuBackend`.

`FexCpuBackend` is compiled into the native AArch64 executable. It owns one FEX process context and one FEX thread context per PS4 guest pthread. It is never constructed on x86-64 builds.

### FEX dependency

FEX is pinned in the runtime component lock to a reviewed commit. The initial source baseline is `f2b679f6028ce1c38875233aecfcf5d3f8ebecec` and may change only through an explicit lock update with build and device evidence.

Build only the minimum FEXCore translator surface: context, decoder/frontend, IR, AArch64 JIT, thread/register state, code cache, host feature detection, and required bundled dependencies. Exclude LinuxEmulation, FEXInterpreter, RootFS tooling, generated library thunks, profiler, tests, and optional disassembly tools.

The initial FEX artifact targets AArch64 Linux/glibc, not Android Bionic. This reuses the managed AArch64 glibc, X11, Vulkan, and loader path already shipped for host Box64. The connected SM8650 device reports a 4096-byte page size, matching FEXCore's current requirement. Runtime capability validation must reject FEX on incompatible page sizes until that limitation is removed.

### Managed runtime and Android selection

One runtime bundle contains:

- `bin/shadps4`: existing x86-64 executable used through Box64;
- `bin/shadps4-fex`: native AArch64 Linux/glibc executable with embedded FEXCore;
- existing Box64, AArch64 glibc loader/libraries, X11, audio, Vulkan, and probe assets.

Runtime manifest version 2 adds a closed list of backend descriptors. Stable backend IDs are `box64` and `fex`. Each descriptor declares its entrypoint and execution ABI (`linux-x86_64` or `linux-aarch64`). Manifest paths remain subject to normalized-path, containment, hash, ELF-class, ELF-machine, and dependency validation.

Runtime profile schema version 2 adds an optional CPU backend setting with global/per-game inheritance. During development, a missing value resolves to Box64. After Sonic passes, the default changes to FEX; explicit user selections remain unchanged.

The launch-plan resolver separates CPU backend from Vulkan-driver ABI. Box64 keeps its existing host-glibc and APK-native launch behavior. FEX uses the APK-owned AArch64 glibc loader with the runtime `host/` library path and launches `bin/shadps4-fex` directly. Driver/backend combinations are validated before process creation.

The installed-runtime receipt stores normalized manifest capabilities atomically. Legacy manifest version 1 runtimes normalize to Box64-only. Unsupported future schema or protocol versions fail closed.

## Guest Execution and HLE Data Flow

1. Android resolves the selected backend, validates the installed runtime and selected driver, writes shadPS4 configuration, and starts shared X server, audio, input, and telemetry infrastructure.
2. The AArch64 glibc loader starts `bin/shadps4-fex` for a FEX session.
3. shadPS4 maps PS4 ELF segments at their existing guest virtual addresses and performs guest-to-guest relocation normally.
4. HLE imports resolve to generated x86-64 trap stubs rather than raw AArch64 host addresses.
5. A trap enters a FEX `OS_GENERIC` syscall handler with the complete guest register state and a validated HLE import ID.
6. A typed HLE registry decodes System V x86-64 integer, floating-point, vector, and stack arguments; calls the native AArch64 implementation; and writes return values into guest state.
7. Reverse callbacks use `CpuBackend::CallGuest`. Nested guest-to-host-to-guest calls retain the active FEX thread context and restore register/TLS state on return.
8. Guest thread creation allocates a FEX thread context with an independent register file, guest stack, and FS/TLS base. Host TLS remains owned by shadPS4 and libc.
9. Memory map/protect/unmap and executable writes notify FEX. Executable writes invalidate affected translated blocks before guest execution resumes.

Existing `HOST_CALL(function)` registrations are the last compile-time point that retains each HLE function's type. Registration will preserve that type and create a validated invoker instead of immediately erasing it to an untyped address. Unsupported signatures fail with symbol-level diagnostics; they are never invoked through unsafe casts.

## Signals and Faults

A single signal coordinator owns the process handlers and alternate signal stack. It classifies each fault as one of:

- FEX translated-code or guest-state handling;
- shadPS4 GPU protected-page tracking;
- guest exception delivery;
- translated-code invalidation; or
- genuine host/backend crash.

FEX and shadPS4 must not install independent competing handlers. Fault classification uses host PC, guest RIP reconstruction, mapped guest ranges, FEX code-cache ranges, access type, and current guest-thread state. An unclassified fault terminates the session with preserved diagnostics instead of repeatedly resuming.

## Failure Behavior

FEX is opt-in until the Sonic acceptance gate passes. After the default changes, FEX failures remain visible and require manual Box64 selection. No automatic fallback occurs.

Stable failure categories cover:

- backend unavailable or wrong ELF architecture;
- incompatible host page size;
- FEX context or thread initialization failure;
- unsupported guest instruction;
- unsupported HLE signature or callback;
- invalid HLE import ID or call nesting state;
- guest memory, signal-routing, or translated-code invalidation failure; and
- guest crash with module and x86-64 RIP.

Logs record backend ID, runtime/FEX revision, failure stage, guest module, guest RIP, and HLE symbol where available. They omit unnecessary absolute user paths. Failed runtime installation or verification never replaces the previous valid runtime.

## Testing Strategy

### Baseline

At branch creation, the untouched `dbe5165b` baseline configured and built successfully. CTest reported 382 of 426 tests passing. Forty-four GCN cases printed successful assertions and then exited with signal 11/status 139 during per-test process teardown. The same behavior reproduced when running an affected GCN case directly, while list/no-match execution exited normally. The user accepted this as a pre-existing baseline defect. Before implementation changes, the exact failing CTest names and baseline revision must be captured in checked-in qualification evidence. New work may exclude only those recorded cases and must not weaken other tests.

### Automated layers

- CPU-backend contract tests run the same synthetic fixtures through `NativeX64CpuBackend` and, where host architecture permits, FEX.
- Synthetic x86-64 fixtures verify main-stack ABI, GPR/SSE/vector arguments, return values, nested callbacks, pthread/TLS/destructors, and executable-code invalidation.
- ELF/linker tests verify guest relocations and HLE trap-stub generation.
- Signal tests verify deterministic routing between FEX, GPU protected pages, guest exceptions, and fatal faults.
- Cross-build tests verify AArch64 ELF machine 183, required dynamic dependencies, and absence of unsupported Android-seccomp syscalls where applicable.
- Runtime tests verify manifest v1 normalization, manifest v2 validation, dual artifacts, hashes, receipts, profile migration, launch-plan matrices, and no silent fallback.
- Android unit tests verify global/per-game backend resolution and Box64 environment omission for FEX sessions.
- Device qualification records backend ID, runtime and FEX revisions, launch stages, first frame, audio, input, duration, exit state, and exported logs.

### Device milestones

Phase 0 proves FEXCore on the tablet with synthetic code: integer and floating-point registers, stack, threads, TLS, callbacks, and code invalidation.

Phase 1 introduces the backend boundary and proves unchanged native x86-64/Box64 behavior before enabling ARM64 guest execution.

Phase 2 adds FEX main entry, typed HLE calls, callbacks, threads/TLS, memory invalidation, and unified signals. Each capability lands behind a focused failing test before implementation.

Phase 3 advances Sonic Mania (`CUSA07023`) through module load, main entry, first guest thread, complete observed HLE imports, first frame, title/audio/input, first-level gameplay, and ten continuous stable minutes. The same game is then launched with manually selected Box64. Only after both paths pass does FEX become the global default.

Phase 4 advances Bloodborne (`CUSA00900`) through logos, title, character load, and the first room. Initial acceptance is correct rendering, controller input, audio, and stable gameplay. FPS optimization begins only after correctness.

## Principal Risks and Mitigations

- **FEX is not an Android-supported userspace runtime.** Use only FEXCore in the existing managed AArch64 glibc environment and prove a minimal device binary before shadPS4 integration.
- **HLE registration currently erases types.** Preserve signatures at `HOST_CALL` and test marshalling by ABI class before broad registration.
- **Guest callbacks are widespread.** Make `CallGuest` a first-class nested operation and cover pthread, cleanup, destructor, heap, media, and event callback patterns incrementally.
- **Signals are shared global state.** Establish one coordinator before enabling GPU protected-page handling under FEX.
- **Page-size assumptions can corrupt JIT state.** Gate FEX availability on the supported page size and report a stable capability error.
- **A dual runtime can drift.** Build, lock, package, verify, and install both artifacts as one immutable version.
- **Performance may remain guest-thread bound.** Defer tuning until correctness evidence identifies the actual FEX hot paths.

## Acceptance Criteria

- The managed Playstore runtime contains verified Box64 and FEX backend descriptors and both shadPS4 ELF artifacts.
- The user can choose FEX or Box64 globally and per game; selection is logged and never silently changed.
- FEX executes synthetic guest tests on the target tablet before commercial-game integration.
- Existing Box64 Sonic behavior remains manually selectable throughout development.
- Sonic Mania's first level has correct rendering, working controller input and audio, and remains stable for ten minutes under FEX.
- FEX becomes the global default only after the Sonic FEX and Box64 regression gates pass.
- Bloodborne reaches stable, correctly rendered first-room gameplay under FEX before performance tuning starts.
