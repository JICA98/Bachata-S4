import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const sourceUrl = new URL("../probes/fexcore-smoke.cpp", import.meta.url);

test("FEXCore smoke initializes stable x86-64 segment state before execution", () => {
  const source = readFileSync(sourceUrl, "utf8");

  assert.match(
    source,
    /class GuestSegmentState final \{[\s\S]*std::array<FEXCore::Core::CPUState::gdt_segment, 32> GDT \{\};[\s\S]*\};/,
  );
  assert.match(
    source,
    /state\.segment_arrays\[FEXCore::Core::CPUState::SEGMENT_ARRAY_INDEX_GDT\] = GDT\.data\(\);/,
  );
  assert.match(
    source,
    /state\.segment_arrays\[FEXCore::Core::CPUState::SEGMENT_ARRAY_INDEX_LDT\] = GDT\.data\(\);/,
  );
  assert.match(source, /state\.cs_idx = FEXCore::Core::CPUState::DEFAULT_USER_CS << 3;/);
  assert.match(
    source,
    /auto\* codeSegment = FEXCore::Core::CPUState::GetSegmentFromIndex\(state, state\.cs_idx\);/,
  );
  assert.match(source, /FEXCore::Core::CPUState::SetGDTBase\(codeSegment, 0\);/);
  assert.match(source, /FEXCore::Core::CPUState::SetGDTLimit\(codeSegment, 0xF'FFFFU\);/);
  assert.match(
    source,
    /state\.cs_cached = FEXCore::Core::CPUState::CalculateGDTBase\(\*codeSegment\);/,
  );
  assert.match(source, /codeSegment->L = 1;/);
  assert.match(source, /codeSegment->D = 0;/);

  const stateOwner = source.indexOf("GuestSegmentState guestSegmentState;");
  const createThread = source.indexOf("auto* thread = context->CreateThread(initialRip, initialRsp);");
  const initializeState = source.lastIndexOf("guestSegmentState.Initialize(thread->CurrentFrame->State);");
  const executeThread = source.lastIndexOf("context->ExecuteThread(thread);");

  assert.notEqual(stateOwner, -1);
  assert.notEqual(createThread, -1);
  assert.notEqual(initializeState, -1);
  assert.notEqual(executeThread, -1);
  assert.ok(stateOwner < createThread, "segment storage must outlive the FEX thread");
  assert.ok(createThread < initializeState, "segment state must initialize the created thread");
  assert.ok(initializeState < executeThread, "segment state must initialize before ExecuteThread");
});
