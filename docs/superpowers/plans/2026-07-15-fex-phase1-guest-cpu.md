# FEX Phase 1 Guest-CPU Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and tablet-qualify a native-AArch64, shadPS4-owned FEXCore guest-CPU harness that proves controlled x86-64 guest execution, a syscall/HLE bridge, independent guest threads, invalidation, and ordered teardown without invoking Box64.

**Architecture:** Keep the existing `fexcore-smoke` Phase 0 binary unchanged as a regression probe. Extend the temporary FEXCore-only CMake patch to build a second `fexcore-guest-harness` executable from a focused engine in `src/core/fex/` plus a synthetic x86-64 harness program. Package and launch that second AArch64 binary through the existing managed runtime and generic host-glibc probe mode; keep the real shadPS4 game path and all Box64 settings unchanged.

**Tech Stack:** C++20, pinned FEXCore `f2b679f6028ce1c38875233aecfcf5d3f8ebecec`, CMake/Ninja, Debian AArch64 glibc sysroot, Node.js tests, Kotlin/JUnit Android instrumentation, Gradle, ADB.

## Global Constraints

- Work only on `feature/fex-arm64-phase0-clean`; never modify the legacy worktree or `/home/jica/repo/GameNative`.
- Preserve the intended architecture: native-AArch64 shadPS4-owned host code with FEXCore as the x86-64 guest CPU. The Phase 1 harness must not invoke Box64.
- Preserve `host/fexcore-smoke` and its exact Phase 0 marker; add a second harness rather than replacing the regression proof.
- Do not introduce Wine, ARM64EC, `app_process`, an external x86 process launcher, or copied Wine-specific GameNative logic.
- Do not launch Sonic Mania or Bloodborne, switch defaults to FEX, remove Box64, or claim performance in this plan. Sonic is the next acceptance stage only after this plan passes.
- Do not hide failures with ignored return values or broad catch blocks. Every FEXCore, mmap/mprotect, build, launcher, and bridge failure reports an explicit stage and error code.
- Do not globally disable Android security or signal checks. Preserve FEXCore’s signal-delegator setup and chain only via its supported API.
- Never run `adb uninstall`, `pm clear`, delete runtime/app/game/save data, or use a destructive Git command. Tablet installation is replacement-only (`adb install -r` or `-r -t`).
- The expected post-build worktree dirt is the existing runtime-builder patch state in `externals/sdl3` and `runtime/sources/box64`; do not reset, stage, commit, or change either submodule pointer.
- Before APK assembly run `git submodule update --init --recursive --jobs 8`, `runtime/scripts/build-runtime-debian.sh`, `node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json`, and `node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs` from the repository root.
- Phase 1 ends only after the on-tablet marker and independent evidence validator pass. If the tablet is unavailable, record the blocker and leave the task in progress.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `src/core/fex/fex_guest_engine.h` | Public, host-owned FEX guest lifecycle and HLE-bridge contract. |
| `src/core/fex/fex_guest_engine.cpp` | FEX context/thread/state/mapping/invalidation implementation with RAII teardown. |
| `runtime/probes/fexcore-guest-harness.cpp` | Controlled x86-64 bytecode and exact Phase 1 contract marker. |
| `runtime/patches/fex-fexcore-only.patch` | Builds both Phase 0 smoke and Phase 1 multi-source harness without permanently changing pinned FEX. |
| `runtime/scripts/build-fexcore-smoke-aarch64.sh` | Builds, strips, verifies, and stages both AArch64 FEX binaries while reversing the temporary patch. |
| `runtime/scripts/stage-debian-runtime.mjs` | Copies `fexcore-guest-harness` into `host/` beside the existing smoke binary. |
| `runtime/tests/verify-fexcore-guest-harness-build.mjs` | ELF, dependency, marker, and forbidden-executable verifier for the new artifact. |
| `android/.../RuntimeProbeLauncher.kt` | Uses the validated request executable for generic host-glibc native probes. |
| `android/.../FexGuestHarnessDeviceTest.kt` | Installs a unique runtime child and runs the new host binary through the Android loader. |
| `runtime/qualification/*phase1*` | Replacement-install runner plus bounded, sanitized Phase 1 evidence schema and validator. |

### Task 1: Add the opt-in dual-harness FEX CMake contract

**Files:**
- Modify: `runtime/patches/fex-fexcore-only.patch`
- Create: `runtime/tests/fexcore-guest-harness-build-source.test.mjs`

**Interfaces:**
- Consumes: the existing `BUILD_FEXCORE_ONLY` patch and `FEXCORE_SMOKE_SOURCE` path.
- Produces: an optional `FEXCORE_GUEST_HARNESS_SOURCES` CMake contract while an unmodified Phase 0 build still creates only `fexcore-smoke`.

**Task-order correction (2026-07-15):** This task is limited to the opt-in
CMake contract and its source test. The guest engine, its sources, the
build-script argument, artifact verification, staging, and APK checks move to
Task 2 because those steps cannot be buildable before the guest sources exist.

- [ ] **Step 1: Write failing source/build-contract tests**

Create `runtime/tests/fexcore-guest-harness-build-source.test.mjs` that reads the patch and build/stage sources and requires all of the following literal contracts:

```js
assert.match(patch, /set\(FEXCORE_GUEST_HARNESS_SOURCES "" CACHE STRING/);
assert.match(patch, /if \(FEXCORE_GUEST_HARNESS_SOURCES\)/);
assert.match(patch, /add_executable\(fexcore-guest-harness \$\{FEXCORE_GUEST_HARNESS_SOURCES\}\)/);
assert.match(patch, /install\(TARGETS fexcore-smoke fexcore-guest-harness RUNTIME DESTINATION bin\)/);
```

Run:

```bash
node --test runtime/tests/fexcore-guest-harness-build-source.test.mjs
```

Expected: FAIL because the FEX-only patch has no opt-in guest harness target.

- [ ] **Step 2: Extend the temporary FEX CMake patch with an optional target**

In `runtime/patches/fex-fexcore-only.patch`, retain `FEXCORE_SMOKE_SOURCE` and add:

```cmake
set(FEXCORE_GUEST_HARNESS_SOURCES "" CACHE STRING "Semicolon-separated absolute guest harness sources")

add_executable(fexcore-smoke "${FEXCORE_SMOKE_SOURCE}")
# Apply the same C++20 feature, include directories, and FEXCore libraries to
# fexcore-smoke and, when present, fexcore-guest-harness.
if (FEXCORE_GUEST_HARNESS_SOURCES)
  foreach(source IN LISTS FEXCORE_GUEST_HARNESS_SOURCES)
    if (NOT IS_ABSOLUTE "${source}")
      message(FATAL_ERROR "fexcore-guest-harness source must be absolute: ${source}")
    endif()
  endforeach()
  add_executable(fexcore-guest-harness ${FEXCORE_GUEST_HARNESS_SOURCES})
  install(TARGETS fexcore-smoke fexcore-guest-harness RUNTIME DESTINATION bin)
else()
  install(TARGETS fexcore-smoke RUNTIME DESTINATION bin)
endif()
```

Use the same include directories, compile feature, and libraries for both
executables. Always create and install `fexcore-smoke`. Only when
`FEXCORE_GUEST_HARNESS_SOURCES` is non-empty, validate every source is
absolute, create `fexcore-guest-harness`, and install both targets in one
command. Otherwise install only `fexcore-smoke`. Keep the `return()` in the
FEX-only branch and do not alter upstream sources outside the temporary patch.

- [ ] **Step 3: [SUPERSEDED — execute in Task 2] Build and verify both artifacts**

In `runtime/scripts/build-fexcore-smoke-aarch64.sh`, define the exact sources:

```bash
GUEST_ENGINE_SOURCE="${PROJECT_ROOT}/src/core/fex/fex_guest_engine.cpp"
GUEST_HARNESS_SOURCE="${PROJECT_ROOT}/runtime/probes/fexcore-guest-harness.cpp"
```

Pass them as one CMake list and build both targets:

```bash
-DFEXCORE_GUEST_HARNESS_SOURCES="${GUEST_ENGINE_SOURCE};${GUEST_HARNESS_SOURCE}"
cmake --build "${BUILD_DIR}" --target fexcore-smoke fexcore-guest-harness --parallel
aarch64-linux-gnu-strip "${STAGE_DIR}/bin/fexcore-smoke" "${STAGE_DIR}/bin/fexcore-guest-harness"
node "${VERIFIER}" "${STAGE_DIR}/bin/fexcore-smoke"
node "${GUEST_HARNESS_VERIFIER}" "${STAGE_DIR}/bin/fexcore-guest-harness"
```

Keep the existing `trap cleanup_patch EXIT`; both success and failure must reverse `fex-fexcore-only.patch` before the script exits.

Create `verify-fexcore-guest-harness-build.mjs` by following the existing smoke verifier. It must require ELF64 little-endian AArch64, interpreter `/lib/ld-linux-aarch64.so.1`, exactly `libc.so.6`, `libgcc_s.so.1`, `libm.so.6`, and `libstdc++.so.6`, the pinned revision, and every marker token:

```text
gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok invalidation=ok teardown=ok
```

It must reject `box64`, `wine`, and any executable other than the inspected harness from the harness stage directory.

- [ ] **Step 4: [SUPERSEDED — execute in Task 2] Package and assert the new host artifact**

Add `fexcoreGuestHarnessStage` in `stage-debian-runtime.mjs`, require it to exist, copy it to `join(hostDir, "fexcore-guest-harness")`, and chmod it `0o755`. Add `host/fexcore-guest-harness` to `REQUIRED_RUNTIME_PATHS` in `verify-runtime.mjs`. Extend `verify-apk-runtime.mjs` so the nested runtime ZIP and manifest contain both FEX paths, each is ELF64/AArch64, and each manifest size/SHA-256 matches. Retain its rule that no FEXCore library is shipped through APK `jniLibs`.

- [ ] **Step 5: [SUPERSEDED — execute in Task 2] Run focused source checks and commit**

Run:

```bash
node --test runtime/tests/build-fexcore-smoke-cleanup-source.test.mjs runtime/tests/fexcore-guest-harness-build-source.test.mjs
git diff --check
```

Expected: both source tests pass and no whitespace errors occur.

Commit:

```bash
git add runtime/patches/fex-fexcore-only.patch runtime/scripts/build-fexcore-smoke-aarch64.sh runtime/scripts/stage-debian-runtime.mjs runtime/tests/verify-runtime.mjs runtime/tests/verify-apk-runtime.mjs runtime/tests/verify-fexcore-guest-harness-build.mjs runtime/tests/fexcore-guest-harness-build-source.test.mjs
git commit -m 'build(fex): add guest harness target'
```

### Task 2: Implement the FEX guest engine and controlled bridge harness

**Files:**
- Create: `src/core/fex/fex_guest_engine.h`
- Create: `src/core/fex/fex_guest_engine.cpp`
- Create: `runtime/probes/fexcore-guest-harness.cpp`
- Create: `runtime/tests/fex-guest-engine-source.test.mjs`
- Modify: `runtime/scripts/build-fexcore-smoke-aarch64.sh`
- Modify: `runtime/scripts/stage-debian-runtime.mjs`
- Modify: `runtime/tests/verify-runtime.mjs`
- Modify: `runtime/tests/verify-apk-runtime.mjs`
- Modify: `runtime/tests/fexcore-guest-harness-build-source.test.mjs`
- Create: `runtime/tests/verify-fexcore-guest-harness-build.mjs`

**Interfaces:**
- Consumes: the dual-target build interface from Task 1 and the pinned public FEXCore APIs already proven by `runtime/probes/fexcore-smoke.cpp`.
- Produces: `Core::Fex::GuestEngine`, `Core::Fex::GuestBridge`, and exact harness marker `FEXCORE_GUEST_ENGINE_OK`.

- [ ] **Step 1: Write the failing engine contract test**

Create `runtime/tests/fex-guest-engine-source.test.mjs` to require the lifecycle API and all critical FEX operations:

```js
assert.match(header, /class GuestBridge/);
assert.match(header, /class GuestEngine/);
assert.match(header, /using EngineResult = std::variant<T, EngineFailure>/);
assert.match(header, /EngineResult<std::unique_ptr<GuestEngine>> Create\(GuestBridge& bridge\)/);
assert.match(source, /CreateNewContext/);
assert.match(source, /CreateThread/);
assert.match(source, /SetXMMRegistersFromState/);
assert.match(source, /HandleSyscall/);
assert.match(source, /InvalidateCodeBuffersCodeRange/);
assert.match(source, /InvalidateThreadCachedCodeRange/);
assert.match(source, /DestroyThread/);
assert.doesNotMatch(source, /box64|wine|system\s*\(/i);
```

Run:

```bash
node --test runtime/tests/fex-guest-engine-source.test.mjs
```

Expected: FAIL because the engine sources do not exist.

- [ ] **Step 2: Define the host-owned public contract**

Create `src/core/fex/fex_guest_engine.h` with these exact host-facing types. Do not expose `FEXCore::Context::Context` outside this implementation boundary:

```cpp
#include <cstdint>
#include <memory>
#include <variant>

namespace Core::Fex {
enum class EngineStage { Config, Context, Mapping, Thread, Execute, Bridge, Invalidate, Teardown };
struct EngineFailure { EngineStage stage; int error_code; };
struct BridgeRequest { uint64_t operation; uint64_t argument; };
template <typename T> using EngineResult = std::variant<T, EngineFailure>;
class GuestBridge {
public:
    virtual ~GuestBridge() = default;
    virtual EngineResult<uint64_t> Dispatch(const BridgeRequest& request) = 0;
};
struct HarnessResult { bool gpr; bool rflags; bool xmm; bool bridge; bool threads; bool tls; bool invalidation; bool teardown; };
class GuestEngine final {
public:
    static EngineResult<std::unique_ptr<GuestEngine>> Create(GuestBridge& bridge);
    GuestEngine(GuestEngine&&) noexcept;
    GuestEngine& operator=(GuestEngine&&) noexcept;
    ~GuestEngine();
    EngineResult<HarnessResult> RunControlledHarness();
private:
    explicit GuestEngine(GuestBridge& bridge);
};
}
```

Delete copy construction/assignment. `EngineFailure.error_code` contains the failing syscall/errno/FEX error value; no operation returns an unchecked status.

- [ ] **Step 3: Implement lifecycle, syscall bridge, and cleanup**

Implement `GuestEngine` by moving the reusable mechanisms from the Phase 0 smoke into private RAII types: configuration scope, code/stack/TLS mappings, guest segment state, call/return stack, FEX signal delegator, syscall handler, thread scope, and code invalidation lock. Keep Phase 0 source intact.

The private syscall handler must derive from `FEXCore::HLE::SyscallHandler`, set `OSABI` to `OS_GENERIC`, obtain the operation from guest RAX and argument from guest RDI, call `GuestBridge::Dispatch`, write the returned value to guest RAX, and propagate an `EngineFailure` variant at `EngineStage::Bridge`. Its executable-range query must cover the controlled code mapping only; do not use an unbounded range.

`RunControlledHarness()` must map separate code, stack, and TLS pages; configure 64-bit FEX; create a context/thread; initialize GDT/CS, RIP/RSP, RFLAGS, FS/TLS, GPR, and XMM state; execute bytecode containing arithmetic, floating-point arithmetic, stack call/return, and one `syscall`; verify bridge result/state; run two host threads with independent TLS sentinels; patch and invalidate an instruction under `GetCodeInvalidationMutex()`; then destroy thread/context/mappings in reverse dependency order.

- [ ] **Step 4: Implement the deterministic executable and marker**

Create `runtime/probes/fexcore-guest-harness.cpp`. Its bridge accepts only:

```cpp
constexpr uint64_t kHarnessBridgeOperation = 0xB4C4'F001ULL;
constexpr uint64_t kHarnessBridgeArgument = 0x1020'3040'5060'7080ULL;
constexpr uint64_t kHarnessBridgeResult = 0x8877'6655'4433'2211ULL;
```

Any other operation returns `EngineFailure {EngineStage::Bridge, ENOSYS}`. On success, print exactly:

```text
FEXCORE_GUEST_ENGINE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok invalidation=ok teardown=ok
```

On failure, print only `FEXCORE_GUEST_ENGINE_FAIL stage=<stage> error=<integer>` to stderr and return nonzero. Do not print paths, serials, mapped addresses, or guest bytes.

- [ ] **Step 5: Wire, stage, and verify the new harness artifact**

Extend `build-fexcore-smoke-aarch64.sh` with the absolute engine and harness
source paths, pass them through `FEXCORE_GUEST_HARNESS_SOURCES`, build both
targets, strip both staged executables, and run a new guest-harness verifier.
Preserve `trap cleanup_patch EXIT` so the FEX source checkout is clean on both
success and failure. Stage the executable as `host/fexcore-guest-harness`, add
it to the runtime and APK-manifest verifiers, and retain the prohibition on
shipping FEX through Android `jniLibs`. Extend the Task 1 source-contract test
with these build, staging, and verification assertions only after the engine
sources exist.

- [ ] **Step 6: Build locally and run tests**

Run:

```bash
node --test runtime/tests/fex-guest-engine-source.test.mjs runtime/tests/fexcore-guest-harness-build-source.test.mjs
runtime/scripts/build-fexcore-smoke-aarch64.sh
node runtime/tests/verify-fexcore-smoke-build.mjs runtime/build/fexcore-smoke-stage/bin/fexcore-smoke
node runtime/tests/verify-fexcore-guest-harness-build.mjs runtime/build/fexcore-smoke-stage/bin/fexcore-guest-harness
```

Expected: source tests pass; both staged files are AArch64/glibc artifacts; the build script exits with the FEX source checkout clean because its temporary patch trap ran.

Commit:

```bash
git add src/core/fex/fex_guest_engine.h src/core/fex/fex_guest_engine.cpp runtime/probes/fexcore-guest-harness.cpp runtime/tests/fex-guest-engine-source.test.mjs
git commit -m 'feat(fex): add guest cpu engine harness'
```

### Task 3: Make the native probe launcher generic and add Android harness coverage

**Files:**
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncher.kt`
- Modify: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncherTest.kt`
- Create: `android/BachataS4/app/src/androidTest/kotlin/com/bachatas4/android/FexGuestHarnessDeviceTest.kt`

**Interfaces:**
- Consumes: `host/fexcore-guest-harness` from Task 1 and `RuntimeProbeRequest.executable` containment checks.
- Produces: generic `HOST_GLIBC_NATIVE` command construction and an exact instrumentation marker check.

- [ ] **Step 1: Write the failing generic-native-launcher unit test**

Add a test that creates `runtimeRoot/host/fexcore-guest-harness`, passes it as `RuntimeProbeRequest.executable` with `HOST_GLIBC_NATIVE`, and expects:

```kotlin
listOf(
    request.nativeLibraryDir.resolve("libbachata_host_loader.so").toRealPath().toString(),
    "--library-path",
    request.runtimeRoot.resolve("host").toRealPath().toString(),
    request.runtimeRoot.resolve("host/fexcore-guest-harness").toRealPath().toString(),
)
```

Run:

```bash
cd android/BachataS4
ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :core:runtime:testDebugUnitTest --tests '*RuntimeProbeLauncherTest*'
```

Expected: FAIL because `HOST_GLIBC_NATIVE` currently substitutes the fixed `fexcore-smoke` runner.

- [ ] **Step 2: Use the validated request executable**

Change the private method signature and call site to:

```kotlin
RuntimeProbeExecutionMode.HOST_GLIBC_NATIVE -> hostGlibcNativeCommand(request, runtimeRoot, executable)
private fun hostGlibcNativeCommand(request: RuntimeProbeRequest, runtimeRoot: Path, executable: Path): List<String>
```

Inside that method, replace the fixed `hostDirectory.resolve(FEXCORE_SMOKE_RUNNER)` lookup with the already real-pathed `executable`, call `validateContainedFile(runtimeRoot, executable, "Host glibc native probe")`, and return it after the loader and library-path arguments. In `executablePaths`, include both the APK-owned host loader and the requested probe for `HOST_GLIBC_NATIVE`. Remove the now-unused smoke-runner constant; do not weaken any parent/containment/readability checks.

- [ ] **Step 3: Add the physical device test**

Create `FexGuestHarnessDeviceTest.kt` using the same `RuntimeInstaller`, unique cache-child cleanup guard, `HOST_GLIBC_NATIVE`, 30-second timeout, and `GLIBC_TUNABLES=glibc.pthread.rseq=0` as `FexCoreSmokeDeviceTest`. Set `executable = installed.resolve("host/fexcore-guest-harness")`, require exit code zero, and require the exact Task 2 marker. Tag logs `BachataFexGuestHarness`; cleanup may delete only the unique `fexcore-guest-harness-<nanoTime>` child.

- [ ] **Step 4: Run JVM/instrumentation compilation checks and commit**

Run:

```bash
cd android/BachataS4
ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :core:runtime:testDebugUnitTest --tests '*RuntimeProbeLauncherTest*' :app:compilePlaystoreDebugAndroidTestKotlin
cd ../..
```

Expected: all launcher unit tests pass and the new Android test source compiles.

Commit:

```bash
git add android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncher.kt android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncherTest.kt android/BachataS4/app/src/androidTest/kotlin/com/bachatas4/android/FexGuestHarnessDeviceTest.kt
git commit -m 'test(android): launch native fex harness'
```

### Task 4: Create Phase 1 replacement-install evidence tooling

**Files:**
- Create: `runtime/qualification/run-fexcore-guest-harness.sh`
- Create: `runtime/qualification/fex-phase1-schema.json`
- Create: `runtime/qualification/create-fex-phase1-evidence.mjs`
- Create: `runtime/tests/verify-fex-phase1-evidence.mjs`
- Create: `runtime/tests/fex-phase1-evidence.test.mjs`
- Create: `runtime/tests/run-fexcore-guest-harness-source.test.mjs`

**Interfaces:**
- Consumes: `FexGuestHarnessDeviceTest`, Playstore APK, exact marker, and the existing Phase 0 privacy principles.
- Produces: independently validated `runtime/evidence/sm8650/fex-phase1.json`, instrumentation capture, and logcat capture.

- [ ] **Step 1: Write failing runner/evidence tests**

Create a source test requiring `set -euo pipefail`, `adb install -r -t`, `FexGuestHarnessDeviceTest`, the exact new marker, nanosecond timing divided by `1000000`, minimum duration `1`, hash capture, and explicit absence of `adb uninstall`, `pm clear`, `force-stop`, raw serial output, and private paths. Create an evidence test that accepts only `device.family = "SM8650"`, `source.fexRevision` equal to the pinned revision, `run.durationMs` in `1..30000`, `run.contracts` with every Task 2 key equal to `"ok"`, matching artifact/log hashes, and sanitized text.

Run:

```bash
node --test runtime/tests/run-fexcore-guest-harness-source.test.mjs runtime/tests/fex-phase1-evidence.test.mjs
```

Expected: FAIL because the Phase 1 runner/schema/verifier do not exist.

- [ ] **Step 2: Implement bounded sanitized evidence**

Mirror the Phase 0 evidence data flow, but use the marker:

```text
FEXCORE_GUEST_ENGINE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok invalidation=ok teardown=ok
```

The runner accepts the explicit ADB serial argument only for command targeting; it must not write it into evidence. It clears logcat before the run, replacement-installs the Playstore APK, runs only `com.bachatas4.android.FexGuestHarnessDeviceTest`, captures only `BachataFexGuestHarness` log lines, normalizes CRLF, and passes the sanitized run directory to the evidence creator. The creator records the clean branch revision, APK SHA-256, runner SHA-256, host ELF properties, elapsed duration, marker contracts, and SHA-256s of the sanitized instrumentation/log captures. The verifier rejects Box64/Wine tokens, serials, IP addresses, `/data/`, `/home/`, `C:\\`, and carriage returns.

- [ ] **Step 3: Run source/evidence tests and commit**

Run:

```bash
node --test runtime/tests/run-fexcore-guest-harness-source.test.mjs runtime/tests/fex-phase1-evidence.test.mjs
git diff --check
```

Expected: both tests pass and no whitespace errors occur.

Commit:

```bash
git add runtime/qualification/run-fexcore-guest-harness.sh runtime/qualification/fex-phase1-schema.json runtime/qualification/create-fex-phase1-evidence.mjs runtime/tests/verify-fex-phase1-evidence.mjs runtime/tests/fex-phase1-evidence.test.mjs runtime/tests/run-fexcore-guest-harness-source.test.mjs
git commit -m 'test(fex): add phase1 harness evidence'
```

### Task 5: Rebuild, tablet-qualify, and commit the Phase 1 proof

**Files:**
- Modify only after all gates pass: `runtime/evidence/sm8650/fex-phase1.json`
- Modify only after all gates pass: `runtime/evidence/sm8650/fex-phase1-instrumentation.txt`
- Modify only after all gates pass: `runtime/evidence/sm8650/fex-phase1-logcat.txt`

**Interfaces:**
- Consumes: all Tasks 1–4 and the physical SM8650 device.
- Produces: the pushed Phase 1 evidence commit; only then may Sonic Mania planning/bring-up start.

- [ ] **Step 1: Run focused source and Android contracts**

Run:

```bash
node --test runtime/tests/build-fexcore-smoke-cleanup-source.test.mjs runtime/tests/fexcore-guest-harness-build-source.test.mjs runtime/tests/fex-guest-engine-source.test.mjs runtime/tests/run-fexcore-guest-harness-source.test.mjs runtime/tests/fex-phase1-evidence.test.mjs
cd android/BachataS4
ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :core:runtime:testDebugUnitTest --tests '*RuntimeProbeLauncherTest*'
cd ../..
```

Expected: every Node subtest and launcher unit test passes.

- [ ] **Step 2: Rebuild the managed runtime and Playstore APK**

Run from the repository root:

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-runtime-debian.sh
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs
node runtime/tests/verify-fexcore-smoke-build.mjs runtime/build/fexcore-smoke-stage/bin/fexcore-smoke
node runtime/tests/verify-fexcore-guest-harness-build.mjs runtime/build/fexcore-smoke-stage/bin/fexcore-guest-harness
cd android/BachataS4
ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew clean test lintDebug assemblePlaystoreDebug assemblePlaystoreDebugAndroidTest
cd ../..
node runtime/tests/verify-apk-runtime.mjs android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
unzip -l android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk | grep -E 'assets/runtime/(manifest\.json|runtime\.zip)'
```

Expected: the runtime and APK verifiers require both FEX binaries; the APK lists the managed manifest/runtime zip; no native FEX `jniLibs` or Turnip payload is present.

- [ ] **Step 3: Run the physical Phase 1 tablet gate**

Run only when device `7d6afed8` is connected and visible:

```bash
adb devices
ADB=/home/jica/scripts/adb runtime/qualification/run-fexcore-guest-harness.sh 7d6afed8
node runtime/tests/verify-fex-phase1-evidence.mjs runtime/evidence/sm8650/fex-phase1.json
```

Expected: replacement installation succeeds; the new Android test emits the exact marker; independent evidence validation reports SM8650 and a duration in `1..30000`. If the device is unavailable or any command fails, stop here, write the first failure and exact status, and do not commit evidence or start Sonic work.

- [ ] **Step 4: Run final regression and clean-range audits**

Run:

```bash
cmake -S . -B runtime/build/shadps4-host-tests -G Ninja -DCMAKE_BUILD_TYPE=Debug -DENABLE_TESTS=ON
cmake --build runtime/build/shadps4-host-tests --parallel 8
node runtime/tests/run-ctest-with-baseline.mjs runtime/build/shadps4-host-tests
git diff --exit-code 5623902477eacbe36b30266e673652c14de32fa8 -- runtime/scripts/build-box64-host.sh runtime/patches/box64-*.patch
git diff --check 5623902477eacbe36b30266e673652c14de32fa8..HEAD
! rg -qi 'box64|wine|arm64ec' runtime/evidence/sm8650/fex-phase1.json runtime/evidence/sm8650/fex-phase1-instrumentation.txt runtime/evidence/sm8650/fex-phase1-logcat.txt
git status --short
```

Expected: CTest reports 382/382 executed tests; the Box64 source-range audit and whitespace audit pass; Phase 1 evidence has no forbidden token. Before staging, parent-repository changes are only the three Phase 1 evidence files plus the known build-induced dirt in `externals/sdl3` and `runtime/sources/box64`.

- [ ] **Step 5: Commit evidence and hand off Sonic planning**

Run:

```bash
git add runtime/evidence/sm8650/fex-phase1.json runtime/evidence/sm8650/fex-phase1-instrumentation.txt runtime/evidence/sm8650/fex-phase1-logcat.txt
git diff --cached --check
node runtime/tests/verify-fex-phase1-evidence.mjs runtime/evidence/sm8650/fex-phase1.json
git commit -m 'test(fex): qualify phase1 guest engine on sm8650'
git push
```

Expected: only the three evidence files are committed and pushed. The next work item is a separate Sonic Mania compatibility-slice design; do not change the global default to FEX until that slice reaches first-level completion with controller, audio, rendering, and ten-minute stability.

## Plan Self-Review

- Spec coverage: Task 1 preserves/builds both artifacts; Task 2 implements the lifecycle, CPU-state, bridge, thread, invalidation, and teardown contracts; Task 3 makes Android native launch generic; Task 4 creates bounded tablet evidence; Task 5 requires build, packaging, physical proof, regression, and source-range gates.
- Scope: no task launches a game, changes the game default, removes Box64, or claims performance; Sonic and Bloodborne remain follow-on work.
- Type consistency: `GuestBridge`, `GuestEngine`, `EngineFailure`, `HarnessResult`, `FexGuestHarnessDeviceTest`, and the `FEXCORE_GUEST_ENGINE_OK` marker are defined once and reused unchanged.
- Placeholder scan: no unresolved placeholders, implicit error handling, or unnamed files remain.
