# FEXCore AArch64 Tablet Proof Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Use `test-driven-development` for every behavior change and `verification-before-completion` before claiming the phase is complete. Track the checkboxes in this file.

**Goal:** Prove that a native AArch64/glibc host executable can embed the pinned FEXCore revision, execute representative x86-64 guest code on the SM8650 tablet, and survive the existing managed-runtime packaging and Android security boundary without invoking Box64.

**Architecture:** Add FEX as a locked source component and carry a small upstream patch that exposes a FEXCore-only CMake build. Cross-compile one `fexcore-smoke` AArch64 executable against the repository's glibc sysroot, package it beside the existing Box64 runtime, and launch it through the APK-owned AArch64 glibc loader. The smoke executable owns synthetic x86-64 code pages and validates GPRs, stack/calls, SSE2, native host threads, FS-base TLS, host-to-guest callbacks, and translated-code invalidation. This phase does not modify shadPS4 or select a game CPU backend.

**Tech Stack:** C++20, FEXCore, Clang cross-compilation, CMake/Ninja, ELF/readelf, Node.js tests and runtime packaging, Kotlin/JUnit, Android instrumentation, ADB.

## Phase Boundary and Acceptance Contract

- Work only in `/home/jica/repo/Bachata-S4/.worktrees/fex-arm64-sonic` on `feature/fex-arm64-sonic`.
- Pin FEX exactly at `f2b679f6028ce1c38875233aecfcf5d3f8ebecec` from `https://github.com/FEX-Emu/FEX.git`, license `MIT`.
- Build only FEXCore, its required common libraries, and `fexcore-smoke`. Do not build or package `FEXInterpreter`, LinuxEmulation, RootFS, thunks, tools, tests, or the full FEX executable. FEXCore's internal interpreter fallback sources are allowed because they are part of FEXCore itself.
- Target AArch64 Linux/glibc, not Android/Bionic. The executable must be ELF64 machine 183 with interpreter `/lib/ld-linux-aarch64.so.1`.
- Permit exactly these sorted `DT_NEEDED` entries: `libc.so.6`, `libgcc_s.so.1`, `libm.so.6`, `libstdc++.so.6`.
- Reject non-4096-byte host pages before `FEXCore::Config::Initialize`, `FEX::FetchHostFeatures`, or any FEX context creation.
- The only success line is:

  ```text
  FEXCORE_SMOKE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok stack=ok fp=ok threads=ok tls=ok callback=ok invalidation=ok
  ```

  Task 2 deliberately commits a three-check bootstrap marker to create a real tablet RED state; Task 5 must replace it with this final marker before qualification.

- A page-size failure exits 2 and prints exactly `FEXCORE_SMOKE_FAIL check=page-size expected=4096 actual=<decimal>`.
- Every other failed check exits nonzero and prints exactly one line beginning `FEXCORE_SMOKE_FAIL check=<name>`.
- The Android native probe path must contain no `box64` argument and must never fall back to Box64.
- Keep the current Box64 runtime and launch behavior unchanged.
- Never uninstall the app, clear app data, delete a managed runtime, or touch game/save data. Device installation must use `adb install -r`.
- Do not start the shadPS4 `CpuBackend` extraction, native shadPS4 build, Sonic, Bloodborne, backend UI, backend default, or performance work in this phase.

---

### Task 1: Freeze the accepted shadPS4 baseline and lock FEX

**Files:**

- Create: `runtime/evidence/baseline/dbe5165b-ctest.json`
- Create: `runtime/tests/shadps4-baseline.test.mjs`
- Create: `runtime/tests/run-ctest-with-baseline.mjs`
- Modify: `runtime/locks/components.lock.json`
- Modify: `runtime/tests/verify-runtime.mjs`

**Interfaces:**

- Baseline evidence schema: revision, totals, common process result, and exact failing CTest names.
- Runtime lock entry: `name`, HTTPS `url`, 40-hex `revision`, SPDX-compatible `license`.

- [ ] **Step 1: Add the failing baseline evidence test**

Create `runtime/tests/shadps4-baseline.test.mjs` with `node:test`. Import the pure catalog/exclusion helpers from `run-ctest-with-baseline.mjs`, load `runtime/evidence/baseline/dbe5165b-ctest.json` relative to the repository root, and assert:

```javascript
assert.equal(evidence.schemaVersion, 1);
assert.equal(evidence.sourceRevision, "dbe5165b86dfd4f9b251be5175d59cdadb26c3b9");
assert.deepEqual(evidence.ctest, { total: 426, passed: 382, failed: 44 });
assert.deepEqual(evidence.failureProcess, { signal: 11, exitStatus: 139 });
assert.equal(new Set(evidence.failingTests).size, 44);
assert.deepEqual([...evidence.failingTests].sort(), [...EXPECTED_FAILURES].sort());
```

Define `EXPECTED_FAILURES` in that test as the exact 44 names listed in Step 3. Also reject any unknown top-level key so the evidence format cannot drift silently.

For the runner helpers, assert that an input catalog containing 426 unique names, including all 44 recorded failures, produces an exclusion file of exactly 44 newline-delimited full names, never a broad `GcnTest` regular expression, and leaves exactly 382 runnable tests. Reject a missing allowlisted name, a duplicate name, or any total other than 426.

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
node --test runtime/tests/shadps4-baseline.test.mjs
```

Expected: failure because the evidence and runner do not exist.

- [ ] **Step 3: Add the exact baseline evidence**

Create the JSON file with this complete content contract:

```json
{
  "schemaVersion": 1,
  "sourceRevision": "dbe5165b86dfd4f9b251be5175d59cdadb26c3b9",
  "ctest": {"total": 426, "passed": 382, "failed": 44},
  "failureProcess": {"signal": 11, "exitStatus": 139},
  "failurePhase": "post-assertion teardown",
  "failingTests": [
    "GcnTest.add_f32",
    "GcnTest.add_nan",
    "GcnTest.add_f16",
    "GcnTest.add_f16_clamp",
    "GcnTest.add_f16_neg",
    "GcnTest.add_f16_opsel_hi",
    "GcnTest.sub_f16",
    "GcnTest.mul_legacy_nan",
    "GcnTest.mul_nan",
    "GcnTest.min_legacy_nan",
    "GcnTest.min_nan",
    "GcnTest.add3_u32_1",
    "GcnTest.add3_u32_2",
    "GcnTest.add3_u32_3",
    "GcnTest.add3_u32_4",
    "GcnTest.or3_u32_1",
    "GcnTest.or3_u32_2",
    "GcnTest.or3_u32_3",
    "GcnTest.or3_u32_4",
    "GcnTest.and_or_b32_1",
    "GcnTest.and_or_b32_2",
    "GcnTest.and_or_b32_3",
    "GcnTest.and_or_b32_4",
    "GcnTest.and_or_b32_5",
    "GcnTest.and_or_b32_6",
    "GcnTest.and_or_b32_7",
    "GcnTest.and_or_b32_8",
    "GcnTest.mad_mix_f32_1",
    "GcnTest.mad_mix_f32_2",
    "GcnTest.mad_mixlo_f16_1",
    "GcnTest.mad_mixhi_f16_1",
    "GcnTest.lshrrev_b16_1",
    "GcnTest.lshrrev_b16_2",
    "GcnTest.lshrrev_b16_3",
    "GcnTest.lshlrev_b16_1",
    "GcnTest.ashrrev_i16_1",
    "GcnTest.pk_add_f16_1",
    "GcnTest.pk_add_f16_2",
    "GcnTest.pk_add_f16_3",
    "GcnTest.pk_add_f16_4",
    "GcnTest.pk_add_f16_5",
    "GcnTest.pk_add_f16_neg_lo",
    "GcnTest.pk_add_f16_neg_hi",
    "GcnTest.pk_add_f16_op_sel_reversed"
  ]
}
```

Create `runtime/tests/run-ctest-with-baseline.mjs` as both an importable module and CLI. The CLI accepts exactly one configured CMake build directory, runs `ctest --test-dir <dir> --show-only=json-v1` without a shell, validates the exact 426-name catalog against the evidence, writes the 44 full names to a temporary file, then runs:

```text
ctest --test-dir <dir> --exclude-from-file <temporary-file> --output-on-failure
```

It must propagate CTest's exit status, require the summary to report 382 tests and zero failures, and remove the temporary file in `finally`. Run the Node test again. Expected: the evidence and pure runner-helper tests pass.

- [ ] **Step 4: Make the runtime lock verifier expect FEX first**

Append this object to `EXPECTED_COMPONENTS` in `runtime/tests/verify-runtime.mjs`, preserving the lock-file order:

```javascript
{
  name: "fex",
  url: "https://github.com/FEX-Emu/FEX.git",
  revision: "f2b679f6028ce1c38875233aecfcf5d3f8ebecec",
  license: "MIT",
},
```

Run:

```bash
node runtime/tests/verify-runtime.mjs --locks-only
```

Expected RED: `Locked components differ from approved upstreams`.

- [ ] **Step 5: Add the matching FEX lock entry**

Append this object to `runtime/locks/components.lock.json` after `mesa`:

```json
{"name":"fex","url":"https://github.com/FEX-Emu/FEX.git","revision":"f2b679f6028ce1c38875233aecfcf5d3f8ebecec","license":"MIT"}
```

Run:

```bash
node runtime/tests/verify-runtime.mjs --locks-only
node --test runtime/tests/shadps4-baseline.test.mjs
```

Expected GREEN: `runtime locks verified: components=6 inputs=30` and one passing baseline test.

- [ ] **Step 6: Commit the baseline and lock**

```bash
git add runtime/evidence/baseline/dbe5165b-ctest.json runtime/tests/shadps4-baseline.test.mjs runtime/tests/run-ctest-with-baseline.mjs runtime/locks/components.lock.json runtime/tests/verify-runtime.mjs
git commit -m "test: freeze FEX phase-zero baseline"
```

---

### Task 2: Cross-build the FEXCore-only smoke executable

**Files:**

- Create: `runtime/patches/fex-fexcore-only.patch`
- Create: `runtime/probes/fexcore-smoke.cpp`
- Create: `runtime/scripts/build-fexcore-smoke-aarch64.sh`
- Create: `runtime/tests/verify-fexcore-smoke-build.mjs`
- Modify: `runtime/tests/verify-runtime.mjs`

**Interfaces:**

- Build output: `runtime/build/fexcore-smoke-stage/bin/fexcore-smoke`.
- FEX patch options: `BUILD_FEXCORE_ONLY=ON` and absolute `FEXCORE_SMOKE_SOURCE`.
- Build verifier: one artifact path argument, exit 0 only for the approved AArch64/glibc ELF and pinned revision marker.

- [ ] **Step 1: Add failing durable-source and artifact checks**

Before the `locksOnly` early return in `runtime/tests/verify-runtime.mjs`, read `runtime/patches/fex-fexcore-only.patch` and require all of these literal markers:

```text
BUILD_FEXCORE_ONLY
FEXCORE_SMOKE_SOURCE
add_executable(fexcore-smoke
FEXCore
CommonTools
install(TARGETS fexcore-smoke
```

Create `runtime/tests/verify-fexcore-smoke-build.mjs`. It must:

1. Require exactly one artifact path.
2. Run `readelf -h`, `readelf -l`, and `readelf -d` without a shell.
3. Require `Class: ELF64`, `Data: 2's complement, little endian`, `Machine: AArch64`, and interpreter `/lib/ld-linux-aarch64.so.1`.
4. Parse every `(NEEDED)` entry, sort it, and require exact equality with `libc.so.6`, `libgcc_s.so.1`, `libm.so.6`, `libstdc++.so.6`.
5. Run `strings` without a shell and require the full pinned FEX revision plus the bootstrap success field names `gpr`, `stack`, and `fp`.
6. Reject any `FEXInterpreter`, `LinuxEmulation`, `RootFS`, or thunk executable in `runtime/build/fexcore-smoke-stage`.

Run:

```bash
node runtime/tests/verify-runtime.mjs --locks-only
node runtime/tests/verify-fexcore-smoke-build.mjs runtime/build/fexcore-smoke-stage/bin/fexcore-smoke
```

Expected RED: the durable patch and built artifact do not exist.

- [ ] **Step 2: Add the FEXCore-only upstream patch**

Create `runtime/patches/fex-fexcore-only.patch` against the pinned FEX top-level `CMakeLists.txt`. Add cache options:

```cmake
option(BUILD_FEXCORE_ONLY "Build only FEXCore and an embedding smoke executable" OFF)
set(FEXCORE_SMOKE_SOURCE "" CACHE FILEPATH "Embedding smoke executable source")
```

In `BUILD_FEXCORE_ONLY` mode, configure only the required dependency targets, `FEXCore`, `Source/Common`, and `Source/Tools/CommonTools`; skip normal `Data/binfmts`, LinuxEmulation, FEXInterpreter, RootFS, thunks, normal tools, install extras, and tests. Define the smoke target exactly as:

```cmake
if (BUILD_FEXCORE_ONLY)
  if (NOT IS_ABSOLUTE "${FEXCORE_SMOKE_SOURCE}")
    message(FATAL_ERROR "FEXCORE_SMOKE_SOURCE must be an absolute path")
  endif()
  add_executable(fexcore-smoke "${FEXCORE_SMOKE_SOURCE}")
  target_compile_features(fexcore-smoke PRIVATE cxx_std_20)
  target_include_directories(fexcore-smoke PRIVATE
    "${CMAKE_BINARY_DIR}/generated"
    "${CMAKE_SOURCE_DIR}/Source/Tools/CommonTools")
  target_link_libraries(fexcore-smoke PRIVATE
    FEXCore Common CommonTools JemallocLibs ${PTHREAD_LIB})
  install(TARGETS fexcore-smoke RUNTIME DESTINATION bin)
endif()
```

The patch must leave the normal upstream build unchanged when the option is off.

- [ ] **Step 3: Implement the smoke executable's page and core lifecycle**

In `runtime/probes/fexcore-smoke.cpp`:

1. Call `sysconf(_SC_PAGESIZE)` first. On any value other than 4096, print the exact page-size failure line and return 2 before touching FEX.
2. Initialize configuration, fetch host features, create a context, create the dummy signal delegator, and install a minimal syscall handler derived from `FEXCore::HLE::SyscallHandler`. Its constructor must set the protected `OSABI` field to `FEXCore::HLE::SyscallOSABI::OS_GENERIC`; it must implement `HandleSyscall`, `QueryGuestExecutableRange`, and `LookupExecutableFileSection`.
3. Call `SetSignalDelegator`, `SetSyscallHandler`, `EnableExitOnHLT`, and `InitCore` in upstream `CodeSizeValidation` order.
4. Map separate RWX guest-code pages and RW stack/TLS pages with `mmap`; reject `MAP_FAILED`; unmap all pages through RAII on every exit.
5. Give every failure path a stable check name and exactly one output line.

Keep the revision as a compiled constant:

```cpp
constexpr std::string_view kFexRevision =
  "f2b679f6028ce1c38875233aecfcf5d3f8ebecec";
```

- [ ] **Step 4: Implement the bootstrap guest execution checks**

Use `CreateThread`, `ExecuteThread`, `ReconstructXMMRegisters`, and `DestroyThread`. The bootstrap implementation must validate:

- `gpr`: `mov`, `add`, and `xor` produce exact 64-bit sentinel results.
- `stack`: an x86-64 `call`/`ret`, `push`/`pop`, and `[rsp]` load preserve the stack sentinel and restore the original RSP.
- `fp`: `addsd xmm0,xmm1` returns the exact IEEE-754 value after `ReconstructXMMRegisters`.

End with this temporary bootstrap line and return 0. Do not print progress lines on success:

```text
FEXCORE_SMOKE_BOOTSTRAP_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok stack=ok fp=ok
```

Do not implement `threads`, `tls`, `callback`, or `invalidation` yet; their failing tablet acceptance test and implementation land together in Task 5.

- [ ] **Step 5: Add the pinned, idempotent cross-build script**

Create executable `runtime/scripts/build-fexcore-smoke-aarch64.sh` with `set -euo pipefail`. It must:

1. Resolve the repository root from `BASH_SOURCE`.
2. Read the FEX URL and revision from `components.lock.json` with Node, not duplicated shell constants.
3. Run `runtime/scripts/checkout-component.sh fex <url> <revision>`.
4. Initialize only these pinned FEX submodules:

   ```text
   External/unordered_dense
   External/rpmalloc
   External/xxhash
   External/fmt
   External/range-v3
   Source/Common/cpp-optparse
   ```

5. Verify the FEX HEAD equals the lock revision and every initialized submodule has a clean worktree.
6. Apply `fex-fexcore-only.patch` with `git apply --check` followed by `git apply`. A rerun starts from the forced pinned checkout, so the patch remains idempotent.
7. Configure with Ninja and exactly these material flags:

   ```bash
   -DCMAKE_SYSTEM_NAME=Linux
   -DCMAKE_SYSTEM_PROCESSOR=aarch64
   -DCMAKE_C_COMPILER=clang
   -DCMAKE_CXX_COMPILER=clang++
   -DCMAKE_C_COMPILER_TARGET=aarch64-linux-gnu
   -DCMAKE_CXX_COMPILER_TARGET=aarch64-linux-gnu
   -DCMAKE_FIND_ROOT_PATH=/usr/aarch64-linux-gnu
   -DCMAKE_BUILD_TYPE=Release
   -DCMAKE_INSTALL_PREFIX="${PROJECT_ROOT}/runtime/build/fexcore-smoke-stage"
   -DBUILD_FEXCORE_ONLY=ON
   -DFEXCORE_SMOKE_SOURCE="${PROJECT_ROOT}/runtime/probes/fexcore-smoke.cpp"
   -DBUILD_TESTING=OFF
   -DBUILD_FEX_LINUX_TESTS=OFF
   -DBUILD_THUNKS=OFF
   -DBUILD_FEXCONFIG=OFF
   -DENABLE_GDB_SYMBOLS=OFF
   -DENABLE_LTO=OFF
   -DENABLE_JEMALLOC_GLIBC_ALLOC=OFF
   -DENABLE_OFFLINE_TELEMETRY=OFF
   -DENABLE_VIXL_DISASSEMBLER=OFF
   -DENABLE_VIXL_SIMULATOR=OFF
   -DENABLE_ZYDIS=OFF
   -DENABLE_FEXCORE_PROFILER=OFF
   ```

8. Build only target `fexcore-smoke`, remove and recreate only `runtime/build/fexcore-smoke-stage`, install, strip with `aarch64-linux-gnu-strip`, then invoke the build verifier.

- [ ] **Step 6: Build and verify GREEN**

Run:

```bash
chmod +x runtime/scripts/build-fexcore-smoke-aarch64.sh
runtime/scripts/build-fexcore-smoke-aarch64.sh
node runtime/tests/verify-fexcore-smoke-build.mjs runtime/build/fexcore-smoke-stage/bin/fexcore-smoke
node runtime/tests/verify-runtime.mjs --locks-only
```

Expected: the artifact verifier reports AArch64, the exact interpreter/dependencies, pinned revision, and three bootstrap check markers. This is a build contract only; execution remains RED until the tablet task.

- [ ] **Step 7: Commit the FEXCore build proof**

```bash
git add runtime/patches/fex-fexcore-only.patch runtime/probes/fexcore-smoke.cpp runtime/scripts/build-fexcore-smoke-aarch64.sh runtime/tests/verify-fexcore-smoke-build.mjs runtime/tests/verify-runtime.mjs
git commit -m "feat: build pinned FEXCore smoke"
```

---

### Task 3: Package and verify the AArch64 smoke artifact

**Files:**

- Modify: `runtime/scripts/package-runtime.mjs`
- Modify: `runtime/tests/verify-runtime.mjs`
- Modify: `runtime/tests/verify-apk-runtime.mjs`
- Modify: `documents/android-building.md`
- Modify: `AGENTS.md`

**Interfaces:**

- Runtime ZIP path: `bin/fexcore-smoke`.
- APK native loader path: `lib/arm64-v8a/libbachata_host_loader.so`.
- Runtime manifest remains schema 1 in this phase; its file list/hash covers the new runner.

- [ ] **Step 1: Add failing runtime ZIP checks**

Add `bin/fexcore-smoke` to `REQUIRED_RUNTIME_PATHS` in `runtime/tests/verify-runtime.mjs`. After locating the ZIP entry, require a valid ELF magic, ELF64 class, little-endian encoding, and `e_machine === 183`. Also require that the file's manifest size and SHA-256 already match through the existing generic file loop.

Run:

```bash
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
```

Expected RED: `bin/fexcore-smoke` is absent from the ZIP/manifest.

- [ ] **Step 2: Copy the staged artifact into the runtime**

In `runtime/scripts/package-runtime.mjs`, define:

```javascript
const fexcoreSmokeBinary = resolve(
  projectRoot,
  "runtime/build/fexcore-smoke-stage/bin/fexcore-smoke",
);
```

Require it as a regular file before mutating the output directory, then copy it with mode `0o755` to `join(rootfs, "bin/fexcore-smoke")` before manifest generation. Do not copy any other FEX file.

- [ ] **Step 3: Extend APK verification**

In `runtime/tests/verify-apk-runtime.mjs`:

1. Require both managed assets and `lib/arm64-v8a/libbachata_host_loader.so`.
2. Parse the nested `assets/runtime/runtime.zip` and matching manifest.
3. Require `bin/fexcore-smoke` in both.
4. Require the nested runner to be ELF64 little-endian machine 183.
5. Require its size/hash to match the nested manifest declaration.

Run the verifier against any old Playstore APK. Expected RED because the old APK lacks the FEX runner.

- [ ] **Step 4: Make the mandatory build documentation truthful**

Insert this command after `runtime/scripts/build-box64-host.sh` and before packaging in both `AGENTS.md` and `documents/android-building.md`:

```bash
runtime/scripts/build-fexcore-smoke-aarch64.sh
```

Keep the Playstore build and managed-asset verification commands intact.

- [ ] **Step 5: Repackage and verify GREEN**

Run:

```bash
runtime/scripts/build-fexcore-smoke-aarch64.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
```

Expected: the runtime verifier identifies the x86-64 hello/shadPS4 files and the AArch64 FEXCore smoke file correctly.

- [ ] **Step 6: Commit packaging changes**

```bash
git add runtime/scripts/package-runtime.mjs runtime/tests/verify-runtime.mjs runtime/tests/verify-apk-runtime.mjs documents/android-building.md AGENTS.md
git commit -m "build: package FEXCore tablet probe"
```

---

### Task 4: Add an explicit native-glibc probe launch mode

**Files:**

- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncher.kt`
- Modify: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncherTest.kt`

**Interfaces:**

- New probe-only enum: `RuntimeProbeExecutionMode`.
- New native command: APK loader, `--library-path`, runtime host directory, AArch64 runner.
- Existing production `Box64Mode` and `RuntimeProcessLauncher` remain unchanged.

- [ ] **Step 1: Write failing native-mode JVM tests**

Change only the test helper and probe tests to use:

```kotlin
enum class RuntimeProbeExecutionMode {
    BOX64_APK_NATIVE,
    BOX64_HOST_GLIBC,
    HOST_GLIBC_NATIVE,
}
```

Add tests that require `HOST_GLIBC_NATIVE` to build exactly:

```kotlin
listOf(
    nativeLibraryDir.resolve("libbachata_host_loader.so").toRealPath().toString(),
    "--library-path",
    runtimeRoot.resolve("host").toRealPath().toString(),
    runtimeRoot.resolve("bin/fexcore-smoke").toRealPath().toString(),
)
```

Also assert:

- no command element contains `box64`, case-insensitively;
- the runner must be a contained regular file under `runtimeRoot`;
- the host directory must remain contained under `runtimeRoot`;
- the loader must resolve directly under `nativeLibraryDir`;
- inherited environment is cleared and only the existing allowlist is copied;
- `processBuilder` does not add execute permission to the installed runner in this mode;
- all existing Box64 probe command tests retain their exact commands.

Run:

```bash
cd android/BachataS4
./gradlew :core:runtime:test
```

Expected RED: `RuntimeProbeExecutionMode` and native mode do not exist.

- [ ] **Step 2: Separate probe execution mode from Box64 configuration**

In `RuntimeProbeLauncher.kt`:

1. Add `RuntimeProbeExecutionMode` as above.
2. Replace `RuntimeProbeRequest.box64Mode` with `executionMode`, defaulting to `BOX64_APK_NATIVE`.
3. Map the two old branches to `BOX64_APK_NATIVE` and `BOX64_HOST_GLIBC` without changing their command content.
4. Add `hostGlibcNativeCommand`, which validates the APK loader, contained host directory, and contained runner, then returns the exact four-element command above plus request arguments.
5. For `HOST_GLIBC_NATIVE`, return only the APK loader from `executablePaths`; do not chmod the extracted runner. The loader opens the runner as the glibc program rather than using `execve` on filesDir.
6. Keep the existing 64 KiB output cap, forced timeout, NUL rejection, path canonicalization, environment allowlist, and shell-free `ProcessBuilder` unchanged.
7. Leave the existing `Box64Mode` used by `RuntimeProcessLauncher` and `VulkanDriverConfiguration` untouched.

- [ ] **Step 3: Run focused and module tests GREEN**

```bash
cd android/BachataS4
./gradlew :core:runtime:test --tests '*RuntimeProbeLauncherTest*'
./gradlew :core:runtime:test
```

Expected: native-mode tests pass and all Box64 launcher tests remain green.

- [ ] **Step 4: Commit the probe launch mode**

```bash
git add android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncher.kt android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncherTest.kt
git commit -m "feat: launch native FEXCore probe"
```

---

### Task 5: Add an isolated Android instrumentation proof

**Files:**

- Create: `android/BachataS4/app/src/androidTest/kotlin/com/bachatas4/android/FexCoreSmokeDeviceTest.kt`
- Create: `runtime/tests/fexcore-device-test-source.test.mjs`
- Modify: `runtime/probes/fexcore-smoke.cpp`
- Modify: `runtime/tests/verify-fexcore-smoke-build.mjs`
- Modify: `android/BachataS4/app/build.gradle.kts` only if the existing Android-test dependencies do not already expose AndroidX Test/JUnit.

**Interfaces:**

- Test class: `com.bachatas4.android.FexCoreSmokeDeviceTest`.
- Log tag: `BachataFexSmoke`.
- Runtime root: unique directory under the target app's `cacheDir`; never `filesDir/games` or the production runtime root.

- [ ] **Step 1: Add the failing instrumentation test**

Implement a single `@Test fun executesPinnedFexCoreOnDevice()` that:

1. Gets `InstrumentationRegistry.getInstrumentation().targetContext`.
2. Decodes `assets/runtime/manifest.json` with `Json { ignoreUnknownKeys = true }`.
3. Creates a unique `cacheDir/fexcore-smoke-<monotonic-value>` root.
4. Installs `assets/runtime/runtime.zip` through `RuntimeInstaller(tempRoot).install(...)`.
5. Launches `<installed>/bin/fexcore-smoke` with `RuntimeProbeLauncher`, `RuntimeProbeExecutionMode.HOST_GLIBC_NATIVE`, `applicationInfo.nativeLibraryDir`, an empty environment, no arguments, and a 30-second timeout.
6. Requires exit code 0 and exact trimmed output equal to the acceptance success line.
7. Emits that line once through `Log.i("BachataFexSmoke", marker)` and `println(marker)`.
8. Deletes only the unique cache root in `finally`; a cleanup failure is reported but must never widen to its parent directory.

Create `runtime/tests/fexcore-device-test-source.test.mjs` to require the unique cache-child prefix and reject `pm clear`, `uninstall`, `filesDir.resolve("games")`, deletion of `cacheDir` itself, or any recursive deletion target not rooted at the unique `fexcore-smoke-*` child.

- [ ] **Step 2: Build and execute the bootstrap runner to verify RED on the tablet**

Build the still-bootstrap smoke runner, target APK, and test APK using the mandatory runtime sequence:

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
runtime/scripts/build-fexcore-smoke-aarch64.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs
cd android/BachataS4
./gradlew :core:runtime:test assemblePlaystoreDebug :app:assemblePlaystoreDebugAndroidTest
cd ../..
node runtime/tests/verify-apk-runtime.mjs android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
adb -s 7d6afed8 install -r android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
adb -s 7d6afed8 install -r -t android/BachataS4/app/build/outputs/apk/androidTest/playstore/debug/app-playstore-debug-androidTest.apk
adb -s 7d6afed8 shell am instrument -w -r -e class com.bachatas4.android.FexCoreSmokeDeviceTest com.bachatas4.android.test/androidx.test.runner.AndroidJUnitRunner
```

Expected RED: the device executes FEXCore and returns the three-check `FEXCORE_SMOKE_BOOTSTRAP_OK` line, while the new instrumentation assertion requires the final seven-check acceptance line. Installation uses replacement semantics and preserves app/game data. If the serial changed, substitute the current serial only on the command line.

- [ ] **Step 3: Implement the remaining four FEXCore checks**

First change `verify-fexcore-smoke-build.mjs` to require `threads`, `tls`, `callback`, and `invalidation` plus the final acceptance marker. Run it against the bootstrap artifact and verify RED.

Then extend `runtime/probes/fexcore-smoke.cpp`:

- `threads`: two native `std::thread`s each create, execute, and destroy their own FEX thread and return distinct sentinels; join both before context teardown.
- `tls`: each thread sets `CurrentFrame->State.fs_cached` to a distinct mapped TLS block; guest bytes `64 48 8b 04 25 00 00 00 00` load `fs:[0]` and must return that thread's sentinel.
- `callback`: after an initial `ExecuteThread` has initialized the callback-return pointer, set guest RDI/RSP and call `HandleCallback(thread, callbackRip)` on guest bytes `48 8d 47 05 c3`; require RAX to equal RDI plus 5 and RSP to return to its pre-callback value.
- `invalidation`: execute `mov rax,imm64; hlt`, change the immediate, call both `InvalidateCodeBuffersCodeRange(start,length)` and `InvalidateThreadCachedCodeRange(thread,start,length)`, reset RIP/RSP, execute again, and require the new immediate rather than stale translated code.

Replace the bootstrap line with the exact final acceptance line. Keep success output to that single line.

- [ ] **Step 4: Rebuild and execute GREEN**

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
runtime/scripts/build-fexcore-smoke-aarch64.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs
node --test runtime/tests/fexcore-device-test-source.test.mjs
cd android/BachataS4
./gradlew :core:runtime:test assemblePlaystoreDebug :app:assemblePlaystoreDebugAndroidTest
cd ../..
node runtime/tests/verify-apk-runtime.mjs android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
adb -s 7d6afed8 install -r android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
adb -s 7d6afed8 install -r -t android/BachataS4/app/build/outputs/apk/androidTest/playstore/debug/app-playstore-debug-androidTest.apk
adb -s 7d6afed8 shell am instrument -w -r -e class com.bachatas4.android.FexCoreSmokeDeviceTest com.bachatas4.android.test/androidx.test.runner.AndroidJUnitRunner
```

Expected GREEN: the targeted instrumentation class passes and prints the exact seven-check line. If it fails, keep the change uncommitted and diagnose the first named check; do not add fallback behavior.

- [ ] **Step 5: Commit the device proof and completed smoke checks**

```bash
git add android/BachataS4/app/src/androidTest/kotlin/com/bachatas4/android/FexCoreSmokeDeviceTest.kt runtime/tests/fexcore-device-test-source.test.mjs runtime/probes/fexcore-smoke.cpp runtime/tests/verify-fexcore-smoke-build.mjs android/BachataS4/app/build.gradle.kts
git commit -m "test: add FEXCore device proof"
```

If `app/build.gradle.kts` did not need modification, omit it from `git add`.

---

### Task 6: Capture validated, sanitized tablet evidence

**Files:**

- Create: `runtime/qualification/fex-phase0-schema.json`
- Create: `runtime/tests/fex-phase0-evidence.test.mjs`
- Create: `runtime/tests/verify-fex-phase0-evidence.mjs`
- Create: `runtime/qualification/run-fexcore-smoke.sh`
- Generate after a passing device run: `runtime/evidence/sm8650/fex-phase0.json`
- Generate after a passing device run: `runtime/evidence/sm8650/fex-phase0-instrumentation.txt`
- Generate after a passing device run: `runtime/evidence/sm8650/fex-phase0-logcat.txt`

**Interfaces:**

- Validator CLI: one evidence JSON path, exit 0 only for the approved device/result contract.
- Device runner CLI: one ADB serial argument; the serial is used for routing but never written to evidence.

- [ ] **Step 1: Add failing evidence-validator tests**

Use `node:test` to create temporary evidence fixtures and spawn the validator without a shell. Cover:

- one exact valid fixture;
- non-4096 page size;
- wrong FEX revision;
- wrong runner/APK SHA-256 shape;
- missing or non-`ok` check;
- nonzero exit code;
- marker mismatch;
- serial-like fields (`serial`, `adbSerial`);
- any string containing `/data/`, `/home/`, `C:\\`, or `com.bachatas4.android/files`;
- artifact hash mismatch between evidence and the runtime manifest.

Run:

```bash
node --test runtime/tests/fex-phase0-evidence.test.mjs
```

Expected RED: schema and validator do not exist.

- [ ] **Step 2: Define the evidence schema and validator**

The schema and validator must require exactly this logical shape:

```json
{
  "schemaVersion": 1,
  "device": {
    "soc": "SM8650",
    "abi": "arm64-v8a",
    "sdk": 36,
    "pageSize": 4096
  },
  "source": {
    "projectRevision": "<40 lowercase hex>",
    "fexRevision": "f2b679f6028ce1c38875233aecfcf5d3f8ebecec"
  },
  "artifact": {
    "apkSha256": "<64 lowercase hex>",
    "runnerSha256": "<64 lowercase hex>",
    "elfClass": "ELF64",
    "machine": "AArch64",
    "interpreter": "/lib/ld-linux-aarch64.so.1",
    "needed": ["libc.so.6", "libgcc_s.so.1", "libm.so.6", "libstdc++.so.6"]
  },
  "run": {
    "exitCode": 0,
    "durationMs": "<positive integer no greater than 30000>",
    "marker": "<exact success line>",
    "checks": {
      "gpr": "ok",
      "stack": "ok",
      "fp": "ok",
      "threads": "ok",
      "tls": "ok",
      "callback": "ok",
      "invalidation": "ok"
    }
  },
  "logs": {
    "instrumentationSha256": "<64 lowercase hex>",
    "logcatSha256": "<64 lowercase hex>"
  }
}
```

Reject unknown keys at every level. The validator must read the packaged manifest/runtime ZIP to independently verify `runnerSha256`, rather than trusting two fields from the same evidence file. It must also hash the two sanitized sibling log files and require exact equality with `logs.instrumentationSha256` and `logs.logcatSha256`.

Run the evidence unit tests. Expected GREEN.

- [ ] **Step 3: Implement the safe device runner**

Create executable `runtime/qualification/run-fexcore-smoke.sh` with `set -euo pipefail`. It must:

1. Require one ADB serial and confirm `adb -s <serial> get-state` returns `device`.
2. Verify the Playstore APK with `node runtime/tests/verify-apk-runtime.mjs` before any installation.
3. Verify device ABI `arm64-v8a`, SoC `SM8650`, SDK 36, and `getconf PAGESIZE` 4096.
4. Install the target APK with `adb -s <serial> install -r` and the Android `testOnly` APK with `adb -s <serial> install -r -t`; both preserve target-app data.
5. Record a device logcat start timestamp immediately before the test; do not clear any global logcat buffer or app data.
6. Run only `com.bachatas4.android.FexCoreSmokeDeviceTest` via `am instrument -w -r -e class` with the AndroidX runner.
7. Enforce a 45-second host-side timeout and require both instrumentation success and the exact smoke marker.
8. Capture raw instrumentation stdout and `adb logcat -d -T <start-timestamp> -s BachataFexSmoke:I '*:S'` under `runtime/build/fex-phase0-evidence/`; sanitize them into the two tracked `runtime/evidence/sm8650/fex-phase0-*.txt` files, rejecting rather than redacting any unexpected private path or serial-bearing line.
9. Invoke a Node helper/validator to compute hashes from those sanitized logs, read ELF metadata from the packaged runner, and atomically write `runtime/evidence/sm8650/fex-phase0.json` without the ADB serial or private paths.
10. Never invoke `adb uninstall`, `pm clear`, `rm` under app storage, `force-stop` with data deletion, or any production runtime/game cleanup.

The expected APK paths are:

```text
android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
android/BachataS4/app/build/outputs/apk/androidTest/playstore/debug/app-playstore-debug-androidTest.apk
```

- [ ] **Step 4: Run the full mandatory build before installation**

From the repository root:

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
runtime/scripts/build-fexcore-smoke-aarch64.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs
cd android/BachataS4
./gradlew clean test lintDebug assemblePlaystoreDebug :app:assemblePlaystoreDebugAndroidTest
cd ../..
node runtime/tests/verify-apk-runtime.mjs android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
unzip -l android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk | grep -E 'assets/runtime/(manifest\.json|runtime\.zip)'
```

Expected: all host/JVM/Gradle checks pass, and both managed-runtime assets are listed. Do not proceed to installation otherwise.

- [ ] **Step 5: Install with replacement and execute on the tablet**

Run:

```bash
runtime/qualification/run-fexcore-smoke.sh 7d6afed8
node runtime/tests/verify-fex-phase0-evidence.mjs runtime/evidence/sm8650/fex-phase0.json
```

Expected: exact success marker, exit 0, duration at most 30 seconds, validated sanitized evidence. If the ADB serial has changed, pass the current serial; do not edit it into any tracked file.

- [ ] **Step 6: Commit qualification tooling and passing evidence**

```bash
git add runtime/qualification/fex-phase0-schema.json runtime/tests/fex-phase0-evidence.test.mjs runtime/tests/verify-fex-phase0-evidence.mjs runtime/qualification/run-fexcore-smoke.sh runtime/evidence/sm8650/fex-phase0.json runtime/evidence/sm8650/fex-phase0-instrumentation.txt runtime/evidence/sm8650/fex-phase0-logcat.txt
git commit -m "test: qualify FEXCore on SM8650"
```

---

### Task 7: Run final Phase 0 regression and hand off Phase 1

**Files:**

- Verify only; no expected source changes.

- [ ] **Step 1: Run focused contracts**

```bash
node --test runtime/tests/shadps4-baseline.test.mjs runtime/tests/fex-phase0-evidence.test.mjs
node runtime/tests/verify-runtime.mjs --locks-only
node runtime/tests/verify-fexcore-smoke-build.mjs runtime/build/fexcore-smoke-stage/bin/fexcore-smoke
node runtime/tests/verify-fex-phase0-evidence.mjs runtime/evidence/sm8650/fex-phase0.json
cd android/BachataS4
./gradlew :core:runtime:test --tests '*RuntimeProbeLauncherTest*'
cd ../..
```

Expected: all focused contracts pass.

- [ ] **Step 2: Run every non-allowlisted shadPS4 CTest**

Configure the same default Debug/GCC test shape used to record the baseline, then let the checked-in runner exclude only the exact 44 accepted names:

```bash
cmake -S . -B runtime/build/shadps4-host-tests -G Ninja \
  -DCMAKE_BUILD_TYPE=Debug \
  -DENABLE_TESTS=ON
cmake --build runtime/build/shadps4-host-tests --parallel 8
node runtime/tests/run-ctest-with-baseline.mjs runtime/build/shadps4-host-tests
```

Expected: the catalog contains 426 unique tests, the temporary exact-name allowlist excludes 44, and all remaining 382 pass. Any new test, missing allowlisted test, duplicate, or non-allowlisted failure blocks Phase 0.

- [ ] **Step 3: Prove Box64 was not changed or used by the native proof**

Run:

```bash
git diff --exit-code dbe5165b -- runtime/scripts/build-box64-host.sh runtime/patches/box64-*.patch
test -s runtime/evidence/sm8650/fex-phase0-instrumentation.txt
test -s runtime/evidence/sm8650/fex-phase0-logcat.txt
test -s runtime/evidence/sm8650/fex-phase0.json
! rg -qi box64 runtime/evidence/sm8650/fex-phase0-instrumentation.txt runtime/evidence/sm8650/fex-phase0-logcat.txt runtime/evidence/sm8650/fex-phase0.json
node runtime/tests/verify-fex-phase0-evidence.mjs runtime/evidence/sm8650/fex-phase0.json
```

Expected: no Box64 source/build patch diff, no Box64 token in the native execution evidence, and independently recomputed sanitized-log hashes. The packaged Box64 artifacts themselves remain present for backward compatibility.

- [ ] **Step 4: Run the complete build/verification sequence once more if any source changed after Task 6**

Repeat Task 6 Step 4, then rerun the device proof. Do not reuse stale evidence after changing the smoke source, FEX patch, build flags, packager, loader, or Android test.

- [ ] **Step 5: Review the final diff and status**

```bash
git diff --check dbe5165b..HEAD
git status --short
git log --oneline dbe5165b..HEAD
```

Expected: no whitespace errors, clean worktree, and task-sized commits for baseline, build, packaging, Android launch, device test, and qualification.

- [ ] **Step 6: Record the Phase 0 gate result**

Phase 0 is complete only when all of these are true:

- pinned FEXCore-only AArch64 artifact passes ELF/dependency verification;
- managed runtime and Playstore APK contain the verified artifact and both required managed assets;
- the tablet reports the exact seven-check success marker on 4096-byte pages;
- evidence validates and contains no serial/private paths;
- existing Box64 launcher/runtime tests remain green;
- app and game data were preserved.

Then write the next separate implementation plan for shadPS4's `CpuBackend` boundary. Do not start Sonic integration in the same commit series.
