package com.lekhani.android.model

/**
 * BengaliDiacriticsFormatter
 *
 * Provides high-performance, zero-allocation display formatting for standalone
 * Bengali vowel signs (Kars), Hasanta, and modifier diacritics.
 *
 * When combining marks (e.g. `ি`, `া`, `ু`, `্`, `ং`, `ঃ`, `ঁ`) are rendered alone
 * without a base consonant, Android's HarfBuzz / Skia font shaping engine automatically inserts
 * a Unicode Dotted Circle (`◌` / U+25CC).
 *
 * In the OpenType Indic shaping specification, Non-Breaking Space (`\u00A0` / NBSP) is categorized
 * as a valid whitespace base glyph (`Zs`). Prepending `\u00A0` satisfies the HarfBuzz syllable
 * parser with an invisible whitespace base, cleanly rendering the pure diacritic without any
 * dotted circle on keyboard keycaps, preview bubbles, and alternate popups.
 */
object BengaliDiacriticsFormatter {

    /** Non-Breaking Space (U+00A0) acts as a valid OpenType whitespace base glyph for HarfBuzz */
    const val NBSP: String = "\u00A0"

    /**
     * Checks if a character is a standalone Bengali combining mark (Kar or modifier).
     */
    @JvmStatic
    fun isBengaliCombiningMark(c: Char): Boolean {
        val code = c.code
        return (code in 0x0981..0x0983) || // ঁ ং ঃ (Chandrabindu, Anusvara, Visarga)
               (code == 0x09BC) ||         // ় (Nukta)
               (code in 0x09BE..0x09CD) || // া ি ী ু ূ ৃ ৄ ে ৈ ো ৌ ্ (Vowel Kars & Hasanta)
               (code == 0x09D7) ||         // ৗ (Au length mark)
               (code in 0x09E2..0x09E3)    // ৢ ৣ (Vocalic signs)
    }

    /**
     * Checks if a string starts with or consists of an isolated combining mark that
     * would trigger HarfBuzz dotted-circle (◌) insertion.
     */
    @JvmStatic
    fun needsDottedCircleSuppression(text: String): Boolean {
        if (text.isEmpty()) return false
        val first = text[0]
        return isBengaliCombiningMark(first) && !text.startsWith(NBSP)
    }

    /**
     * Returns a display-safe string that prevents HarfBuzz from rendering a dotted circle (◌).
     * If the input is not an isolated combining mark, returns the original string directly
     * with zero allocations.
     */
    @JvmStatic
    fun formatForDisplay(label: String?): String {
        if (label == null || label.isEmpty()) return ""
        return if (needsDottedCircleSuppression(label)) {
            NBSP + label
        } else {
            label
        }
    }
}
