# Bloodborne Cache-Maintenance Fault Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Turnip `DC CVAC`/`DC CIVAC` cache maintenance from repeatedly taking the read-side shadPS4 protection-fault path, then verify Bloodborne reaches at least 30 FPS in the controlled SM8650 scene.

**Architecture:** Extend Box64's narrow native fault classifier so the two observed cache-maintenance-by-virtual-address instructions are write-like for shadPS4 invalidation. Keep the durable patch, its pure-C opcode regression test, and the managed-runtime verifier synchronized; then rebuild the full runtime and APK and compare the same device scene with `simpleperf`.

**Tech Stack:** C11, AArch64 instruction encoding, Box64, shadPS4 C++, Node.js runtime verification, Android/Gradle, ADB, simpleperf, Vulkan/Turnip.

## Global Constraints

- Match only `DC CVAC, Xt` and `DC CIVAC, Xt` apart from the five-bit `Xt` operand; do not classify every `DC` instruction.
- Preserve all existing store-pair, single-store, and `DC ZVA` classifications.
- Do not add per-fault or per-frame production logging.
- Do not change rendering resolution, graphics quality, CPU governors, thermal controls, save data, or application settings.
- Do not add arbitrary sleeps or change P2P socket semantics in this implementation.
- The fault-reduction gate is at least 90% at Turnip `dc cvac`/`dc civac`, without crashes, corruption, or stale frames.
- The controlled-scene performance target is at least 30 FPS.
- Before `assembleDebug`, run every runtime build/package command mandated by `AGENTS.md`.
- Do not install the APK until it contains both `assets/runtime/manifest.json` and `assets/runtime/runtime.zip`.

---

### Task 1: Classify Turnip cache maintenance as write-like

**Files:**
- Modify: `runtime/sources/box64/tests/test_native_write_opcode.c`
- Modify: `runtime/sources/box64/src/libtools/decopcode_native.c`
- Modify: `runtime/patches/box64-native-write-opcode.patch`
- Modify: `runtime/tests/verify-runtime.mjs`

**Interfaces:**
- Consumes: `int is_native_write_opcode(uint32_t opcode)` from `runtime/sources/box64/src/libtools/decopcode_native.h`.
- Produces: exact write-like classification for `DC CVAC, Xt` (`0xd50b7a20` base) and `DC CIVAC, Xt` (`0xd50b7e20` base), plus durable-patch verification markers.

- [ ] **Step 1: Add failing opcode cases to the live Box64 source test**

Add these entries to `cases[]` in `runtime/sources/box64/tests/test_native_write_opcode.c`, retaining every existing case:

```c
        {0xd50b7a20, 1, "dc cvac, x0"},
        {0xd50b7a27, 1, "dc cvac, x7"},
        {0xd50b7e20, 1, "dc civac, x0"},
        {0xd50b7e33, 1, "dc civac, x19"},
        {0xd5087640, 0, "dc isw, x0"},
```

The `dc isw` case proves the implementation does not accept the whole `DC` encoding family.

- [ ] **Step 2: Add a failing durable-patch check to the runtime verifier**

Define the native patch path beside the other Box64 patch paths in `runtime/tests/verify-runtime.mjs`:

```javascript
const box64NativeWriteOpcodePatchPath = resolve(
  projectRoot,
  "runtime/patches/box64-native-write-opcode.patch",
);
```

Before the `locksOnly` early exit, load the patch and require both implementation encodings and representative regression cases:

```javascript
const nativeWriteOpcodePatch = readFileSync(box64NativeWriteOpcodePatchPath, "utf8");
for (const marker of [
  "0xd50b7a20u",
  "0xd50b7e20u",
  '"dc cvac, x7"',
  '"dc civac, x19"',
  '"dc isw, x0"',
]) {
  if (!nativeWriteOpcodePatch.includes(marker)) {
    fail(`Box64 native write classifier patch is missing ${marker}`);
  }
}
```

- [ ] **Step 3: Run both tests and verify the RED state**

Run:

```bash
mkdir -p runtime/build/bachata-runtime-tests
cc -std=c11 -Wall -Wextra -Werror \
  -Iruntime/sources/box64/src/libtools \
  runtime/sources/box64/tests/test_native_write_opcode.c \
  runtime/sources/box64/src/libtools/decopcode_native.c \
  -o runtime/build/bachata-runtime-tests/native-write-opcode
runtime/build/bachata-runtime-tests/native-write-opcode
```

Expected: nonzero exit with failures for the four `dc cvac`/`dc civac` cases; `dc isw` remains correctly classified as non-write.

Run:

```bash
node runtime/tests/verify-runtime.mjs --locks-only
```

Expected: nonzero exit beginning with `Box64 native write classifier patch is missing 0xd50b7a20u`.

- [ ] **Step 4: Implement the minimal exact classifier in the live Box64 source**

Insert this block before the existing `DC ZVA` check in `runtime/sources/box64/src/libtools/decopcode_native.c`:

```c
    // Cache clean/invalidate by virtual address must take shadPS4's write-side
    // invalidation path when Turnip touches a protected GPU-tracked page.
    const uint32_t cache_maintenance = opcode & 0xffffffe0u;
    if (cache_maintenance == 0xd50b7a20u || // DC CVAC, Xt
        cache_maintenance == 0xd50b7e20u)   // DC CIVAC, Xt
        return 1;
```

Do not widen either mask and do not include `DC ISW`, `DC CVAU`, or `IC IVAU`.

- [ ] **Step 5: Synchronize the durable Box64 patch**

Update `runtime/patches/box64-native-write-opcode.patch` so its new-file hunks contain the exact classifier block from Step 4 and all five test cases from Step 1. Adjust the hunk lengths so `git apply` parses the patch; retain all pre-existing patch content.

The resulting `decopcode_native.c` patch section must contain:

```c
    // Cache clean/invalidate by virtual address must take shadPS4's write-side
    // invalidation path when Turnip touches a protected GPU-tracked page.
    const uint32_t cache_maintenance = opcode & 0xffffffe0u;
    if (cache_maintenance == 0xd50b7a20u || // DC CVAC, Xt
        cache_maintenance == 0xd50b7e20u)   // DC CIVAC, Xt
        return 1;
```

The resulting test patch section must contain:

```c
        {0xd50b7a20, 1, "dc cvac, x0"},
        {0xd50b7a27, 1, "dc cvac, x7"},
        {0xd50b7e20, 1, "dc civac, x0"},
        {0xd50b7e33, 1, "dc civac, x19"},
        {0xd5087640, 0, "dc isw, x0"},
```

- [ ] **Step 6: Verify GREEN, patch durability, and regression safety**

Run:

```bash
cc -std=c11 -Wall -Wextra -Werror \
  -Iruntime/sources/box64/src/libtools \
  runtime/sources/box64/tests/test_native_write_opcode.c \
  runtime/sources/box64/src/libtools/decopcode_native.c \
  -o runtime/build/bachata-runtime-tests/native-write-opcode
runtime/build/bachata-runtime-tests/native-write-opcode
node runtime/tests/verify-runtime.mjs --locks-only
git -C runtime/sources/box64 apply --reverse --check \
  "$PWD/runtime/patches/box64-native-write-opcode.patch"
git diff --check -- \
  runtime/patches/box64-native-write-opcode.patch \
  runtime/tests/verify-runtime.mjs
```

Expected: opcode test exits zero with no output; verifier prints `runtime locks verified`; reverse patch check exits zero; diff check emits nothing.

- [ ] **Step 7: Commit the classifier and verification contract**

```bash
git add runtime/patches/box64-native-write-opcode.patch \
  runtime/tests/verify-runtime.mjs
git commit -m "perf(box64): classify cache maintenance faults"
```

Do not stage the `runtime/sources/box64` submodule worktree; the durable patch is the root-repository source of truth.

---

### Task 2: Build, package, install, and validate the managed runtime

**Files:**
- Consume: `runtime/patches/box64-native-write-opcode.patch`
- Consume: `runtime/tests/verify-runtime.mjs`
- Generate (ignored build artifact): `android/BachataS4/app/src/main/assets/runtime/runtime.zip`
- Generate (ignored build artifact): `android/BachataS4/app/src/main/assets/runtime/manifest.json`
- Generate (ignored build artifact): `android/BachataS4/app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: the exact classifier and verifier contract committed by Task 1.
- Produces: a verified debug APK containing the rebuilt managed runtime and a controlled device profile comparing fault rate and FPS.

- [ ] **Step 1: Build and package the runtime in the mandated order**

From the repository root, run:

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
```

Expected: both native builds succeed; packaging prints the runtime archive and manifest information; runtime verification exits zero.

- [ ] **Step 2: Run Android verification and assemble the APK**

Run:

```bash
cd android/BachataS4
./gradlew test lintDebug assembleDebug
```

Expected: Gradle ends with `BUILD SUCCESSFUL` and produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Enforce the managed-runtime APK gate**

Run from `android/BachataS4`:

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk \
  | grep -E 'assets/runtime/(manifest\.json|runtime\.zip)'
```

Expected: exactly one entry for `assets/runtime/manifest.json` and one for `assets/runtime/runtime.zip`. Stop without installing if either entry is absent.

- [ ] **Step 4: Preserve settings and install the verified APK**

Capture the active settings before installation:

```bash
adb exec-out run-as com.bachatas4.android cat files/settings/global.json \
  > /tmp/bachata-global-before-cache-fix.json
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb exec-out run-as com.bachatas4.android cat files/settings/global.json \
  > /tmp/bachata-global-after-cache-fix.json
cmp /tmp/bachata-global-before-cache-fix.json \
  /tmp/bachata-global-after-cache-fix.json
```

Expected: installation reports `Success`; `cmp` exits zero, proving the same Custom profile with `BOX64_DYNAREC_DIRTY=2` remains active for the controlled comparison.

- [ ] **Step 5: Reproduce the same stationary Bloodborne scene**

Launch Bloodborne, enter the existing save, stand in the first controllable room used for the baseline, keep the same camera direction, and do not move for 30 seconds. Confirm the FPS overlay is visible before recording.

- [ ] **Step 6: Record post-fix CPU and page-fault evidence**

Resolve the backend PID and record the same counters used for the baseline:

```bash
pid=$(adb shell "ps -A -o PID,ARGS | grep libbachata_host_loader | grep -v grep | head -1" \
  | awk '{print $1}' | tr -d '\r')
test -n "$pid"
adb shell "run-as com.bachatas4.android /system/bin/simpleperf stat \
  --duration 5 -p $pid --per-thread --csv -e task-clock,page-faults"
adb shell "simpleperf record --app com.bachatas4.android --duration 8 \
  -p $pid -e page-faults -o /data/local/tmp/bloodborne-cache-fix.data"
adb shell "simpleperf report -i /data/local/tmp/bloodborne-cache-fix.data \
  --sort comm,pid,tid,dso,symbol --children" | head -120
adb shell 'cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null'
adb shell 'dumpsys thermalservice 2>/dev/null | grep "Thermal Status:" | head -1'
adb exec-out screencap -p > /tmp/bloodborne-cache-fix.png
```

Expected: the report no longer has Turnip `libvulkan_freedreno.so[+2f5690]` near 40% of page-fault samples, and the `shadPS4:Present` fault count is at least 90% below the 15,361-fault five-second baseline.

- [ ] **Step 7: Verify the stale P2P log is absent from the packaged runtime**

Run:

```bash
adb shell run-as com.bachatas4.android grep -F -c \
  'p2p_sockets.cpp:63 Accept: (STUBBED) called' \
  files/runtime-home/.local/share/shadPS4/log/shad_log.txt || true
```

Expected: `0`. Bloodborne's own `sceNetAccept Failed` TTY output may remain; no socket behavior change is part of this task.

- [ ] **Step 8: Apply the success gate**

Pass when all of the following are true:

- Turnip `dc cvac`/`dc civac` fault samples fall by at least 90%.
- The scene renders continuously without corruption, stale frames, crash, or backend restart.
- The overlay sustains at least 30 FPS during the stationary 30-second window.
- The APK runtime-asset and settings-preservation gates passed.

If the fault reduction passes but FPS remains below 30, stop implementation changes and capture a new eight-second `cpu-cycles` profile:

```bash
adb shell "simpleperf record --app com.bachatas4.android --duration 8 \
  -p $pid -e cpu-cycles -o /data/local/tmp/bloodborne-next-bottleneck.data"
adb shell "simpleperf report -i /data/local/tmp/bloodborne-next-bottleneck.data \
  --sort comm,pid,tid,dso,symbol --children" | head -120
```

Report the newly dominant symbol/thread before proposing another fix; do not stack speculative changes onto the cache-maintenance patch.
