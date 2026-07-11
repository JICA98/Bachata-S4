# Touch controller slot routing

## Problem

The touch overlay submits `ControllerSnapshot` values through the legacy
`ManagedSession.submitController(snapshot)` method. The emulation service
registers only the slot-aware sink, so these submissions have no listener and
never reach the emulator.

## Decision

Keep the touch overlay as a player-one controller. Make the legacy submission
method delegate to the slot-aware submission method with slot `0`.

This preserves the legacy API's player-one semantics, routes it through the
service's active controller transport, and avoids coupling Compose UI code to
the multi-controller transport API.

## Rejected alternatives

* Change `SessionScreen` to call the slot-aware method directly. This fixes the
  current overlay but leaves the legacy API able to silently discard input.
* Register both legacy and slot-aware service sinks. This duplicates transport
  wiring and can submit the same player-one state through two paths.

## Data flow

`FixedControllerOverlay` → `ManagedSession.submitController(snapshot)` →
`ManagedSession.submitController(0, snapshot)` → slot-aware service sink →
controller frame encoder → emulator socket.

## Error handling and compatibility

The existing slot validation remains authoritative. The compatibility overload
always uses valid slot `0`. Slot-aware callers retain their existing behavior,
including forwarding player-one input to an attached legacy observer.

## Tests and verification

Add a unit test that attaches only the slot-aware sink, sends a legacy snapshot,
and asserts receipt as slot `0`. The test must fail before the delegation and
pass after it. Run the runtime and session debug unit-test tasks after the fix.
