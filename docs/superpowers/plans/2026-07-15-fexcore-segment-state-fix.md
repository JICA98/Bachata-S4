# FEXCore Guest Segment-State Fix Plan

**Goal:** Make the ARM64 FEXCore smoke initialize the minimum x86-64 guest
segment state required before its first translated instruction, then prove the
real tablet smoke gate passes.

**Root cause evidence:** With Android's host-glibc rseq disabled, the smoke
reaches `Context::ExecuteThread` and faults at address `0x4` in
`FEXCore::Frontend::Decoder::DecodeInstructionsAtEntry`. The raw
`CreateThread` frame has null selector-array pointers and selector zero. FEX's
own Linux embedding sets both selector arrays, selects
`CPUState::DEFAULT_USER_CS << 3`, initializes that GDT entry, and sets its
64-bit `L` flag before execution.

**Constraints:** Do not modify FEXCore or GameNative. Do not copy Wine,
ARM64EC, rootfs, or launcher behavior. Do not add error suppression, stubs,
or a global security change. Do not commit. Preserve unrelated worktree
changes. Treat tablet validation as required; Task 4 remains open until it
passes.

## Task 1: Regression test and smallest state initialization

**Files:**

- Create: `runtime/tests/fexcore-guest-state-source.test.mjs`
- Modify: `runtime/probes/fexcore-smoke.cpp`

1. Add a source-level regression test that fails when the smoke starts a
   FEX thread without a stable 32-entry GDT, GDT/LDT selector-array pointers,
   a default code selector, and a 64-bit code descriptor. The test must prove
   initialization happens before `ExecuteThread`.
2. Run the exact new test and record its expected failure before production
   code changes.
3. Add one local, lifetime-safe guest segment-state owner to the probe.
   Initialize only the generic FEX x86-64 state: both selector-array pointers,
   `DEFAULT_USER_CS << 3`, zero base, FEX's normal limit, cached base, `L = 1`,
   and `D = 0`.
4. Run the new test, the existing diagnostic source test, and an isolated
   AArch64 smoke build/verifier. Do not modify FEX source.

## Task 2: Physical Android proof

**Files:**

- Modify only generated runtime/APK outputs as produced by existing scripts.

1. Rebuild and package the managed runtime using the repository's Android
   prerequisite sequence, then build the Playstore debug APK and Android test
   APK. Verify `assets/runtime/manifest.json` and `assets/runtime/runtime.zip`
   are present in the installed candidate.
2. Replacement-install only the project app/test packages on device
   `7d6afed8`; do not clear data or uninstall.
3. Run `FexCoreSmokeDeviceTest` with `GLIBC_TUNABLES=glibc.pthread.rseq=0`.
   The acceptance output is `FEXCORE_SMOKE_BOOTSTRAP_OK` with GPR, stack, and
   floating-point checks passing. Capture focused logcat on failure.
4. Independently review the final diff and validations. If device proof does
   not pass, report the earliest new failure and leave Task 4 open.
