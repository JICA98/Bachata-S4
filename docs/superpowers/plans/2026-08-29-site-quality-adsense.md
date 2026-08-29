# Bachata S4 Site Quality and AdSense Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the supplied validated site-quality payload to `JICA98/Bachata-S4` while preserving all emulator changes in the development checkout.

**Architecture:** Keep website source changes on a branch from `release/gh-pages` and scheduled deployment changes on a separate branch from `release/main`. Use the ZIP helper for guarded in-place frontend transformations and copy the remaining ZIP payloads exactly.

**Tech Stack:** Git worktrees, GitHub Pages Actions, Python 3, static HTML/CSS/JavaScript, local `Bachata-S4-Compatibility` data, `site_audit.py`.

## Global Constraints

- Website content remains on `gh-pages`; scheduled dispatcher remains on `main`.
- Do not modify `/home/jica/repo/Bachata-S4-Dev` or any emulator source.
- Do not run the helper against the development checkout.
- Do not push, publish, install, or deploy.
- Preserve exact ZIP payload bytes for supplied files.
- Helper second run must make no changes.
- Generated `_site` output must remain temporary and untracked.

---

### Task 1: Create isolated main worktree

**Files:**
- Create worktree: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense-main`

**Interfaces:**
- Consumes: `release/main` fetched from remote `release`.
- Produces: clean main-based worktree for `.github/workflows/deploy-site.yml`.

- [ ] **Step 1: Fetch current target refs**

Run from `/home/jica/repo/Bachata-S4`:

```bash
git fetch release main gh-pages
```

Expected: `release/main` and `release/gh-pages` update or report already current.

- [ ] **Step 2: Create the main worktree and branch**

```bash
git worktree add -b fix/site-quality-adsense-main /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense-main release/main
```

Expected: new branch points at `release/main`.

- [ ] **Step 3: Confirm isolation**

```bash
git -C /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense-main status --short --branch
```

Expected: branch is `fix/site-quality-adsense-main`; no files are modified.

### Task 2: Apply exact `gh-pages` site payload

**Files:**
- Modify: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/compatibility.html`
- Modify: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/assets/js/app.js`
- Create: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/guide.html`
- Create: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/faq.html`
- Modify: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/privacy.html`
- Create: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/assets/css/content-pages.css`
- Create: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/scripts/compatibility/build_static_pages.py`
- Create: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/scripts/compatibility/site_audit.py`
- Modify: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/.github/workflows/compatibility-pages.yml`

**Interfaces:**
- Consumes: `/home/jica/Downloads/Bachata-S4-site-fix-2026-08-25.zip` and clean `fix/site-quality-adsense` worktree.
- Produces: site source with permanent content pages, reproducible generation, and audit checks.

- [ ] **Step 1: Extract helper into a temporary staging directory**

```bash
stage_dir="$(mktemp -d /tmp/bachata-site-fix.XXXXXX)"
unzip -q /home/jica/Downloads/Bachata-S4-site-fix-2026-08-25.zip 'bachata_site_fix/SOURCE_PATCHES/apply_source_fixes.py' -d "$stage_dir"
```

Expected: helper exists at `$stage_dir/bachata_site_fix/SOURCE_PATCHES/apply_source_fixes.py`.

- [ ] **Step 2: Run guarded helper once**

```bash
python3 "$stage_dir/bachata_site_fix/SOURCE_PATCHES/apply_source_fixes.py" --root /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense
```

Expected: reports the two frontend files changed; exits 0.

- [ ] **Step 3: Extract exact `gh-pages` payload**

```bash
unzip -q -o /home/jica/Downloads/Bachata-S4-site-fix-2026-08-25.zip \
  'bachata_site_fix/gh-pages/.github/workflows/compatibility-pages.yml' \
  'bachata_site_fix/gh-pages/compatibility-site/assets/css/content-pages.css' \
  'bachata_site_fix/gh-pages/compatibility-site/faq.html' \
  'bachata_site_fix/gh-pages/compatibility-site/guide.html' \
  'bachata_site_fix/gh-pages/compatibility-site/privacy.html' \
  'bachata_site_fix/gh-pages/scripts/compatibility/build_static_pages.py' \
  'bachata_site_fix/gh-pages/scripts/compatibility/site_audit.py' \
  -d "$stage_dir"
cp "$stage_dir"/bachata_site_fix/gh-pages/.github/workflows/compatibility-pages.yml /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/.github/workflows/compatibility-pages.yml
cp "$stage_dir"/bachata_site_fix/gh-pages/compatibility-site/assets/css/content-pages.css /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/assets/css/content-pages.css
cp "$stage_dir"/bachata_site_fix/gh-pages/compatibility-site/faq.html /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/faq.html
cp "$stage_dir"/bachata_site_fix/gh-pages/compatibility-site/guide.html /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/guide.html
cp "$stage_dir"/bachata_site_fix/gh-pages/compatibility-site/privacy.html /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site/privacy.html
cp "$stage_dir"/bachata_site_fix/gh-pages/scripts/compatibility/build_static_pages.py /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/scripts/compatibility/build_static_pages.py
cp "$stage_dir"/bachata_site_fix/gh-pages/scripts/compatibility/site_audit.py /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/scripts/compatibility/site_audit.py
```

Expected: all seven supplied files exist at their branch-root paths.

### Task 3: Apply exact `main` dispatcher payload

**Files:**
- Create or replace: `/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense-main/.github/workflows/deploy-site.yml`

**Interfaces:**
- Consumes: ZIP `main/.github/workflows/deploy-site.yml` and clean main worktree.
- Produces: scheduled/manual workflow that checks out only `gh-pages` website content.

- [ ] **Step 1: Extract the dispatcher payload into the existing staging directory**

```bash
unzip -q -o /home/jica/Downloads/Bachata-S4-site-fix-2026-08-25.zip \
  'bachata_site_fix/main/.github/workflows/deploy-site.yml' -d "$stage_dir"
```

Expected: dispatcher exists at `$stage_dir/bachata_site_fix/main/.github/workflows/deploy-site.yml`.

- [ ] **Step 2: Copy dispatcher to the main worktree**

```bash
cp "$stage_dir"/bachata_site_fix/main/.github/workflows/deploy-site.yml /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense-main/.github/workflows/deploy-site.yml
```

Expected: only the intended workflow is modified in the main worktree.

### Task 4: Verify exact payload and helper idempotency

**Files:**
- Verify: all files listed in Tasks 2–3.

**Interfaces:**
- Consumes: modified worktrees and ZIP manifest.
- Produces: evidence that payload hashes match and repeated helper execution is a no-op.

- [ ] **Step 1: Run helper second time**

```bash
python3 "$stage_dir/bachata_site_fix/SOURCE_PATCHES/apply_source_fixes.py" --root /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense
```

Expected: `Source fixes were already applied; no changes needed.`

- [ ] **Step 2: Compare supplied payload hashes**

For every ZIP manifest entry under `gh-pages/`, compare the ZIP bytes against the corresponding root-relative file in the site worktree. Compare `main/.github/workflows/deploy-site.yml` against the main worktree’s workflow.

Expected: copied files match byte-for-byte; helper-managed files differ only by the documented transformations.

- [ ] **Step 3: Check worktree diffs**

```bash
git -C /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense diff --check
git -C /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense-main diff --check
git -C /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense status --short
git -C /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense-main status --short
```

Expected: only planned site/docs files appear; no Android, runtime, C/C++, dependency, or generated emulator files appear.

### Task 5: Run syntax and static-site validation

**Files:**
- Verify: site JavaScript, Python scripts, workflows, generated `_site`.

**Interfaces:**
- Consumes: completed site and main worktrees plus local `/home/jica/repo/Bachata-S4-Compatibility` data.
- Produces: passing syntax checks and audited static artifact.

- [ ] **Step 1: Compile and syntax-check source**

```bash
cd /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense
python3 -m py_compile scripts/compatibility/build_static_pages.py scripts/compatibility/site_audit.py
node --check compatibility-site/assets/js/app.js
node --check compatibility-site/assets/js/home.js
node --check compatibility-site/assets/js/updates.js
node --check compatibility-site/assets/js/site.js
```

Expected: all commands exit 0 with no syntax errors.

- [ ] **Step 2: Parse workflow YAML**

```bash
python3 - <<'PY'
from pathlib import Path
import yaml
for path in [
    Path('/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/.github/workflows/compatibility-pages.yml'),
    Path('/home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense-main/.github/workflows/deploy-site.yml'),
]:
    with path.open() as handle:
        yaml.safe_load(handle)
    print(f'parsed {path}')
PY
```

Expected: both workflow paths print as parsed. If PyYAML is unavailable, use the repository’s installed YAML parser rather than editing workflows.

- [ ] **Step 3: Generate a temporary production-style site**

```bash
artifact_dir="$(mktemp -d /tmp/bachata-site-artifact.XXXXXX)"
cp -a /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/compatibility-site "$artifact_dir/_site"
mkdir -p "$artifact_dir/_site/data"
cp /home/jica/repo/Bachata-S4-Compatibility/generated/site-index.json "$artifact_dir/_site/data/site-index.json"
cp /home/jica/repo/Bachata-S4-Compatibility/generated/releases.json "$artifact_dir/_site/data/releases.json"
cp -a /home/jica/repo/Bachata-S4-Compatibility/generated/games "$artifact_dir/_site/data/games"
python3 /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/scripts/compatibility/build_static_pages.py \
  --site "$artifact_dir/_site" \
  --data "$artifact_dir/_site/data" \
  --base-url https://bachatas4.games
```

Expected: permanent game pages, `/games/index.html`, static content sections, sitemap, robots file, and 404 page are generated without modifying tracked source files. Save `artifact_dir` for the audit step.

- [ ] **Step 4: Audit generated artifact**

```bash
python3 /home/jica/repo/Bachata-S4/.worktrees/site-quality-adsense/scripts/compatibility/site_audit.py --site "$artifact_dir/_site"
```

Expected: audit passes required files, `ads.txt`, canonicals, metadata, content thresholds, JSON-LD, internal links, image alt attributes, sitemap coverage, and forbidden-placeholder checks.

- [ ] **Step 5: Confirm development checkout unchanged**

```bash
git -C /home/jica/repo/Bachata-S4-Dev status --short
```

Expected: pre-existing emulator/UI changes are unchanged and no site-fix files were added there.
