package com.bachatas4.android.di

import android.content.Context
import com.bachatas4.android.feature.drivers.DriverManagerBackend
import com.bachatas4.android.feature.drivers.DriverManagerCapabilities
import com.bachatas4.android.runtime.driver.BundledTurnipInstaller
import com.bachatas4.android.runtime.driver.BundledTurnipSpec
import com.bachatas4.android.runtime.driver.InstalledDriver
import com.bachatas4.android.runtime.driver.TurnipReleaseAsset
import com.bachatas4.android.runtime.process.VulkanDriverConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.file.Path
import javax.inject.Singleton

/**
 * Play Store driver backend: only the APK-bundled Turnip 26.1.0 package.
 * No catalogue fetch, archive download, or ZIP import.
 */
internal class PlaystoreDriverManagerBackend(context: Context) : DriverManagerBackend {
    private val assets = context.assets
    private val root = context.filesDir.toPath().resolve("vulkan-drivers/installed")
    private val bundled = BundledTurnipInstaller(
        registryRoot = root,
        openAsset = { assets.open(BundledTurnipSpec.ASSET_PATH) },
    )

    override fun capabilities() = DriverManagerCapabilities(
        remoteCatalogEnabled = false,
        importEnabled = false,
        deleteEnabled = false,
        statusMessage = PLAY_STATUS,
    )

    override fun installed(): List<InstalledDriver> = listOf(bundled.ensureInstalled())

    override fun releases(force: Boolean): List<TurnipReleaseAsset> = emptyList()

    override fun download(asset: TurnipReleaseAsset, progress: (Long, Long) -> Unit): InstalledDriver {
        throw UnsupportedOperationException(REMOTE_DISABLED)
    }

    override fun importZip(bytes: ByteArray, assetName: String): InstalledDriver {
        throw UnsupportedOperationException(REMOTE_DISABLED)
    }

    override fun remove(id: String): Boolean {
        throw UnsupportedOperationException("The bundled Turnip driver cannot be removed")
    }

    override fun configurationFor(driverId: String, runtimeRoot: Path): VulkanDriverConfiguration {
        // Play has no driver picker — always launch with the bundled Turnip 26.1.0 package.
        val driver = bundled.ensureInstalled()
        return VulkanDriverConfiguration.resolve(driver, runtimeRoot)
    }

    override fun autoSelectDriverId(): String = bundled.ensureInstalled().metadata.id

    companion object {
        const val REMOTE_DISABLED =
            "Remote and imported drivers are not available in this build. " +
                "Turnip updates are delivered through app updates."
        const val PLAY_STATUS =
            "Turnip 26.1.0 is included with this version of the app. " +
                "Driver updates are delivered through app updates."
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PlaystoreDriverModule {
    @Provides
    @Singleton
    fun backend(@ApplicationContext context: Context): DriverManagerBackend =
        PlaystoreDriverManagerBackend(context)
}
