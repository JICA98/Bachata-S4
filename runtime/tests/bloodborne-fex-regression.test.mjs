import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const linkerUrl = new URL("../../src/core/linker.cpp", import.meta.url);
const semaphoreUrl = new URL(
  "../../src/core/libraries/kernel/threads/semaphore.cpp",
  import.meta.url,
);
const ajmHeaderUrl = new URL("../../src/core/libraries/ajm/ajm.h", import.meta.url);
const ajmSourceUrl = new URL("../../src/core/libraries/ajm/ajm.cpp", import.meta.url);
const symbolsResolverUrl = new URL(
  "../../src/core/loader/symbols_resolver.cpp",
  import.meta.url,
);

test("Bloodborne guest threads expose the PS4 TCB through x86-64 FS", () => {
  const linker = readFileSync(linkerUrl, "utf8");
  const fsAssignments = [
    ...linker.matchAll(
      /request\.FsBase\s*=\s*reinterpret_cast<std::uintptr_t>\(GetTcbBase\(\)\)/g,
    ),
  ];

  assert.ok(fsAssignments.length >= 2, "main and detached guest calls require an FS base");
  assert.doesNotMatch(
    linker,
    /request\.GsBase\s*=\s*reinterpret_cast<std::uintptr_t>\(GetTcbBase\(\)\)/,
  );
});

test("PS4 semaphore handles cross the FEX HLE boundary as their u32 ABI value", () => {
  const semaphore = readFileSync(semaphoreUrl, "utf8");

  assert.match(semaphore, /using OrbisKernelSema = u32;/);
  assert.match(semaphore, /orbis_sems\s*\.insert\([\s\S]*\)\s*\.index;/);
  assert.doesNotMatch(semaphore, /using OrbisKernelSema = Common::SlotId;/);
});

test("AJM instance flags cross the FEX HLE boundary as their u64 ABI value", () => {
  const header = readFileSync(ajmHeaderUrl, "utf8");
  const source = readFileSync(ajmSourceUrl, "utf8");

  assert.match(
    header,
    /sceAjmInstanceCreate\(u32 context, AjmCodecType codec_type, u64 flags_raw,/,
  );
  assert.match(source, /AjmInstanceFlags flags\{\.raw = flags_raw\};/);
  assert.doesNotMatch(
    header,
    /sceAjmInstanceCreate\(u32 context, AjmCodecType codec_type, AjmInstanceFlags flags,/,
  );
});

test("late HLE registration replaces an existing FEX ENOSYS fallback", () => {
  const resolver = readFileSync(symbolsResolverUrl, "utf8");

  assert.match(
    resolver,
    /record\.name == name && record\.hle_fallback[\s\S]*record\.hle_adapter = std::move\(adapter\);[\s\S]*record\.hle_fallback = false;/,
  );
});

test("AJM batch inputs and return addresses are read-only at the FEX boundary", () => {
  const header = readFileSync(ajmHeaderUrl, "utf8");
  const source = readFileSync(ajmSourceUrl, "utf8");

  for (const implementation of [header, source]) {
    assert.match(
      implementation,
      /sceAjmBatchJobControlBufferRa\([\s\S]{0,400}const void\* p_sideband_input[\s\S]{0,400}const void\* p_return_address/,
    );
    assert.match(
      implementation,
      /sceAjmBatchJobRunBufferRa\([\s\S]{0,400}const void\* p_data_input[\s\S]{0,500}const void\* p_return_address/,
    );
    assert.match(
      implementation,
      /sceAjmBatchJobRunSplitBufferRa\([\s\S]{0,600}const void\* p_return_address/,
    );
  }
});
