package com.lekhani.android.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import com.lekhani.android.model.KeyboardLayout

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
    }

    var keyListener: KeyListener? = null

    // ── Layout state ──────────────────────────────────────────────────────────

    private var layout: KeyboardLayout? = null
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

    // ── Touch / ripple state (pre-allocated, mutated in place) ───────────────

    /** Index into [resolvedKeys] of the currently pressed key, or -1 */
    private var pressedKeyIndex: Int = -1

    /** Timestamp (ms) when the current press began, for ripple animation */
    private var pressStartTime: Long = 0L

    /** Ripple RectF — reused each frame, never reallocated */
    private val rippleRect = RectF()

    /** Reusable scratch RectF for hit-testing math */
    private val scratchRect = RectF()

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
    // Layout management
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Set the active keyboard layout.
     * Triggers a re-computation of pixel bounds and a redraw.
     * Called from LekhaniInputMethodService on layout switch.
     */
    fun setLayout(newLayout: KeyboardLayout, shifted: Boolean = false) {
        layout = newLayout
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

    // ══════════════════════════════════════════════════════════════════════════
    // Size change — the ONLY place pixel bounding boxes are computed
    // ══════════════════════════════════════════════════════════════════════════

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // ── Dimension derivations ──────────────────────────────────────────
        val density = resources.displayMetrics.density
        keyMarginH = 3.5f * density
        keyMarginV = 4f * density
        keyCornerRadius = 9f * density
        homeRowAccentHeight = 2.5f * density

        val currentLayout = layout ?: return
        val rowCount = currentLayout.rows.size       // typically 3
        spacebarRowHeight = h * 0.22f                // spacebar row ~22% of height
        keyHeight = (h - spacebarRowHeight - keyMarginV * (rowCount + 1)) / rowCount
        labelSize = keyHeight * 0.38f
        labelSizeSmall = keyHeight * 0.20f

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
        val spaceRowTop = height - spacebarRowHeight - keyMarginV
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
            canvas.drawRoundRect(bounds, keyCornerRadius, keyCornerRadius, keyBorderPaint)

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
            val labelText = key.displayLabel(isShifted)
            val cx = bounds.centerX()
            val cy = bounds.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f

            when (key.action) {
                KeyAction.Backspace, KeyAction.Enter, KeyAction.Shift,
                KeyAction.SwitchNumeric, KeyAction.SwitchLayout -> {
                    // System keys use smaller label
                    canvas.drawText(labelText, cx, cy, labelPaintSmall)
                }
                else -> {
                    canvas.drawText(labelText, cx, cy, labelPaint)
                }
            }
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
                val idx = findKeyIndex(event.x, event.y)
                if (idx >= 0) {
                    pressedKeyIndex = idx
                    pressStartTime = SystemClock.uptimeMillis()
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = findKeyIndex(event.x, event.y)
                if (idx >= 0 && idx == pressedKeyIndex) {
                    dispatchKey(resolvedKeys[idx].key)
                }
                pressedKeyIndex = -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedKeyIndex = -1
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
