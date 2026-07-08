package com.bachatas4.android

import com.bachatas4.android.data.RuntimeProfileStore
import com.bachatas4.android.runtime.settings.CompatibilityConstraint
import com.bachatas4.android.runtime.settings.ProfileScope
import com.bachatas4.android.runtime.settings.RuntimeSettingCatalog
import com.bachatas4.android.runtime.settings.RuntimeSettingSpec
import com.bachatas4.android.runtime.settings.SettingKind
import com.bachatas4.android.runtime.settings.ValueSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeLaunchProfileProviderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val devKit = spec("general.dev_kit_mode", "General.dev_kit_mode", SettingKind.BOOLEAN, JsonPrimitive(false))
    private val boxLog = spec("box64.log", "BOX64_LOG", SettingKind.ENUM, JsonPrimitive("0"), listOf("0", "1", "2"))
    private val catalog = RuntimeSettingCatalog(listOf(devKit), listOf(boxLog))

    @Test
    fun resolvesGameOverridesAndCompatibilityConstraints() = runTest {
        val store = RuntimeProfileStore(temporaryFolder.root)
        store.update(ProfileScope.Global) { it.copy(values = mapOf(boxLog.id to JsonPrimitive("1"))) }
        store.update(ProfileScope.Game("CUSA00001")) {
            it.copy(values = mapOf(boxLog.id to JsonPrimitive("2"), devKit.id to JsonPrimitive(false)))
        }
        val provider = RuntimeLaunchProfileProvider(
            store,
            catalog,
            mapOf(devKit.id to CompatibilityConstraint(JsonPrimitive(true), "Required on Android")),
        )

        val resolved = provider.resolve("CUSA00001")

        assertEquals(ValueSource.GAME, resolved.settings.getValue(boxLog.id).source)
        assertEquals(ValueSource.COMPATIBILITY, resolved.settings.getValue(devKit.id).source)
        assertEquals(mapOf("BOX64_LOG" to "2"), provider.box64Environment(resolved))
    }

    @Test
    fun launchOwnedUnknownBox64ValueIsRejected() = runTest {
        val store = RuntimeProfileStore(temporaryFolder.root)
        store.update(ProfileScope.Global) {
            it.copy(unknownBox64 = mapOf("BOX64_PATH" to "/tmp/escape"))
        }
        val provider = RuntimeLaunchProfileProvider(store, catalog, emptyMap())

        val resolved = provider.resolve("CUSA00001")
        val error = assertThrows(IllegalArgumentException::class.java) {
            provider.box64Environment(resolved)
        }

        assertFalse(error.message.isNullOrBlank())
    }

    private fun spec(
        id: String,
        nativeKey: String,
        kind: SettingKind,
        defaultValue: JsonPrimitive,
        choices: List<String> = emptyList(),
    ) = RuntimeSettingSpec(
        id = id,
        nativeKey = nativeKey,
        section = if (nativeKey.startsWith("BOX64_")) "Box64" else nativeKey.substringBefore('.'),
        category = "Test",
        title = id,
        help = id,
        kind = kind,
        defaultValue = defaultValue,
        choices = choices,
    )
}
