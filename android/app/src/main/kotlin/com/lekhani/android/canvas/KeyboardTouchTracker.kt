package com.lekhani.android.canvas

import android.graphics.RectF
import com.lekhani.android.model.Key

/**
 * KeyboardTouchTracker
 *
 * Implements the **Touch & Spatial Resolution Tracker** for [KeyboardCanvasView].
 * Decouples multi-touch hit-testing, Gaussian spatial error compensation,
 * and gesture velocity / distance estimations from view layout and drawing.
 *
 * Performance guarantees:
 *   - Zero runtime allocations in the touch hot path ([findKeyIndex], [computeCursorSlideDelta]).
 *   - All calculations use pre-computed coordinates and primitives.
 */
class KeyboardTouchTracker {

    companion object {
        /** Vertical tolerance window (in px) when looking for keys on adjacent rows */
        const val KEY_Y_TOLERANCE = 24f

        /** De-emphasise vertical error when evaluating nearest key distances */
        const val ROW_Y_BIAS = 1.6f
    }

    /**
     * Resolves the nearest key index for touch coordinate ([x], [y]) using
     * spatial Gaussian weighting.
     *
     * Touch resolution strategy:
     *   1. Direct bounding-box containment check (fastest path).
     *   2. If touch landed in a gap or key margin, evaluate distance to key centers
     *      with horizontal preference ([ROW_Y_BIAS]) within the vertical row band.
     */
    fun findKeyIndex(
        x: Float,
        y: Float,
        keys: List<ResolvedKey>,
        viewWidth: Float,
        viewHeight: Float,
    ): Int {
        if (keys.isEmpty()) return -1

        val clampedX = x.coerceIn(0f, viewWidth.coerceAtLeast(1f))
        val clampedY = y.coerceIn(0f, viewHeight.coerceAtLeast(1f))

        // Fast direct hit-test (unless touch landed on the extreme horizontal border of a key)
        var directHit = -1
        for (i in keys.indices) {
            val b = keys[i].bounds
            if (b.contains(clampedX, clampedY)) {
                val edgeThreshold = b.width() * 0.12f
                val nearEdge = (clampedX - b.left) < edgeThreshold || (b.right - clampedX) < edgeThreshold
                if (!nearEdge) {
                    return i
                }
                directHit = i
                break
            }
        }

        var bestIdx = directHit
        var bestScore = Float.MAX_VALUE

        for (i in keys.indices) {
            val bounds = keys[i].bounds
            // Only consider keys within vertical band
            if (clampedY < bounds.top - KEY_Y_TOLERANCE || clampedY > bounds.bottom + KEY_Y_TOLERANCE) {
                continue
            }

            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val dx = clampedX - cx
            val dy = (clampedY - cy) * ROW_Y_BIAS
            val dist = dx * dx + dy * dy

            if (dist < bestScore) {
                bestScore = dist
                bestIdx = i
            }
        }

        return bestIdx
    }

    /**
     * Estimates number of words to delete based on drag distance from the Backspace key.
     * [thresholdPx] is the distance threshold per word deleted.
     */
    fun computeSwipeDeleteWordCount(dragDeltaX: Float, thresholdPx: Float): Int {
        if (dragDeltaX >= 0f || thresholdPx <= 0f) return 0
        return (-dragDeltaX / thresholdPx).toInt()
    }

    /**
     * Computes cursor movement steps based on horizontal drag delta across the Spacebar.
     */
    fun computeCursorSlideDelta(dragDeltaX: Float, stepPx: Float): Int {
        if (stepPx <= 0f) return 0
        return (dragDeltaX / stepPx).toInt()
    }
}
