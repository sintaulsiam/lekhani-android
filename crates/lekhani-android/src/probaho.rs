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
            "র্য".to_string(),         // রেফ-য (সূর্য, ধৈর্য, কার্য)
            "র্ম".to_string(),         // ধর্ম, কর্ম
            "র্ক".to_string(),         // তর্ক, সার্ক
            "র্ষ".to_string(),         // বর্ষা, আকর্ষণ
            "র্ত".to_string(),         // বার্তা, মূর্তি
            "র্দ".to_string(),         // পর্দা, সর্দি
            "র্ব".to_string(),         // সর্ব, গর্ব
            "র্থ".to_string(),         // অর্থ, ব্যর্থ
            "র্জ".to_string(),         // অর্জন, বর্জন
            "র্ণ".to_string(),         // বর্ণ, স্বর্ণ
            "র্শ".to_string(),         // আদর্শ, দর্শন
            "র্গ".to_string(),         // বর্গ, স্বৰ্গ
            "র্ধ".to_string(),         // অর্ধ, সার্থ
            "র্প".to_string(),         // সর্প, দর্পণ
            "র্ভ".to_string(),         // গর্ভ
            "র্স".to_string(),         // পার্সেল
            "র্ঘ".to_string(),         // দীর্ঘ
            "র্চ".to_string(),         // চর্চা
            "র্ন".to_string(),         // ঝরনা
            "র্হ".to_string(),         // গার্হস্থ্য
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

    // Project "রূপমূল" (Rupmul): Multi-Tier Agglutinative Compounds (e.g. মানুষগুলোর, মানুষগুলোরও)
    let synthesizer = lekhani_core::morpheme::RupmulGraphSynthesizer::new();
    for form in synthesizer.synthesize_agglutinations(root) {
        if !inflected.contains(&form) {
            inflected.push(form);
        }
    }

    inflected
}

fn resolve_fixed_space_commit(raw: &str, chosen_candidate: Option<&str>) -> String {
    if let Some(cand) = chosen_candidate {
        nfc_normalize(cand)
    } else {
        let db = crate::session::get_core_database();
        let lower = raw.to_lowercase();
        let autocompleted = if let Ok(uac) = db.user_autocorrect.read() {
            uac.get(raw).cloned().or_else(|| uac.get(&lower).cloned())
        } else {
            None
        };
        if let Some(repl) = autocompleted {
            nfc_normalize(&repl)
        } else {
            nfc_normalize(raw)
        }
    }
}

fn regenerate_fixed_candidates(input: &str) -> Vec<String> {
    let db = crate::session::get_core_database();
    let prefix_matches = db.trie.find_prefix_entries(input, 8);
    let mut cands = vec![input.to_string()];
    for (word, _) in prefix_matches {
        if !cands.iter().any(|c| c == word) {
            cands.push(word.to_string());
        }
    }
    cands
}

pub(crate) struct ProbahoStrategy;

impl crate::layout::LayoutStrategy for ProbahoStrategy {
    fn process_key(
        &self,
        key: &str,
        state: &mut crate::session::SessionState,
    ) -> Result<crate::session::TypingResult, crate::error::LekhaniError> {
        let now = std::time::Instant::now();
        let is_rapid_consonant_double_tap = state
            .last_consonant_tap
            .map(|(c, t)| key.starts_with(c) && now.duration_since(t).as_millis() <= 280)
            .unwrap_or(false);
        let mut did_geminate_or_revert = false;

        let chars: Vec<char> = state.composing_buffer.chars().collect();
        let last_ch = chars.last().copied();
        let (is_start, last_is_vowel, last_is_consonant) = if let Some(last) = last_ch {
            (
                false,
                is_bengali_vowel(last),
                is_bengali_consonant_or_modifier(last) && last != '্',
            )
        } else if let Some(ctx_ch) = state.surrounding_context.chars().last() {
            if is_bengali_consonant_or_modifier(ctx_ch) && ctx_ch != '্' {
                (false, false, true)
            } else if is_bengali_vowel(ctx_ch) {
                (false, true, false)
            } else {
                (true, false, false)
            }
        } else {
            (true, false, false)
        };
        let key_first_char = key.chars().next();
        let is_raw_kar = key.chars().count() == 1
            && key_first_char.is_some_and(|c| ('\u{09BE}'..='\u{09CC}').contains(&c));
        let last_is_kar = last_ch.is_some_and(|c| ('\u{09BE}'..='\u{09CC}').contains(&c));
        let prev_is_consonant = chars.len() >= 2
            && is_bengali_consonant_or_modifier(chars[chars.len() - 2])
            && chars[chars.len() - 2] != '্';
        let is_same_kar_double_tap =
            is_raw_kar && last_is_kar && prev_is_consonant && last_ch == key_first_char;

        let promoted = if state.smart_initial_kar_enabled {
            promote_kar_if_needed(key, is_start || last_is_vowel)
        } else {
            key.to_string()
        };
        let transformed = if state.smart_initial_kar_enabled {
            demote_vowel_to_kar_if_preceded_by_consonant(&promoted, last_is_consonant)
        } else {
            promoted.clone()
        };

        if is_same_kar_double_tap {
            let iv = match last_ch {
                Some('ি') => Some("ই"),
                Some('ো') => Some("ও"),
                Some('ু') => Some("উ"),
                Some('ে') => Some("এ"),
                Some('ী') => Some("ঈ"),
                Some('ূ') => Some("ঊ"),
                Some('ৈ') => Some("ঐ"),
                _ => None,
            };
            if let Some(independent_vowel) = iv {
                state.composing_buffer.pop();
                state.composing_buffer.push_str(independent_vowel);
            } else {
                state.composing_buffer.pop();
                state.composing_buffer.push_str(&transformed);
            }
        } else if is_raw_kar
            && prev_is_consonant
            && last_ch.is_some_and(|c| {
                matches!(
                    (c, key),
                    ('ই', "ি")
                        | ('ও', "ো")
                        | ('উ', "ু")
                        | ('এ', "ে")
                        | ('ঈ', "ী")
                        | ('ঊ', "ূ")
                        | ('ঐ', "ৈ")
                )
            })
        {
            state.composing_buffer.pop();
            state.composing_buffer.push_str(key);
        } else if is_raw_kar && last_is_kar && prev_is_consonant && !last_is_vowel {
            state.composing_buffer.pop();
            state.composing_buffer.push_str(&transformed);
        } else if (is_raw_kar || transformed != key) && last_ch == Some('ঁ') {
            state.composing_buffer.pop();
            state.composing_buffer.push_str(&transformed);
            state.composing_buffer.push('ঁ');
        } else if key == "র্"
            && last_ch.is_some_and(|c| {
                is_bengali_consonant_or_modifier(c) && c != '্' && c != 'র' && c != 'ৎ'
            })
        {
            if let Some(c) = state.composing_buffer.pop() {
                state.composing_buffer.push_str("র্");
                state.composing_buffer.push(c);
            }
        } else if state.geminate_double_tap_enabled
            && key.chars().count() == 1
            && last_ch == key_first_char
            && chars.len() >= 3
            && chars[chars.len() - 2] == '্'
            && chars[chars.len() - 3] == chars[chars.len() - 1]
        {
            state.composing_buffer.pop();
            state.composing_buffer.pop();
            state.composing_buffer.push_str(key);
            did_geminate_or_revert = true;
            state.last_consonant_tap = None;
        } else if state.geminate_double_tap_enabled
            && is_rapid_consonant_double_tap
            && key.chars().count() == 1
            && last_ch.is_some_and(|c| {
                key.starts_with(c)
                    && is_bengali_consonant_or_modifier(c)
                    && c != '্'
                    && c != 'ৎ'
                    && c != 'ড়'
                    && c != 'ঢ়'
                    && c != 'য়'
            })
            && (chars.len() < 2
                || key_first_char
                    .is_some_and(|kc| chars[chars.len() - 2] != '্' && chars[chars.len() - 2] != kc))
        {
            state.composing_buffer.push('্');
            state.composing_buffer.push_str(key);
            did_geminate_or_revert = true;
            state.last_consonant_tap = None;
        } else {
            state.composing_buffer.push_str(&transformed);
        }

        if !did_geminate_or_revert {
            if let Some(ch) = key_first_char.filter(|_| key.chars().count() == 1) {
                if is_bengali_consonant_or_modifier(ch) && ch != '্' && ch != 'ৎ' {
                    state.last_consonant_tap = Some((ch, now));
                } else {
                    state.last_consonant_tap = None;
                }
            } else {
                state.last_consonant_tap = None;
            }
        }

        let mut candidates = Vec::new();

        if state.hasanta_conjuncts_enabled && (promoted == "্" || key == "্") {
            let chars: Vec<char> = state.composing_buffer.chars().collect();
            let hasanta_pos = chars.len().wrapping_sub(1);
            if hasanta_pos > 0 {
                let base_consonant = chars[hasanta_pos - 1];
                candidates = get_conjunct_suggestions(base_consonant);
            } else if let Some(base_consonant) = state.surrounding_context.chars().last() {
                if is_bengali_consonant_or_modifier(base_consonant) {
                    candidates = get_conjunct_suggestions(base_consonant);
                }
            }
        }

        if candidates.is_empty() {
            let db = crate::session::get_core_database();
            let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 6);
            if !prefix_matches.is_empty() {
                candidates.push(state.composing_buffer.clone());
                for (word, _) in prefix_matches {
                    if !candidates.iter().any(|c| c == word) {
                        candidates.push(word.to_string());
                    }
                }
            }
        }

        let cb_chars: Vec<char> = state.composing_buffer.chars().collect();
        if cb_chars.len() == 2 && is_bengali_consonant_or_modifier(cb_chars[0]) && cb_chars[0] != '্'
        {
            let iv_partner = match cb_chars[1] {
                'ি' => Some('ই'),
                'ো' => Some('ও'),
                'ু' => Some('উ'),
                'ে' => Some('এ'),
                'ী' => Some('ঈ'),
                'ূ' => Some('ঊ'),
                'ৈ' => Some('ঐ'),
                _ => None,
            };
            if let Some(iv) = iv_partner {
                let iv_word = format!("{}{}", cb_chars[0], iv);
                let db = crate::session::get_core_database();
                if db.trie.contains_exact(&iv_word) && !candidates.contains(&iv_word) {
                    let insert_pos = if candidates.is_empty() { 0 } else { 1 };
                    candidates.insert(insert_pos, iv_word);
                }
            }
        }

        if !state.composing_buffer.ends_with('্') && state.composing_buffer.chars().count() >= 2 {
            let db = crate::session::get_core_database();
            let is_exact_word = db.trie.contains_exact(&state.composing_buffer);
            if is_exact_word || candidates.len() < 5 {
                let inflections = generate_inflectional_suffixes(&state.composing_buffer);
                for inf in inflections {
                    if candidates.len() >= 8 {
                        break;
                    }
                    if (db.trie.contains_exact(&inf) || candidates.len() < 4)
                        && !candidates.contains(&inf)
                    {
                        candidates.push(inf);
                    }
                }
            }
        }

        if candidates.len() < 5 {
            let db = crate::session::get_core_database();
            let stems = lekhani_core::phonetic::morphology::peel_all_stems(&state.composing_buffer);
            for stem in stems {
                if stem.len() >= 2 {
                    let stem_matches = db.trie.find_prefix_matches(&stem, 3);
                    for w in stem_matches {
                        if candidates.len() >= 8 {
                            break;
                        }
                        if !candidates.contains(&w) {
                            candidates.push(w);
                        }
                    }
                }
            }
        }

        if !state.surrounding_context.is_empty() && candidates.len() > 1 {
            let mut words_buf = [""; 16];
            let count =
                crate::session::get_context_words(&state.surrounding_context, &mut words_buf);
            let words = &words_buf[..count];
            let scorer = crate::session::get_context_scorer();
            let right_word = state.right_context.split_whitespace().next();
            scorer.rank_candidates_in_place_bidirectional(words, right_word, &mut candidates);
        }

        if let Some(pos) = candidates.iter().position(|c| c == &state.composing_buffer) {
            let exact = candidates.remove(pos);
            candidates.insert(0, exact);
        }

        let len = unicode_segmentation::UnicodeSegmentation::graphemes(
            state.composing_buffer.as_str(),
            true,
        )
        .count() as u32;
        Ok(crate::session::TypingResult {
            preedit: state.composing_buffer.clone(),
            commit_text: None,
            candidates,
            cursor_position: len,
        })
    }

    fn resolve_space_commit(
        &self,
        raw: &str,
        _state: &mut crate::session::SessionState,
        chosen_candidate: Option<&str>,
    ) -> String {
        resolve_fixed_space_commit(raw, chosen_candidate)
    }

    fn regenerate_candidates(
        &self,
        _state: &crate::session::SessionState,
        input: &str,
    ) -> Vec<String> {
        regenerate_fixed_candidates(input)
    }
}

pub(crate) struct ProbhatStrategy;

impl crate::layout::LayoutStrategy for ProbhatStrategy {
    fn process_key(
        &self,
        key: &str,
        state: &mut crate::session::SessionState,
    ) -> Result<crate::session::TypingResult, crate::error::LekhaniError> {
        let now = std::time::Instant::now();
        let key_first_char = key.chars().next();
        let is_raw_kar = key.chars().count() == 1
            && key_first_char.is_some_and(|c| ('\u{09BE}'..='\u{09CC}').contains(&c));

        let chars: Vec<char> = state.composing_buffer.chars().collect();
        let last_ch = chars.last().copied();
        let is_rapid_consonant_double_tap = if let Some((prev_ch, prev_time)) = state.last_consonant_tap {
            key.chars().count() == 1
                && key_first_char == Some(prev_ch)
                && now.duration_since(prev_time).as_millis() <= 450
        } else {
            false
        };

        let mut did_geminate_or_revert = false;

        if state.composing_buffer.is_empty() && is_raw_kar && state.smart_initial_kar_enabled {
            let is_start = state
                .surrounding_context
                .chars()
                .last()
                .map(|c| !is_bengali_consonant_or_modifier(c) && !is_bengali_vowel(c))
                .unwrap_or(true);
            let promoted = promote_kar_if_needed(key, is_start);
            state.composing_buffer.push_str(&promoted);
        } else if is_raw_kar && last_ch == Some('ঁ') {
            state.composing_buffer.pop();
            state.composing_buffer.push_str(key);
            state.composing_buffer.push('ঁ');
        } else if key == "র্"
            && last_ch.is_some_and(|c| {
                is_bengali_consonant_or_modifier(c) && c != '্' && c != 'র' && c != 'ৎ'
            })
        {
            if let Some(c) = state.composing_buffer.pop() {
                state.composing_buffer.push_str("র্");
                state.composing_buffer.push(c);
            }
        } else if state.geminate_double_tap_enabled
            && key.chars().count() == 1
            && last_ch == key_first_char
            && chars.len() >= 3
            && chars[chars.len() - 2] == '্'
            && chars[chars.len() - 3] == chars[chars.len() - 1]
        {
            state.composing_buffer.pop();
            state.composing_buffer.pop();
            state.composing_buffer.push_str(key);
            did_geminate_or_revert = true;
            state.last_consonant_tap = None;
        } else if state.geminate_double_tap_enabled
            && is_rapid_consonant_double_tap
            && key.chars().count() == 1
            && last_ch.is_some_and(|c| {
                key.starts_with(c)
                    && is_bengali_consonant_or_modifier(c)
                    && c != '্'
                    && c != 'ৎ'
                    && c != 'ড়'
                    && c != 'ঢ়'
                    && c != 'য়'
            })
            && (chars.len() < 2
                || key_first_char
                    .is_some_and(|kc| chars[chars.len() - 2] != '্' && chars[chars.len() - 2] != kc))
        {
            state.composing_buffer.push('্');
            state.composing_buffer.push_str(key);
            did_geminate_or_revert = true;
            state.last_consonant_tap = None;
        } else {
            state.composing_buffer.push_str(key);
        }

        if !did_geminate_or_revert {
            if let Some(ch) = key_first_char.filter(|_| key.chars().count() == 1) {
                if is_bengali_consonant_or_modifier(ch) && ch != '্' && ch != 'ৎ' {
                    state.last_consonant_tap = Some((ch, now));
                } else {
                    state.last_consonant_tap = None;
                }
            } else {
                state.last_consonant_tap = None;
            }
        }

        let mut candidates = Vec::new();
        if state.hasanta_conjuncts_enabled && key == "্" {
            let chars: Vec<char> = state.composing_buffer.chars().collect();
            let hasanta_pos = chars.len().wrapping_sub(1);
            if hasanta_pos > 0 {
                let base_consonant = chars[hasanta_pos - 1];
                candidates = get_conjunct_suggestions(base_consonant);
            } else if let Some(base_consonant) = state.surrounding_context.chars().last() {
                if is_bengali_consonant_or_modifier(base_consonant) {
                    candidates = get_conjunct_suggestions(base_consonant);
                }
            }
        }

        if candidates.is_empty() {
            let db = crate::session::get_core_database();
            let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 6);
            if !prefix_matches.is_empty() {
                candidates.push(state.composing_buffer.clone());
                for (word, _) in prefix_matches {
                    if !candidates.iter().any(|c| c == word) {
                        candidates.push(word.to_string());
                    }
                }
            }
        }

        if !state.surrounding_context.is_empty() && candidates.len() > 1 {
            let mut words_buf = [""; 16];
            let count =
                crate::session::get_context_words(&state.surrounding_context, &mut words_buf);
            let words = &words_buf[..count];
            let scorer = crate::session::get_context_scorer();
            let right_word = state.right_context.split_whitespace().next();
            scorer.rank_candidates_in_place_bidirectional(words, right_word, &mut candidates);
        }

        if let Some(pos) = candidates.iter().position(|c| c == &state.composing_buffer) {
            let exact = candidates.remove(pos);
            candidates.insert(0, exact);
        }

        let len = unicode_segmentation::UnicodeSegmentation::graphemes(
            state.composing_buffer.as_str(),
            true,
        )
        .count() as u32;
        Ok(crate::session::TypingResult {
            preedit: state.composing_buffer.clone(),
            commit_text: None,
            candidates,
            cursor_position: len,
        })
    }

    fn resolve_space_commit(
        &self,
        raw: &str,
        _state: &mut crate::session::SessionState,
        chosen_candidate: Option<&str>,
    ) -> String {
        resolve_fixed_space_commit(raw, chosen_candidate)
    }

    fn regenerate_candidates(
        &self,
        _state: &crate::session::SessionState,
        input: &str,
    ) -> Vec<String> {
        regenerate_fixed_candidates(input)
    }
}

pub(crate) struct NationalStrategy;

impl crate::layout::LayoutStrategy for NationalStrategy {
    fn process_key(
        &self,
        key: &str,
        state: &mut crate::session::SessionState,
    ) -> Result<crate::session::TypingResult, crate::error::LekhaniError> {
        let key_first_char_nat = key.chars().next();
        let is_raw_kar = key.chars().count() == 1
            && key_first_char_nat.is_some_and(|c| ('\u{09BE}'..='\u{09CC}').contains(&c));
        let transformed_vowel = if state.composing_buffer.ends_with('্') {
            match key {
                "া" => Some("অ"),
                "ি" => Some("ই"),
                "ী" => Some("ঈ"),
                "ু" => Some("উ"),
                "ূ" => Some("ঊ"),
                "ৃ" => Some("ঋ"),
                "ে" => Some("এ"),
                "ৈ" => Some("ঐ"),
                "ো" => Some("ও"),
                "ৌ" => Some("ঔ"),
                _ => None,
            }
        } else {
            None
        };

        if let Some(vowel) = transformed_vowel {
            state.composing_buffer.pop();
            state.composing_buffer.push_str(vowel);
        } else if state.composing_buffer.ends_with('অ') && key == "া" {
            state.composing_buffer.pop();
            state.composing_buffer.push('আ');
        } else if state.composing_buffer.is_empty() && is_raw_kar && state.smart_initial_kar_enabled
        {
            let is_start = state
                .surrounding_context
                .chars()
                .last()
                .map(|c| !is_bengali_consonant_or_modifier(c) && !is_bengali_vowel(c))
                .unwrap_or(true);
            let promoted = promote_kar_if_needed(key, is_start);
            state.composing_buffer.push_str(&promoted);
        } else if is_raw_kar && state.composing_buffer.ends_with('ঁ') {
            state.composing_buffer.pop();
            state.composing_buffer.push_str(key);
            state.composing_buffer.push('ঁ');
        } else {
            state.composing_buffer.push_str(key);
        }

        let mut candidates = Vec::new();
        if state.hasanta_conjuncts_enabled && key == "্" {
            let chars: Vec<char> = state.composing_buffer.chars().collect();
            let hasanta_pos = chars.len().wrapping_sub(1);
            if hasanta_pos > 0 {
                let base_consonant = chars[hasanta_pos - 1];
                candidates = get_conjunct_suggestions(base_consonant);
            }
        }

        if candidates.is_empty() {
            let db = crate::session::get_core_database();
            let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 4);
            if !prefix_matches.is_empty() {
                candidates.push(state.composing_buffer.clone());
                for (word, _) in prefix_matches {
                    if !candidates.iter().any(|c| c == word) {
                        candidates.push(word.to_string());
                    }
                }
            }
        }

        let nat_chars: Vec<char> = state.composing_buffer.chars().collect();
        if nat_chars.len() == 2
            && is_bengali_consonant_or_modifier(nat_chars[0])
            && nat_chars[0] != '্'
        {
            let iv_partner = match nat_chars[1] {
                'ি' => Some('ই'),
                'ো' => Some('ও'),
                'ু' => Some('উ'),
                'ে' => Some('এ'),
                'ী' => Some('ঈ'),
                'ূ' => Some('ঊ'),
                'ৈ' => Some('ঐ'),
                _ => None,
            };
            if let Some(iv) = iv_partner {
                let iv_word = format!("{}{}", nat_chars[0], iv);
                let db = crate::session::get_core_database();
                if db.trie.contains_exact(&iv_word) && !candidates.contains(&iv_word) {
                    let insert_pos = if candidates.is_empty() { 0 } else { 1 };
                    candidates.insert(insert_pos, iv_word);
                }
            }
        }

        if !state.surrounding_context.is_empty() && candidates.len() > 1 {
            let mut words_buf = [""; 16];
            let count =
                crate::session::get_context_words(&state.surrounding_context, &mut words_buf);
            let words = &words_buf[..count];
            let scorer = crate::session::get_context_scorer();
            let right_word = state.right_context.split_whitespace().next();
            scorer.rank_candidates_in_place_bidirectional(words, right_word, &mut candidates);
        }
        if let Some(pos) = candidates.iter().position(|c| c == &state.composing_buffer) {
            let exact = candidates.remove(pos);
            candidates.insert(0, exact);
        }

        let len = unicode_segmentation::UnicodeSegmentation::graphemes(
            state.composing_buffer.as_str(),
            true,
        )
        .count() as u32;
        Ok(crate::session::TypingResult {
            preedit: state.composing_buffer.clone(),
            commit_text: None,
            candidates,
            cursor_position: len,
        })
    }

    fn resolve_space_commit(
        &self,
        raw: &str,
        _state: &mut crate::session::SessionState,
        chosen_candidate: Option<&str>,
    ) -> String {
        resolve_fixed_space_commit(raw, chosen_candidate)
    }

    fn regenerate_candidates(
        &self,
        _state: &crate::session::SessionState,
        input: &str,
    ) -> Vec<String> {
        regenerate_fixed_candidates(input)
    }
}

pub(crate) struct GboardStrategy;

impl crate::layout::LayoutStrategy for GboardStrategy {
    fn process_key(
        &self,
        key: &str,
        state: &mut crate::session::SessionState,
    ) -> Result<crate::session::TypingResult, crate::error::LekhaniError> {
        let key_first_char_fix = key.chars().next();
        let is_raw_kar = key.chars().count() == 1
            && key_first_char_fix.is_some_and(|c| ('\u{09BE}'..='\u{09CC}').contains(&c));
        let chars: Vec<char> = state.composing_buffer.chars().collect();
        let last_ch = chars.last().copied();
        let last_is_kar = last_ch.is_some_and(|c| ('\u{09BE}'..='\u{09CC}').contains(&c));
        let prev_is_consonant = chars.len() >= 2
            && is_bengali_consonant_or_modifier(chars[chars.len() - 2])
            && chars[chars.len() - 2] != '্';
        let is_same_kar_double_tap =
            is_raw_kar && last_is_kar && prev_is_consonant && last_ch == key_first_char_fix;

        if is_same_kar_double_tap && state.smart_initial_kar_enabled {
            let iv = match last_ch {
                Some('ি') => Some("ই"),
                Some('ো') => Some("ও"),
                Some('ু') => Some("উ"),
                Some('ে') => Some("এ"),
                Some('ী') => Some("ঈ"),
                Some('ূ') => Some("ঊ"),
                Some('ৈ') => Some("ঐ"),
                _ => None,
            };
            if let Some(independent_vowel) = iv {
                state.composing_buffer.pop();
                state.composing_buffer.push_str(independent_vowel);
            } else {
                state.composing_buffer.push_str(key);
            }
        } else if state.composing_buffer.ends_with('অ') && key == "া" {
            state.composing_buffer.pop();
            state.composing_buffer.push('আ');
        } else if state.composing_buffer.is_empty() && is_raw_kar && state.smart_initial_kar_enabled
        {
            let is_start = state
                .surrounding_context
                .chars()
                .last()
                .map(|c| !is_bengali_consonant_or_modifier(c) && !is_bengali_vowel(c))
                .unwrap_or(true);
            let promoted = promote_kar_if_needed(key, is_start);
            state.composing_buffer.push_str(&promoted);
        } else if is_raw_kar && state.composing_buffer.ends_with('ঁ') {
            state.composing_buffer.pop();
            state.composing_buffer.push_str(key);
            state.composing_buffer.push('ঁ');
        } else if key == "র্"
            && last_ch.is_some_and(|c| {
                is_bengali_consonant_or_modifier(c) && c != '্' && c != 'র' && c != 'ৎ'
            })
        {
            if let Some(c) = state.composing_buffer.pop() {
                state.composing_buffer.push_str("র্");
                state.composing_buffer.push(c);
            }
        } else {
            state.composing_buffer.push_str(key);
        }

        let mut candidates = Vec::new();
        let db = crate::session::get_core_database();
        if key == "্" {
            let chars: Vec<char> = state.composing_buffer.chars().collect();
            let hasanta_pos = chars.len().wrapping_sub(1);
            if hasanta_pos > 0 {
                let base_consonant = chars[hasanta_pos - 1];
                candidates = get_conjunct_suggestions(base_consonant);
            } else if let Some(base_consonant) = state.surrounding_context.chars().last() {
                if is_bengali_consonant_or_modifier(base_consonant) {
                    candidates = get_conjunct_suggestions(base_consonant);
                }
            }
        }

        if candidates.is_empty() {
            let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 4);
            if !prefix_matches.is_empty() {
                candidates.push(state.composing_buffer.clone());
                for (word, _) in prefix_matches {
                    if !candidates.iter().any(|c| c == word) {
                        candidates.push(word.to_string());
                    }
                }
            }
        }

        let fix_chars: Vec<char> = state.composing_buffer.chars().collect();
        if fix_chars.len() == 2
            && is_bengali_consonant_or_modifier(fix_chars[0])
            && fix_chars[0] != '্'
        {
            let iv_partner = match fix_chars[1] {
                'ি' => Some('ই'),
                'ো' => Some('ও'),
                'ু' => Some('উ'),
                'ে' => Some('এ'),
                'ী' => Some('ঈ'),
                'ূ' => Some('ঊ'),
                'ৈ' => Some('ঐ'),
                _ => None,
            };
            if let Some(iv) = iv_partner {
                let iv_word = format!("{}{}", fix_chars[0], iv);
                let db = crate::session::get_core_database();
                if db.trie.contains_exact(&iv_word) && !candidates.contains(&iv_word) {
                    let insert_pos = if candidates.is_empty() { 0 } else { 1 };
                    candidates.insert(insert_pos, iv_word);
                }
            }
        }

        if !state.composing_buffer.ends_with('্') && state.composing_buffer.chars().count() >= 2 {
            let is_exact_word = db.trie.contains_exact(&state.composing_buffer);
            if is_exact_word || candidates.len() < 5 {
                let inflections = generate_inflectional_suffixes(&state.composing_buffer);
                for inf in inflections {
                    if candidates.len() >= 8 {
                        break;
                    }
                    if (db.trie.contains_exact(&inf) || candidates.len() < 4)
                        && !candidates.contains(&inf)
                    {
                        candidates.push(inf);
                    }
                }
            }
        }

        if candidates.len() < 5 {
            let stems = lekhani_core::phonetic::morphology::peel_all_stems(&state.composing_buffer);
            for stem in stems {
                if stem.len() >= 2 {
                    let stem_matches = db.trie.find_prefix_matches(&stem, 3);
                    for w in stem_matches {
                        if candidates.len() >= 8 {
                            break;
                        }
                        if !candidates.contains(&w) {
                            candidates.push(w);
                        }
                    }
                }
            }
        }

        if candidates.len() > 1 {
            let mut words_buf = [""; 16];
            let count =
                crate::session::get_context_words(&state.surrounding_context, &mut words_buf);
            let words = &words_buf[..count];
            let scorer = crate::session::get_context_scorer();
            let right_word = state.right_context.split_whitespace().next();
            scorer.rank_candidates_in_place_bidirectional(words, right_word, &mut candidates);
        }

        if let Some(pos) = candidates.iter().position(|c| c == &state.composing_buffer) {
            let exact = candidates.remove(pos);
            candidates.insert(0, exact);
        }

        let len = unicode_segmentation::UnicodeSegmentation::graphemes(
            state.composing_buffer.as_str(),
            true,
        )
        .count() as u32;
        Ok(crate::session::TypingResult {
            preedit: state.composing_buffer.clone(),
            commit_text: None,
            candidates,
            cursor_position: len,
        })
    }

    fn resolve_space_commit(
        &self,
        raw: &str,
        _state: &mut crate::session::SessionState,
        chosen_candidate: Option<&str>,
    ) -> String {
        resolve_fixed_space_commit(raw, chosen_candidate)
    }

    fn regenerate_candidates(
        &self,
        _state: &crate::session::SessionState,
        input: &str,
    ) -> Vec<String> {
        regenerate_fixed_candidates(input)
    }
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
