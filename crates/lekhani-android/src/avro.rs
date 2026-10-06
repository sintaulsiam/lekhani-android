//! Avro Phonetic Transliteration Engine powered by `lekhani-parser` and `lekhani-ai`
//!
//! Provides deterministic Roman-to-Bengali transliteration according to
//! classic Avro Phonetic muscle memory standards using the upstream
//! zero-allocation L1 Trie grammar compiler from `lekhani-parser`.

use edit_distance::edit_distance;
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

/// Prioritizes candidate matching static common words or data-driven phonetic overrides.
/// Places the preferred candidate at position 0 without reallocating when possible.
pub fn prioritize_common_or_override_candidate(input: &str, candidates: &mut Vec<String>) {
    if candidates.len() <= 1 {
        return;
    }
    // 1. Check static common words (cold-start fallback)
    if let Some(common_list) = get_common_word_candidates(input) {
        if common_list.len() == 1 {
            let target_nfc = crate::probaho::nfc_normalize(common_list[0]);
            if let Some(pos) = candidates
                .iter()
                .position(|c| crate::probaho::nfc_normalize(c) == target_nfc)
            {
                let _ = candidates.remove(pos);
                candidates.insert(0, target_nfc);
                return;
            }
        } else {
            // Multi-homophone common list (e.g. "pora" -> ["পরা", "পড়া"], "ki" -> ["কি", "কী"]):
            // If the top candidate is ALREADY in the common_list (e.g. ranked by context LM), keep it at position 0!
            if let Some(first_cand) = candidates.first() {
                let first_nfc = crate::probaho::nfc_normalize(first_cand);
                if common_list
                    .iter()
                    .any(|&w| crate::probaho::nfc_normalize(w) == first_nfc)
                {
                    return;
                }
            }
            for &target in common_list {
                let target_nfc = crate::probaho::nfc_normalize(target);
                if let Some(pos) = candidates
                    .iter()
                    .position(|c| crate::probaho::nfc_normalize(c) == target_nfc)
                {
                    let _ = candidates.remove(pos);
                    candidates.insert(0, target_nfc);
                    return;
                }
            }
        }
        if let Some(&first_common) = common_list.first() {
            let norm = crate::probaho::nfc_normalize(first_common);
            if !candidates.contains(&norm) {
                candidates.insert(0, norm);
            }
            return;
        }
    }
    // 2. Check dynamic/bundled supervised phonetic overrides (phonetic_overrides.bin / json)
    let lower = input.to_lowercase();
    let db = crate::session::get_core_database();
    let is_title_case = input.len() >= 3
        && input.chars().next().is_some_and(|c| c.is_uppercase())
        && input
            .chars()
            .skip(1)
            .all(|c| c.is_lowercase() || !c.is_alphabetic());
    let override_opt = db.lookup_override(input).or_else(|| {
        if is_title_case {
            db.lookup_override(&lower)
        } else {
            None
        }
    });
    if let Some(overrides) = override_opt {
        let mut inserted_idx = 0;
        for (override_word, score) in overrides {
            if let Some(pos) = candidates.iter().position(|c| c == override_word) {
                let cand = candidates.remove(pos);
                candidates.insert(inserted_idx, cand);
                inserted_idx += 1;
            } else if score >= 0.70 {
                candidates.insert(inserted_idx, override_word.to_string());
                inserted_idx += 1;
            } else if !candidates.iter().any(|c| c == override_word) {
                if candidates.len() >= 12 {
                    candidates.pop();
                }
                candidates.push(override_word.to_string());
            }
        }
    }
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
        m.insert("oi", &["ঐ", "ওই"][..]);
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
        m.insert("pera", &["প্যারা", "পেরা"][..]);
        m.insert("pyara", &["প্যারা"][..]);
        m.insert("shala", &["শালা"][..]);
        m.insert("sala", &["শালা"][..]);
        m.insert("dost", &["দোস্ত"][..]);
        m.insert("bro", &["ব্রো"][..]);
        m.insert("vai", &["ভাই"][..]);
        m.insert("kire", &["কিরে"][..]);
        m.insert("khapang", &["খাপ্যাং"][..]);
        m.insert("mama", &["মামা"][..]);
        m.insert("choto", &["ছোট", "ছোটো"][..]);
        m.insert("boro", &["বড়", "বড়", "বোরো"][..]);
        m.insert("boRo", &["বড়", "বড়"][..]);
        m.insert("re", &["রে"][..]);
        m.insert("na", &["না"][..]);
        m.insert("pora", &["পরা", "পড়া"][..]);
        m.insert("poRa", &["পড়া", "পরা"][..]);
        m.insert("valo", &["ভালো"][..]);
        m.insert("valobasha", &["ভালোবাসা"][..]);
        m.insert("valobashi", &["ভালোবাসি"][..]);
        m.insert("shathe", &["সাথে"][..]);
        m.insert("sathe", &["সাথে"][..]);
        m.insert("kotha", &["কথা"][..]);
        m.insert("protom", &["প্রথম"][..]);
        m.insert("prothom", &["প্রথম"][..]);
        m.insert("kabar", &["খাবার", "কাবার"][..]);
        m.insert("khabar", &["খাবার"][..]);
        m.insert("takbo", &["থাকবো", "থাকব"][..]);
        m.insert("thakbo", &["থাকবো", "থাকব"][..]);
        m.insert("balobasa", &["ভালোবাসা"][..]);
        m.insert("balobasha", &["ভালোবাসা"][..]);
        m.insert("bhalobasa", &["ভালোবাসা"][..]);
        m.insert("kichu", &["কিছু"][..]);
        m.insert("gari", &["গাড়ি"][..]);
        m.insert("gaRi", &["গাড়ি"][..]);
        m.insert("bari", &["বাড়ি"][..]);
        m.insert("baRi", &["বাড়ি"][..]);
        m.insert("shari", &["শাড়ি", "সারি"][..]);
        m.insert("churi", &["চুড়ি", "ছুরি", "চুরি"][..]);
        m.insert("poro", &["পড়ো", "পরো"][..]);
        m.insert("hole", &["হলে", "হোল"][..]);
        m.insert("hote", &["হতে", "হটে"][..]);
        m.insert("nice", &["নিচে", "নাইস"][..]);
        m.insert("tin", &["তিন", "টিন"][..]);
        m.insert("chil", &["ছিল", "চিল"][..]);
        m.insert("apu", &["আপু", "অপু"][..]);
        m.insert("rasta", &["রাস্তা"][..]);
        m.insert("gaan", &["গান"][..]);
        m.insert("gan", &["গান"][..]);
        // Conversational chat contractions & shortcuts
        m.insert("kmn", &["কেমন"][..]);
        m.insert("kemn", &["কেমন"][..]);
        m.insert("aso", &["আছো"][..]);
        m.insert("asen", &["আছেন"][..]);
        m.insert("asi", &["আছি"][..]);
        m.insert("chill", &["চিল"][..]);
        m.insert("kop", &["কোপ"][..]);
        m.insert("disi", &["দিছি", "দিসি"][..]);
        m.insert("dhuktesi", &["ঢুকতেছি"][..]);
        m.insert("dhuksi", &["ঢুকেছি", "ঢুকছি"][..]);
        m.insert("aytasi", &["আইতেছি", "আসতেছি"][..]);
        m.insert("astesi", &["আসতেছি"][..]);
        m.insert("jatesi", &["যাইতেছি", "যাচ্ছি"][..]);
        m.insert("peranai", &["প্যারা নাই"][..]);
        m.insert("chillbro", &["চিল ব্রো"][..]);
        m.insert("kiobostha", &["কি অবস্থা"][..]);
        m.insert("koris", &["করিস"][..]);
        m.insert("dekhis", &["দেখিস"][..]);
        m.insert("shunis", &["শুনিস"][..]);
        m.insert("bolis", &["বলিস"][..]);
        m.insert("jabis", &["যাবিস"][..]);
        m.insert("khabis", &["খাবিস"][..]);
        m.insert("thakis", &["থাকিস"][..]);
        m.insert("likhis", &["লিখিস"][..]);
        m.insert("parbi", &["পারবি"][..]);
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
        m.insert("sriti", &["স্মৃতি", "সৃতি"][..]);
        m.insert("smriti", &["স্মৃতি", "সৃতি"][..]);
        m.insert("dondho", &["দ্বন্দ্ব", "দন্ধ"][..]);
        m.insert("dwondwo", &["দ্বন্দ্ব", "দন্ধ"][..]);
        m.insert("trishna", &["তৃষ্ণা"][..]);
        m.insert("chotto", &["ছোট্ট"][..]);
        m.insert("onnya", &["অন্য"][..]);
        m.insert("onno", &["অন্য"][..]);
        m.insert("karun", &["কারণ"][..]);
        m.insert("karon", &["কারণ", "কারন"][..]);
        m.insert("khuje", &["খুঁজে"][..]);
        m.insert("shonchoi", &["সঞ্চয়"][..]);
        m.insert("onjo", &["অঞ্জ"][..]);
        m.insert("shongko", &["শঙ্ক"][..]);
        m.insert("songko", &["শঙ্ক"][..]);
        m.insert("hot``hat``", &["হঠাৎ"][..]);
        m.insert("hotat", &["হঠাৎ", "হতাত"][..]);
        m.insert("hothat", &["হঠাৎ", "হতাত"][..]);
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
        m.insert("ja", &["যা", "জা"][..]);
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

        // Conversational single-letter standalone words & currency
        m.insert("r", &["আর", "র"][..]);
        m.insert("o", &["ও", "অ"][..]);
        m.insert("e", &["এ"][..]);
        m.insert("k", &["কে", "ক"][..]);
        m.insert("b", &["বা", "ব"][..]);
        m.insert("tk", &["৳", "টাকা", "তক"][..]);
        m.insert("taka", &["টাকা", "৳"][..]);

        // Khanda-Ta (ৎ) auto-words (resolving desktop Avro t` on mobile)
        m.insert("biddut", &["বিদ্যুৎ"][..]);
        m.insert("bidyut", &["বিদ্যুৎ"][..]);
        m.insert("utshob", &["উৎসব"][..]);
        m.insert("utsob", &["উৎসব"][..]);
        m.insert("utshah", &["উৎসাহ"][..]);
        m.insert("utshaho", &["উৎসাহ"][..]);
        m.insert("hotat", &["হঠাৎ"][..]);
        m.insert("hothat", &["হঠাৎ"][..]);
        m.insert("jotshna", &["জ্যোৎস্না", "জোছনা"][..]);
        m.insert("jotsna", &["জ্যোৎস্না", "জোছনা"][..]);

        // Anusvara (ং) & Bisarga (ঃ) auto-words
        m.insert("shongstha", &["সংস্থা"][..]);
        m.insert("songstha", &["সংস্থা"][..]);
        m.insert("shongram", &["সংগ্রাম"][..]);
        m.insert("songram", &["সংগ্রাম"][..]);
        m.insert("dukkho", &["দুঃখ"][..]);
        m.insert("dukho", &["দুঃখ"][..]);
        m.insert("shothik", &["সঠিক"][..]);
        m.insert("sothik", &["সঠিক"][..]);
        m.insert("bissho", &["বিশ্ব"][..]);
        m.insert("bishsho", &["বিশ্ব"][..]);
        m.insert(
            "ryab",
            &["\u{09B0}\u{200D}\u{09CD}\u{09AF}\u{09BE}\u{09AC}"][..],
        );
        m.insert(
            "rab",
            &["\u{09B0}\u{200D}\u{09CD}\u{09AF}\u{09BE}\u{09AC}", "রব"][..],
        );

        // ── Phase 2: Missing-vowel chat shorthand verb families ─────────────
        // 'kr' family (কর-)
        m.insert("krbo", &["করবো", "করব"][..]);
        m.insert("krchi", &["করছি"][..]);
        m.insert("krcho", &["করছো"][..]);
        m.insert("krche", &["করছে"][..]);
        m.insert("krchen", &["করছেন"][..]);
        m.insert("krchis", &["করছিস"][..]);
        m.insert("krlam", &["করলাম"][..]);
        m.insert("krle", &["করলে"][..]);
        m.insert("krte", &["করতে"][..]);
        m.insert("krbona", &["করবোনা", "করবো না"][..]);

        // 'bl' family (বল-)
        m.insert("blbo", &["বলবো", "বলব"][..]);
        m.insert("blchi", &["বলছি"][..]);
        m.insert("blcho", &["বলছো"][..]);
        m.insert("blche", &["বলছে"][..]);
        m.insert("blchen", &["বলছেন"][..]);
        m.insert("bllam", &["বললাম"][..]);
        m.insert("blle", &["বললে"][..]);
        m.insert("blte", &["বলতে"][..]);

        // 'dkh' family (দেখ-)
        m.insert("dkhbo", &["দেখবো", "দেখব"][..]);
        m.insert("dkhchi", &["দেখছি"][..]);
        m.insert("dkhcho", &["দেখছো"][..]);
        m.insert("dkhche", &["দেখছে"][..]);
        m.insert("dkhlam", &["দেখলাম"][..]);
        m.insert("dkhle", &["দেখলে"][..]);
        m.insert("dkhte", &["দেখতে"][..]);

        // 'shn' family (শুন-)
        m.insert("shnbo", &["শুনবো", "শুনব"][..]);
        m.insert("shnchi", &["শুনছি"][..]);
        m.insert("shncho", &["শুনছো"][..]);
        m.insert("shnche", &["শুনছে"][..]);
        m.insert("shnlam", &["শুনলাম"][..]);
        m.insert("shnte", &["শুনতে"][..]);

        // 'pr' family (পার-)
        m.insert("prbo", &["পারবো", "পারব"][..]);
        m.insert("prchi", &["পারছি"][..]);
        m.insert("prcho", &["পারছো"][..]);
        m.insert("prche", &["পারছে"][..]);
        m.insert("prlam", &["পারলাম"][..]);
        m.insert("prle", &["পারলে"][..]);
        m.insert("prte", &["পারতে"][..]);
        m.insert("prbona", &["পারবোনা"][..]);

        // 'bjh' family (বুঝ-)
        m.insert("bjhsi", &["বুঝেছি"][..]);
        m.insert("bjhlm", &["বুঝলাম"][..]);
        m.insert("bjhso", &["বুঝেছো"][..]);
        m.insert("bjhsen", &["বুঝেছেন"][..]);
        m.insert("bjhte", &["বুঝতে"][..]);
        m.insert("bjhina", &["বুঝিনা"][..]);
        m.insert("bujhlam", &["বুঝলাম"][..]);
        m.insert("bujhina", &["বুঝিনা"][..]);
        m.insert("bujhte", &["বুঝতে"][..]);

        // 'jb' / 'khb' family
        m.insert("jbo", &["যাবো", "যাব"][..]);
        m.insert("jbona", &["যাবোনা"][..]);
        m.insert("khbo", &["খাবো", "খাব"][..]);
        m.insert("khbona", &["খাবোনা"][..]);

        // Conversational SMS contractions
        m.insert("thk", &["ঠিক"][..]);
        m.insert("thkse", &["ঠিক আছে"][..]);
        m.insert("thikase", &["ঠিক আছে"][..]);
        m.insert("thikache", &["ঠিক আছে"][..]);
        m.insert("drkr", &["দরকার"][..]);
        m.insert("kkhn", &["কখন"][..]);
        m.insert("kkhno", &["কখনো"][..]);
        m.insert("ekhno", &["এখনো"][..]);
        m.insert("tkhno", &["তখনো"][..]);
        m.insert("sbar", &["সবার"][..]);
        m.insert("jni", &["জানি"][..]);
        m.insert("jnina", &["জানিনা"][..]);
        m.insert("dhnnbad", &["ধন্যবাদ"][..]);
        m.insert("hye", &["হয়ে"][..]);
        m.insert("hoisilo", &["হয়েছিল"][..]);
        m.insert("accha", &["আচ্ছা"][..]);
        m.insert("acchha", &["আচ্ছা"][..]);
        m.insert("ekdom", &["একদম"][..]);
        m.insert("arekta", &["আরেকটা"][..]);
        m.insert("arekbar", &["আরেকবার"][..]);
        m.insert("arekjon", &["আরেকজন"][..]);

        // ── Phase 4: Suffix & Clitic Attachment Rules (-o 'also', -i 'emphatic') ──
        m.insert("amio", &["আমিও"][..]);
        m.insert("tumio", &["তুমিও"][..]);
        m.insert("apnio", &["আপনিও"][..]);
        m.insert("sheo", &["সেও"][..]);
        m.insert("amrao", &["আমরাও"][..]);
        m.insert("tomrao", &["তোমরাও"][..]);
        m.insert("tarao", &["তারাও"][..]);
        m.insert("orao", &["ওরাও"][..]);
        m.insert("sobaio", &["সবাইও"][..]);
        m.insert("shobaio", &["সবাইও"][..]);
        m.insert("ekhono", &["এখনো"][..]);
        m.insert("tokhono", &["তখনো"][..]);
        m.insert("kokhono", &["কখনো"][..]);
        m.insert("kothao", &["কোথাও"][..]);
        m.insert("amii", &["আমিই"][..]);
        m.insert("tumii", &["তুমিই"][..]);
        m.insert("apnii", &["আপনিই"][..]);
        m.insert("ekhoni", &["এখনই"][..]);
        m.insert("shei", &["সেই"][..]);

        // ── Phase 5: Regional & Sound-Law Equivalence (s/sh, v/bh, z/j) ───────
        m.insert("vabchi", &["ভাবছি"][..]);
        m.insert("vabcho", &["ভাবছো"][..]);
        m.insert("vabche", &["ভাবছে"][..]);
        m.insert("vablam", &["ভাবলাম"][..]);
        m.insert("vabte", &["ভাবতে"][..]);
        m.insert("vabbo", &["ভাববো"][..]);
        m.insert("zao", &["যাও"][..]);
        m.insert("zabe", &["যাবে"][..]);
        m.insert("zodi", &["যদি"][..]);
        m.insert("sombhob", &["সম্ভব"][..]);
        m.insert("osombhob", &["অসম্ভব"][..]);
        m.insert("somossa", &["সমস্যা"][..]);
        m.insert("shomosya", &["সমস্যা"][..]);
        m.insert("shombhob", &["সম্ভব"][..]);
        m.insert("oshombhob", &["অসম্ভব"][..]);

        // ── Everyday Mobile Orthography & Compound Words ──────────────────────
        m.insert("kosto", &["কষ্ট"][..]);
        m.insert("koshto", &["কষ্ট"][..]);
        m.insert("srishti", &["সৃষ্টি"][..]);
        m.insert("shristi", &["সৃষ্টি"][..]);
        m.insert("sristi", &["সৃষ্টি"][..]);
        m.insert("drishti", &["দৃষ্টি"][..]);
        m.insert("dristi", &["দৃষ্টি"][..]);
        m.insert("poriskar", &["পরিষ্কার"][..]);
        m.insert("porishkar", &["পরিষ্কার"][..]);
        m.insert("sustho", &["সুস্থ"][..]);
        m.insert("osustho", &["অসুস্থ"][..]);
        m.insert("shadhinota", &["স্বাধীনতা"][..]);
        m.insert("sadhinota", &["স্বাধীনতা"][..]);
        m.insert("shadhin", &["স্বাধীন"][..]);
        m.insert("sadhin", &["স্বাধীন"][..]);
        m.insert("protishthan", &["প্রতিষ্ঠান"][..]);
        m.insert("protisthan", &["প্রতিষ্ঠান"][..]);
        m.insert("porikkha", &["পরীক্ষা"][..]);
        m.insert("porikha", &["পরীক্ষা"][..]);
        m.insert("shikkha", &["শিক্ষা"][..]);
        m.insert("shikkhar", &["শিক্ষার"][..]);
        m.insert("shikkhok", &["শিক্ষক"][..]);
        m.insert("shanto", &["শান্ত"][..]);
        m.insert("santo", &["শান্ত"][..]);
        m.insert("shanti", &["শান্তি"][..]);
        m.insert("santi", &["শান্তি"][..]);
        m.insert("songshod", &["সংসদ"][..]);
        m.insert("shongshod", &["সংসদ"][..]);
        m.insert("songbad", &["সংবাদ"][..]);
        m.insert("shongbad", &["সংবাদ"][..]);
        m.insert("songskriti", &["সংস্কৃতি"][..]);
        m.insert("shongskriti", &["সংস্কৃতি"][..]);
        m.insert("biggan", &["বিজ্ঞান"][..]);
        m.insert("bigyan", &["বিজ্ঞান"][..]);
        m.insert("sotyi", &["সত্যি"][..]);
        m.insert("sotti", &["সত্যি"][..]);
        m.insert("shotto", &["সত্য"][..]);
        m.insert("sotto", &["সত্য"][..]);
        m.insert("mitthe", &["মিথ্যা"][..]);
        m.insert("mittha", &["মিথ্যা"][..]);
        m.insert("surjo", &["সূর্য"][..]);
        m.insert("shurjo", &["সূর্য"][..]);
        m.insert("purnima", &["পূর্ণিমা"][..]);
        m.insert("biddut", &["বিদ্যুৎ"][..]);
        m.insert("bidyut", &["বিদ্যুৎ"][..]);
        m.insert("ujjwal", &["উজ্জ্বল"][..]);
        m.insert("ovinondon", &["অভিনন্দন"][..]);
        m.insert("obhinondon", &["অভিনন্দন"][..]);
        m.insert("shuvechha", &["শুভেচ্ছা"][..]);
        m.insert("shubhechha", &["শুভেচ্ছা"][..]);
        m.insert("subheccha", &["শুভেচ্ছা"][..]);
        m.insert("shohid", &["শহীদ"][..]);
        m.insert("sohid", &["শহীদ"][..]);
        m.insert("bistar", &["বিস্তার"][..]);
        m.insert("shondha", &["সন্ধ্যা"][..]);
        m.insert("sondha", &["সন্ধ্যা"][..]);
        m.insert("shondhya", &["সন্ধ্যা"][..]);
        m.insert("shubho", &["শুভ"][..]);
        m.insert("subho", &["শুভ"][..]);
        m.insert("druto", &["দ্রুত"][..]);

        // ── Phase 1: High-Frequency Everyday English Loanwords ────────────────
        m.insert("school", &["স্কুল"][..]);
        m.insert("college", &["কলেজ"][..]);
        m.insert("varsity", &["ভার্সিটি"][..]);
        m.insert("university", &["ভার্সিটি", "ইউনিভার্সিটি"][..]);
        m.insert("class", &["ক্লাস"][..]);
        m.insert("result", &["রেজাল্ট"][..]);
        m.insert("exam", &["পরীক্ষা", "এক্সাম"][..]);
        m.insert("routine", &["রুটিন"][..]);
        m.insert("notice", &["নোটিশ"][..]);
        m.insert("table", &["টেবিল"][..]);
        m.insert("chair", &["চেয়ার"][..]);
        m.insert("fan", &["ফ্যান"][..]);
        m.insert("light", &["লাইট"][..]);
        m.insert("pen", &["কলম", "পেন"][..]);
        m.insert("pencil", &["পেন্সিল"][..]);
        m.insert("paper", &["পেপার"][..]);
        m.insert("bottle", &["বোতল"][..]);
        m.insert("glass", &["গ্লাস"][..]);
        m.insert("cup", &["কাপ"][..]);
        m.insert("plate", &["প্লেট"][..]);
        m.insert("box", &["বক্স"][..]);
        m.insert("bag", &["ব্যাগ"][..]);
        m.insert("doctor", &["ডাক্তার"][..]);
        m.insert("hospital", &["হাসপাতাল"][..]);
        m.insert("clinic", &["ক্লিনিক"][..]);
        m.insert("medicine", &["ওষুধ", "মেডিসিন"][..]);
        m.insert("nurse", &["নার্স"][..]);
        m.insert("ambulance", &["অ্যাম্বুলেন্স"][..]);
        m.insert("police", &["পুলিশ"][..]);
        m.insert("bank", &["ব্যাংক"][..]);
        m.insert("cash", &["ক্যাশ"][..]);
        m.insert("card", &["কার্ড"][..]);
        m.insert("balance", &["ব্যালেন্স"][..]);
        m.insert("bill", &["বিল"][..]);
        m.insert("recharge", &["রিচার্জ"][..]);
        m.insert("offer", &["অফার"][..]);
        m.insert("discount", &["ডিসকাউন্ট"][..]);
        m.insert("price", &["দাম", "প্রাইস"][..]);
        m.insert("market", &["মার্কেট"][..]);
        m.insert("shop", &["দোকান", "শপ"][..]);
        m.insert("order", &["অর্ডার"][..]);
        m.insert("delivery", &["ডেলিভারি"][..]);
        m.insert("salary", &["বেতন", "স্যালারি"][..]);
        m.insert("job", &["চাকরি", "জব"][..]);
        m.insert("boss", &["বস"][..]);
        m.insert("sir", &["স্যার"][..]);
        m.insert("madam", &["ম্যাডাম"][..]);
        m.insert("car", &["কার", "গাড়ি"][..]);
        m.insert("bike", &["বাইক"][..]);
        m.insert("station", &["স্টেশন"][..]);
        m.insert("airport", &["এয়ারপোর্ট"][..]);
        m.insert("flight", &["ফ্লাইট"][..]);
        m.insert("hotel", &["হোটেল"][..]);
        m.insert("restaurant", &["রেস্টুরেন্ট"][..]);
        m.insert("tour", &["ট্যুর"][..]);
        m.insert("trip", &["ট্রিপ"][..]);
        m.insert("traffic", &["ট্রাফিক"][..]);
        m.insert("coffee", &["কফি"][..]);
        m.insert("tea", &["চা"][..]);
        m.insert("juice", &["জুস"][..]);
        m.insert("cake", &["কেক"][..]);
        m.insert("chocolate", &["চকলেট"][..]);
        m.insert("biscuit", &["বিস্কুট"][..]);
        m.insert("burger", &["বার্গার"][..]);
        m.insert("pizza", &["পিৎজা", "পিজ্জা"][..]);
        m.insert("sandwich", &["স্যান্ডউইচ"][..]);
        m.insert("breakfast", &["ব্রেকফাস্ট"][..]);
        m.insert("lunch", &["লাঞ্চ"][..]);
        m.insert("dinner", &["ডিনার"][..]);
        m.insert("party", &["পার্টি"][..]);
        m.insert("picnic", &["পিকনিক"][..]);
        m.insert("movie", &["মুভি", "সিনেমা"][..]);
        m.insert("cinema", &["সিনেমা", "মুভি"][..]);
        m.insert("drama", &["নাটক", "ড্রামা"][..]);
        m.insert("cricket", &["ক্রিকেট"][..]);
        m.insert("football", &["ফুটবল"][..]);
        m.insert("sorry", &["সরি"][..]);
        m.insert("thanks", &["ধন্যবাদ", "থ্যাংকস"][..]);
        m.insert("thank", &["ধন্যবাদ", "থ্যাংকস"][..]);
        m.insert("welcome", &["ওয়েলকাম"][..]);
        m.insert("ok", &["ওকে"][..]);
        m.insert("okay", &["ওকে"][..]);
        m.insert("yes", &["হ্যাঁ", "ইয়েস"][..]);
        m.insert("no", &["না", "নো"][..]);
        m.insert("ready", &["রেডি"][..]);
        m.insert("busy", &["ব্যস্ত", "বিজি"][..]);
        m.insert("simple", &["সহজ", "সিম্পল"][..]);
        m.insert("smart", &["স্মার্ট"][..]);
        m.insert("cute", &["কিউট"][..]);
        m.insert("sweet", &["সুইট"][..]);
        m.insert("cool", &["কুল"][..]);
        m.insert("nice", &["নিচে", "নাইস"][..]);
        m.insert("tension", &["টেনশন"][..]);
        m.insert("relax", &["রিল্যাক্স"][..]);
        m.insert("happy", &["খুশি", "হ্যাপি"][..]);
        m.insert("sad", &["দুঃখী", "স্যাড"][..]);
        m.insert("love", &["ভালোবাসা", "লাভ"][..]);
        m.insert("care", &["কেয়ার"][..]);
        m.insert("miss", &["মিস"][..]);
        m.insert("gift", &["উপহার", "গিফট"][..]);
        m.insert("surprise", &["সারপ্রাইজ"][..]);
        m.insert("date", &["তারিখ", "ডেট"][..]);
        m.insert("family", &["পরিবার", "ফ্যামিলি"][..]);
        m.insert("friend", &["বন্ধু", "ফ্রেন্ড"][..]);
        m.insert("baby", &["বেবি", "শিশু"][..]);
        m.insert("email", &["ইমেইল"][..]);
        m.insert("mail", &["মেইল", "ইমেইল"][..]);
        m.insert("facebook", &["ফেসবুক"][..]);
        m.insert("fb", &["ফেসবুক"][..]);
        m.insert("youtube", &["ইউটিউব"][..]);
        m.insert("yt", &["ইউটিউব"][..]);
        m.insert("google", &["গুগল"][..]);
        m.insert("whatsapp", &["হোয়াটসঅ্যাপ"][..]);
        m.insert("wa", &["হোয়াটসঅ্যাপ"][..]);
        m.insert("page", &["পেজ"][..]);
        m.insert("like", &["লাইক"][..]);
        m.insert("inbox", &["ইনবক্স"][..]);
        m.insert("status", &["স্ট্যাটাস"][..]);
        m.insert("story", &["স্টোরি"][..]);
        m.insert("reels", &["রিলস"][..]);
        m.insert("block", &["ব্লক"][..]);
        m.insert("unblock", &["আনব্লক"][..]);
        m.insert("battery", &["ব্যাটারি"][..]);
        m.insert("charger", &["চার্জার"][..]);
        m.insert("charge", &["চার্জ"][..]);
        m.insert("sim", &["সিম"][..]);
        m.insert("wifi", &["ওয়াইফাই"][..]);
        m
    })
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

    let is_explicit_common =
        get_common_words().contains_key(input) || get_common_words().contains_key(lower.as_str());

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
        if let Some(&words) = get_common_words()
            .get(input)
            .or_else(|| get_common_words().get(lower.as_str()))
        {
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
        sugg.suggest_with_multi_context(input, context, false, true, &candidate_memory)
    };

    if candidates.is_empty() {
        let parser = get_avro_parser();
        let def = parser.convert(input);
        candidates.push(def);
    }

    // Bilingual loanword surfacing: if the input is recognized as a valid English word
    // (e.g. "meeting", "office", "project", "class", "email", "ok", "hi"), surface the verbatim English word
    if crate::english::is_recognized_english_word(input)
        && !candidates.iter().any(|c| c.eq_ignore_ascii_case(input))
    {
        let insert_pos = candidates.len().min(1);
        candidates.insert(insert_pos, input.to_string());
    }

    // Capitalized / CamelCase / Code Token / Mixed Symbol / Alphanumeric Latin Escape Hatch (e.g. "Siam", "Figma", "ChatGPT", "myVar", "covid19", "350tk")
    let is_code_or_mixed = input.chars().skip(1).any(|c| c.is_ascii_uppercase())
        || input.contains('_')
        || input.contains('-')
        || input.contains('.')
        || (input.chars().any(|c| c.is_ascii_digit()) && input.chars().any(|c| c.is_alphabetic()));
    if is_code_or_mixed && input.is_ascii() && !candidates.iter().any(|c| c == input) {
        let insert_pos = candidates.len().min(1);
        candidates.insert(insert_pos, input.to_string());
    }

    // Digits / Currency Symbols dual candidate surfacing (e.g. "1234" -> ["১২৩৪", "1234"], "$100" -> ["৳১০০", "$100"])
    let is_numeric_or_currency =
        input.chars().all(|c| c.is_ascii_digit()) || input.starts_with('$');
    if is_numeric_or_currency && !candidates.iter().any(|c| c == input) {
        let insert_pos = candidates.len().min(1);
        candidates.insert(insert_pos, input.to_string());
    }

    let mut selected_idx = selected_idx;

    // Apply deferred bundled system autocorrect if active
    if let Some(ref replacement) = autocorrect_replacement {
        if let Some(pos) = candidates.iter().position(|c| c == replacement) {
            candidates.remove(pos);
        }
        candidates.insert(0, replacement.clone());
        selected_idx = 0;
    }

    // 2. Ensure core common words are prioritized at the top of candidate list
    let is_explicit_common =
        get_common_words().contains_key(input) || get_common_words().contains_key(lower.as_str());
    if let Some(&words) = get_common_words()
        .get(input)
        .or_else(|| get_common_words().get(lower.as_str()))
    {
        for (i, &w) in words.iter().enumerate() {
            let ws = w.to_string();
            if let Some(pos) = candidates.iter().position(|c| c == &ws) {
                candidates.remove(pos);
            }
            candidates.insert(i.min(candidates.len()), ws);
        }
        selected_idx = 0;
    } else if let Some(ref collapsed) = collapse_elongation(&lower) {
        let words: Option<&[&str]> = match collapsed.as_str() {
            "ha" => Some(&["হ্যাঁ", "হা"][..]),
            "na" => Some(&["না"][..]),
            _ => get_common_words().get(collapsed.as_str()).copied(),
        };

        if let Some(words) = words {
            for (i, &w) in words.iter().enumerate() {
                let ws = w.to_string();
                if let Some(pos) = candidates.iter().position(|c| c == &ws) {
                    candidates.remove(pos);
                }
                candidates.insert(i.min(candidates.len()), ws);
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
    // prioritize the matching 'ো'-inflected form from candidates (unless explicitly ordered in COMMON_WORDS)
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
        // All-Caps input (e.g. "AMI", "TUMI", "DESH"):
        // Normalizing to lowercase avoids mangling retroflexes and dirgho-i/u.
        let resolved_lower = if let Some(ac) = db.autocorrect.get(&lower) {
            ac.clone()
        } else if let Some(&words) = get_common_words().get(&lower.as_str()) {
            words.first().copied().unwrap_or("").to_string()
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
    } else if has_explicit_avro_case && !def.is_empty() {
        let is_titlecase = input.chars().next().is_some_and(|c| c.is_ascii_uppercase())
            && input.chars().skip(1).all(|c| c.is_ascii_lowercase());

        let resolved_lower = if let Some(ac) = db.autocorrect.get(&lower) {
            ac.clone()
        } else if let Some(&words) = get_common_words().get(&lower.as_str()) {
            words.first().copied().unwrap_or("").to_string()
        } else {
            parser.convert(&lower)
        };

        if is_titlecase {
            // For sentence-initial titlecase (e.g. "Din" -> দিন vs ডিন, "Tara" -> তারা vs টারা):
            // Check if the lowercase variant is a common word or much higher frequency.
            let is_common_lower = get_common_words().contains_key(&lower.as_str());
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
    let mut common_typos = Vec::new();
    let mut dict_typos = Vec::new();
    let is_exact_match = is_explicit_common
        || (db.is_exact_dictionary_word(&def) && db.get_frequency(&def) >= 1000);
    if !is_exact_match && lower.len() >= 3 && lower.len() <= 8 {
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
        if common_typos.is_empty() && chars.len() >= 3 && chars.len() <= 8 {
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
    append_prefix_matches(&primary, &mut candidates);

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
}
