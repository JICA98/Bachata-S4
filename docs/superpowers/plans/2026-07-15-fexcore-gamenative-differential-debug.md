# FEXCore GameNative Differential Debug Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Identify the first concrete incompatibility that prevents the ARM64 FEXCore smoke from executing on Android, then implement and tablet-validate the smallest reusable fix.

**Architecture:** Treat GameNative as read-only evidence, separating its generic FEXCore/Bionic setup from Wine/ARM64EC behavior. Preserve the current ARM64 shadPS4 host architecture and use the physical tablet smoke gate as the acceptance test.

**Tech Stack:** Android/Kotlin, native AArch64, pinned FEXCore, Gradle, APK assets/jniLibs, ADB.

## Global Constraints

- Do not modify `/home/jica/repo/GameNative`.
- Do not replace FEXCore with Box64 or copy Wine/ARM64EC-only launch behavior.
- No broad error suppression, fake stubs, global security relaxation, clearing, uninstalling, or commits.
- Preserve unrelated worktree changes and validate the real tablet gate before calling Task 4 complete.
- The device currently has insufficient free `/data` space for an APK replacement; do not perform destructive cleanup without user direction.

---

### Task 1: Capture the current-project failure boundary

**Files:**
- Create: `.superpowers/sdd/fexcore-gamenative-phase1.md`
- Read: `runtime/probes/fexcore-smoke.cpp`, Android test/launcher code, runtime build/package configuration

- [ ] Record root, branch state, focused FEX source map, existing logs, connected-device state, and the earliest meaningful failure.
- [ ] Reproduce only with already installed artifacts where possible; record the storage gate if replacement installation is required.
- [ ] Preserve prior evidence: rseq-disabled execution reaches `Context::ExecuteThread`, then exits 139, while fault-context APK installation is blocked by free space.

### Task 2: Map GameNative’s FEXCore integration

**Files:**
- Create: `.superpowers/sdd/fexcore-gamenative-reference.md`
- Read-only: `/home/jica/repo/GameNative/**`

- [ ] Record GameNative revision and enumerate FEXCore manager, program launcher, content/rootfs, native library loading, environment, signal, memory, packaging, and build files with exact line references.
- [ ] Classify every behavior as generic FEXCore, Android/Bionic, Wine/ARM64EC-specific, or irrelevant to an in-process shadPS4 guest CPU backend.

### Task 3: Compare binaries, packaging, and launch prerequisites

**Files:**
- Create: `.superpowers/sdd/fexcore-gamenative-binary-packaging.md`
- Read: current runtime/Android packaging files and read-only GameNative artifacts

- [ ] Inspect each discoverable FEXCore binary’s ABI, interpreter, dynamic dependencies, exported symbols, Android/Bionic versus glibc target, and APK layout.
- [ ] Compare Gradle/CMake ABI filters, native library extraction, linker-loadable locations, rootfs layout, permissions, and library order.
- [ ] Mark each difference as causal, non-causal, or unproven with evidence.

### Task 4: Trace our FEXCore embedding contract

**Files:**
- Create: `.superpowers/sdd/fexcore-gamenative-callflow.md`
- Read: `runtime/probes/fexcore-smoke.cpp`, `RuntimeProbeLauncher`, FEXCore public/source interfaces, shadPS4 launch integration

- [ ] Produce the complete Android-to-first-translated-block call flow with owner thread, inputs, lifetimes, required prerequisite, and error path.
- [ ] Compare against FEX’s supported context lifecycle and GameNative only where it exercises generic requirements.
- [ ] Distinguish missing embedding setup from an Android policy or binary ABI failure.

### Task 5: Make and validate the minimal root-cause fix

**Precondition:** Tasks 1–4 identify one evidenced root cause and the user/device can support physical validation.

- [ ] Write a separate exact TDD fix plan naming only the required production and test files.
- [ ] Implement one minimal fix; do not fold in diagnostic scaffolding or Wine-specific code.
- [ ] Rebuild managed runtime and APK, verify packaging, replacement-install, and pass the physical FEXCore smoke gate.
- [ ] Review the diff before any completion claim; leave Task 4 in progress if the tablet validation cannot run.
