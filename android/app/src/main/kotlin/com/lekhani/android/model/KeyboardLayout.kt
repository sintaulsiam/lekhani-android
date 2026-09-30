package com.lekhani.android.model

/**
 * KeyboardLayout — immutable layout specification
 * ══════════════════════════════════════════════════════════════════════════════
 * A layout is a list of rows; each row is a list of [Key]s. The canvas view
 * iterates this structure once on size change to compute pixel bounding boxes,
 * then never touches it again in the draw loop.
 */
data class KeyboardLayout(
    val name: String,
    /** Row 0 = top row, Row N-1 = bottom-most key row (excluding spacebar) */
    val rows: List<List<Key>>,
    /** The spacebar row — always rendered last, may differ per layout */
    val spacebarRow: List<Key>,
)

/**
 * Convenience constructor for [Key] entries in layout tables.
 * Reduces boilerplate in the layout definition files.
 */
@Suppress("FunctionName")
fun Ch(
    label: String,
    shifted: String? = null,
    hint: String? = null,
    shiftedHint: String? = null,
    homeRow: Boolean = false,
    desc: String = label,
): Key = Key(
    label = label,
    shiftedLabel = shifted,
    hintLabel = hint,
    shiftedHintLabel = shiftedHint,
    action = KeyAction.Character(label),
    shiftedAction = shifted?.let { KeyAction.Character(it) } ?: KeyAction.Character(label),
    longPressAction = hint?.let { KeyAction.Character(it) },
    shiftedLongPressAction = shiftedHint?.let { KeyAction.Character(it) },
    isHomeRow = homeRow,
    contentDesc = desc,
)
