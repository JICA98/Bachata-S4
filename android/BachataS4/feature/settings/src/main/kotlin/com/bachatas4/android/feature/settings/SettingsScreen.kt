package com.bachatas4.android.feature.settings

import android.content.Context
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bachatas4.android.runtime.process.RuntimeVulkanDriver
import com.bachatas4.android.runtime.process.RuntimeVulkanDriverPreference

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
    val state by viewModel.state.collectAsState()
    SettingsContent(
        state = state,
        driver = driver,
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
    onSelectDriver: (RuntimeVulkanDriver) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Runtime revision: ${state.runtimeRevision}")
        Text("Vulkan UUID: ${state.vulkanUuid}")
        Text("Box64 preset: ${state.box64Preset}")
        Text("Vulkan driver: ${driver.displayName()}")
        RuntimeVulkanDriver.entries.reversed().forEach { option ->
            Button(onClick = { onSelectDriver(option) }, enabled = option != driver) {
                Text(if (option == driver) "${option.displayName()} (selected)" else option.displayName())
            }
        }
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}

private fun RuntimeVulkanDriver.displayName(): String = when (this) {
    RuntimeVulkanDriver.TURNIP_25_0_0 -> "Turnip 25.0.0"
    RuntimeVulkanDriver.TURNIP_26_1_0 -> "Turnip 26.1.0"
}
