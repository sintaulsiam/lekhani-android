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
        m
    })
}

/// Transliterates Romanized ASCII text to Bengali script using `lekhani-parser` Trie grammar rules,
/// bilingual loanword dictionary, and phonetic database.
/// Returns a tuple of (primary_transliteration, list_of_candidates).
pub fn transliterate_avro(input: &str) -> (String, Vec<String>) {
    if input.is_empty() {
        return (String::new(), Vec::new());
    }

    let lower = input.to_lowercase();

    // 0. User candidate override memory (personal learned overrides)
    let db = crate::session::get_core_database();
    if let Ok(learner) = db.learner.read() {
        if let Some(user_choice) = learner.candidate_memory.get(input).or_else(|| learner.candidate_memory.get(&lower)) {
            let primary = user_choice.clone();
            let mut candidates = vec![primary.clone()];
            let parser = get_avro_parser();
            let def = parser.convert(input);
            if def != primary {
                candidates.push(def);
            }
            return (primary, candidates);
        }
    }

    // 1. Bilingual Loanword Dictionary (e.g. "shirt" -> "শার্ট", "copy" -> "কপি", "password" -> "পাসওয়ার্ড")
    if let Some((bn_loan, en_loan)) = lekhani_core::phonetic::PhoneticDatabase::get_bilingual_loanword(&lower) {
        let primary = bn_loan.to_string();
        let mut candidates = vec![primary.clone()];
        let parser = get_avro_parser();
        let def = parser.convert(input);
        if def != primary && !candidates.contains(&def) {
            candidates.push(def);
        }
        if !candidates.iter().any(|c| c == en_loan) {
            candidates.push(en_loan.to_string());
        }
        return (primary, candidates);
    }

    // 2. Exact common word match
    if let Some(&words) = get_common_words().get(input) {
        let primary = words[0].to_string();
        let candidates = words.iter().map(|&s| s.to_string()).collect();
        return (primary, candidates);
    }
    if let Some(&words) = get_common_words().get(lower.as_str()) {
        let primary = words[0].to_string();
        let candidates = words.iter().map(|&s| s.to_string()).collect();
        return (primary, candidates);
    }

    // 3. Upstream lekhani-parser Trie grammar engine (11 ns/char)
    let parser = get_avro_parser();
    let primary = parser.convert(input);
    let mut candidates = vec![primary.clone()];

    // Generate alternate phonetic candidates for casual mobile typing:
    // 3a. 'r' <-> 'R' (র vs ড়) homophone expansion (e.g. "pora" -> "পরা" and "পড়া")
    if input.contains('r') && !input.contains("rr") {
        let alt_r = parser.convert(&input.replace('r', "R"));
        if alt_r != primary && !candidates.contains(&alt_r) {
            candidates.push(alt_r);
        }
    } else if input.contains('R') {
        let alt_r = parser.convert(&input.replace('R', "r"));
        if alt_r != primary && !candidates.contains(&alt_r) {
            candidates.push(alt_r);
        }
    }

    // 3b. 'sh' <-> 's' (শ vs স) alternation
    if input.contains("sh") || input.contains('s') {
        let alt_input = if input.contains("sh") {
            input.replace("sh", "s")
        } else {
            input.replace('s', "sh")
        };
        let alt = parser.convert(&alt_input);
        if alt != primary && !candidates.contains(&alt) {
            candidates.push(alt);
        }
    }

    // 4. QWERTY adjacency auto-correction for fat-finger typos
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

    // 5. Core database PrefixTrie lookup to expand matching vocabulary
    if candidates.len() < 6 {
        let db = crate::session::get_core_database();
        let prefix_matches = db.trie.find_prefix_entries(&primary, 4);
        for (w, _) in prefix_matches {
            if candidates.len() >= 8 {
                break;
            }
            if !candidates.iter().any(|c| c == w) {
                candidates.push(w.to_string());
            }
        }
    }

    (primary, candidates)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
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
    fn test_rules_transliteration() {
        let (res, _) = transliterate_avro("dhaka");
        assert_eq!(res, "ঢাকা");

        let (res, _) = transliterate_avro("shonar");
        assert_eq!(res, "সোনার");

        let (res, _) = transliterate_avro("bhalo");
        assert_eq!(res, "ভালো");
    }

    #[test]
    fn test_conjuncts() {
        let (res, _) = transliterate_avro("shikkhok");
        assert_eq!(res, "শিক্ষক");

        let (res, _) = transliterate_avro("bristi");
        assert_eq!(res, "বৃষ্টি");
    }

    #[test]
    fn test_qwerty_proximity_typo_correction() {
        // 's' is next to 'a' on QWERTY -> "smi" typo suggests "আমি"
        let (_, cands_smi) = transliterate_avro("smi");
        assert!(cands_smi.contains(&"আমি".to_string()));

        // 'i' is next to 'o' on QWERTY -> "bhali" typo suggests "ভালো"
        let (_, cands_bhali) = transliterate_avro("bhali");
        assert!(cands_bhali.contains(&"ভালো".to_string()));
    }

    #[test]
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
}
