package com.lekhani.android.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import com.lekhani.android.theme.KeyboardTheme

/**
 * KeyPreviewPopupWindow
 * A lightweight [PopupWindow] that renders the key-press preview bubble
 * (the "keycap" that floats above the pressed key).
 *
 * Rendering happens in its own window layer — completely outside the
 * [KeyboardCanvasView] bounds — so the bubble is always visible on every row,
 * including row 1, without colliding with the CandidateStripView above.
 *
 * Performance: zero allocations in [PreviewView.onDraw]. All Paint and RectF
 * objects are pre-allocated at construction.
 */
internal class KeyPreviewPopupWindow(context: Context) {

    // ── Content view ──────────────────────────────────────────────────────────

    private val previewView = PreviewView(context)

    // ── PopupWindow ───────────────────────────────────────────────────────────

    private val popup = PopupWindow(
        previewView,
        1,  // width set dynamically on show()
        1,  // height set dynamically on show()
        false,
    ).apply {
        isClippingEnabled = false       // Allow drawing above the IME strip
        isTouchable = false             // Pass all touches through to the keyboard
        animationStyle = android.R.style.Animation // subtle system fade
        elevation = 8f
    }

    val isShowing: Boolean get() = popup.isShowing

    // ── Public API ────────────────────────────────────────────────────────────

    fun applyTheme(theme: KeyboardTheme) {
        previewView.applyTheme(theme)
    }

    /**
     * Show or reposition the key preview bubble.
     *
     * @param anchor       The [KeyboardCanvasView] that owns this popup.
     * @param label        The character string to display (respects shift state).
     * @param keyLeft      Key left edge in view-local px.
     * @param keyTop       Key top edge in view-local px.
     * @param keyRight     Key right edge in view-local px.
     * @param keyBottom    Key bottom edge in view-local px.
     * @param density      Display density for dp→px conversions.
     */
    fun show(
        anchor: View,
        label: String,
        keyLeft: Float,
        keyTop: Float,
        keyRight: Float,
        keyBottom: Float,
        density: Float,
    ) {
        val keyW = keyRight - keyLeft
        val keyH = keyBottom - keyTop

        val popupW = (keyW * 1.35f).coerceAtLeast(48f * density).toInt()
        val popupH = (keyH * 1.25f).coerceAtLeast(50f * density).toInt()

        // Convert key center from view-local to screen coordinates
        val viewLocation = IntArray(2)
        anchor.getLocationInWindow(viewLocation)
        val viewX = viewLocation[0]
        val viewY = viewLocation[1]

        val keyCenterX = viewX + keyLeft + keyW / 2f
        // Always place the popup ABOVE the key — no flip needed since we're in window space
        val popupScreenTop = (viewY + keyTop - popupH - 6f * density).toInt()
        val popupScreenLeft = (keyCenterX - popupW / 2f)
            .coerceAtLeast(4f * density)
            .coerceAtMost(anchor.rootView.width - popupW - 4f * density)
            .toInt()

        previewView.setLabel(label)

        if (popup.isShowing) {
            popup.update(popupScreenLeft, popupScreenTop, popupW, popupH)
        } else {
            popup.width = popupW
            popup.height = popupH
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popupScreenLeft, popupScreenTop)
        }
    }

    fun dismiss() {
        if (popup.isShowing) popup.dismiss()
    }

    // ── Content view implementation ───────────────────────────────────────────

    private class PreviewView(context: Context) : View(context) {

        private var label: String = ""

        // Pre-allocated paints (zero allocation in onDraw)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.DITHER_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            color = Color.WHITE
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
            textPaint.color = theme.labelColor
            invalidate()
        }

        fun setLabel(newLabel: String) {
            val sanitized = com.lekhani.android.model.BengaliDiacriticsFormatter.formatForDisplay(newLabel)
            if (label != sanitized) {
                label = sanitized
                invalidate()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val density = resources.displayMetrics.density
            textPaint.textSize = h * 0.42f
            bgRect.set(0f, 0f, w.toFloat(), h.toFloat())
            shadowRect.set(0f, 3f * density, w.toFloat(), h + 3f * density)
        }

        override fun onDraw(canvas: Canvas) {
            val r = height * 0.22f
            canvas.drawRoundRect(shadowRect, r, r, shadowPaint)
            canvas.drawRoundRect(bgRect, r, r, bgPaint)
            canvas.drawRoundRect(bgRect, r, r, strokePaint)
            val ty = bgRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, bgRect.centerX(), ty, textPaint)
        }
    }
}
