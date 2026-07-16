import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const source = readFileSync(resolve(root, "src/common/signal_context.cpp"), "utf8");

test("Linux ARM64 signal context exposes PC and write-fault state", () => {
  assert.match(source, /#elif defined\(__linux__\) && defined\(ARCH_ARM64\)\s*\n\s*return \(void\*\)\(\(ucontext_t\*\)ctx\)->uc_mcontext\.pc;/);
  assert.match(source, /#include <asm\/sigcontext\.h>/);
  assert.match(source, /uc_mcontext\.__reserved/);
  assert.match(source, /ESR_MAGIC/);
  assert.match(source, /esr_context/);
  assert.match(source, /esr & 0x40/);
  assert.match(source, /#elif defined\(__linux__\) && defined\(ARCH_ARM64\)\s*\n\s*const auto\* context/);
  assert.doesNotMatch(source, /uc_mcontext\.esr/);
});
