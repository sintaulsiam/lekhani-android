package com.lekhani.android.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
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
        fun onCursorMove(deltaChars: Int)
        fun onSwipeDelete(wordCount: Int)
        fun onSwipeDeletePreview(wordCount: Int)
        fun onFormFactorChange(newFormFactor: KeyboardPreferences.FormFactor)
    }

    var keyListener: KeyListener? = null

    // ── Theme, Ergonomics & Form Factor state ──────────────────────────────────

    var activeTheme: KeyboardTheme = ThemeRegistry.THEME_FLOW_TEAL
        private set

    var feedbackManager: LekhaniFeedbackManager? = null

    var heightScale: Float = 1.0f
    var marginHDp: Float = 3.0f
    var marginVDp: Float = 4.0f
    var bottomChinPaddingDp: Float = 0f
    var fontScale: Float = 1.0f
    var showKeyBorders: Boolean = false
    var showHomeRowAccents: Boolean = false
    var spacebarSwipeMode: KeyboardPreferences.SpacebarSwipeMode = KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV
    var longPressDelayMs: Long = 300L

    var formFactor: KeyboardPreferences.FormFactor = KeyboardPreferences.FormFactor.STANDARD
        set(value) {
            if (field != value) {
                field = value
                if (width > 0 && height > 0) {
                    computeKeyBounds()
                    invalidate()
                }
            }
        }

    var spaceCursorSlideEnabled: Boolean = true
    var swipeToDeleteEnabled: Boolean = true
    var keyGlowRippleEnabled: Boolean = true

    // Side Dock buttons for One-Handed mode (zero allocation in onDraw)
    enum class SideDockAction { EXPAND_STANDARD, SWAP_SIDE, TOGGLE_FLOATING }
    private class SideDockButton(var action: SideDockAction, val bounds: RectF = RectF())
    private val sideDockButtons = arrayOf(
        SideDockButton(SideDockAction.EXPAND_STANDARD),
        SideDockButton(SideDockAction.SWAP_SIDE),
        SideDockButton(SideDockAction.TOGGLE_FLOATING),
    )
    private var isSideDockVisible: Boolean = false
    private val sideDockBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1AFFFFFF.toInt()
    }
    private val sideDockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }

    // Pre-allocated vector icon paths and paints for zero-allocation vector drawing
    private val vectorIconPath = Path()
    private val vectorIconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val vectorIconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }

    // Floating mode drag state
    private var floatingOffsetX: Float = 0f
    private var floatingOffsetY: Float = 0f
    private var isDraggingFloatingBar: Boolean = false
    private var floatingDragStartX: Float = 0f
    private var floatingDragStartY: Float = 0f
    private val floatingTopBarRect = RectF()
    private val floatingDockBtnRect = RectF()

    // Spacebar cursor slide navigation state
    private var isSpaceCursorMoving: Boolean = false
    private var spaceSlideLastX: Float = 0f
    private var spaceSlideStepPx: Float = 0f
    private var spaceSlideThresholdPx: Float = 0f
    private val spaceSlideTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0x8000E5B8.toInt()
    }
    private val spaceSlideThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5B8.toInt()
    }

    // Backspace swipe to delete state
    private var isBackspaceSwiping: Boolean = false
    private var backspaceSwipeStartX: Float = 0f
    private var backspaceDeletedWordCount: Int = 0
    private var backspaceSwipeStepPx: Float = 0f
    private val backspaceBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE53935.toInt()
    }
    private val backspaceBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Key Glow paint (Material 3 Expressive press effect)
    private val keyGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x6600E5B8.toInt()
    }

    // Key tactile 3D shadow paint (Material 3 depth)
    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44000000
    }

    // Key Preview Bubble (Popup) pre-allocated state (zero allocation in onDraw)
    private val keyPopupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF243048.toInt()
    }
    private val keyPopupShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x4D000000
    }
    private val keyPopupStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x33FFFFFF.toInt()
    }
    private val keyPopupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val keyPopupRect = RectF()
    private val keyPopupShadowRect = RectF()
    private val keyDrawRect = RectF()

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

    /** Active pointer ID currently tracking [pressedKeyIndex] */
    private var pressedPointerId: Int = -1

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

    /** Pre-allocated long-press runnable for alternate hints and actions (zero allocation) */
    private val longPressRunnable = Runnable {
        if (pressedKeyIndex in resolvedKeys.indices) {
            val key = resolvedKeys[pressedKeyIndex].key
            isLongPressTriggered = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            when {
                key.action == KeyAction.Space -> {
                    keyListener?.onSpaceLongPress()
                }
                key.longPressAction != null -> {
                    keyListener?.onKey(key, key.longPressAction)
                }
                key.hintLabel != null -> {
                    keyListener?.onKey(key, KeyAction.Character(key.hintLabel))
                }
                key.shiftedLabel != null && key.shiftedLabel != key.label -> {
                    keyListener?.onKey(key, key.shiftedAction)
                }
            }
            invalidate()
        }
    }

    /** Pre-allocated runnable for continuous rapid Backspace deletion on hold (zero allocation) */
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (pressedKeyIndex in resolvedKeys.indices && !isBackspaceSwiping) {
                val key = resolvedKeys[pressedKeyIndex].key
                if (key.action == KeyAction.Backspace) {
                    isLongPressTriggered = true
                    feedbackManager?.onKeyFeedback(this@KeyboardCanvasView)
                        ?: performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    keyListener?.onKey(key, KeyAction.Backspace)
                    postDelayed(this, 50L)
                }
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

    // ── Dedicated Number Row Definitions (10 keys) ──────────────────────────
    var showDedicatedNumberRow: Boolean = false
        private set

    private val bengaliDedicatedNumberRow = listOf(
        Key("১", shiftedLabel = "1", hintLabel = "1", action = KeyAction.Character("১"), contentDesc = "Bengali digit 1"),
        Key("২", shiftedLabel = "2", hintLabel = "2", action = KeyAction.Character("২"), contentDesc = "Bengali digit 2"),
        Key("৩", shiftedLabel = "3", hintLabel = "3", action = KeyAction.Character("৩"), contentDesc = "Bengali digit 3"),
        Key("৪", shiftedLabel = "4", hintLabel = "4", action = KeyAction.Character("৪"), contentDesc = "Bengali digit 4"),
        Key("৫", shiftedLabel = "5", hintLabel = "5", action = KeyAction.Character("৫"), contentDesc = "Bengali digit 5"),
        Key("৬", shiftedLabel = "6", hintLabel = "6", action = KeyAction.Character("৬"), contentDesc = "Bengali digit 6"),
        Key("৭", shiftedLabel = "7", hintLabel = "7", action = KeyAction.Character("৭"), contentDesc = "Bengali digit 7"),
        Key("৮", shiftedLabel = "8", hintLabel = "8", action = KeyAction.Character("৮"), contentDesc = "Bengali digit 8"),
        Key("৯", shiftedLabel = "9", hintLabel = "9", action = KeyAction.Character("৯"), contentDesc = "Bengali digit 9"),
        Key("০", shiftedLabel = "0", hintLabel = "0", action = KeyAction.Character("০"), contentDesc = "Bengali digit 0"),
    )

    private val englishDedicatedNumberRow = listOf(
        Key("1", shiftedLabel = "১", hintLabel = "১", action = KeyAction.Character("1"), contentDesc = "Digit 1"),
        Key("2", shiftedLabel = "২", hintLabel = "২", action = KeyAction.Character("2"), contentDesc = "Digit 2"),
        Key("3", shiftedLabel = "৩", hintLabel = "৩", action = KeyAction.Character("3"), contentDesc = "Digit 3"),
        Key("4", shiftedLabel = "৪", hintLabel = "৪", action = KeyAction.Character("4"), contentDesc = "Digit 4"),
        Key("5", shiftedLabel = "৫", hintLabel = "৫", action = KeyAction.Character("5"), contentDesc = "Digit 5"),
        Key("6", shiftedLabel = "৬", hintLabel = "৬", action = KeyAction.Character("6"), contentDesc = "Digit 6"),
        Key("7", shiftedLabel = "৭", hintLabel = "৭", action = KeyAction.Character("7"), contentDesc = "Digit 7"),
        Key("8", shiftedLabel = "৮", hintLabel = "৮", action = KeyAction.Character("8"), contentDesc = "Digit 8"),
        Key("9", shiftedLabel = "৯", hintLabel = "৯", action = KeyAction.Character("9"), contentDesc = "Digit 9"),
        Key("0", shiftedLabel = "০", hintLabel = "০", action = KeyAction.Character("0"), contentDesc = "Digit 0"),
    )

    private fun isNumberSymbolsActive(): Boolean =
        layout == com.lekhani.android.model.NumberSymbolsLayout.numericLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.bengaliNumericLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.moreSymbolsLayout

    private val accessibilityHelper = KeyboardAccessibilityHelper()

    // ══════════════════════════════════════════════════════════════════════════
    // Initialization
    // ══════════════════════════════════════════════════════════════════════════

    init {
        // Hardware canvas is required for 120 FPS
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = false
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
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
        this.showDedicatedNumberRow = prefs.showDedicatedNumberRow
        this.heightScale = prefs.heightScale
        this.marginHDp = prefs.keyMarginH
        this.marginVDp = prefs.keyMarginV
        this.bottomChinPaddingDp = prefs.bottomChinPadding
        this.fontScale = prefs.fontScale
        this.formFactor = prefs.formFactor
        this.spacebarSwipeMode = prefs.spacebarSwipeMode
        this.spaceCursorSlideEnabled = (prefs.spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV)
        this.showHomeRowAccents = prefs.showHomeRowAccents
        this.swipeToDeleteEnabled = prefs.swipeToDeleteEnabled
        this.keyGlowRippleEnabled = prefs.keyGlowRippleEnabled

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

        keyGlowPaint.color = (theme.accentColor and 0x00FFFFFF) or 0x66000000.toInt()
        spaceSlideTrackPaint.color = (theme.accentColor and 0x00FFFFFF) or 0x80000000.toInt()
        spaceSlideThumbPaint.color = theme.accentColor
        vectorIconStrokePaint.color = theme.labelColor
        vectorIconFillPaint.color = theme.labelColor
        hintPaint.color = theme.labelDimColor

        keyPopupBgPaint.color = if (theme.isDark) {
            Color.rgb(
                (Color.red(theme.keyNormalColor) + 24).coerceAtMost(255),
                (Color.green(theme.keyNormalColor) + 28).coerceAtMost(255),
                (Color.blue(theme.keyNormalColor) + 38).coerceAtMost(255)
            )
        } else {
            0xFFFFFFFF.toInt()
        }
        keyPopupStrokePaint.color = if (theme.isDark) 0x33FFFFFF.toInt() else 0x1A000000
        keyPopupTextPaint.color = theme.labelColor
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
    var isGboardKarsActive: Boolean = false
        private set
    var gboardActiveConsonant: String = ""
        private set

    fun setGboardKarsActive(active: Boolean, consonant: String = "") {
        if (isGboardKarsActive != active || gboardActiveConsonant != consonant) {
            isGboardKarsActive = active
            gboardActiveConsonant = consonant
            if (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.GBOARD) {
                if (width > 0 && height > 0) {
                    computeKeyBounds()
                    invalidate()
                }
            }
        }
    }

    fun setLayout(
        newLayout: KeyboardLayout,
        newLayoutType: com.lekhani.android.ffi.LekhaniLayoutType = com.lekhani.android.ffi.LekhaniLayoutType.PROBAHO,
        shifted: Boolean = false,
    ) {
        layout = newLayout
        layoutType = newLayoutType
        isShifted = shifted
        isGboardKarsActive = false
        gboardActiveConsonant = ""
        accessibilityHelper.invalidateRoot()
        if (width > 0 && height > 0) {
            requestLayout()
            computeKeyBounds()
            invalidate()
        }
    }

    fun setShifted(shifted: Boolean) {
        if (isShifted != shifted) {
            isShifted = shifted
            accessibilityHelper.invalidateRoot()
            invalidate()
        }
    }

    fun toggleShift(): Boolean {
        isShifted = !isShifted
        accessibilityHelper.invalidateRoot()
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
        val hasExtraNumberRow = showDedicatedNumberRow && !isNumberSymbolsActive()
        val rowCount = (layout?.rows?.size ?: 3) + 1 + (if (hasExtraNumberRow) 1 else 0)
        val isSixRow = rowCount >= 6
        val baseHeightDp = if (isLandscape) {
            if (isSixRow) 160f else 135f
        } else {
            if (isSixRow) 330f else 280f
        }
        val defaultHeightDp = baseHeightDp * heightScale + bottomChinPaddingDp
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
        keyCornerRadius = 7.5f * density
        homeRowAccentHeight = 2.5f * density
        swipeThresholdPx = 36f * density
        spaceSlideThresholdPx = 16f * density
        spaceSlideStepPx = 16f * density
        backspaceSwipeStepPx = 28f * density

        glideStrokePaint.strokeWidth = 4.5f * density
        glideGlowPaint.strokeWidth = 11f * density
        glideMinDistancePx = 16f * density
        glideSampleDistSq = (8f * density) * (8f * density)
        spaceSlideTrackPaint.strokeWidth = 2.5f * density
        keyGlowPaint.strokeWidth = 2.2f * density
        sideDockTextPaint.textSize = 20f * density
        backspaceBadgeTextPaint.textSize = 12f * density
        vectorIconStrokePaint.strokeWidth = 2.2f * density

        val currentLayout = layout ?: return
        val hasExtraNumberRow = showDedicatedNumberRow && !isNumberSymbolsActive()
        val rowCount = currentLayout.rows.size + (if (hasExtraNumberRow) 1 else 0)
        val chinPx = bottomChinPaddingDp * density
        val availableH = (h - chinPx).coerceAtLeast(100f)
        val totalRows = rowCount + 1
        val totalMarginsV = (totalRows + 1) * keyMarginV
        keyHeight = ((availableH - totalMarginsV) / totalRows).coerceAtLeast(36f * density)
        spacebarRowHeight = keyHeight
        labelSize = keyHeight * 0.38f * fontScale
        labelSizeSmall = keyHeight * 0.22f * fontScale

        labelPaint.textSize = labelSize
        labelPaintSmall.textSize = labelSizeSmall
        hintPaint.textSize = labelSizeSmall * 0.92f
        keyPopupTextPaint.textSize = labelSize * 1.50f

        // Zone divider: exactly at x = width / 2 (5 keys left, 5 keys right)
        zoneDividerX = w / 2f

        computeKeyBounds()
    }

    /**
     * Compute pixel [RectF] for every key in the current layout according to [formFactor].
     * Result stored in [resolvedKeys]. Called from [onSizeChanged], [applyPreferences], and [setLayout].
     * Must NOT be called from [onDraw] or [onTouchEvent].
     */
    private fun computeKeyBounds() {
        resolvedKeys.clear()
        accessibilityHelper.invalidateRoot()
        val currentLayout = layout ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val chinPx = bottomChinPaddingDp * density
        val availableH = (h - chinPx).coerceAtLeast(100f)

        when (formFactor) {
            KeyboardPreferences.FormFactor.ONE_HANDED_LEFT -> {
                isSideDockVisible = true
                val kbW = w * 0.82f
                val dockLeft = kbW
                val dockW = w - kbW
                setupSideDockButtons(dockLeft, 0f, dockW, availableH, isLeft = false)
                layoutKeysStandard(0f, kbW, availableH)
            }
            KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT -> {
                isSideDockVisible = true
                val dockW = w * 0.18f
                val kbLeft = dockW
                val kbW = w - dockW
                setupSideDockButtons(0f, 0f, dockW, availableH, isLeft = true)
                layoutKeysStandard(kbLeft, kbW, availableH)
            }
            KeyboardPreferences.FormFactor.FLOATING -> {
                isSideDockVisible = false
                val floatingW = (w * 0.76f).coerceAtLeast(260f * density).coerceAtMost(w)
                val topBarH = 26f * density
                val floatingH = availableH * 0.86f
                val minX = 0f
                val maxX = (w - floatingW).coerceAtLeast(0f)
                val startX = ((w - floatingW) / 2f + floatingOffsetX).coerceIn(minX, maxX)
                val startY = floatingOffsetY.coerceIn(0f, (h - floatingH).coerceAtLeast(0f))

                floatingTopBarRect.set(startX, startY, startX + floatingW, startY + topBarH)
                floatingDockBtnRect.set(startX + floatingW - 32f * density, startY, startX + floatingW, startY + topBarH)

                val kbAreaTop = startY + topBarH
                val kbAreaH = floatingH - topBarH
                layoutKeysStandard(startX, floatingW, kbAreaH, yOffset = kbAreaTop)
            }
            KeyboardPreferences.FormFactor.SPLIT -> {
                isSideDockVisible = false
                layoutKeysSplit(w, availableH)
            }
            KeyboardPreferences.FormFactor.STANDARD -> {
                isSideDockVisible = false
                layoutKeysStandard(0f, w, availableH)
            }
        }
    }

    private fun setupSideDockButtons(left: Float, top: Float, width: Float, height: Float, isLeft: Boolean) {
        val btnH = (height / 3f).coerceAtLeast(30f)
        val pad = 4f * resources.displayMetrics.density

        sideDockButtons[0].action = SideDockAction.EXPAND_STANDARD
        sideDockButtons[0].bounds.set(left + pad, top + pad, left + width - pad, top + btnH - pad)

        sideDockButtons[1].action = SideDockAction.SWAP_SIDE
        sideDockButtons[1].bounds.set(left + pad, top + btnH + pad, left + width - pad, top + 2f * btnH - pad)

        sideDockButtons[2].action = SideDockAction.TOGGLE_FLOATING
        sideDockButtons[2].bounds.set(left + pad, top + 2f * btnH + pad, left + width - pad, top + 3f * btnH - pad)
    }

    private fun layoutKeysStandard(originX: Float, totalW: Float, totalH: Float, yOffset: Float = 0f) {
        val currentLayout = layout ?: return
        val density = resources.displayMetrics.density

        val baseRows = currentLayout.rows.mapIndexed { rowIndex, originalRow ->
            if (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.GBOARD) {
                when (rowIndex) {
                    0 -> if (isGboardKarsActive) com.lekhani.android.model.GboardBengaliLayout.getDynamicVowelsRow(gboardActiveConsonant) else originalRow
                    4 -> if (isGboardKarsActive) com.lekhani.android.model.GboardBengaliLayout.getDynamicRow5(gboardActiveConsonant) else originalRow
                    else -> originalRow
                }
            } else {
                originalRow
            }
        }
        val allRows = if (showDedicatedNumberRow && !isNumberSymbolsActive()) {
            val numRow = if (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.ENGLISH) englishDedicatedNumberRow else bengaliDedicatedNumberRow
            listOf(numRow) + baseRows
        } else {
            baseRows
        }

        val rowCount = allRows.size
        val totalRows = rowCount + 1
        val totalMarginsV = (totalRows + 1) * keyMarginV
        val kHeight = ((totalH - totalMarginsV) / totalRows).coerceAtLeast(36f * density)

        val sidePadding = (keyMarginH * 0.75f).coerceAtLeast(2f * density)
        val availableRowW = (totalW - 2f * sidePadding).coerceAtLeast(10f)

        // Find reference key width based on the primary 10-key row (or the widest character row)
        val maxKeysInRow = allRows.maxOfOrNull { it.size }?.coerceAtLeast(10) ?: 10
        val standardGaps = (maxKeysInRow - 1) * keyMarginH
        val standardUnitWidth = (availableRowW - standardGaps) / maxKeysInRow

        var currentRowTop = yOffset + keyMarginV
        for ((rowIndex, row) in allRows.withIndex()) {
            val hasShiftAtStart = row.isNotEmpty() && row.first().action == KeyAction.Shift
            val hasBackspaceAtEnd = row.isNotEmpty() && row.last().action == KeyAction.Backspace

            if (hasShiftAtStart && hasBackspaceAtEnd && row.size > 2) {
                // Bottom letter row: [Shift] ... [letters] ... [Backspace]
                // Distribute width proportionally using ergonomic weights (~1.32x for Shift/Backspace)
                // so middle letters are comfortable and wide, while Shift and Backspace are balanced,
                // matching Gboard/iOS gold standards rather than oversized slabs.
                val middleLetterCount = row.size - 2
                val rawShiftWeight = row.first().widthWeight
                val rawBackWeight = row.last().widthWeight
                val funcWeight = if (rawShiftWeight > 1.0f) rawShiftWeight.coerceIn(1.25f, 1.35f) else 1.32f
                val backWeight = if (rawBackWeight > 1.0f) rawBackWeight.coerceIn(1.25f, 1.35f) else 1.32f

                val rowWeight = funcWeight + backWeight + middleLetterCount * 1.0f
                val totalGaps = (row.size - 1) * keyMarginH
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
                    keyLeft += keyWidth + keyMarginH
                }
            } else {
                val rowWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
                val totalGaps = (row.size - 1) * keyMarginH
                val isStandardCharRow = row.all { it.widthWeight == 1.0f }

                val (rowSideInset, unitWidth) = when {
                    // Gboard: 10 keys per row, uniform width across all rows, no insets
                    layoutType == com.lekhani.android.ffi.LekhaniLayoutType.GBOARD -> {
                        val w = (availableRowW - totalGaps) / rowWeight
                        Pair(0f, w)
                    }
                    // Probhat Row 1 (9 keys below 12 keys): gentle 0.5-key QWERTY offset, keys expand naturally
                    layoutType == com.lekhani.android.ffi.LekhaniLayoutType.PROBHAT && rowIndex == 1 -> {
                        val halfKeyOffset = standardUnitWidth * 0.5f
                        val contentW = availableRowW - 2f * halfKeyOffset
                        val w = (contentW - totalGaps) / rowWeight
                        Pair(halfKeyOffset, w)
                    }
                    // English / National 9-key home row below 10-key row: gentle 0.5-key QWERTY offset
                    isStandardCharRow && row.size == maxKeysInRow - 1 -> {
                        val halfKeyOffset = standardUnitWidth * 0.5f
                        Pair(halfKeyOffset, standardUnitWidth)
                    }
                    // Probaho and 10-key home rows: elegant second row side padding like other layouts
                    rowIndex == 1 && currentLayout.rows.size >= 3 -> {
                        val halfKeyOffset = standardUnitWidth * 0.35f
                        val contentW = availableRowW - 2f * halfKeyOffset
                        val w = (contentW - totalGaps) / rowWeight
                        Pair(halfKeyOffset, w)
                    }
                    // Default fallback
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
                    keyLeft += keyWidth + keyMarginH
                }
            }
            currentRowTop += kHeight + keyMarginV
        }

        // Spacebar row
        val spaceRow = currentLayout.spacebarRow
        val totalSpaceWeight = spaceRow.sumOf { it.widthWeight.toDouble() }.toFloat()
        val spaceGaps = (spaceRow.size - 1) * keyMarginH
        val spaceUnitWidth = (availableRowW - spaceGaps) / totalSpaceWeight
        val spaceRowTop = currentRowTop
        val spaceKeyHeight = (yOffset + totalH - keyMarginV - spaceRowTop).coerceAtLeast(kHeight)

        var spaceKeyLeft = originX + sidePadding
        for (key in spaceRow) {
            val keyWidth = spaceUnitWidth * key.widthWeight
            resolvedKeys.add(
                ResolvedKey(
                    key = key,
                    bounds = RectF(spaceKeyLeft, spaceRowTop, spaceKeyLeft + keyWidth, spaceRowTop + spaceKeyHeight)
                )
            )
            spaceKeyLeft += keyWidth + keyMarginH
        }
    }

    private fun layoutKeysSplit(totalW: Float, totalH: Float) {
        val currentLayout = layout ?: return
        val density = resources.displayMetrics.density

        val baseRows = currentLayout.rows.mapIndexed { rowIndex, originalRow ->
            if (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.GBOARD) {
                when (rowIndex) {
                    0 -> if (isGboardKarsActive) com.lekhani.android.model.GboardBengaliLayout.getDynamicVowelsRow(gboardActiveConsonant) else originalRow
                    4 -> if (isGboardKarsActive) com.lekhani.android.model.GboardBengaliLayout.getDynamicRow5(gboardActiveConsonant) else originalRow
                    else -> originalRow
                }
            } else {
                originalRow
            }
        }
        val allRows = if (showDedicatedNumberRow && !isNumberSymbolsActive()) {
            val numRow = if (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.ENGLISH) englishDedicatedNumberRow else bengaliDedicatedNumberRow
            listOf(numRow) + baseRows
        } else {
            baseRows
        }

        val rowCount = allRows.size
        val totalRows = rowCount + 1
        val totalMarginsV = (totalRows + 1) * keyMarginV
        val kHeight = ((totalH - totalMarginsV) / totalRows).coerceAtLeast(36f * density)

        val centerGap = totalW * 0.14f
        val clusterW = (totalW - centerGap) / 2f
        val rightClusterOrigin = clusterW + centerGap

        var currentRowTop = keyMarginV
        for ((rowIndex, row) in allRows.withIndex()) {
            val halfCount = (row.size + 1) / 2
            val leftKeys = row.take(halfCount)
            val rightKeys = row.drop(halfCount)

            val leftWeight = leftKeys.sumOf { it.widthWeight.toDouble() }.toFloat()
            val leftUnitW = (clusterW - keyMarginH * (leftKeys.size + 1)) / leftWeight
            var keyLeft = keyMarginH
            for (key in leftKeys) {
                val keyW = leftUnitW * key.widthWeight
                resolvedKeys.add(ResolvedKey(key, RectF(keyLeft, currentRowTop, keyLeft + keyW, currentRowTop + kHeight)))
                keyLeft += keyW + keyMarginH
            }

            val rightWeight = rightKeys.sumOf { it.widthWeight.toDouble() }.toFloat()
            val rightUnitW = (clusterW - keyMarginH * (rightKeys.size + 1)) / rightWeight
            var rightKeyLeft = rightClusterOrigin + keyMarginH
            for (key in rightKeys) {
                val keyW = rightUnitW * key.widthWeight
                resolvedKeys.add(ResolvedKey(key, RectF(rightKeyLeft, currentRowTop, rightKeyLeft + keyW, currentRowTop + kHeight)))
                rightKeyLeft += keyW + keyMarginH
            }

            currentRowTop += kHeight + keyMarginV
        }

        // Spacebar row in Split mode
        val spaceRow = currentLayout.spacebarRow
        val halfCount = (spaceRow.size + 1) / 2
        val leftSpaceKeys = spaceRow.take(halfCount)
        val rightSpaceKeys = spaceRow.drop(halfCount)
        val spaceRowTop = currentRowTop
        val spaceKeyHeight = (totalH - keyMarginV - spaceRowTop).coerceAtLeast(kHeight)

        val leftWeight = leftSpaceKeys.sumOf { it.widthWeight.toDouble() }.toFloat()
        val leftUnitW = (clusterW - keyMarginH * (leftSpaceKeys.size + 1)) / leftWeight
        var keyLeft = keyMarginH
        for (key in leftSpaceKeys) {
            val keyW = leftUnitW * key.widthWeight
            resolvedKeys.add(ResolvedKey(key, RectF(keyLeft, spaceRowTop, keyLeft + keyW, spaceRowTop + spaceKeyHeight)))
            keyLeft += keyW + keyMarginH
        }

        val rightWeight = rightSpaceKeys.sumOf { it.widthWeight.toDouble() }.toFloat()
        val rightUnitW = (clusterW - keyMarginH * (rightSpaceKeys.size + 1)) / rightWeight
        var rightKeyLeft = rightClusterOrigin + keyMarginH
        for (key in rightSpaceKeys) {
            val keyW = rightUnitW * key.widthWeight
            resolvedKeys.add(ResolvedKey(key, RectF(rightKeyLeft, spaceRowTop, rightKeyLeft + keyW, spaceRowTop + spaceKeyHeight)))
            rightKeyLeft += keyW + keyMarginH
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Vector Drawing Helpers — Zero allocations permitted
    // ══════════════════════════════════════════════════════════════════════════

    private fun drawVectorBackspace(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        vectorIconPath.rewind()
        val halfW = size * 0.52f
        val halfH = size * 0.35f
        val tipW = size * 0.34f

        vectorIconPath.moveTo(cx - halfW, cy)
        vectorIconPath.lineTo(cx - halfW + tipW, cy - halfH)
        vectorIconPath.lineTo(cx + halfW, cy - halfH)
        vectorIconPath.lineTo(cx + halfW, cy + halfH)
        vectorIconPath.lineTo(cx - halfW + tipW, cy + halfH)
        vectorIconPath.close()
        canvas.drawPath(vectorIconPath, paint)

        val crossHalf = size * 0.14f
        val crossCx = cx + tipW * 0.32f
        canvas.drawLine(crossCx - crossHalf, cy - crossHalf, crossCx + crossHalf, cy + crossHalf, paint)
        canvas.drawLine(crossCx - crossHalf, cy + crossHalf, crossCx + crossHalf, cy - crossHalf, paint)
    }

    private fun drawVectorShift(canvas: Canvas, cx: Float, cy: Float, size: Float, isShifted: Boolean, isLocked: Boolean, strokePaint: Paint, fillPaint: Paint) {
        vectorIconPath.rewind()
        val halfW = size * 0.40f
        val headH = size * 0.36f
        val stemHalfW = size * 0.18f
        val totalHalfH = size * 0.44f

        vectorIconPath.moveTo(cx, cy - totalHalfH)
        vectorIconPath.lineTo(cx + halfW, cy - totalHalfH + headH)
        vectorIconPath.lineTo(cx + stemHalfW, cy - totalHalfH + headH)
        vectorIconPath.lineTo(cx + stemHalfW, cy + totalHalfH)
        vectorIconPath.lineTo(cx - stemHalfW, cy + totalHalfH)
        vectorIconPath.lineTo(cx - stemHalfW, cy - totalHalfH + headH)
        vectorIconPath.lineTo(cx - halfW, cy - totalHalfH + headH)
        vectorIconPath.close()

        if (isShifted || isLocked) {
            fillPaint.color = activeTheme.accentColor
            canvas.drawPath(vectorIconPath, fillPaint)
        } else {
            strokePaint.color = activeTheme.labelColor
            canvas.drawPath(vectorIconPath, strokePaint)
        }
    }

    private fun drawVectorEnter(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        vectorIconPath.rewind()
        val w = size * 0.60f
        val h = size * 0.42f
        val left = cx - w / 2f
        val right = cx + w / 2f
        val top = cy - h / 2f
        val bottom = cy + h / 2f
        val arrowSize = size * 0.20f

        vectorIconPath.moveTo(right, top)
        vectorIconPath.lineTo(right, bottom)
        vectorIconPath.lineTo(left, bottom)

        vectorIconPath.moveTo(left + arrowSize, bottom - arrowSize)
        vectorIconPath.lineTo(left, bottom)
        vectorIconPath.lineTo(left + arrowSize, bottom + arrowSize)

        canvas.drawPath(vectorIconPath, strokePaint)
    }

    private fun drawVectorExpand(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val s = size * 0.40f
        val b = size * 0.18f
        vectorIconPath.rewind()
        vectorIconPath.moveTo(cx - s, cy - s + b); vectorIconPath.lineTo(cx - s, cy - s); vectorIconPath.lineTo(cx - s + b, cy - s)
        vectorIconPath.moveTo(cx + s - b, cy - s); vectorIconPath.lineTo(cx + s, cy - s); vectorIconPath.lineTo(cx + s, cy - s + b)
        vectorIconPath.moveTo(cx - s, cy + s - b); vectorIconPath.lineTo(cx - s, cy + s); vectorIconPath.lineTo(cx - s + b, cy + s)
        vectorIconPath.moveTo(cx + s - b, cy + s); vectorIconPath.lineTo(cx + s, cy + s); vectorIconPath.lineTo(cx + s, cy + s - b)
        canvas.drawPath(vectorIconPath, strokePaint)
    }

    private fun drawVectorChevron(canvas: Canvas, cx: Float, cy: Float, size: Float, isLeft: Boolean, strokePaint: Paint) {
        val w = size * 0.24f
        val h = size * 0.36f
        vectorIconPath.rewind()
        if (isLeft) {
            vectorIconPath.moveTo(cx + w, cy - h)
            vectorIconPath.lineTo(cx - w, cy)
            vectorIconPath.lineTo(cx + w, cy + h)
        } else {
            vectorIconPath.moveTo(cx - w, cy - h)
            vectorIconPath.lineTo(cx + w, cy)
            vectorIconPath.lineTo(cx - w, cy + h)
        }
        canvas.drawPath(vectorIconPath, strokePaint)
    }

    private fun drawVectorFloating(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val halfW = size * 0.42f
        val halfH = size * 0.32f
        scratchRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        canvas.drawRoundRect(scratchRect, 3f, 3f, strokePaint)
        canvas.drawLine(cx - halfW, cy - halfH * 0.35f, cx + halfW, cy - halfH * 0.35f, strokePaint)
    }

    private fun drawVectorGlobe(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val r = size * 0.38f
        canvas.drawCircle(cx, cy, r, strokePaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, strokePaint)
        scratchRect.set(cx - r * 0.46f, cy - r, cx + r * 0.46f, cy + r)
        canvas.drawOval(scratchRect, strokePaint)
    }

    private fun sanitizeLabelForDisplay(label: String): String = label

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
        val density = resources.displayMetrics.density

        // ── Side Dock Buttons (One-Handed mode, crisp vector rendering) ───
        if (isSideDockVisible) {
            val dockIconSize = 18f * density
            for (btn in sideDockButtons) {
                canvas.drawRoundRect(btn.bounds, 10f * density, 10f * density, sideDockBgPaint)
                val bcx = btn.bounds.centerX()
                val bcy = btn.bounds.centerY()
                when (btn.action) {
                    SideDockAction.EXPAND_STANDARD -> drawVectorExpand(canvas, bcx, bcy, dockIconSize, vectorIconStrokePaint)
                    SideDockAction.SWAP_SIDE -> drawVectorChevron(canvas, bcx, bcy, dockIconSize, isLeft = (formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT), vectorIconStrokePaint)
                    SideDockAction.TOGGLE_FLOATING -> drawVectorFloating(canvas, bcx, bcy, dockIconSize, vectorIconStrokePaint)
                }
            }
        }

        // ── Floating Mode Top Drag Bar ────────────────────────────────────
        if (formFactor == KeyboardPreferences.FormFactor.FLOATING) {
            canvas.drawRoundRect(floatingTopBarRect, 8f * density, 8f * density, sideDockBgPaint)
            val barMidX = floatingTopBarRect.centerX()
            val barMidY = floatingTopBarRect.centerY()
            scratchRect.set(barMidX - 16f * density, barMidY - 2.5f * density, barMidX + 16f * density, barMidY + 2.5f * density)
            canvas.drawRoundRect(scratchRect, 2.5f * density, 2.5f * density, sideDockTextPaint)
            drawVectorExpand(canvas, floatingDockBtnRect.centerX(), floatingDockBtnRect.centerY(), 14f * density, vectorIconStrokePaint)
        }

        // ── Keys ───────────────────────────────────────────────────────────
        for (i in resolvedKeys.indices) {
            val resolved = resolvedKeys[i]
            val key = resolved.key
            val bounds = resolved.bounds

            // Select background paint
            val bgPaint = when {
                key.action == KeyAction.Backspace || key.action == KeyAction.Shift ||
                key.action == KeyAction.SwitchNumeric || key.action == KeyAction.SwitchMoreSymbols ||
                key.action == KeyAction.SwitchAlpha || key.action == KeyAction.ToggleBengaliDigits ||
                key.action == KeyAction.SwitchLayout || key.action == KeyAction.Enter ||
                key.action == KeyAction.SwitchEmoji || key.action == KeyAction.SwitchClipboard -> keyShiftBgPaint
                key.action == KeyAction.Space -> keySpaceBgPaint
                else -> keyBgPaint
            }

            // 3D Keycap tactile depth & depression
            val shadowLip = 1.8f * density
            val drawBounds = if (i == pressedKeyIndex) {
                // Key pressed down: depressed into the keyboard surface
                keyDrawRect.set(bounds.left, bounds.top + shadowLip, bounds.right, bounds.bottom)
                if (keyGlowRippleEnabled) {
                    scratchRect.set(keyDrawRect.left - 2.5f, keyDrawRect.top - 2.5f, keyDrawRect.right + 2.5f, keyDrawRect.bottom + 2.5f)
                    canvas.drawRoundRect(scratchRect, keyCornerRadius + 2.5f, keyCornerRadius + 2.5f, keyGlowPaint)
                }
                keyDrawRect
            } else {
                // Key bottom shadow
                scratchRect.set(bounds.left, bounds.top + shadowLip, bounds.right, bounds.bottom)
                canvas.drawRoundRect(scratchRect, keyCornerRadius, keyCornerRadius, keyShadowPaint)
                // Key elevated top surface
                keyDrawRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom - shadowLip)
                keyDrawRect
            }

            // Draw key background with rounded corners
            canvas.drawRoundRect(drawBounds, keyCornerRadius, keyCornerRadius, bgPaint)

            // Draw key border
            if (showKeyBorders) {
                canvas.drawRoundRect(drawBounds, keyCornerRadius, keyCornerRadius, keyBorderPaint)
            }

            // Home row accent underline (optional tactile hint, disabled by default)
            if (showHomeRowAccents && key.isHomeRow) {
                scratchRect.set(
                    drawBounds.left + keyCornerRadius,
                    drawBounds.bottom - homeRowAccentHeight,
                    drawBounds.right - keyCornerRadius,
                    drawBounds.bottom,
                )
                canvas.drawRect(scratchRect, homeRowAccentPaint)
            }

            // Draw ripple if this key is pressed
            if (i == pressedKeyIndex) {
                val elapsed = (now - pressStartTime).coerceAtMost(RIPPLE_DURATION_MS)
                val fraction = elapsed.toFloat() / RIPPLE_DURATION_MS
                val rippleAlpha = ((1f - fraction) * RIPPLE_MAX_ALPHA).toInt().coerceIn(0, 255)
                ripplePaint.alpha = rippleAlpha
                rippleRect.set(drawBounds)
                canvas.drawRoundRect(rippleRect, keyCornerRadius, keyCornerRadius, ripplePaint)
                if (fraction < 1f) invalidate()
            }

            // Draw label
            val rawLabel = if (key.action == KeyAction.Space) {
                com.lekhani.android.model.LayoutRegistry.getSpacebarLabel(layoutType)
            } else {
                key.displayLabel(isShifted)
            }
            val labelText = sanitizeLabelForDisplay(rawLabel)
            val cx = drawBounds.centerX()
            val cy = drawBounds.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f

            when (key.action) {
                KeyAction.Backspace -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    drawVectorBackspace(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                }
                KeyAction.Shift -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    drawVectorShift(canvas, cx, drawBounds.centerY(), iconSize, isShifted, false, vectorIconStrokePaint, vectorIconFillPaint)
                }
                KeyAction.Enter -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    drawVectorEnter(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                }
                KeyAction.SwitchLayout -> {
                    val iconSize = (drawBounds.height() * 0.40f).coerceAtLeast(16f * density)
                    drawVectorGlobe(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                    val hint = key.hintLabel
                    if (hint != null) {
                        val displayHint = sanitizeLabelForDisplay(hint)
                        val hintX = drawBounds.right - 5f * density
                        val hintY = drawBounds.top + 13f * density
                        canvas.drawText(displayHint, hintX, hintY, hintPaint)
                    }
                }
                KeyAction.SwitchNumeric, KeyAction.SwitchMoreSymbols, KeyAction.SwitchAlpha, KeyAction.ToggleBengaliDigits, KeyAction.Space -> {
                    canvas.drawText(labelText, cx, cy, labelPaintSmall)
                }
                else -> {
                    canvas.drawText(labelText, cx, cy, labelPaint)
                    val hint = key.hintLabel ?: if (!isShifted && key.shiftedLabel != null && key.shiftedLabel != key.label && key.shiftedLabel.isNotEmpty()) key.shiftedLabel else null
                    if (hint != null) {
                        val displayHint = sanitizeLabelForDisplay(hint)
                        val hintX = drawBounds.right - 5f * density
                        val hintY = drawBounds.top + 13f * density
                        canvas.drawText(displayHint, hintX, hintY, hintPaint)
                    }
                }
            }

            // Spacebar cursor slide visualization
            if (isSpaceCursorMoving && key.action == KeyAction.Space) {
                val midY = bounds.centerY()
                canvas.drawLine(bounds.left + 16f * density, midY, bounds.right - 16f * density, midY, spaceSlideTrackPaint)
                val dotX = glideCurX.coerceIn(bounds.left + 24f * density, bounds.right - 24f * density)
                canvas.drawCircle(dotX, midY, 6f * density, spaceSlideThumbPaint)
            }

            // Backspace swipe to delete badge visualization
            if (isBackspaceSwiping && key.action == KeyAction.Backspace && backspaceDeletedWordCount > 0) {
                val badgeW = 60f * density
                val badgeH = 22f * density
                val badgeL = bounds.centerX() - badgeW / 2f
                val badgeT = bounds.top - 18f * density
                scratchRect.set(badgeL, badgeT, badgeL + badgeW, badgeT + badgeH)
                canvas.drawRoundRect(scratchRect, 6f * density, 6f * density, backspaceBadgePaint)
                val textY = scratchRect.centerY() - (backspaceBadgeTextPaint.ascent() + backspaceBadgeTextPaint.descent()) / 2f
                canvas.drawText("-$backspaceDeletedWordCount", scratchRect.centerX(), textY, backspaceBadgeTextPaint)
            }
        }

        // ── Floating Key Preview Bubble (Material 3 Elevated Keycap) ─────────
        if (pressedKeyIndex in resolvedKeys.indices && !isGliding && !isSpaceCursorMoving && !isBackspaceSwiping) {
            val pressedResolved = resolvedKeys[pressedKeyIndex]
            val pKey = pressedResolved.key
            if (pKey.action is KeyAction.Character && pKey.label.isNotEmpty()) {
                val pBounds = pressedResolved.bounds
                val popupW = (pBounds.width() * 1.35f).coerceAtLeast(48f * density)
                val popupH = (pBounds.height() * 1.25f).coerceAtLeast(50f * density)
                val pcx = pBounds.centerX()
                val pLeft = (pcx - popupW / 2f).coerceIn(4f * density, (width.toFloat() - popupW - 4f * density).coerceAtLeast(4f * density))
                val pRight = pLeft + popupW
                val pTop = (pBounds.top - popupH - 8f * density).coerceAtLeast(-popupH * 0.85f)
                val pBottom = pTop + popupH

                // Popup shadow
                keyPopupShadowRect.set(pLeft, pTop + 3f * density, pRight, pBottom + 3f * density)
                canvas.drawRoundRect(keyPopupShadowRect, 12f * density, 12f * density, keyPopupShadowPaint)

                // Popup body & border
                keyPopupRect.set(pLeft, pTop, pRight, pBottom)
                canvas.drawRoundRect(keyPopupRect, 12f * density, 12f * density, keyPopupBgPaint)
                canvas.drawRoundRect(keyPopupRect, 12f * density, 12f * density, keyPopupStrokePaint)

                // Popup character
                val charStr = sanitizeLabelForDisplay(pKey.displayLabel(isShifted))
                val pTextY = keyPopupRect.centerY() - (keyPopupTextPaint.ascent() + keyPopupTextPaint.descent()) / 2f
                canvas.drawText(charStr, keyPopupRect.centerX(), pTextY, keyPopupTextPaint)
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
        key.action == KeyAction.SwitchMoreSymbols || key.action == KeyAction.SwitchAlpha ||
        key.action == KeyAction.ToggleBengaliDigits ||
        key.action == KeyAction.SwitchLayout || key.action == KeyAction.Enter

    // ══════════════════════════════════════════════════════════════════════════
    // Touch handling — ZERO allocations permitted here
    // ══════════════════════════════════════════════════════════════════════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val px = event.getX(actionIndex)
        val py = event.getY(actionIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    // Commit pending key from previous pointer before switching to new pointer
                    if (pressedKeyIndex in resolvedKeys.indices && !isGliding && !isSpaceSwiping &&
                        !isSpaceCursorMoving && !isBackspaceSwiping && !isLongPressTriggered) {
                        dispatchKey(resolvedKeys[pressedKeyIndex].key)
                    }
                    removeCallbacks(longPressRunnable)
                    removeCallbacks(backspaceRepeatRunnable)
                }

                pressedPointerId = event.getPointerId(actionIndex)
                touchStartX = px
                touchStartY = py
                glideCurX = px
                glideCurY = py
                isGliding = false
                glidePointCount = 0
                visitedGlideKeys.clear()
                lastVisitedKeyIdx = -1

                // Side Dock Buttons hit-testing (One-Handed mode)
                if (isSideDockVisible) {
                    for (btn in sideDockButtons) {
                        if (btn.bounds.contains(px, py)) {
                            feedbackManager?.onKeyFeedback(this) ?: performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            when (btn.action) {
                                SideDockAction.EXPAND_STANDARD -> keyListener?.onFormFactorChange(KeyboardPreferences.FormFactor.STANDARD)
                                SideDockAction.SWAP_SIDE -> {
                                    val next = if (formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT) {
                                        KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                                    } else {
                                        KeyboardPreferences.FormFactor.ONE_HANDED_LEFT
                                    }
                                    keyListener?.onFormFactorChange(next)
                                }
                                SideDockAction.TOGGLE_FLOATING -> keyListener?.onFormFactorChange(KeyboardPreferences.FormFactor.FLOATING)
                            }
                            return true
                        }
                    }
                }

                // Floating mode top bar / dock button hit-testing
                if (formFactor == KeyboardPreferences.FormFactor.FLOATING) {
                    if (floatingDockBtnRect.contains(px, py)) {
                        feedbackManager?.onKeyFeedback(this) ?: performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        keyListener?.onFormFactorChange(KeyboardPreferences.FormFactor.STANDARD)
                        return true
                    }
                    if (floatingTopBarRect.contains(px, py)) {
                        isDraggingFloatingBar = true
                        floatingDragStartX = px - floatingOffsetX
                        floatingDragStartY = py - floatingOffsetY
                        return true
                    }
                }

                val idx = findKeyIndex(px, py)
                if (idx >= 0) {
                    pressedKeyIndex = idx
                    pressStartTime = SystemClock.uptimeMillis()
                    isLongPressTriggered = false
                    isSpaceSwiping = false
                    isSpaceCursorMoving = false
                    isBackspaceSwiping = false
                    backspaceDeletedWordCount = 0

                    feedbackManager?.onKeyFeedback(this) ?: performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    val key = resolvedKeys[idx].key
                    val keyAct = key.activeAction(isShifted)
                    if (keyAct is KeyAction.Character) {
                        glidePointsX[0] = px
                        glidePointsY[0] = py
                        glidePointCount = 1
                        visitedGlideKeys.add(keyAct.token)
                        lastVisitedKeyIdx = idx
                        postDelayed(longPressRunnable, longPressDelayMs)
                    } else if (key.action == KeyAction.Space) {
                        spaceTouchStartX = px
                        spaceTouchStartY = py
                        spaceSlideLastX = px
                        postDelayed(longPressRunnable, longPressDelayMs)
                    } else if (key.action == KeyAction.Backspace) {
                        backspaceSwipeStartX = px
                        postDelayed(backspaceRepeatRunnable, 400L)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val curIndex = if (pressedPointerId >= 0) event.findPointerIndex(pressedPointerId) else 0
                val curX = if (curIndex in 0 until event.pointerCount) event.getX(curIndex) else event.x
                val curY = if (curIndex in 0 until event.pointerCount) event.getY(curIndex) else event.y
                glideCurX = curX
                glideCurY = curY

                val moveDistSq = (curX - touchStartX) * (curX - touchStartX) + (curY - touchStartY) * (curY - touchStartY)
                val touchSlopPx = 8f * resources.displayMetrics.density
                if (moveDistSq > touchSlopPx * touchSlopPx) {
                    removeCallbacks(longPressRunnable)
                    removeCallbacks(backspaceRepeatRunnable)
                }

                // Floating mode window dragging
                if (isDraggingFloatingBar) {
                    floatingOffsetX = curX - floatingDragStartX
                    floatingOffsetY = curY - floatingDragStartY
                    computeKeyBounds()
                    invalidate()
                    return true
                }

                if (pressedKeyIndex in resolvedKeys.indices) {
                    val key = resolvedKeys[pressedKeyIndex].key

                    // Spacebar cursor slide navigation (active when spacebarSwipeMode == CURSOR_NAV)
                    if (key.action == KeyAction.Space && spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV) {
                        val dx = curX - spaceTouchStartX
                        val absDx = kotlin.math.abs(dx)
                        if (!isSpaceCursorMoving && absDx > spaceSlideThresholdPx) {
                            isSpaceCursorMoving = true
                            isSpaceSwiping = false
                            removeCallbacks(longPressRunnable)
                        }
                        if (isSpaceCursorMoving) {
                            val stepDelta = curX - spaceSlideLastX
                            if (stepDelta >= spaceSlideStepPx) {
                                val steps = (stepDelta / spaceSlideStepPx).toInt()
                                keyListener?.onCursorMove(steps)
                                spaceSlideLastX += steps * spaceSlideStepPx
                                feedbackManager?.onKeyFeedback(this) ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                invalidate()
                            } else if (stepDelta <= -spaceSlideStepPx) {
                                val steps = (-stepDelta / spaceSlideStepPx).toInt()
                                keyListener?.onCursorMove(-steps)
                                spaceSlideLastX -= steps * spaceSlideStepPx
                                feedbackManager?.onKeyFeedback(this) ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                invalidate()
                            }
                            return true
                        }
                    }

                    // Swipe-to-delete gesture on Backspace
                    if (key.action == KeyAction.Backspace && swipeToDeleteEnabled) {
                        val dx = curX - backspaceSwipeStartX
                        if (dx < -swipeThresholdPx) {
                            removeCallbacks(backspaceRepeatRunnable)
                            isBackspaceSwiping = true
                            val words = ((-dx - swipeThresholdPx) / backspaceSwipeStepPx).toInt() + 1
                            val clamped = words.coerceIn(1, 20)
                            if (clamped != backspaceDeletedWordCount) {
                                backspaceDeletedWordCount = clamped
                                keyListener?.onSwipeDeletePreview(backspaceDeletedWordCount)
                                feedbackManager?.onKeyFeedback(this) ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                invalidate()
                            }
                            return true
                        } else if (isBackspaceSwiping && dx > -swipeThresholdPx * 0.4f) {
                            isBackspaceSwiping = false
                            backspaceDeletedWordCount = 0
                            keyListener?.onSwipeDeletePreview(0)
                            invalidate()
                            return true
                        }
                    }

                    // Spacebar layout swipe detection (active when spacebarSwipeMode == LAYOUT_SWITCH)
                    if (key.action == KeyAction.Space && spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH && !isSpaceSwiping && !isSpaceCursorMoving && !isLongPressTriggered) {
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

                if (!isGliding && !isSpaceSwiping && !isSpaceCursorMoving && !isBackspaceSwiping &&
                    totalDistSq > glideMinDistancePx * glideMinDistancePx) {
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
                        val actionK = resolvedKeys[keyIdx].key.activeAction(isShifted)
                        if (actionK is KeyAction.Character) {
                            visitedGlideKeys.add(actionK.token)
                            lastVisitedKeyIdx = keyIdx
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    invalidate()
                    return true
                }

                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(actionIndex)
                if (pointerId == pressedPointerId) {
                    removeCallbacks(longPressRunnable)
                    removeCallbacks(backspaceRepeatRunnable)
                    val idx = findKeyIndex(px, py)
                    if (idx >= 0 && idx == pressedKeyIndex && !isLongPressTriggered && !isSpaceSwiping &&
                        !isGliding && !isBackspaceSwiping && !isSpaceCursorMoving) {
                        dispatchKey(resolvedKeys[idx].key)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    isLongPressTriggered = false
                    isSpaceSwiping = false
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                removeCallbacks(backspaceRepeatRunnable)
                isDraggingFloatingBar = false

                if (isSpaceCursorMoving) {
                    isSpaceCursorMoving = false
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    invalidate()
                    return true
                }

                if (isBackspaceSwiping) {
                    if (backspaceDeletedWordCount > 0) {
                        keyListener?.onSwipeDelete(backspaceDeletedWordCount)
                        feedbackManager?.onKeyFeedback(this) ?: performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    isBackspaceSwiping = false
                    backspaceDeletedWordCount = 0
                    keyListener?.onSwipeDeletePreview(0)
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    invalidate()
                    return true
                }

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
                    pressedPointerId = -1
                    isLongPressTriggered = false
                    isSpaceSwiping = false
                    invalidate()
                    return true
                }

                val idx = findKeyIndex(px, py)
                if (idx >= 0 && idx == pressedKeyIndex && !isLongPressTriggered && !isSpaceSwiping) {
                    dispatchKey(resolvedKeys[idx].key)
                }
                pressedKeyIndex = -1
                pressedPointerId = -1
                isLongPressTriggered = false
                isSpaceSwiping = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                removeCallbacks(backspaceRepeatRunnable)
                isGliding = false
                isSpaceCursorMoving = false
                isBackspaceSwiping = false
                isDraggingFloatingBar = false
                backspaceDeletedWordCount = 0
                keyListener?.onSwipeDeletePreview(0)
                isSpaceSwiping = false
                pressedKeyIndex = -1
                pressedPointerId = -1
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
        // Fast direct hit-test
        for (i in resolvedKeys.indices) {
            if (resolvedKeys[i].bounds.contains(x, y)) {
                return i
            }
        }

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
    // Accessibility / TalkBack Exploration (WCAG 2.1)
    // ══════════════════════════════════════════════════════════════════════════

    private inner class KeyboardAccessibilityHelper : ExploreByTouchHelper(this@KeyboardCanvasView) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val keyIndex = findKeyIndex(x, y)
            return if (keyIndex in resolvedKeys.indices) keyIndex else HOST_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in resolvedKeys.indices) {
                virtualViewIds.add(i)
            }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            if (virtualViewId !in resolvedKeys.indices) {
                node.text = ""
                node.setBoundsInParent(Rect())
                return
            }
            val resolved = resolvedKeys[virtualViewId]
            val key = resolved.key
            val bounds = resolved.bounds

            val rect = Rect(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt()
            )
            node.setBoundsInParent(rect)

            val desc = getBengaliAccessibilityDescription(key, isShifted)
            node.contentDescription = desc
            node.className = "android.widget.Button"
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.isClickable = true
            node.isEnabled = true
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK && virtualViewId in resolvedKeys.indices) {
                val key = resolvedKeys[virtualViewId].key
                val act = key.activeAction(isShifted)
                feedbackManager?.onKeyFeedback(this@KeyboardCanvasView)
                    ?: performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                keyListener?.onKey(key, act)
                return true
            }
            return false
        }
    }

    private fun getBengaliAccessibilityDescription(key: Key, shifted: Boolean): String {
        val label = key.displayLabel(shifted)
        return when (key.action) {
            KeyAction.Space -> "স্পেসবার (Spacebar)"
            KeyAction.Backspace -> "ব্যাকস্পেস (Backspace)"
            KeyAction.Enter -> "এন্টার (Enter)"
            KeyAction.Shift -> if (shifted) "শিফট সক্রিয় (Shift Active)" else "শিফট (Shift)"
            KeyAction.SwitchNumeric -> "সংখ্যা ও প্রতীক (Numbers and symbols)"
            KeyAction.SwitchMoreSymbols -> "অতিরিক্ত প্রতীক (More symbols)"
            KeyAction.SwitchAlpha -> "বর্ণমালা (Alphabet)"
            KeyAction.ToggleBengaliDigits -> "সংখ্যা পরিবর্তন (Toggle Digits)"
            KeyAction.SwitchLayout -> "লেআউট পরিবর্তন (Switch Layout)"
            KeyAction.VoiceTyping -> "ভয়েস টাইপিং (Voice Typing)"
            KeyAction.SwitchEmoji -> "ইমোজি (Emoji)"
            KeyAction.SwitchClipboard -> "ক্লিপবোর্ড (Clipboard)"
            else -> {
                if (key.contentDesc.isNotBlank() && key.contentDesc != key.label) {
                    key.contentDesc
                } else {
                    when (label) {
                        "অ" -> "অ, স্বর অ"
                        "আ" -> "আ, স্বর আ"
                        "ই" -> "ই, হ্রস্ব ই"
                        "ঈ" -> "ঈ, দীর্ঘ ঈ"
                        "উ" -> "উ, হ্রস্ব উ"
                        "ঊ" -> "ঊ, দীর্ঘ ঊ"
                        "ঋ" -> "ঋ, রি"
                        "এ" -> "এ"
                        "ঐ" -> "ঐ"
                        "ও" -> "ও"
                        "ঔ" -> "ঔ"
                        "ক" -> "ক"
                        "খ" -> "খ"
                        "গ" -> "গ"
                        "ঘ" -> "ঘ"
                        "ঙ" -> "ঙ, উঙ"
                        "চ" -> "চ"
                        "ছ" -> "ছ"
                        "জ" -> "জ, বর্গীয় জ"
                        "ঝ" -> "ঝ"
                        "ঞ" -> "ঞ, ইঞ"
                        "ট" -> "ট"
                        "ঠ" -> "ঠ"
                        "ড" -> "ড"
                        "ঢ" -> "ঢ"
                        "ণ" -> "ণ, মূর্ধন্য ণ"
                        "ত" -> "ত"
                        "থ" -> "থ"
                        "দ" -> "দ"
                        "ধ" -> "ধ"
                        "ন" -> "ন, দন্ত্য ন"
                        "প" -> "প"
                        "ফ" -> "ফ"
                        "ব" -> "ব"
                        "ভ" -> "ভ"
                        "ম" -> "ম"
                        "য" -> "য, অন্তঃস্থ য"
                        "র" -> "র"
                        "ল" -> "ল"
                        "শ" -> "শ, তালব্য শ"
                        "ষ" -> "ষ, মূর্ধন্য ষ"
                        "স" -> "স, দন্ত্য স"
                        "হ" -> "হ"
                        "ড়" -> "ড়, ড-এ বিন্দু ড়"
                        "ঢ়" -> "ঢ়, ঢ-এ বিন্দু ঢ়"
                        "য়" -> "য়, অন্তঃস্থ য়"
                        "ৎ" -> "ৎ, খণ্ড ত"
                        "ং" -> "ং, অনুস্বার"
                        "ঃ" -> "ঃ, বিসর্গ"
                        "ঁ" -> "ঁ, চন্দ্রবিন্দু"
                        "্" -> "্, হসন্ত"
                        "া" -> "আ-কার"
                        "ি" -> "ই-কার"
                        "ী" -> "ঈ-কার"
                        "ু" -> "উ-কার"
                        "ূ" -> "ঊ-কার"
                        "ৃ" -> "ঋ-কার"
                        "ে" -> "এ-কার"
                        "ৈ" -> "ঐ-কার"
                        "ো" -> "ও-কার"
                        "ৌ" -> "ঔ-কার"
                        "।" -> "দাঁড়ি (Dari)"
                        "॥" -> "ডাবল দাঁড়ি (Double Dari)"
                        else -> label
                    }
                }
            }
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
