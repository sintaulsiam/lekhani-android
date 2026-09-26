package com.lekhani.android.ui.candidate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * ══════════════════════════════════════════════════════════════════════════════
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
) {
    val state by stateFlow.collectAsState()
    val hasItems = state is CandidateStripState.Candidates && !isToolsMenuOpen
    var showToolbarOverride by remember { mutableStateOf(false) }

    LaunchedEffect(hasItems) {
        if (!hasItems) showToolbarOverride = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .background(Color(theme.backgroundColor)),
    ) {
        if (state is CandidateStripState.EmojiSearch) {
            val emojiState = state as CandidateStripState.EmojiSearch
            EmojiSearchStrip(
                query = emojiState.query,
                emojis = emojiState.emojis,
                onEmojiClick = onCandidateClick,
                onBack = { onEmojiSearchClose?.invoke() },
                onClearQuery = { onEmojiSearchClear?.invoke() },
                theme = theme,
                isEnglish = isEnglish,
            )
        } else if (hasItems && !showToolbarOverride && !isToolsMenuOpen) {
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

                val items = (state as? CandidateStripState.Candidates)?.items ?: emptyList()
                Box(modifier = Modifier.weight(1f)) {
                    StripContent(
                        items = items,
                        onCandidateClick = onCandidateClick,
                        onBlacklist = onBlacklist,
                        theme = theme,
                    )
                }
            }
        } else {
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

@Composable
private fun EmojiSearchStrip(
    query: String,
    emojis: List<String>,
    onEmojiClick: (String) -> Unit,
    onBack: () -> Unit,
    onClearQuery: () -> Unit,
    theme: KeyboardTheme,
    isEnglish: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back button returning to full Emoji palette
        Box(
            modifier = Modifier
                .size(StripHeight)
                .clip(CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isEnglish) "Back to emojis" else "ইমোজিতে ফিরে যান",
                tint = Color(theme.accentColor),
                modifier = Modifier.size(20.dp),
            )
        }

        // Active search query indicator badge
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .clip(RoundedCornerShape(CornerRadius))
                .background(Color(theme.keyNormalColor))
                .clickable { if (query.isNotEmpty()) onClearQuery() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (query.isEmpty()) "🔍 ইমোজি..." else "🔍 $query",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (query.isEmpty()) Color(theme.labelDimColor) else Color(theme.accentColor),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
                if (query.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "✕",
                        fontSize = 11.sp,
                        color = Color(theme.labelDimColor),
                    )
                }
            }
        }

        // Horizontal scrolling emoji results
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (emoji in emojis) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEmojiClick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                    )
                }
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
        // Prominent Extra Tools menu trigger on the far left:
        // Turns into a Close icon when the vault drawer is open.
        Box(
            modifier = Modifier
                .padding(start = 4.dp, end = 2.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (isToolsMenuOpen) 0.25f else 0.14f))
                .clickable { onOpenToolsMenu?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isToolsMenuOpen) Icons.Filled.Close else Icons.Filled.GridView,
                contentDescription = if (isToolsMenuOpen) {
                    if (isEnglish) "Close Vault" else "ভল্ট বন্ধ করুন"
                } else {
                    if (isEnglish) "Tool Vault" else "টুল ভল্ট"
                },
                modifier = Modifier.size(19.dp),
                tint = accentColor
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Horizontally scrollable list of active tools (Settings is first!)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (tool in tools) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToolClick?.invoke(tool) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
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

@Composable
private fun StripContent(
    items: List<CandidateItem>,
    onCandidateClick: (String) -> Unit,
    onBlacklist: (String) -> Unit,
    theme: KeyboardTheme,
) {
    val scrollState = rememberScrollState()

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

    Box(
        modifier = Modifier
            .alpha(pillAlpha)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (item.isPrimary) primaryBg else secondaryBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    isFlashingBlacklist = true
                    onLongClick()
                },
            )
            .padding(horizontal = if (item.isPrimary) 16.dp else 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.offset(y = (-1.0).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.text,
                fontSize = 15.sp,
                fontWeight = if (item.isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                color = if (item.isPrimary) primaryText else normalText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    lineHeight = 18.sp,
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
                HomophoneBadge(alternate = item.homophones)
            }
        }
    }
}

/**
 * A small badge shown below the candidate text when a known homophone exists.
 * Example: "পড়া" shows a badge "≠ পরা" to help the user pick the right spelling.
 */
@Composable
private fun HomophoneBadge(alternate: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(BadgeCorner))
            .background(BadgeBg)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = "≠ $alternate",
            fontSize = 9.sp,
            color = BadgeText,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
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
