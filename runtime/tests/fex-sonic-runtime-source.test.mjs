import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => readFileSync(resolve(root, relative), "utf8");

test("Sonic packages and selects the native ARM64 FEX runtime without replacing Box64", () => {
  const build = read("runtime/scripts/build-runtime-debian.sh");
  const arm64Build = read("runtime/scripts/build-shadps4-arm64.sh");
  const dependencies = read("runtime/scripts/install-debian-runtime-deps.sh");
  const pack = read("runtime/scripts/package-runtime.mjs");
  const verify = read("runtime/tests/verify-runtime.mjs");
  const profile = read("android/BachataS4/app/src/main/kotlin/com/bachatas4/android/RuntimeLaunchProfileProvider.kt");
  const launcher = read("android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncher.kt");
  const service = read("android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt");

  assert.match(build, /build-shadps4-arm64\.sh/);
  assert.match(dependencies, /libx11-dev:arm64/);
  assert.match(dependencies, /libxext-dev:arm64/);
  assert.match(arm64Build, /XEXT_LIB:FILEPATH/);
  assert.match(arm64Build, /SDL_VIDEO_DRIVER_X11_DYNAMIC_XEXT/);
  assert.match(pack, /shadps4-arm64-fex/);
  assert.match(verify, /host\/shadps4-arm64-fex/);
  assert.match(profile, /CUSA07023/);
  assert.match(profile, /RuntimeGuestBackend\.FEX/);
  assert.match(launcher, /RuntimeGuestBackend/);
  assert.match(service, /guestBackend/);
  assert.match(service, /host\/shadps4-arm64-fex/);
  assert.match(pack, /bin\/shadps4/);
  assert.match(launcher, /RuntimeGuestBackend\.BOX64/);
});
