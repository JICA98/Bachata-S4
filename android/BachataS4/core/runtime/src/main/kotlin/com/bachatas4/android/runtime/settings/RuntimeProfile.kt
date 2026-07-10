package com.bachatas4.android.runtime.settings

import com.bachatas4.android.runtime.input.ControllerProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val CURRENT_SCHEMA_VERSION = 1

@Serializable
enum class Box64Preset(val environmentValue: String?) {
    SAFEST("safest"),
    SAFE("safe"),
    DEFAULT("default"),
    FAST("fast"),
    FASTEST("fastest"),
    CUSTOM(null),
}

@Serializable
data class RuntimeProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val values: Map<String, JsonElement> = emptyMap(),
    val unknownShadPs4: Map<String, JsonElement> = emptyMap(),
    val unknownBox64: Map<String, String> = emptyMap(),
    val box64Preset: Box64Preset? = null,
    val driverId: String? = null,
    val controllerSlots: List<ControllerProfile> = emptyList(),
    val touchLayoutId: String? = null,
)

@Serializable
sealed interface ProfileScope {
    @Serializable
    data object Global : ProfileScope

    @Serializable
    data class Game(val gameId: String) : ProfileScope {
        init {
            require(gameId.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid game id: $gameId" }
        }
    }
}
