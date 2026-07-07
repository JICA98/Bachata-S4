# Box64 Native Write-Fault Classification Plan

**Goal:** Correctly mark page faults caused by native AArch64 stores in Box64-wrapped host libraries so shadPS4 invalidates GPU-tracked memory instead of repeatedly reading it.

**Evidence:** Bloodborne stalls after the title screen with millions of read faults. Live LLDB capture shows `memcpy` faulting on `stp q2, q3, [x3,#0x30]` (`0xad018c62`) and `memset` faulting on `str q0, [x0]` (`0x3d800000`). `write_opcode()` currently ignores its `native_ip` argument.

### Task 1: Native AArch64 opcode regression test

**Files:**
- Create: `runtime/sources/box64/tests/test_native_write_opcode.c`
- Create: `runtime/sources/box64/src/libtools/decopcode_native.h`

- [ ] Add test vectors for STP/STR writes, LDP/LDR reads, and DC ZVA writes.
- [ ] Compile and run the test; verify it fails because the classifier is missing.

### Task 2: Minimal native store classifier

**Files:**
- Create: `runtime/sources/box64/src/libtools/decopcode_native.c`
- Modify: `runtime/sources/box64/src/libtools/decopcode.c`
- Modify: `runtime/sources/box64/CMakeLists.txt`

- [ ] Classify unambiguous AArch64 store-pair, single-store, exclusive-store, SIMD-structure-store, and DC ZVA encodings.
- [ ] Prefer a recognized native write in `write_opcode()`; preserve x86 decoding as fallback.
- [ ] Run the regression test and Box64 host build.

### Task 3: Package and device verification

**Files:**
- Modify: `runtime/patches/box64-vex-write-opcode.patch` (extend durable patch)

- [ ] Package runtime, verify runtime manifest, build APK, and install it.
- [ ] Launch Bloodborne and pass the title screen.
- [ ] Confirm fault logs report writes, readback loops stop, flips resume, and FPS becomes non-zero.
- [ ] Capture logs and screenshot under `/home/jica/logs/`.

### Task 4: Follow-up Box64 audit

- [ ] Inventory remaining native memory-writing AArch64 instruction classes not covered by the minimal classifier.
- [ ] Compare against upstream Box64 and propose an upstreamable decoder test matrix.
- [ ] Address additional gaps only when a test or captured fault demonstrates them.
