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
 * Visual interactive keyboard height & bottom clearance adjuster overlay.
 *
 * Provides:
 * 1. Tactile draggable handle bar to adjust keyboard height smoothly in real time at 120 FPS.
 * 2. Step increment/decrement buttons ([-5%], [+5%]).
 * 3. Quick bottom clearance presets (0dp, 8dp, 14dp, 20dp).
 * 4. Quick reset button to restore optimal default scale and bottom clearance.
 * 5. Confirmation commit [Check] and cancel [Close] buttons.
 */
@Composable
fun KeyboardResizeOverlayView(
    initialScale: Float,
    initialChin: Float = 0f,
    theme: KeyboardTheme,
    isEnglish: Boolean = false,
    onScaleLiveChange: (Float) -> Unit,
    onChinLiveChange: (Float) -> Unit = {},
    onConfirm: (scale: Float, chin: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(initialScale.coerceIn(0.70f, 1.35f)) }
    var chin by remember { mutableFloatStateOf(initialChin.coerceIn(0f, 32f)) }
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
                    contentDescription = if (isEnglish) "Drag to Resize" else "টেনে আকার পরিবর্তন করুন",
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) "Drag up/down for Height" else "উচ্চতা পরিবর্তন করতে উপরে/নিচে টানুন",
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
                .height(38.dp),
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
                    contentDescription = if (isEnglish) "Cancel" else "বাতিল",
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
                    contentDescription = if (isEnglish) "Decrease Height (-5%)" else "উচ্চতা কমান (-৫%)",
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
                val label = if (percent == 100) {
                    if (isEnglish) "100%" else "১০০%"
                } else {
                    if (isEnglish) "$percent%" else "${toBengaliDigits(percent)}%"
                }
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
                    contentDescription = if (isEnglish) "Increase Height (+5%)" else "উচ্চতা বাড়ান (+৫%)",
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Reset to Default Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .clickable {
                        val defChin = com.lekhani.android.data.settings.KeyboardPreferences.getDefaultBottomChin()
                        scale = 1.0f
                        chin = defChin
                        onScaleLiveChange(1.0f)
                        onChinLiveChange(defChin)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = if (isEnglish) "Reset to Default" else "ডিফল্ট উচ্চতায় রিসেট",
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
                    .clickable { onConfirm(scale, chin) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = if (isEnglish) "Done" else "সম্পন্ন",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) "Done" else "সম্পন্ন",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Bottom Space Preset Chips Row ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (isEnglish) "Bottom:" else "নিচে:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = dimText,
                modifier = Modifier.padding(end = 2.dp),
            )
            val chinPresets = listOf(0f to "0dp", 8f to "8dp", 14f to "14dp", 20f to "20dp")
            chinPresets.forEach { (cVal, cLabel) ->
                val isSel = Math.abs(chin - cVal) < 1.0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) accent.copy(alpha = 0.25f) else cardBg)
                        .clickable {
                            chin = cVal
                            onChinLiveChange(cVal)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = cLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) accent else textColor,
                    )
                }
            }
        }
    }
}

private fun toBengaliDigits(number: Int): String {
    val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return number.toString().map { if (it in '0'..'9') bnDigits[it - '0'] else it }.joinToString("")
}
