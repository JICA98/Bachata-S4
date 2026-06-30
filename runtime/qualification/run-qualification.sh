#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 gate-8-qualification.json" >&2
  exit 64
fi

evidence=$1
project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
schema="$project_root/runtime/qualification/qualification-schema.json"

node --input-type=module - "$schema" "$evidence" <<'NODE'
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
const [, , schemaPath, evidencePath] = process.argv;
const schema = JSON.parse(readFileSync(schemaPath, "utf8"));
const evidenceText = readFileSync(evidencePath, "utf8");
const evidence = JSON.parse(evidenceText);
const fail = (message) => { throw new Error(message); };
if (schema.properties.schemaVersion.const !== evidence.schemaVersion) fail("schemaVersion mismatch");
if (!/^[0-9a-f]{64}$/.test(evidence.fingerprintSha256 ?? "")) fail("fingerprint must be SHA-256");
if ("serial" in evidence || /\/data\/(user|data)|\/storage\//.test(evidenceText)) fail("private identifier/path in evidence");
if (!Array.isArray(evidence.sessions) || evidence.sessions.length !== 10) fail("exactly ten sessions required");
if (evidence.sessions.filter((s) => s.kind === "cold").length !== 5) fail("five cold sessions required");
if (evidence.sessions.filter((s) => s.kind === "warm").length !== 5) fail("five warm sessions required");
for (const session of evidence.sessions) {
  for (const key of ["startupMs", "peakRssBytes", "audioUnderruns", "droppedInput", "exitCode", "crashFreeSeconds"]) {
    if (typeof session[key] !== "number") fail(`missing numeric ${key}`);
  }
  for (const percentile of ["p50", "p95", "p99"]) if (typeof session.frameTimeMs?.[percentile] !== "number") fail(`missing ${percentile}`);
}
console.log(`qualification valid: ${evidence.soc} sessions=${evidence.sessions.length} sha256=${createHash("sha256").update(evidenceText).digest("hex")}`);
NODE
