//! Avro Phonetic Transliteration Engine powered by `lekhani-parser` and `lekhani-ai`
//!
//! Provides deterministic Roman-to-Bengali transliteration according to
//! classic Avro Phonetic muscle memory standards using the upstream
//! zero-allocation L1 Trie grammar compiler from `lekhani-parser`.

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
    prioritize_common_or_override_candidate_with_context(input, candidates, false);
}

pub fn prioritize_common_or_override_candidate_with_context(
    input: &str,
    candidates: &mut Vec<String>,
    has_context: bool,
) {
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
                .or_else(|| {
                    lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(&lower, r)
                })
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
    let db = crate::session::get_core_database();
    let def_conv = get_avro_parser().convert(input);
    let def_lower_conv = get_avro_parser().convert(&lower);
    let _is_bengali_dict_word = (!def_conv.is_empty() && db.is_exact_dictionary_word(&def_conv))
        || (!def_lower_conv.is_empty() && db.is_exact_dictionary_word(&def_lower_conv));

    let is_verified_tech_acronym = is_common_tech_acronym(input);
    if is_verified_tech_acronym {
        if let Some(pos) = candidates
            .iter()
            .position(|c| c.eq_ignore_ascii_case(input))
        {
            let cand = candidates.remove(pos);
            candidates.insert(0, cand);
            return;
        }
    } else if let Some(pos) = candidates
        .iter()
        .position(|c| c.eq_ignore_ascii_case(input))
    {
        // Enforce Sacred Slot 0: In Avro Bengali layout, English candidate sits at Slot 1 (Rank 2),
        // NEVER stealing Slot 0 from valid Bengali transliterations.
        if pos == 0 && candidates.len() > 1 {
            let cand = candidates.remove(0);
            candidates.insert(1, cand);
        }
    }

    if has_explicit_avro_intent(input) {
        // User explicitly specified exact Avro grammar (e.g. paR, kOno, c^ad, du:kho, hot`at).
        // Deterministic Avro preedit at Slot 0 must remain locked and immune to generic fuzzy overrides.
        return;
    }

    if is_bilingual_clitic_enabled()
        && (resolve_bilingual_hyphen_clitic(input).is_some()
            || resolve_unhyphenated_bilingual_clitic(input).is_some())
    {
        return;
    }

    // 1. Check dynamic/bundled supervised phonetic overrides (phonetic_overrides.bin / json)
    let is_title_case = input.len() >= 2
        && input.chars().next().is_some_and(|c| c.is_uppercase())
        && input
            .chars()
            .skip(1)
            .all(|c| c.is_lowercase() || !c.is_alphabetic());
    let collapsed_opt = collapse_elongation(&lower);
    let override_opt = db
        .lookup_override_inflected(input)
        .or_else(|| {
            if is_title_case {
                db.lookup_override_inflected(&lower)
            } else {
                None
            }
        })
        .or_else(|| {
            collapsed_opt
                .as_deref()
                .and_then(|c| db.lookup_override_inflected(c))
        });
    let mut inserted_idx = 0;
    if let Some(overrides) = override_opt {
        // Multi-homophone list (e.g. "pora" -> ["পরা", "পড়া"], "ki" -> ["কি", "কী"]):
        // If the top candidate is ALREADY in the overrides list with equal/higher score, keep it at position 0!
        // Never keep a lower-scoring override or titlecase retroflex at position 0.
        let top_override_score = overrides.first().map(|(_, s)| *s).unwrap_or(0.0);
        let has_top_in_overrides = if !is_title_case && overrides.len() > 1 {
            if let Some(first_cand) = candidates.first() {
                let first_nfc = crate::probaho::nfc_normalize(first_cand);
                if has_context {
                    overrides
                        .iter()
                        .any(|(w, _)| crate::probaho::nfc_normalize(w) == first_nfc)
                } else {
                    overrides.iter().any(|(w, s)| {
                        crate::probaho::nfc_normalize(w) == first_nfc && *s >= top_override_score
                    })
                }
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
            let target_nfc = crate::probaho::nfc_normalize(&override_word);
            if has_top_in_overrides
                && candidates
                    .first()
                    .map(|s| crate::probaho::nfc_normalize(s) == target_nfc)
                    .unwrap_or(false)
            {
                continue;
            }
            if let Some(pos) = candidates
                .iter()
                .position(|c| crate::probaho::nfc_normalize(c) == target_nfc)
            {
                let cand = candidates.remove(pos);
                candidates.insert(inserted_idx.min(candidates.len()), cand);
                inserted_idx += 1;
            } else if score >= 0.70 {
                candidates.insert(inserted_idx.min(candidates.len()), target_nfc.clone());
                inserted_idx += 1;
            } else if !candidates
                .iter()
                .any(|c| crate::probaho::nfc_normalize(c) == target_nfc)
            {
                if candidates.len() >= 12 {
                    candidates.pop();
                }
                candidates.push(target_nfc);
            }
        }
        return;
    }

    // 2. Sentence-initial titlecase & All-caps normalization when no explicit override exists (e.g. Din -> দিন vs ডিন, DESH -> দেশ vs ডেশ)
    if is_title_case || (is_all_caps && !is_common_tech_acronym(input)) {
        let def_lower_norm = crate::probaho::nfc_normalize(&def_lower_conv);
        let def_norm = crate::probaho::nfc_normalize(&def_conv);
        if !def_lower_norm.is_empty() && def_lower_norm != def_norm {
            let lower_in_dict = db.is_exact_dictionary_word(&def_lower_norm);
            let _def_in_dict = db.is_exact_dictionary_word(&def_norm);
            let freq_lower = db.get_frequency(&def_lower_norm);
            let freq_def = db.get_frequency(&def_norm);
            if lower_in_dict || freq_lower >= freq_def {
                if let Some(pos) = candidates
                    .iter()
                    .position(|c| crate::probaho::nfc_normalize(c) == def_lower_norm)
                {
                    let cand = candidates.remove(pos);
                    candidates.insert(0, cand);
                } else {
                    candidates.insert(0, def_lower_norm);
                }
                if !candidates
                    .iter()
                    .any(|c| crate::probaho::nfc_normalize(c) == def_norm)
                {
                    let insert_idx = 1.min(candidates.len());
                    candidates.insert(insert_idx, def_norm);
                }
                return;
            }
        }
    }

    // 3. Check phonetic variant equivalences (geminates, S-class, Sanskrit initial vowels, compound Reph)
    // 3. Check phonetic variant equivalences (geminates, S-class, retroflex stops, elided vowels)
    let equivs = get_phonetic_equivalence_candidates(input);
    let mut inserted_idx = 0;
    for eq_word in equivs {
        let eq_freq = db.get_frequency(&eq_word);
        let top_freq = candidates.first().map(|w| db.get_frequency(w)).unwrap_or(0);

        let top_is_valid = candidates
            .first()
            .map(|w| {
                db.is_exact_dictionary_word(w)
                    || db.trie.contains_exact(w)
                    || lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&lower)
                        .is_some()
            })
            .unwrap_or(false);

        let is_geminate_input = (lower.contains("cc")
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
            && eq_word.contains('্');

        let should_promote_to_slot0 = inserted_idx == 0
            && (is_geminate_input
                || (eq_freq >= 500 && (!top_is_valid || eq_freq >= top_freq.saturating_mul(10))));

        let target_idx = if should_promote_to_slot0 {
            0
        } else {
            inserted_idx.max(1).min(candidates.len())
        };

        if let Some(pos) = candidates.iter().position(|c| c == &eq_word) {
            if pos == 0 {
                // Already the top candidate; keep at Slot 0 and advance inserted_idx!
                inserted_idx = inserted_idx.max(1);
                continue;
            }
            let cand = candidates.remove(pos);
            candidates.insert(target_idx, cand);
            if target_idx == 0 {
                inserted_idx = 1;
            } else {
                inserted_idx += 1;
            }
        } else {
            candidates.insert(target_idx, eq_word);
            if target_idx == 0 {
                inserted_idx = 1;
            } else {
                inserted_idx += 1;
            }
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
    let db = crate::session::get_core_database();
    db.suggest_phonetic_equivalences(input, |k| get_avro_parser().convert(k), 16)
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
    let is_dental_retroflex_t = |c| c == 'ত' || c == 'ট' || c == 'ৎ' || c == 'থ' || c == 'ঠ';
    if is_dental_retroflex_t(c1) && is_dental_retroflex_t(c2) {
        return true;
    }
    let is_dental_retroflex_d = |c| c == 'দ' || c == 'ড' || c == 'ধ' || c == 'ঢ';
    if is_dental_retroflex_d(c1) && is_dental_retroflex_d(c2) {
        return true;
    }
    let is_palatal = |c| c == 'চ' || c == 'ছ';
    if is_palatal(c1) && is_palatal(c2) {
        return true;
    }
    let is_velar = |c| c == 'ক' || c == 'খ' || c == 'গ' || c == 'ঘ';
    if is_velar(c1) && is_velar(c2) {
        return true;
    }
    let is_labial = |c| c == 'প' || c == 'ফ' || c == 'ব' || c == 'ভ';
    if is_labial(c1) && is_labial(c2) {
        return true;
    }
    let is_vowel = |c| "অআইঈউঊঋএঐওঔািীুূৃেৈোৌ".contains(c);
    is_vowel(c1) && is_vowel(c2)
}

/// Looks up offline supervised phonetic override candidates for a key (including inflected suffix peeling).
pub fn get_override_words(key: &str) -> Option<Vec<String>> {
    let db = crate::session::get_core_database();
    db.lookup_override_inflected(key)
        .map(|ov| ov.into_iter().map(|(w, _)| w).collect())
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
        || !base
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
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
            let is_base_latin = base.contains('-')
                || base.chars().any(|c| c.is_ascii_uppercase())
                || crate::english::is_recognized_english_word(base);
            if !is_base_latin {
                return None;
            }
            let parser = get_avro_parser();
            let suffix_conv = parser.convert(suffix);
            if !suffix_conv.is_empty() {
                let in_flight_primary = format!("{}-{}", base, suffix_conv);
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
    if (!def_conv.is_empty()
        && (db.is_exact_dictionary_word(&def_conv) || db.trie.contains_exact(&def_conv)))
        || (!def_lower_conv.is_empty()
            && (db.is_exact_dictionary_word(&def_lower_conv)
                || db.trie.contains_exact(&def_lower_conv)))
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

            let is_all_upper_acronym =
                base.len() >= 2 && base.chars().all(|c| c.is_ascii_uppercase());
            let is_camel_or_title = base.len() >= 3
                && base.chars().next().is_some_and(|c| c.is_ascii_uppercase())
                && base.chars().skip(1).all(|c| c.is_ascii_lowercase());
            let is_recognized_english =
                base.len() >= 3 && crate::english::is_recognized_english_word(base);
            let is_known_brand_or_tech = matches!(
                base.to_lowercase().as_str(),
                "whatsapp"
                    | "facebook"
                    | "google"
                    | "youtube"
                    | "instagram"
                    | "twitter"
                    | "tiktok"
                    | "zoom"
                    | "skype"
                    | "android"
                    | "windows"
                    | "linux"
                    | "apple"
                    | "microsoft"
                    | "bkash"
                    | "nagad"
                    | "rocket"
                    | "upay"
                    | "uber"
                    | "pathao"
                    | "daraz"
                    | "foodpanda"
                    | "netflix"
                    | "spotify"
                    | "telegram"
                    | "signal"
                    | "gmail"
                    | "yahoo"
                    | "wifi"
                    | "bluetooth"
                    | "laptop"
                    | "desktop"
                    | "monitor"
                    | "server"
                    | "cloud"
                    | "bug"
                    | "meeting"
                    | "post"
                    | "link"
                    | "call"
                    | "group"
                    | "chat"
                    | "message"
                    | "status"
                    | "story"
                    | "video"
                    | "audio"
                    | "photo"
                    | "camera"
                    | "file"
                    | "app"
                    | "code"
                    | "commit"
                    | "branch"
                    | "repo"
                    | "issue"
                    | "pr"
                    | "merge"
                    | "test"
                    | "fail"
                    | "pass"
                    | "click"
                    | "share"
                    | "like"
                    | "comment"
                    | "feed"
                    | "stream"
                    | "cash"
                    | "bank"
                    | "card"
                    | "bill"
                    | "offer"
            );

            let clean_base_lower = base.to_lowercase();
            let has_loanword =
                lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&clean_base_lower)
                    .is_some();
            let is_valid_stem = if suf.len() == 1 {
                is_all_upper_acronym || is_camel_or_title || is_known_brand_or_tech || has_loanword
            } else {
                is_all_upper_acronym
                    || is_camel_or_title
                    || is_recognized_english
                    || is_known_brand_or_tech
                    || has_loanword
            };

            if is_valid_stem {
                let code_mix_primary = format!("{}{}", base, b_clitic);
                let mut primary = code_mix_primary.clone();
                let mut candidates = vec![code_mix_primary];

                // Slot 1: Try phonetic Bengali loanword if available
                let clean_base_lower = base.to_lowercase();
                if let Some((bn_loan, _)) =
                    lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(
                        &clean_base_lower,
                    )
                {
                    let unified_clitic = match suf {
                        "e" => {
                            if bn_loan.ends_with('া')
                                || bn_loan.ends_with('ি')
                                || bn_loan.ends_with('ী')
                                || bn_loan.ends_with('ু')
                                || bn_loan.ends_with('ূ')
                                || bn_loan.ends_with('ে')
                                || bn_loan.ends_with('ো')
                            {
                                "য়ে"
                            } else {
                                "ে"
                            }
                        }
                        "er" => {
                            if bn_loan.ends_with('া')
                                || bn_loan.ends_with('ি')
                                || bn_loan.ends_with('ী')
                                || bn_loan.ends_with('ু')
                                || bn_loan.ends_with('ূ')
                                || bn_loan.ends_with('ে')
                                || bn_loan.ends_with('ো')
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
                    let bn_hyphen_cand =
                        format!("{}-{}", bn_loan, b_clitic.trim_start_matches('-').trim());
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
    let db = crate::session::get_core_database();
    if db.lookup_override(input).is_some() || db.lookup_override(&lower).is_some() {
        return None;
    }

    // ── 0. Morphological Compound Lattice (Soft-Hyphen & Unhyphenated Morpheme Snapping) ──
    let def_conv = get_avro_parser().convert(input);
    let is_whole_dict_word = !def_conv.is_empty() && db.is_exact_dictionary_word(&def_conv);

    if !is_whole_dict_word {
        let resolve_stem = |stem: &str| -> Option<String> {
            let (stem_bn, _) = transliterate_avro(stem);
            let effective = if !stem_bn.is_empty() && stem_bn != stem {
                stem_bn
            } else {
                get_avro_parser().convert(stem)
            };
            if !effective.is_empty()
                && (db.is_exact_dictionary_word(&effective)
                    || db.trie.contains_exact(&effective)
                    || db.lookup_override(stem).is_some()
                    || db.get_frequency(&effective) > 0
                    || effective.chars().count() >= 3)
            {
                Some(effective)
            } else {
                None
            }
        };

        if let Some(compound) =
            lekhani_core::phonetic::morphology::decompose_compound_token(input, resolve_stem)
        {
            let mut cands = vec![compound.unified.clone()];
            if let Some(hyphenated) = compound.hyphenated {
                if hyphenated != compound.unified {
                    cands.push(hyphenated);
                }
            }
            cands.push(input.to_string());
            return Some((compound.unified, cands));
        }
    }

    // ── 1. Academic Tatshama Suffixes & Prefixes ──
    // a. Initial diphthong oi- -> ঐ- (e.g. oitihashik -> ঐতিহাসিক, oikyo -> ঐক্য)
    if lower.starts_with("oi") && lower.len() >= 4 {
        let remainder = &lower[2..];
        let (rem_bn, _) = transliterate_avro(remainder);
        let word = format!("ঐ{}", rem_bn);
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
        if let Some((bn_loan, _)) =
            lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&lower).or_else(|| {
                lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&k_form)
            })
        {
            return Some((bn_loan.to_string(), vec![bn_loan.to_string()]));
        }
    }

    // ── 2. Colloquial & Regional Dialect Verb Morphology ─────────────────────
    let resolve_stem = |stem: &str| -> Option<String> {
        let (stem_bn, _) = transliterate_avro(stem);
        if !stem_bn.is_empty() && stem_bn != stem {
            Some(stem_bn)
        } else {
            let conv = get_avro_parser().convert(stem);
            if !conv.is_empty() {
                Some(conv)
            } else {
                None
            }
        }
    };
    if let Some(dialect_hyp) =
        lekhani_core::phonetic::morphology::decompose_dialect_verbal_token(&lower, resolve_stem)
    {
        let mut cands = vec![dialect_hyp.dialect.clone()];
        if dialect_hyp.standard != dialect_hyp.dialect {
            cands.push(dialect_hyp.standard);
        }
        return Some((dialect_hyp.dialect, cands));
    }

    // ── 3. Bengali Dual-Stem Hyphen Compounds (e.g. hrisswota-dirghotar -> হ্রস্বতা-দীর্ঘতার) ──
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

    None
}

/// Returns true if the token is strictly non-phonetic (social mention, hashtag, or numeric expression)
/// where NLP context scoring is irrelevant.
pub fn is_avro_special_fastpath(input: &str) -> bool {
    input.starts_with('@')
        || input.starts_with('#')
        || (!input.is_empty()
            && input.chars().all(|c| {
                c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c) || c == '.' || c == ','
            })
            && input
                .chars()
                .any(|c| c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c)))
        || resolve_digit_unit_candidates(input).is_some()
        || (is_bilingual_clitic_enabled()
            && (resolve_bilingual_hyphen_clitic(input).is_some()
                || resolve_unhyphenated_bilingual_clitic(input).is_some()))
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
        && input.chars().all(|c| {
            c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c) || c == '.' || c == ','
        })
        && input
            .chars()
            .any(|c| c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c))
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
            append_prefix_matches(input, &def, &mut candidates);
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
        if !candidates.iter().any(|c| c.eq_ignore_ascii_case(input)) {
            candidates.push(input.to_string());
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

    lekhani_core::phonetic::contextual_rules::apply_contextual_homophone_rules(
        input,
        &[],
        "",
        "",
        false,
        has_explicit_avro_intent(input),
        &mut short_candidates,
    );
    let primary = short_candidates.first().cloned().unwrap_or(primary);

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

fn stage_phonetic_engine(ctx: &AvroRequestContext<'_>) -> (String, Vec<String>) {
    let mut candidate_memory = HashMap::new();
    if let Some(ref choice) = ctx.remembered_choice {
        candidate_memory.insert(ctx.input.to_string(), choice.clone());
    }

    let sugg_mutex = crate::session::get_phonetic_suggestion();
    let (mut raw_candidates, _selected_idx) = {
        let mut sugg = match sugg_mutex.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        sugg.suggest_with_multi_context(ctx.input, ctx.context, false, true, &candidate_memory)
    };

    if raw_candidates.is_empty() && !ctx.def.is_empty() {
        raw_candidates.push(ctx.def.to_string());
    }

    // 1. Muscle-memory invariant: literal Avro conversion is ALWAYS retained
    let def_pure = get_avro_parser().convert(ctx.input);
    let def_norm = crate::probaho::nfc_normalize(&def_pure);
    if !def_norm.is_empty() {
        let db = crate::session::get_core_database();
        let top_is_in_dict = raw_candidates
            .first()
            .map(|w| db.is_exact_dictionary_word(w) || db.trie.contains_exact(w))
            .unwrap_or(false);
        let top_is_override = ctx.is_explicit_common;
        let top_is_contextual =
            is_contextual_homophone_winner(ctx.input, ctx.context, raw_candidates.first());
        let top_is_valid = top_is_in_dict || top_is_override || top_is_contextual;
        let top_len = raw_candidates
            .first()
            .map(|w| w.chars().count())
            .unwrap_or(0);
        let def_len = def_norm.chars().count();
        let is_top_distant = !top_is_valid || (def_len >= 4 && top_len + 1 < def_len);

        if let Some(pos) = raw_candidates.iter().position(|c| c == &def_norm) {
            if is_top_distant
                && pos > 0
                && ctx.remembered_choice.is_none()
                && !has_explicit_avro_intent(ctx.input)
                && !ctx.is_explicit_common
            {
                let cand = raw_candidates.remove(pos);
                raw_candidates.insert(0, cand);
            }
        } else {
            let insert_idx =
                if is_top_distant && ctx.remembered_choice.is_none() && !ctx.is_explicit_common {
                    0
                } else {
                    1.min(raw_candidates.len())
                };
            raw_candidates.insert(insert_idx, def_norm);
        }
    }

    // 2. Verbatim Latin input escape hatch
    if ctx.input.is_ascii()
        && ctx.input.len() >= 2
        && !raw_candidates
            .iter()
            .any(|c| c.eq_ignore_ascii_case(ctx.input))
    {
        raw_candidates.push(ctx.input.to_string());
    }

    // 3. User remembered choice takes Slot 0 if not explicitly Avro grammar
    if let Some(ref choice) = ctx.remembered_choice {
        if !has_explicit_avro_intent(ctx.input) {
            if let Some(pos) = raw_candidates.iter().position(|c| c == choice) {
                raw_candidates.remove(pos);
            }
            raw_candidates.insert(0, choice.clone());
        }
    }

    if !has_explicit_avro_intent(ctx.input) && ctx.remembered_choice.is_none() {
        prioritize_common_or_override_candidate_with_context(
            ctx.input,
            &mut raw_candidates,
            !ctx.context.is_empty(),
        );
    }

    let primary = raw_candidates.first().cloned().unwrap_or_default();
    let primary_nfc = crate::probaho::nfc_normalize(&primary);

    append_prefix_matches(ctx.input, &primary_nfc, &mut raw_candidates);

    let mut normalized_candidates = Vec::with_capacity(raw_candidates.len());
    for c in raw_candidates {
        let norm = crate::probaho::nfc_normalize(&c);
        if !normalized_candidates.contains(&norm) {
            normalized_candidates.push(norm);
        }
    }

    (primary_nfc, normalized_candidates)
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
    if let Some(res) = stage_user_autocorrect(input, &lower, &db) {
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
    if let Some(res) = stage_explicit_avro_intent(input, &lower, &db) {
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
        return stage_short_input_phonetic(input, &lower, &def, &db);
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
        db: &db,
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
                candidates.push(v.clone());
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
    };
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

/// Unified Phase C / D resolver for Avro candidates and preedit.
/// Guarantees that:
/// 1. Bidirectional context scoring, candidate memory, inflected overrides, and geminate rules execute identically.
/// 2. `preedit` is 100% mathematically synchronized with `candidates[0]`.
/// 3. Normalization is single-pass NFC without duplicate allocations.
fn is_contextual_homophone_winner(raw: &str, context: &[&str], top: Option<&String>) -> bool {
    let Some(top_word) = top else {
        return false;
    };
    let mut probe = Vec::with_capacity(1);
    lekhani_core::phonetic::contextual_rules::apply_contextual_homophone_rules(
        raw,
        context,
        "",
        "",
        false,
        has_explicit_avro_intent(raw),
        &mut probe,
    );
    probe.first() == Some(top_word)
}

pub(crate) fn resolve_avro_candidates(
    raw: &str,
    context_words: &[&str],
    right_word: Option<&str>,
) -> (String, Vec<String>) {
    let (preedit, mut candidates) = transliterate_avro_with_context(raw, context_words);
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

    if !is_avro_special_fastpath(raw) && candidates.len() > 1 {
        if !context_words.is_empty() && right_word.is_some() {
            let scorer = crate::session::get_context_scorer();
            scorer.rank_candidates_in_place_bidirectional(
                context_words,
                right_word,
                &mut candidates,
            );
        }

        let db = crate::session::get_core_database();
        let top_is_in_dict = candidates
            .first()
            .map(|w| db.is_exact_dictionary_word(w) || db.trie.contains_exact(w))
            .unwrap_or(false);
        let preedit_is_in_dict =
            db.is_exact_dictionary_word(&preedit) || db.trie.contains_exact(&preedit);
        let top_len = candidates.first().map(|w| w.chars().count()).unwrap_or(0);
        let preedit_len = preedit.chars().count();
        let is_top_distant = !top_is_in_dict || (preedit_len >= 4 && top_len + 1 < preedit_len);
        let is_oov_gibberish = !preedit_is_in_dict && !top_is_in_dict && !has_override_word(raw);

        let is_contextual_rule_winner =
            is_contextual_homophone_winner(raw, context_words, candidates.first());

        if !is_contextual_rule_winner
            && (has_explicit_avro_intent(raw)
                || is_oov_gibberish
                || (is_top_distant && !has_candidate_memory))
        {
            if let Some(pos) = candidates.iter().position(|c| c == &preedit) {
                if pos != 0 {
                    let cand = candidates.remove(pos);
                    candidates.insert(0, cand);
                }
            }
        } else if has_candidate_memory {
            let db = crate::session::get_core_database();
            if let Ok(learner) = db.learner.read() {
                let preferred = learner
                    .lookup_input_error(raw)
                    .or_else(|| learner.candidate_memory.get(raw).map(|s| s.as_str()))
                    .or_else(|| {
                        learner
                            .candidate_memory
                            .get(&raw.to_lowercase())
                            .map(|s| s.as_str())
                    });
                if let Some(pref) = preferred {
                    if let Some(pos) = candidates.iter().position(|c| c == pref) {
                        let cand = candidates.remove(pos);
                        candidates.insert(0, cand);
                    }
                }
            };
        } else {
            prioritize_common_or_override_candidate_with_context(
                raw,
                &mut candidates,
                !context_words.is_empty(),
            );
        }

        let lower = raw.to_lowercase();
        let is_acronym_or_tech = is_common_tech_acronym(raw) || raw == "NaN" || raw == "Infinity";
        if is_acronym_or_tech {
            if let Some(pos) = candidates.iter().position(|c| c.eq_ignore_ascii_case(raw)) {
                let cand = candidates.remove(pos);
                candidates.insert(0, cand);
            }
        } else if let Some(pos) = candidates.iter().position(|c| c.eq_ignore_ascii_case(raw)) {
            // Keep English at Slot 1 (Rank 2) if it somehow landed at Slot 0
            if pos == 0 && candidates.len() > 1 {
                let cand = candidates.remove(0);
                candidates.insert(1, cand);
            }
        }

        if let Some(top_owned) = candidates.first().cloned() {
            let top = top_owned.as_str();
            let db = crate::session::get_core_database();
            let has_override = db.lookup_override_inflected(raw).is_some()
                || db.lookup_override_inflected(&lower).is_some();
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

            let freq_preedit = db.get_frequency(&preedit);
            let freq_top = db.get_frequency(top);
            let is_archaic_preedit = freq_preedit == 0 && freq_top > 0;

            let has_strong_context_preference = {
                let scorer = crate::session::get_context_scorer();
                let prev1 = context_words.last().copied();
                let prev2 = if context_words.len() >= 2 {
                    Some(context_words[context_words.len() - 2])
                } else {
                    None
                };
                let lm_top = scorer.lm().score_candidate(prev2, prev1, top);
                let lm_pre = scorer.lm().score_candidate(prev2, prev1, &preedit);
                (lm_top - lm_pre) >= 2.0
            };

            if !has_candidate_memory
                && !is_explicit_common
                && !has_override
                && (db.is_exact_dictionary_word(&preedit)
                    || db.trie.contains_exact(&preedit)
                    || is_geminate)
                && (!are_phonetically_compatible(&preedit, top)
                    || top.chars().count() > preedit.chars().count()
                    || ((is_geminate || !is_archaic_preedit)
                        && !has_strong_context_preference
                        && ((!is_geminate && !db.is_exact_dictionary_word(top))
                            || (is_geminate && !top.contains('্'))
                            || (is_geminate && top.contains('য') && !preedit.contains('য')))))
            {
                if let Some(pos) = candidates.iter().position(|c| c == &preedit) {
                    let cand = candidates.remove(pos);
                    candidates.insert(0, cand);
                }
            }

            if !has_override {
                let def_pure = get_avro_parser().convert(raw);
                let def_nfc = crate::probaho::nfc_normalize(&def_pure);
                if let Some(pos) = candidates
                    .iter()
                    .position(|c| crate::probaho::nfc_normalize(c) == def_nfc)
                {
                    let is_cch = raw.contains("cch") && def_nfc.contains("চ্ছ");
                    let is_geminate_hijacked =
                        is_geminate && top.contains('য') && !def_nfc.contains('য');
                    if (!are_phonetically_compatible(&def_nfc, top)
                        && db.is_exact_dictionary_word(&def_nfc))
                        || is_cch
                        || is_geminate_hijacked
                    {
                        let cand = candidates.remove(pos);
                        candidates.insert(0, cand);
                    }
                }
            }
        }

        if lower.contains("dd") && !lower.contains("dw") && !lower.contains("ddh") {
            if let Some(pos) = candidates.iter().position(|c| c.contains("দ্দ")) {
                if pos > 0
                    && candidates
                        .first()
                        .map(|s| s.contains("দ্ব"))
                        .unwrap_or(false)
                {
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

    let final_preedit = norm_candidates
        .first()
        .cloned()
        .unwrap_or_else(|| crate::probaho::nfc_normalize(&preedit));
    (final_preedit, norm_candidates)
}

pub(crate) struct AvroStrategy;

impl crate::layout::LayoutStrategy for AvroStrategy {
    fn process_key(
        &self,
        key: &str,
        state: &mut crate::session::SessionState,
    ) -> Result<crate::session::TypingResult, crate::error::LekhaniError> {
        state.composing_buffer.push_str(key);
        if state.pure_avro_mode {
            let parser = get_avro_parser();
            let preedit = parser.convert(&state.composing_buffer);
            let len = unicode_segmentation::UnicodeSegmentation::graphemes(preedit.as_str(), true)
                .count() as u32;
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
        let right_word = state.right_context.split_whitespace().next();

        let (preedit, candidates) =
            resolve_avro_candidates(&state.composing_buffer, words, right_word);
        let len = unicode_segmentation::UnicodeSegmentation::graphemes(preedit.as_str(), true)
            .count() as u32;
        Ok(crate::session::TypingResult {
            preedit,
            commit_text: None,
            candidates,
            cursor_position: len,
        })
    }

    fn resolve_space_commit(
        &self,
        raw: &str,
        state: &mut crate::session::SessionState,
        chosen_candidate: Option<&str>,
    ) -> String {
        let mut words_buf = [""; 16];
        let count = if !state.surrounding_context.is_empty() {
            crate::session::get_context_words(&state.surrounding_context, &mut words_buf)
        } else {
            0
        };
        let context_words = &words_buf[..count];

        if let Some(cand) = chosen_candidate {
            crate::probaho::nfc_normalize(cand)
        } else if state.pure_avro_mode {
            let parser = get_avro_parser();
            let def = parser.convert(raw);
            crate::probaho::nfc_normalize(&def)
        } else {
            let right_word = state.right_context.split_whitespace().next();
            let (preedit, candidates) = resolve_avro_candidates(raw, context_words, right_word);
            candidates.first().cloned().unwrap_or(preedit)
        }
    }

    fn regenerate_candidates(
        &self,
        state: &crate::session::SessionState,
        input: &str,
    ) -> Vec<String> {
        let mut words_buf = [""; 16];
        let count = if !state.surrounding_context.is_empty() {
            crate::session::get_context_words(&state.surrounding_context, &mut words_buf)
        } else {
            0
        };
        let words = &words_buf[..count];
        let right_word = state.right_context.split_whitespace().next();
        let (_, cands) = resolve_avro_candidates(input, words, right_word);
        cands
    }
}

#[cfg(test)]
#[path = "avro_tests.rs"]
mod tests;
