package com.bachatas4.android.runtime.input

import kotlinx.serialization.Serializable

@Serializable
data class ControllerProfile(
    val bindings: Map<String, String> = emptyMap(),
)
