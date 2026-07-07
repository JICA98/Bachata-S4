package com.bachatas4.android.feature.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.bachatas4.android.runtime.process.CustomVulkanDriverInstaller
import com.bachatas4.android.runtime.process.RuntimeVulkanDriver
import com.bachatas4.android.runtime.process.RuntimeVulkanDriverPreference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(RuntimeVulkanDriverPreference.FILE_NAME, Context.MODE_PRIVATE)
    }
    var driver by remember {
        mutableStateOf(
            RuntimeVulkanDriverPreference.decode(
                preferences.getString(RuntimeVulkanDriverPreference.KEY, null),
            ),
        )
    }
    val installer = remember(context) {
        CustomVulkanDriverInstaller(File(context.filesDir, "vulkan-drivers/custom").toPath())
    }
    val scope = rememberCoroutineScope()
    var customDriverName by remember { mutableStateOf(installer.load()?.name) }
    var importError by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use(installer::install)
                            ?: error("Unable to open selected ZIP")
                    }
                }.onSuccess { installed ->
                    customDriverName = installed.name
                    importError = null
                    preferences.edit().putString(RuntimeVulkanDriverPreference.KEY, RuntimeVulkanDriver.CUSTOM.name).apply()
                    driver = RuntimeVulkanDriver.CUSTOM
                }.onFailure { error ->
                    importError = error.message ?: "Driver import failed"
                }
            }
        }
    }
    val state by viewModel.state.collectAsState()
    SettingsContent(
        state = state,
        driver = driver,
        customDriverName = customDriverName,
        importError = importError,
        onImportDriver = { importer.launch(arrayOf("application/zip", "application/octet-stream")) },
        onSelectDriver = { selected ->
            preferences.edit().putString(RuntimeVulkanDriverPreference.KEY, selected.name).apply()
            driver = selected
        },
        onBack = onBack,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    driver: RuntimeVulkanDriver,
    customDriverName: String?,
    importError: String?,
    onImportDriver: () -> Unit,
    onSelectDriver: (RuntimeVulkanDriver) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Runtime revision: ${state.runtimeRevision}")
        Text("Vulkan UUID: ${state.vulkanUuid}")
        Text("Box64 preset: ${state.box64Preset}")
        Text("Vulkan driver: ${driver.displayName(customDriverName)}")
        RuntimeVulkanDriver.entries.filter { it != RuntimeVulkanDriver.CUSTOM || customDriverName != null }.reversed().forEach { option ->
            Button(onClick = { onSelectDriver(option) }, enabled = option != driver) {
                val name = option.displayName(customDriverName)
                Text(if (option == driver) "$name (selected)" else name)
            }
        }
        Button(onClick = onImportDriver) {
            Text("Import custom Turnip ZIP")
        }
        importError?.let { Text("Import failed: $it", color = MaterialTheme.colorScheme.error) }
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}

private fun RuntimeVulkanDriver.displayName(customDriverName: String? = null): String = when (this) {
    RuntimeVulkanDriver.CUSTOM -> customDriverName ?: "Custom Turnip"
    RuntimeVulkanDriver.TURNIP_25_0_0 -> "Turnip 25.0.0"
    RuntimeVulkanDriver.TURNIP_25_3_0_R11 -> "Turnip 25.3.0 R11"
    RuntimeVulkanDriver.TURNIP_26_1_0 -> "Turnip 26.1.0"
}
