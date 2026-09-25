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
            // Row 1 (Top) - 10 keys with 1..0 number hints
            listOf(
                Ch("q", "Q", hint = "1"),
                Ch("w", "W", hint = "2"),
                Ch("e", "E", hint = "3"),
                Ch("r", "R", hint = "4"),
                Ch("t", "T", hint = "5"),
                Ch("y", "Y", hint = "6"),
                Ch("u", "U", hint = "7"),
                Ch("i", "I", hint = "8"),
                Ch("o", "O", hint = "9"),
                Ch("p", "P", hint = "0"),
            ),
            // Row 2 (Home) - EXACTLY 9 keys: a s d f g h j k l
            listOf(
                Ch("a", "A", homeRow = true),
                Ch("s", "S", homeRow = true),
                Ch("d", "D", homeRow = true),
                Ch("f", "F", homeRow = true),
                Ch("g", "G", homeRow = true),
                Ch("h", "H", homeRow = true),
                Ch("j", "J", homeRow = true),
                Ch("k", "K", homeRow = true),
                Ch("l", "L", homeRow = true),
            ),
            // Row 3 (Bottom) - Shift, EXACTLY 7 letters: z x c v b n m, Backspace
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.4f, contentDesc = "Shift",
                ),
                Ch("z", "Z"), Ch("x", "X"), Ch("c", "C"), Ch("v", "V"),
                Ch("b", "B"), Ch("n", "N"), Ch("m", "M"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.4f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                widthWeight = 1.4f, contentDesc = "Numbers and symbols",
            ),
            Key(
                label = ",", shiftedLabel = ";", hintLabel = "😊",
                action = KeyAction.Character(","),
                shiftedAction = KeyAction.Character(";"),
                longPressAction = KeyAction.SwitchEmoji,
                widthWeight = 1.0f, contentDesc = "Comma, long press for emoji",
            ),
            Key(
                label = "🌐", shiftedLabel = "🌐",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchLayout,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.2f, contentDesc = "Space",
            ),
            Key(
                label = ".", shiftedLabel = "?", hintLabel = "!",
                action = KeyAction.Character("."),
                shiftedAction = KeyAction.Character("?"),
                widthWeight = 1.0f, contentDesc = "Period",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f, contentDesc = "Enter",
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
            // Row 1 (BBS standard: q w e r t y u i o p)
            listOf(
                Ch("ঙ", "ং"), Ch("য", "য়"), Ch("ড", "ঢ"), Ch("প", "ফ"),
                Ch("ট", "ঠ"), Ch("চ", "ছ"), Ch("জ", "ঝ"), Ch("হ", "ঞ"),
                Ch("গ", "ঘ"), Ch("ড়", "ঢ়"),
            ),
            // Row 2 (Home: a s d f g h j k l)
            listOf(
                Ch("ৃ", "ৗ", homeRow = true), Ch("ু", "ূ", homeRow = true),
                Ch("ি", "ী", homeRow = true), Ch("ব", "ভ", homeRow = true),
                Ch("্", "।", homeRow = true), Ch("া", "অ", homeRow = true),
                Ch("ক", "খ", homeRow = true), Ch("ত", "থ", homeRow = true),
                Ch("দ", "ধ", homeRow = true),
            ),
            // Row 3 (Shift, z x c v b n m, Khanda Ta, Backspace)
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.4f, contentDesc = "Shift",
                ),
                Ch("ঁ", "ঃ"), Ch("ো", "ৌ"), Ch("ে", "ৈ"),
                Ch("র", "ল"), Ch("ন", "ণ"), Ch("স", "ষ"),
                Ch("ম", "শ"), Ch("ৎ", "ঋ", desc = "Khanda Ta, shifted Ri"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.4f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "😊", hintLabel = "😊",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchEmoji,
                longPressAction = KeyAction.SwitchEmoji,
                widthWeight = 1.4f, contentDesc = "Numbers",
            ),
            Key(
                label = "🌐", shiftedLabel = "📋", hintLabel = "📋",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchClipboard,
                longPressAction = KeyAction.SwitchClipboard,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Key(
                label = ",", shiftedLabel = ";",
                action = KeyAction.Character(","),
                shiftedAction = KeyAction.Character(";"),
                widthWeight = 1.0f, contentDesc = "Comma",
            ),
            Key(
                label = "স্পেস • জাতীয়", shiftedLabel = "স্পেস • জাতীয়",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.2f, contentDesc = "Space",
            ),
            Ch("।", "!", desc = "Dari, shifted exclamation"),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f, contentDesc = "Enter",
            ),
        ),
    )
}

/**
 * Probhat (प्रभात) Layout — Mobile 10-Key Refactored Standard
 * ══════════════════════════════════════════════════════════════════════════════
 * Refactored for mobile ergonomics (maximum 10 keys per row, no piano teeth).
 * Crucially includes Hasanta (্) and Chandra Bindu (ঁ) for complete conjuncts.
 */
object ProbhatLayout {

    val layout: KeyboardLayout = KeyboardLayout(
        name = "Probhat",
        rows = listOf(
            // Row 1 (10 keys: Top consonants & high-frequency long/short vowels)
            listOf(
                Ch("দ", "ধ"), Ch("ূ", "ঊ"), Ch("ী", "ঈ"), Ch("র", "ড়"),
                Ch("ট", "ঠ"), Ch("এ", "ঐ"), Ch("ু", "উ"), Ch("ি", "ই"),
                Ch("ও", "ঔ"), Ch("প", "ফ"),
            ),
            // Row 2 (Home: a s d f g h j k l — 9 keys centered)
            listOf(
                Ch("া", "অ", homeRow = true), Ch("স", "ষ", homeRow = true),
                Ch("ড", "ঢ", homeRow = true), Ch("ত", "থ", homeRow = true),
                Ch("গ", "ঘ", homeRow = true), Ch("হ", "ঃ", homeRow = true),
                Ch("জ", "ঝ", homeRow = true), Ch("ক", "খ", homeRow = true),
                Ch("ল", "ং", homeRow = true),
            ),
            // Row 3 (Shift, 8 letters + Hasanta/ChandraBindu + Backspace = 10 keys)
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.4f, contentDesc = "Shift",
                ),
                Ch("য়", "য"), Ch("শ", "ঢ়"), Ch("চ", "ছ"),
                Ch("আ", "ঋ"), Ch("ব", "ভ"), Ch("ন", "ণ"),
                Ch("ম", "ঙ"),
                Ch("্", "ঁ", desc = "Hasanta virama, shifted Chandra Bindu"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.4f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "😊", hintLabel = "😊",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchEmoji,
                longPressAction = KeyAction.SwitchEmoji,
                widthWeight = 1.4f, contentDesc = "Numbers",
            ),
            Key(
                label = "🌐", shiftedLabel = "📋", hintLabel = "📋",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchClipboard,
                longPressAction = KeyAction.SwitchClipboard,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Ch("ে", "ৈ", desc = "E-kar, shifted Oi-kar"),
            Key(
                label = "স্পেস • প্রভাত", shiftedLabel = "স্পেস • প্রভাত",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.2f, contentDesc = "Space",
            ),
            Ch("ো", "ৌ", desc = "O-kar, shifted Ou-kar"),
            Ch("।", "?", desc = "Dari, shifted question"),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f, contentDesc = "Enter",
            ),
        ),
    )
}

/**
 * Gboard Bengali (জি-বোর্ড বাংলা) Layout
 * ══════════════════════════════════════════════════════════════════════════════
 * Standard Gboard Bengali fixed mapping with complete consonant coverage.
 */
object GboardBengaliLayout {

    val layout: KeyboardLayout = KeyboardLayout(
        name = "Gboard Style",
        rows = listOf(
            // Row 1 (10 keys)
            listOf(
                Ch("ৌ", "ঔ"), Ch("ৈ", "ঐ"), Ch("া", "আ"), Ch("ী", "ঈ"),
                Ch("ূ", "ঊ"), Ch("ব", "ভ"), Ch("হ", "ঃ"), Ch("গ", "ঘ"),
                Ch("দ", "ধ"), Ch("জ", "ঝ"),
            ),
            // Row 2 (Home — 9 keys centered)
            listOf(
                Ch("ো", "ও", homeRow = true), Ch("ে", "এ", homeRow = true),
                Ch("্", "অ", homeRow = true), Ch("ি", "ই", homeRow = true),
                Ch("ু", "উ", homeRow = true), Ch("র", "ড়", homeRow = true),
                Ch("ক", "খ", homeRow = true), Ch("ত", "থ", homeRow = true),
                Ch("চ", "ছ", homeRow = true),
            ),
            // Row 3 (Shift, 8 letters + Backspace = 10 keys)
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.4f, contentDesc = "Shift",
                ),
                Ch("ট", "ঠ"), Ch("ড", "ঢ"), Ch("ণ", "ৎ"),
                Ch("প", "ফ"), Ch("ন", "ঙ"), Ch("ল", "ং"),
                Ch("স", "ষ"), Ch("ম", "শ"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.4f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "😊", hintLabel = "😊",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchEmoji,
                longPressAction = KeyAction.SwitchEmoji,
                widthWeight = 1.4f, contentDesc = "Numbers",
            ),
            Key(
                label = "🌐", shiftedLabel = "📋", hintLabel = "📋",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchClipboard,
                longPressAction = KeyAction.SwitchClipboard,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Key(
                label = ",", shiftedLabel = ";",
                action = KeyAction.Character(","),
                shiftedAction = KeyAction.Character(";"),
                widthWeight = 1.0f, contentDesc = "Comma",
            ),
            Key(
                label = "স্পেস • জিবোর্ড", shiftedLabel = "স্পেস • জিবোর্ড",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.2f, contentDesc = "Space",
            ),
            Ch("।", "?", desc = "Dari, shifted question"),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f, contentDesc = "Enter",
            ),
        ),
    )
}

