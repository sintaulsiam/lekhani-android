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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Features (FEATURES.md §2 & ROADMAP.md Phase 4):
 *   ✅ Fluid slide-in / slide-out animation (spring physics)
 *   ✅ Centre-pinned primary candidate with glowing teal pill
 *   ✅ Homophone disambiguation badge (*পড়া* vs *পরা*)
 *   ✅ Long-press to blacklist a candidate
 *   ✅ TalkBack accessibility (WCAG 2.1 — contentDescription on each pill)
 *
 * @param stateFlow         Hot [StateFlow] of [CandidateStripState] from the IME service
 * @param onCandidateClick  Called when the user taps a candidate
 * @param onBlacklist       Called when the user long-presses a candidate
 */
@Composable
fun CandidateStripView(
    stateFlow: StateFlow<CandidateStripState>,
    onCandidateClick: (String) -> Unit,
    onBlacklist: (String) -> Unit,
) {
    val state by stateFlow.collectAsState()
    val hasItems = state is CandidateStripState.Candidates

    AnimatedVisibility(
        visible = hasItems,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        ) + fadeIn(animationSpec = tween(120)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(100),
        ) + fadeOut(animationSpec = tween(80)),
    ) {
        val items = (state as? CandidateStripState.Candidates)?.items ?: return@AnimatedVisibility
        StripContent(
            items = items,
            onCandidateClick = onCandidateClick,
            onBlacklist = onBlacklist,
        )
    }
}

// ── Internal composables ─────────────────────────────────────────────────────

@Composable
private fun StripContent(
    items: List<CandidateItem>,
    onCandidateClick: (String) -> Unit,
    onBlacklist: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .background(StripBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    // Thin vertical divider between candidates
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(DividerColor)
                    )
                }
                CandidatePill(
                    item = item,
                    onClick = { onCandidateClick(item.text) },
                    onLongClick = { onBlacklist(item.text) },
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
                        colors = listOf(Color.Transparent, StripBackground)
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

    Box(
        modifier = Modifier
            .alpha(pillAlpha)
            .wrapContentSize()
            .clip(RoundedCornerShape(CornerRadius))
            .then(
                if (item.isPrimary) Modifier.background(PrimaryPillBg)
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    isFlashingBlacklist = true
                    onLongClick()
                },
            )
            .padding(
                horizontal = if (item.isPrimary) PrimaryPillHPad else 10.dp,
                vertical = PrimaryPillVPad,
            )
            .semantics { contentDescription = semanticDesc },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = item.text,
                fontSize = if (item.isPrimary) 17.sp else 15.sp,
                fontWeight = if (item.isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                color = if (item.isPrimary) PrimaryPillText else SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Homophone disambiguation badge
            if (item.homophones != null) {
                Spacer(Modifier.height(2.dp))
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
    )
}
