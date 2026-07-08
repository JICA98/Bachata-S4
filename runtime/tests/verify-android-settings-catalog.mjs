#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const generator = resolve(projectRoot, "runtime/scripts/generate-android-settings-catalog.mjs");
const result = spawnSync(process.execPath, [generator, "--check"], { encoding: "utf8" });
if (result.status !== 0) {
  process.stderr.write(result.stderr || result.stdout);
  process.exit(result.status ?? 1);
}

const shad = JSON.parse(readFileSync(resolve(projectRoot, "android/BachataS4/core/runtime/src/main/resources/runtime-settings/shadps4.json"), "utf8"));
const box64 = JSON.parse(readFileSync(resolve(projectRoot, "android/BachataS4/core/runtime/src/main/resources/runtime-settings/box64.json"), "utf8"));
const unique = (items, field) => new Set(items.map((item) => item[field])).size === items.length;

if (!unique(shad, "id") || !unique(shad, "nativeKey") || !unique(box64, "id") || !unique(box64, "nativeKey")) {
  throw new Error("Settings catalog contains duplicate ids or native keys");
}
if (shad.length < 80 || box64.length < 100) {
  throw new Error(`Settings coverage unexpectedly low: shadPS4=${shad.length} box64=${box64.length}`);
}
console.log(`settings catalogs verified: shadPS4=${shad.length} box64=${box64.length}`);
