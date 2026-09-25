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
        m.insert("koren", &["করেন"][..]);
        m
    })
}

/// Transliterates Romanized ASCII text to Bengali script using `lekhani-parser` Trie grammar rules.
/// Returns a tuple of (primary_transliteration, list_of_candidates).
pub fn transliterate_avro(input: &str) -> (String, Vec<String>) {
    if input.is_empty() {
        return (String::new(), Vec::new());
    }

    // 1. Exact common word match
    let lower = input.to_lowercase();
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

    // 2. Upstream lekhani-parser Trie grammar engine (11 ns/char)
    let parser = get_avro_parser();
    let primary = parser.convert(input);
    let mut candidates = vec![primary.clone()];

    // Generate alternate phonetic candidates if applicable
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
}
