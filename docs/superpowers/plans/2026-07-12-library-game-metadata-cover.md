# Library Game Title and Local Cover Art Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the real game title from `param.sfo` and cover art from `sce_sys/icon0.png` in the Android library after import (and backfill titles for existing rows).

**Architecture:** Pure-Kotlin `ParamSfoReader` in `core:data` parses offline `param.sfo`. Import resolves `TITLE` / `TITLE_ID` before copy; Room keeps storing title only. Cover path is derived from `game.relativePath` + `sce_sys/icon0.png` under app `filesDir`. Library UI decodes that PNG with `BitmapFactory` and falls back to the existing placeholder.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Room, JUnit 4, Gradle (`android/BachataS4`), Hilt.

**Spec:** `docs/superpowers/specs/2026-07-12-library-game-metadata-cover-design.md`

## Global Constraints

- Offline only: no cover-art scraping or network metadata.
- Cover path is derived; do **not** add a Room cover column or bump DB version for cover.
- Do **not** rename existing game directories/ids during backfill (title only).
- Invalid/missing SFO or icon must not fail an otherwise valid import.
- Game ids must still match `ContentImporter` validation: `[A-Za-z0-9._-]+` only.
- Before any APK install or publication, follow runtime packaging and `unzip` verification in `AGENTS.md`.

## File Map

| File | Responsibility |
| --- | --- |
| `core/data/.../ParamSfoReader.kt` | Parse PSF/SFO; return `title` / `titleId`. |
| `core/data/.../GameMetadataResolver.kt` | Pure fallbacks: SFO → folder name → defaults. |
| `core/data/.../GameIconPaths.kt` | Resolve `icon0.png` and `param.sfo` under `filesDir`. |
| `core/data/.../GameRepository.kt` | Title backfill from on-disk SFO; expose icon path helper if needed. |
| `core/database/.../GameDao.kt` | Add `updateTitle(id, title)`. |
| `feature/library/.../LibraryScreen.kt` | Pre-import SFO read; show cover bitmaps on cards + selected panel. |
| `core/data/.../ParamSfoReaderTest.kt` | SFO parse unit tests + fixture builder. |
| `core/data/.../GameMetadataResolverTest.kt` | Fallback chain unit tests. |
| `core/data/.../GameRepositoryTitleBackfillTest.kt` | Backfill title from on-disk SFO. |

---

### Task 1: `ParamSfoReader` (parse TITLE / TITLE_ID)

**Files:**
- Create: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/ParamSfoReader.kt`
- Create: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/ParamSfoReaderTest.kt`

**Interfaces:**
- Consumes: raw SFO bytes (`ByteArray`) or `InputStream` read fully into bytes
- Produces:
  ```kotlin
  data class ParamSfoMetadata(val title: String?, val titleId: String?)
  object ParamSfoReader {
      fun parse(bytes: ByteArray): ParamSfoMetadata
  }
  ```
  Non-throwing for corrupt input: return `ParamSfoMetadata(null, null)` when magic/version/structure is invalid.

PSF layout (match desktop `src/core/file_format/psf.*`):

- Magic: big-endian `0x00505346` (bytes `00 50 53 46`)
- Version: little-endian `0x00000101` or `0x00000100`
- Header (20 bytes LE unless noted): `magic_be(4)`, `version_le(4)`, `keyTableOffset_le(4)`, `dataTableOffset_le(4)`, `indexCount_le(4)`
- Each index entry (16 bytes): `keyOffset_le(2)`, `fmt_be(2)`, `len_le(4)`, `maxLen_le(4)`, `dataOffset_le(4)`
- String format: `0x0204` (big-endian layout of fmt field as stored — desktop uses `u16_be param_fmt`; on disk for Text the two bytes are `04 02` if little-endian host wrote BE value `0x0204`… Use the same interpretation as C++: read 2 bytes big-endian as format id; `Text = 0x0204`)
- Keys are null-terminated in key table; string values are null-terminated UTF-8 in data table

- [ ] **Step 1: Write the failing tests**

Create `ParamSfoReaderTest.kt`:

```kotlin
package com.bachatas4.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ParamSfoReaderTest {
    @Test
    fun parsesTitleAndTitleId() {
        val bytes = buildMinimalSfo(
            mapOf(
                "TITLE" to "Bloodborne",
                "TITLE_ID" to "CUSA00900",
            ),
        )
        val meta = ParamSfoReader.parse(bytes)
        assertEquals("Bloodborne", meta.title)
        assertEquals("CUSA00900", meta.titleId)
    }

    @Test
    fun missingKeysReturnNullFields() {
        val bytes = buildMinimalSfo(mapOf("CATEGORY" to "gd"))
        val meta = ParamSfoReader.parse(bytes)
        assertNull(meta.title)
        assertNull(meta.titleId)
    }

    @Test
    fun truncatedOrInvalidReturnsEmptyMetadata() {
        assertEquals(ParamSfoMetadata(null, null), ParamSfoReader.parse(ByteArray(0)))
        assertEquals(ParamSfoMetadata(null, null), ParamSfoReader.parse("not-sfo".toByteArray()))
        assertEquals(ParamSfoMetadata(null, null), ParamSfoReader.parse(ByteArray(8) { 0 }))
    }

    /** Minimal UTF-8 text-only SFO sufficient for unit tests. */
    internal fun buildMinimalSfo(strings: Map<String, String>): ByteArray {
        val entries = strings.entries.toList()
        val headerSize = 20
        val indexSize = entries.size * 16
        val keyTable = ArrayList<Byte>()
        val keyOffsets = IntArray(entries.size)
        entries.forEachIndexed { i, (key, _) ->
            keyOffsets[i] = keyTable.size
            keyTable.addAll(key.toByteArray(Charsets.UTF_8).toList())
            keyTable.add(0)
        }
        while (keyTable.size % 4 != 0) keyTable.add(0)

        val dataTable = ArrayList<Byte>()
        val dataOffsets = IntArray(entries.size)
        val lengths = IntArray(entries.size)
        entries.forEachIndexed { i, (_, value) ->
            dataOffsets[i] = dataTable.size
            val raw = value.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
            lengths[i] = raw.size
            dataTable.addAll(raw.toList())
            while (dataTable.size % 4 != 0) dataTable.add(0)
        }

        val keyTableOffset = headerSize + indexSize
        val dataTableOffset = keyTableOffset + keyTable.size
        val out = ByteBuffer.allocate(dataTableOffset + dataTable.size).order(ByteOrder.LITTLE_ENDIAN)
        // magic big-endian 0x00505346
        out.put(0, 0x00)
        out.put(1, 0x50)
        out.put(2, 0x53)
        out.put(3, 0x46)
        out.position(4)
        out.putInt(0x00000101) // version LE
        out.putInt(keyTableOffset)
        out.putInt(dataTableOffset)
        out.putInt(entries.size)
        entries.forEachIndexed { i, _ ->
            out.putShort(keyOffsets[i].toShort())
            // param_fmt as big-endian u16 0x0204 → bytes 0x04, 0x02 at this LE buffer position:
            // write raw BE bytes for format
            out.put(0x04)
            out.put(0x02)
            out.putInt(lengths[i])
            out.putInt(lengths[i])
            out.putInt(dataOffsets[i])
        }
        keyTable.forEach { out.put(it) }
        dataTable.forEach { out.put(it) }
        return out.array()
    }
}
```

Note: if the format bytes need adjustment when implementing against the C++ reader, fix the fixture once tests assert against a known-good parse. Keep fixture and reader consistent.

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
cd android/BachataS4 && ./gradlew :core:data:testDebugUnitTest --tests 'com.bachatas4.android.data.ParamSfoReaderTest'
```

Expected: FAIL — `ParamSfoReader` / `ParamSfoMetadata` unresolved.

- [ ] **Step 3: Implement `ParamSfoReader`**

Create `ParamSfoReader.kt`:

```kotlin
package com.bachatas4.android.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParamSfoMetadata(
    val title: String?,
    val titleId: String?,
)

object ParamSfoReader {
    private const val MAGIC = 0x00505346
    private const val VERSION_1_0 = 0x00000100
    private const val VERSION_1_1 = 0x00000101
    private const val FMT_TEXT = 0x0204
    private const val HEADER_SIZE = 20
    private const val INDEX_ENTRY_SIZE = 16

    fun parse(bytes: ByteArray): ParamSfoMetadata {
        if (bytes.size < HEADER_SIZE) return ParamSfoMetadata(null, null)
        return try {
            parseOrThrow(bytes)
        } catch (_: Exception) {
            ParamSfoMetadata(null, null)
        }
    }

    private fun parseOrThrow(bytes: ByteArray): ParamSfoMetadata {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = buf.int
        if (magic != MAGIC) return ParamSfoMetadata(null, null)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.int
        if (version != VERSION_1_0 && version != VERSION_1_1) return ParamSfoMetadata(null, null)
        val keyTableOffset = buf.int
        val dataTableOffset = buf.int
        val count = buf.int
        if (count < 0 || count > 4096) return ParamSfoMetadata(null, null)

        var title: String? = null
        var titleId: String? = null
        for (i in 0 until count) {
            val entryPos = HEADER_SIZE + i * INDEX_ENTRY_SIZE
            if (entryPos + INDEX_ENTRY_SIZE > bytes.size) return ParamSfoMetadata(null, null)
            buf.position(entryPos)
            buf.order(ByteOrder.LITTLE_ENDIAN)
            val keyOffset = buf.short.toInt() and 0xFFFF
            buf.order(ByteOrder.BIG_ENDIAN)
            val fmt = buf.short.toInt() and 0xFFFF
            buf.order(ByteOrder.LITTLE_ENDIAN)
            val len = buf.int
            buf.int // maxLen
            val dataOffset = buf.int
            if (fmt != FMT_TEXT) continue
            val key = readCString(bytes, keyTableOffset + keyOffset) ?: continue
            val value = readCString(bytes, dataTableOffset + dataOffset, maxLen = len) ?: continue
            when (key) {
                "TITLE" -> title = value.ifBlank { null }
                "TITLE_ID" -> titleId = value.ifBlank { null }
            }
        }
        return ParamSfoMetadata(title = title, titleId = titleId)
    }

    private fun readCString(bytes: ByteArray, start: Int, maxLen: Int = Int.MAX_VALUE): String? {
        if (start < 0 || start >= bytes.size) return null
        val endLimit = minOf(bytes.size, if (maxLen == Int.MAX_VALUE) bytes.size else start + maxLen)
        var end = start
        while (end < endLimit && bytes[end] != 0.toByte()) end++
        if (end == start) return ""
        return String(bytes, start, end - start, Charsets.UTF_8)
    }
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Run the same Gradle command as Step 2.

Expected: PASS (all three tests).

If `parsesTitleAndTitleId` fails due to format endianness, adjust the fixture `0x04 0x02` vs reader `FMT_TEXT` until they match the C++ layout; do not weaken the test.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/ParamSfoReader.kt \
  android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/ParamSfoReaderTest.kt
git commit -m "feat(android): parse param.sfo TITLE and TITLE_ID"
```

---

### Task 2: `GameMetadataResolver` (fallback chain)

**Files:**
- Create: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/GameMetadataResolver.kt`
- Create: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/GameMetadataResolverTest.kt`

**Interfaces:**
- Consumes: `ParamSfoMetadata`, folder display name, optional id factory for UUID fallback
- Produces:
  ```kotlin
  data class ResolvedGameMetadata(val id: String, val title: String)
  object GameMetadataResolver {
      fun resolve(
          folderName: String?,
          sfo: ParamSfoMetadata?,
          randomId: () -> String = { java.util.UUID.randomUUID().toString() },
      ): ResolvedGameMetadata
  }
  ```

Rules (spec table):

1. `title` = non-blank `sfo.title` → else non-blank `folderName` → else `"Imported game"`
2. `id` = valid `sfo.titleId` (matches `[A-Za-z0-9._-]+`) → else first `CUSA\d{5}` (case-insensitive) in folder name uppercased → else `"GAME-${randomId()}"`
3. Never return blank title or blank id.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.bachatas4.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMetadataResolverTest {
    @Test
    fun prefersSfoTitleAndTitleId() {
        val resolved = GameMetadataResolver.resolve(
            folderName = "some-folder-CUSA00001",
            sfo = ParamSfoMetadata(title = "Real Title", titleId = "CUSA00900"),
        )
        assertEquals("CUSA00900", resolved.id)
        assertEquals("Real Title", resolved.title)
    }

    @Test
    fun fallsBackToFolderNameAndCusaRegex() {
        val resolved = GameMetadataResolver.resolve(
            folderName = "Bloodborne-CUSA00900",
            sfo = null,
        )
        assertEquals("CUSA00900", resolved.id)
        assertEquals("Bloodborne-CUSA00900", resolved.title)
    }

    @Test
    fun fallsBackToImportedGameAndGeneratedId() {
        val resolved = GameMetadataResolver.resolve(
            folderName = null,
            sfo = ParamSfoMetadata(null, null),
            randomId = { "fixed-uuid" },
        )
        assertEquals("GAME-fixed-uuid", resolved.id)
        assertEquals("Imported game", resolved.title)
    }

    @Test
    fun rejectsUnsafeTitleId() {
        val resolved = GameMetadataResolver.resolve(
            folderName = "safe-CUSA12345",
            sfo = ParamSfoMetadata(title = "X", titleId = "../escape"),
            randomId = { "u" },
        )
        assertEquals("CUSA12345", resolved.id)
        assertEquals("X", resolved.title)
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

```bash
cd android/BachataS4 && ./gradlew :core:data:testDebugUnitTest --tests 'com.bachatas4.android.data.GameMetadataResolverTest'
```

Expected: FAIL — unresolved `GameMetadataResolver`.

- [ ] **Step 3: Implement resolver**

```kotlin
package com.bachatas4.android.data

import java.util.UUID

data class ResolvedGameMetadata(
    val id: String,
    val title: String,
)

object GameMetadataResolver {
    private val safeId = Regex("^[A-Za-z0-9._-]+$")
    private val cusa = Regex("CUSA\\d{5}", RegexOption.IGNORE_CASE)

    fun resolve(
        folderName: String?,
        sfo: ParamSfoMetadata?,
        randomId: () -> String = { UUID.randomUUID().toString() },
    ): ResolvedGameMetadata {
        val title = sfo?.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: folderName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Imported game"
        val id = sfo?.titleId?.trim()?.takeIf { it.isNotEmpty() && safeId.matches(it) }
            ?: folderName?.let { cusa.find(it)?.value?.uppercase() }
            ?: "GAME-${randomId()}"
        return ResolvedGameMetadata(id = id, title = title)
    }
}
```

- [ ] **Step 4: Run tests to verify GREEN**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/GameMetadataResolver.kt \
  android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/GameMetadataResolverTest.kt
git commit -m "feat(android): resolve game id and title from SFO with fallbacks"
```

---

### Task 3: Icon / SFO path helpers

**Files:**
- Create: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/GameIconPaths.kt`
- Create: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/GameIconPathsTest.kt`

**Interfaces:**
- Consumes: `filesDir: File`, `relativePath: String` (e.g. `games/CUSA00900`)
- Produces:
  ```kotlin
  object GameIconPaths {
      fun icon0(filesDir: File, relativePath: String): File
      fun paramSfo(filesDir: File, relativePath: String): File
  }
  ```
  Paths: `{filesDir}/{relativePath}/sce_sys/icon0.png` and `.../param.sfo`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.bachatas4.android.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class GameIconPathsTest {
    @Test
    fun resolvesIconAndSfoUnderRelativePath() {
        val root = File("/tmp/app-files")
        assertEquals(
            File("/tmp/app-files/games/CUSA00900/sce_sys/icon0.png"),
            GameIconPaths.icon0(root, "games/CUSA00900"),
        )
        assertEquals(
            File("/tmp/app-files/games/CUSA00900/sce_sys/param.sfo"),
            GameIconPaths.paramSfo(root, "games/CUSA00900"),
        )
    }
}
```

- [ ] **Step 2: Run RED**

```bash
cd android/BachataS4 && ./gradlew :core:data:testDebugUnitTest --tests 'com.bachatas4.android.data.GameIconPathsTest'
```

- [ ] **Step 3: Implement**

```kotlin
package com.bachatas4.android.data

import java.io.File

object GameIconPaths {
    fun icon0(filesDir: File, relativePath: String): File =
        File(filesDir, "$relativePath/sce_sys/icon0.png")

    fun paramSfo(filesDir: File, relativePath: String): File =
        File(filesDir, "$relativePath/sce_sys/param.sfo")
}
```

- [ ] **Step 4: Run GREEN** — same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/GameIconPaths.kt \
  android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/GameIconPathsTest.kt
git commit -m "feat(android): derive param.sfo and icon0 paths from game relativePath"
```

---

### Task 4: Wire SFO metadata into library import

**Files:**
- Modify: `android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt` (import coroutine ~lines 82–107)
- Optional test: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/ContentImporterTest.kt` already covers tree copy; metadata resolution is covered by Task 2. No need to retest SAF here.

**Interfaces:**
- Consumes: `ParamSfoReader`, `GameMetadataResolver`, existing `ContentImporter` / `ContentImportRequest`
- Produces: imports that store real title/id when `sce_sys/param.sfo` is in the tree

- [ ] **Step 1: Replace folder-only metadata in the import launcher**

In `LibraryScreen` import `scope.launch` block, after building entries, resolve metadata before `importGameTree`:

```kotlin
val (folderName, entries) = withContext(Dispatchers.IO) {
    val root = requireNotNull(DocumentFile.fromTreeUri(context, uri)) {
        "Cannot read selected folder"
    }
    (root.name?.ifBlank { null } ?: "Imported game") to root.toImportEntries()
}
val sfoEntry = entries.firstOrNull {
    it.relativePath == "sce_sys/param.sfo" || it.relativePath.endsWith("/sce_sys/param.sfo")
}
// Prefer exact relative path used by dumps:
val sfoBytes = sfoEntry?.let { entry ->
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(entry.sourceUri))
                ?.use { it.readBytes() }
        }.getOrNull()
    }
}
val sfo = sfoBytes?.let { ParamSfoReader.parse(it) }
val resolved = GameMetadataResolver.resolve(folderName = folderName, sfo = sfo)
val result = dependencies.contentImporter().importGameTree(
    ContentImportRequest(
        id = resolved.id,
        title = resolved.title,
        sourceUri = uri.toString(),
    ),
    entries,
)
dependencies.gameRepository().addImportedGame(result, uri.toString(), System.currentTimeMillis())
```

Remove the old `Regex("CUSA\\d{5}"...)` / `title = title` path; `GameMetadataResolver` owns that logic.

Imports to add:

```kotlin
import com.bachatas4.android.data.GameMetadataResolver
import com.bachatas4.android.data.ParamSfoReader
```

Use **exact** match `it.relativePath == "sce_sys/param.sfo"` only (dump root is the selected folder). Do not match nested paths that could be wrong.

- [ ] **Step 2: Compile library module**

```bash
cd android/BachataS4 && ./gradlew :feature:library:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt
git commit -m "feat(android): use param.sfo title and id on game import"
```

---

### Task 5: Title backfill for existing library rows

**Files:**
- Modify: `android/BachataS4/core/database/src/main/kotlin/com/bachatas4/android/database/GameDao.kt`
- Modify: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/GameRepository.kt`
- Create: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/GameRepositoryTitleBackfillTest.kt`
- Modify: `android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt` (call backfill once on open)

**Interfaces:**
- Consumes: on-disk `param.sfo` via `GameIconPaths.paramSfo`, `ParamSfoReader`
- Produces:
  ```kotlin
  // GameDao
  @Query("UPDATE games SET title = :title WHERE id = :id")
  suspend fun updateTitle(id: String, title: String): Int

  // GameRepository
  suspend fun backfillTitlesFromSfo()
  ```

- [ ] **Step 1: Write the failing backfill test**

`GameRepository` currently needs Room. Prefer a focused unit that tests pure backfill logic if Room is heavy; simplest approach used elsewhere is TemporaryFolder + in-memory Room.

Check whether other tests use Room — if not, extract a package-visible helper:

```kotlin
// in GameRepository.kt or GameTitleBackfill.kt
internal fun titlesToUpdate(
    games: List<Pair<String /*id*/, String /*storedTitle*/>>,
    sfoTitleFor: (String /*id*/) -> String?,
): List<Pair<String, String>> =
    games.mapNotNull { (id, stored) ->
        val next = sfoTitleFor(id)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        if (next == stored) null else id to next
    }
```

Test:

```kotlin
class GameTitleBackfillTest {
    @Test
    fun onlyReturnsChangedTitles() {
        val updates = titlesToUpdate(
            games = listOf(
                "CUSA1" to "folder-name",
                "CUSA2" to "Already Good",
                "CUSA3" to "old",
            ),
            sfoTitleFor = { id ->
                when (id) {
                    "CUSA1" -> "Real Name"
                    "CUSA2" -> "Already Good"
                    "CUSA3" -> null
                    else -> null
                }
            },
        )
        assertEquals(listOf("CUSA1" to "Real Name"), updates)
    }
}
```

- [ ] **Step 2: Run RED**

```bash
cd android/BachataS4 && ./gradlew :core:data:testDebugUnitTest --tests 'com.bachatas4.android.data.GameTitleBackfillTest'
```

- [ ] **Step 3: Implement helper + DAO + repository method**

Add to `GameDao.kt`:

```kotlin
@Query("UPDATE games SET title = :title WHERE id = :id")
suspend fun updateTitle(id: String, title: String): Int
```

Add to `GameRepository.kt`:

```kotlin
suspend fun backfillTitlesFromSfo() {
    val games = gameDao.observeAll() // cannot collect once easily
}
```

Better: add DAO query:

```kotlin
@Query("SELECT * FROM games")
suspend fun getAll(): List<GameEntity>
```

Then:

```kotlin
suspend fun backfillTitlesFromSfo() {
    val entities = gameDao.getAll()
    val updates = titlesToUpdate(
        games = entities.map { it.id to it.title },
        sfoTitleFor = { id ->
            val entity = entities.first { it.id == id }
            val file = GameIconPaths.paramSfo(context.filesDir, entity.relativePath)
            if (!file.isFile) return@titlesToUpdate null
            runCatching { ParamSfoReader.parse(file.readBytes()).title }.getOrNull()
        },
    )
    updates.forEach { (id, title) -> gameDao.updateTitle(id, title) }
}
```

Put `titlesToUpdate` in the same file as `GameRepository` or in `GameTitleBackfill.kt` as `internal fun`.

- [ ] **Step 4: Call backfill from library**

In `LibraryScreen` `LaunchedEffect(dependencies)`:

```kotlin
LaunchedEffect(dependencies) {
    runCatching { dependencies.gameRepository().backfillTitlesFromSfo() }
    dependencies.gameRepository().observeGames().collectLatest(viewModel::setGames)
}
```

- [ ] **Step 5: Run tests GREEN**

```bash
cd android/BachataS4 && ./gradlew :core:data:testDebugUnitTest :core:database:compileDebugKotlin :feature:library:compileDebugKotlin
```

Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/core/database/src/main/kotlin/com/bachatas4/android/database/GameDao.kt \
  android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/GameRepository.kt \
  android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/GameTitleBackfill.kt \
  android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/GameTitleBackfillTest.kt \
  android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt
git commit -m "feat(android): backfill library titles from on-disk param.sfo"
```

---

### Task 6: Show `icon0.png` on library cards and selected panel

**Files:**
- Modify: `android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt`
- Create (optional pure helper test): path resolution already in Task 3

**Interfaces:**
- Consumes: `GameIconPaths.icon0(filesDir, game.relativePath)`
- Produces: Compose UI that shows decoded PNG or placeholder

- [ ] **Step 1: Add a small cover composable in `LibraryScreen.kt`**

```kotlin
@Composable
private fun GameCover(
    relativePath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(relativePath) {
        val file = GameIconPaths.icon0(context.filesDir, relativePath)
        if (!file.isFile) null
        else runCatching {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BachataPalette.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Text("GAME", color = BachataPalette.Secondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

Imports:

```kotlin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.bachatas4.android.data.GameIconPaths
```

- [ ] **Step 2: Use `GameCover` in `LibraryGameCard`**

Replace the placeholder `Box { Text("GAME") }` with:

```kotlin
GameCover(
    relativePath = game.relativePath,
    modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
)
```

- [ ] **Step 3: Use `GameCover` in the selected-game panel**

Inside the selected branch `Column`, above the title (or in a `Row` with title), add:

```kotlin
GameCover(
    relativePath = selected.relativePath,
    modifier = Modifier
        .width(120.dp)
        .aspectRatio(0.75f),
)
```

Keep existing title / id / buttons; title text still uses `selected.title` (now correct from import/backfill).

- [ ] **Step 4: Compile**

```bash
cd android/BachataS4 && ./gradlew :feature:library:compileDebugKotlin :feature:library:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; existing `LibraryViewModelTest` PASS.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt
git commit -m "feat(android): show local icon0.png cover art in game library"
```

---

### Task 7: Full module verification

**Files:** none new — verification only

- [ ] **Step 1: Run unit tests for touched modules**

```bash
cd android/BachataS4 && ./gradlew \
  :core:data:testDebugUnitTest \
  :feature:library:testDebugUnitTest \
  :core:database:compileDebugKotlin \
  lintDebug
```

Expected: all tests PASS; lint issues introduced by this work: 0 (or only pre-existing unrelated).

- [ ] **Step 2: Manual device check (when hardware available)**

1. Import a real dump folder that contains `sce_sys/param.sfo` and `sce_sys/icon0.png`.
2. Confirm library card title matches `TITLE`, id matches `TITLE_ID`, cover shows the icon.
3. Confirm a game imported earlier with a folder-name title updates after reopening the library (if `param.sfo` is under `filesDir/games/...`).
4. Confirm a folder without SFO still imports with folder-name fallback and placeholder cover.

- [ ] **Step 3: Final commit only if verification left uncommitted fixes**

If Step 1 required fixes, commit them with a focused message; otherwise done.

---

## Spec coverage checklist

| Spec requirement | Task |
| --- | --- |
| Parse `TITLE` / `TITLE_ID` from `param.sfo` | Task 1 |
| Fallback chain folder / CUSA / UUID / "Imported game" | Task 2 |
| Prefer SFO before copy on import | Task 4 |
| Cover path derived from `relativePath` + `icon0.png` | Task 3, 6 |
| No Room cover column / no DB version bump for cover | Tasks 3–6 (constraint) |
| Title backfill for existing rows; no id rename | Task 5 |
| Missing SFO/icon does not fail import | Task 2, 4, 6 |
| Unit tests for reader, import metadata, backfill | Tasks 1, 2, 5 |
| Library card + selected panel cover | Task 6 |

## Self-review notes

- No online covers; no `pic0`/`pic1`.
- `ContentImporter.validateGameId` still enforced — resolver rejects unsafe `TITLE_ID`.
- `Game` model unchanged; presentation derives icon file path.
- SFO fixture endianness must match reader; Task 1 owns that consistency.
