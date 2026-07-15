#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const apk = resolve(root, "android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk");
const runnerPath = "host/fexcore-guest-harness";
const needed = ["libc.so.6", "libgcc_s.so.1", "libm.so.6", "libstdc++.so.6"];
const contracts = ["gpr", "rflags", "xmm", "bridge", "threads", "tls", "invalidation", "teardown"];
const sha256Pattern = /^[0-9a-f]{64}$/;
const forbiddenText = /box64|wine|\/data\/|\/home\/|C:\\|\b(?:\d{1,3}\.){3}\d{1,3}\b|\r|serial/i;

export const marker = "FEXCORE_GUEST_ENGINE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok invalidation=ok teardown=ok";

function fail(message) {
  throw new Error(`FEX Phase 1 evidence verification failed: ${message}`);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function exactKeys(value, expected, label) {
  const actual = Object.keys(value ?? {}).sort();
  const sortedExpected = [...expected].sort();
  if (JSON.stringify(actual) !== JSON.stringify(sortedExpected)) fail(`${label} has unknown or missing keys`);
}

function scanPrivacy(value, label = "evidence") {
  if (typeof value === "string") {
    if (forbiddenText.test(value)) fail(`${label} contains forbidden text`);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((entry, index) => scanPrivacy(entry, `${label}[${index}]`));
    return;
  }
  if (value !== null && typeof value === "object") {
    for (const [key, entry] of Object.entries(value)) {
      if (key.toLowerCase().includes("serial")) fail(`${label}.${key} is serial-bearing`);
      scanPrivacy(entry, `${label}.${key}`);
    }
  }
}

function parseElf(runner) {
  const temporary = mkdtempSync(join(tmpdir(), "fex-phase1-elf-"));
  try {
    const path = join(temporary, "fexcore-guest-harness");
    writeFileSync(path, runner);
    const header = execFileSync("readelf", ["-h", path], { encoding: "utf8" });
    const programHeaders = execFileSync("readelf", ["-l", path], { encoding: "utf8" });
    const dynamic = execFileSync("readelf", ["-d", path], { encoding: "utf8" });
    const actualNeeded = [...dynamic.matchAll(/\(NEEDED\).*Shared library: \[([^\]]+)\]/g)].map((match) => match[1]).sort();
    if (!header.includes("Class:                             ELF64") || !header.includes("Machine:                           AArch64")) {
      fail("guest harness is not ELF64 AArch64");
    }
    if (JSON.stringify(actualNeeded) !== JSON.stringify(needed)) fail("guest harness dependencies differ");
    const interpreter = programHeaders.match(/Requesting program interpreter: ([^\]]+)/)?.[1] ?? "";
    if (interpreter !== "/lib/ld-linux-aarch64.so.1") fail("guest harness interpreter differs");
    return { elfClass: "ELF64", machine: "AArch64", interpreter, needed: actualNeeded };
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
}

export function packagedArtifact() {
  const temporary = mkdtempSync(join(tmpdir(), "fex-phase1-apk-"));
  try {
    const manifest = JSON.parse(execFileSync("unzip", ["-p", apk, "assets/runtime/manifest.json"], { encoding: "utf8" }));
    const runnerManifest = manifest.files?.find((entry) => entry.path === runnerPath);
    if (!runnerManifest || !sha256Pattern.test(runnerManifest.sha256 ?? "")) fail("APK manifest has no valid guest harness digest");
    const runtimeZip = join(temporary, "runtime.zip");
    writeFileSync(runtimeZip, execFileSync("unzip", ["-p", apk, "assets/runtime/runtime.zip"], { encoding: "buffer", maxBuffer: 256 * 1024 * 1024 }));
    const runner = execFileSync("unzip", ["-p", runtimeZip, runnerPath], { encoding: "buffer", maxBuffer: 64 * 1024 * 1024 });
    const runnerSha256 = sha256(runner);
    if (runnerSha256 !== runnerManifest.sha256) fail("APK manifest guest harness digest differs from runtime ZIP");
    return { apkSha256: sha256(readFileSync(apk)), runnerSha256, ...parseElf(runner) };
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
}

export function validateEvidence(evidencePath) {
  const rawEvidence = readFileSync(evidencePath, "utf8");
  if (rawEvidence.includes("\r")) fail("evidence contains carriage returns");
  const evidence = JSON.parse(rawEvidence);
  scanPrivacy(evidence);
  exactKeys(evidence, ["schemaVersion", "device", "source", "artifact", "run", "logs"], "evidence");
  if (evidence.schemaVersion !== 1) fail("schemaVersion must be 1");
  exactKeys(evidence.device, ["family", "abi", "sdk", "pageSize"], "device");
  if (evidence.device.family !== "SM8650" || evidence.device.abi !== "arm64-v8a" || evidence.device.sdk !== 36 || evidence.device.pageSize !== 4096) {
    fail("device must be SM8650 arm64-v8a API 36 with 4096-byte pages");
  }
  exactKeys(evidence.source, ["projectRevision", "fexRevision"], "source");
  if (!/^[0-9a-f]{40}$/.test(evidence.source.projectRevision) || evidence.source.fexRevision !== "f2b679f6028ce1c38875233aecfcf5d3f8ebecec") {
    fail("source revision is invalid");
  }
  exactKeys(evidence.artifact, ["apkSha256", "runnerSha256", "elfClass", "machine", "interpreter", "needed"], "artifact");
  if (!sha256Pattern.test(evidence.artifact.apkSha256) || !sha256Pattern.test(evidence.artifact.runnerSha256) ||
      evidence.artifact.elfClass !== "ELF64" || evidence.artifact.machine !== "AArch64" ||
      evidence.artifact.interpreter !== "/lib/ld-linux-aarch64.so.1" || JSON.stringify(evidence.artifact.needed) !== JSON.stringify(needed)) {
    fail("artifact is invalid");
  }
  exactKeys(evidence.run, ["exitCode", "durationMs", "marker", "contracts"], "run");
  if (evidence.run.exitCode !== 0 || !Number.isInteger(evidence.run.durationMs) || evidence.run.durationMs < 1 || evidence.run.durationMs > 30000 || evidence.run.marker !== marker) {
    fail("run result is invalid");
  }
  exactKeys(evidence.run.contracts, contracts, "run.contracts");
  for (const contract of contracts) if (evidence.run.contracts[contract] !== "ok") fail(`run contract is not ok: ${contract}`);
  exactKeys(evidence.logs, ["instrumentationSha256", "logcatSha256"], "logs");
  if (!sha256Pattern.test(evidence.logs.instrumentationSha256) || !sha256Pattern.test(evidence.logs.logcatSha256)) fail("log hash is invalid");
  const directory = dirname(resolve(evidencePath));
  const base = resolve(evidencePath).replace(/\.json$/, "");
  const instrumentation = readFileSync(`${base}-instrumentation.txt`, "utf8");
  const logcat = readFileSync(`${base}-logcat.txt`, "utf8");
  scanPrivacy(instrumentation, "instrumentation");
  scanPrivacy(logcat, "logcat");
  if (sha256(instrumentation) !== evidence.logs.instrumentationSha256 || sha256(logcat) !== evidence.logs.logcatSha256) {
    fail("sanitized log hashes differ");
  }
  if (!instrumentation.includes(marker) && !logcat.includes(marker)) fail("exact guest harness marker was not captured");
  return evidence;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 3) fail("usage: verify-fex-phase1-evidence.mjs <evidence-path>");
  const evidence = validateEvidence(resolve(process.argv[2]));
  console.log(`FEX Phase 1 evidence verified: ${evidence.device.family} durationMs=${evidence.run.durationMs}`);
}
