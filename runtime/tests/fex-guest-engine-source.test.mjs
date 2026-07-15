import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const headerUrl = new URL("../../src/core/fex/fex_guest_engine.h", import.meta.url);
const sourceUrl = new URL("../../src/core/fex/fex_guest_engine.cpp", import.meta.url);
const harnessUrl = new URL("../probes/fexcore-guest-harness.cpp", import.meta.url);

test("guest engine owns the FEX context and controlled bridge lifecycle", () => {
  const header = readFileSync(headerUrl, "utf8");
  const source = readFileSync(sourceUrl, "utf8");
  const harness = readFileSync(harnessUrl, "utf8");

  assert.match(header, /namespace Core::Fex/);
  assert.match(header, /class GuestBridge/);
  assert.match(header, /class GuestEngine/);
  assert.match(header, /enum class EngineStage/);
  assert.match(header, /using EngineResult = std::variant<T, EngineFailure>/);
  assert.match(header, /EngineResult<std::unique_ptr<GuestEngine>> Create\(GuestBridge& bridge\)/);
  assert.match(header, /EngineResult<GuestRunResult> RunControlledHarness\(\)/);

  for (const operation of [
    "InitializeConfigs",
    "FEXCore::Config::Initialize",
    "CreateNewContext",
    "SetSignalDelegator",
    "SetSyscallHandler",
    "InitCore",
    "CreateThread",
    "ExecuteThread",
    "GetCodeInvalidationMutex",
    "InvalidateCodeBuffersCodeRange",
    "InvalidateThreadCachedCodeRange",
    "DestroyThread",
  ]) {
    assert.match(source, new RegExp(operation.replaceAll("::", "::")));
  }
  assert.match(source, /mmap\(/);
  assert.match(source, /mprotect\(/);
  assert.doesNotMatch(source, /box64|wine|system\s*\(/i);

  assert.match(harness, /FEXCORE_GUEST_ENGINE_OK/);
  assert.match(harness, /gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok invalidation=ok teardown=ok/);
  assert.doesNotMatch(harness, /box64|wine|system\s*\(/i);
});
