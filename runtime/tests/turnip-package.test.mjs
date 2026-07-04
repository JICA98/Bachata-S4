import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const lock = JSON.parse(readFileSync(new URL("../locks/runtime-inputs.lock.json", import.meta.url), "utf8"));
const packageSource = readFileSync(new URL("../scripts/package-runtime.mjs", import.meta.url), "utf8");

const archive = {
  name: "Turnip_v25.3.0_R11.zip",
  url: "https://github.com/K11MCH1/AdrenoToolsDrivers/releases/download/v25.3.0-rc.11/Turnip_v25.3.0_R11.zip",
  sha256: "0452bdd5e966ca94b58ba7c489db3648df6715d862e38cfa939ec62625293b40",
};

test("packages selectable Winlator Turnip drivers and isolated RC11", () => {
  assert.deepEqual(lock.inputs.find(({ name }) => name === archive.name), archive);
  assert.match(packageSource, /Mesa Turnip Driver v25\.3\.0-R11/);
  assert.match(packageSource, /minApi[^\n]+27/);
  assert.match(packageSource, /libraryName[^\n]+vulkan\.ad07xx\.so/);
  assert.match(packageSource, /turnip-25\.0\.0\.tzst/);
  assert.match(packageSource, /copy\([^\n]+turnipGlibcExtract[^\n]+drivers\/turnip-25\.0\.0\/libvulkan_freedreno\.so/);
  assert.match(packageSource, /copy\([^\n]+vulkan\.ad07xx\.so[^\n]+drivers\/turnip-25\.3\.0-r11\/vulkan\.ad07xx\.so/);
  assert.doesNotMatch(packageSource, /copy\([^\n]+vulkan\.ad07xx\.so[^\n]+host\/libvulkan_freedreno\.so/);
  assert.match(packageSource, /turnip-26\.1\.0\.tzst/);
  assert.match(packageSource, /9b4a10975456197e403c2b6a8a9781a8fd42ccf5048262a8cdea6538bb68d288/);
  assert.match(packageSource, /copy\([^\n]+turnip261Extract[^\n]+drivers\/turnip-26\.1\.0\/libvulkan_freedreno\.so/);
  assert.match(packageSource, /drivers\/turnip-25\.0\.0\/libvulkan_freedreno\.so/);
});
