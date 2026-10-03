package com.lekhani.android.data.avro

/**
 * AvroReverseTransliterator
 *
 * Converts Unicode Bengali words back into canonical Avro Phonetic English representations.
 * Used for dynamic recomposition when the user taps on any point in a Bengali word or backspaces
 * into a committed word in Avro mode, allowing the English version to show again in the candidate strip.
 */
object AvroReverseTransliterator {

    private val VOWEL_MAP = mapOf(
        'অ' to "o",
        'আ' to "a",
        'ই' to "i",
        'ঈ' to "I",
        'উ' to "u",
        'ঊ' to "U",
        'ঋ' to "rri",
        'এ' to "e",
        'ঐ' to "oi",
        'ও' to "O",
        'ঔ' to "ou"
    )

    private val KAR_MAP = mapOf(
        'া' to "a",
        'ি' to "i",
        'ী' to "I",
        'ু' to "u",
        'ূ' to "U",
        'ৃ' to "rri",
        'ে' to "e",
        'ৈ' to "oi",
        'ো' to "o",
        'ৌ' to "ou"
    )

    private val CONSONANT_MAP = mapOf(
        'ক' to "k",
        'খ' to "kh",
        'গ' to "g",
        'ঘ' to "gh",
        'ঙ' to "Ng",
        'চ' to "c",
        'ছ' to "ch",
        'জ' to "j",
        'ঝ' to "jh",
        'ঞ' to "NG",
        'ট' to "T",
        'ঠ' to "Th",
        'ড' to "D",
        'ঢ' to "Dh",
        'ণ' to "N",
        'ত' to "t",
        'থ' to "th",
        'দ' to "d",
        'ধ' to "dh",
        'ন' to "n",
        'প' to "p",
        'ফ' to "f",
        'ব' to "b",
        'ভ' to "bh",
        'ম' to "m",
        'য' to "z",
        'র' to "r",
        'ল' to "l",
        'শ' to "sh",
        'ষ' to "Sh",
        'স' to "s",
        'হ' to "h",
        'ড়' to "R",
        'ঢ়' to "Rh",
        'য়' to "y",
        'ৎ' to "t`",
        'ং' to "ng",
        'ঃ' to ":",
        'ঁ' to "^"
    )

    /**
     * Checks if a character belongs to the Bengali Unicode block.
     */
    fun isBengali(c: Char): Boolean = c in '\u0980'..'\u09FF'

    /**
     * Returns true if the string contains at least one Bengali letter.
     */
    fun isBengaliWord(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.any { it in '\u0985'..'\u09B9' || it in '\u09CE'..'\u09DC' || it in '\u09DF'..'\u09E3' }
    }

    /**
     * Converts a Bengali Unicode word into its canonical Avro Phonetic English representation.
     */
    fun bengaliToAvro(bengaliWord: String): String {
        if (bengaliWord.isEmpty()) return ""
        val sb = StringBuilder(bengaliWord.length * 2)
        val len = bengaliWord.length
        var i = 0

        while (i < len) {
            val c = bengaliWord[i]

            // 1. Independent Vowels
            val vowel = VOWEL_MAP[c]
            if (vowel != null) {
                sb.append(vowel)
                i++
                continue
            }

            // 2. Vowel Kars
            val kar = KAR_MAP[c]
            if (kar != null) {
                sb.append(kar)
                i++
                continue
            }

            // 3. Consonants & modifiers
            val cons = CONSONANT_MAP[c]
            if (cons != null) {
                val next = if (i + 1 < len) bengaliWord[i + 1] else null
                val nextNext = if (i + 2 < len) bengaliWord[i + 2] else null

                // Special conjuncts:
                // 3a. ক্ষ (Ksh) -> canonical Avro "kkh"
                if (c == 'ক' && next == '্' && nextNext == 'ষ') {
                    sb.append("kkh")
                    val afterConj = if (i + 3 < len) bengaliWord[i + 3] else null
                    if (afterConj != null && CONSONANT_MAP.containsKey(afterConj) && afterConj != 'ং' && afterConj != 'ঃ' && afterConj != 'ঁ') {
                        sb.append("o")
                    }
                    i += 3
                    continue
                }

                // 3b. জ্ঞ (Gya) -> canonical Avro "gg"
                if (c == 'জ' && next == '্' && nextNext == 'ঞ') {
                    sb.append("gg")
                    val afterConj = if (i + 3 < len) bengaliWord[i + 3] else null
                    if (afterConj != null && CONSONANT_MAP.containsKey(afterConj) && afterConj != 'ং' && afterConj != 'ঃ' && afterConj != 'ঁ') {
                        sb.append("o")
                    }
                    i += 3
                    continue
                }

                // 3c. ঞ্চ/ঞ্ছ/ঞ্জ/ঞ্ঝ -> "n" + consonant (nc, nch, nj, njh)
                if (c == 'ঞ' && next == '্' && (nextNext == 'চ' || nextNext == 'ছ' || nextNext == 'জ' || nextNext == 'ঝ')) {
                    sb.append("n")
                    i += 2 // skip ঞ and ্, so next consonant processes normally
                    continue
                }

                // 3d. Ya-phala (consonant + ্ + য) -> cons + "y"
                if (next == '্' && nextNext == 'য') {
                    sb.append(cons).append("y")
                    val afterYa = if (i + 3 < len) bengaliWord[i + 3] else null
                    if (afterYa != null && CONSONANT_MAP.containsKey(afterYa) && afterYa != 'ং' && afterYa != 'ঃ' && afterYa != 'ঁ') {
                        sb.append("o")
                    }
                    i += 3
                    continue
                }

                // 3e. Ba-phala (consonant + ্ + ব) -> cons + "w"
                if (next == '্' && nextNext == 'ব') {
                    sb.append(cons).append("w")
                    val afterBa = if (i + 3 < len) bengaliWord[i + 3] else null
                    if (afterBa != null && CONSONANT_MAP.containsKey(afterBa) && afterBa != 'ং' && afterBa != 'ঃ' && afterBa != 'ঁ') {
                        sb.append("o")
                    }
                    i += 3
                    continue
                }

                sb.append(cons)

                if (next == '্') {
                    // Hasanta link (conjunct)
                    if (nextNext != null && CONSONANT_MAP.containsKey(nextNext)) {
                        // Consonant cluster: skip hasanta so next consonant attaches directly
                        i += 2
                        continue
                    } else {
                        // Word-final or standalone hasanta
                        sb.append(",,")
                        i += 2
                        continue
                    }
                } else if (next != null && (KAR_MAP.containsKey(next) || next == '্')) {
                    // Consonant directly followed by Kar or Hasanta: no inherent vowel
                    i++
                    continue
                } else if ((c in '\u0995'..'\u09B9' || c == 'ড়' || c == 'ঢ়' || c == 'য়') &&
                    next != null && CONSONANT_MAP.containsKey(next) && next != 'ং' && next != 'ঃ' && next != 'ঁ'
                ) {
                    // True consonant followed by another consonant without hasanta:
                    // In Bengali, syllables carry inherent vowel 'o' (e.g. ক + র -> "kor")
                    sb.append("o")
                    i++
                    continue
                } else {
                    // Word-final consonant or followed by non-consonant / modifier
                    i++
                    continue
                }
            }

            // 4. Bengali digits ০-৯
            if (c in '\u09E6'..'\u09EF') {
                sb.append((c - '\u09E6' + '0'.code).toChar())
                i++
                continue
            }

            // 5. Unmapped character (ASCII, punctuation, symbols)
            sb.append(c)
            i++
        }

        return sb.toString()
    }
}
