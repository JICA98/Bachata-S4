# Bloodborne FEX First-Room Design

## Objective

Run Bloodborne (`CUSA00900`) through the title flow into the first controllable
room using the native ARM64 shadPS4 host and FEXCore guest CPU backend. The
milestone passes only when that controlled scene sustains at least 30 FPS with
correct rendering, audio, controller input, and ten minutes of stability.

This is the first measurable Bloodborne milestone, not a claim that every area
or boss encounter has been validated.

## Backend Policy

FEX becomes the default guest backend for games. Box64 remains installed and
available as an explicit global or per-game fallback; it is not removed from
the runtime or packaging flow.

FEX launches inherit Android compatibility constraints needed by asynchronous
native execution, including copied GPU command buffers. Backend selection must
be visible in the session log so every performance result identifies the path
actually used.

## Bring-Up Flow

1. Build and install the Play Store debug variant with the committed ARM64 FEX
   runtime and managed runtime assets.
2. Launch Bloodborne directly by title ID, without library navigation.
3. Capture the earliest meaningful failure from the application log,
   shadPS4 log, Android logcat, and native tombstone when present.
4. Fix one proven blocker at a time in this order: configuration and launch,
   guest execution/imports, rendering and presentation, input/audio, then
   performance.
5. Reach the existing save's first controllable room before applying any
   performance behavior change.

Errors remain fatal when they violate guest state, ownership, memory, or GPU
ordering invariants. Targeted diagnostics may be added, but failures must not
be suppressed merely to extend runtime.

## Performance Investigation

Use the same save, first-room camera position, Turnip driver, resolution, and
30-second stationary capture for before/after measurements. Record FPS and
frame time alongside CPU, GPU, fault, and scheduler evidence.

The known protected-memory cache-maintenance fault loop is a high-priority
hypothesis, not an assumed FEX root cause. Apply its existing targeted design
only if the FEX trace still shows repeated `dc cvac`/`dc civac` faults or the
equivalent protected-memory retry loop. If removing that loop does not reach
30 FPS, profile the newly exposed bottleneck before changing another subsystem.

## Validation

The milestone requires all of the following on the connected Snapdragon 8 Gen
3 tablet:

- application log reports `guestBackend=fex`;
- title flow reaches the first controllable room;
- the same room holds at least 30 FPS during a stationary 30-second capture;
- controller movement and camera input work;
- game audio initializes and remains audible;
- screenshots show complete frames without missing geometry, corruption, or
  stale presentation;
- no fatal guest, FEX, Vulkan, flip-order, or ownership error occurs during a
  continuous ten-minute in-room run.

Any tablet-only criterion remains incomplete until observed on the device.

## Non-Goals

- removing Box64;
- claiming whole-game or boss-fight compatibility from the first-room result;
- broad security, W^X, signal, or validation disablement;
- speculative graphics-quality reductions or frame-generation techniques;
- unrelated emulator redesign.
