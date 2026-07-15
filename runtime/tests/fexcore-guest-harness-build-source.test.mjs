import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const patchUrl = new URL("../patches/fex-fexcore-only.patch", import.meta.url);

test("FEX-only patch keeps the guest harness opt-in", () => {
  const patch = readFileSync(patchUrl, "utf8");

  assert.match(patch, /set\(FEXCORE_GUEST_HARNESS_SOURCES "" CACHE STRING/);
  assert.match(patch, /if \(FEXCORE_GUEST_HARNESS_SOURCES\)/);
  assert.doesNotMatch(patch, /if \(NOT FEXCORE_GUEST_HARNESS_SOURCES\)/);
  assert.match(
    patch,
    /add_executable\(fexcore-guest-harness \$\{FEXCORE_GUEST_HARNESS_SOURCES\}\)/,
  );
  assert.match(
    patch,
    /install\(TARGETS fexcore-smoke fexcore-guest-harness RUNTIME DESTINATION bin\)/,
  );
  assert.match(
    patch,
    /\+  else\(\)\n\+    install\(TARGETS fexcore-smoke RUNTIME DESTINATION bin\)/,
  );
});
