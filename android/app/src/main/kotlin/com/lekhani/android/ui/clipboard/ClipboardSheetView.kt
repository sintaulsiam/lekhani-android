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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
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
 * Features (ROADMAP.md Phase 6):
 *   ✅ Dynamic KeyboardTheme synchronization (respects active color scheme)
 *   ✅ Clip history list with timestamp labels
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
    theme: KeyboardTheme = ThemeRegistry.THEME_FLOW_TEAL,
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

    val retentionLabel = remember(clipboardStore.retentionMinutes) {
        RetentionPeriod.fromMinutes(clipboardStore.retentionMinutes).labelBengali
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(clipboardBg)
            .semantics { contentDescription = "ক্লিপবোর্ড প্যানেল" },
    ) {
        // ── Top Header Bar ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(headerBg)
                .padding(horizontal = 14.dp),
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ক্লিপবোর্ড",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Retention auto-clear indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardBg.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = retentionLabel,
                        fontSize = 10.sp,
                        color = textSecondary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (clips.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .clickable { clipboardStore.clearUnpinned() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .semantics { contentDescription = "পিন ছাড়া সব মুছুন" },
                    ) {
                        Text("মুছুন", fontSize = 12.sp, color = textSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .clickable { onClose() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .semantics { contentDescription = "কিবোর্ডে ফিরে যান" },
                ) {
                    Text("⌨ ABC", fontSize = 13.sp, color = textPrimary, fontWeight = FontWeight.Bold)
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
                    text = "ক্লিপবোর্ডে কোনো লেখা সংরক্ষিত নেই।\nযেকোনো লেখা কপি করলে তা এখানে দেখা যাবে।",
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
                items(clips, key = { it.id }) { clip ->
                    ClipCard(
                        clip = clip,
                        onPaste = { onPaste(clip.text) },
                        onTogglePin = { clipboardStore.togglePin(clip.id) },
                        onToggleSave = { clipboardStore.toggleSave(clip.id) },
                        onDelete = { clipboardStore.deleteClip(clip.id) },
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
}

@Composable
private fun ClipCard(
    clip: ClipItem,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleSave: () -> Unit,
    onDelete: () -> Unit,
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
                        Text("সংবেদনশীল / OTP", fontSize = 9.sp, color = sensitiveBadge, fontWeight = FontWeight.Medium)
                    }
                }
                if (clip.isSaved) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFB703).copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("💾 ভল্ট", fontSize = 9.sp, color = Color(0xFFFFB703), fontWeight = FontWeight.Bold)
                    }
                }
                if (containsLinks) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00B4D8).copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("🔗 লিংক", fontSize = 9.sp, color = Color(0xFF0096C7), fontWeight = FontWeight.Bold)
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                    contentDescription = if (clip.isPinned) "Unpin" else "Pin",
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
                    contentDescription = if (clip.isSaved) "Unsave" else "Save to Vault",
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
                    contentDescription = "Delete",
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
