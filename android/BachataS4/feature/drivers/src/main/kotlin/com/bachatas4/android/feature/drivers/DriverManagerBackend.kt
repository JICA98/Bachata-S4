package com.bachatas4.android.feature.drivers

import com.bachatas4.android.runtime.driver.InstalledDriver
import com.bachatas4.android.runtime.driver.TurnipReleaseAsset
import com.bachatas4.android.runtime.process.VulkanDriverConfiguration
import java.nio.file.Path

data class DriverManagerCapabilities(
    val remoteCatalogEnabled: Boolean,
    val importEnabled: Boolean,
    val deleteEnabled: Boolean,
    /** Shown in the drivers screen header when non-null. */
    val statusMessage: String? = null,
)

/**
 * Driver catalogue / install backend.
 *
 * Play Store builds provide a backend that only surfaces the bundled Turnip package.
 * Non-Play builds keep remote download + ZIP import.
 */
interface DriverManagerBackend {
    fun capabilities(): DriverManagerCapabilities
    fun installed(): List<InstalledDriver>
    fun releases(force: Boolean): List<TurnipReleaseAsset>
    fun download(asset: TurnipReleaseAsset, progress: (Long, Long) -> Unit): InstalledDriver
    fun importZip(bytes: ByteArray, assetName: String): InstalledDriver
    fun remove(id: String): Boolean

    /**
     * Resolve Vulkan configuration for launch. Play backends may remap stale driver ids
     * to the bundled package; non-Play backends load any installed id.
     */
    fun configurationFor(driverId: String, runtimeRoot: Path): VulkanDriverConfiguration

    /**
     * When non-null, setup should skip the driver picker and persist this id
     * (Play: bundled Turnip). F-Droid returns null and shows the picker.
     */
    fun autoSelectDriverId(): String? = null
}
