import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const generator = resolve(root, "runtime/qualification/create-fex-phase0-evidence.mjs");
const validator = resolve(root, "runtime/tests/verify-fex-phase0-evidence.mjs");
const marker = "FEXCORE_SMOKE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok stack=ok fp=ok threads=ok tls=ok callback=ok invalidation=ok";

test("FEX Phase 0 evidence normalizes Android CRLF capture", () => {
  const directory = mkdtempSync(join(tmpdir(), "fex-phase0-create-"));
  try {
    const instrumentation = join(directory, "instrumentation.raw");
    const logcat = join(directory, "logcat.raw");
    const output = join(directory, "fex-phase0.json");
    writeFileSync(instrumentation, `${marker}\r\n`);
    writeFileSync(logcat, `BachataFexSmoke: ${marker}\r\n`);

    const created = spawnSync(process.execPath, [generator, "1", instrumentation, logcat, output], { cwd: root, encoding: "utf8" });
    assert.equal(created.status, 0, `${created.stdout}\n${created.stderr}`);
    assert.doesNotMatch(readFileSync(join(directory, "fex-phase0-instrumentation.txt"), "utf8"), /\r/);
    assert.doesNotMatch(readFileSync(join(directory, "fex-phase0-logcat.txt"), "utf8"), /\r/);

    const verified = spawnSync(process.execPath, [validator, output], { cwd: root, encoding: "utf8" });
    assert.equal(verified.status, 0, `${verified.stdout}\n${verified.stderr}`);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
