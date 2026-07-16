import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const installer = readFileSync(
  resolve(root, "runtime/scripts/install-debian-runtime-deps.sh"),
  "utf8",
);

test("ARM64 native shadPS4 build installs target development linker inputs", () => {
  const arm64Packages = installer.match(/arch_arm64=\([\s\S]*?\n\)/)?.[0] ?? "";

  assert.match(arm64Packages, /\buuid-dev:arm64\b/);
  assert.match(arm64Packages, /\blibudev-dev:arm64\b/);
});
