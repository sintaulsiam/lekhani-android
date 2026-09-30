package com.lekhani.android.model

/**
 * Lekhani প্রবাহ (Flow) Layout Definition
 * ══════════════════════════════════════════════════════════════════════════════
 * Based on the ergonomic specification in LAYOUT_PROBAHO.md.
 *
 * Design principles:
 *  - Left thumb: vowels & kars (স্বরবর্ণ)
 *  - Right thumb: consonants (ব্যঞ্জনবর্ণ)
 *  - Home row (Row 2): অ া ি ু ে | র ত ন স ক  (68.7% of daily keystrokes)
 *  - Long vowels sit directly ABOVE their short partners (learnability rule 1)
 *  - Aspirated pairs on Shift (learnability rule 2)
 *  - Smart Kar auto-promotion at word-start (learnability rule 3, handled in Rust)
 *  - Hasanta (্) beside Spacebar for one-thumb conjunct access (learnability rule 4)
 */
object ProbahLayout {

    val layout: KeyboardLayout = KeyboardLayout(
        name = "Lekhani প্রবাহ",
        rows = listOf(
            // ── Row 1 (Top) — Long vowels | Labial/Dental consonants ──────────
            listOf(
                Ch("আ", shifted = "ঔ", desc = "আ, shifted ঔ"),
                Ch("ো", shifted = "ৌ", hint = "ঐ", desc = "ো, shifted ৌ, hint ঐ"),
                Ch("ী", shifted = "ঈ", desc = "ী, shifted ঈ"),
                Ch("ূ", shifted = "ঊ", desc = "ূ, shifted ঊ"),
                Ch("ৈ", shifted = "ৃ", hint = "ৃ", desc = "ৈ, shifted ৃ"),
                Ch("প", shifted = "ফ", desc = "প, shifted ফ"),
                Ch("ব", shifted = "ভ", desc = "ব, shifted ভ"),
                Ch("ম", shifted = "গ", hint = "গ", desc = "ম, shifted গ, hint গ"),
                Ch("দ", shifted = "ধ", desc = "দ, shifted ধ"),
                Ch("ল", shifted = "ঘ", hint = "ঘ", desc = "ল, shifted ঘ, hint ঘ"),
            ),
            // ── Row 2 (Home) — Base vowels | Golden 5 consonants ─────────────
            listOf(
                Ch("অ", shifted = "ঋ", hint = "ৃ", homeRow = true, desc = "অ, shifted ঋ, hint ৃ"),
                Ch("া", shifted = "ঽ", homeRow = true, desc = "া, shifted ঽ"),
                Ch("ি", shifted = "য়", homeRow = true, desc = "ি, shifted য়"),
                Ch("ু", shifted = "ৎ", homeRow = true, desc = "ু, shifted ৎ"),
                Ch("ে", shifted = "য",  homeRow = true, desc = "ে, shifted য"),
                Ch("র", shifted = "ড়", hint = "ঢ়", homeRow = true, desc = "র, shifted ড়, hint ঢ়"),
                Ch("ত", shifted = "থ",  homeRow = true, desc = "ত, shifted থ"),
                Ch("ন", shifted = "ণ",  homeRow = true, desc = "ন, shifted ণ"),
                Ch("স", shifted = "শ", hint = "ষ", homeRow = true, desc = "স, shifted শ, hint ষ"),
                Ch("ক", shifted = "খ",  homeRow = true, desc = "ক, shifted খ"),
            ),
            // ── Row 3 (Bottom) — Nasals/modifiers | Palatal/Retroflex ─────────
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.32f,
                    contentDesc = "Shift",
                ),
                Ch("হ", shifted = "ঞ", hint = "ঞ", desc = "হ, shifted ঞ, hint ঞ"),
                Ch("ং", shifted = "ঙ", desc = "ং, shifted ঙ"),
                Ch("ঁ",  shifted = "\u200D", hint = "ZWJ", desc = "ঁ, shifted ZWJ"),    // ZWJ on shift
                Ch("ঃ",  shifted = "\u200C", hint = "ZWNJ", desc = "ঃ, shifted ZWNJ"),   // ZWNJ on shift
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
                longPressAction = KeyAction.SwitchEmoji,
                widthWeight = 1.4f,
                contentDesc = "Numbers and symbols, long press for emoji",
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
                shiftedLabel = "হ",    // হ on shift row (see shifted layout spec)
                hintLabel = "হ",
                action = KeyAction.Character("্"),
                shiftedAction = KeyAction.Character("হ"),
                longPressAction = KeyAction.Character("হ"),
                widthWeight = 1.0f,
                contentDesc = "Hasanta conjunct key, shifted or long-press হ",
            ),
            Key(
                label = "।", shiftedLabel = "!", hintLabel = "?",
                action = KeyAction.Character("।"),
                shiftedAction = KeyAction.Character("!"),
                longPressAction = KeyAction.Character("?"),
                widthWeight = 1.0f,
                contentDesc = "Dari, shifted exclamation, long press question mark",
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
