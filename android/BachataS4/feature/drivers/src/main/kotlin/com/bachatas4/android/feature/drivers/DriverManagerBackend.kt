package com.bachatas4.android.feature.drivers

import android.content.Context
import com.bachatas4.android.runtime.driver.DriverPackageSource
import com.bachatas4.android.runtime.driver.DriverRegistry
import com.bachatas4.android.runtime.driver.InstalledDriver
import com.bachatas4.android.runtime.driver.TurnipDownloadManager
import com.bachatas4.android.runtime.driver.TurnipPackageInstaller
import com.bachatas4.android.runtime.driver.TurnipReleaseAsset
import com.bachatas4.android.runtime.driver.TurnipReleaseClient
import com.bachatas4.android.runtime.driver.UrlConnectionDriverAssetTransport
import com.bachatas4.android.runtime.driver.UrlConnectionHttpTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayInputStream

interface DriverManagerBackend {
    fun installed(): List<InstalledDriver>
    fun releases(force: Boolean): List<TurnipReleaseAsset>
    fun download(asset: TurnipReleaseAsset, progress: (Long, Long) -> Unit): InstalledDriver
    fun importZip(bytes: ByteArray, assetName: String): InstalledDriver
    fun remove(id: String): Boolean
}

private class DefaultDriverManagerBackend(context: Context) : DriverManagerBackend {
    private val root = context.filesDir.toPath().resolve("vulkan-drivers/installed")
    private val registry = DriverRegistry(root)
    private val installer = TurnipPackageInstaller(root)
    private val releaseClient = TurnipReleaseClient(
        UrlConnectionHttpTransport(),
        context.cacheDir.toPath().resolve("turnip-releases.json"),
    )
    private val downloader = TurnipDownloadManager(installer, UrlConnectionDriverAssetTransport())

    override fun installed() = registry.listInstalled()
    override fun releases(force: Boolean) = releaseClient.listReleases(force)
    override fun download(asset: TurnipReleaseAsset, progress: (Long, Long) -> Unit) = downloader.download(asset, progress)
    override fun importZip(bytes: ByteArray, assetName: String) = installer.install(
        ByteArrayInputStream(bytes),
        DriverPackageSource(assetName = assetName),
    )
    override fun remove(id: String) = registry.remove(id)
}

@Module
@InstallIn(SingletonComponent::class)
object DriverManagerModule {
    @Provides
    fun backend(@ApplicationContext context: Context): DriverManagerBackend = DefaultDriverManagerBackend(context)
}
