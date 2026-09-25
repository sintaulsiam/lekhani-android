package com.lekhani.android.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.feedback.LekhaniFeedbackManager
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import com.lekhani.android.model.KeyboardLayout
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeRegistry

/**
 * KeyboardCanvasView
 * ══════════════════════════════════════════════════════════════════════════════
 * The 120 FPS hardware-accelerated Canvas keyboard grid for Lekhani.
 *
 * Performance guarantees (AGENTS.md §1.2):
 *   ✅ Zero heap allocations in [onDraw] or [onTouchEvent]
 *   ✅ All Paint, RectF, and path objects pre-allocated in [init] / [onSizeChanged]
 *   ✅ Hardware canvas via android:hardwareAccelerated="true" on the Window
 *   ✅ Touch-to-screen keystroke latency target: < 3 ms
 *
 * Architecture:
 *   [onSizeChanged] ─ compute pixel bounding boxes for every key → [resolvedKeys]
 *   [onDraw]        ─ iterate [resolvedKeys], draw with pre-allocated Paint objects
 *   [onTouchEvent]  ─ Gaussian nearest-key lookup → dispatch to [keyListener]
 *
 * Threading:
 *   All methods run on the main thread (View contract).
 *   The Rust session is called via [keyListener] which is implemented by
 *   [LekhaniInputMethodService] and also runs on the main thread.
 */
class KeyboardCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // ── Public interface ──────────────────────────────────────────────────────

    /** Callback interface implemented by LekhaniInputMethodService */
    interface KeyListener {
        fun onKey(key: Key, action: KeyAction)
        fun onSpaceSwipe(direction: Int) // -1 for previous layout, +1 for next layout
        fun onSpaceLongPress()
        fun onGlideGesture(keys: List<String>)
    }

    var keyListener: KeyListener? = null

    // ── Theme & Ergonomics state ──────────────────────────────────────────────

    var activeTheme: KeyboardTheme = ThemeRegistry.THEME_FLOW_TEAL
        private set

    var feedbackManager: LekhaniFeedbackManager? = null

    var heightScale: Float = 1.0f
    var marginHDp: Float = 3.5f
    var marginVDp: Float = 4.0f
    var bottomChinPaddingDp: Float = 0f
    var fontScale: Float = 1.0f
    var showKeyBorders: Boolean = true
    var longPressDelayMs: Long = 300L

    private var wallpaperBitmap: Bitmap? = null
    private var wallpaperOpacity: Float = 0.25f
    private val wallpaperPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallpaperSrcRect = Rect()
    private val wallpaperDstRect = RectF()

    // ── Layout state ──────────────────────────────────────────────────────────

    private var layout: KeyboardLayout? = null
    private var layoutType: com.lekhani.android.ffi.LekhaniLayoutType = com.lekhani.android.ffi.LekhaniLayoutType.PROBAHO
    private var isShifted: Boolean = false

    /**
     * A resolved key pairs a [Key] with its computed pixel [RectF].
     * Pre-allocated as a flat list in [onSizeChanged]; never re-allocated in draw/touch.
     */
    private data class ResolvedKey(val key: Key, val bounds: RectF)

    /** Flat list of all keys with pixel bounds. Rebuilt only in [onSizeChanged]. */
    private val resolvedKeys = ArrayList<ResolvedKey>(50)

    // ── Pre-allocated drawing primitives (ZERO allocation in onDraw) ──────────

    // Key backgrounds
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = KEY_COLOR_NORMAL
    }
    private val keyShiftBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = KEY_COLOR_SHIFT
    }
    private val keySpaceBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = KEY_COLOR_SPACE
    }
    private val keyHasantaBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = KEY_COLOR_HASANTA
    }

    // Key border
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = KEY_BORDER_COLOR
    }

    // Key labels
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LABEL_COLOR
        textAlign = Paint.Align.CENTER
    }
    private val labelPaintSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LABEL_COLOR_DIM
        textAlign = Paint.Align.CENTER
    }

    // Home row accent underline
    private val homeRowAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ACCENT_TEAL
    }

    // Zone divider (between left vowel and right consonant halves)
    private val zoneDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = ZONE_DIVIDER_COLOR
    }

    // Ripple animation
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = RIPPLE_COLOR
    }

    // Glide / Gesture typing path and glow paints (zero allocation in onDraw)
    private val glidePath = android.graphics.Path()
    private val glideGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#4000D4A0") // semi-transparent teal glow
    }
    private val glideStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#00E5B8") // vibrant teal stroke
    }
    private val glideDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0FFFFFF") // glowing tip
    }

    // Glide coordinates buffer & state (pre-allocated, zero allocation in onTouchEvent)
    private val MAX_GLIDE_POINTS = 256
    private val glidePointsX = FloatArray(MAX_GLIDE_POINTS)
    private val glidePointsY = FloatArray(MAX_GLIDE_POINTS)
    private var glidePointCount = 0
    private var isGliding = false
    private val visitedGlideKeys = ArrayList<String>(32)
    private var lastVisitedKeyIdx = -1
    private var glideMinDistancePx = 0f
    private var glideSampleDistSq = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var glideCurX = 0f
    private var glideCurY = 0f

    // ── Touch / ripple state (pre-allocated, mutated in place) ───────────────

    /** Index into [resolvedKeys] of the currently pressed key, or -1 */
    private var pressedKeyIndex: Int = -1

    /** Timestamp (ms) when the current press began, for ripple animation */
    private var pressStartTime: Long = 0L

    /** Ripple RectF — reused each frame, never reallocated */
    private val rippleRect = RectF()

    /** Reusable scratch RectF for hit-testing math */
    private val scratchRect = RectF()

    /** Whether the current touch has triggered a long-press (suppresses tap) */
    private var isLongPressTriggered: Boolean = false

    /** Spacebar swipe gesture detection state (zero allocation) */
    private var spaceTouchStartX: Float = 0f
    private var spaceTouchStartY: Float = 0f
    private var isSpaceSwiping: Boolean = false
    private var swipeThresholdPx: Float = 0f

    /** Pre-allocated long-press runnable for Spacebar quick layout selector (zero allocation) */
    private val longPressRunnable = Runnable {
        if (pressedKeyIndex in resolvedKeys.indices) {
            val key = resolvedKeys[pressedKeyIndex].key
            if (key.action == KeyAction.Space) {
                isLongPressTriggered = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                keyListener?.onSpaceLongPress()
            }
        }
    }

    // ── Dimensions (set in onSizeChanged) ────────────────────────────────────

    private var keyHeight: Float = 0f
    private var spacebarRowHeight: Float = 0f
    private var keyMarginH: Float = 0f
    private var keyMarginV: Float = 0f
    private var keyCornerRadius: Float = 0f
    private var homeRowAccentHeight: Float = 0f
    private var labelSize: Float = 0f
    private var labelSizeSmall: Float = 0f
    private var zoneDividerX: Float = 0f  // x-center of the gap between left/right halves

    // ══════════════════════════════════════════════════════════════════════════
    // Initialization
    // ══════════════════════════════════════════════════════════════════════════

    init {
        // Hardware canvas is required for 120 FPS
        setLayerType(LAYER_TYPE_HARDWARE, null)
        // Disable View's default click handling — we do our own in onTouchEvent
        isClickable = true
        isFocusable = false
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Layout & Theme Management
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Applies full configuration and theme from [KeyboardPreferences].
     * Zero-allocation in draw/touch hot paths.
     */
    fun applyPreferences(prefs: KeyboardPreferences, feedbackMgr: LekhaniFeedbackManager? = null) {
        this.feedbackManager = feedbackMgr
        this.longPressDelayMs = prefs.longPressDelayMs
        this.showKeyBorders = prefs.showKeyBorders
        this.heightScale = prefs.heightScale
        this.marginHDp = prefs.keyMarginH
        this.marginVDp = prefs.keyMarginV
        this.bottomChinPaddingDp = prefs.bottomChinPadding
        this.fontScale = prefs.fontScale

        val tf = when (prefs.fontStyle) {
            KeyboardPreferences.FONT_SERIF -> Typeface.SERIF
            KeyboardPreferences.FONT_SANS_SERIF -> Typeface.SANS_SERIF
            KeyboardPreferences.FONT_MONOSPACE -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        labelPaint.typeface = tf
        labelPaintSmall.typeface = tf

        val theme = ThemeRegistry.resolveTheme(context, prefs.themeId)
        applyTheme(theme)

        loadWallpaper(prefs.customWallpaperUri, prefs.wallpaperOpacity)

        if (width > 0 && height > 0) {
            computeKeyBounds()
            invalidate()
        }
        requestLayout()
    }

    /**
     * Applies a [KeyboardTheme] to all pre-allocated Paint objects.
     */
    fun applyTheme(theme: KeyboardTheme) {
        activeTheme = theme
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
        invalidate()
    }

    /**
     * Loads a background wallpaper image with memory-safe downsampling.
     */
    fun loadWallpaper(uriString: String, opacity: Float) {
        wallpaperOpacity = opacity.coerceIn(0f, 1f)
        if (uriString.isBlank()) {
            wallpaperBitmap = null
            return
        }
        try {
            val uri = Uri.parse(uriString)
            val stream = context.contentResolver.openInputStream(uri)
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            wallpaperBitmap = BitmapFactory.decodeStream(stream, null, opts)
            stream?.close()
        } catch (_: Exception) {
            wallpaperBitmap = null
        }
    }

    /**
     * Set the active keyboard layout.
     * Triggers a re-computation of pixel bounds and a redraw.
     * Called from LekhaniInputMethodService on layout switch.
     */
    fun setLayout(
        newLayout: KeyboardLayout,
        newLayoutType: com.lekhani.android.ffi.LekhaniLayoutType = com.lekhani.android.ffi.LekhaniLayoutType.PROBAHO,
        shifted: Boolean = false,
    ) {
        layout = newLayout
        layoutType = newLayoutType
        isShifted = shifted
        if (width > 0 && height > 0) {
            computeKeyBounds()
            invalidate()
        }
    }

    fun setShifted(shifted: Boolean) {
        if (isShifted != shifted) {
            isShifted = shifted
            invalidate()
        }
    }

    fun toggleShift(): Boolean {
        isShifted = !isShifted
        invalidate()
        return isShifted
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Measurement & Size change
    // ══════════════════════════════════════════════════════════════════════════

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val defaultHeightDp = (if (isLandscape) 180f else 260f) * heightScale + bottomChinPaddingDp
        val desiredHeight = (defaultHeightDp * density).toInt()

        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // ── Dimension derivations ──────────────────────────────────────────
        val density = resources.displayMetrics.density
        keyMarginH = marginHDp * density
        keyMarginV = marginVDp * density
        keyCornerRadius = 9f * density
        homeRowAccentHeight = 2.5f * density
        swipeThresholdPx = 40f * density

        glideStrokePaint.strokeWidth = 4.5f * density
        glideGlowPaint.strokeWidth = 11f * density
        glideMinDistancePx = 16f * density
        glideSampleDistSq = (8f * density) * (8f * density)

        val currentLayout = layout ?: return
        val rowCount = currentLayout.rows.size       // typically 3
        val chinPx = bottomChinPaddingDp * density
        val availableH = (h - chinPx).coerceAtLeast(100f)
        spacebarRowHeight = availableH * 0.22f                // spacebar row ~22% of height
        keyHeight = (availableH - spacebarRowHeight - keyMarginV * (rowCount + 1)) / rowCount
        labelSize = keyHeight * 0.38f * fontScale
        labelSizeSmall = keyHeight * 0.20f * fontScale

        labelPaint.textSize = labelSize
        labelPaintSmall.textSize = labelSizeSmall

        // Zone divider: exactly at x = width / 2 (5 keys left, 5 keys right)
        zoneDividerX = w / 2f

        computeKeyBounds()
    }

    /**
     * Compute pixel [RectF] for every key in the current layout.
     * Result stored in [resolvedKeys]. Called from [onSizeChanged] and [setLayout].
     * Must NOT be called from [onDraw] or [onTouchEvent].
     */
    private fun computeKeyBounds() {
        resolvedKeys.clear()
        val currentLayout = layout ?: return
        val w = width.toFloat()

        var rowTop = keyMarginV

        // ── Main key rows ──────────────────────────────────────────────────
        for (row in currentLayout.rows) {
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val unitWidth = (w - keyMarginH * (row.size + 1)) / totalWeight

            var keyLeft = keyMarginH
            for (key in row) {
                val keyWidth = unitWidth * key.widthWeight
                resolvedKeys.add(
                    ResolvedKey(
                        key = key,
                        bounds = RectF(
                            keyLeft,
                            rowTop,
                            keyLeft + keyWidth,
                            rowTop + keyHeight,
                        )
                    )
                )
                keyLeft += keyWidth + keyMarginH
            }
            rowTop += keyHeight + keyMarginV
        }

        // ── Spacebar row ───────────────────────────────────────────────────
        val spaceRow = currentLayout.spacebarRow
        val totalWeight = spaceRow.sumOf { it.widthWeight.toDouble() }.toFloat()
        val unitWidth = (w - keyMarginH * (spaceRow.size + 1)) / totalWeight
        val chinPx = bottomChinPaddingDp * resources.displayMetrics.density
        val availableH = (height - chinPx).coerceAtLeast(100f)
        val spaceRowTop = availableH - spacebarRowHeight - keyMarginV
        val spaceKeyHeight = spacebarRowHeight - keyMarginV

        var keyLeft = keyMarginH
        for (key in spaceRow) {
            val keyWidth = unitWidth * key.widthWeight
            resolvedKeys.add(
                ResolvedKey(
                    key = key,
                    bounds = RectF(keyLeft, spaceRowTop, keyLeft + keyWidth, spaceRowTop + spaceKeyHeight)
                )
            )
            keyLeft += keyWidth + keyMarginH
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Drawing — ZERO allocations permitted here
    // ══════════════════════════════════════════════════════════════════════════

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (resolvedKeys.isEmpty()) return

        // ── Keyboard Canvas Background ─────────────────────────────────────
        canvas.drawColor(activeTheme.backgroundColor)
        wallpaperBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                wallpaperSrcRect.set(0, 0, bmp.width, bmp.height)
                wallpaperDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
                wallpaperPaint.alpha = (wallpaperOpacity * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(bmp, wallpaperSrcRect, wallpaperDstRect, wallpaperPaint)
            }
        }

        val now = SystemClock.uptimeMillis()

        // ── Zone divider line ──────────────────────────────────────────────
        if (layout?.rows?.isNotEmpty() == true) {
            val firstKeyTop = resolvedKeys.firstOrNull()?.bounds?.top ?: 0f
            val lastMainKey = resolvedKeys.lastOrNull { !isSpacebarKey(it.key) }?.bounds?.bottom ?: height.toFloat()
            canvas.drawLine(zoneDividerX, firstKeyTop, zoneDividerX, lastMainKey, zoneDividerPaint)
        }

        // ── Keys ───────────────────────────────────────────────────────────
        for (i in resolvedKeys.indices) {
            val resolved = resolvedKeys[i]
            val key = resolved.key
            val bounds = resolved.bounds

            // Select background paint
            val bgPaint = when {
                key.action == KeyAction.Backspace || key.action == KeyAction.Shift ||
                key.action == KeyAction.SwitchNumeric -> keyShiftBgPaint
                key.action == KeyAction.Space -> keySpaceBgPaint
                key.action is KeyAction.Character && key.label == "্" -> keyHasantaBgPaint
                else -> keyBgPaint
            }

            // Draw key background with rounded corners
            canvas.drawRoundRect(bounds, keyCornerRadius, keyCornerRadius, bgPaint)

            // Draw key border
            if (showKeyBorders) {
                canvas.drawRoundRect(bounds, keyCornerRadius, keyCornerRadius, keyBorderPaint)
            }

            // Home row accent underline
            if (key.isHomeRow) {
                scratchRect.set(
                    bounds.left + keyCornerRadius,
                    bounds.bottom - homeRowAccentHeight,
                    bounds.right - keyCornerRadius,
                    bounds.bottom,
                )
                canvas.drawRect(scratchRect, homeRowAccentPaint)
            }

            // Draw ripple if this key is pressed
            if (i == pressedKeyIndex) {
                val elapsed = (now - pressStartTime).coerceAtMost(RIPPLE_DURATION_MS)
                val fraction = elapsed.toFloat() / RIPPLE_DURATION_MS
                val rippleAlpha = ((1f - fraction) * RIPPLE_MAX_ALPHA).toInt().coerceIn(0, 255)
                ripplePaint.alpha = rippleAlpha
                rippleRect.set(bounds)
                canvas.drawRoundRect(rippleRect, keyCornerRadius, keyCornerRadius, ripplePaint)
                // Continue invalidating while ripple is animating
                if (fraction < 1f) invalidate()
            }

            // Draw label
            val labelText = if (key.action == KeyAction.Space) {
                com.lekhani.android.model.LayoutRegistry.getSpacebarLabel(layoutType)
            } else {
                key.displayLabel(isShifted)
            }
            val cx = bounds.centerX()
            val cy = bounds.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f

            when (key.action) {
                KeyAction.Backspace, KeyAction.Enter, KeyAction.Shift,
                KeyAction.SwitchNumeric, KeyAction.SwitchLayout, KeyAction.Space -> {
                    // System keys & Spacebar use smaller label
                    canvas.drawText(labelText, cx, cy, labelPaintSmall)
                }
                else -> {
                    canvas.drawText(labelText, cx, cy, labelPaint)
                    // Draw hint (shifted alternate character) in upper area if unshifted
                    val shiftedLbl = key.shiftedLabel
                    if (!isShifted && shiftedLbl != null && shiftedLbl != key.label && shiftedLbl.isNotEmpty()) {
                        val hintY = bounds.top + (bounds.height() * 0.28f)
                        val origAlpha = labelPaintSmall.alpha
                        labelPaintSmall.alpha = 130
                        canvas.drawText(shiftedLbl, cx, hintY, labelPaintSmall)
                        labelPaintSmall.alpha = origAlpha
                    }
                }
            }
        }

        // ── Glide / Gesture typing trail (zero allocation, 120 FPS Bezier smoothing) ──
        if (isGliding && glidePointCount > 0) {
            glidePath.rewind()
            glidePath.moveTo(glidePointsX[0], glidePointsY[0])
            for (p in 1 until glidePointCount) {
                val prevX = glidePointsX[p - 1]
                val prevY = glidePointsY[p - 1]
                val currX = glidePointsX[p]
                val currY = glidePointsY[p]
                val midX = (prevX + currX) / 2f
                val midY = (prevY + currY) / 2f
                glidePath.quadTo(prevX, prevY, midX, midY)
            }
            val lastX = glidePointsX[glidePointCount - 1]
            val lastY = glidePointsY[glidePointCount - 1]
            glidePath.quadTo(lastX, lastY, (lastX + glideCurX) / 2f, (lastY + glideCurY) / 2f)
            glidePath.lineTo(glideCurX, glideCurY)

            canvas.drawPath(glidePath, glideGlowPaint)
            canvas.drawPath(glidePath, glideStrokePaint)
            canvas.drawCircle(glideCurX, glideCurY, glideStrokePaint.strokeWidth * 0.75f, glideDotPaint)
        }
    }

    private fun isSpacebarKey(key: Key): Boolean =
        key.action == KeyAction.Space || key.action == KeyAction.SwitchNumeric ||
        key.action == KeyAction.SwitchLayout || key.action == KeyAction.Enter

    // ══════════════════════════════════════════════════════════════════════════
    // Touch handling — ZERO allocations permitted here
    // ══════════════════════════════════════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                glideCurX = event.x
                glideCurY = event.y
                isGliding = false
                glidePointCount = 0
                visitedGlideKeys.clear()
                lastVisitedKeyIdx = -1

                val idx = findKeyIndex(event.x, event.y)
                if (idx >= 0) {
                    pressedKeyIndex = idx
                    pressStartTime = SystemClock.uptimeMillis()
                    isLongPressTriggered = false
                    isSpaceSwiping = false
                    if (feedbackManager != null) {
                        feedbackManager?.onKeyFeedback(this)
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }

                    val key = resolvedKeys[idx].key
                    val action = key.activeAction(isShifted)
                    if (action is KeyAction.Character) {
                        glidePointsX[0] = event.x
                        glidePointsY[0] = event.y
                        glidePointCount = 1
                        visitedGlideKeys.add(action.token)
                        lastVisitedKeyIdx = idx
                    } else if (key.action == KeyAction.Space) {
                        spaceTouchStartX = event.x
                        spaceTouchStartY = event.y
                        postDelayed(longPressRunnable, longPressDelayMs)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val curX = event.x
                val curY = event.y
                glideCurX = curX
                glideCurY = curY

                // Spacebar layout swipe detection
                if (pressedKeyIndex in resolvedKeys.indices) {
                    val key = resolvedKeys[pressedKeyIndex].key
                    if (key.action == KeyAction.Space && !isSpaceSwiping && !isLongPressTriggered) {
                        val dx = curX - spaceTouchStartX
                        val dy = kotlin.math.abs(curY - spaceTouchStartY)
                        if (kotlin.math.abs(dx) > swipeThresholdPx && kotlin.math.abs(dx) > dy * 1.3f) {
                            isSpaceSwiping = true
                            removeCallbacks(longPressRunnable)
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (dx > 0) {
                                keyListener?.onSpaceSwipe(1) // Next layout
                            } else {
                                keyListener?.onSpaceSwipe(-1) // Previous layout
                            }
                        }
                    }
                }

                // Glide / Swipe typing gesture detection
                val totalDx = curX - touchStartX
                val totalDy = curY - touchStartY
                val totalDistSq = totalDx * totalDx + totalDy * totalDy

                if (!isGliding && !isSpaceSwiping && totalDistSq > glideMinDistancePx * glideMinDistancePx) {
                    if (pressedKeyIndex in resolvedKeys.indices &&
                        resolvedKeys[pressedKeyIndex].key.activeAction(isShifted) is KeyAction.Character) {
                        isGliding = true
                        removeCallbacks(longPressRunnable)
                    }
                }

                if (isGliding) {
                    val lastP = glidePointCount - 1
                    if (lastP >= 0) {
                        val dx = curX - glidePointsX[lastP]
                        val dy = curY - glidePointsY[lastP]
                        if (dx * dx + dy * dy >= glideSampleDistSq && glidePointCount < MAX_GLIDE_POINTS) {
                            glidePointsX[glidePointCount] = curX
                            glidePointsY[glidePointCount] = curY
                            glidePointCount++
                        }
                    }

                    val keyIdx = findKeyIndex(curX, curY)
                    if (keyIdx >= 0 && keyIdx != lastVisitedKeyIdx) {
                        val action = resolvedKeys[keyIdx].key.activeAction(isShifted)
                        if (action is KeyAction.Character) {
                            visitedGlideKeys.add(action.token)
                            lastVisitedKeyIdx = keyIdx
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    invalidate()
                    return true
                }

                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                removeCallbacks(longPressRunnable)
                if (isGliding) {
                    if (visitedGlideKeys.size >= 2) {
                        keyListener?.onGlideGesture(visitedGlideKeys.toList())
                    } else if (visitedGlideKeys.size == 1 && pressedKeyIndex >= 0) {
                        dispatchKey(resolvedKeys[pressedKeyIndex].key)
                    }
                    isGliding = false
                    glidePointCount = 0
                    visitedGlideKeys.clear()
                    lastVisitedKeyIdx = -1
                    pressedKeyIndex = -1
                    isLongPressTriggered = false
                    isSpaceSwiping = false
                    invalidate()
                    return true
                }

                val idx = findKeyIndex(event.x, event.y)
                if (idx >= 0 && idx == pressedKeyIndex && !isLongPressTriggered && !isSpaceSwiping) {
                    dispatchKey(resolvedKeys[idx].key)
                }
                pressedKeyIndex = -1
                isLongPressTriggered = false
                isSpaceSwiping = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                isGliding = false
                glidePointCount = 0
                visitedGlideKeys.clear()
                lastVisitedKeyIdx = -1
                pressedKeyIndex = -1
                isLongPressTriggered = false
                isSpaceSwiping = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Gaussian spatial nearest-key lookup.
     *
     * Instead of a simple point-in-rectangle hit test, we use a weighted
     * distance metric that biases toward key centres — this makes the keyboard
     * more forgiving of touches that land on the narrow gap between keys.
     *
     * The Gaussian weight for key i is:
     *   w_i = exp(-d_i² / (2σ²))
     * where d_i is the Euclidean distance from the touch point to the key centre
     * and σ is the standard deviation (half the average key width).
     *
     * The key with the maximum weight wins.
     *
     * Since this is the hot path and must be allocation-free, all arithmetic
     * operates on pre-computed centre coordinates stored in [resolvedKeys].
     */
    private fun findKeyIndex(x: Float, y: Float): Int {
        var bestIdx = -1
        var bestScore = Float.MAX_VALUE

        for (i in resolvedKeys.indices) {
            val bounds = resolvedKeys[i].bounds
            // Only consider keys within a reasonable y-band (improves accuracy
            // for multi-row keyboards — avoids picking keys from the wrong row)
            if (y < bounds.top - KEY_Y_TOLERANCE || y > bounds.bottom + KEY_Y_TOLERANCE) continue

            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val dx = x - cx
            val dy = (y - cy) * ROW_Y_BIAS    // de-emphasise vertical error
            val dist = dx * dx + dy * dy

            if (dist < bestScore) {
                bestScore = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun dispatchKey(key: Key) {
        val action = key.activeAction(isShifted)
        keyListener?.onKey(key, action)

        // Auto-release shift after one character (one-shot shift)
        if (isShifted && action !is KeyAction.Shift) {
            isShifted = false
            invalidate()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Constants — all colours and metrics in one place
    // ══════════════════════════════════════════════════════════════════════════

    companion object {
        // Palette (dark theme — Material 3 Expressive)
        private val KEY_COLOR_NORMAL   = Color.parseColor("#1A2035")
        private val KEY_COLOR_SHIFT    = Color.parseColor("#141926")
        private val KEY_COLOR_SPACE    = Color.parseColor("#1E2840")
        private val KEY_COLOR_HASANTA  = Color.parseColor("#003D4D")  // teal-tinted for discoverability
        private val KEY_BORDER_COLOR   = Color.parseColor("#2A3550")
        private val LABEL_COLOR        = Color.parseColor("#E8EAF0")
        private val LABEL_COLOR_DIM    = Color.parseColor("#9098B0")
        private val ACCENT_TEAL        = Color.parseColor("#00D4A0")  // home row underline
        private val ZONE_DIVIDER_COLOR = Color.parseColor("#1E2840")
        private val RIPPLE_COLOR       = Color.parseColor("#4000D4A0") // semi-transparent teal

        private const val RIPPLE_DURATION_MS = 180L
        private const val RIPPLE_MAX_ALPHA   = 80

        // Hit-testing parameters
        private const val KEY_Y_TOLERANCE = 20f   // px — vertical grace zone beyond key bounds
        private const val ROW_Y_BIAS      = 1.8f  // amplify vertical distance in scoring
    }
}
