package com.bachatas4.android.feature.drivers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.runtime.driver.TurnipReleaseClient
import com.bachatas4.android.runtime.settings.ProfileScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DriverManagerScreen(
    onBack: () -> Unit,
    viewModel: DriverManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        val bytes = it.readNBytes(TurnipReleaseClient.MAX_ASSET_BYTES.toInt() + 1)
                        require(bytes.size <= TurnipReleaseClient.MAX_ASSET_BYTES) { "Driver ZIP exceeds 32 MiB" }
                        bytes
                    }
                }
            }.onSuccess { bytes -> if (bytes != null) viewModel.importZip(bytes, uri.lastPathSegment ?: "imported.zip") }
        }
    }
    var gameId by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Turnip drivers", style = MaterialTheme.typography.headlineMedium)
        Text("Trusted source: ${TurnipReleaseClient.REPOSITORY}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = { viewModel.refresh() }, enabled = !state.loading) { Text("Refresh") }
            Button(onClick = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) { Text("Import ZIP") }
            Button(onClick = { viewModel.select("system") }) { Text("System driver") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { viewModel.selectScope(ProfileScope.Global) }) { Text("Global") }
            OutlinedTextField(gameId, { gameId = it }, label = { Text("Game ID") }, modifier = Modifier.weight(1f))
            Button(onClick = { runCatching { viewModel.selectScope(ProfileScope.Game(gameId)) } }) { Text("Use") }
        }
        Text("Selected: ${state.selectedDriverId}")
        state.downloadAsset?.let { Text("Downloading $it: ${state.downloaded}/${state.downloadTotal} bytes") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.pendingDeleteId?.let { id ->
            Text("This driver is selected for the current scope. Switch to system and delete?")
            Row { Button(onClick = viewModel::confirmDelete) { Text("Confirm") }; Button(onClick = viewModel::cancelDelete) { Text("Cancel") } }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Installed", style = MaterialTheme.typography.titleLarge) }
            items(state.installed, key = { it.metadata.id }) { driver ->
                Column(Modifier.fillMaxWidth()) {
                    Text(driver.metadata.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("${driver.metadata.releaseTag ?: "Imported"} · ${driver.metadata.abi} · ${driver.metadata.sha256.take(16)}")
                    Text(driver.metadata.assetName)
                    Row { Button(onClick = { viewModel.select(driver.metadata.id) }) { Text("Select") }; Button(onClick = { viewModel.requestDelete(driver.metadata.id) }) { Text("Delete") } }
                }
            }
            item { Text("Available", style = MaterialTheme.typography.titleLarge) }
            items(state.available, key = { it.downloadUrl }) { asset ->
                Column(Modifier.fillMaxWidth()) {
                    Text(asset.releaseTag, style = MaterialTheme.typography.titleMedium)
                    Text("${asset.name} · ${asset.size / 1024 / 1024} MiB · ${asset.publishedAt}")
                    Button(onClick = { viewModel.download(asset) }, enabled = state.downloadAsset == null) { Text("Download") }
                }
            }
        }
    }
}
