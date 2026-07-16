import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const runnerUrl = new URL("../qualification/run-fexcore-guest-harness.sh", import.meta.url);
const generatorUrl = new URL("../qualification/create-fex-phase1-evidence.mjs", import.meta.url);

test("FEX Phase 1 runner replacement-installs and captures bounded sanitized proof", () => {
  const source = readFileSync(runnerUrl, "utf8");
  const generator = readFileSync(generatorUrl, "utf8");

  assert.match(source, /set -euo pipefail/);
  assert.match(source, /install -r -t/);
  assert.match(source, /FexGuestHarnessDeviceTest/);
  assert.match(source, /FEXCORE_GUEST_ENGINE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok invalidation=ok teardown=ok/);
  assert.match(source, /started_ns=\$\(date \+%s%N\)/);
  assert.match(source, /finished_ns=\$\(date \+%s%N\)/);
  assert.match(source, /\(finished_ns - started_ns\) \/ 1000000/);
  assert.match(source, /duration_ms < 1/);
  assert.match(source, /grep -Fq 'FAILURES!!!' "\$run_directory\/instrumentation\.raw"/);
  assert.match(source, /instrumentation reported test failure/);
  assert.match(generator, /sha256/);
  assert.match(source, /BachataFexGuestHarness/);
  assert.doesNotMatch(source, /adb uninstall|pm clear|force-stop/i);
  assert.doesNotMatch(source, /echo[^\n]*\$serial|serial.*>>|serial.*evidence/i);
  assert.doesNotMatch(source, /\/data\/|\/home\/|C:\\\\/);
});
