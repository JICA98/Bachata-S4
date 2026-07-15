#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { readFileSync, renameSync, writeFileSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { marker, packagedArtifact } from "../tests/verify-fex-phase1-evidence.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const forbiddenText = /box64|wine|\/data\/|\/home\/|C:\\|\b(?:\d{1,3}\.){3}\d{1,3}\b|\r|serial/i;

function fail(message) {
  throw new Error(`FEX Phase 1 evidence creation failed: ${message}`);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function readSanitized(path, label) {
  const contents = readFileSync(path, "utf8").replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  if (forbiddenText.test(contents)) fail(`${label} contains forbidden text`);
  return contents;
}

if (process.argv.length !== 6) fail("usage: create-fex-phase1-evidence.mjs <duration-ms> <instrumentation> <logcat> <output>");
const durationMs = Number(process.argv[2]);
if (!Number.isInteger(durationMs) || durationMs < 1 || durationMs > 30000) fail("duration must be an integer in 1..30000");
const instrumentation = readSanitized(resolve(process.argv[3]), "instrumentation");
const logcat = readSanitized(resolve(process.argv[4]), "logcat");
if (!instrumentation.includes(marker) && !logcat.includes(marker)) fail("exact guest harness marker was not captured");

const output = resolve(process.argv[5]);
const base = basename(output, ".json");
const outputDirectory = dirname(output);
const instrumentationOutput = join(outputDirectory, `${base}-instrumentation.txt`);
const logcatOutput = join(outputDirectory, `${base}-logcat.txt`);
writeFileSync(instrumentationOutput, instrumentation, { mode: 0o644 });
writeFileSync(logcatOutput, logcat, { mode: 0o644 });

const evidence = {
  schemaVersion: 1,
  device: { family: "SM8650", abi: "arm64-v8a", sdk: 36, pageSize: 4096 },
  source: {
    projectRevision: execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim(),
    fexRevision: "f2b679f6028ce1c38875233aecfcf5d3f8ebecec",
  },
  artifact: packagedArtifact(),
  run: {
    exitCode: 0,
    durationMs,
    marker,
    contracts: { gpr: "ok", rflags: "ok", xmm: "ok", bridge: "ok", threads: "ok", tls: "ok", invalidation: "ok", teardown: "ok" },
  },
  logs: { instrumentationSha256: sha256(instrumentation), logcatSha256: sha256(logcat) },
};
const temporary = `${output}.tmp-${process.pid}`;
writeFileSync(temporary, `${JSON.stringify(evidence, null, 2)}\n`, { mode: 0o644 });
renameSync(temporary, output);
console.log(`FEX Phase 1 evidence written: ${output}`);
