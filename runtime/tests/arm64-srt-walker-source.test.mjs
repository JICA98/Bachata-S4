import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const passUrl = new URL(
  "../../src/shader_recompiler/ir/passes/flatten_extended_userdata_pass.cpp",
  import.meta.url,
);
const serializationUrl = new URL(
  "../../src/video_core/renderer_vulkan/vk_pipeline_serialization.cpp",
  import.meta.url,
);
const signalContextUrl = new URL("../../src/common/signal_context.cpp", import.meta.url);

test("ARM64 builds and executes native SRT walkers", () => {
  const source = readFileSync(passUrl, "utf8");
  const signalContext = readFileSync(signalContextUrl, "utf8");

  assert.match(source, /#elif defined\(ARCH_ARM64\) && defined\(__linux__\)/);
  assert.match(source, /class Arm64SrtEmitter/);
  assert.match(source, /SrtWalkerSignalHandler/);
  assert.match(source, /Common::IncrementRip\(context, sizeof\(u32\)\)/);
  assert.match(source, /info\.srt_info\.walker_func = RegisterWalkerCode/);
  assert.match(source, /info\.srt_info\.flattened_bufsize_dw = pass_info\.dst_off_dw/);
  assert.match(signalContext, /uc_mcontext\.pc \+= length/);
});

test("ARM64 rejects incompatible cached SRT machine code", () => {
  const source = readFileSync(passUrl, "utf8");
  const serialization = readFileSync(serializationUrl, "utf8");

  assert.match(source, /IsArm64SrtWalker\(ptr, size\)/);
  assert.match(source, /return nullptr/);
  assert.match(serialization, /if \(!walker_func\) \{\s*return false;\s*\}/);
});
