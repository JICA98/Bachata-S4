# Bloodborne Cache-Maintenance Fault Performance Design

## Objective

Raise Bloodborne gameplay performance from the reproduced 5–8 FPS toward at least 30 FPS on Snapdragon 8 Gen 3 and newer devices by removing the dominant protected-memory fault loop between Turnip, Box64, and shadPS4.

The first implementation targets the proven fault source. It does not introduce unrelated rendering-quality changes, resolution reductions, CPU-governor changes, or speculative socket delays.

## Evidence

The controlled reproduction used a OnePlus OPD2403 with Snapdragon SM8650, the packaged host-glibc Box64 runtime, and the imported Turnip driver `turnip-9793eeea56ede6ce`.

- The in-game overlay reported 5.6 FPS in the first controllable room.
- GPU busy time was approximately 46–60%, so the Adreno GPU was not saturated.
- The backend used approximately 4.7 CPU cores.
- `BOX64_DYNAREC_DIRTY=2` was verified in the live process and did not materially change thread utilization or fault rates.
- A five-second per-thread capture recorded 15,361 faults in `shadPS4:Present` and 34,824 faults across `Game:Main` and the five `GXWorker` threads.
- An eight-second page-fault profile recorded 114,554 faults. Of sampled faults, 40.62% occurred in Turnip at offset `0x2f5690` and approximately 49% occurred at a shared Box64-translated native address.
- Disassembly of Turnip offset `0x2f5690` identified `dc cvac, x0` (`0xd50b7a20`). The adjacent Turnip cache-maintenance path uses `dc civac, x0` (`0xd50b7e20`).
- Disassembly of the translated fault site identified `str x1, [x17], #8`, which the existing native-write classifier already recognizes.

The evidence isolates cache-maintenance faults as the missing classification rather than an incomplete ordinary-store classification or an ineffective Box64 preset.

## Root Cause

shadPS4 protects GPU-tracked guest pages and handles host access violations as either reads or writes. Because shadPS4 itself is an x86-64 process translated by Box64, Box64 must construct an x86-compatible signal context from the faulting AArch64 instruction.

The durable Box64 patch currently recognizes ordinary AArch64 stores and `DC ZVA` as write-like operations. It does not recognize Turnip's `DC CVAC` or `DC CIVAC` cache-maintenance operations. When Turnip cleans a protected guest range, Box64 reports the access through the non-write path. shadPS4 therefore does not perform the write-side invalidation needed to release the protected GPU-tracked page, and cache cleaning repeatedly faults while presenting frames.

For this integration, cache clean/invalidate-by-address operations are classified as write-like. This is an interoperability classification for shadPS4 memory invalidation, not a claim that the architectural instruction stores new application data.

## Design

### Native access classification

Extend `is_native_write_opcode()` in the durable Box64 patch to recognize the exact cache-maintenance-by-virtual-address encodings required by the observed Turnip paths:

- `DC CVAC, Xt`, ignoring the five-bit register operand;
- `DC CIVAC, Xt`, ignoring the five-bit register operand.

Keep the match exact apart from `Xt`. Do not classify unrelated system instructions or all `DC` operations as writes. Preserve the existing load/store, store-pair, and `DC ZVA` behavior.

### Regression tests

Extend Box64's native opcode regression test before changing the classifier.

Positive cases:

- observed `dc cvac, x0`;
- `dc cvac` with a nonzero register operand;
- adjacent Turnip `dc civac, x0`;
- `dc civac` with a nonzero register operand.

Negative cases:

- an unrelated `DC` system instruction;
- existing load instructions;
- `ret`.

The new positive cases must fail against the current classifier, then pass after the minimal implementation.

### Runtime packaging

Update `runtime/patches/box64-native-write-opcode.patch`, rebuild shadPS4 and the Box64 host runtime through the repository scripts, and create a new managed-runtime manifest and archive. Verify the runtime lock and packaged APK assets according to `AGENTS.md`.

The rebuild also picks up the current `P2PSocket::Accept` implementation, which no longer emits the stale per-call `LOG_ERROR` present in the installed runtime. No arbitrary delay or altered socket semantics will be added during this phase because profiling attributed only about 2.7% of one CPU to that thread. Networking behavior will be changed only if post-fix profiling shows it is still a material frame-time bottleneck.

### Diagnostics

Keep production logging bounded. Use external `simpleperf`, GPU-busy counters, thermal status, and the existing FPS overlay for comparison. Do not add per-fault or per-frame production logs.

## Verification

### Host verification

- Compile and run the native opcode regression test.
- Build the host Box64 runtime successfully.
- Run existing runtime verification tests.
- Build the Android unit tests and lint checks required by `AGENTS.md`.
- Verify the APK contains both `assets/runtime/manifest.json` and `assets/runtime/runtime.zip`.

### Device verification

Use the same Bloodborne save, first controllable room, camera direction, Turnip driver, and a stationary 30-second capture.

Record before and after:

- FPS and frame time;
- total page faults over eight seconds;
- per-thread page faults;
- percentage of page-fault samples at Turnip `dc cvac`;
- backend CPU utilization;
- GPU busy percentage;
- thermal status;
- `sceNetAccept` log rate.

The cache-maintenance fix passes its primary gate when faults at Turnip `dc cvac`/`dc civac` fall by at least 90% without crashes, rendering corruption, or stale frames. The performance target is at least 30 FPS in the controlled scene. If the fault gate passes but FPS remains below 30, the next profile must identify the newly exposed bottleneck before another behavior change is proposed.

## Risks and Rollback

Treating cache maintenance as write-like can invalidate more cached GPU state than a read classification. The exact opcode masks and regression negatives limit this effect to the observed Turnip cache-maintenance paths. Rendering correctness and stale-frame checks are mandatory on device.

Rollback consists of reverting the two exact classifier cases from the durable patch and rebuilding the managed runtime. No user data, save data, game files, or persistent application schema changes are involved.

## Out of Scope

- Native ARM64 shadPS4 migration;
- resolution or graphics-quality reductions;
- device-specific governor or thermal overrides;
- broad disabling of shadPS4 memory tracking;
- speculative changes to Bloodborne guest networking behavior;
- claiming 30 FPS until the controlled device verification demonstrates it.
