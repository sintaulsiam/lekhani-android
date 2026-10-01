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
                widthWeight = 1.4f, contentDesc = "Alphabet",
            ),
            Key(
                label = "১২৩", shiftedLabel = "১২৩",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "Bengali digits",
            ),
            Key(
                label = ",", shiftedLabel = ",",
                action = KeyAction.Character(","), shiftedAction = KeyAction.Character(","),
                widthWeight = 1.0f, contentDesc = "Comma",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
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
                widthWeight = 1.4f, contentDesc = "Alphabet",
            ),
            Key(
                label = ",", shiftedLabel = ",",
                action = KeyAction.Character(","), shiftedAction = KeyAction.Character(","),
                widthWeight = 1.0f, contentDesc = "Comma",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
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
                widthWeight = 1.4f, contentDesc = "Alphabet",
            ),
            Key(
                label = "123", shiftedLabel = "123",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "English digits",
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
                label = ".", shiftedLabel = "।", hintLabel = "।",
                action = KeyAction.Character("."), shiftedAction = KeyAction.Character("।"),
                longPressAction = KeyAction.Character("।"),
                widthWeight = 1.0f, contentDesc = "Period",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f, contentDesc = "Enter",
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

    /** Dedicated 3×4 Phone Dialpad layout for TYPE_CLASS_PHONE */
    val phoneDialpadLayout: KeyboardLayout = KeyboardLayout(
        name = "Phone Dialpad",
        rows = listOf(
            // Row 1: 1, 2, 3
            listOf(
                Ch("1", hint = "১", desc = "1"),
                Ch("2", hint = "ABC", desc = "2 ABC"),
                Ch("3", hint = "DEF", desc = "3 DEF"),
            ),
            // Row 2: 4, 5, 6
            listOf(
                Ch("4", hint = "GHI", desc = "4 GHI"),
                Ch("5", hint = "JKL", desc = "5 JKL"),
                Ch("6", hint = "MNO", desc = "6 MNO"),
            ),
            // Row 3: 7, 8, 9
            listOf(
                Ch("7", hint = "PQRS", desc = "7 PQRS"),
                Ch("8", hint = "TUV", desc = "8 TUV"),
                Ch("9", hint = "WXYZ", desc = "9 WXYZ"),
            ),
            // Row 4: *, 0/+, #
            listOf(
                Key(
                    label = "*", shiftedLabel = "*",
                    action = KeyAction.Character("*"), shiftedAction = KeyAction.Character("*"),
                    widthWeight = 1.0f, contentDesc = "Star",
                ),
                Key(
                    label = "0", shiftedLabel = "+", hintLabel = "+",
                    action = KeyAction.Character("0"), shiftedAction = KeyAction.Character("+"),
                    longPressAction = KeyAction.Character("+"),
                    widthWeight = 1.0f, contentDesc = "0, long press plus",
                ),
                Key(
                    label = "#", shiftedLabel = "#",
                    action = KeyAction.Character("#"), shiftedAction = KeyAction.Character("#"),
                    widthWeight = 1.0f, contentDesc = "Pound hash",
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
                label = "+", shiftedLabel = "+",
                action = KeyAction.Character("+"), shiftedAction = KeyAction.Character("+"),
                widthWeight = 0.8f, contentDesc = "Plus",
            ),
            Key(
                label = ",", shiftedLabel = ";", hintLabel = ";",
                action = KeyAction.Character(","), shiftedAction = KeyAction.Character(";"),
                longPressAction = KeyAction.Character(";"),
                widthWeight = 0.8f, contentDesc = "Pause / Wait",
            ),
            Key(
                label = "⌫", shiftedLabel = "⌫",
                action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                widthWeight = 1.0f, contentDesc = "Backspace",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.2f, contentDesc = "Call or Next",
            ),
        ),
    )

    /** Dedicated 3×4 Number/PIN Pad layout for TYPE_CLASS_NUMBER and TYPE_CLASS_DATETIME */
    val numpadPinLayout: KeyboardLayout = KeyboardLayout(
        name = "Number PIN Pad",
        rows = listOf(
            // Row 1: 1, 2, 3
            listOf(
                Ch("1", hint = "১", desc = "1"),
                Ch("2", hint = "২", desc = "2"),
                Ch("3", hint = "৩", desc = "3"),
            ),
            // Row 2: 4, 5, 6
            listOf(
                Ch("4", hint = "৪", desc = "4"),
                Ch("5", hint = "৫", desc = "5"),
                Ch("6", hint = "৬", desc = "6"),
            ),
            // Row 3: 7, 8, 9
            listOf(
                Ch("7", hint = "৭", desc = "7"),
                Ch("8", hint = "৮", desc = "8"),
                Ch("9", hint = "৯", desc = "9"),
            ),
            // Row 4: ., 0, ⌫
            listOf(
                Key(
                    label = ".", shiftedLabel = ",", hintLabel = ",",
                    action = KeyAction.Character("."), shiftedAction = KeyAction.Character(","),
                    longPressAction = KeyAction.Character(","),
                    widthWeight = 1.0f, contentDesc = "Decimal point, long press comma",
                ),
                Ch("0", hint = "০", desc = "0"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.0f, contentDesc = "Backspace",
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
                label = "১২৩", shiftedLabel = "১২৩",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "Switch to Bengali digits",
            ),
            Key(
                label = "-", shiftedLabel = "/", hintLabel = "/",
                action = KeyAction.Character("-"), shiftedAction = KeyAction.Character("/"),
                longPressAction = KeyAction.Character("/"),
                widthWeight = 1.0f, contentDesc = "Minus, slash",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.2f, contentDesc = "Done or Enter",
            ),
        ),
    )

    /** Native Bengali numerals 3×4 Number/PIN Pad layout */
    val bengaliNumpadPinLayout: KeyboardLayout = KeyboardLayout(
        name = "Bengali Number PIN Pad",
        rows = listOf(
            // Row 1: ১, ২, ৩
            listOf(
                Ch("১", hint = "1", desc = "১"),
                Ch("২", hint = "2", desc = "২"),
                Ch("৩", hint = "3", desc = "৩"),
            ),
            // Row 2: ৪, ৫, ৬
            listOf(
                Ch("৪", hint = "4", desc = "৪"),
                Ch("৫", hint = "5", desc = "৫"),
                Ch("৬", hint = "6", desc = "৬"),
            ),
            // Row 3: ৭, ৮, ৯
            listOf(
                Ch("৭", hint = "7", desc = "৭"),
                Ch("৮", hint = "8", desc = "৮"),
                Ch("৯", hint = "9", desc = "৯"),
            ),
            // Row 4: ., ০, ⌫
            listOf(
                Key(
                    label = ".", shiftedLabel = "।", hintLabel = "।",
                    action = KeyAction.Character("."), shiftedAction = KeyAction.Character("।"),
                    longPressAction = KeyAction.Character("।"),
                    widthWeight = 1.0f, contentDesc = "Decimal point, long press Dari",
                ),
                Ch("০", hint = "0", desc = "০"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.0f, contentDesc = "Backspace",
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
                label = "123", shiftedLabel = "123",
                action = KeyAction.ToggleBengaliDigits, shiftedAction = KeyAction.ToggleBengaliDigits,
                widthWeight = 1.0f, contentDesc = "Switch to English digits",
            ),
            Key(
                label = "৳", shiftedLabel = "-", hintLabel = "-",
                action = KeyAction.Character("৳"), shiftedAction = KeyAction.Character("-"),
                longPressAction = KeyAction.Character("-"),
                widthWeight = 1.0f, contentDesc = "Taka symbol, minus",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.2f, contentDesc = "Done or Enter",
            ),
        ),
    )
}
