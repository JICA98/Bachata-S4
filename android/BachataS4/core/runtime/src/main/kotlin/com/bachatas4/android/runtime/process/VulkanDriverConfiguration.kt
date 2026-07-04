package com.bachatas4.android.runtime.process

import java.nio.file.Path

enum class RuntimeVulkanDriver {
    TURNIP_25_0_0,
    TURNIP_26_1_0,
}

object RuntimeVulkanDriverPreference {
    const val FILE_NAME = "emulator_settings"
    const val KEY = "vulkan_driver"
    val DEFAULT = RuntimeVulkanDriver.TURNIP_26_1_0

    fun decode(value: String?): RuntimeVulkanDriver =
        RuntimeVulkanDriver.entries.firstOrNull { it.name == value } ?: DEFAULT
}

data class VulkanDriverConfiguration(
    val box64Mode: Box64Mode,
    val environment: Map<String, String>,
) {
    companion object {
        fun resolve(driver: RuntimeVulkanDriver, runtimeRoot: Path): VulkanDriverConfiguration =
            when (driver) {
                RuntimeVulkanDriver.TURNIP_25_0_0 -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.HOST_GLIBC,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to runtimeRoot.resolve("host/libvulkan.so.1").toString(),
                        "VK_ICD_FILENAMES" to runtimeRoot.resolve("host/vulkan/icd.d/turnip-25.0.0.json").toString(),
                    ),
                )
                RuntimeVulkanDriver.TURNIP_26_1_0 -> VulkanDriverConfiguration(
                    box64Mode = Box64Mode.HOST_GLIBC,
                    environment = mapOf(
                        "SDL_VULKAN_LIBRARY" to runtimeRoot.resolve("host/libvulkan.so.1").toString(),
                        "VK_ICD_FILENAMES" to runtimeRoot.resolve("host/vulkan/icd.d/turnip-26.1.0.json").toString(),
                    ),
                )
            }
    }
}
