package com.lekhani.android.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import com.lekhani.android.theme.KeyboardTheme

/**
 * AlternatePopupWindow
 * A [PopupWindow] that renders the long-press alternate-characters strip
 * (Gboard-style horizontal pill row). Lives in its own window layer — no
 * clipping against [KeyboardCanvasView] bounds.
 *
 * Touch handling: horizontal swipe from the originating finger selects the
 * alternate; lifting commits it. The caller ([KeyboardCanvasView]) forwards
 * [ACTION_MOVE] and [ACTION_UP] events via [onTouchMove] / [onTouchUp].
 *
 * Performance: zero allocations in [AlternateView.onDraw].
 */
internal class AlternatePopupWindow(context: Context) {

    // ── Content view ──────────────────────────────────────────────────────────

    private val alternateView = AlternateView(context)

    // ── PopupWindow ───────────────────────────────────────────────────────────

    private val popup = PopupWindow(
        alternateView,
        1,
        1,
        false,
    ).apply {
        isClippingEnabled = false
        isTouchable = false
        animationStyle = android.R.style.Animation
        elevation = 10f
    }

    val isShowing: Boolean get() = popup.isShowing

    /** Index of the currently highlighted alternate (0-based). */
    val selectedIndex: Int get() = alternateView.selectedIndex

    /** Number of alternates currently displayed. */
    val count: Int get() = alternateView.labels.size

    /** The label at [selectedIndex]. */
    val selectedLabel: String? get() = alternateView.labels.getOrNull(alternateView.selectedIndex)

    // ── Public API ────────────────────────────────────────────────────────────

    fun applyTheme(theme: KeyboardTheme) {
        alternateView.applyTheme(theme)
    }

    // Reusable coordinate array (zero allocation during show)
    private val viewLocation = IntArray(2)

    /**
     * Show the alternate popup anchored to a key.
     * Candidate #0 (e.g. Shift alternative) is positioned directly above the key center.
     * Remaining candidates fan inward towards the screen center for effortless reachability.
     *
     * @param anchor    The [KeyboardCanvasView] that owns this popup.
     * @param alts      Array of alternate character strings (capped at 4).
     * @param keyLeft   Key left edge in view-local px.
     * @param keyTop    Key top edge in view-local px.
     * @param keyRight  Key right edge in view-local px.
     * @param keyBottom Key bottom edge in view-local px.
     * @param density   Display density for dp→px conversions.
     */
    fun show(
        anchor: View,
        alts: Array<String>,
        keyLeft: Float,
        keyTop: Float,
        keyRight: Float,
        keyBottom: Float,
        density: Float,
    ) {
        val displayAlts = alts.take(4)
        if (displayAlts.isEmpty()) return

        val count = displayAlts.size
        val keyW = keyRight - keyLeft
        val pillW = (keyW * 0.95f).coerceAtLeast(36f * density)
        val pillH = ((keyBottom - keyTop) * 0.95f).coerceAtLeast(42f * density)
        val spacing = 4f * density
        val pad = 6f * density
        val totalW = (count * pillW + (count - 1) * spacing + pad * 2).toInt()
        val totalH = (pillH + pad * 2).toInt()

        // Convert key center to screen coordinates (using pre-allocated viewLocation)
        anchor.getLocationInWindow(viewLocation)
        val viewX = viewLocation[0]
        val viewY = viewLocation[1]

        val keyCenterX = viewX + keyLeft + keyW / 2f
        val screenWidth = anchor.rootView.width.takeIf { it > 0 } ?: (anchor.width.takeIf { it > 0 } ?: 1080)
        val margin = 6f * density

        // Zone-aware layout: ensure candidate #0 (Shift alternative) is anchored directly
        // above the key center so releasing commits it immediately. Remaining candidates fan inward.
        val (arrangedLabels, anchorIndex) = when {
            keyCenterX < screenWidth * 0.35f -> {
                // Left zone: Candidate 0 at index 0 (above key), variants fan right
                displayAlts to 0
            }
            keyCenterX > screenWidth * 0.65f -> {
                // Right zone: Candidate 0 at rightmost index (above key), variants fan left
                displayAlts.reversed() to (count - 1)
            }
            else -> {
                // Center zone: Candidate 0 centered above key
                when (count) {
                    1 -> displayAlts to 0
                    2 -> if (keyCenterX <= screenWidth * 0.5f) displayAlts to 0 else displayAlts.reversed() to 1
                    3 -> listOf(displayAlts[1], displayAlts[0], displayAlts[2]) to 1
                    else -> listOf(displayAlts[1], displayAlts[0], displayAlts[2], displayAlts[3]) to 1
                }
            }
        }

        alternateView.setAlternates(arrangedLabels, density, initialIndex = anchorIndex)

        val anchorPillCenterInPopup = pad + anchorIndex * (pillW + spacing) + pillW / 2f
        val popupScreenTop = (viewY + keyTop - 8f * density - totalH).toInt()
        val popupScreenLeft = (keyCenterX - anchorPillCenterInPopup)
            .coerceAtLeast(margin)
            .coerceAtMost(screenWidth - totalW - margin)
            .toInt()

        // Pass pill layout to the view before showing (so onDraw has correct geometry)
        alternateView.setPillLayout(
            count,
            pillW,
            pillH,
            spacing,
            pad,
            totalW.toFloat(),
            totalH.toFloat(),
            density,
        )

        if (popup.isShowing) {
            popup.update(popupScreenLeft, popupScreenTop, totalW, totalH)
        } else {
            popup.width = totalW
            popup.height = totalH
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popupScreenLeft, popupScreenTop)
        }
    }

    private val popupScreenLocation = IntArray(2)

    /**
     * Forward a MOVE event from [KeyboardCanvasView] to update the selection highlight.
     * [touchX] must be in view-local coordinates (event.x from onTouchEvent).
     * Returns true if the selected index changed.
     */
    fun onTouchMove(touchX: Float, touchY: Float, anchorViewX: Int): Boolean {
        if (!popup.isShowing) return false
        val content = popup.contentView ?: return false
        content.getLocationOnScreen(popupScreenLocation)
        val popupX = touchX + anchorViewX - popupScreenLocation[0]
        return alternateView.updateSelection(popupX)
    }

    fun dismiss() {
        if (popup.isShowing) popup.dismiss()
    }

    // ── Content view implementation ───────────────────────────────────────────

    internal class AlternateView(context: Context) : View(context) {

        internal val labels = ArrayList<String>(8)
        internal var selectedIndex: Int = 0
            private set

        private var pillCount: Int = 0
        private var pillW: Float = 0f
        private var pillH: Float = 0f
        private var spacing: Float = 0f
        private var pad: Float = 0f
        private var density: Float = 1f

        // Pre-allocated pill rects (zero allocation in onDraw)
        private val pillRects = Array(8) { RectF() }

        // Pre-allocated paints
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF243048.toInt()
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x4D000000
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = 0x33FFFFFF.toInt()
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF00C896.toInt()
        }
        private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x1AFFFFFF.toInt()
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            color = Color.WHITE
        }
        private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            color = Color.BLACK
        }

        private val bgRect = RectF()
        private val shadowRect = RectF()

        fun applyTheme(theme: KeyboardTheme) {
            bgPaint.color = if (theme.isDark) {
                Color.rgb(
                    (Color.red(theme.keyNormalColor) + 24).coerceAtMost(255),
                    (Color.green(theme.keyNormalColor) + 28).coerceAtMost(255),
                    (Color.blue(theme.keyNormalColor) + 38).coerceAtMost(255),
                )
            } else {
                0xFFFFFFFF.toInt()
            }
            strokePaint.color = if (theme.isDark) 0x33FFFFFF.toInt() else 0x1A000000
            highlightPaint.color = theme.accentColor
            pillBgPaint.color = if (theme.isDark) 0x1AFFFFFF.toInt() else 0x0D000000
            textPaint.color = theme.labelColor
            selectedTextPaint.color = if (theme.isDark) Color.BLACK else Color.WHITE
            invalidate()
        }

        fun setAlternates(alts: List<String>, density: Float, initialIndex: Int = 0) {
            this.density = density
            labels.clear()
            labels.addAll(alts)
            selectedIndex = initialIndex.coerceIn(0, (alts.size - 1).coerceAtLeast(0))
            pillCount = alts.size
        }

        fun setPillLayout(
            count: Int,
            pillW: Float,
            pillH: Float,
            spacing: Float,
            pad: Float,
            totalW: Float,
            totalH: Float,
            density: Float,
        ) {
            this.pillCount = count
            this.pillW = pillW
            this.pillH = pillH
            this.spacing = spacing
            this.pad = pad
            this.density = density

            bgRect.set(0f, 0f, totalW, totalH)
            shadowRect.set(0f, 2f * density, totalW, totalH + 4f * density)

            for (i in 0 until count) {
                val pLeft = pad + i * (pillW + spacing)
                val pTop = pad
                pillRects[i].set(pLeft, pTop, pLeft + pillW, pTop + pillH)
            }

            textPaint.textSize = pillH * 0.42f
            selectedTextPaint.textSize = pillH * 0.46f
            invalidate()
        }

        /** Returns true if selection changed. */
        fun updateSelection(popupLocalX: Float): Boolean {
            var best = selectedIndex
            var minDist = Float.MAX_VALUE
            for (i in 0 until pillCount) {
                val dist = kotlin.math.abs(popupLocalX - pillRects[i].centerX())
                if (dist < minDist) {
                    minDist = dist
                    best = i
                }
            }
            if (best != selectedIndex) {
                selectedIndex = best
                invalidate()
                return true
            }
            return false
        }

        override fun onDraw(canvas: Canvas) {
            val r = height * 0.18f
            val pr = pillH * 0.22f
            // Background card
            canvas.drawRoundRect(shadowRect, r, r, shadowPaint)
            canvas.drawRoundRect(bgRect, r, r, bgPaint)
            canvas.drawRoundRect(bgRect, r, r, strokePaint)
            // Pills
            for (i in 0 until pillCount) {
                val rect = pillRects[i]
                val isSelected = (i == selectedIndex)
                if (isSelected) {
                    canvas.drawRoundRect(rect, pr, pr, highlightPaint)
                } else {
                    canvas.drawRoundRect(rect, pr, pr, pillBgPaint)
                }
                val label = labels.getOrNull(i) ?: continue
                val displayLabel = com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(label)
                val tp = if (isSelected) selectedTextPaint else textPaint
                val ty = rect.centerY() - (tp.ascent() + tp.descent()) / 2f
                canvas.drawText(displayLabel, rect.centerX(), ty, tp)
            }
        }
    }
}
