package com.lekhani.android.canvas

import android.graphics.RectF
import com.lekhani.android.model.Key

/**
 * A resolved key pairs a [Key] model with its pre-computed pixel [bounds].
 * Pre-allocated as a flat list in [KeyboardCanvasView.onSizeChanged];
 * never re-allocated in draw or touch event loops.
 */
data class ResolvedKey(var key: Key, val bounds: RectF)
