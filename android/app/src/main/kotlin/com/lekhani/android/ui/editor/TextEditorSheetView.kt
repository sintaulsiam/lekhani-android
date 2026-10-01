package com.lekhani.android.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.theme.KeyboardTheme

/**
 * Text & Cursor Editor Sheet
 * Provides a dedicated navigation D-pad, precise character/word cursor traversal,
 * text selection mode, and clipboard actions (Cut, Copy, Paste, Select All).
 */
@Composable
fun TextEditorSheetView(
    theme: KeyboardTheme,
    sheetHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(heightModifier)
            .background(Color(theme.backgroundColor))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Header Bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.OpenWith,
                    contentDescription = null,
                    tint = Color(theme.accentColor),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) "Text Navigation & Editor" else "কার্সার ও টেক্সট এডিটর",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(theme.labelColor),
                )
            }

            // Close / Return to keyboard button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(theme.keyNormalColor))
                    .clickable { onClose() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = if (isEnglish) "Back to Keyboard" else "কীবোর্ডে ফিরুন",
                        tint = Color(theme.labelColor),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) "Keyboard" else "কীবোর্ড",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(theme.labelColor),
                    )
                }
            }
        }

        // ── Top Action Strip: 5 uniform actions (Select, Select All, Cut, Copy, Paste) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Select Mode Toggle
            ActionStripButton(
                label = if (isSelectActive) {
                    if (isEnglish) "Selecting" else "নির্বাচন"
                } else {
                    if (isEnglish) "Select" else "নির্বাচন"
                },
                icon = if (isSelectActive) Icons.Filled.SelectAll else null,
                isActive = isSelectActive,
                theme = theme,
                modifier = Modifier.weight(1.1f),
                onClick = { isSelectActive = !isSelectActive },
            )

            // Select All
            ActionStripButton(
                label = if (isEnglish) "Select All" else "সব নির্বাচন",
                icon = Icons.Filled.SelectAll,
                theme = theme,
                modifier = Modifier.weight(1.2f),
                onClick = {
                    onSelectAll()
                    isSelectActive = true
                },
            )

            // Cut
            ActionStripButton(
                label = if (isEnglish) "Cut" else "কাট",
                icon = Icons.Filled.ContentCut,
                theme = theme,
                modifier = Modifier.weight(0.9f),
                onClick = {
                    onCut()
                    isSelectActive = false
                },
            )

            // Copy
            ActionStripButton(
                label = if (isEnglish) "Copy" else "কপি",
                icon = Icons.Filled.ContentCopy,
                theme = theme,
                modifier = Modifier.weight(0.9f),
                onClick = {
                    onCopy()
                    isSelectActive = false
                },
            )

            // Paste
            ActionStripButton(
                label = if (isEnglish) "Paste" else "পেস্ট",
                icon = Icons.Filled.ContentPaste,
                theme = theme,
                modifier = Modifier.weight(0.9f),
                onClick = {
                    onPaste()
                    isSelectActive = false
                },
            )
        }

        // ── Main Controller Grid: Left Column, Center D-Pad, Right Column ──────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column: Home and Delete
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EditorSideButton(
                    label = if (isEnglish) "Home" else "শুরু (Home)",
                    theme = theme,
                    onClick = { onMoveHome(isSelectActive) },
                )
                EditorSideButton(
                    label = if (isEnglish) "Delete" else "মুছুন",
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    isDestructive = true,
                    theme = theme,
                    onClick = {
                        onBackspace()
                        isSelectActive = false
                    },
                )
            }

            // Center Column: 3x3 Unified Directional D-Pad
            Column(
                modifier = Modifier.padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Top: Up
                DPadArrowButton(
                    icon = Icons.Filled.KeyboardArrowUp,
                    theme = theme,
                    contentDesc = if (isEnglish) "Cursor Up" else "কার্সার উপরে",
                    onClick = { onMoveUp(isSelectActive) },
                )

                // Mid row: Left, Center Dot/SEL, Right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DPadArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        theme = theme,
                        contentDesc = if (isEnglish) "Cursor Left" else "কার্সার বাঁয়ে",
                        onClick = { onMoveLeft(isSelectActive) },
                    )

                    // Center Dot indicating active selection status, toggles on click
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelectActive) Color(theme.accentColor)
                                else Color(theme.keyNormalColor)
                            )
                            .clickable { isSelectActive = !isSelectActive },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isSelectActive) "SEL" else "•",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelectActive) Color(theme.backgroundColor) else Color(theme.labelColor).copy(alpha = 0.7f),
                        )
                    }

                    DPadArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        theme = theme,
                        contentDesc = if (isEnglish) "Cursor Right" else "কার্সার ডানে",
                        onClick = { onMoveRight(isSelectActive) },
                    )
                }

                // Bottom: Down
                DPadArrowButton(
                    icon = Icons.Filled.KeyboardArrowDown,
                    theme = theme,
                    contentDesc = if (isEnglish) "Cursor Down" else "কার্সার নিচে",
                    onClick = { onMoveDown(isSelectActive) },
                )
            }

            // Right Column: End and Enter
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EditorSideButton(
                    label = if (isEnglish) "End" else "শেষ (End)",
                    theme = theme,
                    onClick = { onMoveEnd(isSelectActive) },
                )
                EditorSideButton(
                    label = if (isEnglish) "Enter" else "নতুন লাইন",
                    icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                    isAccent = true,
                    theme = theme,
                    onClick = {
                        onEnter()
                        isSelectActive = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionStripButton(
    label: String,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) Color(theme.accentColor)
                else Color(theme.keyNormalColor)
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color(theme.backgroundColor) else Color(theme.labelColor),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color(theme.backgroundColor) else Color(theme.labelColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EditorSideButton(
    label: String,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    isAccent: Boolean = false,
    onClick: () -> Unit,
) {
    val bgColor = when {
        isAccent -> Color(theme.accentColor)
        isDestructive -> Color(theme.keyShiftColor)
        else -> Color(theme.keyNormalColor)
    }
    val contentColor = when {
        isAccent -> Color(theme.backgroundColor)
        isDestructive -> Color(0xFFFF5252)
        else -> Color(theme.labelColor)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DPadArrowButton(
    icon: ImageVector,
    theme: KeyboardTheme,
    contentDesc: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(theme.keyNormalColor))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = Color(theme.labelColor),
            modifier = Modifier.size(22.dp),
        )
    }
}
