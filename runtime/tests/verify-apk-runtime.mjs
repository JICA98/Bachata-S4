#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

const apk = resolve(process.argv[2] ?? "android/BachataS4/app/build/outputs/apk/debug/app-debug.apk");
const required = ["assets/runtime/manifest.json", "assets/runtime/runtime.zip"];

function listing(archive) {
  return execFileSync("unzip", ["-Z1", archive], { encoding: "utf8", maxBuffer: 16 * 1024 * 1024 })
    .split("\n").filter(Boolean);
}

function forbidden(entries) {
  return entries.filter((entry) => {
    const path = entry.toLowerCase();
    const name = path.split("/").at(-1);
    return path.includes("turnip") || name === "vulkan.ad07xx.so" ||
      name === "libvulkan_freedreno.so" || /(^|\/)freedreno[^/]*\.json$/.test(path);
  });
}

const apkEntries = listing(apk);
const missing = required.filter((entry) => !apkEntries.includes(entry));
if (missing.length) throw new Error(`APK is missing managed runtime assets: ${missing.join(", ")}`);

const temporary = mkdtempSync(join(tmpdir(), "bachata-apk-runtime-"));
try {
  const runtimeZip = join(temporary, "runtime.zip");
  writeFileSync(runtimeZip, execFileSync("unzip", ["-p", apk, "assets/runtime/runtime.zip"], {
    encoding: "buffer",
    maxBuffer: 256 * 1024 * 1024,
  }));
  const offenders = [...forbidden(apkEntries), ...forbidden(listing(runtimeZip)).map((entry) => `runtime.zip:${entry}`)];
  if (offenders.length) throw new Error(`APK bundles forbidden Turnip payloads:\n${offenders.join("\n")}`);
  console.log(`APK runtime verified: ${apkEntries.length} APK entries, required assets present, no bundled Turnip`);
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
