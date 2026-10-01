package com.lekhani.android.model

/**
 * KeyAction — what a key does when tapped
 * ══════════════════════════════════════════════════════════════════════════════
 * A sealed hierarchy so the keyboard view can dispatch actions without string
 * comparisons in the hot path.
 */
sealed class KeyAction {
    /** Emit a Unicode string into the composing buffer */
    data class Character(val token: String) : KeyAction()
    /** Backspace — grapheme-cluster-aware deletion */
    data object Backspace : KeyAction()
    /** Spacebar commit */
    data object Space : KeyAction()
    /** Enter / newline */
    data object Enter : KeyAction()
    /** Toggle Shift state */
    data object Shift : KeyAction()
    /** Switch to the ?123 number/symbol layer */
    data object SwitchNumeric : KeyAction()
    /** Switch to the =< more symbols layer */
    data object SwitchMoreSymbols : KeyAction()
    /** Return from number/symbol mode to the active letter layout */
    data object SwitchAlpha : KeyAction()
    /** Toggle between English digits (1..0) and Bengali digits (১..০) */
    data object ToggleBengaliDigits : KeyAction()
    /** Cycle to the next enabled layout (Globe key) */
    data object SwitchLayout : KeyAction()
    /** Trigger 100% offline on-device speech-to-text voice typing */
    data object VoiceTyping : KeyAction()
    /** Switch to Unicode 15.1+ emoji / kaomoji / symbol picker */
    data object SwitchEmoji : KeyAction()
    /** Switch to local clipboard history sheet */
    data object SwitchClipboard : KeyAction()
    /** Move cursor left in active input connection */
    data object CursorLeft : KeyAction()
    /** Move cursor right in active input connection */
    data object CursorRight : KeyAction()
    /** Insert Tab character / event */
    data object Tab : KeyAction()
}

/**
 * Key — a single keyboard key's complete specification
 * ══════════════════════════════════════════════════════════════════════════════
 * Immutable data class. All layout definitions are declared as lists of Keys.
 *
 * Performance note: Key objects are created ONCE at layout initialization time
 * and reused across frames. No Key is ever instantiated inside onDraw() or
 * onTouchEvent().
 *
 * @param label         Primary display character (unshifted)
 * @param shiftedLabel  Shifted / long-press alternate character (null if none)
 * @param action        Primary action on tap
 * @param shiftedAction Shifted action (defaults to Character(shiftedLabel))
 * @param widthWeight   Relative width weight; 1.0f = standard key width
 * @param isHomeRow     True for Row 2 — drives the subtle accent glow
 * @param contentDesc   TalkBack accessibility description (WCAG 2.1)
 */
data class Key(
    val label: String,
    val shiftedLabel: String? = null,
    val hintLabel: String? = null,
    val shiftedHintLabel: String? = null,
    val action: KeyAction,
    val shiftedAction: KeyAction = shiftedLabel
        ?.let { KeyAction.Character(it) }
        ?: action,
    val longPressAction: KeyAction? = null,
    val shiftedLongPressAction: KeyAction? = null,
    val widthWeight: Float = 1.0f,
    val isHomeRow: Boolean = false,
    val contentDesc: String = label,
) {
    /** Returns the active label for the current shift state */
    fun displayLabel(shifted: Boolean): String =
        if (shifted && shiftedLabel != null) shiftedLabel else label

    /** Returns the active hint for the current shift state */
    fun displayHint(shifted: Boolean): String? =
        if (shifted) shiftedHintLabel ?: (if (hintLabel != shiftedLabel) hintLabel else null)
        else hintLabel ?: if (shiftedLabel != null && shiftedLabel != label) shiftedLabel else null

    /** Returns the active action for the current shift state */
    fun activeAction(shifted: Boolean): KeyAction =
        if (shifted) shiftedAction else action

    /** Returns the active long-press action for the current shift state */
    fun activeLongPressAction(shifted: Boolean): KeyAction? =
        if (shifted && shiftedLongPressAction != null) shiftedLongPressAction else longPressAction
}
