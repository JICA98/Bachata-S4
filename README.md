# Compatibility website

Deploy pipeline for https://bachatas4.games. This branch contains website
content ONLY — no emulator, runtime, or Android application source. The
release-source branch `main` of `JICA98/Bachata-S4` never changes when this
branch changes.

## Contents

- `compatibility-site/` — dependency-free static frontend
- `scripts/compatibility/` — capture and validation helpers
- `assets/bachata-s4-logo.png` — logo used by the Pages build
- `.github/workflows/compatibility-pages.yml` — validation + Pages deployment

Compatibility evidence lives in the `JICA98/Bachata-S4-Compatibility` data
repository; the deployment workflow merges it into the generated site.

## Deployment

The workflow deploys on:

- push to this branch (paths: `compatibility-site/**`, `scripts/compatibility/**`, the workflow itself)
- schedule (every 6 hours)
- manual `workflow_dispatch`

No deployment happens on pull requests. Custom domain `bachatas4.games`,
`ads.txt`, and existing site paths are preserved by the workflow.

## Preview locally

```bash
python3 -m http.server 8080 --directory compatibility-site
```

## Update the site

Site content is edited in the private development repository
(`compatibility-site/` on its `main` branch) and published here with
`scripts/publish-site.sh` from that repository. After this branch is pushed,
the workflow rebuilds and deploys the site.
