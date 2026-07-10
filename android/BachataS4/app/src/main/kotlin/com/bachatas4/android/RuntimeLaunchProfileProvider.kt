package com.bachatas4.android

import com.bachatas4.android.data.RuntimeProfileStore
import com.bachatas4.android.runtime.settings.Box64EnvironmentCodec
import com.bachatas4.android.runtime.settings.CompatibilityConstraint
import com.bachatas4.android.runtime.settings.ProfileScope
import com.bachatas4.android.runtime.settings.ResolvedRuntimeProfile
import com.bachatas4.android.runtime.settings.RuntimeProfileResolver
import com.bachatas4.android.runtime.settings.RuntimeSettingCatalog
import com.bachatas4.android.runtime.settings.SettingKind
import com.bachatas4.android.runtime.settings.ValueSource
import com.bachatas4.android.runtime.driver.DriverRegistry
import com.bachatas4.android.runtime.process.RuntimeVulkanDriver
import com.bachatas4.android.runtime.process.VulkanDriverConfiguration
import java.nio.file.Path
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

class RuntimeLaunchProfileProvider internal constructor(
    private val store: RuntimeProfileStore,
    private val catalog: RuntimeSettingCatalog,
    constraints: Map<String, CompatibilityConstraint>,
) {
    private val resolver = RuntimeProfileResolver(catalog.shadPs4 + catalog.box64, constraints)

    @Inject
    constructor(store: RuntimeProfileStore) : this(
        store,
        RuntimeSettingCatalog.loadFromResources(),
        androidCompatibilityConstraints(),
    )

    suspend fun resolve(gameId: String): ResolvedRuntimeProfile = resolver.resolve(
        store.load(ProfileScope.Global),
        store.load(ProfileScope.Game(gameId)),
    )

    fun box64Environment(profile: ResolvedRuntimeProfile): Map<String, String> {
        val environment = profile.unknownBox64.toMutableMap()
        profile.settings.values
            .filter { it.spec.section == "Box64" && it.source != ValueSource.DEFAULT && it.value != null }
            .forEach { setting ->
                val primitive = setting.value as? JsonPrimitive
                    ?: throw IllegalArgumentException("Invalid Box64 value for ${setting.spec.nativeKey}")
                environment[setting.spec.nativeKey] = when (setting.spec.kind) {
                    SettingKind.BOOLEAN -> if (primitive.booleanOrNull == true) "1" else "0"
                    else -> primitive.content
                }
            }
        val validated = Box64EnvironmentCodec.decode(Box64EnvironmentCodec.encode(environment)).toMutableMap()
        profile.box64Preset.environmentValue?.let { validated["BOX64_PROFILE"] = it }
        return validated
    }

    fun explicitSettingIds(profile: ResolvedRuntimeProfile): List<String> =
        profile.settings.values.filter { it.source != ValueSource.DEFAULT }.map { it.spec.id }.sorted()

    fun vulkanConfiguration(profile: ResolvedRuntimeProfile, runtimeRoot: Path, filesDir: Path): VulkanDriverConfiguration {
        if (profile.driverId == "system") return VulkanDriverConfiguration.resolve(RuntimeVulkanDriver.SYSTEM, runtimeRoot)
        val driver = DriverRegistry(filesDir.resolve("vulkan-drivers/installed")).resolve(profile.driverId)
            ?: throw MissingRuntimeDriverException(profile.driverId)
        return VulkanDriverConfiguration.resolve(driver, runtimeRoot)
    }

    private companion object {
        fun androidCompatibilityConstraints() = mapOf(
            "general.dev_kit_mode" to CompatibilityConstraint(JsonPrimitive(true), "Required by Android runtime"),
            "log.sync" to CompatibilityConstraint(JsonPrimitive(false), "Avoid blocking Android runtime logging"),
            "vulkan.pipeline_cache_enabled" to CompatibilityConstraint(JsonPrimitive(true), "Preserve Android pipeline cache"),
            "vulkan.pipeline_cache_archived" to CompatibilityConstraint(JsonPrimitive(false), "Use live Android pipeline cache"),
            "vulkan.vkvalidation_core_enabled" to CompatibilityConstraint(JsonPrimitive(false), "Validation layers are unavailable"),
        )
    }
}

class MissingRuntimeDriverException(val driverId: String) : IllegalStateException(
    "Selected Vulkan driver '$driverId' is not installed; open Turnip drivers and select another driver",
)
