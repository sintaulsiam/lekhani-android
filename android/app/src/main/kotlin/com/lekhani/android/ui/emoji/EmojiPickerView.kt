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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
    paletteHeight: Dp = 304.dp,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIdx by remember { mutableIntStateOf(1) } // 0: Recents, 1: Emojis, 2: Kaomoji, 3: Symbols
    var skinToneTarget by remember { mutableStateOf<EmojiItem?>(null) }
    var defaultSkinToneIndex by remember { mutableIntStateOf(recentsManager.defaultSkinToneIndex) }

    val recents = remember(selectedTabIdx) { recentsManager.getRecents() }
    val gridState = rememberLazyGridState()

    val pickerBg = Color(theme.backgroundColor)
    val tabBarBg = Color(theme.keyShiftColor)
    val activeTabPill = Color(theme.accentColor)
    val inactiveTabText = Color(theme.labelDimColor)
    val searchBg = Color(theme.keyNormalColor)
    val textColor = Color(theme.labelColor)
    val borderColor = Color(theme.keyBorderColor)

    val tabScrollState = rememberScrollState()
    var pendingScrollCategory by remember { mutableStateOf<Int?>(null) }

    // Precalculate category indices for continuous jump scrolling (without headers)
    val categoryScrollOffsets = remember(EmojiData.categories) {
        val offsets = mutableListOf<Int>()
        var runningCount = 0
        EmojiData.categories.forEach { cat ->
            offsets.add(runningCount)
            runningCount += cat.items.size
        }
        offsets
    }

    // Decoupled category index calculation: only notifies observers when category boundary is crossed
    val currentCategoryIdx by remember {
        derivedStateOf {
            val firstVisible = gridState.firstVisibleItemIndex
            val index = categoryScrollOffsets.indexOfLast { it <= firstVisible }
            if (index >= 0 && index < EmojiData.categories.size) index else 0
        }
    }

    // Auto-scroll the top tab bar to keep the active category in view,
    // but ONLY when the user is not actively flinging the grid to prevent animation collisions
    LaunchedEffect(currentCategoryIdx, selectedTabIdx, gridState.isScrollInProgress) {
        if (selectedTabIdx == 1 && !gridState.isScrollInProgress) {
            val density = view.resources.displayMetrics.density
            val approxTabWidthPx = (46f * density).toInt()
            val targetScroll = (currentCategoryIdx * approxTabWidthPx) - (view.width / 3).coerceAtLeast(0)
            tabScrollState.animateScrollTo(targetScroll.coerceAtLeast(0))
        }
    }

    // Handle jump to category (including switching from Recents / Kaomoji / Symbols)
    val onCategoryClick: (Int) -> Unit = { catIndex ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (selectedTabIdx != 1) {
            pendingScrollCategory = catIndex
            selectedTabIdx = 1
        } else {
            val targetOffset = categoryScrollOffsets.getOrElse(catIndex) { 0 }
            coroutineScope.launch {
                gridState.scrollToItem(targetOffset)
            }
        }
    }

    // When switching from another tab into emojis, execute the pending category scroll once grid is mounted
    LaunchedEffect(selectedTabIdx, pendingScrollCategory) {
        if (selectedTabIdx == 1 && pendingScrollCategory != null) {
            val targetCat = pendingScrollCategory!!
            pendingScrollCategory = null
            val targetOffset = categoryScrollOffsets.getOrElse(targetCat) { 0 }
            gridState.scrollToItem(targetOffset)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(paletteHeight)
            .background(pickerBg)
            .semantics { contentDescription = if (isEnglish) "Emoji and symbol palette" else "ইমোজি এবং প্রতীক প্যালেট" },
    ) {
        // ── Top: Category Tab Bar (Isolated in sub-composable to avoid root recompositions) ──
        CategoryTabBar(
            selectedTabIdx = selectedTabIdx,
            currentCategoryIdx = currentCategoryIdx,
            tabScrollState = tabScrollState,
            tabBarBg = tabBarBg,
            activeTabPill = activeTabPill,
            inactiveTabText = inactiveTabText,
            isEnglish = isEnglish,
            onTabSelect = { tabIdx ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                selectedTabIdx = tabIdx
            },
            onCategoryClick = onCategoryClick,
        )

        // ── Main Content Grid Area (Clean, Uninterrupted, High Density) ─────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (selectedTabIdx) {
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
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = recents,
                                key = { it },
                                contentType = { "recent" }
                            ) { emoji ->
                                EmojiCell(
                                    emoji = emoji,
                                    hasSkinTones = false,
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
                    // Continuous Vertical Scroll (Pure Uninterrupted Emoji Sea, No Headers)
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 40.dp),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        EmojiData.categories.forEach { cat ->
                            items(
                                items = cat.items,
                                key = { "${cat.id}_${it.emoji}" },
                                contentType = { "emoji" }
                            ) { item ->
                                val displayEmoji = if (defaultSkinToneIndex in 0 until item.skinTones.size) {
                                    item.skinTones[defaultSkinToneIndex]
                                } else {
                                    item.emoji
                                }
                                EmojiCell(
                                    emoji = displayEmoji,
                                    hasSkinTones = item.skinTones.isNotEmpty(),
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
                            item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_kao_${cat.name}", contentType = "header") {
                                Text(
                                    text = cat.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeTabPill,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            items(
                                items = cat.items,
                                key = { "${cat.name}_$it" },
                                contentType = { "kaomoji" }
                            ) { kaomoji ->
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
                            item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_sym_${cat.name}", contentType = "header") {
                                Text(
                                    text = cat.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeTabPill,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            items(
                                items = cat.items,
                                key = { "${cat.name}_$it" },
                                contentType = { "symbol" }
                            ) { symbol ->
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
                .height(44.dp)
                .background(tabBarBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Prominent ABC key returning to the typing keyboard
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(theme.keyNormalColor))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClose()
                    }
                    .semantics { contentDescription = if (isEnglish) "Return to keyboard" else "কীবোর্ডে ফিরে যান" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⌨ ABC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }

            // Dedicated On-Canvas Search toggle -> Invokes Option B Full Keyboard Search Mode
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(theme.keyNormalColor))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSearchClick("")
                    }
                    .semantics { contentDescription = if (isEnglish) "Search emojis" else "ইমোজি অনুসন্ধান" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = activeTabPill,
                    modifier = Modifier.size(19.dp),
                )
            }

            // Spacebar in emoji palette
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                    .size(width = 46.dp, height = 34.dp)
                    .clip(RoundedCornerShape(10.dp))
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
private fun CategoryTabIcon(
    categoryId: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val icon = when (categoryId) {
        "smileys" -> Icons.Outlined.SentimentSatisfied
        "people" -> Icons.Outlined.Person
        "nature", "animals" -> Icons.Outlined.Pets
        "food" -> Icons.Outlined.LocalCafe
        "activities" -> Icons.Outlined.SportsSoccer
        "travel" -> Icons.Outlined.Explore
        "objects" -> Icons.Outlined.Lightbulb
        "symbols" -> Icons.Outlined.Tag
        "flags" -> Icons.Outlined.Flag
        else -> Icons.Outlined.SentimentSatisfied
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(17.dp)
    )
}

@Composable
private fun TabItem(
    isSelected: Boolean,
    onClick: () -> Unit,
    desc: String,
    activePill: Color = Color(0xFF00E5B8),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) activePill else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun CategoryTabBar(
    selectedTabIdx: Int,
    currentCategoryIdx: Int,
    tabScrollState: androidx.compose.foundation.ScrollState,
    tabBarBg: Color,
    activeTabPill: Color,
    inactiveTabText: Color,
    isEnglish: Boolean,
    onTabSelect: (Int) -> Unit,
    onCategoryClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(tabBarBg)
            .horizontalScroll(tabScrollState)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Tab 0: Recents (Schedule clock icon)
        TabItem(
            isSelected = selectedTabIdx == 0,
            onClick = { onTabSelect(0) },
            desc = if (isEnglish) "Recent Emojis" else "সাম্প্রতিক ইমোজি",
            activePill = activeTabPill,
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = if (selectedTabIdx == 0) Color.Black else inactiveTabText,
                modifier = Modifier.size(17.dp)
            )
        }

        // Tab 1..N: Standard Categories with Theme-Aware Vector Icons
        EmojiData.categories.forEachIndexed { index, cat ->
            val isCatSelected = (selectedTabIdx == 1 && currentCategoryIdx == index)
            TabItem(
                isSelected = isCatSelected,
                onClick = { onCategoryClick(index) },
                desc = cat.title,
                activePill = activeTabPill,
            ) {
                CategoryTabIcon(
                    categoryId = cat.id,
                    tint = if (isCatSelected) Color.Black else inactiveTabText,
                )
            }
        }

        // Kaomoji Tab
        TabItem(
            isSelected = selectedTabIdx == 2,
            onClick = { onTabSelect(2) },
            desc = if (isEnglish) "Kaomoji Emoticons" else "কাওমোজি ইমোটিকন",
            activePill = activeTabPill,
        ) {
            Text(
                text = "(^_^)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedTabIdx == 2) Color.Black else inactiveTabText,
            )
        }

        // Symbols Tab
        TabItem(
            isSelected = selectedTabIdx == 3,
            onClick = { onTabSelect(3) },
            desc = if (isEnglish) "Symbols & Math" else "বাংলা ও গণিত প্রতীক",
            activePill = activeTabPill,
        ) {
            Text(
                text = "৳",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedTabIdx == 3) Color.Black else inactiveTabText,
            )
        }
    }
}

private val EmojiTextStyle = TextStyle(
    fontSize = 21.sp,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(
    emoji: String,
    hasSkinTones: Boolean = false,
    onSelect: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val cellModifier = if (hasSkinTones) {
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongClick,
            )
    } else {
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
    }

    Box(
        modifier = cellModifier.semantics { contentDescription = emoji },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = EmojiTextStyle,
        )
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
