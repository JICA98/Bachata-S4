# Play Store bundled Turnip 26.1.0

## Why

Google Play policy disallows downloading executable native code (`.so` / driver
ZIPs) after install. The `playstore` product flavor therefore ships **Turnip
26.1.0** inside the app and disables remote driver catalogue, download, and
ZIP import.

The `fdroid` flavor keeps GitHub Releases download + local ZIP import.

## Flavor

| Flavor | Driver sources |
|--------|----------------|
| `playstore` | Bundled Turnip 26.1.0 + system Vulkan only |
| `fdroid` | System + download from `JICA98/bachata-s4-drivers` + import ZIP |

Gradle: `android/BachataS4/app/build.gradle.kts` (`productFlavors.playstore` / `fdroid`).

## Package location

| Item | Path |
|------|------|
| ZIP asset | `android/BachataS4/app/src/playstore/assets/drivers/turnip-26.1.0-EMULATOR.zip` |
| SHA-256 sidecar | `.../turnip-26.1.0-EMULATOR.zip.sha256` |
| Licence notice | `android/BachataS4/app/src/playstore/assets/licenses/mesa-turnip-26.1.0-NOTICE.txt` |
| Spec constants | `BundledTurnipSpec` in `core:runtime` |

Asset path at runtime: `drivers/turnip-26.1.0-EMULATOR.zip`.

Format matches `TurnipPackageInstaller` (flat ZIP with `meta.json` +
`libvulkan_freedreno.so`).

## Source & licence

- Upstream package: `Turnip-mojo-26.1-v2-08e7443-EMULATOR.zip` from
  [bachata-s4-drivers](https://github.com/JICA98/bachata-s4-drivers)
- Mesa source: `whitebelyash/mesa-unified` branch `mojo/26.1` commit `08e7443…`
- Binary licence: MIT (Mesa). Notice file ships in Play assets.

## Behaviour

### Play (`PlaystoreDriverManagerBackend`)

1. Setup **skips** the Turnip selection screen (`SHOW_DRIVER_SELECTION=false`).
   Continue goes straight to the library after auto-selecting bundled Turnip.
2. On continue / launch, extract ZIP into app-private
   `files/vulkan-drivers/installed/` via `BundledTurnipInstaller`.
3. Verify SHA-256 before install; atomic staging through existing installer.
4. Marker file `.bundled-turnip-version` records the bundled version token.
5. Settings has **no Drivers tab**; session has no “Open Turnip drivers” action.
6. Launch always uses the bundled package (system Vulkan is not offered).
7. Profile `driverId` is set to the bundled install id on first setup continue.

### Non-Play (`FdroidDriverManagerBackend`)

Unchanged: `TurnipReleaseClient` + `TurnipDownloadManager` + import ZIP.

## Updating the bundled driver

1. Obtain a new `*-EMULATOR.zip` from bachata-s4-drivers (same package format).
2. Replace `app/src/playstore/assets/drivers/turnip-26.1.0-EMULATOR.zip`
   (rename file if the version label changes).
3. `sha256sum` the ZIP → update `.sha256` sidecar and `BundledTurnipSpec.SHA256`.
4. Update `VERSION_MARKER`, `ASSET_NAME`, `RELEASE_TAG`, display label, and the
   NOTICE file (commit / branch).
5. Run unit tests + Play release packaging inspection (below).

## Intentionally disabled on Play

- GitHub release catalogue HTTP
- Driver archive HTTP download
- User ZIP import
- Deleting the bundled driver
- Selecting previously downloaded custom drivers

## Migration for existing Play users

- App data (games, settings) kept.
- Old files under `vulkan-drivers/installed/` are not deleted, but are not
  listed or loaded.
- Profiles pointing at old driver ids select the new bundled package.

## Tests & verification

```bash
cd android/BachataS4
./gradlew :core:runtime:test :feature:drivers:test :app:testPlaystoreDebugUnitTest
./gradlew :app:assemblePlaystoreRelease :app:assembleFdroidRelease

# Play AAB/APK must contain the bundled ZIP; fdroid must not
unzip -l app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab \
  | grep 'assets/drivers/turnip-26.1.0-EMULATOR.zip'
! unzip -l app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk \
  | grep 'assets/drivers/turnip'

# Managed runtime still must not embed Turnip (separate from Play driver asset)
node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs
node runtime/tests/verify-playstore-bundled-turnip.mjs \
  android/BachataS4/app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab
```
