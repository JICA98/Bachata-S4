# FEX Phase 0 Clean-History Design

## Goal

Create a durable, auditable FEX Phase 0 branch without losing the proven work
on `feature/fex-arm64-sonic`.

## Current Evidence

`feature/fex-arm64-sonic` currently contains the complete physical proof at
`c010d096c57a5de12f930d7fdf65a1b4c9404df9`.  Its FEX work includes the pinned
FEXCore smoke build, Debian runtime packaging, Android native loader path,
seven-check smoke test, and sanitized SM8650 evidence.  The branch remains
unchanged and is the recovery source for this migration.

## Problem

The Phase 0 plan compares Box64 paths and whitespace against the old
`dbe5165b` baseline.  The current branch subsequently merged Debian main at
`69d8ae3b`, whose main parent is
`5623902477eacbe36b30266e673652c14de32fa8`, and also contains a later Box64
checkout-lock change.  That makes the old comparison range include accepted
main history and unrelated Box64 work, so it cannot prove that the FEX work
itself left Box64 unchanged.

## Decision

Create `feature/fex-arm64-phase0-clean` in a new linked worktree.  Its immutable
base is `5623902477eacbe36b30266e673652c14de32fa8`, the exact Debian-main tree
accepted when FEX work was resumed.  The clean branch will reproduce only the
FEX Phase 0 tree and omit the post-merge Box64 checkout-lock change and other
unrelated history.

The existing `feature/fex-arm64-sonic` branch will not be rebased, reset,
deleted, force-pushed, or otherwise changed as part of this migration.

## Reconstruction Contract

1. Record the legacy proof commit and base commit before creating the new
   worktree.
2. Reconstruct the FEX tree from the legacy branch, including pre-merge FEX
   commits, merge-resolution content required on the Debian base, and FEX-only
   post-merge commits.  Do not copy unrelated Box64, F-Droid, UI, settings, or
   generated-build changes.
3. Compare an explicit FEX path manifest between the legacy proof tree and the
   clean candidate.  Every FEX source, runtime, Android launcher/test, and
   qualification artifact must be byte-identical before requalification.
4. Require `git diff --exit-code <clean-base> -- runtime/scripts/build-box64-host.sh runtime/patches/box64-*.patch` to succeed.  This is not a rebaseline
   that accepts FEX-side Box64 changes: the selected base already contains only
   the accepted Debian-main Box64 state.
5. Require `git diff --check <clean-base>..HEAD` to succeed.  Historical
   whitespace outside this range is handled separately on its owning branch.
6. Rebuild, package, validate the Playstore APK, and rerun the replacement-only
   tablet qualification.  Generate new evidence on the clean branch so its
   `source.projectRevision`, APK hash, runner hash, and sanitized logs match
   the clean commit IDs.

## Safety and Scope

- Keep FEX pinned at `f2b679f6028ce1c38875233aecfcf5d3f8ebecec`.
- Preserve the native ARM64 host plus FEXCore x86-64 guest architecture; do not
  substitute Box64.
- Do not modify GameNative, game/save data, managed runtime data, or the three
  protected dirty submodules in the legacy worktree.
- Use only `adb install -r` and `adb install -r -t`; never uninstall or clear
  app data.
- Do not begin CpuBackend, Sonic, Bloodborne, backend-selection, or performance
  work until the clean Phase 0 gate has passed.

## Acceptance

The clean branch is ready for Phase 1 only when it retains every FEX Phase 0
artifact and test, has no Box64 source/build diff from the clean base, passes
its range-scoped whitespace audit, passes focused and CTest regression gates,
and has fresh validated SM8650 evidence tied to its own source revision.
