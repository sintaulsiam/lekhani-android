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
        // Verb & inflection homophones
        "পড়া"      to "পরা",       // to read/fall  ↔  to wear
        "পরা"      to "পড়া",
        "পড়ি"      to "পরি",       // I read        ↔  I wear / fairy
        "পরি"      to "পড়ি",
        "পড়ে"      to "পরে",       // reads / falls ↔  later / after
        "পরে"      to "পড়ে",
        "পড়ব"      to "পরব",       // will read     ↔  will wear
        "পরব"      to "পড়ব",
        // Nouns & homophones with phonetic divergence
        "সব"       to "শব",        // all / every   ↔  corpse
        "শব"       to "সব",
        "বাড়ি"     to "বারি",      // house / home  ↔  water
        "বারি"     to "বাড়ি",
        "গাড়ি"     to "গারি",      // vehicle / car ↔  inflected root
        "গারি"     to "গাড়ি",
        "কোনো"     to "কোন",       // any / some    ↔  which / corner
        "কোন"      to "কোনো",
        "ভালো"     to "ভাল",       // good / fine   ↔  forehead / good
        "ভাল"      to "ভালো",
        "সোনা"     to "শোনা",      // gold          ↔  to listen / hear
        "শোনা"     to "সোনা",
        "দিন"      to "দীন",       // day           ↔  poor / humble
        "দীন"      to "দিন",
        "কুল"      to "কূল",       // berry / clan  ↔  shore / bank
        "কূল"      to "কুল",
        "নীল"      to "নিল",       // blue          ↔  took
        "নিল"      to "নীল",
        "কী"       to "কি",        // what (long)   ↔  interrogative (short)
        "কি"       to "কী",
        "ধনী"      to "ধ্বনি",     // wealthy       ↔  sound / voice
        "ধ্বনি"     to "ধনী",
        // Chandra Bindu (nasalization) pairs
        "কাঁচা"    to "কাচা",      // raw / unwashed
        "কাচা"     to "কাঁচা",
        "বাঁধা"    to "বাধা",      // tied / bound  ↔  obstacle
        "বাধা"     to "বাঁধা",
        "হাঁস"     to "হাস",       // duck          ↔  laugh
        "হাস"      to "হাঁস",
        "চাঁদ"     to "চাদ",       // moon
        "চাদ"      to "চাঁদ",
        "কাঁটা"    to "কাটা",      // thorn         ↔  cut
        "কাটা"     to "কাঁটা",
        // Standard homophone noun & dialectal pairs
        "বাংলা"    to "বাঙলা",     // preferred spelling ↔ alternate romanisation
        "বাঙলা"    to "বাংলা",
        "যাওয়া"    to "জাওয়া",    // to go (standard) ↔ dialectal form
        "জাওয়া"    to "যাওয়া",
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
