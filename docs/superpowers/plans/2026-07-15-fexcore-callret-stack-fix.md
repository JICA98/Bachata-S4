# FEXCore Call/Return Stack Fix Plan

**Goal:** Supply the per-thread FEX call/return stack that raw
`Context::CreateThread` does not create, then repeat the real Android smoke
gate.

**Root cause evidence:** The segment-state fix moved execution from FEX's
native decoder to `[anon:FEXMemJIT]`. The device fault is
`PC = FEXMemJIT + 0x64`, `address = 0xfffffffffffffff0`. FEX's generated
dispatcher emits `stp ... REG_CALLRET_SP, -0x10`; the raw frame's
`CPUState::callret_sp` remains zero. FEX's generic Linux thread manager
allocates `CALLRET_STACK_SIZE + 2 * FEX_PAGE_SIZE`, leaves guard pages
inaccessible, makes the middle stack read/write, attaches it to
`InternalThreadState::CallRetStackBase`, and initializes `callret_sp` one
quarter into that middle stack.

**Constraints:** Do not modify FEXCore or GameNative. Do not add Wine,
ARM64EC, rootfs, or Box64 behavior. Do not disable security, suppress errors,
use stubs, clear data, uninstall, or commit. Keep the normal packaged runner
diagnostic-free. Preserve unrelated worktree changes. Task 4 remains open
until the normal tablet smoke succeeds.

## Task 1: TDD call/return stack ownership

**Files:**

- Create: `runtime/tests/fexcore-callret-stack-source.test.mjs`
- Modify: `runtime/probes/fexcore-smoke.cpp`

1. Add a source-level test which fails without a local owner that reserves
   the FEX-required call/return allocation with two guard pages, makes only
   the middle region read/write, checks allocation/protection failure, assigns
   `CallRetStackBase`, initializes `callret_sp` at the documented default
   location, and outlives `DestroyThread`.
2. Run that exact test red before changing probe code.
3. Add the smallest RAII owner to the smoke probe. Use only FEX's generic
   `InternalThreadState::CALLRET_STACK_SIZE` and FEX 4 KiB page contract;
   do not import LinuxEmulation/Wine classes. Fail explicitly if mapping or
   protection fails.
4. Run all three source tests and the isolated AArch64 smoke build/verifier.

## Task 2: Normal physical validation

1. Rebuild/package the ordinary macro-off managed runtime and the Playstore
   app/Android-test APKs. Verify the APK contains runtime manifest and ZIP;
   verify the runner has no diagnostic marker.
2. Replacement-install only `com.bachatas4.android` and its test package on
   device `7d6afed8`. Do not clear or uninstall.
3. Run `FexCoreSmokeDeviceTest` with the existing narrow rseq environment.
   Acceptance is exact `FEXCORE_SMOKE_BOOTSTRAP_OK` output with GPR, stack,
   and FP validation.
4. Request an independent final review. If the physical test fails, record
   only the first new boundary and leave Task 4 open.
