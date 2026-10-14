//! Avro Phonetic Transliteration Engine powered by `lekhani-parser` and `lekhani-ai`
//!
//! Provides deterministic Roman-to-Bengali transliteration according to
//! classic Avro Phonetic muscle memory standards using the upstream
//! zero-allocation L1 Trie grammar compiler from `lekhani-parser`.

use edit_distance::edit_distance;
use hashbrown::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::OnceLock;

static BILINGUAL_CLITIC_ENABLED: AtomicBool = AtomicBool::new(false);

/// Enable or disable bilingual clitic resolution globally (default: false)
pub fn set_bilingual_clitic_enabled(enabled: bool) {
    BILINGUAL_CLITIC_ENABLED.store(enabled, Ordering::Relaxed);
    lekhani_core::bilingual::set_bilingual_clitic_enabled(enabled);
}

/// Returns true if bilingual clitic resolution is currently enabled
pub fn is_bilingual_clitic_enabled() -> bool {
    BILINGUAL_CLITIC_ENABLED.load(Ordering::Relaxed)
}

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
        || has_override_word(input)
        || has_override_word(&lower)
        || db.lookup_override(input).is_some()
        || db.lookup_override(&lower).is_some()
        || lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&lower).is_some();

    let is_all_caps_acronym = is_all_caps && !has_override_word(&lower) && !is_bengali_dict_word;
    let can_promote_english_slot0 = is_all_caps_acronym
        || (is_recognized_english
            && !has_established_loanword
            && (input.len() >= 4
                || (crate::english::STATIC_SHORT_ENGLISH_WORDS.contains(&lower.as_str())
                    && !def_conv.contains('্'))));

    if can_promote_english_slot0 {
        if let Some(pos) = candidates.iter().position(|c| c.eq_ignore_ascii_case(input)) {
            let cand = candidates.remove(pos);
            candidates.insert(0, cand);
            return;
        }
    }

    if has_explicit_avro_intent(input) {
        // User explicitly specified exact Avro grammar (e.g. paR, kOno, c^ad, du:kho, hot`at).
        // Deterministic Avro preedit at Slot 0 must remain locked and immune to generic fuzzy overrides.
        return;
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
            inserted_idx = 1;
        }
        for (override_word, score) in overrides {
            let target_nfc = crate::probaho::nfc_normalize(override_word);
            if has_top_in_overrides
                && candidates.first().map(|s| crate::probaho::nfc_normalize(s) == target_nfc).unwrap_or(false)
            {
                continue;
            }
            if let Some(pos) = candidates.iter().position(|c| crate::probaho::nfc_normalize(c) == target_nfc) {
                let cand = candidates.remove(pos);
                candidates.insert(inserted_idx.min(candidates.len()), cand);
                inserted_idx += 1;
            } else if score >= 0.70 {
                candidates.insert(inserted_idx.min(candidates.len()), target_nfc.clone());
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

    // 3. Check phonetic variant equivalences (geminates, S-class, Sanskrit initial vowels, compound Reph)
    // Equivalences are alternative phonetic suggestions; they must sit at index 1+ and never clobber Slot 0.
    let equivs = get_phonetic_equivalence_candidates(input);
    let mut inserted_idx = 0;
    for eq_word in equivs {
        if let Some(pos) = candidates.iter().position(|c| c == &eq_word) {
            if pos == 0 {
                // Already the top candidate; keep at Slot 0 and advance inserted_idx!
                inserted_idx = inserted_idx.max(1);
                continue;
            }
            let cand = candidates.remove(pos);
            candidates.insert(inserted_idx, cand);
            inserted_idx += 1;
        } else {
            candidates.insert(inserted_idx, eq_word);
            inserted_idx += 1;
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

    // 2. S-class sibilant swap (strictly for words >= 4 characters to avoid corrupting basic syllables like sha, she, sho):
    // Initial sibilants: 'shomporko' -> 'somporko', 'shomossa' -> 'somossa'
    if lower.len() >= 4 {
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
    if !lower.starts_with("jj") {
        if let Some(stripped) = lower.strip_prefix('j') {
            variant_keys.push(format!("z{}", stripped));
        }
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
            && (db.trie.contains_exact(&parsed) || db.is_exact_dictionary_word(&parsed))
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

pub use lekhani_core::bilingual::{
    ascii_digits_to_bengali, bengali_digits_to_ascii, is_common_tech_acronym,
    resolve_digit_unit_candidates, COMMON_TECH_ACRONYMS,
};

/// Returns true if the token contains explicit, unambiguous Avro grammatical operators
/// or mid-word case distinctions that indicate intentional phonetic rule typing.
///
/// Executes in single-pass linear byte time (< 5 nanoseconds) with zero heap allocations.
#[inline]
pub fn has_explicit_avro_intent(input: &str) -> bool {
    lekhani_parser::has_explicit_avro_intent(input)
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

/// Resolves bilingual hyphenated enclitics attached to Latin words (e.g. "Facebook-e" -> "Facebook-এ")
pub fn resolve_bilingual_hyphen_clitic(input: &str) -> Option<(String, Vec<String>)> {
    if !input.contains('-') || input.starts_with('-') || input.ends_with('-') {
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

    if has_explicit_avro_intent(base) {
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

/// Resolves unhyphenated bilingual enclitics attached to Latin words (e.g. "WhatsAppe" -> "WhatsApp-এ", "Meetinger" -> "Meeting-এর")
pub fn resolve_unhyphenated_bilingual_clitic(input: &str) -> Option<(String, Vec<String>)> {
    if input.is_empty() || !input.is_ascii() || input.contains('-') || input.len() < 3 {
        return None;
    }

    if has_override_word(input) || has_override_word(&input.to_lowercase()) {
        return None;
    }

    let db = crate::session::get_core_database();
    let parser = get_avro_parser();
    let lower = input.to_lowercase();
    let def_conv = parser.convert(input);
    let def_lower_conv = parser.convert(&lower);

    // If the entire token already resolves to an exact valid Bengali dictionary word, do not decouple!
    if (!def_conv.is_empty() && (db.is_exact_dictionary_word(&def_conv) || db.trie.contains_exact(&def_conv)))
        || (!def_lower_conv.is_empty() && (db.is_exact_dictionary_word(&def_lower_conv) || db.trie.contains_exact(&def_lower_conv)))
    {
        return None;
    }

    // High-frequency clitic endings ordered by descending length
    const CLITIC_SUFFIXES: &[(&str, &str)] = &[
        ("gulor", "-গুলোর"),
        ("gulote", "-গুলোতে"),
        ("gulo", "-গুলো"),
        ("gula", "-গুলা"),
        ("guli", "-গুলি"),
        ("derke", "-দেরকে"),
        ("der", "-দের"),
        ("marse", " মারছে"),
        ("marlam", " মারলাম"),
        ("kora", " করা"),
        ("kore", " করে"),
        ("koro", " করো"),
        ("bhittik", "-ভিত্তিক"),
        ("shongkranto", "-সংক্রান্ত"),
        ("er", "-এর"),
        ("te", "-তে"),
        ("ta", "-টা"),
        ("ti", "-টি"),
        ("ke", "-কে"),
        ("ra", "-রা"),
        ("re", "-রে"),
        ("e", "-এ"),
        ("o", "-ও"),
        ("i", "-ই"),
    ];

    for &(suf, b_clitic) in CLITIC_SUFFIXES {
        if lower.ends_with(suf) && input.len() > suf.len() {
            let base = &input[..input.len() - suf.len()];
            if base.is_empty() || !base.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
                continue;
            }

            if has_explicit_avro_intent(base) {
                continue;
            }

            let is_all_upper_acronym = base.len() >= 2 && base.chars().all(|c| c.is_ascii_uppercase());
            let is_camel_or_title = base.len() >= 3
                && base.chars().next().is_some_and(|c| c.is_ascii_uppercase())
                && base.chars().skip(1).all(|c| c.is_ascii_lowercase());
            let is_recognized_english = base.len() >= 3 && crate::english::is_recognized_english_word(base);
            let is_known_brand_or_tech = matches!(
                base.to_lowercase().as_str(),
                "whatsapp" | "facebook" | "google" | "youtube" | "instagram" | "twitter" | "tiktok"
                | "zoom" | "skype" | "android" | "windows" | "linux" | "apple" | "microsoft"
                | "bkash" | "nagad" | "rocket" | "upay" | "uber" | "pathao" | "daraz" | "foodpanda"
                | "netflix" | "spotify" | "telegram" | "signal" | "gmail" | "yahoo" | "wifi"
                | "bluetooth" | "laptop" | "desktop" | "monitor" | "server" | "cloud" | "bug"
                | "meeting" | "post" | "link" | "call" | "group" | "chat" | "message" | "status"
                | "story" | "video" | "audio" | "photo" | "camera" | "file" | "app" | "code"
                | "commit" | "branch" | "repo" | "issue" | "pr" | "merge" | "test" | "fail"
                | "pass" | "click" | "share" | "like" | "comment" | "feed" | "stream" | "cash"
                | "bank" | "card" | "bill" | "offer"
            );

            let is_valid_stem = if suf.len() == 1 {
                is_all_upper_acronym || is_camel_or_title || is_known_brand_or_tech
            } else {
                is_all_upper_acronym || is_camel_or_title || is_recognized_english || is_known_brand_or_tech
            };

            if is_valid_stem {
                let code_mix_primary = format!("{}{}", base, b_clitic);
                let mut primary = code_mix_primary.clone();
                let mut candidates = vec![code_mix_primary];

                // Slot 1: Try phonetic Bengali loanword if available
                let clean_base_lower = base.to_lowercase();
                if let Some((bn_loan, _)) = lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&clean_base_lower) {
                    let unified_clitic = match suf {
                        "e" => {
                            if bn_loan.ends_with('া') || bn_loan.ends_with('ি') || bn_loan.ends_with('ী')
                                || bn_loan.ends_with('ু') || bn_loan.ends_with('ূ') || bn_loan.ends_with('ে') || bn_loan.ends_with('ো')
                            {
                                "য়ে"
                            } else {
                                "ে"
                            }
                        }
                        "er" => {
                            if bn_loan.ends_with('া') || bn_loan.ends_with('ি') || bn_loan.ends_with('ী')
                                || bn_loan.ends_with('ু') || bn_loan.ends_with('ূ') || bn_loan.ends_with('ে') || bn_loan.ends_with('ো')
                                || bn_loan.ends_with('ং')
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
                        "gulor" => "গুলোর",
                        "der" => "দের",
                        "ra" => "রা",
                        "i" => "ই",
                        "o" => "ও",
                        _ => "",
                    };
                    if !unified_clitic.is_empty() {
                        let bn_cand = format!("{}{}", bn_loan, unified_clitic);
                        if base.chars().all(|c| c.is_lowercase()) && !is_all_upper_acronym {
                            primary = bn_cand.clone();
                            if !candidates.contains(&bn_cand) {
                                candidates.insert(0, bn_cand);
                            }
                        } else if !candidates.contains(&bn_cand) {
                            candidates.push(bn_cand);
                        }
                    }
                    let bn_hyphen_cand = format!("{}-{}", bn_loan, b_clitic.trim_start_matches('-').trim());
                    if !candidates.contains(&bn_hyphen_cand) {
                        candidates.push(bn_hyphen_cand);
                    }
                }

                // Add Avro transliterated base if distinct
                let (base_bn, _) = transliterate_avro(base);
                if !base_bn.is_empty() && base_bn != base {
                    let avro_cand = format!("{}{}", base_bn, b_clitic);
                    if !candidates.contains(&avro_cand) {
                        candidates.push(avro_cand);
                    }
                }

                // Raw input preservation
                if !candidates.contains(&input.to_string()) {
                    candidates.push(input.to_string());
                }

                return Some((primary, candidates));
            }
        }
    }

    None
}

/// Generalized morphological affixes & compounding engine.
/// Dynamically resolves Tatshama academic suffixes, Natva Vidhana sound mutations,
/// and systematic colloquial/dialectal verb conjugations without hardcoded word lists.
pub fn resolve_generative_morphology(input: &str) -> Option<(String, Vec<String>)> {
    if input.is_empty() || !input.is_ascii() || has_explicit_avro_intent(input) {
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
        if stem.len() >= 2 && stem.chars().all(|c| c.is_ascii_alphabetic()) {
            let stem_bn = get_avro_parser().convert(stem);
            let word = format!("{}ুম", stem_bn);
            return Some((word.clone(), vec![word]));
        }
    }
    // 1st-person dialect future: -mu -> -মু (e.g. ja-mu -> যামু, kha-mu -> খামু)
    if let Some(stem) = lower.strip_suffix("mu") {
        if stem.len() >= 2 && stem.chars().all(|c| c.is_ascii_alphabetic()) {
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
        || (!input.is_empty()
            && input.chars().all(|c| c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c) || c == '.' || c == ',')
            && input.chars().any(|c| c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c)))
        || has_explicit_avro_intent(input)
        || resolve_digit_unit_candidates(input).is_some()
        || (is_bilingual_clitic_enabled()
            && (resolve_bilingual_hyphen_clitic(input).is_some()
                || resolve_unhyphenated_bilingual_clitic(input).is_some()))
        || resolve_generative_morphology(input).is_some()
}

// ──────────────────────────────────────────────────────────────────────────────
// Transliteration Pipeline Stages (Modular & Decomposed)
// ──────────────────────────────────────────────────────────────────────────────

fn stage_user_autocorrect(
    input: &str,
    lower: &str,
    db: &lekhani_core::phonetic::PhoneticDatabase,
) -> Option<(String, Vec<String>)> {
    if let Ok(uac) = db.user_autocorrect.read() {
        if let Some(replacement) = uac.get(input).or_else(|| uac.get(lower)) {
            let primary = replacement.clone();
            let mut candidates = vec![primary.clone()];
            let parser = get_avro_parser();
            let def = parser.convert(input);
            if def != primary {
                candidates.push(def);
            }
            append_prefix_matches(input, &primary, &mut candidates);
            return Some((primary, candidates));
        }
    }
    None
}

fn stage_social_mentions(input: &str) -> Option<(String, Vec<String>)> {
    if let Some((primary, mut candidates)) = resolve_social_mention_or_hashtag(input) {
        let def_pure = get_avro_parser().convert(input);
        if !def_pure.is_empty() && !candidates.contains(&def_pure) {
            candidates.push(def_pure);
        }
        return Some((primary, candidates));
    }
    None
}

fn stage_digit_units_and_numbers(input: &str) -> Option<(String, Vec<String>)> {
    // Fast-path 2: Digit units, quantifiers, currency, and ordinals (e.g. 10ta -> ১০টা, 500tk -> ৫০০ টাকা, 1st -> ১ম)
    if let Some((primary, mut candidates)) = resolve_digit_unit_candidates(input) {
        let def_pure = get_avro_parser().convert(input);
        if !def_pure.is_empty() && !candidates.contains(&def_pure) {
            candidates.push(def_pure);
        }
        return Some((primary, candidates));
    }

    // Fast-path 2b: Pure numeric literals and decimal/comma numbers (e.g. "12486" -> "১২৪৮৬", "8.4" -> "৮.৪")
    if !input.is_empty()
        && input.chars().all(|c| c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c) || c == '.' || c == ',')
        && input.chars().any(|c| c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c))
    {
        let b_num = ascii_digits_to_bengali(input);
        let a_num = bengali_digits_to_ascii(input);
        let candidates = if b_num == a_num {
            vec![b_num.clone()]
        } else {
            vec![b_num.clone(), a_num]
        };
        return Some((b_num, candidates));
    }

    None
}

fn stage_bilingual_clitics(input: &str) -> Option<(String, Vec<String>)> {
    if is_bilingual_clitic_enabled() {
        if let Some((primary, mut candidates)) = resolve_bilingual_hyphen_clitic(input) {
            let def_pure = get_avro_parser().convert(input);
            if !def_pure.is_empty() && !candidates.contains(&def_pure) {
                candidates.push(def_pure);
            }
            return Some((primary, candidates));
        }

        if let Some((primary, mut candidates)) = resolve_unhyphenated_bilingual_clitic(input) {
            let def_pure = get_avro_parser().convert(input);
            if !def_pure.is_empty() && !candidates.contains(&def_pure) {
                candidates.push(def_pure);
            }
            return Some((primary, candidates));
        }
    }
    None
}

fn stage_explicit_avro_intent(
    input: &str,
    lower: &str,
    db: &lekhani_core::phonetic::PhoneticDatabase,
) -> Option<(String, Vec<String>)> {
    // Fast-path 3b: Explicit Avro Grammar & Intent Fast-Path
    if has_explicit_avro_intent(input) {
        let parser = get_avro_parser();
        let def = parser.convert(input);
        if !def.is_empty() {
            let mut candidates = vec![def.clone()];
            let lower_def = parser.convert(lower);
            if !lower_def.is_empty() && lower_def != def && !candidates.contains(&lower_def) {
                candidates.push(lower_def);
            }
            if let Some(cands) = db.lookup_override(lower) {
                for (w, _) in cands {
                    let w_str = w.to_string();
                    if !candidates.contains(&w_str) {
                        candidates.push(w_str);
                    }
                }
            }
            if !candidates.iter().any(|c| c.eq_ignore_ascii_case(input)) {
                candidates.push(input.to_string());
            }
            return Some((def, candidates));
        }
    }
    None
}

fn stage_generative_morphology(input: &str) -> Option<(String, Vec<String>)> {
    if let Some((primary, mut candidates)) = resolve_generative_morphology(input) {
        let def_pure = get_avro_parser().convert(input);
        if !def_pure.is_empty() && !candidates.contains(&def_pure) {
            candidates.push(def_pure);
        }
        return Some((primary, candidates));
    }
    None
}

fn stage_short_input_phonetic(
    input: &str,
    lower: &str,
    def: &str,
    db: &lekhani_core::phonetic::PhoneticDatabase,
) -> (String, Vec<String>) {
    let mut short_candidates = Vec::with_capacity(12);

    // 1. Direct phonetic transliteration is always the primary preedit for short inputs
    let primary = def.to_string();
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
    if let Some(words) = get_override_words(input).or_else(|| get_override_words(lower)) {
        for w in words {
            if !short_candidates.contains(&w) {
                short_candidates.push(w);
            }
        }
    }
    if let Some(shorthand) = db.shorthand.get(input).or_else(|| db.shorthand.get(lower)) {
        if !short_candidates.iter().any(|c| c == shorthand) {
            short_candidates.push(shorthand.clone());
        }
    }

    // Common word prefixes sorted by confidence score descending, then length ascending
    let max_len = if lower.len() == 1 { 3 } else { 6 };
    let mut prefix_entries = db.lookup_override_prefix(lower, max_len, 256);
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
    if !short_candidates.iter().any(|c| c == def) {
        short_candidates.push(def.to_string());
    }

    (primary, short_candidates)
}

/// Context object encapsulating the environment and inputs for phonetic transliteration.
pub struct AvroRequestContext<'a> {
    pub input: &'a str,
    pub context: &'a [&'a str],
    pub lower: &'a str,
    pub def: &'a str,
    pub db: &'a lekhani_core::phonetic::PhoneticDatabase,
    pub remembered_choice: Option<String>,
    pub autocorrect_replacement: Option<&'a str>,
    pub is_explicit_common: bool,
}

/// Accumulator encapsulating deduplication, promotion, and ordered slot manipulation.
pub struct CandidateAccumulator {
    items: Vec<String>,
}

impl CandidateAccumulator {
    pub fn new(mut initial: Vec<String>) -> Self {
        if initial.capacity() < 8 {
            initial.reserve(8usize.saturating_sub(initial.len()));
        }
        Self { items: initial }
    }

    pub fn with_capacity(cap: usize) -> Self {
        Self { items: Vec::with_capacity(cap.max(8)) }
    }

    pub fn contains(&self, text: &str) -> bool {
        self.items.iter().any(|c| c == text)
    }

    pub fn first(&self) -> Option<&str> {
        self.items.first().map(|s| s.as_str())
    }

    pub fn len(&self) -> usize {
        self.items.len()
    }

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    pub fn push_unique(&mut self, text: String) {
        if !self.contains(&text) {
            self.items.push(text);
        }
    }

    pub fn promote_to_front(&mut self, text: &str) {
        if let Some(pos) = self.items.iter().position(|c| c == text) {
            let item = self.items.remove(pos);
            self.items.insert(0, item);
        } else {
            self.items.insert(0, text.to_string());
        }
    }

    pub fn insert_or_move(&mut self, target_idx: usize, text: String) {
        if let Some(pos) = self.items.iter().position(|c| c == &text) {
            self.items.remove(pos);
        }
        let pos = target_idx.min(self.items.len());
        self.items.insert(pos, text);
    }

    pub fn swap(&mut self, a: usize, b: usize) {
        if a < self.items.len() && b < self.items.len() {
            self.items.swap(a, b);
        }
    }

    pub fn as_slice(&self) -> &[String] {
        &self.items
    }

    pub fn as_mut_slice(&mut self) -> &mut [String] {
        &mut self.items
    }

    pub fn inner_mut(&mut self) -> &mut Vec<String> {
        &mut self.items
    }

    pub fn into_vec(self) -> Vec<String> {
        self.items
    }
}

fn apply_verbatim_and_english_promotion(
    ctx: &AvroRequestContext<'_>,
    acc: &mut CandidateAccumulator,
    selected_idx: &mut usize,
) {
    let is_verbatim_candidate_needed = ctx.input.is_ascii()
        && ctx.input.len() >= 2
        && !acc.as_slice().iter().any(|c| c.eq_ignore_ascii_case(ctx.input));

    let is_all_caps = ctx.input.len() >= 2
        && ctx.input.chars().any(|c| c.is_ascii_alphabetic())
        && ctx.input
            .chars()
            .all(|c| c.is_ascii_uppercase() || !c.is_alphabetic());
    let is_recognized_english = crate::english::is_recognized_english_word(ctx.input);
    let def_lower_conv = get_avro_parser().convert(ctx.lower);
    let is_bengali_dict_word = (!ctx.def.is_empty() && ctx.db.is_exact_dictionary_word(ctx.def))
        || (!def_lower_conv.is_empty() && ctx.db.is_exact_dictionary_word(&def_lower_conv));

    let has_established_loanword = ctx.is_explicit_common
        || is_bengali_dict_word
        || ctx.db.lookup_override(ctx.input).is_some()
        || ctx.db.lookup_override(ctx.lower).is_some()
        || lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(ctx.lower).is_some();

    let is_all_caps_acronym = is_common_tech_acronym(ctx.input)
        || (is_all_caps && !has_override_word(ctx.lower));
    let can_promote_english_slot0 = !has_explicit_avro_intent(ctx.input)
        && (is_all_caps_acronym
            || (is_recognized_english
                && !has_established_loanword
                && (ctx.input.len() >= 4
                    || (crate::english::STATIC_SHORT_ENGLISH_WORDS.contains(&ctx.lower)
                        && !ctx.def.contains('্')))));

    if can_promote_english_slot0 {
        if let Some(pos) = acc.as_slice().iter().position(|c| c.eq_ignore_ascii_case(ctx.input)) {
            let item = acc.as_slice()[pos].clone();
            acc.promote_to_front(&item);
        } else {
            acc.promote_to_front(ctx.input);
        }
        *selected_idx = 0;
    } else if is_verbatim_candidate_needed {
        let insert_pos = acc.len().min(1);
        acc.insert_or_move(insert_pos, ctx.input.to_string());
    }
}

fn apply_anvaya_and_overrides(
    ctx: &AvroRequestContext<'_>,
    concordant_cand: Option<&str>,
    acc: &mut CandidateAccumulator,
    selected_idx: &mut usize,
) {
    if let Some(words) = get_override_words(ctx.input).or_else(|| get_override_words(ctx.lower)) {
        let start_idx = if let Some(concordant) = concordant_cand {
            let conc_nfc = crate::probaho::nfc_normalize(concordant);
            acc.insert_or_move(1.min(acc.len()), conc_nfc);
            0
        } else {
            0
        };

        for (i, w) in words.into_iter().enumerate() {
            acc.insert_or_move((start_idx + i).min(acc.len()), w);
        }
        *selected_idx = 0;
    } else if let Some(ref collapsed) = collapse_elongation(ctx.lower) {
        let words = get_override_words(collapsed.as_str());

        if let Some(words) = words {
            for (i, w) in words.into_iter().enumerate() {
                acc.insert_or_move(i.min(acc.len()), w);
            }
            *selected_idx = 0;
        } else if let Some((bn_loan, _)) =
            lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(collapsed)
        {
            let ws = bn_loan.to_string();
            acc.promote_to_front(&ws);
            *selected_idx = 0;
        }
    }
}

fn apply_inflections_and_geminates(
    ctx: &AvroRequestContext<'_>,
    concordant_cand: Option<&str>,
    acc: &mut CandidateAccumulator,
    selected_idx: &mut usize,
) {
    // 2b. If the user explicitly typed an inflection ending in 'bo' or 'cho', prioritize matching 'ো'-inflected form
    if !ctx.is_explicit_common
        && (ctx.lower.ends_with("bo") || ctx.lower.ends_with("cho"))
        && !acc.is_empty()
    {
        if let Some(pos) = acc.as_slice().iter().position(|c| c.ends_with('ো')) {
            let o_cand = acc.as_slice()[pos].clone();
            acc.promote_to_front(&o_cand);
            *selected_idx = 0;
        } else if ctx.def.ends_with('ো') {
            acc.promote_to_front(ctx.def);
            *selected_idx = 0;
        }
    }

    // 2c. Ensure def (deterministic Avro parser output) is preserved in candidates as "Force Avro"
    let has_double_consonant = ctx.lower.contains("cc")
        || ctx.lower.contains("kk")
        || ctx.lower.contains("tt")
        || ctx.lower.contains("dd")
        || ctx.lower.contains("bb")
        || ctx.lower.contains("pp")
        || ctx.lower.contains("mm")
        || ctx.lower.contains("nn")
        || ctx.lower.contains("ll")
        || ctx.lower.contains("ss")
        || ctx.lower.contains("jj")
        || ctx.lower.contains("gg");

    if !ctx.def.is_empty() && acc.first() != Some(ctx.def) {
        let is_aspirated_geminate_mismatch = (ctx.lower.contains("cch") && ctx.def.contains("চ্ছ") && acc.first().map(|c| c.contains("চ্চ") && !c.contains("চ্ছ")).unwrap_or(false))
            || (ctx.lower.contains("kkh") && ctx.def.contains("ক্ষ") && acc.first().map(|c| c.contains("ক্ক") && !c.contains("ক্ষ")).unwrap_or(false))
            || (ctx.lower.contains("tth") && ctx.def.contains("ত্থ") && acc.first().map(|c| c.contains("ত্ত") && !c.contains("ত্থ")).unwrap_or(false));

        let is_collapsed_geminate = (has_double_consonant || is_aspirated_geminate_mismatch)
            && ctx.def.contains('্')
            && !ctx.is_explicit_common
            && (is_aspirated_geminate_mismatch || acc.first().map(|c| {
                !c.contains('্')
                    || (!ctx.lower.contains('y') && !ctx.lower.contains('z') && !ctx.lower.contains("ri") && c.contains('য') && !ctx.def.contains('য'))
            }).unwrap_or(false));

        let target_idx = if is_collapsed_geminate { 0 } else { 1.min(acc.len()) };
        acc.insert_or_move(target_idx, ctx.def.to_string());
        if is_collapsed_geminate {
            *selected_idx = 0;
        }
    }

    // 2c2. Honorific Agreement Invariant: Concordant verbs sit at Slot 1 (index 1), NEVER overriding Slot 0
    if let Some(concordant) = concordant_cand {
        let conc_nfc = crate::probaho::nfc_normalize(concordant);
        if acc.len() > 1 && acc.as_slice()[0] == conc_nfc {
            acc.swap(0, 1);
        } else if let Some(pos) = acc.as_slice().iter().position(|c| crate::probaho::nfc_normalize(c) == conc_nfc) {
            if pos != 1 {
                acc.insert_or_move(1.min(acc.len()), conc_nfc);
            }
        } else {
            acc.insert_or_move(1.min(acc.len()), conc_nfc);
        }
    }
}

fn apply_case_fidelity(
    ctx: &AvroRequestContext<'_>,
    acc: &mut CandidateAccumulator,
    selected_idx: &mut usize,
) {
    let parser = get_avro_parser();
    let has_explicit_avro_case = ctx.input
        .chars()
        .any(|c| c.is_ascii_uppercase() && "OIUDGJNRSTYZ".contains(c));
    let has_explicit_intent = has_explicit_avro_intent(ctx.input);
    let is_all_caps = ctx.input.chars().count() >= 2 && ctx.input.chars().all(|c| c.is_ascii_uppercase());
    if is_all_caps {
        let is_common_bengali_all_caps = !is_common_tech_acronym(ctx.input) && has_override_word(ctx.lower);
        if is_common_bengali_all_caps {
            let resolved_lower = if let Some(ac) = ctx.db.autocorrect.get(ctx.lower) {
                ac.clone()
            } else if let Some(words) = get_override_words(ctx.lower) {
                words.into_iter().next().unwrap_or_default()
            } else {
                parser.convert(ctx.lower)
            };
            if !resolved_lower.is_empty() {
                acc.promote_to_front(&resolved_lower);
                if !ctx.def.is_empty() && !acc.contains(ctx.def) {
                    acc.insert_or_move(1.min(acc.len()), ctx.def.to_string());
                }
                *selected_idx = 0;
            }
        } else {
            acc.promote_to_front(ctx.input);
            *selected_idx = 0;
        }
    } else if (has_explicit_avro_case || has_explicit_intent) && !ctx.def.is_empty() {
        let is_titlecase = !has_explicit_intent
            && ctx.input.chars().next().is_some_and(|c| c.is_ascii_uppercase())
            && ctx.input.chars().skip(1).all(|c| c.is_ascii_lowercase());

        let resolved_lower = if let Some(ac) = ctx.db.autocorrect.get(ctx.lower) {
            ac.clone()
        } else if let Some(words) = get_override_words(ctx.lower) {
            words.into_iter().next().unwrap_or_default()
        } else {
            parser.convert(ctx.lower)
        };

        if is_titlecase {
            let is_common_lower = has_override_word(ctx.lower);
            let freq_lower = get_word_frequency(resolved_lower.as_str());
            let freq_def = get_word_frequency(ctx.def);

            if is_common_lower
                || freq_lower >= freq_def * 3
                || (!ctx.db.is_exact_dictionary_word(ctx.def)
                    && ctx.db.is_exact_dictionary_word(&resolved_lower))
            {
                acc.promote_to_front(&resolved_lower);
                if !acc.contains(ctx.def) {
                    acc.insert_or_move(1.min(acc.len()), ctx.def.to_string());
                }
                *selected_idx = 0;
            } else {
                acc.promote_to_front(ctx.def);
                *selected_idx = 0;
            }
        } else {
            acc.promote_to_front(ctx.def);
            *selected_idx = 0;
        }
    }
}

fn apply_typo_corrections(
    ctx: &AvroRequestContext<'_>,
    acc: &mut CandidateAccumulator,
) {
    let parser = get_avro_parser();
    let mut common_typos: Vec<(String, f32)> = Vec::new();
    let mut dict_typos: Vec<String> = Vec::new();
    let is_exact_match = ctx.is_explicit_common
        || (ctx.db.is_exact_dictionary_word(ctx.def) && ctx.db.get_frequency(ctx.def) >= 1000);
    if !is_exact_match && ctx.lower.len() >= 3 && ctx.lower.len() <= 8 {
        let chars: Vec<char> = ctx.lower.chars().collect();
        for (i, &ch) in chars.iter().enumerate() {
            for &adj in crate::english::get_qwerty_adjacent_keys(ch) {
                let mut sub = chars.clone();
                sub[i] = adj;
                let cand_key: String = sub.into_iter().collect();
                if let Some(ov) = ctx.db.lookup_override(cand_key.as_str()) {
                    for (ws, score) in ov {
                        let ws_str = ws.to_string();
                        if !acc.contains(&ws_str) && !common_typos.iter().any(|(w, _)| w == &ws_str) {
                            common_typos.push((ws_str, score));
                        }
                    }
                } else if let Some(ac) = ctx.db.autocorrect.get(&cand_key) {
                    if !acc.contains(ac) && !common_typos.iter().any(|(w, _)| w == ac) {
                        common_typos.push((ac.clone(), 0.95));
                    }
                } else if dict_typos.len() < 3 {
                    let conv = parser.convert(&cand_key);
                    if !conv.is_empty()
                        && ctx.db.is_exact_dictionary_word(&conv)
                        && !acc.contains(&conv)
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
                if let Some(ov) = ctx.db.lookup_override(swap_key.as_str()) {
                    for (ws, score) in ov {
                        let ws_str = ws.to_string();
                        if !acc.contains(&ws_str) && !common_typos.iter().any(|(w, _)| w == &ws_str) {
                            common_typos.push((ws_str, score));
                        }
                    }
                } else if let Some(ac) = ctx.db.autocorrect.get(&swap_key) {
                    if !acc.contains(ac) && !common_typos.iter().any(|(w, _)| w == ac) {
                        common_typos.push((ac.clone(), 0.95));
                    }
                } else if dict_typos.len() < 3 {
                    let conv = parser.convert(&swap_key);
                    if !conv.is_empty()
                        && ctx.db.is_exact_dictionary_word(&conv)
                        && !acc.contains(&conv)
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
    if !ctx.input.contains('^') && ctx.lower.len() >= 3 && ctx.lower.len() <= 8 {
        let chars: Vec<char> = ctx.lower.chars().collect();
        for (i, &ch) in chars.iter().enumerate() {
            if "aeiou".contains(ch) {
                let mut with_caret = String::with_capacity(ctx.lower.len() + 1);
                with_caret.push_str(&ctx.lower[..i]);
                with_caret.push('^');
                with_caret.push_str(&ctx.lower[i..]);
                let conv = parser.convert(&with_caret);
                if conv.contains('ঁ')
                    && ctx.db.is_exact_dictionary_word(&conv)
                    && !acc.contains(&conv)
                {
                    let insert_pos = 1.min(acc.len());
                    acc.insert_or_move(insert_pos, conv);
                }

                // Also check 'ch' -> 'c' substitution with caret (e.g. "chad" -> "c^ad" -> "চাঁদ")
                if ctx.lower.starts_with("ch") && i == 2 {
                    let mut c_caret = String::with_capacity(ctx.lower.len());
                    c_caret.push_str("c^");
                    c_caret.push_str(&ctx.lower[2..]);
                    let conv_c = parser.convert(&c_caret);
                    if conv_c.contains('ঁ')
                        && ctx.db.is_exact_dictionary_word(&conv_c)
                        && !acc.contains(&conv_c)
                    {
                        let insert_pos = 1.min(acc.len());
                        acc.insert_or_move(insert_pos, conv_c);
                    }
                }
            }
        }
    }

    // Place typo corrections AFTER def to preserve primary phonetic output at index 0
    // and Force Avro at index 1, offering typo recoveries as optional strip choices.
    let insert_offset = if acc.len() > 1 && acc.as_slice()[1] == ctx.def {
        2
    } else {
        1
    };
    if !ctx.is_explicit_common && !typo_corrections.is_empty() {
        for (idx, corr) in typo_corrections.into_iter().enumerate() {
            if idx < 2 {
                let target = (insert_offset + idx).min(acc.len());
                acc.insert_or_move(target, corr);
            } else {
                acc.push_unique(corr);
            }
        }
    } else {
        for corr in typo_corrections {
            acc.push_unique(corr);
        }
    }
}

fn extract_typo_rescue_slot3(candidates: &mut Vec<String>) {
    let slot1 = candidates.get(1).cloned();
    let typo_rescue: Option<String> = candidates
        .iter()
        .skip(3)
        .find(|c| {
            let c_chars: Vec<char> = c.chars().collect();
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
        if let Some(pos) = candidates.iter().position(|c| c == &rescue) {
            candidates.remove(pos);
        }
        let target = 3.min(candidates.len());
        candidates.insert(target, rescue);
    }
}

fn stage_phonetic_engine(ctx: &AvroRequestContext<'_>) -> (String, Vec<String>) {
    let mut candidate_memory = HashMap::new();
    if let Some(ref choice) = ctx.remembered_choice {
        candidate_memory.insert(ctx.input.to_string(), choice.clone());
    }

    let sugg_mutex = crate::session::get_phonetic_suggestion();
    let (raw_candidates, mut selected_idx) = {
        let mut sugg = match sugg_mutex.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        sugg.suggest_with_multi_context(ctx.input, ctx.context, false, true, &candidate_memory)
    };

    let mut acc = CandidateAccumulator::new(raw_candidates);
    if acc.is_empty() {
        acc.push_unique(ctx.def.to_string());
    }

    // 1. Verbatim Latin escape hatch & English/acronym promotion
    apply_verbatim_and_english_promotion(ctx, &mut acc, &mut selected_idx);

    // 2. Pure literal Avro conversion retention
    if !ctx.def.is_empty() && !acc.contains(ctx.def) {
        let insert_pos = acc.len().min(2);
        acc.insert_or_move(insert_pos, ctx.def.to_string());
    }

    // Digits / Currency Symbols dual candidate surfacing
    let is_numeric_or_currency =
        ctx.input.chars().all(|c| c.is_ascii_digit()) || ctx.input.starts_with('$');
    if is_numeric_or_currency && !acc.contains(ctx.input) {
        let insert_pos = acc.len().min(1);
        acc.insert_or_move(insert_pos, ctx.input.to_string());
    }

    // Apply deferred bundled system autocorrect if active
    if let Some(replacement) = ctx.autocorrect_replacement {
        acc.promote_to_front(replacement);
        selected_idx = 0;
    }

    // Anvaya concordant verb
    let anvaya_state = lekhani_ai::AnvayaAgreementEngine::extract_state(ctx.context);
    let concordant_cand = if anvaya_state.honorific_tier != lekhani_ai::HonorificRegister::Neutral {
        lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(ctx.input, anvaya_state.honorific_tier)
            .or_else(|| lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(ctx.lower, anvaya_state.honorific_tier))
    } else {
        None
    };

    // Sub-stage: Anvaya & overrides / elongation collapse
    apply_anvaya_and_overrides(ctx, concordant_cand, &mut acc, &mut selected_idx);

    // Sub-stage: bo/cho inflections & geminates
    apply_inflections_and_geminates(ctx, concordant_cand, &mut acc, &mut selected_idx);

    // Sub-stage: Case fidelity & all-caps/title-case
    apply_case_fidelity(ctx, &mut acc, &mut selected_idx);

    // Sub-stage: Typo corrections & implicit Chandra Bindu
    apply_typo_corrections(ctx, &mut acc);

    // Primary selection
    let has_explicit_avro_case = ctx.input
        .chars()
        .any(|c| c.is_ascii_uppercase() && "OIUDGJNRSTYZ".contains(c));
    let has_explicit_intent = has_explicit_avro_intent(ctx.input);

    let primary = if let Some(ref choice) = ctx.remembered_choice {
        if !has_explicit_intent && !has_explicit_avro_case {
            acc.promote_to_front(choice);
            choice.clone()
        } else if !acc.is_empty() {
            acc.as_slice()[0].clone()
        } else {
            choice.clone()
        }
    } else if selected_idx < acc.len() && selected_idx > 0 && !has_explicit_intent && !has_explicit_avro_case {
        let p = acc.as_slice()[selected_idx].clone();
        acc.promote_to_front(&p);
        p
    } else if !acc.is_empty() {
        acc.as_slice()[0].clone()
    } else {
        String::new()
    };

    // Slot 3: Typo rescue extraction
    extract_typo_rescue_slot3(acc.inner_mut());

    // Prefix trie expansion
    append_prefix_matches(ctx.input, &primary, acc.inner_mut());

    if ctx.remembered_choice.is_none() {
        prioritize_common_or_override_candidate(ctx.input, acc.inner_mut());
    }
    let raw_primary = acc.first().map(|s| s.to_string()).unwrap_or(primary);
    let primary = crate::probaho::nfc_normalize(&raw_primary);

    let mut normalized_candidates = Vec::with_capacity(acc.len() + 1);
    for c in acc.into_vec() {
        let norm = crate::probaho::nfc_normalize(&c);
        if !normalized_candidates.contains(&norm) {
            normalized_candidates.push(norm);
        }
    }

    // Guarantee muscle-memory invariant: pure Avro conversion is NEVER dropped
    let def_pure = get_avro_parser().convert(ctx.input);
    let def_norm = crate::probaho::nfc_normalize(&def_pure);
    if !def_norm.is_empty() && !normalized_candidates.contains(&def_norm) {
        let target_idx = 1.min(normalized_candidates.len());
        normalized_candidates.insert(target_idx, def_norm);
    }

    (primary, normalized_candidates)
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

    // Stage 1: User explicit autocorrect shortcuts
    if let Some(res) = stage_user_autocorrect(input, &lower, db) {
        return res;
    }

    // Stage 2: Social mentions & hashtags
    if let Some(res) = stage_social_mentions(input) {
        return res;
    }

    // Stage 3: Digit units, currency, ordinals & numerics
    if let Some(res) = stage_digit_units_and_numbers(input) {
        return res;
    }

    // Stage 4: Bilingual clitics (if enabled)
    if let Some(res) = stage_bilingual_clitics(input) {
        return res;
    }

    // Stage 5: Explicit Avro Grammar & Intent Fast-Path
    if let Some(res) = stage_explicit_avro_intent(input, &lower, db) {
        return res;
    }

    // Stage 6: Generative morphology & dialectal verb conjugations
    if let Some(res) = stage_generative_morphology(input) {
        return res;
    }

    let parser = get_avro_parser();
    let def = parser.convert(input);

    // Stage 7: Short input phonetic (< 2 chars)
    if input.chars().count() <= 2 {
        return stage_short_input_phonetic(input, &lower, &def, db);
    }

    // Candidate memory & system autocorrect lookup for general inputs
    let mut remembered_choice: Option<String> = None;
    if let Ok(learner) = db.learner.read() {
        if let Some(error_override) = learner
            .lookup_input_error(input)
            .or_else(|| learner.lookup_input_error(&lower))
        {
            remembered_choice = Some(error_override.to_string());
        } else if let Some(user_choice) = learner
            .candidate_memory
            .get(input)
            .or_else(|| learner.candidate_memory.get(&lower))
        {
            remembered_choice = Some(user_choice.clone());
        }
    }

    let is_explicit_common = has_override_word(input) || has_override_word(&lower);
    let autocorrect_replacement = if remembered_choice.is_none() && !is_explicit_common {
        db.autocorrect
            .get(input)
            .or_else(|| db.autocorrect.get(&lower))
            .map(|s| s.as_str())
    } else {
        None
    };

    let req_ctx = AvroRequestContext {
        input,
        context,
        lower: &lower,
        def: &def,
        db,
        remembered_choice,
        autocorrect_replacement,
        is_explicit_common,
    };

    // Stage 8: Upstream Phonetic Engine with Multi-Tier Contextual Ranking
    stage_phonetic_engine(&req_ctx)
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

pub(crate) struct AvroStrategy;

impl crate::layout::LayoutStrategy for AvroStrategy {
    fn process_key(&self, key: &str, state: &mut crate::session::SessionState) -> Result<crate::session::TypingResult, crate::error::LekhaniError> {
        state.composing_buffer.push_str(key);
        if state.pure_avro_mode {
            let parser = get_avro_parser();
            let preedit = parser.convert(&state.composing_buffer);
            let len = unicode_segmentation::UnicodeSegmentation::graphemes(preedit.as_str(), true).count() as u32;
            let candidates = vec![preedit.clone()];
            return Ok(crate::session::TypingResult {
                preedit,
                commit_text: None,
                candidates,
                cursor_position: len,
            });
        }
        let mut words_buf = [""; 16];
        let count = if !state.surrounding_context.is_empty() {
            crate::session::get_context_words(&state.surrounding_context, &mut words_buf)
        } else {
            0
        };
        let words = &words_buf[..count];
        let (preedit, mut candidates) =
            transliterate_avro_with_context(&state.composing_buffer, words);
        let has_candidate_memory = {
            if state.composing_buffer.chars().count() <= 2 {
                false
            } else {
                let db = crate::session::get_core_database();
                db.learner.read().ok().is_some_and(|l| {
                    l.lookup_input_error(&state.composing_buffer).is_some()
                        || l.candidate_memory.contains_key(&state.composing_buffer)
                        || l.candidate_memory
                            .contains_key(&state.composing_buffer.to_lowercase())
                })
            }
        };
        if !is_avro_special_fastpath(&state.composing_buffer) && count > 0 && candidates.len() > 1 {
            let scorer = crate::session::get_context_scorer();
            let right_word = state.right_context.split_whitespace().next();
            scorer.rank_candidates_in_place_bidirectional(
                words,
                right_word,
                &mut candidates,
            );
            if has_candidate_memory {
                let db = crate::session::get_core_database();
                if let Ok(learner) = db.learner.read() {
                    let preferred = learner
                        .lookup_input_error(&state.composing_buffer)
                        .or_else(|| {
                            learner
                                .candidate_memory
                                .get(&state.composing_buffer)
                                .map(|s| s.as_str())
                        })
                        .or_else(|| {
                            learner
                                .candidate_memory
                                .get(&state.composing_buffer.to_lowercase())
                                .map(|s| s.as_str())
                        });
                    if let Some(pref) = preferred {
                        if let Some(pos) = candidates.iter().position(|c| c == pref) {
                            let cand = candidates.remove(pos);
                            candidates.insert(0, cand);
                        }
                    }
                }
            } else {
                prioritize_common_or_override_candidate(
                    &state.composing_buffer,
                    &mut candidates,
                );
            }
            if let Some(top) = candidates.first() {
                let db = crate::session::get_core_database();
                let has_override = db.lookup_override(&state.composing_buffer).is_some()
                    || db
                        .lookup_override(&state.composing_buffer.to_lowercase())
                        .is_some();
                let is_explicit_common = has_override;

                let freq_preedit = db.get_frequency(&preedit);
                let freq_top = db.get_frequency(top);
                let is_archaic_preedit = freq_preedit == 0 && freq_top > 0;

                let has_strong_context_preference = {
                    let scorer = crate::session::get_context_scorer();
                    let prev1 = words.last().copied();
                    let prev2 = if words.len() >= 2 { Some(words[words.len() - 2]) } else { None };
                    let lm_top = scorer.lm().score_candidate(prev2, prev1, top);
                    let lm_pre = scorer.lm().score_candidate(prev2, prev1, &preedit);
                    (lm_top - lm_pre) >= 2.0
                };

                if !has_candidate_memory
                    && !is_explicit_common
                    && !has_override
                    && db.is_exact_dictionary_word(&preedit)
                    && (top.chars().count() > preedit.chars().count()
                        || (!is_archaic_preedit
                            && !has_strong_context_preference
                            && (!are_phonetically_compatible(&preedit, top)
                                || !db.is_exact_dictionary_word(top))))
                {
                    if let Some(pos) = candidates.iter().position(|c| c == &preedit) {
                        let cand = candidates.remove(pos);
                        candidates.insert(0, cand);
                    }
                }
            }
        }
        let mut norm_candidates = Vec::with_capacity(candidates.len());
        for c in candidates {
            let n = crate::probaho::nfc_normalize(&c);
            if !norm_candidates.contains(&n) {
                norm_candidates.push(n);
            }
        }
        let len = unicode_segmentation::UnicodeSegmentation::graphemes(preedit.as_str(), true).count() as u32;
        Ok(crate::session::TypingResult {
            preedit,
            commit_text: None,
            candidates: norm_candidates,
            cursor_position: len,
        })
    }

    fn resolve_space_commit(&self, raw: &str, state: &mut crate::session::SessionState, chosen_candidate: Option<&str>) -> String {
        let mut words_buf = [""; 16];
        let count = if !state.surrounding_context.is_empty() {
            crate::session::get_context_words(&state.surrounding_context, &mut words_buf)
        } else {
            0
        };
        let context_words = &words_buf[..count];

        if let Some(cand) = chosen_candidate {
            let (preedit, _) = transliterate_avro_with_context(raw, context_words);
            if lekhani_ai::AnvayaAgreementEngine::is_honorific_shift(&preedit, cand) {
                crate::probaho::nfc_normalize(&preedit)
            } else {
                crate::probaho::nfc_normalize(cand)
            }
        } else if state.pure_avro_mode {
            let parser = get_avro_parser();
            let def = parser.convert(raw);
            crate::probaho::nfc_normalize(&def)
        } else {
            let has_candidate_memory = {
                if raw.chars().count() <= 2 {
                    false
                } else {
                    let db = crate::session::get_core_database();
                    db.learner.read().ok().is_some_and(|l| {
                        l.lookup_input_error(raw).is_some()
                            || l.candidate_memory.contains_key(raw)
                            || l.candidate_memory.contains_key(&raw.to_lowercase())
                    })
                }
            };
            let (preedit, mut candidates) =
                transliterate_avro_with_context(raw, context_words);
            let is_fastpath = is_avro_special_fastpath(raw);
            let chosen = if !is_fastpath && !has_candidate_memory && candidates.len() > 1 {
                if !context_words.is_empty() {
                    let right_word = state.right_context.split_whitespace().next();
                    let scorer = crate::session::get_context_scorer();
                    scorer.rank_candidates_in_place_bidirectional(
                        context_words,
                        right_word,
                        &mut candidates,
                    );
                }
                prioritize_common_or_override_candidate(raw, &mut candidates);
                let lower = raw.to_lowercase();
                let is_acronym_or_tech = is_common_tech_acronym(raw)
                    || raw == "NaN"
                    || raw == "Infinity"
                    || (raw.len() >= 2
                        && raw.chars().all(|c| c.is_ascii_uppercase())
                        && !has_override_word(&lower));
                if is_acronym_or_tech {
                    if let Some(pos) = candidates.iter().position(|c| c.eq_ignore_ascii_case(raw)) {
                        let cand = candidates.remove(pos);
                        candidates.insert(0, cand);
                    }
                }
                if let Some(top) = candidates.first() {
                    let db = crate::session::get_core_database();
                    let has_override = db.lookup_override(raw).is_some()
                        || db.lookup_override(&raw.to_lowercase()).is_some();
                    let is_explicit_common = has_override;

                    let is_geminate = (lower.contains("cc")
                        || lower.contains("kk")
                        || lower.contains("tt")
                        || lower.contains("dd")
                        || lower.contains("bb")
                        || lower.contains("pp")
                        || lower.contains("mm")
                        || lower.contains("nn")
                        || lower.contains("ll")
                        || lower.contains("ss")
                        || lower.contains("jj")
                        || lower.contains("gg"))
                        && preedit.contains('্');

                    if !has_candidate_memory
                        && !is_explicit_common
                        && !has_override
                        && (db.is_exact_dictionary_word(&preedit) || is_geminate)
                        && (top.chars().count() > preedit.chars().count()
                            || !are_phonetically_compatible(&preedit, top)
                            || !db.is_exact_dictionary_word(top)
                            || (is_geminate && !top.contains('্'))
                            || (is_geminate && top.contains('য') && !preedit.contains('য')))
                    {
                        if let Some(pos) = candidates.iter().position(|c| c == &preedit) {
                            let cand = candidates.remove(pos);
                            candidates.insert(0, cand);
                        }
                    }
                }
                candidates.first().map(|s| s.as_str()).unwrap_or(raw)
            } else if let Some(top) = candidates.first() {
                top.as_str()
            } else if !preedit.is_empty() {
                &preedit
            } else {
                raw
            };
            crate::probaho::nfc_normalize(chosen)
        }
    }

    fn regenerate_candidates(&self, state: &crate::session::SessionState, input: &str) -> Vec<String> {
        let mut words_buf = [""; 16];
        let count = if !state.surrounding_context.is_empty() {
            crate::session::get_context_words(&state.surrounding_context, &mut words_buf)
        } else {
            0
        };
        let words = &words_buf[..count];
        let (_, mut cands) = transliterate_avro_with_context(input, words);
        let is_fastpath = is_avro_special_fastpath(input);
        if !is_fastpath && count > 0 && cands.len() > 1 {
            if let Some(right_word) = state.right_context.split_whitespace().next() {
                let scorer = crate::session::get_context_scorer();
                scorer.rank_candidates_in_place_bidirectional(
                    words,
                    Some(right_word),
                    &mut cands,
                );
            }
        }
        cands
    }
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
    #[serial_test::serial]
    fn test_bilingual_hyphen_clitics() {
        set_bilingual_clitic_enabled(true);
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
        set_bilingual_clitic_enabled(false);
    }

    #[test]
    #[serial_test::serial]
    fn test_unhyphenated_bilingual_sandhi_clitics() {
        set_bilingual_clitic_enabled(true);
        // 1. WhatsAppe -> "WhatsApp-এ", surfaces "হোয়াটসঅ্যাপে" and "WhatsAppe"
        let (wa_res, wa_cands) = transliterate_avro("WhatsAppe");
        assert_eq!(wa_res, "WhatsApp-এ");
        assert!(wa_cands.contains(&"WhatsAppe".to_string()));
        assert!(wa_cands.contains(&"হোয়াটসঅ্যাপে".to_string()));

        // 2. WhatsApper -> "WhatsApp-এর"
        let (wa_er_res, wa_er_cands) = transliterate_avro("WhatsApper");
        assert_eq!(wa_er_res, "WhatsApp-এর");
        assert!(wa_er_cands.contains(&"WhatsApper".to_string()));

        // 3. Meetinger -> "Meeting-এর", surfaces "মিটিংয়ের"
        let (meet_res, meet_cands) = transliterate_avro("Meetinger");
        assert_eq!(meet_res, "Meeting-এর");
        assert!(meet_cands.contains(&"Meetinger".to_string()));
        assert!(meet_cands.contains(&"মিটিংয়ের".to_string()));

        // 4. APIgulor -> "API-গুলোর"
        let (api_res, api_cands) = transliterate_avro("APIgulor");
        assert_eq!(api_res, "API-গুলোর");
        assert!(api_cands.contains(&"APIgulor".to_string()));

        // 5. Bugta -> "Bug-টা"
        let (bug_res, bug_cands) = transliterate_avro("Bugta");
        assert_eq!(bug_res, "Bug-টা");
        assert!(bug_cands.contains(&"Bugta".to_string()));

        // 6. failmarse -> "fail মারছে"
        let (fail_res, _) = transliterate_avro("failmarse");
        assert_eq!(fail_res, "fail মারছে");

        // 7. Everyday inflected loanwords resolve naturally to Bengali spelling, while hyphenated produces code-mixed
        let (post_res, _) = transliterate_avro("poste");
        assert_eq!(post_res, "পোস্টে");
        let (post_hyphen_res, post_hyphen_cands) = transliterate_avro("post-e");
        assert_eq!(post_hyphen_res, "post-এ");
        assert!(post_hyphen_cands.contains(&"post-e".to_string()));

        // 8. Bengali core words are completely safe from false decoupling
        assert_eq!(transliterate_avro("pore").0, "পরে");
        assert_eq!(transliterate_avro("kore").0, "করে");
        assert_eq!(transliterate_avro("amar").0, "আমার");
        assert_eq!(transliterate_avro("tumi").0, "তুমি");
        set_bilingual_clitic_enabled(false);
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

    #[test]
    fn test_anvaya_slot0_wysiwyg_guarantee() {
        // Under formal subject ("আপনি কেমন"), typing "aso":
        // Slot 0 MUST be the familiar verb "আছো" (WYSIWYG: Space commits what is highlighted)
        // Slot 1 MUST be the Anvaya formal suggestion "আছেন" (commits on tap, not on Space)
        let (primary, candidates) =
            transliterate_avro_with_context("aso", &["আপনি", "কেমন"]);
        assert_eq!(primary, "আছো", "Slot 0 must be familiar 'আছো'");
        assert!(candidates.len() >= 2, "Must have at least 2 candidates");
        assert_eq!(candidates[0], "আছো", "Candidate[0] must be 'আছো'");
        assert_eq!(candidates[1], "আছেন", "Candidate[1] must be 'আছেন'");

        // Intimate subject ("তুই"), typing "koro":
        // Slot 0 is "করো", Slot 1 is "করছিস"
        let (primary_tui, candidates_tui) =
            transliterate_avro_with_context("koro", &["তুই"]);
        assert_eq!(primary_tui, "করো", "Slot 0 must be familiar 'করো'");
        assert!(candidates_tui.contains(&"করছিস".to_string()), "Candidate pool must contain 'করছিস'");
    }

    #[test]
    fn test_grammar_biased_muscle_memory_invariant() {
        let parser = get_avro_parser();
        let roots = [
            "k", "kh", "g", "gh", "c", "ch", "j", "jh", "T", "Th", "D", "Dh", "N",
            "t", "th", "d", "dh", "n", "p", "ph", "b", "bh", "m", "r", "l", "sh",
            "Sh", "s", "h", "R", "Rh", "y", "kSh", "gyan", "rri",
        ];
        let vowels = ["", "a", "i", "I", "u", "U", "e", "o", "O", "OI", "OU"];
        let codas = ["", "r", "l", "n", "m", "t", "k", "sh", "^", ":"];
        let suffixes = ["", "ta", "gulo", "ke", "ra", "der", "te", "techi"];

        let mut checked = 0;
        for &r in &roots {
            for &v in &vowels {
                for &c in &codas {
                    for &suf in &suffixes {
                        let mut token = String::new();
                        token.push_str(r);
                        token.push_str(v);
                        token.push_str(c);
                        token.push_str(suf);

                        if token.is_empty() {
                            continue;
                        }

                        let expected = parser.convert(&token);
                        if expected.is_empty() {
                            continue;
                        }

                        let (_, candidates) = transliterate_avro(&token);
                        assert!(
                            candidates.contains(&expected),
                            "Invariant violated for '{}': expected '{}' not in candidates {:?}",
                            token,
                            expected,
                            candidates
                        );
                        checked += 1;
                    }
                }
            }
        }
        assert!(checked > 5000, "Should have fuzzed > 5000 grammar-biased inputs, fuzzed {}", checked);
    }

    #[test]
    fn test_cca_and_short_acronym_promotion() {
        // Lowercase "cca" must transliterate to Bengali "চ্চা" in Slot 0, NOT Latin "cca"
        let (primary, cands) = transliterate_avro("cca");
        assert_eq!(primary, "চ্চা", "Primary preedit for 'cca' must be 'চ্চা', got '{}'", primary);
        assert_eq!(cands.first().map(|s| s.as_str()), Some("চ্চা"));
        assert!(cands.contains(&"cca".to_string()), "Candidate strip should still offer Latin 'cca'");

        // Uppercase "CCA" should promote to Slot 0 Latin "CCA" as a technical acronym
        let (primary_caps, cands_caps) = transliterate_avro("CCA");
        assert_eq!(primary_caps, "CCA", "Primary preedit for all-caps 'CCA' must be 'CCA'");
        assert_eq!(cands_caps.first().map(|s| s.as_str()), Some("CCA"));
    }

    #[test]
    fn test_explicit_avro_rule_intent_detection() {
        // 1. Explicit operators must be detected
        assert!(has_explicit_avro_intent("c^ad"));
        assert!(has_explicit_avro_intent("h^as"));
        assert!(has_explicit_avro_intent("b^ash"));
        assert!(has_explicit_avro_intent("du:kho"));
        assert!(has_explicit_avro_intent("k`kh"));
        assert!(has_explicit_avro_intent("k'kh"));
        assert!(has_explicit_avro_intent("hot`at"));
        assert!(has_explicit_avro_intent("hot'at"));
        assert!(has_explicit_avro_intent("k,,k"));
        assert!(has_explicit_avro_intent("k..k"));
        assert!(has_explicit_avro_intent("$500"));

        // 2. Mid-word explicit Avro uppercase distinctions must be detected
        assert!(has_explicit_avro_intent("paR"));
        assert!(has_explicit_avro_intent("kOno"));
        assert!(has_explicit_avro_intent("kaTh"));
        assert!(has_explicit_avro_intent("biSTi"));
        assert!(has_explicit_avro_intent("bODh"));
        assert!(has_explicit_avro_intent("baRi"));

        // 3. Sentence-initial titlecase & casual typing must NOT be marked as explicit intent
        assert!(!has_explicit_avro_intent("Tumi"));
        assert!(!has_explicit_avro_intent("Din"));
        assert!(!has_explicit_avro_intent("Tara"));
        assert!(!has_explicit_avro_intent("kemon"));
        assert!(!has_explicit_avro_intent("basha"));
        assert!(!has_explicit_avro_intent("ami"));
        assert!(!has_explicit_avro_intent("dharmo"));

        // 4. End-to-end Slot 0 preedit locking for explicit Avro inputs
        let (cad, _) = transliterate_avro("ca^d");
        assert_eq!(cad, "চাঁদ", "ca^d must lock to 'চাঁদ'");

        let (par, _) = transliterate_avro("paR");
        assert_eq!(par, "পাড়", "paR must lock to 'পাড়'");

        let (por, _) = transliterate_avro("poR");
        assert_eq!(por, "পড়", "poR must lock to 'পড়'");

        let (dukho, _) = transliterate_avro("du:kho");
        assert_eq!(dukho, "দুঃখ", "du:kho must lock to 'দুঃখ'");

        let (hotat, _) = transliterate_avro("hoThat``");
        assert_eq!(hotat, "হঠাৎ", "hoThat`` must lock to 'হঠাৎ'");

        let (biggani, _) = transliterate_avro("bigganI");
        assert_eq!(biggani, "বিজ্ঞানী", "bigganI must lock to 'বিজ্ঞানী'");

        let (kk, _) = transliterate_avro("k`k");
        assert_eq!(kk, "কক", "k`k must break conjunct into 'কক'");
    }
}

