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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.theme.KeyboardTheme

/**
 * Text & Cursor Editor Sheet
 * ══════════════════════════════════════════════════════════════════════════════
 * Provides a dedicated navigation D-pad, precise character/word cursor traversal,
 * text selection mode, and clipboard actions (Cut, Copy, Paste, Select All).
 *
 * Designed to ensure full text navigation capability even when the spacebar is
 * assigned to layout switching.
 */
@Composable
fun TextEditorSheetView(
    theme: KeyboardTheme,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(theme.backgroundColor))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEnglish) "Text Navigation & Editor" else "কার্সার ও টেক্সট এডিটর (Text Navigation)",
                    fontSize = 14.sp,
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
                        modifier = Modifier.size(16.dp),
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

        // ── Main Controller Grid ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column: Quick jumps & Select All
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EditorPillButton(
                    label = if (isEnglish) "Home" else "শুরু (Home)",
                    theme = theme,
                    onClick = { onMoveHome(isSelectActive) },
                )
                EditorPillButton(
                    label = if (isSelectActive) {
                        if (isEnglish) "✓ Selecting" else "✓ নির্বাচন চালু"
                    } else {
                        if (isEnglish) "Select Mode" else "নির্বাচন মোড"
                    },
                    theme = theme,
                    isActive = isSelectActive,
                    onClick = { isSelectActive = !isSelectActive },
                )
                EditorPillButton(
                    label = if (isEnglish) "Select All" else "সব নির্বাচন",
                    icon = Icons.Filled.SelectAll,
                    theme = theme,
                    onClick = onSelectAll,
                )
            }

            // Center Column: 4-Way D-Pad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Up
                DPadArrowButton(
                    icon = Icons.Filled.KeyboardArrowUp,
                    theme = theme,
                    contentDesc = if (isEnglish) "Cursor Up" else "কার্সার উপরে",
                    onClick = { onMoveUp(isSelectActive) },
                )

                // Left, Center Dot/Status, Right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DPadArrowButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        theme = theme,
                        contentDesc = if (isEnglish) "Cursor Left" else "কার্সার বাঁয়ে",
                        onClick = { onMoveLeft(isSelectActive) },
                    )

                    // Center Dot indicating active selection status
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelectActive) Color(theme.accentColor)
                                else Color(theme.keyNormalColor)
                            ),
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

                // Down
                DPadArrowButton(
                    icon = Icons.Filled.KeyboardArrowDown,
                    theme = theme,
                    contentDesc = if (isEnglish) "Cursor Down" else "কার্সার নিচে",
                    onClick = { onMoveDown(isSelectActive) },
                )
            }

            // Right Column: Line End & Editing
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EditorPillButton(
                    label = if (isEnglish) "End" else "শেষ (End)",
                    theme = theme,
                    onClick = { onMoveEnd(isSelectActive) },
                )
                EditorPillButton(
                    label = if (isEnglish) "Cut" else "কাট (Cut)",
                    icon = Icons.Filled.ContentCut,
                    theme = theme,
                    onClick = onCut,
                )
                EditorPillButton(
                    label = if (isEnglish) "Copy" else "কপি (Copy)",
                    icon = Icons.Filled.ContentCopy,
                    theme = theme,
                    onClick = onCopy,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Bottom Action Row (Paste, Backspace, Enter) ────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Paste
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(theme.keyNormalColor))
                    .clickable { onPaste() },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = if (isEnglish) "Paste" else "পেস্ট",
                        tint = Color(theme.accentColor),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "Paste" else "পেস্ট (Paste)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(theme.labelColor),
                    )
                }
            }

            // Backspace
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(theme.keyShiftColor))
                    .clickable { onBackspace() },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = if (isEnglish) "Delete" else "ব্যাকস্পেস",
                        tint = Color(theme.labelColor),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) "Delete" else "মুছুন",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(theme.labelColor),
                    )
                }
            }

            // Enter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(theme.accentColor))
                    .clickable { onEnter() },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = if (isEnglish) "Enter" else "নতুন লাইন",
                        tint = Color(theme.backgroundColor),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEnglish) "Enter" else "নতুন লাইন",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(theme.backgroundColor),
                    )
                }
            }
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
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(theme.keyNormalColor))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = Color(theme.labelColor),
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun EditorPillButton(
    label: String,
    theme: KeyboardTheme,
    icon: ImageVector? = null,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(105.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive) Color(theme.accentColor)
                else Color(theme.keyNormalColor)
            )
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
                    tint = if (isActive) Color(theme.backgroundColor) else Color(theme.labelColor),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color(theme.backgroundColor) else Color(theme.labelColor),
                maxLines = 1,
            )
        }
    }
}
