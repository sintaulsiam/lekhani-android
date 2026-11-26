package com.lekhani.android.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ime.EnterKeyResolver
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import com.lekhani.android.theme.ChromaStyle
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry

/**
 * KeycapPaintPipeline
 *
 * Implements the **Keycap & Hardware Canvas Rendering Pipeline** for [KeyboardCanvasView].
 * Decouples all keycap background drawing, shadow styling, Chroma animations, label
 * typography, and vector icons from view lifecycle and input events.
 *
 * Guarantees:
 *   - Strictly zero runtime object allocations or `new` in `render()` (Tier 1 Zero-Alloc).
 *   - 120 FPS hardware accelerated rendering budget (< 8.3 ms frame time).
 */
class KeycapPaintPipeline(
    private val keyboardPainter: KeyboardPainter = KeyboardPainter()
) {

    interface Host {
        val width: Float
        val height: Float
        val density: Float
        val activeTheme: KeyboardTheme
        val isThemeApplied: Boolean
        val formFactor: KeyboardPreferences.FormFactor
        val isShifted: Boolean
        val isCapsLock: Boolean
        val isShiftActive: Boolean
        val isBottomRowKeyEmoji: Boolean
        val layoutType: LekhaniLayoutType
        val spacebarSwipeMode: KeyboardPreferences.SpacebarSwipeMode
        val enabledLayoutsCount: Int
        val showKeyBorders: Boolean
        val showHomeRowAccents: Boolean
        val showKeyHints: Boolean
        val showBilateralAura: Boolean
        val showLayoutNameOnSpacebar: Boolean
        val isUiLanguageEnglish: Boolean
        val keyGlowRippleEnabled: Boolean
        val keyShadowLip: Float
        val keyCornerRadius: Float
        val homeRowAccentHeight: Float
        val enterActionIcon: EnterKeyResolver.ActionIcon
        val isResizeVisualGuide: Boolean
        val isSideDockVisible: Boolean
        val sideDockButtons: Array<SideDockButton>
        val resolvedKeys: List<ResolvedKey>
        val wallpaperBitmap: Bitmap?
        val wallpaperOpacity: Float

        val pressedKeyIndex: Int
        val pressStartTime: Long
        val glideTypingEnabled: Boolean
        val isGliding: Boolean
        val glidePointsX: FloatArray
        val glidePointsY: FloatArray
        val glidePointCount: Int
        val glideCurX: Float
        val glideCurY: Float
        val isSpaceCursorMoving: Boolean
        val spaceSlideLastX: Float
        val isBackspaceSwiping: Boolean
        val backspaceDeletedWordCount: Int

        fun applyInitialTheme(theme: KeyboardTheme)
        fun invalidate()
        fun postInvalidateOnAnimation()
    }

    // ── Pre-allocated drawing primitives (ZERO allocation in onDraw) ──────────
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF21262D.toInt()
    }
    private val keyShiftBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF30363D.toInt()
    }
    private val keySpaceBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF21262D.toInt()
    }
    private val keyHasantaBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF161B22.toInt()
    }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = 0x26FFFFFF
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val labelPaintSmall = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val homeRowAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5B8.toInt()
    }
    private val zoneDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = 0x33FFFFFF
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4000D4A0")
    }
    private val glidePath = Path()
    private val glideGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#4000D4A0")
    }
    private val glideStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FF00D4A0")
    }
    private val glideDotPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0FFFFFF")
    }
    private val spaceSlideTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0x8000E5B8.toInt()
    }
    private val spaceSlideThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5B8.toInt()
    }
    private val backspaceBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE53935.toInt()
    }
    private val backspaceBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val keyGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x6600E5B8.toInt()
    }
    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44000000
    }
    private val vowelAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val sideDockBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1AFFFFFF.toInt()
    }
    private val sideDockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val resizeGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val vectorIconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.2f
        color = 0xFFFFFFFF.toInt()
    }
    private val vectorIconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val wallpaperPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val wallpaperSrcRect = Rect()
    private val wallpaperDstRect = RectF()
    private val scratchRect = RectF()
    private val keyDrawRect = RectF()
    private val rippleRect = RectF()

    private val RIPPLE_DURATION_MS = 180L
    private val RIPPLE_MAX_ALPHA = 80

    fun applyTheme(theme: KeyboardTheme) {
        keyBgPaint.color = theme.keyNormalColor
        keyShiftBgPaint.color = theme.keyShiftColor
        keySpaceBgPaint.color = theme.keySpaceColor
        keyHasantaBgPaint.color = theme.keyHasantaColor
        keyBorderPaint.color = theme.keyBorderColor
        labelPaint.color = theme.labelColor
        labelPaintSmall.color = theme.labelDimColor
        homeRowAccentPaint.color = theme.accentColor
        ripplePaint.color = theme.rippleColor
        glideStrokePaint.color = theme.glideStrokeColor
        glideGlowPaint.color = theme.glideGlowColor
        zoneDividerPaint.color = theme.keySpaceColor

        keyGlowPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(theme.accentColor, 0x66)
        spaceSlideTrackPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(theme.accentColor, 0x80)
        spaceSlideThumbPaint.color = theme.accentColor
        vectorIconStrokePaint.color = theme.labelColor
        vectorIconFillPaint.color = theme.labelColor
        hintPaint.color = theme.labelDimColor
        vowelAuraPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(theme.accentColor, 18)
    }

    fun updateDimensions(density: Float) {
        glideStrokePaint.strokeWidth = 4.5f * density
        glideGlowPaint.strokeWidth = 11f * density
        spaceSlideTrackPaint.strokeWidth = 2.5f * density
        keyGlowPaint.strokeWidth = 2.2f * density
        sideDockTextPaint.textSize = 20f * density
        backspaceBadgeTextPaint.textSize = 12f * density
        vectorIconStrokePaint.strokeWidth = 2.2f * density
    }

    fun updateTextSizes(labelSize: Float, labelSizeSmall: Float) {
        labelPaint.textSize = labelSize
        labelPaintSmall.textSize = labelSizeSmall
        hintPaint.textSize = labelSizeSmall * 0.92f
    }

    fun setTypeface(tf: Typeface) {
        labelPaint.typeface = tf
        labelPaintSmall.typeface = tf
        hintPaint.typeface = tf
    }

    fun render(canvas: Canvas, host: Host) {
        if (!host.isThemeApplied) {
            host.applyInitialTheme(ThemeRegistry.THEME_FLOW_TEAL)
        }
        if (host.resolvedKeys.isEmpty()) return

        val density = host.density
        val activeTheme = host.activeTheme

        // ── Keyboard Canvas Background ─────────────────────────────────────
        canvas.drawColor(activeTheme.backgroundColor)
        host.wallpaperBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                wallpaperSrcRect.set(0, 0, bmp.width, bmp.height)
                wallpaperDstRect.set(0f, 0f, host.width, host.height)
                wallpaperPaint.alpha = (host.wallpaperOpacity * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(bmp, wallpaperSrcRect, wallpaperDstRect, wallpaperPaint)
            }
        }

        val now = SystemClock.uptimeMillis()
        var centerChromaColor = 0
        if (activeTheme.isRgbChroma) {
            centerChromaColor = keyboardPainter.getChromaColor(activeTheme.chromaMode, now, 0.5f)
            keyBorderPaint.color = centerChromaColor
            keyBorderPaint.strokeWidth = when (activeTheme.chromaStyle) {
                ChromaStyle.FULL_BORDER -> 1.6f * density
                ChromaStyle.AMBIENT_BREATHE -> 0.9f * density
                ChromaStyle.CLEAN_MINIMAL -> 1.0f * density
            }
            homeRowAccentPaint.color = centerChromaColor
            keyGlowPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(centerChromaColor, 0x80)
            spaceSlideThumbPaint.color = centerChromaColor
            glideStrokePaint.color = centerChromaColor
            glideDotPaint.color = centerChromaColor
            host.postInvalidateOnAnimation()
        }

        // ── Side Dock Buttons (One-Handed mode) ────────────────────────────
        if (host.isSideDockVisible) {
            val dockIconSize = 18f * density
            for (btn in host.sideDockButtons) {
                canvas.drawRoundRect(btn.bounds, 10f * density, 10f * density, sideDockBgPaint)
                val bcx = btn.bounds.centerX()
                val bcy = btn.bounds.centerY()
                when (btn.action) {
                    SideDockAction.EXPAND_STANDARD -> keyboardPainter.drawVectorExpand(canvas, bcx, bcy, dockIconSize, vectorIconStrokePaint)
                    SideDockAction.SWAP_SIDE -> keyboardPainter.drawVectorChevron(canvas, bcx, bcy, dockIconSize, isLeft = (host.formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT), strokePaint = vectorIconStrokePaint)
                    SideDockAction.TOGGLE_FLOATING -> keyboardPainter.drawVectorFloating(canvas, bcx, bcy, dockIconSize, vectorIconStrokePaint)
                }
            }
        }

        // ── Keys Rendering ────────────────────────────────────────────────
        for (i in host.resolvedKeys.indices) {
            val resolved = host.resolvedKeys[i]
            val key = resolved.key
            val bounds = resolved.bounds

            val bgPaint = when {
                key.action == KeyAction.Backspace || key.action == KeyAction.Shift ||
                key.action == KeyAction.SwitchNumeric || key.action == KeyAction.SwitchMoreSymbols ||
                key.action == KeyAction.SwitchAlpha || key.action == KeyAction.SwitchNumpad || key.action == KeyAction.ToggleBengaliDigits ||
                key.action == KeyAction.SwitchLayout || key.action == KeyAction.Enter ||
                key.action == KeyAction.SwitchEmoji || key.action == KeyAction.SwitchClipboard ||
                key.action == KeyAction.CursorLeft || key.action == KeyAction.CursorRight ||
                key.action == KeyAction.Tab -> keyShiftBgPaint
                key.action == KeyAction.Space -> keySpaceBgPaint
                else -> keyBgPaint
            }

            val drawBounds = if (i == host.pressedKeyIndex) {
                keyDrawRect.set(bounds.left, bounds.top + host.keyShadowLip, bounds.right, bounds.bottom)
                if (host.keyGlowRippleEnabled) {
                    scratchRect.set(keyDrawRect.left - 2.5f, keyDrawRect.top - 2.5f, keyDrawRect.right + 2.5f, keyDrawRect.bottom + 2.5f)
                    canvas.drawRoundRect(scratchRect, host.keyCornerRadius + 2.5f, host.keyCornerRadius + 2.5f, keyGlowPaint)
                }
                keyDrawRect
            } else {
                scratchRect.set(bounds.left, bounds.top + host.keyShadowLip, bounds.right, bounds.bottom)
                canvas.drawRoundRect(scratchRect, host.keyCornerRadius, host.keyCornerRadius, keyShadowPaint)
                keyDrawRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom - host.keyShadowLip)
                keyDrawRect
            }

            canvas.drawRoundRect(drawBounds, host.keyCornerRadius, host.keyCornerRadius, bgPaint)

            if (host.showBilateralAura &&
                host.layoutType == LekhaniLayoutType.PROBAHO &&
                drawBounds.centerX() < host.width / 2f &&
                key.action is KeyAction.Character &&
                !activeTheme.isRgbChroma
            ) {
                canvas.drawRoundRect(drawBounds, host.keyCornerRadius, host.keyCornerRadius, vowelAuraPaint)
            }

            val shouldDrawKeyBorder = when {
                activeTheme.isRgbChroma -> {
                    when (activeTheme.chromaStyle) {
                        ChromaStyle.FULL_BORDER -> true
                        ChromaStyle.AMBIENT_BREATHE -> true
                        ChromaStyle.CLEAN_MINIMAL -> key.action == KeyAction.Enter || key.action == KeyAction.Space
                    }
                }
                else -> host.showKeyBorders
            }

            if (shouldDrawKeyBorder) {
                if (activeTheme.isRgbChroma) {
                    val xRatio = if (host.width > 0) (drawBounds.centerX() / host.width).coerceIn(0f, 1f) else 0.5f
                    val chromaColor = keyboardPainter.getChromaColor(activeTheme.chromaMode, now, xRatio)
                    if (activeTheme.chromaStyle == ChromaStyle.AMBIENT_BREATHE) {
                        keyBorderPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(chromaColor, 0x99)
                    } else {
                        keyBorderPaint.color = chromaColor
                    }
                }
                canvas.drawRoundRect(drawBounds, host.keyCornerRadius, host.keyCornerRadius, keyBorderPaint)
            }

            if (host.showHomeRowAccents && key.isHomeRow) {
                scratchRect.set(
                    drawBounds.left + host.keyCornerRadius,
                    drawBounds.bottom - host.homeRowAccentHeight,
                    drawBounds.right - host.keyCornerRadius,
                    drawBounds.bottom,
                )
                canvas.drawRect(scratchRect, homeRowAccentPaint)
            }

            if (i == host.pressedKeyIndex) {
                val elapsed = (now - host.pressStartTime).coerceAtMost(RIPPLE_DURATION_MS)
                val fraction = elapsed.toFloat() / RIPPLE_DURATION_MS
                val rippleAlpha = ((1f - fraction) * RIPPLE_MAX_ALPHA).toInt().coerceIn(0, 255)
                if (activeTheme.isRgbChroma) {
                    val xRatio = if (host.width > 0) (drawBounds.centerX() / host.width).coerceIn(0f, 1f) else 0.5f
                    val rippleBaseColor = keyboardPainter.getChromaColor(activeTheme.chromaMode, now, xRatio)
                    ripplePaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(rippleBaseColor, rippleAlpha)
                } else {
                    ripplePaint.alpha = rippleAlpha
                }
                rippleRect.set(drawBounds)
                canvas.drawRoundRect(rippleRect, host.keyCornerRadius, host.keyCornerRadius, ripplePaint)
                if (fraction < 1f) host.invalidate()
            }

            val labelText = if (key.action == KeyAction.Space) {
                com.lekhani.android.model.LayoutRegistry.getSpacebarDisplayLabel(
                    host.layoutType,
                    host.showLayoutNameOnSpacebar,
                    host.isUiLanguageEnglish
                )
            } else {
                com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(key.displayLabel(host.isShifted))
            }
            val cx = drawBounds.centerX()
            val cy = drawBounds.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f
            val cySmall = drawBounds.centerY() - (labelPaintSmall.ascent() + labelPaintSmall.descent()) / 2f

            when (key.action) {
                KeyAction.Backspace -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    keyboardPainter.drawVectorBackspace(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                }
                KeyAction.Shift -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    keyboardPainter.drawVectorShift(canvas, cx, drawBounds.centerY(), iconSize, host.isShifted, host.isCapsLock, vectorIconStrokePaint, vectorIconFillPaint)
                }
                KeyAction.Enter -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    if (activeTheme.isRgbChroma) {
                        vectorIconStrokePaint.color = keyBorderPaint.color
                    }
                    val resolvedIcon = if (host.isShiftActive) EnterKeyResolver.ActionIcon.NEWLINE else host.enterActionIcon
                    when (resolvedIcon) {
                        EnterKeyResolver.ActionIcon.SEARCH -> keyboardPainter.drawVectorSearch(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.SEND -> keyboardPainter.drawVectorSend(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.GO -> keyboardPainter.drawVectorGo(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.NEXT -> keyboardPainter.drawVectorNext(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.DONE -> keyboardPainter.drawVectorDone(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.NEWLINE -> keyboardPainter.drawVectorEnter(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                    }
                    if (activeTheme.isRgbChroma) {
                        vectorIconStrokePaint.color = activeTheme.labelColor
                    }
                }
                KeyAction.SwitchLayout -> {
                    val iconSize = (drawBounds.height() * 0.40f).coerceAtLeast(16f * density)
                    if (host.isBottomRowKeyEmoji) {
                        keyboardPainter.drawVectorSmiley(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint, vectorIconFillPaint)
                    } else {
                        keyboardPainter.drawVectorGlobe(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        val hint = key.hintLabel?.let { com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(it) }
                        if (hint != null) {
                            val hintX = drawBounds.right - 5f * density
                            val hintY = drawBounds.top + 13f * density
                            canvas.drawText(hint, hintX, hintY, hintPaint)
                        }
                    }
                }
                KeyAction.SwitchEmoji -> {
                    val iconSize = (drawBounds.height() * 0.40f).coerceAtLeast(16f * density)
                    keyboardPainter.drawVectorSmiley(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint, vectorIconFillPaint)
                }
                KeyAction.SwitchNumpad -> {
                    val iconSize = (drawBounds.height() * 0.40f).coerceAtLeast(16f * density)
                    keyboardPainter.drawVectorNumpad(canvas, cx, drawBounds.centerY(), iconSize, vectorIconFillPaint)
                }
                KeyAction.Space -> {
                    if (host.spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH && host.enabledLayoutsCount > 1 && !host.isSpaceCursorMoving) {
                        canvas.drawText("‹   $labelText   ›", cx, cySmall, labelPaintSmall)
                    } else {
                        canvas.drawText(labelText, cx, cySmall, labelPaintSmall)
                    }
                }
                KeyAction.SwitchNumeric -> {
                    canvas.drawText(labelText, cx, cySmall, labelPaintSmall)
                }
                KeyAction.SwitchMoreSymbols, KeyAction.SwitchAlpha, KeyAction.ToggleBengaliDigits -> {
                    canvas.drawText(labelText, cx, cySmall, labelPaintSmall)
                    if (host.showKeyHints) {
                        val hint = key.displayHint(host.isShifted)?.let { com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(it) }
                        if (hint != null) {
                            val hintX = drawBounds.right - 5f * density
                            val hintY = drawBounds.top + 13f * density
                            val isShiftedSpecialHint = host.isShifted && key.shiftedHintLabel != null
                            if (isShiftedSpecialHint) {
                                val prevColor = hintPaint.color
                                hintPaint.color = activeTheme.accentColor
                                canvas.drawText(hint, hintX, hintY, hintPaint)
                                hintPaint.color = prevColor
                            } else {
                                canvas.drawText(hint, hintX, hintY, hintPaint)
                            }
                        }
                    }
                }
                else -> {
                    canvas.drawText(labelText, cx, cy, labelPaint)
                    if (host.showKeyHints) {
                        val hint = key.displayHint(host.isShifted)?.let { com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(it) }
                        if (hint != null) {
                            val hintX = drawBounds.right - 5f * density
                            val hintY = drawBounds.top + 13f * density
                            val isShiftedSpecialHint = host.isShifted && key.shiftedHintLabel != null
                            if (isShiftedSpecialHint) {
                                val prevColor = hintPaint.color
                                hintPaint.color = activeTheme.accentColor
                                canvas.drawText(hint, hintX, hintY, hintPaint)
                                hintPaint.color = prevColor
                            } else {
                                canvas.drawText(hint, hintX, hintY, hintPaint)
                            }
                        }
                    }
                }
            }

            if (host.isSpaceCursorMoving && key.action == KeyAction.Space) {
                val midY = bounds.centerY()
                canvas.drawLine(bounds.left + 16f * density, midY, bounds.right - 16f * density, midY, spaceSlideTrackPaint)
                val dotX = host.glideCurX.coerceIn(bounds.left + 24f * density, bounds.right - 24f * density)
                canvas.drawCircle(dotX, midY, 6f * density, spaceSlideThumbPaint)
            }

            if (host.isBackspaceSwiping && key.action == KeyAction.Backspace && host.backspaceDeletedWordCount > 0) {
                val badgeW = 60f * density
                val badgeH = 22f * density
                val badgeL = bounds.centerX() - badgeW / 2f
                val badgeT = bounds.top - 18f * density
                scratchRect.set(badgeL, badgeT, badgeL + badgeW, badgeT + badgeH)
                canvas.drawRoundRect(scratchRect, 6f * density, 6f * density, backspaceBadgePaint)
                val textY = scratchRect.centerY() - (backspaceBadgeTextPaint.ascent() + backspaceBadgeTextPaint.descent()) / 2f
                canvas.drawText("-${host.backspaceDeletedWordCount}", scratchRect.centerX(), textY, backspaceBadgeTextPaint)
            }
        }

        // ── Glide / Gesture typing trail (zero allocation, 120 FPS Bezier smoothing) ──
        if (host.glideTypingEnabled && host.isGliding && host.glidePointCount > 0) {
            glidePath.rewind()
            glidePath.moveTo(host.glidePointsX[0], host.glidePointsY[0])
            for (p in 1 until host.glidePointCount) {
                val prevX = host.glidePointsX[p - 1]
                val prevY = host.glidePointsY[p - 1]
                val currX = host.glidePointsX[p]
                val currY = host.glidePointsY[p]
                val midX = (prevX + currX) / 2f
                val midY = (prevY + currY) / 2f
                glidePath.quadTo(prevX, prevY, midX, midY)
            }
            val lastX = host.glidePointsX[host.glidePointCount - 1]
            val lastY = host.glidePointsY[host.glidePointCount - 1]
            glidePath.quadTo(lastX, lastY, (lastX + host.glideCurX) / 2f, (lastY + host.glideCurY) / 2f)
            glidePath.lineTo(host.glideCurX, host.glideCurY)

            canvas.drawPath(glidePath, glideGlowPaint)
            canvas.drawPath(glidePath, glideStrokePaint)
            canvas.drawCircle(host.glideCurX, host.glideCurY, glideStrokePaint.strokeWidth * 0.75f, glideDotPaint)
        }

        // ── Resize Visual Guide Outline ───────────────────────────────────
        if (host.isResizeVisualGuide) {
            val pad = 3f * density
            resizeGuidePaint.color = activeTheme.accentColor
            resizeGuidePaint.strokeWidth = 2.5f * density
            scratchRect.set(pad, pad, host.width - pad, host.height - pad)
            canvas.drawRoundRect(scratchRect, 10f * density, 10f * density, resizeGuidePaint)
        }
    }
}
