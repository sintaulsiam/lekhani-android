package com.lekhani.android.ime

import android.view.View
import android.view.animation.DecelerateInterpolator
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.model.KeyboardLayout
import com.lekhani.android.model.NumberSymbolsLayout

/**
 * KeyboardModeController
 *
 * Encapsulates:
 * - View crossfade animation management for mode changes
 * - Numeric / Symbols / Numpad / Phone dialpad mode state machine
 * - Layout resolution for number and symbols views
 */
class KeyboardModeController {

    var isNumericMode: Boolean = false
    var isMoreSymbolsMode: Boolean = false
    var isBengaliDigitsMode: Boolean = false
    var isNumericFieldMode: Boolean = false
    var isPhoneDialpadMode: Boolean = false
    var previousLayoutBeforeNumeric: LekhaniLayoutType? = null

    /**
     * Crossfades [activeView] in with a smooth decelerate animation while immediately
     * hiding and resetting [otherViews].
     */
    fun crossfade(activeView: View?, vararg otherViews: View?) {
        for (view in otherViews) {
            view?.animate()?.cancel()
            view?.visibility = View.GONE
            view?.alpha = 0f
        }
        activeView?.let { view ->
            if (view.visibility == View.VISIBLE && view.alpha == 1f) {
                return
            }
            view.animate()?.cancel()
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate()
                .alpha(1f)
                .setDuration(120)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /**
     * Resolves the appropriate [KeyboardLayout] for numeric / symbols / phone dialpad states.
     */
    fun resolveNumberSymbolsLayout(currentLayoutType: LekhaniLayoutType): KeyboardLayout {
        val isEnglish = currentLayoutType == LekhaniLayoutType.ENGLISH
        return when {
            isPhoneDialpadMode -> NumberSymbolsLayout.phoneDialpadLayout
            isNumericFieldMode -> if (isBengaliDigitsMode) NumberSymbolsLayout.bengaliNumpadPinLayout else NumberSymbolsLayout.numpadPinLayout
            isMoreSymbolsMode -> NumberSymbolsLayout.moreSymbolsLayout
            isEnglish -> NumberSymbolsLayout.englishNumericLayout
            isBengaliDigitsMode -> NumberSymbolsLayout.bengaliNumericLayout
            else -> NumberSymbolsLayout.numericLayout
        }
    }

    /**
     * Resets numeric/symbol modes and returns the layout to restore.
     */
    fun restoreAlphaKeyboard(currentLayoutType: LekhaniLayoutType): LekhaniLayoutType {
        isNumericMode = false
        isMoreSymbolsMode = false
        isNumericFieldMode = false
        isPhoneDialpadMode = false
        val targetLayout = previousLayoutBeforeNumeric ?: currentLayoutType
        previousLayoutBeforeNumeric = null
        return targetLayout
    }

    fun reset() {
        isNumericMode = false
        isMoreSymbolsMode = false
        isNumericFieldMode = false
        isPhoneDialpadMode = false
        previousLayoutBeforeNumeric = null
    }
}
