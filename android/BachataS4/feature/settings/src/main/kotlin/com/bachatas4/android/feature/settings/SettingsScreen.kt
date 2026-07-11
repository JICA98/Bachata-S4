package com.bachatas4.android.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.bachatas4.android.runtime.session.ManagedSession
import com.bachatas4.android.runtime.session.ManagedSessionState
import com.bachatas4.android.designsystem.BachataActionBar
import com.bachatas4.android.designsystem.BachataPanel
import com.bachatas4.android.designsystem.BachataPrimaryButton
import com.bachatas4.android.designsystem.BachataScreenHeader
import com.bachatas4.android.designsystem.theme.BachataPalette

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDrivers: () -> Unit = {},
    initialGameId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sessionState by ManagedSession.state.collectAsState()
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
    LaunchedEffect(initialGameId) {
        if (initialGameId != null) viewModel.selectScope(ProfileScope.Game(initialGameId))
    }
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
        onCategory = viewModel::selectCategory,
        onScope = viewModel::selectScope,
        onPreset = viewModel::setPreset,
        onValue = viewModel::setText,
        onBoolean = viewModel::setValue,
        onReset = { viewModel.setValue(it, null) },
        onImport = { importer.launch(arrayOf("application/json")) },
        onExport = { exporter.launch("bachata-runtime-profile.json") },
        appliesNextLaunch = sessionState is ManagedSessionState.Running || sessionState is ManagedSessionState.Preparing,
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
    onCategory: (String?) -> Unit,
    onScope: (ProfileScope) -> Unit,
    onPreset: (Box64Preset) -> Unit,
    onValue: (RuntimeSettingSpec, String) -> Unit,
    onBoolean: (RuntimeSettingSpec, JsonPrimitive) -> Unit,
    onReset: (RuntimeSettingSpec) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    appliesNextLaunch: Boolean,
) {
    var gameId by remember { mutableStateOf("") }
    val scopeLabel = when (val scope = state.scope) {
        ProfileScope.Global -> "Global"
        is ProfileScope.Game -> "Game: ${scope.gameId}"
    }
    val preset = state.profile.box64Preset ?: Box64Preset.DEFAULT
    val shown = if (state.runtime == SettingsRuntime.BOX64 && preset != Box64Preset.CUSTOM) emptyList() else state.settings
    Scaffold(
        containerColor = BachataPalette.Canvas,
        bottomBar = { BachataActionBar("A  CONFIRM", "↔  CHANGE", "B  BACK") },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BachataScreenHeader(title = "Settings", onBack = onBack) }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        scopeLabel,
                        modifier = Modifier.weight(1f),
                        color = BachataPalette.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { onScope(ProfileScope.Global) }) { Text("Global") }
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onRuntime(SettingsRuntime.SHADPS4) }, modifier = Modifier.weight(1f)) {
                        Text("shadPS4")
                    }
                    Button(onClick = { onRuntime(SettingsRuntime.BOX64) }, modifier = Modifier.weight(1f)) {
                        Text("Box64")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = gameId,
                    onValueChange = { gameId = it },
                    label = { Text("Game ID for per-game settings") },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { runCatching { onScope(ProfileScope.Game(gameId)) } }) { Text("Use") }
                    },
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        CategoryTab("All", state.selectedCategory == null) { onCategory(null) }
                    }
                    items(state.categories) { category ->
                        CategoryTab(category, state.selectedCategory == category) { onCategory(category) }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    label = { Text("Search name, key, category") },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                BachataPanel(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TOOLS", color = BachataPalette.Secondary, style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item { TextButton(onClick = onOpenDrivers) { Text("Drivers") } }
                            item { TextButton(onClick = onOpenRaw) { Text("Raw") } }
                            item { TextButton(onClick = onOpenControllers) { Text("Controllers") } }
                            item { TextButton(onClick = onOpenTouchLayout) { Text("Touch layout") } }
                            item { TextButton(onClick = onImport) { Text("Import") } }
                            item { TextButton(onClick = onExport) { Text("Export") } }
                        }
                    }
                }
            }
            if (appliesNextLaunch) {
                item {
                    BachataPanel(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), color = Color(0xFF3A2B18)) {
                        Text("Emulation is active; changes apply next launch.", modifier = Modifier.padding(12.dp), color = Color(0xFFFFDDB5))
                    }
                }
            }
            if (state.runtime == SettingsRuntime.BOX64) {
                item {
                    BachataPanel(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), color = BachataPalette.RaisedSurface) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Official Box64 preset", fontWeight = FontWeight.SemiBold, color = BachataPalette.Primary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(Box64Preset.entries) { option ->
                                    CategoryTab(option.name.lowercase(), preset == option) { onPreset(option) }
                                }
                            }
                            if (preset != Box64Preset.CUSTOM) {
                                Text(
                                    "${preset.name.lowercase()} uses upstream Box64 defaults. Select custom to fine-tune all flags.",
                                    color = BachataPalette.Secondary,
                                )
                            }
                        }
                    }
                }
            }
            state.error?.let { message ->
                item { Text(message, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
            }
            items(shown, key = { it.id }) { spec ->
                SettingEditor(spec, state, onValue, onBoolean, onReset)
            }
        }
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    BachataPanel(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) BachataPalette.Accent else BachataPalette.Surface,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) BachataPalette.OnAccent else BachataPalette.Primary,
            style = MaterialTheme.typography.labelLarge,
        )
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
    BachataPanel(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        color = if (inherited) BachataPalette.RaisedSurface else BachataPalette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(spec.title.ifBlank { spec.nativeKey }, style = MaterialTheme.typography.titleMedium, color = BachataPalette.Primary)
            Text(
                "${spec.category} · ${spec.nativeKey}${if (inherited) " · inherited" else ""}",
                color = BachataPalette.Secondary,
                style = MaterialTheme.typography.labelSmall,
            )
            if (spec.help.isNotBlank()) Text(spec.help, style = MaterialTheme.typography.bodySmall, color = BachataPalette.Secondary)
            spec.readOnlyReason?.let { Text("Managed: $it", color = Color(0xFFFFDDB5)) }
            if (spec.kind == SettingKind.BOOLEAN) {
                val checked = (current as? JsonPrimitive)?.booleanOrNull ?: false
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (checked) "Enabled" else "Disabled", modifier = Modifier.weight(1f), color = BachataPalette.Primary)
                    Switch(checked, { onBoolean(spec, JsonPrimitive(it)) }, enabled = spec.readOnlyReason == null)
                }
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = spec.readOnlyReason == null,
                    supportingText = if (spec.choices.isEmpty()) null else ({ Text("Choices: ${spec.choices.joinToString()}") }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BachataPrimaryButton(onClick = { onValue(spec, text) }, enabled = spec.readOnlyReason == null) { Text("Apply") }
                    TextButton(onClick = { onReset(spec) }, enabled = spec.readOnlyReason == null) {
                        Text(if (state.scope is ProfileScope.Game) "Inherit" else "Default")
                    }
                }
            }
        }
    }
}
