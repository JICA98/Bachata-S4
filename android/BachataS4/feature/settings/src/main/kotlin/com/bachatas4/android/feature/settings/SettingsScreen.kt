package com.bachatas4.android.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.bachatas4.android.feature.drivers.DriverManagerScreen
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
    var activeTab by remember { mutableStateOf("Runtime") }
    var isSearchActive by remember { mutableStateOf(false) }
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
        activeTab = activeTab,
        onTabSelected = { activeTab = it },
        isSearchActive = isSearchActive,
        onSearchActiveChange = { isSearchActive = it },
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
    activeTab: String,
    onTabSelected: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
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
    val title = when (val scope = state.scope) {
        ProfileScope.Global -> "Settings"
        is ProfileScope.Game -> "Settings (Game: ${scope.gameId})"
    }
    val preset = state.profile.box64Preset ?: Box64Preset.DEFAULT
    val shown = if (state.runtime == SettingsRuntime.BOX64 && preset != Box64Preset.CUSTOM) emptyList() else state.settings
    Scaffold(
        containerColor = BachataPalette.Canvas,
        bottomBar = { BachataActionBar("A  CONFIRM", "↔  CHANGE", "B  BACK") },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = {
                        onSearchActiveChange(false)
                        onSearch("")
                    }) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onSearch,
                        placeholder = { Text("Search settings...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { onSearch("") }) { Text("X") }
                        }
                    )
                }
            } else {
                BachataScreenHeader(
                    title = title,
                    onBack = onBack,
                    actions = {
                        androidx.compose.material3.Surface(
                            color = BachataPalette.RaisedSurface,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                            modifier = Modifier.clickable { onSearchActiveChange(true) }
                        ) {
                            Text(
                                text = "SEARCH",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = BachataPalette.Accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    TopTab(
                        label = "Runtime",
                        selected = activeTab == "Runtime",
                        onClick = {
                            onTabSelected("Runtime")
                            onRuntime(SettingsRuntime.SHADPS4)
                        }
                    )
                }
                item {
                    TopTab(
                        label = "CPU",
                        selected = activeTab == "CPU",
                        onClick = {
                            onTabSelected("CPU")
                            onRuntime(SettingsRuntime.BOX64)
                        }
                    )
                }
                item {
                    TopTab(
                        label = "Drivers",
                        selected = activeTab == "Drivers",
                        onClick = { onTabSelected("Drivers") }
                    )
                }
                item {
                    TopTab(
                        label = "RAW",
                        selected = activeTab == "RAW",
                        onClick = { onTabSelected("RAW") }
                    )
                }
                item {
                    TopTab(
                        label = "Controllers",
                        selected = activeTab == "Controllers",
                        onClick = { onTabSelected("Controllers") }
                    )
                }
                item {
                    TopTab(
                        label = "Touch Input",
                        selected = activeTab == "Touch Input",
                        onClick = { onTabSelected("Touch Input") }
                    )
                }
                item {
                    TopTab(
                        label = "Import",
                        selected = false,
                        onClick = onImport
                    )
                }
                item {
                    TopTab(
                        label = "Export",
                        selected = false,
                        onClick = onExport
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activeTab == "Runtime" || activeTab == "CPU") {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
                        if (shown.isEmpty() && state.query.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No Results for \"${state.query}\"",
                                        color = BachataPalette.Secondary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                        items(shown, key = { it.id }) { spec ->
                            SettingEditor(spec, state, onValue, onBoolean, onReset)
                        }
                    }
                } else {
                    when (activeTab) {
                        "Drivers" -> {
                            DriverManagerScreen(
                                scope = state.scope,
                                onBack = { onTabSelected("Runtime") },
                                standalone = false,
                            )
                        }
                        "RAW" -> {
                            RawConfigScreen(
                                scope = state.scope,
                                onBack = { onTabSelected("Runtime") }
                            )
                        }
                        "Controllers" -> {
                            ControllerMappingScreen(
                                scope = state.scope,
                                onBack = { onTabSelected("Runtime") }
                            )
                        }
                        "Touch Input" -> {
                            TouchLayoutEditorScreen(
                                scope = state.scope,
                                onBack = { onTabSelected("Runtime") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) BachataPalette.Primary else BachataPalette.Secondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(if (selected) BachataPalette.Accent else Color.Transparent)
        )
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) BachataPalette.Primary else BachataPalette.Secondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(2.dp)
                .background(if (selected) BachataPalette.Accent else Color.Transparent)
        )
    }
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    color: Color = BachataPalette.Primary
) {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        Text(text = text, modifier = modifier, style = style, color = color)
        return
    }
    val annotatedString = remember(text, query) {
        androidx.compose.ui.text.buildAnnotatedString {
            var startIndex = 0
            while (true) {
                val index = text.indexOf(query, startIndex, ignoreCase = true)
                if (index == -1) {
                    append(text.substring(startIndex))
                    break
                }
                append(text.substring(startIndex, index))
                pushStyle(androidx.compose.ui.text.SpanStyle(background = BachataPalette.Accent, color = BachataPalette.OnAccent))
                append(text.substring(index, index + query.length))
                pop()
                startIndex = index + query.length
            }
        }
    }
    Text(text = annotatedString, modifier = modifier, style = style, color = color)
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
        if (spec.kind == SettingKind.BOOLEAN) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HighlightedText(
                        text = spec.title.ifBlank { spec.nativeKey },
                        query = state.query,
                        style = MaterialTheme.typography.titleMedium,
                        color = BachataPalette.Primary
                    )
                    Row {
                        HighlightedText(
                            text = spec.category,
                            query = state.query,
                            style = MaterialTheme.typography.labelSmall,
                            color = BachataPalette.Secondary
                        )
                        Text(" · ", style = MaterialTheme.typography.labelSmall, color = BachataPalette.Secondary)
                        HighlightedText(
                            text = spec.nativeKey,
                            query = state.query,
                            style = MaterialTheme.typography.labelSmall,
                            color = BachataPalette.Secondary
                        )
                        if (inherited) {
                            Text(" · inherited", style = MaterialTheme.typography.labelSmall, color = BachataPalette.Secondary)
                        }
                    }
                    if (spec.help.isNotBlank()) {
                        HighlightedText(
                            text = spec.help,
                            query = state.query,
                            style = MaterialTheme.typography.bodySmall,
                            color = BachataPalette.Secondary
                        )
                    }
                    spec.readOnlyReason?.let { Text("Managed: $it", color = Color(0xFFFFDDB5), style = MaterialTheme.typography.bodySmall) }
                }
                val checked = (current as? JsonPrimitive)?.booleanOrNull ?: false
                Switch(checked, { onBoolean(spec, JsonPrimitive(it)) }, enabled = spec.readOnlyReason == null)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HighlightedText(
                    text = spec.title.ifBlank { spec.nativeKey },
                    query = state.query,
                    style = MaterialTheme.typography.titleMedium,
                    color = BachataPalette.Primary
                )
                Row {
                    HighlightedText(
                        text = spec.category,
                        query = state.query,
                        style = MaterialTheme.typography.labelSmall,
                        color = BachataPalette.Secondary
                    )
                    Text(" · ", style = MaterialTheme.typography.labelSmall, color = BachataPalette.Secondary)
                    HighlightedText(
                        text = spec.nativeKey,
                        query = state.query,
                        style = MaterialTheme.typography.labelSmall,
                        color = BachataPalette.Secondary
                    )
                    if (inherited) {
                        Text(" · inherited", style = MaterialTheme.typography.labelSmall, color = BachataPalette.Secondary)
                    }
                }
                if (spec.help.isNotBlank()) {
                    HighlightedText(
                        text = spec.help,
                        query = state.query,
                        style = MaterialTheme.typography.bodySmall,
                        color = BachataPalette.Secondary
                    )
                }
                spec.readOnlyReason?.let { Text("Managed: $it", color = Color(0xFFFFDDB5), style = MaterialTheme.typography.bodySmall) }
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
