package com.lekhani.android.ui.candidate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.Text
import com.lekhani.android.ui.theme.iconVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry
import kotlinx.coroutines.flow.StateFlow

// ── Design tokens ─────────────────────────────────────────────────────────────

private val StripBackground   = Color(0xFF0D1117)
private val PrimaryPillBg     = Color(0xFF00D4A0)       // teal — matches home row accent
private val PrimaryPillText   = Color(0xFF000000)
private val SecondaryText     = Color(0xFFB0B8CC)
private val DimText           = Color(0xFF606880)
private val BadgeBg           = Color(0xFF1A2540)
private val BadgeText         = Color(0xFF00AAFF)
private val DividerColor      = Color(0xFF1E2840)
private val BlacklistFlash    = Color(0x66FF4444)

private val StripHeight       = 44.dp
private val PrimaryPillHPad   = 16.dp
private val PrimaryPillVPad   = 6.dp
private val CornerRadius      = 20.dp
private val BadgeCorner       = 6.dp
private val ItemSpacing       = 8.dp

// ── Public composable ─────────────────────────────────────────────────────────

/**
 * CandidateStripView
 * The horizontal candidate suggestion strip rendered above the keyboard canvas.
 *
 * Features (FEATURES.md §2, ROADMAP.md Phase 4 & Phase 10):
 *   ✅ Fluid slide-in / slide-out animation (spring physics)
 *   ✅ Centre-pinned primary candidate with glowing theme-accent pill
 *   ✅ Homophone disambiguation badge (*পড়া* vs *পরা*)
 *   ✅ Long-press to blacklist a candidate
 *   ✅ Customizable Quick Toolbar when idle (Emoji, Voice, Clipboard, Theme, Settings)
 *   ✅ TalkBack accessibility (WCAG 2.1 — contentDescription on each pill)
 *
 * @param stateFlow         Hot [StateFlow] of [CandidateStripState] from the IME service
 * @param onCandidateClick  Called when the user taps a candidate
 * @param onBlacklist       Called when the user long-presses a candidate
 * @param theme             Active [KeyboardTheme]
 * @param activeTools       List of enabled [KeyboardPreferences.ToolbarTool]
 * @param onToolClick       Callback when a quick tool icon is tapped
 */
@Composable
fun CandidateStripView(
    stateFlow: StateFlow<CandidateStripState>,
    onCandidateClick: (String) -> Unit,
    onBlacklist: (String) -> Unit,
    theme: KeyboardTheme = ThemeRegistry.THEME_FLOW_TEAL,
    activeTools: List<KeyboardPreferences.ToolbarTool> = KeyboardPreferences.DEFAULT_TOOL_LIST,
    isEnglish: Boolean = false,
    isToolsMenuOpen: Boolean = false,
    onToolClick: ((KeyboardPreferences.ToolbarTool) -> Unit)? = null,
    onOpenToolsMenu: (() -> Unit)? = null,
    onEmojiSearchClose: (() -> Unit)? = null,
    onEmojiSearchClear: (() -> Unit)? = null,
    onEmojiSearchExitToKeyboard: (() -> Unit)? = null,
    onUndoClick: ((UndoInfo) -> Unit)? = null,
) {
    val state by stateFlow.collectAsState()
    val hasItems = state is CandidateStripState.Candidates && !isToolsMenuOpen
    var showToolbarOverride by remember { mutableStateOf(false) }

    val currentTopCandidate = (state as? CandidateStripState.Candidates)?.items?.firstOrNull()?.text
    var lastCandidateText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentTopCandidate, hasItems) {
        if (!hasItems || (currentTopCandidate != null && currentTopCandidate != lastCandidateText)) {
            showToolbarOverride = false
        }
        lastCandidateText = currentTopCandidate
    }

    val isEmojiSearch = state is CandidateStripState.EmojiSearch
    val targetStripHeight = if (isEmojiSearch) 124.dp else StripHeight
    val animatedStripHeight by animateDpAsState(
        targetValue = targetStripHeight,
        animationSpec = tween(120),
        label = "StripHeightAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(animatedStripHeight)
            .background(Color(theme.backgroundColor)),
    ) {
        val displayMode = when {
            state is CandidateStripState.Notice -> 7
            state is CandidateStripState.EmojiSearch -> 0
            state is CandidateStripState.SwipeDeletePreview -> 5
            state is CandidateStripState.Selection -> 6
            hasItems && !showToolbarOverride && !isToolsMenuOpen -> 1
            state is CandidateStripState.Undo && !isToolsMenuOpen -> 3
            state is CandidateStripState.QuickChip && !isToolsMenuOpen && !showToolbarOverride -> 4
            else -> 2
        }

        AnimatedContent(
            targetState = displayMode,
            transitionSpec = {
                // Only animate when actually switching between major modes (toolbar ⇔ candidates).
                // When updating candidates list within the same mode (1→1), use a fast cross-fade
                // so the strip never visually collapses between keystrokes.
                if (initialState == 1 && targetState == 1) {
                    fadeIn(animationSpec = tween(40)) togetherWith fadeOut(animationSpec = tween(40))
                } else {
                    fadeIn(animationSpec = tween(80)) togetherWith fadeOut(animationSpec = tween(80))
                }
            },
            label = "CandidateStripModeTransition"
        ) { mode ->
            when (mode) {
                0 -> {
                    val emojiState = state as? CandidateStripState.EmojiSearch
                    if (emojiState != null) {
                        EmojiSearchStrip(
                            query = emojiState.query,
                            emojis = emojiState.emojis,
                            onEmojiClick = onCandidateClick,
                            onBack = { onEmojiSearchClose?.invoke() },
                            onClearQuery = { onEmojiSearchClear?.invoke() },
                            onExitToKeyboard = { onEmojiSearchExitToKeyboard?.invoke() },
                            theme = theme,
                            isEnglish = isEnglish,
                        )
                    }
                }
                1 -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Persistent toolbar expand button on far left
                        Box(
                            modifier = Modifier
                                .size(StripHeight)
                                .clip(CircleShape)
                                .clickable { showToolbarOverride = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = if (isEnglish) "Show Toolbar" else "টুলবার প্রদর্শন",
                                tint = Color(theme.labelColor).copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        val candState = state as? CandidateStripState.Candidates
                        candState?.undoInfo?.let { undo: UndoInfo ->
                            UndoChip(
                                undoInfo = undo,
                                onUndoClick = { onUndoClick?.invoke(undo) },
                                theme = theme,
                                isEnglish = isEnglish,
                            )
                        }

                        val items = candState?.items ?: emptyList()
                        Box(modifier = Modifier.weight(1f)) {
                            StripContent(
                                items = items,
                                onCandidateClick = onCandidateClick,
                                onBlacklist = onBlacklist,
                                theme = theme,
                            )
                        }
                    }
                }
                3 -> {
                    val undoState = state as? CandidateStripState.Undo
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        undoState?.undoInfo?.let { undo: UndoInfo ->
                            UndoChip(
                                undoInfo = undo,
                                onUndoClick = { onUndoClick?.invoke(undo) },
                                theme = theme,
                                isEnglish = isEnglish,
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            ToolbarContent(
                                tools = activeTools,
                                onToolClick = onToolClick,
                                onOpenToolsMenu = onOpenToolsMenu,
                                theme = theme,
                                isEnglish = isEnglish,
                                isToolsMenuOpen = isToolsMenuOpen,
                            )
                        }
                    }
                }
                4 -> {
                    val quickState = state as? CandidateStripState.QuickChip
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(StripHeight)
                                .clip(CircleShape)
                                .clickable { showToolbarOverride = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = if (isEnglish) "Show Toolbar" else "টুলবার প্রদর্শন",
                                tint = Color(theme.labelColor).copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (quickState != null) {
                            Spacer(Modifier.width(6.dp))
                            QuickChipPill(
                                chip = quickState,
                                onClick = { onCandidateClick(quickState.pasteText) },
                                theme = theme,
                            )
                        }
                    }
                }
                5 -> {
                    val swipeState = state as? CandidateStripState.SwipeDeletePreview
                    if (swipeState != null) {
                        SwipeDeletePreviewStrip(
                            state = swipeState,
                            theme = theme,
                            isEnglish = isEnglish,
                        )
                    }
                }
                6 -> {
                    val selState = state as? CandidateStripState.Selection
                    if (selState != null) {
                        CandidateSelectionStrip(
                            state = selState,
                            theme = theme,
                            isEnglish = isEnglish,
                        )
                    }
                }
                7 -> {
                    val noticeState = state as? CandidateStripState.Notice
                    if (noticeState != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = noticeState.icon,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = noticeState.message,
                                    color = Color(theme.labelColor),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { noticeState.onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(theme.labelDimColor),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasItems && !isToolsMenuOpen) {
                            // Collapse toolbar button back to candidates
                            Box(
                                modifier = Modifier
                                    .size(StripHeight)
                                    .clip(CircleShape)
                                    .clickable { showToolbarOverride = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isEnglish) "Show Candidates" else "পরামর্শ প্রদর্শন",
                                    tint = Color(theme.accentColor),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            ToolbarContent(
                                tools = activeTools,
                                onToolClick = onToolClick,
                                onOpenToolsMenu = onOpenToolsMenu,
                                theme = theme,
                                isEnglish = isEnglish,
                                isToolsMenuOpen = isToolsMenuOpen,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun EmojiSearchStrip(
    query: String,
    emojis: List<String>,
    onEmojiClick: (String) -> Unit,
    onBack: () -> Unit,
    onClearQuery: () -> Unit,
    onExitToKeyboard: () -> Unit,
    theme: KeyboardTheme,
    isEnglish: Boolean = false,
) {
    val accentColor = Color(theme.accentColor)
    val labelColor = Color(theme.labelColor)
    val labelDimColor = Color(theme.labelDimColor)
    val keyBg = Color(theme.keyNormalColor)
    val borderColor = Color(theme.keyBorderColor)
    val barBg = Color(theme.keyShiftColor)

    val infiniteTransition = rememberInfiniteTransition(label = "SearchCursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CursorAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .background(Color(theme.backgroundColor))
    ) {
        // ── Top: 2-Row Emoji Results Grid ───────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (emojis.isEmpty() && query.isNotBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isEnglish) "No emojis matching \"$query\""
                               else "\"$query\"-এর জন্য কোনো ইমোজি পাওয়া যায়নি",
                        fontSize = 12.sp,
                        color = labelDimColor,
                    )
                }
            } else {
                val gridState = rememberLazyGridState()
                LaunchedEffect(query) {
                    gridState.scrollToItem(0)
                }
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    items(emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(keyBg.copy(alpha = 0.35f))
                                .clickable { onEmojiClick(emoji) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 21.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // ── Bottom: Search Query Bar (Docked Directly Above Keyboard) ────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(barBg)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // [ ⬅ Back ] Button returning to full Emoji Palette
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(keyBg)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isEnglish) "Back to emojis" else "ইমোজিতে ফিরে যান",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Search Query Pill (Center, weight 1f) with Live Blinking Cursor
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(keyBg)
                    .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(17.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (query.isEmpty()) labelDimColor else accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (query.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .alpha(cursorAlpha)
                                .background(accentColor, RoundedCornerShape(1.dp))
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isEnglish) "Search emojis..." else "ইমোজি খুঁজুন...",
                            fontSize = 13.sp,
                            color = labelDimColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    } else {
                        Text(
                            text = query,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = labelColor,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .alpha(cursorAlpha)
                                .background(accentColor, RoundedCornerShape(1.dp))
                        )
                    }
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onClearQuery() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = labelDimColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (emojis.isNotEmpty()) {
                    Text(
                        text = "${emojis.size}",
                        fontSize = 11.sp,
                        color = labelDimColor.copy(alpha = 0.7f)
                    )
                }
            }

            // Quick [ ABC ] Button returning directly to normal typing keyboard
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(keyBg)
                    .clickable { onExitToKeyboard() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }
    }
}

// ── Internal composables ─────────────────────────────────────────────────────

@Composable
private fun ToolbarContent(
    tools: List<KeyboardPreferences.ToolbarTool>,
    onToolClick: ((KeyboardPreferences.ToolbarTool) -> Unit)?,
    onOpenToolsMenu: (() -> Unit)?,
    theme: KeyboardTheme,
    isEnglish: Boolean = false,
    isToolsMenuOpen: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val accentColor = Color(theme.accentColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Prominent Extra Tools menu trigger on the far left (44dp touch target):
        // Turns into a Close icon when the drawer is open.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onOpenToolsMenu?.invoke() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = if (isToolsMenuOpen) 0.25f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isToolsMenuOpen) Icons.Filled.Close else Icons.Filled.GridView,
                    contentDescription = if (isToolsMenuOpen) {
                        if (isEnglish) "Close tools" else "টুলস বন্ধ করুন"
                    } else {
                        if (isEnglish) "More tools" else "আরও টুলস"
                    },
                    modifier = Modifier.size(19.dp),
                    tint = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Dynamically distributed active tools with equal spacing & touch targets
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val totalWidth = maxWidth
            val toolCount = tools.size
            val itemWidth = if (toolCount > 0) totalWidth / toolCount else 48.dp
            val needsScroll = itemWidth < 42.dp

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (needsScroll) Modifier.horizontalScroll(scrollState) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (needsScroll) Arrangement.spacedBy(4.dp) else Arrangement.SpaceEvenly,
            ) {
                for (tool in tools) {
                    Box(
                        modifier = (if (needsScroll) Modifier.width(44.dp) else Modifier.weight(1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToolClick?.invoke(tool) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tool.iconVector,
                            contentDescription = if (isEnglish) tool.titleEnglish else tool.titleBengali,
                            modifier = Modifier.size(20.dp),
                            tint = Color(theme.labelColor).copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StripContent(
    items: List<CandidateItem>,
    onCandidateClick: (String) -> Unit,
    onBlacklist: (String) -> Unit,
    theme: KeyboardTheme,
) {
    val scrollState = rememberScrollState()
    // Only reset scroll when the PRIMARY candidate changes (new word being typed),
    // not on every keystroke that updates secondary candidates — avoids scroll jitter.
    val primaryKey = items.firstOrNull()?.text
    LaunchedEffect(primaryKey) {
        scrollState.scrollTo(0)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .background(Color(theme.backgroundColor)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            items.forEach { item ->
                CandidatePill(
                    item = item,
                    onClick = { onCandidateClick(item.text) },
                    onLongClick = { onBlacklist(item.text) },
                    theme = theme,
                )
            }
        }

        // Fade-out edges to hint at horizontal scroll
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(24.dp)
                .height(StripHeight)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(theme.backgroundColor))
                    )
                )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidatePill(
    item: CandidateItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    theme: KeyboardTheme,
) {
    var isFlashingBlacklist by remember { mutableStateOf(false) }
    val pillAlpha by animateFloatAsState(
        targetValue = if (isFlashingBlacklist) 0.3f else 1f,
        animationSpec = tween(200),
        label = "pillAlpha",
    )

    val semanticDesc = buildString {
        append(item.text)
        if (item.isPrimary) append(", primary suggestion")
        if (item.homophones != null) append(", tap to distinguish from ${item.homophones}")
        append(", long-press to remove from suggestions")
    }

    val primaryBg = Color(theme.accentColor)
    val lum = (primaryBg.red * 0.299f + primaryBg.green * 0.587f + primaryBg.blue * 0.114f)
    val primaryText = if (lum > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)

    // Blend secondary pills with keycap background of active theme
    val secondaryBg = Color(theme.keyNormalColor)
    val normalText = Color(theme.labelColor)

    val pillBorder = if (item.isVerbatimPreview) {
        BorderStroke(1.dp, Color(theme.labelColor).copy(alpha = 0.28f))
    } else null

    Box(
        modifier = Modifier
            .semantics { contentDescription = semanticDesc }
            .alpha(pillAlpha)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .then(
                if (pillBorder != null) Modifier.border(pillBorder, RoundedCornerShape(17.dp)) else Modifier
            )
            .background(if (item.isPrimary) primaryBg else secondaryBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!item.isVerbatimPreview) {
                        isFlashingBlacklist = true
                        onLongClick()
                    }
                },
            )
            .padding(horizontal = if (item.isEmoji) 10.dp else if (item.isPrimary) 16.dp else if (item.isVerbatimPreview) 12.dp else 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.offset(y = (-1.0).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val displayText = if (item.isVerbatimPreview) "\"${item.text}\"" else item.text
            Text(
                text = displayText,
                fontSize = if (item.isEmoji) 18.sp else if (item.isVerbatimPreview) 14.sp else 15.sp,
                fontWeight = if (item.isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                color = if (item.isPrimary) primaryText else if (item.isVerbatimPreview) normalText.copy(alpha = 0.88f) else normalText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    lineHeight = if (item.isEmoji) 20.sp else 18.sp,
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    ),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
            // Homophone disambiguation badge
            if (item.homophones != null) {
                Spacer(Modifier.width(4.dp))
                HomophoneBadge(alternate = item.homophones, theme = theme)
            }
        }
    }
}

@Composable
private fun QuickChipPill(
    chip: CandidateStripState.QuickChip,
    onClick: () -> Unit,
    theme: KeyboardTheme,
) {
    val accentColor = Color(theme.accentColor)
    val keyBg = Color(theme.keyNormalColor)
    val labelColor = Color(theme.labelColor)

    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(keyBg)
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val chipIcon = when (chip.chipType) {
                com.lekhani.android.data.smart.SmartAssistant.QuickChipType.OTP -> Icons.Filled.Lock
                com.lekhani.android.data.smart.SmartAssistant.QuickChipType.URL -> Icons.Filled.Link
                com.lekhani.android.data.smart.SmartAssistant.QuickChipType.EMAIL -> Icons.Filled.Email
                com.lekhani.android.data.smart.SmartAssistant.QuickChipType.PHONE -> Icons.Filled.Phone
                com.lekhani.android.data.smart.SmartAssistant.QuickChipType.RECENT -> Icons.Filled.ContentPaste
            }
            Icon(
                imageVector = chipIcon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = chip.label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A small badge shown below the candidate text when a known homophone exists.
 * Example: "পড়া" shows a badge "≠ পরা" to help the user pick the right spelling.
 */
@Composable
private fun HomophoneBadge(alternate: String, theme: KeyboardTheme) {
    val badgeBg = Color(theme.keyShiftColor)
    val badgeText = Color(theme.accentColor)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(BadgeCorner))
            .background(badgeBg)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = "≠ $alternate",
            fontSize = 9.sp,
            color = badgeText,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * A sleek chip shown when a word was autocorrected or transliterated,
 * allowing single-tap rollback to the verbatim original text.
 */
@Composable
private fun UndoChip(
    undoInfo: UndoInfo,
    onUndoClick: () -> Unit,
    theme: KeyboardTheme,
    isEnglish: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onUndoClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(theme.accentColor).copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color(theme.accentColor).copy(alpha = 0.45f)),
        modifier = modifier
            .padding(start = 4.dp, end = 4.dp)
            .height(30.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isEnglish) "Undo autocorrection" else "পূর্বাবস্থায় ফেরান",
                tint = Color(theme.accentColor),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${if (isEnglish) "Undo" else "পূর্বাবস্থা"}: \"${undoInfo.originalText}\"",
                color = Color(theme.labelColor),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Mid-gesture preview strip for swipe-to-delete.
 * Displays live strikethrough text preview and two-thumb multi-touch action chips (Copy / Cut).
 */
@Composable
private fun SwipeDeletePreviewStrip(
    state: CandidateStripState.SwipeDeletePreview,
    theme: KeyboardTheme,
    isEnglish: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Multi-touch two-thumb action chips for second hand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                onClick = state.onCopy,
                shape = RoundedCornerShape(12.dp),
                color = Color(theme.keyNormalColor),
                border = BorderStroke(1.dp, Color(theme.accentColor).copy(alpha = 0.35f)),
                modifier = Modifier.height(30.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = if (isEnglish) "Copy" else "কপি",
                        tint = Color(theme.accentColor),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (isEnglish) "Copy" else "কপি",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(theme.labelColor),
                    )
                }
            }

            Surface(
                onClick = state.onCut,
                shape = RoundedCornerShape(12.dp),
                color = Color(theme.keyNormalColor),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f)),
                modifier = Modifier.height(30.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCut,
                        contentDescription = if (isEnglish) "Cut" else "কাট",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (isEnglish) "Cut" else "কাট",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(theme.labelColor),
                    )
                }
            }
        }

        // Live preview of text with strikethrough and count badge
        Row(
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = state.previewText,
                style = TextStyle(
                    textDecoration = TextDecoration.LineThrough,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = Color(0x33FF5252),
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "-${state.wordCount}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                    )
                }
            }
        }
    }
}

/**
 * Contextual Selection Action Bar shown whenever text is highlighted.
 */
@Composable
private fun CandidateSelectionStrip(
    state: CandidateStripState.Selection,
    theme: KeyboardTheme,
    isEnglish: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SelectionActionButton(
            icon = Icons.Filled.ContentCut,
            label = if (isEnglish) "Cut" else "কাট",
            theme = theme,
            onClick = state.onCut,
        )
        SelectionActionButton(
            icon = Icons.Filled.ContentCopy,
            label = if (isEnglish) "Copy" else "কপি",
            theme = theme,
            onClick = state.onCopy,
        )
        SelectionActionButton(
            icon = Icons.Filled.ContentPaste,
            label = if (isEnglish) "Paste" else "পেস্ট",
            theme = theme,
            onClick = state.onPaste,
        )
        SelectionActionButton(
            icon = Icons.Filled.SelectAll,
            label = if (isEnglish) "Select All" else "সব নির্বাচন",
            theme = theme,
            onClick = state.onSelectAll,
        )
        SelectionActionButton(
            icon = Icons.Filled.Delete,
            label = if (isEnglish) "Delete" else "মুছুন",
            theme = theme,
            isDestructive = true,
            onClick = state.onDelete,
        )
        SelectionActionButton(
            icon = Icons.Filled.Close,
            label = if (isEnglish) "Deselect" else "বাতিল",
            theme = theme,
            onClick = state.onDeselect,
        )
    }
}

@Composable
private fun SelectionActionButton(
    icon: ImageVector,
    label: String,
    theme: KeyboardTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    val activeColor = if (isDestructive) Color(0xFFFF5252) else Color(theme.accentColor)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(theme.keyNormalColor),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.25f)),
        modifier = modifier
            .height(32.dp)
            .padding(horizontal = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = activeColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(theme.labelColor),
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
private fun CandidateStripPreview() {
    val items = listOf(
        CandidateItem("আমাদের", isPrimary = true),
        CandidateItem("আমি"),
        CandidateItem("পড়া", homophones = "পরা"),
        CandidateItem("বাংলাদেশ"),
        CandidateItem("ভালো"),
        CandidateItem("খুব"),
    )
    StripContent(
        items = items,
        onCandidateClick = {},
        onBlacklist = {},
        theme = ThemeRegistry.THEME_FLOW_TEAL,
    )
}
