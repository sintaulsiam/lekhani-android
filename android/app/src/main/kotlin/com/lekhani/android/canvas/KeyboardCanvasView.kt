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
    var spacebarSwipeMode: KeyboardPreferences.SpacebarSwipeMode = KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH
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
    private val resizeGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Side Dock buttons for One-Handed mode (zero allocation in onDraw)
    private val sideDockButtons = arrayOf(
        SideDockButton(SideDockAction.EXPAND_STANDARD),
        SideDockButton(SideDockAction.SWAP_SIDE),
        SideDockButton(SideDockAction.TOGGLE_FLOATING),
    )
    private var isSideDockVisible: Boolean = false
    private val sideDockBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1AFFFFFF.toInt()
    }
    private val sideDockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // Pre-allocated vector icon paths and paints for zero-allocation vector drawing
    private val vectorIconPath = Path()
    private val vectorIconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val vectorIconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    // Spacebar cursor slide navigation paint
    private val spaceSlideTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0x8000E5B8.toInt()
    }
    private val spaceSlideThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5B8.toInt()
    }

    // Backspace swipe badge paint
    private val backspaceBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE53935.toInt()
    }
    private val backspaceBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Key Glow paint (Material 3 Expressive press effect)
    private val keyGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x6600E5B8.toInt()
    }

    // Key tactile 3D shadow paint (Material 3 depth)
    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44000000
    }

    // Swipe flick gestures (Probaho 2.0 / rapid shifted & subscript access)
    var swipeUpFlickEnabled: Boolean = true

    // Power-user long-press clipboard shortcuts (C=Copy, V=Paste, X=Cut, A=SelectAll) on Latin layouts
    private var longPressClipboardShortcuts: Boolean = false

    // Bilateral Thumb Aura paint (Probaho 2.0 vowel-consonant realm tint)
    private var showBilateralAura: Boolean = false
    private val vowelAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
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
    private val wallpaperPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
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

    // ── Dedicated Collaborators (Strategy / Painter & Touch decomposition) ───
    private val keyboardPainter = KeyboardPainter()
    private val keyboardTouchTracker = KeyboardTouchTracker()
    private val keyboardGeometryEngine = KeyboardGeometryEngine()

    /** Flat list of all keys with pixel bounds. Rebuilt only in [onSizeChanged]. */
    private val resolvedKeys = ArrayList<ResolvedKey>(50)

    // ── Pre-allocated drawing primitives (ZERO allocation in onDraw) ──────────

    // Key backgrounds
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = KEY_COLOR_NORMAL
    }
    private val keyShiftBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = KEY_COLOR_SHIFT
    }
    private val keySpaceBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = KEY_COLOR_SPACE
    }
    private val keyHasantaBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = KEY_COLOR_HASANTA
    }

    // Key border
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
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
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = LABEL_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val labelPaintSmall = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
        color = LABEL_COLOR_DIM
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    // Home row accent underline
    private val homeRowAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = ACCENT_TEAL
    }

    // Zone divider (between left vowel and right consonant halves)
    private val zoneDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = ZONE_DIVIDER_COLOR
    }

    // Ripple animation
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = RIPPLE_COLOR
    }

    // Glide / Gesture typing path and glow paints (zero allocation in onDraw)
    private val glidePath = android.graphics.Path()
    private val glideGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#4000D4A0") // semi-transparent teal glow
    }
    private val glideStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#00E5B8") // vibrant teal stroke
    }
    private val glideDotPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0FFFFFF") // glowing tip
    }

    // Delegate touch state to TouchGestureDispatcher
    private val touchHost = object : TouchGestureDispatcher.Host {
        override val view: View get() = this@KeyboardCanvasView
        override val resolvedKeys: List<ResolvedKey> get() = this@KeyboardCanvasView.resolvedKeys
        override val isShifted: Boolean get() = this@KeyboardCanvasView.isShifted
        override val isCapsLock: Boolean get() = this@KeyboardCanvasView.isCapsLock
        override val isBottomRowKeyEmoji: Boolean get() = this@KeyboardCanvasView.isBottomRowKeyEmoji
        override val layoutType: com.lekhani.android.ffi.LekhaniLayoutType get() = this@KeyboardCanvasView.layoutType
        override val spacebarSwipeMode: KeyboardPreferences.SpacebarSwipeMode get() = this@KeyboardCanvasView.spacebarSwipeMode
        override val swipeToDeleteEnabled: Boolean get() = this@KeyboardCanvasView.swipeToDeleteEnabled
        override val swipeUpFlickEnabled: Boolean get() = this@KeyboardCanvasView.swipeUpFlickEnabled
        override val glideTypingEnabled: Boolean get() = this@KeyboardCanvasView.glideTypingEnabled
        override val showKeyPreviews: Boolean get() = this@KeyboardCanvasView.showKeyPreviews
        override val longPressDelayMs: Long get() = this@KeyboardCanvasView.longPressDelayMs
        override val longPressClipboardShortcuts: Boolean get() = this@KeyboardCanvasView.longPressClipboardShortcuts
        override val isSideDockVisible: Boolean get() = this@KeyboardCanvasView.isSideDockVisible
        override val sideDockButtons: Array<SideDockButton> get() = this@KeyboardCanvasView.sideDockButtons
        override val formFactor: KeyboardPreferences.FormFactor get() = this@KeyboardCanvasView.formFactor
        override val keyMarginH: Float get() = this@KeyboardCanvasView.keyMarginH
        override val density: Float get() = this@KeyboardCanvasView.cachedDensity.takeIf { it > 0f } ?: this@KeyboardCanvasView.resources.displayMetrics.density
        override val feedbackManager: LekhaniFeedbackManager? get() = this@KeyboardCanvasView.feedbackManager
        override val keyListener: KeyListener? get() = this@KeyboardCanvasView.keyListener

        override fun findKeyIndex(x: Float, y: Float): Int =
            this@KeyboardCanvasView.findKeyIndex(x, y)

        override fun dispatchKey(key: Key, touchX: Float, touchY: Float) =
            this@KeyboardCanvasView.dispatchKey(key, touchX, touchY)

        override fun showKeyPreview(key: Key, bounds: RectF) {
            val densityVal = density
            keyPreviewPopup.show(
                anchor = this@KeyboardCanvasView,
                label = key.displayLabel(isShifted),
                keyLeft = bounds.left,
                keyTop = bounds.top,
                keyRight = bounds.right,
                keyBottom = bounds.bottom,
                density = densityVal,
            )
        }

        override fun showFlickKeyPreview(label: String, bounds: RectF) {
            val densityVal = density
            keyPreviewPopup.show(
                anchor = this@KeyboardCanvasView,
                label = label,
                keyLeft = bounds.left,
                keyTop = bounds.top,
                keyRight = bounds.right,
                keyBottom = bounds.bottom,
                density = densityVal,
            )
        }

        override fun dismissKeyPreview() = keyPreviewPopup.dismiss()

        override fun showAlternatePopup(resolvedKey: ResolvedKey, alts: Array<String>) {
            this@KeyboardCanvasView.showAlternatePopup(resolvedKey, alts)
        }

        override fun dismissAlternatePopup() = alternatePopup.dismiss()

        override fun isAlternatePopupShowing(): Boolean = alternatePopup.isShowing

        override fun onAlternatePopupTouchMove(x: Float, y: Float, viewLocationX: Int): Boolean =
            alternatePopup.onTouchMove(x, y, viewLocationX)

        override val alternatePopupSelectedLabel: String? get() = alternatePopup.selectedLabel

        override fun getResolvedAlternates(key: Key, isShifted: Boolean): Array<String> =
            this@KeyboardCanvasView.getResolvedAlternates(key, isShifted)

        override fun invalidate() = this@KeyboardCanvasView.invalidate()
    }

    private val touchGestureDispatcher = TouchGestureDispatcher(touchHost)

    private val pressedKeyIndex get() = touchGestureDispatcher.pressedKeyIndex
    private val pressStartTime get() = touchGestureDispatcher.pressStartTime
    private val isGliding get() = touchGestureDispatcher.isGliding
    private val glidePointsX get() = touchGestureDispatcher.glidePointsX
    private val glidePointsY get() = touchGestureDispatcher.glidePointsY
    private val glidePointCount get() = touchGestureDispatcher.glidePointCount
    private val glideCurX get() = touchGestureDispatcher.glideCurX
    private val glideCurY get() = touchGestureDispatcher.glideCurY
    private val isBackspaceSwiping get() = touchGestureDispatcher.isBackspaceSwiping
    private val backspaceDeletedWordCount get() = touchGestureDispatcher.backspaceDeletedWordCount
    private val isSpaceCursorMoving get() = touchGestureDispatcher.isSpaceCursorMoving
    private val spaceSlideLastX get() = touchGestureDispatcher.spaceSlideLastX
    private val isFlickGestureActive get() = touchGestureDispatcher.isFlickGestureActive
    private val isFlickDownActive get() = touchGestureDispatcher.isFlickDownActive

    /** Ripple RectF — reused each frame, never reallocated */
    private val rippleRect = RectF()

    /** Reusable scratch RectF for hit-testing math */
    private val scratchRect = RectF()

    /**
     * Resolves up to 4 alternate characters for long-press popups.
     * Prioritizes the layout-specific Shift character as Candidate #0 on fixed and
     * Bengali layouts (where Shift is a distinct letter or modifier, e.g. ক -> খ, া -> অ).
     * For Latin letters (English/Avro), numbers and diacritics are prioritized.
     */
    private fun getResolvedAlternates(key: Key, isShifted: Boolean): Array<String> {
        if (key.action !is KeyAction.Character) return emptyArray()

        val activeChar = key.displayLabel(isShifted)
        val candidates = ArrayList<String>(4)

        // 1. Shift alternative (Primary on fixed & flow layouts where shift is a distinct character)
        val shiftedChar = if (isShifted) key.label else key.shiftedLabel
        if (!shiftedChar.isNullOrEmpty() && shiftedChar != activeChar) {
            val isCaseShiftOnly = shiftedChar.equals(activeChar, ignoreCase = true)
            if (!isCaseShiftOnly) {
                candidates.add(shiftedChar)
            }
        }

        // 2. Explicit key longPressAction character (if configured)
        val explicitLongChar = (key.activeLongPressAction(isShifted) as? KeyAction.Character)?.token
        if (!explicitLongChar.isNullOrEmpty() && explicitLongChar != activeChar && !candidates.contains(explicitLongChar)) {
            candidates.add(explicitLongChar)
        }

        // 3. Key hint label (e.g. top-row numbers or dead-key shortcuts like ৎ, ঁ)
        val hint = key.displayHint(isShifted)
        if (!hint.isNullOrEmpty() && hint != activeChar && !candidates.contains(hint)) {
            candidates.add(hint)
        }

        // 4. Secondary variants & conjuncts from BengaliAlternates dictionary
        val rawToken = (key.action as? KeyAction.Character)?.token
        val rawAlts = (rawToken?.let { com.lekhani.android.model.BengaliAlternates.getAlternates(it) })
            ?: com.lekhani.android.model.BengaliAlternates.getAlternates(activeChar)
        if (rawAlts != null) {
            for (alt in rawAlts) {
                if (candidates.size >= 4) break
                if (alt.isNotEmpty() && alt != activeChar && !candidates.contains(alt)) {
                    candidates.add(alt)
                }
            }
        }

        // 5. Fallback for Latin keys without hint: add uppercase shift if no other alternate exists
        if (candidates.isEmpty() && !shiftedChar.isNullOrEmpty() && shiftedChar != activeChar) {
            candidates.add(shiftedChar)
        }

        return if (candidates.isEmpty()) emptyArray() else candidates.toTypedArray()
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
        this.showDedicatedNumberRow = prefs.isNumberRowActive(layoutType)
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
        this.longPressClipboardShortcuts = prefs.longPressClipboardShortcuts

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
     * Dynamically updates the bottom chin padding in real time during visual resizing.
     * Triggers requestLayout and redraw without allocations.
     */
    fun setLiveBottomChin(chinDp: Float) {
        val clamped = chinDp.coerceIn(0f, 32f)
        if (Math.abs(this.bottomChinPaddingDp - clamped) > 0.001f) {
            this.bottomChinPaddingDp = clamped
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
        touchGestureDispatcher.cancelSwipeDelete()
    }

    fun setLayout(
        newLayout: KeyboardLayout,
        newLayoutType: com.lekhani.android.ffi.LekhaniLayoutType,
        shifted: Boolean = false,
    ) {
        layout = newLayout
        layoutType = newLayoutType
        this.showDedicatedNumberRow = KeyboardPreferences.get(context).isNumberRowActive(newLayoutType)
        isShifted = shifted
        isCapsLock = false
        isGboardKarsActive = false
        gboardActiveConsonant = ""
        touchGestureDispatcher.resetTouchState()
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
            if (isSixRow) 145f else 125f
        } else {
            when {
                rowCount >= 6 -> 256f
                rowCount == 5 -> 236f
                else -> 218f
            }
        }
        val effectiveChinDp = if (isLandscape || formFactor == KeyboardPreferences.FormFactor.FLOATING) 0f else bottomChinPaddingDp
        val defaultHeightDp = baseHeightDp * heightScale + effectiveChinDp
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
        keyShadowLip  = 1.6f * density  // pre-computed — never recomputed in onDraw
        keyMarginH = marginHDp * density
        keyMarginV = marginVDp * density
        keyCornerRadius = 7.5f * density
        homeRowAccentHeight = 2.5f * density
        touchGestureDispatcher.updateDimensions(density)

        glideStrokePaint.strokeWidth = 4.5f * density
        glideGlowPaint.strokeWidth = 11f * density
        spaceSlideTrackPaint.strokeWidth = 2.5f * density
        keyGlowPaint.strokeWidth = 2.2f * density
        sideDockTextPaint.textSize = 20f * density
        backspaceBadgeTextPaint.textSize = 12f * density
        vectorIconStrokePaint.strokeWidth = 2.2f * density

        val currentLayout = layout ?: return
        val hasExtraNumberRow = showDedicatedNumberRow && !isNumberSymbolsActive()
        val rowCount = currentLayout.rows.size + (if (hasExtraNumberRow) 1 else 0)
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val effectiveChinDp = if (isLandscape || formFactor == KeyboardPreferences.FormFactor.FLOATING) 0f else bottomChinPaddingDp
        val chinPx = effectiveChinDp * density
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
        labelSize = (keyHeight * 0.36f * fontScale).coerceAtLeast(9.5f * density)
        labelSizeSmall = (keyHeight * 0.21f * fontScale).coerceAtLeast(7.5f * density)

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
        accessibilityHelper.invalidateRoot()
        val currentLayout = layout ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = cachedDensity.takeIf { it > 0f } ?: resources.displayMetrics.density
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val params = KeyboardGeometryEngine.Params(
            layout = currentLayout,
            layoutType = layoutType,
            formFactor = formFactor,
            width = w,
            height = h,
            density = density,
            isLandscape = isLandscape,
            bottomChinPaddingDp = bottomChinPaddingDp,
            keyMarginH = keyMarginH,
            keyMarginV = keyMarginV,
            showDedicatedNumberRow = showDedicatedNumberRow,
            isNumberSymbolsActive = isNumberSymbolsActive(),
            isNumpad = isNumpadLayout(),
            avroNumeralsBengali = avroNumeralsBengali,
            englishDedicatedNumberRow = englishDedicatedNumberRow,
            bengaliDedicatedNumberRow = bengaliDedicatedNumberRow,
            isGboardKarsActive = isGboardKarsActive,
            gboardActiveConsonant = gboardActiveConsonant,
            fieldType = fieldType,
        )

        keyboardGeometryEngine.computeKeyBounds(
            params = params,
            resolvedKeys = resolvedKeys,
            sideDockButtons = sideDockButtons,
            onSideDockVisibilityChange = { isSideDockVisible = it },
            onGeometryCalculated = { keyListener?.onGeometryChanged(it) }
        )
    }

    // Vector Drawing Helpers — Zero allocations permitted

    private fun drawVectorBackspace(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        keyboardPainter.drawVectorBackspace(canvas, cx, cy, size, paint)
    }

    private fun drawVectorShift(canvas: Canvas, cx: Float, cy: Float, size: Float, isShifted: Boolean, isLocked: Boolean, strokePaint: Paint, fillPaint: Paint) {
        keyboardPainter.drawVectorShift(canvas, cx, cy, size, isShifted, isLocked, strokePaint, fillPaint)
    }

    private fun getAdaptiveSpaceRow(originalRow: List<Key>): List<Key> =
        keyboardGeometryEngine.getAdaptiveSpaceRow(originalRow, fieldType, isNumberSymbolsActive())

    private fun drawVectorEnter(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorEnter(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorSearch(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorSearch(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorSend(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorSend(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorGo(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorGo(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorNext(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorNext(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorDone(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorDone(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorExpand(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorExpand(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorChevron(canvas: Canvas, cx: Float, cy: Float, size: Float, isLeft: Boolean, strokePaint: Paint) {
        keyboardPainter.drawVectorChevron(canvas, cx, cy, size, isLeft, strokePaint)
    }

    private fun drawVectorFloating(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorFloating(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorGlobe(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorGlobe(canvas, cx, cy, size, strokePaint)
    }

    private fun drawVectorSmiley(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        keyboardPainter.drawVectorSmiley(canvas, cx, cy, size, strokePaint, vectorIconFillPaint)
    }

    private fun drawVectorNumpad(canvas: Canvas, cx: Float, cy: Float, size: Float, fillPaint: Paint) {
        keyboardPainter.drawVectorNumpad(canvas, cx, cy, size, fillPaint)
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
        if (touchGestureDispatcher.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        touchGestureDispatcher.onDetachedFromWindow()
        // Dismiss both PopupWindows to avoid "window leaked" exceptions when IME hides
        keyPreviewPopup.dismiss()
        alternatePopup.dismiss()
    }

    private fun findKeyIndex(x: Float, y: Float): Int {
        return keyboardTouchTracker.findKeyIndex(x, y, resolvedKeys, width.toFloat(), height.toFloat())
    }

    private fun dispatchKey(key: Key, touchX: Float, touchY: Float) {
        var action = key.activeAction(isShifted)
        if (action == KeyAction.SwitchLayout && isBottomRowKeyEmoji) {
            action = KeyAction.SwitchEmoji
        }
        keyListener?.onKeyWithTouch(key, action, touchX, touchY)
        keyListener?.onKey(key, action)

        // Auto-release shift after one character (one-shot shift), unless Caps Lock is active
        if (isShifted && !isCapsLock && action !is KeyAction.Shift) {
            isShifted = false
            invalidate()
        }
    }

    private fun dispatchKey(key: Key) {
        dispatchKey(key, touchGestureDispatcher.touchStartX, touchGestureDispatcher.touchStartY)
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
