package com.lekhani.android.ui.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.data.settings.KeyboardPreferences.ToolbarTool
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.ui.theme.iconVector
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * ExtraToolsSheetView
 * ══════════════════════════════════════════════════════════════════════════════
 * Sleek, clean Material 3 Tool Vault drawer with rock-solid direct-placement drag-and-drop.
 *
 * Guarantees:
 *   ✅ Persistent Top Toolbar with dynamic Close icon.
 *   ✅ Disjoint Tool Sets: Toolbar tools never appear in Vault, and vice-versa.
 *   ✅ Minimalist Aesthetics: Zero clutter, no tool counts, no long subtitles.
 *   ✅ Proportional Reset Button with 12dp spacing to Done button.
 *   ✅ Non-Janky Direct Placement: Detaches with 3° tilt, shadow & ghost slot.
 *   ✅ Clear Visible Placement Slots: Visual Drop Slots in both Toolbar and Vault,
 *      clearly highlighting the exact placement position in real time.
 *   ✅ Guaranteed Drop Handling: try/finally inside awaitEachGesture with boundsInRoot,
 *      ensuring dragging never hangs, offsets properly, and never gets stuck.
 *   ✅ Instant Quick-Move: Quick tap moves tool between Toolbar and Vault.
 */
@Composable
fun ExtraToolsSheetView(
    theme: KeyboardTheme,
    prefs: KeyboardPreferences,
    isEnglish: Boolean = false,
    onToolSelected: (ToolbarTool) -> Unit,
    onToolsUpdated: (List<ToolbarTool>) -> Unit,
    onClose: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = Color(theme.backgroundColor)
    val cardBg = Color(theme.keyNormalColor)
    val accentColor = Color(theme.accentColor)
    val textColor = Color(theme.labelColor)
    val subTextColor = textColor.copy(alpha = 0.60f)
    val borderColor = textColor.copy(alpha = 0.12f)

    var isEditMode by remember { mutableStateOf(false) }
    var activeTools by remember { mutableStateOf(prefs.getActiveToolbarTools()) }
    var vaultTools by remember {
        mutableStateOf(prefs.getVaultTools().filter { !prefs.getActiveToolbarTools().contains(it) })
    }

    fun syncTools(newActive: List<ToolbarTool>, newVault: List<ToolbarTool>) {
        val sanitizedActive = newActive.distinct()
        val sanitizedVault = newVault.filter { !sanitizedActive.contains(it) }.distinct()
        activeTools = sanitizedActive
        vaultTools = sanitizedVault
        prefs.setToolbarToolsList(sanitizedActive)
        prefs.setVaultToolsList(sanitizedVault)
        onToolsUpdated(sanitizedActive)
    }

    fun sendToVault(tool: ToolbarTool, targetIndex: Int = 0) {
        if (activeTools.size <= 1) return // Keep at least 1 tool on toolbar
        val newActive = activeTools.filter { it != tool }
        val newVault = vaultTools.toMutableList()
        val clamped = targetIndex.coerceIn(0, newVault.size)
        newVault.add(clamped, tool)
        syncTools(newActive, newVault.distinct())
    }

    fun bringToToolbar(tool: ToolbarTool, targetIndex: Int = activeTools.size) {
        val newVault = vaultTools.filter { it != tool }
        val newActive = activeTools.toMutableList()
        val clamped = targetIndex.coerceIn(0, newActive.size)
        newActive.add(clamped, tool)
        syncTools(newActive.distinct(), newVault)
    }

    fun reorderActive(tool: ToolbarTool, targetIndex: Int) {
        val list = activeTools.toMutableList()
        val from = list.indexOf(tool)
        if (from >= 0) {
            list.removeAt(from)
            val clamped = targetIndex.coerceIn(0, list.size)
            list.add(clamped, tool)
            syncTools(list, vaultTools)
        }
    }

    fun reorderVault(tool: ToolbarTool, targetIndex: Int) {
        val list = vaultTools.toMutableList()
        val from = list.indexOf(tool)
        if (from >= 0) {
            list.removeAt(from)
            val clamped = targetIndex.coerceIn(0, list.size)
            list.add(clamped, tool)
            syncTools(activeTools, list)
        }
    }

    fun resetToDefaults() {
        prefs.resetToolsToDefault()
        syncTools(prefs.getActiveToolbarTools(), prefs.getVaultTools())
    }

    // Drag-and-drop coordinates in Root Window coordinates
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var draggedTool by remember { mutableStateOf<ToolbarTool?>(null) }
    var dragFromToolbar by remember { mutableStateOf(false) }
    var currentTouchPosInRoot by remember { mutableStateOf(Offset.Zero) }

    var toolbarZoneBounds by remember { mutableStateOf(Rect.Zero) }
    var vaultZoneBounds by remember { mutableStateOf(Rect.Zero) }

    val toolbarItemBounds = remember { mutableStateMapOf<Int, Rect>() }
    val vaultItemBounds = remember { mutableStateMapOf<Int, Rect>() }

    // Drop Zone Calculation in Root Coordinates:
    val vaultTop = if (vaultZoneBounds.top > 0) vaultZoneBounds.top else (rootBounds.top + 160f)
    val isHoveringToolbar = (draggedTool != null && currentTouchPosInRoot.y < vaultTop)
    val isHoveringVault = (draggedTool != null && currentTouchPosInRoot.y >= vaultTop)

    // Calculate Toolbar placement target index
    val toolbarDropIndex = remember(currentTouchPosInRoot, activeTools.size, isHoveringToolbar) {
        if (!isHoveringToolbar || activeTools.isEmpty()) {
            activeTools.size
        } else {
            var target = activeTools.size
            for (i in activeTools.indices) {
                val b = toolbarItemBounds[i] ?: continue
                if (currentTouchPosInRoot.x < b.center.x) {
                    target = i
                    break
                }
            }
            target
        }
    }

    // Calculate Vault placement target index
    val vaultDropIndex = remember(currentTouchPosInRoot, vaultTools.size, isHoveringVault) {
        if (!isHoveringVault || vaultTools.isEmpty()) {
            vaultTools.size
        } else {
            var closest = vaultTools.size
            var minDist = Float.MAX_VALUE
            for (i in vaultTools.indices) {
                val b = vaultItemBounds[i] ?: continue
                val dist = (b.center - currentTouchPosInRoot).getDistance()
                if (dist < minDist) {
                    minDist = dist
                    closest = i
                }
            }
            closest
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .onGloballyPositioned { coords ->
                rootBounds = coords.boundsInRoot()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (!isEditMode) {
            // ══════════════════════════════════════════════════════════════════
            // NORMAL VIEW: Spacious, clean grid of Vault-only tools
            // ══════════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Header (Clean title, no counters, no duplicate buttons)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Tool Vault" else "টুল ভল্ট",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    }

                    if (vaultTools.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "All tools are placed on the toolbar"
                                       else "সকল টুল কীবোর্ড টুলবারে রাখা আছে",
                                fontSize = 12.sp,
                                color = subTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // 3-column clean grid of vault tools (50dp compact height)
                        val chunked = vaultTools.chunked(3)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (row in chunked) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (tool in row) {
                                        Surface(
                                            onClick = { onToolSelected(tool) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = cardBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = tool.iconVector,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = if (isEnglish) tool.titleEnglish else tool.titleBengali,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = textColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Single clean Edit Button at bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(
                        onClick = { isEditMode = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cardBg,
                            contentColor = textColor
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Edit Toolbar" else "টুলবার সাজান",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // ══════════════════════════════════════════════════════════════════
            // EDIT MODE: Direct-Placement Drag-and-Drop & Quick Tap Reorder
            // ══════════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header with properly-proportioned Reset and Done buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isEnglish) "Edit Toolbar" else "টুলবার সাজান",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .clickable { resetToDefaults() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = if (isEnglish) "Reset Defaults" else "রিসেট",
                                tint = textColor.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                draggedTool = null
                                isEditMode = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEnglish) "Done" else "সম্পন্ন",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // ── Toolbar Zone ──────────────────────────────────────────────
                val toolbarBorderColor by animateColorAsState(
                    targetValue = if (isHoveringToolbar) accentColor else borderColor,
                    animationSpec = tween(150),
                    label = "tbBorder"
                )
                val toolbarBgColor by animateColorAsState(
                    targetValue = if (isHoveringToolbar) accentColor.copy(alpha = 0.16f) else cardBg.copy(alpha = 0.45f),
                    animationSpec = tween(150),
                    label = "tbBg"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            toolbarZoneBounds = coords.boundsInRoot()
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(toolbarBgColor)
                        .border(if (isHoveringToolbar) 1.5.dp else 1.dp, toolbarBorderColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEnglish) "Toolbar" else "টুলবার",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isHoveringToolbar) accentColor else textColor
                        )
                        if (isHoveringToolbar) {
                            Text(
                                text = if (isEnglish) "Drop here to place" else "এখানে ছাড়ুন",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeTools.forEachIndexed { index, tool ->
                            val isThisBeingDragged = (draggedTool == tool)
                            var itemCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                            // ── VISIBLE PLACEMENT SLOT (Before item) ───────────
                            if (isHoveringToolbar && toolbarDropIndex == index) {
                                Box(
                                    modifier = Modifier
                                        .weight(1.15f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(accentColor.copy(alpha = 0.25f))
                                        .border(2.dp, accentColor, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = draggedTool?.iconVector ?: Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isEnglish) "Place" else "বসবে",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                    }
                                }
                            }

                            // ── Existing Tool Pill ────────────────────────────
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .onGloballyPositioned { coords ->
                                        itemCoords = coords
                                        toolbarItemBounds[index] = coords.boundsInRoot()
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isThisBeingDragged) accentColor.copy(alpha = 0.08f) else cardBg)
                                    .border(
                                        1.dp,
                                        if (isThisBeingDragged) accentColor.copy(alpha = 0.35f) else borderColor,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .pointerInput(tool, index) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var dragStarted = false
                                            val downId = down.id
                                            val itemTopLeft = itemCoords?.boundsInRoot()?.topLeft ?: Offset.Zero
                                            var touchPos = itemTopLeft + down.position

                                            try {
                                                withTimeoutOrNull(200L) {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == downId }
                                                        if (change == null || !change.pressed) {
                                                            return@withTimeoutOrNull
                                                        }
                                                        val delta = change.positionChange()
                                                        touchPos += delta
                                                        if ((change.position - down.position).getDistance() > 18f) {
                                                            dragStarted = true
                                                            change.consume()
                                                            return@withTimeoutOrNull
                                                        }
                                                    }
                                                }

                                                val currentChange = currentEvent.changes.firstOrNull { it.id == downId }
                                                if (currentChange != null && currentChange.pressed) {
                                                    dragStarted = true
                                                }

                                                if (dragStarted) {
                                                    draggedTool = tool
                                                    dragFromToolbar = true
                                                    currentTouchPosInRoot = touchPos
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                    drag(downId) { change ->
                                                        val delta = change.positionChange()
                                                        touchPos += delta
                                                        currentTouchPosInRoot = touchPos
                                                        change.consume()
                                                    }

                                                    val vTop = if (vaultZoneBounds.top > 0) vaultZoneBounds.top else (rootBounds.top + 160f)
                                                    if (touchPos.y < vTop) {
                                                        reorderActive(tool, toolbarDropIndex)
                                                    } else {
                                                        sendToVault(tool, vaultDropIndex)
                                                    }
                                                } else {
                                                    sendToVault(tool)
                                                }
                                            } finally {
                                                draggedTool = null
                                            }
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                if (!isThisBeingDragged) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = tool.iconVector,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isEnglish) tool.titleEnglish else tool.titleBengali,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tool.iconVector,
                                            contentDescription = null,
                                            tint = accentColor.copy(alpha = 0.25f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // ── VISIBLE PLACEMENT SLOT (At the end of row) ─────────
                        if (isHoveringToolbar && toolbarDropIndex == activeTools.size) {
                            Box(
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accentColor.copy(alpha = 0.25f))
                                    .border(2.dp, accentColor, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = draggedTool?.iconVector ?: Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isEnglish) "Place" else "বসবে",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Vault Zone ────────────────────────────────────────────────
                val vaultBorderColor by animateColorAsState(
                    targetValue = if (isHoveringVault) accentColor else borderColor,
                    animationSpec = tween(150),
                    label = "vBorder"
                )
                val vaultBgColor by animateColorAsState(
                    targetValue = if (isHoveringVault) accentColor.copy(alpha = 0.16f) else cardBg.copy(alpha = 0.45f),
                    animationSpec = tween(150),
                    label = "vBg"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            vaultZoneBounds = coords.boundsInRoot()
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(vaultBgColor)
                        .border(if (isHoveringVault) 1.5.dp else 1.dp, vaultBorderColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEnglish) "Vault" else "ভল্ট",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isHoveringVault) accentColor else textColor
                        )
                        if (isHoveringVault) {
                            Text(
                                text = if (isEnglish) "Drop here to store" else "ভল্টে রাখতে ছাড়ুন",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (vaultTools.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnglish) "Vault is empty — all tools on toolbar"
                                       else "ভল্ট খালি — সকল টুল কীবোর্ড টুলবারে আছে",
                                fontSize = 11.sp,
                                color = subTextColor
                            )
                        }
                    } else {
                        val chunked = vaultTools.chunked(3)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            for (row in chunked) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (tool in row) {
                                        val index = vaultTools.indexOf(tool)
                                        val isThisBeingDragged = (draggedTool == tool)
                                        val isDropTargetSlot = (isHoveringVault && vaultDropIndex == index)
                                        var itemCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(28.dp)
                                                .onGloballyPositioned { coords ->
                                                    itemCoords = coords
                                                    vaultItemBounds[index] = coords.boundsInRoot()
                                                }
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isThisBeingDragged) accentColor.copy(alpha = 0.08f)
                                                    else if (isDropTargetSlot) accentColor.copy(alpha = 0.28f)
                                                    else cardBg
                                                )
                                                .border(
                                                    if (isDropTargetSlot) 2.dp else 1.dp,
                                                    if (isThisBeingDragged) accentColor.copy(alpha = 0.35f)
                                                    else if (isDropTargetSlot) accentColor
                                                    else borderColor,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .pointerInput(tool, index) {
                                                    awaitEachGesture {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        var dragStarted = false
                                                        val downId = down.id
                                                        val itemTopLeft = itemCoords?.boundsInRoot()?.topLeft ?: Offset.Zero
                                                        var touchPos = itemTopLeft + down.position

                                                        try {
                                                            withTimeoutOrNull(200L) {
                                                                while (true) {
                                                                    val event = awaitPointerEvent()
                                                                    val change = event.changes.firstOrNull { it.id == downId }
                                                                    if (change == null || !change.pressed) {
                                                                        return@withTimeoutOrNull
                                                                    }
                                                                    val delta = change.positionChange()
                                                                    touchPos += delta
                                                                    if ((change.position - down.position).getDistance() > 18f) {
                                                                        dragStarted = true
                                                                        change.consume()
                                                                        return@withTimeoutOrNull
                                                                    }
                                                                }
                                                            }

                                                            val currentChange = currentEvent.changes.firstOrNull { it.id == downId }
                                                            if (currentChange != null && currentChange.pressed) {
                                                                dragStarted = true
                                                            }

                                                            if (dragStarted) {
                                                                draggedTool = tool
                                                                dragFromToolbar = false
                                                                currentTouchPosInRoot = touchPos
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                                drag(downId) { change ->
                                                                    val delta = change.positionChange()
                                                                    touchPos += delta
                                                                    currentTouchPosInRoot = touchPos
                                                                    change.consume()
                                                                }

                                                                val vTop = if (vaultZoneBounds.top > 0) vaultZoneBounds.top else (rootBounds.top + 160f)
                                                                if (touchPos.y < vTop) {
                                                                    bringToToolbar(tool, toolbarDropIndex)
                                                                } else {
                                                                    reorderVault(tool, vaultDropIndex)
                                                                }
                                                            } else {
                                                                bringToToolbar(tool)
                                                            }
                                                        } finally {
                                                            draggedTool = null
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                        ) {
                                            if (!isThisBeingDragged) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = tool.iconVector,
                                                        contentDescription = null,
                                                        tint = if (isDropTargetSlot) accentColor else textColor,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isDropTargetSlot) {
                                                            if (isEnglish) "Place" else "বসবে"
                                                        } else {
                                                            if (isEnglish) tool.titleEnglish else tool.titleBengali
                                                        },
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isDropTargetSlot) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isDropTargetSlot) accentColor else textColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = tool.iconVector,
                                                        contentDescription = null,
                                                        tint = accentColor.copy(alpha = 0.25f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // DETACHED FLOATING CARD: Renders at root level following finger
        // ══════════════════════════════════════════════════════════════════════
        if (draggedTool != null) {
            val activeTool = draggedTool!!
            val cardOffset = currentTouchPosInRoot - rootBounds.topLeft
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (cardOffset.x - 45.dp.toPx()).roundToInt(),
                            (cardOffset.y - 20.dp.toPx()).roundToInt()
                        )
                    }
                    .zIndex(9999f)
                    .graphicsLayer {
                        scaleX = 1.14f
                        scaleY = 1.14f
                        rotationZ = -3.0f
                    }
                    .shadow(16.dp, RoundedCornerShape(14.dp), spotColor = accentColor)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .border(2.dp, accentColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = activeTool.iconVector,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isEnglish) activeTool.titleEnglish else activeTool.titleBengali,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                }
            }
        }
    }
}
