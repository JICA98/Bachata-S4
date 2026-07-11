package com.bachatas4.android.feature.session.controller

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bachatas4.android.designsystem.theme.BachataPalette
import com.bachatas4.android.runtime.input.ControllerSnapshot
import kotlin.math.min

@Composable
fun FixedControllerOverlay(
    modifier: Modifier = Modifier,
    layout: TouchLayout = TouchLayout(),
    onSnapshot: (ControllerSnapshot) -> Unit,
) {
    val state = remember(layout) { TouchControllerState(layout) }
    var faded by remember { mutableStateOf(false) }
    DisposableEffect(state) {
        onDispose { state.cancelAll(); onSnapshot(ControllerSnapshot.Neutral) }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(layout) {
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
            val renderAlpha = (layout.opacity * if (faded) 0.35f else 1f).coerceIn(0.05f, 1f)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 26f * densityScale
            }
            layout.controls.filter(TouchControlPlacement::visible).forEach { control ->
                val width = control.width * size.width * layout.scale
                val height = control.height * size.height * layout.scale
                val topLeft = Offset(control.centerX * size.width - width / 2f, control.centerY * size.height - height / 2f)
                val style = TouchControlVisualStyle.forControl(control.control)
                val fill = Color.White.copy(alpha = renderAlpha * if (style == TouchControlVisualStyle.Stick) 0.12f else 0.07f)
                val outlineColor = visualColor(control.control).copy(alpha = renderAlpha)
                when (style) {
                    TouchControlVisualStyle.Stick -> {
                        val center = Offset(topLeft.x + width / 2f, topLeft.y + height / 2f)
                        drawCircle(fill, min(width, height) / 2f, center)
                        drawCircle(outlineColor, min(width, height) / 2f, center, style = Stroke(3f * densityScale))
                        drawCircle(
                            Color(0xFF303544).copy(alpha = renderAlpha),
                            min(width, height) / 4f,
                            center,
                        )
                    }
                    else -> {
                        drawRoundRect(
                            color = fill,
                            topLeft = topLeft,
                            size = Size(width, height),
                            cornerRadius = CornerRadius(min(width, height) * if (style == TouchControlVisualStyle.Dpad) 0.12f else 0.2f),
                        )
                        drawRoundRect(
                            color = outlineColor,
                            topLeft = topLeft,
                            size = Size(width, height),
                            cornerRadius = CornerRadius(min(width, height) * if (style == TouchControlVisualStyle.Dpad) 0.12f else 0.2f),
                            style = Stroke(if (style == TouchControlVisualStyle.Face) 3f else 2f * densityScale),
                        )
                    }
                }
                textPaint.color = outlineColor.toArgb()
                textPaint.textSize = (if (style == TouchControlVisualStyle.Stick) 18f else 26f) * densityScale
                drawContext.canvas.nativeCanvas.drawText(
                    displayLabel(control.control),
                    control.centerX * size.width,
                    control.centerY * size.height + textPaint.textSize * 0.35f,
                    textPaint,
                )
            }
        }
        Text(
            text = if (faded) "SHOW" else "FADE",
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { faded = !faded }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = BachataPalette.Primary,
        )
    }
}

private fun visualColor(control: String): Color = when (control) {
    "triangle" -> Color(0xFF5DDE90)
    "circle" -> Color(0xFFF06292)
    "cross" -> Color(0xFF5B9CF6)
    "square" -> Color(0xFFE57EF0)
    else -> Color(0xFFCFD8DC)
}

private fun displayLabel(control: String): String = when (control) {
    "triangle" -> "△"
    "circle" -> "○"
    "cross" -> "✕"
    "square" -> "□"
    "dpad_up" -> "▲"
    "dpad_right" -> "▶"
    "dpad_down" -> "▼"
    "dpad_left" -> "◀"
    "left_stick" -> "L3"
    "right_stick" -> "R3"
    else -> control.uppercase()
}
