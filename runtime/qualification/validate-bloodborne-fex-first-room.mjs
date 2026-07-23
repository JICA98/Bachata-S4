#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

function requireObject(value, label) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label} must be an object`);
  }
  return value;
}

function requireTrue(value, label) {
  if (value !== true) throw new Error(`${label} is not verified`);
}

function requireFinite(value, label) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new Error(`${label} must be a finite number`);
  }
  return value;
}

export function validateEvidence(value) {
  const evidence = requireObject(value, "evidence");
  if (evidence.schemaVersion !== 1) throw new Error("Unsupported evidence schemaVersion");
  if (evidence.titleId !== "CUSA00900") throw new Error("Evidence titleId must be CUSA00900");

  for (const [label, backends] of [
    ["backend log", evidence.backendLogValues],
    ["process backend", evidence.processBackends],
  ]) {
    if (!Array.isArray(backends) || backends.length === 0 || backends.some((backend) => backend !== "fex")) {
      throw new Error(`${label} evidence must contain only fex`);
    }
  }

  const samples = evidence.performanceSamples;
  if (!Array.isArray(samples) || samples.length < 2) throw new Error("Performance samples are missing");
  let previousElapsed;
  let minimumFps = Number.POSITIVE_INFINITY;
  for (const [index, rawSample] of samples.entries()) {
    const sample = requireObject(rawSample, `performanceSamples[${index}]`);
    const elapsed = requireFinite(sample.elapsedMs, `performanceSamples[${index}].elapsedMs`);
    const fps = requireFinite(sample.fps, `performanceSamples[${index}].fps`);
    const frameTime = requireFinite(sample.frameTimeMs, `performanceSamples[${index}].frameTimeMs`);
    if (elapsed < 0 || frameTime <= 0) throw new Error("Performance sample values must be positive");
    if (previousElapsed !== undefined) {
      const gap = elapsed - previousElapsed;
      if (gap <= 0 || gap > 3_000) throw new Error("Performance samples are not consecutive");
    }
    if (fps < 30) throw new Error(`Performance minimum FPS is below 30: ${fps}`);
    minimumFps = Math.min(minimumFps, fps);
    previousElapsed = elapsed;
  }
  const durationMs = samples.at(-1).elapsedMs - samples[0].elapsedMs;
  if (durationMs < 30_000) throw new Error("Performance capture must cover at least 30 seconds");

  const rendering = requireObject(evidence.rendering, "rendering");
  if (typeof rendering.startScreenshot !== "string" || rendering.startScreenshot.length === 0 ||
      typeof rendering.endScreenshot !== "string" || rendering.endScreenshot.length === 0) {
    throw new Error("Rendering screenshots are missing");
  }
  requireTrue(rendering.completeFrames, "Complete frames");
  requireTrue(rendering.noCorruption, "Rendering corruption check");
  requireTrue(rendering.noStalePresentation, "Stale presentation check");

  const controller = requireObject(evidence.controller, "controller");
  requireTrue(controller.movement, "Controller movement");
  requireTrue(controller.camera, "Controller camera");
  if (controller.verifiedBy !== "user") throw new Error("Controller evidence requires user verification");

  const audio = requireObject(evidence.audio, "audio");
  requireTrue(audio.audible, "Audible game audio");
  requireTrue(audio.stable, "Stable game audio");
  if (audio.verifiedBy !== "user") throw new Error("Audio evidence requires user verification");

  const stability = requireObject(evidence.stability, "stability");
  if (requireFinite(stability.durationSeconds, "stability.durationSeconds") < 600) {
    throw new Error("Stability evidence must cover ten minutes");
  }
  if (!Array.isArray(stability.fatalErrors) || stability.fatalErrors.length !== 0) {
    throw new Error("Stability evidence contains fatal errors");
  }

  return { durationMs, minimumFps, samples: samples.length };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const evidencePath = process.argv[2];
  if (!evidencePath) {
    console.error("Usage: validate-bloodborne-fex-first-room.mjs <evidence.json>");
    process.exit(2);
  }
  try {
    const result = validateEvidence(JSON.parse(readFileSync(evidencePath, "utf8")));
    console.log(
      `Bloodborne FEX first-room evidence verified: durationMs=${result.durationMs} ` +
      `minimumFps=${result.minimumFps.toFixed(2)} samples=${result.samples}`,
    );
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
  }
}
