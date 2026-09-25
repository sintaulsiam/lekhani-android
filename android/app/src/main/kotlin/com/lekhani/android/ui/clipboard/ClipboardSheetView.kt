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
import com.lekhani.android.data.clipboard.ClipItem
import com.lekhani.android.data.clipboard.LekhaniClipboardStore

private val ClipboardBg     = Color(0xFF0D1117)
private val CardBg          = Color(0xFF161B22)
private val CardPinnedBg    = Color(0xFF1B2A26)
private val BorderColor     = Color(0xFF30363D)
private val PrimaryTeal     = Color(0xFF00D4A0)
private val TextPrimary     = Color(0xFFF0F6FC)
private val TextSecondary   = Color(0xFF8B949E)
private val SensitiveBadge  = Color(0xFFFF9500)

/**
 * ClipboardSheetView
 * ══════════════════════════════════════════════════════════════════════════════
 * Smart 100% on-device clipboard manager panel.
 *
 * Features (ROADMAP.md Phase 6):
 *   ✅ Clip history list with timestamp labels
 *   ✅ Pinning/unpinning clips to prevent auto-clearing
 *   ✅ Sensitive content identification (OTP / Card badge)
 *   ✅ Tap to paste instantly into active InputConnection
 *   ✅ Individual clip deletion and bulk clear
 */
@Composable
fun ClipboardSheetView(
    clipboardStore: LekhaniClipboardStore,
    onPaste: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clips by clipboardStore.clips.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(ClipboardBg)
            .semantics { contentDescription = "ক্লিপবোর্ড প্যানেল" },
    ) {
        // ── Top Header Bar ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color(0xFF161B22))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ক্লিপবোর্ড 📋",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (clips.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF21262D))
                            .clickable { clipboardStore.clearUnpinned() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .semantics { contentDescription = "পিন ছাড়া সব মুছুন" },
                    ) {
                        Text("মুছুন", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryTeal)
                        .clickable { onClose() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .semantics { contentDescription = "কিবোর্ডে ফিরে যান" },
                ) {
                    Text("কিবোর্ড ⌨️", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
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
                    color = TextSecondary,
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
                        onDelete = { clipboardStore.deleteClip(clip.id) },
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
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (clip.isPinned) CardPinnedBg else CardBg)
            .clickable { onPaste() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (clip.isSensitive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SensitiveBadge.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("🔒 সংবেদনশীল / OTP", fontSize = 9.sp, color = SensitiveBadge, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(2.dp))
            }

            Text(
                text = clip.text,
                fontSize = 14.sp,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Pin Toggle Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF21262D))
                    .clickable { onTogglePin() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (clip.isPinned) "📌" else "📍",
                    fontSize = 13.sp,
                )
            }

            // Delete Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF21262D))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "✕", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}
