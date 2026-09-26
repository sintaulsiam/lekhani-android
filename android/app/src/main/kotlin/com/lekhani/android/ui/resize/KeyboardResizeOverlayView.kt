package com.lekhani.android.ui.resize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Visual interactive keyboard height adjuster overlay.
 *
 * Provides:
 * 1. Tactile draggable handle bar to adjust keyboard height smoothly in real time at 120 FPS.
 * 2. Step increment/decrement buttons ([-5%], [+5%]).
 * 3. Quick reset button to restore optimal default scale (100%).
 * 4. Confirmation commit [Check] and cancel [Close] buttons.
 */
@Composable
fun KeyboardResizeOverlayView(
    initialScale: Float,
    theme: KeyboardTheme,
    onScaleLiveChange: (Float) -> Unit,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(initialScale.coerceIn(0.70f, 1.35f)) }
    val density = LocalDensity.current.density

    val bg = Color(theme.backgroundColor)
    val cardBg = Color(theme.keyNormalColor)
    val accent = Color(theme.accentColor)
    val textColor = Color(theme.labelColor)
    val dimText = Color(theme.labelDimColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Top Tactile Drag Handle ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.16f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        // Dragging UP (-dragAmount) increases keyboard height
                        // Dragging DOWN (+dragAmount) decreases keyboard height
                        val deltaScale = -dragAmount / (density * 160f)
                        val newScale = (scale + deltaScale).coerceIn(0.70f, 1.35f)
                        scale = newScale
                        onScaleLiveChange(newScale)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = "টেনে আকার পরিবর্তন করুন",
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "টেনে আকার পরিবর্তন করুন (Drag to Resize)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Controls Row (Steppers, Live %, Reset, Commit) ───────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Cancel Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "বাতিল",
                    tint = dimText,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Stepper: Minus 5%
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .clickable {
                        val newScale = (scale - 0.05f).coerceIn(0.70f, 1.35f)
                        scale = newScale
                        onScaleLiveChange(newScale)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "ছোট করুন",
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Percentage Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardBg)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val percent = (scale * 100).roundToInt()
                val label = if (percent == 100) "১০০% (স্বাভাবিক)" else "$percent%"
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (percent == 100) accent else textColor,
                )
            }

            // Stepper: Plus 5%
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .clickable {
                        val newScale = (scale + 0.05f).coerceIn(0.70f, 1.35f)
                        scale = newScale
                        onScaleLiveChange(newScale)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "বড় করুন",
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Reset to 100% Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .clickable {
                        scale = 1.0f
                        onScaleLiveChange(1.0f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "রিসেট",
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Confirm Done Button
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent)
                    .clickable { onConfirm(scale) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "সম্পন্ন",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "সম্পন্ন",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}
