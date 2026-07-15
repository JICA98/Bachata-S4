# FEX Phase 1 Guest-CPU Design

## Goal

Create the smallest shadPS4-owned, native-AArch64 FEXCore guest-CPU harness
that can execute a controlled PS4-style x86-64 guest thread and cross the
guest/HLE boundary deterministically. It is the reusable CPU foundation for a
Sonic Mania compatibility slice, followed by Bloodborne work.

## Context

Phase 0 proved the pinned FEXCore revision (`f2b679f6028ce1c38875233aecfcf5d3f8ebecec`)
in the packaged Android runtime on SM8650. The proof covers guest GPRs, stack,
floating point/XMM state, threads, TLS, host callback, invalidation, teardown,
and a physical 1,420 ms run. It is an embedding probe, not a shadPS4 CPU
backend or a game launch path.

The current Android runtime still launches an x86-64 shadPS4 executable through
Box64. Phase 1 must not claim that FEX is the game default, replace Box64, or
make Sonic Mania launch. FEX becomes the default guest CPU setting only after
Sonic completes its acceptance slice.

## Chosen Approach

Build a narrow, production-owned FEX guest engine and an Android-exercisable
harness around it. The engine has an explicit lifecycle and an explicit host
bridge; it does not expose a raw FEX context to shadPS4 callers. The first
harness uses synthetic x86-64 guest code and a controlled guest mapping rather
than a PS4 executable.

This is preferred over launching Sonic immediately because it isolates the
seven CPU-embedding contracts that a game cannot diagnose cleanly: guest state,
memory, thread creation, register synchronization, guest-to-host calls,
invalidation, and teardown. It is also preferred over a Wine/ARM64EC launcher
because shadPS4 needs a guest CPU backend, not a Windows process environment.

## Architecture

```text
Native AArch64 shadPS4-owned harness
  -> FexGuestEngine lifecycle owner
    -> pinned FEXCore configuration/context/thread state
    -> controlled x86-64 guest mapping and RIP/RSP initialization
    -> FexHostBridge callbacks
      -> narrow HLE-dispatch adapter
```

`FexGuestEngine` owns all FEXCore state that has process or thread lifetime:
configuration initialization/shutdown, context, signal delegator, syscall
handler, guest-thread state, code cache/invalidation, and destruction order.
`FexHostBridge` is the only interface through which translated guest code can
request a host/HLE operation. It receives an operation identifier and the
guest CPU frame, returns a defined result, and never assumes a host ABI is
identical to the x86-64 guest ABI.

The controlled harness owns its executable guest page, stack, and data page.
It initializes RIP, RSP, GPRs, flags, segment state, and XMM state through the
same engine entry point that later production integration will use. It proves
host callbacks, guest-return behavior, a second guest thread, code
invalidation, and ordered shutdown. Failures are structured results with the
failing lifecycle stage and errno/FEX error detail; they are not swallowed.

## Phase 1 Contract

The harness must prove all of the following on the tablet before Phase 1 is
complete:

- The host binary and FEXCore are AArch64/glibc runtime artifacts; no Box64 is
  invoked by the harness.
- One engine creates a 64-bit FEX context and one guest thread on the owning
  host thread, then executes from a specified guest RIP and stack.
- Guest GPR, RFLAGS, XMM, segment/TLS state, and return state survive the
  guest-to-host bridge with the documented values.
- The host bridge observes an exact synthetic HLE operation, writes its result
  into the guest frame, and returns guest control without host/guest ABI casts.
- A second guest thread has independent state and may use the same engine
  without TLS or signal-handler corruption.
- Explicit invalidation causes the changed guest instruction stream to be
  observed on the next execution.
- Every mapping, thread state, context, configuration object, and temporary
  code page is released in reverse dependency order.

The harness will produce a bounded, sanitized Android marker containing the
FEX revision and the seven contract outcomes. It must not emit a device serial,
private app path, game content, or guest-memory dump.

## Explicit Non-Goals

- No direct Sonic Mania or Bloodborne launch in Phase 1.
- No Wine, ARM64EC, `app_process`, external x86 process launcher, or copied
  GameNative Wine behavior.
- No wholesale port of the shadPS4 UI, renderer, audio stack, or PS4 kernel/HLE
  implementation to AArch64 in this milestone.
- No Box64 removal, default-setting change, performance claim, or game
  compatibility claim.
- No global disabling of Android security checks or signal handlers.

## Follow-On Compatibility Stages

Phase 2 wires the engine behind an explicit native-AArch64 shadPS4 CPU-backend
boundary and adds the minimum guest-memory and HLE dispatch adapters necessary
to load a real title. It keeps Box64 as a separate selectable legacy runtime
until Sonic succeeds.

Phase 3 is Sonic Mania acceptance: the game reaches and completes its first
level using a controller, rendering and audio work, and the process remains
stable for ten minutes. Only then does FEX become the default guest CPU setting
for games, while Box64 remains an explicit fallback.

Phase 4 is Bloodborne bring-up on the same backend: boot/title progression,
then in-room execution, followed by measured frame-time analysis and targeted
optimization. A 30+ FPS target is a performance objective after correctness;
it is not an acceptance shortcut for missing CPU, GPU, audio, or HLE behavior.

## Validation Strategy

Phase 1 uses three layers of evidence:

1. Source-level tests lock the lifecycle, state transfer, bridge contract,
   cleanup order, and absence of Box64/Wine launch coupling.
2. The Debian runtime build and APK verifier prove that the native-AArch64
   harness and required FEXCore artifacts are packaged in the managed runtime.
3. A replacement-install tablet runner verifies the exact sanitized marker and
   independently validates its revision, duration, artifact hashes, and
   privacy constraints.

The existing Phase 0 smoke remains a regression gate. A Phase 1 failure stops
at the first failed contract and records the lifecycle boundary; it does not
fall back to Box64 or silently downgrade the test.

## Decision Gates

Do not begin Sonic work until the Phase 1 tablet contract passes. Do not make
FEX the default for every game until the Sonic acceptance slice passes. Do not
start Bloodborne performance tuning until Bloodborne has reached stable in-room
execution through the same FEX guest engine.
