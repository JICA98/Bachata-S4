import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import test from "node:test";

const root = resolve(new URL("../..", import.meta.url).pathname);
const validator = resolve(root, "runtime/tests/verify-fex-phase1-evidence.mjs");
const marker = "FEXCORE_GUEST_ENGINE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok invalidation=ok teardown=ok";
const sha256 = (text) => createHash("sha256").update(text).digest("hex");

test("FEX Phase 1 evidence accepts only sanitized complete harness contracts", () => {
  const directory = mkdtempSync(join(tmpdir(), "fex-phase1-evidence-"));
  try {
    const instrumentation = `${marker}\n`;
    const logcat = `BachataFexGuestHarness: ${marker}\n`;
    writeFileSync(join(directory, "fex-phase1-instrumentation.txt"), instrumentation);
    writeFileSync(join(directory, "fex-phase1-logcat.txt"), logcat);
    const evidence = {
      schemaVersion: 1,
      device: { family: "SM8650", abi: "arm64-v8a", sdk: 36, pageSize: 4096 },
      source: { projectRevision: "a".repeat(40), fexRevision: "f2b679f6028ce1c38875233aecfcf5d3f8ebecec" },
      artifact: {
        apkSha256: "b".repeat(64), runnerSha256: "c".repeat(64), elfClass: "ELF64", machine: "AArch64",
        interpreter: "/lib/ld-linux-aarch64.so.1", needed: ["libc.so.6", "libgcc_s.so.1", "libm.so.6", "libstdc++.so.6"],
      },
      run: {
        exitCode: 0, durationMs: 1, marker,
        contracts: { gpr: "ok", rflags: "ok", xmm: "ok", bridge: "ok", threads: "ok", tls: "ok", invalidation: "ok", teardown: "ok" },
      },
      logs: { instrumentationSha256: sha256(instrumentation), logcatSha256: sha256(logcat) },
    };
    const evidencePath = join(directory, "fex-phase1.json");
    writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
    const verified = spawnSync(process.execPath, [validator, evidencePath], { cwd: root, encoding: "utf8" });
    assert.equal(verified.status, 0, `${verified.stdout}\n${verified.stderr}`);

    evidence.run.marker = `${marker} 192.168.1.1`;
    writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
    const rejected = spawnSync(process.execPath, [validator, evidencePath], { cwd: root, encoding: "utf8" });
    assert.notEqual(rejected.status, 0);
    assert.match(readFileSync(evidencePath, "utf8"), /192\.168\.1\.1/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
