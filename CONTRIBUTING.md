# Contributing to Bachata S4

This public repository is the community and release portal for Bachata S4. Public pull requests are intentionally limited to:

- compatibility records under `compatibility-data/**`;
- compatibility portal content under `compatibility-site/**`;
- `README.md`, contribution/security guidance, and approved public documentation;
- the public workflows and templates that validate those files.

Link every pull request to an issue. Use the compatibility PR template for game data and the site/docs template for public content. Keep evidence minimal and remove usernames, local paths, account data, tokens, keys, and copyrighted game data.

## Changes not accepted here

Android, managed runtime, emulator, FEX guest CPU emulation, graphics-driver integration, packaging, release-signing, private evidence, and hardening implementation changes are not accepted in this repository. Open an issue with reproduction evidence so maintainers can triage implementation fixes privately.

Patches to the open-source ARM64 emulator core belong at [zenithblue-oss/shadps4-arm64](https://github.com/zenithblue-oss/shadps4-arm64).

Do not work around the public path policy by embedding source, archives, generated binaries, submodules, or encoded payloads in an allowed directory.

## Compatibility contributions

Search for the CUSA first. One canonical compatibility issue is maintained per CUSA. A data change must map to that issue, follow the published schema, and preserve a single compatibility status. Only test games you legally own using your own dump.

## Site and documentation contributions

Keep content accurate, accessible, responsive, and usable without proprietary assets. Include before/after screenshots for visual changes and run the checks named by the pull-request workflow.
