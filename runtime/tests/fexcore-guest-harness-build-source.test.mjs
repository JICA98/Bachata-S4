import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const patchUrl = new URL("../patches/fex-fexcore-only.patch", import.meta.url);
const buildScriptUrl = new URL("../scripts/build-fexcore-smoke-aarch64.sh", import.meta.url);
const stageScriptUrl = new URL("../scripts/stage-debian-runtime.mjs", import.meta.url);
const runtimeVerifierUrl = new URL("verify-runtime.mjs", import.meta.url);
const apkVerifierUrl = new URL("verify-apk-runtime.mjs", import.meta.url);
const harnessVerifierUrl = new URL("verify-fexcore-guest-harness-build.mjs", import.meta.url);

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

  const build = readFileSync(buildScriptUrl, "utf8");
  const stage = readFileSync(stageScriptUrl, "utf8");
  const runtimeVerifier = readFileSync(runtimeVerifierUrl, "utf8");
  const apkVerifier = readFileSync(apkVerifierUrl, "utf8");
  const harnessVerifier = readFileSync(harnessVerifierUrl, "utf8");
  assert.match(build, /GUEST_ENGINE_SOURCE=/);
  assert.match(build, /FEXCORE_GUEST_HARNESS_SOURCES=/);
  assert.match(build, /--target fexcore-smoke fexcore-guest-harness/);
  assert.match(build, /verify-fexcore-guest-harness-build\.mjs/);
  assert.match(stage, /hostDir, "fexcore-guest-harness"/);
  assert.match(runtimeVerifier, /host\/fexcore-guest-harness/);
  assert.match(apkVerifier, /host\/fexcore-guest-harness/);
  assert.match(harnessVerifier, /FEXCORE_GUEST_ENGINE_OK/);
});
