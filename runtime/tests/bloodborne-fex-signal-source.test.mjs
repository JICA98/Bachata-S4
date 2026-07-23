import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const engineUrl = new URL("../../src/core/fex/fex_guest_engine.cpp", import.meta.url);
const signalsUrl = new URL("../../src/core/signals.cpp", import.meta.url);

test("GPU-tracking SIGSEGV bypasses FEX diagnostics and reaches normal dispatch", () => {
  const engine = readFileSync(engineUrl, "utf8");
  const signals = readFileSync(signalsUrl, "utf8");

  assert.match(signals, /case SIGBUS:\s*\n\s*case SIGSEGV:/);
  assert.match(
    signals,
    /sig == SIGBUS && ::Core::Fex::HandleGuestSignal\(sig, info, raw_context\)/,
  );

  assert.match(engine, /signal != SIGBUS/);
  assert.match(engine, /HandleUnalignedAccess/);
  assert.doesNotMatch(engine, /BACHATA_FEX_GUEST_FAULT/);
  assert.doesNotMatch(engine, /RestoreRIPFromHostPC/);
});
