import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const validator = resolve(root, "runtime/tests/verify-fex-phase0-evidence.mjs");
const apk = resolve(root, "android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk");
const marker = "FEXCORE_SMOKE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok stack=ok fp=ok threads=ok tls=ok callback=ok invalidation=ok";

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function readRunnerHash() {
  const manifest = JSON.parse(execFileSync("unzip", ["-p", apk, "assets/runtime/manifest.json"], { encoding: "utf8" }));
  const runner = manifest.files.find((entry) => entry.path === "host/fexcore-smoke");
  assert.ok(runner, "APK manifest must declare the FEXCore smoke runner");
  return runner.sha256;
}

function validEvidence(instrumentation, logcat) {
  return {
    schemaVersion: 1,
    device: { soc: "SM8650", abi: "arm64-v8a", sdk: 36, pageSize: 4096 },
    source: {
      projectRevision: execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim(),
      fexRevision: "f2b679f6028ce1c38875233aecfcf5d3f8ebecec",
    },
    artifact: {
      apkSha256: sha256(readFileSync(apk)),
      runnerSha256: readRunnerHash(),
      elfClass: "ELF64",
      machine: "AArch64",
      interpreter: "/lib/ld-linux-aarch64.so.1",
      needed: ["libc.so.6", "libgcc_s.so.1", "libm.so.6", "libstdc++.so.6"],
    },
    run: {
      exitCode: 0,
      durationMs: 617,
      marker,
      checks: { gpr: "ok", stack: "ok", fp: "ok", threads: "ok", tls: "ok", callback: "ok", invalidation: "ok" },
    },
    logs: { instrumentationSha256: sha256(instrumentation), logcatSha256: sha256(logcat) },
  };
}

function writeFixture(directory, mutate = (evidence) => evidence) {
  const evidencePath = join(directory, "fex-phase0.json");
  const instrumentationPath = join(directory, "fex-phase0-instrumentation.txt");
  const logcatPath = join(directory, "fex-phase0-logcat.txt");
  const instrumentation = `${marker}\n`;
  const logcat = `BachataFexSmoke: ${marker}\n`;
  const evidence = mutate(validEvidence(instrumentation, logcat));
  writeFileSync(instrumentationPath, instrumentation);
  writeFileSync(logcatPath, logcat);
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function validate(evidencePath) {
  return spawnSync(process.execPath, [validator, evidencePath], { cwd: root, encoding: "utf8" });
}

function reject(mutator) {
  const directory = mkdtempSync(join(tmpdir(), "fex-phase0-evidence-"));
  try {
    const result = validate(writeFixture(directory, mutator));
    assert.notEqual(result.status, 0, `${result.stdout}\n${result.stderr}`);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test("FEX Phase 0 evidence accepts the exact packaged-device contract", () => {
  const directory = mkdtempSync(join(tmpdir(), "fex-phase0-evidence-"));
  try {
    const result = validate(writeFixture(directory));
    assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("FEX Phase 0 evidence rejects invalid device, source, run, privacy, and artifact data", () => {
  reject((evidence) => ({ ...evidence, device: { ...evidence.device, pageSize: 16384 } }));
  reject((evidence) => ({ ...evidence, source: { ...evidence.source, fexRevision: "0".repeat(40) } }));
  reject((evidence) => ({ ...evidence, artifact: { ...evidence.artifact, apkSha256: "invalid" } }));
  reject((evidence) => ({ ...evidence, artifact: { ...evidence.artifact, runnerSha256: "invalid" } }));
  reject((evidence) => ({ ...evidence, artifact: { ...evidence.artifact, runnerSha256: "0".repeat(64) } }));
  reject((evidence) => ({ ...evidence, run: { ...evidence.run, checks: { ...evidence.run.checks, tls: undefined } } }));
  reject((evidence) => ({ ...evidence, run: { ...evidence.run, checks: { ...evidence.run.checks, callback: "failed" } } }));
  reject((evidence) => ({ ...evidence, run: { ...evidence.run, exitCode: 1 } }));
  reject((evidence) => ({ ...evidence, run: { ...evidence.run, marker: "not the FEX marker" } }));
  reject((evidence) => ({ ...evidence, serial: "7d6afed8" }));
  reject((evidence) => ({ ...evidence, source: { ...evidence.source, projectRevision: "/home/private" } }));
  reject((evidence) => ({ ...evidence, source: { ...evidence.source, projectRevision: "C:\\\\private" } }));
  reject((evidence) => ({ ...evidence, source: { ...evidence.source, projectRevision: "com.bachatas4.android/files" } }));
  reject((evidence) => ({ ...evidence, extra: true }));
});

test("FEX Phase 0 evidence rejects private paths in sanitized logs", () => {
  const directory = mkdtempSync(join(tmpdir(), "fex-phase0-evidence-"));
  try {
    const evidencePath = writeFixture(directory);
    writeFileSync(join(directory, `${basename(evidencePath, ".json")}-logcat.txt`), "path=/data/user/0/private\n");
    const result = validate(evidencePath);
    assert.notEqual(result.status, 0, `${result.stdout}\n${result.stderr}`);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
