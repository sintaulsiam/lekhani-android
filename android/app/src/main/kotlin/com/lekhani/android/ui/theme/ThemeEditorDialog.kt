package com.lekhani.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lekhani.android.theme.KeyboardTheme

/**
 * ThemeEditorDialog
 * ══════════════════════════════════════════════════════════════════════════════
 * Interactive Material 3 theme customizer with real-time mini-keyboard canvas preview,
 * color swatches, WCAG 2.1 AA luminance contrast checker, and direct local persistence.
 */
@Composable
fun ThemeEditorDialog(
    initialTheme: KeyboardTheme,
    isEnglish: Boolean = false,
    onSave: (KeyboardTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    var themeName by remember { mutableStateOf(if (isEnglish) initialTheme.nameEnglish else initialTheme.nameBengali) }
    var bgColor by remember { mutableIntStateOf(initialTheme.backgroundColor) }
    var keyColor by remember { mutableIntStateOf(initialTheme.keyNormalColor) }
    var labelColor by remember { mutableIntStateOf(initialTheme.labelColor) }
    var accentColor by remember { mutableIntStateOf(initialTheme.accentColor) }
    var spaceColor by remember { mutableIntStateOf(initialTheme.keySpaceColor) }
    var activeColorTarget by remember { mutableStateOf(ColorTarget.BACKGROUND) }

    // WCAG 2.1 Luminance Contrast Ratio
    val textContrast = remember(labelColor, keyColor) {
        contrastRatio(labelColor, keyColor)
    }
    val isContrastPass = textContrast >= 4.5

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) "Theme Studio Editor" else "থিম স্টুডিও এডিটর",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Theme Name Input
                OutlinedTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    label = { Text(if (isEnglish) "Theme Name" else "থিমের নাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ── Live Mini-Keyboard Canvas Preview ─────────────────────────
                Text(
                    text = if (isEnglish) "Live Interactive Preview" else "লাইভ কীবোর্ড প্রিভিউ",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(bgColor)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Preview Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("আ", "ো", "ী", "প", "ব", "ম", "দ", "ল").forEach { char ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(keyColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char, color = Color(labelColor), fontSize = 12.sp)
                                }
                            }
                        }

                        // Preview Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("অ", "া", "ি", "র", "ত", "ন", "স", "ক").forEach { char ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(keyColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char, color = Color(labelColor), fontSize = 12.sp)
                                }
                            }
                        }

                        // Preview Row 3 (Spacebar row)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(keyColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("?123", color = Color(labelColor), fontSize = 10.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(3.5f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(spaceColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isEnglish) "Space" else "স্পেস", color = Color(labelColor), fontSize = 10.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(accentColor).copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("্", color = Color(accentColor), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(accentColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↵", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // WCAG 2.1 AA Contrast Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val badgeColor = if (isContrastPass) Color(0xFF2E7D32) else Color(0xFFD84315)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isContrastPass) "✓ WCAG AA Passed (${String.format("%.1f", textContrast)}:1)"
                                   else "⚠️ Low Contrast (${String.format("%.1f", textContrast)}:1)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isContrastPass) (if (isEnglish) "Clear text legibility" else "স্পষ্ট পাঠযোগ্যতা")
                               else (if (isEnglish) "Text may be hard to read" else "অক্ষর অস্পষ্ট হতে পারে"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color Target Selector Tabs
                Text(
                    text = if (isEnglish) "Customize Color Property" else "রং পরিবর্তন করুন",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ColorTarget.values()) { target ->
                        val isSelected = activeColorTarget == target
                        val title = when (target) {
                            ColorTarget.BACKGROUND -> if (isEnglish) "Background" else "ব্যাকগ্রাউন্ড"
                            ColorTarget.KEY -> if (isEnglish) "Buttons" else "কী-বাটন"
                            ColorTarget.TEXT -> if (isEnglish) "Text Label" else "অক্ষরের রঙ"
                            ColorTarget.ACCENT -> if (isEnglish) "Accent" else "অ্যাকসেন্ট"
                            ColorTarget.SPACE -> if (isEnglish) "Spacebar" else "স্পেসবার"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { activeColorTarget = target }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Palette Swatches for Active Target
                val swatches = listOf(
                    0xFF0A111E.toInt(), 0xFF121212.toInt(), 0xFF000000.toInt(), 0xFF181419.toInt(),
                    0xFF0A1410.toInt(), 0xFF0F172A.toInt(), 0xFF17130E.toInt(), 0xFF1E293B.toInt(),
                    0xFF241E26.toInt(), 0xFF12241C.toInt(), 0xFF2D241E.toInt(), 0xFF00E5B8.toInt(),
                    0xFF00E676.toInt(), 0xFF64B5F6.toInt(), 0xFF38BDF8.toInt(), 0xFFF48FB1.toInt(),
                    0xFFFF9100.toInt(), 0xFFCFBCFF.toInt(), 0xFFFFD700.toInt(), 0xFFFFFFFF.toInt(),
                    0xFFF7F9FA.toInt(), 0xFFF3ECE4.toInt(), 0xFFFAF6F2.toInt(), 0xFF94A3B8.toInt()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Select Color Swatch" else "কালার সোয়াচ নির্বাচন করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Swatch Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    swatches.chunked(8).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { colorInt ->
                                val isChosen = when (activeColorTarget) {
                                    ColorTarget.BACKGROUND -> bgColor == colorInt
                                    ColorTarget.KEY -> keyColor == colorInt
                                    ColorTarget.TEXT -> labelColor == colorInt
                                    ColorTarget.ACCENT -> accentColor == colorInt
                                    ColorTarget.SPACE -> spaceColor == colorInt
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .border(
                                            width = if (isChosen) 2.5.dp else 1.dp,
                                            color = if (isChosen) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            when (activeColorTarget) {
                                                ColorTarget.BACKGROUND -> bgColor = colorInt
                                                ColorTarget.KEY -> keyColor = colorInt
                                                ColorTarget.TEXT -> labelColor = colorInt
                                                ColorTarget.ACCENT -> accentColor = colorInt
                                                ColorTarget.SPACE -> spaceColor = colorInt
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isChosen) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (calculateLuminance(colorInt) > 0.5) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons (Cancel / Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isEnglish) "Cancel" else "বাতিল")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val isDark = calculateLuminance(bgColor) < 0.5
                            val finalTheme = initialTheme.copy(
                                id = if (initialTheme.isCustom) initialTheme.id else "custom_${System.currentTimeMillis()}",
                                nameBengali = themeName.ifBlank { "কাস্টম থিম" },
                                nameEnglish = themeName.ifBlank { "Custom Theme" },
                                backgroundColor = bgColor,
                                keyNormalColor = keyColor,
                                keyShiftColor = keyColor,
                                keySpaceColor = spaceColor,
                                keyHasantaColor = accentColor,
                                keyBorderColor = if (isDark) 0x33FFFFFF else 0x22000000,
                                labelColor = labelColor,
                                labelDimColor = if (isDark) 0xFFA0AEC0.toInt() else 0xFF718096.toInt(),
                                accentColor = accentColor,
                                rippleColor = (accentColor and 0x00FFFFFF) or 0x40000000,
                                glideStrokeColor = accentColor,
                                glideGlowColor = (accentColor and 0x00FFFFFF) or 0x40000000,
                                isDark = isDark,
                                isCustom = true
                            )
                            onSave(finalTheme)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isEnglish) "Save Theme" else "থিম সংরক্ষণ করুন")
                    }
                }
            }
        }
    }
}

private enum class ColorTarget {
    BACKGROUND,
    KEY,
    TEXT,
    ACCENT,
    SPACE,
}

private fun calculateLuminance(color: Int): Double {
    val r = ((color shr 16) and 0xFF) / 255.0
    val g = ((color shr 8) and 0xFF) / 255.0
    val b = (color and 0xFF) / 255.0
    val rL = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
    val gL = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
    val bL = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)
    return 0.2126 * rL + 0.7152 * gL + 0.0722 * bL
}

private fun contrastRatio(c1: Int, c2: Int): Double {
    val l1 = calculateLuminance(c1)
    val l2 = calculateLuminance(c2)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}
