package com.bachatas4.android.feature.session.controller

import kotlinx.serialization.Serializable

@Serializable
data class TouchControlPlacement(
    val control: String,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val zIndex: Int = 0,
    val visible: Boolean = true,
) {
    init {
        require(control.isNotBlank()) { "Touch control name is blank" }
        require(listOf(centerX, centerY, width, height).all(Float::isFinite)) { "Touch placement must be finite" }
        require(centerX in 0f..1f && centerY in 0f..1f) { "Touch control center must be normalized" }
        require(width in MIN_SIZE..1f && height in MIN_SIZE..1f) { "Touch control size is unreachable" }
    }
    companion object { const val MIN_SIZE = 0.04f }
}

@Serializable
data class TouchLayout(
    val id: String = "default",
    val name: String = "Default",
    val controls: List<TouchControlPlacement> = defaultControls(),
    val opacity: Float = 0.32f,
    val scale: Float = 1f,
    val vibrationEnabled: Boolean = true,
    val analogCentering: Boolean = true,
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid touch layout id" }
        require(name.isNotBlank()) { "Touch layout name is blank" }
        require(opacity.isFinite() && opacity in 0.05f..1f) { "Touch opacity must be 0.05..1" }
        require(scale.isFinite() && scale in 0.5f..2f) { "Touch scale must be 0.5..2" }
        require(controls.map { it.control }.distinct().size == controls.size) { "Duplicate touch control" }
    }

    companion object {
        fun defaultControls(): List<TouchControlPlacement> = listOf(
            p("left_stick", 250f, 830f, 290f, 290f), p("right_stick", 1250f, 830f, 290f, 290f),
            p("triangle", 1660f, 590f), p("circle", 1770f, 700f), p("cross", 1660f, 810f), p("square", 1550f, 700f),
            p("dpad_up", 600f, 690f), p("dpad_right", 710f, 800f), p("dpad_down", 600f, 910f), p("dpad_left", 490f, 800f),
            p("l2", 140f, 75f, 200f, 90f), p("l1", 360f, 75f, 200f, 90f),
            p("r1", 1560f, 75f, 200f, 90f), p("r2", 1780f, 75f, 200f, 90f),
            p("touchpad", 960f, 95f, 280f, 130f), p("options", 1190f, 210f, 140f, 80f),
        )

        private fun p(control: String, x: Float, y: Float, width: Float = 140f, height: Float = 140f) =
            TouchControlPlacement(control, x / 1920f, y / 1080f, width / 1920f, height / 1080f)
    }
}
