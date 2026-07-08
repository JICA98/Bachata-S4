package com.bachatas4.android.runtime.config

import com.bachatas4.android.runtime.settings.ResolvedRuntimeProfile
import com.bachatas4.android.runtime.settings.ShadPs4JsonCodec
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object ShadPs4ConfigManager {
    private val json = Json { prettyPrint = true }

    fun applyAndroidCompatibilityProfile(runtimeRoot: Path) {
        val config = runtimeRoot.resolve(".local/share/shadPS4/config.json")
        Files.createDirectories(config.parent)
        val root = if (Files.exists(config)) {
            json.parseToJsonElement(Files.newBufferedReader(config).use { it.readText() }).jsonObject
        } else {
            JsonObject(emptyMap())
        }
        val vulkan = root["Vulkan"]?.jsonObject ?: JsonObject(emptyMap())
        val updatedVulkan = buildJsonObject {
            vulkan.forEach(::put)
            put("pipeline_cache_enabled", true)
            put("pipeline_cache_archived", false)
            put("vkvalidation_core_enabled", false)
        }
        val general = buildJsonObject {
            root["General"]?.jsonObject?.forEach(::put)
            put("dev_kit_mode", true)
        }
        val log = buildJsonObject {
            root["Log"]?.jsonObject?.forEach(::put)
            put("sync", false)
        }
        val updatedRoot = buildJsonObject {
            root.forEach(::put)
            put("General", general)
            put("Log", log)
            put("Vulkan", updatedVulkan)
        }
        val temporary = config.resolveSibling("${config.fileName}.tmp")
        Files.newBufferedWriter(temporary).use { writer -> writer.write(json.encodeToString(updatedRoot)) }
        Files.move(
            temporary,
            config,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    fun write(runtimeRoot: Path, profile: ResolvedRuntimeProfile) {
        val config = runtimeRoot.resolve(".local/share/shadPS4/config.json")
        Files.createDirectories(config.parent)
        var document = if (Files.isRegularFile(config)) {
            ShadPs4JsonCodec.parse(Files.readString(config))
        } else {
            ShadPs4JsonCodec.empty()
        }
        document = document.mergeUnknown(profile.unknownShadPs4)
        profile.settings.values
            .filter { it.spec.section != "Box64" && it.value != null }
            .sortedBy { it.spec.nativeKey }
            .forEach { setting ->
                document = document.set(
                    setting.spec.section,
                    setting.spec.nativeKey.substringAfter('.'),
                    requireNotNull(setting.value),
                )
            }
        val temporary = config.resolveSibling("${config.fileName}.tmp")
        Files.newBufferedWriter(temporary).use { it.write(document.render()) }
        try {
            Files.move(
                temporary,
                config,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
