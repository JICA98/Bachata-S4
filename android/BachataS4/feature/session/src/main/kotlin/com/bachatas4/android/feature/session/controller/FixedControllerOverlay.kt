package com.bachatas4.android.feature.session.controller

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
    layout: TouchLayout = TouchLayout(),
    onSnapshot: (ControllerSnapshot) -> Unit,
) {
    val state = remember(layout) { TouchControllerState(layout) }
    DisposableEffect(state) {
        onDispose { state.cancelAll(); onSnapshot(ControllerSnapshot.Neutral) }
    }
    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(layout) {
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
        val densityScale = min(size.width / 1920f, size.height / 1080f)
        val fill = Color.White.copy(alpha = layout.opacity * 0.3f)
        val outline = Color.White.copy(alpha = layout.opacity)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((layout.opacity * 255).toInt(), 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = 26f * densityScale
        }
        layout.controls.filter(TouchControlPlacement::visible).forEach { control ->
            val width = control.width * size.width * layout.scale
            val height = control.height * size.height * layout.scale
            val topLeft = Offset(control.centerX * size.width - width / 2f, control.centerY * size.height - height / 2f)
            drawRoundRect(fill, topLeft, Size(width, height), CornerRadius(min(width, height) / 2f))
            drawRoundRect(outline, topLeft, Size(width, height), CornerRadius(min(width, height) / 2f), style = Stroke(3f * densityScale))
            drawContext.canvas.nativeCanvas.drawText(control.control, control.centerX * size.width, control.centerY * size.height + 9f * densityScale, textPaint)
        }
    }
}
