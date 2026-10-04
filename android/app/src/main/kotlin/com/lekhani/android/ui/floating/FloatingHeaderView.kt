package com.lekhani.android.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.theme.KeyboardTheme

/**
 * Material 3 Expressive drag header bar for Floating Keyboard Mode.
 *
 * Provides:
 *  - Centered drag handle pill for smooth repositioning anywhere on screen.
 *  - Quick "Dock / Expand" button to return to standard docked keyboard mode.
 *  - High-contrast visual styling adapted dynamically to the active Lekhani theme.
 */
@Composable
fun FloatingHeaderView(
    theme: KeyboardTheme,
    isEnglish: Boolean,
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDockToStandard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = Color(theme.backgroundColor)
    val contentColor = Color(theme.labelColor)
    val accentColor = Color(theme.accentColor)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
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
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left badge / label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = if (isEnglish) "Floating" else "ভাসমান",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.65f)
            )
        }

        // Center drag handle pill
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(contentColor.copy(alpha = 0.35f))
        )

        // Right dock / fullscreen restore action
        IconButton(
            onClick = onDockToStandard,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FitScreen,
                contentDescription = if (isEnglish) "Dock Keyboard" else "কীবোর্ড ডক করুন",
                tint = contentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
