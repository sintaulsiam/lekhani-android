package com.lekhani.android.ui.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lekhani.android.data.clipboard.ClipItem
import com.lekhani.android.data.clipboard.ClipboardSnapshot
import com.lekhani.android.data.clipboard.ExtractedLink
import com.lekhani.android.data.clipboard.LekhaniClipboardStore
import com.lekhani.android.data.clipboard.RetentionPeriod
import com.lekhani.android.data.settings.KeyboardPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ClipboardManagerSheet
 * Material 3 Expressive UI for Lekhani's full in-app Clipboard Suite:
 *  - Configurable auto-clearing retention (default 1 hour, customizable to 6h, 24h, 7d, Never)
 *  - Pin items to top & Save items permanently to long-term vault
 *  - Take, preview, restore, or delete clipboard snapshots
 *  - Combine all items into consolidated text block with 1-click copy/share
 *  - Automatic URL parsing & extraction with domain badges and 1-click vault saving
 *  - Real-time search across all items
 */
@Composable
fun ClipboardManagerSheet(
    clipboardStore: LekhaniClipboardStore,
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clips by clipboardStore.clips.collectAsState()
    val snapshots by clipboardStore.snapshots.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog visibility states
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showCombinedTextDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var snapshotToPreview by remember { mutableStateOf<ClipboardSnapshot?>(null) }
    var clipToEdit by remember { mutableStateOf<ClipItem?>(null) }

    val currentRetention = RetentionPeriod.fromMinutes(prefs.clipboardRetentionMinutes)

    // Filtered lists
    val filteredClips = remember(clips, searchQuery) {
        if (searchQuery.isBlank()) {
            clips
        } else {
            clips.filter { it.text.contains(searchQuery, ignoreCase = true) }
        }
    }

    val pinnedClips = remember(filteredClips) { filteredClips.filter { it.isPinned } }
    val savedClips = remember(filteredClips) { filteredClips.filter { it.isSaved } }
    val extractedLinks = remember(clips) { clipboardStore.extractLinks(clips) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top App Bar ───────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isEnglish) "Clipboard Manager" else "ক্লিপবোর্ড ম্যানেজার",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (isEnglish) "${clips.size} items stored locally" else "${clips.size}টি আইটেম সংরক্ষিত",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Auto-Clear Retention Config Chip Button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showRetentionDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = if (isEnglish) currentRetention.labelEnglish else currentRetention.labelBengali,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // ── Quick Action Strip ────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Take Snapshot Action
                    item {
                        FilledTonalButton(
                            onClick = {
                                val snap = clipboardStore.takeSnapshot()
                                Toast.makeText(
                                    context,
                                    if (isEnglish) "Snapshot created: ${snap.title}"
                                    else "স্ন্যাপশট সংরক্ষিত হয়েছে (${snap.items.size}টি আইটেম)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Snapshot" else "স্ন্যাপশট নিন",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Combined Text Action
                    item {
                        FilledTonalButton(
                            onClick = { showCombinedTextDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Combine All" else "একত্রিত টেক্সট",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Links Tab Shortcut
                    item {
                        FilledTonalButton(
                            onClick = { selectedTab = 3 },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Links (${extractedLinks.size})" else "লিংক (${extractedLinks.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Clean Up Unpinned Action
                    item {
                        OutlinedButton(
                            onClick = { showClearConfirmDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "Clean Up" else "মুছুন",
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // ── Search Field ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        if (isEnglish) "Search copied texts..." else "ক্লিপবোর্ডের লেখা খুঁজুন...",
                        fontSize = 13.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            // ── Filter Tabs ───────────────────────────────────────────────────────
            val tabs = listOf(
                Pair(if (isEnglish) "All" else "সব", filteredClips.size),
                Pair(if (isEnglish) "Pinned" else "পিন করা", pinnedClips.size),
                Pair(if (isEnglish) "Saved Vault" else "সংরক্ষিত", savedClips.size),
                Pair(if (isEnglish) "Links" else "লিংক", extractedLinks.size),
                Pair(if (isEnglish) "Snapshots" else "স্ন্যাপশট", snapshots.size),
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                divider = { HorizontalDivider() }
            ) {
                tabs.forEachIndexed { index, pair ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = pair.first,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                )
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedTab == index)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = "${pair.second}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = if (selectedTab == index)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // ── Tab Contents ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> ClipsListView(
                        clips = filteredClips,
                        isEnglish = isEnglish,
                        emptyMessage = if (isEnglish) "No clipboard items yet" else "ক্লিপবোর্ডে কোনো লেখা নেই",
                        clipboardStore = clipboardStore,
                        onEdit = { clipToEdit = it },
                    )
                    1 -> ClipsListView(
                        clips = pinnedClips,
                        isEnglish = isEnglish,
                        emptyMessage = if (isEnglish) "No pinned items. Tap the pin icon on any item to keep it at top."
                        else "কোনো পিন করা আইটেম নেই। আইটেম পিন করতে পিন আইকনে চাপুন।",
                        clipboardStore = clipboardStore,
                        onEdit = { clipToEdit = it },
                    )
                    2 -> ClipsListView(
                        clips = savedClips,
                        isEnglish = isEnglish,
                        emptyMessage = if (isEnglish) "No long-term saved items in vault. Tap the bookmark icon to save permanently."
                        else "ভল্টে কোনো দীর্ঘমেয়াদী সংরক্ষিত লেখা নেই। বুকমার্ক আইকনে চেপে স্থায়ীভাবে সংরক্ষণ করুন।",
                        clipboardStore = clipboardStore,
                        onEdit = { clipToEdit = it },
                    )
                    3 -> LinksListView(
                        links = extractedLinks,
                        isEnglish = isEnglish,
                        clipboardStore = clipboardStore,
                    )
                    4 -> SnapshotsListView(
                        snapshots = snapshots,
                        isEnglish = isEnglish,
                        clipboardStore = clipboardStore,
                        onPreviewSnapshot = { snapshotToPreview = it },
                    )
                }
            }
        }
    }

    // ── Retention Period Configuration Dialog ─────────────────────────────────
    if (showRetentionDialog) {
        RetentionPeriodDialog(
            currentPeriod = currentRetention,
            isEnglish = isEnglish,
            onSelectPeriod = { period ->
                prefs.clipboardRetentionMinutes = period.minutes
                clipboardStore.retentionMinutes = period.minutes
                showRetentionDialog = false
                Toast.makeText(
                    context,
                    if (isEnglish) "Auto-clear updated: ${period.labelEnglish}"
                    else "স্বয়ংক্রিয়ভাবে মোছার সময় হালনাগাদ: ${period.labelBengali}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showRetentionDialog = false },
        )
    }

    // ── Edit Clip Dialog ──────────────────────────────────────────────────────
    clipToEdit?.let { clip ->
        var editFieldText by remember(clip) { mutableStateOf(clip.text) }
        AlertDialog(
            onDismissRequest = { clipToEdit = null },
            title = { Text(if (isEnglish) "Edit Clipboard Item" else "ক্লিপবোর্ড লেখা এডিট করুন") },
            text = {
                OutlinedTextField(
                    value = editFieldText,
                    onValueChange = { editFieldText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    label = { Text(if (isEnglish) "Content" else "লেখা") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editFieldText.isNotBlank()) {
                        clipboardStore.editClip(clip.id, editFieldText)
                        Toast.makeText(
                            context,
                            if (isEnglish) "Clip updated" else "ক্লিপ হালনাগাদ করা হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    clipToEdit = null
                }) {
                    Text(if (isEnglish) "Save" else "সংরক্ষণ")
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToEdit = null }) {
                    Text(if (isEnglish) "Cancel" else "বাতিল")
                }
            }
        )
    }

    // ── Combined Text Dialog ──────────────────────────────────────────────────
    if (showCombinedTextDialog) {
        val targetClips = when (selectedTab) {
            1 -> pinnedClips
            2 -> savedClips
            else -> filteredClips
        }
        val combined = remember(targetClips) { clipboardStore.getCombinedText(targetClips) }

        CombinedTextDialog(
            text = combined,
            itemCount = targetClips.size,
            isEnglish = isEnglish,
            onDismiss = { showCombinedTextDialog = false },
        )
    }

    // ── Snapshot Preview / Restore Dialog ─────────────────────────────────────
    snapshotToPreview?.let { snapshot ->
        SnapshotDetailDialog(
            snapshot = snapshot,
            isEnglish = isEnglish,
            onRestore = { replace ->
                clipboardStore.restoreSnapshot(snapshot.id, replace = replace)
                snapshotToPreview = null
                Toast.makeText(
                    context,
                    if (isEnglish) "Snapshot restored (${snapshot.items.size} items)"
                    else "স্ন্যাপশট পুনরুদ্ধার করা হয়েছে",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { snapshotToPreview = null },
        )
    }

    // ── Clean Up Confirmation Dialog ──────────────────────────────────────────
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(if (isEnglish) "Clear Clipboard?" else "ক্লিপবোর্ড পরিষ্কার করবেন?")
            },
            text = {
                Text(
                    if (isEnglish) "This will remove all unpinned and unsaved clips. Pinned and saved vault items will be kept safe."
                    else "পিন ছাড়া ও সাধারণ সব ক্লিপ মুছে যাবে। পিন করা এবং ভল্টে সংরক্ষিত লেখা সম্পূর্ণ নিরাপদ থাকবে।"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardStore.clearUnpinned()
                        showClearConfirmDialog = false
                        Toast.makeText(
                            context,
                            if (isEnglish) "Unpinned items cleared" else "অপ্রয়োজনীয় ক্লিপ মুছে ফেলা হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(if (isEnglish) "Clear" else "মুছুন", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "বাতিল")
                }
            }
        )
    }
}

// Tab Content: Clips List

@Composable
private fun ClipsListView(
    clips: List<ClipItem>,
    isEnglish: Boolean,
    emptyMessage: String,
    clipboardStore: LekhaniClipboardStore,
    onEdit: (ClipItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    if (clips.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(clips, key = { index, clip -> "${clip.id}_$index" }) { _, clip ->
                ClipItemCard(
                    clip = clip,
                    isEnglish = isEnglish,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Lekhani Clip", clip.text))
                        Toast.makeText(
                            context,
                            if (isEnglish) "Copied to clipboard" else "ক্লিপবোর্ডে কপি করা হয়েছে",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onEdit = { onEdit(clip) },
                    onTogglePin = { clipboardStore.togglePin(clip.id) },
                    onToggleSave = { clipboardStore.toggleSave(clip.id) },
                    onDelete = { clipboardStore.deleteClip(clip.id) },
                )
            }
        }
    }
}

@Composable
private fun ClipItemCard(
    clip: ClipItem,
    isEnglish: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val containsLinks = remember(clip.text) { LekhaniClipboardStore.URL_REGEX.containsMatchIn(clip.text) }

    val formattedDate = remember(clip.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(clip.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (clip.isPinned) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else if (clip.isSaved) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (clip.isPinned) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isEnglish) "Pinned" else "পিন করা",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (clip.isSaved) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isEnglish) "Saved Vault" else "সংরক্ষিত ভল্ট",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }

                    if (clip.isSensitive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF9500).copy(alpha = 0.2f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9500),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isEnglish) "Sensitive / OTP" else "সংবেদনশীল",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9500),
                                )
                            }
                        }
                    }

                    if (containsLinks) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00B4D8).copy(alpha = 0.2f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF0096C7),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isEnglish) "Link" else "লিংক",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0096C7),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clip Text Content
            Text(
                text = clip.text,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                // Copy Button
                IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Edit Button
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Pin Button
                IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = if (clip.isPinned) "Unpin" else "Pin",
                        tint = if (clip.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Save Vault Button
                IconButton(onClick = onToggleSave, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (clip.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (clip.isSaved) "Unsave" else "Save to Vault",
                        tint = if (clip.isSaved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Delete Button
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// Tab Content: Links List

@Composable
private fun LinksListView(
    links: List<ExtractedLink>,
    isEnglish: Boolean,
    clipboardStore: LekhaniClipboardStore,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        // Summary Header Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = if (isEnglish) "${links.size} Links Parsed" else "${links.size}টি লিংক পাওয়া গেছে",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = if (isEnglish) "Extracted from all clipboard history" else "ক্লিপবোর্ডের সব লেখা থেকে স্বয়ংক্রিয়ভাবে আলাদা করা",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (links.isNotEmpty()) {
                    Button(
                        onClick = {
                            clipboardStore.saveExtractedLinks(links)
                            Toast.makeText(
                                context,
                                if (isEnglish) "All ${links.size} links saved to vault!"
                                else "সবগুলো লিংক ভল্টে সংরক্ষিত হয়েছে!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(if (isEnglish) "Save All to Vault" else "সব ভল্টে রাখুন", fontSize = 12.sp)
                    }
                }
            }
        }

        if (links.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = if (isEnglish) "No web links found in your clipboard history"
                        else "ক্লিপবোর্ডের কোনো লেখায় ওয়েব লিংক বা URL পাওয়া যায়নি।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(links, key = { index, link -> "${link.url}_$index" }) { _, link ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Domain Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = link.domain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Full URL
                            Text(
                                text = link.url,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // Source preview
                            if (link.sourceClipText.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isEnglish) "Source: \"${link.sourceClipText.take(60)}...\""
                                    else "উৎস: \"${link.sourceClipText.take(60)}...\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Open in Browser
                                IconButton(
                                    onClick = {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            context.startActivity(intent)
                                        }.onFailure {
                                            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                // Copy Link
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Link", link.url))
                                        Toast.makeText(
                                            context,
                                            if (isEnglish) "Link copied" else "লিংক কপি করা হয়েছে",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                // Save Link to Vault
                                IconButton(
                                    onClick = {
                                        clipboardStore.addClip(link.url, isSaved = true)
                                        Toast.makeText(
                                            context,
                                            if (isEnglish) "Link saved to vault" else "লিংক ভল্টে সংরক্ষিত হয়েছে",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bookmark,
                                        contentDescription = "Save",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tab Content: Snapshots List

@Composable
private fun SnapshotsListView(
    snapshots: List<ClipboardSnapshot>,
    isEnglish: Boolean,
    clipboardStore: LekhaniClipboardStore,
    onPreviewSnapshot: (ClipboardSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        // Summary Header Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = if (isEnglish) "Clipboard Snapshots (${snapshots.size})" else "ক্লিপবোর্ড স্ন্যাপশট (${snapshots.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = if (isEnglish) "Time-frozen backups of your clipboard" else "যেকোনো সময়ের ক্লিপবোর্ড অবস্থার স্থায়ী ব্যাকআপ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = {
                        val snap = clipboardStore.takeSnapshot()
                        Toast.makeText(
                            context,
                            if (isEnglish) "New snapshot created!" else "নতুন স্ন্যাপশট সংরক্ষিত!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isEnglish) "Take Now" else "এখনই নিন", fontSize = 12.sp)
                }
            }
        }

        if (snapshots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = if (isEnglish) "No snapshots saved yet.\nTap 'Take Now' to capture an immutable backup of all your current clipboard items."
                        else "কোনো স্ন্যাপশট নেওয়া হয়নি।\n'এখনই নিন' বোতাম চেপে বর্তমান সব ক্লিপের স্থায়ী ব্যাকআপ তৈরি করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(snapshots, key = { index, snapshot -> "${snapshot.id}_$index" }) { _, snapshot ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = snapshot.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    fontSize = 14.sp,
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "${snapshot.items.size} clips",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }

                            // Preview items snippet
                            Spacer(Modifier.height(6.dp))
                            val previewSnippet = snapshot.items.take(2).joinToString(" • ") { it.text.take(30) }
                            if (previewSnippet.isNotBlank()) {
                                Text(
                                    text = previewSnippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onPreviewSnapshot(snapshot) }) {
                                    Text(if (isEnglish) "View & Restore" else "দেখুন ও পুনরুদ্ধার")
                                }
                                IconButton(
                                    onClick = {
                                        clipboardStore.deleteSnapshot(snapshot.id)
                                        Toast.makeText(
                                            context,
                                            if (isEnglish) "Snapshot deleted" else "স্ন্যাপশট মুছে ফেলা হয়েছে",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialogs

@Composable
private fun RetentionPeriodDialog(
    currentPeriod: RetentionPeriod,
    isEnglish: Boolean,
    onSelectPeriod: (RetentionPeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEnglish) "Clipboard Auto-Clear" else "স্বয়ংক্রিয়ভাবে মুছে ফেলার সময়সীমা")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isEnglish)
                        "Select how long unpinned clipboard items are kept before being cleared automatically:"
                    else
                        "অপ্রয়োজনীয় ক্লিপবোর্ড আইটেম কতক্ষণ পর স্বয়ংক্রিয়ভাবে মুছে যাবে তা নির্ধারণ করুন:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                RetentionPeriod.entries.forEach { period ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectPeriod(period) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = period == currentPeriod,
                            onClick = { onSelectPeriod(period) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isEnglish) period.labelEnglish else period.labelBengali,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (period == currentPeriod) FontWeight.Bold else FontWeight.Normal
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = if (isEnglish) "Pinned items and Saved vault items will never be auto-cleared."
                        else "পিন করা এবং ভল্টে সংরক্ষিত লেখা কখনো স্বয়ংক্রিয়ভাবে মুছে যাবে না।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Close" else "বন্ধ করুন")
            }
        }
    )
}

@Composable
private fun CombinedTextDialog(
    text: String,
    itemCount: Int,
    isEnglish: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val wordsCount = remember(text) { text.split("\\s+".toRegex()).count { it.isNotBlank() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(if (isEnglish) "Combined Clipboard Text" else "একত্রিত ক্লিপবোর্ড টেক্সট")
                Text(
                    text = if (isEnglish) "$itemCount items • $wordsCount words • ${text.length} chars"
                    else "$itemCount টি আইটেম • $wordsCount টি শব্দ • ${text.length} টি অক্ষর",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                ) {
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = if (text.isBlank()) (if (isEnglish) "(Empty)" else "(খালি)") else text,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Combined Lekhani Clips", text))
                    Toast.makeText(
                        context,
                        if (isEnglish) "Combined text copied!" else "একত্রিত টেক্সট কপি করা হয়েছে!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isEnglish) "Copy All" else "সব কপি করুন")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, text)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(shareIntent)
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isEnglish) "Share" else "শেয়ার")
                }
                TextButton(onClick = onDismiss) {
                    Text(if (isEnglish) "Close" else "বন্ধ")
                }
            }
        }
    )
}

@Composable
private fun SnapshotDetailDialog(
    snapshot: ClipboardSnapshot,
    isEnglish: Boolean,
    onRestore: (replace: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showRestoreOptions by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(snapshot.title)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEnglish) "${snapshot.items.size} clips saved in this snapshot:"
                    else "এই স্ন্যাপশটে ${snapshot.items.size}টি আইটেম সংরক্ষিত আছে:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(snapshot.items, key = { index, item -> "${item.id}_$index" }) { _, item ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = item.text,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRestore(false) },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(imageVector = Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isEnglish) "Restore Clips" else "পুনরুদ্ধার করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Close" else "বন্ধ")
            }
        }
    )
}
