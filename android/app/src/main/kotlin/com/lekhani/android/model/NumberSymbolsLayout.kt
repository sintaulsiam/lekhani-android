package com.lekhani.android.model

/**
 * Number & Symbol Layouts
 * ══════════════════════════════════════════════════════════════════════════════
 * Provides dedicated Number & Symbol layers across all typing modes:
 * - [numericLayout]: Primary digits (1..0) with Bengali digits hint, common punctuation, currency
 * - [bengaliNumericLayout]: Native Bengali numerals (১..০) with English digits hint
 * - [moreSymbolsLayout]: Extended mathematical operators, brackets, currency symbols (৳, €, ¥, £, ₹),
 *   copyright/trademark signs, and typographic glyphs.
 */
object NumberSymbolsLayout {

    /** Primary Number & Common Symbols layer (accessible via ?123 key) */
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
            // Row 2: Standard and Bengali currency / math symbols
            listOf(
                Ch("@", shifted = "~", hint = "~"),
                Ch("#", shifted = "|", hint = "|"),
                Ch("৳", shifted = "$", hint = "$"),
                Ch("%", shifted = "^", hint = "^"),
                Ch("&", shifted = "*", hint = "*"),
                Ch("-", shifted = "_", hint = "_"),
                Ch("+", shifted = "=", hint = "="),
                Ch("(", shifted = "{", hint = "{"),
                Ch(")", shifted = "}", hint = "}"),
                Ch("/", shifted = "\\", hint = "\\"),
            ),
            // Row 3: More symbols toggle, punctuations, and backspace
            listOf(
                Key(
                    label = "=\\<", shiftedLabel = "=\\<",
                    action = KeyAction.SwitchMoreSymbols, shiftedAction = KeyAction.SwitchMoreSymbols,
                    widthWeight = 1.32f, contentDesc = "More symbols",
                ),
                Ch("*"), Ch("\""), Ch("'"), Ch(":"), Ch(";"), Ch("!"), Ch("?"),
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
                widthWeight = 3.2f, contentDesc = "Space",
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

    /** Native Bengali Numerals (১..০) layout */
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
            listOf(
                Ch("@", shifted = "~", hint = "~"),
                Ch("#", shifted = "|", hint = "|"),
                Ch("৳", shifted = "$", hint = "$"),
                Ch("%", shifted = "^", hint = "^"),
                Ch("&", shifted = "*", hint = "*"),
                Ch("-", shifted = "_", hint = "_"),
                Ch("+", shifted = "=", hint = "="),
                Ch("(", shifted = "{", hint = "{"),
                Ch(")", shifted = "}", hint = "}"),
                Ch("/", shifted = "\\", hint = "\\"),
            ),
            listOf(
                Key(
                    label = "=\\<", shiftedLabel = "=\\<",
                    action = KeyAction.SwitchMoreSymbols, shiftedAction = KeyAction.SwitchMoreSymbols,
                    widthWeight = 1.32f, contentDesc = "More symbols",
                ),
                Ch("*"), Ch("\""), Ch("'"), Ch(":"), Ch(";"), Ch("!"), Ch("?"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "বাংলা", shiftedLabel = "বাংলা",
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
                widthWeight = 3.2f, contentDesc = "Space",
            ),
            Key(
                label = "।", shiftedLabel = ".", hintLabel = ".",
                action = KeyAction.Character("।"), shiftedAction = KeyAction.Character("."),
                longPressAction = KeyAction.Character("."),
                widthWeight = 1.0f, contentDesc = "Dari",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f, contentDesc = "Enter",
            ),
        ),
    )

    /** Secondary / Extended Symbols layer (accessible via =\\< key) */
    val moreSymbolsLayout: KeyboardLayout = KeyboardLayout(
        name = "More Symbols",
        rows = listOf(
            listOf(
                Ch("~"), Ch("`"), Ch("|"), Ch("^"), Ch("\\"),
                Ch("{"), Ch("}"), Ch("["), Ch("]"), Ch("°"),
            ),
            listOf(
                Ch("_"), Ch("="), Ch("৳"), Ch("€"), Ch("¥"),
                Ch("£"), Ch("₹"), Ch("©"), Ch("®"), Ch("™"),
            ),
            listOf(
                Key(
                    label = "?123", shiftedLabel = "?123",
                    action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                    widthWeight = 1.32f, contentDesc = "Numbers and symbols",
                ),
                Ch("<"), Ch(">"), Ch("•"), Ch("✓"), Ch("§"), Ch("÷"), Ch("×"),
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
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                widthWeight = 1.0f, contentDesc = "Numbers",
            ),
            Key(
                label = "!", shiftedLabel = "!",
                action = KeyAction.Character("!"), shiftedAction = KeyAction.Character("!"),
                widthWeight = 1.0f, contentDesc = "Exclamation",
            ),
            Key(
                label = "Space", shiftedLabel = "Space",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 3.2f, contentDesc = "Space",
            ),
            Key(
                label = "?", shiftedLabel = "?",
                action = KeyAction.Character("?"), shiftedAction = KeyAction.Character("?"),
                widthWeight = 1.0f, contentDesc = "Question",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f, contentDesc = "Enter",
            ),
        ),
    )
}
