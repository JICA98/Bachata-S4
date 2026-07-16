import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const source = readFileSync(resolve(root, "runtime/scripts/build-shadps4-arm64.sh"), "utf8");

test("ARM64 shadPS4 build is explicit and does not replace the x86 runtime", () => {
  assert.match(source, /^#!\/usr\/bin\/env bash$/m);
  assert.match(source, /set -euo pipefail/);
  assert.match(source, /CMAKE_C_COMPILER_TARGET=aarch64-linux-gnu/);
  assert.match(source, /CMAKE_CXX_COMPILER_TARGET=aarch64-linux-gnu/);
  assert.match(source, /shadps4-arm64/);
  assert.match(source, /CMAKE_C_COMPILER/);
  assert.match(source, /CMAKE_CXX_COMPILER/);
  assert.match(source, /readelf -h/);
  assert.match(source, /AArch64/);
  assert.doesNotMatch(source, /box64|wine|arm64ec/i);
});
