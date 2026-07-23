import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => readFileSync(resolve(root, relative), "utf8");

test("debug APK exposes a generic ADB game launcher without another launcher icon", () => {
  const activity = read(
    "android/BachataS4/app/src/debug/kotlin/com/bachatas4/android/DirectLaunchActivity.kt",
  );
  const mainActivity = read(
    "android/BachataS4/app/src/main/kotlin/com/bachatas4/android/MainActivity.kt",
  );
  const manifest = read("android/BachataS4/app/src/debug/AndroidManifest.xml");
  const declaration = manifest.match(
    /<activity\s+[^>]*android:name="\.DirectLaunchActivity"[^>]*\/>/s,
  )?.[0];

  assert.ok(declaration, "DirectLaunchActivity must be declared in the debug manifest");
  assert.match(declaration, /android:exported="true"/);
  assert.doesNotMatch(declaration, /MAIN|LAUNCHER|intent-filter/);
  assert.match(activity, /class DirectLaunchActivity/);
  assert.match(activity, /DirectGameLaunchRequest\.resolve/);
  assert.match(activity, /DirectGameLaunchRequest\.EXTRA_GAME_ID/);
  assert.match(activity, /SessionScreen\(/);
  assert.match(activity, /GamepadInputManager\.dispatchKeyEvent/);
  assert.match(activity, /GamepadInputManager\.dispatchGenericMotionEvent/);
  assert.match(activity, /@SuppressLint\("RestrictedApi"\)/);
  assert.match(mainActivity, /@SuppressLint\("RestrictedApi"\)/);
  assert.match(activity, /Toast\.makeText/);
});

test("Bloodborne qualification launcher is bounded and preserves app data", () => {
  const script = read("runtime/qualification/run-bloodborne-fex-first-room.sh");

  assert.match(script, /set -euo pipefail/);
  assert.match(script, /DirectLaunchActivity/);
  assert.match(script, /--es game_id/);
  assert.match(script, /CUSA00900/);
  assert.match(script, /logcat -c/);
  assert.match(script, /screencap -p/);
  assert.match(script, /files\/logs/);
  assert.match(script, /run-as[^\n]*ls -1 files\/logs/);
  assert.match(script, /sessions_before=/);
  assert.match(script, /comm -13/);
  assert.ok(
    script.indexOf("sessions_before=") < script.indexOf("am start"),
    "existing sessions must be captured before launch",
  );
  assert.doesNotMatch(script, /run-as[^\n]*sh -c/);
  assert.match(script, /BACHATA_CAPTURE_SECONDS/);
  assert.match(script, /seq 1 60/);
  assert.doesNotMatch(script, /adb[^\n]*(?:uninstall|pm clear)/);
  assert.doesNotMatch(script, /run-as[^\n]*\brm\b/);
  assert.doesNotMatch(script, /rm -rf/);
});
