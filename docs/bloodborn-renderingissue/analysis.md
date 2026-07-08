# Bloodborne First-Frame Rendering Analysis

## Summary

Bloodborne reaches the running state in Bachata S4, initializes Vulkan, opens VideoOut, and registers three 1920×1080 guest buffers. It never advances to `PrepareFrame` or a presented flip, leaving the Android surface grey.

The working native Android port follows the same initialization sequence but prepares and presents its first frame about 7.7 seconds after launch. The failure boundary is therefore between guest VideoOut buffer registration and Bachata's first presenter submission.

## Compared Logs

- `bachata/application.log`: Bachata session lifecycle.
- `bachata/shadps4.log`: Bachata Box64/shadPS4 output.
- `bachata/logcat.txt`: Android logcat during the three-minute reproduction.
- `bachata/processes.txt`: Process and thread snapshot.
- `official/shad_log.txt`: Working native Android port output.

## Common Successful Initialization

Both runs:

- Detect the Turnip GPU and enable `VK_KHR_swapchain`.
- Initialize the Vulkan presenter.
- Open VideoOut.
- Configure `A8R8G8B8Srgb`, 1920×1080, pitch 1920.
- Register three guest frame buffers.
- Continue loading Bloodborne resources.

### Bachata buffer registration

```text
[Lib.VideoOut] <Info> sceVideoOutOpen: called
[Lib.VideoOut] <Info> sceVideoOutSetBufferAttribute: pixelFormat = A8R8G8B8Srgb, ... width = 1920, height = 1080, pitchInPixel = 1920
[Lib.VideoOut] <Info> RegisterBuffers: startIndex = 0, bufferNum = 3, ...
[Lib.VideoOut] <Info> RegisterBuffers: buffers[0] = 0x24de08000
[Lib.VideoOut] <Info> RegisterBuffers: buffers[1] = 0x24e600000
[Lib.VideoOut] <Info> RegisterBuffers: buffers[2] = 0x24edf8000
```

## First Divergence

The working Android port immediately progresses from registered buffers to frame preparation and presentation:

```text
[Render.Vulkan] <Info> PrepareFrame: guest=0x5e8e808000 guest_size=1920x1080 output_size=2560x1440 ...
[Lib.VideoOut] <Info> Android first game flip: elapsed_ms=7769 blank_frames=180 index=0 flip_arg=0 eop=true
[Lib.VideoOut] <Info> Flip presented: index=0 flip_arg=0 eop=true frame=0 size=2560x1440
```

It then presents subsequent buffers:

```text
[Render.Vulkan] <Info> PrepareFrame: guest=0x5e8f000000 ...
[Lib.VideoOut] <Info> Flip presented: index=1 flip_arg=1 eop=true frame=1 size=2560x1440
[Render.Vulkan] <Info> PrepareFrame: guest=0x5e8f7f8000 ...
[Lib.VideoOut] <Info> Flip presented: index=2 flip_arg=2 eop=true frame=2 size=2560x1440
```

Bachata contains no `PrepareFrame`, `Android first game flip`, or `Flip presented` entry after buffer registration. Its backend remains alive and reports Running, but no guest frame reaches the presenter.

## Missing `logo.tpf.dcx` Is Not the Cause

Both runtimes probe this optional path and receive the same missing-file result:

```text
[Kernel.Fs] <Error> Opening path /app0/dvdroot_ps4/menu/logo.tpf.dcx failed, file does not exist
```

The working port reports this only after it has already presented frames 0 through 4, then continues presenting. The path is not hardcoded in Bachata or shadPS4; it is supplied by Bloodborne's `Core.Res.CacheableFileLoader` guest thread. This lookup is non-fatal.

## Non-Causal Errors Shared or Tolerated

- `GetInstanceLayers: Failed to query layer properties: Success` also occurs in the working port.
- Missing PS4 system fonts are reported by both runs.
- Dummy/stubbed HLE calls and unresolved module lifecycle symbols do not stop game initialization.
- Missing `profile.bin` is an initial cache miss.
- Missing PulseAudio, ALSA, sndio, and JACK backends affect optional SDL audio probing; Bachata's audio bridge continues independently.

No Bachata log shows Vulkan device loss, `VK_ERROR`, native crash, fatal assertion, ANR, or backend exit during the captured window.

## Architecture Difference

The working APK is a native ARM64 Android shadPS4 build. It uses Bionic, `ANativeWindow`, AdrenoTools, and explicit Android VideoOut first-flip handling.

Bachata runs the x86-64 Linux shadPS4 binary through Box64, a glibc Vulkan loader, Turnip, and an embedded X11 server before output reaches Android's `SurfaceView`. The working APK's `Android first game flip` path is absent from Bachata's current source and runtime binary.

## Root-Cause Hypothesis

The highest-confidence hypothesis is a dropped or never-triggered flip between:

1. guest `sceVideoOutSubmitFlip` or the GNM EOP flip interrupt;
2. `VideoOutDriver::SubmitFlip` / `SubmitFlipInternal`;
3. `Vulkan::Presenter::PrepareFrame`.

The next diagnostic build should log entry and arguments at each boundary. A forced presentation immediately after `RegisterBuffers` is not recommended because buffers may be uninitialized and it bypasses guest flip synchronization.

## Recommended Fix Process

1. Instrument `sceVideoOutSubmitFlip`.
2. Instrument the GNM EOP callback that invokes `SubmitFlip`.
3. Instrument `SubmitFlip`, its GPU-thread command, and `SubmitFlipInternal`.
4. Instrument presenter queue consumption and `PrepareFrame`.
5. Reproduce once and identify the first missing transition.
6. Port or repair only that transition, then verify the first frame and continuing flips.
