package com.lekhani.android.model

/**
 * Pre-computed alternate characters, conjuncts, diacritics, and numeric shortcuts
 * for long-press popups on Lekhani keyboard keys.
 */
object BengaliAlternates {

    private val ALTERNATES_MAP = HashMap<String, Array<String>>(120).apply {
        // ── Bengali Vowels & Modifiers ──────────────────────────────────────────
        put("অ", arrayOf("আ", "ঋ", "ৃ", "অ্যা", "১"))
        put("আ", arrayOf("অ্যা", "অ", "১"))
        put("ই", arrayOf("ঈ", "২"))
        put("ঈ", arrayOf("ই", "২"))
        put("উ", arrayOf("ঊ", "৩"))
        put("ঊ", arrayOf("উ", "৩"))
        put("ঋ", arrayOf("ৃ", "ৠ", "৪"))
        put("এ", arrayOf("ঐ", "ঞ", "৫"))
        put("ঐ", arrayOf("ৈ", "ঞ", "এ", "৫"))
        put("ও", arrayOf("ঔ", "৬"))
        put("ঔ", arrayOf("ৌ", "ও", "৬"))

        // ── Vowel Kars (Diacritics) ─────────────────────────────────────────────
        put("া", arrayOf("আ", "্যা", "া"))
        put("ি", arrayOf("ই", "ী", "ি"))
        put("ী", arrayOf("ঈ", "ি", "ী"))
        put("ু", arrayOf("উ", "ূ", "ু"))
        put("ূ", arrayOf("ঊ", "ু", "ূ"))
        put("ৃ", arrayOf("ঋ", "ৄ", "ৃ"))
        put("ে", arrayOf("এ", "ৈ", "ে"))
        put("ৈ", arrayOf("ঐ", "ৌ", "ঔ", "ে", "ৈ"))
        put("ো", arrayOf("ও", "ৌ", "ঔ", "ো"))
        put("ৌ", arrayOf("ঔ", "ো", "ৌ"))

        // ── Consonants & Conjunct Roots ─────────────────────────────────────────
        put("ক", arrayOf("খ", "ক্ষ", "ক্ত", "ঙ্ক", "ক্র", "১"))
        put("খ", arrayOf("ক", "ক্ষ", "্খ", "১"))
        put("গ", arrayOf("ঘ", "জ্ঞ", "গ্ধ", "গ্র", "ঙ্গ", "৩"))
        put("ঘ", arrayOf("গ", "্ঘ", "৩"))
        put("ঙ", arrayOf("ং", "ঁ", "০"))
        put("চ", arrayOf("ছ", "চ্চ", "্ছ"))
        put("ছ", arrayOf("চ", "্ছ"))
        put("জ", arrayOf("ঝ", "জ্ঞ", "জ্জ", "জ্ব"))
        put("ঝ", arrayOf("জ", "্ঝ"))
        put("ঞ", arrayOf("ঞ্চ", "ঞ্ছ", "ঞ্জ"))
        put("ট", arrayOf("ঠ", "ট্ট", "১"))
        put("ঠ", arrayOf("ট", "্ঠ"))
        put("ড", arrayOf("ড়", "ঢ", "ড্ড"))
        put("ঢ", arrayOf("ঢ়", "ড", "্ঢ"))
        put("ণ", arrayOf("ন", "ণ্ণ", "ণ্ট", "০"))
        put("ত", arrayOf("থ", "ৎ", "ত্র", "ত্ত", "ত্ন", "ত্ব", "৫"))
        put("থ", arrayOf("ত", "্থ", "৫"))
        put("দ", arrayOf("ধ", "দ্ধ", "দ্ব", "দ্র", "দ্ভ", "৪"))
        put("ধ", arrayOf("দ", "্ধ", "৪"))
        put("ন", arrayOf("ণ", "ন্ত", "ন্দ", "ন্ধ", "ন্ন", "ন্ট", "০"))
        put("প", arrayOf("ফ", "প্ত", "প্র", "প্ল", "৮"))
        put("ফ", arrayOf("প", "্ফ", "৮"))
        put("ব", arrayOf("ভ", "ব্র", "ব্ব", "ব্ল", "৭"))
        put("ভ", arrayOf("ব", "্ভ", "৭"))
        put("ম", arrayOf("ম্ম", "ম্প", "ম্ব", "ম্ভ", "ম্ন", "৫"))
        put("য", arrayOf("য়", "্য", "য"))
        put("র", arrayOf("র্", "ড়", "ঢ়", "র\u200D্য", "ৰ", "ৱ", "৴", "৵"))
        put("ল", arrayOf("শ", "ল্ল", "ল্প", "ল্ট", "ল্ড", "ল্ক", "০"))
        put("শ", arrayOf("ষ", "স", "শ্চ", "শ্র", "শ্ব", "২"))
        put("ষ", arrayOf("শ", "ক্ষ", "ষ্ট", "ষ্ঠ", "ষ্ণ", "ষ্প"))
        put("স", arrayOf("ষ", "শ", "স্ত", "স্থ", "স্প", "স্ম", "স্র", "২"))
        put("হ", arrayOf("হ্ন", "হ্ণ", "হ্ম", "হ্য", "হৃ", "হ্র", "৮"))
        put("ড়", arrayOf("ঢ়", "ড"))
        put("ঢ়", arrayOf("ড়", "ঢ"))
        put("য়", arrayOf("য", "্য"))
        put("ৎ", arrayOf("ত", "থ"))
        put("ং", arrayOf("ঁ", "ঃ", "ঙ"))
        put("ঃ", arrayOf("ং", "ঁ"))
        put("ঁ", arrayOf("ং", "ঃ"))
        put("্", arrayOf("্‌", "‌", "‍")) // Hasanta, ZWNJ, ZWJ

        // ── Bengali Digits & Numeric Shortcuts ──────────────────────────────────
        put("১", arrayOf("1", "১"))
        put("২", arrayOf("2", "২"))
        put("৩", arrayOf("3", "৩"))
        put("৪", arrayOf("4", "৪"))
        put("৫", arrayOf("5", "৫"))
        put("৬", arrayOf("6", "৬"))
        put("৭", arrayOf("7", "৭"))
        put("৮", arrayOf("8", "৮"))
        put("৯", arrayOf("9", "৯"))
        put("০", arrayOf("0", "০"))

        // ── Punctuation ─────────────────────────────────────────────────────────
        put("।", arrayOf("॥", ".", "?", "!"))
        put(",", arrayOf(";", "—", "।"))
        put(".", arrayOf("!", "?", ",", "...", "।"))
        put("?", arrayOf("!", "¿", "‽"))
        put("!", arrayOf("?", "¡"))

        // ── English Vowels, Consonants & Numeric Top-Row Shortcuts ─────────────
        put("q", arrayOf("1", "+", "="))
        put("w", arrayOf("2", "\\", "|"))
        put("e", arrayOf("3", "é", "è", "ê", "ë", "ē"))
        put("r", arrayOf("4", "®", "$", "€"))
        put("t", arrayOf("5", "™", "%", "‰"))
        put("y", arrayOf("6", "ÿ", "ý", "¥"))
        put("u", arrayOf("7", "ú", "ù", "û", "ü", "ū"))
        put("i", arrayOf("8", "í", "ì", "î", "ï", "ī"))
        put("o", arrayOf("9", "ó", "ò", "ô", "ö", "õ", "œ"))
        put("p", arrayOf("0", "π", "¶"))
        put("a", arrayOf("á", "à", "â", "ä", "æ", "ã", "å"))
        put("s", arrayOf("ß", "ś", "š", "§"))
        put("d", arrayOf("ð", "đ"))
        put("f", arrayOf("ƒ"))
        put("g", arrayOf("ğ"))
        put("h", arrayOf("—", "–"))
        put("j", arrayOf("¿"))
        put("k", arrayOf("«", "»"))
        put("l", arrayOf("£"))
        put("z", arrayOf("ž", "ź", "ż"))
        put("x", arrayOf("×", "÷"))
        put("c", arrayOf("ç", "ć", "č", "©"))
        put("v", arrayOf("√"))
        put("b", arrayOf("•"))
        put("n", arrayOf("ñ", "ń"))
        put("m", arrayOf("µ"))
        put("A", arrayOf("1", "Á", "À", "Â", "Ä", "Æ", "Ã"))
        put("E", arrayOf("3", "É", "È", "Ê", "Ë", "Ē"))
        put("I", arrayOf("8", "Í", "Ì", "Î", "Ï", "Ī"))
        put("O", arrayOf("9", "Ó", "Ò", "Ô", "Ö", "Õ", "Œ"))
        put("U", arrayOf("7", "Ú", "Ù", "Û", "Ü", "Ū"))
        put("C", arrayOf("Ç", "Ć", "Č"))
        put("N", arrayOf("Ñ", "Ń"))
        put("S", arrayOf("Ś", "Š", "ẞ"))
    }

    /**
     * Retrieves alternate characters for a given key label.
     * Returns an empty array if no alternates are defined.
     */
    fun getAlternates(label: String): Array<String>? = ALTERNATES_MAP[label]
}
