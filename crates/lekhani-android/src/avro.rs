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

pub fn get_common_word_candidates(input: &str) -> Option<&'static [&'static str]> {
    let lower = input.to_lowercase();
    get_common_words()
        .get(input)
        .or_else(|| get_common_words().get(lower.as_str()))
        .copied()
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
        m.insert("koto", &["কত", "কতো"][..]);
        m.insert("pore", &["পরে", "পড়ে"][..]);
        m.insert("ashole", &["আসলে"][..]);
        m.insert("hacche", &["হচ্ছে"][..]);
        m.insert("hocche", &["হচ্ছে"][..]);
        m.insert("jacche", &["যাচ্ছে"][..]);
        m.insert("khacche", &["খাচ্ছে"][..]);
        m.insert("dekhche", &["দেখছে"][..]);
        m.insert("bolche", &["বলছে"][..]);
        m.insert("sriti", &["স্মৃতি"][..]);
        m.insert("smriti", &["স্মৃতি"][..]);
        m.insert("dondho", &["দ্বন্দ্ব"][..]);
        m.insert("dwondwo", &["দ্বন্দ্ব"][..]);
        m.insert("trishna", &["তৃষ্ণা"][..]);
        m.insert("chotto", &["ছোট্ট"][..]);
        m.insert("onnya", &["অন্য"][..]);
        m.insert("onno", &["অন্য"][..]);
        m.insert("karun", &["কারণ"][..]);
        m.insert("karon", &["কারণ"][..]);
        m.insert("khuje", &["খুঁজে"][..]);
        m.insert("shonchoi", &["সঞ্চয়"][..]);
        m.insert("onjo", &["অঞ্জ"][..]);
        m.insert("shongko", &["শঙ্ক"][..]);
        m.insert("songko", &["শঙ্ক"][..]);
        m.insert("hot``hat``", &["হঠাৎ"][..]);
        m.insert("hothat", &["হঠাৎ"][..]);
        m.insert("kkh", &["ক্ষ"][..]);
        m.insert("jha", &["ঝা"][..]);
        m.insert("ko", &["কো", "ক"][..]);
        m.insert("khabo", &["খাবো", "খাব"][..]);
        m.insert("likhbo", &["লিখবো", "লিখব"][..]);
        m.insert("janbo", &["জানবো", "জানব"][..]);
        m.insert("parbo", &["পারবো", "পারব"][..]);
        m.insert("korcho", &["করছো", "করছ"][..]);
        m.insert("gechilam", &["গিয়েছিলাম", "গেছিলাম"][..]);
        m.insert("shongjog", &["সংযোগ"][..]);
        m.insert("songjog", &["সংযোগ"][..]);
        m.insert("barna", &["বর্ণ", "বারনা"][..]);
        m.insert("borno", &["বর্ণ"][..]);
        m.insert("za", &["যা", "জা"][..]);
        m.insert("Za", &["যা", "্যা"][..]);
        m.insert("ya", &["য়া", "ইয়া"][..]);
        m.insert("jao", &["যাও"][..]);
        m.insert("jan", &["যান", "জান"][..]);
        m.insert("konna", &["কন্যা"][..]);
        m.insert("kanya", &["কন্যা"][..]);
        m.insert("banya", &["বন্যা"][..]);
        m.insert("bonna", &["বন্যা"][..]);
        m.insert("dhonno", &["ধন্য"][..]);
        m.insert("britto", &["বৃত্ত"][..]);
        m.insert("ongko", &["অঙ্ক", "অংক"][..]);
        m.insert("shonkha", &["সংখ্যা"][..]);
        m.insert("shongkha", &["সংখ্যা"][..]);
        m.insert("songkha", &["সংখ্যা"][..]);
        m.insert("ghonta", &["ঘণ্টা", "ঘন্টা"][..]);
        m.insert("ghonTa", &["ঘণ্টা", "ঘন্টা"][..]);
        m.insert("kando", &["কাণ্ড", "কান্দ"][..]);
        m.insert("kanDo", &["কাণ্ড"][..]);
        m.insert("rong", &["রং", "রঙ"][..]);
        m.insert("shoshto", &["ষষ্ঠ", "ষষ্ট"][..]);
        m.insert("shoshTho", &["ষষ্ঠ"][..]);
        m.insert("mrtto", &["মর্ত্য"][..]);
        m.insert("mortyo", &["মর্ত্য"][..]);
        m.insert("purno", &["পূর্ণ"][..]);
        m.insert("porbo", &["পড়বো", "পরবো"][..]);
        m.insert("bolbo", &["বলবো", "বলব"][..]);
        m.insert("ticket", &["টিকেট", "টিকিট"][..]);
        m.insert("bhalobashbo", &["ভালোবাসবো", "ভালোবাসব"][..]);
        m.insert("valobashbo", &["ভালোবাসবো", "ভালোবাসব"][..]);
        m.insert("shunbo", &["শুনবো", "শুনব"][..]);
        m.insert("ashbo", &["আসবো", "আসব"][..]);
        m.insert("korbo", &["করবো", "করব"][..]);
        m.insert("jabo", &["যাবো", "যাব"][..]);
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

    let is_explicit_common = get_common_words().contains_key(input) || get_common_words().contains_key(lower.as_str());

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

    // -1b. Bundled system autocorrect (only if no user choice override and not an explicit common word)
    if remembered_choice.is_none() && !is_explicit_common {
        if let Some(replacement) = db.autocorrect.get(input).or_else(|| db.autocorrect.get(&lower)) {
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

    let parser = get_avro_parser();
    let def = parser.convert(input);

    // For short inputs (1 or 2 characters), guarantee pure phonetic preedit fidelity
    // so in-flight typing does not abruptly jump to multi-syllable shorthand words (e.g. "kn" -> "কেন").
    // Shorthand and high-frequency completions are offered as candidate strip suggestions.
    if input.chars().count() <= 2 {
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

        // Common word prefixes sorted by length so concise, high-frequency words appear first
        let common = get_common_words();
        let mut prefix_matches: Vec<(&'static str, &'static [&'static str])> = common
            .iter()
            .filter(|(&k, _)| k.starts_with(&lower) && k != lower)
            .map(|(&k, &w)| (k, w))
            .collect();
        prefix_matches.sort_by_key(|(k, _)| k.len());
        for (_, words) in prefix_matches {
            for &w in words {
                if !short_candidates.iter().any(|c| c == w) {
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

    let mut selected_idx = selected_idx;

    // 2. Ensure core common words are prioritized at the top of candidate list
    let is_explicit_common = get_common_words().contains_key(input) || get_common_words().contains_key(lower.as_str());
    if let Some(&words) = get_common_words().get(input).or_else(|| get_common_words().get(lower.as_str())) {
        for (i, &w) in words.iter().enumerate() {
            let ws = w.to_string();
            if let Some(pos) = candidates.iter().position(|c| c == &ws) {
                candidates.remove(pos);
            }
            candidates.insert(i.min(candidates.len()), ws);
        }
        selected_idx = 0;
    }

    // 2b. If the user explicitly typed an inflection ending in 'bo' or 'cho' (e.g. khabo -> খাবো, korcho -> করছো),
    // prioritize the matching 'ো'-inflected form from candidates (unless explicitly ordered in COMMON_WORDS)
    if !is_explicit_common && (lower.ends_with("bo") || lower.ends_with("cho")) && !candidates.is_empty() {
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

    // 2c. Ensure def (deterministic Avro parser output) is present in candidates without overriding dictionary matches
    if !def.is_empty() && !candidates.iter().any(|c| c == &def) {
        candidates.insert(1.min(candidates.len()), def.clone());
    }

    // 2d. Exact case-sensitivity fidelity & Sentence-initial Titlecase smart promotion:
    // If the user typed an uppercase Avro character ("OIUDGJNRSTYZ"):
    // - If it's a mobile Titlecase (e.g. "Tomar", "Tumi") where the lowercase form is a valid
    //   high-frequency dictionary word ("তোমার", "তুমি") but the uppercase form is non-existent
    //   ("টোমার", "টুমি"), promote the valid word while retaining def as candidate.
    // - Otherwise, prioritize def at candidate index 0 to honor desktop Avro muscle memory ("Daktar", "Dhaka", "poRa").
    let has_explicit_avro_case = input.chars().any(|c| {
        c.is_ascii_uppercase() && "OIUDGJNRSTYZ".contains(c)
    });
    if has_explicit_avro_case && !def.is_empty() {
        let is_titlecase = input.chars().next().is_some_and(|c| c.is_ascii_uppercase())
            && input.chars().skip(1).all(|c| c.is_ascii_lowercase());

        let resolved_lower = if let Some(ac) = db.autocorrect.get(&lower) {
            ac.clone()
        } else if let Some(&words) = get_common_words().get(&lower.as_str()) {
            words.first().copied().unwrap_or("").to_string()
        } else {
            parser.convert(&lower)
        };
        let def_in_dict = db.is_exact_dictionary_word(&def);
        let lower_in_dict = db.is_exact_dictionary_word(&resolved_lower);

        if is_titlecase && !def_in_dict && lower_in_dict {
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
    }

    // 3. QWERTY adjacency & transposition auto-correction for fat-finger typos on touchscreen
    let mut common_typos = Vec::new();
    let mut dict_typos = Vec::new();
    if lower.len() >= 3 {
        let chars: Vec<char> = lower.chars().collect();
        for (i, &ch) in chars.iter().enumerate() {
            for &adj in crate::english::get_qwerty_adjacent_keys(ch) {
                let mut sub = chars.clone();
                sub[i] = adj;
                let cand_key: String = sub.into_iter().collect();
                if let Some(&words) = get_common_words().get(cand_key.as_str()) {
                    for &w in words {
                        let ws = w.to_string();
                        if !candidates.contains(&ws) && !common_typos.contains(&ws) {
                            common_typos.push(ws);
                        }
                    }
                } else if let Some(ac) = db.autocorrect.get(&cand_key) {
                    if !candidates.contains(ac) && !common_typos.contains(ac) {
                        common_typos.push(ac.clone());
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
        if common_typos.is_empty() && chars.len() >= 3 {
            for i in 0..chars.len() - 1 {
                let mut swapped = chars.clone();
                swapped.swap(i, i + 1);
                let swap_key: String = swapped.into_iter().collect();
                if let Some(&words) = get_common_words().get(swap_key.as_str()) {
                    for &w in words {
                        let ws = w.to_string();
                        if !candidates.contains(&ws) && !common_typos.contains(&ws) {
                            common_typos.push(ws);
                        }
                    }
                } else if let Some(ac) = db.autocorrect.get(&swap_key) {
                    if !candidates.contains(ac) && !common_typos.contains(ac) {
                        common_typos.push(ac.clone());
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
    let mut typo_corrections = common_typos;
    typo_corrections.extend(dict_typos);

    // If mistyped (not an explicit common word), place the top typo correction at candidate index 1
    // (so UI strip has: 1st: Raw English, 2nd: Direct conversion, 3rd: Corrected word)
    if !is_explicit_common && !typo_corrections.is_empty() {
        for (idx, corr) in typo_corrections.into_iter().enumerate() {
            if idx < 2 {
                candidates.insert(1 + idx, corr);
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
        // index 0: direct conversion "শমি"
        // index 1: typo auto-correction "আমি"
        let (pre_smi, cands_smi) = transliterate_avro("smi");
        assert_eq!(pre_smi, "শমি");
        assert_eq!(cands_smi.first().map(|s| s.as_str()), Some("শমি"));
        assert_eq!(cands_smi.get(1).map(|s| s.as_str()), Some("আমি"));

        // 'i' is next to 'o' on QWERTY -> "bhali" typo suggests "ভালো"
        // index 0: direct conversion "ভালী"
        // index 1: typo auto-correction "ভালো"
        let (pre_bhali, cands_bhali) = transliterate_avro("bhali");
        assert_eq!(pre_bhali, "ভালি");
        assert_eq!(cands_bhali.first().map(|s| s.as_str()), Some("ভালি"));
        assert_eq!(cands_bhali.get(1).map(|s| s.as_str()), Some("ভালো"));
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

    #[test]
    #[serial]
    fn test_advanced_phonetic_expansion() {
        // 1. Lowercase retroflex expansion: "daktar" -> "ডাক্তার"
        let (daktar, daktar_cands) = transliterate_avro("daktar");
        assert_eq!(daktar, "ডাক্তার", "daktar should produce ডাক্তার");
        assert!(daktar_cands.contains(&"ডাক্তার".to_string()));

        // 2. Strict uppercase Avro muscle memory: "Daktar" -> "ডাক্তার", "poRa" -> "পড়া"
        let (d_aktar, _) = transliterate_avro("Daktar");
        assert_eq!(d_aktar, "ডাক্তার", "Daktar with capital D must prioritize ডাক্তার");

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
        assert!(bhslo_cands.contains(&"ভালো".to_string()), "bhslo must recover ভালো via QWERTY proximity");

        // 6. Sentence-initial Titlecase smart promotion: "Tomar" -> "তোমার", "Tumi" -> "তুমি"
        let (tomar_title, _) = transliterate_avro("Tomar");
        assert_eq!(tomar_title, "তোমার", "Titlecase 'Tomar' must promote তোমার over টোমার");

        let (tumi_title, _) = transliterate_avro("Tumi");
        assert_eq!(tumi_title, "তুমি", "Titlecase 'Tumi' must promote তুমি over টুমি");

        // 7. 1-step adjacent letter transposition recovery: "bhlao" ('l' and 'a' swapped) -> offers "ভালো"
        let (_, bhlao_cands) = transliterate_avro("bhlao");
        assert!(bhlao_cands.contains(&"ভালো".to_string()), "bhlao must recover ভালো via transposition");
    }
}
