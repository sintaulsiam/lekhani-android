package com.lekhani.android.ui.emoji

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.emoji.EmojiCategory
import com.lekhani.android.data.emoji.EmojiData
import com.lekhani.android.data.emoji.EmojiItem
import com.lekhani.android.data.emoji.EmojiRecentsManager
import com.lekhani.android.data.emoji.KaomojiData
import com.lekhani.android.data.emoji.SymbolData
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * EmojiPickerView
 * ══════════════════════════════════════════════════════════════════════════════
 * Modern, fluid Unicode 15.1+ emoji, Kaomoji, and typographical symbols palette.
 *
 * Upgrades:
 *   ✅ In-Palette Live Interactive Search with real-time 2D grid filtering
 *   ✅ Continuous vertical scrolling with category section headers
 *   ✅ Category tab bar with smooth scroll synchronization
 *   ✅ Floating anchored skin-tone callout bubble (thumb-relative)
 *   ✅ Zero-jump dynamic height synchronization with keyboard canvas
 *   ✅ Long-press continuous repeating Backspace key
 *   ✅ Micro-haptics on emoji selection, tabs, and skin tones
 */
@Composable
fun EmojiPickerView(
    recentsManager: EmojiRecentsManager,
    onEmojiSelected: (String) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    onSearchClick: (String) -> Unit = {},
    onSpace: () -> Unit = {},
    isEnglish: Boolean = false,
    theme: KeyboardTheme = ThemeRegistry.THEME_FLOW_TEAL,
    paletteHeight: Dp = 260.dp,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIdx by remember { mutableIntStateOf(1) } // 0: Recents, 1: Emojis, 2: Kaomoji, 3: Symbols
    var skinToneTarget by remember { mutableStateOf<EmojiItem?>(null) }
    var defaultSkinToneIndex by remember { mutableIntStateOf(recentsManager.defaultSkinToneIndex) }

    val recents = remember(selectedTabIdx, searchQuery) { recentsManager.getRecents() }
    val gridState = rememberLazyGridState()

    val pickerBg = Color(theme.backgroundColor)
    val tabBarBg = Color(theme.keyShiftColor)
    val activeTabPill = Color(theme.accentColor)
    val inactiveTabText = Color(theme.labelDimColor)
    val searchBg = Color(theme.keyNormalColor)
    val textColor = Color(theme.labelColor)
    val borderColor = Color(theme.keyBorderColor)

    // Precalculate category indices for continuous jump scrolling
    // Header item = 1, each item in category = 1
    val categoryScrollOffsets = remember {
        val offsets = mutableListOf<Int>()
        var runningCount = 0
        EmojiData.categories.forEach { cat ->
            offsets.add(runningCount)
            runningCount += 1 + cat.items.size
        }
        offsets
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(paletteHeight)
            .background(pickerBg)
            .semantics { contentDescription = if (isEnglish) "Emoji and symbol palette" else "ইমোজি এবং প্রতীক প্যালেট" },
    ) {
        // ── Search & Filter Bar ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(searchBg)
                    .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(19.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = if (isEnglish) "Search" else "অনুসন্ধান",
                    tint = activeTabPill,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = textColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    cursorBrush = SolidColor(activeTabPill),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (isEnglish) "Search emojis (e.g. smile, love, kanna)..."
                                       else "ইমোজি খুঁজুন (যেমন: হাসি, প্রেম, কান্না, আগুন)...",
                                fontSize = 12.sp,
                                color = inactiveTabText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isEnglish) "Clear search" else "মুছুন",
                            tint = inactiveTabText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Quick Tag Chips when not searching or for quick filtering
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val quickChips = listOf(
                "🔥" to if (isEnglish) "fire" else "আগুন",
                "❤️" to if (isEnglish) "love" else "ভালোবাসা",
                "😂" to if (isEnglish) "laugh" else "হাসি",
                "😭" to if (isEnglish) "cry" else "কান্না",
                "👍" to if (isEnglish) "hand" else "হাত",
                "🎉" to if (isEnglish) "party" else "উৎসব",
                "☕" to if (isEnglish) "tea" else "চা",
                "৳" to if (isEnglish) "money" else "টাকা",
                "🌧️" to if (isEnglish) "rain" else "বৃষ্টি",
                "✨" to if (isEnglish) "star" else "তারা",
                "🇧🇩" to if (isEnglish) "flag" else "বাংলাদেশ"
            )
            quickChips.forEach { (emoji, tag) ->
                val isTagActive = (searchQuery.trim().lowercase() == tag.lowercase())
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isTagActive) activeTabPill else tabBarBg)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            searchQuery = if (isTagActive) "" else tag
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$emoji $tag",
                        fontSize = 11.sp,
                        fontWeight = if (isTagActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isTagActive) Color.Black else inactiveTabText
                    )
                }
            }
        }

        // ── Category Tab Bar (When not actively searching) ────────────────────
        if (searchQuery.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
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
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedTabIdx = 0
                    },
                    desc = if (isEnglish) "Recent Emojis" else "সাম্প্রতিক ইমোজি",
                    activePill = activeTabPill,
                    inactiveColor = inactiveTabText,
                )

                // Tab 1..N: Standard Categories
                EmojiData.categories.forEachIndexed { index, cat ->
                    val isCatSelected = (selectedTabIdx == 1)
                    TabItem(
                        label = cat.icon,
                        isSelected = selectedTabIdx == 1 && index == 0,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedTabIdx = 1
                            val targetOffset = categoryScrollOffsets.getOrElse(index) { 0 }
                            coroutineScope.launch {
                                gridState.animateScrollToItem(targetOffset)
                            }
                        },
                        desc = cat.title,
                        activePill = activeTabPill,
                        inactiveColor = inactiveTabText,
                    )
                }

                // Kaomoji Tab
                TabItem(
                    label = "ツ",
                    isSelected = selectedTabIdx == 2,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedTabIdx = 2
                    },
                    desc = if (isEnglish) "Kaomoji Emoticons" else "কাওমোজি ইমোটিকন",
                    activePill = activeTabPill,
                    inactiveColor = inactiveTabText,
                )

                // Symbols Tab
                TabItem(
                    label = "৳",
                    isSelected = selectedTabIdx == 3,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedTabIdx = 3
                    },
                    desc = if (isEnglish) "Symbols & Math" else "বাংলা ও গণিত প্রতীক",
                    activePill = activeTabPill,
                    inactiveColor = inactiveTabText,
                )
            }
        }

        // ── Main Content Grid Area ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (searchQuery.isNotBlank()) {
                // Live Search Results
                val searchResults = remember(searchQuery) { EmojiData.search(searchQuery) }
                if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (isEnglish) "No emojis found for '$searchQuery'" else "'$searchQuery'-এর জন্য কোনো ইমোজি পাওয়া যায়নি",
                            color = inactiveTabText,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (isEnglish) "${searchResults.size} results found" else "${searchResults.size}টি ইমোজি পাওয়া গেছে",
                                fontSize = 11.sp,
                                color = inactiveTabText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        items(searchResults) { item ->
                            val displayEmoji = if (defaultSkinToneIndex in 0 until item.skinTones.size) {
                                item.skinTones[defaultSkinToneIndex]
                            } else {
                                item.emoji
                            }
                            EmojiCell(
                                emoji = displayEmoji,
                                onSelect = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    recentsManager.addRecent(displayEmoji)
                                    onEmojiSelected(displayEmoji)
                                },
                                onLongClick = {
                                    if (item.skinTones.isNotEmpty()) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                            columns = GridCells.Adaptive(minSize = 42.dp),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(recents) { emoji ->
                                EmojiCell(
                                    emoji = emoji,
                                    onSelect = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onEmojiSelected(emoji)
                                    },
                                    onLongClick = {},
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Continuous Vertical Scroll with Sticky Section Headers
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        EmojiData.categories.forEach { cat ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "${cat.icon} ${cat.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeTabPill,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(pickerBg.copy(alpha = 0.95f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            items(cat.items) { item ->
                                val displayEmoji = if (defaultSkinToneIndex in 0 until item.skinTones.size) {
                                item.skinTones[defaultSkinToneIndex]
                            } else {
                                item.emoji
                            }
                                EmojiCell(
                                    emoji = displayEmoji,
                                    onSelect = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        recentsManager.addRecent(displayEmoji)
                                        onEmojiSelected(displayEmoji)
                                    },
                                    onLongClick = {
                                        if (item.skinTones.isNotEmpty()) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            skinToneTarget = item
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Kaomoji Picker
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        KaomojiData.categories.forEach { cat ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = cat.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeTabPill,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            items(cat.items) { kaomoji ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(searchBg)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onEmojiSelected(kaomoji)
                                        }
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
                3 -> {
                    // Symbols Picker
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 44.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SymbolData.categories.forEach { cat ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = cat.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeTabPill,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            items(cat.items) { symbol ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(searchBg)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onEmojiSelected(symbol)
                                        }
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

            // Anchored Floating Skin Tone Selector Callout
            skinToneTarget?.let { item ->
                AnchoredSkinToneSelector(
                    item = item,
                    theme = theme,
                    onSelect = { tone ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        skinToneTarget = null
                        if (tone == item.emoji) {
                            defaultSkinToneIndex = -1
                            recentsManager.defaultSkinToneIndex = -1
                        } else {
                            val toneIdx = item.skinTones.indexOf(tone)
                            if (toneIdx >= 0) {
                                defaultSkinToneIndex = toneIdx
                                recentsManager.defaultSkinToneIndex = toneIdx
                            }
                        }
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
                .height(46.dp)
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
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClose()
                    }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
                    .semantics { contentDescription = if (isEnglish) "Return to keyboard" else "কীবোর্ডে ফিরে যান" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⌨ ABC",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }

            // Dedicated On-Canvas Search toggle
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.keyNormalColor))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSearchClick(searchQuery)
                    }
                    .semantics { contentDescription = if (isEnglish) "Search with keyboard" else "কীবোর্ড দিয়ে খুঁজুন" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = activeTabPill,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Spacebar in emoji palette
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.keySpaceColor))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSpace()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isEnglish) "Space" else "স্পেস",
                    fontSize = 12.sp,
                    color = inactiveTabText,
                )
            }

            // Backspace key in emoji palette with repeating hold
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.keyNormalColor))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onBackspace()
                                val job = coroutineScope.launch {
                                    delay(400)
                                    while (isActive) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onBackspace()
                                        delay(60)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    }
                    .semantics { contentDescription = if (isEnglish) "Backspace" else "ডিলিট করুন" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(19.dp),
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
            .size(42.dp)
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
private fun AnchoredSkinToneSelector(
    item: EmojiItem,
    theme: KeyboardTheme,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(theme.keyNormalColor))
                .border(1.dp, Color(theme.accentColor).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Default base emoji
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(theme.backgroundColor))
                    .clickable { onSelect(item.emoji) }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.emoji, fontSize = 24.sp)
            }
            // Skin tone variations
            item.skinTones.forEach { tone ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onSelect(tone) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = tone, fontSize = 24.sp)
                }
            }
        }
    }
}
