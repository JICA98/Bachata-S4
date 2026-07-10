package com.bachatas4.android.feature.settings

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
import androidx.compose.material3.Switch
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
import com.bachatas4.android.runtime.settings.Box64Preset
import com.bachatas4.android.runtime.settings.ProfileScope
import com.bachatas4.android.runtime.settings.RuntimeSettingSpec
import com.bachatas4.android.runtime.settings.SettingKind
import com.bachatas4.android.feature.settings.input.ControllerMappingScreen
import com.bachatas4.android.feature.settings.input.TouchLayoutEditorScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDrivers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showRaw by remember { mutableStateOf(false) }
    var showControllers by remember { mutableStateOf(false) }
    var showTouchLayout by remember { mutableStateOf(false) }
    if (showRaw) {
        RawConfigScreen(scope = state.scope, onBack = { showRaw = false })
        return
    }
    if (showControllers) {
        ControllerMappingScreen(scope = state.scope, onBack = { showControllers = false })
        return
    }
    if (showTouchLayout) {
        TouchLayoutEditorScreen(scope = state.scope, onBack = { showTouchLayout = false })
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (text != null) viewModel.importJson(text)
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = viewModel.exportJson()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.writer()?.use { it.write(text) } }
        }
    }
    SettingsContent(
        state = state,
        onBack = onBack,
        onOpenDrivers = onOpenDrivers,
        onOpenRaw = { showRaw = true },
        onOpenControllers = { showControllers = true },
        onOpenTouchLayout = { showTouchLayout = true },
        onRuntime = viewModel::selectRuntime,
        onSearch = viewModel::search,
        onScope = viewModel::selectScope,
        onPreset = viewModel::setPreset,
        onValue = viewModel::setText,
        onBoolean = viewModel::setValue,
        onReset = { viewModel.setValue(it, null) },
        onImport = { importer.launch(arrayOf("application/json")) },
        onExport = { exporter.launch("bachata-runtime-profile.json") },
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenDrivers: () -> Unit,
    onOpenRaw: () -> Unit,
    onOpenControllers: () -> Unit,
    onOpenTouchLayout: () -> Unit,
    onRuntime: (SettingsRuntime) -> Unit,
    onSearch: (String) -> Unit,
    onScope: (ProfileScope) -> Unit,
    onPreset: (Box64Preset) -> Unit,
    onValue: (RuntimeSettingSpec, String) -> Unit,
    onBoolean: (RuntimeSettingSpec, JsonPrimitive) -> Unit,
    onReset: (RuntimeSettingSpec) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var gameId by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Runtime settings", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = onOpenDrivers) { Text("Turnip drivers") }
            Button(onClick = onOpenRaw) { Text("Raw") }
            Button(onClick = onOpenControllers) { Text("Controllers") }
            Button(onClick = onOpenTouchLayout) { Text("Touch layout") }
            Button(onClick = onImport) { Text("Import JSON") }
            Button(onClick = onExport) { Text("Export JSON") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { onScope(ProfileScope.Global) }) { Text("Global") }
            OutlinedTextField(gameId, { gameId = it }, label = { Text("Game ID") }, modifier = Modifier.weight(1f))
            Button(onClick = { runCatching { onScope(ProfileScope.Game(gameId)) } }) { Text("Use") }
        }
        Text("Scope: ${if (state.scope is ProfileScope.Global) "Global" else (state.scope as ProfileScope.Game).gameId}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { onRuntime(SettingsRuntime.SHADPS4) }) { Text("shadPS4 (86)") }
            Button(onClick = { onRuntime(SettingsRuntime.BOX64) }) { Text("Box64 (118)") }
        }
        OutlinedTextField(state.query, onSearch, label = { Text("Search name, key, category") }, modifier = Modifier.fillMaxWidth())
        if (state.runtime == SettingsRuntime.BOX64) {
            Text("Official Box64 preset")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box64Preset.entries.forEach { preset ->
                    Button(onClick = { onPreset(preset) }) { Text(preset.name.lowercase()) }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val preset = state.profile.box64Preset ?: Box64Preset.DEFAULT
        val shown = if (state.runtime == SettingsRuntime.BOX64 && preset != Box64Preset.CUSTOM) emptyList() else state.settings
        if (state.runtime == SettingsRuntime.BOX64 && preset != Box64Preset.CUSTOM) {
            Text("${preset.name.lowercase()} uses upstream Box64 defaults. Select custom to fine-tune all flags.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { spec ->
                SettingEditor(spec, state, onValue, onBoolean, onReset)
            }
        }
    }
}

@Composable
private fun SettingEditor(
    spec: RuntimeSettingSpec,
    state: SettingsUiState,
    onValue: (RuntimeSettingSpec, String) -> Unit,
    onBoolean: (RuntimeSettingSpec, JsonPrimitive) -> Unit,
    onReset: (RuntimeSettingSpec) -> Unit,
) {
    val current = state.profile.values[spec.id] ?: spec.defaultValue
    val inherited = state.scope is ProfileScope.Game && spec.id !in state.profile.values
    var text by remember(spec.id, current) {
        mutableStateOf(if (current is JsonArray) current.joinToString(",") { (it as JsonPrimitive).content } else (current as? JsonPrimitive)?.content.orEmpty())
    }
    Column(Modifier.fillMaxWidth()) {
        Text(spec.title.ifBlank { spec.nativeKey }, style = MaterialTheme.typography.titleMedium)
        Text("${spec.category} · ${spec.nativeKey}${if (inherited) " · inherited" else ""}")
        if (spec.help.isNotBlank()) Text(spec.help, style = MaterialTheme.typography.bodySmall)
        spec.readOnlyReason?.let { Text("Managed: $it") }
        if (spec.kind == SettingKind.BOOLEAN) {
            val checked = (current as? JsonPrimitive)?.booleanOrNull ?: false
            Switch(checked, { onBoolean(spec, JsonPrimitive(it)) }, enabled = spec.readOnlyReason == null)
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = spec.readOnlyReason == null,
                supportingText = if (spec.choices.isEmpty()) null else ({ Text("Choices: ${spec.choices.joinToString()}") }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onValue(spec, text) }, enabled = spec.readOnlyReason == null) { Text("Apply") }
                Button(onClick = { onReset(spec) }, enabled = spec.readOnlyReason == null) { Text(if (state.scope is ProfileScope.Game) "Inherit" else "Default") }
            }
        }
    }
}
