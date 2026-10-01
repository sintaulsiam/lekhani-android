package com.lekhani.android.model

/**
 * Number & Symbol Layouts
 * ══════════════════════════════════════════════════════════════════════════════
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
}
