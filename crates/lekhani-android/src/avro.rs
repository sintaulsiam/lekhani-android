//! Avro Phonetic Transliteration Engine powered by `lekhani-parser` and `lekhani-ai`
//!
//! Provides deterministic Roman-to-Bengali transliteration according to
//! classic Avro Phonetic muscle memory standards using the upstream
//! zero-allocation L1 Trie grammar compiler from `lekhani-parser`.

use hashbrown::HashMap;
use std::sync::OnceLock;

static PARSER: OnceLock<lekhani_parser::LekhaniParser> = OnceLock::new();
static COMMON_WORDS: OnceLock<HashMap<&'static str, &'static [&'static str]>> = OnceLock::new();

pub fn get_avro_parser() -> &'static lekhani_parser::LekhaniParser {
    PARSER.get_or_init(lekhani_parser::default_avro_parser)
}

fn get_common_words() -> &'static HashMap<&'static str, &'static [&'static str]> {
    COMMON_WORDS.get_or_init(|| {
        let mut m = HashMap::new();
        m.insert("ami", &["আমি"][..]);
        m.insert("amar", &["আমার"][..]);
        m.insert("amake", &["আমাকে"][..]);
        m.insert("amader", &["আমাদের"][..]);
        m.insert("tumi", &["তুমি"][..]);
        m.insert("tomar", &["তোমার"][..]);
        m.insert("tomake", &["তোমাকে"][..]);
        m.insert("apni", &["আপনি"][..]);
        m.insert("apnar", &["আপনার"][..]);
        m.insert("kemon", &["কেমন"][..]);
        m.insert("achen", &["আছেন"][..]);
        m.insert("achhi", &["আছি"][..]);
        m.insert("achhe", &["আছে"][..]);
        m.insert("bangla", &["বাংলা"][..]);
        m.insert("bangladesh", &["বাংলাদেশ"][..]);
        m.insert("dhaka", &["ঢাকা"][..]);
        m.insert("bhalo", &["ভালো"][..]);
        m.insert("desh", &["দেশ"][..]);
        m.insert("shonar", &["সোনার", "শোনার"][..]);
        m.insert("sonar", &["সোনার"][..]);
        m.insert("akash", &["আকাশ"][..]);
        m.insert("shikkhok", &["শিক্ষক"][..]);
        m.insert("shikkha", &["শিক্ষা"][..]);
        m.insert("brriShTi", &["বৃষ্টি"][..]);
        m.insert("bristi", &["বৃষ্টি"][..]);
        m.insert("dhonnobad", &["ধন্যবাদ"][..]);
        m.insert("kothay", &["কোথায়"][..]);
        m.insert("ki", &["কি", "কী"][..]);
        m.insert("kee", &["কী"][..]);
        m.insert("ekhon", &["এখন"][..]);
        m.insert("protidin", &["প্রতিদিন"][..]);
        m.insert("shob", &["সব"][..]);
        m.insert("sob", &["সব"][..]);
        m.insert("shundor", &["সুন্দর"][..]);
        m.insert("sundor", &["সুন্দর"][..]);
        m.insert("bhalobasha", &["ভালোবাসা"][..]);
        m.insert("bondhu", &["বন্ধু"][..]);
        m.insert("manush", &["মানুষ"][..]);
        m.insert("shomoy", &["সময়"][..]);
        m.insert("somoy", &["সময়"][..]);
        m.insert("jibon", &["জীবন"][..]);
        m.insert("kaj", &["কাজ"][..]);
        m.insert("khobor", &["খবর"][..]);
        m.insert("pani", &["পানি"][..]);
        m.insert("jol", &["জল"][..]);
        m.insert("bhai", &["ভাই"][..]);
        m.insert("bon", &["বোন"][..]);
        m.insert("baba", &["বাবা"][..]);
        m.insert("ma", &["মা"][..]);
        m.insert("kolkata", &["কলকাতা"][..]);
        m.insert("onek", &["অনেক"][..]);
        m.insert("aro", &["আরও", "আরো"][..]);
        m.insert("khub", &["খুব"][..]);
        m.insert("ekta", &["একটা"][..]);
        m.insert("kintu", &["কিন্তু"][..]);
        m.insert("tai", &["তাই"][..]);
        m.insert("she", &["সে"][..]);
        m.insert("tar", &["তার"][..]);
        m.insert("tara", &["তারা"][..]);
        m.insert("shobai", &["সবাই"][..]);
        m.insert("sobai", &["সবাই"][..]);
        m.insert("hobe", &["হবে"][..]);
        m.insert("hoy", &["হয়"][..]);
        m.insert("hoye", &["হয়ে"][..]);
        m.insert("geche", &["গেছে"][..]);
        m.insert("korchi", &["করছি"][..]);
        m.insert("kori", &["করি"][..]);
        m.insert("koro", &["করো"][..]);
        m.insert("kono", &["কোনো", "কোন"][..]);
        m.insert("kon", &["কোন", "কোনো"][..]);
        m.insert("boi", &["বই"][..]);
        m.insert("pora", &["পরা", "পড়া"][..]);
        m.insert("poRa", &["পড়া", "পরা"][..]);
        m.insert("valo", &["ভালো"][..]);
        m.insert("valobasha", &["ভালোবাসা"][..]);
        m.insert("valobashi", &["ভালোবাসি"][..]);
        m.insert("shathe", &["সাথে"][..]);
        m.insert("sathe", &["সাথে"][..]);
        m.insert("kotha", &["কথা"][..]);
        m.insert("kichu", &["কিছু"][..]);
        m.insert("gari", &["গাড়ি"][..]);
        m.insert("gaRi", &["গাড়ি"][..]);
        m.insert("bari", &["বাড়ি"][..]);
        m.insert("baRi", &["বাড়ি"][..]);
        m.insert("rasta", &["রাস্তা"][..]);
        m.insert("gaan", &["গান"][..]);
        m.insert("gan", &["গান"][..]);
        // Conversational chat contractions & shortcuts
        m.insert("kmn", &["কেমন"][..]);
        m.insert("kemn", &["কেমন"][..]);
        m.insert("aso", &["আছো"][..]);
        m.insert("asen", &["আছেন"][..]);
        m.insert("asi", &["আছি"][..]);
        m.insert("kisu", &["কিছু"][..]);
        m.insert("khbr", &["খবর"][..]);
        m.insert("thnx", &["ধন্যবাদ"][..]);
        m.insert("thx", &["ধন্যবাদ"][..]);
        m.insert("apnr", &["আপনার"][..]);
        m.insert("tmr", &["তোমার"][..]);
        m.insert("amr", &["আমার"][..]);
        m.insert("amdr", &["আমাদের"][..]);
        m.insert("tmdr", &["তোমাদের"][..]);
        m.insert("sbai", &["সবাই"][..]);
        m.insert("shb", &["সব"][..]);
        m.insert("ekhn", &["এখন"][..]);
        m.insert("tkhn", &["তখন"][..]);
        m.insert("kn", &["কেন"][..]);
        m.insert("hbe", &["হবে"][..]);
        m.insert("hoise", &["হয়েছে"][..]);
        m.insert("krbo", &["করবো"][..]);
        m.insert("krben", &["করবেন"][..]);
        m.insert("krso", &["করছো"][..]);
        m.insert("krse", &["করছে"][..]);
        m.insert("bujhsi", &["বুঝেছি"][..]);
        m.insert("thik", &["ঠিক"][..]);
        m.insert("shotti", &["সত্যি"][..]);
        m.insert("sotti", &["সত্যি"][..]);
        m.insert("plz", &["প্লিজ"][..]);
        m.insert("pls", &["প্লিজ"][..]);
        m
    })
}

/// Transliterates Romanized ASCII text to Bengali script using `lekhani-core`'s
/// production PhoneticSuggestion engine, bilingual loanwords, and Avro Trie parser.
///
/// Returns (primary_transliteration, list_of_candidates).
pub fn transliterate_avro_with_context(input: &str, context: &[&str]) -> (String, Vec<String>) {
    if input.is_empty() {
        return (String::new(), Vec::new());
    }

    let lower = input.to_lowercase();
    let db = crate::session::get_core_database();

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
            append_prefix_matches(&primary, &mut candidates);
            return (primary, candidates);
        }
    }

    // 0. User candidate override memory
    let mut candidate_memory = HashMap::new();
    let mut remembered_choice: Option<String> = None;
    if let Ok(learner) = db.learner.read() {
        if let Some(user_choice) = learner.candidate_memory.get(input).or_else(|| learner.candidate_memory.get(&lower)) {
            candidate_memory.insert(input.to_string(), user_choice.clone());
            remembered_choice = Some(user_choice.clone());
        }
    }

    let parser = get_avro_parser();
    let def = parser.convert(input);

    // For single-character inputs: return ONLY the phonetic preedit.
    // Showing multi-syllable completions (e.g. "k" → "কেমন") from a single keypress is bad UX:
    //   • The preedit jumps to a long word the user hasn't signalled intent for.
    //   • Space auto-complete would commit a completely wrong word.
    // Completions and shorthand are surfaced starting from 2-char inputs.
    if input.chars().count() == 1 {
        let primary = if let Some(ref choice) = remembered_choice {
            choice.clone()
        } else {
            def.clone()
        };
        let mut single_candidates = vec![primary.clone()];
        // Only include shorthand that *exactly* matches the single char (rare user-defined shortcuts)
        if let Some(shorthand) = db.shorthand.get(input).or_else(|| db.shorthand.get(&lower)) {
            if !single_candidates.iter().any(|c| c == shorthand) {
                single_candidates.push(shorthand.clone());
            }
        }
        if !single_candidates.iter().any(|c| c == &def) {
            single_candidates.push(def);
        }
        return (primary, single_candidates);
    }

    // For 2-character inputs: show phonetic preedit + targeted prefix completions.
    // Two chars is a meaningful partial-word signal so helpful completions are warranted.
    if input.chars().count() == 2 {
        let mut short_candidates = Vec::with_capacity(12);

        // 1. Direct phonetic transliteration is always the primary preedit
        let primary = if let Some(ref choice) = remembered_choice {
            choice.clone()
        } else {
            def.clone()
        };
        short_candidates.push(primary.clone());

        // 2. Shorthand & Common Contractions (offered as secondary suggestions)
        if let Some(&words) = get_common_words().get(input).or_else(|| get_common_words().get(lower.as_str())) {
            for &w in words {
                if !short_candidates.iter().any(|c| c == w) {
                    short_candidates.push(w.to_string());
                }
            }
        }
        if let Some(shorthand) = db.shorthand.get(input).or_else(|| db.shorthand.get(&lower)) {
            if !short_candidates.iter().any(|c| c == shorthand) {
                short_candidates.push(shorthand.clone());
            }
        }

        // Common word prefixes (e.g. "kn" -> "কেন")
        let common = get_common_words();
        for (&k, &words) in common.iter() {
            if k.starts_with(&lower) && k != lower {
                for &w in words {
                    if !short_candidates.iter().any(|c| c == w) {
                        short_candidates.push(w.to_string());
                    }
                    if short_candidates.len() >= 6 {
                        break;
                    }
                }
            }
            if short_candidates.len() >= 6 {
                break;
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
    let (mut candidates, selected_idx) = {
        let mut sugg = match sugg_mutex.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        sugg.suggest_with_multi_context(
            input,
            context,
            false,
            true,
            &candidate_memory,
        )
    };

    if candidates.is_empty() {
        let parser = get_avro_parser();
        let def = parser.convert(input);
        candidates.push(def);
    }

    // 2. Ensure core common words are present in candidate list for classic muscle memory
    if let Some(&words) = get_common_words().get(input).or_else(|| get_common_words().get(lower.as_str())) {
        for &w in words {
            if !candidates.contains(&w.to_string()) {
                candidates.push(w.to_string());
            }
        }
    }

    // 3. QWERTY adjacency auto-correction for fat-finger typos on touchscreen
    if lower.len() >= 3 {
        let chars: Vec<char> = lower.chars().collect();
        for (i, &ch) in chars.iter().enumerate() {
            for &adj in crate::english::get_qwerty_adjacent_keys(ch) {
                let mut sub = chars.clone();
                sub[i] = adj;
                let cand_key: String = sub.into_iter().collect();
                if let Some(&words) = get_common_words().get(cand_key.as_str()) {
                    for &w in words {
                        if !candidates.contains(&w.to_string()) {
                            candidates.push(w.to_string());
                        }
                    }
                }
            }
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

    // 4. Core database PrefixTrie lookup to expand matching vocabulary
    append_prefix_matches(&primary, &mut candidates);

    (primary, candidates)
}

/// Convenience wrapper for zero-context transliteration
pub fn transliterate_avro(input: &str) -> (String, Vec<String>) {
    transliterate_avro_with_context(input, &[])
}

fn append_prefix_matches(primary: &str, candidates: &mut Vec<String>) {
    if primary.chars().count() < 2 {
        return;
    }
    let db = crate::session::get_core_database();
    let prefix_matches = db.trie.find_prefix_entries(primary, 4);
    for (w, _) in prefix_matches {
        if candidates.len() >= 8 {
            break;
        }
        if !candidates.iter().any(|c| c == w) {
            candidates.push(w.to_string());
        }
    }
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
        let (_, cands_smi) = transliterate_avro("smi");
        assert!(cands_smi.contains(&"আমি".to_string()));

        // 'i' is next to 'o' on QWERTY -> "bhali" typo suggests "ভালো"
        let (_, cands_bhali) = transliterate_avro("bhali");
        assert!(cands_bhali.contains(&"ভালো".to_string()));
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
        assert_eq!(nomoshkar, "নমস্কার", "nomoshkar should transliterate to নমস্কার");
        let (porishkar, _) = transliterate_avro("porishkar");
        assert_eq!(porishkar, "পরিষ্কার", "porishkar should transliterate to পরিষ্কার");
        let (puroshkar, _) = transliterate_avro("puroshkar");
        assert_eq!(puroshkar, "পুরস্কার", "puroshkar should transliterate to পুরস্কার");
        let (abishkar, _) = transliterate_avro("abishkar");
        assert_eq!(abishkar, "আবিষ্কার", "abishkar should transliterate to আবিষ্কার");
        let (lokkhi, _) = transliterate_avro("lokkhi");
        assert_eq!(lokkhi, "লক্ষ্মী", "lokkhi should transliterate to লক্ষ্মী");
        let (rokkha, _) = transliterate_avro("rokkha");
        assert_eq!(rokkha, "রক্ষা", "rokkha should transliterate to রক্ষা");
        let (bhabishshot, _) = transliterate_avro("bhabishshot");
        assert_eq!(bhabishshot, "ভবিষ্যৎ", "bhabishshot should transliterate to ভবিষ্যৎ");
        let (shobcheye, _) = transliterate_avro("shobcheye");
        assert!(shobcheye == "সবচেয়ে" || shobcheye == "সবচেয়ে", "shobcheye should transliterate to সবচেয়ে");
        let (chikitshok, _) = transliterate_avro("chikitshok");
        assert_eq!(chikitshok, "চিকিৎসক", "chikitshok should transliterate to চিকিৎসক");
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
        assert_eq!(protiddhoni, "প্রতিধ্বনি", "protiddhoni should transliterate to প্রতিধ্বনি");
        let (bhalobasha, _) = transliterate_avro("bhalobasha");
        assert_eq!(bhalobasha, "ভালোবাসা", "bhalobasha should transliterate to ভালোবাসা");
        let (valobasha, _) = transliterate_avro("valobasha");
        assert_eq!(valobasha, "ভালোবাসা", "valobasha should transliterate to ভালোবাসা");
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
}
