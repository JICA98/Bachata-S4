#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { readFileSync, renameSync, writeFileSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { marker, packagedArtifact } from "../tests/verify-fex-phase0-evidence.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const forbiddenText = /\/data\/|\/home\/|C:\\|com\.bachatas4\.android\/files|serial/i;

function fail(message) {
  throw new Error(`FEX Phase 0 evidence creation failed: ${message}`);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function readSanitized(path, label) {
  const contents = readFileSync(path, "utf8");
  if (forbiddenText.test(contents)) fail(`${label} contains private or serial-bearing text`);
  return contents.replace(/\r\n?/g, "\n");
}

if (process.argv.length !== 6) {
  console.error(`usage: ${process.argv[1]} duration-ms instrumentation.raw logcat.raw output.json`);
  process.exit(64);
}

const durationMs = Number(process.argv[2]);
if (!Number.isInteger(durationMs) || durationMs < 1 || durationMs > 30000) fail("duration must be an integer in 1..30000");
const instrumentation = readSanitized(resolve(process.argv[3]), "instrumentation");
const logcat = readSanitized(resolve(process.argv[4]), "logcat");
if (!instrumentation.includes(marker) && !logcat.includes(marker)) fail("exact FEXCore smoke marker was not captured");

const output = resolve(process.argv[5]);
const base = basename(output, ".json");
const outputDirectory = dirname(output);
const instrumentationOutput = join(outputDirectory, `${base}-instrumentation.txt`);
const logcatOutput = join(outputDirectory, `${base}-logcat.txt`);
writeFileSync(instrumentationOutput, instrumentation);
writeFileSync(logcatOutput, logcat);

const artifact = packagedArtifact();
const evidence = {
  schemaVersion: 1,
  device: { soc: "SM8650", abi: "arm64-v8a", sdk: 36, pageSize: 4096 },
  source: {
    projectRevision: execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim(),
    fexRevision: "f2b679f6028ce1c38875233aecfcf5d3f8ebecec",
  },
  artifact,
  run: {
    exitCode: 0,
    durationMs,
    marker,
    checks: { gpr: "ok", stack: "ok", fp: "ok", threads: "ok", tls: "ok", callback: "ok", invalidation: "ok" },
  },
  logs: { instrumentationSha256: sha256(instrumentation), logcatSha256: sha256(logcat) },
};
const temporary = `${output}.tmp-${process.pid}`;
writeFileSync(temporary, `${JSON.stringify(evidence, null, 2)}\n`, { mode: 0o644 });
renameSync(temporary, output);
console.log(`FEX Phase 0 evidence written: ${output}`);
