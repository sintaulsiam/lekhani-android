package com.lekhani.android.canvas

import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.feedback.LekhaniFeedbackManager
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import java.util.ArrayList

/**
 * TouchGestureDispatcher
 *
 * Implements the **Touch & Gesture Processing Pipeline** for [KeyboardCanvasView].
 * Decouples multi-touch tracking, glide typing trails, spacebar cursor sliding,
 * layout switching gestures, Probaho 2.0 flicks, and backspace swipe-to-delete.
 *
 * Guarantees:
 *   - Strictly zero runtime object allocations or `new` in touch loops (Tier 1 Zero-Alloc).
 *   - All buffers, coordinate arrays, and runnables pre-allocated.
 */
class TouchGestureDispatcher(
    private val host: Host
) {

    interface Host {
        val view: View
        val resolvedKeys: List<ResolvedKey>
        val isShifted: Boolean
        val isCapsLock: Boolean
        val isBottomRowKeyEmoji: Boolean
        val layoutType: LekhaniLayoutType
        val spacebarSwipeMode: KeyboardPreferences.SpacebarSwipeMode
        val swipeToDeleteEnabled: Boolean
        val swipeUpFlickEnabled: Boolean
        val glideTypingEnabled: Boolean
        val showKeyPreviews: Boolean
        val longPressDelayMs: Long
        val longPressClipboardShortcuts: Boolean
        val isSideDockVisible: Boolean
        val sideDockButtons: Array<SideDockButton>
        val formFactor: KeyboardPreferences.FormFactor
        val keyMarginH: Float
        val density: Float
        val feedbackManager: LekhaniFeedbackManager?
        val keyListener: KeyboardCanvasView.KeyListener?

        fun findKeyIndex(x: Float, y: Float): Int
        fun dispatchKey(key: Key, touchX: Float, touchY: Float)
        fun showKeyPreview(key: Key, bounds: RectF)
        fun showFlickKeyPreview(label: String, bounds: RectF)
        fun dismissKeyPreview()
        fun showAlternatePopup(resolvedKey: ResolvedKey, alts: Array<String>)
        fun dismissAlternatePopup()
        fun isAlternatePopupShowing(): Boolean
        fun onAlternatePopupTouchMove(x: Float, y: Float, viewLocationX: Int): Boolean
        val alternatePopupSelectedLabel: String?
        fun getResolvedAlternates(key: Key, isShifted: Boolean): Array<String>
        fun invalidate()
    }

    // ── Glide coordinates buffer & state (pre-allocated) ─────────────────────
    val MAX_GLIDE_POINTS = 256
    val glidePointsX = FloatArray(MAX_GLIDE_POINTS)
    val glidePointsY = FloatArray(MAX_GLIDE_POINTS)
    var glidePointCount = 0
        private set
    var isGliding = false
        private set
    private val visitedGlideKeys = ArrayList<String>(32)
    private var lastVisitedKeyIdx = -1
    private var glideMinDistancePx = 0f
    private var glideSampleDistSq = 0f
    var glideCurX = 0f
        private set
    var glideCurY = 0f
        private set

    // ── Touch state (mutated in place, zero allocation) ──────────────────────
    var pressedKeyIndex: Int = -1
        private set
    private var pressedPointerId: Int = -1
    var pressStartTime: Long = 0L
        private set
    var touchStartX = 0f
        private set
    var touchStartY = 0f
        private set

    private var isLongPressTriggered: Boolean = false
    var isFlickGestureActive: Boolean = false
        private set
    var isFlickDownActive: Boolean = false
        private set

    // ── Spacebar gesture detection state ─────────────────────────────────────
    var spaceTouchStartX: Float = 0f
        private set
    var spaceTouchStartY: Float = 0f
        private set
    var isSpaceSwiping: Boolean = false
        private set
    var isSpaceCursorMoving: Boolean = false
        private set
    var spaceSlideLastX: Float = 0f
        private set
    private var swipeThresholdPx: Float = 0f
    private var spaceSlideThresholdPx: Float = 0f
    private var spaceSlideStepPx: Float = 0f
    private var flickThresholdPx: Float = 0f

    // ── Backspace swipe delete state ─────────────────────────────────────────
    private var backspaceSwipeStartX: Float = 0f
    private var backspaceSwipeStartY: Float = 0f
    private var backspaceSwipeStepPx: Float = 0f
    var isBackspaceSwiping: Boolean = false
        private set
    var isSwipeDeleteGestureActive: Boolean = false
        private set
    private var isBackspaceDisarmed: Boolean = false
    var backspaceDeletedWordCount: Int = 0
        private set
    private var backspaceRepeatCount = 0

    // Reusable view location buffer to avoid allocating IntArray in onTouchEvent
    private val viewLocation = IntArray(2)

    // Pre-allocated long-press runnable
    private val longPressRunnable = Runnable {
        if (pressedKeyIndex in host.resolvedKeys.indices) {
            val key = host.resolvedKeys[pressedKeyIndex].key
            isLongPressTriggered = true
            host.feedbackManager?.onLongPressFeedback(host.view)
                ?: host.view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val longAction = key.activeLongPressAction(host.isShifted)

            // Optional clipboard shortcuts on English/Avro Latin layouts (C=Copy, V=Paste, X=Cut, A=SelectAll)
            if (host.longPressClipboardShortcuts && (host.layoutType == LekhaniLayoutType.ENGLISH || host.layoutType == LekhaniLayoutType.AVRO)) {
                val clipboardAction = when (key.label.lowercase()) {
                    "c" -> KeyAction.Copy
                    "v" -> KeyAction.Paste
                    "x" -> KeyAction.Cut
                    "a" -> KeyAction.SelectAll
                    else -> null
                }
                if (clipboardAction != null) {
                    host.dismissKeyPreview()
                    host.keyListener?.onKey(key, clipboardAction)
                    host.invalidate()
                    return@Runnable
                }
            }

            val alts = host.getResolvedAlternates(key, host.isShifted)
            when {
                key.action == KeyAction.Space || key.action == KeyAction.SwitchLayout -> {
                    host.keyListener?.onSpaceLongPress()
                }
                alts.isNotEmpty() -> {
                    host.showAlternatePopup(host.resolvedKeys[pressedKeyIndex], alts)
                }
                longAction != null -> {
                    host.keyListener?.onKey(key, longAction)
                }
                else -> {
                    val activeHint = key.displayHint(host.isShifted)
                    if (activeHint != null) {
                        host.keyListener?.onKey(key, KeyAction.Character(activeHint))
                    } else if (key.shiftedLabel != null && key.shiftedLabel != key.label) {
                        host.keyListener?.onKey(key, key.shiftedAction)
                    }
                }
            }
            host.invalidate()
        }
    }

    // Pre-allocated backspace repeat runnable
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (pressedKeyIndex in host.resolvedKeys.indices && !isBackspaceSwiping) {
                val key = host.resolvedKeys[pressedKeyIndex].key
                if (key.action == KeyAction.Backspace) {
                    isLongPressTriggered = true
                    backspaceRepeatCount++
                    host.feedbackManager?.onKeyFeedback(host.view)
                        ?: host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    host.keyListener?.onKey(key, KeyAction.Backspace)
                    val nextDelay = if (backspaceRepeatCount > 10) 35L else 55L
                    host.view.postDelayed(this, nextDelay)
                }
            }
        }
    }

    fun updateDimensions(density: Float) {
        swipeThresholdPx = 28f * density
        flickThresholdPx = 11f * density
        spaceSlideThresholdPx = 16f * density
        spaceSlideStepPx = 16f * density
        backspaceSwipeStepPx = 28f * density

        glideMinDistancePx = 52f * density
        glideSampleDistSq = (18f * density) * (18f * density)
    }

    fun resetTouchState() {
        host.view.removeCallbacks(longPressRunnable)
        host.view.removeCallbacks(backspaceRepeatRunnable)
        pressedKeyIndex = -1
        pressedPointerId = -1
        isLongPressTriggered = false
        isFlickGestureActive = false
        isFlickDownActive = false
        isSpaceSwiping = false
        isSpaceCursorMoving = false
        isGliding = false
        glidePointCount = 0
        visitedGlideKeys.clear()
        lastVisitedKeyIdx = -1
        isBackspaceSwiping = false
        isSwipeDeleteGestureActive = false
        isBackspaceDisarmed = false
        backspaceDeletedWordCount = 0
    }

    fun cancelSwipeDelete() {
        if (isBackspaceSwiping || isSwipeDeleteGestureActive || isBackspaceDisarmed) {
            isBackspaceSwiping = false
            isSwipeDeleteGestureActive = false
            isBackspaceDisarmed = true
            backspaceDeletedWordCount = 0
            host.view.removeCallbacks(backspaceRepeatRunnable)
            host.invalidate()
        }
    }

    fun onDetachedFromWindow() {
        host.view.removeCallbacks(longPressRunnable)
        host.view.removeCallbacks(backspaceRepeatRunnable)
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val px = event.getX(actionIndex)
        val py = event.getY(actionIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    // Commit pending key from previous pointer before switching to new pointer
                    if (pressedKeyIndex in host.resolvedKeys.indices && !isGliding && !isSpaceSwiping &&
                        !isSpaceCursorMoving && !isBackspaceSwiping && !isLongPressTriggered) {
                        host.dispatchKey(host.resolvedKeys[pressedKeyIndex].key, touchStartX, touchStartY)
                    }
                    host.view.removeCallbacks(longPressRunnable)
                    host.view.removeCallbacks(backspaceRepeatRunnable)
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
                if (host.isSideDockVisible) {
                    for (btn in host.sideDockButtons) {
                        if (btn.bounds.contains(px, py)) {
                            host.feedbackManager?.onKeyFeedback(host.view)
                                ?: host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            when (btn.action) {
                                SideDockAction.EXPAND_STANDARD -> host.keyListener?.onFormFactorChange(KeyboardPreferences.FormFactor.STANDARD)
                                SideDockAction.SWAP_SIDE -> {
                                    val next = if (host.formFactor == KeyboardPreferences.FormFactor.ONE_HANDED_LEFT) {
                                        KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                                    } else {
                                        KeyboardPreferences.FormFactor.ONE_HANDED_LEFT
                                    }
                                    host.keyListener?.onFormFactorChange(next)
                                }
                                SideDockAction.TOGGLE_FLOATING -> host.keyListener?.onFormFactorChange(KeyboardPreferences.FormFactor.FLOATING)
                            }
                            return true
                        }
                    }
                }

                val idx = host.findKeyIndex(px, py)
                if (idx >= 0) {
                    pressedKeyIndex = idx
                    pressStartTime = SystemClock.uptimeMillis()
                    isLongPressTriggered = false
                    isFlickGestureActive = false
                    isFlickDownActive = false
                    isSpaceSwiping = false
                    isSpaceCursorMoving = false
                    isBackspaceSwiping = false
                    backspaceDeletedWordCount = 0

                    host.feedbackManager?.onKeyFeedback(host.view)
                        ?: host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    val key = host.resolvedKeys[idx].key
                    val keyAct = key.activeAction(host.isShifted)
                    if (host.glideTypingEnabled && keyAct is KeyAction.Character) {
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
                        host.view.postDelayed(longPressRunnable, host.longPressDelayMs)
                    } else if (keyAct is KeyAction.Character || key.action == KeyAction.SwitchLayout || key.activeLongPressAction(host.isShifted) != null) {
                        host.view.postDelayed(longPressRunnable, host.longPressDelayMs)
                    } else if (key.action == KeyAction.Backspace) {
                        backspaceSwipeStartX = px
                        backspaceSwipeStartY = py
                        backspaceRepeatCount = 0
                        isSwipeDeleteGestureActive = false
                        isBackspaceDisarmed = false
                        host.view.postDelayed(backspaceRepeatRunnable, 350L)
                    }

                    // Show key preview popup for character keys
                    if (host.showKeyPreviews && keyAct is KeyAction.Character && key.label.isNotEmpty()) {
                        host.showKeyPreview(key, host.resolvedKeys[idx].bounds)
                    }
                    host.invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (host.isAlternatePopupShowing()) {
                    host.view.getLocationInWindow(viewLocation)
                    val changed = host.onAlternatePopupTouchMove(event.x, event.y, viewLocation[0])
                    if (changed) host.feedbackManager?.onTickFeedback(host.view)
                    return true
                }

                val curIndex = if (pressedPointerId >= 0) event.findPointerIndex(pressedPointerId) else 0
                val curX = if (curIndex in 0 until event.pointerCount) event.getX(curIndex) else event.x
                val curY = if (curIndex in 0 until event.pointerCount) event.getY(curIndex) else event.y
                glideCurX = curX
                glideCurY = curY

                val moveDistSq = (curX - touchStartX) * (curX - touchStartX) + (curY - touchStartY) * (curY - touchStartY)
                val touchSlopPx = 8f * host.density

                val isOverBackspace = if (pressedKeyIndex in host.resolvedKeys.indices) {
                    val bKey = host.resolvedKeys[pressedKeyIndex].key
                    if (bKey.action == KeyAction.Backspace) {
                        val bBounds = host.resolvedKeys[pressedKeyIndex].bounds
                        val slopX = 28f * host.density
                        val slopY = 20f * host.density
                        curX >= bBounds.left - slopX && curX <= bBounds.right + slopX &&
                            curY >= bBounds.top - slopY && curY <= bBounds.bottom + slopY
                    } else false
                } else false

                if (!isOverBackspace && moveDistSq > touchSlopPx * touchSlopPx) {
                    host.view.removeCallbacks(longPressRunnable)
                    host.view.removeCallbacks(backspaceRepeatRunnable)
                    // Dismiss key preview once finger moves (unless flick gesture is active)
                    if (!isFlickGestureActive) {
                        host.dismissKeyPreview()
                    }
                }

                if (pressedKeyIndex in host.resolvedKeys.indices) {
                    val key = host.resolvedKeys[pressedKeyIndex].key

                    // Spacebar cursor slide navigation (active when spacebarSwipeMode == CURSOR_NAV)
                    if (key.action == KeyAction.Space && host.spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.CURSOR_NAV) {
                        val dx = curX - spaceTouchStartX
                        val absDx = kotlin.math.abs(dx)
                        if (!isSpaceCursorMoving && absDx > spaceSlideThresholdPx) {
                            isSpaceCursorMoving = true
                            isSpaceSwiping = false
                            host.view.removeCallbacks(longPressRunnable)
                        }
                        if (isSpaceCursorMoving) {
                            val stepDelta = curX - spaceSlideLastX
                            if (stepDelta >= spaceSlideStepPx) {
                                val steps = (stepDelta / spaceSlideStepPx).toInt()
                                host.keyListener?.onCursorMove(steps)
                                spaceSlideLastX += steps * spaceSlideStepPx
                                host.feedbackManager?.onTickFeedback(host.view)
                                    ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                host.invalidate()
                            } else if (stepDelta <= -spaceSlideStepPx) {
                                val steps = (-stepDelta / spaceSlideStepPx).toInt()
                                host.keyListener?.onCursorMove(-steps)
                                spaceSlideLastX -= steps * spaceSlideStepPx
                                host.feedbackManager?.onTickFeedback(host.view)
                                    ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                host.invalidate()
                            }
                            return true
                        }
                    }

                    // Swipe-to-delete gesture on Backspace
                    if (key.action == KeyAction.Backspace && host.swipeToDeleteEnabled) {
                        val dx = curX - backspaceSwipeStartX
                        val dy = curY - backspaceSwipeStartY

                        if (!isBackspaceSwiping && host.keyListener?.canSwipeDelete() == false) {
                            return true
                        }

                        if (isBackspaceDisarmed) {
                            return true
                        }

                        // Early cancellation of hold-repeat as soon as horizontal swipe intent is detected
                        if (kotlin.math.abs(dx) > 8f * host.density) {
                            host.view.removeCallbacks(backspaceRepeatRunnable)
                        }

                        // Downward swipe cancellation
                        if (dy > 32f * host.density) {
                            if (isBackspaceSwiping) {
                                isBackspaceSwiping = false
                                isSwipeDeleteGestureActive = false
                                isBackspaceDisarmed = true
                                backspaceDeletedWordCount = 0
                                host.keyListener?.onSwipeDeletePreview(0)
                                host.feedbackManager?.onTickFeedback(host.view)
                                    ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                host.invalidate()
                            }
                            return true
                        }

                        if (dx < -swipeThresholdPx) {
                            host.view.removeCallbacks(backspaceRepeatRunnable)
                            isBackspaceSwiping = true
                            isSwipeDeleteGestureActive = true
                            val words = ((-dx - swipeThresholdPx) / backspaceSwipeStepPx).toInt() + 1
                            val clamped = words.coerceIn(1, 15)
                            if (clamped != backspaceDeletedWordCount) {
                                backspaceDeletedWordCount = clamped
                                host.keyListener?.onSwipeDeletePreview(backspaceDeletedWordCount)
                                host.feedbackManager?.onTickFeedback(host.view)
                                    ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                host.invalidate()
                            }
                            return true
                        } else if (isBackspaceSwiping) {
                            // User dragged back right towards Backspace key
                            if (dx > -16f * host.density) {
                                isBackspaceSwiping = false
                                isSwipeDeleteGestureActive = false
                                backspaceDeletedWordCount = 0
                                host.keyListener?.onSwipeDeletePreview(0)
                                host.feedbackManager?.onTickFeedback(host.view)
                                    ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                host.invalidate()
                            } else {
                                if (backspaceDeletedWordCount != 0) {
                                    backspaceDeletedWordCount = 0
                                    host.keyListener?.onSwipeDeletePreview(0)
                                    host.feedbackManager?.onTickFeedback(host.view)
                                        ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    host.invalidate()
                                }
                            }
                            return true
                        }
                    }

                    // Spacebar layout swipe detection (active when spacebarSwipeMode == LAYOUT_SWITCH)
                    if (key.action == KeyAction.Space && host.spacebarSwipeMode == KeyboardPreferences.SpacebarSwipeMode.LAYOUT_SWITCH &&
                        !isSpaceSwiping && !isSpaceCursorMoving && !isLongPressTriggered) {
                        val dx = curX - spaceTouchStartX
                        val dy = curY - spaceTouchStartY
                        val absDx = kotlin.math.abs(dx)
                        val absDy = kotlin.math.abs(dy)
                        if (dy < -swipeThresholdPx && absDy > absDx * 1.3f) {
                            // Upward swipe on spacebar opens Cursor/Text Editor immediately
                            isSpaceSwiping = true
                            pressedKeyIndex = -1
                            pressedPointerId = -1
                            host.view.removeCallbacks(longPressRunnable)
                            host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            host.keyListener?.onSpaceSwipeUp()
                            return true
                        } else if (absDx > swipeThresholdPx && absDx > absDy * 1.3f) {
                            isSpaceSwiping = true
                            pressedKeyIndex = -1
                            pressedPointerId = -1
                            host.view.removeCallbacks(longPressRunnable)
                            host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (dx > 0) {
                                host.keyListener?.onSpaceSwipe(1)  // Next layout (forward)
                            } else {
                                host.keyListener?.onSpaceSwipe(-1) // Previous layout (backward)
                            }
                            return true
                        }
                    }
                }

                // ── Swipe Flick Gesture Detection (Probaho 2.0 / rapid shifted & subscript access) ──
                if (host.swipeUpFlickEnabled && !isGliding && !isSpaceSwiping && !isSpaceCursorMoving &&
                    !isBackspaceSwiping && !host.isAlternatePopupShowing()) {
                    if (pressedKeyIndex in host.resolvedKeys.indices) {
                        val flickKey = host.resolvedKeys[pressedKeyIndex].key
                        val dy = curY - touchStartY
                        val dx = curX - touchStartX
                        val absDx = kotlin.math.abs(dx)

                        // 1. Swipe-Up Flick (Shifted character / Aspirated partner)
                        if (flickKey.action is KeyAction.Character && flickKey.shiftedLabel != null && flickKey.shiftedLabel != flickKey.label) {
                            if (-dy > flickThresholdPx && -dy > absDx * 0.75f) {
                                if (!isFlickGestureActive && !isFlickDownActive) {
                                    isFlickGestureActive = true
                                    host.view.removeCallbacks(longPressRunnable)
                                    val bounds = host.resolvedKeys[pressedKeyIndex].bounds
                                    host.showFlickKeyPreview(flickKey.shiftedLabel!!, bounds)
                                    host.feedbackManager?.onTickFeedback(host.view)
                                        ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            } else if (isFlickGestureActive && -dy < flickThresholdPx * 0.35f) {
                                isFlickGestureActive = false
                                val bounds = host.resolvedKeys[pressedKeyIndex].bounds
                                host.showFlickKeyPreview(flickKey.displayLabel(host.isShifted), bounds)
                                host.feedbackManager?.onTickFeedback(host.view)
                                    ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }

                        // 2. Swipe-Down Flick (Subscript numeric digit / secondary symbol)
                        if (flickKey.hintLabel != null && flickKey.longPressAction != null && !isFlickGestureActive) {
                            if (dy > flickThresholdPx && dy > absDx * 0.75f) {
                                if (!isFlickDownActive) {
                                    isFlickDownActive = true
                                    host.view.removeCallbacks(longPressRunnable)
                                    val bounds = host.resolvedKeys[pressedKeyIndex].bounds
                                    host.showFlickKeyPreview(flickKey.hintLabel!!, bounds)
                                    host.feedbackManager?.onTickFeedback(host.view)
                                        ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            } else if (isFlickDownActive && dy < flickThresholdPx * 0.35f) {
                                isFlickDownActive = false
                                val bounds = host.resolvedKeys[pressedKeyIndex].bounds
                                host.showFlickKeyPreview(flickKey.displayLabel(host.isShifted), bounds)
                                host.feedbackManager?.onTickFeedback(host.view)
                                    ?: host.view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
                }

                // Glide / Swipe typing gesture detection
                val totalDx = curX - touchStartX
                val totalDy = curY - touchStartY
                val totalDistSq = totalDx * totalDx + totalDy * totalDy

                if (host.glideTypingEnabled && !isGliding && !isSpaceSwiping && !isSpaceCursorMoving && !isBackspaceSwiping &&
                    totalDistSq > glideMinDistancePx * glideMinDistancePx) {
                    if (pressedKeyIndex in host.resolvedKeys.indices &&
                        host.resolvedKeys[pressedKeyIndex].key.activeAction(host.isShifted) is KeyAction.Character) {
                        isGliding = true
                        host.view.removeCallbacks(longPressRunnable)
                    }
                }

                if (host.glideTypingEnabled && isGliding) {
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

                    val keyIdx = host.findKeyIndex(curX, curY)
                    if (keyIdx >= 0 && keyIdx != lastVisitedKeyIdx) {
                        val actionK = host.resolvedKeys[keyIdx].key.activeAction(host.isShifted)
                        if (actionK is KeyAction.Character) {
                            visitedGlideKeys.add(actionK.token)
                            lastVisitedKeyIdx = keyIdx
                            host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    host.invalidate()
                    return true
                }

                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(actionIndex)
                if (pointerId == pressedPointerId) {
                    host.view.removeCallbacks(longPressRunnable)
                    host.view.removeCallbacks(backspaceRepeatRunnable)
                    val targetKeyIdx = if (pressedKeyIndex in host.resolvedKeys.indices) {
                        val bounds = host.resolvedKeys[pressedKeyIndex].bounds
                        val slop = (host.keyMarginH * 1.5f).coerceAtLeast(10f * host.density)
                        if (px >= bounds.left - slop && px <= bounds.right + slop &&
                            py >= bounds.top - slop && py <= bounds.bottom + slop) {
                            pressedKeyIndex
                        } else {
                            val idx = host.findKeyIndex(px, py)
                            if (idx == pressedKeyIndex) pressedKeyIndex else -1
                        }
                    } else {
                        -1
                    }
                    if (targetKeyIdx >= 0 && !isLongPressTriggered && !isSpaceSwiping &&
                        !isGliding && !isBackspaceSwiping && !isSpaceCursorMoving) {
                        host.dispatchKey(host.resolvedKeys[targetKeyIdx].key, touchStartX, touchStartY)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    isLongPressTriggered = false
                    isFlickGestureActive = false
                    isSpaceSwiping = false
                    host.dismissKeyPreview()
                    host.dismissAlternatePopup()
                    host.invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                host.view.removeCallbacks(longPressRunnable)
                host.view.removeCallbacks(backspaceRepeatRunnable)
                host.dismissKeyPreview()
                if (host.isAlternatePopupShowing()) {
                    val chosen = host.alternatePopupSelectedLabel
                    host.dismissAlternatePopup()
                    if (chosen != null && pressedKeyIndex in host.resolvedKeys.indices) {
                        val key = host.resolvedKeys[pressedKeyIndex].key
                        host.keyListener?.onKey(key, KeyAction.Character(chosen))
                        host.feedbackManager?.onKeyFeedback(host.view)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    host.invalidate()
                    return true
                }

                if (isSpaceSwiping) {
                    isSpaceSwiping = false
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    host.invalidate()
                    return true
                }

                if (isSpaceCursorMoving) {
                    isSpaceCursorMoving = false
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    host.invalidate()
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
                        host.keyListener?.onSwipeDelete(count)
                        host.feedbackManager?.onKeyFeedback(host.view)
                            ?: host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } else if (!wasDisarmed) {
                        host.keyListener?.onSwipeDeletePreview(0)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    host.invalidate()
                    return true
                }

                if (host.glideTypingEnabled && isGliding) {
                    if (visitedGlideKeys.size >= 2) {
                        host.keyListener?.onGlideGesture(visitedGlideKeys)
                    } else if (visitedGlideKeys.size == 1 && pressedKeyIndex >= 0) {
                        host.dispatchKey(host.resolvedKeys[pressedKeyIndex].key, touchStartX, touchStartY)
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
                    host.dismissKeyPreview()
                    host.invalidate()
                    return true
                }

                val upDy = py - touchStartY
                val upDx = px - touchStartX
                val isFlickRelease = host.swipeUpFlickEnabled && pressedKeyIndex in host.resolvedKeys.indices && run {
                    val flickKey = host.resolvedKeys[pressedKeyIndex].key
                    flickKey.action is KeyAction.Character &&
                        flickKey.shiftedLabel != null &&
                        flickKey.shiftedLabel != flickKey.label &&
                        (isFlickGestureActive || (-upDy >= flickThresholdPx && -upDy > kotlin.math.abs(upDx) * 0.75f))
                }
                val isFlickDownRelease = host.swipeUpFlickEnabled && pressedKeyIndex in host.resolvedKeys.indices && run {
                    val flickKey = host.resolvedKeys[pressedKeyIndex].key
                    flickKey.hintLabel != null &&
                        flickKey.longPressAction != null &&
                        (isFlickDownActive || (upDy >= flickThresholdPx && upDy > kotlin.math.abs(upDx) * 0.75f))
                }

                if (isFlickGestureActive || isFlickRelease) {
                    isFlickGestureActive = false
                    isFlickDownActive = false
                    host.dismissKeyPreview()
                    if (pressedKeyIndex in host.resolvedKeys.indices) {
                        val flickKey = host.resolvedKeys[pressedKeyIndex].key
                        host.keyListener?.onKeyWithTouch(flickKey, flickKey.shiftedAction, touchStartX, touchStartY)
                        host.keyListener?.onKey(flickKey, flickKey.shiftedAction)
                        host.feedbackManager?.onKeyFeedback(host.view)
                            ?: host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    isLongPressTriggered = false
                    isSpaceSwiping = false
                    host.invalidate()
                    return true
                }

                if (isFlickDownActive || isFlickDownRelease) {
                    isFlickGestureActive = false
                    isFlickDownActive = false
                    host.dismissKeyPreview()
                    if (pressedKeyIndex in host.resolvedKeys.indices) {
                        val flickKey = host.resolvedKeys[pressedKeyIndex].key
                        flickKey.longPressAction?.let { act ->
                            host.keyListener?.onKeyWithTouch(flickKey, act, touchStartX, touchStartY)
                            host.keyListener?.onKey(flickKey, act)
                            host.feedbackManager?.onKeyFeedback(host.view)
                                ?: host.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    pressedKeyIndex = -1
                    pressedPointerId = -1
                    isLongPressTriggered = false
                    isSpaceSwiping = false
                    host.invalidate()
                    return true
                }

                val targetKeyIdx = if (pressedKeyIndex in host.resolvedKeys.indices) {
                    val bounds = host.resolvedKeys[pressedKeyIndex].bounds
                    val slop = (host.keyMarginH * 1.5f).coerceAtLeast(10f * host.density)
                    if (px >= bounds.left - slop && px <= bounds.right + slop &&
                        py >= bounds.top - slop && py <= bounds.bottom + slop) {
                        pressedKeyIndex
                    } else {
                        val idx = host.findKeyIndex(px, py)
                        if (idx == pressedKeyIndex) pressedKeyIndex else -1
                    }
                } else {
                    -1
                }
                if (targetKeyIdx >= 0 && !isLongPressTriggered && !isSpaceSwiping) {
                    host.dispatchKey(host.resolvedKeys[targetKeyIdx].key, touchStartX, touchStartY)
                }
                pressedKeyIndex = -1
                pressedPointerId = -1
                isLongPressTriggered = false
                isSpaceSwiping = false
                host.invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                host.view.removeCallbacks(longPressRunnable)
                host.view.removeCallbacks(backspaceRepeatRunnable)
                host.dismissKeyPreview()
                host.dismissAlternatePopup()
                isGliding = false
                isSpaceCursorMoving = false
                isBackspaceSwiping = false
                isSwipeDeleteGestureActive = false
                isBackspaceDisarmed = false
                backspaceDeletedWordCount = 0
                host.keyListener?.onSwipeDeletePreview(0)
                isSpaceSwiping = false
                isFlickGestureActive = false
                isFlickDownActive = false
                pressedKeyIndex = -1
                pressedPointerId = -1
                host.invalidate()
                return true
            }
        }
        return false
    }
}
