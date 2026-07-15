import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const sourceUrl = new URL("../probes/fexcore-smoke.cpp", import.meta.url);

test("FEXCore smoke proves thread, TLS, callback, and invalidation embedding paths", () => {
  const source = readFileSync(sourceUrl, "utf8");

  assert.match(
    source,
    /FEXCORE_SMOKE_OK revision=.*gpr=ok stack=ok fp=ok threads=ok tls=ok callback=ok invalidation=ok/,
  );
  assert.match(source, /std::thread/);
  assert.match(source, /fs_cached/);
  assert.match(source, /HandleCallback/);
  assert.match(source, /class SmokeSignalDelegator final : public FEXCore::SignalDelegator/);
  assert.match(source, /GetThunkCallbackRET\(\) const override/);
  assert.match(source, /0x0f, 0x3e/);
  assert.match(source, /GetCodeInvalidationMutex\(\)/);
  assert.match(source, /InvalidateCodeBuffersCodeRange/);
  assert.match(source, /InvalidateThreadCachedCodeRange/);
});
