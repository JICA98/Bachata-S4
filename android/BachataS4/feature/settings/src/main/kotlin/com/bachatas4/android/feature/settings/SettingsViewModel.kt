package com.bachatas4.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bachatas4.android.data.RuntimeProfileStore
import com.bachatas4.android.runtime.settings.Box64Preset
import com.bachatas4.android.runtime.settings.ProfileScope
import com.bachatas4.android.runtime.settings.RuntimeProfile
import com.bachatas4.android.runtime.settings.RuntimeSettingCatalog
import com.bachatas4.android.runtime.settings.RuntimeSettingSpec
import com.bachatas4.android.runtime.settings.SettingKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

enum class SettingsRuntime { SHADPS4, BOX64 }

data class SettingsUiState(
    val runtimeRevision: String = "runtime-dev",
    val vulkanUuid: String = "unknown",
    val gameIds: List<String> = emptyList(),
    val scope: ProfileScope = ProfileScope.Global,
    val runtime: SettingsRuntime = SettingsRuntime.SHADPS4,
    val query: String = "",
    val profile: RuntimeProfile = RuntimeProfile(),
    val settings: List<RuntimeSettingSpec> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: RuntimeProfileStore,
) : ViewModel() {
    private val catalog = RuntimeSettingCatalog.loadFromResources()
    private val mutableState = MutableStateFlow(SettingsUiState(settings = catalog.shadPs4))
    val state: StateFlow<SettingsUiState> = mutableState
    private var profileJob: Job? = null

    init { selectScope(ProfileScope.Global) }

    fun selectRuntime(runtime: SettingsRuntime) {
        mutableState.value = mutableState.value.copy(runtime = runtime, settings = visible(runtime, mutableState.value.query))
    }

    fun search(query: String) {
        mutableState.value = mutableState.value.copy(query = query, settings = visible(mutableState.value.runtime, query))
    }

    fun selectScope(scope: ProfileScope) {
        profileJob?.cancel()
        mutableState.value = mutableState.value.copy(scope = scope, error = null)
        profileJob = viewModelScope.launch {
            store.observe(scope).collectLatest { profile -> mutableState.value = mutableState.value.copy(profile = profile) }
        }
    }

    fun setValue(spec: RuntimeSettingSpec, value: JsonElement?) = mutate { profile ->
        val values = profile.values.toMutableMap()
        if (value == null) values.remove(spec.id) else values[spec.id] = value
        profile.copy(values = values)
    }

    fun setText(spec: RuntimeSettingSpec, text: String) {
        runCatching { parse(spec, text) }
            .onSuccess { setValue(spec, it) }
            .onFailure { mutableState.value = mutableState.value.copy(error = it.message) }
    }

    fun setPreset(preset: Box64Preset) = mutate { it.copy(box64Preset = preset) }

    fun importJson(text: String) {
        viewModelScope.launch {
            val scope = mutableState.value.scope
            runCatching {
                val profile = ProfileTransfer.import(
                    text,
                    catalog.shadPs4 + catalog.box64,
                    (scope as? ProfileScope.Game)?.gameId,
                )
                store.update(scope) { profile }
            }
                .onFailure { mutableState.value = mutableState.value.copy(error = it.message) }
        }
    }

    suspend fun exportJson(): String = ProfileTransfer.export(
        store.load(mutableState.value.scope),
        catalog.shadPs4 + catalog.box64,
    )

    fun setDiagnostics(runtimeRevision: String, vulkanUuid: String, gameIds: List<String>) {
        mutableState.value = mutableState.value.copy(
            runtimeRevision = runtimeRevision,
            vulkanUuid = vulkanUuid,
            gameIds = gameIds.sorted(),
        )
    }

    fun diagnosticExport(): String = buildString {
        appendLine("runtimeRevision=${mutableState.value.runtimeRevision}")
        appendLine("vulkanUuid=${mutableState.value.vulkanUuid}")
        append("gameIds=${mutableState.value.gameIds.joinToString(",")}")
    }

    internal fun visible(runtime: SettingsRuntime, query: String): List<RuntimeSettingSpec> {
        val source = if (runtime == SettingsRuntime.SHADPS4) catalog.shadPs4 else catalog.box64
        val needle = query.trim()
        return source.filter { spec ->
            needle.isEmpty() || listOf(spec.title, spec.help, spec.nativeKey, spec.category, spec.section)
                .any { it.contains(needle, ignoreCase = true) }
        }
    }

    private fun mutate(block: (RuntimeProfile) -> RuntimeProfile) {
        viewModelScope.launch {
            runCatching { store.update(mutableState.value.scope, block) }
                .onFailure { mutableState.value = mutableState.value.copy(error = it.message) }
        }
    }

    private fun parse(spec: RuntimeSettingSpec, text: String): JsonElement {
        val value = when (spec.kind) {
            SettingKind.BOOLEAN -> JsonPrimitive(text.toBooleanStrict())
            SettingKind.INTEGER -> JsonPrimitive(text.toLong())
            SettingKind.DECIMAL -> JsonPrimitive(text.toDouble())
            SettingKind.LIST -> JsonArray(text.split(',').map(String::trim).filter(String::isNotEmpty).map(::JsonPrimitive))
            SettingKind.ENUM -> JsonPrimitive(text.also { require(it in spec.choices) { "Invalid choice for ${spec.title}" } })
            SettingKind.STRING, SettingKind.PATH -> JsonPrimitive(text)
        }
        val numeric = (value as? JsonPrimitive)?.content?.toDoubleOrNull()
        val minimum = spec.minimum
        val maximum = spec.maximum
        require(numeric == null || minimum == null || numeric >= minimum) { "${spec.title} is below $minimum" }
        require(numeric == null || maximum == null || numeric <= maximum) { "${spec.title} is above $maximum" }
        return value
    }
}
