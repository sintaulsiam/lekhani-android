package com.lekhani.android.model

/**
 * Number & Symbol Layouts
 * Provides dedicated Number & Symbol layers across all typing modes:
 * - [numericLayout]: Primary digits (1..0) with Bengali digits hint, common punctuation, currency
 * - [bengaliNumericLayout]: Native Bengali numerals (১..০) with English digits hint (State 2)
 * - [moreSymbolsLayout]: Extended mathematical operators, brackets, currency symbols (৳, €, ¥, £, ₹),
 *   copyright/trademark signs, tab, cursor nav, and typographic glyphs (State 3).
 */
object NumberSymbolsLayout {

    /** Primary Number & Common Symbols layer (accessible via ?123 key in English mode) */
    val numericLayout: KeyboardLayout = KeyboardLayout(
        name = "Numbers & Symbols",
        rows = listOf(
            // Row 1: English digits with Bengali digit hints
            listOf(
                Ch("1", shifted = "১", hint = "১"),
                Ch("2", shifted = "২", hint = "২"),
                Ch("3", shifted = "৩", hint = "৩"),
                Ch("4", shifted = "৪", hint = "৪"),
                Ch("5", shifted = "৫", hint = "৫"),
                Ch("6", shifted = "৬", hint = "৬"),
                Ch("7", shifted = "৭", hint = "৭"),
                Ch("8", shifted = "৮", hint = "৮"),
                Ch("9", shifted = "৯", hint = "৯"),
                Ch("0", shifted = "০", hint = "০"),
            ),
            // Row 2: Standard currency / math symbols
            listOf(
                Ch("@"), Ch("#"), Ch("$"), Ch("%"), Ch("&"),
                Ch("*"), Ch("-"), Ch("+"), Ch("("), Ch(")"),
            ),
            // Row 3: More symbols toggle, punctuations, and backspace
            listOf(
                Key(
                    label = "=\\<", shiftedLabel = "=\\<",
                    action = KeyAction.SwitchMoreSymbols, shiftedAction = KeyAction.SwitchMoreSymbols,
                    widthWeight = 1.32f, contentDesc = "More symbols",
                ),
                Ch("!"), Ch("\""), Ch("'"), Ch(":"), Ch(";"), Ch("/"), Ch("?"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "ABC", shiftedLabel = "ABC",
                action = KeyAction.SwitchAlpha, shiftedAction = KeyAction.SwitchAlpha,
                widthWeight = 1.3f, contentDesc = "Alphabet",
            ),
            Key(
                label = "🔢", shiftedLabel = "🔢",
                action = KeyAction.SwitchNumpad, shiftedAction = KeyAction.SwitchNumpad,
                widthWeight = 1.0f, contentDesc = "Number PIN pad",
            ),
            Key(
                label = "১২৩", shiftedLabel = "১২৩",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "Bengali digits",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 3.4f, contentDesc = "Space",
            ),
            Key(
                label = ".", shiftedLabel = ".",
                action = KeyAction.Character("."), shiftedAction = KeyAction.Character("."),
                widthWeight = 1.0f, contentDesc = "Period",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.3f, contentDesc = "Enter",
            ),
        ),
    )

    /** Pure English Numeric & Symbols layer (dedicated for English QWERTY mode) */
    val englishNumericLayout: KeyboardLayout = KeyboardLayout(
        name = "English Numbers & Symbols",
        rows = listOf(
            // Row 1: Clean English digits (no Bengali hints)
            listOf(
                Ch("1"), Ch("2"), Ch("3"), Ch("4"), Ch("5"),
                Ch("6"), Ch("7"), Ch("8"), Ch("9"), Ch("0"),
            ),
            // Row 2: Standard currency & math symbols ($ instead of ৳)
            listOf(
                Ch("@"), Ch("#"), Ch("$"), Ch("%"), Ch("&"),
                Ch("*"), Ch("-"), Ch("+"), Ch("("), Ch(")"),
            ),
            // Row 3: More symbols toggle, punctuations, and backspace
            listOf(
                Key(
                    label = "=\\<", shiftedLabel = "=\\<",
                    action = KeyAction.SwitchMoreSymbols, shiftedAction = KeyAction.SwitchMoreSymbols,
                    widthWeight = 1.32f, contentDesc = "More symbols",
                ),
                Ch("!"), Ch("\""), Ch("'"), Ch(":"), Ch(";"), Ch("/"), Ch("?"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "ABC", shiftedLabel = "ABC",
                action = KeyAction.SwitchAlpha, shiftedAction = KeyAction.SwitchAlpha,
                widthWeight = 1.3f, contentDesc = "Alphabet",
            ),
            Key(
                label = "🔢", shiftedLabel = "🔢",
                action = KeyAction.SwitchNumpad, shiftedAction = KeyAction.SwitchNumpad,
                widthWeight = 1.0f, contentDesc = "Number PIN pad",
            ),
            Key(
                label = ",", shiftedLabel = ",",
                action = KeyAction.Character(","), shiftedAction = KeyAction.Character(","),
                widthWeight = 1.0f, contentDesc = "Comma",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 3.4f, contentDesc = "Space",
            ),
            Key(
                label = ".", shiftedLabel = ".",
                action = KeyAction.Character("."), shiftedAction = KeyAction.Character("."),
                widthWeight = 1.0f, contentDesc = "Period",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.3f, contentDesc = "Enter",
            ),
        ),
    )

    /** Native Bengali Numerals (১..০) layout — State 2 */
    val bengaliNumericLayout: KeyboardLayout = KeyboardLayout(
        name = "Bengali Numbers",
        rows = listOf(
            // Row 1: Bengali digits with English digit hints
            listOf(
                Ch("১", shifted = "1", hint = "1"),
                Ch("২", shifted = "2", hint = "2"),
                Ch("৩", shifted = "3", hint = "3"),
                Ch("৪", shifted = "4", hint = "4"),
                Ch("৫", shifted = "5", hint = "5"),
                Ch("৬", shifted = "6", hint = "6"),
                Ch("৭", shifted = "7", hint = "7"),
                Ch("৮", shifted = "8", hint = "8"),
                Ch("৯", shifted = "9", hint = "9"),
                Ch("০", shifted = "0", hint = "0"),
            ),
            // Row 2: Standard and Bengali currency / math symbols
            listOf(
                Ch("@"), Ch("#"), Ch("৳"), Ch("%"), Ch("&"),
                Ch("*"), Ch("-"), Ch("+"), Ch("("), Ch(")"),
            ),
            // Row 3: More symbols toggle, punctuations, and backspace
            listOf(
                Key(
                    label = "=\\<", shiftedLabel = "=\\<",
                    action = KeyAction.SwitchMoreSymbols, shiftedAction = KeyAction.SwitchMoreSymbols,
                    widthWeight = 1.32f, contentDesc = "More symbols",
                ),
                Ch("!"), Ch("\""), Ch("'"), Ch(":"), Ch(";"), Ch("/"), Ch("?"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "ABC", shiftedLabel = "ABC",
                action = KeyAction.SwitchAlpha, shiftedAction = KeyAction.SwitchAlpha,
                widthWeight = 1.3f, contentDesc = "Alphabet",
            ),
            Key(
                label = "🔢", shiftedLabel = "🔢",
                action = KeyAction.SwitchNumpad, shiftedAction = KeyAction.SwitchNumpad,
                widthWeight = 1.0f, contentDesc = "Number PIN pad",
            ),
            Key(
                label = "123", shiftedLabel = "123",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "English digits",
            ),
            Key(
                label = "স্পেস", shiftedLabel = "স্পেস",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 3.4f, contentDesc = "Space",
            ),
            Key(
                label = ".", shiftedLabel = "।", hintLabel = "।",
                action = KeyAction.Character("."), shiftedAction = KeyAction.Character("।"),
                longPressAction = KeyAction.Character("।"),
                widthWeight = 1.0f, contentDesc = "Period",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.3f, contentDesc = "Enter",
            ),
        ),
    )

    /** Secondary / Extended Symbols layer (accessible via =\\< key) — State 3 */
    val moreSymbolsLayout: KeyboardLayout = KeyboardLayout(
        name = "More Symbols",
        rows = listOf(
            // Row 1: Tilde, grave, pipe, Rupee mark, square root, pi, div, mult, curly brackets
            listOf(
                Ch("~"), Ch("`"), Ch("|"), Ch("৲"), Ch("√"),
                Ch("π"), Ch("÷"), Ch("×"), Ch("{"), Ch("}"),
            ),
            // Row 2: Tab, Pound, Dollar, Euro, caret, degree, underscore, equals, square brackets
            listOf(
                Key(
                    label = "⇥", shiftedLabel = "⇥",
                    action = KeyAction.Tab, shiftedAction = KeyAction.Tab,
                    widthWeight = 1.0f, contentDesc = "Tab",
                ),
                Ch("£"), Ch("$"), Ch("€"), Ch("^"),
                Ch("°"), Ch("_"), Ch("="), Ch("["), Ch("]"),
            ),
            // Row 3: Back to numeric, cursor left, registered, copyright, trademark, backslash, angle brackets, backspace
            listOf(
                Key(
                    label = "?123", shiftedLabel = "?123",
                    action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                    widthWeight = 1.32f, contentDesc = "Numbers and symbols",
                ),
                Key(
                    label = "◀", shiftedLabel = "◀",
                    action = KeyAction.CursorLeft, shiftedAction = KeyAction.CursorLeft,
                    widthWeight = 1.0f, contentDesc = "Cursor left",
                ),
                Key(
                    label = "▶", shiftedLabel = "▶",
                    action = KeyAction.CursorRight, shiftedAction = KeyAction.CursorRight,
                    widthWeight = 1.0f, contentDesc = "Cursor right",
                ),
                Ch("®"), Ch("©"), Ch("™"),
                Ch("\\"), Ch("<"), Ch(">"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "ABC", shiftedLabel = "ABC",
                action = KeyAction.SwitchAlpha, shiftedAction = KeyAction.SwitchAlpha,
                widthWeight = 1.4f, contentDesc = "Alphabet",
            ),
            Key(
                label = ",", shiftedLabel = ",",
                action = KeyAction.Character(","), shiftedAction = KeyAction.Character(","),
                widthWeight = 1.0f, contentDesc = "Comma",
            ),
            Key(
                label = "স্পেস", shiftedLabel = "স্পেস",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.2f, contentDesc = "Space",
            ),
            Key(
                label = ".", shiftedLabel = ".",
                action = KeyAction.Character("."), shiftedAction = KeyAction.Character("."),
                widthWeight = 1.0f, contentDesc = "Period",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f, contentDesc = "Enter",
            ),
        ),
    )

    /** Dedicated clean 4-column Phone Dialpad layout for TYPE_CLASS_PHONE */
    val phoneDialpadLayout: KeyboardLayout = KeyboardLayout(
        name = "Phone Dialpad",
        rows = listOf(
            // Row 1: 1, 2, 3, ⌫
            listOf(
                Ch("1", desc = "1"),
                Ch("2", desc = "2"),
                Ch("3", desc = "3"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.0f, contentDesc = "Backspace",
                ),
            ),
            // Row 2: 4, 5, 6, -
            listOf(
                Ch("4", desc = "4"),
                Ch("5", desc = "5"),
                Ch("6", desc = "6"),
                Ch("-", desc = "Minus"),
            ),
            // Row 3: 7, 8, 9, +
            listOf(
                Ch("7", desc = "7"),
                Ch("8", desc = "8"),
                Ch("9", desc = "9"),
                Ch("+", desc = "Plus"),
            ),
            // Row 4: *, 0, #, ↵
            listOf(
                Ch("*", desc = "Star"),
                Ch("0", desc = "0"),
                Ch("#", desc = "Pound"),
                Key(
                    label = "↵", shiftedLabel = "↵",
                    action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                    widthWeight = 1.0f, contentDesc = "Call or Action",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "ABC", shiftedLabel = "ABC",
                action = KeyAction.SwitchAlpha, shiftedAction = KeyAction.SwitchAlpha,
                widthWeight = 1.0f, contentDesc = "Switch to text keyboard",
            ),
            Key(
                label = ",", shiftedLabel = ",",
                action = KeyAction.Character(","), shiftedAction = KeyAction.Character(","),
                widthWeight = 0.8f, contentDesc = "Pause",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 1.4f, contentDesc = "Space",
            ),
            Key(
                label = ";", shiftedLabel = ";",
                action = KeyAction.Character(";"), shiftedAction = KeyAction.Character(";"),
                widthWeight = 0.8f, contentDesc = "Wait",
            ),
            Key(
                label = "১২৩", shiftedLabel = "১২৩",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "Switch to Bengali digits",
            ),
        ),
    )

    /** Dedicated clean 4-column Number/PIN Pad layout */
    val numpadPinLayout: KeyboardLayout = KeyboardLayout(
        name = "Number PIN Pad",
        rows = listOf(
            // Row 1: 1, 2, 3, ⌫
            listOf(
                Ch("1", desc = "1"),
                Ch("2", desc = "2"),
                Ch("3", desc = "3"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.0f, contentDesc = "Backspace",
                ),
            ),
            // Row 2: 4, 5, 6, -
            listOf(
                Ch("4", desc = "4"),
                Ch("5", desc = "5"),
                Ch("6", desc = "6"),
                Ch("-", desc = "Minus"),
            ),
            // Row 3: 7, 8, 9, +
            listOf(
                Ch("7", desc = "7"),
                Ch("8", desc = "8"),
                Ch("9", desc = "9"),
                Ch("+", desc = "Plus"),
            ),
            // Row 4: ., 0, ,, ↵
            listOf(
                Ch(".", desc = "Period"),
                Ch("0", desc = "0"),
                Ch(",", desc = "Comma"),
                Key(
                    label = "↵", shiftedLabel = "↵",
                    action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                    widthWeight = 1.0f, contentDesc = "Enter",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "ABC", shiftedLabel = "ABC",
                action = KeyAction.SwitchAlpha, shiftedAction = KeyAction.SwitchAlpha,
                widthWeight = 1.0f, contentDesc = "Switch to text keyboard",
            ),
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                widthWeight = 0.8f, contentDesc = "Switch to symbols",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 1.4f, contentDesc = "Space",
            ),
            Key(
                label = ")", shiftedLabel = ")",
                action = KeyAction.Character(")"), shiftedAction = KeyAction.Character(")"),
                widthWeight = 0.8f, contentDesc = "Close parenthesis",
            ),
            Key(
                label = "১২৩", shiftedLabel = "১২৩",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "Switch to Bengali digits",
            ),
        ),
    )

    /** Native Bengali numerals clean 4-column Number/PIN Pad layout */
    val bengaliNumpadPinLayout: KeyboardLayout = KeyboardLayout(
        name = "Bengali Number PIN Pad",
        rows = listOf(
            // Row 1: ১, ২, ৩, ⌫
            listOf(
                Ch("১", desc = "১"),
                Ch("২", desc = "২"),
                Ch("৩", desc = "৩"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.0f, contentDesc = "Backspace",
                ),
            ),
            // Row 2: ৪, ৫, ৬, -
            listOf(
                Ch("৪", desc = "৪"),
                Ch("৫", desc = "৫"),
                Ch("৬", desc = "৬"),
                Ch("-", desc = "Minus"),
            ),
            // Row 3: ৭, ৮, ৯, +
            listOf(
                Ch("৭", desc = "৭"),
                Ch("৮", desc = "৮"),
                Ch("৯", desc = "৯"),
                Ch("+", desc = "Plus"),
            ),
            // Row 4: ., ০, ।, ↵
            listOf(
                Ch(".", desc = "Period"),
                Ch("০", desc = "০"),
                Ch("।", desc = "Dari"),
                Key(
                    label = "↵", shiftedLabel = "↵",
                    action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                    widthWeight = 1.0f, contentDesc = "Enter",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "ABC", shiftedLabel = "ABC",
                action = KeyAction.SwitchAlpha, shiftedAction = KeyAction.SwitchAlpha,
                widthWeight = 1.0f, contentDesc = "Switch to text keyboard",
            ),
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                widthWeight = 0.8f, contentDesc = "Switch to symbols",
            ),
            Key(
                label = "স্পেস", shiftedLabel = "স্পেস",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 1.4f, contentDesc = "Space",
            ),
            Key(
                label = ",", shiftedLabel = ",",
                action = KeyAction.Character(","), shiftedAction = KeyAction.Character(","),
                widthWeight = 0.8f, contentDesc = "Comma",
            ),
            Key(
                label = "123", shiftedLabel = "123",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "Switch to English digits",
            ),
        ),
    )
}
