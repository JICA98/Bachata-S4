# Direct Game Launch Activity

## Goal

Provide an ADB-only entry point in debug APKs that launches any imported game by title ID without navigating the library UI.

## Interface

```text
adb shell am start -n com.bachatas4.android/.DirectLaunchActivity --es game_id CUSA07023
```

`game_id` is required and must be a valid PlayStation title ID. The Activity resolves it beneath the app-owned `files/games` directory and requires `eboot.bin` to exist.

## Architecture

`DirectLaunchActivity` is exported only by debug manifests and has no launcher intent filter. It validates the request and hosts the existing `SessionScreen` directly. Keeping the Activity alive is required because `SessionScreen` owns the Android rendering surface and delegates the managed-session start to `SessionViewModel`. Runtime backend and driver selection remain owned by the existing launch-profile and settings code.

The production service remains non-exported. No arbitrary filesystem path is accepted.

## Errors

Missing, malformed, escaping, or uninstalled game IDs do not start emulation. The Activity emits a focused log entry, shows a short Toast, and finishes. A valid launch remains open for rendering, controller input, audio, telemetry, and session shutdown.

## Verification

- Unit-test title-ID validation and app-storage resolution.
- Verify the merged debug manifest exports the Activity without a launcher category.
- Install the Play Store debug APK and launch `CUSA07023` with the ADB command.
- Confirm a new managed session uses the FEX backend and continue title-driven Sonic validation.
