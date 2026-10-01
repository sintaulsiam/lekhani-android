package com.lekhani.android.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.theme.KeyboardTheme

/**
 * Professional Text Navigation & Cursor Editor Sheet.
 *
 * Designed to Gboard / Android IME gold standard:
 *
 * ┌────────────────────────────────────────────────────────────────────────┐
 * │ [← কীবোর্ড / Keyboard]             [✂ কাট]   [⧉ কপি]   [📋 পেস্ট]    │  ← Header Toolbar
 * ├─────────────────────────┬────────────────────────────┬─────────────────┤
 * │ [ ⊞ সবটুকু / All ]      │         [ ▲ উপরে ]         │                 │
 * │                         ├──────────────┬─────────────┤   [ ⌫ মুছুন ]   │
 * │ [ |◀ শুরুতে / Home ]   │ [◀] [ ◉ বাছাই ] [▶]        │   Backspace     │
 * │                         ├──────────────┴─────────────┤                 │
 * │ [ ▶| শেষে / End ]       │         [ ▼ নিচে ]         │   [ ↵ Enter ]   │
 * └─────────────────────────┴────────────────────────────┴─────────────────┘
 *                           [ Safe Chin Area for Gesture Bar ]
 *
 * Key Design Principles:
 *   • Tactile 3D Key Styling: Each key features a 2dp bottom shadow lip matching the
 *     native Canvas keyboard keys.
 *   • True D-Pad Controller: Large 30dp+ bold directional arrows with an illuminated
 *     selection badge at the center.
 *   • Clean Gesture Bar Clearance: Dedicated bottom chin padding prevents the Android
 *     gesture pill line from colliding with interactive buttons.
 *   • Seamless Selection Lifecycle: Selection mode glows accent when active, and
 *     automatically deactivates after Cut, Copy, or Paste.
 *   • Adaptive Breakpoints: Smoothly scales tokens on tiny (<320dp) and compact (<360dp) screens.
 */
@Composable
fun TextEditorSheetView(
    theme: KeyboardTheme,
    sheetHeight: Dp = Dp.Unspecified,
    isEnglish: Boolean = false,
    onMoveLeft: (select: Boolean) -> Unit,
    onMoveRight: (select: Boolean) -> Unit,
    onMoveUp: (select: Boolean) -> Unit,
    onMoveDown: (select: Boolean) -> Unit,
    onMoveHome: (select: Boolean) -> Unit,
    onMoveEnd: (select: Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onClose: () -> Unit,
) {
    var isSelectActive by remember { mutableStateOf(false) }
    val heightModifier = if (sheetHeight.value > 0f) Modifier.height(sheetHeight) else Modifier
    val view = LocalView.current
    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    // Palette sourced directly from active KeyboardTheme
    val colBg      = Color(theme.backgroundColor)
    val colKey     = Color(theme.keyNormalColor)
    val colSpecial = blendColors(colKey, colBg, 0.45f)
    val colAccent  = Color(theme.accentColor)
    val colLabel   = Color(theme.labelColor)
    val colShadow  = blendColors(colKey, Color.Black, 0.50f)
    val colSpecialShadow = blendColors(colSpecial, Color.Black, 0.55f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(heightModifier)
            .background(colBg),
    ) {
        val w = maxWidth
        val isTiny    = w < 320.dp
        val isCompact = w < 360.dp

        // Scaled geometry tokens
        val padH: Dp       = if (isTiny) 4.dp else if (isCompact) 6.dp else 8.dp
        val padTop: Dp     = if (isTiny) 4.dp else 6.dp
        val bottomChin: Dp = if (isTiny) 10.dp else if (isCompact) 13.dp else 16.dp
        val gap: Dp        = if (isTiny) 3.5.dp else if (isCompact) 4.5.dp else 6.dp
        val corner: Dp     = if (isTiny) 7.dp else if (isCompact) 8.5.dp else 10.dp

        val headerH: Dp    = if (isTiny) 36.dp else if (isCompact) 38.dp else 42.dp
        val arrowIconDp: Dp = if (isTiny) 26.dp else if (isCompact) 28.dp else 32.dp
        val sideIconDp: Dp  = if (isTiny) 15.dp else if (isCompact) 17.dp else 19.dp
        val actionIconDp: Dp = if (isTiny) 14.dp else if (isCompact) 15.dp else 16.dp

        val labelSp: TextUnit = if (isTiny) 9.sp else if (isCompact) 10.sp else 11.sp
        val headerSp: TextUnit = if (isTiny) 10.5.sp else if (isCompact) 11.5.sp else 12.5.sp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = padH, end = padH, top = padTop, bottom = bottomChin),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {

            // ── Top Header Toolbar: Return to Keyboard + Clipboard Actions ────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerH),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Return to Keyboard pill button
                TactilePill(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = if (isEnglish) "Keyboard" else "কীবোর্ড",
                    colKey = colSpecial,
                    colShadow = colSpecialShadow,
                    colContent = colAccent,
                    corner = corner,
                    iconSize = actionIconDp + 2.dp,
                    fontSize = headerSp,
                    onClick = { haptic(); onClose() },
                )

                // Quick Clipboard Actions: Cut, Copy, Paste
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isTiny) 3.dp else 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val cutCopyAlpha = if (isSelectActive) 1.0f else 0.45f

                    // Cut
                    TactilePill(
                        icon = Icons.Filled.ContentCut,
                        label = if (isEnglish) "Cut" else "কাট",
                        colKey = if (isSelectActive) blendColors(colSpecial, colAccent, 0.22f) else colSpecial,
                        colShadow = colSpecialShadow,
                        colContent = if (isSelectActive) colAccent else colLabel.copy(alpha = cutCopyAlpha),
                        corner = corner,
                        iconSize = actionIconDp,
                        fontSize = labelSp,
                        onClick = {
                            haptic()
                            onCut()
                            isSelectActive = false
                        },
                    )

                    // Copy
                    TactilePill(
                        icon = Icons.Filled.ContentCopy,
                        label = if (isEnglish) "Copy" else "কপি",
                        colKey = if (isSelectActive) blendColors(colSpecial, colAccent, 0.22f) else colSpecial,
                        colShadow = colSpecialShadow,
                        colContent = if (isSelectActive) colAccent else colLabel.copy(alpha = cutCopyAlpha),
                        corner = corner,
                        iconSize = actionIconDp,
                        fontSize = labelSp,
                        onClick = {
                            haptic()
                            onCopy()
                            isSelectActive = false
                        },
                    )

                    // Paste
                    TactilePill(
                        icon = Icons.Filled.ContentPaste,
                        label = if (isEnglish) "Paste" else "পেস্ট",
                        colKey = colSpecial,
                        colShadow = colSpecialShadow,
                        colContent = colLabel,
                        corner = corner,
                        iconSize = actionIconDp,
                        fontSize = labelSp,
                        onClick = {
                            haptic()
                            onPaste()
                            isSelectActive = false
                        },
                    )
                }
            }

            // ── Main Body: 3-Zone Architecture ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {

                // ── Left Column: Document Navigation & Select All ─────────────
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    TactileKey(
                        icon = Icons.Filled.SelectAll,
                        label = if (isEnglish) "Select All" else "সবটুকু",
                        colKey = colKey,
                        colShadow = colShadow,
                        colContent = colLabel,
                        corner = corner,
                        iconSize = sideIconDp,
                        fontSize = labelSp,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            haptic()
                            onSelectAll()
                            isSelectActive = true
                        },
                    )

                    TactileKey(
                        icon = Icons.Filled.FirstPage,
                        label = if (isEnglish) "Home" else "শুরু",
                        colKey = colKey,
                        colShadow = colShadow,
                        colContent = colLabel,
                        corner = corner,
                        iconSize = sideIconDp,
                        fontSize = labelSp,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            haptic()
                            onMoveHome(isSelectActive)
                        },
                    )

                    TactileKey(
                        icon = Icons.AutoMirrored.Filled.LastPage,
                        label = if (isEnglish) "End" else "শেষ",
                        colKey = colKey,
                        colShadow = colShadow,
                        colContent = colLabel,
                        corner = corner,
                        iconSize = sideIconDp,
                        fontSize = labelSp,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            haptic()
                            onMoveEnd(isSelectActive)
                        },
                    )
                }

                // ── Center Column: The Directional D-Pad ──────────────────────
                Column(
                    modifier = Modifier
                        .weight(1.55f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    // Up Arrow
                    TactileArrowKey(
                        icon = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Up",
                        colKey = colKey,
                        colShadow = colShadow,
                        colContent = colLabel,
                        corner = corner,
                        arrowSize = arrowIconDp,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            haptic()
                            onMoveUp(isSelectActive)
                        },
                    )

                    // Horizontal Cross Row: Left Arrow · Center Select Toggle · Right Arrow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.05f),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        TactileArrowKey(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Left",
                            colKey = colKey,
                            colShadow = colShadow,
                            colContent = colLabel,
                            corner = corner,
                            arrowSize = arrowIconDp,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = {
                                haptic()
                                onMoveLeft(isSelectActive)
                            },
                        )

                        // Center Select Toggle Button
                        TactileCenterSelectKey(
                            isActive = isSelectActive,
                            isEnglish = isEnglish,
                            colKey = colKey,
                            colShadow = colShadow,
                            colAccent = colAccent,
                            colLabel = colLabel,
                            corner = corner,
                            fontSize = labelSp,
                            iconSize = sideIconDp,
                            modifier = Modifier.weight(1.25f).fillMaxHeight(),
                            onClick = {
                                haptic()
                                isSelectActive = !isSelectActive
                            },
                        )

                        TactileArrowKey(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Right",
                            colKey = colKey,
                            colShadow = colShadow,
                            colContent = colLabel,
                            corner = corner,
                            arrowSize = arrowIconDp,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = {
                                haptic()
                                onMoveRight(isSelectActive)
                            },
                        )
                    }

                    // Down Arrow
                    TactileArrowKey(
                        icon = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Down",
                        colKey = colKey,
                        colShadow = colShadow,
                        colContent = colLabel,
                        corner = corner,
                        arrowSize = arrowIconDp,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            haptic()
                            onMoveDown(isSelectActive)
                        },
                    )
                }

                // ── Right Column: Backspace & Enter ───────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    // Backspace / Delete Key
                    TactileKey(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        label = if (isEnglish) "Delete" else "মুছুন",
                        colKey = colSpecial,
                        colShadow = colSpecialShadow,
                        colContent = colLabel,
                        corner = corner,
                        iconSize = sideIconDp + 2.dp,
                        fontSize = labelSp,
                        modifier = Modifier.fillMaxWidth().weight(1.5f),
                        onClick = {
                            haptic()
                            onBackspace()
                            isSelectActive = false
                        },
                    )

                    // Enter / Return Key
                    TactileKey(
                        icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                        label = if (isEnglish) "Enter" else "নতুন লাইন",
                        colKey = blendColors(colSpecial, colAccent, 0.16f),
                        colShadow = blendColors(colSpecialShadow, colAccent, 0.10f),
                        colContent = colAccent,
                        corner = corner,
                        iconSize = sideIconDp + 2.dp,
                        fontSize = labelSp,
                        modifier = Modifier.fillMaxWidth().weight(1.2f),
                        onClick = {
                            haptic()
                            onEnter()
                            isSelectActive = false
                        },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tactile Key Composables (True Keyboard Key Depth & Sizing)
// ─────────────────────────────────────────────────────────────────────────────

/** Standard tactile key: icon stacked above label with a 2dp bottom shadow lip. */
@Composable
private fun TactileKey(
    icon: ImageVector,
    label: String,
    colKey: Color,
    colShadow: Color,
    colContent: Color,
    corner: Dp,
    iconSize: Dp,
    fontSize: TextUnit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(colShadow)
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape((corner - 1.dp).coerceAtLeast(2.dp)))
            .background(colKey)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colContent,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = colContent.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Directional arrow key: bold prominent icon centered in tactile key. */
@Composable
private fun TactileArrowKey(
    icon: ImageVector,
    contentDescription: String,
    colKey: Color,
    colShadow: Color,
    colContent: Color,
    corner: Dp,
    arrowSize: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(colShadow)
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape((corner - 1.dp).coerceAtLeast(2.dp)))
            .background(colKey)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colContent,
            modifier = Modifier.size(arrowSize),
        )
    }
}

/** Center selection toggle button: glows accent when active with distinct state badge. */
@Composable
private fun TactileCenterSelectKey(
    isActive: Boolean,
    isEnglish: Boolean,
    colKey: Color,
    colShadow: Color,
    colAccent: Color,
    colLabel: Color,
    corner: Dp,
    fontSize: TextUnit,
    iconSize: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bg = if (isActive) colAccent else colKey
    val shadow = if (isActive) blendColors(colAccent, Color.Black, 0.40f) else colShadow
    val contentCol = if (isActive) colKey else colLabel
    val subCol = if (isActive) colKey.copy(alpha = 0.80f) else colLabel.copy(alpha = 0.60f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(shadow)
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape((corner - 1.dp).coerceAtLeast(2.dp)))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isActive) Icons.Filled.TouchApp else Icons.Filled.CropFree,
                contentDescription = if (isActive) "Selection Active" else "Select Mode",
                tint = contentCol,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isActive) {
                    if (isEnglish) "ACTIVE" else "চলছে"
                } else {
                    if (isEnglish) "Select" else "বাছাই"
                },
                fontSize = fontSize,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = subCol,
                maxLines = 1,
            )
        }
    }
}

/** Header pill button: compact horizontal icon + text with tactile depth. */
@Composable
private fun TactilePill(
    icon: ImageVector,
    label: String,
    colKey: Color,
    colShadow: Color,
    colContent: Color,
    corner: Dp,
    iconSize: Dp,
    fontSize: TextUnit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(colShadow)
            .padding(bottom = 1.5.dp)
            .clip(RoundedCornerShape((corner - 0.75.dp).coerceAtLeast(2.dp)))
            .background(colKey)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colContent,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = colContent,
                maxLines = 1,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color Interpolation Utility
// ─────────────────────────────────────────────────────────────────────────────

/** Linear colour interpolation between [a] and [b] by [t] (0 = full a, 1 = full b). */
private fun blendColors(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
