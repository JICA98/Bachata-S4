# FEX Phase 0 Clean-History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconstruct the complete FEX Phase 0 implementation on a clean Debian-main base, prove that the reconstructed range does not change Box64 source/build paths, and regenerate SM8650 qualification evidence for the new source revision.

**Architecture:** Keep `feature/fex-arm64-sonic` unchanged as the legacy recovery branch.  Create a separate linked worktree on `feature/fex-arm64-phase0-clean` from the accepted Debian-main parent `5623902477eacbe36b30266e673652c14de32fa8`; merge the FEX pre-merge side, then replay only the explicitly listed FEX and general runtime-cleanliness commits.  Verify the reconstructed merge tree against `69d8ae3b`, verify the FEX source manifest against `c010d096`, and rerun package/device qualification so evidence names the clean branch revision.

**Tech Stack:** Git worktrees and three-way merge, C++20 FEXCore smoke runner, Node.js runtime/evidence verifiers, Android Gradle, ADB replacement installation, CMake/Ninja/CTest.

## Global Constraints

- Never rebase, reset, delete, force-push, or otherwise rewrite `feature/fex-arm64-sonic`; the legacy physical proof commit is `c010d096c57a5de12f930d7fdf65a1b4c9404df9`.
- Base the clean branch exactly on `5623902477eacbe36b30266e673652c14de32fa8`; do not silently use a moving `main` reference.
- Pin FEX at `f2b679f6028ce1c38875233aecfcf5d3f8ebecec`; preserve native ARM64 host plus FEXCore x86-64 guest execution and never substitute Box64.
- The clean range must not change `runtime/scripts/build-box64-host.sh` or `runtime/patches/box64-*.patch` from its clean base.
- Preserve the legacy worktree's dirty `externals/sdl3`, `externals/winlator-app`, and `runtime/sources/box64` submodules. Do not inspect them as candidate migration content.
- Do not modify `/home/jica/repo/GameNative`, uninstall/clear Android data, delete managed runtime/game/save data, or begin CpuBackend/Sonic/Bloodborne work.
- Device installation uses only `adb install -r` and `adb install -r -t`.

---

### Task 1: Protect the legacy proof and create the clean worktree

**Files:**
- Create: linked worktree `/home/jica/repo/Bachata-S4/.worktrees/fex-arm64-phase0-clean`
- Create: branch `feature/fex-arm64-phase0-clean`
- Create: annotated tag `fex-phase0-legacy-c010d096`
- Modify: none in the legacy worktree

**Interfaces:**
- Legacy recovery ref: `fex-phase0-legacy-c010d096` resolves exactly to `c010d096c57a5de12f930d7fdf65a1b4c9404df9`.
- Clean base ref: `feature/fex-arm64-phase0-clean` initially resolves exactly to `5623902477eacbe36b30266e673652c14de32fa8`.

- [ ] **Step 1: Verify the immutable legacy proof before creating refs**

Run from `/home/jica/repo/Bachata-S4/.worktrees/fex-arm64-sonic`:

```bash
git merge-base --is-ancestor c010d096c57a5de12f930d7fdf65a1b4c9404df9 feature/fex-arm64-sonic
git show -s --format='%H %s' c010d096c57a5de12f930d7fdf65a1b4c9404df9
git rev-parse 69d8ae3b^2
```

Expected: all commands exit 0; the final command prints `5623902477eacbe36b30266e673652c14de32fa8`.

- [ ] **Step 2: Create the recovery tag and clean linked worktree**

```bash
git tag -a fex-phase0-legacy-c010d096 c010d096c57a5de12f930d7fdf65a1b4c9404df9 -m 'Legacy FEX Phase 0 SM8650 proof before clean-history reconstruction'
git worktree add /home/jica/repo/Bachata-S4/.worktrees/fex-arm64-phase0-clean -b feature/fex-arm64-phase0-clean 5623902477eacbe36b30266e673652c14de32fa8
git -C /home/jica/repo/Bachata-S4/.worktrees/fex-arm64-phase0-clean rev-parse HEAD
```

Expected: the new worktree prints exactly `5623902477eacbe36b30266e673652c14de32fa8`; the legacy worktree remains on `feature/fex-arm64-sonic`.

- [ ] **Step 3: Verify isolation and push only the recovery ref**

```bash
git -C /home/jica/repo/Bachata-S4/.worktrees/fex-arm64-sonic status --short
git -C /home/jica/repo/Bachata-S4/.worktrees/fex-arm64-phase0-clean status --short
git push origin refs/tags/fex-phase0-legacy-c010d096
```

Expected: the legacy status still reports only its protected submodule dirt; the clean worktree has no output; the remote tag resolves to the legacy proof commit.

- [ ] **Step 4: Verify the committed legacy design and migration plan**

```bash
git -C /home/jica/repo/Bachata-S4/.worktrees/fex-arm64-sonic show -s --format='%H %s' 924e99f5
git -C /home/jica/repo/Bachata-S4/.worktrees/fex-arm64-sonic show -s --format='%H %s' a01f7fa4
git ls-remote --heads origin feature/fex-arm64-sonic
```

Expected: the remote legacy branch contains both documentation commits; no
runtime, Android, FEX, or Box64 source file changes are introduced by this
step.

---

### Task 2: Reconstruct the FEX-only history and prove source equivalence

**Files:**
- Modify in clean worktree: the FEX Phase 0 source/runtime/Android files introduced by the replayed commits
- Do not modify in clean worktree: `runtime/scripts/build-box64-host.sh`, `runtime/patches/box64-*.patch`
- Test: Git tree and path-manifest comparisons

**Interfaces:**
- Pre-merge FEX side head: `4a7e6d226f34f21dd98e920acc65113a168362ab`.
- Original accepted merge tree: `69d8ae3babfc66b6e2004555fe3fbe7a94776dd3`.
- Legacy qualification tree: `c010d096c57a5de12f930d7fdf65a1b4c9404df9`.

- [ ] **Step 1: Recreate the accepted Debian/FEX merge tree**

Run in `/home/jica/repo/Bachata-S4/.worktrees/fex-arm64-phase0-clean`:

```bash
git merge --no-ff 4a7e6d226f34f21dd98e920acc65113a168362ab -m 'merge: reconstruct FEX pre-Debian Phase 0 work'
git diff --check 69d8ae3babfc66b6e2004555fe3fbe7a94776dd3..HEAD
git diff --exit-code 69d8ae3babfc66b6e2004555fe3fbe7a94776dd3 -- . ':(exclude)build-tests'
```

If Git reports a merge conflict, resolve each conflicted file to its exact blob from `69d8ae3b` with `git checkout 69d8ae3b -- <conflicted-path>`, stage it, and continue the merge. Expected: the final comparison exits 0 outside `build-tests`, which is historical generated content excluded from the reconstruction tree check.

- [ ] **Step 2: Replay only the approved post-merge FEX/runtime commits**

Cherry-pick these commits in this exact order:

```bash
git cherry-pick ed5311348401eb4b3846b9e33adf381219c0b5f7
git cherry-pick b142deb723d068bbf2555fcdef72103fe193d7ec
git cherry-pick 63a60e589708de1c1fc8b2bdc3b959aea2912305
git cherry-pick 6ec56bb7c62dadbf732f015f80efed24508a8a71
git cherry-pick fcf6bac25f51718a9b01d741200c0101f40e30d2
git cherry-pick d072ff8336b6bb1637532eab065d132c2cbc0e9d
git cherry-pick 735e166ef83d432c444a19171a4d4b34370b4295
git cherry-pick 7069c618ad2d0d8a475a7855bb6884c8125aea42
git cherry-pick d6eda2cc3ab3e97eb2f1f2a4b349871c17c5809b
git cherry-pick fcb2573208c6da09bb424c7322ab5fba5530b6c3
git cherry-pick b4d38a53705ab295625b4d0cf96691545e7fd008
git cherry-pick 608d0391521b92123b987ad041d19ebe5f747db1
git cherry-pick c010d096c57a5de12f930d7fdf65a1b4c9404df9
```

Do not cherry-pick `3536154356f2b2229aa7c9c9ef076f7acd4045de`, which changes Box64 checkout behavior.  If a cherry-pick conflicts, resolve only the FEX/runtime/Android path from the source commit; preserve the clean base version of every Box64 source/build path, then run `git cherry-pick --continue`.

- [ ] **Step 3: Remove tracked generated and out-of-phase merge baggage**

The historical merge added generated `build-tests/` content and an out-of-phase
CpuBackend design document.  Neither is required by the Phase 0 FEX artifact,
and both make a source-range whitespace audit non-deterministic.  Remove only
these paths from the clean candidate:

```bash
git rm -r --ignore-unmatch build-tests
git rm --ignore-unmatch docs/superpowers/specs/2026-07-12-fex-guest-cpu-backend-design.md
git commit -m 'chore(fex): omit generated phase0 baggage'
git diff --check 5623902477eacbe36b30266e673652c14de32fa8..HEAD
```

Expected: the deletion commit contains only the listed generated/out-of-phase
paths and the range-scoped whitespace audit exits 0.  Do not delete a runtime,
FEX, Android, Box64 source/build, or evidence file in this step.

- [ ] **Step 4: Compare the explicit FEX manifest before requalification**

Create `/tmp/fex-phase0-clean-paths.txt` with this exact newline-delimited list:

```text
AGENTS.md
android/BachataS4/app/src/androidTest/kotlin/com/bachatas4/android/FexCoreSmokeDeviceTest.kt
android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncher.kt
android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeProbeLauncherTest.kt
documents/android-building.md
runtime/evidence/baseline/dbe5165b-ctest.json
runtime/locks/components.lock.json
runtime/patches/fex-fexcore-only.patch
runtime/probes/fexcore-smoke.cpp
runtime/qualification/create-fex-phase0-evidence.mjs
runtime/qualification/fex-phase0-schema.json
runtime/qualification/run-fexcore-smoke.sh
runtime/scripts/build-fexcore-smoke-aarch64.sh
runtime/scripts/build-runtime-debian.sh
runtime/scripts/checkout-component.sh
runtime/scripts/package-runtime.mjs
runtime/scripts/stage-debian-runtime.mjs
runtime/tests/build-fexcore-smoke-cleanup-source.test.mjs
runtime/tests/checkout-component-cleanliness.test.mjs
runtime/tests/create-fex-phase0-evidence.test.mjs
runtime/tests/fexcore-callret-stack-source.test.mjs
runtime/tests/fexcore-ci-source.test.mjs
runtime/tests/fexcore-device-test-source.test.mjs
runtime/tests/fexcore-fault-diagnostic-source.test.mjs
runtime/tests/fexcore-final-acceptance-source.test.mjs
runtime/tests/fexcore-guest-state-source.test.mjs
runtime/tests/fexcore-mapping-cleanup-source.test.mjs
runtime/tests/fex-phase0-evidence.test.mjs
runtime/tests/run-ctest-with-baseline.mjs
runtime/tests/run-fexcore-smoke-source.test.mjs
runtime/tests/shadps4-baseline.test.mjs
runtime/tests/verify-apk-runtime.mjs
runtime/tests/verify-fexcore-smoke-build.mjs
runtime/tests/verify-fex-phase0-evidence.mjs
runtime/tests/verify-runtime.mjs
```

Then run:

```bash
while IFS= read -r path; do
  git diff --exit-code c010d096c57a5de12f930d7fdf65a1b4c9404df9 -- "$path"
done < /tmp/fex-phase0-clean-paths.txt
git diff --exit-code 5623902477eacbe36b30266e673652c14de32fa8 -- runtime/scripts/build-box64-host.sh runtime/patches/box64-*.patch
git diff --check 5623902477eacbe36b30266e673652c14de32fa8..HEAD
```

Expected: every manifest path exactly matches the legacy proof tree before evidence regeneration; the Box64 diff and range-scoped whitespace checks exit 0.

- [ ] **Step 5: Verify the reconstructed source range and push the branch**

```bash
git status --short
git push -u origin feature/fex-arm64-phase0-clean
```

Expected: only clean-branch commits are pushed. The legacy branch and its protected submodule dirt are unchanged.

---

### Task 3: Requalify the clean branch and close the Phase 0 gate

**Files:**
- Modify in clean worktree: `runtime/evidence/sm8650/fex-phase0.json`
- Modify in clean worktree: `runtime/evidence/sm8650/fex-phase0-instrumentation.txt`
- Modify in clean worktree: `runtime/evidence/sm8650/fex-phase0-logcat.txt`
- Test: runtime, APK, instrumentation, CTest, Box64 separation, and range audit

**Interfaces:**
- Success marker: `FEXCORE_SMOKE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok stack=ok fp=ok threads=ok tls=ok callback=ok invalidation=ok`.
- Evidence source revision: the clean branch `HEAD` at the time of the physical runner.

- [ ] **Step 1: Run focused source and Android launcher contracts**

```bash
node --test runtime/tests/shadps4-baseline.test.mjs runtime/tests/fex-phase0-evidence.test.mjs runtime/tests/create-fex-phase0-evidence.test.mjs runtime/tests/run-fexcore-smoke-source.test.mjs runtime/tests/build-fexcore-smoke-cleanup-source.test.mjs
node runtime/tests/verify-runtime.mjs --locks-only
cd android/BachataS4
ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :core:runtime:testDebugUnitTest --tests '*RuntimeProbeLauncherTest*'
cd ../..
```

Expected: all Node subtests and the lock verifier pass, and the Android launcher
test reports 15 passing tests.  The binary artifact verifier runs after the
clean runtime build in Step 2.

- [ ] **Step 2: Rebuild the runtime and Playstore APK before device installation**

```bash
runtime/scripts/build-runtime-debian.sh
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs
node runtime/tests/verify-fexcore-smoke-build.mjs runtime/build/fexcore-smoke-stage/bin/fexcore-smoke
cd android/BachataS4
ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew clean test lintDebug assemblePlaystoreDebug assemblePlaystoreDebugAndroidTest
cd ../..
node runtime/tests/verify-apk-runtime.mjs android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
unzip -l android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk | grep -E 'assets/runtime/(manifest\.json|runtime\.zip)'
```

Expected: runtime/Gradle/APK verification exits 0 and the APK lists both managed runtime assets.

- [ ] **Step 3: Replacement-install and run the clean-branch tablet qualification**

```bash
adb devices
ADB=/home/jica/scripts/adb runtime/qualification/run-fexcore-smoke.sh 7d6afed8
node runtime/tests/verify-fex-phase0-evidence.mjs runtime/evidence/sm8650/fex-phase0.json
```

Expected: the runner and independent validator exit 0; evidence contains the exact success marker, a duration in `1..30000`, `source.projectRevision` equal to the clean `HEAD`, matching artifact/log hashes, and no carriage returns, serial, or private paths.

- [ ] **Step 4: Run the final clean-range audits and CTest gate**

```bash
cmake -S . -B runtime/build/shadps4-host-tests -G Ninja -DCMAKE_BUILD_TYPE=Debug -DENABLE_TESTS=ON
cmake --build runtime/build/shadps4-host-tests --parallel 8
node runtime/tests/run-ctest-with-baseline.mjs runtime/build/shadps4-host-tests
git diff --exit-code 5623902477eacbe36b30266e673652c14de32fa8 -- runtime/scripts/build-box64-host.sh runtime/patches/box64-*.patch
git diff --check 5623902477eacbe36b30266e673652c14de32fa8..HEAD
! rg -qi box64 runtime/evidence/sm8650/fex-phase0-instrumentation.txt runtime/evidence/sm8650/fex-phase0-logcat.txt runtime/evidence/sm8650/fex-phase0.json
git status --short
```

Expected: CTest reports 382/382 executed tests, both Git audits exit 0, evidence contains no Box64 token, and the clean worktree has only the three regenerated evidence files before their commit.

- [ ] **Step 5: Commit fresh evidence and record Phase 0 completion**

```bash
git add runtime/evidence/sm8650/fex-phase0.json runtime/evidence/sm8650/fex-phase0-instrumentation.txt runtime/evidence/sm8650/fex-phase0-logcat.txt
git diff --cached --check
node runtime/tests/verify-fex-phase0-evidence.mjs runtime/evidence/sm8650/fex-phase0.json
git commit -m 'test(fex): qualify clean phase0 on sm8650'
git push
```

Expected: the evidence commit is pushed on `feature/fex-arm64-phase0-clean`; the legacy proof and branch remain reachable; Phase 1 planning may begin only after this commit.
