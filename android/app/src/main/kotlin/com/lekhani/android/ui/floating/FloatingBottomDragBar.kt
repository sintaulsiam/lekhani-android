package com.lekhani.android.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lekhani.android.theme.KeyboardTheme

/**
 * Material 3 Expressive bottom drag bar for Floating Keyboard Mode.
 * Allows effortless one-thumb 360° repositioning from the bottom edge.
 */
@Composable
fun FloatingBottomDragBar(
    theme: KeyboardTheme,
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = Color(theme.backgroundColor)
    val contentColor = Color(theme.labelColor)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(barBg)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(contentColor.copy(alpha = 0.35f))
        )
    }
}
