package com.bachatas4.android.feature.session.controller

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import com.bachatas4.android.runtime.input.ControllerSnapshot
import kotlin.math.min

@Composable
fun FixedControllerOverlay(
    modifier: Modifier = Modifier,
    onSnapshot: (ControllerSnapshot) -> Unit,
) {
    val state = remember { TouchControllerState() }
    DisposableEffect(Unit) {
        onDispose {
            state.cancelAll()
            onSnapshot(ControllerSnapshot.Neutral)
        }
    }
    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val scaleX = size.width / TouchControllerState.LogicalWidth
                    val scaleY = size.height / TouchControllerState.LogicalHeight
                    event.changes.forEach { change ->
                        val x = change.position.x / scaleX
                        val y = change.position.y / scaleY
                        when {
                            change.changedToDown() -> state.pointerDown(change.id.value, x, y)
                            !change.pressed -> state.pointerUp(change.id.value)
                            else -> state.pointerMove(change.id.value, x, y)
                        }
                        change.consume()
                    }
                    onSnapshot(state.snapshot())
                }
            }
        },
    ) {
        val sx = size.width / TouchControllerState.LogicalWidth
        val sy = size.height / TouchControllerState.LogicalHeight
        val scale = min(sx, sy)
        val fill = Color.White.copy(alpha = 0.09f)
        val outline = Color.White.copy(alpha = 0.32f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(180, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = 38f * scale
        }
        fun center(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun control(x: Float, y: Float, radius: Float, label: String) {
            drawCircle(fill, radius * scale, center(x, y))
            drawCircle(outline, radius * scale, center(x, y), style = Stroke(3f * scale))
            drawContext.canvas.nativeCanvas.drawText(label, x * sx, y * sy + 13f * scale, textPaint)
        }
        control(TouchControllerState.LEFT_STICK_X, TouchControllerState.STICK_Y, TouchControllerState.STICK_RADIUS, "L")
        control(TouchControllerState.RIGHT_STICK_X, TouchControllerState.STICK_Y, TouchControllerState.STICK_RADIUS, "R")
        TouchControllerState.dpadButtons.forEach { control(it.x, it.y, TouchControllerState.DPAD_RADIUS, it.label) }
        TouchControllerState.faceButtons.forEach { control(it.x, it.y, TouchControllerState.BUTTON_RADIUS, it.label) }
        listOf(
            Triple(40f, "L2", 200f), Triple(260f, "L1", 200f),
            Triple(1460f, "R1", 200f), Triple(1680f, "R2", 200f),
        ).forEach { (x, label, width) ->
            drawRoundRect(fill, center(x, 30f), Size(width * sx, 90f * sy), CornerRadius(18f * scale))
            drawContext.canvas.nativeCanvas.drawText(label, (x + width / 2f) * sx, 92f * sy, textPaint)
        }
        drawRoundRect(fill, center(820f, 30f), Size(280f * sx, 130f * sy), CornerRadius(20f * scale))
        drawContext.canvas.nativeCanvas.drawText("TOUCH", 960f * sx, 108f * sy, textPaint)
        drawRoundRect(fill, center(1120f, 170f), Size(140f * sx, 80f * sy), CornerRadius(18f * scale))
        drawContext.canvas.nativeCanvas.drawText("OPT", 1190f * sx, 224f * sy, textPaint)
    }
}
