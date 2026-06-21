# Android Runtime Third-Party Notice

## Winlator

- Upstream: https://github.com/brunodev85/winlator-app.git
- Revision: `e113da42beefc39c69c8944b27c19c3703bfa856`
- License: LGPL-2.1
- Copied paths: `app/src/main/java/com/winlator/{xserver,alsaserver,core,math,renderer,sysvshm,xconnector}` and `app/src/main/cpp/winlator`
- Local destination: `android/BachataS4/core/runtime/src/main/{java/com/winlator,cpp/winlator}`
- Modifications: source selection only; copied files remain byte-identical to the pinned revision. Bachata S4 integration lives outside `com.winlator`.

`runtime/locks/winlator-vendor.sha256` records every copied upstream path, local path, and SHA-256. Wine UI, installers, assets, and bundled binaries are excluded.
