package com.lekhani.android.model

/**
 * Pre-computed alternate characters, conjuncts, diacritics, and numeric shortcuts
 * for long-press popups on Lekhani keyboard keys.
 */
object BengaliAlternates {

    private val ALTERNATES_MAP = HashMap<String, Array<String>>(120).apply {
        // ── Bengali Vowels & Modifiers ──────────────────────────────────────────
        put("অ", arrayOf("আ", "অ্যা", "১"))
        put("আ", arrayOf("অ্যা", "অ", "১"))
        put("ই", arrayOf("ঈ", "২"))
        put("ঈ", arrayOf("ই", "২"))
        put("উ", arrayOf("ঊ", "৩"))
        put("ঊ", arrayOf("উ", "৩"))
        put("ঋ", arrayOf("ৠ", "৪"))
        put("এ", arrayOf("ঐ", "৫"))
        put("ঐ", arrayOf("এ", "৫"))
        put("ও", arrayOf("ঔ", "৬"))
        put("ঔ", arrayOf("ও", "৬"))

        // ── Vowel Kars (Diacritics) ─────────────────────────────────────────────
        put("া", arrayOf("্যা", "া"))
        put("ি", arrayOf("ী", "ি"))
        put("ী", arrayOf("ি", "ী"))
        put("ু", arrayOf("ূ", "ু"))
        put("ূ", arrayOf("ু", "ূ"))
        put("ৃ", arrayOf("ৄ", "ৃ"))
        put("ে", arrayOf("ৈ", "ে"))
        put("ৈ", arrayOf("ে", "ৈ"))
        put("ো", arrayOf("ৌ", "ো"))
        put("ৌ", arrayOf("ো", "ৌ"))

        // ── Consonants & Conjunct Roots ─────────────────────────────────────────
        put("ক", arrayOf("ক্ষ", "খ", "১", "্ক"))
        put("খ", arrayOf("ক", "১", "্খ"))
        put("গ", arrayOf("ঘ", "জ্ঞ", "৩", "্গ"))
        put("ঘ", arrayOf("গ", "৩", "্ঘ"))
        put("ঙ", arrayOf("ং", "ঁ", "০"))
        put("চ", arrayOf("ছ", "চ্চ", "্ছ"))
        put("ছ", arrayOf("চ", "্ছ"))
        put("জ", arrayOf("ঝ", "জ্ঞ", "জ্জ"))
        put("ঝ", arrayOf("জ", "্ঝ"))
        put("ঞ", arrayOf("ঞ্চ", "ঞ্ছ", "ঞ্জ"))
        put("ট", arrayOf("ঠ", "ট্ট", "১"))
        put("ঠ", arrayOf("ট", "্ঠ"))
        put("ড", arrayOf("ড়", "ঢ", "ড্ড"))
        put("ঢ", arrayOf("ঢ়", "ড", "্ঢ"))
        put("ণ", arrayOf("ন", "ণ্ণ", "ণ্ট"))
        put("ত", arrayOf("ৎ", "থ", "ত্র", "৫"))
        put("থ", arrayOf("ত", "্থ", "৫"))
        put("দ", arrayOf("ধ", "দ্ধ", "দ্ব", "৪"))
        put("ধ", arrayOf("দ", "্ধ", "৪"))
        put("ন", arrayOf("ণ", "ন্ন", "ন্ট", "০"))
        put("প", arrayOf("ফ", "প্ত", "প্র", "৮"))
        put("ফ", arrayOf("প", "্ফ", "৮"))
        put("ব", arrayOf("ভ", "ব্ব", "ব্র", "৭"))
        put("ভ", arrayOf("ব", "্ভ", "৭"))
        put("ম", arrayOf("ম্ম", "ম্প", "ম্ব", "৫"))
        put("য", arrayOf("য়", "্য", "য"))
        put("র", arrayOf("ড়", "র\u200D্য", "ৰ", "ৱ", "৴", "৵"))
        put("ল", arrayOf("ল্ল", "ল্প", "০"))
        put("শ", arrayOf("ষ", "স", "শ্চ", "২"))
        put("ষ", arrayOf("শ", "ক্ষ", "ষ্ট"))
        put("স", arrayOf("শ", "স্ত", "স্থ", "২"))
        put("হ", arrayOf("হ্ন", "হ্ম", "হ্ল", "৮"))
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
        put(".", arrayOf("...", "।", ",", "?"))
        put("?", arrayOf("!", "¿", "‽"))
        put("!", arrayOf("?", "¡"))

        // ── English Vowels & Diacritics ─────────────────────────────────────────
        put("a", arrayOf("á", "à", "â", "ä", "æ", "ã", "å"))
        put("e", arrayOf("é", "è", "ê", "ë", "ē"))
        put("i", arrayOf("í", "ì", "î", "ï", "ī"))
        put("o", arrayOf("ó", "ò", "ô", "ö", "õ", "œ"))
        put("u", arrayOf("ú", "ù", "û", "ü", "ū"))
        put("c", arrayOf("ç", "ć", "č"))
        put("n", arrayOf("ñ", "ń"))
        put("s", arrayOf("ß", "ś", "š"))
        put("y", arrayOf("ÿ", "ý"))
        put("z", arrayOf("ž", "ź", "ż"))
        put("A", arrayOf("Á", "À", "Â", "Ä", "Æ", "Ã"))
        put("E", arrayOf("É", "È", "Ê", "Ë", "Ē"))
        put("I", arrayOf("Í", "Ì", "Î", "Ï", "Ī"))
        put("O", arrayOf("Ó", "Ò", "Ô", "Ö", "Õ", "Œ"))
        put("U", arrayOf("Ú", "Ù", "Û", "Ü", "Ū"))
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
