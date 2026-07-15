import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const sourceUrl = new URL("../probes/fexcore-smoke.cpp", import.meta.url);

test("FEXCore smoke owns a guarded call/return stack through thread destruction", () => {
  const source = readFileSync(sourceUrl, "utf8");

  assert.match(
    source,
    /class CallRetStack final \{[\s\S]*FEXCore::Core::InternalThreadState::CALLRET_STACK_SIZE \+ 2 \* kRequiredPageSize/,
  );
  assert.match(
    source,
    /mmap\(nullptr, kAllocationSize, PROT_NONE, MAP_PRIVATE \| MAP_ANONYMOUS, -1, 0\)/,
  );
  assert.match(source, /Address != MAP_FAILED/);
  assert.match(
    source,
    /~CallRetStack\(\) \{[\s\S]*?if \(IsReserved\(\) && munmap\(Address, kAllocationSize\) != 0\) \{[\s\S]*?FEXCORE_SMOKE_FATAL check=release-callret-stack[\s\S]*?std::abort\(\);[\s\S]*?\}/,
  );
  assert.match(source, /if \(!callRetStack\.IsReserved\(\)\) return Fail\("reserve-callret-stack"\);/);
  assert.match(
    source,
    /mprotect\(StackBase\(\), FEXCore::Core::InternalThreadState::CALLRET_STACK_SIZE, PROT_READ \| PROT_WRITE\) == 0/,
  );
  assert.match(source, /if \(!callRetStack\.MakeWritable\(\)\) return Fail\("protect-callret-stack"\);/);
  assert.match(source, /thread->CallRetStackBase = StackBase\(\);/);
  assert.match(
    source,
    /thread->CurrentFrame->State\.callret_sp =\s*reinterpret_cast<uint64_t>\(StackBase\(\)\) \+\s*FEXCore::Core::InternalThreadState::CALLRET_STACK_SIZE \/ 4;/,
  );

  const stackOwner = source.indexOf("CallRetStack callRetStack;");
  const createThread = source.indexOf("auto* thread = context->CreateThread(initialRip, initialRsp);");
  const attachStack = source.indexOf("callRetStack.Initialize(thread);");
  const threadScope = source.indexOf("ThreadScope threadScope {context.get(), thread};");

  assert.notEqual(stackOwner, -1);
  assert.notEqual(createThread, -1);
  assert.notEqual(attachStack, -1);
  assert.notEqual(threadScope, -1);
  assert.ok(stackOwner < createThread, "call/return stack must be reserved before the thread");
  assert.ok(createThread < attachStack, "call/return stack must be attached after CreateThread succeeds");
  assert.ok(attachStack < threadScope, "call/return stack must outlive DestroyThread");
});
