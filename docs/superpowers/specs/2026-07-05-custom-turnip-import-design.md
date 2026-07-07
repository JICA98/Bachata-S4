# Custom Turnip Import Design

## Goal

Import an ARM64 glibc Turnip ZIP from Settings and use it for subsequent game launches.

## Package and security

Supported ZIPs contain `meta.json` and the library named by `libraryName`. Metadata must declare `abi` as `linux-aarch64-glibc`. The library must be an AArch64 ELF linked against glibc. Import rejects duplicate entries, traversal paths, unexpected nested paths, archives over 64 MiB uncompressed, malformed metadata, and Bionic libraries.

## Storage and selection

One custom driver is installed atomically under app-private `files/vulkan-drivers/custom`. A successful import replaces the previous custom driver and selects `CUSTOM` in existing preferences. Bundled selections remain available.

## Launch

The installer generates an ICD manifest with an absolute app-private library path. Custom selection uses the existing host glibc Vulkan loader and passes that manifest through `VK_ICD_FILENAMES`. Active sessions remain immutable.

## UI and errors

Settings adds an Android document picker restricted to ZIP files, shows imported driver name, and displays concise validation errors. Import runs off the main thread.

