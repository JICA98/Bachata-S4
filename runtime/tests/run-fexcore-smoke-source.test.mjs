import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const runnerUrl = new URL("../qualification/run-fexcore-smoke.sh", import.meta.url);

test("FEX Phase 0 runner preserves app data and records bounded sanitized proof", () => {
  const source = readFileSync(runnerUrl, "utf8");

  assert.match(source, /set -euo pipefail/);
  assert.match(source, /get-state/);
  assert.match(source, /verify-apk-runtime\.mjs/);
  assert.match(source, /install -r/);
  assert.match(source, /install -r -t/);
  assert.match(source, /getconf PAGESIZE/);
  assert.match(source, /timeout 45s/);
  assert.match(source, /FexCoreSmokeDeviceTest/);
  assert.match(source, /shell "date '\+%m-%d %H:%M:%S\.000'"/);
  assert.match(source, /logcat -d -T/);
  assert.match(source, /create-fex-phase0-evidence\.mjs/);
  assert.doesNotMatch(source, /\badb uninstall\b|\bpm clear\b|\bforce-stop\b/);
});
