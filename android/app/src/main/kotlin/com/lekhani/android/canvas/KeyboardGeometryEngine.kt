package com.lekhani.android.canvas

import android.graphics.RectF
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.ffi.KeyGeometryConfig
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ime.EnterKeyResolver
import com.lekhani.android.model.GboardBengaliLayout
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import com.lekhani.android.model.KeyboardLayout

enum class SideDockAction { EXPAND_STANDARD, SWAP_SIDE, TOGGLE_FLOATING }
class SideDockButton(var action: SideDockAction, val bounds: RectF = RectF())

/**
 * KeyboardGeometryEngine
 *
 * Implements the **Geometry Calculation Engine** for [KeyboardCanvasView].
 * Calculates precise key bounding boxes for standard, split, and one-handed dock layouts.
 */
class KeyboardGeometryEngine {

    data class Params(
        val layout: KeyboardLayout?,
        val layoutType: LekhaniLayoutType,
        val formFactor: KeyboardPreferences.FormFactor,
        val width: Float,
        val height: Float,
        val density: Float,
        val isLandscape: Boolean,
        val bottomChinPaddingDp: Float,
        val keyMarginH: Float,
        val keyMarginV: Float,
        val showDedicatedNumberRow: Boolean,
        val isNumberSymbolsActive: Boolean,
        val isNumpad: Boolean,
        val avroNumeralsBengali: Boolean,
        val nationalNumeralsBengali: Boolean = true,
        val englishDedicatedNumberRow: List<Key>,
        val bengaliDedicatedNumberRow: List<Key>,
        val isGboardKarsActive: Boolean,
        val gboardActiveConsonant: String,
        val gboardDynamicPhalaRow: Boolean = true,
        val fieldType: EnterKeyResolver.FieldType,
    )

    fun getAdaptiveSpaceRow(
        originalRow: List<Key>,
        fieldType: EnterKeyResolver.FieldType,
        isNumberSymbolsActive: Boolean,
    ): List<Key> {
        if (fieldType == EnterKeyResolver.FieldType.NORMAL || isNumberSymbolsActive) return originalRow

        val spaceIndex = originalRow.indexOfFirst { it.action == KeyAction.Space }
        if (spaceIndex < 0) return originalRow

        return originalRow.mapIndexed { idx, key ->
            when (idx) {
                spaceIndex - 1 -> {
                    if (fieldType == EnterKeyResolver.FieldType.EMAIL) {
                        Key(
                            label = "@",
                            shiftedLabel = "@",
                            action = KeyAction.Character("@"),
                            shiftedAction = KeyAction.Character("@"),
                            widthWeight = key.widthWeight,
                            contentDesc = "At symbol",
                        )
                    } else {
                        Key(
                            label = "/",
                            shiftedLabel = "/",
                            action = KeyAction.Character("/"),
                            shiftedAction = KeyAction.Character("/"),
                            widthWeight = key.widthWeight,
                            contentDesc = "Forward slash",
                        )
                    }
                }
                spaceIndex + 1 -> {
                    if (key.action is KeyAction.Character && (key.action as KeyAction.Character).token == ".") {
                        key
                    } else if (key.action !is KeyAction.Enter) {
                        Key(
                            label = ".",
                            shiftedLabel = ".",
                            action = KeyAction.Character("."),
                            shiftedAction = KeyAction.Character("."),
                            widthWeight = key.widthWeight,
                            contentDesc = "Period",
                        )
                    } else {
                        key
                    }
                }
                else -> key
            }
        }
    }

    fun setupSideDockButtons(
        buttons: Array<SideDockButton>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        density: Float,
    ) {
        val btnH = (height / 3f).coerceAtLeast(30f)
        val pad = 4f * density

        buttons[0].action = SideDockAction.EXPAND_STANDARD
        buttons[0].bounds.set(left + pad, top + pad, left + width - pad, top + btnH - pad)

        buttons[1].action = SideDockAction.SWAP_SIDE
        buttons[1].bounds.set(left + pad, top + btnH + pad, left + width - pad, top + 2f * btnH - pad)

        buttons[2].action = SideDockAction.TOGGLE_FLOATING
        buttons[2].bounds.set(left + pad, top + 2f * btnH + pad, left + width - pad, top + 3f * btnH - pad)
    }

    fun computeKeyBounds(
        params: Params,
        resolvedKeys: ArrayList<ResolvedKey>,
        sideDockButtons: Array<SideDockButton>,
        onSideDockVisibilityChange: (Boolean) -> Unit,
        onGeometryCalculated: (List<KeyGeometryConfig>) -> Unit,
    ) {
        resolvedKeys.clear()
        val currentLayout = params.layout ?: return
        val w = params.width
        val h = params.height
        if (w <= 0f || h <= 0f) return

        val effectiveChinDp = if (params.isLandscape || params.formFactor == KeyboardPreferences.FormFactor.FLOATING) 0f else params.bottomChinPaddingDp
        val chinPx = effectiveChinDp * params.density
        val availableH = (h - chinPx).coerceAtLeast(100f)

        when (params.formFactor) {
            KeyboardPreferences.FormFactor.ONE_HANDED_LEFT -> {
                onSideDockVisibilityChange(true)
                val kbW = w * 0.82f
                val dockLeft = kbW
                val dockW = w - kbW
                setupSideDockButtons(sideDockButtons, dockLeft, 0f, dockW, availableH, params.density)
                layoutKeysStandard(params, 0f, kbW, availableH, resolvedKeys)
            }
            KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT -> {
                onSideDockVisibilityChange(true)
                val dockW = w * 0.18f
                val kbLeft = dockW
                val kbW = w - dockW
                setupSideDockButtons(sideDockButtons, 0f, 0f, dockW, availableH, params.density)
                layoutKeysStandard(params, kbLeft, kbW, availableH, resolvedKeys)
            }
            KeyboardPreferences.FormFactor.FLOATING,
            KeyboardPreferences.FormFactor.STANDARD -> {
                onSideDockVisibilityChange(false)
                layoutKeysStandard(params, 0f, w, availableH, resolvedKeys)
            }
            KeyboardPreferences.FormFactor.SPLIT -> {
                onSideDockVisibilityChange(false)
                layoutKeysSplit(params, w, availableH, resolvedKeys, onGeometryCalculated)
                return
            }
        }

        // Notify updated key geometries for spatial Gaussian touch correction
        val configs = resolvedKeys.mapNotNull { rk ->
            val charToken = (rk.key.action as? KeyAction.Character)?.token ?: rk.key.label
            if (charToken.isNotEmpty()) {
                KeyGeometryConfig(
                    label = charToken,
                    centerX = rk.bounds.centerX(),
                    centerY = rk.bounds.centerY(),
                    width = rk.bounds.width(),
                    height = rk.bounds.height(),
                )
            } else null
        }
        onGeometryCalculated(configs)
    }

    private fun layoutKeysStandard(
        params: Params,
        originX: Float,
        totalW: Float,
        totalH: Float,
        resolvedKeys: ArrayList<ResolvedKey>,
        yOffset: Float = 0f,
    ) {
        val currentLayout = params.layout ?: return
        val density = params.density

        val baseRows = currentLayout.rows.mapIndexed { rowIndex, originalRow ->
            if (params.layoutType == LekhaniLayoutType.GBOARD) {
                when (rowIndex) {
                    0 -> if (params.isGboardKarsActive) GboardBengaliLayout.getDynamicVowelsRow(params.gboardActiveConsonant) else originalRow
                    4 -> if (params.isGboardKarsActive && params.gboardDynamicPhalaRow) GboardBengaliLayout.getDynamicRow5(params.gboardActiveConsonant) else originalRow
                    else -> originalRow
                }
            } else {
                originalRow
            }
        }
        val allRows = if (params.showDedicatedNumberRow && !params.isNumberSymbolsActive) {
            val isEnglishDigits = when (params.layoutType) {
                LekhaniLayoutType.ENGLISH -> true
                LekhaniLayoutType.NATIONAL -> !params.nationalNumeralsBengali
                else -> !params.avroNumeralsBengali
            }
            val numRow = if (isEnglishDigits) params.englishDedicatedNumberRow else params.bengaliDedicatedNumberRow
            listOf(numRow) + baseRows
        } else {
            baseRows
        }

        val rowCount = allRows.size
        val totalRows = rowCount + 1
        val effectiveMarginV = if (totalH / totalRows < 40f * density) {
            (params.keyMarginV * 0.5f).coerceAtLeast(1.5f * density)
        } else {
            params.keyMarginV
        }
        val totalMarginsV = (totalRows + 1) * effectiveMarginV
        val kHeight = ((totalH - totalMarginsV) / totalRows).coerceAtLeast(18f * density)

        val sidePadding = (params.keyMarginH * 0.75f).coerceAtLeast(2f * density)
        val availableRowW = (totalW - 2f * sidePadding).coerceAtLeast(10f)

        val isNumpad = params.isNumpad
        val maxKeysInRow = if (isNumpad) {
            allRows.maxOfOrNull { it.size } ?: 3
        } else {
            allRows.maxOfOrNull { it.size }?.coerceAtLeast(10) ?: 10
        }
        val standardGaps = (maxKeysInRow - 1) * params.keyMarginH
        val standardUnitWidth = (availableRowW - standardGaps) / maxKeysInRow

        var currentRowTop = yOffset + effectiveMarginV
        for ((rowIndex, row) in allRows.withIndex()) {
            val hasShiftAtStart = row.isNotEmpty() && row.first().action == KeyAction.Shift
            val hasBackspaceAtEnd = row.isNotEmpty() && row.last().action == KeyAction.Backspace

            if (hasShiftAtStart && hasBackspaceAtEnd && row.size > 2) {
                val middleLetterCount = row.size - 2
                val rawShiftWeight = row.first().widthWeight
                val rawBackWeight = row.last().widthWeight
                val funcWeight = if (rawShiftWeight > 1.0f) rawShiftWeight.coerceIn(1.25f, 1.35f) else 1.32f
                val backWeight = if (rawBackWeight > 1.0f) rawBackWeight.coerceIn(1.25f, 1.35f) else 1.32f

                val rowWeight = funcWeight + backWeight + middleLetterCount * 1.0f
                val totalGaps = (row.size - 1) * params.keyMarginH
                val unitWidth = (availableRowW - totalGaps) / rowWeight
                val shiftKeyWidth = unitWidth * funcWeight
                val backKeyWidth = unitWidth * backWeight

                var keyLeft = originX + sidePadding
                for (i in row.indices) {
                    val key = row[i]
                    val keyWidth = when (i) {
                        0 -> shiftKeyWidth
                        row.lastIndex -> backKeyWidth
                        else -> unitWidth * key.widthWeight
                    }
                    resolvedKeys.add(
                        ResolvedKey(
                            key = key,
                            bounds = RectF(keyLeft, currentRowTop, keyLeft + keyWidth, currentRowTop + kHeight)
                        )
                    )
                    keyLeft += keyWidth + params.keyMarginH
                }
            } else {
                val rowWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
                val totalGaps = (row.size - 1) * params.keyMarginH
                val isStandardCharRow = row.all { it.widthWeight == 1.0f }
                val isDedicatedNumberRow = (params.showDedicatedNumberRow && !params.isNumberSymbolsActive && rowIndex == 0)

                val (rowSideInset, unitWidth) = when {
                    isDedicatedNumberRow -> {
                        val w = (availableRowW - totalGaps) / rowWeight
                        Pair(0f, w)
                    }
                    isNumpad -> {
                        val w = (availableRowW - totalGaps) / rowWeight
                        Pair(0f, w)
                    }
                    params.layoutType == LekhaniLayoutType.GBOARD -> {
                        val w = (availableRowW - totalGaps) / rowWeight
                        Pair(0f, w)
                    }
                    params.layoutType == LekhaniLayoutType.PROBHAT && row.size == 9 -> {
                        val halfKeyOffset = standardUnitWidth * 0.5f
                        val contentW = availableRowW - 2f * halfKeyOffset
                        val w = (contentW - totalGaps) / rowWeight
                        Pair(halfKeyOffset, w)
                    }
                    isStandardCharRow && row.size == maxKeysInRow - 1 -> {
                        val halfKeyOffset = standardUnitWidth * 0.5f
                        Pair(halfKeyOffset, standardUnitWidth)
                    }
                    (rowIndex == 1 || (params.showDedicatedNumberRow && rowIndex == 2)) && currentLayout.rows.size >= 3 -> {
                        val halfKeyOffset = standardUnitWidth * 0.35f
                        val contentW = availableRowW - 2f * halfKeyOffset
                        val w = (contentW - totalGaps) / rowWeight
                        Pair(halfKeyOffset, w)
                    }
                    isStandardCharRow && row.size < maxKeysInRow -> {
                        val contentW = row.size * standardUnitWidth + totalGaps
                        val inset = ((availableRowW - contentW) / 2f).coerceAtLeast(0f)
                        Pair(inset, standardUnitWidth)
                    }
                    else -> {
                        val w = (availableRowW - totalGaps) / rowWeight
                        Pair(0f, w)
                    }
                }

                var keyLeft = originX + sidePadding + rowSideInset
                for (key in row) {
                    val keyWidth = unitWidth * key.widthWeight
                    resolvedKeys.add(
                        ResolvedKey(
                            key = key,
                            bounds = RectF(keyLeft, currentRowTop, keyLeft + keyWidth, currentRowTop + kHeight)
                        )
                    )
                    keyLeft += keyWidth + params.keyMarginH
                }
            }
            currentRowTop += kHeight + effectiveMarginV
        }

        val spaceRow = getAdaptiveSpaceRow(currentLayout.spacebarRow, params.fieldType, params.isNumberSymbolsActive)
        val totalSpaceWeight = spaceRow.sumOf { it.widthWeight.toDouble() }.toFloat()
        val spaceGaps = (spaceRow.size - 1) * params.keyMarginH
        val spaceUnitWidth = (availableRowW - spaceGaps) / totalSpaceWeight
        val spaceRowTop = currentRowTop
        val spaceKeyHeight = kHeight

        var spaceKeyLeft = originX + sidePadding
        for (key in spaceRow) {
            val keyWidth = spaceUnitWidth * key.widthWeight
            resolvedKeys.add(
                ResolvedKey(
                    key = key,
                    bounds = RectF(spaceKeyLeft, spaceRowTop, spaceKeyLeft + keyWidth, spaceRowTop + spaceKeyHeight)
                )
            )
            spaceKeyLeft += keyWidth + params.keyMarginH
        }
    }

    private fun layoutKeysSplit(
        params: Params,
        totalW: Float,
        totalH: Float,
        resolvedKeys: ArrayList<ResolvedKey>,
        onGeometryCalculated: (List<KeyGeometryConfig>) -> Unit,
    ) {
        val currentLayout = params.layout ?: return
        val density = params.density

        val baseRows = currentLayout.rows.mapIndexed { rowIndex, originalRow ->
            if (params.layoutType == LekhaniLayoutType.GBOARD) {
                when (rowIndex) {
                    0 -> if (params.isGboardKarsActive) GboardBengaliLayout.getDynamicVowelsRow(params.gboardActiveConsonant) else originalRow
                    4 -> if (params.isGboardKarsActive && params.gboardDynamicPhalaRow) GboardBengaliLayout.getDynamicRow5(params.gboardActiveConsonant) else originalRow
                    else -> originalRow
                }
            } else {
                originalRow
            }
        }
        val allRows = if (params.showDedicatedNumberRow && !params.isNumberSymbolsActive) {
            val isEnglishDigits = when (params.layoutType) {
                LekhaniLayoutType.ENGLISH -> true
                LekhaniLayoutType.NATIONAL -> !params.nationalNumeralsBengali
                else -> !params.avroNumeralsBengali
            }
            val numRow = if (isEnglishDigits) params.englishDedicatedNumberRow else params.bengaliDedicatedNumberRow
            listOf(numRow) + baseRows
        } else {
            baseRows
        }

        val rowCount = allRows.size
        val totalRows = rowCount + 1
        val effectiveMarginV = if (totalH / totalRows < 40f * density) {
            (params.keyMarginV * 0.5f).coerceAtLeast(1.5f * density)
        } else {
            params.keyMarginV
        }
        val totalMarginsV = (totalRows + 1) * effectiveMarginV
        val kHeight = ((totalH - totalMarginsV) / totalRows).coerceAtLeast(18f * density)

        val centerGap = totalW * 0.14f
        val clusterW = (totalW - centerGap) / 2f
        val rightClusterOrigin = clusterW + centerGap

        var currentRowTop = effectiveMarginV
        for ((rowIndex, row) in allRows.withIndex()) {
            val halfCount = (row.size + 1) / 2
            val leftKeys = row.take(halfCount)
            val rightKeys = row.drop(halfCount)

            val leftWeight = leftKeys.sumOf { it.widthWeight.toDouble() }.toFloat()
            val leftUnitW = (clusterW - params.keyMarginH * (leftKeys.size + 1)) / leftWeight
            var keyLeft = params.keyMarginH
            for (key in leftKeys) {
                val keyW = leftUnitW * key.widthWeight
                resolvedKeys.add(ResolvedKey(key, RectF(keyLeft, currentRowTop, keyLeft + keyW, currentRowTop + kHeight)))
                keyLeft += keyW + params.keyMarginH
            }

            val rightWeight = rightKeys.sumOf { it.widthWeight.toDouble() }.toFloat()
            val rightUnitW = (clusterW - params.keyMarginH * (rightKeys.size + 1)) / rightWeight
            var rightKeyLeft = rightClusterOrigin + params.keyMarginH
            for (key in rightKeys) {
                val keyW = rightUnitW * key.widthWeight
                resolvedKeys.add(ResolvedKey(key, RectF(rightKeyLeft, currentRowTop, rightKeyLeft + keyW, currentRowTop + kHeight)))
                rightKeyLeft += keyW + params.keyMarginH
            }

            currentRowTop += kHeight + effectiveMarginV
        }

        val spaceRow = getAdaptiveSpaceRow(currentLayout.spacebarRow, params.fieldType, params.isNumberSymbolsActive)
        val spaceIndex = spaceRow.indexOfFirst { it.action == KeyAction.Space }
        val spaceKey = if (spaceIndex >= 0) spaceRow[spaceIndex] else Key(
            label = "Space", shiftedLabel = "Space",
            action = KeyAction.Space, shiftedAction = KeyAction.Space,
            widthWeight = 2.4f, contentDesc = "Space",
        )
        val leftSpaceKeys: List<Key>
        val rightSpaceKeys: List<Key>
        if (spaceIndex >= 0) {
            val leftSide = spaceRow.take(spaceIndex)
            val rightSide = spaceRow.drop(spaceIndex + 1)
            leftSpaceKeys = leftSide + spaceKey.copy(widthWeight = 2.4f)
            rightSpaceKeys = listOf(spaceKey.copy(widthWeight = 2.4f)) + rightSide
        } else {
            val halfCount = (spaceRow.size + 1) / 2
            leftSpaceKeys = spaceRow.take(halfCount)
            rightSpaceKeys = spaceRow.drop(halfCount)
        }
        val spaceRowTop = currentRowTop
        val spaceKeyHeight = kHeight

        val leftWeight = leftSpaceKeys.sumOf { it.widthWeight.toDouble() }.toFloat()
        val leftUnitW = (clusterW - params.keyMarginH * (leftSpaceKeys.size + 1)) / leftWeight
        var keyLeft = params.keyMarginH
        for (key in leftSpaceKeys) {
            val keyW = leftUnitW * key.widthWeight
            resolvedKeys.add(ResolvedKey(key, RectF(keyLeft, spaceRowTop, keyLeft + keyW, spaceRowTop + spaceKeyHeight)))
            keyLeft += keyW + params.keyMarginH
        }

        val rightWeight = rightSpaceKeys.sumOf { it.widthWeight.toDouble() }.toFloat()
        val rightUnitW = (clusterW - params.keyMarginH * (rightSpaceKeys.size + 1)) / rightWeight
        var rightKeyLeft = rightClusterOrigin + params.keyMarginH
        for (key in rightSpaceKeys) {
            val keyW = rightUnitW * key.widthWeight
            resolvedKeys.add(ResolvedKey(key, RectF(rightKeyLeft, spaceRowTop, rightKeyLeft + keyW, spaceRowTop + spaceKeyHeight)))
            rightKeyLeft += keyW + params.keyMarginH
        }

        val configs = resolvedKeys.mapNotNull { rk ->
            val charToken = (rk.key.action as? KeyAction.Character)?.token ?: rk.key.label
            if (charToken.isNotEmpty()) {
                KeyGeometryConfig(
                    label = charToken,
                    centerX = rk.bounds.centerX(),
                    centerY = rk.bounds.centerY(),
                    width = rk.bounds.width(),
                    height = rk.bounds.height(),
                )
            } else null
        }
        onGeometryCalculated(configs)
    }
}
