package com.lekhani.android.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.data.settings.KeyboardPreferences.ToolbarTool
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.ui.theme.iconVector

/**
 * ExtraToolsSheetView
 * ══════════════════════════════════════════════════════════════════════════════
 * Interactive Tool Vault and Toolbar Customization Drawer.
 *
 * Features:
 *   ✅ Uncluttered active toolbar with primary shortcuts (Settings, Themes, Clipboard, Emoji)
 *   ✅ Secondary tools stored safely in the Tool Vault (Voice, Height Resize, Editor, One-Handed, Floating, Split)
 *   ✅ In-drawer customization mode to bring tools from the Vault to the Toolbar
 *   ✅ Move tools back to the Vault with one tap
 *   ✅ Real-time reordering of tools in both Toolbar and Vault
 *   ✅ One-tap "Reset to Defaults" button
 *   ✅ Fully bilingual (Bengali & English) and themed to match active keyboard skin
 */
@Composable
fun ExtraToolsSheetView(
    theme: KeyboardTheme,
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onToolSelected: (ToolbarTool) -> Unit,
    onToolsUpdated: (List<ToolbarTool>) -> Unit,
    onClose: () -> Unit,
) {
    val bgColor = Color(theme.backgroundColor)
    val cardBg = Color(theme.keyNormalColor)
    val accentColor = Color(theme.accentColor)
    val textColor = Color(theme.labelColor)
    val subTextColor = textColor.copy(alpha = 0.70f)
    val borderColor = textColor.copy(alpha = 0.12f)

    var isRearrangeMode by remember { mutableStateOf(false) }
    var activeTools by remember { mutableStateOf(prefs.getActiveToolbarTools()) }
    var vaultTools by remember { mutableStateOf(prefs.getVaultTools()) }

    fun syncTools(newActive: List<ToolbarTool>, newVault: List<ToolbarTool>) {
        activeTools = newActive
        vaultTools = newVault
        prefs.setToolbarToolsList(newActive)
        prefs.setVaultToolsList(newVault)
        onToolsUpdated(newActive)
    }

    fun moveActiveTool(fromIdx: Int, toIdx: Int) {
        if (fromIdx in activeTools.indices && toIdx in activeTools.indices) {
            val list = activeTools.toMutableList()
            val item = list.removeAt(fromIdx)
            list.add(toIdx, item)
            syncTools(list, vaultTools)
        }
    }

    fun moveVaultTool(fromIdx: Int, toIdx: Int) {
        if (fromIdx in vaultTools.indices && toIdx in vaultTools.indices) {
            val list = vaultTools.toMutableList()
            val item = list.removeAt(fromIdx)
            list.add(toIdx, item)
            syncTools(activeTools, list)
        }
    }

    fun sendToVault(tool: ToolbarTool) {
        if (activeTools.size <= 1) return // Keep at least 1 tool on toolbar
        val newActive = activeTools.filter { it != tool }
        val newVault = (listOf(tool) + vaultTools).distinct()
        syncTools(newActive, newVault)
    }

    fun bringToToolbar(tool: ToolbarTool) {
        val newVault = vaultTools.filter { it != tool }
        val newActive = (activeTools + tool).distinct()
        syncTools(newActive, newVault)
    }

    fun resetToDefaults() {
        prefs.resetToolsToDefault()
        syncTools(prefs.getActiveToolbarTools(), prefs.getVaultTools())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // ── Top Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRearrangeMode) Icons.Filled.Tune else Icons.Filled.GridView,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isRearrangeMode) {
                            if (isEnglish) "Rearrange Toolbar & Vault" else "টুলবার ও ভল্ট সাজান"
                        } else {
                            if (isEnglish) "Tool Vault" else "টুল ভল্ট"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = if (isRearrangeMode) {
                            if (isEnglish) "Move & organize keyboard tools" else "টুলবার ও ভল্টের মধ্যে স্থানান্তর করুন"
                        } else {
                            if (isEnglish) "Tap tool to open • Customize below" else "চালু করতে যেকোনো টুলে ট্যাপ করুন"
                        },
                        fontSize = 11.sp,
                        color = subTextColor
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isRearrangeMode) {
                    // Reset Button
                    IconButton(
                        onClick = { resetToDefaults() },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = if (isEnglish) "Reset Defaults" else "ডিফল্ট রিসেট",
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Done Button
                    Button(
                        onClick = { isRearrangeMode = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Done" else "সম্পন্ন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Enter Customize Mode Button
                    OutlinedButton(
                        onClick = { isRearrangeMode = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = accentColor
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEnglish) "Customize" else "সাজান",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = if (isEnglish) "Close tools" else "টুলস বন্ধ করুন",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── Main Body ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isRearrangeMode) {
                // ── Normal Mode: Active Toolbar Shortcuts ────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEnglish) "📌 Toolbar Shortcuts" else "📌 সক্রিয় টুলবার শর্টকাট",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        Text(
                            text = if (isEnglish) "${activeTools.size} pinned" else "${activeTools.size}টি পিন করা",
                            fontSize = 11.sp,
                            color = subTextColor
                        )
                    }

                    // Grid or Row of Active Tools
                    activeTools.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (tool in pair) {
                                ToolLaunchCard(
                                    tool = tool,
                                    isToolbarTool = true,
                                    modifier = Modifier.weight(1f),
                                    cardBg = cardBg,
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    subTextColor = subTextColor,
                                    borderColor = accentColor.copy(alpha = 0.25f),
                                    isEnglish = isEnglish,
                                    onClick = { onToolSelected(tool) }
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ── Normal Mode: Tool Vault (Remaining Tools) ────────────────
                if (vaultTools.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isEnglish) "🗄️ More Tools in Vault" else "🗄️ টুল ভল্ট (অতিরিক্ত টুলস)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Text(
                                text = if (isEnglish) "${vaultTools.size} in vault" else "${vaultTools.size}টি ভল্টে",
                                fontSize = 11.sp,
                                color = subTextColor
                            )
                        }

                        vaultTools.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (tool in pair) {
                                    ToolLaunchCard(
                                        tool = tool,
                                        isToolbarTool = false,
                                        modifier = Modifier.weight(1f),
                                        cardBg = cardBg,
                                        accentColor = accentColor,
                                        textColor = textColor,
                                        subTextColor = subTextColor,
                                        borderColor = borderColor,
                                        isEnglish = isEnglish,
                                        onClick = { onToolSelected(tool) }
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Bottom Callout to Customize
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg.copy(alpha = 0.6f))
                        .clickable { isRearrangeMode = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isEnglish) "Add, remove & rearrange toolbar tools" else "টুলবার ও ভল্টের টুল সাজান বা পরিবর্তন করুন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            } else {
                // ── Customize / Rearrange Mode ───────────────────────────────

                // Helpful Info Tip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isEnglish) {
                            "💡 Tap (+) to bring a tool to Toolbar, (-) to send to Vault. Use arrows (← → / ↑ ↓) to reorder."
                        } else {
                            "💡 টুলবারে আনতে (+) ও ভল্টে পাঠাতে (-) ট্যাপ করুন। অবস্থান বদলাতে তীর চিহ্ন ব্যবহার করুন।"
                        },
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.9f),
                        lineHeight = 15.sp
                    )
                }

                // ── Section 1: Active Toolbar Tools ──────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isEnglish) "📌 On Keyboard Toolbar (${activeTools.size})" else "📌 সক্রিয় কীবোর্ড টুলবার (${activeTools.size}টি)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )

                    activeTools.forEachIndexed { index, tool ->
                        RearrangeRow(
                            tool = tool,
                            isPinnedToToolbar = true,
                            canMoveUp = index > 0,
                            canMoveDown = index < activeTools.size - 1,
                            canRemove = activeTools.size > 1,
                            onMoveUp = { moveActiveTool(index, index - 1) },
                            onMoveDown = { moveActiveTool(index, index + 1) },
                            onToggleVault = { sendToVault(tool) },
                            cardBg = cardBg,
                            accentColor = accentColor,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            isEnglish = isEnglish
                        )
                    }
                }

                // ── Section 2: Stored in Vault ───────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isEnglish) "🗄️ In Tool Vault (${vaultTools.size})" else "🗄️ টুল ভল্টে সংরক্ষিত (${vaultTools.size}টি)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    if (vaultTools.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "Vault is empty — all tools are on toolbar" else "ভল্ট খালি — সকল টুল টুলবারে যুক্ত আছে",
                                fontSize = 12.sp,
                                color = subTextColor
                            )
                        }
                    } else {
                        vaultTools.forEachIndexed { index, tool ->
                            RearrangeRow(
                                tool = tool,
                                isPinnedToToolbar = false,
                                canMoveUp = index > 0,
                                canMoveDown = index < vaultTools.size - 1,
                                canRemove = true,
                                onMoveUp = { moveVaultTool(index, index - 1) },
                                onMoveDown = { moveVaultTool(index, index + 1) },
                                onToggleVault = { bringToToolbar(tool) },
                                cardBg = cardBg,
                                accentColor = accentColor,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                isEnglish = isEnglish
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Supporting Composables ───────────────────────────────────────────────────

@Composable
private fun ToolLaunchCard(
    tool: ToolbarTool,
    isToolbarTool: Boolean,
    modifier: Modifier = Modifier,
    cardBg: Color,
    accentColor: Color,
    textColor: Color,
    subTextColor: Color,
    borderColor: Color,
    isEnglish: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isToolbarTool) accentColor.copy(alpha = 0.22f) else textColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.iconVector,
                    contentDescription = null,
                    tint = if (isToolbarTool) accentColor else textColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnglish) tool.titleEnglish else tool.titleBengali,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tool.description(isEnglish),
                    fontSize = 10.sp,
                    color = subTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RearrangeRow(
    tool: ToolbarTool,
    isPinnedToToolbar: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVault: () -> Unit,
    cardBg: Color,
    accentColor: Color,
    textColor: Color,
    subTextColor: Color,
    isEnglish: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Icon + Label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isPinnedToToolbar) accentColor.copy(alpha = 0.20f) else textColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.iconVector,
                    contentDescription = null,
                    tint = if (isPinnedToToolbar) accentColor else textColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = if (isEnglish) tool.titleEnglish else tool.titleBengali,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tool.description(isEnglish),
                    fontSize = 9.sp,
                    color = subTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Reordering and Add/Remove Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Move Up / Left
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isPinnedToToolbar) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Move earlier",
                    tint = if (canMoveUp) textColor else textColor.copy(alpha = 0.25f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Move Down / Right
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isPinnedToToolbar) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Move later",
                    tint = if (canMoveDown) textColor else textColor.copy(alpha = 0.25f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Action: Bring to Toolbar (+) or Send to Vault (-)
            if (isPinnedToToolbar) {
                IconButton(
                    onClick = onToggleVault,
                    enabled = canRemove,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (canRemove) textColor.copy(alpha = 0.12f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = if (isEnglish) "Move to Vault" else "ভল্টে পাঠান",
                        tint = if (canRemove) textColor else textColor.copy(alpha = 0.25f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onToggleVault,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.25f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = if (isEnglish) "Bring to Toolbar" else "টুলবারে আনুন",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun ToolbarTool.description(isEnglish: Boolean): String = when (this) {
    ToolbarTool.SETTINGS    -> if (isEnglish) "Preferences & about" else "কীবোর্ড সেটিংস"
    ToolbarTool.THEME       -> if (isEnglish) "Colors & styling" else "থিম ও রঙ"
    ToolbarTool.CLIPBOARD   -> if (isEnglish) "Copied snippets" else "ক্লিপবোর্ড হিস্ট্রি"
    ToolbarTool.EMOJI       -> if (isEnglish) "Emojis & kaomoji" else "ইমোজি ও প্রতীক"
    ToolbarTool.VOICE       -> if (isEnglish) "Speech-to-text" else "ভয়েস টাইপিং"
    ToolbarTool.RESIZE      -> if (isEnglish) "Scale keyboard height" else "উচ্চতা স্কেলিং"
    ToolbarTool.TEXT_EDITOR -> if (isEnglish) "Cursor & select" else "কার্সার মুভমেন্ট"
    ToolbarTool.ONE_HANDED  -> if (isEnglish) "One-hand dock" else "একহাতে টাইপিং"
    ToolbarTool.FLOATING    -> if (isEnglish) "Movable window" else "ভাসমান কীবোর্ড"
    ToolbarTool.SPLIT       -> if (isEnglish) "Thumb split mode" else "দ্বিখণ্ডিত লেআউট"
}
