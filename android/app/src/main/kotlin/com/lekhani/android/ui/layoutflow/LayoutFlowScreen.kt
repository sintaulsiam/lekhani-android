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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
 * Dedicated Material 3 screen for configuring keyboard layout sequence and Home layout.
 * Features a forward-tilted cylindrical 3D carousel and clean relative-offset reorder list.
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
            // Subtitle
            Text(
                text = if (isEnglish) "Arrange the order used when switching layouts."
                       else "লেআউট পরিবর্তনের সোয়াইপ ক্রম সাজান।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // ── 1. Forward-Tilted Cylindrical Carousel Preview ─────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                contentAlignment = Alignment.Center
            ) {
                if (enabledLayouts.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 96.dp),
                        pageSpacing = 16.dp,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val layoutType = enabledLayouts[page]
                        val isCurrent = (page == pagerState.currentPage)
                        val title = if (isEnglish) LayoutRegistry.getEnglishName(layoutType)
                                    else LayoutRegistry.getBengaliName(layoutType)

                        // 3D cylindrical projection: curve forward toward the viewer
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        val rotationY = (pageOffset * -16f).coerceIn(-25f, 25f)
                        val scale = lerp(0.86f, 1.0f, 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f))
                        val alpha = lerp(0.55f, 1.0f, 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f))

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    this.rotationY = rotationY
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.alpha = alpha
                                    cameraDistance = 18f * density
                                }
                                .width(155.dp)
                                .height(145.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onActiveLayoutChanged(layoutType)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(page)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                         else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                shadowElevation = if (isCurrent) 10.dp else 2.dp,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Keyboard,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isCurrent) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Speech-bubble pill on top of active card
                            if (isCurrent) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .graphicsLayer { translationY = -12.dp.toPx() }
                                ) {
                                    Text(
                                        text = if (isEnglish) "Current layout" else "বর্তমান লেআউট",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Flanking chevrons
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = if (isEnglish) "Previous" else "পূর্ববর্তী",
                            tint = if (pagerState.currentPage > 0) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(enabledLayouts.size - 1))
                            }
                        },
                        enabled = pagerState.currentPage < enabledLayouts.size - 1,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isEnglish) "Next" else "পরবর্তী",
                            tint = if (pagerState.currentPage < enabledLayouts.size - 1) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                }
            }

            // Pagination dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            ) {
                repeat(enabledLayouts.size) { idx ->
                    val isSelected = (idx == pagerState.currentPage)
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                    )
                }
            }

            // ── 2. Your Layouts (order) List with Relative Badges ──────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) "Your Layouts (order)" else "আপনার লেআউটসমূহ (সোয়াইপ ক্রম)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            var draggingLayout by remember { mutableStateOf<LekhaniLayoutType?>(null) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            var dragStartIndex by remember { mutableIntStateOf(-1) }
            var currentDropIndex by remember { mutableIntStateOf(-1) }
            var itemHeightPx by remember { mutableFloatStateOf(0f) }
            val itemCenterYs = remember { mutableStateMapOf<Int, Float>() }
            val density = LocalDensity.current

            val isDraggingActive = (draggingLayout != null && dragStartIndex in enabledLayouts.indices && currentDropIndex in enabledLayouts.indices)
            val effectiveRowHeight = if (itemHeightPx > 0f) itemHeightPx else with(density) { 60.dp.toPx() }

            val activeLayoutTitle = if (isEnglish) LayoutRegistry.getEnglishName(activeLayout)
                                   else LayoutRegistry.getBengaliName(activeLayout)

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
                    val isThisDragging = (draggingLayout == type)

                    val targetShift = when {
                        !isDraggingActive -> 0f
                        isThisDragging -> 0f
                        dragStartIndex > currentDropIndex && index >= currentDropIndex && index < dragStartIndex -> effectiveRowHeight
                        dragStartIndex < currentDropIndex && index > dragStartIndex && index <= currentDropIndex -> -effectiveRowHeight
                        else -> 0f
                    }

                    val shiftAnim = remember(type) { Animatable(0f) }
                    LaunchedEffect(targetShift, isDraggingActive) {
                        if (isDraggingActive) {
                            shiftAnim.animateTo(
                                targetValue = targetShift,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        } else {
                            shiftAnim.snapTo(0f)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (size.height > 0 && !isThisDragging) {
                                    itemHeightPx = size.height.toFloat()
                                }
                            }
                            .onGloballyPositioned { coords ->
                                val bounds = coords.boundsInParent()
                                itemCenterYs[index] = bounds.top + bounds.height / 2f
                            }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isThisDragging) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = when {
                                isThisDragging -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                isHome -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            },
                            shadowElevation = if (isThisDragging) 12.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isThisDragging) 30f else 0f)
                                .graphicsLayer {
                                    translationY = if (isThisDragging) dragOffsetY else shiftAnim.value
                                    scaleX = if (isThisDragging) 1.02f else 1f
                                    scaleY = if (isThisDragging) 1.02f else 1f
                                }
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onActiveLayoutChanged(type)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Title and dynamic relative subtitle
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isHome) FontWeight.Bold else FontWeight.SemiBold
                                        ),
                                        color = if (isHome) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurface
                                    )
                                    val subtitleText = when {
                                        isHome -> if (isEnglish) "Home layout (tap to switch)" else "হোম লেআউট"
                                        offset > 0 -> if (isEnglish) "Swipe right from $activeLayoutTitle"
                                                      else "$activeLayoutTitle থেকে ডানে সোয়াইপ"
                                        else -> if (isEnglish) "Swipe left from $activeLayoutTitle"
                                                else "$activeLayoutTitle থেকে বামে সোয়াইপ"
                                    }
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Relative Directional Offset Badge on the right
                                if (isHome) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(end = 12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Home,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp)
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
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 12.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                // Drag handle
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .pointerInput(type) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggingLayout = type
                                                    val sIdx = enabledLayouts.indexOf(type)
                                                    dragStartIndex = sIdx
                                                    currentDropIndex = sIdx
                                                    dragOffsetY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragEnd = {
                                                    val from = dragStartIndex
                                                    val to = currentDropIndex
                                                    if (from in enabledLayouts.indices && to in enabledLayouts.indices && from != to) {
                                                        val mutable = enabledLayouts.toMutableList()
                                                        val item = mutable.removeAt(from)
                                                        mutable.add(to, item)
                                                        onLayoutsReordered(mutable)
                                                    }
                                                    draggingLayout = null
                                                    dragStartIndex = -1
                                                    currentDropIndex = -1
                                                    dragOffsetY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragCancel = {
                                                    draggingLayout = null
                                                    dragStartIndex = -1
                                                    currentDropIndex = -1
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    val originCenterY = itemCenterYs[dragStartIndex] ?: 0f
                                                    val currentDragCenterY = originCenterY + dragOffsetY
                                                    var closestIndex = dragStartIndex
                                                    var minDistance = Float.MAX_VALUE
                                                    itemCenterYs.forEach { (idx, centerY) ->
                                                        val dist = kotlin.math.abs(centerY - currentDragCenterY)
                                                        if (dist < minDistance) {
                                                            minDistance = dist
                                                            closestIndex = idx
                                                        }
                                                    }
                                                    if (closestIndex != currentDropIndex && closestIndex in enabledLayouts.indices) {
                                                        currentDropIndex = closestIndex
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = if (isEnglish) "Reorder handle" else "ক্রম পরিবর্তন",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .padding(vertical = 12.dp)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEnglish) "Reset to default" else "ডিফল্ট ক্রমে ফিরুন",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = if (isEnglish) "Layout Flow Navigation" else "লেআউট ফ্লো নির্দেশিকা",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isEnglish) {
                        "The layouts are arranged in a horizontal loop on your keyboard spacebar.\n\n" +
                        "• Home Layout: Your primary startup keyboard.\n" +
                        "• Swipe Left (◂): Steps backward to layouts on the left.\n" +
                        "• Swipe Right (▸): Steps forward to layouts on the right.\n\n" +
                        "Drag the handles (≡) to reorder the sequence, or tap any card in the carousel to set it as your Home layout."
                    } else {
                        "লেআউটগুলো আপনার কীবোর্ড স্পেসবারে একটি আনুভূমিক বৃত্তের মতো কাজ করে।\n\n" +
                        "• হোম লেআউট: কীবোর্ড খোলার সাথে সাথে চালু হওয়া প্রধান লেআউট।\n" +
                        "• বামে সোয়াইপ (◂): বামের লেআউটে যায়।\n" +
                        "• ডানে সোয়াইপ (▸): ডানের লেআউটে যায়।\n\n" +
                        "ক্রম পরিবর্তন করতে হ্যান্ডেল (≡) ধরে টানুন, অথবা যেকোনো কার্ডে ট্যাপ করে হোম লেআউট হিসেবে সেট করুন।"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(if (isEnglish) "Got it" else "বুঝেছি")
                }
            }
        )
    }
}
