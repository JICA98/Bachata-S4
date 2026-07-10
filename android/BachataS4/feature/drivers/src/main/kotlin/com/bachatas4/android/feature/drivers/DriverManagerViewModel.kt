package com.bachatas4.android.feature.drivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bachatas4.android.data.RuntimeProfileStore
import com.bachatas4.android.runtime.driver.InstalledDriver
import com.bachatas4.android.runtime.driver.TurnipReleaseAsset
import com.bachatas4.android.runtime.settings.ProfileScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DriverManagerUiState(
    val installed: List<InstalledDriver> = emptyList(),
    val available: List<TurnipReleaseAsset> = emptyList(),
    val scope: ProfileScope = ProfileScope.Global,
    val selectedDriverId: String = "system",
    val loading: Boolean = false,
    val downloadAsset: String? = null,
    val downloaded: Long = 0,
    val downloadTotal: Long = 0,
    val pendingDeleteId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class DriverManagerViewModel @Inject constructor(
    private val backend: DriverManagerBackend,
    private val profiles: RuntimeProfileStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DriverManagerUiState())
    val state: StateFlow<DriverManagerUiState> = mutableState

    init { selectScope(ProfileScope.Global); refresh(false) }

    fun selectScope(scope: ProfileScope) {
        viewModelScope.launch {
            val selected = profiles.load(scope).driverId ?: "system"
            mutableState.value = mutableState.value.copy(scope = scope, selectedDriverId = selected)
        }
    }

    fun refresh(force: Boolean = true) = work(loading = true) {
        val installed = backend.installed()
        val releases = backend.releases(force)
        mutableState.value = mutableState.value.copy(installed = installed, available = releases)
    }

    fun download(asset: TurnipReleaseAsset) = work(downloadAsset = asset.name) {
        backend.download(asset) { copied, total ->
            mutableState.value = mutableState.value.copy(downloaded = copied, downloadTotal = total)
        }
        mutableState.value = mutableState.value.copy(installed = backend.installed())
    }

    fun importZip(bytes: ByteArray, assetName: String) = work(loading = true) {
        backend.importZip(bytes, assetName)
        mutableState.value = mutableState.value.copy(installed = backend.installed())
    }

    fun select(id: String) {
        require(id == "system" || backend.installed().any { it.metadata.id == id }) { "Driver is not installed" }
        viewModelScope.launch {
            profiles.update(mutableState.value.scope) { it.copy(driverId = id.takeUnless { value -> value == "system" }) }
            mutableState.value = mutableState.value.copy(selectedDriverId = id)
        }
    }

    fun requestDelete(id: String) {
        if (mutableState.value.selectedDriverId == id) {
            mutableState.value = mutableState.value.copy(pendingDeleteId = id)
        } else delete(id)
    }

    fun confirmDelete() {
        val id = mutableState.value.pendingDeleteId ?: return
        select("system")
        delete(id)
    }

    fun cancelDelete() { mutableState.value = mutableState.value.copy(pendingDeleteId = null) }

    private fun delete(id: String) = work(loading = true) {
        backend.remove(id)
        mutableState.value = mutableState.value.copy(installed = backend.installed(), pendingDeleteId = null)
    }

    private fun work(
        loading: Boolean = false,
        downloadAsset: String? = null,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(loading = loading, downloadAsset = downloadAsset, error = null)
        runCatching { withContext(Dispatchers.IO) { block() } }
            .onFailure { mutableState.value = mutableState.value.copy(error = it.message ?: it.javaClass.simpleName) }
        mutableState.value = mutableState.value.copy(loading = false, downloadAsset = null)
    }
}
