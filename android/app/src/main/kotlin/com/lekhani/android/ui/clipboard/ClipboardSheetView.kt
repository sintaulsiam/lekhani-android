package com.lekhani.android.ui.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.clipboard.ClipItem
import com.lekhani.android.data.clipboard.LekhaniClipboardStore
import com.lekhani.android.data.clipboard.RetentionPeriod
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry

/**
 * ClipboardSheetView
 * Modern, resilient 100% on-device clipboard panel.
 *
 * IME Stability Guarantee:
 *   ✅ ZERO `androidx.compose.ui.window.Dialog` instances:
 *      IME windows cannot own sub-panel dialogs without WindowManager dismissing the IME.
 *      All menus, overlays, and confirmations are rendered 100% inline inside the Compose hierarchy.
 *   ✅ Tap-to-paste primary focus with zero micro-button clutter.
 *   ✅ Quick pin/unpin toggle directly on the card with 36dp touch target.
 *   ✅ Manage mode (pencil toggle) for rapid bulk deletion and clearing.
 *   ✅ Safe clear confirmation overlay.
 *   ✅ "New / Editor" launches the full-screen Clipboard Studio Activity where the keyboard can type.
 */
@Composable
fun ClipboardSheetView(
    clipboardStore: LekhaniClipboardStore,
    onPaste: (String) -> Unit,
    onClose: () -> Unit,
    onOpenEditor: (() -> Unit)? = null,
    isEnglish: Boolean = false,
    theme: KeyboardTheme = ThemeRegistry.THEME_FLOW_TEAL,
    sheetHeight: Dp = 260.dp,
    modifier: Modifier = Modifier,
) {
    val clips by clipboardStore.clips.collectAsState()

    val clipboardBg = Color(theme.backgroundColor)
    val headerBg = Color(theme.keyShiftColor)
    val cardBg = Color(theme.keyNormalColor)
    val cardPinnedBg = Color(theme.accentColor).copy(alpha = 0.16f)
    val primaryAccent = Color(theme.accentColor)
    val textPrimary = Color(theme.labelColor)
    val textSecondary = Color(theme.labelDimColor)
    val sensitiveBadge = Color(0xFFFF9500)
    val dangerColor = Color(0xFFFF5252)

    var isManageMode by remember { mutableStateOf(false) }
    var actionClip by remember { mutableStateOf<ClipItem?>(null) }
    var showClearConfirmOverlay by remember { mutableStateOf(false) }

    val retentionLabel = remember(clipboardStore.retentionMinutes, isEnglish) {
        val period = RetentionPeriod.fromMinutes(clipboardStore.retentionMinutes)
        if (isEnglish) period.labelEnglish else period.labelBengali
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .background(clipboardBg)
            .semantics { contentDescription = if (isEnglish) "Clipboard Panel" else "ক্লিপবোর্ড প্যানেল" }
    ) {
        // ── Base Content Layer ────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(headerBg)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: Title + Retention Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "Clipboard" else "ক্লিপবোর্ড",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Retention auto-clear indicator pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardBg.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = retentionLabel,
                            fontSize = 10.sp,
                            color = textSecondary,
                        )
                    }
                }

                // Right: Actions Cluster
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isManageMode) {
                        // In Manage Mode: "Clear Unpinned" button
                        if (clips.any { !it.isPinned }) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(dangerColor.copy(alpha = 0.15f))
                                    .clickable { showClearConfirmOverlay = true }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = null,
                                        tint = dangerColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = if (isEnglish) "Clear" else "মুছুন",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = dangerColor
                                    )
                                }
                            }
                        }

                        // Exit Manage Mode ("Done")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(primaryAccent.copy(alpha = 0.2f))
                                .clickable { isManageMode = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = if (isEnglish) "Done" else "সম্পন্ন",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAccent
                                )
                            }
                        }
                    } else {
                        // Open in App's Clipboard Manager
                        if (onOpenEditor != null) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(cardBg)
                                    .clickable { onOpenEditor() }
                                    .semantics { contentDescription = if (isEnglish) "Open App's Clipboard" else "অ্যাপের ক্লিপবোর্ড খুলুন" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Manage Mode Toggle Button (Pencil Icon)
                        if (clips.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(cardBg)
                                    .clickable { isManageMode = true }
                                    .semantics { contentDescription = if (isEnglish) "Manage Clips" else "ক্লিপ পরিচালনা করুন" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Return to Keyboard (ABC)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(cardBg)
                                .clickable { onClose() }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                                .semantics { contentDescription = if (isEnglish) "Back to Keyboard" else "কিবোর্ডে ফিরে যান" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Keyboard,
                                    contentDescription = null,
                                    tint = textPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "ABC",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Main List / Empty State
            if (clips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isEnglish) "Clipboard is empty" else "ক্লিপবোর্ড ফাঁকা",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isEnglish) "Copied text will appear here"
                                   else "কপি করা টেক্সট এখানে দেখাবে",
                            fontSize = 11.sp,
                            color = textSecondary.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                        )
                        if (onOpenEditor != null) {
                            Spacer(Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(primaryAccent.copy(alpha = 0.15f))
                                    .clickable { onOpenEditor() }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .semantics { contentDescription = if (isEnglish) "Open App's Clipboard" else "অ্যাপের ক্লিপবোর্ড খুলুন" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isEnglish) "Open App's Clipboard" else "অ্যাপের ক্লিপবোর্ড খুলুন",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryAccent,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(clips, key = { index, clip -> "${clip.id}_$index" }) { _, clip ->
                        CleanClipCard(
                            clip = clip,
                            isManageMode = isManageMode,
                            onPaste = { onPaste(clip.text) },
                            onActionMenu = { actionClip = clip },
                            onTogglePin = { clipboardStore.togglePin(clip.id) },
                            onDelete = { clipboardStore.deleteClip(clip.id) },
                            isEnglish = isEnglish,
                            cardBg = cardBg,
                            cardPinnedBg = cardPinnedBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentColor = primaryAccent,
                            sensitiveBadge = sensitiveBadge,
                            dangerColor = dangerColor,
                        )
                    }

                    // Helpful interaction hint footer
                    if (!isManageMode) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = if (isEnglish) "Tap clip to paste • Long-press for options"
                                           else "পেস্ট করতে ট্যাপ করুন • অপশনের জন্য চেপে ধরুন",
                                    fontSize = 11.sp,
                                    color = textSecondary.copy(alpha = 0.6f)
                                )
                                if (onOpenEditor != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onOpenEditor() }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .semantics { contentDescription = if (isEnglish) "Open App's Clipboard" else "অ্যাপের ক্লিপবোর্ড খুলুন" },
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = primaryAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isEnglish) "Open full clipboard manager"
                                                   else "সম্পূর্ণ ক্লিপবোর্ড ম্যানেজার খুলুন",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = primaryAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── INLINE OVERLAY: Long-Press Contextual Menu (Never closes the IME!) ─
        // ── INLINE OVERLAY: Clip Options ─────────────────────────────────────
        actionClip?.let { clip ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { actionClip = null }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {} // Prevent dismiss when tapping card
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        // Header with Title & prominent Close 'X' Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEnglish) "Clip Options" else "ক্লিপ অপশন",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            IconButton(
                                onClick = { actionClip = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = if (isEnglish) "Close" else "বন্ধ করুন",
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(headerBg)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = clip.text,
                                fontSize = 12.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(6.dp))

                        // Action 1: Paste into active input
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onPaste(clip.text)
                                    actionClip = null
                                }
                                .padding(vertical = 7.dp, horizontal = 8.dp)
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (isEnglish) "Paste into field" else "ফিল্ডে পেস্ট করুন", fontSize = 13.sp, color = textPrimary, fontWeight = FontWeight.Medium)
                        }

                        // Action 2: Pin / Unpin
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    clipboardStore.togglePin(clip.id)
                                    actionClip = null
                                }
                                .padding(vertical = 7.dp, horizontal = 8.dp)
                        ) {
                            Icon(Icons.Filled.PushPin, contentDescription = null, tint = if (clip.isPinned) primaryAccent else textSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (clip.isPinned) (if (isEnglish) "Unpin clip" else "আনপিন করুন")
                                       else (if (isEnglish) "Pin to top (Keep forever)" else "উপরে পিন করুন (স্থায়ী)"),
                                fontSize = 13.sp,
                                color = textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Action 3: Delete Clip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    clipboardStore.deleteClip(clip.id)
                                    actionClip = null
                                }
                                .padding(vertical = 7.dp, horizontal = 8.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = dangerColor, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (isEnglish) "Delete clip" else "ক্লিপ মুছুন", fontSize = 13.sp, color = dangerColor, fontWeight = FontWeight.Medium)
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { actionClip = null }) {
                                Text(if (isEnglish) "Cancel" else "বাতিল", color = textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── INLINE OVERLAY: Clear Confirmation (Never closes the IME!) ────────
        if (showClearConfirmOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { showClearConfirmOverlay = false }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {}
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEnglish) "Clear unpinned clips?" else "পিন ছাড়া সব ক্লিপ মুছবেন?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            IconButton(
                                onClick = { showClearConfirmOverlay = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = if (isEnglish) "Close" else "বন্ধ করুন",
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isEnglish) "All recent clips will be removed. Your pinned clips will stay safe."
                                   else "সাম্প্রতিক সব ক্লিপ মুছে যাবে। তবে পিন করা ক্লিপগুলো সুরক্ষিত থাকবে।",
                            fontSize = 13.sp,
                            color = textSecondary
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showClearConfirmOverlay = false }) {
                                Text(if (isEnglish) "Cancel" else "বাতিল", color = textSecondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    clipboardStore.clearUnpinned()
                                    showClearConfirmOverlay = false
                                    isManageMode = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                            ) {
                                Text(if (isEnglish) "Clear" else "মুছুন", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * CleanClipCard
 * Elegant, thumb-friendly card designed for instant tap-to-paste without micro-button clutter.
 */
@Composable
private fun CleanClipCard(
    clip: ClipItem,
    isManageMode: Boolean,
    onPaste: () -> Unit,
    onActionMenu: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    isEnglish: Boolean = false,
    cardBg: Color,
    cardPinnedBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    sensitiveBadge: Color,
    dangerColor: Color,
) {
    val containsLinks = remember(clip.text) { LekhaniClipboardStore.URL_REGEX.containsMatchIn(clip.text) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (clip.isPinned) cardPinnedBg else cardBg)
            .pointerInput(clip.id) {
                detectTapGestures(
                    onTap = {
                        if (isManageMode) onActionMenu() else onPaste()
                    },
                    onLongPress = {
                        onActionMenu()
                    }
                )
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Badges row
            if (clip.isPinned || clip.isSensitive || containsLinks) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (clip.isPinned) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.22f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = if (isEnglish) "Pinned" else "পিন করা",
                                    fontSize = 10.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (clip.isSensitive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(sensitiveBadge.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = sensitiveBadge,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = if (isEnglish) "OTP / Sensitive" else "ওটিপি / সংবেদনশীল",
                                    fontSize = 10.sp,
                                    color = sensitiveBadge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (containsLinks) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF00B4D8).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF0096C7),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = if (isEnglish) "Link" else "লিংক",
                                    fontSize = 10.sp,
                                    color = Color(0xFF0096C7),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }

            Text(
                text = clip.text,
                fontSize = 14.sp,
                color = textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
        }

        Spacer(Modifier.width(10.dp))

        // Right side: Dedicated comfortable action button
        if (isManageMode) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Pin button in manage mode
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onTogglePin() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = if (clip.isPinned) "Unpin" else "Pin",
                        tint = if (clip.isPinned) accentColor else textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete button in manage mode
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete",
                        tint = dangerColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        } else {
            // Normal Mode: Single dedicated, clean Pin Toggle (36x36 dp comfortable touch target)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onTogglePin() }
                    .semantics { contentDescription = if (clip.isPinned) "Unpin" else "Pin" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = if (clip.isPinned) accentColor else textSecondary.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
