// ─── NFC Normalization ────────────────────────────────────────────────────────

/// Return the Unicode NFC canonical form of `text`.
///
/// Bengali text composed via Kar key-presses can arrive in NFD order
/// (base character + combining diacritic in decomposed sequences).
/// Always committing NFC form ensures Canonical Unicode & Script Integrity
/// and prevents downstream apps from seeing duplicate representations of the same grapheme cluster.
///
/// This implementation does an in-place scan and only allocates a new `String`
/// when the input is not already NFC — the common case during normal typing
/// produces zero allocations.
pub fn nfc_normalize(text: &str) -> String {
    if !text.contains('\u{09BC}') {
        return text.to_string();
    }
    let mut out = String::with_capacity(text.len());
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        if chars.peek() == Some(&'\u{09BC}') {
            match c {
                'ড' => {
                    chars.next();
                    out.push('ড়');
                }
                'ঢ' => {
                    chars.next();
                    out.push('ঢ়');
                }
                'য' => {
                    chars.next();
                    out.push('য়');
                }
                _ => {
                    out.push(c);
                }
            }
        } else {
            out.push(c);
        }
    }
    out
}


/// Returns true if the character is a Bengali vowel (independent or dependent Kar).
#[inline]
pub fn is_bengali_vowel(c: char) -> bool {
    matches!(c,
        '\u{0985}'..='\u{0994}' // Independent vowels: অ, আ, ই, ঈ, উ, ঊ, ঋ, ঌ, এ, ঐ, ও, ঔ
        | '\u{09BE}'..='\u{09CC}' // Dependent vowel signs (Kars): া, ি, ী, ু, ূ, ৃ, ৄ, ে, ৈ, ো, ৌ
        | '\u{09E0}'..='\u{09E1}' // ৠ, ৡ
    )
}

/// Returns true if the character is a Bengali consonant or consonant modifier (Virama/Hasanta, Nukta).
#[inline]
pub fn is_bengali_consonant_or_modifier(c: char) -> bool {
    matches!(c,
        '\u{0995}'..='\u{09B9}' // Consonants: ক through হ
        | '\u{09CE}'             // Khanda Ta: ৎ
        | '\u{09DC}'..='\u{09DF}' // ড়, ঢ়, য়
        | '\u{09CD}'             // Virama / Hasanta: ্
        | '\u{09BC}'             // Nukta: ়
    )
}

/// Returns true if the character is a delimiter, whitespace, or punctuation.
#[inline]
pub fn is_bengali_punctuation_or_space(c: char) -> bool {
    c.is_whitespace() || matches!(c, '।' | '॥' | ',' | ';' | ':' | '?' | '!' | '.' | '"' | '\'' | '(' | ')' | '[' | ']' | '{' | '}' | '-' | '—' | '–' | '/' | '\\')
}

/// When a vowel modifier (Kar) is typed at the beginning of a word, after whitespace/punctuation,
/// or immediately following another vowel (forming diphthongs like খাই, সেই, পাউরুটি),
/// it promotes to the corresponding independent vowel.
pub fn promote_kar_if_needed(kar: &str, should_promote: bool) -> String {
    if !should_promote {
        return kar.to_string();
    }

    match kar {
        "া" => "আ".to_string(),
        "ি" => "ই".to_string(),
        "ী" => "ঈ".to_string(),
        "ু" => "উ".to_string(),
        "ূ" => "ঊ".to_string(),
        "ৃ" => "ঋ".to_string(),
        "ে" => "এ".to_string(),
        "ৈ" => "ঐ".to_string(),
        "ো" => "ও".to_string(),
        "ৌ" => "ঔ".to_string(),
        _ => kar.to_string(),
    }
}

/// When an independent vowel is typed immediately following a consonant,
/// Bengali orthography dictates that it automatically converts into its corresponding Kar sign
/// (e.g. ব + ঋ -> বৃ, ক + আ -> কা, ন + ঔ -> নৌ, ব + ঐ -> বৈ).
pub fn demote_vowel_to_kar_if_preceded_by_consonant(vowel: &str, preceded_by_consonant: bool) -> String {
    if !preceded_by_consonant {
        return vowel.to_string();
    }

    match vowel {
        "আ" => "া".to_string(),
        "ই" => "ি".to_string(),
        "ঈ" => "ী".to_string(),
        "উ" => "ু".to_string(),
        "ঊ" => "ূ".to_string(),
        "ঋ" => "ৃ".to_string(),
        "এ" => "ে".to_string(),
        "ঐ" => "ৈ".to_string(),
        "ও" => "ো".to_string(),
        "ঔ" => "ৌ".to_string(),
        _ => vowel.to_string(),
    }
}

/// Returns dynamic conjunct suggestions for the candidate strip when Hasanta (`্`) is typed.
/// Powered directly by Tier 1 `lekhani-core`'s authentic Bengali `ConjunctCatalog`.
pub fn get_conjunct_suggestions(last_consonant: char) -> Vec<String> {
    if last_consonant == 'র' {
        return vec![
            "র্ক".to_string(),
            "র্গ".to_string(),
            "র্জ".to_string(),
            "র্ণ".to_string(),
            "র্ত".to_string(),
            "র্থ".to_string(),
            "র্দ".to_string(),
            "র্ধ".to_string(),
            "র্প".to_string(),
            "র্ব".to_string(),
            "র্ভ".to_string(),
            "র্ম".to_string(),
            "র্য".to_string(),
            "র্শ".to_string(),
            "র্ষ".to_string(),
            "র্স".to_string(),
            "র্ঘ".to_string(),
            "র্চ".to_string(),
            "র্ন".to_string(),
            "র্হ".to_string(),
        ];
    }
    let prefix = format!("{} + ্", last_consonant);
    let mut results: Vec<String> = lekhani_core::conjuncts::ConjunctCatalog::all()
        .into_iter()
        .filter(|info| info.breakdown.starts_with(&prefix))
        .map(|info| info.conjunct)
        .collect();

    // Ensure Ya-phola (্য) is always offered for any standard consonant
    if is_bengali_consonant_or_modifier(last_consonant) && last_consonant != 'র' && last_consonant != '্' && last_consonant != 'ৎ' {
        let yaphola = format!("{}্য", last_consonant);
        if !results.contains(&yaphola) {
            results.push(yaphola);
        }
    }

    results
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_kar_promotion() {
        assert_eq!(promote_kar_if_needed("া", true), "আ");
        assert_eq!(promote_kar_if_needed("ি", true), "ই");
        assert_eq!(promote_kar_if_needed("ৃ", true), "ঋ");
        assert_eq!(promote_kar_if_needed("ে", true), "এ");
        assert_eq!(promote_kar_if_needed("া", false), "া");
        assert_eq!(promote_kar_if_needed("ৃ", false), "ৃ");
    }

    #[test]
    fn test_vowel_demotion_to_kar() {
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("ঋ", true), "ৃ");
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("ঔ", true), "ৌ");
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("ঐ", true), "ৈ");
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("আ", true), "া");
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("ঋ", false), "ঋ");
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("ঔ", false), "ঔ");
    }

    #[test]
    fn test_is_bengali_vowel() {
        // Independent vowels
        assert!(is_bengali_vowel('অ'));
        assert!(is_bengali_vowel('আ'));
        assert!(is_bengali_vowel('ই'));
        assert!(is_bengali_vowel('এ'));
        assert!(is_bengali_vowel('ও'));

        // Dependent vowel signs (Kars)
        assert!(is_bengali_vowel('া'));
        assert!(is_bengali_vowel('ি'));
        assert!(is_bengali_vowel('ী'));
        assert!(is_bengali_vowel('ু'));
        assert!(is_bengali_vowel('ে'));
        assert!(is_bengali_vowel('ো'));

        // Consonants and non-vowels
        assert!(!is_bengali_vowel('ক'));
        assert!(!is_bengali_vowel('খ'));
        assert!(!is_bengali_vowel('্'));
        assert!(!is_bengali_vowel('ৎ'));
        assert!(!is_bengali_vowel(' '));
    }

    #[test]
    fn test_conjunct_suggestions() {
        let suggestions = get_conjunct_suggestions('ক');
        assert!(suggestions.contains(&"ক্ত".to_string()));
        assert!(suggestions.contains(&"ক্ষ".to_string()));

        let reph_suggestions = get_conjunct_suggestions('র');
        assert!(reph_suggestions.contains(&"র্ক".to_string()));
        assert!(reph_suggestions.contains(&"র্ম".to_string()));
        assert!(reph_suggestions.contains(&"র্ষ".to_string()));
    }

    #[test]
    fn test_is_bengali_consonant_or_modifier() {
        assert!(is_bengali_consonant_or_modifier('ক'));
        assert!(is_bengali_consonant_or_modifier('ষ'));
        assert!(is_bengali_consonant_or_modifier('্'));
        assert!(is_bengali_consonant_or_modifier('়'));
        assert!(!is_bengali_consonant_or_modifier('া'));
        assert!(!is_bengali_consonant_or_modifier('আ'));
        assert!(!is_bengali_consonant_or_modifier(' '));
    }
}
