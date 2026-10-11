//! Avro Phonetic Transliteration Engine powered by `lekhani-parser` and `lekhani-ai`
//!
//! Provides deterministic Roman-to-Bengali transliteration according to
//! classic Avro Phonetic muscle memory standards using the upstream
//! zero-allocation L1 Trie grammar compiler from `lekhani-parser`.

use edit_distance::edit_distance;
use hashbrown::HashMap;
use std::sync::OnceLock;

static PARSER: OnceLock<lekhani_parser::LekhaniParser> = OnceLock::new();

pub fn get_avro_parser() -> &'static lekhani_parser::LekhaniParser {
    PARSER.get_or_init(lekhani_parser::default_avro_parser)
}

/// Prioritizes candidate matching static common words or data-driven phonetic overrides.
/// Places the preferred candidate at position 0 without reallocating when possible.
pub fn prioritize_common_or_override_candidate(input: &str, candidates: &mut Vec<String>) {
    if candidates.len() <= 1 {
        return;
    }
    // If candidates already has a concordant verb ranked by Anvaya, preserve it at position 0
    if let Some(first_cand) = candidates.first() {
        let first_nfc = crate::probaho::nfc_normalize(first_cand);
        let lower = input.to_lowercase();
        let is_concordant = [
            lekhani_ai::HonorificRegister::FormalHonorific,
            lekhani_ai::HonorificRegister::Intimate,
            lekhani_ai::HonorificRegister::Familiar,
        ]
        .iter()
        .any(|&r| {
            lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(input, r)
                .or_else(|| lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(&lower, r))
                .map(|v| crate::probaho::nfc_normalize(v) == first_nfc)
                .unwrap_or(false)
        });
        if is_concordant {
            return;
        }
    }

    // 0. Check all-caps acronyms & English code-switching passthrough (Slot 0 Latin)
    let lower = input.to_lowercase();
    let is_all_caps = input.len() >= 2
        && input.chars().any(|c| c.is_ascii_alphabetic())
        && input
            .chars()
            .all(|c| c.is_ascii_uppercase() || !c.is_alphabetic());
    let is_recognized_english = crate::english::is_recognized_english_word(input);
    let db = crate::session::get_core_database();
    let def_conv = get_avro_parser().convert(input);
    let def_lower_conv = get_avro_parser().convert(&lower);
    let is_bengali_dict_word = (!def_conv.is_empty() && db.is_exact_dictionary_word(&def_conv))
        || (!def_lower_conv.is_empty() && db.is_exact_dictionary_word(&def_lower_conv));

    let has_established_loanword = is_bengali_dict_word
        || db.lookup_override(input).is_some()
        || db.lookup_override(&lower).is_some()
        || lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&lower).is_some();

    if (is_all_caps || is_recognized_english) && !has_established_loanword {
        if let Some(pos) = candidates.iter().position(|c| c.eq_ignore_ascii_case(input)) {
            let cand = candidates.remove(pos);
            candidates.insert(0, cand);
            return;
        }
    }

    // 1. Check dynamic/bundled supervised phonetic overrides (phonetic_overrides.bin / json)
    let is_title_case = input.len() >= 3
        && input.chars().next().is_some_and(|c| c.is_uppercase())
        && input
            .chars()
            .skip(1)
            .all(|c| c.is_lowercase() || !c.is_alphabetic());
    let collapsed_opt = collapse_elongation(&lower);
    let override_opt = db.lookup_override(input)
        .or_else(|| {
            if is_title_case {
                db.lookup_override(&lower)
            } else {
                None
            }
        })
        .or_else(|| {
            collapsed_opt.as_deref().and_then(|c| db.lookup_override(c))
        });
    let mut inserted_idx = 0;
    if let Some(overrides) = override_opt {
        // Multi-homophone list (e.g. "pora" -> ["পরা", "পড়া"], "ki" -> ["কি", "কী"]):
        // If the top candidate is ALREADY in the overrides list (e.g. ranked by context LM), keep it at position 0!
        let has_top_in_overrides = if overrides.len() > 1 {
            if let Some(first_cand) = candidates.first() {
                let first_nfc = crate::probaho::nfc_normalize(first_cand);
                overrides.iter().any(|(w, _)| crate::probaho::nfc_normalize(w) == first_nfc)
            } else {
                false
            }
        } else {
            false
        };

        if has_top_in_overrides {
            return;
        } else {
            for (override_word, score) in overrides {
                let target_nfc = crate::probaho::nfc_normalize(override_word);
                if let Some(pos) = candidates.iter().position(|c| crate::probaho::nfc_normalize(c) == target_nfc) {
                    let cand = candidates.remove(pos);
                    candidates.insert(inserted_idx, cand);
                    inserted_idx += 1;
                } else if score >= 0.70 {
                    candidates.insert(inserted_idx, target_nfc.clone());
                    inserted_idx += 1;
                } else if !candidates.iter().any(|c| crate::probaho::nfc_normalize(c) == target_nfc) {
                    if candidates.len() >= 12 {
                        candidates.pop();
                    }
                    candidates.push(target_nfc);
                }
            }
            return;
        }
    }

    // 3. Check phonetic variant equivalences (geminates, S-class, Sanskrit initial vowels, compound Reph)
    let equivs = get_phonetic_equivalence_candidates(input);
    for eq_word in equivs {
        if let Some(pos) = candidates.iter().position(|c| c == &eq_word) {
            if inserted_idx == 0 {
                let cand = candidates.remove(pos);
                candidates.insert(inserted_idx, cand);
                inserted_idx += 1;
            }
        } else if inserted_idx > 0 || candidates.is_empty() {
            candidates.insert(inserted_idx, eq_word);
            inserted_idx += 1;
        } else {
            candidates.push(eq_word);
        }
        if candidates.len() > 12 {
            candidates.pop();
        }
    }

    // 4. Clitic-ও De-greediness:
    // If the top candidate ends with 'ও' (e.g. 'সম্পর্কও', 'কতও'), check if removing 'ও' gives an exact dictionary word.
    // If the input represents the natural inherent vowel rather than an explicit clitic, prioritize the base word!
    if let Some(top) = candidates.first() {
        if top.ends_with('ও') && top.chars().count() >= 3 && !input.contains("-o") {
            let base = &top[..top.len() - 'ও'.len_utf8()];
            let db = crate::session::get_core_database();
            if db.trie.contains_exact(base) || db.is_exact_dictionary_word(base) {
                let base_str = base.to_string();
                if let Some(pos) = candidates.iter().position(|c| c == &base_str) {
                    candidates.remove(pos);
                }
                candidates.insert(0, base_str);
            }
        }
    }
}

pub fn get_phonetic_equivalence_candidates(input: &str) -> Vec<String> {
    let lower = input.to_lowercase();
    let mut equiv_words = Vec::new();
    let db = crate::session::get_core_database();
    let mut variant_keys = Vec::new();

    // 1. Ja-phola geminate contraction: 'kky' -> 'ky' / 'kyo', 'tty' -> 'ty' / 'tyo', etc.
    // e.g. 'bakkya' -> 'bakyo' -> বাক্য
    if lower.contains("kky")
        || lower.contains("tty")
        || lower.contains("bby")
        || lower.contains("ppy")
        || lower.contains("mmy")
        || lower.contains("lly")
        || lower.contains("ddy")
        || lower.contains("zzy")
        || lower.contains("jgy")
    {
        let contracted = lower
            .replace("kky", "ky")
            .replace("tty", "ty")
            .replace("bby", "by")
            .replace("ppy", "py")
            .replace("mmy", "my")
            .replace("lly", "ly")
            .replace("ddy", "dy")
            .replace("zzy", "zy")
            .replace("jgy", "gy");
        if contracted.ends_with('a') {
            variant_keys.push(format!("{}o", &contracted[..contracted.len() - 1]));
        }
        variant_keys.push(contracted);
    }

    // 2. S-class sibilant swap:
    // Initial sibilants: 'shomporko' -> 'somporko', 'shomossa' -> 'somossa'
    if lower.starts_with("sh") {
        variant_keys.push(lower.replacen("sh", "s", 1));
    } else if lower.starts_with('s') {
        variant_keys.push(lower.replacen('s', "sh", 1));
    }
    // Intervocalic / pre-vocalic sibilant swap (e.g. 'oitihashik' -> 'oitihasik'):
    // Preserves conjunct clusters like 'shk' (আবিষ্কার), 'sht' (বৃষ্টি), 'shp' (পুষ্প)
    let vowels = "aeiouy";
    if let Some(idx) = lower.find("sh") {
        if idx + 2 < lower.len() && vowels.contains(lower.as_bytes()[idx + 2] as char) {
            let mut v = lower.clone();
            v.replace_range(idx..idx + 2, "s");
            variant_keys.push(v);
        }
    }

    // 3. Dharma / Karma / Antor / Ontor phonetic vowel harmony
    if lower.starts_with("dhar") {
        variant_keys.push(lower.replacen("dhar", "dhor", 1));
    } else if lower.starts_with("kar") {
        variant_keys.push(lower.replacen("kar", "kor", 1));
    }
    if lower.starts_with("ontor") {
        variant_keys.push(lower.replacen("ontor", "antor", 1));
    }

    // 4. Reph doubling (single 'r' before consonant -> 'rr')
    // In Avro grammar, Reph requires 'rr' before a consonant (e.g. 'antorrjatik' -> আন্তর্জাতিক,
    // 'ontorrbhukto' -> অন্তর্ভুক্ত, 'dhorrmo' -> ধর্ম, 'somporrko' -> সম্পর্ক).
    // Touchscreen typists naturally type single 'r'.
    let mut reph_keys = Vec::new();
    let consonants = "bcdgjkmnpstvz";
    let base_keys: Vec<String> = std::iter::once(lower.clone())
        .chain(variant_keys.iter().cloned())
        .collect();
    for base in &base_keys {
        let bytes = base.as_bytes();
        for i in 0..bytes.len().saturating_sub(1) {
            if bytes[i] == b'r'
                && (i == 0 || bytes[i - 1] != b'r')
                && (i + 1 < bytes.len() && bytes[i + 1] != b'r')
            {
                let next = bytes[i + 1] as char;
                if consonants.contains(next) {
                    let mut with_reph = String::with_capacity(base.len() + 1);
                    with_reph.push_str(&base[..=i]);
                    with_reph.push('r');
                    with_reph.push_str(&base[i + 1..]);
                    reph_keys.push(with_reph);
                }
            }
        }
    }
    variant_keys.extend(reph_keys);

    // 5. Initial 'j' -> 'z' mapping for Ontostho-Ja (য)
    if let Some(stripped) = lower.strip_prefix('j') {
        variant_keys.push(format!("z{}", stripped));
    }

    // 6. Flap Rhotic 'r' -> 'R' (ড়) auto-disambiguation
    // Only generate if the term is NOT already a common high-frequency word or exact dictionary word
    let is_explicit_common_base = db.lookup_override(input).is_some() || db.lookup_override(&lower).is_some();
    let parser_base = get_avro_parser().convert(&lower);
    let is_exact_dict_base = !parser_base.is_empty() && (db.is_exact_dictionary_word(&parser_base) || is_explicit_common_base);
    if !is_exact_dict_base && lower.contains('r') {
        let r_indices: Vec<usize> = lower
            .char_indices()
            .filter_map(|(idx, ch)| if ch == 'r' { Some(idx) } else { None })
            .collect();
        for &idx in &r_indices {
            let mut with_cap_r = lower.clone();
            with_cap_r.replace_range(idx..idx + 1, "R");
            variant_keys.push(with_cap_r);
        }
    }

    // 6b. Diphthong 'oi' -> 'OI' (ঐ / ৈ) auto-disambiguation
    if lower.contains("oi") {
        variant_keys.push(lower.replace("oi", "OI"));
        if lower.contains("sh") {
            variant_keys.push(lower.replace("oi", "OI").replace("sh", "s"));
        }
    }

    // 6c. Sanskrit ya-phola on retroflex sibilant cluster: '-shto' -> '-shtyo' / '-sHtyo' (e.g. boishishto -> বৈশিষ্ট্য)
    if let Some(base_shto) = lower.strip_suffix("shto") {
        variant_keys.push(format!("{}sHtyo", base_shto));
        variant_keys.push(format!("{}shtyo", base_shto));
        if base_shto.contains("oi") {
            variant_keys.push(format!("{}sHtyo", base_shto.replace("oi", "OI")));
        }
    }

    // 6d. Ri-kar vocalic onset (consonant + 'ri' -> 'rri', e.g. 'kri' -> 'krri' -> কৃ, 'krittrim' -> 'krritrim' -> কৃত্রিম)
    if lower.contains("ri") {
        let with_rri = lower.replace("ri", "rri");
        variant_keys.push(with_rri.clone());
        if lower.contains("ttri") {
            variant_keys.push(lower.replace("ttri", "tri"));
            variant_keys.push(with_rri.replace("ttri", "tri"));
        }
    }

    // 6e. Va-phola on dental aspirate: 'dhoni' -> 'dhwoni' (ধ্বনি)
    if lower.contains("dhoni") {
        variant_keys.push(lower.replace("dhoni", "dhwoni"));
    }

    // 6f. Tatshama compound vowel harmony: 'bahan' -> 'bohon' (পরিবহনব্যবস্থা)
    if lower.contains("bahan") {
        variant_keys.push(lower.replace("bahan", "bohon"));
    }

    // 7. Retroflex 'd' -> 'D' (ড) auto-disambiguation for dialect suffix '-da' or specific retroflex nouns
    if lower.ends_with("da") || lower == "dal" || lower == "daktar" || lower == "dab" {
        let mut with_cap_d = lower.clone();
        if with_cap_d.starts_with('d') {
            with_cap_d.replace_range(0..1, "D");
            variant_keys.push(with_cap_d.clone());
        }
        if with_cap_d.ends_with("da") {
            let len = with_cap_d.len();
            with_cap_d.replace_range(len - 2..len - 1, "D");
            variant_keys.push(with_cap_d);
        }
    }

    // 8. Colloquial dialect verb inflection breaker: 'nch' -> 'n`ch' (e.g. 'hunchos' -> 'hun`chos' -> 'হুনছস')
    if lower.contains("nch") {
        variant_keys.push(lower.replace("nch", "n`ch"));
    }

    for k in variant_keys {
        if let Some(ac) = db.autocorrect.get(&k) {
            if !equiv_words.contains(ac) {
                equiv_words.push(ac.clone());
            }
        }
        if let Some(ov) = db.lookup_override(&k) {
            for (w, _) in ov {
                let w_str = w.to_string();
                if !equiv_words.contains(&w_str) {
                    equiv_words.push(w_str);
                }
            }
        }
        let parsed = get_avro_parser().convert(&k);
        if !parsed.is_empty()
            && (db.trie.contains_exact(&parsed) || db.is_exact_dictionary_word(&parsed) || db.lookup_override(&k).is_some())
            && !equiv_words.contains(&parsed)
        {
            equiv_words.push(parsed);
        }
    }

    equiv_words
}

/// Determines if two Bengali words share a compatible phonetic root consonant or vowel,
/// distinguishing authentic homophones (e.g. করি vs কড়ি, সকাল vs শকাল) from
/// fat-finger QWERTY key substitutions (e.g. বান [typed 'b'] vs গান [typo 'g']).
pub fn are_phonetically_compatible(w1: &str, w2: &str) -> bool {
    let c1 = match w1.chars().next() {
        Some(c) => c,
        None => return true,
    };
    let c2 = match w2.chars().next() {
        Some(c) => c,
        None => return true,
    };
    if c1 == c2 {
        return true;
    }
    let is_sibilant = |c| c == 'স' || c == 'শ' || c == 'ষ';
    if is_sibilant(c1) && is_sibilant(c2) {
        return true;
    }
    let is_rhotic = |c| c == 'র' || c == 'ড়' || c == 'ঢ়';
    if is_rhotic(c1) && is_rhotic(c2) {
        return true;
    }
    let is_nasal = |c| c == 'ন' || c == 'ণ' || c == 'ঙ' || c == 'ঞ' || c == 'ং';
    if is_nasal(c1) && is_nasal(c2) {
        return true;
    }
    let is_ja_ya = |c| c == 'জ' || c == 'য' || c == 'য়';
    if is_ja_ya(c1) && is_ja_ya(c2) {
        return true;
    }
    let is_ta = |c| c == 'ত' || c == 'ৎ';
    if is_ta(c1) && is_ta(c2) {
        return true;
    }
    let is_vowel = |c| "অআইঈউঊঋএঐওঔািীুূৃেৈোৌ".contains(c);
    if is_vowel(c1) && is_vowel(c2) {
        return true;
    }
    false
}

/// Looks up offline supervised phonetic override candidates for a key.
pub fn get_override_words(key: &str) -> Option<Vec<String>> {
    let db = crate::session::get_core_database();
    db.lookup_override(key).map(|ov| {
        ov.into_iter().map(|(w, _)| w.to_string()).collect()
    })
}

pub fn has_override_word(key: &str) -> bool {
    let db = crate::session::get_core_database();
    db.lookup_override(key).is_some()
}

/// Collapses repeated characters (3+ identical consecutive characters) in chat typing.
/// E.g. "haaa" -> "ha", "naaa" -> "na", "oneeeek" -> "onek", "plzzz" -> "plz".
pub fn collapse_elongation(input: &str) -> Option<String> {
    let chars: Vec<char> = input.chars().collect();
    if chars.len() < 3 {
        return None;
    }

    let mut has_elongation = false;
    let mut collapsed = String::with_capacity(chars.len());
    let mut i = 0;
    while i < chars.len() {
        let ch = chars[i];
        let mut count = 1;
        while i + count < chars.len() && chars[i + count].eq_ignore_ascii_case(&ch) {
            count += 1;
        }

        if count >= 3 {
            has_elongation = true;
            collapsed.push(ch);
        } else {
            for j in 0..count {
                collapsed.push(chars[i + j]);
            }
        }
        i += count;
    }

    if has_elongation {
        Some(collapsed)
    } else {
        None
    }
}

/// Converts ASCII digits ('0'..='9') to Bengali digits ('০'..='৯')
pub fn ascii_digits_to_bengali(digits: &str) -> String {
    digits
        .chars()
        .map(|c| match c {
            '0' => '০',
            '1' => '১',
            '2' => '২',
            '3' => '৩',
            '4' => '৪',
            '5' => '৫',
            '6' => '৬',
            '7' => '৭',
            '8' => '৮',
            '9' => '৯',
            _ => c,
        })
        .collect()
}

/// Detects social media mentions (@user) and hashtags (#tag) to preserve verbatim ASCII handles
/// while still providing the phonetic transliteration as a secondary candidate.
pub fn resolve_social_mention_or_hashtag(input: &str) -> Option<(String, Vec<String>)> {
    if !input.starts_with('@') && !input.starts_with('#') {
        return None;
    }
    let prefix = &input[..1];
    let remainder = &input[1..];
    if remainder.is_empty() {
        return Some((input.to_string(), vec![input.to_string()]));
    }
    let (b_primary, b_cands) = transliterate_avro(remainder);
    let mut candidates = vec![input.to_string()];
    if !b_primary.is_empty() && b_primary != remainder {
        candidates.push(format!("{}{}", prefix, b_primary));
    }
    for c in b_cands {
        if c != remainder {
            let cand = format!("{}{}", prefix, c);
            if !candidates.contains(&cand) {
                candidates.push(cand);
            }
        }
    }
    Some((input.to_string(), candidates))
}

/// Resolves digit prefixes with units, quantifiers, currency, and ordinals
/// e.g. "10ta" -> "১০টা", "500tk" -> "৫০০ টাকা", "1st" -> "১ম"
pub fn resolve_digit_unit_candidates(input: &str) -> Option<(String, Vec<String>)> {
    let digit_count = input.chars().take_while(|c| c.is_ascii_digit()).count();
    if digit_count == 0 || digit_count == input.chars().count() {
        return None;
    }

    let (digits, suffix) = input.split_at(digit_count);
    let lower_suffix = suffix.to_lowercase();
    let b_digits = ascii_digits_to_bengali(digits);

    let (primary, candidates) = match lower_suffix.as_str() {
        "ta" => {
            let p = format!("{}টা", b_digits);
            let cands = vec![
                p.clone(),
                input.to_string(),
                format!("{}তা", b_digits),
                digits.to_string(),
            ];
            (p, cands)
        }
        "ti" => {
            let p = format!("{}টি", b_digits);
            let cands = vec![
                p.clone(),
                input.to_string(),
                format!("{}তি", b_digits),
                digits.to_string(),
            ];
            (p, cands)
        }
        "tk" => {
            let p = format!("{} টাকা", b_digits);
            let cands = vec![
                p.clone(),
                format!("৳{}", b_digits),
                input.to_string(),
                format!("{}tk", b_digits),
                b_digits.clone(),
            ];
            (p, cands)
        }
        "taka" => {
            let p = format!("{} টাকা", b_digits);
            let cands = vec![
                p.clone(),
                format!("৳{}", b_digits),
                input.to_string(),
                b_digits.clone(),
            ];
            (p, cands)
        }
        "jon" => {
            let p = format!("{}জন", b_digits);
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        "bar" => {
            let p = format!("{}বার", b_digits);
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        "din" => {
            let p = format!("{}দিন", b_digits);
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        "mas" => {
            let p = format!("{}মাস", b_digits);
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        "bochor" | "bosor" => {
            let p = format!("{}বছর", b_digits);
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        "kg" => {
            let p = format!("{} কেজি", b_digits);
            let cands = vec![
                p.clone(),
                input.to_string(),
                format!("{}kg", b_digits),
                b_digits.clone(),
            ];
            (p, cands)
        }
        "gm" | "gram" => {
            let p = format!("{} গ্রাম", b_digits);
            let cands = vec![
                p.clone(),
                input.to_string(),
                format!("{}gm", b_digits),
                b_digits.clone(),
            ];
            (p, cands)
        }
        "km" => {
            let p = format!("{} কিমি", b_digits);
            let cands = vec![
                p.clone(),
                input.to_string(),
                format!("{}km", b_digits),
                b_digits.clone(),
            ];
            (p, cands)
        }
        "mb" => {
            let p = format!("{} এমবি", b_digits);
            let cands = vec![
                p.clone(),
                format!("{}MB", digits),
                input.to_string(),
            ];
            (p, cands)
        }
        "gb" => {
            let p = format!("{} জিবি", b_digits);
            let cands = vec![
                p.clone(),
                format!("{}GB", digits),
                input.to_string(),
            ];
            (p, cands)
        }
        // English ordinals to Bengali ordinals
        "st" => {
            let p = if digits == "1" {
                "১ম".to_string()
            } else {
                format!("{}তম", b_digits)
            };
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        "nd" => {
            let p = if digits == "2" {
                "২য়".to_string()
            } else {
                format!("{}তম", b_digits)
            };
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        "rd" => {
            let p = if digits == "3" {
                "৩য়".to_string()
            } else {
                format!("{}তম", b_digits)
            };
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        "th" => {
            let p = match digits {
                "4" => "৪র্থ".to_string(),
                "5" => "৫ম".to_string(),
                "6" => "৬ষ্ঠ".to_string(),
                "7" => "৭ম".to_string(),
                "8" => "৮ম".to_string(),
                "9" => "৯ম".to_string(),
                "10" => "১০ম".to_string(),
                _ => format!("{}তম", b_digits),
            };
            let cands = vec![p.clone(), input.to_string(), b_digits.clone()];
            (p, cands)
        }
        _ => return None,
    };

    Some((primary, candidates))
}

/// Resolves bilingual hyphenated enclitics attached to Latin words (e.g. "Facebook-e" -> "Facebook-এ")
pub fn resolve_bilingual_hyphen_clitic(input: &str) -> Option<(String, Vec<String>)> {
    if !input.contains('-') || input.starts_with('-') || input.ends_with('-') {
        return None;
    }

    if has_override_word(input)
        || has_override_word(&input.to_lowercase())
    {
        return None;
    }

    let dash_idx = input.rfind('-')?;
    let (base, suffix_with_dash) = input.split_at(dash_idx);
    let suffix = &suffix_with_dash[1..];

    if base.is_empty()
        || suffix.is_empty()
        || !base.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
    {
        return None;
    }

    let lower_suffix = suffix.to_lowercase();

    // If suffix itself is a recognized English word, the whole token is a hyphenated English compound (e.g. Content-Type, cross-platform, inference-time)
    if crate::english::is_recognized_english_word(suffix) {
        return Some((input.to_string(), vec![input.to_string()]));
    }

    let b_clitic = match lower_suffix.as_str() {
        "e" => "-এ",
        "te" => "-তে",
        "r" => "-র",
        "er" => "-এর",
        "ta" => "-টা",
        "ti" => "-টি",
        "ke" => "-কে",
        "gulo" => "-গুলো",
        "gulor" => "-গুলোর",
        "der" => "-দের",
        "o" => "-ও",
        "i" => "-ই",
        "bhittik" => "-ভিত্তিক",
        "shongkranto" => "-সংক্রান্ত",
        _ => {
            let (base_bn, _) = transliterate_avro(base);
            let parser = get_avro_parser();
            let is_base_latin = base.contains('-')
                || base.chars().any(|c| c.is_ascii_uppercase())
                || crate::english::is_recognized_english_word(base);
            let effective_base_bn = if is_base_latin {
                base.to_string()
            } else if !base_bn.is_empty() && base_bn != base {
                base_bn
            } else {
                parser.convert(base)
            };
            if !effective_base_bn.is_empty() {
                let suffix_conv = parser.convert(suffix);
                let in_flight_primary = format!("{}-{}", effective_base_bn, suffix_conv);
                let in_flight_cands = vec![in_flight_primary.clone(), input.to_string()];
                return Some((in_flight_primary, in_flight_cands));
            }
            return None;
        }
    };

    let primary = format!("{}{}", base, b_clitic);
    let mut candidates = vec![primary.clone(), input.to_string()];

    let parser = get_avro_parser();
    let base_conv = parser.convert(base);
    let (base_bn, _) = transliterate_avro(base);
    let effective_base_bn = if !base_bn.is_empty() && base_bn != base {
        base_bn
    } else if !base_conv.is_empty() && base_conv != base {
        base_conv
    } else {
        String::new()
    };

    if !effective_base_bn.is_empty() {
        let hyphen_cand = format!("{}{}", effective_base_bn, b_clitic);
        if !candidates.contains(&hyphen_cand) {
            candidates.push(hyphen_cand);
        }

        let unified_clitic = match lower_suffix.as_str() {
            "e" => {
                if effective_base_bn.ends_with('া')
                    || effective_base_bn.ends_with('ি')
                    || effective_base_bn.ends_with('ী')
                    || effective_base_bn.ends_with('ু')
                    || effective_base_bn.ends_with('ূ')
                    || effective_base_bn.ends_with('ে')
                    || effective_base_bn.ends_with('ো')
                {
                    "য়ে"
                } else {
                    "ে"
                }
            }
            "er" => {
                if effective_base_bn.ends_with('া')
                    || effective_base_bn.ends_with('ি')
                    || effective_base_bn.ends_with('ী')
                    || effective_base_bn.ends_with('ু')
                    || effective_base_bn.ends_with('ূ')
                    || effective_base_bn.ends_with('ে')
                    || effective_base_bn.ends_with('ো')
                {
                    "য়ের"
                } else {
                    "ের"
                }
            }
            "te" => "তে",
            "ta" => "টা",
            "ti" => "টি",
            "ke" => "কে",
            "gulo" => "গুলো",
            "i" => "ই",
            "o" => "ও",
            _ => "",
        };

        if !unified_clitic.is_empty() {
            let unified_cand = format!("{}{}", effective_base_bn, unified_clitic);
            if !candidates.contains(&unified_cand) {
                candidates.push(unified_cand.clone());
            }

            let is_romanized_bengali_compound = [
                "varsity", "ma", "ghotona", "baba", "bhai", "bon", "bari", "gari", "rasta",
                "kotha", "aj",
            ]
            .contains(&base.to_lowercase().as_str())
                && !base.chars().next().is_some_and(|c| c.is_uppercase());

            if is_romanized_bengali_compound {
                candidates.retain(|c| c != &unified_cand);
                candidates.insert(0, unified_cand.clone());
                return Some((unified_cand, candidates));
            } else {
                return Some((primary, candidates));
            }
        }
    }

    Some((primary, candidates))
}

/// Generalized morphological affixes & compounding engine.
/// Dynamically resolves Tatshama academic suffixes, Natva Vidhana sound mutations,
/// and systematic colloquial/dialectal verb conjugations without hardcoded word lists.
pub fn resolve_generative_morphology(input: &str) -> Option<(String, Vec<String>)> {
    if input.is_empty() || !input.is_ascii() {
        return None;
    }
    let lower = input.to_lowercase();

    // ── 1. Academic Tatshama Suffixes & Compounding ──────────────────────────
    // a. Suffix -tattik -> -তাত্ত্বিক (e.g. dhoni-tattik -> ধ্বনিতাত্ত্বিক, rup-tattik -> রূপতাত্ত্বিক, bakko-tattik -> বাক্যতাত্ত্বিক)
    if let Some(stem) = lower.strip_suffix("tattik") {
        if !stem.is_empty() {
            let (stem_bn, _) = transliterate_avro(stem);
            let word = format!("{}তাত্ত্বিক", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }

    // b. Suffix -babostha / -babosthar -> -ব্যবস্থা / -ব্যবস্থার (e.g. jogajogbabosthar -> যোগাযোগব্যবস্থার, nishkashonbabostha -> নিষ্কাশনব্যবস্থা)
    if let Some(stem) = lower.strip_suffix("babosthar") {
        if !stem.is_empty() {
            let (stem_bn, _) = transliterate_avro(stem);
            let word = format!("{}ব্যবস্থার", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }
    if let Some(stem) = lower.strip_suffix("babostha") {
        if !stem.is_empty() {
            let (stem_bn, _) = transliterate_avro(stem);
            let word = format!("{}ব্যবস্থা", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }

    // c. Suffix -banjon / -banjoner -> -ব্যঞ্জন / -ব্যঞ্জনের (e.g. juktobanjoner -> যুক্তব্যঞ্জনের)
    if let Some(stem) = lower.strip_suffix("banjoner") {
        if !stem.is_empty() {
            let (stem_bn, _) = transliterate_avro(stem);
            let word = format!("{}ব্যঞ্জনের", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }
    if let Some(stem) = lower.strip_suffix("banjon") {
        if !stem.is_empty() {
            let (stem_bn, _) = transliterate_avro(stem);
            let word = format!("{}ব্যঞ্জন", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }

    // d. Suffix -rayon / -ayon (Natva Vidhana rule): Murdhanya ণ after r/sh, dental ন otherwise
    if let Some(stem) = lower.strip_suffix("rayon") {
        if !stem.is_empty() {
            let (stem_bn, _) = transliterate_avro(stem);
            let word = format!("{}রায়ণ", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }
    if let Some(stem) = lower.strip_suffix("ayon") {
        if !stem.is_empty() {
            let (stem_bn, _) = transliterate_avro(stem);
            let word = format!("{}ায়ন", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }

    // e. Suffix -shishto (e.g. bi-shishto -> বিশিষ্ট, pro-shishto -> প্রবিশিষ্ট)
    if let Some(stem) = lower.strip_suffix("shishto") {
        if !stem.is_empty() {
            let (stem_bn, _) = transliterate_avro(stem);
            let word = format!("{}বিশিষ্ট", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }

    // f. Initial diphthong oi- -> ঐ- (e.g. oitihashik -> ঐতিহাসিক, oikyo -> ঐক্য)
    if lower.starts_with("oi") && lower.len() >= 4 {
        let remainder = &lower[2..];
        let (rem_bn, _) = transliterate_avro(remainder);
        let word = format!("ঐ{}", rem_bn);
        return Some((word.clone(), vec![word]));
    }

    // g. Anusvara compounds with -shongsthan / -shonghar
    if let Some(stem) = lower.strip_suffix("shongsthan") {
        let (stem_bn, _) = transliterate_avro(stem);
        let word = format!("{}সংস্থান", stem_bn);
        return Some((word.clone(), vec![word]));
    }
    if let Some(stem) = lower.strip_suffix("shonghar") {
        let (stem_bn, _) = transliterate_avro(stem);
        let word = format!("{}সংহার", stem_bn);
        return Some((word.clone(), vec![word]));
    }

    // h. Tatshama Murdhanya & conjunct morphology rules:
    // Suffix -shleshon -> -শ্লেষণ (e.g. bi-shleshon -> বিশ্লেষণ, shong-shleshon -> সংশ্লেষণ)
    if let Some(stem) = lower.strip_suffix("shleshon") {
        let (stem_bn, _) = transliterate_avro(stem);
        let word = format!("{}শ্লেষণ", stem_bn);
        return Some((word.clone(), vec![word]));
    }
    // Suffix -bekkhan -> -বেক্ষণ (e.g. porjo-bekkhan -> পর্যবেক্ষণ, niri-bekkhan -> নিরীক্ষণ)
    if let Some(stem) = lower.strip_suffix("bekkhan") {
        let (stem_bn, _) = transliterate_avro(stem);
        let word = format!("{}বেক্ষণ", stem_bn);
        return Some((word.clone(), vec![word]));
    }
    // Suffix -koron -> -করণ (e.g. shoroli-koron -> সরলীকরণ, nogori-koron -> নগরীকরণ)
    if let Some(stem) = lower.strip_suffix("koron") {
        let (mut stem_bn, _) = transliterate_avro(stem);
        if stem_bn.ends_with('ই') || stem_bn.ends_with('ি') {
            stem_bn.pop();
            stem_bn.push('ী');
        }
        let word = format!("{}করণ", stem_bn);
        return Some((word.clone(), vec![word]));
    }
    // Prefix shwor- -> স্বর- (e.g. shwor-dhoni -> স্বরধ্বনি, shwor-borno -> স্বরবর্ণ)
    if let Some(rest) = lower.strip_prefix("shwor") {
        let (rest_bn, _) = transliterate_avro(rest);
        let word = format!("স্বর{}", rest_bn);
        return Some((word.clone(), vec![word]));
    }
    // Loanword velar onset: c + [aou] -> k (e.g. computar -> কম্পিউটার)
    if lower.starts_with("co") || lower.starts_with("ca") {
        let mut k_form = lower.clone();
        k_form.replace_range(..1, "k");
        if let Some((bn_loan, _)) = lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&lower)
            .or_else(|| lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&k_form))
        {
            return Some((bn_loan.to_string(), vec![bn_loan.to_string()]));
        }
    }
    // Inherent vocalic retroflexion: -arshon -> -র্শন (e.g. darshon -> দর্শন, sparshon -> স্পর্শন)
    if let Some(stem) = lower.strip_suffix("arshon") {
        let (stem_bn, _) = transliterate_avro(stem);
        let word = format!("{}র্শন", stem_bn);
        return Some((word.clone(), vec![word]));
    }

    // ── 2. Colloquial & Regional Dialect Verb Morphology ─────────────────────
    // a. Continuous progressive aspect: -tachi -> -তাছি, -tasi -> -তাসি (e.g. ditachi, jaitachi, kortachi, khaitasi)
    if let Some(stem) = lower.strip_suffix("tachi") {
        let effective = if stem.starts_with('j') { stem.replacen('j', "z", 1) } else { stem.to_string() };
        let stem_bn = get_avro_parser().convert(&effective);
        let word = format!("{}তাছি", stem_bn);
        return Some((word.clone(), vec![word]));
    }
    if let Some(stem) = lower.strip_suffix("tasi") {
        let effective = if stem.starts_with('j') { stem.replacen('j', "z", 1) } else { stem.to_string() };
        let stem_bn = get_avro_parser().convert(&effective);
        let word = format!("{}তাসি", stem_bn);
        return Some((word.clone(), vec![word]));
    }

    // b. 1st-person dialect future: -um -> -ুম (e.g. bos-um -> বসুম, kor-um -> করুম)
    if let Some(stem) = lower.strip_suffix("um") {
        if !stem.is_empty() && stem.chars().all(|c| c.is_ascii_alphabetic()) {
            let stem_bn = get_avro_parser().convert(stem);
            let word = format!("{}ুম", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }
    // 1st-person dialect future: -mu -> -মু (e.g. ja-mu -> যামু, kha-mu -> খামু)
    if let Some(stem) = lower.strip_suffix("mu") {
        if !stem.is_empty() && stem.chars().all(|c| c.is_ascii_alphabetic()) {
            let effective = if stem.starts_with('j') { stem.replacen('j', "z", 1) } else { stem.to_string() };
            let stem_bn = get_avro_parser().convert(&effective);
            let word = format!("{}মু", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }

    // c. 2nd-person dialect familiar: -os -> -স (e.g. k-os -> কস, dhor-os -> ধরস, dich-os -> দিছস)
    if let Some(stem) = lower.strip_suffix("os") {
        if !stem.is_empty() && stem.chars().all(|c| c.is_ascii_alphabetic()) {
            let stem_bn = get_avro_parser().convert(stem);
            let word = format!("{}স", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }

    // d. Dialect future/infinitive: -ibo -> -হইব / -হইবো (e.g. ho-ibo -> হইব, kor-ibo -> করিব)
    if let Some(stem) = lower.strip_suffix("ibo") {
        let (stem_bn, _) = transliterate_avro(stem);
        let w1 = format!("{}ইব", stem_bn);
        let w2 = format!("{}ইবো", stem_bn);
        return Some((w1.clone(), vec![w1, w2]));
    }

    // e. Dialect participles: -eika -> -েইকা (e.g. theika -> থেইকা), -oiya -> -ইয়া (e.g. koiya -> কইয়া)
    if let Some(stem) = lower.strip_suffix("eika") {
        let (stem_bn, _) = transliterate_avro(stem);
        let word = format!("{}েইকা", stem_bn);
        return Some((word.clone(), vec![word]));
    }
    if let Some(stem) = lower.strip_suffix("oiya") {
        let (stem_bn, _) = transliterate_avro(stem);
        let word = format!("{}ইয়া", stem_bn);
        return Some((word.clone(), vec![word]));
    }
    // Temporal dialect suffix -jka -> -জকা (e.g. ai-jka -> আইজকা, kail-jka -> কাইলকা)
    if let Some(stem) = lower.strip_suffix("jka") {
        let (stem_bn, _) = transliterate_avro(stem);
        let word = format!("{}জকা", stem_bn);
        return Some((word.clone(), vec![word]));
    }

    // f. Bengali dual-stem hyphen compounds (e.g. hrisswota-dirghotar -> হ্রস্বতা-দীর্ঘতার)
    if lower.contains('-') {
        let parts: Vec<&str> = lower.split('-').collect();
        if parts.len() == 2 && !parts[0].is_empty() && !parts[1].is_empty() {
            let p1_str = if parts[0].starts_with("hrisswo") {
                format!("হ্রস্ব{}", &parts[0]["hrisswo".len()..])
            } else {
                let (p, _) = transliterate_avro(parts[0]);
                p
            };
            let p2_str = if parts[1].starts_with("dirgho") {
                format!("দীর্ঘ{}", &parts[1]["dirgho".len()..])
            } else {
                let (p, _) = transliterate_avro(parts[1]);
                p
            };
            if !p1_str.is_empty() && !p2_str.is_empty() {
                let word = format!("{}-{}", p1_str, p2_str);
                return Some((word.clone(), vec![word]));
            }
        }
    }
    if lower.contains('-') {
        let parts: Vec<&str> = lower.split('-').collect();
        if parts.len() == 2 && !parts[0].is_empty() && !parts[1].is_empty() {
            let (p1, _) = transliterate_avro(parts[0]);
            let (p2, _) = transliterate_avro(parts[1]);
            if !p1.is_empty() && !p2.is_empty() && p1 != parts[0] && p2 != parts[1] {
                let word = format!("{}-{}", p1, p2);
                return Some((word.clone(), vec![word]));
            }
        }
    }

    None
}

/// Returns true if the token is a social mention, hashtag, digit unit, bilingual clitic, or generative morphology
pub fn is_avro_special_fastpath(input: &str) -> bool {
    input.starts_with('@')
        || input.starts_with('#')
        || resolve_digit_unit_candidates(input).is_some()
        || resolve_bilingual_hyphen_clitic(input).is_some()
        || resolve_generative_morphology(input).is_some()
}

/// Transliterates Romanized ASCII text to Bengali script using `lekhani-core`'s
/// production PhoneticSuggestion engine, bilingual loanwords, and Avro Trie parser.
///
/// Returns (primary_transliteration, list_of_candidates).
pub fn transliterate_avro_with_context(input: &str, context: &[&str]) -> (String, Vec<String>) {
    if input.is_empty() {
        return (String::new(), Vec::new());
    }

    // Soft apostrophe escape: mobile users type ' as a consonant/vowel separator (e.g. k'kh -> k`kh -> কখ)
    let escaped_storage: String;
    let effective_input = if input.contains('\'') {
        escaped_storage = input.replace('\'', "`");
        escaped_storage.as_str()
    } else {
        input
    };
    let input = effective_input;

    let lower = input.to_lowercase();
    let db = crate::session::get_core_database();

    let is_explicit_common = has_override_word(input) || has_override_word(&lower);

    // -1. User-defined explicit autocorrect / shortcut rules
    if let Ok(uac) = db.user_autocorrect.read() {
        if let Some(replacement) = uac.get(input).or_else(|| uac.get(&lower)) {
            let primary = replacement.clone();
            let mut candidates = vec![primary.clone()];
            let parser = get_avro_parser();
            let def = parser.convert(input);
            if def != primary {
                candidates.push(def);
            }
            append_prefix_matches(input, &primary, &mut candidates);
            return (primary, candidates);
        }
    }

    // Fast-path 1: Social media mentions (@username) and hashtags (#tag)
    if let Some((primary, candidates)) = resolve_social_mention_or_hashtag(input) {
        return (primary, candidates);
    }

    // Fast-path 2: Digit units, quantifiers, currency, and ordinals (e.g. 10ta -> ১০টা, 500tk -> ৫০০ টাকা, 1st -> ১ম)
    if let Some((primary, candidates)) = resolve_digit_unit_candidates(input) {
        return (primary, candidates);
    }

    // Fast-path 2b: Pure numeric literals and decimal/comma numbers (e.g. "12486" -> "১২৪৮৬", "8.4" -> "৮.৪")
    if !input.is_empty()
        && input.chars().all(|c| c.is_ascii_digit() || c == '.' || c == ',')
        && input.chars().any(|c| c.is_ascii_digit())
    {
        let b_num = ascii_digits_to_bengali(input);
        let candidates = vec![b_num.clone(), input.to_string()];
        return (b_num, candidates);
    }

    // Fast-path 3: Bilingual hyphen clitics (e.g. Facebook-e -> Facebook-এ, bkash-e -> bkash-এ)
    if let Some((primary, candidates)) = resolve_bilingual_hyphen_clitic(input) {
        return (primary, candidates);
    }

    // Fast-path 4: Generative Morphology & Dialectal Verb Conjugations
    if let Some((primary, candidates)) = resolve_generative_morphology(input) {
        return (primary, candidates);
    }

    // 0. User candidate override memory (strictly for inputs > 2 characters)
    let is_short_input = input.chars().count() <= 2;
    let mut candidate_memory = HashMap::new();
    let mut remembered_choice: Option<String> = None;
    if !is_short_input {
        if let Ok(learner) = db.learner.read() {
            if let Some(error_override) = learner
                .lookup_input_error(input)
                .or_else(|| learner.lookup_input_error(&lower))
            {
                candidate_memory.insert(input.to_string(), error_override.to_string());
                remembered_choice = Some(error_override.to_string());
            } else if let Some(user_choice) = learner
                .candidate_memory
                .get(input)
                .or_else(|| learner.candidate_memory.get(&lower))
            {
                candidate_memory.insert(input.to_string(), user_choice.clone());
                remembered_choice = Some(user_choice.clone());
            }
        }
    }

    // -1b. Bundled system autocorrect (only if no user choice override and not an explicit common word)
    let mut autocorrect_replacement: Option<String> = None;
    if remembered_choice.is_none() && !is_explicit_common {
        if let Some(replacement) = db
            .autocorrect
            .get(input)
            .or_else(|| db.autocorrect.get(&lower))
        {
            autocorrect_replacement = Some(replacement.clone());
        }
    }

    let parser = get_avro_parser();
    let def = parser.convert(input);

    // For short inputs (1 or 2 characters), guarantee pure phonetic preedit fidelity
    // so in-flight typing does not abruptly jump to multi-syllable shorthand words (e.g. "kn" -> "কেন")
    // and never gets hijacked by candidate taps (e.g. "oi" -> "ঐ" vs "ওই").
    // Shorthand and high-frequency completions are offered as candidate strip suggestions.
    if is_short_input {
        let mut short_candidates = Vec::with_capacity(12);

        // 1. Direct phonetic transliteration is always the primary preedit for short inputs
        let primary = def.clone();
        short_candidates.push(primary.clone());

        // 2a. Short English words, digits & Latin escape hatch (e.g. "ok", "hi", "fb", "id", "to", "no")
        let is_passthrough = (crate::english::is_recognized_english_word(input)
            && !short_candidates
                .iter()
                .any(|c| c.eq_ignore_ascii_case(input)))
            || (input.chars().all(|c| c.is_ascii_digit())
                && !short_candidates.contains(&input.to_string()));
        if is_passthrough {
            short_candidates.insert(1.min(short_candidates.len()), input.to_string());
        }

        // 2. Shorthand & Common Contractions (offered as secondary suggestions)
        if let Some(words) = get_override_words(input)
            .or_else(|| get_override_words(&lower))
        {
            for w in words {
                if !short_candidates.contains(&w) {
                    short_candidates.push(w);
                }
            }
        }
        if let Some(shorthand) = db.shorthand.get(input).or_else(|| db.shorthand.get(&lower)) {
            if !short_candidates.iter().any(|c| c == shorthand) {
                short_candidates.push(shorthand.clone());
            }
        }

        // Common word prefixes sorted by confidence score descending, then length ascending
        let max_len = if lower.len() == 1 { 3 } else { 6 };
        let mut prefix_entries = db.lookup_override_prefix(&lower, max_len, 256);
        prefix_entries.sort_by(|a, b| {
            let score_a = a.1.first().map(|(_, s)| s).unwrap_or(0.0);
            let score_b = b.1.first().map(|(_, s)| s).unwrap_or(0.0);
            score_b
                .partial_cmp(&score_a)
                .unwrap_or(std::cmp::Ordering::Equal)
                .then_with(|| a.0.len().cmp(&b.0.len()))
        });
        for (_, entry) in prefix_entries {
            for (w, score) in entry {
                if score >= 0.90 && !short_candidates.iter().any(|c| c == w) {
                    short_candidates.push(w.to_string());
                }
                if short_candidates.len() >= 8 {
                    break;
                }
            }
            if short_candidates.len() >= 8 {
                break;
            }
        }

        // Common word prefixes sorted by frequency
        if short_candidates.len() < 8 {
            for p in db.search_dictionary(&primary, 8 - short_candidates.len()) {
                if !short_candidates.contains(&p) {
                    short_candidates.push(p);
                }
            }
        }

        // 3. High-frequency natural completions from CORE_BENGALI_FREQUENCIES
        let freq_completions = get_frequency_completions(&primary, 8);
        for word in freq_completions {
            if !short_candidates.iter().any(|c| c == &word) {
                short_candidates.push(word);
            }
            if short_candidates.len() >= 8 {
                break;
            }
        }

        // 4. Custom User Words
        if let Ok(learner) = db.learner.read() {
            for w in &learner.custom_user_words {
                if w.starts_with(&primary) && !short_candidates.iter().any(|c| c == w) {
                    short_candidates.push(w.clone());
                }
                if short_candidates.len() >= 8 {
                    break;
                }
            }
        }

        // Ensure def is present
        if !short_candidates.iter().any(|c| c == &def) {
            short_candidates.push(def);
        }

        return (primary, short_candidates);
    }

    // 1. Upstream PhoneticSuggestion Engine:
    // Performs Chandra Bindu normalization ("c^ad" -> "চাঁদ"),
    // Sanskrit and sound-law conjuncts ("sotyo" -> "সত্য", "mrittu" -> "মৃত্যু", "shuryo" -> "সূর্য"),
    // vowel/kar normalization ("dure" -> "দূরে", "tomake" -> "তোমাকে", "boiti" -> "বইটি"),
    // loanwords ("shirt" -> "শার্ট", "password" -> "পাসওয়ার্ড"),
    // and statistical context ranking.
    let sugg_mutex = crate::session::get_phonetic_suggestion();
    let (mut candidates, mut selected_idx) = {
        let mut sugg = match sugg_mutex.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        sugg.suggest_with_multi_context(input, context, false, true, &candidate_memory)
    };

    if candidates.is_empty() {
        let parser = get_avro_parser();
        let def = parser.convert(input);
        candidates.push(def);
    }

    // 1. Verbatim Latin Escape Hatch: If the input is ASCII and >= 2 characters,
    // always surface the exact typed string in the candidate strip.
    // Acronyms (e.g. API, JSON, UI, CGPA) and English words without established Bengali loanwords
    // (e.g. asynchronous, deployment, postpone, validate, reproduce, bug, fix, perfect)
    // are promoted directly to Slot 0 so code-switching typists don't suffer mangled nonsense syllables.
    let is_verbatim_candidate_needed = input.is_ascii()
        && input.len() >= 2
        && !candidates.iter().any(|c| c.eq_ignore_ascii_case(input));

    let is_all_caps = input.len() >= 2
        && input.chars().any(|c| c.is_ascii_alphabetic())
        && input
            .chars()
            .all(|c| c.is_ascii_uppercase() || !c.is_alphabetic());
    let is_recognized_english = crate::english::is_recognized_english_word(input);
    let def_conv = get_avro_parser().convert(input);
    let def_lower_conv = get_avro_parser().convert(&lower);
    let _is_bengali_dict_word = (!def_conv.is_empty() && db.is_exact_dictionary_word(&def_conv))
        || (!def_lower_conv.is_empty() && db.is_exact_dictionary_word(&def_lower_conv));

    let has_established_loanword = is_explicit_common
        || db.lookup_override(input).is_some()
        || db.lookup_override(&lower).is_some()
        || lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&lower).is_some();

    if is_all_caps || (is_recognized_english && !has_established_loanword) {
        if let Some(pos) = candidates.iter().position(|c| c.eq_ignore_ascii_case(input)) {
            candidates.remove(pos);
        }
        candidates.insert(0, input.to_string());
        selected_idx = 0;
    } else if is_verbatim_candidate_needed {
        let insert_pos = candidates.len().min(1);
        candidates.insert(insert_pos, input.to_string());
    }

    // 2. Pure Literal Avro Conversion: Ensure the deterministic rule-based Avro parser output
    // is always available within the top candidates so typists can bypass AI homophone ranking if desired.
    let def_pure = get_avro_parser().convert(input);
    if !def_pure.is_empty() && !candidates.iter().any(|c| c == &def_pure) {
        let insert_pos = candidates.len().min(2);
        candidates.insert(insert_pos, def_pure);
    }

    // Digits / Currency Symbols dual candidate surfacing (e.g. "1234" -> ["১২৩৪", "1234"], "$100" -> ["৳১০০", "$100"])
    let is_numeric_or_currency =
        input.chars().all(|c| c.is_ascii_digit()) || input.starts_with('$');
    if is_numeric_or_currency && !candidates.iter().any(|c| c == input) {
        let insert_pos = candidates.len().min(1);
        candidates.insert(insert_pos, input.to_string());
    }

    // Apply deferred bundled system autocorrect if active
    if let Some(ref replacement) = autocorrect_replacement {
        if let Some(pos) = candidates.iter().position(|c| c == replacement) {
            candidates.remove(pos);
        }
        candidates.insert(0, replacement.clone());
        selected_idx = 0;
    }

    // 2. Ensure core common words are prioritized at the top of candidate list,
    // while preserving Anvaya concordant verb if an active honorific agreement exists.
    let anvaya_state = lekhani_ai::AnvayaAgreementEngine::extract_state(context);
    let concordant_cand = if anvaya_state.honorific_tier != lekhani_ai::HonorificRegister::Neutral {
        lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(input, anvaya_state.honorific_tier)
            .or_else(|| lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(&lower, anvaya_state.honorific_tier))
    } else {
        None
    };

    let is_explicit_common = has_override_word(input) || has_override_word(&lower);
    if let Some(words) = get_override_words(input)
        .or_else(|| get_override_words(&lower))
    {
        let start_idx = if let Some(concordant) = concordant_cand {
            let conc_nfc = crate::probaho::nfc_normalize(concordant);
            if let Some(pos) = candidates.iter().position(|c| crate::probaho::nfc_normalize(c) == conc_nfc) {
                let c = candidates.remove(pos);
                candidates.insert(0, c);
            } else {
                candidates.insert(0, conc_nfc);
            }
            1
        } else {
            0
        };

        for (i, w) in words.into_iter().enumerate() {
            if let Some(pos) = candidates.iter().position(|c| c == &w) {
                candidates.remove(pos);
            }
            candidates.insert((start_idx + i).min(candidates.len()), w);
        }
        selected_idx = 0;
    } else if let Some(ref collapsed) = collapse_elongation(&lower) {
        let words: Option<Vec<String>> = match collapsed.as_str() {
            "ha" => Some(vec!["হ্যাঁ".to_string(), "হা".to_string()]),
            "na" => Some(vec!["না".to_string()]),
            _ => get_override_words(collapsed.as_str()),
        };

        if let Some(words) = words {
            for (i, w) in words.into_iter().enumerate() {
                if let Some(pos) = candidates.iter().position(|c| c == &w) {
                    candidates.remove(pos);
                }
                candidates.insert(i.min(candidates.len()), w);
            }
            selected_idx = 0;
        } else if let Some((bn_loan, _)) =
            lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(collapsed)
        {
            let ws = bn_loan.to_string();
            if let Some(pos) = candidates.iter().position(|c| c == &ws) {
                candidates.remove(pos);
            }
            candidates.insert(0, ws);
            selected_idx = 0;
        }
    }

    // 2b. If the user explicitly typed an inflection ending in 'bo' or 'cho' (e.g. khabo -> খাবো, korcho -> করছো),
    // prioritize the matching 'ো'-inflected form from candidates (unless explicitly ordered in phonetic overrides)
    if !is_explicit_common
        && (lower.ends_with("bo") || lower.ends_with("cho"))
        && !candidates.is_empty()
    {
        if let Some(pos) = candidates.iter().position(|c| c.ends_with('ো')) {
            let o_cand = candidates.remove(pos);
            candidates.insert(0, o_cand);
            selected_idx = 0;
        } else if def.ends_with('ো') {
            if let Some(pos) = candidates.iter().position(|c| c == &def) {
                candidates.remove(pos);
            }
            candidates.insert(0, def.clone());
            selected_idx = 0;
        }
    }

    // 2c. Ensure def (deterministic Avro parser output) is preserved in candidates as "Force Avro"
    if !def.is_empty() && candidates.first() != Some(&def) {
        if let Some(pos) = candidates.iter().position(|c| c == &def) {
            candidates.remove(pos);
        }
        let target_idx = 1.min(candidates.len());
        candidates.insert(target_idx, def.clone());
    }

    // 2d. Exact case-sensitivity fidelity, All-Caps normalization & Sentence-initial Titlecase smart promotion:
    let has_explicit_avro_case = input
        .chars()
        .any(|c| c.is_ascii_uppercase() && "OIUDGJNRSTYZ".contains(c));
    let is_all_caps = input.chars().count() >= 2 && input.chars().all(|c| c.is_ascii_uppercase());
    if is_all_caps {
        let is_common_bengali_all_caps = has_override_word(&lower);
        if is_common_bengali_all_caps {
            // All-Caps Bengali input (e.g. "AMI", "TUMI", "DESH"):
            let resolved_lower = if let Some(ac) = db.autocorrect.get(&lower) {
                ac.clone()
            } else if let Some(words) = get_override_words(&lower) {
                words.into_iter().next().unwrap_or_default()
            } else {
                parser.convert(&lower)
            };
            if !resolved_lower.is_empty() {
                if let Some(pos) = candidates.iter().position(|c| c == &resolved_lower) {
                    candidates.remove(pos);
                }
                candidates.insert(0, resolved_lower);
                if !def.is_empty() && !candidates.contains(&def) {
                    candidates.insert(1.min(candidates.len()), def.clone());
                }
                selected_idx = 0;
            }
        } else {
            // Technical or English Acronym (e.g. "CER", "WER", "RFC", "API", "JSON", "LLM", "NaN"):
            if let Some(pos) = candidates.iter().position(|c| c.eq_ignore_ascii_case(input)) {
                candidates.remove(pos);
            }
            candidates.insert(0, input.to_string());
            selected_idx = 0;
        }
    } else if has_explicit_avro_case && !def.is_empty() {
        let is_titlecase = input.chars().next().is_some_and(|c| c.is_ascii_uppercase())
            && input.chars().skip(1).all(|c| c.is_ascii_lowercase());

        let resolved_lower = if let Some(ac) = db.autocorrect.get(&lower) {
            ac.clone()
        } else if let Some(words) = get_override_words(&lower) {
            words.into_iter().next().unwrap_or_default()
        } else {
            parser.convert(&lower)
        };

        if is_titlecase {
            // For sentence-initial titlecase (e.g. "Din" -> দিন vs ডিন, "Tara" -> তারা vs টারা):
            // Check if the lowercase variant is a common word or much higher frequency.
            let is_common_lower = has_override_word(&lower);
            let freq_lower = get_word_frequency(resolved_lower.as_str());
            let freq_def = get_word_frequency(def.as_str());

            if is_common_lower
                || freq_lower >= freq_def * 3
                || (!db.is_exact_dictionary_word(&def)
                    && db.is_exact_dictionary_word(&resolved_lower))
            {
                if let Some(pos) = candidates.iter().position(|c| c == &resolved_lower) {
                    candidates.remove(pos);
                }
                candidates.insert(0, resolved_lower);
                if !candidates.iter().any(|c| c == &def) {
                    candidates.insert(1.min(candidates.len()), def.clone());
                }
                selected_idx = 0;
            } else {
                if let Some(pos) = candidates.iter().position(|c| c == &def) {
                    candidates.remove(pos);
                }
                candidates.insert(0, def.clone());
                selected_idx = 0;
            }
        } else {
            if let Some(pos) = candidates.iter().position(|c| c == &def) {
                candidates.remove(pos);
            }
            candidates.insert(0, def.clone());
            selected_idx = 0;
        }
    }

    // 3. QWERTY adjacency & transposition auto-correction for fat-finger typos on touchscreen
    let mut common_typos: Vec<(String, f32)> = Vec::new();
    let mut dict_typos: Vec<String> = Vec::new();
    let is_exact_match = is_explicit_common
        || (db.is_exact_dictionary_word(&def) && db.get_frequency(&def) >= 1000);
    if !is_exact_match && lower.len() >= 3 && lower.len() <= 8 {
        let chars: Vec<char> = lower.chars().collect();
        for (i, &ch) in chars.iter().enumerate() {
            for &adj in crate::english::get_qwerty_adjacent_keys(ch) {
                let mut sub = chars.clone();
                sub[i] = adj;
                let cand_key: String = sub.into_iter().collect();
                if let Some(ov) = db.lookup_override(cand_key.as_str()) {
                    for (ws, score) in ov {
                        let ws_str = ws.to_string();
                        if !candidates.contains(&ws_str) && !common_typos.iter().any(|(w, _)| w == &ws_str) {
                            common_typos.push((ws_str, score));
                        }
                    }
                } else if let Some(ac) = db.autocorrect.get(&cand_key) {
                    if !candidates.contains(ac) && !common_typos.iter().any(|(w, _)| w == ac) {
                        common_typos.push((ac.clone(), 0.95));
                    }
                } else if dict_typos.len() < 3 {
                    let conv = parser.convert(&cand_key);
                    if !conv.is_empty()
                        && db.is_exact_dictionary_word(&conv)
                        && !candidates.contains(&conv)
                        && !dict_typos.contains(&conv)
                    {
                        dict_typos.push(conv);
                    }
                }
            }
        }

        // 3b. 1-step adjacent letter transposition recovery for fast two-thumb typing (e.g. "bhlao" -> "bhalo" -> "ভালো")
        if common_typos.is_empty() && chars.len() >= 3 && chars.len() <= 8 {
            for i in 0..chars.len() - 1 {
                let mut swapped = chars.clone();
                swapped.swap(i, i + 1);
                let swap_key: String = swapped.into_iter().collect();
                if let Some(ov) = db.lookup_override(swap_key.as_str()) {
                    for (ws, score) in ov {
                        let ws_str = ws.to_string();
                        if !candidates.contains(&ws_str) && !common_typos.iter().any(|(w, _)| w == &ws_str) {
                            common_typos.push((ws_str, score));
                        }
                    }
                } else if let Some(ac) = db.autocorrect.get(&swap_key) {
                    if !candidates.contains(ac) && !common_typos.iter().any(|(w, _)| w == ac) {
                        common_typos.push((ac.clone(), 0.95));
                    }
                } else if dict_typos.len() < 3 {
                    let conv = parser.convert(&swap_key);
                    if !conv.is_empty()
                        && db.is_exact_dictionary_word(&conv)
                        && !candidates.contains(&conv)
                        && !dict_typos.contains(&conv)
                    {
                        dict_typos.push(conv);
                    }
                }
            }
        }
    }
    common_typos.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
    let mut typo_corrections: Vec<String> = common_typos.into_iter().map(|(w, _)| w).collect();
    typo_corrections.extend(dict_typos);

    // 3c. Implicit Chandra Bindu (nasalization) auto-inference:
    // On mobile keyboards, '^' is buried in symbol sub-layers.
    // When users type unnasalized Latin forms (e.g. "chad", "bash", "pac", "dat", "has", "kada", "faka", "badha"),
    // synthesize the nasalized dictionary counterpart ("চাঁদ", "বাঁশ", "পাঁচ", "দাঁত", "হাঁস", "কাঁদা", "ফাঁকা", "বাঁধা").
    if !input.contains('^') && lower.len() >= 3 && lower.len() <= 8 {
        let chars: Vec<char> = lower.chars().collect();
        for (i, &ch) in chars.iter().enumerate() {
            if "aeiou".contains(ch) {
                let mut with_caret = String::with_capacity(lower.len() + 1);
                with_caret.push_str(&lower[..i]);
                with_caret.push('^');
                with_caret.push_str(&lower[i..]);
                let conv = parser.convert(&with_caret);
                if conv.contains('ঁ')
                    && db.is_exact_dictionary_word(&conv)
                    && !candidates.contains(&conv)
                {
                    let insert_pos = 1.min(candidates.len());
                    candidates.insert(insert_pos, conv);
                }

                // Also check 'ch' -> 'c' substitution with caret (e.g. "chad" -> "c^ad" -> "চাঁদ")
                if lower.starts_with("ch") && i == 2 {
                    let mut c_caret = String::with_capacity(lower.len());
                    c_caret.push_str("c^");
                    c_caret.push_str(&lower[2..]);
                    let conv_c = parser.convert(&c_caret);
                    if conv_c.contains('ঁ')
                        && db.is_exact_dictionary_word(&conv_c)
                        && !candidates.contains(&conv_c)
                    {
                        let insert_pos = 1.min(candidates.len());
                        candidates.insert(insert_pos, conv_c);
                    }
                }
            }
        }
    }

    // Place typo corrections AFTER def to preserve primary phonetic output at index 0
    // and Force Avro at index 1, offering typo recoveries as optional strip choices.
    let insert_offset = if candidates.len() > 1 && candidates[1] == def {
        2
    } else {
        1
    };
    if !is_explicit_common && !typo_corrections.is_empty() {
        for (idx, corr) in typo_corrections.into_iter().enumerate() {
            if idx < 2 {
                let target = (insert_offset + idx).min(candidates.len());
                candidates.insert(target, corr);
            } else {
                candidates.push(corr);
            }
        }
    } else {
        for corr in typo_corrections {
            candidates.push(corr);
        }
    }

    let primary = if let Some(ref choice) = remembered_choice {
        if let Some(pos) = candidates.iter().position(|c| c == choice) {
            candidates.remove(pos);
        }
        candidates.insert(0, choice.clone());
        choice.clone()
    } else if selected_idx < candidates.len() && selected_idx > 0 {
        let p = candidates.remove(selected_idx);
        candidates.insert(0, p.clone());
        p
    } else if !candidates.is_empty() {
        candidates[0].clone()
    } else {
        String::new()
    };

    // Slot 3: Typo rescue extraction
    // If the user has a typo correction candidate (from spatial proximity or error-pattern learning)
    // and it differs meaningfully from Slot 1 (the primary AI pick), surface it explicitly.
    // This ensures mistyped inputs always show at least one recovery option.
    {
        let slot1 = candidates.get(1).cloned();
        // Find the first candidate that originated from a TypoFallback source
        // by checking if it differs from slot0 and slot1 by edit distance > 1.
        // We do this heuristically: candidates after the first 3 positions that are Bengali
        // and not identical to candidates[0..3] are rescue candidates.
        let typo_rescue: Option<String> = candidates
            .iter()
            .skip(3)
            .find(|c| {
                let c_chars: Vec<char> = c.chars().collect();
                // Must be Bengali (not ASCII) and differ meaningfully from slot 1
                let is_bengali = c_chars
                    .iter()
                    .any(|ch| ('\u{0980}'..='\u{09FF}').contains(ch));
                let differs_from_slot1 = slot1
                    .as_deref()
                    .map(|s| edit_distance(s, c) > 1)
                    .unwrap_or(true);
                is_bengali && differs_from_slot1
            })
            .cloned();

        if let Some(rescue) = typo_rescue {
            // Remove from its current position and place at index 3
            if let Some(pos) = candidates.iter().position(|c| c == &rescue) {
                candidates.remove(pos);
            }
            let target = 3.min(candidates.len());
            candidates.insert(target, rescue);
        }
    }

    // 4. Core database PrefixTrie lookup to expand matching vocabulary
    append_prefix_matches(input, &primary, &mut candidates);

    if remembered_choice.is_none() {
        prioritize_common_or_override_candidate(input, &mut candidates);
    }
    let raw_primary = candidates.first().cloned().unwrap_or(primary);
    let primary = crate::probaho::nfc_normalize(&raw_primary);

    let mut normalized_candidates = Vec::with_capacity(candidates.len());
    for c in candidates {
        let norm = crate::probaho::nfc_normalize(&c);
        if !normalized_candidates.contains(&norm) {
            normalized_candidates.push(norm);
        }
    }

    (primary, normalized_candidates)
}

/// Convenience wrapper for zero-context transliteration
pub fn transliterate_avro(input: &str) -> (String, Vec<String>) {
    transliterate_avro_with_context(input, &[])
}

fn append_prefix_matches(input: &str, primary: &str, candidates: &mut Vec<String>) {
    if primary.chars().count() < 2 {
        return;
    }
    let db = crate::session::get_core_database();

    // 1. Direct prefix matches from core Trie
    let prefix_matches = db.trie.find_prefix_entries(primary, 4);
    for (w, _) in prefix_matches {
        if candidates.len() >= 8 {
            break;
        }
        if !candidates.iter().any(|c| c == w) {
            candidates.push(w.to_string());
        }
    }

    // 2. Reph variant prefix search (e.g. 'কিংকরত' -> 'কিংকর্ত', 'কিংকরতব্যব' -> 'কিংকর্তব্যব')
    // This allows in-flight un-reduced 'র' + consonant to match Reph words in the 103K vocabulary trie.
    let chars: Vec<char> = primary.chars().collect();
    let mut has_plain_r_cons = false;
    let mut reph_buf = Vec::with_capacity(chars.len() + 2);
    let mut idx = 0;
    while idx < chars.len() {
        if chars[idx] == 'র'
            && idx + 1 < chars.len()
            && (('\u{0995}'..='\u{09B9}').contains(&chars[idx + 1])
                || ('\u{09DC}'..='\u{09DF}').contains(&chars[idx + 1])
                || chars[idx + 1] == 'ৎ')
        {
            reph_buf.push('র');
            reph_buf.push('্');
            reph_buf.push(chars[idx + 1]);
            has_plain_r_cons = true;
            idx += 2;
        } else {
            reph_buf.push(chars[idx]);
            idx += 1;
        }
    }
    if has_plain_r_cons {
        let reph_primary: String = reph_buf.into_iter().collect();
        let reph_matches = db.trie.find_prefix_entries(&reph_primary, 4);
        for (w, _) in reph_matches {
            if candidates.len() >= 8 {
                break;
            }
            if !candidates.iter().any(|c| c == w) {
                candidates.push(w.to_string());
            }
        }
    }

    // 3. Autocorrect prefix lookahead for long compound words (>= 4 Latin characters)
    let lower = input.to_lowercase();
    if lower.len() >= 4 {
        for (k, v) in db.autocorrect.iter() {
            if candidates.len() >= 8 {
                break;
            }
            if k.starts_with(&lower) && !candidates.contains(v) {
                let insert_idx = 1.min(candidates.len());
                candidates.insert(insert_idx, v.clone());
            }
        }
    }

    // 4. Custom User Words
    if let Ok(learner) = db.learner.read() {
        for w in &learner.custom_user_words {
            if candidates.len() >= 8 {
                break;
            }
            if w.starts_with(primary) && !candidates.contains(w) {
                candidates.push(w.clone());
            }
        }
    }
}

fn get_word_frequency(word: &str) -> u32 {
    lekhani_core::phonetic::database::CORE_BENGALI_FREQUENCIES
        .iter()
        .find(|&&(w, _)| w == word)
        .map(|&(_, f)| f)
        .unwrap_or(0)
}

fn get_frequency_completions(prefix: &str, limit: usize) -> Vec<String> {
    if prefix.is_empty() {
        return Vec::new();
    }
    let mut results = Vec::with_capacity(limit);
    for &(word, _freq) in lekhani_core::phonetic::database::CORE_BENGALI_FREQUENCIES {
        if word.starts_with(prefix) && word != prefix && !results.iter().any(|r| r == word) {
            results.push(word.to_string());
            if results.len() >= limit {
                break;
            }
        }
    }
    results
}

#[cfg(test)]
mod tests {
    use super::*;
    use serial_test::serial;

    #[test]
    #[serial]
    fn test_prioritize_common_or_override_candidate() {
        let mut cands = vec!["অন্যকিছু".to_string(), "আমি".to_string()];
        prioritize_common_or_override_candidate("ami", &mut cands);
        assert_eq!(cands[0], "আমি");
    }

    #[test]
    #[serial]
    fn test_khabo() {
        let (res, cands) = transliterate_avro("khabo");
        assert_eq!(res, "খাবো");
        assert!(cands.contains(&"খাবো".to_string()));
        assert!(cands.contains(&"খাব".to_string()));
    }

    #[test]
    #[serial]
    fn test_lekhani_parser_avro() {
        let (res, cands) = transliterate_avro("ami");
        assert_eq!(res, "আমি");
        assert!(cands.contains(&"আমি".to_string()));

        let (res, _) = transliterate_avro("bangla");
        assert_eq!(res, "বাংলা");

        let (res, _) = transliterate_avro("kemon");
        assert_eq!(res, "কেমন");

        let (res, _) = transliterate_avro("akash");
        assert_eq!(res, "আকাশ");
    }

    #[test]
    #[serial]
    fn test_rules_transliteration() {
        let (res, _) = transliterate_avro("dhaka");
        assert_eq!(res, "ঢাকা");

        let (res, _) = transliterate_avro("shonar");
        assert_eq!(res, "সোনার");

        let (res, _) = transliterate_avro("bhalo");
        assert_eq!(res, "ভালো");
    }

    #[test]
    #[serial]
    fn test_conjuncts() {
        let (res, _) = transliterate_avro("shikkhok");
        assert_eq!(res, "শিক্ষক");

        let (res, _) = transliterate_avro("bristi");
        assert_eq!(res, "বৃষ্টি");
    }

    #[test]
    #[serial]
    fn test_qwerty_proximity_typo_correction() {
        // 's' is next to 'a' on QWERTY -> "smi" typo suggests "আমি"
        // index 0: direct conversion (phonetic candidate)
        // index 1: Force Avro def "স্মি" is preserved
        // index 2: typo auto-correction "আমি"
        let (pre_smi, cands_smi) = transliterate_avro("smi");
        assert_eq!(pre_smi, cands_smi[0]);
        assert_eq!(cands_smi.get(1).map(|s| s.as_str()), Some("স্মি"));
        assert_eq!(cands_smi.get(2).map(|s| s.as_str()), Some("আমি"));

        // Adjacent transposition typo: "bhlao" -> "ভালো"
        let (_pre_bhlao, cands_bhlao) = transliterate_avro("bhlao");
        assert!(cands_bhlao.contains(&"ভালো".to_string()), "Expected 'ভালো' in candidates for 'bhlao'");
    }

    #[test]
    #[serial]
    fn test_user_reported_avro_words() {
        let (boi, _) = transliterate_avro("boi");
        assert_eq!(boi, "বই");

        let (kono, cands_kono) = transliterate_avro("kono");
        assert_eq!(kono, "কোনো");
        assert!(cands_kono.contains(&"কোন".to_string()));

        let (shirt, _) = transliterate_avro("shirt");
        assert_eq!(shirt, "শার্ট");

        let (password, _) = transliterate_avro("password");
        assert_eq!(password, "পাসওয়ার্ড");

        let (copy, _) = transliterate_avro("copy");
        assert_eq!(copy, "কপি");

        let (_, cands_pora) = transliterate_avro("pora");
        assert!(cands_pora.contains(&"পরা".to_string()));
        assert!(cands_pora.contains(&"পড়া".to_string()));
    }

    #[test]
    #[serial]
    fn test_avro_complex_and_common_fidelity() {
        // 1. Chandra Bindu normalization
        let (cad, _) = transliterate_avro("c^ad");
        assert_eq!(cad, "চাঁদ", "c^ad should transliterate to চাঁদ");
        let (ca_d, _) = transliterate_avro("ca^d");
        assert_eq!(ca_d, "চাঁদ", "ca^d should transliterate to চাঁদ");
        let (ch_ad, _) = transliterate_avro("ch^ad");
        assert_eq!(ch_ad, "ছাঁদ", "ch^ad should transliterate to ছাঁদ");
        let (kada, _) = transliterate_avro("k^ada");
        assert_eq!(kada, "কাঁদা", "k^ada should transliterate to কাঁদা");
        let (bash, _) = transliterate_avro("b^ash");
        assert_eq!(bash, "বাঁশ", "b^ash should transliterate to বাঁশ");

        // 2. Vowel and Kar normalization
        let (dure, _) = transliterate_avro("dure");
        assert_eq!(dure, "দূরে", "dure should transliterate to দূরে");
        let (d_ure, _) = transliterate_avro("dUre");
        assert_eq!(d_ure, "দূরে", "dUre should transliterate to দূরে");
        let (dur, _) = transliterate_avro("dur");
        assert_eq!(dur, "দূর", "dur should transliterate to দূর");
        let (d_ur, _) = transliterate_avro("dUr");
        assert_eq!(d_ur, "দূর", "dUr should transliterate to দূর");
        let (tomake, _) = transliterate_avro("tomake");
        assert_eq!(tomake, "তোমাকে", "tomake should transliterate to তোমাকে");
        let (t_omake, _) = transliterate_avro("tOmake");
        assert_eq!(t_omake, "তোমাকে", "tOmake should transliterate to তোমাকে");
        let (boiti, _) = transliterate_avro("boiti");
        assert_eq!(boiti, "বইটি", "boiti should transliterate to কোনটি");

        // 3. Sanskrit & Sound-Law Complex Conjuncts
        let (sotyo, _) = transliterate_avro("sotyo");
        assert_eq!(sotyo, "সত্য", "sotyo should transliterate to সত্য");
        let (mrittu, _) = transliterate_avro("mrittu");
        assert_eq!(mrittu, "মৃত্যু", "mrittu should transliterate to মৃত্যু");
        let (mrittyu, _) = transliterate_avro("mrittyu");
        assert_eq!(mrittyu, "মৃত্যু", "mrittyu should transliterate to মৃত্যু");
        let (shuryo, _) = transliterate_avro("shuryo");
        assert_eq!(shuryo, "সূর্য", "shuryo should transliterate to সূর্য");
        let (nomoshkar, _) = transliterate_avro("nomoshkar");
        assert_eq!(
            nomoshkar, "নমস্কার",
            "nomoshkar should transliterate to নমস্কার"
        );
        let (porishkar, _) = transliterate_avro("porishkar");
        assert_eq!(
            porishkar, "পরিষ্কার",
            "porishkar should transliterate to পরিষ্কার"
        );
        let (puroshkar, _) = transliterate_avro("puroshkar");
        assert_eq!(
            puroshkar, "পুরস্কার",
            "puroshkar should transliterate to পুরস্কার"
        );
        let (abishkar, _) = transliterate_avro("abishkar");
        assert_eq!(
            abishkar, "আবিষ্কার",
            "abishkar should transliterate to আবিষ্কার"
        );
        let (lokkhi, _) = transliterate_avro("lokkhi");
        assert_eq!(lokkhi, "লক্ষ্মী", "lokkhi should transliterate to লক্ষ্মী");
        let (rokkha, _) = transliterate_avro("rokkha");
        assert_eq!(rokkha, "রক্ষা", "rokkha should transliterate to রক্ষা");
        let (bhabishshot, _) = transliterate_avro("bhabishshot");
        assert_eq!(
            bhabishshot, "ভবিষ্যৎ",
            "bhabishshot should transliterate to ভবিষ্যৎ"
        );
        let (shobcheye, _) = transliterate_avro("shobcheye");
        assert!(
            shobcheye == "সবচেয়ে" || shobcheye == "সবচেয়ে",
            "shobcheye should transliterate to সবচেয়ে"
        );
        let (chikitshok, _) = transliterate_avro("chikitshok");
        assert_eq!(
            chikitshok, "চিকিৎসক",
            "chikitshok should transliterate to চিকিৎসক"
        );
        let (ahban, _) = transliterate_avro("ahban");
        assert_eq!(ahban, "আহ্বান", "ahban should transliterate to আহ্বান");
        let (jihba, _) = transliterate_avro("jihba");
        assert_eq!(jihba, "জিহ্বা", "jihba should transliterate to জিহ্বা");
        let (chinho, _) = transliterate_avro("chinho");
        assert_eq!(chinho, "চিহ্ন", "chinho should transliterate to চিহ্ন");
        let (totto, _) = transliterate_avro("totto");
        assert_eq!(totto, "তত্ত্ব", "totto should transliterate to তত্ত্ব");
        let (ucchash, _) = transliterate_avro("ucchash");
        assert_eq!(ucchash, "উচ্ছ্বাস", "ucchash should transliterate to উচ্ছ্বাস");
        let (protiddhoni, _) = transliterate_avro("protiddhoni");
        assert_eq!(
            protiddhoni, "প্রতিধ্বনি",
            "protiddhoni should transliterate to প্রতিধ্বনি"
        );
        let (bhalobasha, _) = transliterate_avro("bhalobasha");
        assert_eq!(
            bhalobasha, "ভালোবাসা",
            "bhalobasha should transliterate to ভালোবাসা"
        );
        let (valobasha, _) = transliterate_avro("valobasha");
        assert_eq!(
            valobasha, "ভালোবাসা",
            "valobasha should transliterate to ভালোবাসা"
        );
    }

    #[test]
    #[serial]
    fn test_avro_short_input_preedit_and_candidates() {
        // 1. Single character 'k'
        let (k_pre, k_cands) = transliterate_avro("k");
        assert_eq!(k_pre, "ক");
        assert!(k_cands.contains(&"ক".to_string()));
        assert!(k_cands.contains(&"কি".to_string()) || k_cands.contains(&"কী".to_string()));
        assert!(k_cands.contains(&"কে".to_string()));
        assert!(!k_cands.contains(&"কংশ".to_string()));
        assert!(!k_cands.contains(&"খহ".to_string()));

        // 2. Two characters 'kn' -> preedit is 'কন', candidates offer 'কেন' (shorthand)
        let (kn_pre, kn_cands) = transliterate_avro("kn");
        assert_eq!(kn_pre, "কন");
        assert!(kn_cands.contains(&"কন".to_string()));
        assert!(kn_cands.contains(&"কেন".to_string()));

        // 3. Two characters 'am' -> preedit is 'আম', candidates offer 'আমি', 'আমার', 'আমাদের'
        let (am_pre, am_cands) = transliterate_avro("am");
        assert_eq!(am_pre, "আম");
        assert!(am_cands.contains(&"আম".to_string()));
        assert!(am_cands.contains(&"আমি".to_string()));
        assert!(am_cands.contains(&"আমার".to_string()));
        assert!(am_cands.contains(&"আমাদের".to_string()));
        assert!(!am_cands.contains(&"আমআম".to_string()));

        // 4. Single character 'b' -> preedit is 'ব', candidates offer 'বা', 'বই', 'বাংলা'
        let (b_pre, b_cands) = transliterate_avro("b");
        assert_eq!(b_pre, "ব");
        assert!(b_cands.contains(&"বা".to_string()) || b_cands.contains(&"বই".to_string()));

        // 5. Three characters 'ami' -> full transliteration to 'আমি'
        let (ami_pre, ami_cands) = transliterate_avro("ami");
        assert_eq!(ami_pre, "আমি");
        assert!(ami_cands.contains(&"আমি".to_string()));
    }

    #[test]
    #[serial]
    fn test_advanced_phonetic_expansion() {
        // 1. Lowercase retroflex expansion: "daktar" -> "ডাক্তার"
        let (daktar, daktar_cands) = transliterate_avro("daktar");
        assert_eq!(daktar, "ডাক্তার", "daktar should produce ডাক্তার");
        assert!(daktar_cands.contains(&"ডাক্তার".to_string()));

        // 2. Strict uppercase Avro muscle memory: "Daktar" -> "ডাক্তার", "poRa" -> "পড়া"
        let (d_aktar, _) = transliterate_avro("Daktar");
        assert_eq!(
            d_aktar, "ডাক্তার",
            "Daktar with capital D must prioritize ডাক্তার"
        );

        let (pora_upper, _) = transliterate_avro("poRa");
        assert_eq!(pora_upper, "পড়া", "poRa with capital R must prioritize পড়া");

        // 3. Agglutinative morphological suffixing: "bristite" -> "বৃষ্টিতে", "deshgulor" -> "দেশগুলোর"
        let (bristite, bristite_cands) = transliterate_avro("bristite");
        assert_eq!(bristite, "বৃষ্টিতে", "bristite should produce বৃষ্টিতে");
        assert!(bristite_cands.contains(&"বৃষ্টিতে".to_string()));

        let (deshgulor, deshgulor_cands) = transliterate_avro("deshgulor");
        assert_eq!(deshgulor, "দেশগুলোর", "deshgulor should produce দেশগুলোর");
        assert!(deshgulor_cands.contains(&"দেশগুলোর".to_string()));

        // 4. Case-agnostic stops & sibilants: "thik" -> "ঠিক", "porikkha" -> "পরীক্ষা"
        let (thik, thik_cands) = transliterate_avro("thik");
        assert_eq!(thik, "ঠিক", "thik should produce ঠিক");
        assert!(thik_cands.contains(&"ঠিক".to_string()));

        let (porikkha, porikkha_cands) = transliterate_avro("porikkha");
        assert_eq!(porikkha, "পরীক্ষা", "porikkha should produce পরীক্ষা");
        assert!(porikkha_cands.contains(&"পরীক্ষা".to_string()));

        // 5. Fat-finger QWERTY proximity recovery: "bhslo" ('s' next to 'a') -> offers "ভালো"
        let (_, bhslo_cands) = transliterate_avro("bhslo");
        assert!(
            bhslo_cands.contains(&"ভালো".to_string()),
            "bhslo must recover ভালো via QWERTY proximity"
        );

        // 6. Sentence-initial Titlecase smart promotion: "Tomar" -> "তোমার", "Tumi" -> "তুমি"
        let (tomar_title, _) = transliterate_avro("Tomar");
        assert_eq!(
            tomar_title, "তোমার",
            "Titlecase 'Tomar' must promote তোমার over টোমার"
        );

        let (tumi_title, _) = transliterate_avro("Tumi");
        assert_eq!(
            tumi_title, "তুমি",
            "Titlecase 'Tumi' must promote তুমি over টুমি"
        );

        // 7. 1-step adjacent letter transposition recovery: "bhlao" ('l' and 'a' swapped) -> offers "ভালো"
        let (_, bhlao_cands) = transliterate_avro("bhlao");
        assert!(
            bhlao_cands.contains(&"ভালো".to_string()),
            "bhlao must recover ভালো via transposition"
        );

        // 8. Implicit Chandra Bindu (nasalization) auto-inference: "chad" -> "চাঁদ", "bash" -> "বাঁশ", "dat" -> "দাঁত", "pac" -> "পাঁচ", "has" -> "হাঁস"
        let (_, chad_cands) = transliterate_avro("chad");
        assert!(
            chad_cands.contains(&"চাঁদ".to_string()),
            "chad should offer চাঁদ"
        );

        let (_, bash_cands) = transliterate_avro("bash");
        assert!(
            bash_cands.contains(&"বাঁশ".to_string()),
            "bash should offer বাঁশ"
        );

        let (_, dat_cands) = transliterate_avro("dat");
        assert!(dat_cands.contains(&"দাঁত".to_string()), "dat should offer দাঁত");

        let (_, pac_cands) = transliterate_avro("pac");
        assert!(pac_cands.contains(&"পাঁচ".to_string()), "pac should offer পাঁচ");

        let (_, has_cands) = transliterate_avro("has");
        assert!(has_cands.contains(&"হাঁস".to_string()), "has should offer হাঁস");

        // 9. Multi-word fluid phrase segmentation: "kemonaso" -> "কেমন আছো"
        let (_, kemonaso_cands) = transliterate_avro("kemonaso");
        assert!(
            kemonaso_cands
                .iter()
                .any(|c| c == "কেমন আছো" || c == "কেমন আছেন"),
            "kemonaso should offer segmented phrase"
        );

        // 10. Standalone chat words: "r" -> offers "আর" alongside "র"
        let (_, r_cands) = transliterate_avro("r");
        assert!(
            r_cands.contains(&"আর".to_string()),
            "r should offer আর as candidate"
        );

        // 11. Khanda-Ta (ৎ) auto-words: "biddut" -> "বিদ্যুৎ", "utshob" -> "উৎসব", "hotat" -> "হঠাৎ"
        let (biddut, _) = transliterate_avro("biddut");
        assert_eq!(biddut, "বিদ্যুৎ", "biddut should produce বিদ্যুৎ");

        let (utshob, _) = transliterate_avro("utshob");
        assert_eq!(utshob, "উৎসব", "utshob should produce উৎসব");

        let (hotat, _) = transliterate_avro("hotat");
        assert_eq!(hotat, "হঠাৎ", "hotat should produce হঠাৎ");

        // 12. Bisarga (ঃ) & Anusvara (ং): "dukkho" -> "দুঃখ", "shongstha" -> "সংস্থা"
        let (dukkho, _) = transliterate_avro("dukkho");
        assert_eq!(dukkho, "দুঃখ", "dukkho should produce দুঃখ");

        let (shongstha, _) = transliterate_avro("shongstha");
        assert_eq!(shongstha, "সংস্থা", "shongstha should produce সংস্থা");

        // 13. Currency symbols: "tk" -> offers "৳"
        let (_, tk_cands) = transliterate_avro("tk");
        assert!(
            tk_cands.contains(&"৳".to_string()),
            "tk should offer ৳ as candidate"
        );

        // 14. Soft apostrophe escape: "k'kh" -> "কখ", while "kkh" -> "ক্ষ"
        let (k_kh, _) = transliterate_avro("k'kh");
        assert_eq!(k_kh, "কখ", "k'kh must escape conjunct and produce কখ");

        let (kkh, _) = transliterate_avro("kkh");
        assert_eq!(kkh, "ক্ষ", "kkh without escape must produce ক্ষ");

        // 15. ZWJ Ya-phala with Ra: "ryab" -> "র‍্যাব"
        let (ryab, _) = transliterate_avro("ryab");
        assert_eq!(
            ryab, "\u{09B0}\u{200D}\u{09CD}\u{09AF}\u{09BE}\u{09AC}",
            "ryab should produce র‍্যাব"
        );
    }

    #[test]
    fn test_loanwords_and_casual_slang_expansions() {
        // 1. English loanwords
        let (school, _) = transliterate_avro("school");
        assert_eq!(school, "স্কুল");

        let (table, _) = transliterate_avro("table");
        assert_eq!(table, "টেবিল");

        let (chair, _) = transliterate_avro("chair");
        assert_eq!(chair, "চেয়ার");

        let (doctor, _) = transliterate_avro("doctor");
        assert_eq!(doctor, "ডাক্তার");

        let (hospital, _) = transliterate_avro("hospital");
        assert_eq!(hospital, "হাসপাতাল");

        let (class, _) = transliterate_avro("class");
        assert_eq!(class, "ক্লাস");

        let (result, _) = transliterate_avro("result");
        assert_eq!(result, "রেজাল্ট");

        let (police, _) = transliterate_avro("police");
        assert_eq!(police, "পুলিশ");

        let (bank, _) = transliterate_avro("bank");
        assert_eq!(bank, "ব্যাংক");

        let (phone, _) = transliterate_avro("phone");
        assert_eq!(phone, "ফোন");

        // 2. Inflected English loanwords
        let (schoole, _) = transliterate_avro("schoole");
        assert_eq!(schoole, "স্কুলে");

        let (tableta, _) = transliterate_avro("tableta");
        assert_eq!(tableta, "টেবিলটা");

        let (chaire, _) = transliterate_avro("chaire");
        assert_eq!(chaire, "চেয়ারে");

        let (hospitaler, _) = transliterate_avro("hospitaler");
        assert_eq!(hospitaler, "হাসপাতালের");

        // 3. Chat shorthand & missing vowels
        let (krbo, _) = transliterate_avro("krbo");
        assert_eq!(krbo, "করবো");

        let (blbo, _) = transliterate_avro("blbo");
        assert_eq!(blbo, "বলবো");

        let (dkhbo, _) = transliterate_avro("dkhbo");
        assert_eq!(dkhbo, "দেখবো");

        let (shnbo, _) = transliterate_avro("shnbo");
        assert_eq!(shnbo, "শুনবো");

        let (prbo, _) = transliterate_avro("prbo");
        assert_eq!(prbo, "পারবো");

        let (bjhlm, _) = transliterate_avro("bjhlm");
        assert_eq!(bjhlm, "বুঝলাম");

        let (thk, _) = transliterate_avro("thk");
        assert_eq!(thk, "ঠিক");

        let (drkr, _) = transliterate_avro("drkr");
        assert_eq!(drkr, "দরকার");

        let (kkhn, _) = transliterate_avro("kkhn");
        assert_eq!(kkhn, "কখন");

        let (sbar, _) = transliterate_avro("sbar");
        assert_eq!(sbar, "সবার");

        // 4. Chat elongations
        let (haaa, _) = transliterate_avro("haaa");
        assert_eq!(haaa, "হ্যাঁ");

        let (naaa, _) = transliterate_avro("naaa");
        assert_eq!(naaa, "না");

        let (oneeeek, _) = transliterate_avro("oneeeek");
        assert_eq!(oneeeek, "অনেক");

        let (plzzz, _) = transliterate_avro("plzzz");
        assert_eq!(plzzz, "প্লিজ");

        let (valooo, _) = transliterate_avro("valooo");
        assert_eq!(valooo, "ভালো");

        let (acchaaaa, _) = transliterate_avro("acchaaaa");
        assert_eq!(acchaaaa, "আচ্ছা");

        let (sundorrrr, _) = transliterate_avro("sundorrrr");
        assert_eq!(sundorrrr, "সুন্দর");

        // 5. Clitics (-o, -i)
        let (amio, _) = transliterate_avro("amio");
        assert_eq!(amio, "আমিও");

        let (tumio, _) = transliterate_avro("tumio");
        assert_eq!(tumio, "তুমিও");

        let (sheo, _) = transliterate_avro("sheo");
        assert_eq!(sheo, "সেও");

        let (ekhono, _) = transliterate_avro("ekhono");
        assert_eq!(ekhono, "এখনো");

        let (amii, _) = transliterate_avro("amii");
        assert_eq!(amii, "আমিই");

        // 6. Sound laws / regional variants
        let (vabchi, _) = transliterate_avro("vabchi");
        assert_eq!(vabchi, "ভাবছি");

        let (zao, _) = transliterate_avro("zao");
        assert_eq!(zao, "যাও");

        let (somossa, _) = transliterate_avro("somossa");
        assert_eq!(somossa, "সমস্যা");
    }

    #[test]
    fn test_short_word_candidate_memory_immunity() {
        let (oi_res, oi_cands) = transliterate_avro("oi");
        assert_eq!(oi_res, "অই");
        assert!(oi_cands.contains(&"ওই".to_string()));
        assert!(oi_cands.contains(&"ঐ".to_string()));

        let (k_res, k_cands) = transliterate_avro("k");
        assert_eq!(k_res, "ক");
        assert!(k_cands.contains(&"ক".to_string()));
    }

    #[test]
    fn test_digit_units_quantifiers_and_ordinals() {
        // 1. Quantifiers: "10ta" -> "১০টা", "2ti" -> "২টি"
        let (ta_res, ta_cands) = transliterate_avro("10ta");
        assert_eq!(ta_res, "১০টা");
        assert!(ta_cands.contains(&"10ta".to_string()));

        let (ti_res, ti_cands) = transliterate_avro("2ti");
        assert_eq!(ti_res, "২টি");
        assert!(ti_cands.contains(&"2ti".to_string()));

        // 2. Currency: "500tk" -> "৫০০ টাকা", offers "৳৫০০"
        let (tk_res, tk_cands) = transliterate_avro("500tk");
        assert_eq!(tk_res, "৫০০ টাকা");
        assert!(tk_cands.contains(&"৳৫০০".to_string()));
        assert!(tk_cands.contains(&"500tk".to_string()));

        // 3. Units: "5kg" -> "৫ কেজি", "500gm" -> "৫০০ গ্রাম"
        let (kg_res, kg_cands) = transliterate_avro("5kg");
        assert_eq!(kg_res, "৫ কেজি");
        assert!(kg_cands.contains(&"5kg".to_string()));

        let (gm_res, _) = transliterate_avro("500gm");
        assert_eq!(gm_res, "৫০০ গ্রাম");

        // 4. Ordinals: "1st" -> "১ম", "2nd" -> "২য়", "3rd" -> "৩য়", "4th" -> "৪র্থ"
        let (st1, _) = transliterate_avro("1st");
        assert_eq!(st1, "১ম");

        let (nd2, _) = transliterate_avro("2nd");
        assert_eq!(nd2, "২য়");

        let (rd3, _) = transliterate_avro("3rd");
        assert_eq!(rd3, "৩য়");

        let (th4, _) = transliterate_avro("4th");
        assert_eq!(th4, "৪র্থ");
    }

    #[test]
    fn test_social_mentions_and_hashtags() {
        // 1. Mentions: "@siam" -> verbatim "@siam" primary, offers "@সিয়াম"
        let (mention_res, mention_cands) = transliterate_avro("@siam");
        assert_eq!(mention_res, "@siam");
        assert!(mention_cands.contains(&"@সিয়াম".to_string()));

        // 2. Hashtags: "#dhaka" -> verbatim "#dhaka" primary, offers "#ঢাকা"
        let (tag_res, tag_cands) = transliterate_avro("#dhaka");
        assert_eq!(tag_res, "#dhaka");
        assert!(tag_cands.contains(&"#ঢাকা".to_string()));
    }

    #[test]
    fn test_bilingual_hyphen_clitics() {
        // 1. "Facebook-e" -> "Facebook-এ"
        let (fb_res, fb_cands) = transliterate_avro("Facebook-e");
        assert_eq!(fb_res, "Facebook-এ");
        assert!(fb_cands.contains(&"Facebook-e".to_string()));

        // 2. "bkash-e" -> "bkash-এ"
        let (bk_res, bk_cands) = transliterate_avro("bkash-e");
        assert_eq!(bk_res, "bkash-এ");
        assert!(bk_cands.contains(&"bkash-e".to_string()));

        // 3. "post-ta" -> "post-টা"
        let (post_res, _) = transliterate_avro("post-ta");
        assert_eq!(post_res, "post-টা");

        // 4. "file-gulo" -> "file-গুলো"
        let (file_res, _) = transliterate_avro("file-gulo");
        assert_eq!(file_res, "file-গুলো");
    }

    #[test]
    fn test_khanda_ta_loanwords_and_dialect_verbs() {
        // 1. Khanda-Ta words
        let (utpadon, _) = transliterate_avro("utpadon");
        assert_eq!(utpadon, "উৎপাদন");

        let (chamatkar, _) = transliterate_avro("chamatkar");
        assert_eq!(chamatkar, "চমৎকার");

        let (utso, _) = transliterate_avro("utso");
        assert_eq!(utso, "উৎস");

        let (jogot, _) = transliterate_avro("jogot");
        assert_eq!(jogot, "জগৎ");

        let (utpotti, _) = transliterate_avro("utpotti");
        assert_eq!(utpotti, "উৎপত্তি");

        // 2. English loanwords with æ / অ্যা
        let (admin, _) = transliterate_avro("admin");
        assert_eq!(admin, "অ্যাডমিন");

        let (match_res, _) = transliterate_avro("match");
        assert_eq!(match_res, "ম্যাচ");

        let (gas, _) = transliterate_avro("gas");
        assert_eq!(gas, "গ্যাস");

        let (plan, _) = transliterate_avro("plan");
        assert_eq!(plan, "প্ল্যান");

        let (address, _) = transliterate_avro("address");
        assert_eq!(address, "অ্যাড্রেস");

        // 3. Dialect continuous verbs
        let (khaitasi, _) = transliterate_avro("khaitasi");
        assert_eq!(khaitasi, "খাইতাসি");

        let (kortasi, _) = transliterate_avro("kortasi");
        assert_eq!(kortasi, "করতাসি");
    }
}

