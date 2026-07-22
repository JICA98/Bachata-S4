import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const headerUrl = new URL("../../src/core/fex/fex_guest_engine.h", import.meta.url);
const sourceUrl = new URL("../../src/core/fex/fex_guest_engine.cpp", import.meta.url);
const signalsUrl = new URL("../../src/core/signals.cpp", import.meta.url);
const harnessUrl = new URL("../probes/fexcore-guest-harness.cpp", import.meta.url);

test("guest engine owns the FEX context and controlled bridge lifecycle", () => {
  const header = readFileSync(headerUrl, "utf8");
  const source = readFileSync(sourceUrl, "utf8");
  const signals = readFileSync(signalsUrl, "utf8");
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
  assert.match(source, /CONFIG_X86DISASSEMBLE/);
  assert.match(source, /getenv\("BACHATA_FEX_TRACE"\)/);
  assert.match(source, /traceEnabled \? "1" : "0"/);
  assert.match(source, /LogMan::Msg::InstallHandler/);
  assert.match(source, /BACHATA_FEX_BLOCK/);
  assert.match(source, /kFexBlockTraceLimit/);
  assert.match(source, /HandleUnalignedAccess/);
  assert.match(source, /IsAddressInCodeBuffer/);
  assert.match(source, /UnalignedHandlerType::HalfBarrier/);
  assert.match(source, /FexExecutionSignalScope/);
  assert.match(signals, /Core::Fex::HandleGuestSignal\(sig, info, raw_context\)/);
  assert.match(source, /\{0x4d, 0x8b, 0x2c, 0x24\}.*mov r13, \[r12\]/);
  assert.match(header, /bool Unaligned/);
  assert.doesNotMatch(source, /box64|wine|system\s*\(/i);

  assert.match(harness, /FEXCORE_GUEST_ENGINE_OK/);
  assert.match(harness, /gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok unaligned=ok invalidation=ok teardown=ok/);
  assert.doesNotMatch(harness, /box64|wine|system\s*\(/i);
});

test("guest bridge runs before the stack proof and preserves arithmetic state", () => {
  const source = readFileSync(sourceUrl, "utf8");
  const bridgeSyscall = source.indexOf("code.insert(code.end(), {0x0f, 0x05}); // syscall");
  const stackCall = source.indexOf("code.push_back(0xe8); // call stack_test");
  const gprCheckStart = source.indexOf("result.Gpr =");
  const gprCheckEnd = source.indexOf("const auto rflags", gprCheckStart);
  const gprCheck = source.slice(gprCheckStart, gprCheckEnd);

  assert.ok(bridgeSyscall >= 0, "guest bridge must use FEX's syscall dispatch");
  assert.ok(stackCall >= 0, "guest harness must exercise the guest stack");
  assert.ok(bridgeSyscall < stackCall, "syscall clobbers RCX and uses RDI before the stack proof establishes them");
  assert.match(source, /\{0x49, 0x89, 0xc9\}.*mov r9, rcx/);
  assert.match(gprCheck, /REG_R9.*kXorLeft \^ kXorRight/);
  assert.doesNotMatch(gprCheck, /REG_RCX/);
});

test("nested guest calls isolate bridge failures from the outer invocation", () => {
  const source = readFileSync(sourceUrl, "utf8");
  const callGuestStart = source.indexOf("EngineResult<GuestExecutionState> GuestEngine::CallGuest(");
  const callGuestEnd = source.indexOf("EngineResult<bool> GuestEngine::Invalidate(", callGuestStart);
  const callGuest = source.slice(callGuestStart, callGuestEnd);

  assert.ok(callGuestStart >= 0 && callGuestEnd > callGuestStart);
  assert.match(callGuest, /BridgeSyscallHandler::InvocationState invocation/);
  assert.match(callGuest, /BridgeSyscallHandler::InvocationScope invocationScope/);
  assert.match(callGuest, /FailureResult\(invocation\)/);
  assert.doesNotMatch(callGuest, /ActiveFailure\(\)/);
});
