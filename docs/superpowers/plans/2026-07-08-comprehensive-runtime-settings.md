# Comprehensive Runtime Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add complete global/per-game shadPS4 and Box64 configuration, trusted Turnip download/ZIP import without bundled drivers, four-controller mapping, and editable touch layouts to the Android app.

**Architecture:** Versioned declarative catalogs drive typed Compose controls and serialization. `core:data` persists sparse profiles, `core:runtime` validates and resolves them into shadPS4 TOML, Box64 environment, driver, and input snapshots, and feature modules render settings/driver/input workflows. Runtime launch consumes one immutable resolved profile.

**Tech Stack:** Kotlin 2.4, Jetpack Compose Material 3, Hilt, kotlinx.serialization, DataStore/file-backed atomic persistence, Room game lookup, Java `HttpURLConnection`, Node.js packaging/schema scripts, C++20 shadPS4 runtime bridge, JUnit 4.

## Global Constraints

- Android `minSdk = 31`, `targetSdk = 37`, Java 17, arm64-v8a only.
- Support global defaults plus sparse per-game overrides.
- Represent every persisted shadPS4 key and every documented `BOX64_*` variable.
- Keep launch-owned paths, sockets, and library wiring read-only.
- Trusted remote source is only `v3kt0r-87/Mesa-Turnip-Builder` and only `*-EMULATOR.zip` assets.
- Support local ZIP import; reject unsafe or incompatible archives.
- Do not package any Turnip shared object, ICD manifest, or archive in runtime assets or APK.
- Preserve unknown valid TOML keys and unknown `BOX64_*` entries.
- Use TDD: verify each new test fails for the intended reason before implementation.
- Preserve unrelated dirty worktree changes and submodule state.

---

### Task 1: Generate complete runtime setting catalogs

**Files:**
- Create: `runtime/settings/android-setting-metadata.json`
- Create: `runtime/scripts/generate-android-settings-catalog.mjs`
- Create: `runtime/tests/verify-android-settings-catalog.mjs`
- Create: `android/BachataS4/core/runtime/src/main/resources/runtime-settings/shadps4.json`
- Create: `android/BachataS4/core/runtime/src/main/resources/runtime-settings/box64.json`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/RuntimeSettingSpec.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/RuntimeSettingCatalog.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/settings/RuntimeSettingCatalogTest.kt`

**Interfaces:**
- Produces: `RuntimeSettingSpec`, `RuntimeSettingCatalog.shadPs4`, `RuntimeSettingCatalog.box64`, and checked-in JSON catalogs.
- Consumes: `src/core/emulator_settings.h`, `src/core/emulator_settings.cpp`, and `runtime/sources/box64/docs/USAGE.md`.

- [ ] **Step 1: Write failing catalog parser tests**

```kotlin
@Test fun `catalog rejects duplicate native keys`() {
    val error = assertFailsWith<IllegalArgumentException> {
        RuntimeSettingCatalog.parse("""[{"id":"a","nativeKey":"x"},{"id":"b","nativeKey":"x"}]""")
    }
    assertTrue(error.message!!.contains("duplicate nativeKey x"))
}

@Test fun `catalog loads bool enum number string path and list specs`() {
    val catalog = RuntimeSettingCatalog.loadFromResources()
    assertEquals(
        setOf(SettingKind.BOOLEAN, SettingKind.ENUM, SettingKind.INTEGER,
            SettingKind.DECIMAL, SettingKind.STRING, SettingKind.PATH, SettingKind.LIST),
        catalog.shadPs4.map { it.kind }.toSet(),
    )
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest --tests '*RuntimeSettingCatalogTest'`

Expected: FAIL because `RuntimeSettingCatalog` does not exist.

- [ ] **Step 3: Implement schema types and strict parser**

```kotlin
@Serializable
data class RuntimeSettingSpec(
    val id: String,
    val nativeKey: String,
    val section: String,
    val category: String,
    val title: String,
    val help: String,
    val kind: SettingKind,
    val defaultValue: JsonElement? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val choices: List<String> = emptyList(),
    val scope: SettingScope = SettingScope.GLOBAL_AND_GAME,
    val restartRequired: Boolean = true,
    val risk: SettingRisk = SettingRisk.NORMAL,
    val readOnlyReason: String? = null,
)

@Serializable enum class SettingKind { BOOLEAN, ENUM, INTEGER, DECIMAL, STRING, PATH, LIST }
@Serializable enum class SettingScope { GLOBAL_ONLY, GLOBAL_AND_GAME }
@Serializable enum class SettingRisk { NORMAL, ADVANCED, DANGEROUS }
```

Parser requirements: nonblank unique IDs/native keys, numeric range ordering, nonempty enum choices, default type/range validation, and mandatory read-only reason for launch-owned entries.

- [ ] **Step 4: Implement generator and metadata**

Generator must:

1. extract all persisted shadPS4 keys from settings structs plus `TransferSettings()` TOML sections;
2. extract every `### BOX64_*` heading and its documented value domain/default;
3. merge human labels, help, ranges, risk, scope, and read-only reasons from metadata;
4. fail when a discovered key lacks metadata or metadata references no discovered key;
5. sort by category then native key and write deterministic JSON.

`android-setting-metadata.json` must contain complete metadata for every discovered key; no wildcard omission list is allowed. Mark `BOX64_PATH`, `BOX64_LD_LIBRARY_PATH`, `BOX64_EMULATED_LIBS`, and `BOX64_LOAD_ADDR` read-only only where current launch code owns them.

- [ ] **Step 5: Generate catalogs and verify coverage**

Run: `node runtime/scripts/generate-android-settings-catalog.mjs`

Run: `node runtime/tests/verify-android-settings-catalog.mjs`

Expected: both exit 0; verifier reports zero missing, extra, or duplicate keys.

- [ ] **Step 6: Run catalog unit tests**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest --tests '*RuntimeSettingCatalogTest'`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add runtime/settings runtime/scripts/generate-android-settings-catalog.mjs runtime/tests/verify-android-settings-catalog.mjs android/BachataS4/core/runtime/src/main/resources/runtime-settings android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/settings
git commit -m "feat(android): add runtime setting catalogs"
```

### Task 2: Add versioned global and per-game profiles

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/RuntimeProfile.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/RuntimeProfileResolver.kt`
- Create: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/RuntimeProfileStore.kt`
- Modify: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/DataModule.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/settings/RuntimeProfileResolverTest.kt`
- Test: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/RuntimeProfileStoreTest.kt`

**Interfaces:**
- Produces: `RuntimeProfileStore.observe(scope)`, `update(scope, mutation)`, `export(scope)`, `import(scope, json)`, and `RuntimeProfileResolver.resolve(global, game)`.
- Consumes: catalogs from Task 1.

- [ ] **Step 1: Write merge/reset tests**

```kotlin
@Test fun `game override wins and reset inherits global`() {
    val global = RuntimeProfile(values = mapOf("gpu.nullGpu" to JsonPrimitive(true)))
    val game = RuntimeProfile(values = mapOf("gpu.nullGpu" to JsonPrimitive(false)))
    assertEquals(false, resolver.resolve(global, game).boolean("gpu.nullGpu"))
    assertEquals(true, resolver.resolve(global, game.copy(values = emptyMap())).boolean("gpu.nullGpu"))
}
```

Also test native defaults, compatibility constraints, unknown field preservation, invalid values, schema migration, import rejection, and atomic-write rollback.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest :core:data:testDebugUnitTest --tests '*RuntimeProfile*'`

Expected: FAIL because profile classes/store do not exist.

- [ ] **Step 3: Implement serializable sparse model**

```kotlin
@Serializable
data class RuntimeProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val values: Map<String, JsonElement> = emptyMap(),
    val unknownToml: Map<String, JsonElement> = emptyMap(),
    val unknownBox64: Map<String, String> = emptyMap(),
    val driverId: String? = null,
    val controllerSlots: List<ControllerProfile> = emptyList(),
    val touchLayoutId: String? = null,
)

sealed interface ProfileScope {
    data object Global : ProfileScope
    data class Game(val gameId: String) : ProfileScope
}
```

Reject game IDs outside `[A-Za-z0-9._-]+`. Store global at `files/settings/global.json` and games at `files/settings/games/<id>.json`. Write `.tmp`, fsync, backup old file, then atomic move.

- [ ] **Step 4: Implement resolver and migrations**

Return each effective value with `ValueSource.DEFAULT`, `GLOBAL`, `GAME`, or `COMPATIBILITY`. Compatibility constraints are a typed map with visible reasons; never mutate stored profiles.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest :core:data:testDebugUnitTest --tests '*RuntimeProfile*'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/settings android/BachataS4/core/data
git commit -m "feat(android): persist runtime profiles"
```

### Task 3: Serialize shadPS4 TOML and Box64 environment

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/LosslessTomlDocument.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings/Box64EnvironmentCodec.kt`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/config/ShadPs4ConfigManager.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/settings/LosslessTomlDocumentTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/settings/Box64EnvironmentCodecTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/config/ShadPs4ConfigManagerTest.kt`

**Interfaces:**
- Produces: `ShadPs4ConfigManager.write(runtimeHome, resolvedProfile)`, `validateRawToml(text)`, `applyRawToml(profile, text)`, `Box64EnvironmentCodec.decode/encode`.
- Consumes: `ResolvedRuntimeProfile` from Task 2.

- [ ] **Step 1: Add failing round-trip tests**

```kotlin
@Test fun `typed edit preserves unknown section and comment`() {
    val source = "# keep\n[Future]\nvalue = { nested = true }\n[GPU]\nnullGpu = false\n"
    val edited = LosslessTomlDocument.parse(source).set("GPU", "nullGpu", JsonPrimitive(true)).render()
    assertTrue(edited.contains("# keep"))
    assertTrue(edited.contains("value = { nested = true }"))
    assertTrue(edited.contains("nullGpu = true"))
}
```

Test all supported scalar/list forms, escaped strings, duplicate key rejection, malformed raw TOML, duplicate Box64 keys, launch-owned key rejection, and deterministic output.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest --tests '*Toml*' --tests '*Box64Environment*' --tests '*ShadPs4ConfigManager*'`

Expected: FAIL on missing codecs/new manager API.

- [ ] **Step 3: Implement lossless document and environment codec**

Parse section headers, assignments, comments, strings, booleans, numbers, arrays, and inline tables while retaining untouched source spans. Known edits replace only the assignment value span. Raw save must fully parse known values and retain unknown spans.

Box64 raw format is exactly one `BOX64_NAME=value` per nonblank, noncomment line. Reject duplicates, invalid names, embedded NUL/newline, and launch-owned keys.

- [ ] **Step 4: Replace compatibility-profile overwrite**

Remove unconditional hardcoded TOML replacement. Write a resolved config atomically, retaining required Android compatibility values as resolver constraints with reasons.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest --tests '*Toml*' --tests '*Box64Environment*' --tests '*ShadPs4ConfigManager*'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/settings android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/config android/BachataS4/core/runtime/src/test
git commit -m "feat(android): resolve runtime config"
```

### Task 4: Resolve profiles in runtime launch

**Files:**
- Create: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/RuntimeLaunchProfileProvider.kt`
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncher.kt`
- Test: `android/BachataS4/app/src/test/kotlin/com/bachatas4/android/RuntimeLaunchProfileProviderTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncherTest.kt`

**Interfaces:**
- Produces: `RuntimeLaunchProfileProvider.resolve(gameId): ResolvedRuntimeProfile`.
- Consumes: store/resolver/codecs from Tasks 2-3.

- [ ] **Step 1: Write failing launch precedence test**

Verify launch request contains app-owned base env plus user Box64 env, rejects overrides of `HOME`, `DISPLAY`, `BOX64_PATH`, and logs only schema version/setting IDs.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :app:testDebugUnitTest :core:runtime:testDebugUnitTest --tests '*RuntimeLaunchProfileProviderTest' --tests '*RuntimeProcessLauncherTest'`

Expected: FAIL because provider is absent.

- [ ] **Step 3: Implement provider and service integration**

Resolve before display/process startup. Write TOML, verify selected driver, build Box64 env, then construct `RuntimeProcessRequest`. Throw a typed configuration error before process launch on any invalid state.

- [ ] **Step 4: Run tests and verify GREEN**

Run: same command as Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/app android/BachataS4/core/runtime
git commit -m "feat(android): apply profiles at launch"
```

### Task 5: Add multi-version Turnip installer and registry

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/driver/InstalledDriver.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/driver/DriverRegistry.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/driver/TurnipPackageInstaller.kt`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/CustomVulkanDriverInstaller.kt`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/VulkanDriverConfiguration.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/driver/TurnipPackageInstallerTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/driver/DriverRegistryTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/VulkanDriverSelectionTest.kt`

**Interfaces:**
- Produces: `TurnipPackageInstaller.install(input, source): InstalledDriver`, `DriverRegistry.observeInstalled()`, `remove(id)`, `resolve(id)`.
- Consumes: app-private driver root and profile references.

- [ ] **Step 1: Add hostile archive tests**

Test traversal, absolute paths, duplicate names, too many entries, >64 MiB expansion, nested archive, non-ELF, wrong endianness/class/machine, non-glibc ABI, absent ICD/library, and interrupted atomic promotion. Add success fixtures for current `*-EMULATOR.zip` directory layouts and legacy flat `meta.json` imports.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest --tests '*TurnipPackageInstallerTest' --tests '*DriverRegistryTest' --tests '*VulkanDriverSelectionTest'`

Expected: FAIL because registry/installer do not exist.

- [ ] **Step 3: Implement immutable driver IDs and metadata**

```kotlin
@Serializable
data class InstalledDriver(
    val id: String,
    val displayName: String,
    val sourceRepository: String?,
    val releaseTag: String?,
    val assetName: String,
    val sha256: String,
    val installedAtMs: Long,
    val libraryRelativePath: String,
    val icdRelativePath: String,
)
```

ID is `turnip-` plus first 16 SHA-256 hex characters. Rewrite ICD to app-private final path after promotion. Preserve existing driver when staging fails.

- [ ] **Step 4: Replace fixed driver enum resolution**

Use `system` or registry ID. Migrate legacy `CUSTOM` after revalidation. Do not silently resolve removed bundled enum values.

- [ ] **Step 5: Run tests and verify GREEN**

Run: same command as Step 2.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/core/runtime
git commit -m "feat(android): manage installed Turnip drivers"
```

### Task 6: Add trusted GitHub release client and downloader

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/driver/TurnipReleaseClient.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/driver/TurnipDownloadManager.kt`
- Modify: `android/BachataS4/app/src/main/AndroidManifest.xml`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/driver/TurnipReleaseClientTest.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/driver/TurnipDownloadManagerTest.kt`

**Interfaces:**
- Produces: `listReleases(forceRefresh)`, `download(asset, progress)`, cached release metadata.
- Consumes: installer from Task 5.

- [ ] **Step 1: Write failing HTTP/cache tests**

Inject a small `HttpTransport` interface. Test filtering to repository `v3kt0r-87/Mesa-Turnip-Builder`, `-EMULATOR.zip`, HTTPS GitHub asset URLs, conditional ETag requests, 304, 403 rate limits, malformed JSON, cancellation, length mismatch, and offline cache fallback.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest --tests '*TurnipReleaseClientTest' --tests '*TurnipDownloadManagerTest'`

Expected: FAIL because clients do not exist.

- [ ] **Step 3: Implement release models/client**

```kotlin
data class TurnipReleaseAsset(
    val releaseTag: String,
    val publishedAt: String,
    val name: String,
    val size: Long,
    val downloadUrl: String,
)
```

Use GitHub Releases endpoint, fixed owner/repository constants, 15-second connect/read timeouts, `User-Agent: BachataS4`, ETag cache, 24-hour freshness, and streamed download into installer staging. Do not store GitHub credentials.

Add only `android.permission.INTERNET`; do not add broad storage permissions.

- [ ] **Step 4: Run tests and verify GREEN**

Run: same command as Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/driver android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/driver android/BachataS4/app/src/main/AndroidManifest.xml
git commit -m "feat(android): fetch trusted Turnip releases"
```

### Task 7: Remove all bundled Turnip payloads

**Files:**
- Modify: `runtime/scripts/package-runtime.mjs`
- Modify: `runtime/locks/runtime-inputs.lock.json`
- Modify: `runtime/tests/verify-runtime.mjs`
- Create: `runtime/tests/verify-no-bundled-turnip.mjs`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/VulkanDriverConfiguration.kt`
- Modify: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/process/RuntimeVulkanDriverPreferenceTest.kt`

**Interfaces:**
- Produces: managed runtime with Vulkan loader but no Turnip payload; verifier callable for runtime directory, runtime ZIP, or APK listing.
- Consumes: system/registry driver selection from Task 5.

- [ ] **Step 1: Write failing no-Turnip verifier**

Verifier rejects case-insensitive path/name matches for Turnip archives, `vulkan.ad07xx.so`, `libvulkan_freedreno.so`, Turnip ICD manifests, and known runtime driver directories. Include positive fixtures proving loader-only `libvulkan.so.1` is allowed.

- [ ] **Step 2: Run verifier and confirm RED against current package**

Run: `node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs`

Expected: FAIL listing existing bundled Turnip paths.

- [ ] **Step 3: Remove Turnip inputs/extraction/copy logic**

Delete Turnip archive constants, hash checks, extraction directories, ICD generation, driver copies, manifest components, and locked Turnip input. Keep host/guest Vulkan loaders required for System Vulkan and downloaded glibc drivers.

- [ ] **Step 4: Repackage and verify GREEN**

Run: `node runtime/scripts/package-runtime.mjs`

Run: `node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json`

Run: `node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs`

Expected: all exit 0.

- [ ] **Step 5: Commit**

```bash
git add runtime/scripts/package-runtime.mjs runtime/locks/runtime-inputs.lock.json runtime/tests android/BachataS4/core/runtime
git commit -m "build(android): remove bundled Turnip drivers"
```

### Task 8: Build driver management feature module

**Files:**
- Modify: `android/BachataS4/settings.gradle.kts`
- Modify: `android/BachataS4/app/build.gradle.kts`
- Create: `android/BachataS4/feature/drivers/build.gradle.kts`
- Create: `android/BachataS4/feature/drivers/src/main/AndroidManifest.xml`
- Create: `android/BachataS4/feature/drivers/src/main/kotlin/com/bachatas4/android/feature/drivers/DriverManagerViewModel.kt`
- Create: `android/BachataS4/feature/drivers/src/main/kotlin/com/bachatas4/android/feature/drivers/DriverManagerScreen.kt`
- Test: `android/BachataS4/feature/drivers/src/test/kotlin/com/bachatas4/android/feature/drivers/DriverManagerViewModelTest.kt`

**Interfaces:**
- Produces: screen callbacks `onBack`, release refresh/download, ZIP import, select, and confirmed delete.
- Consumes: clients/registry/profile store from Tasks 2, 5, 6.

- [ ] **Step 1: Write failing ViewModel state tests**

Test loading/cached/offline/error states, progress, successful install/select, import cancellation, referenced delete confirmation, and fallback-to-system profile updates.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:drivers:testDebugUnitTest`

Expected: FAIL because module is absent.

- [ ] **Step 3: Add module and UI**

Use Material 3 lists for Installed and Available releases. Show source tag, asset name, size, hash, install time, selected scopes, download progress, retry, import ZIP, and delete. Never expose repository editing.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `cd android/BachataS4 && ./gradlew :feature:drivers:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/settings.gradle.kts android/BachataS4/app/build.gradle.kts android/BachataS4/feature/drivers
git commit -m "feat(android): add driver manager"
```

### Task 9: Replace current settings screen with schema-driven UI

**Files:**
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingEditorState.kt`
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingCards.kt`
- Modify: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsViewModel.kt`
- Modify: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/SettingsScreen.kt`
- Test: `android/BachataS4/feature/settings/src/test/kotlin/com/bachatas4/android/feature/settings/SettingsViewModelTest.kt`
- Create: `android/BachataS4/feature/settings/src/test/kotlin/com/bachatas4/android/feature/settings/SettingsSearchTest.kt`

**Interfaces:**
- Produces: category/search/detail routes and typed setting edit/reset events.
- Consumes: catalogs, profile store, game repository, and driver route callback.

- [ ] **Step 1: Write failing search/scope/edit tests**

```kotlin
@Test fun `search matches title native key and help`() = runTest {
    viewModel.setQuery("BOX64_DYNAREC_CALLRET")
    assertEquals("BOX64_DYNAREC_CALLRET", viewModel.state.value.results.single().spec.nativeKey)
}
```

Test category counts, Global/game scope, inherited source, reset, validation messages, autosave, undo, danger warning, and restart marker.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:settings:testDebugUnitTest`

Expected: FAIL on missing schema-driven state/events.

- [ ] **Step 3: Implement ViewModel and generated controls**

Render category cards: General, Log, Debug, Input, Audio, GPU, Vulkan, Android, Box64, Drivers, Raw Config. Use switches, enum dialogs, slider plus numeric entry, text/path controls, and list editors from `SettingKind`. Each card exposes native key/default/range/help/source/risk/restart information.

- [ ] **Step 4: Verify accessibility semantics**

Add content descriptions, stable semantics tags, minimum 48dp targets, state text independent of color, and dynamic type-safe layouts. Create a pure `SettingCardSemantics` mapper and JVM tests proving every generated control exposes label, native key, effective value, inheritance state, and enabled state.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `cd android/BachataS4 && ./gradlew :feature:settings:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/feature/settings
git commit -m "feat(android): add comprehensive settings UI"
```

### Task 10: Add raw editors and profile import/export

**Files:**
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/RawConfigViewModel.kt`
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/RawConfigScreen.kt`
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/ProfileTransfer.kt`
- Test: `android/BachataS4/feature/settings/src/test/kotlin/com/bachatas4/android/feature/settings/RawConfigViewModelTest.kt`
- Test: `android/BachataS4/feature/settings/src/test/kotlin/com/bachatas4/android/feature/settings/ProfileTransferTest.kt`

**Interfaces:**
- Produces: draft/validate/save for TOML and Box64; JSON profile import/export via Android document picker.
- Consumes: codecs/store from Tasks 2-3.

- [ ] **Step 1: Write failing draft safety tests**

Test invalid draft remains unsaved, valid save updates typed values, unknown keys survive, launch-owned Box64 entries reject, export excludes games/logs/drivers/paths, and import rejects newer schema/invalid game ID.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:settings:testDebugUnitTest --tests '*RawConfig*' --tests '*ProfileTransfer*'`

Expected: FAIL because screens/ViewModels do not exist.

- [ ] **Step 3: Implement explicit Validate/Save workflow**

Keep drafts only in ViewModel saved state. Save only after parser success and show conflicts by native key. Document picker writes/reads UTF-8 JSON without granting persistent URI permissions unnecessarily.

- [ ] **Step 4: Run tests and verify GREEN**

Run: same command as Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/BachataS4/feature/settings
git commit -m "feat(android): add raw config editors"
```

### Task 11: Add persistent four-slot controller mappings

**Files:**
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/input/ControllerProfile.kt`
- Create: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/input/ControllerBindingResolver.kt`
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/input/ControllerMapper.kt`
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/input/ControllerMappingViewModel.kt`
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/input/ControllerMappingScreen.kt`
- Test: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/input/ControllerBindingResolverTest.kt`
- Test: `android/BachataS4/feature/settings/src/test/kotlin/com/bachatas4/android/feature/settings/input/ControllerMappingViewModelTest.kt`

**Interfaces:**
- Produces: four resolved slot mappings from Android events to snapshots.
- Consumes: profile persistence from Task 2.

- [ ] **Step 1: Write failing mapping tests**

Test descriptor+vendor/product identity, transient device-ID changes, sequential capture, per-binding capture, conflict replace/cancel, dead zone, inversion, trigger threshold, auto-map, clear, disconnect neutralization, and per-game inheritance.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest :feature:settings:testDebugUnitTest --tests '*ControllerBinding*' --tests '*ControllerMapping*'`

Expected: FAIL because profiles/resolver/UI are missing.

- [ ] **Step 3: Implement stable mapping model**

```kotlin
@Serializable data class ControllerDeviceKey(
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val fallbackName: String,
)

@Serializable data class ControllerProfile(
    val device: ControllerDeviceKey? = null,
    val bindings: Map<String, PhysicalBinding> = emptyMap(),
    val deadZone: Float = 0.08f,
    val invertAxes: Set<String> = emptySet(),
    val triggerThreshold: Float = 0.5f,
    val vibrationEnabled: Boolean = true,
    val motionEnabled: Boolean = false,
)
```

- [ ] **Step 4: Build capture/remap screen**

Cover sticks/L3/R3, D-pad, face buttons, L1/L2/R1/R2, Options, Share/touchpad, vibration, and motion. Show four slots and current device identity.

- [ ] **Step 5: Run tests and verify GREEN**

Run: same command as Step 2.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/input android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/input android/BachataS4/feature/settings
git commit -m "feat(android): add controller mapping"
```

### Task 12: Extend control protocol to four controller slots

**Files:**
- Modify: `android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/input/ControllerFrameEncoder.kt`
- Modify: `android/BachataS4/core/runtime/src/test/kotlin/com/bachatas4/android/runtime/input/ControllerFrameEncoderTest.kt`
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt`
- Modify: `src/platform/bachata/controller_snapshot.h`
- Modify: `src/platform/bachata/controller_snapshot.cpp`
- Modify: `src/platform/bachata/runtime_client.cpp`
- Create: `tests/platform/test_bachata_controller_snapshot.cpp`
- Modify: `tests/CMakeLists.txt`

**Interfaces:**
- Produces: protocol line `BACHATA/1 INPUT slot=<0..3> seq=<n> ...`; native application to corresponding `GameControllers` slot.
- Consumes: resolved mappings from Task 11.

- [ ] **Step 1: Update Kotlin tests first**

```kotlin
@Test fun `encodes slot and independent sequence`() {
    val line = ControllerFrameEncoder().encode(slot = 2, ControllerSnapshot.Neutral)!!.decodeToString()
    assertTrue(line.startsWith("BACHATA/1 INPUT slot=2 seq=1 "))
}
```

Test slot bounds and independent last-snapshot suppression per slot.

- [ ] **Step 2: Add failing native parser tests**

Test slots 0-3, reject 4/negative/missing slot, retain slot-0 compatibility for old lines during one migration version, and apply neutral disconnect snapshots.

- [ ] **Step 3: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :core:runtime:testDebugUnitTest --tests '*ControllerFrameEncoderTest'`

Run: `cmake --build runtime/build/bachata-runtime-tests --target shadps4_bachata_runtime_test && runtime/build/bachata-runtime-tests/tests/shadps4_bachata_runtime_test`

Expected: both fail on absent slot support.

- [ ] **Step 4: Implement protocol and service sinks**

Change managed session/controller sink to `(slot: Int, snapshot: ControllerSnapshot) -> Unit`. Native parser returns slot plus snapshot and dispatches to controller index. Keep protocol version 1 and accept legacy no-slot input as slot 0.

- [ ] **Step 5: Run tests and verify GREEN**

Run both Step 3 commands.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/core/runtime android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service src/platform/bachata tests
git commit -m "feat(runtime): support four controller slots"
```

### Task 13: Replace fixed touch overlay with editable layouts

**Files:**
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/controller/TouchLayout.kt`
- Create: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/controller/TouchLayoutRenderer.kt`
- Create: `android/BachataS4/feature/settings/src/main/kotlin/com/bachatas4/android/feature/settings/input/TouchLayoutEditorScreen.kt`
- Modify: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/controller/FixedControllerOverlay.kt`
- Modify: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/controller/TouchControllerState.kt`
- Test: `android/BachataS4/feature/session/src/test/kotlin/com/bachatas4/android/feature/session/controller/TouchLayoutTest.kt`
- Test: `android/BachataS4/feature/session/src/test/kotlin/com/bachatas4/android/feature/session/controller/TouchControllerStateTest.kt`

**Interfaces:**
- Produces: same `TouchLayoutRenderer` and hit-test model for editor preview and live session.
- Consumes: global/per-game layout reference from Task 2.

- [ ] **Step 1: Write failing layout tests**

Test normalized bounds, safe-area clamp, minimum reachable size, z-order hit test, drag, resize, hide/show, reset, opacity/scale, analog centering, button groups, rotation/inset changes, serialization, and per-game inheritance.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :feature:session:testDebugUnitTest --tests '*TouchLayout*' --tests '*TouchControllerStateTest'`

Expected: FAIL because layout model is absent.

- [ ] **Step 3: Implement model and shared renderer**

```kotlin
@Serializable data class TouchControlPlacement(
    val control: String,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val zIndex: Int,
    val visible: Boolean = true,
)
```

Coordinates are normalized 0..1 after safe-area insets. Validation rejects nonfinite/out-of-range values and clamps only interactive editor gestures before save.

- [ ] **Step 4: Implement editor and live integration**

Add edit mode drag/resize/hide/reorder/reset, opacity, scale, vibration, analog centering, stick regions, and button-group order. Replace hardcoded constants in `FixedControllerOverlay`.

- [ ] **Step 5: Run tests and verify GREEN**

Run: same command as Step 2.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/feature/session android/BachataS4/feature/settings
git commit -m "feat(android): add touch layout editor"
```

### Task 14: Wire navigation, migrations, and user-facing errors

**Files:**
- Modify: `android/BachataS4/app/src/main/kotlin/com/bachatas4/android/BachataNavHost.kt`
- Modify: `android/BachataS4/feature/library/src/main/kotlin/com/bachatas4/android/feature/library/LibraryScreen.kt`
- Modify: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/SessionViewModel.kt`
- Modify: `android/BachataS4/feature/session/src/main/kotlin/com/bachatas4/android/feature/session/SessionScreen.kt`
- Create: `android/BachataS4/core/data/src/main/kotlin/com/bachatas4/android/data/LegacyRuntimeSettingsMigration.kt`
- Test: `android/BachataS4/app/src/test/kotlin/com/bachatas4/android/MainActivityRouteTest.kt`
- Test: `android/BachataS4/core/data/src/test/kotlin/com/bachatas4/android/data/LegacyRuntimeSettingsMigrationTest.kt`

**Interfaces:**
- Produces: routes for global settings, game settings, drivers, controller slot, touch editor, raw TOML, raw Box64; typed launch-blocking errors.
- Consumes: all prior tasks.

- [ ] **Step 1: Write failing route/migration tests**

Test each route, selected game scope, missing-driver navigation, invalid-profile error details, legacy `SharedPreferences` driver migration, legacy custom driver validation, bundled enum migration to System Vulkan, and one-time migration marker.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd android/BachataS4 && ./gradlew :app:testDebugUnitTest :core:data:testDebugUnitTest --tests '*RouteTest' --tests '*LegacyRuntimeSettingsMigrationTest'`

Expected: FAIL on absent routes/migration.

- [ ] **Step 3: Implement routes and typed errors**

Missing driver opens driver selector. Invalid key shows native key/value/constraint. Network errors offer retry while installed drivers remain visible. Runtime changes during active emulation display “applies next launch.”

- [ ] **Step 4: Implement idempotent migration**

Never delete legacy data until new profile/registry writes verify. Re-run safely after interruption. Back up migrated profile before future schema upgrades.

- [ ] **Step 5: Run tests and verify GREEN**

Run: same command as Step 2.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/BachataS4/app android/BachataS4/core/data android/BachataS4/feature/library android/BachataS4/feature/session
git commit -m "feat(android): integrate runtime settings"
```

### Task 15: Full verification and documentation

**Files:**
- Modify: `documents/android-building.md`
- Modify: `android/README.md`
- Modify: `runtime/tests/verify-runtime.mjs`
- Create: `runtime/tests/verify-apk-runtime.mjs`

**Interfaces:**
- Produces: reproducible build instructions and one APK verifier enforcing required assets plus no Turnip payload.
- Consumes: completed implementation.

- [ ] **Step 1: Add APK verification test**

Verifier opens APK/runtime ZIP listings and requires:

```text
assets/runtime/manifest.json
assets/runtime/runtime.zip
```

It rejects Turnip names/libraries using Task 7 rules and exits nonzero with exact offending entry.

- [ ] **Step 2: Run complete Android unit test suite**

Run: `cd android/BachataS4 && ./gradlew test`

Expected: BUILD SUCCESSFUL, zero failed tests.

- [ ] **Step 3: Rebuild/package managed runtime in required order**

Run from repository root:

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
node runtime/scripts/package-runtime.mjs
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
node runtime/tests/verify-no-bundled-turnip.mjs runtime/build/rootfs
```

Expected: all commands exit 0; no Turnip payload found.

- [ ] **Step 4: Lint and assemble**

Run: `cd android/BachataS4 && ./gradlew lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL with no lint errors.

- [ ] **Step 5: Verify APK contents**

Run: `node runtime/tests/verify-apk-runtime.mjs android/BachataS4/app/build/outputs/apk/debug/app-debug.apk`

Expected: required managed-runtime assets present; zero Turnip entries.

- [ ] **Step 6: Update docs**

Document settings scopes, driver download/import source, offline behavior, ZIP safety, profile transfer, controller mapping, touch editor, and explicit no-bundled-Turnip rule. Keep existing mandatory runtime packaging sequence intact.

- [ ] **Step 7: Review final diff and commit**

Run: `git diff --check`

Run: `git status --short`

Confirm only intended files are staged; do not include unrelated submodule or existing user changes.

```bash
git add documents/android-building.md android/README.md runtime/tests/verify-runtime.mjs runtime/tests/verify-apk-runtime.mjs
git commit -m "docs(android): document runtime settings"
```
