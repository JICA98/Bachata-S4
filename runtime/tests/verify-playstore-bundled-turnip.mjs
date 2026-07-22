#!/usr/bin/env node

/**
 * Assert a Play Store APK/AAB packages the bundled Turnip 26.1.0 asset
 * and does not embed it under the managed runtime rootfs path.
 */
import { execFileSync } from "node:child_process";
import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

const target = resolve(process.argv[2] ?? "");
if (!target || !existsSync(target)) {
  console.error("Usage: verify-playstore-bundled-turnip.mjs <playstore.apk|aab>");
  process.exit(2);
}

const expectedAsset = "assets/drivers/turnip-26.1.0-EMULATOR.zip";
const expectedShaPath = resolve(
  "android/BachataS4/app/src/playstore/assets/drivers/turnip-26.1.0-EMULATOR.zip.sha256",
);

const listing = execFileSync("unzip", ["-Z1", target], { encoding: "utf8" })
  .split("\n")
  .filter(Boolean);

const hasAsset = listing.some(
  (entry) => entry === expectedAsset || entry.endsWith(`/${expectedAsset}`) || entry.endsWith(expectedAsset),
);
// AAB stores as base/assets/...
const hasAabAsset = listing.some((entry) => entry.includes("assets/drivers/turnip-26.1.0-EMULATOR.zip"));

if (!hasAsset && !hasAabAsset) {
  console.error(`Missing bundled Turnip asset in ${target}`);
  process.exit(1);
}

const runtimeLeak = listing.filter((entry) => {
  const path = entry.toLowerCase();
  return (
    path.includes("assets/runtime/") &&
    (path.includes("libvulkan_freedreno") ||
      path.includes("vulkan.ad07xx") ||
      path.includes("turnip-25") ||
      path.includes("turnip-26"))
  );
});
if (runtimeLeak.length > 0) {
  console.error(`Turnip leaked into managed runtime packaging:\n${runtimeLeak.join("\n")}`);
  process.exit(1);
}

if (existsSync(expectedShaPath)) {
  const expected = readFileSync(expectedShaPath, "utf8").trim().split(/\s+/)[0];
  const assetEntry =
    listing.find((e) => e.endsWith("assets/drivers/turnip-26.1.0-EMULATOR.zip")) ?? expectedAsset;
  try {
    // Stream extract to avoid ENOBUFS on large archives under some hosts.
    const actual = execFileSync(
      "bash",
      ["-lc", `unzip -p ${JSON.stringify(target)} ${JSON.stringify(assetEntry)} | sha256sum | awk '{print $1}'`],
      { encoding: "utf8", maxBuffer: 1024 * 1024 },
    ).trim();
    if (actual !== expected) {
      console.error(`Bundled Turnip SHA-256 mismatch: expected ${expected}, got ${actual}`);
      process.exit(1);
    }
  } catch (error) {
    console.error(`Failed to extract asset for checksum: ${error.message ?? error}`);
    process.exit(1);
  }
}

console.log(`Play bundled Turnip OK in ${target}`);
