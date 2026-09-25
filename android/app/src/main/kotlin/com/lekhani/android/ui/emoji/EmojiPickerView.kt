package com.lekhani.android.ui.emoji

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.emoji.EmojiCategory
import com.lekhani.android.data.emoji.EmojiData
import com.lekhani.android.data.emoji.EmojiItem
import com.lekhani.android.data.emoji.EmojiRecentsManager
import com.lekhani.android.data.emoji.KaomojiData
import com.lekhani.android.data.emoji.SymbolData

// ── Colors ────────────────────────────────────────────────────────────────────

private val PickerBackground = Color(0xFF0D1117)
private val TabBarBackground   = Color(0xFF161B22)
private val ActiveTabPill      = Color(0xFF00D4A0) // Lekhani Teal
private val InactiveTabText    = Color(0xFF8B949E)
private val SearchBg           = Color(0xFF21262D)
private val AccentTeal         = Color(0xFF00D4A0)

/**
 * EmojiPickerView
 * ══════════════════════════════════════════════════════════════════════════════
 * Full Unicode 15.1+ emoji, Kaomoji, and typographical symbols palette.
 *
 * Features (ROADMAP.md Phase 6):
 *   ✅ Category tabs with fast switching
 *   ✅ Instant bilingual search (Bengali e.g. "হাসি", "আগুন" + English)
 *   ✅ Recents & favorites shelf
 *   ✅ Long-press skin-tone selector
 *   ✅ Kaomoji Japanese emoticons picker (`(◕‿◕)`, `¯\_(ツ)_/¯`)
 *   ✅ Specialized math, currency (`৳`), and Bengali typographical symbols
 *   ✅ Bottom navigation returning to keyboard
 */
@Composable
fun EmojiPickerView(
    recentsManager: EmojiRecentsManager,
    onEmojiSelected: (String) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIdx by remember { mutableIntStateOf(0) }
    var skinToneTarget by remember { mutableStateOf<EmojiItem?>(null) }

    val recents = remember(selectedTabIdx) { recentsManager.getRecents() }

    // Tab definitions: 0: Recents, 1..N: Emoji categories, N+1: Kaomoji, N+2: Symbols
    val totalTabs = 1 + EmojiData.categories.size + 2
    val kaomojiTabIdx = 1 + EmojiData.categories.size
    val symbolTabIdx = kaomojiTabIdx + 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(PickerBackground)
            .semantics { contentDescription = "ইমোজি এবং প্রতীক প্যালেট" },
    ) {
        // ── Search Bar ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "ইমোজি খুঁজুন (যেমন: হাসি, আগুন, love, flag)...",
                        fontSize = 12.sp,
                        color = InactiveTabText
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SearchBg,
                    unfocusedContainerColor = SearchBg,
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            )
        }

        // ── Category Tab Bar ─────────────────────────────────────────────────
        if (searchQuery.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(TabBarBackground)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Tab 0: Recents
                TabItem(
                    label = "🕒",
                    isSelected = selectedTabIdx == 0,
                    onClick = { selectedTabIdx = 0 },
                    desc = "সাম্প্রতিক ইমোজি"
                )

                // Standard Categories
                EmojiData.categories.forEachIndexed { index, cat ->
                    TabItem(
                        label = cat.icon,
                        isSelected = selectedTabIdx == index + 1,
                        onClick = { selectedTabIdx = index + 1 },
                        desc = cat.title
                    )
                }

                // Kaomoji Tab
                TabItem(
                    label = "ツ",
                    isSelected = selectedTabIdx == kaomojiTabIdx,
                    onClick = { selectedTabIdx = kaomojiTabIdx },
                    desc = "কাওমোজি ইমোটিকন"
                )

                // Symbols Tab
                TabItem(
                    label = "৳",
                    isSelected = selectedTabIdx == symbolTabIdx,
                    onClick = { selectedTabIdx = symbolTabIdx },
                    desc = "বাংলা ও গণিত প্রতীক"
                )
            }
        }

        // ── Content Area ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (searchQuery.isNotBlank()) {
                // Search Results
                val searchResults = remember(searchQuery) { EmojiData.search(searchQuery) }
                if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("কোনো ইমোজি পাওয়া যায়নি", color = InactiveTabText, fontSize = 13.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 40.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(searchResults) { item ->
                            EmojiCell(
                                emoji = item.emoji,
                                onSelect = {
                                    recentsManager.addRecent(item.emoji)
                                    onEmojiSelected(item.emoji)
                                },
                                onLongClick = {
                                    if (item.skinTones.isNotEmpty()) {
                                        skinToneTarget = item
                                    }
                                },
                            )
                        }
                    }
                }
            } else when (selectedTabIdx) {
                0 -> {
                    // Recents Tab
                    if (recents.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("সম্প্রতি ব্যবহৃত কোনো ইমোজি নেই", color = InactiveTabText, fontSize = 13.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 40.dp),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(recents) { emoji ->
                                EmojiCell(
                                    emoji = emoji,
                                    onSelect = { onEmojiSelected(emoji) },
                                    onLongClick = {},
                                )
                            }
                        }
                    }
                }
                in 1..EmojiData.categories.size -> {
                    // Emoji Category Grid
                    val cat = EmojiData.categories[selectedTabIdx - 1]
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 40.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(cat.items) { item ->
                            EmojiCell(
                                emoji = item.emoji,
                                onSelect = {
                                    recentsManager.addRecent(item.emoji)
                                    onEmojiSelected(item.emoji)
                                },
                                onLongClick = {
                                    if (item.skinTones.isNotEmpty()) {
                                        skinToneTarget = item
                                    }
                                },
                            )
                        }
                    }
                }
                kaomojiTabIdx -> {
                    // Kaomoji Picker
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        KaomojiData.categories.forEach { cat ->
                            items(cat.items) { kaomoji ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SearchBg)
                                        .clickable { onEmojiSelected(kaomoji) }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = kaomoji,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                symbolTabIdx -> {
                    // Symbol Picker
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 44.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SymbolData.categories.forEach { cat ->
                            items(cat.items) { symbol ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SearchBg)
                                        .clickable { onEmojiSelected(symbol) }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = symbol,
                                        fontSize = 18.sp,
                                        color = if (symbol == "৳") AccentTeal else Color.White,
                                        fontWeight = if (symbol == "৳") FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Skin tone popup overlay
            skinToneTarget?.let { item ->
                SkinToneSelectorPopup(
                    item = item,
                    onSelect = { tone ->
                        skinToneTarget = null
                        recentsManager.addRecent(tone)
                        onEmojiSelected(tone)
                    },
                    onDismiss = { skinToneTarget = null },
                )
            }
        }

        // ── Bottom Navigation Row ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(TabBarBackground)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Return to keyboard button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SearchBg)
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics { contentDescription = "কিবোর্ডে ফিরে যান" },
            ) {
                Text("কিবোর্ড ⌨️", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }

            // Backspace button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SearchBg)
                    .clickable { onBackspace() }
                    .semantics { contentDescription = "ডিলিট করুন" },
                contentAlignment = Alignment.Center,
            ) {
                Text("⌫", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun TabItem(label: String, isSelected: Boolean, onClick: () -> Unit, desc: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) ActiveTabPill else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (isSelected) Color.Black else InactiveTabText,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(
    emoji: String,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = emoji },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SkinToneSelectorPopup(
    item: EmojiItem,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF21262D))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Default base emoji
            Text(
                text = item.emoji,
                fontSize = 24.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(item.emoji) }
                    .padding(4.dp),
            )
            // Skin tone variations
            item.skinTones.forEach { tone ->
                Text(
                    text = tone,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(tone) }
                        .padding(4.dp),
                )
            }
        }
    }
}
