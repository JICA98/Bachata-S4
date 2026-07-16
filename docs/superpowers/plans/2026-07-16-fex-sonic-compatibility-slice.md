# FEX Sonic Compatibility Slice Plan

> **Execution:** work task-by-task with TDD. Do not mark a task complete until
> its stated automated evidence passes; do not mark device-gated tasks complete
> without the SM8650 tablet result.

**Goal:** native AArch64 shadPS4 executes Sonic Mania PS4 x86-64 guest code in
FEXCore, reaches first-level completion with controller/rendering/audio, then
survives ten minutes. Box64 remains explicit fallback until that proof exists.

**Architecture:** replace direct native invocation of PS4 virtual addresses with
`GuestCpuBackend`. Its FEX implementation owns FEXCore lifecycle and per-host
thread guest state. `SymbolsResolver` retains typed native HLE registrations;
the linker maps generated x86-64 veneers into guest space instead of writing
native ARM addresses into `R_X86_64_JUMP_SLOT` relocations.

## Constraints

- Work only in `feature/fex-arm64-phase0-clean`; do not modify GameNative,
  `feature/fex-arm64-sonic`, `externals/sdl3`, or `runtime/sources/box64`.
- Preserve Phase 0 and Phase 1 probes and their SM8650 evidence.
- No Box64 fallback while FEX mode is selected, no Wine/ARM64EC, no external
  x86 shadPS4 process, no rootfs/thunk reuse, and no fake HLE implementation.
- Use source tests before implementation. Every FEX, mapping, relocation, and
  typed-dispatch failure must surface a stage/error instead of being ignored.
- Never uninstall/clear app or game data. Device installs are replacement-only.
- Before an APK build run the repository-required Debian runtime build and
  verifier; do not install/publish unless both managed-runtime assets are in
  the APK.

## Task 1: Lock the native-guest boundary with failing source contracts

**Files:**

- Create: `runtime/tests/fex-sonic-guest-backend-source.test.mjs`
- Create: `runtime/tests/fex-arm64-shadps4-build-source.test.mjs`
- Modify: `src/core/linker.h`
- Create: `src/core/guest_cpu/guest_cpu.h`
- Create: `src/core/guest_cpu/guest_cpu.cpp`
- Modify: `CMakeLists.txt`

**Contract:** `GuestCpuBackend` accepts a validated guest entry request
(`rip`, `rsp`, GPR/RFLAGS/XMM state, execution ranges, and explicit stop
reason), returns a typed result, and has no Box64 or process-launch API. The
current direct guest-call paths remain only in the x86 native backend.

1. Write Node source tests that fail until `GuestCpuBackend` exists, ARM64 CMake
   includes it, direct guest-address casts are guarded away from ARM64 FEX mode,
   and an ARM64 build script exists. Assert that no new FEX file mentions
   Box64/Wine/ARM64EC or `system`/`exec` launch calls.
2. Add the value-only API and a `NativeGuestCpuBackend` that preserves current
   x86 behavior. Add `UnavailableGuestCpuBackend` for unsupported selection;
   it returns a typed `ENOTSUP` result and never falls back.
3. Add `runtime/scripts/build-shadps4-arm64.sh`, modeled on the Debian cross
   compiler/sysroot conventions used by `build-fexcore-smoke-aarch64.sh`, but
   staging no runtime default change. Require ELF64/AArch64 output.
4. Run the two Node tests (red then green), `bash -n` on the script, and an
   ARM64 CMake configure. Record the first actual cross-compile error before
   applying any upstream ARM64 compatibility change.

## Task 2: Make shadPS4 compile as native ARM64 without guest execution

**Files:**

- Modify only proven-required files from upstream ARM64 commits, expected set:
  `CMakeLists.txt`, `src/common/signal_context.cpp`, `src/common/va_ctx.h`,
  `src/core/thread.cpp`, `src/core/tls.cpp`, `src/core/linker.cpp`,
  `src/core/libraries/kernel/threads/{pthread.cpp,stack.S}`
- Create: `runtime/tests/fex-arm64-compile-guards.test.mjs`

**Contract:** an AArch64 host binary compiles. It does not execute a game until
Task 4. ARM64 signal context accesses PC, x86-only instruction patching remains
disabled, and no x86 assembly is selected for ARM64.

1. Add failing static assertions for correct architecture guards and an ARM64
   implementation/typed failure for every old x86-only path.
2. Build with `build-shadps4-arm64.sh`; apply the minimum relevant pieces from
   local upstream commits `dcd036d2` and `362bd7da` only when the actual compile
   error requires them. Do not cherry-pick wholesale.
3. Repeat until the ARM64 host binary links. Run `file`, `readelf -h`, and
   `readelf -d`; verify `Machine: AArch64`, no x86 loader, and dependencies are
   satisfiable from the Debian ARM64 stage.
4. Run host CTest and all Task 1/2 Node tests. If a test baseline differs,
   preserve and report it; do not suppress the test.

## Task 3: Evolve FEX harness into real guest mapping/thread primitives

**Files:**

- Modify: `src/core/fex/fex_guest_engine.{h,cpp}`
- Create: `src/core/guest_cpu/fex_guest_cpu.{h,cpp}`
- Create: `runtime/tests/fex-guest-cpu-source.test.mjs`
- Extend: `runtime/probes/fexcore-guest-harness.cpp`

**Contract:** FEX executes caller-supplied mapped guest code and guest stack,
not a private synthetic page. Per-host-thread `InternalThreadState` lifecycle,
range validation, RIP/RSP initialization, frame transfer, invalidation, and
shutdown are explicit and testable.

1. Write failing source/harness tests for supplied mappings, stack bounds,
   second-thread isolation, first/last RIP logging, invalidation, teardown
   order, and page-size/W^X rejection.
2. Split Phase 1 controlled code construction from reusable `GuestEngine`.
   Preserve its marker and add the new API without weakening cleanup.
3. Implement `FexGuestCpuBackend::CreateThread`, `Run`, `Invalidate`, and
   `DestroyThread`; create/destroy FEX thread state only on its owner thread.
4. Extend the harness to map a small caller-owned ELF-style executable range and
   prove return state. Build/package it, run the old Phase 1 harness regression,
   and run the new host test.

## Task 4: Add typed HLE function records and guest call veneers

**Files:**

- Modify: `src/core/loader/symbols_resolver.{h,cpp}`
- Modify: `src/core/libraries/libs.h`
- Create: `src/core/guest_cpu/hle_call_adapter.{h,cpp}`
- Modify: `src/core/guest_cpu/fex_guest_cpu.{h,cpp}`
- Modify: `src/core/linker.{h,cpp}`
- Create: `runtime/tests/fex-hle-veneer-source.test.mjs`

**Contract:** a function relocation receives a guest executable FEX veneer;
object relocation remains guest data. The veneer dispatches to a typed native
HLE call adapter and writes PS4 ABI return state. An unsupported signature is a
named error, never a raw cast.

1. Write a failing test requiring separate object/function records, generated
   `LIB_FUNCTION` invokers, veneer allocation in a guest executable mapping,
   no function relocation equal to a host address in FEX mode, and error checks
   for integer, vector, stack, and unsupported arguments.
2. Add a type-erased call record generated from `decltype(function)` at each
   `LIB_FUNCTION` registration. Decode System V GPR/XMM/stack argument slots
   only through the typed adapter; validate guest pointers against mapped PS4
   regions before native HLE dereference.
3. Generate `mov rax, operation; syscall; ret` guest stubs with W^X and cache
   flush. Connect `BridgeSyscallHandler` to the typed record and exact FEX CPU
   frame. Record the guest symbol ID in errors without logging memory content.
4. Add an engine-level harness that calls real representative HLE functions for
   scalar, pointer, and vector return paths. It must prove no ABI cast and no
   Box64 launch.

## Task 5: Route main/module/pthread/callback guest entry through FEX

**Files:**

- Modify: `src/core/module.cpp`
- Modify: `src/core/linker.{h,cpp}`
- Modify: `src/core/libraries/kernel/threads/pthread.cpp`
- Modify exact callback callsites only after import/trace evidence identifies
  them, beginning under `src/core/libraries/{audio*,pad,kernel}`
- Create: `runtime/tests/fex-guest-entry-source.test.mjs`

**Contract:** FEX mode never invokes a PS4 virtual address as a native C++
function. Main entry, module init, guest pthread start, and required HLE
callbacks use one backend API and preserve guest ABI state across a return
sentinel.

1. Add red tests covering all four direct-call families and nested callback
   rejection/return state.
2. Replace `Module::Start`, `RunMainEntry`, and `RunThread` with backend calls.
   Preserve the existing direct native path only in `NativeGuestCpuBackend`.
3. Implement nested native-HLE-to-guest callbacks through a dedicated guest
   call frame. Verify signal chaining and TLS on a second guest thread.
4. Run CTest, backend/harness suites, and ARM64 link checks. Stop and diagnose
   the first unsupported HLE signature rather than adding a stub.

## Task 6: Add explicit Sonic-only selection and runtime packaging

**Files:**

- Modify: `runtime/scripts/build-runtime-debian.sh`
- Modify: `runtime/scripts/stage-debian-runtime.mjs`
- Modify: `runtime/scripts/package-runtime.mjs`
- Modify: `runtime/tests/verify-runtime.mjs`
- Modify: Android runtime profile and launch files identified by
  `RuntimeLaunchProfileProvider` and `ManagedSession`
- Create: focused Kotlin and Node tests for resolved FEX selection

**Contract:** only game `CUSA07023` may opt into the native ARM64 FEX binary.
Global/default resolution remains current Box64. Selecting FEX verifies the
native binary/runtime hashes and reports a hard launch error if unavailable;
it never quietly runs Box64.

1. Write failing profile/launch tests: per-game FEX selection resolves native
   runner and loader; global/default selection resolves existing Box64 command.
2. Package `host/shadps4-arm64-fex` and its ARM64 FEX dependencies, verify ELF
   and manifest hashes, and retain existing `shadps4`/Box64 assets unchanged.
3. Add title/backend structured launch logging with sanitization. Include no
   game paths, serial, or raw guest memory.
4. Rebuild runtime, run package/APK verifiers, assemble Playstore APK, and
   inspect its managed runtime entries before any device install.

## Task 7: Device bring-up and Sonic acceptance

**Files:**

- Create: `runtime/qualification/run-fex-sonic-slice.sh`
- Create: `runtime/qualification/{create,verify}-fex-sonic-evidence.mjs`
- Create: `runtime/qualification/fex-sonic-schema.json`
- Create only after passing: `runtime/evidence/sm8650/fex-sonic.*`

**Contract:** sanitized evidence proves the selected FEX backend reaches Sonic
first-level completion, video, audio, controller input, and a ten-minute stable
run. This is a physical tablet gate and remains incomplete without device proof.

1. Add red evidence tests enforcing backend/FEX revision, CUSA07023, ordered
   launch stages, one input event, audio/video success, first-level result,
   600,000+ ms duration, and no crash/forbidden private data.
2. On the connected SM8650 only, replacement-install the Playstore APK, choose
   the explicit per-title FEX profile, and capture bounded focused logs. Do not
   copy game content or clear data.
3. If boot fails, use the first structured backend/HLE failure to return to the
   responsible task. If any physical criterion is unverified, leave Task 7
   in progress and ask for tablet access; do not record passing evidence.
4. After all gates pass, run full runtime verification, Gradle tests/lint,
   CTest, whitespace and Box64-range audits. Only then prepare the separate
   global-default decision; do not make that change in this slice.

## Plan Review

The plan starts with an ARM64 artifact and explicit ownership boundary before
touching title behavior. It treats typed HLE dispatch, guest call re-entry, and
physical title evidence as distinct blocking layers. Existing FEX Phase 1 and
Box64 runtime behavior remain regression gates throughout.
