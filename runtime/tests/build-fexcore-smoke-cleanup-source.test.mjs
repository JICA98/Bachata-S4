import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const buildScriptUrl = new URL("../scripts/build-fexcore-smoke-aarch64.sh", import.meta.url);

test("FEXCore smoke build reverses its temporary upstream CMake patch on exit", () => {
  const source = readFileSync(buildScriptUrl, "utf8");

  assert.match(source, /PATCH_APPLIED=0/);
  assert.match(source, /trap cleanup_patch EXIT/);
  assert.match(source, /git -C "\$\{FEX_SOURCE\}" apply -R "\$\{PATCH_PATH\}"/);
  assert.match(source, /PATCH_APPLIED=1/);
});
