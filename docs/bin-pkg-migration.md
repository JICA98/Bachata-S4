You are working directly in the `JICA98/Bachata-S4` repository.

## Objective

Replace the entire Arch Linux and Arch Linux ARM binary-package-based runtime pipeline with a Debian stable `main` multiarch pipeline.

The finished implementation must allow F-Droid to build `runtime.zip` on a clean Debian stable amd64 build machine using packages installed with:

```bash
apt-get install
```

The final runtime must continue to contain:

1. The x86_64 shadPS4 guest executable and all required x86_64 libraries.
2. The AArch64 Box64 host executable and all required AArch64 libraries.
3. Vulkan loader libraries for both amd64 and arm64.
4. The X11, XCB, SDL, keyboard, D-Bus, systemd, udev, UUID, zlib, DRM and C/C++ runtime libraries required by the existing runtime.
5. The existing Android-compatible ARM64 glibc modifications.
6. The deterministic `runtime.zip` and `manifest.json`.
7. The required JNI libraries under the existing Android output directory.

Do not merely provide a plan. Inspect the repository, modify the files, run the builds and tests, and provide the final results.

Do not ask for clarification. Make the safest reasonable implementation based on the existing repository.

---

# Non-negotiable requirements

The completed runtime build must not:

* Download any `.pkg.tar.zst` or `.pkg.tar.xz` files.
* Access `archive.archlinux.org`.
* Access an Arch Linux ARM mirror.
* use Pacman.
* depend on an Arch Linux filesystem layout.
* download a prebuilt `runtime.zip`.
* download executable libraries from GitHub Releases.
* extract executable libraries from Winlator’s prebuilt `rootfs.tzst`.
* commit `.deb` files or extracted Debian libraries into Git.
* use Ubuntu packages, PPAs, third-party apt repositories, Debian `contrib`, Debian `non-free`, Debian `sid`, or arbitrary binary download sites.
* suppress runtime binaries using broad F-Droid `scanignore` entries.
* silently remove the existing Android glibc compatibility patches.
* hardcode full library versions such as `libvulkan.so.1.4.350`, `libstdc++.so.6.0.35`, or `libsystemd.so.0.44.0`.

Only packages from the Debian stable `main` repository may supply prebuilt runtime libraries.

Use Debian multiarch packages:

```text
amd64
arm64
```

---

# Current implementation that must be replaced

Inspect at least these files before modifying anything:

```text
runtime/locks/runtime-inputs.lock.json
runtime/scripts/fetch-runtime-inputs.mjs
runtime/scripts/package-runtime.mjs
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
runtime/tests/
runtime/sources/winlator-app/app/src/main/assets/rootfs.tzst
```

The current lock file contains approximately 30 Arch Linux or Arch Linux ARM package archives. The package script extracts them using Arch paths such as:

```text
usr/lib/libc.so.6
usr/lib/libstdc++.so.6.0.35
usr/lib/libvulkan.so.1.4.350
```

Debian uses multiarch paths such as:

```text
/lib/x86_64-linux-gnu/
/usr/lib/x86_64-linux-gnu/
/lib/aarch64-linux-gnu/
/usr/lib/aarch64-linux-gnu/
```

Refactor the runtime builder to resolve Debian library paths dynamically.

The current script also extracts ARM64 glibc, libgcc and many ARM64 X11/XCB libraries from Winlator’s `rootfs.tzst`. This is also a prebuilt executable archive and must no longer be used as a runtime-library source.

Winlator source code and source patches may remain where required, but no executable ELF file from `rootfs.tzst` may enter the new runtime.

---

# Required Arch-to-Debian package mapping

Use this mapping as the minimum conversion baseline.

| Existing Arch input                                   | Debian package                                           |
| ----------------------------------------------------- | -------------------------------------------------------- |
| `glibc`                                               | `libc6`                                                  |
| `libstdc++`                                           | `libstdc++6`                                             |
| `libgcc`                                              | `libgcc-s1`                                              |
| `ca-certificates-mozilla` and downloaded `cacert.pem` | `ca-certificates`                                        |
| `sdl2`                                                | `libsdl2-2.0-0`                                          |
| SDL headers/linker files                              | `libsdl2-dev`                                            |
| `libx11`                                              | `libx11-6` and `libx11-xcb1`                             |
| `libxext`                                             | `libxext6`                                               |
| `libxcursor`                                          | `libxcursor1`                                            |
| `libxrandr`                                           | `libxrandr2`                                             |
| `libxrender`                                          | `libxrender1`                                            |
| `libxfixes`                                           | `libxfixes3`                                             |
| `libxi`                                               | `libxi6`                                                 |
| `libxss`                                              | `libxss1`                                                |
| `libxcb`                                              | `libxcb1` plus the required split XCB extension packages |
| `libxau`                                              | `libxau6`                                                |
| `libxdmcp`                                            | `libxdmcp6`                                              |
| `libxkbcommon`                                        | `libxkbcommon0`                                          |
| XKB configuration data                                | `xkb-data`                                               |
| `dbus`                                                | `libdbus-1-3`                                            |
| `systemd-libs` containing `libsystemd`                | `libsystemd0`                                            |
| `systemd-libs` containing `libudev`                   | `libudev1`                                               |
| `util-linux-libs` containing UUID                     | `libuuid1`                                               |
| `vulkan-icd-loader`                                   | `libvulkan1`                                             |
| Vulkan development linker file                        | `libvulkan-dev`                                          |
| `zlib`                                                | `zlib1g`                                                 |
| `libdrm`                                              | `libdrm2`                                                |

Confirm every package name against Debian stable using `apt-cache show` or `apt-cache policy` before finalizing the installation script. Where Debian has renamed a package, use the Debian stable replacement and document it.

---

# Required Debian package installation script

Create:

```text
runtime/scripts/install-debian-runtime-deps.sh
```

The script must:

1. Require root privileges.
2. Be idempotent.
3. Add the `arm64` foreign architecture.
4. Use only the build machine’s Debian stable `main` sources.
5. Install packages with `--no-install-recommends`.
6. Set `DEBIAN_FRONTEND=noninteractive`.
7. Verify that every requested package was installed.
8. Print the installed package version and architecture.
9. Fail if the operating system is not Debian, unless a clearly documented override is supplied for CI testing.
10. Fail if a required package comes from an unauthorized repository.

Start with:

```bash
#!/usr/bin/env bash
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

test "$(id -u)" -eq 0 || {
    echo "This script must run as root" >&2
    exit 1
}

dpkg --add-architecture arm64
apt-get update
```

## Build tools

At minimum, install and verify:

```text
ca-certificates
git
nodejs
cmake
ninja-build
clang
llvm
gcc
g++
gcc-aarch64-linux-gnu
g++-aarch64-linux-gnu
binutils
binutils-aarch64-linux-gnu
pkg-config
file
patchelf
pax-utils
python3
python3-pyelftools
dpkg-dev
xz-utils
zstd
zip
unzip
```

The existing source builders require:

```text
cmake
ninja
clang
clang++
llvm-ar
llvm-ranlib
readelf
aarch64-linux-gnu-gcc
aarch64-linux-gnu-g++
```

Do not omit these.

## Build-time development packages

Install at least:

```text
libc6-dev:amd64
libsdl2-dev:amd64
libvulkan-dev:amd64
```

Only add more `-dev` packages when a source compilation or probe actually requires them.

## amd64 runtime seed packages

Install at least:

```text
libc6:amd64
libgcc-s1:amd64
libstdc++6:amd64
libsdl2-2.0-0:amd64
libvulkan1:amd64
libudev1:amd64
libuuid1:amd64
libx11-6:amd64
libx11-xcb1:amd64
libxcursor1:amd64
libxext6:amd64
libxfixes3:amd64
libxi6:amd64
libxrandr2:amd64
libxrender1:amd64
libxss1:amd64
libxcb1:amd64
libxcb-dri3-0:amd64
libxcb-present0:amd64
libxcb-randr0:amd64
libxcb-render0:amd64
libxcb-shm0:amd64
libxcb-sync1:amd64
libxau6:amd64
libxdmcp6:amd64
libxkbcommon0:amd64
libdbus-1-3:amd64
libsystemd0:amd64
zlib1g:amd64
libdrm2:amd64
```

## arm64 runtime seed packages

Install at least:

```text
libc6:arm64
libgcc-s1:arm64
libstdc++6:arm64
libvulkan1:arm64
libudev1:arm64
libuuid1:arm64
libx11-6:arm64
libx11-xcb1:arm64
libxcursor1:arm64
libxext6:arm64
libxfixes3:arm64
libxi6:arm64
libxrandr2:arm64
libxrender1:arm64
libxss1:arm64
libxcb1:arm64
libxcb-dri3-0:arm64
libxcb-present0:arm64
libxcb-randr0:arm64
libxcb-render0:arm64
libxcb-shm0:arm64
libxcb-sync1:arm64
libxau6:arm64
libxdmcp6:arm64
libxkbcommon0:arm64
libdbus-1-3:arm64
libsystemd0:arm64
zlib1g:arm64
libdrm2:arm64
```

Also install:

```text
ca-certificates
xkb-data
```

These package arrays are seed packages, not permission to ignore transitive dependencies. The finished packager must compute and copy the complete ELF dependency closure.

If Debian stable uses a renamed package, update the package list after verifying the replacement.

---

# Replace the Arch lock file

Remove the list of Arch package URLs and hashes from:

```text
runtime/locks/runtime-inputs.lock.json
```

Replace it with a Debian package specification such as:

```text
runtime/locks/debian-runtime-packages.json
```

Use an appropriate schema, for example:

```json
{
  "schemaVersion": 1,
  "distribution": "debian",
  "suite": "stable",
  "component": "main",
  "architectures": {
    "amd64": [
      "libc6",
      "libgcc-s1",
      "libstdc++6"
    ],
    "arm64": [
      "libc6",
      "libgcc-s1",
      "libstdc++6"
    ]
  },
  "architectureIndependent": [
    "ca-certificates",
    "xkb-data"
  ]
}
```

Do not place changing version numbers in source code paths.

At build time, generate a package provenance manifest containing:

```text
binary package name
source package name
installed version
architecture
Debian suite
Debian component
files copied from the package
```

Include this provenance in the generated runtime manifest.

The generated manifest should make it obvious that every packaged system ELF came from Debian `main`.

---

# Replace `fetch-runtime-inputs.mjs`

The new runtime must not need a generic HTTP binary downloader.

Either:

1. Delete `runtime/scripts/fetch-runtime-inputs.mjs`, or
2. Restrict it exclusively to non-executable, source-like inputs that cannot be provided by Debian.

It must not fetch:

```text
ELF executables
shared libraries
glibc
Vulkan loaders
SDL
X11
XCB
D-Bus
systemd
zlib
libdrm
CA bundles
runtime.zip
```

CA certificates must come from Debian’s installed `ca-certificates` package.

---

# Stage Debian files safely

Create a new staging implementation, preferably:

```text
runtime/scripts/stage-debian-runtime.mjs
```

or a well-structured equivalent.

It must locate Debian package files through:

```text
dpkg-query
dpkg-query -L
dpkg-query -S
readlink
realpath
```

Use Debian multiarch paths:

```text
/lib/x86_64-linux-gnu
/usr/lib/x86_64-linux-gnu
/lib64

/lib/aarch64-linux-gnu
/usr/lib/aarch64-linux-gnu
```

Account for modern Debian’s merged `/usr` filesystem, where `/lib` may be a symlink into `/usr/lib`.

Do not assume that the actual file lives under only one of these paths.

Preserve symlink chains where appropriate.

For example, do not hardcode:

```text
libvulkan.so.1.4.350
```

Resolve:

```text
libvulkan.so.1
```

and copy its actual target dynamically.

Do the same for:

```text
libstdc++.so.6
libsystemd.so.0
libudev.so.1
libdbus-1.so.3
libSDL2-2.0.so.0
libXss.so.1
libxkbcommon.so.0
libz.so.1
libdrm.so.2
```

When the runtime requires an unversioned alias such as:

```text
libvulkan.so
libstdc++.so
libz.so
libdrm.so
libX11.so
```

create a relative symlink inside the runtime staging directory pointing to the copied SONAME library. Do not copy a linker script as though it were a runtime ELF library.

---

# Required guest amd64 runtime

The guest root must preserve the existing output layout:

```text
rootfs/lib64/ld-linux-x86-64.so.2
rootfs/lib/x86_64-linux-gnu/
rootfs/bin/shadps4
rootfs/bin/hello
rootfs/bin/sdl-window
rootfs/bin/audio-tone
rootfs/bin/vulkan-info
```

At minimum, ensure the guest runtime can resolve these SONAMEs where required:

```text
ld-linux-x86-64.so.2
libc.so.6
libdl.so.2
libm.so.6
libpthread.so.0
libresolv.so.2
libgcc_s.so.1
libstdc++.so.6
libudev.so.1
libuuid.so.1
libSDL2-2.0.so.0
libvulkan.so.1
libX11.so.6
libX11-xcb.so.1
libXcursor.so.1
libXext.so.6
libXfixes.so.3
libXi.so.6
libXrandr.so.2
libXrender.so.1
libXss.so.1
libxcb.so.1
libXau.so.6
libXdmcp.so.6
libxkbcommon.so.0
```

The actual shadPS4 `DT_NEEDED` list is authoritative. Do not rely only on this static list.

---

# Required host arm64 runtime

The host root must preserve the existing layout:

```text
rootfs/host/ld-linux-aarch64.so.1
rootfs/host/box64
rootfs/host/
```

At minimum, ensure the host runtime contains or can resolve:

```text
ld-linux-aarch64.so.1
libc.so.6
libdl.so.2
libm.so.6
libpthread.so.0
libresolv.so.2
libgcc_s.so.1
libstdc++.so.6
libvulkan.so.1
libz.so.1
libdrm.so.2
libdbus-1.so.3
libsystemd.so.0
libudev.so.1
libuuid.so.1
libX11.so.6
libX11-xcb.so.1
libXcursor.so.1
libXext.so.6
libXfixes.so.3
libXi.so.6
libXrandr.so.2
libXrender.so.1
libXss.so.1
libxcb.so.1
libxcb-dri3.so.0
libxcb-present.so.0
libxcb-randr.so.0
libxcb-render.so.0
libxcb-shm.so.0
libxcb-sync.so.1
libXau.so.6
libXdmcp.so.6
libxkbcommon.so.0
```

This replaces all equivalent executable files currently extracted from:

```text
runtime/sources/winlator-app/app/src/main/assets/rootfs.tzst
```

Do not use that archive as a fallback.

---

# Recursive ELF dependency closure

A fixed package list alone is not sufficient because Debian may split packages differently or compile libraries with different dependencies.

Implement a recursive dependency resolver separately for amd64 and arm64.

For every packaged ELF root:

1. Read its ELF architecture.
2. Read its `PT_INTERP`, if present.
3. Read every `DT_NEEDED` entry.
4. Resolve each SONAME from the correct Debian multiarch paths.
5. Copy the resolved library.
6. Repeat until no unresolved dependencies remain.
7. Record the Debian package owning every copied file.
8. Fail on an unresolved dependency.
9. Fail on an architecture mismatch.
10. Fail if a copied ELF is not owned by an approved Debian package or built from source during this build.

Use `readelf`, `objdump`, `lddtree`, or `pyelftools`. Do not execute an amd64 or arm64 target binary merely to discover its dependencies.

Ignore only legitimate pseudo-objects such as:

```text
linux-vdso.so.1
```

Do not blindly copy all files from `/usr/lib`.

Copy only:

* explicitly required runtime files,
* recursive ELF dependencies,
* documented dynamically loaded libraries,
* required non-ELF runtime data.

Because some libraries use `dlopen`, retain the existing explicit X11, XCB, SDL, Vulkan and keyboard libraries even if they are not visible in a direct `DT_NEEDED` list.

---

# Non-ELF runtime data

Copy the Debian CA bundle from:

```text
/etc/ssl/certs/ca-certificates.crt
```

to the existing runtime location:

```text
rootfs/etc/ssl/certs/ca-certificates.crt
```

Do not download `cacert.pem` separately.

Investigate whether the runtime needs:

```text
/usr/share/X11/xkb
```

If keyboard handling requires it, copy the required data from the Debian `xkb-data` package and record its provenance.

Do not copy Debian documentation, locales, man pages, package metadata, static libraries or headers into `runtime.zip` unless the application demonstrably requires them.

---

# Preserve Android glibc compatibility

The current package builder modifies the ARM64 loader and libc to disable or adapt unsupported Android/Linux syscall behaviour, including handling around:

```text
set_robust_list
clone3
faccessat2
```

Inspect the existing functions such as:

```text
disableArm64SetRobustList
disableArm64Clone3
disableArm64Faccessat2
```

Preserve their intended behaviour.

The Debian ARM64 glibc version will differ from the previous Arch ARM64 glibc version. Therefore:

1. Do not rely on fixed full library version names.
2. Validate all binary patch patterns against Debian’s `libc.so.6` and loader.
3. Require the expected number of successful patches.
4. Fail loudly if a pattern is not found or if too many occurrences are found.
5. Verify the patched files remain valid AArch64 ELF objects.
6. Record the original and patched SHA-256 hashes.
7. Add tests for every applied compatibility patch.

Do not “fix” a failed Debian build by removing these patches.

If the existing byte-pattern patching is too version-dependent, replace it with a more robust symbol-aware or instruction-aware patcher using Debian-packaged tools. Preserve the same runtime behaviour.

---

# Refactor `package-runtime.mjs`

Refactor the script so that it:

* does not open Arch package archives;
* does not invoke `tar --zstd` for Arch packages;
* does not search for package names by Arch filename prefixes;
* does not extract executable libraries from Winlator `rootfs.tzst`;
* receives staged Debian sysroot directories or resolves installed Debian files;
* uses SONAMEs rather than exact full versions;
* preserves the existing output paths expected by the Android application;
* preserves deterministic file ordering;
* preserves deterministic timestamps and permissions;
* continues to generate `runtime.zip`;
* continues to generate `manifest.json`;
* records Debian package provenance;
* records source-built component revisions;
* records the ARM64 glibc patch provenance;
* fails when any required file is missing.

The ZIP output must remain deterministic.

---

# Add one-shot build entry point

Create:

```text
runtime/scripts/build-runtime-debian.sh
```

It must assume the apt dependencies have already been installed and then run, in the correct order:

```text
source revision verification
shadPS4 x86_64 source build
Box64 AArch64 source build
Debian runtime staging
runtime packaging
runtime manifest generation
runtime validation
determinism checks where practical
Android JNI library installation
```

Example structure:

```bash
#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$project_root"

runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
node runtime/scripts/stage-debian-runtime.mjs
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs
```

Adapt the actual command names to the implementation.

---

# Required validation tests

Add or update tests to enforce all of the following.

## No Arch dependencies

Fail if any tracked runtime build file contains:

```text
archive.archlinux.org
archlinuxarm
.pkg.tar.zst
.pkg.tar.xz
pacman
```

Allow references in historical documentation only when clearly marked as removed legacy behaviour.

## No Winlator binary rootfs extraction

Fail if the runtime package pipeline extracts an executable or shared library from:

```text
rootfs.tzst
```

## ELF architecture validation

For every ELF in the generated runtime:

* Files under `lib/x86_64-linux-gnu`, `lib64`, and guest `bin` must be x86-64 unless explicitly documented otherwise.
* Files under `host` and ARM64 JNI outputs must be AArch64.
* No x86-64 library may appear in the host directory.
* No AArch64 library may appear in the guest library directory.

## Dependency validation

For every ELF:

* Parse `DT_NEEDED`.
* Confirm every required SONAME exists in the correct runtime search path.
* Confirm the ELF interpreter exists.
* Fail on any unresolved dependency.
* Print a readable dependency tree when validation fails.

Validate at least:

```text
shadps4
box64
sdl-window
vulkan-info
libSDL2-2.0.so.0
libvulkan.so.1 for amd64
libvulkan.so.1 for arm64
libX11.so.6 for amd64
libX11.so.6 for arm64
libxkbcommon.so.0 for arm64
libdbus-1.so.3 for arm64
libsystemd.so.0 for arm64
```

## Debian provenance validation

For every system-supplied ELF, the test must identify:

```text
Debian binary package
Debian source package
package version
architecture
```

Fail if ownership cannot be determined.

Source-built executables such as shadPS4 and Box64 must instead be tied to their pinned Git revisions.

## Required runtime paths

Generate a checked required-path manifest and verify it.

At minimum, verify:

```text
bin/shadps4
host/box64
lib64/ld-linux-x86-64.so.2
host/ld-linux-aarch64.so.1
lib/x86_64-linux-gnu/libc.so.6
host/libc.so.6
lib/x86_64-linux-gnu/libstdc++.so.6
host/libstdc++.so.6
lib/x86_64-linux-gnu/libvulkan.so.1
host/libvulkan.so.1
lib/x86_64-linux-gnu/libSDL2-2.0.so.0
lib/x86_64-linux-gnu/libX11.so.6
host/libX11.so.6
host/libxkbcommon.so.0
host/libXss.so.1
host/libdbus-1.so.3
host/libsystemd.so.0
etc/ssl/certs/ca-certificates.crt
```

Use SONAME paths and stable aliases rather than exact Debian implementation version filenames.

## Determinism

Perform two clean runtime builds with the same source tree and Debian package set.

Verify:

```bash
sha256sum runtime.zip
sha256sum manifest.json
```

Both builds must produce identical hashes.

If the build environment itself prevents full reproducibility, identify the exact nondeterministic files and fix their timestamps, ordering, permissions or embedded build paths.

---

# Clean Debian build test

Test the full pipeline in a clean Debian stable amd64 environment, preferably a container or equivalent isolated environment.

The test sequence must be equivalent to:

```bash
runtime/scripts/install-debian-runtime-deps.sh
runtime/scripts/build-runtime-debian.sh
```

The build must not depend on packages already present on the developer’s machine.

Verify that the generated archive is nonempty and list its contents:

```bash
unzip -l runtime.zip
```

Run all repository runtime tests.

Then build the Android F-Droid flavour using the newly generated runtime assets.

Do not claim that an Android device launch was tested unless an actual compatible Android device was available.

If `adb` and a compatible device are available, additionally test:

```text
runtime extraction
Box64 startup
x86_64 hello probe
SDL/X11 probe
Vulkan loader startup
shadPS4 process startup
```

---

# F-Droid integration

Provide an exact F-Droid `sudo:` package-installation block based on the final tested package list.

It should follow this shape:

```yaml
sudo:
  - dpkg --add-architecture arm64
  - apt-get update
  - apt-get install -y --no-install-recommends
      ca-certificates
      cmake
      ninja-build
      clang
      llvm
      gcc
      g++
      gcc-aarch64-linux-gnu
      g++-aarch64-linux-gnu
      nodejs
      file
      patchelf
      pax-utils
      python3
      python3-pyelftools
      libc6:amd64
      libc6:arm64
      ...
```

Provide the complete tested list. Do not leave `...` in the final result.

Provide the corresponding build command, for example:

```yaml
prebuild:
  - cd ../../.. && runtime/scripts/build-runtime-debian.sh
```

The F-Droid recipe must not download a prebuilt `runtime.zip`.

The generated runtime should be built before Gradle packages the APK.

---

# Documentation

Update the runtime documentation to explain:

1. Why Arch binary packages were removed.
2. Which Debian stable packages supply the runtime.
3. How multiarch installation works.
4. How to perform a clean local Debian build.
5. How to verify package provenance.
6. How to update the Debian package list safely.
7. Why exact `.so.X.Y.Z` filenames must not be hardcoded.
8. Why `rootfs.tzst` is no longer an acceptable executable-library source.
9. How to reproduce the F-Droid build locally.
10. Which parts are source-built and which parts come from Debian `main`.

---

# Completion criteria

The task is complete only when all of the following are true:

* No Arch package URL remains in the active runtime build pipeline.
* No Arch package archive is downloaded or extracted.
* No runtime ELF is extracted from Winlator `rootfs.tzst`.
* All system runtime libraries come from installed Debian stable `main` packages.
* shadPS4 is built from its pinned source.
* Box64 is built from its pinned source.
* Both amd64 and arm64 runtime dependencies are complete.
* ARM64 glibc compatibility patches remain enabled and tested.
* `runtime.zip` is successfully generated.
* `manifest.json` is successfully generated.
* The manifest records Debian package provenance.
* Every ELF has the correct architecture.
* Every ELF dependency resolves inside the packaged runtime.
* The Android F-Droid flavour builds using the generated runtime.
* Two clean equivalent builds produce identical runtime hashes.
* The repository contains no committed `.deb` files or extracted Debian binaries.
* The final report includes the exact apt package list and commands that were tested.

At completion, provide:

1. A summary of the implementation.
2. A list of every file added, changed or deleted.
3. The final exact Debian package list.
4. The final F-Droid YAML installation/build snippet.
5. The commands executed.
6. Test output summaries.
7. The generated `runtime.zip` SHA-256.
8. The generated `manifest.json` SHA-256.
9. Any tests that could not be performed and the exact reason.
10. Any remaining technical risk, especially around Debian ARM64 glibc binary patching.

Do not stop after replacing the lock file. The task is only complete when a clean Debian build produces and validates the complete runtime.
