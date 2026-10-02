package com.lekhani.android.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Determines the appropriate behavior for the Enter key based on the active
 * editor's [EditorInfo], input flags, and modifier state.
 *
 * Adheres to Android IME guidelines:
 * 1. Shift+Enter always forces a literal newline ('\n').
 * 2. If [EditorInfo.IME_FLAG_NO_ENTER_ACTION] is set (automatically applied by
 *    Android to multiline TextViews), Enter always inserts a newline.
 * 3. Multi-line fields (e.g. notes, documents, email compose body) treat Enter
 *    as a newline unless explicitly configured for IME_ACTION_SEND without noEnterAction.
 * 4. Raw/terminal fields (TYPE_NULL) receive a simulated hardware KEYCODE_ENTER event.
 * 5. Single-line fields with explicit actions (Search, Go, Next, Done, Send) execute the action.
 * 6. Single-line fields without an explicit action fall back to default action or newline.
 */
object EnterKeyResolver {

    enum class Behavior {
        NEWLINE,
        ACTION,
        RAW_KEY
    }

    fun determineBehavior(
        info: EditorInfo?,
        isShiftActive: Boolean
    ): Behavior {
        // 1. Shift+Enter always forces a literal newline (universal behavior)
        if (isShiftActive) return Behavior.NEWLINE
        if (info == null) return Behavior.NEWLINE

        val imeOptions = info.imeOptions
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val inputType = info.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS

        // 2. Editor explicitly requested no action on Enter (e.g. multiline TextViews)
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (noEnterAction) return Behavior.NEWLINE

        // 3. Multi-line fields: Enter is a newline, unless explicitly configured to SEND (e.g. chat apps)
        val isMultiline = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 ||
                          (inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0 ||
                          (inputClass == InputType.TYPE_CLASS_TEXT &&
                           (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)

        if (isMultiline) {
            return if (action == EditorInfo.IME_ACTION_SEND) {
                Behavior.ACTION
            } else {
                Behavior.NEWLINE
            }
        }

        // 4. Raw / terminal inputs (TYPE_NULL): send hardware KEYCODE_ENTER event
        if (inputType == InputType.TYPE_NULL) {
            return Behavior.RAW_KEY
        }

        // 5. Single-line fields with a defined action (Search, Go, Next, Done, Send): perform action
        if (action != EditorInfo.IME_ACTION_UNSPECIFIED && action != EditorInfo.IME_ACTION_NONE) {
            return Behavior.ACTION
        }

        // 6. Single-line field with no action specified
        return Behavior.RAW_KEY
    }
}
