package com.lekhani.android.model

/**
 * Lekhani প্রবাহ (Flow) Layout Definition
 * Based on the ergonomic specification in LAYOUT_PROBAHO.md.
 *
 * Design principles:
 *  - Left thumb: vowels & kars (স্বরবর্ণ)
 *  - Right thumb: consonants (ব্যঞ্জনবর্ণ)
 *  - Home row (Row 2): অ া ি ু ে | র ত ন স ক  (68.7% of daily keystrokes)
 *  - Independent vowels sit on the Shift layer of their corresponding Kars (ই on ি, উ on ু, এ on ে, ও on ো, আ on া, ঐ on ৈ)
 *  - Long vowels & Kars sit paired with their independent partners on Shift (ঈ on ী, ঊ on ূ, ঔ on ঁ, ঐ on ৈ)
 *  - য and য় paired on Shift (য -> য়), Aspirated & Sibilant pairs on Shift (ষ on স, শ on ল, খ on ক, ঘ on গ, থ on ত, ধ on দ, ফ on প, ভ on ব)
 *  - Smart Kar auto-promotion at word-start (handled in Rust session engine)
 *  - Hasanta (্) beside Spacebar for one-thumb conjunct access with Khanda Ta (ৎ) on Shift
 */
object ProbahLayout {

    val layout: KeyboardLayout = KeyboardLayout(
        name = "Lekhani প্রবাহ",
        rows = listOf(
            // ── Row 1 (Top) — Vowels & Labials/Dentals ──────────
            listOf(
                Ch("ৈ", shifted = "ঐ", desc = "ৈ, shifted ঐ"),
                Ch("ো", shifted = "ও", desc = "ো, shifted ও"),
                Ch("ী", shifted = "ঈ", desc = "ী, shifted ঈ"),
                Ch("ূ", shifted = "ঊ", desc = "ূ, shifted ঊ"),
                Ch("য", shifted = "য়", desc = "য, shifted য়"),
                Ch("প", shifted = "ফ", desc = "প, shifted ফ"),
                Ch("ব", shifted = "ভ", desc = "ব, shifted ভ"),
                Ch("গ", shifted = "ঘ", desc = "গ, shifted ঘ"),
                Ch("দ", shifted = "ধ", desc = "দ, shifted ধ"),
                Ch("ল", shifted = "শ", desc = "ল, shifted শ"),
            ),
            // ── Row 2 (Home) — Base vowels | Golden 5 consonants ─────────────
            listOf(
                Ch("অ", shifted = "ঋ", homeRow = true, desc = "অ, shifted ঋ"),
                Ch("া", shifted = "আ", homeRow = true, desc = "া, shifted আ"),
                Ch("ি", shifted = "ই", homeRow = true, desc = "ি, shifted ই"),
                Ch("ু", shifted = "উ", homeRow = true, desc = "ু, shifted উ"),
                Ch("ে", shifted = "এ", homeRow = true, desc = "ে, shifted এ"),
                Ch("র", shifted = "ড়", homeRow = true, desc = "র, shifted ড়"),
                Ch("ত", shifted = "থ", homeRow = true, desc = "ত, shifted থ"),
                Ch("ন", shifted = "ণ", homeRow = true, desc = "ন, shifted ণ"),
                Ch("স", shifted = "ষ", homeRow = true, desc = "স, shifted ষ"),
                Ch("ক", shifted = "খ", homeRow = true, desc = "ক, shifted খ"),
            ),
            // ── Row 3 (Bottom) — Nasals/modifiers | Palatal/Retroflex ─────────
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.32f,
                    contentDesc = "Shift",
                ),
                Ch("হ", shifted = "ঞ", desc = "হ, shifted ঞ"),
                Ch("ম", shifted = "ঙ", desc = "ম, shifted ঙ"),
                Ch("ং", shifted = "ঃ", desc = "ং, shifted ঃ (Visarga)"),
                Ch("ঁ", shifted = "৳", desc = "ঁ, shifted Taka ৳"),
                Ch("চ", shifted = "ছ", desc = "চ, shifted ছ"),
                Ch("জ", shifted = "ঝ", desc = "জ, shifted ঝ"),
                Ch("ট", shifted = "ঠ", desc = "ট, shifted ঠ"),
                Ch("ড", shifted = "ঢ", desc = "ড, shifted ঢ"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f,
                    contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                longPressAction = KeyAction.SwitchNumpad,
                widthWeight = 1.4f,
                contentDesc = "Numbers and symbols, long press for number pad",
            ),
            Key(
                label = "🌐", shiftedLabel = "🌐",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchLayout,
                widthWeight = 1.0f,
                contentDesc = "Switch keyboard layout",
            ),
            Key(
                label = ",", shiftedLabel = "?", hintLabel = "?",
                action = KeyAction.Character(","),
                shiftedAction = KeyAction.Character("?"),
                longPressAction = KeyAction.Character("?"),
                widthWeight = 1.0f,
                contentDesc = "Comma, shifted question mark",
            ),
            Key(
                label = "স্পেস", shiftedLabel = "স্পেস",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.2f,    // spacebar takes majority of the row
                contentDesc = "Space",
            ),
            Key(
                label = "্",           // Hasanta — conjunct trigger
                shiftedLabel = "ৎ",    // Khanda Ta on shift
                hintLabel = "ৎ",
                action = KeyAction.Character("্"),
                shiftedAction = KeyAction.Character("ৎ"),
                longPressAction = KeyAction.Character("ৎ"),
                widthWeight = 1.0f,
                contentDesc = "Hasanta conjunct key, shifted or long-press Khanda Ta ৎ",
            ),
            Key(
                label = "।", shiftedLabel = "!", hintLabel = "!",
                action = KeyAction.Character("।"),
                shiftedAction = KeyAction.Character("!"),
                longPressAction = KeyAction.Character("!"),
                widthWeight = 1.0f,
                contentDesc = "Dari, shifted exclamation",
            ),
            Key(
                label = "↵", shiftedLabel = "↵",
                action = KeyAction.Enter, shiftedAction = KeyAction.Enter,
                widthWeight = 1.4f,
                contentDesc = "Enter",
            ),
        ),
    )
}
