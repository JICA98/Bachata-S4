import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const presenter = readFileSync(
  new URL("../../src/video_core/renderer_vulkan/vk_presenter.cpp", import.meta.url),
  "utf8",
);

test("Bachata runtime compensates X11 present channel order", () => {
  assert.match(presenter, /#ifdef ENABLE_BACHATA_RUNTIME[\s\S]*A8B8G8R8Srgb[\s\S]*eB8G8R8A8Srgb/);
});
