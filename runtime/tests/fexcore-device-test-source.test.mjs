import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const sourceUrl = new URL(
  "../../android/BachataS4/app/src/androidTest/kotlin/com/bachatas4/android/FexCoreSmokeDeviceTest.kt",
  import.meta.url,
);

test("FEXCore device smoke uses only an isolated cache child and narrow cleanup", () => {
  const source = readFileSync(sourceUrl, "utf8");

  assert.match(
    source,
    /val cacheDir = targetContext\.cacheDir\.toPath\(\)\.toRealPath\(\)/,
  );
  assert.match(source, /cacheDir\.resolve\("fexcore-smoke-\$\{System\.nanoTime\(\)\}"\)/);
  assert.match(source, /cleanupUniqueChild\(installRoot\)/);
  assert.match(source, /Files\.walk\(child\)/);
  assert.match(source, /child\.fileName\.toString\(\)\.startsWith\("fexcore-smoke-"\)/);
  assert.match(source, /child\.parent == cacheDir/);

  for (const forbidden of [
    /pm\s+clear/i,
    /\buninstall\b/i,
    /filesDir\.resolve\("games"\)/,
    /filesDir\.toPath\(\)\.resolve\("games"\)/,
    /deleteRecursively\(\s*(?:targetContext\.)?cacheDir\s*\)/,
    /Files\.walk\(\s*(?:targetContext\.)?cacheDir\s*\)/,
    /deleteIfExists\(\s*(?:targetContext\.)?cacheDir\s*\)/,
  ]) {
    assert.doesNotMatch(source, forbidden);
  }
});
