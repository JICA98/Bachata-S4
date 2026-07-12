# Library game title and local cover art

**Date:** 2026-07-12  
**Status:** Approved  
**Scope:** Android game library — real title from `param.sfo` and cover from dump `icon0.png`

## Summary

When a user imports a PS4 game folder, the library currently labels the game with the **folder name** and shows a **placeholder** cover. This design makes the library show the actual game **title** from `sce_sys/param.sfo` and **cover art** from `sce_sys/icon0.png` inside the imported dump. All metadata is local and offline; no cover-art scraping or online title databases.

## Goals

- Display the real game title (`TITLE` from `param.sfo`) after import.
- Prefer `TITLE_ID` from `param.sfo` as the stable game id / storage directory name when available.
- Show `icon0.png` from the imported game tree on library cards and the selected-game panel.
- Backfill titles for games already imported under folder names when `param.sfo` is present in app storage.
- Keep import working when SFO or icon files are missing (fallbacks, no hard fail for metadata alone).

## Non-goals

- Online cover or title download services.
- Using `pic0.png` / `pic1.png` as hero or splash art (future option).
- Renaming existing game directories when a previously wrong id was used.
- User-editable custom titles or custom cover images.
- JNI/native SFO parsing via shadPS4 for this feature.

## Current behavior

Import lives in `feature/library` (`LibraryScreen`):

1. User picks a folder via Storage Access Framework (`OpenDocumentTree`).
2. Tree is scanned into `ContentTreeEntry` list.
3. **Title** = document root folder name (or `"Imported game"`).
4. **Id** = first `CUSA\d{5}` match in that title, else `GAME-{uuid}`.
5. `ContentImporter.importGameTree` copies into app-private `games/{id}/`.
6. Room stores `id`, `title`, `relativePath`, `sourceUri`, `importedAtMs`.
7. UI cards show a solid placeholder box with the text `"GAME"`.

Desktop shadPS4 already reads `TITLE` / `TITLE_ID` from `param.sfo` and loads `icon0.png` for the big-picture library. Android should align with that local-metadata model.

## Design

### Metadata mapping

| Field | Primary source | Fallback chain |
|--------|----------------|----------------|
| Game id | `TITLE_ID` in `sce_sys/param.sfo` | `CUSA\d{5}` in folder name → `GAME-{uuid}` |
| Title | `TITLE` in `param.sfo` | Folder name → `"Imported game"` |
| Cover path | `{filesDir}/{relativePath}/sce_sys/icon0.png` | Placeholder UI (no image) |

Cover path is **derived**, not stored in Room. No schema migration for cover.

### `ParamSfoReader` (`core:data`)

New pure-Kotlin binary reader for PS4 `param.sfo`:

- Opens a `File` or `InputStream` / byte array.
- Parses the standard PSF/SFO header and index table sufficiently to resolve string keys.
- Exposes at least:
  - `title: String?` from key `TITLE`
  - `titleId: String?` from key `TITLE_ID`
- Missing keys or truncated/invalid files return nulls / empty result without throwing for normal “not a valid SFO” cases; clearly invalid buffers may throw only when the caller asked for strict parse, or return a sealed failure — prefer non-throwing `parse(...) -> ParamSfoMetadata` with null fields so import never aborts solely because SFO is bad.
- Unit tests with a minimal fixture covering present keys, missing keys, and truncated input.

No dependency on C++ `src/core/file_format/psf.*` for the Android app path.

### Import flow

Order of operations (preferred):

1. Scan the selected tree into `ContentTreeEntry` as today.
2. Locate relative path `sce_sys/param.sfo` among entries (case-sensitive path as stored in dumps; PS4 dumps use lowercase `sce_sys`).
3. If found, open the SAF URI with `ContentResolver` and parse with `ParamSfoReader` **before** copy.
4. Build `ContentImportRequest`:
   - `id` = normalized `TITLE_ID` when present and valid (same validation rules as current game id: safe single path segment, no `..`, matches existing `validateGameId` constraints); else existing folder/CUSA/uuid fallbacks.
   - `title` = non-blank `TITLE` when present; else folder name fallbacks.
5. Call `importGameTree` so files land under `games/{id}/` including `sce_sys/icon0.png` when present.
6. Persist via `GameRepository.addImportedGame` with the resolved title/id.

If SFO is absent or unreadable, import continues with folder-name behavior; do not fail the import for metadata alone. Existing requirements (e.g. `eboot.bin`, storage checks) remain unchanged.

### Backfill for existing library rows

Games already stored with folder-name titles should pick up real titles without re-import:

- In `GameRepository` (or a small helper it calls), when observing or on first library open, for each entity:
  - If `filesDir/{relativePath}/sce_sys/param.sfo` exists, parse `TITLE`.
  - If parsed title is non-blank and differs from stored `title`, update Room.
- Do **not** change `id` or move directories for existing rows (avoids breaking relative paths and settings keyed by game id).
- Cover art for old rows works automatically if `icon0.png` was copied during import; if the dump never had it, placeholder remains.

Implementation detail: prefer a lightweight path that avoids rewriting every row every collect. Acceptable patterns:

- One-shot “backfill if needed” suspended call when library starts, or
- `UPDATE` only when title differs after reading SFO.

### UI

**Library cards (`LibraryGameCard`)**  
Replace the placeholder-only box with:

- If `icon0.png` exists under the game’s app-owned path, decode and display it (crop/fill in the existing aspect-ratio box).
- Else keep the current `"GAME"` placeholder styling.

**Selected-game panel**  
Show the same icon (when available) next to or above the title, still using stored `title` and `id` text.

**Image loading**  
No Coil/Glide dependency required for v1. Use Android `BitmapFactory` + Compose `Image` / `ImageBitmap` with a small helper that:

- Resolves absolute path from `Context.filesDir` + `game.relativePath` + `sce_sys/icon0.png`.
- Handles missing file and decode failure by showing placeholder.
- Optionally remember bitmap by game id/path to avoid re-decode on every recomposition.

`Game` model may stay as `{ id, title, relativePath }`. Cover is a presentation concern derived from `relativePath` + app files dir. If helpful for testability, a pure function `Game.iconFile(filesDir: File): File` can live in data or feature code without expanding the serializable model.

### Error handling

| Situation | Behavior |
|-----------|----------|
| Missing `param.sfo` | Import OK; folder-name title/id fallbacks |
| Corrupt `param.sfo` | Import OK; same fallbacks |
| Missing `icon0.png` | Import OK; placeholder cover |
| Corrupt PNG | Placeholder cover |
| Invalid `TITLE_ID` for path safety | Reject that id; fall back to CUSA-from-name or `GAME-{uuid}` |
| Duplicate id after SFO id | Existing “game already imported” error unchanged |

### Testing

- **`ParamSfoReader` unit tests:** fixture bytes with `TITLE`/`TITLE_ID`; missing keys; truncated file.
- **Import integration/unit tests:** tree containing minimal SFO (+ optional icon) asserts stored title and id come from SFO, not folder name.
- **Backfill test:** Room row with folder title + on-disk `param.sfo` → after backfill, title matches SFO.
- **UI:** optional small unit/logic test for icon path resolution; visual Compose tests not required for v1.
- Existing `LibraryViewModelTest` and `ContentImporterTest` remain green; extend importer/library tests as needed.

## Architecture fit

```
feature/library (LibraryScreen)
  → parse SFO from SAF (optional pre-copy) / backfill via repository
  → ContentImporter.importGameTree
  → GameRepository (title in Room; icon path derived)

core/data
  → ParamSfoReader
  → ContentImporter (unchanged contract; request already carries id/title)
  → GameRepository backfill

core/model, core/database
  → Game / GameEntity unchanged schema for cover
```

Feature modules continue to depend on core interfaces; SFO parsing stays in `core:data` so session or setup can reuse it later if needed.

## Implementation notes

1. Add `ParamSfoReader` + tests first (TDD-friendly).
2. Wire pre-import SFO read in library import coroutine.
3. Add icon path helper + card/panel UI.
4. Add title backfill for existing games.
5. Extend importer/library tests with SFO fixtures.

## PR plan

Single focused change set is enough (one PR or one branch):

1. `ParamSfoReader` + unit tests.
2. Import uses SFO title/id; library shows local `icon0.png`.
3. Title backfill for existing rows + tests.

No Room schema version bump. No runtime package changes.

## Open decisions (resolved)

- **Cover source:** local dump `icon0.png` only (user choice).
- **Approach:** parse on import + path-derived cover; no cover column in DB (user choice A).
- **Existing ids:** do not rename directories; only backfill titles.
