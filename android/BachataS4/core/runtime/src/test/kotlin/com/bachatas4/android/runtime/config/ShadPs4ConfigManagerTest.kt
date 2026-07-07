package com.bachatas4.android.runtime.config

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShadPs4ConfigManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun enablesPersistentPipelineCacheWithoutReplacingExistingSettings() {
        val runtimeRoot = temporaryFolder.newFolder("runtime").toPath()
        val config = runtimeRoot.resolve(".local/share/shadPS4/config.json")
        Files.createDirectories(config.parent)
        Files.writeString(
            config,
            """{"General":{"console_language":1},"Vulkan":{"gpu_id":3,"pipeline_cache_enabled":false}}""",
        )

        ShadPs4ConfigManager.applyAndroidCompatibilityProfile(runtimeRoot)

        val root = Json.parseToJsonElement(Files.readString(config)).jsonObject
        assertTrue(root.getValue("General").jsonObject.getValue("console_language").jsonPrimitive.content == "1")
        val vulkan = root.getValue("Vulkan").jsonObject
        assertTrue(vulkan.getValue("gpu_id").jsonPrimitive.content == "3")
        assertTrue(vulkan.getValue("pipeline_cache_enabled").jsonPrimitive.boolean)
        assertFalse(vulkan.getValue("pipeline_cache_archived").jsonPrimitive.boolean)
        assertFalse(vulkan.getValue("vkvalidation_core_enabled").jsonPrimitive.boolean)
        assertTrue(root.getValue("General").jsonObject.getValue("dev_kit_mode").jsonPrimitive.boolean)
        assertFalse(root.getValue("Log").jsonObject.getValue("sync").jsonPrimitive.boolean)
    }

    @Test
    fun createsMinimalConfigWhenShadPs4HasNotRun() {
        val runtimeRoot = temporaryFolder.newFolder("runtime-new").toPath()

        ShadPs4ConfigManager.applyAndroidCompatibilityProfile(runtimeRoot)

        val config = runtimeRoot.resolve(".local/share/shadPS4/config.json")
        val vulkan = Json.parseToJsonElement(Files.readString(config)).jsonObject
            .getValue("Vulkan").jsonObject
        assertTrue(vulkan.getValue("pipeline_cache_enabled").jsonPrimitive.boolean)
    }
}
