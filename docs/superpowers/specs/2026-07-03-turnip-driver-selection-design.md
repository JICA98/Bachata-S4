# Turnip Driver Selection

## Goal

Run the newest compatible bundled Turnip by default while retaining the known-working 25.0.0 fallback and preserving RC11 for later native-renderer work.

## Drivers

Turnip 26.1.0 comes from the pinned Winlator app revision. Its archive SHA-256 is `9b4a10975456197e403c2b6a8a9781a8fd42ccf5048262a8cdea6538bb68d288`. It is an ARM64 glibc driver and therefore works with the proven host Box64/Vulkan path.

Turnip 25.0.0 remains separately packaged as the rollback driver. K11MCH1 Turnip 25.3.0 R11 remains checksum-locked and isolated under `drivers/turnip-25.3.0-r11`, but is not selectable: it is Android-Bionic and requires Vortek or another Vulkan IPC renderer to cross the glibc process boundary.

## Selection

Resolve driver choice before process launch through `RuntimeVulkanDriver`. Each selectable driver supplies its own ICD JSON while sharing the host glibc Vulkan loader. The selection remains immutable until session restart.

Initial UI launches use Turnip 26.1.0. The Intent extra provides the stable integration point for a later settings picker and per-game persistence.

## Testing

Packaging tests verify source hashes, isolated destinations, and ICD paths. Unit tests verify 26.1.0 and 25.0.0 environment selection. Acceptance requires a clean APK build, runtime verifier pass, Gate 7 Running/OK, sustained correctly scaled Sonic frames, and no device-lost/native crash on OnePlus Pad 2.
