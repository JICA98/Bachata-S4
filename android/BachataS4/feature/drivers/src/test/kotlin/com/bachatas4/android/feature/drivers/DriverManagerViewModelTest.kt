package com.bachatas4.android.feature.drivers

import com.bachatas4.android.data.RuntimeProfileStore
import com.bachatas4.android.runtime.driver.DriverAbi
import com.bachatas4.android.runtime.driver.InstalledDriver
import com.bachatas4.android.runtime.driver.InstalledDriverMetadata
import com.bachatas4.android.runtime.driver.TurnipReleaseAsset
import com.bachatas4.android.runtime.settings.ProfileScope
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriverManagerViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun loadsTrustedReleasesAndSelectsInstalledDriver() = runBlocking {
        val backend = FakeBackend()
        val store = RuntimeProfileStore(temporaryFolder.root)
        val viewModel = DriverManagerViewModel(backend, store)
        withTimeout(2_000) { viewModel.state.first { it.available.isNotEmpty() } }
        viewModel.select(backend.driver.metadata.id)
        withTimeout(2_000) { viewModel.state.first { it.selectedDriverId == backend.driver.metadata.id } }
        assertEquals(backend.driver.metadata.id, store.load(ProfileScope.Global).driverId)
    }

    private class FakeBackend : DriverManagerBackend {
        val driver = InstalledDriver(
            InstalledDriverMetadata(
                id = "turnip-0123456789abcdef", displayName = "Turnip test", assetName = "test.zip",
                sha256 = "0".repeat(64), installedAtMs = 1, abi = DriverAbi.ANDROID_BIONIC,
                libraryRelativePath = "vulkan.turnip.so",
            ),
            Paths.get("/tmp/turnip-0123456789abcdef"),
        )
        private val asset = TurnipReleaseAsset("v1", "today", "Turnip-EMULATOR.zip", 1, "https://github.com/v3kt0r-87/Mesa-Turnip-Builder/releases/download/v1/Turnip-EMULATOR.zip")
        override fun installed() = listOf(driver)
        override fun releases(force: Boolean) = listOf(asset)
        override fun download(asset: TurnipReleaseAsset, progress: (Long, Long) -> Unit) = driver
        override fun importZip(bytes: ByteArray, assetName: String) = driver
        override fun remove(id: String) = true
    }
}
