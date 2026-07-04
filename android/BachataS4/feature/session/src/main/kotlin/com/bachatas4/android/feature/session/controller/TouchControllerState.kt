package com.bachatas4.android.feature.session.controller

import com.bachatas4.android.runtime.input.ControllerSnapshot
import com.bachatas4.android.runtime.input.Ps4Button
import kotlin.math.hypot

class TouchControllerState {
    data class ButtonControl(val x: Float, val y: Float, val button: Long, val label: String)

    private sealed interface ActiveControl {
        data class Stick(val right: Boolean, var x: Float = 0f, var y: Float = 0f) : ActiveControl
        data class Button(var mask: Long) : ActiveControl
    }

    private val pointers = linkedMapOf<Long, ActiveControl>()

    fun pointerDown(id: Long, x: Float, y: Float) {
        val control = when {
            distance(x, y, LEFT_STICK_X, STICK_Y) <= STICK_RADIUS -> ActiveControl.Stick(false)
            distance(x, y, RIGHT_STICK_X, STICK_Y) <= STICK_RADIUS -> ActiveControl.Stick(true)
            else -> ActiveControl.Button(buttonAt(x, y))
        }
        pointers[id] = control
        pointerMove(id, x, y)
    }

    fun pointerMove(id: Long, x: Float, y: Float) {
        when (val control = pointers[id]) {
            is ActiveControl.Stick -> {
                val centerX = if (control.right) RIGHT_STICK_X else LEFT_STICK_X
                val dx = x - centerX
                val dy = y - STICK_Y
                val length = hypot(dx, dy)
                val scale = if (length > STICK_RADIUS) STICK_RADIUS / length else 1f
                control.x = dx * scale / STICK_RADIUS
                control.y = dy * scale / STICK_RADIUS
            }
            is ActiveControl.Button -> control.mask = buttonAt(x, y)
            null -> Unit
        }
    }

    fun pointerUp(id: Long) {
        pointers.remove(id)
    }

    fun cancelAll() {
        pointers.clear()
    }

    fun snapshot(): ControllerSnapshot {
        var buttons = 0L
        var leftX = 0f
        var leftY = 0f
        var rightX = 0f
        var rightY = 0f
        for (control in pointers.values) {
            when (control) {
                is ActiveControl.Button -> buttons = buttons or control.mask
                is ActiveControl.Stick -> if (control.right) {
                    rightX = control.x
                    rightY = control.y
                } else {
                    leftX = control.x
                    leftY = control.y
                }
            }
        }
        return ControllerSnapshot.normalized(
            buttons = buttons,
            leftX = leftX,
            leftY = leftY,
            rightX = rightX,
            rightY = rightY,
            leftTrigger = if (buttons and Ps4Button.L2 != 0L) 1f else 0f,
            rightTrigger = if (buttons and Ps4Button.R2 != 0L) 1f else 0f,
        )
    }

    private fun buttonAt(x: Float, y: Float): Long = when {
        faceButtons.firstOrNull { inCircle(x, y, it.x, it.y, BUTTON_RADIUS) } != null ->
            faceButtons.first { inCircle(x, y, it.x, it.y, BUTTON_RADIUS) }.button
        dpadButtons.firstOrNull { inCircle(x, y, it.x, it.y, DPAD_RADIUS) } != null ->
            dpadButtons.first { inCircle(x, y, it.x, it.y, DPAD_RADIUS) }.button
        x in 40f..240f && y in 30f..120f -> Ps4Button.L2
        x in 260f..460f && y in 30f..120f -> Ps4Button.L1
        x in 1460f..1660f && y in 30f..120f -> Ps4Button.R1
        x in 1680f..1880f && y in 30f..120f -> Ps4Button.R2
        x in 820f..1100f && y in 30f..160f -> Ps4Button.TOUCH_PAD
        x in 1120f..1260f && y in 170f..250f -> Ps4Button.OPTIONS
        else -> 0L
    }

    private fun inCircle(x: Float, y: Float, centerX: Float, centerY: Float, radius: Float) =
        distance(x, y, centerX, centerY) <= radius

    private fun distance(x: Float, y: Float, centerX: Float, centerY: Float) =
        hypot(x - centerX, y - centerY)

    companion object {
        const val LogicalWidth = 1920f
        const val LogicalHeight = 1080f
        const val LEFT_STICK_X = 250f
        const val RIGHT_STICK_X = 1250f
        const val STICK_Y = 830f
        const val STICK_RADIUS = 145f
        const val BUTTON_RADIUS = 70f
        const val DPAD_RADIUS = 62f
        val faceButtons = listOf(
            ButtonControl(1660f, 590f, Ps4Button.TRIANGLE, "TRI"),
            ButtonControl(1770f, 700f, Ps4Button.CIRCLE, "CIR"),
            ButtonControl(1660f, 810f, Ps4Button.CROSS, "X"),
            ButtonControl(1550f, 700f, Ps4Button.SQUARE, "SQ"),
        )
        val dpadButtons = listOf(
            ButtonControl(600f, 690f, Ps4Button.UP, "UP"),
            ButtonControl(710f, 800f, Ps4Button.RIGHT, "R"),
            ButtonControl(600f, 910f, Ps4Button.DOWN, "DN"),
            ButtonControl(490f, 800f, Ps4Button.LEFT, "L"),
        )
    }
}
