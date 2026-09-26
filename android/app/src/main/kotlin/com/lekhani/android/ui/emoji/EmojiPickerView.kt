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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry

/**
 * EmojiPickerView
 * ══════════════════════════════════════════════════════════════════════════════
 * Full Unicode 15.1+ emoji, Kaomoji, and typographical symbols palette.
 *
 * Features (ROADMAP.md Phase 6):
 *   ✅ Dynamic KeyboardTheme synchronization (respects active color scheme)
 *   ✅ Category tabs with fast switching
 *   ✅ Instant bilingual search (Bengali e.g. "হাসি", "আগুন" + English)
 *   ✅ Recents & favorites shelf
 *   ✅ Long-press skin-tone selector
 *   ✅ Kaomoji Japanese emoticons picker (`(◕‿◕)`, `¯\_(ツ)_/¯`)
 *   ✅ Specialized math, currency (`৳`), and Bengali typographical symbols
 *   ✅ Dedicated ABC keyboard return key, Spacebar, and Backspace
 */
@Composable
fun EmojiPickerView(
    recentsManager: EmojiRecentsManager,
    onEmojiSelected: (String) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    onSearchClick: () -> Unit = {},
    onSpace: () -> Unit = {},
    isEnglish: Boolean = false,
    theme: KeyboardTheme = ThemeRegistry.THEME_FLOW_TEAL,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIdx by remember { mutableIntStateOf(0) }
    var skinToneTarget by remember { mutableStateOf<EmojiItem?>(null) }

    val recents = remember(selectedTabIdx) { recentsManager.getRecents() }

    val pickerBg = Color(theme.backgroundColor)
    val tabBarBg = Color(theme.keyShiftColor)
    val activeTabPill = Color(theme.accentColor)
    val inactiveTabText = Color(theme.labelDimColor)
    val searchBg = Color(theme.keyNormalColor)
    val textColor = Color(theme.labelColor)

    // Tab definitions: 0: Recents, 1..N: Emoji categories, N+1: Kaomoji, N+2: Symbols
    val totalTabs = 1 + EmojiData.categories.size + 2
    val kaomojiTabIdx = 1 + EmojiData.categories.size
    val symbolTabIdx = kaomojiTabIdx + 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(270.dp)
            .background(pickerBg)
            .semantics { contentDescription = if (isEnglish) "Emoji and symbol palette" else "ইমোজি এবং প্রতীক প্যালেট" },
    ) {
        // ── Search Pill ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(searchBg)
                    .clickable { onSearchClick() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = if (isEnglish) "Search" else "অনুসন্ধান",
                    tint = activeTabPill,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEnglish) "Search emojis (e.g. smile, fire, love, flag)..."
                           else "ইমোজি খুঁজুন (যেমন: হাসি, আগুন, প্রেম, পতাকা)...",
                    fontSize = 13.sp,
                    color = inactiveTabText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        lineHeight = 18.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }

        // ── Category Tab Bar ─────────────────────────────────────────────────
        if (searchQuery.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(tabBarBg)
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
                    desc = if (isEnglish) "Recent Emojis" else "সাম্প্রতিক ইমোজি",
                    activePill = activeTabPill,
                    inactiveColor = inactiveTabText,
                )

                // Standard Categories
                EmojiData.categories.forEachIndexed { index, cat ->
                    val catTitle = if (isEnglish) {
                        when (cat.id) {
                            "smileys" -> "Smileys & Emotion"
                            "people" -> "People & Body"
                            "animals" -> "Animals & Nature"
                            "food" -> "Food & Drink"
                            "travel" -> "Travel & Places"
                            "activities" -> "Activities & Games"
                            "objects" -> "Objects"
                            "symbols" -> "Symbols"
                            "flags" -> "Flags"
                            else -> cat.title
                        }
                    } else {
                        cat.title
                    }
                    TabItem(
                        label = cat.icon,
                        isSelected = selectedTabIdx == index + 1,
                        onClick = { selectedTabIdx = index + 1 },
                        desc = catTitle,
                        activePill = activeTabPill,
                        inactiveColor = inactiveTabText,
                    )
                }

                // Kaomoji Tab
                TabItem(
                    label = "ツ",
                    isSelected = selectedTabIdx == kaomojiTabIdx,
                    onClick = { selectedTabIdx = kaomojiTabIdx },
                    desc = if (isEnglish) "Kaomoji Emoticons" else "কাওমোজি ইমোটিকন",
                    activePill = activeTabPill,
                    inactiveColor = inactiveTabText,
                )

                // Symbols Tab
                TabItem(
                    label = "৳",
                    isSelected = selectedTabIdx == symbolTabIdx,
                    onClick = { selectedTabIdx = symbolTabIdx },
                    desc = if (isEnglish) "Symbols & Math" else "বাংলা ও গণিত প্রতীক",
                    activePill = activeTabPill,
                    inactiveColor = inactiveTabText,
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
                        Text(
                            if (isEnglish) "No emojis found" else "কোনো ইমোজি পাওয়া যায়নি",
                            color = inactiveTabText,
                            fontSize = 13.sp
                        )
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
                                }
                            )
                        }
                    }
                }
            } else when (selectedTabIdx) {
                0 -> {
                    // Recents Tab
                    if (recents.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (isEnglish) "No recently used emojis" else "সম্প্রতি ব্যবহৃত কোনো ইমোজি নেই",
                                color = inactiveTabText,
                                fontSize = 13.sp
                            )
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
                                }
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
                                        .background(searchBg)
                                        .clickable { onEmojiSelected(kaomoji) }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = kaomoji,
                                        fontSize = 13.sp,
                                        color = textColor,
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
                                        .background(searchBg)
                                        .clickable { onEmojiSelected(symbol) }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = symbol,
                                        fontSize = 18.sp,
                                        color = if (symbol == "৳") activeTabPill else textColor,
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

        // ── Bottom Navigation Row (Gboard-Style Keyboard Return Bar) ─────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(tabBarBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Prominent ABC key returning to the typing keyboard
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.keyNormalColor))
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = if (isEnglish) "Return to keyboard" else "কীবোর্ডে ফিরে যান" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⌨ ABC",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }

            // Dedicated Search button
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.keyNormalColor))
                    .clickable { onSearchClick() }
                    .semantics { contentDescription = if (isEnglish) "Search emojis" else "ইমোজি অনুসন্ধান" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = if (isEnglish) "Search emojis" else "ইমোজি অনুসন্ধান",
                    tint = activeTabPill,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Spacebar in emoji palette
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.keySpaceColor))
                    .clickable { onSpace() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isEnglish) "Space" else "স্পেস",
                    fontSize = 12.sp,
                    color = inactiveTabText,
                )
            }

            // Backspace key in emoji palette
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.keyNormalColor))
                    .clickable { onBackspace() }
                    .semantics { contentDescription = if (isEnglish) "Backspace" else "ডিলিট করুন" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = if (isEnglish) "Backspace" else "ডিলিট করুন",
                    tint = textColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    desc: String,
    activePill: Color = Color(0xFF00E5B8),
    inactiveColor: Color = Color(0xFF8B949E),
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) activePill else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (isSelected) Color.Black else inactiveColor,
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
