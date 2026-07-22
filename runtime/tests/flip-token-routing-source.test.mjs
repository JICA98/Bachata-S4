import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => readFileSync(resolve(root, relative), "utf8");

test("patched flip IRQs route by command-buffer token instead of FIFO order", () => {
  const platform = read("src/core/platform.h");
  const gnm = read("src/core/libraries/gnmdriver/gnmdriver.cpp");
  const videoOut = read("src/core/libraries/videoout/video_out.cpp");
  const liverpool = read("src/video_core/amdgpu/liverpool.cpp");

  assert.match(platform, /RegisterOnce\(InterruptId irq, u32 token, IrqHandler handler\)/);
  assert.match(platform, /Signal\(InterruptId irq, u32 token\)/);
  assert.match(platform, /keyed_one_time_subscribers/);
  assert.match(gnm, /const u32 flip_token = next_flip_token\.fetch_add/);
  assert.match(gnm, /nop->data_block\[1\] = flip_token/);
  assert.match(videoOut, /RegisterOnce\(\s*Platform::InterruptId::GfxFlip, flip_token,/);
  assert.match(
    liverpool,
    /Signal\(\s*Platform::InterruptId::GfxFlip,\s*nop->data_block\[1\]\)/,
  );
  assert.match(videoOut, /port->buffer_labels\[buf_id\] == 1/);
});
