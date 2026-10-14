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
    // Side Dock buttons for One-Handed mode
    private val sideDockButtons = arrayOf(
        SideDockButton(SideDockAction.EXPAND_STANDARD),
        SideDockButton(SideDockAction.SWAP_SIDE),
        SideDockButton(SideDockAction.TOGGLE_FLOATING),
    )
    private var isSideDockVisible: Boolean = false

    // Swipe flick gestures (Probaho 2.0 / rapid shifted & subscript access)
    var swipeUpFlickEnabled: Boolean = true

    // Power-user long-press clipboard shortcuts (C=Copy, V=Paste, X=Cut, A=SelectAll) on Latin layouts
    private var longPressClipboardShortcuts: Boolean = false

    // Bilateral Thumb Aura (Probaho 2.0 vowel-consonant realm tint)
    private var showBilateralAura: Boolean = false

    // ── PopupWindow-based Key Preview & Alternate Popups ──────────────────────
    // Both live in their own window layer so they're never clipped by this view's
    // bounds — consistently visible on row 1 without overlapping the candidate strip.
    private val keyPreviewPopup = KeyPreviewPopupWindow(context)
    private val alternatePopup = AlternatePopupWindow(context)

    private var wallpaperBitmap: Bitmap? = null
    private var wallpaperOpacity: Float = 0.25f

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

    // ── Keycap & Hardware Canvas Rendering Pipeline ───────────────────────────
    private val keycapPaintPipeline = KeycapPaintPipeline(keyboardPainter)

    private val paintHost = object : KeycapPaintPipeline.Host {
        override val width: Float get() = this@KeyboardCanvasView.width.toFloat()
        override val height: Float get() = this@KeyboardCanvasView.height.toFloat()
        override val density: Float get() = this@KeyboardCanvasView.cachedDensity.takeIf { it > 0f } ?: this@KeyboardCanvasView.resources.displayMetrics.density
        override val activeTheme: KeyboardTheme get() = this@KeyboardCanvasView.activeTheme
        override val isThemeApplied: Boolean get() = this@KeyboardCanvasView.isThemeApplied
        override val formFactor: KeyboardPreferences.FormFactor get() = this@KeyboardCanvasView.formFactor
        override val isShifted: Boolean get() = this@KeyboardCanvasView.isShifted
        override val isCapsLock: Boolean get() = this@KeyboardCanvasView.isCapsLock
        override val isShiftActive: Boolean get() = this@KeyboardCanvasView.isShiftActive
        override val isBottomRowKeyEmoji: Boolean get() = this@KeyboardCanvasView.isBottomRowKeyEmoji
        override val layoutType: com.lekhani.android.ffi.LekhaniLayoutType get() = this@KeyboardCanvasView.layoutType
        override val spacebarSwipeMode: KeyboardPreferences.SpacebarSwipeMode get() = this@KeyboardCanvasView.spacebarSwipeMode
        override val enabledLayoutsCount: Int get() = this@KeyboardCanvasView.enabledLayoutsCount
        override val showKeyBorders: Boolean get() = this@KeyboardCanvasView.showKeyBorders
        override val showHomeRowAccents: Boolean get() = this@KeyboardCanvasView.showHomeRowAccents
        override val showKeyHints: Boolean get() = this@KeyboardCanvasView.showKeyHints
        override val showBilateralAura: Boolean get() = this@KeyboardCanvasView.showBilateralAura
        override val showLayoutNameOnSpacebar: Boolean get() = this@KeyboardCanvasView.showLayoutNameOnSpacebar
        override val isUiLanguageEnglish: Boolean get() = this@KeyboardCanvasView.isUiLanguageEnglish
        override val keyGlowRippleEnabled: Boolean get() = this@KeyboardCanvasView.keyGlowRippleEnabled
        override val keyShadowLip: Float get() = this@KeyboardCanvasView.keyShadowLip
        override val keyCornerRadius: Float get() = this@KeyboardCanvasView.keyCornerRadius
        override val homeRowAccentHeight: Float get() = this@KeyboardCanvasView.homeRowAccentHeight
        override val enterActionIcon: EnterKeyResolver.ActionIcon get() = this@KeyboardCanvasView.enterActionIcon
        override val isResizeVisualGuide: Boolean get() = this@KeyboardCanvasView.isResizeVisualGuide
        override val isSideDockVisible: Boolean get() = this@KeyboardCanvasView.isSideDockVisible
        override val sideDockButtons: Array<SideDockButton> get() = this@KeyboardCanvasView.sideDockButtons
        override val resolvedKeys: List<ResolvedKey> get() = this@KeyboardCanvasView.resolvedKeys
        override val wallpaperBitmap: Bitmap? get() = this@KeyboardCanvasView.wallpaperBitmap
        override val wallpaperOpacity: Float get() = this@KeyboardCanvasView.wallpaperOpacity

        override val pressedKeyIndex: Int get() = this@KeyboardCanvasView.pressedKeyIndex
        override val pressStartTime: Long get() = this@KeyboardCanvasView.pressStartTime
        override val glideTypingEnabled: Boolean get() = this@KeyboardCanvasView.glideTypingEnabled
        override val isGliding: Boolean get() = this@KeyboardCanvasView.isGliding
        override val glidePointsX: FloatArray get() = this@KeyboardCanvasView.glidePointsX
        override val glidePointsY: FloatArray get() = this@KeyboardCanvasView.glidePointsY
        override val glidePointCount: Int get() = this@KeyboardCanvasView.glidePointCount
        override val glideCurX: Float get() = this@KeyboardCanvasView.glideCurX
        override val glideCurY: Float get() = this@KeyboardCanvasView.glideCurY
        override val isSpaceCursorMoving: Boolean get() = this@KeyboardCanvasView.isSpaceCursorMoving
        override val spaceSlideLastX: Float get() = this@KeyboardCanvasView.spaceSlideLastX
        override val isBackspaceSwiping: Boolean get() = this@KeyboardCanvasView.isBackspaceSwiping
        override val backspaceDeletedWordCount: Int get() = this@KeyboardCanvasView.backspaceDeletedWordCount

        override fun applyInitialTheme(theme: KeyboardTheme) = this@KeyboardCanvasView.applyTheme(theme)
        override fun invalidate() = this@KeyboardCanvasView.invalidate()
        override fun postInvalidateOnAnimation() = this@KeyboardCanvasView.postInvalidateOnAnimation()
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

    private fun getResolvedAlternates(key: Key, isShifted: Boolean): Array<String> =
        LongPressAlternatesResolver.resolve(key, isShifted)

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

    private val accessibilityHost = object : KeyboardAccessibilityDelegate.Host {
        override val view: View get() = this@KeyboardCanvasView
        override val resolvedKeys: List<ResolvedKey> get() = this@KeyboardCanvasView.resolvedKeys
        override val isShifted: Boolean get() = this@KeyboardCanvasView.isShifted
        override val isCapsLock: Boolean get() = this@KeyboardCanvasView.isCapsLock
        override val isShiftActive: Boolean get() = this@KeyboardCanvasView.isShiftActive
        override val isBottomRowKeyEmoji: Boolean get() = this@KeyboardCanvasView.isBottomRowKeyEmoji
        override val enterActionIcon: EnterKeyResolver.ActionIcon get() = this@KeyboardCanvasView.enterActionIcon
        override val feedbackManager: LekhaniFeedbackManager? get() = this@KeyboardCanvasView.feedbackManager
        override val keyListener: KeyListener? get() = this@KeyboardCanvasView.keyListener
        override fun findKeyIndex(x: Float, y: Float): Int = this@KeyboardCanvasView.findKeyIndex(x, y)
    }

    private val accessibilityHelper = KeyboardAccessibilityDelegate(accessibilityHost)

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
        keycapPaintPipeline.setTypeface(tf)

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
        keycapPaintPipeline.applyTheme(theme)
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
        keycapPaintPipeline.updateDimensions(density)

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
        keycapPaintPipeline.updateTextSizes(labelSize, labelSizeSmall)
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

    // ── Hardware Canvas Rendering ─────────────────────────────────────────────


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        keycapPaintPipeline.render(canvas, paintHost)
    }

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
}


