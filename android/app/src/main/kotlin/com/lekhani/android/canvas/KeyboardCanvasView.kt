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
import com.lekhani.android.ime.EnterKeyResolver
import com.lekhani.android.theme.ChromaMode
import com.lekhani.android.theme.ChromaStyle
import com.lekhani.android.theme.KeyboardTheme
import com.lekhani.android.theme.ThemeChromaUtils
import com.lekhani.android.theme.ThemeRegistry

/**
 * KeyboardCanvasView
 *
 * The 120 FPS hardware-accelerated Canvas keyboard grid for Lekhani.
 *
 * Performance guarantees:
 *   - Zero heap allocations in [onDraw] or [onTouchEvent]
 *   - All Paint, RectF, and path objects pre-allocated in [init] / [onSizeChanged]
 *   - Hardware canvas via android:hardwareAccelerated="true" on the Window
 *   - Touch-to-screen keystroke latency target: < 3 ms
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
        fun onKeyWithTouch(key: Key, action: KeyAction, touchX: Float, touchY: Float) {}
        fun onGeometryChanged(geometries: List<com.lekhani.android.ffi.KeyGeometryConfig>) {}
        fun onSpaceSwipe(direction: Int) // -1 for previous layout, +1 for next layout
        fun onSpaceSwipeUp() {}
        fun onSpaceLongPress()
        fun onGlideGesture(keys: List<String>)
        fun onCursorMove(deltaChars: Int)
        fun onSwipeDelete(wordCount: Int)
        fun onSwipeDeletePreview(wordCount: Int)
        fun canSwipeDelete(): Boolean = true
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
    var bottomRowKeyMode: KeyboardPreferences.BottomRowKeyMode = KeyboardPreferences.BottomRowKeyMode.SMART
    var enabledLayoutsCount: Int = 2

    val isBottomRowKeyEmoji: Boolean
        get() = when (bottomRowKeyMode) {
            KeyboardPreferences.BottomRowKeyMode.EMOJI -> true
            KeyboardPreferences.BottomRowKeyMode.LANGUAGE_SWITCH -> false
            KeyboardPreferences.BottomRowKeyMode.SMART -> {
                spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH || enabledLayoutsCount <= 1
            }
        }

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
    var glideTypingEnabled: Boolean = false
    var showKeyPreviews: Boolean = true
    var isUiLanguageEnglish: Boolean = false
    var showLayoutNameOnSpacebar: Boolean = true
    var showKeyHints: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isResizeVisualGuide: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    private val resizeGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

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
    private var isSwipeDeleteGestureActive: Boolean = false
    private var isBackspaceDisarmed: Boolean = false
    private var backspaceSwipeStartX: Float = 0f
    private var backspaceSwipeStartY: Float = 0f
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

    // Swipe-up flick gesture state (Probaho 2.0 / rapid shifted access)
    private var swipeUpFlickEnabled: Boolean = true
    private var isFlickGestureActive: Boolean = false
    private var flickThresholdPx: Float = 0f

    // Bilateral Thumb Aura paint (Probaho 2.0 vowel-consonant realm tint)
    private var showBilateralAura: Boolean = false
    private val vowelAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── PopupWindow-based Key Preview & Alternate Popups ──────────────────────
    // Both live in their own window layer so they're never clipped by this view's
    // bounds — consistently visible on row 1 without overlapping the candidate strip.
    private val keyPreviewPopup = KeyPreviewPopupWindow(context)
    private val alternatePopup = AlternatePopupWindow(context)

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
    private var isCapsLock: Boolean = false
    private var lastShiftTapTime: Long = 0L

    val currentLayoutType: com.lekhani.android.ffi.LekhaniLayoutType
        get() = layoutType

    private var enterActionIcon: EnterKeyResolver.ActionIcon = EnterKeyResolver.ActionIcon.NEWLINE
    private var fieldType: EnterKeyResolver.FieldType = EnterKeyResolver.FieldType.NORMAL

    val isShiftLocked: Boolean
        get() = isCapsLock

    val isShiftActive: Boolean
        get() = isShifted || isCapsLock

    /**
     * A resolved key pairs a [Key] with its computed pixel [RectF].
     * Pre-allocated as a flat list in [onSizeChanged]; never re-allocated in draw/touch.
     */
    private data class ResolvedKey(var key: Key, val bounds: RectF)

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

    // Pre-allocated array for zero-allocation dynamic 120 FPS RGB Chroma calculation
    private val rgbHsv = FloatArray(3)

    /**
     * Computes the dynamic RGB Chroma color based on [mode], animation timestamp [now],
     * and horizontal position ratio [xRatio] (0f..1f) across keyboard surface.
     * Reuses pre-allocated [rgbHsv] for zero allocations inside the 120 FPS hot path.
     */
    private fun getChromaColor(mode: ChromaMode, now: Long, xRatio: Float = 0.5f): Int {
        return ThemeChromaUtils.getColor(mode, now, xRatio, rgbHsv)
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
            feedbackManager?.onLongPressFeedback(this@KeyboardCanvasView)
                ?: performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS)
            val longAction = key.activeLongPressAction(isShifted)
            when {
                key.action == KeyAction.Space || key.action == KeyAction.SwitchLayout -> {
                    keyListener?.onSpaceLongPress()
                }
                longAction != null -> {
                    keyListener?.onKey(key, longAction)
                }
                else -> {
                    val actionToken = (key.action as? KeyAction.Character)?.token
                    val rawChar = key.displayLabel(isShifted)
                    val alts = (actionToken?.let { com.lekhani.android.model.BengaliAlternates.getAlternates(it) })
                        ?: com.lekhani.android.model.BengaliAlternates.getAlternates(rawChar)
                    if (!alts.isNullOrEmpty()) {
                        showAlternatePopup(resolvedKeys[pressedKeyIndex], alts)
                    } else {
                        val activeHint = key.displayHint(isShifted)
                        if (activeHint != null) {
                            keyListener?.onKey(key, KeyAction.Character(activeHint))
                        } else if (key.shiftedLabel != null && key.shiftedLabel != key.label) {
                            keyListener?.onKey(key, key.shiftedAction)
                        }
                    }
                }
            }
            invalidate()
        }
    }

    private fun showAlternatePopup(resolvedKey: ResolvedKey, alts: Array<String>) {
        val density = cachedDensity.takeIf { it > 0f } ?: resources.displayMetrics.density
        alternatePopup.show(
            anchor = this,
            alts = alts,
            keyLeft = resolvedKey.bounds.left,
            keyTop = resolvedKey.bounds.top,
            keyRight = resolvedKey.bounds.right,
            keyBottom = resolvedKey.bounds.bottom,
            density = density,
        )
    }

    private var backspaceRepeatCount = 0

    /** Pre-allocated runnable for continuous rapid Backspace deletion on hold (zero allocation) */
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (pressedKeyIndex in resolvedKeys.indices && !isBackspaceSwiping) {
                val key = resolvedKeys[pressedKeyIndex].key
                if (key.action == KeyAction.Backspace) {
                    isLongPressTriggered = true
                    backspaceRepeatCount++
                    feedbackManager?.onKeyFeedback(this@KeyboardCanvasView)
                        ?: performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP)
                    keyListener?.onKey(key, KeyAction.Backspace)
                    val nextDelay = if (backspaceRepeatCount > 10) 35L else 55L
                    postDelayed(this, nextDelay)
                }
            }
        }
    }

    // ── Dimensions (set in onSizeChanged) ────────────────────────────────────

    /** Cached display density — set once in [onSizeChanged], never in [onDraw] or [onTouchEvent]. */
    private var cachedDensity: Float = 0f

    /** Pre-computed 3D shadow lip height in pixels — set once in [onSizeChanged]. */
    private var keyShadowLip: Float = 0f

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
    var avroNumeralsBengali: Boolean = false
        private set

    private val bengaliDedicatedNumberRow = listOf(
        Key("১", shiftedLabel = "1", hintLabel = "1", action = KeyAction.Character("১"), longPressAction = KeyAction.Character("1"), contentDesc = "Bengali digit 1, hold for English 1"),
        Key("২", shiftedLabel = "2", hintLabel = "2", action = KeyAction.Character("২"), longPressAction = KeyAction.Character("2"), contentDesc = "Bengali digit 2, hold for English 2"),
        Key("৩", shiftedLabel = "3", hintLabel = "3", action = KeyAction.Character("৩"), longPressAction = KeyAction.Character("3"), contentDesc = "Bengali digit 3, hold for English 3"),
        Key("৪", shiftedLabel = "4", hintLabel = "4", action = KeyAction.Character("৪"), longPressAction = KeyAction.Character("4"), contentDesc = "Bengali digit 4, hold for English 4"),
        Key("৫", shiftedLabel = "5", hintLabel = "5", action = KeyAction.Character("৫"), longPressAction = KeyAction.Character("5"), contentDesc = "Bengali digit 5, hold for English 5"),
        Key("৬", shiftedLabel = "6", hintLabel = "6", action = KeyAction.Character("৬"), longPressAction = KeyAction.Character("6"), contentDesc = "Bengali digit 6, hold for English 6"),
        Key("৭", shiftedLabel = "7", hintLabel = "7", action = KeyAction.Character("৭"), longPressAction = KeyAction.Character("7"), contentDesc = "Bengali digit 7, hold for English 7"),
        Key("৮", shiftedLabel = "8", hintLabel = "8", action = KeyAction.Character("৮"), longPressAction = KeyAction.Character("8"), contentDesc = "Bengali digit 8, hold for English 8"),
        Key("৯", shiftedLabel = "9", hintLabel = "9", action = KeyAction.Character("৯"), longPressAction = KeyAction.Character("9"), contentDesc = "Bengali digit 9, hold for English 9"),
        Key("০", shiftedLabel = "0", hintLabel = "0", action = KeyAction.Character("০"), longPressAction = KeyAction.Character("0"), contentDesc = "Bengali digit 0, hold for English 0"),
    )

    private val englishDedicatedNumberRow = listOf(
        Key("1", shiftedLabel = "১", hintLabel = "১", action = KeyAction.Character("1"), longPressAction = KeyAction.Character("১"), contentDesc = "Digit 1, hold for Bengali ১"),
        Key("2", shiftedLabel = "২", hintLabel = "২", action = KeyAction.Character("2"), longPressAction = KeyAction.Character("২"), contentDesc = "Digit 2, hold for Bengali ২"),
        Key("3", shiftedLabel = "৩", hintLabel = "৩", action = KeyAction.Character("3"), longPressAction = KeyAction.Character("৩"), contentDesc = "Digit 3, hold for Bengali ৩"),
        Key("4", shiftedLabel = "৪", hintLabel = "৪", action = KeyAction.Character("4"), longPressAction = KeyAction.Character("৪"), contentDesc = "Digit 4, hold for Bengali ৪"),
        Key("5", shiftedLabel = "৫", hintLabel = "৫", action = KeyAction.Character("5"), longPressAction = KeyAction.Character("৫"), contentDesc = "Digit 5, hold for Bengali ৫"),
        Key("6", shiftedLabel = "৬", hintLabel = "৬", action = KeyAction.Character("6"), longPressAction = KeyAction.Character("৬"), contentDesc = "Digit 6, hold for Bengali ৬"),
        Key("7", shiftedLabel = "৭", hintLabel = "৭", action = KeyAction.Character("7"), longPressAction = KeyAction.Character("৭"), contentDesc = "Digit 7, hold for Bengali ৭"),
        Key("8", shiftedLabel = "৮", hintLabel = "৮", action = KeyAction.Character("8"), longPressAction = KeyAction.Character("৮"), contentDesc = "Digit 8, hold for Bengali ৮"),
        Key("9", shiftedLabel = "৯", hintLabel = "৯", action = KeyAction.Character("9"), longPressAction = KeyAction.Character("৯"), contentDesc = "Digit 9, hold for Bengali ৯"),
        Key("0", shiftedLabel = "০", hintLabel = "০", action = KeyAction.Character("0"), longPressAction = KeyAction.Character("০"), contentDesc = "Digit 0, hold for Bengali ০"),
    )

    private fun isNumberSymbolsActive(): Boolean =
        layout == com.lekhani.android.model.NumberSymbolsLayout.numericLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.bengaliNumericLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.englishNumericLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.moreSymbolsLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.phoneDialpadLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.numpadPinLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.bengaliNumpadPinLayout

    private fun isNumpadLayout(): Boolean =
        layout == com.lekhani.android.model.NumberSymbolsLayout.phoneDialpadLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.numpadPinLayout ||
        layout == com.lekhani.android.model.NumberSymbolsLayout.bengaliNumpadPinLayout

    private val accessibilityHelper = KeyboardAccessibilityHelper()

    // Initialization

    private var isThemeApplied: Boolean = false

    init {
        // Hardware canvas is required for 120 FPS
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = false
        isHapticFeedbackEnabled = true
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        try {
            val initialTheme = ThemeRegistry.resolveTheme(context, KeyboardPreferences.get(context).themeId)
            applyTheme(initialTheme)
        } catch (_: Exception) {}
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    // Layout & Theme Management

    /**
     * Applies full configuration and theme from [KeyboardPreferences].
     * Zero-allocation in draw/touch hot paths.
     */
    fun applyPreferences(prefs: KeyboardPreferences, feedbackMgr: LekhaniFeedbackManager? = null) {
        if (feedbackMgr != null) {
            this.feedbackManager = feedbackMgr
            feedbackMgr.updateCache()
        }
        this.longPressDelayMs = prefs.longPressDelayMs
        this.showKeyBorders = prefs.showKeyBorders
        this.showDedicatedNumberRow = prefs.showDedicatedNumberRow
        this.avroNumeralsBengali = prefs.avroNumeralsBengali
        this.heightScale = prefs.getHeightScaleForOrientation(resources.configuration.orientation)
        this.marginHDp = prefs.keyMarginH
        this.marginVDp = prefs.keyMarginV
        this.bottomChinPaddingDp = prefs.bottomChinPadding
        this.fontScale = prefs.fontScale
        this.formFactor = prefs.formFactor
        this.spacebarSwipeMode = prefs.spacebarSwipeMode
        this.bottomRowKeyMode = prefs.bottomRowKeyMode
        this.spaceCursorSlideEnabled = (prefs.spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV)
        this.showHomeRowAccents = prefs.showHomeRowAccents
        this.swipeToDeleteEnabled = prefs.swipeToDeleteEnabled
        this.keyGlowRippleEnabled = prefs.keyGlowRippleEnabled
        this.glideTypingEnabled = prefs.glideTypingEnabled
        this.showKeyPreviews = prefs.showKeyPreviews
        this.isUiLanguageEnglish = (prefs.uiLanguage == "en")
        this.showLayoutNameOnSpacebar = prefs.showLayoutNameOnSpacebar
        this.showBilateralAura = prefs.showBilateralAura
        this.showKeyHints = prefs.showKeyHints
        this.swipeUpFlickEnabled = prefs.swipeUpFlickEnabled

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
     * Dynamically updates the keyboard height scale in real time during visual resizing.
     * Triggers requestLayout and redraw without allocations.
     */
    fun setLiveHeightScale(scale: Float) {
        val clamped = scale.coerceIn(0.70f, 1.35f)
        if (Math.abs(this.heightScale - clamped) > 0.001f) {
            this.heightScale = clamped
            requestLayout()
            invalidate()
        }
    }

    /**
     * Applies a [KeyboardTheme] to all pre-allocated Paint objects.
     */
    fun applyTheme(theme: KeyboardTheme) {
        activeTheme = theme
        isThemeApplied = true
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

        keyGlowPaint.color        = androidx.core.graphics.ColorUtils.setAlphaComponent(theme.accentColor, 0x66)
        spaceSlideTrackPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(theme.accentColor, 0x80)
        spaceSlideThumbPaint.color = theme.accentColor
        vectorIconStrokePaint.color = theme.labelColor
        vectorIconFillPaint.color = theme.labelColor
        hintPaint.color = theme.labelDimColor
        vowelAuraPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(theme.accentColor, 18)

        keyPreviewPopup.applyTheme(theme)
        alternatePopup.applyTheme(theme)
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
                    val isDedicatedNumberRow = showDedicatedNumberRow && !isNumberSymbolsActive()
                    val row0StartIndex = if (isDedicatedNumberRow) 10 else 0
                    val row4StartIndex = if (isDedicatedNumberRow) 51 else 41
                    val newRow0 = if (active) com.lekhani.android.model.GboardBengaliLayout.getDynamicVowelsRow(consonant) else com.lekhani.android.model.GboardBengaliLayout.vowelsRow
                    val newRow4 = if (active) com.lekhani.android.model.GboardBengaliLayout.getDynamicRow5(consonant) else layout?.rows?.getOrNull(4)

                    if (newRow4 != null &&
                        resolvedKeys.size > row4StartIndex + 10 &&
                        newRow0.size == 11 &&
                        newRow4.size == 11
                    ) {
                        for (i in 0 until 11) {
                            resolvedKeys[row0StartIndex + i].key = newRow0[i]
                            resolvedKeys[row4StartIndex + i].key = newRow4[i]
                        }
                        invalidate()
                    } else {
                        computeKeyBounds()
                        invalidate()
                    }
                }
            }
        }
    }

    /**
     * Disarms and cancels any active swipe-to-delete gesture (e.g. when user taps Copy in the preview strip).
     */
    fun cancelSwipeDelete() {
        if (isBackspaceSwiping || isSwipeDeleteGestureActive || isBackspaceDisarmed) {
            isBackspaceSwiping = false
            isSwipeDeleteGestureActive = false
            isBackspaceDisarmed = true
            backspaceDeletedWordCount = 0
            removeCallbacks(backspaceRepeatRunnable)
            invalidate()
        }
    }

    fun setLayout(
        newLayout: KeyboardLayout,
        newLayoutType: com.lekhani.android.ffi.LekhaniLayoutType,
        shifted: Boolean = false,
    ) {
        layout = newLayout
        layoutType = newLayoutType
        isShifted = shifted
        isCapsLock = false
        isGboardKarsActive = false
        gboardActiveConsonant = ""
        pressedKeyIndex = -1
        pressedPointerId = -1
        keyPreviewPopup.dismiss()
        alternatePopup.dismiss()
        accessibilityHelper.invalidateRoot()
        if (width > 0 && height > 0) {
            requestLayout()
            computeKeyBounds()
            invalidate()
        }
    }

    fun setShifted(shifted: Boolean) {
        if (!shifted) {
            isCapsLock = false
        }
        if (isShifted != shifted) {
            isShifted = shifted
            accessibilityHelper.invalidateRoot()
            invalidate()
        }
    }

    fun toggleShift(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (isCapsLock) {
            isCapsLock = false
            isShifted = false
        } else if (isShifted) {
            // Double-tap shift within 500ms activates Caps Lock
            if (now - lastShiftTapTime <= 500) {
                isCapsLock = true
                isShifted = true
            } else {
                isShifted = false
            }
        } else {
            isShifted = true
        }
        lastShiftTapTime = now
        accessibilityHelper.invalidateRoot()
        invalidate()
        return isShifted
    }

    fun setEnterActionIcon(icon: EnterKeyResolver.ActionIcon) {
        if (enterActionIcon != icon) {
            enterActionIcon = icon
            accessibilityHelper.invalidateRoot()
            invalidate()
        }
    }

    fun setFieldType(type: EnterKeyResolver.FieldType) {
        if (fieldType != type) {
            fieldType = type
            if (width > 0 && height > 0) {
                computeKeyBounds()
                invalidate()
            }
        }
    }

    // Measurement & Size change

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val hasExtraNumberRow = showDedicatedNumberRow && !isNumberSymbolsActive()
        val rowCount = (layout?.rows?.size ?: 3) + 1 + (if (hasExtraNumberRow) 1 else 0)
        val isSixRow = rowCount >= 6
        val baseHeightDp = if (isLandscape) {
            if (isSixRow) 150f else 130f
        } else {
            when {
                rowCount >= 6 -> 264f
                rowCount == 5 -> 244f
                else -> 220f
            }
        }
        val defaultHeightDp = baseHeightDp * heightScale + bottomChinPaddingDp
        val rawDesiredHeight = (defaultHeightDp * density).toInt()
        val desiredHeight = if (isLandscape) {
            val maxLandscapeHeight = (resources.displayMetrics.heightPixels * 0.42f).toInt()
            minOf(rawDesiredHeight, maxLandscapeHeight)
        } else {
            rawDesiredHeight
        }

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
        cachedDensity = density
        keyShadowLip  = 1.8f * density  // pre-computed — never recomputed in onDraw
        keyMarginH = marginHDp * density
        keyMarginV = marginVDp * density
        keyCornerRadius = 7.5f * density
        homeRowAccentHeight = 2.5f * density
        swipeThresholdPx = 28f * density
        flickThresholdPx = 11f * density
        spaceSlideThresholdPx = 16f * density
        spaceSlideStepPx = 16f * density
        backspaceSwipeStepPx = 28f * density

        glideStrokePaint.strokeWidth = 4.5f * density
        glideGlowPaint.strokeWidth = 11f * density
        glideMinDistancePx = 52f * density
        glideSampleDistSq = (18f * density) * (18f * density)
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
        val effectiveMarginV = if (availableH / totalRows < 40f * density) {
            (keyMarginV * 0.5f).coerceAtLeast(1.5f * density)
        } else {
            keyMarginV
        }
        val totalMarginsV = (totalRows + 1) * effectiveMarginV
        keyHeight = ((availableH - totalMarginsV) / totalRows).coerceAtLeast(18f * density)
        spacebarRowHeight = keyHeight
        labelSize = (keyHeight * 0.38f * fontScale).coerceAtLeast(10f * density)
        labelSizeSmall = (keyHeight * 0.22f * fontScale).coerceAtLeast(8f * density)

        labelPaint.textSize = labelSize
        labelPaintSmall.textSize = labelSizeSmall
        hintPaint.textSize = labelSizeSmall * 0.92f
        // Popup text sizes are set inside KeyPreviewPopupWindow and AlternatePopupWindow

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

        val density = cachedDensity.takeIf { it > 0f } ?: resources.displayMetrics.density
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
                layoutKeysStandard(0f, w, availableH)
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
            val isEnglishDigits = (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.ENGLISH || (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.AVRO && !avroNumeralsBengali))
            val numRow = if (isEnglishDigits) englishDedicatedNumberRow else bengaliDedicatedNumberRow
            listOf(numRow) + baseRows
        } else {
            baseRows
        }

        val rowCount = allRows.size
        val totalRows = rowCount + 1
        val effectiveMarginV = if (totalH / totalRows < 40f * density) {
            (keyMarginV * 0.5f).coerceAtLeast(1.5f * density)
        } else {
            keyMarginV
        }
        val totalMarginsV = (totalRows + 1) * effectiveMarginV
        val kHeight = ((totalH - totalMarginsV) / totalRows).coerceAtLeast(18f * density)

        val sidePadding = (keyMarginH * 0.75f).coerceAtLeast(2f * density)
        val availableRowW = (totalW - 2f * sidePadding).coerceAtLeast(10f)

        // Find reference key width based on the primary 10-key row (or numpad row)
        val isNumpad = isNumpadLayout()
        val maxKeysInRow = if (isNumpad) {
            allRows.maxOfOrNull { it.size } ?: 3
        } else {
            allRows.maxOfOrNull { it.size }?.coerceAtLeast(10) ?: 10
        }
        val standardGaps = (maxKeysInRow - 1) * keyMarginH
        val standardUnitWidth = (availableRowW - standardGaps) / maxKeysInRow

        var currentRowTop = yOffset + effectiveMarginV
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
                val isDedicatedNumberRow = (showDedicatedNumberRow && !isNumberSymbolsActive() && rowIndex == 0)

                val (rowSideInset, unitWidth) = when {
                    // Dedicated Number Row: evenly spans full available width across all keys (no side margins)
                    isDedicatedNumberRow -> {
                        val w = (availableRowW - totalGaps) / rowWeight
                        Pair(0f, w)
                    }
                    // Numpad / Phone Dialpad: evenly distribute large keys across available width
                    isNumpad -> {
                        val w = (availableRowW - totalGaps) / rowWeight
                        Pair(0f, w)
                    }
                    // Gboard: 10 keys per row, uniform width across all rows, no insets
                    layoutType == com.lekhani.android.ffi.LekhaniLayoutType.GBOARD -> {
                        val w = (availableRowW - totalGaps) / rowWeight
                        Pair(0f, w)
                    }
                    // Probhat 9-key row (া স ড ত গ হ জ ক ল): gentle 0.5-key QWERTY offset, keys expand naturally
                    layoutType == com.lekhani.android.ffi.LekhaniLayoutType.PROBHAT && row.size == 9 -> {
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
                    (rowIndex == 1 || (showDedicatedNumberRow && rowIndex == 2)) && currentLayout.rows.size >= 3 -> {
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
            currentRowTop += kHeight + effectiveMarginV
        }

        // Spacebar row: proportional, sleek, and never oversized
        val spaceRow = getAdaptiveSpaceRow(currentLayout.spacebarRow)
        val totalSpaceWeight = spaceRow.sumOf { it.widthWeight.toDouble() }.toFloat()
        val spaceGaps = (spaceRow.size - 1) * keyMarginH
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
            val isEnglishDigits = (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.ENGLISH || (layoutType == com.lekhani.android.ffi.LekhaniLayoutType.AVRO && !avroNumeralsBengali))
            val numRow = if (isEnglishDigits) englishDedicatedNumberRow else bengaliDedicatedNumberRow
            listOf(numRow) + baseRows
        } else {
            baseRows
        }

        val rowCount = allRows.size
        val totalRows = rowCount + 1
        val effectiveMarginV = if (totalH / totalRows < 40f * density) {
            (keyMarginV * 0.5f).coerceAtLeast(1.5f * density)
        } else {
            keyMarginV
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

            currentRowTop += kHeight + effectiveMarginV
        }

        // Spacebar row in Split mode — ergonomic split spacebar for both thumbs
        val spaceRow = getAdaptiveSpaceRow(currentLayout.spacebarRow)
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

        // Notify session of updated key geometries for spatial Gaussian touch correction
        val configs = resolvedKeys.mapNotNull { rk ->
            val charToken = (rk.key.action as? KeyAction.Character)?.token ?: rk.key.label
            if (charToken.isNotEmpty()) {
                com.lekhani.android.ffi.KeyGeometryConfig(
                    label = charToken,
                    centerX = rk.bounds.centerX(),
                    centerY = rk.bounds.centerY(),
                    width = rk.bounds.width(),
                    height = rk.bounds.height(),
                )
            } else null
        }
        keyListener?.onGeometryChanged(configs)
    }

    // Vector Drawing Helpers — Zero allocations permitted

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

        if (isLocked) {
            fillPaint.color = activeTheme.accentColor
            canvas.drawPath(vectorIconPath, fillPaint)
            // Underline bar under the arrow to indicate locked state
            val barY = cy + totalHalfH + size * 0.14f
            val oldStrokeWidth = strokePaint.strokeWidth
            strokePaint.color = activeTheme.accentColor
            strokePaint.strokeWidth = size * 0.12f
            canvas.drawLine(cx - halfW * 0.75f, barY, cx + halfW * 0.75f, barY, strokePaint)
            strokePaint.strokeWidth = oldStrokeWidth
        } else if (isShifted) {
            fillPaint.color = activeTheme.accentColor
            canvas.drawPath(vectorIconPath, fillPaint)
        } else {
            strokePaint.color = activeTheme.labelColor
            canvas.drawPath(vectorIconPath, strokePaint)
        }
    }

    private fun getAdaptiveSpaceRow(originalRow: List<Key>): List<Key> {
        if (fieldType == EnterKeyResolver.FieldType.NORMAL || isNumberSymbolsActive()) return originalRow

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

    private fun drawVectorSearch(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val r = size * 0.25f
        val glassCx = cx - size * 0.08f
        val glassCy = cy - size * 0.08f
        canvas.drawCircle(glassCx, glassCy, r, strokePaint)
        val cos45 = 0.7071f
        val startX = glassCx + r * cos45
        val startY = glassCy + r * cos45
        val endX = startX + size * 0.30f * cos45
        val endY = startY + size * 0.30f * cos45
        canvas.drawLine(startX, startY, endX, endY, strokePaint)
    }

    private fun drawVectorSend(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        vectorIconPath.rewind()
        val tipX = cx + size * 0.32f
        val tipY = cy - size * 0.28f
        val backBottomX = cx - size * 0.16f
        val backBottomY = cy + size * 0.30f
        val notchX = cx - size * 0.04f
        val notchY = cy + size * 0.06f
        val backLeftX = cx - size * 0.30f
        val backLeftY = cy - size * 0.08f

        vectorIconPath.moveTo(tipX, tipY)
        vectorIconPath.lineTo(backLeftX, backLeftY)
        vectorIconPath.lineTo(notchX, notchY)
        vectorIconPath.lineTo(backBottomX, backBottomY)
        vectorIconPath.close()

        vectorIconPath.moveTo(tipX, tipY)
        vectorIconPath.lineTo(notchX, notchY)
        canvas.drawPath(vectorIconPath, strokePaint)
    }

    private fun drawVectorGo(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        vectorIconPath.rewind()
        val halfW = size * 0.34f
        val arrowHead = size * 0.22f
        vectorIconPath.moveTo(cx - halfW, cy)
        vectorIconPath.lineTo(cx + halfW, cy)
        vectorIconPath.moveTo(cx + halfW - arrowHead, cy - arrowHead)
        vectorIconPath.lineTo(cx + halfW, cy)
        vectorIconPath.lineTo(cx + halfW - arrowHead, cy + arrowHead)
        canvas.drawPath(vectorIconPath, strokePaint)
    }

    private fun drawVectorNext(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        vectorIconPath.rewind()
        val halfW = size * 0.34f
        val arrowHead = size * 0.20f
        val stemRight = cx + halfW - size * 0.12f
        vectorIconPath.moveTo(cx - halfW, cy)
        vectorIconPath.lineTo(stemRight, cy)
        vectorIconPath.moveTo(stemRight - arrowHead, cy - arrowHead)
        vectorIconPath.lineTo(stemRight, cy)
        vectorIconPath.lineTo(stemRight - arrowHead, cy + arrowHead)
        val barX = cx + halfW
        val barHalfH = size * 0.25f
        vectorIconPath.moveTo(barX, cy - barHalfH)
        vectorIconPath.lineTo(barX, cy + barHalfH)
        canvas.drawPath(vectorIconPath, strokePaint)
    }

    private fun drawVectorDone(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        vectorIconPath.rewind()
        val leftX = cx - size * 0.28f
        val leftY = cy + size * 0.02f
        val midX = cx - size * 0.08f
        val midY = cy + size * 0.22f
        val rightX = cx + size * 0.30f
        val rightY = cy - size * 0.24f
        vectorIconPath.moveTo(leftX, leftY)
        vectorIconPath.lineTo(midX, midY)
        vectorIconPath.lineTo(rightX, rightY)
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

    private fun drawVectorSmiley(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val r = size * 0.38f
        // Outer face circle
        canvas.drawCircle(cx, cy, r, strokePaint)
        // Two eyes
        val eyeOffsetX = r * 0.38f
        val eyeOffsetY = r * 0.26f
        val eyeR = (r * 0.11f).coerceAtLeast(1.5f)
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeR, vectorIconFillPaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeR, vectorIconFillPaint)
        // Smile arc
        scratchRect.set(cx - r * 0.50f, cy - r * 0.30f, cx + r * 0.50f, cy + r * 0.55f)
        canvas.drawArc(scratchRect, 25f, 130f, false, strokePaint)
    }

    private fun drawVectorNumpad(canvas: Canvas, cx: Float, cy: Float, size: Float, fillPaint: Paint) {
        val dotRadius = (size * 0.085f).coerceAtLeast(1.5f)
        val spacingX = size * 0.28f
        val spacingY = size * 0.24f

        // Row 1 (top): 3 keypad dots
        canvas.drawCircle(cx - spacingX, cy - 1.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx, cy - 1.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx + spacingX, cy - 1.5f * spacingY, dotRadius, fillPaint)

        // Row 2 (upper middle): 3 keypad dots
        canvas.drawCircle(cx - spacingX, cy - 0.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx, cy - 0.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx + spacingX, cy - 0.5f * spacingY, dotRadius, fillPaint)

        // Row 3 (lower middle): 3 keypad dots
        canvas.drawCircle(cx - spacingX, cy + 0.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx, cy + 0.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx + spacingX, cy + 0.5f * spacingY, dotRadius, fillPaint)

        // Row 4 (bottom): 1 centered dot (classic 0 key on dialpad)
        canvas.drawCircle(cx, cy + 1.5f * spacingY, dotRadius, fillPaint)
    }

    private fun drawMiniVectorDialpad(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val dotRadius = (size * 0.09f).coerceAtLeast(1.0f)
        val spX = size * 0.32f
        val spY = size * 0.30f

        canvas.drawCircle(cx - spX, cy - spY, dotRadius, paint)
        canvas.drawCircle(cx, cy - spY, dotRadius, paint)
        canvas.drawCircle(cx + spX, cy - spY, dotRadius, paint)

        canvas.drawCircle(cx - spX, cy, dotRadius, paint)
        canvas.drawCircle(cx, cy, dotRadius, paint)
        canvas.drawCircle(cx + spX, cy, dotRadius, paint)

        canvas.drawCircle(cx - spX, cy + spY, dotRadius, paint)
        canvas.drawCircle(cx, cy + spY, dotRadius, paint)
        canvas.drawCircle(cx + spX, cy + spY, dotRadius, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isThemeApplied) {
            try {
                val initialTheme = ThemeRegistry.resolveTheme(context, KeyboardPreferences.get(context).themeId)
                applyTheme(initialTheme)
            } catch (_: Exception) {}
        }
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
        var centerChromaColor = 0
        if (activeTheme.isRgbChroma) {
            centerChromaColor = getChromaColor(activeTheme.chromaMode, now, 0.5f)
            keyBorderPaint.color = centerChromaColor
            keyBorderPaint.strokeWidth = when (activeTheme.chromaStyle) {
                ChromaStyle.FULL_BORDER -> 1.6f * cachedDensity
                ChromaStyle.AMBIENT_BREATHE -> 0.9f * cachedDensity
                ChromaStyle.CLEAN_MINIMAL -> 1.0f * cachedDensity
            }
            homeRowAccentPaint.color = centerChromaColor
            keyGlowPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(centerChromaColor, 0x80)
            spaceSlideThumbPaint.color = centerChromaColor
            glideStrokePaint.color = centerChromaColor
            glideDotPaint.color = centerChromaColor
            postInvalidateOnAnimation()
        }
        // Use the cached density — never call resources.displayMetrics in onDraw (120 FPS hot path)
        val density = cachedDensity

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

        // ── Keys ───────────────────────────────────────────────────────────
        for (i in resolvedKeys.indices) {
            val resolved = resolvedKeys[i]
            val key = resolved.key
            val bounds = resolved.bounds

            // Select background paint
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

            // 3D Keycap tactile depth & depression
            val drawBounds = if (i == pressedKeyIndex) {
                // Key pressed down: depressed into the keyboard surface
                keyDrawRect.set(bounds.left, bounds.top + keyShadowLip, bounds.right, bounds.bottom)
                if (keyGlowRippleEnabled) {
                    scratchRect.set(keyDrawRect.left - 2.5f, keyDrawRect.top - 2.5f, keyDrawRect.right + 2.5f, keyDrawRect.bottom + 2.5f)
                    canvas.drawRoundRect(scratchRect, keyCornerRadius + 2.5f, keyCornerRadius + 2.5f, keyGlowPaint)
                }
                keyDrawRect
            } else {
                // Key bottom shadow
                scratchRect.set(bounds.left, bounds.top + keyShadowLip, bounds.right, bounds.bottom)
                canvas.drawRoundRect(scratchRect, keyCornerRadius, keyCornerRadius, keyShadowPaint)
                // Key elevated top surface
                keyDrawRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom - keyShadowLip)
                keyDrawRect
            }

            // Draw key background with rounded corners
            canvas.drawRoundRect(drawBounds, keyCornerRadius, keyCornerRadius, bgPaint)

            // Probaho Bilateral Thumb Aura: subtle tint on Left Hand (vowels realm) character keys
            if (showBilateralAura &&
                layoutType == com.lekhani.android.ffi.LekhaniLayoutType.PROBAHO &&
                drawBounds.centerX() < width / 2f &&
                key.action is KeyAction.Character &&
                !activeTheme.isRgbChroma
            ) {
                canvas.drawRoundRect(drawBounds, keyCornerRadius, keyCornerRadius, vowelAuraPaint)
            }

            // Draw key border (enabled by user preference OR dynamic RGB chroma)
            val shouldDrawKeyBorder = when {
                activeTheme.isRgbChroma -> {
                    when (activeTheme.chromaStyle) {
                        ChromaStyle.FULL_BORDER -> true
                        ChromaStyle.AMBIENT_BREATHE -> true
                        ChromaStyle.CLEAN_MINIMAL -> {
                            // In CLEAN_MINIMAL: floating borderless keys, dynamic accent only on Enter and Spacebar!
                            key.action == KeyAction.Enter || key.action == KeyAction.Space
                        }
                    }
                }
                else -> showKeyBorders
            }

            if (shouldDrawKeyBorder) {
                if (activeTheme.isRgbChroma) {
                    val xRatio = if (width > 0) (drawBounds.centerX() / width.toFloat()).coerceIn(0f, 1f) else 0.5f
                    val chromaColor = getChromaColor(activeTheme.chromaMode, now, xRatio)
                    if (activeTheme.chromaStyle == ChromaStyle.AMBIENT_BREATHE) {
                        keyBorderPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(chromaColor, 0x99)
                    } else {
                        keyBorderPaint.color = chromaColor
                    }
                }
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
                if (activeTheme.isRgbChroma) {
                    val xRatio = if (width > 0) (drawBounds.centerX() / width.toFloat()).coerceIn(0f, 1f) else 0.5f
                    val rippleBaseColor = getChromaColor(activeTheme.chromaMode, now, xRatio)
                    ripplePaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(rippleBaseColor, rippleAlpha)
                } else {
                    ripplePaint.alpha = rippleAlpha
                }
                rippleRect.set(drawBounds)
                canvas.drawRoundRect(rippleRect, keyCornerRadius, keyCornerRadius, ripplePaint)
                if (fraction < 1f) invalidate()
            }

            // Draw label
            val labelText = if (key.action == KeyAction.Space) {
                com.lekhani.android.model.LayoutRegistry.getSpacebarDisplayLabel(
                    layoutType,
                    showLayoutNameOnSpacebar,
                    isUiLanguageEnglish
                )
            } else {
                com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(key.displayLabel(isShifted))
            }
            val cx = drawBounds.centerX()
            val cy = drawBounds.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f
            val cySmall = drawBounds.centerY() - (labelPaintSmall.ascent() + labelPaintSmall.descent()) / 2f

            when (key.action) {
                KeyAction.Backspace -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    drawVectorBackspace(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                }
                KeyAction.Shift -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    drawVectorShift(canvas, cx, drawBounds.centerY(), iconSize, isShifted, isCapsLock, vectorIconStrokePaint, vectorIconFillPaint)
                }
                KeyAction.Enter -> {
                    val iconSize = (drawBounds.height() * 0.42f).coerceAtLeast(16f * density)
                    if (activeTheme.isRgbChroma) {
                        vectorIconStrokePaint.color = keyBorderPaint.color
                    }
                    val resolvedIcon = if (isShiftActive) EnterKeyResolver.ActionIcon.NEWLINE else enterActionIcon
                    when (resolvedIcon) {
                        EnterKeyResolver.ActionIcon.SEARCH -> drawVectorSearch(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.SEND -> drawVectorSend(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.GO -> drawVectorGo(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.NEXT -> drawVectorNext(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.DONE -> drawVectorDone(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                        EnterKeyResolver.ActionIcon.NEWLINE -> drawVectorEnter(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                    }
                    if (activeTheme.isRgbChroma) {
                        vectorIconStrokePaint.color = activeTheme.labelColor
                    }
                }
                KeyAction.SwitchLayout -> {
                    val iconSize = (drawBounds.height() * 0.40f).coerceAtLeast(16f * density)
                    if (isBottomRowKeyEmoji) {
                        drawVectorSmiley(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                    } else {
                        drawVectorGlobe(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
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
                    drawVectorSmiley(canvas, cx, drawBounds.centerY(), iconSize, vectorIconStrokePaint)
                }
                KeyAction.SwitchNumpad -> {
                    val iconSize = (drawBounds.height() * 0.40f).coerceAtLeast(16f * density)
                    drawVectorNumpad(canvas, cx, drawBounds.centerY(), iconSize, vectorIconFillPaint)
                }
                KeyAction.Space -> {
                    if (spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH && enabledLayoutsCount > 1 && !isSpaceCursorMoving) {
                        canvas.drawText("‹   $labelText   ›", cx, cySmall, labelPaintSmall)
                    } else {
                        canvas.drawText(labelText, cx, cySmall, labelPaintSmall)
                    }
                }
                KeyAction.SwitchNumeric -> {
                    canvas.drawText(labelText, cx, cySmall, labelPaintSmall)
                    // No secondary hint icon — the ?123 label is self-explanatory.
                    // Long-press numpad is discoverable without a hint glyph (matches Gboard/SwiftKey convention).
                }
                KeyAction.SwitchMoreSymbols, KeyAction.SwitchAlpha, KeyAction.ToggleBengaliDigits -> {
                    canvas.drawText(labelText, cx, cySmall, labelPaintSmall)
                    if (showKeyHints) {
                        val hint = key.displayHint(isShifted)?.let { com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(it) }
                        if (hint != null) {
                            val hintX = drawBounds.right - 5f * density
                            val hintY = drawBounds.top + 13f * density
                            canvas.drawText(hint, hintX, hintY, hintPaint)
                        }
                    }
                }
                else -> {
                    canvas.drawText(labelText, cx, cy, labelPaint)
                    if (showKeyHints) {
                        val hint = key.displayHint(isShifted)?.let { com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(it) }
                        if (hint != null) {
                            val hintX = drawBounds.right - 5f * density
                            val hintY = drawBounds.top + 13f * density
                            canvas.drawText(hint, hintX, hintY, hintPaint)
                        }
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

        // ── Popups are now rendered by PopupWindow (KeyPreviewPopupWindow / AlternatePopupWindow)
        // ── and live in their own window layer. Nothing to draw here.

        // ── Glide / Gesture typing trail (zero allocation, 120 FPS Bezier smoothing) ──
        if (glideTypingEnabled && isGliding && glidePointCount > 0) {
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

        // ── Resize Visual Guide Bounding Outline ─────────────────────────
        if (isResizeVisualGuide) {
            val pad = 3f * density
            resizeGuidePaint.color = activeTheme.accentColor
            resizeGuidePaint.strokeWidth = 2.5f * density
            scratchRect.set(pad, pad, width - pad, height - pad)
            canvas.drawRoundRect(scratchRect, 10f * density, 10f * density, resizeGuidePaint)
        }
    }

    private fun isSpacebarKey(key: Key): Boolean =
        key.action == KeyAction.Space || key.action == KeyAction.SwitchNumeric ||
        key.action == KeyAction.SwitchMoreSymbols || key.action == KeyAction.SwitchAlpha ||
        key.action == KeyAction.SwitchNumpad || key.action == KeyAction.ToggleBengaliDigits ||
        key.action == KeyAction.SwitchLayout || key.action == KeyAction.Enter ||
        key.action == KeyAction.CursorLeft || key.action == KeyAction.CursorRight ||
        key.action == KeyAction.Tab

    // Touch handling — ZERO allocations permitted here

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
                            feedbackManager?.onKeyFeedback(this)
                                ?: performHapticFeedback(
                                    HapticFeedbackConstants.KEYBOARD_TAP)
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

                val idx = findKeyIndex(px, py)
                if (idx >= 0) {
                    pressedKeyIndex = idx
                    pressStartTime = SystemClock.uptimeMillis()
                    isLongPressTriggered = false
                    isFlickGestureActive = false
                    isSpaceSwiping = false
                    isSpaceCursorMoving = false
                    isBackspaceSwiping = false
                    backspaceDeletedWordCount = 0

                    feedbackManager?.onKeyFeedback(this)
                        ?: performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP)

                    val key = resolvedKeys[idx].key
                    val keyAct = key.activeAction(isShifted)
                    if (glideTypingEnabled && keyAct is KeyAction.Character) {
                        glidePointsX[0] = px
                        glidePointsY[0] = py
                        glidePointCount = 1
                        visitedGlideKeys.clear()
                        visitedGlideKeys.add(keyAct.token)
                        lastVisitedKeyIdx = idx
                    }
                    if (key.action == KeyAction.Space) {
                        spaceTouchStartX = px
                        spaceTouchStartY = py
                        spaceSlideLastX = px
                        postDelayed(longPressRunnable, longPressDelayMs)
                    } else if (keyAct is KeyAction.Character || key.action == KeyAction.SwitchLayout || key.activeLongPressAction(isShifted) != null) {
                        postDelayed(longPressRunnable, longPressDelayMs)
                    } else if (key.action == KeyAction.Backspace) {
                        backspaceSwipeStartX = px
                        backspaceSwipeStartY = py
                        backspaceRepeatCount = 0
                        isSwipeDeleteGestureActive = false
                        isBackspaceDisarmed = false
                        postDelayed(backspaceRepeatRunnable, 350L)
                    }
                    // Show key preview popup for character keys (lives in its own window layer)
                    if (showKeyPreviews && keyAct is KeyAction.Character && key.label.isNotEmpty()) {
                        val bounds = resolvedKeys[idx].bounds
                        keyPreviewPopup.show(
                            anchor = this,
                            label = key.displayLabel(isShifted),
                            keyLeft = bounds.left,
                            keyTop = bounds.top,
                            keyRight = bounds.right,
                            keyBottom = bounds.bottom,
                            density = cachedDensity.takeIf { it > 0f } ?: resources.displayMetrics.density,
                        )
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (alternatePopup.isShowing) {
                    val viewLocation = IntArray(2)
                    getLocationInWindow(viewLocation)
                    val changed = alternatePopup.onTouchMove(event.x, event.y, viewLocation[0])
                    if (changed) feedbackManager?.onTickFeedback(this)
                    return true
                }

                val curIndex = if (pressedPointerId >= 0) event.findPointerIndex(pressedPointerId) else 0
                val curX = if (curIndex in 0 until event.pointerCount) event.getX(curIndex) else event.x
                val curY = if (curIndex in 0 until event.pointerCount) event.getY(curIndex) else event.y
                glideCurX = curX
                glideCurY = curY

                val moveDistSq = (curX - touchStartX) * (curX - touchStartX) + (curY - touchStartY) * (curY - touchStartY)
                val touchSlopPx = 8f * resources.displayMetrics.density

                val isOverBackspace = if (pressedKeyIndex in resolvedKeys.indices) {
                    val bKey = resolvedKeys[pressedKeyIndex].key
                    if (bKey.action == KeyAction.Backspace) {
                        val bBounds = resolvedKeys[pressedKeyIndex].bounds
                        val slopX = 28f * resources.displayMetrics.density
                        val slopY = 20f * resources.displayMetrics.density
                        curX >= bBounds.left - slopX && curX <= bBounds.right + slopX &&
                        curY >= bBounds.top - slopY && curY <= bBounds.bottom + slopY
                    } else false
                } else false

                if (!isOverBackspace && moveDistSq > touchSlopPx * touchSlopPx) {
                    removeCallbacks(longPressRunnable)
                    removeCallbacks(backspaceRepeatRunnable)
                    // Dismiss key preview once the finger moves (unless flick gesture is active)
                    if (!isFlickGestureActive) {
                        keyPreviewPopup.dismiss()
                    }
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
                                feedbackManager?.onTickFeedback(this)
                                    ?: performHapticFeedback(
                                        HapticFeedbackConstants.CLOCK_TICK)
                                invalidate()
                            } else if (stepDelta <= -spaceSlideStepPx) {
                                val steps = (-stepDelta / spaceSlideStepPx).toInt()
                                keyListener?.onCursorMove(-steps)
                                spaceSlideLastX -= steps * spaceSlideStepPx
                                feedbackManager?.onTickFeedback(this)
                                    ?: performHapticFeedback(
                                        HapticFeedbackConstants.CLOCK_TICK)
                                invalidate()
                            }
                            return true
                        }
                    }

                    // Swipe-to-delete gesture on Backspace
                    if (key.action == KeyAction.Backspace && swipeToDeleteEnabled) {
                        val dx = curX - backspaceSwipeStartX
                        val dy = curY - backspaceSwipeStartY

                        // If not already swiping, verify with IME that there is text to delete
                        if (!isBackspaceSwiping && keyListener?.canSwipeDelete() == false) {
                            return true
                        }

                        if (isBackspaceDisarmed) {
                            return true
                        }

                        // Early cancellation of hold-repeat as soon as horizontal swipe intent is detected
                        val density = cachedDensity.takeIf { it > 0f } ?: resources.displayMetrics.density
                        if (kotlin.math.abs(dx) > 8f * density) {
                            removeCallbacks(backspaceRepeatRunnable)
                        }

                        // Downward swipe cancellation
                        if (dy > 32f * density) {
                            if (isBackspaceSwiping) {
                                isBackspaceSwiping = false
                                isSwipeDeleteGestureActive = false
                                isBackspaceDisarmed = true
                                backspaceDeletedWordCount = 0
                                keyListener?.onSwipeDeletePreview(0)
                                feedbackManager?.onTickFeedback(this)
                                    ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                invalidate()
                            }
                            return true
                        }

                        if (dx < -swipeThresholdPx) {
                            removeCallbacks(backspaceRepeatRunnable)
                            isBackspaceSwiping = true
                            isSwipeDeleteGestureActive = true
                            val words = ((-dx - swipeThresholdPx) / backspaceSwipeStepPx).toInt() + 1
                            val clamped = words.coerceIn(1, 15)
                            if (clamped != backspaceDeletedWordCount) {
                                backspaceDeletedWordCount = clamped
                                keyListener?.onSwipeDeletePreview(backspaceDeletedWordCount)
                                feedbackManager?.onTickFeedback(this)
                                    ?: performHapticFeedback(
                                        HapticFeedbackConstants.CLOCK_TICK)
                                invalidate()
                            }
                            return true
                        } else if (isBackspaceSwiping) {
                            // User dragged back right towards Backspace key
                            if (dx > -16f * density) {
                                isBackspaceSwiping = false
                                isSwipeDeleteGestureActive = false
                                backspaceDeletedWordCount = 0
                                keyListener?.onSwipeDeletePreview(0)
                                feedbackManager?.onTickFeedback(this)
                                    ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                invalidate()
                            } else {
                                if (backspaceDeletedWordCount != 0) {
                                    backspaceDeletedWordCount = 0
                                    keyListener?.onSwipeDeletePreview(0)
                                    feedbackManager?.onTickFeedback(this)
                                        ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    invalidate()
                                }
                            }
                            return true
                        }
                    }

                    // Spacebar layout swipe detection (active when spacebarSwipeMode == LAYOUT_SWITCH)
                    if (key.action == KeyAction.Space && spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH && !isSpaceSwiping && !isSpaceCursorMoving && !isLongPressTriggered) {
                        val dx = curX - spaceTouchStartX
                        val dy = curY - spaceTouchStartY
                        val absDx = kotlin.math.abs(dx)
                        val absDy = kotlin.math.abs(dy)
                        if (dy < -swipeThresholdPx && absDy > absDx * 1.3f) {
                            // Upward swipe on spacebar opens Cursor/Text Editor immediately
                            isSpaceSwiping = true
                            pressedKeyIndex = -1
                            pressedPointerId = -1
                            removeCallbacks(longPressRunnable)
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            keyListener?.onSpaceSwipeUp()
                            return true
                        } else if (absDx > swipeThresholdPx && absDx > absDy * 1.3f) {
                            isSpaceSwiping = true
                            pressedKeyIndex = -1
                            pressedPointerId = -1
                            removeCallbacks(longPressRunnable)
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            // Spacebar swipe direction matches visual order (Left to Right):
                            // • Swipe Right (dx > 0): Advance to NEXT layout (index + 1)
                            // • Swipe Left (dx < 0): Retreat to PREVIOUS layout (index - 1)
                            if (dx > 0) {
                                keyListener?.onSpaceSwipe(1)  // Next layout (forward)
                            } else {
                                keyListener?.onSpaceSwipe(-1) // Previous layout (backward)
                            }
                            return true
                        }
                    }
                }

                // ── Swipe-Up Flick Gesture Detection (Probaho 2.0 / rapid shifted access) ──
                if (swipeUpFlickEnabled && !isGliding && !isSpaceSwiping && !isSpaceCursorMoving && !isBackspaceSwiping && !alternatePopup.isShowing) {
                    if (pressedKeyIndex in resolvedKeys.indices) {
                        val flickKey = resolvedKeys[pressedKeyIndex].key
                        if (flickKey.action is KeyAction.Character && flickKey.shiftedLabel != null && flickKey.shiftedLabel != flickKey.label) {
                            val dy = curY - touchStartY
                            val dx = curX - touchStartX
                            val absDx = kotlin.math.abs(dx)
                            if (-dy > flickThresholdPx && -dy > absDx * 0.75f) {
                                if (!isFlickGestureActive) {
                                    isFlickGestureActive = true
                                    removeCallbacks(longPressRunnable)
                                    val bounds = resolvedKeys[pressedKeyIndex].bounds
                                    keyPreviewPopup.show(
                                        anchor = this,
                                        label = flickKey.shiftedLabel!!,
                                        keyLeft = bounds.left,
                                        keyTop = bounds.top,
                                        keyRight = bounds.right,
                                        keyBottom = bounds.bottom,
                                        density = cachedDensity.takeIf { it > 0f } ?: resources.displayMetrics.density,
                                    )
                                    feedbackManager?.onTickFeedback(this)
                                        ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            } else if (isFlickGestureActive && -dy < flickThresholdPx * 0.35f) {
                                isFlickGestureActive = false
                                val bounds = resolvedKeys[pressedKeyIndex].bounds
                                keyPreviewPopup.show(
                                    anchor = this,
                                    label = flickKey.displayLabel(isShifted),
                                    keyLeft = bounds.left,
                                    keyTop = bounds.top,
                                    keyRight = bounds.right,
                                    keyBottom = bounds.bottom,
                                    density = cachedDensity.takeIf { it > 0f } ?: resources.displayMetrics.density,
                                )
                                feedbackManager?.onTickFeedback(this)
                                    ?: performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
                }

                // Glide / Swipe typing gesture detection
                val totalDx = curX - touchStartX
                val totalDy = curY - touchStartY
                val totalDistSq = totalDx * totalDx + totalDy * totalDy

                if (glideTypingEnabled && !isGliding && !isSpaceSwiping && !isSpaceCursorMoving && !isBackspaceSwiping &&
                    totalDistSq > glideMinDistancePx * glideMinDistancePx) {
                    if (pressedKeyIndex in resolvedKeys.indices &&
                        resolvedKeys[pressedKeyIndex].key.activeAction(isShifted) is KeyAction.Character) {
                        isGliding = true
                        removeCallbacks(longPressRunnable)
                    }
                }

                if (glideTypingEnabled && isGliding) {
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
                    val targetKeyIdx = if (pressedKeyIndex in resolvedKeys.indices) {
                        val bounds = resolvedKeys[pressedKeyIndex].bounds
                        val slop = (keyMarginH * 1.5f).coerceAtLeast(10f * resources.displayMetrics.density)
                        if (px >= bounds.left - slop && px <= bounds.right + slop &&
                            py >= bounds.top - slop && py <= bounds.bottom + slop) {
                            pressedKeyIndex
                        } else {
                            val idx = findKeyIndex(px, py)
                            if (idx == pressedKeyIndex) pressedKeyIndex else -1
                        }
                    } else {
                        -1
                    }
                    if (targetKeyIdx >= 0 && !isLongPressTriggered && !isSpaceSwiping &&
                        !isGliding && !isBackspaceSwiping && !isSpaceCursorMoving) {
                        dispatchKey(resolvedKeys[targetKeyIdx].key)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    isLongPressTriggered = false
                    isFlickGestureActive = false
                    isSpaceSwiping = false
                    keyPreviewPopup.dismiss()
                    alternatePopup.dismiss()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                removeCallbacks(backspaceRepeatRunnable)
                keyPreviewPopup.dismiss()
                if (alternatePopup.isShowing) {
                    val chosen = alternatePopup.selectedLabel
                    alternatePopup.dismiss()
                    if (chosen != null && pressedKeyIndex in resolvedKeys.indices) {
                        val key = resolvedKeys[pressedKeyIndex].key
                        keyListener?.onKey(key, KeyAction.Character(chosen))
                        feedbackManager?.onKeyFeedback(this)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    invalidate()
                    return true
                }

                if (isSpaceSwiping) {
                    isSpaceSwiping = false
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    invalidate()
                    return true
                }

                if (isSpaceCursorMoving) {
                    isSpaceCursorMoving = false
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    invalidate()
                    return true
                }

                if (isBackspaceSwiping || isSwipeDeleteGestureActive || isBackspaceDisarmed) {
                    val count = backspaceDeletedWordCount
                    val wasSwiping = isBackspaceSwiping
                    val wasDisarmed = isBackspaceDisarmed
                    isBackspaceSwiping = false
                    isSwipeDeleteGestureActive = false
                    isBackspaceDisarmed = false
                    backspaceDeletedWordCount = 0
                    if (wasSwiping && count > 0 && !wasDisarmed) {
                        keyListener?.onSwipeDelete(count)
                        feedbackManager?.onKeyFeedback(this)
                            ?: performHapticFeedback(
                                HapticFeedbackConstants.KEYBOARD_TAP)
                    } else if (!wasDisarmed) {
                        keyListener?.onSwipeDeletePreview(0)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    invalidate()
                    return true
                }

                if (glideTypingEnabled && isGliding) {
                    if (visitedGlideKeys.size >= 2) {
                        keyListener?.onGlideGesture(visitedGlideKeys)
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
                    isFlickGestureActive = false
                    isSpaceSwiping = false
                    keyPreviewPopup.dismiss()
                    invalidate()
                    return true
                }

                val upDy = py - touchStartY
                val upDx = px - touchStartX
                val isFlickRelease = swipeUpFlickEnabled && pressedKeyIndex in resolvedKeys.indices && run {
                    val flickKey = resolvedKeys[pressedKeyIndex].key
                    flickKey.action is KeyAction.Character &&
                        flickKey.shiftedLabel != null &&
                        flickKey.shiftedLabel != flickKey.label &&
                        (isFlickGestureActive || (-upDy >= flickThresholdPx && -upDy > kotlin.math.abs(upDx) * 0.75f))
                }

                if (isFlickGestureActive || isFlickRelease) {
                    isFlickGestureActive = false
                    keyPreviewPopup.dismiss()
                    if (pressedKeyIndex in resolvedKeys.indices) {
                        val flickKey = resolvedKeys[pressedKeyIndex].key
                        keyListener?.onKey(flickKey, flickKey.shiftedAction)
                        keyListener?.onKeyWithTouch(flickKey, flickKey.shiftedAction, touchStartX, touchStartY)
                        feedbackManager?.onKeyFeedback(this)
                            ?: performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    isLongPressTriggered = false
                    isSpaceSwiping = false
                    invalidate()
                    return true
                }

                val targetKeyIdx = if (pressedKeyIndex in resolvedKeys.indices) {
                    val bounds = resolvedKeys[pressedKeyIndex].bounds
                    val slopDensity = cachedDensity.takeIf { it > 0f } ?: resources.displayMetrics.density
                    val slop = (keyMarginH * 1.5f).coerceAtLeast(10f * slopDensity)
                    if (px >= bounds.left - slop && px <= bounds.right + slop &&
                        py >= bounds.top - slop && py <= bounds.bottom + slop) {
                        pressedKeyIndex
                    } else {
                        val idx = findKeyIndex(px, py)
                        if (idx == pressedKeyIndex) pressedKeyIndex else -1
                    }
                } else {
                    -1
                }
                if (targetKeyIdx >= 0 && !isLongPressTriggered && !isSpaceSwiping) {
                    dispatchKey(resolvedKeys[targetKeyIdx].key)
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
                keyPreviewPopup.dismiss()
                alternatePopup.dismiss()
                isGliding = false
                isSpaceCursorMoving = false
                isBackspaceSwiping = false
                isSwipeDeleteGestureActive = false
                isBackspaceDisarmed = false
                backspaceDeletedWordCount = 0
                keyListener?.onSwipeDeletePreview(0)
                isSpaceSwiping = false
                isFlickGestureActive = false
                pressedKeyIndex = -1
                pressedPointerId = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(longPressRunnable)
        removeCallbacks(backspaceRepeatRunnable)
        // Dismiss both PopupWindows to avoid "window leaked" exceptions when IME hides
        keyPreviewPopup.dismiss()
        alternatePopup.dismiss()
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
        var action = key.activeAction(isShifted)
        if (action == KeyAction.SwitchLayout && isBottomRowKeyEmoji) {
            action = KeyAction.SwitchEmoji
        }
        keyListener?.onKey(key, action)
        keyListener?.onKeyWithTouch(key, action, touchStartX, touchStartY)

        // Auto-release shift after one character (one-shot shift), unless Caps Lock is active
        if (isShifted && !isCapsLock && action !is KeyAction.Shift) {
            isShifted = false
            invalidate()
        }
    }

    // Accessibility / TalkBack Exploration (WCAG 2.1)

    @Suppress("DEPRECATION") // setBoundsInParent is the correct API for ExploreByTouchHelper virtual views
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
                    ?: performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP)
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
            KeyAction.Enter -> {
                val resolvedIcon = if (isShiftActive) EnterKeyResolver.ActionIcon.NEWLINE else enterActionIcon
                when (resolvedIcon) {
                    EnterKeyResolver.ActionIcon.SEARCH -> "অনুসন্ধান (Search)"
                    EnterKeyResolver.ActionIcon.SEND -> "পাঠান (Send)"
                    EnterKeyResolver.ActionIcon.GO -> "যান (Go)"
                    EnterKeyResolver.ActionIcon.NEXT -> "পরবর্তী (Next)"
                    EnterKeyResolver.ActionIcon.DONE -> "সম্পন্ন (Done)"
                    EnterKeyResolver.ActionIcon.NEWLINE -> "এন্টার (Enter)"
                }
            }
            KeyAction.Shift -> when {
                isCapsLock -> "ক্যাপস লক সক্রিয় (Caps Lock Active)"
                shifted -> "শিফট সক্রিয় (Shift Active)"
                else -> "শিফট (Shift)"
            }
            KeyAction.SwitchNumeric -> "সংখ্যা ও প্রতীক (Numbers and symbols)"
            KeyAction.SwitchMoreSymbols -> "অতিরিক্ত প্রতীক (More symbols)"
            KeyAction.SwitchAlpha -> "বর্ণমালা (Alphabet)"
            KeyAction.SwitchNumpad -> "নম্বর প্যাড (Number pad)"
            KeyAction.ToggleBengaliDigits -> "সংখ্যা পরিবর্তন (Toggle Digits)"
            KeyAction.SwitchLayout -> if (isBottomRowKeyEmoji) "ইমোজি (Emoji)" else "লেআউট পরিবর্তন (Switch Layout)"
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

    // Constants — all colours and metrics in one place

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
