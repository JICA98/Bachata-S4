import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => readFileSync(resolve(root, relative), "utf8");

test("FEX guest backend executes caller-owned mappings with explicit thread lifetime", () => {
  const engineHeader = read("src/core/fex/fex_guest_engine.h");
  const engineSource = read("src/core/fex/fex_guest_engine.cpp");
  const backendHeader = read("src/core/guest_cpu/fex_guest_cpu.h");
  const backendSource = read("src/core/guest_cpu/fex_guest_cpu.cpp");
  const genericHeader = read("src/core/guest_cpu/guest_cpu.h");
  const harness = read("runtime/probes/fexcore-guest-harness.cpp");
  const buildScript = read("runtime/scripts/build-fexcore-smoke-aarch64.sh");
  const harnessVerifier = read("runtime/tests/verify-fexcore-guest-harness-build.mjs");
  const runtimeVerifier = read("runtime/tests/verify-runtime.mjs");
  const deviceTest = read("android/BachataS4/app/src/androidTest/kotlin/com/bachatas4/android/FexGuestHarnessDeviceTest.kt");

  assert.match(genericHeader, /bool Writable/);
  assert.match(genericHeader, /MappedRanges/);
  assert.match(engineHeader, /class Thread;/);
  assert.match(engineHeader, /CreateThread\(const GuestExecutionRequest& request\)/);
  assert.match(engineHeader, /Run\(Thread& thread\)/);
  assert.match(engineHeader, /Invalidate\(Thread& thread, std::uintptr_t begin, std::size_t size\)/);
  assert.match(engineHeader, /DestroyThread\(Thread\*& thread\)/);
  assert.match(engineSource, /ValidateRequest\(const GuestExecutionRequest& request\)/);
  assert.match(engineSource, /request\.Rip/);
  assert.match(engineSource, /request\.Rsp/);
  assert.match(engineSource, /std::this_thread::get_id\(\)/);
  assert.match(engineSource, /Context->InvalidateCodeBuffersCodeRange/);
  assert.match(engineSource, /Context->InvalidateThreadCachedCodeRange/);
  assert.doesNotMatch(engineSource, /CreateThread\(reinterpret_cast<uint64_t>\(Code->Get\(\)\)/);
  const createEngine = engineSource.slice(
    engineSource.indexOf("GuestEngine::Create"),
    engineSource.indexOf("GuestEngine::RunControlledHarness"),
  );
  assert.doesNotMatch(createEngine, /BuildGuestCode\(/);
  assert.match(createEngine, /FunctionReturn/);
  assert.match(createEngine, /CallbackReturn/);
  assert.match(engineSource, /GuestCode guest = BuildGuestCode\(\)/);

  assert.match(backendHeader, /class FexGuestCpuBackend/);
  assert.match(backendHeader, /CreateThread\(const GuestExecutionRequest& request\)/);
  assert.match(backendHeader, /DestroyThread\(std::unique_ptr<Thread>& thread\)/);
  assert.match(backendSource, /engine->CreateThread/);
  assert.match(backendSource, /engine->DestroyThread/);

  assert.match(harness, /mmap\(/);
  assert.match(harness, /MappedRanges/);
  assert.match(harness, /FexGuestCpuBackend/);
  assert.match(harness, /caller_mapping=ok/);
  assert.match(buildScript, /fex_guest_cpu\.cpp/);
  assert.match(harnessVerifier, /caller_mapping=ok/);
  assert.match(runtimeVerifier, /caller_mapping=ok/);
  assert.match(deviceTest, /FEXCORE_GUEST_CPU_OK caller_mapping=ok thread_lifetime=ok invalidation=ok/);
});

test("FEX guest backend keeps production wiring and per-thread execution state", () => {
  const cmake = read("CMakeLists.txt");
  const engineSource = read("src/core/fex/fex_guest_engine.cpp");
  const backendHeader = read("src/core/guest_cpu/fex_guest_cpu.h");
  const backendSource = read("src/core/guest_cpu/fex_guest_cpu.cpp");
  const armBuild = read("runtime/scripts/build-shadps4-arm64.sh");
  const harness = read("runtime/probes/fexcore-guest-harness.cpp");

  assert.match(cmake, /ENABLE_FEX_GUEST_CPU/);
  assert.match(cmake, /src\/core\/fex\/fex_guest_engine\.cpp/);
  assert.match(cmake, /src\/core\/guest_cpu\/fex_guest_cpu\.cpp/);
  assert.match(cmake, /FEXCORE_GUEST_CPU_LIBRARIES/);
  assert.match(armBuild, /ENABLE_FEX_GUEST_CPU=ON/);
  const coreSources = cmake.slice(cmake.indexOf("set(CORE"), cmake.indexOf("set(SHADER_RECOMPILER"));
  assert.match(coreSources, /src\/core\/fex\/fex_guest_engine\.cpp/);
  assert.match(coreSources, /src\/core\/guest_cpu\/fex_guest_cpu\.cpp/);

  assert.match(engineSource, /RangesOverlap/);
  assert.match(engineSource, /ValidateHostMapping/);
  assert.match(engineSource, /thread_local InvocationState\* ActiveInvocation/);
  assert.match(engineSource, /RegisterThread\(FEXCore::Core::InternalThreadState\* thread/);
  assert.match(engineSource, /QueryGuestExecutableRange\(FEXCore::Core::InternalThreadState\* thread,[\s\S]*uint64_t address\)/);
  assert.doesNotMatch(engineSource, /return \{0, std::numeric_limits<uint64_t>::max\(\), true\}/);
  assert.match(engineSource, /std::mutex ThreadsMutex/);
  assert.match(engineSource, /FexConfigLease/);
  assert.match(engineSource, /result\.FirstRip = thread\.FirstRip/);
  assert.match(engineSource, /result\.LastRip = thread\.LastRip/);

  assert.match(backendHeader, /~Thread\(\)/);
  assert.match(backendSource, /DestroyThreadOrAbort/);
  assert.match(harness, /thread_isolation=ok/);
  assert.match(harness, /overlap_rejected=ok/);
});
