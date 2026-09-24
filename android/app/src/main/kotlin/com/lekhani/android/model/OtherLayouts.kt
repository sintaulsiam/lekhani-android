package com.lekhani.android.model

/**
 * English QWERTY Layout
 * ══════════════════════════════════════════════════════════════════════════════
 * Standard QWERTY — used as:
 *  - The bilingual English typing layer
 *  - Auto-fallback for password / PIN fields (AGENTS.md §3.2)
 */
object EnglishQwertyLayout {

    val layout: KeyboardLayout = KeyboardLayout(
        name = "English",
        rows = listOf(
            // Row 1
            listOf(
                Ch("q", "Q"), Ch("w", "W"), Ch("e", "E"), Ch("r", "R"), Ch("t", "T"),
                Ch("y", "Y"), Ch("u", "U"), Ch("i", "I"), Ch("o", "O"), Ch("p", "P"),
            ),
            // Row 2 (Home)
            listOf(
                Ch("a", "A", homeRow = true), Ch("s", "S", homeRow = true),
                Ch("d", "D", homeRow = true), Ch("f", "F", homeRow = true),
                Ch("g", "G", homeRow = true), Ch("h", "H", homeRow = true),
                Ch("j", "J", homeRow = true), Ch("k", "K", homeRow = true),
                Ch("l", "L", homeRow = true),
                Ch("'", "\"", homeRow = true, desc = "Apostrophe, shifted quote"),
            ),
            // Row 3
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.5f, contentDesc = "Shift",
                ),
                Ch("z", "Z"), Ch("x", "X"), Ch("c", "C"), Ch("v", "V"),
                Ch("b", "B"), Ch("n", "N"), Ch("m", "M"),
                Ch(",", "!", desc = "Comma, shifted exclamation"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.5f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                widthWeight = 1.5f, contentDesc = "Numbers and symbols",
            ),
            Key(
                label = "🌐", shiftedLabel = "🌐",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchLayout,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Key(
                label = ".", shiftedLabel = "?",
                action = KeyAction.Character("."),
                shiftedAction = KeyAction.Character("?"),
                widthWeight = 1.0f, contentDesc = "Period, shifted question mark",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.5f, contentDesc = "Space",
            ),
            Ch("@", "#", desc = "At sign, shifted hash"),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 2.0f, contentDesc = "Enter",
            ),
        ),
    )
}

/**
 * Avro Phonetic Layout
 * ══════════════════════════════════════════════════════════════════════════════
 * Classic muscle-memory transliteration: typing phonetic English produces
 * Bengali output via the lekhani-parser crate (e.g. "ami" → "আমি").
 * The visual keys are standard QWERTY; the Rust engine handles transliteration.
 */
object AvroPhoneticLayout {
    /**
     * Avro uses the same QWERTY key grid — the transliteration is done in the
     * Rust lekhani-parser crate, not by key remapping. We share the English
     * layout data and override the session layout type.
     */
    val layout: KeyboardLayout = EnglishQwertyLayout.layout.copy(
        name = "Avro Phonetic",
    )
}

/**
 * Fixed National (জাতীয়) Layout — Official Bangladesh BBS Standard
 * ══════════════════════════════════════════════════════════════════════════════
 * Standard government-mandated fixed layout for official Bengali typing.
 * Commonly used in government offices and educational institutions.
 */
object NationalLayout {

    val layout: KeyboardLayout = KeyboardLayout(
        name = "জাতীয় (National)",
        rows = listOf(
            // Row 1
            listOf(
                Ch("ৌ", "ৌ"), Ch("ৈ", "ৈ"), Ch("ূ", "ূ"), Ch("ী", "ী"),
                Ch("ু", "ু"), Ch("ি", "ি"), Ch("া", "া"), Ch("ে", "ে"),
                Ch("ো", "ো"), Ch("ঃ", "ঃ"),
            ),
            // Row 2 (Home)
            listOf(
                Ch("ট", "ঠ", homeRow = true), Ch("থ", "থ", homeRow = true),
                Ch("গ", "ঘ", homeRow = true), Ch("হ", "হ", homeRow = true),
                Ch("য", "য়", homeRow = true), Ch("র", "ড়", homeRow = true),
                Ch("ল", "ল", homeRow = true), Ch("ক", "খ", homeRow = true),
                Ch("প", "ফ", homeRow = true), Ch("জ", "ঝ", homeRow = true),
            ),
            // Row 3
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.5f, contentDesc = "Shift",
                ),
                Ch("ন", "ণ"), Ch("ম", "ঙ"), Ch("ব", "ভ"),
                Ch("স", "ষ"), Ch("দ", "ধ"), Ch("ত", "ৎ"),
                Ch("চ", "ছ"), Ch("ং", "ঞ"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.5f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                widthWeight = 1.5f, contentDesc = "Numbers",
            ),
            Key(
                label = "🌐", shiftedLabel = "🌐",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchLayout,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Key(
                label = "অ", shiftedLabel = "আ",
                action = KeyAction.Character("অ"),
                shiftedAction = KeyAction.Character("আ"),
                widthWeight = 1.0f, contentDesc = "Vowel অ",
            ),
            Key(
                label = "স্পেস", shiftedLabel = "স্পেস",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.5f, contentDesc = "Space",
            ),
            Ch("্", "্", desc = "Hasanta"),
            Ch("।", "!", desc = "Dari, shifted exclamation"),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.5f, contentDesc = "Enter",
            ),
        ),
    )
}
