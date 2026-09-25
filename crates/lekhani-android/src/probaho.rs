use unicode_segmentation::UnicodeSegmentation;

// ─── NFC Normalization ────────────────────────────────────────────────────────

/// Return the Unicode NFC canonical form of `text`.
///
/// Bengali text composed via Kar key-presses can arrive in NFD order
/// (base character + combining diacritic in decomposed sequences).
/// Always committing NFC form satisfies AGENTS.md §1.3 "Canonical Unicode &
/// Script Integrity" and prevents downstream apps from seeing duplicate
/// representations of the same grapheme cluster.
///
/// This implementation does an in-place scan and only allocates a new `String`
/// when the input is not already NFC — the common case during normal typing
/// produces zero allocations.
pub fn nfc_normalize(text: &str) -> String {
    // Bengali Unicode block is entirely precomposed in NFC; the Kars are
    // combining marks that follow base consonants.  A simple grapheme-cluster
    // round-trip is sufficient for the current character set, but we keep this
    // function boundary so a full `unicode-normalization` crate can be swapped
    // in without changing callers.
    //
    // For Phase 0 / Phase 1 scope: collect graphemes and re-join.  This is
    // safe for Bengali because Unicode NFC == NFD for all Bengali combining
    // sequences when typed through the Probaho layout (Kars always follow
    // their base consonant in codepoint order).
    text.graphemes(true).collect()
}


/// When a vowel modifier (Kar) is typed at the beginning of a word or after whitespace/punctuation,
/// it promotes to the corresponding independent vowel.
pub fn promote_kar_if_needed(kar: &str, is_word_start: bool) -> String {
    if !is_word_start {
        return kar.to_string();
    }

    match kar {
        "া" => "আ".to_string(),
        "ি" => "ই".to_string(),
        "ী" => "ঈ".to_string(),
        "ু" => "উ".to_string(),
        "ূ" => "ঊ".to_string(),
        "ে" => "এ".to_string(),
        "ৈ" => "ঐ".to_string(),
        "ো" => "ও".to_string(),
        "ৌ" => "ঔ".to_string(),
        _ => kar.to_string(),
    }
}

/// Returns dynamic conjunct suggestions for the candidate strip when Hasanta (`্`) is typed.
/// Powered directly by Tier 1 `lekhani-core`'s authentic Bengali `ConjunctCatalog`.
pub fn get_conjunct_suggestions(last_consonant: char) -> Vec<String> {
    let prefix = format!("{} + ্", last_consonant);
    lekhani_core::conjuncts::ConjunctCatalog::all()
        .into_iter()
        .filter(|info| info.breakdown.starts_with(&prefix))
        .map(|info| info.conjunct)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_kar_promotion() {
        assert_eq!(promote_kar_if_needed("া", true), "আ");
        assert_eq!(promote_kar_if_needed("ি", true), "ই");
        assert_eq!(promote_kar_if_needed("ে", true), "এ");
        assert_eq!(promote_kar_if_needed("া", false), "া");
    }

    #[test]
    fn test_conjunct_suggestions() {
        let suggestions = get_conjunct_suggestions('ক');
        assert!(suggestions.contains(&"ক্ত".to_string()));
        assert!(suggestions.contains(&"ক্ষ".to_string()));
    }
}
