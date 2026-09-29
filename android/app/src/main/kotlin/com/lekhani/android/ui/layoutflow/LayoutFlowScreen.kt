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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.LayoutRegistry
import kotlinx.coroutines.launch

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
 * Layout Flow screen — Swipe Rail redesign.
 *
 * A single horizontal rail of layout chips represents the spacebar swipe order.
 * Center chip = Home (opens first). Tap any chip to make it Home.
 * Long-press a chip for reorder (move left / move right) via a context menu.
 * No carousel. No redundant list. One mental model.
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

    val haptic         = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showHelp        by rememberSaveable { mutableStateOf(false) }

    val activeIndex = remember(activeLayout, enabledLayouts) {
        enabledLayouts.indexOf(activeLayout).coerceAtLeast(0)
    }

    // Helper: swap two positions in the layout list
    fun moveLayout(from: Int, to: Int) {
        if (from in enabledLayouts.indices && to in enabledLayouts.indices && from != to) {
            val list = enabledLayouts.toMutableList()
            list.add(to, list.removeAt(from))
            onLayoutsReordered(list)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
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
                    IconButton(onClick = { showHelp = !showHelp }) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = if (isEnglish) "Help" else "সাহায্য",
                            tint = if (showHelp)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
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

            // ── Inline help card ──────────────────────────────────────────────
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

                // ── Hero: active layout display ───────────────────────────────
                val activeName = if (isEnglish)
                    LayoutRegistry.getEnglishName(activeLayout)
                else
                    LayoutRegistry.getBengaliName(activeLayout)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    // "Default Layout" badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = if (isEnglish) "Default Layout" else "ডিফল্ট লেআউট",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.04.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Animated layout name
                    AnimatedContent(
                        targetState = activeName,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "activeName",
                    ) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = if (isEnglish)
                            "Opens first when you tap a text field"
                        else
                            "টেক্সট ফিল্ডে ট্যাপ করলে এটি প্রথমে খুলবে",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(20.dp))

                // ── Section header ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isEnglish) "Spacebar Swipe Order" else "স্পেসবার সোয়াইপ ক্রম",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isEnglish) "long-press to reorder" else "চেপে ধরুন সাজাতে",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── Direction labels ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (isEnglish) "Swipe Left: Prev" else "বামে সোয়াইপ: পূর্ববর্তী",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (isEnglish) "Next: Swipe Right" else "পরবর্তী: ডানে সোয়াইপ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── Swipe Rail ────────────────────────────────────────────────
                SwipeRail(
                    layouts      = enabledLayouts,
                    activeIndex  = activeIndex,
                    isEnglish    = isEnglish,
                    onSetDefault = { type ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onActiveLayoutChanged(type)
                    },
                    onMoveLeft   = { idx -> moveLayout(idx, idx - 1) },
                    onMoveRight  = { idx -> moveLayout(idx, idx + 1) },
                )

                Spacer(Modifier.height(24.dp))

                // ── Live Typing & Gesture Sandbox ─────────────────────────────
                var sandboxText by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = if (isEnglish) "Live Typing Sandbox" else "লাইভ টাইপিং পরীক্ষা",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = if (isEnglish) "Active: $activeName" else "চালু: $activeName",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        androidx.compose.material3.OutlinedTextField(
                            value = sandboxText,
                            onValueChange = { sandboxText = it },
                            placeholder = {
                                Text(
                                    text = if (isEnglish) "Tap here & swipe spacebar to test..." else "এখানে ট্যাপ করে স্পেসবার সোয়াইপ করুন...",
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
                            }
                        )

                        Text(
                            text = if (isEnglish)
                                "💡 Swipe thumb across the spacebar while typing to seamlessly switch between your selected layouts."
                            else
                                "💡 টাইপ করার সময় স্পেসবারে সোয়াইপ করলেই পরপর আপনার পছন্দের লেআউটগুলো বদলাবে।",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── Reset text link ───────────────────────────────────────────
                TextButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isEnglish) "Reset to defaults" else "ডিফল্টে ফিরুন",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
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
// SwipeRail — horizontal scrollable chip row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SwipeRail(
    layouts: List<LekhaniLayoutType>,
    activeIndex: Int,
    isEnglish: Boolean,
    onSetDefault: (LekhaniLayoutType) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
) {
    val listState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()

    // Scroll rail so the home chip is visible when activeIndex changes
    LaunchedEffect(activeIndex) {
        if (activeIndex in layouts.indices) {
            scope.launch { listState.animateScrollToItem(activeIndex) }
        }
    }

    LazyRow(
        state            = listState,
        contentPadding   = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier         = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(layouts, key = { _, type -> type }) { index, type ->
            val isHome = (index == activeIndex)
            var showMenu by remember { mutableStateOf(false) }

            val chipWidth by animateDpAsState(
                targetValue  = if (isHome) 124.dp else 112.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label        = "chipWidth_$index",
            )
            val chipHeight by animateDpAsState(
                targetValue  = if (isHome) 86.dp else 78.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label        = "chipHeight_$index",
            )
            val chipAlpha by animateFloatAsState(
                targetValue  = if (isHome) 1f else 0.75f,
                animationSpec = tween(220),
                label        = "chipAlpha_$index",
            )
            val chipScale by animateFloatAsState(
                targetValue  = if (isHome) 1f else 0.95f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label        = "chipScale_$index",
            )

            Box(modifier = Modifier.wrapContentSize()) {
                LayoutChip(
                    type      = type,
                    isHome    = isHome,
                    isEnglish = isEnglish,
                    width     = chipWidth,
                    height    = chipHeight,
                    alpha     = chipAlpha,
                    scale     = chipScale,
                    onTap     = { if (!isHome) onSetDefault(type) },
                    onLongPress = { showMenu = true },
                )

                // Context menu: reorder actions
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    if (index > 0) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isEnglish) "← Move Left" else "← বামে সরান",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showMenu = false
                                onMoveLeft(index)
                            },
                        )
                    }
                    if (index < layouts.lastIndex) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isEnglish) "Move Right →" else "ডানে সরান →",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showMenu = false
                                onMoveRight(index)
                            },
                        )
                    }
                    if (!isHome) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isEnglish) "Set as Default" else "ডিফল্ট করুন",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onSetDefault(type)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LayoutChip — single chip in the rail
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LayoutChip(
    type: LekhaniLayoutType,
    isHome: Boolean,
    isEnglish: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    alpha: Float,
    scale: Float,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val style = layoutCardStyle(type)

    // Shorten long names to fit chip width
    val shortName = when (type) {
        LekhaniLayoutType.PROBAHO  -> if (isEnglish) "Probaho" else "প্রবাহ"
        LekhaniLayoutType.AVRO     -> if (isEnglish) "Avro" else "অভ্র"
        LekhaniLayoutType.NATIONAL -> if (isEnglish) "National" else "জাতীয়"
        LekhaniLayoutType.PROBHAT  -> if (isEnglish) "Probhat" else "প্রভাত"
        LekhaniLayoutType.GBOARD   -> if (isEnglish) "Gboard" else "জিবোর্ড"
        LekhaniLayoutType.ENGLISH  -> if (isEnglish) "English" else "ইংরেজি"
    }

    val subtitle = when (type) {
        LekhaniLayoutType.PROBAHO  -> if (isEnglish) "Flow" else "প্রবাহ"
        LekhaniLayoutType.AVRO     -> if (isEnglish) "Phonetic" else "ধ্বনিভিত্তিক"
        LekhaniLayoutType.NATIONAL -> if (isEnglish) "BBS Fixed" else "জাতীয় মান"
        LekhaniLayoutType.PROBHAT  -> if (isEnglish) "Fixed" else "প্রভাত ফিক্সড"
        LekhaniLayoutType.GBOARD   -> if (isEnglish) "Standard" else "জিবোর্ড"
        LekhaniLayoutType.ENGLISH  -> if (isEnglish) "QWERTY" else "ইংরেজি"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = height)
                .graphicsLayer {
                    this.alpha  = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(style.gradientStart, style.gradientEnd),
                    ),
                )
                .then(
                    if (isHome) Modifier.border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp)
                    ) else Modifier
                )
                .pointerInput(type) {
                    detectTapGestures(
                        onTap       = { onTap() },
                        onLongPress = { onLongPress() },
                    )
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // Top Row: Type Pill on Left, Default Star or Hint on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.32f),
                ) {
                    Text(
                        text     = style.typeLabel,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color    = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                if (isHome) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.28f),
                    ) {
                        Text(
                            text     = if (isEnglish) "★ DEFAULT" else "★ ডিফল্ট",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color    = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Text(
                        text     = style.hintText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color    = Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            // Bottom Section: Name and Subtitle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
            ) {
                Text(
                    text       = shortName,
                    style      = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.5.sp
                    ),
                    color      = Color.White,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    text       = subtitle,
                    style      = MaterialTheme.typography.labelSmall.copy(
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color      = Color.White.copy(alpha = 0.78f),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SummaryStrip — compact horizontal sequence badge row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SummaryStrip(
    layouts: List<LekhaniLayoutType>,
    activeIndex: Int,
    isEnglish: Boolean,
) {
    if (layouts.size < 2) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        layouts.forEachIndexed { index, type ->
            val isHome  = (index == activeIndex)
            val offset  = index - activeIndex
            val style   = layoutCardStyle(type)
            val label   = when (type) {
                LekhaniLayoutType.PROBAHO  -> if (isEnglish) "Probaho" else "প্রবাহ"
                LekhaniLayoutType.AVRO     -> if (isEnglish) "Avro" else "অভ্র"
                LekhaniLayoutType.NATIONAL -> if (isEnglish) "National" else "জাতীয়"
                LekhaniLayoutType.PROBHAT  -> if (isEnglish) "Probhat" else "প্রভাত"
                LekhaniLayoutType.GBOARD   -> if (isEnglish) "Gboard" else "জিবোর্ড"
                LekhaniLayoutType.ENGLISH  -> if (isEnglish) "English" else "ইংরেজি"
            }
            val chipText = when {
                isHome        -> "● $label"
                offset < 0   -> "← $label"
                else          -> "$label →"
            }

            Surface(
                shape  = RoundedCornerShape(20.dp),
                color  = if (isHome)
                    style.gradientStart.copy(alpha = 0.20f)
                else
                    Color.Transparent,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isHome)
                        style.gradientStart.copy(alpha = 0.55f)
                    else
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
            ) {
                Text(
                    text  = chipText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isHome) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isHome)
                        style.gradientStart
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HelpCard — inline expandable help (replaces static AlertDialog)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HelpCard(isEnglish: Boolean) {
    Surface(
        shape  = RoundedCornerShape(0.dp),
        color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HelpRow(
                icon = "◉",
                text = if (isEnglish)
                    "Tap any chip in the rail to make it your default layout"
                else
                    "রেলে যেকোনো চিপে ট্যাপ করে ডিফল্ট লেআউট বেছে নিন",
            )
            HelpRow(
                icon = "⇄",
                text = if (isEnglish)
                    "Swipe the spacebar left or right to cycle through layouts while typing"
                else
                    "টাইপ করার সময় স্পেসবার বামে বা ডানে সোয়াইপ করে লেআউট পরিবর্তন করুন",
            )
            HelpRow(
                icon = "⋮",
                text = if (isEnglish)
                    "Long-press a chip to move it left or right in the swipe order"
                else
                    "চিপ দীর্ঘক্ষণ চেপে ধরলে বামে বা ডানে সরানোর বিকল্প পাবেন",
            )
        }
    }
}

@Composable
private fun HelpRow(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text  = icon,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text  = text,
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
        Text(
            text  = "⌨",
            fontSize = 48.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text  = if (isEnglish) "No layouts enabled" else "কোনো লেআউট সক্রিয় নেই",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = if (isEnglish)
                "Go to Settings → Enabled Layouts to add some."
            else
                "সেটিংস → সক্রিয় লেআউট থেকে লেআউট যোগ করুন।",
            style       = MaterialTheme.typography.bodySmall,
            color       = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            textAlign   = TextAlign.Center,
            modifier    = Modifier.padding(horizontal = 36.dp),
        )
    }
}
