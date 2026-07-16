import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => readFileSync(resolve(root, relative), "utf8");

test("FEX Sonic guest backend has a value-only, process-free boundary", () => {
  const header = read("src/core/guest_cpu/guest_cpu.h");
  const source = read("src/core/guest_cpu/guest_cpu.cpp");
  const cmake = read("CMakeLists.txt");

  assert.match(header, /class GuestCpuBackend/);
  assert.match(header, /struct GuestExecutionRequest/);
  assert.match(header, /GuestExecutionResult/);
  assert.match(header, /virtual GuestExecutionResult Run\(/);
  assert.match(header, /class NativeGuestCpuBackend/);
  assert.match(header, /class UnavailableGuestCpuBackend/);
  assert.match(source, /ENOTSUP/);
  assert.match(cmake, /src\/core\/guest_cpu\/guest_cpu\.cpp/);

  for (const text of [header, source]) {
    assert.doesNotMatch(text, /box64|wine|arm64ec/i);
    assert.doesNotMatch(text, /\b(?:system|exec(?:v|ve|vp|vpe)?)\s*\(/);
  }
});
