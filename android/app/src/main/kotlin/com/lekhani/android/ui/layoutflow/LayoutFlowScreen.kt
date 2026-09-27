package com.lekhani.android.ui.layoutflow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.LayoutRegistry
import kotlinx.coroutines.launch

/**
 * LayoutFlowScreen
 * ══════════════════════════════════════════════════════════════════════════════
 * Modern, polished Material 3 screen for configuring keyboard layout sequence and Home layout.
 * Features an inward-tilted amphitheater 3D carousel with keyboard previews,
 * explicit 1-tap Home selection, and smooth jitter-free reordering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutFlowScreen(
    enabledLayouts: List<LekhaniLayoutType>,
    activeLayout: LekhaniLayoutType,
    isEnglish: Boolean,
    onLayoutsReordered: (List<LekhaniLayoutType>) -> Unit,
    onActiveLayoutChanged: (LekhaniLayoutType) -> Unit,
    onResetToDefault: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showHelpDialog by remember { mutableStateOf(false) }

    val activeIndex = remember(activeLayout, enabledLayouts) {
        enabledLayouts.indexOf(activeLayout).coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = activeIndex,
        pageCount = { enabledLayouts.size }
    )

    // Synchronize pager with active layout
    LaunchedEffect(activeIndex) {
        if (pagerState.currentPage != activeIndex && activeIndex in enabledLayouts.indices) {
            pagerState.animateScrollToPage(activeIndex)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isEnglish) "Layout Flow" else "লেআউট ফ্লো",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "ফিরে যান"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = if (isEnglish) "Help" else "সাহায্য",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Screen subtitle
            Text(
                text = if (isEnglish) "Set your Home starting layout and arrange the spacebar swipe order."
                       else "হোম লেআউট নির্ধারণ করুন এবং স্পেসবারের সোয়াইপ ক্রম সাজান।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            // ── 1. Inward Amphitheater 3D Carousel Preview ─────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp),
                contentAlignment = Alignment.Center
            ) {
                if (enabledLayouts.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 105.dp),
                        pageSpacing = 10.dp,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val layoutType = enabledLayouts[page]
                        val isCenterCard = (page == pagerState.currentPage)
                        val isHome = (layoutType == activeLayout)
                        val title = if (isEnglish) LayoutRegistry.getEnglishName(layoutType)
                                    else LayoutRegistry.getBengaliName(layoutType)

                        // Inward 3D curve (amphitheater perspective)
                        val pageOffset = (page - pagerState.currentPage) + pagerState.currentPageOffsetFraction
                        // Negative distance: left edge comes forward; Positive distance: right edge goes back
                        val rotationY = (-pageOffset * 26f).coerceIn(-30f, 30f)
                        val scale = lerp(0.88f, 1.0f, 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f))
                        val alpha = lerp(0.72f, 1.0f, 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                                .graphicsLayer {
                                    this.rotationY = rotationY
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.alpha = alpha
                                    cameraDistance = 12f * density
                                }
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(page)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isCenterCard) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                                border = if (isHome) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                         else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                shadowElevation = if (isCenterCard) 10.dp else 2.dp,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Top tag row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = when (layoutType) {
                                                    LekhaniLayoutType.PROBAHO -> "FLOW"
                                                    LekhaniLayoutType.ENGLISH -> "QWERTY"
                                                    LekhaniLayoutType.PROBHAT -> "FIXED"
                                                    LekhaniLayoutType.AVRO -> "PHONETIC"
                                                    LekhaniLayoutType.NATIONAL -> "BBS"
                                                    LekhaniLayoutType.GBOARD -> "GBOARD"
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (isHome) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Home,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = if (isEnglish) "Home" else "হোম",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    // Miniature 3-Row Keyboard Keycaps Graphic
                                    MiniatureKeyboardGraphic(
                                        isHome = isHome,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    // Title
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCenterCard) FontWeight.Bold else FontWeight.SemiBold
                                        ),
                                        color = if (isCenterCard) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Action / Status Pill
                                    if (isHome) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Text(
                                                    text = if (isEnglish) "✓ Active Home" else "✓ সক্রিয় হোম",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .height(24.dp)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onActiveLayoutChanged(layoutType)
                                                }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Text(
                                                    text = if (isEnglish) "Set as Home" else "হোম করুন",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Speech bubble on top of center card
                            if (isCenterCard) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .graphicsLayer { translationY = -10.dp.toPx() }
                                ) {
                                    Text(
                                        text = if (isHome) (if (isEnglish) "Default Home" else "ডিফল্ট হোম")
                                               else (if (isEnglish) "Tap to preview" else "প্রিভিউ"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pagination dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            ) {
                repeat(enabledLayouts.size) { idx ->
                    val isSelected = (idx == pagerState.currentPage)
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                    )
                }
            }

            // ── 2. Swipe Sequence Reorder List ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isEnglish) "Spacebar Swipe Sequence" else "স্পেসবারে সোয়াইপ ক্রম",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isEnglish) "Swipe Left ⟷ Right" else "বাম ⟷ ডান",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            fun swapLayouts(fromIndex: Int, toIndex: Int) {
                if (fromIndex in enabledLayouts.indices && toIndex in enabledLayouts.indices && fromIndex != toIndex) {
                    val mutable = enabledLayouts.toMutableList()
                    val item = mutable.removeAt(fromIndex)
                    mutable.add(toIndex, item)
                    onLayoutsReordered(mutable)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(enabledLayouts, key = { _, type -> type }) { index, type ->
                    val isHome = (type == activeLayout)
                    val offset = index - activeIndex
                    val title = if (isEnglish) LayoutRegistry.getEnglishName(type) else LayoutRegistry.getBengaliName(type)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isHome) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isHome) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        shadowElevation = if (isHome) 3.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Relative Sequence Pill Badge
                            if (isHome) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(end = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Home,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = if (isEnglish) "Home" else "হোম",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            } else {
                                val badgeText = if (offset > 0) "$offset ▸" else "◂ ${-offset}"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.padding(end = 10.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Layout Title
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isHome) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = if (isHome) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        isHome -> if (isEnglish) "Default startup layout" else "ডিফল্ট প্রারম্ভিক লেআউট"
                                        offset > 0 -> if (isEnglish) "Swipe right from Home" else "হোম থেকে ডানে সোয়াইপ"
                                        else -> if (isEnglish) "Swipe left from Home" else "হোম থেকে বামে সোয়াইপ"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 1-Tap "Set as Home" button for non-home items
                            if (!isHome) {
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onActiveLayoutChanged(type)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (isEnglish) "Make Home" else "হোম করুন",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Precision Reorder Buttons (▲ and ▼) — 100% reliable, zero jank
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { swapLayouts(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = if (isEnglish) "Move up" else "উপরে নিন",
                                        tint = if (index > 0) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { swapLayouts(index, index + 1) },
                                    enabled = index < enabledLayouts.lastIndex,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = if (isEnglish) "Move down" else "নিচে নিন",
                                        tint = if (index < enabledLayouts.lastIndex) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Reset to Default Button ────────────────────────────────────
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onResetToDefault()
                },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEnglish) "Reset to default flow" else "ডিফল্ট ক্রমে ফিরুন",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = if (isEnglish) "How Layout Flow Works" else "লেআউট ফ্লো ব্যবহারের নিয়ম",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isEnglish)
                            "• Home Layout: The keyboard layout that opens by default.\n\n" +
                            "• Spacebar Swipe: Swiping left or right on the spacebar smoothly switches to adjacent layouts.\n\n" +
                            "• Reorder: Use the ▲ and ▼ buttons to move layouts up or down to set your ideal swipe order.\n\n" +
                            "• Set as Home: Tap 'Make Home' on any layout to set it as your primary starting keyboard."
                        else
                            "• হোম লেআউট: কীবোর্ড চালু হলে প্রথমে এই লেআউটটি থাকবে।\n\n" +
                            "• স্পেসবার সোয়াইপ: স্পেসবারে বামে বা ডানে সোয়াইপ করে সহজে অন্য লেআউটে যাওয়া যায়।\n\n" +
                            "• ক্রম পরিবর্তন: পছন্দের সোয়াইপ ক্রম সাজাতে ▲ এবং ▼ বোতাম ব্যবহার করুন।\n\n" +
                            "• হোম নির্ধারণ: যেকোনো লেআউটের 'হোম করুন' বোতামে চাপ দিয়ে প্রাথমিক লেআউট নির্বাচন করুন।"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(if (isEnglish) "Got it" else "বুঝেছি")
                }
            }
        )
    }
}

/**
 * MiniatureKeyboardGraphic
 * ─────────────────────────────────────────────────────────────────────────────
 * Sleek 3-row miniature keycaps graphic giving the card an authentic keyboard look.
 */
@Composable
private fun MiniatureKeyboardGraphic(
    isHome: Boolean,
    modifier: Modifier = Modifier
) {
    val keyColor = if (isHome) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Row 1: 5 small keycaps
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(keyColor)
                )
            }
        }
        // Row 2: 5 small keycaps
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(keyColor)
                )
            }
        }
        // Row 3: Spacebar + 2 flank keys
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(keyColor)
            )
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isHome) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else keyColor)
            )
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(keyColor)
            )
        }
    }
}
