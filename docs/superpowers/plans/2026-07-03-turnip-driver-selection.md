# Turnip Driver Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Select Turnip 26.1.0 by default with Turnip 25.0.0 fallback.

**Architecture:** Package each glibc driver separately and choose its ICD before launching the existing host Box64 process. Keep RC11 isolated until a Vulkan IPC renderer is integrated.

## Tasks

- [x] Lock and validate RC11 without installing it over the glibc driver.
- [x] Validate pinned Winlator Turnip 26.1.0 archive hash.
- [x] Package 25.0.0 and 26.1.0 under separate driver directories.
- [x] Generate version-specific ICD files.
- [x] Add immutable session driver enum and Intent extra.
- [x] Default library and Gate 7 launches to 26.1.0.
- [x] Preserve explicit 25.0.0 fallback configuration.
- [x] Run packaging tests, runtime verifier, unit tests, and clean APK build.
- [x] Install on OnePlus Pad 2 and verify Gate 7 sustained rendering.
- [ ] Verify fallback launch and commit implementation.
