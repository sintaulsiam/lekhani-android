package com.lekhani.android.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.lekhani.android.theme.ChromaMode
import com.lekhani.android.theme.ThemeChromaUtils

/**
 * KeyboardPainter
 *
 * Implements the **Painter / Renderer Pattern** for [KeyboardCanvasView].
 * Decouples all vector glyph geometry, Chroma calculations, and hardware canvas
 * operations from IME view lifecycle and touch handling.
 *
 * Guarantees:
 *   - Zero runtime memory allocations inside drawing methods.
 *   - Pre-allocated scratchpad buffers (`Path`, `RectF`) instantiated once.
 */
class KeyboardPainter {

    // ── Pre-allocated scratchpad buffers (Zero GC in onDraw) ─────────────────
    val vectorIconPath = Path()
    val scratchRect = RectF()

    /**
     * Draws the Backspace vector icon.
     */
    fun drawVectorBackspace(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        vectorIconPath.rewind()
        val halfW = size * 0.45f
        val halfH = size * 0.32f
        val arrowW = size * 0.28f

        vectorIconPath.moveTo(cx - halfW, cy)
        vectorIconPath.lineTo(cx - halfW + arrowW, cy - halfH)
        vectorIconPath.lineTo(cx + halfW, cy - halfH)
        vectorIconPath.lineTo(cx + halfW, cy + halfH)
        vectorIconPath.lineTo(cx - halfW + arrowW, cy + halfH)
        vectorIconPath.close()

        val crossR = size * 0.12f
        val crossCx = cx + arrowW * 0.35f
        vectorIconPath.moveTo(crossCx - crossR, cy - crossR)
        vectorIconPath.lineTo(crossCx + crossR, cy + crossR)
        vectorIconPath.moveTo(crossCx + crossR, cy - crossR)
        vectorIconPath.lineTo(crossCx - crossR, cy + crossR)

        canvas.drawPath(vectorIconPath, paint)
    }

    /**
     * Draws the Shift vector icon (arrow indicating shifted / locked state).
     */
    fun drawVectorShift(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        isShifted: Boolean,
        isLocked: Boolean,
        strokePaint: Paint,
        fillPaint: Paint,
    ) {
        vectorIconPath.rewind()
        val halfW = size * 0.38f
        val stemHalfW = size * 0.18f
        val arrowH = size * 0.32f
        val stemH = size * 0.26f

        val topY = cy - (arrowH + stemH) / 2f
        val notchY = topY + arrowH
        val bottomY = topY + arrowH + stemH

        vectorIconPath.moveTo(cx, topY)
        vectorIconPath.lineTo(cx + halfW, notchY)
        vectorIconPath.lineTo(cx + stemHalfW, notchY)
        vectorIconPath.lineTo(cx + stemHalfW, bottomY)
        vectorIconPath.lineTo(cx - stemHalfW, bottomY)
        vectorIconPath.lineTo(cx - stemHalfW, notchY)
        vectorIconPath.lineTo(cx - halfW, notchY)
        vectorIconPath.close()

        if (isLocked) {
            canvas.drawPath(vectorIconPath, fillPaint)
            scratchRect.set(cx - stemHalfW, bottomY + 3f, cx + stemHalfW, bottomY + 7f)
            canvas.drawRoundRect(scratchRect, 2f, 2f, fillPaint)
        } else if (isShifted) {
            canvas.drawPath(vectorIconPath, fillPaint)
        } else {
            canvas.drawPath(vectorIconPath, strokePaint)
        }
    }

    /**
     * Draws the Return / Enter vector icon.
     */
    fun drawVectorEnter(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        vectorIconPath.rewind()
        val halfW = size * 0.38f
        val halfH = size * 0.28f
        val arrowHead = size * 0.16f

        vectorIconPath.moveTo(cx + halfW, cy - halfH)
        vectorIconPath.lineTo(cx + halfW, cy + halfH * 0.3f)
        vectorIconPath.lineTo(cx - halfW, cy + halfH * 0.3f)

        vectorIconPath.moveTo(cx - halfW + arrowHead, cy + halfH * 0.3f - arrowHead)
        vectorIconPath.lineTo(cx - halfW, cy + halfH * 0.3f)
        vectorIconPath.lineTo(cx - halfW + arrowHead, cy + halfH * 0.3f + arrowHead)

        canvas.drawPath(vectorIconPath, strokePaint)
    }

    /**
     * Draws the Search vector icon (magnifying glass).
     */
    fun drawVectorSearch(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val glassRadius = size * 0.26f
        val glassCenterX = cx - size * 0.08f
        val glassCenterY = cy - size * 0.08f

        canvas.drawCircle(glassCenterX, glassCenterY, glassRadius, strokePaint)

        val handleAngle = Math.toRadians(45.0)
        val startX = (glassCenterX + glassRadius * Math.cos(handleAngle)).toFloat()
        val startY = (glassCenterY + glassRadius * Math.sin(handleAngle)).toFloat()
        val endX = startX + size * 0.26f
        val endY = startY + size * 0.26f

        canvas.drawLine(startX, startY, endX, endY, strokePaint)
    }

    /**
     * Draws the Send vector icon (paper airplane).
     */
    fun drawVectorSend(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
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

    /**
     * Draws the Go / Forward vector icon.
     */
    fun drawVectorGo(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
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

    /**
     * Draws the Next vector icon.
     */
    fun drawVectorNext(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
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

    /**
     * Draws the Done vector icon (checkmark).
     */
    fun drawVectorDone(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
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

    /**
     * Draws the Expand vector icon (corners expanding outwards).
     */
    fun drawVectorExpand(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val s = size * 0.40f
        val b = size * 0.18f
        vectorIconPath.rewind()
        vectorIconPath.moveTo(cx - s, cy - s + b); vectorIconPath.lineTo(cx - s, cy - s); vectorIconPath.lineTo(cx - s + b, cy - s)
        vectorIconPath.moveTo(cx + s - b, cy - s); vectorIconPath.lineTo(cx + s, cy - s); vectorIconPath.lineTo(cx + s, cy - s + b)
        vectorIconPath.moveTo(cx - s, cy + s - b); vectorIconPath.lineTo(cx - s, cy + s); vectorIconPath.lineTo(cx - s + b, cy + s)
        vectorIconPath.moveTo(cx + s - b, cy + s); vectorIconPath.lineTo(cx + s, cy + s); vectorIconPath.lineTo(cx + s, cy + s - b)
        canvas.drawPath(vectorIconPath, strokePaint)
    }

    /**
     * Draws a Chevron vector icon pointing left or right.
     */
    fun drawVectorChevron(canvas: Canvas, cx: Float, cy: Float, size: Float, isLeft: Boolean, strokePaint: Paint) {
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

    /**
     * Draws the Floating keyboard icon.
     */
    fun drawVectorFloating(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val halfW = size * 0.42f
        val halfH = size * 0.32f
        scratchRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        canvas.drawRoundRect(scratchRect, 3f, 3f, strokePaint)
        canvas.drawLine(cx - halfW, cy - halfH * 0.35f, cx + halfW, cy - halfH * 0.35f, strokePaint)
    }

    /**
     * Draws the Globe language-switch icon.
     */
    fun drawVectorGlobe(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint) {
        val r = size * 0.38f
        canvas.drawCircle(cx, cy, r, strokePaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, strokePaint)
        scratchRect.set(cx - r * 0.46f, cy - r, cx + r * 0.46f, cy + r)
        canvas.drawOval(scratchRect, strokePaint)
    }

    /**
     * Draws the Emoji / Smiley icon.
     */
    fun drawVectorSmiley(canvas: Canvas, cx: Float, cy: Float, size: Float, strokePaint: Paint, fillPaint: Paint) {
        val r = size * 0.38f
        canvas.drawCircle(cx, cy, r, strokePaint)
        val eyeOffsetX = r * 0.38f
        val eyeOffsetY = r * 0.26f
        val eyeR = (r * 0.11f).coerceAtLeast(1.5f)
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeR, fillPaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeR, fillPaint)
        scratchRect.set(cx - r * 0.50f, cy - r * 0.30f, cx + r * 0.50f, cy + r * 0.55f)
        canvas.drawArc(scratchRect, 25f, 130f, false, strokePaint)
    }

    /**
     * Draws the dialpad / numpad 3x4 dot grid.
     */
    fun drawVectorNumpad(canvas: Canvas, cx: Float, cy: Float, size: Float, fillPaint: Paint) {
        val dotRadius = (size * 0.085f).coerceAtLeast(1.5f)
        val spacingX = size * 0.28f
        val spacingY = size * 0.24f

        canvas.drawCircle(cx - spacingX, cy - 1.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx, cy - 1.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx + spacingX, cy - 1.5f * spacingY, dotRadius, fillPaint)

        canvas.drawCircle(cx - spacingX, cy - 0.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx, cy - 0.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx + spacingX, cy - 0.5f * spacingY, dotRadius, fillPaint)

        canvas.drawCircle(cx - spacingX, cy + 0.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx, cy + 0.5f * spacingY, dotRadius, fillPaint)
        canvas.drawCircle(cx + spacingX, cy + 0.5f * spacingY, dotRadius, fillPaint)

        canvas.drawCircle(cx, cy + 1.5f * spacingY, dotRadius, fillPaint)
    }

    // Pre-allocated HSV buffer for zero-allocation chroma computation
    private val rgbHsv = FloatArray(3)

    /**
     * Evaluates dynamic RGB chroma color based on current time and horizontal key proportion.
     */
    fun getChromaColor(mode: ChromaMode, now: Long, xRatio: Float = 0.5f): Int {
        return ThemeChromaUtils.getColor(mode, now, xRatio, rgbHsv)
    }
}
