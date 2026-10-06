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
    c.is_whitespace()
        || matches!(
            c,
            '।' | '॥'
                | ','
                | ';'
                | ':'
                | '?'
                | '!'
                | '.'
                | '"'
                | '\''
                | '('
                | ')'
                | '['
                | ']'
                | '{'
                | '}'
                | '-'
                | '—'
                | '–'
                | '/'
                | '\\'
        )
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

/// When a rare / proxy vowel that lacks a dedicated unshifted Kar key on mobile layouts
/// (e.g. ঋ and ঔ on Probaho) is typed immediately following a consonant,
/// it converts into its corresponding Kar sign (e.g. ব + ঋ -> বৃ for বৃষ্টি, ন + ঔ -> নৌ for নৌকা).
///
/// NOTE: Independent vowels 'ই', 'ও', 'উ', 'এ', 'ঈ', 'ঊ', 'ঐ' MUST NOT be demoted after a consonant,
/// because Bengali has high-frequency authentic words like:
/// - কই, কও (e.g. কই যাবে, কথা কও)
/// - বই, বউ (e.g. বই পড়া, নতুন বউ)
/// - হই, হও (e.g. বড় হই, মানুষ হও)
/// - লও, রও (e.g. বিদায় লও, একটু রও)
/// - সই, খই, দই, নই, মউ, হওয়া, কওয়া, ইত্যাদি!
pub fn demote_vowel_to_kar_if_preceded_by_consonant(
    vowel: &str,
    preceded_by_consonant: bool,
) -> String {
    if !preceded_by_consonant {
        return vowel.to_string();
    }

    match vowel {
        "ঋ" => "ৃ".to_string(),
        "ঔ" => "ৌ".to_string(),
        "আ" => "া".to_string(),
        _ => vowel.to_string(),
    }
}

/// Returns dynamic conjunct suggestions for the candidate strip when Hasanta (`্`) is typed.
/// Powered directly by Tier 1 `lekhani-core`'s authentic Bengali `ConjunctCatalog`
/// combined with high-frequency pholas (্র, ্য, ্ব, ্ম, ্ল) and geminates.
pub fn get_conjunct_suggestions(last_consonant: char) -> Vec<String> {
    if last_consonant == 'র' {
        return vec![
            "র\u{200D}্য".to_string(), // র-এ য-ফলা with ZWJ (র্যাব, র‍্যালি, র‍্যান্ডম)
            "র্য".to_string(),        // রেফ-য (সূর্য, ধৈর্য, কার্য)
            "র্ম".to_string(),        // ধর্ম, কর্ম
            "র্ক".to_string(),        // তর্ক, সার্ক
            "র্ষ".to_string(),        // বর্ষা, আকর্ষণ
            "র্ত".to_string(),        // বার্তা, মূর্তি
            "র্দ".to_string(),        // পর্দা, সর্দি
            "র্ব".to_string(),        // সর্ব, গর্ব
            "র্থ".to_string(),        // অর্থ, ব্যর্থ
            "র্জ".to_string(),        // অর্জন, বর্জন
            "র্ণ".to_string(),        // বর্ণ, স্বর্ণ
            "র্শ".to_string(),        // আদর্শ, দর্শন
            "র্গ".to_string(),        // বর্গ, স্বৰ্গ
            "র্ধ".to_string(),        // অর্ধ, সার্থ
            "র্প".to_string(),        // সর্প, দর্পণ
            "র্ভ".to_string(),        // গর্ভ
            "র্স".to_string(),        // পার্সেল
            "র্ঘ".to_string(),        // দীর্ঘ
            "র্চ".to_string(),        // চর্চা
            "র্ন".to_string(),        // ঝরনা
            "র্হ".to_string(),        // গার্হস্থ্য
        ];
    }
    let prefix = format!("{} + ্", last_consonant);
    let mut results: Vec<String> = lekhani_core::conjuncts::ConjunctCatalog::all()
        .into_iter()
        .filter(|info| info.breakdown.starts_with(&prefix))
        .map(|info| info.conjunct)
        .collect();

    let is_eligible = is_bengali_consonant_or_modifier(last_consonant)
        && !matches!(
            last_consonant,
            'র' | '্' | 'ৎ' | 'ড়' | 'ঢ়' | '়' | 'ং' | 'ঃ' | 'ঁ'
        );

    if is_eligible {
        // 1. R-phola (্র)
        if last_consonant != 'য়' {
            let rphola = format!("{}্র", last_consonant);
            if !results.contains(&rphola) {
                results.push(rphola);
            }
        }

        // 2. Ya-phola (্য)
        let yaphola = format!("{}্য", last_consonant);
        if !results.contains(&yaphola) {
            results.push(yaphola);
        }

        // 3. Ba-phola (্ব)
        if matches!(
            last_consonant,
            'শ' | 'দ'
                | 'ত'
                | 'স'
                | 'ধ'
                | 'হ'
                | 'ম'
                | 'ব'
                | 'জ'
                | 'ক'
                | 'খ'
                | 'গ'
                | 'ল'
        ) {
            let baphola = format!("{}্ব", last_consonant);
            if !results.contains(&baphola) {
                results.push(baphola);
            }
        }

        // 4. Ma-phola (্ম)
        if matches!(
            last_consonant,
            'ত' | 'দ' | 'স' | 'শ' | 'হ' | 'ম' | 'ল' | 'গ' | 'ষ' | 'ক' | 'ণ' | 'ন'
        ) {
            let maphola = format!("{}্ম", last_consonant);
            if !results.contains(&maphola) {
                results.push(maphola);
            }
        }

        // 5. La-phola (্ল)
        if matches!(
            last_consonant,
            'শ' | 'প' | 'ক' | 'গ' | 'ব' | 'ম' | 'ফ' | 'হ' | 'স'
        ) {
            let laphola = format!("{}্ল", last_consonant);
            if !results.contains(&laphola) {
                results.push(laphola);
            }
        }

        // 6. Geminate conjunct (e.g. ক্ক, ত্ত, ব্ব, ল্ল, প্প, ম্ম, ন্ন, চ্চ, জ্জ, দ্দ, স্স, ট্ট, ড্ড)
        if matches!(
            last_consonant,
            'ক' | 'গ'
                | 'চ'
                | 'জ'
                | 'ট'
                | 'ড'
                | 'ণ'
                | 'ত'
                | 'দ'
                | 'ন'
                | 'প'
                | 'ব'
                | 'ম'
                | 'ল'
                | 'শ'
                | 'ষ'
                | 'স'
        ) {
            let geminate = nfc_normalize(&format!("{}{}{}", last_consonant, '্', last_consonant));
            if !results.contains(&geminate) {
                results.push(geminate);
            }
        }

        // 7. Special primary conjunct promotions
        match last_consonant {
            'ক' => {
                if !results.contains(&"ক্ষ".to_string()) {
                    results.push("ক্ষ".to_string());
                }
            }
            'জ' => {
                if !results.contains(&"জ্ঞ".to_string()) {
                    results.push("জ্ঞ".to_string());
                }
            }
            'ঞ' => {
                if !results.contains(&"ঞ্চ".to_string()) {
                    results.push("ঞ্চ".to_string());
                }
                if !results.contains(&"ঞ্জ".to_string()) {
                    results.push("ঞ্জ".to_string());
                }
            }
            'দ' => {
                if !results.contains(&"দ্ধ".to_string()) {
                    results.push("দ্ধ".to_string());
                }
            }
            'গ' => {
                if !results.contains(&"গ্ধ".to_string()) {
                    results.push("গ্ধ".to_string());
                }
            }
            'স' => {
                if !results.contains(&"স্ত".to_string()) {
                    results.push("স্ত".to_string());
                }
                if !results.contains(&"স্থ".to_string()) {
                    results.push("স্থ".to_string());
                }
                if !results.contains(&"স্প".to_string()) {
                    results.push("স্প".to_string());
                }
                if !results.contains(&"স্ফ".to_string()) {
                    results.push("স্ফ".to_string());
                }
            }
            'ষ' => {
                if !results.contains(&"ষ্ট".to_string()) {
                    results.push("ষ্ট".to_string());
                }
                if !results.contains(&"ষ্ঠ".to_string()) {
                    results.push("ষ্ঠ".to_string());
                }
                if !results.contains(&"ষ্ণ".to_string()) {
                    results.push("ষ্ণ".to_string());
                }
            }
            'হ' => {
                if !results.contains(&"হ্ন".to_string()) {
                    results.push("হ্ন".to_string());
                }
                if !results.contains(&"হ্ম".to_string()) {
                    results.push("হ্ম".to_string());
                }
                if !results.contains(&"হ্ল".to_string()) {
                    results.push("হ্ল".to_string());
                }
                if !results.contains(&"হৃ".to_string()) {
                    results.push("হৃ".to_string());
                }
            }
            _ => {}
        }

        // 8. Reph on this consonant (e.g. র্ম, র্ক, র্ষ, র্ণ, র্ত, র্দ, র্ধ, র্ব, র্শ, র্গ)
        if matches!(last_consonant, 'ক'..='হ' | 'ড়'..='ঢ়') && last_consonant != 'র' {
            let reph = format!("র্{}", last_consonant);
            if !results.contains(&reph) {
                results.push(reph);
            }
        }
    }

    // Prioritize R-phola and Ya-phola at index 0 & 1 if present
    if is_eligible {
        let rphola = format!("{}্র", last_consonant);
        if let Some(pos) = results.iter().position(|r| r == &rphola) {
            let item = results.remove(pos);
            results.insert(0, item);
        }
        let yaphola = format!("{}্য", last_consonant);
        if let Some(pos) = results.iter().position(|r| r == &yaphola) {
            let item = results.remove(pos);
            let idx = if !results.is_empty() { 1 } else { 0 };
            results.insert(idx, item);
        }
    }

    results
}

/// Generate high-frequency agglutinative inflectional suffixes for a given base word.
///
/// In Bengali, roots attach specific case markers (বিভক্তি) and enclitics (প্রত্যয়):
/// - Genitive: `-এর` after consonants (`মানুষের`, `দেশের`), `-র` / `-য়ের` after vowels (`কথার`, `পানির`, `বইয়ের`)
/// - Locative: `-এ` after consonants (`দেশে`, `ঘরে`), `-তে` / `-য়ে` after vowels (`পানিতে`, `বইয়ে`, `মাথায়`)
/// - Accusative/Dative: `-কে` (`মানুষকে`, `তোমাকে`)
/// - Definite articles: `-টি`, `-টা` (`মানুষটি`, `বইটি`, `দেশটা`)
/// - Plural: `-গুলো`, `-দের` (`মানুষগুলো`, `বইগুলো`, `মানুষদের`)
pub fn generate_inflectional_suffixes(root: &str) -> Vec<String> {
    if root.is_empty() || root.chars().count() < 2 {
        return Vec::new();
    }

    let last_ch = match root.chars().last() {
        Some(c) => c,
        None => return Vec::new(),
    };

    // If root already ends with Hasanta, modifier (ং, ঃ, ঁ, ৎ), or punctuation, do not inflect
    if matches!(last_ch, '্' | 'ং' | 'ঃ' | 'ঁ' | 'ৎ' | '।' | ',' | '?' | '!') {
        return Vec::new();
    }

    let is_vowel = is_bengali_vowel(last_ch);
    let mut inflected = Vec::with_capacity(8);

    if is_vowel {
        // Root ends in vowel or vowel sign (া, ি, ী, ু, ূ, ৃ, ে, ৈ, ো, ঔ, or independent vowel)
        match last_ch {
            'া' | 'আ' => {
                // কথা -> কথার, কথায়, কথাটি, কথাগুলো, কথাকে
                inflected.push(format!("{}র", root));
                inflected.push(format!("{}য়", root));
                inflected.push(format!("{}টি", root));
                inflected.push(format!("{}গুলো", root));
                inflected.push(format!("{}কে", root));
                inflected.push(format!("{}তে", root));
                inflected.push(format!("{}টা", root));
            }
            'ি' | 'ই' | 'ী' | 'ঈ' => {
                // পানি -> পানির, পানিতে, পানিটা, পানিটি, পানিগুলো
                // বই -> বইয়ের, বইয়ে, বইটি, বইগুলো, বইকে
                if matches!(last_ch, 'ই' | 'ঈ') {
                    inflected.push(format!("{}য়ের", root));
                    inflected.push(format!("{}য়ে", root));
                } else {
                    inflected.push(format!("{}র", root));
                    inflected.push(format!("{}তে", root));
                }
                inflected.push(format!("{}টি", root));
                inflected.push(format!("{}গুলো", root));
                inflected.push(format!("{}কে", root));
                inflected.push(format!("{}টা", root));
            }
            'ে' | 'এ' | 'ো' | 'ও' => {
                // আলো -> আলোর, আলোতে, আলোটি, আলোটা
                inflected.push(format!("{}র", root));
                inflected.push(format!("{}তে", root));
                inflected.push(format!("{}টি", root));
                inflected.push(format!("{}গুলো", root));
                inflected.push(format!("{}কে", root));
                inflected.push(format!("{}টা", root));
            }
            _ => {
                inflected.push(format!("{}র", root));
                inflected.push(format!("{}তে", root));
                inflected.push(format!("{}টি", root));
                inflected.push(format!("{}গুলো", root));
                inflected.push(format!("{}কে", root));
            }
        }
    } else {
        // Root ends in a consonant (e.g. মানুষ, দেশ, ঘর, কাজ, দিন)
        // মানুষ -> মানুষের, মানুষকে, মানুষগুলো, মানুষটি, মানুষদের
        // দেশ -> দেশের, দেশে, দেশকে, দেশগুলো, দেশটি
        inflected.push(format!("{}ের", root));
        inflected.push(format!("{}ে", root));
        inflected.push(format!("{}কে", root));
        inflected.push(format!("{}গুলো", root));
        inflected.push(format!("{}টি", root));
        inflected.push(format!("{}টা", root));
        inflected.push(format!("{}দের", root));
    }

    inflected
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
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("আ", true), "া");
        // Independent vowels that must NEVER be demoted after a consonant:
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("ই", true), "ই");
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("ও", true), "ও");
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("উ", true), "উ");
        assert_eq!(demote_vowel_to_kar_if_preceded_by_consonant("এ", true), "এ");
        assert_eq!(
            demote_vowel_to_kar_if_preceded_by_consonant("ঋ", false),
            "ঋ"
        );
        assert_eq!(
            demote_vowel_to_kar_if_preceded_by_consonant("ঔ", false),
            "ঔ"
        );
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
        assert_eq!(suggestions[0], "ক্র");
        assert_eq!(suggestions[1], "ক্য");
        assert!(suggestions.contains(&"ক্ত".to_string()));
        assert!(suggestions.contains(&"ক্ষ".to_string()));

        let p_suggestions = get_conjunct_suggestions('প');
        assert_eq!(p_suggestions[0], "প্র");
        assert_eq!(p_suggestions[1], "প্য");

        let reph_suggestions = get_conjunct_suggestions('র');
        assert_eq!(reph_suggestions[0], "র\u{200D}্য");
        assert_eq!(reph_suggestions[1], "র্য");
        assert!(reph_suggestions.contains(&"র্ক".to_string()));
        assert!(reph_suggestions.contains(&"র্ম".to_string()));
        assert!(reph_suggestions.contains(&"র্ষ".to_string()));

        // Consonant + Hasanta gives Reph on that consonant
        assert!(
            suggestions.contains(&"র্ক".to_string()),
            "ক + ্ must suggest র্ক"
        );
        let m_suggestions = get_conjunct_suggestions('ম');
        assert!(
            m_suggestions.contains(&"র্ম".to_string()),
            "ম + ্ must suggest র্ম"
        );
    }

    #[test]
    fn test_generate_inflectional_suffixes() {
        let manush = generate_inflectional_suffixes("মানুষ");
        assert!(manush.contains(&"মানুষের".to_string()));
        assert!(manush.contains(&"মানুষকে".to_string()));
        assert!(manush.contains(&"মানুষগুলো".to_string()));
        assert!(manush.contains(&"মানুষটি".to_string()));

        let desh = generate_inflectional_suffixes("দেশ");
        assert!(desh.contains(&"দেশের".to_string()));
        assert!(desh.contains(&"দেশে".to_string()));
        assert!(desh.contains(&"দেশকে".to_string()));
        assert!(desh.contains(&"দেশগুলো".to_string()));

        let boi = generate_inflectional_suffixes("বই");
        assert!(boi.contains(&"বইটি".to_string()));
        assert!(boi.contains(&"বইগুলো".to_string()));
        assert!(boi.contains(&"বইয়ের".to_string()));

        let kotha = generate_inflectional_suffixes("কথা");
        assert!(kotha.contains(&"কথার".to_string()));
        assert!(kotha.contains(&"কথায়".to_string()));
        assert!(kotha.contains(&"কথাটি".to_string()));
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
