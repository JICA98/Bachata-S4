# Custom Turnip Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import and launch an ARM64 glibc Turnip ZIP selected from Settings.

**Architecture:** A runtime-owned installer validates and atomically stores one custom driver. Existing preference and launch paths gain a `CUSTOM` selection whose ICD points to app-private storage.

**Tech Stack:** Kotlin, Java ZIP APIs, kotlinx.serialization, Android SAF, Jetpack Compose, JUnit 4.

## Global Constraints

- Work only in `.worktrees/android-emulator`.
- Accept only `linux-aarch64-glibc` packages.
- Limit expanded archive content to 64 MiB.
- Preserve bundled driver behavior.

---

### Task 1: Secure installer

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/CustomVulkanDriverInstaller.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/CustomVulkanDriverInstallerTest.kt`

**Interfaces:**
- Produces: `CustomVulkanDriverInstaller(root).install(InputStream)` and `load()`.

- [ ] Write tests for valid import, Bionic rejection, and traversal rejection.
- [ ] Run tests and verify failure.
- [ ] Implement metadata, ELF, size, path, and atomic-install validation.
- [ ] Run tests and verify pass.

### Task 2: Runtime selection

**Files:**
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/VulkanDriverConfiguration.kt`
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/VulkanDriverSelectionTest.kt`

**Interfaces:**
- Consumes: installed custom ICD path.
- Produces: host-glibc launch environment for `CUSTOM`.

- [ ] Write failing custom-selection test.
- [ ] Add enum selection and configuration path.
- [ ] Pass app-private custom directory from service.
- [ ] Run runtime tests.

### Task 3: Settings import UI

**Files:**
- Modify: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: Android SAF URI stream and installer.
- Produces: custom display state and persisted `CUSTOM` selection.

- [ ] Add OpenDocument launcher and background import.
- [ ] Show custom name and validation result.
- [ ] Build app and run full unit tests.

