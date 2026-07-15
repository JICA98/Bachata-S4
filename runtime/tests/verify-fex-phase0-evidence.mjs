#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const schema = JSON.parse(readFileSync(resolve(root, "runtime/qualification/fex-phase0-schema.json"), "utf8"));
const apk = resolve(root, "android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk");
const marker = "FEXCORE_SMOKE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok stack=ok fp=ok threads=ok tls=ok callback=ok invalidation=ok";
const needed = ["libc.so.6", "libgcc_s.so.1", "libm.so.6", "libstdc++.so.6"];
const sha256Pattern = /^[0-9a-f]{64}$/;
const revisionPattern = /^[0-9a-f]{40}$/;
const forbiddenText = /\/data\/|\/home\/|C:\\|com\.bachatas4\.android\/files/;

function fail(message) {
  throw new Error(`FEX Phase 0 evidence invalid: ${message}`);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function exactKeys(value, keys, label) {
  if (value === null || Array.isArray(value) || typeof value !== "object") fail(`${label} must be an object`);
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) {
    fail(`${label} has unknown or missing keys`);
  }
}

function stringValue(value, label) {
  if (typeof value !== "string") fail(`${label} must be a string`);
}

function scanPrivacy(value, label = "evidence") {
  if (typeof value === "string") {
    if (forbiddenText.test(value)) fail(`${label} contains a private path`);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((entry, index) => scanPrivacy(entry, `${label}[${index}]`));
    return;
  }
  if (value !== null && typeof value === "object") {
    for (const [key, entry] of Object.entries(value)) {
      if (key.toLowerCase().includes("serial")) fail(`${label}.${key} contains a serial-bearing field`);
      scanPrivacy(entry, `${label}.${key}`);
    }
  }
}

function parseElf(runner) {
  const temporary = mkdtempSync(join(tmpdir(), "fex-phase0-elf-"));
  try {
    const path = join(temporary, "fexcore-smoke");
    writeFileSync(path, runner);
    const header = execFileSync("readelf", ["-h", path], { encoding: "utf8" });
    const programHeaders = execFileSync("readelf", ["-l", path], { encoding: "utf8" });
    const dynamic = execFileSync("readelf", ["-d", path], { encoding: "utf8" });
    const actualNeeded = [...dynamic.matchAll(/Shared library: \[([^\]]+)\]/g)].map((match) => match[1]).sort();
    return {
      elfClass: /Class:\s+ELF64/.test(header) ? "ELF64" : "",
      machine: /Machine:\s+AArch64/.test(header) ? "AArch64" : "",
      interpreter: programHeaders.match(/Requesting program interpreter: ([^\]]+)/)?.[1] ?? "",
      needed: actualNeeded,
    };
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
}

function packagedArtifact() {
  const temporary = mkdtempSync(join(tmpdir(), "fex-phase0-apk-"));
  try {
    const manifest = JSON.parse(execFileSync("unzip", ["-p", apk, "assets/runtime/manifest.json"], { encoding: "utf8" }));
    const runnerManifest = manifest.files?.find((entry) => entry.path === "host/fexcore-smoke");
    if (!runnerManifest || !sha256Pattern.test(runnerManifest.sha256 ?? "")) fail("APK runtime manifest has no valid FEXCore runner digest");
    const runtimeZip = join(temporary, "runtime.zip");
    writeFileSync(runtimeZip, execFileSync("unzip", ["-p", apk, "assets/runtime/runtime.zip"], {
      encoding: "buffer",
      maxBuffer: 256 * 1024 * 1024,
    }));
    const runner = execFileSync("unzip", ["-p", runtimeZip, "host/fexcore-smoke"], {
      encoding: "buffer",
      maxBuffer: 64 * 1024 * 1024,
    });
    const runnerSha256 = sha256(runner);
    if (runnerSha256 !== runnerManifest.sha256) fail("APK runtime manifest runner digest does not match runtime ZIP");
    return { apkSha256: sha256(readFileSync(apk)), runnerSha256, ...parseElf(runner) };
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
}

function validateEvidence(evidencePath) {
  const evidence = JSON.parse(readFileSync(evidencePath, "utf8"));
  scanPrivacy(evidence);
  exactKeys(evidence, ["schemaVersion", "device", "source", "artifact", "run", "logs"], "evidence");
  if (evidence.schemaVersion !== schema.properties.schemaVersion.const) fail("schemaVersion must be 1");

  exactKeys(evidence.device, ["soc", "abi", "sdk", "pageSize"], "device");
  if (evidence.device.soc !== "SM8650" || evidence.device.abi !== "arm64-v8a" || evidence.device.sdk !== 36 || evidence.device.pageSize !== 4096) {
    fail("device must be SM8650 arm64-v8a API 36 with 4096-byte pages");
  }

  exactKeys(evidence.source, ["projectRevision", "fexRevision"], "source");
  if (!revisionPattern.test(evidence.source.projectRevision ?? "")) fail("source.projectRevision must be a lowercase 40-hex revision");
  if (evidence.source.fexRevision !== "f2b679f6028ce1c38875233aecfcf5d3f8ebecec") fail("source.fexRevision is not pinned");

  exactKeys(evidence.artifact, ["apkSha256", "runnerSha256", "elfClass", "machine", "interpreter", "needed"], "artifact");
  for (const key of ["apkSha256", "runnerSha256"]) if (!sha256Pattern.test(evidence.artifact[key] ?? "")) fail(`artifact.${key} must be lowercase SHA-256`);
  if (!Array.isArray(evidence.artifact.needed)) fail("artifact.needed must be an array");

  exactKeys(evidence.run, ["exitCode", "durationMs", "marker", "checks"], "run");
  if (evidence.run.exitCode !== 0) fail("run.exitCode must be zero");
  if (!Number.isInteger(evidence.run.durationMs) || evidence.run.durationMs < 1 || evidence.run.durationMs > 30000) fail("run.durationMs must be 1..30000");
  if (evidence.run.marker !== marker) fail("run.marker does not match the exact smoke marker");
  exactKeys(evidence.run.checks, ["gpr", "stack", "fp", "threads", "tls", "callback", "invalidation"], "run.checks");
  for (const [name, result] of Object.entries(evidence.run.checks)) if (result !== "ok") fail(`run.checks.${name} must be ok`);

  exactKeys(evidence.logs, ["instrumentationSha256", "logcatSha256"], "logs");
  for (const key of ["instrumentationSha256", "logcatSha256"]) if (!sha256Pattern.test(evidence.logs[key] ?? "")) fail(`logs.${key} must be lowercase SHA-256`);
  const base = basename(evidencePath, ".json");
  const evidenceDirectory = dirname(evidencePath);
  const instrumentation = readFileSync(join(evidenceDirectory, `${base}-instrumentation.txt`));
  const logcat = readFileSync(join(evidenceDirectory, `${base}-logcat.txt`));
  scanPrivacy(instrumentation.toString("utf8"), "instrumentation log");
  scanPrivacy(logcat.toString("utf8"), "logcat");
  if (sha256(instrumentation) !== evidence.logs.instrumentationSha256) fail("instrumentation log SHA-256 mismatch");
  if (sha256(logcat) !== evidence.logs.logcatSha256) fail("logcat SHA-256 mismatch");

  const actual = packagedArtifact();
  for (const key of ["apkSha256", "runnerSha256", "elfClass", "machine", "interpreter"]) {
    if (evidence.artifact[key] !== actual[key]) fail(`artifact.${key} does not match the packaged APK`);
  }
  if (JSON.stringify(evidence.artifact.needed) !== JSON.stringify(needed) || JSON.stringify(actual.needed) !== JSON.stringify(needed)) {
    fail("artifact.needed does not match the approved glibc dependency set");
  }
  return evidence;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 3) {
    console.error(`usage: ${process.argv[1]} runtime/evidence/sm8650/fex-phase0.json`);
    process.exit(64);
  }

  const evidence = validateEvidence(resolve(process.argv[2]));
  console.log(`FEX Phase 0 evidence valid: ${evidence.device.soc} durationMs=${evidence.run.durationMs}`);
}

export { marker, packagedArtifact, validateEvidence };
