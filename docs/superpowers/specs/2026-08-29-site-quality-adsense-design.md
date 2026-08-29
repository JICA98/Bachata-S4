# Bachata S4 Site Quality and AdSense Readiness Design

## Goal

Apply the supplied site-quality and AdSense-readiness patch to the public
`JICA98/Bachata-S4` repository while keeping emulator source and the existing
dirty development checkout untouched.

## Scope and repository boundaries

- Website content and generation changes belong on `gh-pages`.
- The scheduled deployment dispatcher belongs on `main`.
- The existing `/home/jica/repo/Bachata-S4-Dev` checkout contains unrelated
  emulator/UI work and must not be modified.
- The ZIP's `SOURCE_PATCHES/apply_source_fixes.py` is a helper input, not a
  file to publish in the website branch.

## Architecture and data flow

Use two isolated worktrees from the `JICA98/Bachata-S4` clone:

1. `fix/site-quality-adsense` tracks `release/gh-pages` and owns the static
   website, generator, audit, and push-triggered Pages workflow.
2. A separate branch from `release/main` owns only the scheduled/manual
   dispatcher workflow.

The helper updates the existing compatibility page and browser app in place.
The ZIP payload adds the permanent guide/FAQ pages, content-page stylesheet,
static-page generator, and site audit. The generator consumes the immutable
compatibility repository and writes a temporary `_site` artifact. The audit
checks generated HTML, metadata, structured data, links, sitemap coverage,
`ads.txt`, and content thresholds.

## Exact changes

On `gh-pages`:

- Run the helper against `compatibility-site/compatibility.html` and
  `compatibility-site/assets/js/app.js`.
- Copy these exact ZIP payloads:
  - `compatibility-site/guide.html`
  - `compatibility-site/faq.html`
  - `compatibility-site/privacy.html`
  - `compatibility-site/assets/css/content-pages.css`
  - `scripts/compatibility/build_static_pages.py`
  - `scripts/compatibility/site_audit.py`
  - `.github/workflows/compatibility-pages.yml`

On `main`:

- Add the exact ZIP payload
  `.github/workflows/deploy-site.yml`.

No emulator C/C++, Android, runtime, dependency, or unrelated data files are
allowed in either change set.

## Error handling

- The helper must fail if an expected source match is absent or duplicated;
  do not force replacements when upstream content differs.
- The generator and audit must run with `set -euo pipefail` around artifact
  assembly and must fail on malformed source data, missing required pages,
  invalid metadata, broken internal links, missing sitemap coverage, or a
  wrong production `ads.txt` publisher record.
- Keep generated output temporary and untracked.

## Verification

- Confirm both worktrees start clean except for intended changes.
- Run helper twice; second run must report no changes.
- Run Python bytecode compilation for both supplied scripts.
- Run `node --check` for all four site JavaScript files.
- Parse both workflow YAML files.
- Generate a production-style `_site` from local compatibility data and run
  `site_audit.py --site _site`.
- Review `git diff --name-only` in each worktree and assert no emulator paths
  changed.

## Non-goals

- Do not inspect or act on any AdSense account state.
- Do not publish, push, install, or deploy without a separate explicit request.
- Do not run the source patch helper against the emulator checkout.
