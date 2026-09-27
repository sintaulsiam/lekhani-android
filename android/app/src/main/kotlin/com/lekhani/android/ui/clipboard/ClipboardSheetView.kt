package com.lekhani.android.ui.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import com.lekhani.android.data.clipboard.ClipItem
import com.lekhani.android.data.clipboard.LekhaniClipboardStore
import com.lekhani.android.data.clipboard.RetentionPeriod
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry

/**
 * ClipboardSheetView
 * ══════════════════════════════════════════════════════════════════════════════
 * Smart 100% on-device clipboard manager panel.
 *
 * Features:
 *   ✅ Dynamic KeyboardTheme synchronization (respects active color scheme)
 *   ✅ Clip history list with safe crash-proof index-backed keys
 *   ✅ Open full Clipboard Manager & Editor directly from header
 *   ✅ Inline clip editor dialog right within the keyboard sheet
 *   ✅ Quick add new clip/note dialog
 *   ✅ Pinning/unpinning clips to prevent auto-clearing
 *   ✅ Sensitive content identification (OTP / Card badge)
 *   ✅ Tap to paste instantly into active InputConnection
 *   ✅ Individual clip deletion and bulk clear
 *   ✅ Prominent ABC button returning to keyboard
 */
@Composable
fun ClipboardSheetView(
    clipboardStore: LekhaniClipboardStore,
    onPaste: (String) -> Unit,
    onClose: () -> Unit,
    onOpenEditor: (() -> Unit)? = null,
    isEnglish: Boolean = false,
    theme: KeyboardTheme = ThemeRegistry.THEME_FLOW_TEAL,
    sheetHeight: androidx.compose.ui.unit.Dp = 260.dp,
    modifier: Modifier = Modifier,
) {
    val clips by clipboardStore.clips.collectAsState()

    val clipboardBg = Color(theme.backgroundColor)
    val headerBg = Color(theme.keyShiftColor)
    val cardBg = Color(theme.keyNormalColor)
    val cardPinnedBg = Color(theme.accentColor).copy(alpha = 0.15f)
    val borderColor = Color(theme.keyBorderColor)
    val primaryAccent = Color(theme.accentColor)
    val textPrimary = Color(theme.labelColor)
    val textSecondary = Color(theme.labelDimColor)
    val sensitiveBadge = Color(0xFFFF9500)

    var editingClip by remember { mutableStateOf<ClipItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val retentionLabel = remember(clipboardStore.retentionMinutes, isEnglish) {
        val period = RetentionPeriod.fromMinutes(clipboardStore.retentionMinutes)
        if (isEnglish) period.labelEnglish else period.labelBengali
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .background(clipboardBg)
            .semantics { contentDescription = if (isEnglish) "Clipboard Panel" else "ক্লিপবোর্ড প্যানেল" },
    ) {
        // ── Top Header Bar ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(headerBg)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Retention auto-clear indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardBg.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = retentionLabel,
                        fontSize = 9.sp,
                        color = textSecondary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Open full clipboard editor in app
                if (onOpenEditor != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryAccent.copy(alpha = 0.15f))
                            .clickable { onOpenEditor() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .semantics { contentDescription = if (isEnglish) "Open Clipboard Editor" else "ক্লিপবোর্ড এডিটর খুলুন" },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(if (isEnglish) "Editor" else "এডিটর", fontSize = 11.sp, color = primaryAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Add quick clip/note
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg)
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                        .semantics { contentDescription = if (isEnglish) "Add New Clip" else "নতুন ক্লিপ যোগ করুন" },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(if (isEnglish) "New" else "নতুন", fontSize = 11.sp, color = textSecondary)
                    }
                }

                if (clips.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardBg)
                            .clickable { clipboardStore.clearUnpinned() }
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                            .semantics { contentDescription = if (isEnglish) "Clear unpinned clips" else "পিন ছাড়া সব মুছুন" },
                    ) {
                        Text(if (isEnglish) "Clear" else "মুছুন", fontSize = 11.sp, color = textSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg)
                        .clickable { onClose() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .semantics { contentDescription = if (isEnglish) "Back to Keyboard" else "কিবোর্ডে ফিরে যান" },
                ) {
                    Text("⌨ ABC", fontSize = 12.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Clips List ───────────────────────────────────────────────────────
        if (clips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isEnglish) "No clips stored in clipboard.\nCopied text or added notes will appear here."
                           else "ক্লিপবোর্ডে কোনো লেখা সংরক্ষিত নেই।\nযেকোনো লেখা কপি করলে বা 'নতুন' চাপলে তা এখানে জমা হবে।",
                    fontSize = 13.sp,
                    color = textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(clips, key = { index, clip -> "${clip.id}_$index" }) { _, clip ->
                    ClipCard(
                        clip = clip,
                        onPaste = { onPaste(clip.text) },
                        onEdit = { editingClip = clip },
                        onTogglePin = { clipboardStore.togglePin(clip.id) },
                        onToggleSave = { clipboardStore.toggleSave(clip.id) },
                        onDelete = { clipboardStore.deleteClip(clip.id) },
                        isEnglish = isEnglish,
                        cardBg = cardBg,
                        cardPinnedBg = cardPinnedBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = primaryAccent,
                        sensitiveBadge = sensitiveBadge,
                    )
                }
            }
        }
    }

    // ── Inline Edit Clip Dialog ───────────────────────────────────────────────
    editingClip?.let { clip ->
        var editFieldText by remember(clip) { mutableStateOf(clip.text) }
        Dialog(onDismissRequest = { editingClip = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "Edit Clip" else "ক্লিপবোর্ড এডিট করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editFieldText,
                        onValueChange = { editFieldText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = borderColor
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingClip = null }) {
                            Text(if (isEnglish) "Cancel" else "বাতিল", color = textSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editFieldText.isNotBlank()) {
                                    clipboardStore.editClip(clip.id, editFieldText)
                                }
                                editingClip = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                        ) {
                            Text(if (isEnglish) "Save" else "সংরক্ষণ", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // ── Add New Clip Dialog ───────────────────────────────────────────────────
    if (showAddDialog) {
        var newText by remember { mutableStateOf("") }
        var saveToVault by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEnglish) "New Clipboard Note" else "নতুন ক্লিপবোর্ড নোট",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it },
                        placeholder = { Text(if (isEnglish) "Type or paste here..." else "এখানে লিখুন বা পেস্ট করুন...", color = textSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = borderColor
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { saveToVault = !saveToVault }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (saveToVault) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = if (saveToVault) Color(0xFFFFB703) else textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEnglish) "Save permanently to vault" else "স্থায়ী ভল্টে সংরক্ষণ করুন", fontSize = 12.sp, color = textPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text(if (isEnglish) "Cancel" else "বাতিল", color = textSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newText.isNotBlank()) {
                                    clipboardStore.addClip(newText, isSaved = saveToVault)
                                }
                                showAddDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                        ) {
                            Text(if (isEnglish) "Add" else "যোগ করুন", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipCard(
    clip: ClipItem,
    onPaste: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleSave: () -> Unit,
    onDelete: () -> Unit,
    isEnglish: Boolean = false,
    cardBg: Color,
    cardPinnedBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    sensitiveBadge: Color,
) {
    val containsLinks = remember(clip.text) { LekhaniClipboardStore.URL_REGEX.containsMatchIn(clip.text) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (clip.isPinned) cardPinnedBg else cardBg)
            .clickable { onPaste() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (clip.isSensitive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(sensitiveBadge.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(if (isEnglish) "Sensitive / OTP" else "সংবেদনশীল / OTP", fontSize = 9.sp, color = sensitiveBadge, fontWeight = FontWeight.Medium)
                    }
                }
                if (clip.isSaved) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFB703).copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(if (isEnglish) "💾 Vault" else "💾 ভল্ট", fontSize = 9.sp, color = Color(0xFFFFB703), fontWeight = FontWeight.Bold)
                    }
                }
                if (containsLinks) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00B4D8).copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(if (isEnglish) "🔗 Link" else "🔗 লিংক", fontSize = 9.sp, color = Color(0xFF0096C7), fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (clip.isSensitive || clip.isSaved || containsLinks) {
                Spacer(Modifier.height(2.dp))
            }

            Text(
                text = clip.text,
                fontSize = 14.sp,
                color = textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Edit Clip Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cardBg)
                    .clickable { onEdit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.EditNote,
                    contentDescription = if (isEnglish) "Edit Clip" else "এডিট করুন",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Pin Toggle Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cardBg)
                    .clickable { onTogglePin() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = if (isEnglish) (if (clip.isPinned) "Unpin" else "Pin") else (if (clip.isPinned) "আনপিন" else "পিন"),
                    tint = if (clip.isPinned) accentColor else textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Save / Vault Toggle Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cardBg)
                    .clickable { onToggleSave() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (clip.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isEnglish) (if (clip.isSaved) "Remove from Vault" else "Save to Vault") else (if (clip.isSaved) "ভল্ট থেকে মুছুন" else "ভল্টে সংরক্ষণ"),
                    tint = if (clip.isSaved) Color(0xFFFFB703) else textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Delete Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cardBg)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = if (isEnglish) "Delete" else "মুছুন",
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
