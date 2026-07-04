# Android Runtime Third-Party Notice

## Winlator

- Upstream: https://github.com/brunodev85/winlator-app.git
- Revision: `e113da42beefc39c69c8944b27c19c3703bfa856`
- License: LGPL-2.1
- Copied paths: `app/src/main/java/com/winlator/{xserver,alsaserver,core,math,renderer,sysvshm,xconnector}` and `app/src/main/cpp/winlator`
- Local destination: `android/BachataS4/core/runtime/src/main/{java/com/winlator,cpp/winlator}`
- Modifications: source selection only; copied files remain byte-identical to the pinned revision. Bachata S4 integration lives outside `com.winlator`.

`runtime/locks/winlator-vendor.sha256` records every copied upstream path, local path, and SHA-256. Wine UI, installers, assets, and bundled binaries are excluded.

## Runtime Components

- shadPS4 backend: GPL-2.0-or-later; corresponding source is this repository, including Bachata runtime changes.
- Box64: MIT, pinned revision recorded in `runtime/locks/components.lock.json`; local compatibility patches are under `runtime/patches`.
- GNU glibc: LGPL-2.1-or-later. Unmodified locked packages are listed in `runtime/locks/runtime-inputs.lock.json`; package-time Android seccomp compatibility edits are reproducible in `runtime/scripts/package-runtime.mjs`.
- Mesa/Turnip and Vulkan loader: MIT-family licenses; revisions and package hashes are recorded in runtime locks.
- SDL2, X11 libraries, libudev, libuuid, libstdc++, libgcc, zlib, libdrm, and CA certificates: redistributed under their respective upstream licenses with exact package hashes in runtime locks.

For GPL/LGPL components, corresponding source and build scripts are provided in this repository. A written source offer is available with distributed binaries for at least three years where required by the applicable license.
