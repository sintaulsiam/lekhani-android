package com.lekhani.android.model

/**
 * English QWERTY Layout
 *
 * Standard QWERTY — used as:
 *  - The bilingual English typing layer
 *  - Auto-fallback for password / PIN fields
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
                    widthWeight = 1.32f, contentDesc = "Shift",
                ),
                Ch("z", "Z"), Ch("x", "X"), Ch("c", "C"), Ch("v", "V"),
                Ch("b", "B"), Ch("n", "N"), Ch("m", "M"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                longPressAction = KeyAction.SwitchNumpad,
                widthWeight = 1.4f, contentDesc = "Numbers and symbols, long press for number pad",
            ),
            Key(
                label = "Layout", shiftedLabel = "Layout",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchLayout,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Key(
                label = ",", shiftedLabel = ";", hintLabel = ";",
                action = KeyAction.Character(","),
                shiftedAction = KeyAction.Character(";"),
                longPressAction = KeyAction.SwitchEmoji,
                widthWeight = 1.0f, contentDesc = "Comma, long press for emoji",
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
                Key(
                    label = "ৃ", shiftedLabel = "ৗ", hintLabel = "ঋ",
                    action = KeyAction.Character("ৃ"),
                    shiftedAction = KeyAction.Character("ৗ"),
                    longPressAction = KeyAction.Character("ঋ"),
                    isHomeRow = true, contentDesc = "Ri-kar, shifted Ou-kar, hint Ri",
                ),
                Key(
                    label = "ু", shiftedLabel = "ূ", hintLabel = "উ",
                    action = KeyAction.Character("ু"),
                    shiftedAction = KeyAction.Character("ূ"),
                    longPressAction = KeyAction.Character("উ"),
                    isHomeRow = true, contentDesc = "U-kar, shifted UU-kar, hint U",
                ),
                Key(
                    label = "ি", shiftedLabel = "ী", hintLabel = "ই",
                    action = KeyAction.Character("ি"),
                    shiftedAction = KeyAction.Character("ী"),
                    longPressAction = KeyAction.Character("ই"),
                    isHomeRow = true, contentDesc = "I-kar, shifted II-kar, hint I",
                ),
                Ch("ব", "ভ", homeRow = true),
                Ch("্", "।", homeRow = true, desc = "Hasanta linker, shifted Dari"),
                Ch("া", "অ", homeRow = true, desc = "Aa-kar, shifted A"),
                Ch("ক", "খ", homeRow = true),
                Ch("ত", "থ", homeRow = true),
                Ch("দ", "ধ", homeRow = true),
            ),
            // Row 3 (Shift, z x c v b n m, Khanda Ta, Backspace)
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.32f, contentDesc = "Shift",
                ),
                Ch("ঁ", "ঃ"),
                Key(
                    label = "ো", shiftedLabel = "ৌ", hintLabel = "ও",
                    action = KeyAction.Character("ো"),
                    shiftedAction = KeyAction.Character("ৌ"),
                    longPressAction = KeyAction.Character("ও"),
                    contentDesc = "O-kar, shifted Ou-kar, hint O",
                ),
                Key(
                    label = "ে", shiftedLabel = "ৈ", hintLabel = "এ",
                    action = KeyAction.Character("ে"),
                    shiftedAction = KeyAction.Character("ৈ"),
                    longPressAction = KeyAction.Character("এ"),
                    contentDesc = "E-kar, shifted Oi-kar, hint E",
                ),
                Ch("র", "ল"), Ch("ন", "ণ"), Ch("স", "ষ"),
                Ch("ম", "শ"), Ch("ৎ", "ঋ", desc = "Khanda Ta, shifted Ri"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                longPressAction = KeyAction.SwitchNumpad,
                widthWeight = 1.4f, contentDesc = "Numbers and symbols, long press for number pad",
            ),
            Key(
                label = "🌐", shiftedLabel = "🌐",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchLayout,
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
            Key(
                label = "।", shiftedLabel = "!", hintLabel = "॥",
                action = KeyAction.Character("।"),
                shiftedAction = KeyAction.Character("!"),
                longPressAction = KeyAction.Character("॥"),
                contentDesc = "Dari, shifted exclamation, hint Double Dari",
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
 * Probhat (प्रभात) Layout — Official 12-Key Ergonomic Bengali Layout
 * Features full standard letter coverage including ে/ৈ and ো/ৌ on top row,
 * dedicated Hasanta (্) and Chandra Bindu (ঁ) in row 3, and standardized spacebar.
 */
object ProbhatLayout {

    val layout: KeyboardLayout = KeyboardLayout(
        name = "Probhat",
        rows = listOf(
            // Row 1 (12 keys: দ ূ ী র ট এ ু ি ও প ে ো)
            listOf(
                Ch("দ", "ধ"), Ch("ূ", "ঊ"), Ch("ী", "ঈ"), Ch("র", "ড়"),
                Ch("ট", "ঠ"), Ch("এ", shifted = "ঐ", shiftedHint = "ঞ", desc = "এ, shifted ঐ, long-press ঞ"), Ch("ু", "উ"), Ch("ি", "ই"),
                Ch("ও", "ঔ"), Ch("প", "ফ"), Ch("ে", "ৈ"), Ch("ো", "ৌ"),
            ),
            // Row 2 (Home: া স ড ত গ হ জ ক ল — 9 keys centered)
            listOf(
                Ch("া", "অ", homeRow = true), Ch("স", "ষ", homeRow = true),
                Ch("ড", "ঢ", homeRow = true), Ch("ত", "থ", homeRow = true),
                Ch("গ", "ঘ", homeRow = true), Ch("হ", "ঃ", homeRow = true),
                Ch("জ", "ঝ", homeRow = true), Ch("ক", "খ", homeRow = true),
                Ch("ল", "ং", homeRow = true),
            ),
            // Row 3 (Shift, 8 letters + Ri-kar + Hasanta + Backspace = 11 keys)
            listOf(
                Key(
                    label = "⇧", shiftedLabel = "⇧",
                    action = KeyAction.Shift, shiftedAction = KeyAction.Shift,
                    widthWeight = 1.32f, contentDesc = "Shift",
                ),
                Ch("য়", "য"), Ch("শ", "ঢ়"), Ch("চ", "ছ"),
                Ch("আ", "ঋ"), Ch("ব", "ভ"), Ch("ন", "ণ"),
                Ch("ম", "ঙ"),
                Ch("ৃ", "<", desc = "Ri-kar, shifted less-than"),
                Ch("্", "ঁ", desc = "Hasanta virama, shifted Chandra Bindu"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.32f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?123", shiftedLabel = "?123",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                longPressAction = KeyAction.SwitchNumpad,
                widthWeight = 1.4f, contentDesc = "Numbers and symbols, long press for number pad",
            ),
            Key(
                label = "🌐", shiftedLabel = "🌐",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchLayout,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Key(
                label = ",", shiftedLabel = ";",
                action = KeyAction.Character(","),
                shiftedAction = KeyAction.Character(";"),
                widthWeight = 1.0f, contentDesc = "Comma",
            ),
            Key(
                label = "◀   প্রভাত   ▶", shiftedLabel = "◀   প্রভাত   ▶",
                action = KeyAction.Space, shiftedAction = KeyAction.Space,
                widthWeight = 4.2f, contentDesc = "Space",
            ),
            Key(
                label = "।", shiftedLabel = "?", hintLabel = "॥",
                action = KeyAction.Character("।"),
                shiftedAction = KeyAction.Character("?"),
                longPressAction = KeyAction.Character("॥"),
                widthWeight = 1.0f,
                contentDesc = "Dari, shifted question, hint Double Dari",
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
 * Gboard Bengali (জি-বোর্ড বাংলা) Layout
 * Official Gboard Bengali Varnamala Layout with Dynamic Vowel/Kar Row.
 * - In default/initial state: Row 1 displays independent vowels (অ..ঔ).
 * - After a consonant is typed: Row 1 dynamically morphs into vowel kars (া..ৌ).
 */
object GboardBengaliLayout {

    // Default independent vowels for Row 1 (11 independent vowels)
    val vowelsRow: List<Key> = listOf(
        Ch("অ", "অ"), Ch("আ", "আ"), Ch("ই", "ই"), Ch("ঈ", "ঈ"),
        Ch("উ", "উ"), Ch("ঊ", "ঊ"), Ch("ঋ", "ঋ"), Ch("এ", "এ"),
        Ch("ঐ", "ঐ"), Ch("ও", "ও"), Ch("ঔ", "ঔ"),
    )

    private val emptyDynamicVowelsRow: List<Key> = listOf(
        Key(label = "া", action = KeyAction.Character("া")),
        Key(label = "ি", action = KeyAction.Character("ি")),
        Key(label = "ী", action = KeyAction.Character("ী")),
        Key(label = "ু", action = KeyAction.Character("ু")),
        Key(label = "ূ", action = KeyAction.Character("ূ")),
        Key(label = "ৃ", action = KeyAction.Character("ৃ")),
        Key(label = "ে", action = KeyAction.Character("ে")),
        Key(label = "ৈ", action = KeyAction.Character("ৈ")),
        Key(label = "ো", action = KeyAction.Character("ো")),
        Key(label = "ৌ", action = KeyAction.Character("ৌ")),
        Key(
            label = "অ",
            action = KeyAction.ToggleGboardVowels,
            longPressAction = KeyAction.Character("অ"),
            contentDesc = "Independent vowels",
        ),
    )

    private val dynamicVowelsCache = java.util.concurrent.ConcurrentHashMap<String, List<Key>>()

    // Dynamic vowel kars for Row 1 (activated immediately after typing any consonant)
    // If an active consonant is present (e.g. 'ম'), renders as 'মা', 'মি', 'মী'..., emitting the kar token on press
    fun getDynamicVowelsRow(activeConsonant: String = ""): List<Key> {
        val c = activeConsonant
        if (c.isEmpty()) return emptyDynamicVowelsRow
        return dynamicVowelsCache.computeIfAbsent(c) {
            listOf(
                Key(label = "$c\u09BE", action = KeyAction.Character("\u09BE"), contentDesc = "$c-kar A"),
                Key(label = "$c\u09BF", action = KeyAction.Character("\u09BF"), contentDesc = "$c-kar I"),
                Key(label = "$c\u09C0", action = KeyAction.Character("\u09C0"), contentDesc = "$c-kar II"),
                Key(label = "$c\u09C1", action = KeyAction.Character("\u09C1"), contentDesc = "$c-kar U"),
                Key(label = "$c\u09C2", action = KeyAction.Character("\u09C2"), contentDesc = "$c-kar UU"),
                Key(label = "$c\u09C3", action = KeyAction.Character("\u09C3"), contentDesc = "$c-kar R"),
                Key(label = "$c\u09C7", action = KeyAction.Character("\u09C7"), contentDesc = "$c-kar E"),
                Key(label = "$c\u09C8", action = KeyAction.Character("\u09C8"), contentDesc = "$c-kar AI"),
                Key(label = "$c\u09CB", action = KeyAction.Character("\u09CB"), contentDesc = "$c-kar O"),
                Key(label = "$c\u09CC", action = KeyAction.Character("\u09CC"), contentDesc = "$c-kar OU"),
                Key(
                    label = "অ",
                    action = KeyAction.ToggleGboardVowels,
                    longPressAction = KeyAction.Character("অ"),
                    contentDesc = "Independent vowels, long press for A",
                ),
            )
        }
    }

    private val dynamicRow5Cache = java.util.concurrent.ConcurrentHashMap<String, List<Key>>()

    // Dynamic Row 5 for Gboard: preserves Hasanta (্) while offering contextual phalas and modifiers
    fun getDynamicRow5(activeConsonant: String = ""): List<Key> {
        val c = activeConsonant
        return dynamicRow5Cache.computeIfAbsent(c) {
            val jaPhalaLabel = if (c.isNotEmpty()) "$c\u09CD\u09AF" else "◌্য"
            val roPhalaLabel = if (c.isNotEmpty()) "$c\u09CD\u09B0" else "◌্র"

            listOf(
                Ch("স", "স"), Ch("হ", "হ"), Ch("ড়", "ড়"), Ch("ঢ়", "ঢ়"),
                Ch("য়", "য়"), Ch("ৎ", "ৎ"),
                Ch("্", "্", desc = "Hasanta conjunct key"),
                Key(
                    label = jaPhalaLabel,
                    action = KeyAction.Character("\u09CD\u09AF"),
                    hintLabel = if (c.isNotEmpty()) "$c\u09CD\u09AC" else "◌্ব",
                    longPressAction = KeyAction.Character("\u09CD\u09AC"),
                    contentDesc = "Ya-phala, long press Ba-phala",
                ),
                Key(
                    label = roPhalaLabel,
                    action = KeyAction.Character("\u09CD\u09B0"),
                    contentDesc = "Ra-phala",
                ),
                Ch("ং", "ঁ", hint = "ঁ", desc = "Anusvara, hint Chandrabindu"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.0f, contentDesc = "Backspace",
                ),
            )
        }
    }

    val layout: KeyboardLayout = KeyboardLayout(
        name = "জি-বোর্ড বাংলা",
        rows = listOf(
            // Row 1 (11 keys: Dynamic Vowels অ..ঔ / Kars া..ৌ)
            vowelsRow,
            // Row 2 (10 keys: ক খ গ ঘ ঙ চ ছ জ ঝ ঞ)
            listOf(
                Ch("ক", "ক"), Ch("খ", "খ"), Ch("গ", "গ"), Ch("ঘ", "ঘ"),
                Ch("ঙ", "ঙ"), Ch("চ", "চ"), Ch("ছ", "ছ"), Ch("জ", "জ"),
                Ch("ঝ", "ঝ"), Ch("ঞ", "ঞ"),
            ),
            // Row 3 (10 keys: ট ঠ ড ঢ ণ ত থ দ ধ ন)
            listOf(
                Ch("ট", "ট"), Ch("ঠ", "ঠ"), Ch("ড", "ড"), Ch("ঢ", "ঢ"),
                Ch("ণ", "ণ"), Ch("ত", "ত"), Ch("থ", "থ"), Ch("দ", "দ"),
                Ch("ধ", "ধ"), Ch("ন", "ন"),
            ),
            // Row 4 (10 keys: প ফ ব ভ ম য র ল শ ষ)
            listOf(
                Ch("প", "প"), Ch("ফ", "ফ"), Ch("ব", "ব"), Ch("ভ", "ভ"),
                Ch("ম", "ম"), Ch("য", "য"), Ch("র", "র"), Ch("ল", "ল"),
                Ch("শ", "শ"), Ch("ষ", "ষ"),
            ),
            // Row 5 (11 keys: স হ ড় ঢ় য় ৎ ্ ং ঃ ঁ ⌫)
            listOf(
                Ch("স", "স"), Ch("হ", "হ"), Ch("ড়", "ড়"), Ch("ঢ়", "ঢ়"),
                Ch("য়", "য়"), Ch("ৎ", "ৎ"),
                Ch("্", "্", desc = "Hasanta conjunct key"),
                Ch("ং", "ং", desc = "Anusvara"),
                Ch("ঃ", "ঃ", desc = "Visarga"),
                Ch("ঁ", "ঁ", desc = "Chandrabindu"),
                Key(
                    label = "⌫", shiftedLabel = "⌫",
                    action = KeyAction.Backspace, shiftedAction = KeyAction.Backspace,
                    widthWeight = 1.0f, contentDesc = "Backspace",
                ),
            ),
        ),
        spacebarRow = listOf(
            Key(
                label = "?১২৩", shiftedLabel = "?১২৩",
                action = KeyAction.SwitchNumeric, shiftedAction = KeyAction.SwitchNumeric,
                longPressAction = KeyAction.SwitchNumpad,
                widthWeight = 1.4f, contentDesc = "Numbers and symbols, long press for number pad",
            ),
            Key(
                label = "🌐", shiftedLabel = "🌐",
                action = KeyAction.SwitchLayout, shiftedAction = KeyAction.SwitchLayout,
                widthWeight = 1.0f, contentDesc = "Switch layout",
            ),
            Key(
                label = ",", shiftedLabel = ";",
                action = KeyAction.Character(","),
                shiftedAction = KeyAction.Character(";"),
                widthWeight = 1.0f, contentDesc = "Comma",
            ),
            Key(
                label = "বাংলা", shiftedLabel = "বাংলা",
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

