import assert from "node:assert/strict";
import test from "node:test";

import { validateEvidence } from "../qualification/validate-bloodborne-fex-first-room.mjs";

function validEvidence() {
  return {
    schemaVersion: 1,
    titleId: "CUSA00900",
    backendLogValues: ["fex"],
    processBackends: ["fex"],
    performanceSamples: Array.from({ length: 16 }, (_, index) => ({
      elapsedMs: index * 2_000,
      fps: 30 + (index % 3),
      frameTimeMs: 1_000 / (30 + (index % 3)),
    })),
    rendering: {
      startScreenshot: "screen-start.png",
      endScreenshot: "screen-end.png",
      completeFrames: true,
      noCorruption: true,
      noStalePresentation: true,
    },
    controller: { movement: true, camera: true, verifiedBy: "user" },
    audio: { audible: true, stable: true, verifiedBy: "user" },
    stability: { durationSeconds: 600, fatalErrors: [] },
  };
}

test("accepts complete FEX first-room evidence", () => {
  assert.deepEqual(validateEvidence(validEvidence()), {
    durationMs: 30_000,
    minimumFps: 30,
    samples: 16,
  });
});

test("rejects mixed backend evidence", () => {
  const evidence = validEvidence();
  evidence.processBackends.push("box64");
  assert.throws(() => validateEvidence(evidence), /backend/i);
});

test("rejects captures shorter than thirty seconds", () => {
  const evidence = validEvidence();
  evidence.performanceSamples.pop();
  assert.throws(() => validateEvidence(evidence), /30 seconds/i);
});

test("rejects any sample below thirty fps", () => {
  const evidence = validEvidence();
  evidence.performanceSamples[8].fps = 29.99;
  assert.throws(() => validateEvidence(evidence), /minimum fps/i);
});

test("rejects gaps in the bounded sample sequence", () => {
  const evidence = validEvidence();
  evidence.performanceSamples[8].elapsedMs += 4_000;
  assert.throws(() => validateEvidence(evidence), /consecutive/i);
});

test("rejects unverified rendering audio controller or stability", () => {
  for (const mutate of [
    (evidence) => { evidence.rendering.noCorruption = false; },
    (evidence) => { evidence.audio.audible = false; },
    (evidence) => { evidence.controller.camera = false; },
    (evidence) => { evidence.stability.fatalErrors.push("SIGSEGV"); },
  ]) {
    const evidence = validEvidence();
    mutate(evidence);
    assert.throws(() => validateEvidence(evidence));
  }
});
