import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => readFileSync(resolve(root, relative), "utf8");

function functionBody(source, signature) {
  const signatureIndex = source.indexOf(signature);
  assert.notEqual(signatureIndex, -1, `missing ${signature}`);
  const openingBrace = source.indexOf("{", signatureIndex);
  let depth = 0;
  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] === "}") depth -= 1;
    if (depth === 0) return source.slice(signatureIndex, index + 1);
  }
  assert.fail(`unterminated ${signature}`);
}

test("FEX HLE guest-memory validation caches only stable VMA generations", () => {
  const linker = read("src/core/linker.cpp");
  const memoryHeader = read("src/core/memory.h");
  const memory = read("src/core/memory.cpp");

  assert.match(linker, /thread_local GuestCpu::GuestMemoryValidationCache/);
  assert.match(linker, /memory->MappingGeneration\(\)/);
  assert.match(linker, /validation_cache\.Contains\(/);
  assert.match(linker, /QueryProtection\([^;]+&range_generation\)/s);
  assert.match(linker, /validation_cache\.Store\(/);

  assert.match(memoryHeader, /MemoryMapGeneration mapping_generation/);
  assert.match(memoryHeader, /u64\* generation/);
  assert.match(memory, /\*generation = mapping_generation\.Load\(\)/);

  for (const method of [
    "MemoryManager::Free(",
    "MemoryManager::PoolCommit(",
    "MemoryManager::MapMemory(",
    "MemoryManager::MapFile(",
    "MemoryManager::PoolDecommit(",
    "MemoryManager::UnmapMemory(",
    "MemoryManager::Protect(",
    "MemoryManager::SetDirectMemoryType(",
    "MemoryManager::NameVirtualRange(",
  ]) {
    assert.match(
      functionBody(memory, method),
      /mapping_generation\.BeginMutation\(\)/,
      `${method} must invalidate cached VMA metadata while its writer lock is held`,
    );
  }
});
