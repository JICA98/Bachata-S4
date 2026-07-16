import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => readFileSync(resolve(root, relative), "utf8");

test("cross builds use a native ImGui font tool instead of executing a target binary", () => {
  const rendererCmake = read("src/imgui/renderer/CMakeLists.txt");
  const arm64Build = read("runtime/scripts/build-shadps4-arm64.sh");

  assert.match(rendererCmake, /IMGUI_FONT_EMBED_EXECUTABLE/);
  assert.match(rendererCmake, /if\s*\(\s*CMAKE_CROSSCOMPILING\s*\)/);
  assert.match(rendererCmake, /CMAKE_CROSSCOMPILING[\s\S]*IMGUI_FONT_EMBED_EXECUTABLE[\s\S]*FATAL_ERROR/);
  assert.match(rendererCmake, /COMMAND\s+\$\{IMGUI_FONT_EMBED_COMMAND\}/);
  assert.match(rendererCmake, /DEPENDS\s+\$\{IMGUI_FONT_EMBED_DEPENDS\}/);

  assert.match(arm64Build, /binary_to_compressed_c\.cpp/);
  assert.match(arm64Build, /host_font_embed/);
  assert.match(arm64Build, /-DIMGUI_FONT_EMBED_EXECUTABLE="\$host_font_embed"/);
  assert.doesNotMatch(arm64Build, /qemu|CMAKE_CROSSCOMPILING_EMULATOR/i);
});
