package com.lekhani.android.ui.candidate

/**
 * HomophoneAnnotator
 * Detects known Bengali homophone pairs and attaches disambiguation badges
 * to [CandidateItem]s before they are displayed in the strip.
 *
 * A "homophone pair" here means words that sound identical or near-identical
 * in standard spoken Bengali but differ in spelling and meaning — the single
 * most common source of autocorrect confusion in Bengali IMEs.
 *
 * Implementation note:
 *   The table is a hard-coded bidirectional map for Phase 4.
 *   Phase 4+ can replace this with a model-scored lookup from `lekhani-ai`.
 *   The lookup is O(1) via HashMap — zero allocation on the hot path after
 *   the map is constructed once at object initialization.
 */
object HomophoneAnnotator {

    /**
     * Maps each word in a known homophone pair to its partner.
     * If a candidate appears in this map, its [CandidateItem.homophones]
     * field is set to the partner word so the UI can render a badge.
     *
     * Only genuinely confusing pairs are included — words the engine
     * might plausibly rank first incorrectly.
     */
    private val pairs: HashMap<String, String> = hashMapOf(
        // Verb homophones (most common confusion category)
        "পড়া"      to "পরা",       // to read/fall  ↔  to wear
        "পরা"      to "পড়া",
        "বলা"      to "বলা",       // identical — context-disambiguated; not in map
        "শোনা"     to "শোনা",
        "খাওয়া"    to "খাওয়া",
        // Motion / auxiliary homophones
        "যাওয়া"    to "জাওয়া",    // to go (standard) ↔ dialectal form
        "জাওয়া"    to "যাওয়া",
        // Colloquial suffix pairs (kortei style — FEATURES.md §2)
        "খাব"      to "যাব",       // will eat ↔ will go (both end in -আব sound)
        "যাব"      to "খাব",
        "করব"      to "ধরব",
        "ধরব"      to "করব",
        // Standard homophone noun pairs
        "বাংলা"    to "বাঙলা",     // preferred spelling ↔ alternate romanisation
        "বাঙলা"    to "বাংলা",
        "আলো"      to "আলা",       // light ↔ hollow/skilled (dialectal)
        "আলা"      to "আলো",
        "মাথা"     to "মাথ",
        "মাথ"      to "মাথা",
        // Kar confusion (common phonetic IME error)
        "করে"      to "করি",       // does/by doing ↔ I do (1st person)
        "করি"      to "করে",
        "দেখে"     to "দেখি",
        "দেখি"     to "দেখে",
        "লিখে"     to "লিখি",
        "লিখি"     to "লিখে",
    )

    /**
     * Annotate a list of raw candidate strings with homophone badges.
     *
     * @param candidates  Raw strings from the Rust engine's TypingResult.
     * @param primaryIdx  Index of the primary candidate (usually 0).
     * @return            List of [CandidateItem]s with homophones set where known.
     */
    fun annotate(
        candidates: List<String>,
        primaryIdx: Int = 0,
        verbatimIdx: Int = -1,
    ): List<CandidateItem> {
        return candidates.mapIndexed { i, text ->
            val emoji = com.lekhani.android.data.emoji.EmojiData.isEmoji(text)
            val isVerbatim = (i == verbatimIdx && !emoji)
            CandidateItem(
                text = text,
                isPrimary = i == primaryIdx && !emoji,
                homophones = if (emoji || isVerbatim) null else pairs[text],
                isEmoji = emoji,
                isVerbatimPreview = isVerbatim,
            )
        }
    }
}
