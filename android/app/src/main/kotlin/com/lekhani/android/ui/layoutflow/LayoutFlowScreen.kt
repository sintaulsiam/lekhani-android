package com.lekhani.android.ui.layoutflow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.LayoutRegistry
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─────────────────────────────────────────────────────────────────────────────
// Per-layout gradient palette + hint character
// ─────────────────────────────────────────────────────────────────────────────
private data class LayoutCardStyle(
    val gradientStart: Color,
    val gradientEnd: Color,
    val hintText: String,
    val typeLabel: String,
)

private fun layoutCardStyle(type: LekhaniLayoutType): LayoutCardStyle = when (type) {
    LekhaniLayoutType.PROBAHO  -> LayoutCardStyle(Color(0xFF4527A0), Color(0xFF7B1FA2), "প্র",  "FLOW")
    LekhaniLayoutType.AVRO     -> LayoutCardStyle(Color(0xFFBF360C), Color(0xFFFF8F00), "a→আ", "PHONETIC")
    LekhaniLayoutType.NATIONAL -> LayoutCardStyle(Color(0xFF0D47A1), Color(0xFF0288D1), "ক খ",  "BBS")
    LekhaniLayoutType.PROBHAT  -> LayoutCardStyle(Color(0xFF1B5E20), Color(0xFF43A047), "অ আ", "FIXED")
    LekhaniLayoutType.GBOARD   -> LayoutCardStyle(Color(0xFF263238), Color(0xFF546E7A), "বাং",  "GBOARD")
    LekhaniLayoutType.ENGLISH  -> LayoutCardStyle(Color(0xFF1A237E), Color(0xFF1976D2), "Aa",   "QWERTY")
}

// ─────────────────────────────────────────────────────────────────────────────
// LayoutFlowScreen
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Layout Flow screen — Modern gesture-native layout manager.
 *
 * - Live synchronized with the active IME layout.
 * - Drag-and-drop reordering with tactile haptic feedback.
 * - Clean credit-card styled chips with top-right watermark and zero text collision.
 * - Uniform height and crisp alignment across all cards.
 * - Zero redundant text strips or walls of tutorial text.
 * - Overflow menu for help and reset actions.
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

    val haptic          = LocalHapticFeedback.current
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showHelp         by rememberSaveable { mutableStateOf(false) }
    var showOverflow     by remember { mutableStateOf(false) }

    val activeName = if (isEnglish)
        LayoutRegistry.getEnglishName(activeLayout)
    else
        LayoutRegistry.getBengaliName(activeLayout)

    val activeStyle = layoutCardStyle(activeLayout)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isEnglish) "Layout Flow" else "লেআউট ফ্লো",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back" else "ফিরে যান",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = if (isEnglish) "More" else "আরও",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(if (isEnglish) "How it works" else "কীভাবে কাজ করে")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showHelp = !showHelp
                                showOverflow = false
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isEnglish) "Reset layout order" else "ক্রম রিসেট করুন",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showResetDialog = true
                                showOverflow = false
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Inline help card (expandable) ─────────────────────────────────
            AnimatedVisibility(
                visible = showHelp,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit  = shrinkVertically(tween(200)) + fadeOut(tween(200)),
            ) {
                HelpCard(isEnglish = isEnglish)
            }

            if (enabledLayouts.isEmpty()) {
                EmptyLayoutsState(
                    isEnglish = isEnglish,
                    modifier   = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.height(4.dp))

                // ── Hero: Active layout status banner ─────────────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, activeStyle.gradientStart.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Leading dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(activeStyle.gradientStart),
                        )

                        // Name + subtitle (fill remaining space, push pill to right)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AnimatedContent(
                                    targetState = activeName,
                                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                                    label = "activeHeroName",
                                ) { name ->
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = activeStyle.gradientStart.copy(alpha = 0.22f),
                                ) {
                                    Text(
                                        text = activeStyle.typeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.5.sp,
                                            letterSpacing = 0.5.sp,
                                        ),
                                        color = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Text(
                                text = if (isEnglish) "Active · swipe spacebar to switch" else "চালু · স্পেসবারে সোয়াইপ করে পরিবর্তন করুন",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // ACTIVE pill — fixed width, never clips
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                text = if (isEnglish) "ACTIVE" else "চালু",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Section header with vector drag icon ──────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isEnglish) "Swipe Sequence" else "সোয়াইপ ক্রম",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Filled.DragIndicator,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                        Text(
                            text = if (isEnglish) "Hold & drag to reorder" else "চেপে ধরে সাজান",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ── Swipe Rail with Drag-and-Drop & Live Active Sync ──────────
                SwipeRail(
                    layouts      = enabledLayouts,
                    activeLayout = activeLayout,
                    isEnglish    = isEnglish,
                    onSetDefault = { type ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onActiveLayoutChanged(type)
                    },
                    onReordered  = onLayoutsReordered,
                )

                Spacer(Modifier.height(20.dp))

                // ── "Try it here" Sandbox Card ────────────────────────────────
                var sandboxText by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    ),
                    border = BorderStroke(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (isEnglish) "Try it here" else "এখানে পরীক্ষা করুন",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        OutlinedTextField(
                            value = sandboxText,
                            onValueChange = { sandboxText = it },
                            placeholder = {
                                Text(
                                    text = if (isEnglish)
                                        "Tap here & swipe spacebar to test..."
                                    else
                                        "এখানে ট্যাপ করে স্পেসবার সোয়াইপ করুন...",
                                    fontSize = 13.sp,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (sandboxText.isNotEmpty()) {
                                    IconButton(onClick = { sandboxText = "" }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                        )

                        // ── Minimalist gesture indicator caption with vector icon ─
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = if (isEnglish)
                                    "Swipe spacebar left or right to switch layouts"
                                else
                                    "লেআউট পরিবর্তন করতে স্পেসবারে বামে বা ডানে সোয়াইপ করুন",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Reset confirmation dialog ─────────────────────────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = if (isEnglish) "Reset Layout Order?" else "ক্রম রিসেট করবেন?",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = if (isEnglish)
                        "This will restore the default spacebar swipe sequence."
                    else
                        "এটি স্পেসবার সোয়াইপের ডিফল্ট ক্রম ফিরিয়ে দেবে।",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onResetToDefault()
                }) {
                    Text(
                        text = if (isEnglish) "Reset" else "রিসেট করুন",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "বাতিল")
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SwipeRail — Drag-and-drop horizontal reorderable rail
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SwipeRail(
    layouts: List<LekhaniLayoutType>,
    activeLayout: LekhaniLayoutType,
    isEnglish: Boolean,
    onSetDefault: (LekhaniLayoutType) -> Unit,
    onReordered: (List<LekhaniLayoutType>) -> Unit,
) {
    var localLayouts by remember(layouts) { mutableStateOf(layouts) }
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    val haptic    = LocalHapticFeedback.current

    val currentLocalLayouts by rememberUpdatedState(localLayouts)
    val currentOnReordered  by rememberUpdatedState(onReordered)

    // Smooth scroll to active item whenever it changes (e.g. via spacebar swipe)
    LaunchedEffect(activeLayout) {
        val targetIdx = localLayouts.indexOf(activeLayout)
        if (targetIdx in localLayouts.indices) {
            scope.launch { listState.animateScrollToItem(targetIdx) }
        }
    }

    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            localLayouts = localLayouts.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
    )

    LazyRow(
        state                 = listState,
        contentPadding        = PaddingValues(start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier              = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(localLayouts, key = { _, type -> type }) { _, type ->
            val isActive = (type == activeLayout)

            ReorderableItem(reorderState, key = type) { isDragging ->
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 10.dp else 0.dp,
                    label       = "elevation_$type",
                )
                val scale by animateFloatAsState(
                    targetValue   = if (isDragging) 1.06f else if (isActive) 1.02f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label         = "scale_$type",
                )
                val chipWidth by animateDpAsState(
                    targetValue   = if (isActive) 116.dp else 104.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label         = "chipWidth_$type",
                )

                LayoutChip(
                    type         = type,
                    isActive     = isActive,
                    isEnglish    = isEnglish,
                    width        = chipWidth,
                    height       = 76.dp, // Pinned uniform height across all cards
                    elevation    = elevation,
                    scale        = scale,
                    onTap        = {
                        if (!isActive) onSetDefault(type)
                    },
                    dragModifier = Modifier.longPressDraggableHandle(
                        onDragStarted = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragStopped = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnReordered(currentLocalLayouts)
                        },
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LayoutChip — Modern card with top-right watermark & top-left active badge
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LayoutChip(
    type: LekhaniLayoutType,
    isActive: Boolean,
    isEnglish: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    elevation: androidx.compose.ui.unit.Dp,
    scale: Float,
    onTap: () -> Unit,
    dragModifier: Modifier,
) {
    val style = layoutCardStyle(type)

    val shortName = when (type) {
        LekhaniLayoutType.PROBAHO  -> if (isEnglish) "Probaho" else "প্রবাহ"
        LekhaniLayoutType.AVRO     -> if (isEnglish) "Avro" else "অভ্র"
        LekhaniLayoutType.NATIONAL -> if (isEnglish) "National" else "জাতীয়"
        LekhaniLayoutType.PROBHAT  -> if (isEnglish) "Probhat" else "প্রভাত"
        LekhaniLayoutType.GBOARD   -> if (isEnglish) "Gboard" else "জিবোর্ড"
        LekhaniLayoutType.ENGLISH  -> if (isEnglish) "English" else "ইংরেজি"
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.shadowElevation = elevation.toPx()
                this.shape = RoundedCornerShape(14.dp)
                this.clip = true
            }
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(style.gradientStart, style.gradientEnd),
                ),
            )
            .then(
                if (isActive) Modifier.border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(14.dp),
                ) else Modifier.border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp),
                )
            )
            .then(dragModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // ── Top Row: ACTIVE pill on left, Watermark script on right ───────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.22f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                        Text(
                            text = if (isEnglish) "ACTIVE" else "চালু",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            color = Color.White,
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(1.dp))
            }

            // Watermark in top-right: zero collision with bottom-left text
            Text(
                text = style.hintText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.24f),
            )
        }

        // ── Bottom Section: Name and Type ─────────────────────────────────────
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(
                text = shortName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = style.typeLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                ),
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HelpCard — inline expandable guide
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HelpCard(isEnglish: Boolean) {
    Surface(
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HelpRow(
                icon = Icons.Filled.SwapHoriz,
                text = if (isEnglish)
                    "Swipe the spacebar left or right to cycle through layouts while typing"
                else
                    "টাইপ করার সময় স্পেসবার বামে বা ডানে সোয়াইপ করে লেআউট পরিবর্তন করুন",
            )
            HelpRow(
                icon = Icons.Filled.DragIndicator,
                text = if (isEnglish)
                    "Long-press and drag a card to change the spacebar sequence"
                else
                    "সোয়াইপের ক্রম বদলাতে যেকোনো কার্ড চেপে ধরে ডানে বা বামে টানুন",
            )
        }
    }
}

@Composable
private fun HelpRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EmptyLayoutsState
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyLayoutsState(isEnglish: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Keyboard,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (isEnglish) "No layouts enabled" else "কোনো লেআউট সক্রিয় নেই",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isEnglish)
                "Go to Settings → Enabled Layouts to add some."
            else
                "সেটিংস → সক্রিয় লেআউট থেকে লেআউট যোগ করুন।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp),
        )
    }
}
