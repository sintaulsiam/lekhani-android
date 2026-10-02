//! Large-scale letter-by-letter typing simulation and deep anomaly analysis.
//!
//! Evaluates:
//! 1. Keystroke-by-keystroke in-flight preedit progression
//! 2. Candidate list quality, deduplication, and ranking at each key
//! 3. Spacebar commit fidelity (vowels, inflections, modifiers)
//! 4. 500+ words from autocorrect dataset + curated edge cases

use hashbrown::HashMap;
use lekhani_android::{AndroidLekhaniSession, LekhaniLayoutType};

#[derive(Debug, Clone)]
#[allow(dead_code)]
struct TestResult {
    input: String,
    expected: String,
    committed: String,
    top_candidates: Vec<String>,
    is_exact: bool,
    in_top_3: bool,
    glitches: Vec<String>,
    steps: Vec<(char, String, Vec<String>)>,
}

#[test]
fn test_large_scale_typing_simulation() {
    println!("\n================================================================================");
    println!("     LEKHANI ANDROID: LARGE-SCALE LETTER-BY-LETTER TYPING STRESS SIMULATION      ");
    println!("================================================================================\n");

    let mut all_results: Vec<TestResult> = Vec::new();

    // ─── 1. Load Autocorrect Dataset ─────────────────────────────────────────
    let manifest_dir = env!("CARGO_MANIFEST_DIR");
    let dict_path = format!("{}/../../data/dictionaries/autocorrect.json", manifest_dir);
    let mut autocorrect_data: HashMap<String, String> = HashMap::new();

    if let Ok(content) = std::fs::read_to_string(&dict_path) {
        if let Ok(parsed) = serde_json::from_str::<HashMap<String, String>>(&content) {
            autocorrect_data = parsed;
        }
    }
    println!("Loaded {} entries from autocorrect.json", autocorrect_data.len());

    // ─── 2. Curated Phonological & Conversational Test Set ───────────────────
    let curated_tests: Vec<(&str, &str)> = vec![
        // Pronouns & Chat Opener
        ("ami", "আমি"),
        ("amar", "আমার"),
        ("amake", "আমাকে"),
        ("amader", "আমাদের"),
        ("tumi", "তুমি"),
        ("tomar", "তোমার"),
        ("tomake", "তোমাকে"),
        ("apni", "আপনি"),
        ("apnar", "আপনার"),
        ("apnake", "আপনাকে"),
        ("she", "সে"),
        ("tar", "তার"),
        ("take", "তাকে"),
        ("tara", "তারা"),
        ("tader", "তাদের"),
        ("shobai", "সবাই"),
        ("sobai", "সবাই"),
        ("kemon", "কেমন"),
        ("achen", "আছেন"),
        ("acho", "আছো"),
        ("achi", "আছি"),
        ("ache", "আছে"),
        ("kothay", "কোথায়"),
        ("ki", "কি"),
        ("kee", "কী"),
        ("keno", "কেন"),
        ("kn", "কেন"),
        ("kokhon", "কখন"),
        ("kibhabe", "কিভাবে"),
        ("koto", "কত"),

        // Common Conversational Verbs (Present, Past, Future, Continuous)
        ("khabo", "খাবো"),
        ("khacchi", "খাচ্ছি"),
        ("kheyechi", "খেয়েছি"),
        ("kheyechilam", "খেয়েছিলাম"),
        ("khacche", "খাচ্ছে"),
        ("khao", "খাও"),
        ("khaan", "খান"),
        ("jabo", "যাবো"),
        ("jacchi", "যাচ্ছি"),
        ("gechi", "গেছি"),
        ("gechilam", "গিয়েছিলাম"),
        ("jacche", "যাচ্ছে"),
        ("jan", "যান"),
        ("jao", "যাও"),
        ("korbo", "করবো"),
        ("korcho", "করছো"),
        ("korchi", "করছি"),
        ("korechi", "করেছি"),
        ("korche", "করছে"),
        ("koren", "করেন"),
        ("koro", "করো"),
        ("kori", "করি"),
        ("bolbo", "বলবো"),
        ("bolcho", "বলছো"),
        ("bolchi", "বলছি"),
        ("bolechi", "বলেছি"),
        ("bolche", "বলছে"),
        ("bollam", "বললাম"),
        ("bolun", "বলুন"),
        ("bolo", "বলো"),
        ("boli", "বলি"),
        ("dekhbo", "দেখবো"),
        ("dekhcho", "দেখছো"),
        ("dekhchi", "দেখছি"),
        ("dekhechi", "দেখেছি"),
        ("dekhche", "দেখছে"),
        ("dekhun", "দেখুন"),
        ("dekho", "দেখো"),
        ("dekhi", "দেখি"),
        ("shunbo", "শুনবো"),
        ("shuncho", "শুনছো"),
        ("shunchi", "শুনছি"),
        ("shunechi", "শুনেছি"),
        ("shunche", "শুনছে"),
        ("shunun", "শুনুন"),
        ("shuno", "শুনো"),
        ("shuni", "শুনি"),
        ("parbo", "পারবো"),
        ("parcho", "পারছো"),
        ("parchi", "পারছি"),
        ("parche", "পারছে"),
        ("parben", "পারবেন"),
        ("paro", "পারো"),
        ("pari", "পারি"),
        ("janbo", "জানবো"),
        ("jancho", "জানছো"),
        ("janchi", "জানছি"),
        ("jenechi", "জেনেছি"),
        ("janen", "জানেন"),
        ("jano", "জানো"),
        ("jani", "জানি"),
        ("likhbo", "লিখবো"),
        ("likhcho", "লিখছো"),
        ("likhchi", "লিখছি"),
        ("likhechi", "লিখেছি"),
        ("likhun", "লিখুন"),
        ("likho", "লিখো"),
        ("likhi", "লিখি"),
        ("porbo", "পড়বো"),
        ("porcho", "পড়ছো"),
        ("porchi", "পড়ছি"),
        ("porechi", "পড়েছি"),
        ("pore", "পরে"),
        ("ashbo", "আসবো"),
        ("ashcho", "আসছো"),
        ("ashchi", "আসছি"),
        ("eshechi", "এসেছি"),
        ("ashche", "আসছে"),
        ("ashun", "আসুন"),
        ("asho", "আসো"),
        ("ashi", "আসি"),
        ("thakbo", "থাকবো"),
        ("thakcho", "থাকছো"),
        ("thakchi", "থাকছি"),
        ("thekechi", "থেকেছি"),
        ("thakche", "থাকছে"),
        ("thaken", "থাকেন"),
        ("thako", "থাকো"),
        ("thaki", "থাকি"),
        ("hobe", "হবে"),
        ("hocche", "হচ্ছে"),
        ("hoyeche", "হয়েছে"),
        ("hoy", "হয়"),
        ("hoye", "হয়ে"),

        // Complex Juktoborno (Conjuncts)
        ("kkh", "ক্ষ"),
        ("shikkha", "শিক্ষা"),
        ("shikkhok", "শিক্ষক"),
        ("parikkha", "পরীক্ষা"),
        ("porikkha", "পরীক্ষা"),
        ("khoma", "ক্ষমা"),
        ("kkhoma", "ক্ষমা"),
        ("khoti", "ক্ষতি"),
        ("okkhi", "অক্ষি"),
        ("lokkhi", "লক্ষ্মী"),
        ("bijnan", "বিজ্ঞান"),
        ("aggan", "অজ্ঞান"),
        ("onno", "অন্য"),
        ("onnya", "অন্য"),
        ("dhonno", "ধন্য"),
        ("banya", "বন্যা"),
        ("bonna", "বন্যা"),
        ("kanya", "কন্যা"),
        ("konna", "কন্যা"),
        ("shotto", "সত্য"),
        ("sotyo", "সত্য"),
        ("mitthe", "মিথ্যে"),
        ("mrtto", "মর্ত্য"),
        ("mrittu", "মৃত্যু"),
        ("mrittyu", "মৃত্যু"),
        ("shadhin", "স্বাধীন"),
        ("dwitiyo", "দ্বিতীয়"),
        ("bissho", "বিশ্ব"),
        ("bisshash", "বিশ্বাস"),
        ("shorgo", "স্বর্গ"),
        ("dharmo", "ধর্ম"),
        ("kormo", "কর্ম"),
        ("barna", "বর্ণ"),
        ("borno", "বর্ণ"),
        ("purno", "পূর্ণ"),
        ("durga", "দুর্গা"),
        ("shurjo", "সূর্য"),
        ("shuryo", "সূর্য"),
        ("prothom", "প্রথম"),
        ("prodhan", "প্রধান"),
        ("prostab", "প্রস্তাব"),
        ("chhatro", "ছাত্র"),
        ("chhatri", "ছাত্রী"),
        ("mitro", "মিত্র"),
        ("shotru", "শত্রু"),
        ("britto", "বৃত্ত"),
        ("trishna", "তৃষ্ণা"),
        ("kripa", "কৃপা"),
        ("krripa", "কৃপা"),
        ("britti", "বৃত্তি"),
        ("brritti", "বৃত্তি"),
        ("srishti", "সৃষ্টি"),
        ("drishti", "দৃষ্টি"),
        ("ongko", "অঙ্ক"),
        ("shongko", "শঙ্ক"),
        ("songko", "শঙ্ক"),
        ("bongo", "বঙ্গ"),
        ("shonchoi", "সঞ্চয়"),
        ("onjo", "অঞ্জ"),
        ("ghonta", "ঘণ্টা"),
        ("ghonTa", "ঘণ্টা"),
        ("kando", "কাণ্ড"),
        ("shanto", "শান্ত"),
        ("sundor", "সুন্দর"),
        ("shundor", "সুন্দর"),
        ("mondir", "মন্দির"),
        ("bondhu", "বন্ধু"),
        ("shomporko", "সম্পর্ক"),
        ("somporko", "সম্পর্ক"),
        ("shompotti", "সম্পত্তি"),
        ("lomba", "লম্বা"),
        ("shobdo", "শব্দ"),
        ("shubho", "শুভ"),
        ("ashchorjo", "আশ্চর্য"),
        ("shrestho", "শ্রেষ্ঠ"),
        ("shreshTho", "শ্রেষ্ঠ"),
        ("koshto", "কষ্ট"),
        ("koshTo", "কষ্ট"),
        ("noshto", "নষ্ট"),
        ("sposhto", "স্পষ্ট"),
        ("shoshto", "ষষ্ঠ"),
        ("chotto", "ছোট্ট"),
        ("dondho", "দ্বন্দ্ব"),
        ("dwondwo", "দ্বন্দ্ব"),
        ("shongjog", "সংযোগ"),
        ("songjog", "সংযোগ"),
        ("shongbad", "সংবাদ"),
        ("songbad", "সংবাদ"),
        ("shongshod", "সংসদ"),
        ("songshod", "সংসদ"),
        ("rong", "রং"),
        ("shonkha", "সংখ্যা"),
        ("shongkha", "সংখ্যা"),
        ("songkha", "সংখ্যা"),

        // Special Modifiers
        ("c^ad", "চাঁদ"),
        ("b^ash", "বাঁশ"),
        ("p^ac", "পাঁচ"),
        ("h^ashi", "হাঁসি"),
        ("k^ada", "কাঁদা"),
        ("ut``shob", "উৎসব"),
        ("utshob", "উৎসব"),
        ("hot``hat``", "হঠাৎ"),
        ("hothat", "হঠাৎ"),
        ("biddut", "বিদ্যুৎ"),
        ("du:kho", "দুঃখ"),
        ("bipod:jonok", "বিপদঃজনক"),

        // English Loanwords in Bengali
        ("school", "স্কুল"),
        ("college", "কলেজ"),
        ("hospital", "হাসপাতাল"),
        ("doctor", "ডাক্তার"),
        ("daktar", "ডাক্তার"),
        ("mobile", "মোবাইল"),
        ("phone", "ফোন"),
        ("internet", "ইন্টারনেট"),
        ("computer", "কম্পিউটার"),
        ("password", "পাসওয়ার্ড"),
        ("ticket", "টিকেট"),
        ("train", "ট্রেন"),
        ("bus", "বাস"),
        ("table", "টেবিল"),
        ("chair", "চেয়ার"),
        ("copy", "কপি"),
        ("shirt", "শার্ট"),
        ("glass", "গ্লাস"),
        ("police", "পুলিশ"),
        ("bank", "ব্যাংক"),
        ("message", "মেসেজ"),
        ("video", "ভিডিও"),
        ("camera", "ক্যামেরা"),
        ("driver", "ড্রাইভার"),
        ("office", "অফিস"),
        ("post", "পোস্ট"),

        // Chat Contractions & Slang
        ("kmn", "কেমন"),
        ("kemn", "কেমন"),
        ("aso", "আছো"),
        ("asen", "আছেন"),
        ("asi", "আছি"),
        ("kisu", "কিছু"),
        ("khbr", "খবর"),
        ("thnx", "ধন্যবাদ"),
        ("thx", "ধন্যবাদ"),
        ("pls", "প্লিজ"),
        ("plz", "প্লিজ"),
        ("apnr", "আপনার"),
        ("tmr", "তোমার"),
        ("amr", "আমার"),
        ("amdr", "আমাদের"),
        ("tmdr", "তোমাদের"),
        ("sbai", "সবাই"),
        ("shb", "সব"),
        ("ekhn", "এখন"),
        ("tkhn", "তখন"),
        ("hbe", "হবে"),
        ("hoise", "হয়েছে"),
        ("krbo", "করবো"),
        ("krben", "করবেন"),
        ("krso", "করছো"),
        ("krse", "করছে"),
        ("bujhsi", "বুঝেছি"),
        ("thik", "ঠিক"),
        ("shotti", "সত্যি"),
        ("sotti", "সত্যি"),
    ];

    // Combine datasets
    let mut test_pairs: Vec<(String, String)> = Vec::new();
    let mut seen_inputs = hashbrown::HashSet::new();

    for (inp, exp) in curated_tests {
        if seen_inputs.insert(inp.to_string()) {
            test_pairs.push((inp.to_string(), exp.to_string()));
        }
    }

    // Add clean words from autocorrect dataset
    for (inp, exp) in autocorrect_data.iter() {
        if inp.chars().all(|c| c.is_ascii_alphabetic()) && inp.len() >= 2 && inp.len() <= 12
            && seen_inputs.insert(inp.clone()) {
            test_pairs.push((inp.clone(), exp.clone()));
        }
    }

    println!("Total distinct test words to simulate letter-by-letter: {}\n", test_pairs.len());

    // ─── 3. Run Simulation on Every Word ─────────────────────────────────────
    let mut passed_exact = 0;
    let mut passed_top3 = 0;
    let mut total_glitches = 0;
    let mut anomaly_categories: HashMap<&'static str, Vec<TestResult>> = HashMap::new();

    for (inp, exp) in &test_pairs {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);
        let _ = session.load_user_autocorrect(dict_path.clone());

        let mut step_history = Vec::new();
        let mut glitches = Vec::new();

        for ch in inp.chars() {
            let res = session.process_key(ch.to_string()).expect("process_key panic");
            let preedit = res.preedit.clone();

            // Glitch 1: Buffer unexpectedly wiped or became empty mid-word
            if preedit.is_empty() {
                glitches.push(format!("Preedit emptied on char '{}'", ch));
            }

            // Glitch 3: Candidates list empty
            if res.candidates.is_empty() {
                glitches.push(format!("Empty candidates list on char '{}'", ch));
            }

            // Glitch 4: Candidates contain duplicates
            let mut cand_set = hashbrown::HashSet::new();
            for c in &res.candidates {
                if !cand_set.insert(c.clone()) {
                    glitches.push(format!("Duplicate candidate '{}' on char '{}'", c, ch));
                    break;
                }
            }

            step_history.push((ch, preedit.clone(), res.candidates.clone()));
        }

        // Commit via space
        let space_res = session.handle_space().expect("handle_space panic");
        let committed = space_res.commit_text.unwrap_or_default().trim().to_string();

        let last_candidates = step_history.last().map(|s| s.2.clone()).unwrap_or_default();
        let is_exact = committed == *exp;
        let in_top_3 = is_exact || last_candidates.iter().take(3).any(|c| c == exp);

        if is_exact {
            passed_exact += 1;
        }
        if in_top_3 {
            passed_top3 += 1;
        }
        if !glitches.is_empty() {
            total_glitches += glitches.len();
        }

        let res = TestResult {
            input: inp.clone(),
            expected: exp.clone(),
            committed: committed.clone(),
            top_candidates: last_candidates.iter().take(5).cloned().collect(),
            is_exact,
            in_top_3,
            glitches,
            steps: step_history,
        };

        if !is_exact {
            // Categorize failure
            let cat = if res.committed.is_empty() {
                "Empty Commit"
            } else if res.committed.ends_with('্') {
                "Trailing Hasanta"
            } else if res.in_top_3 {
                "Sub-optimal Top Rank (In Top 3)"
            } else if res.top_candidates.iter().any(|c| c == exp) {
                "Buried in Candidates (>3)"
            } else {
                "Missing from Candidates"
            };
            anomaly_categories.entry(cat).or_default().push(res.clone());
        }

        all_results.push(res);
    }

    // ─── 4. Report Detailed Analysis ─────────────────────────────────────────
    println!("--------------------------------------------------------------------------------");
    println!("                           SIMULATION RESULTS SUMMARY                           ");
    println!("--------------------------------------------------------------------------------");
    let total = test_pairs.len();
    println!("Total Words Tested:           {}", total);
    println!("Exact Matches (#1 Commit):    {} ({:.1}%)", passed_exact, (passed_exact as f64 / total as f64) * 100.0);
    println!("Top-3 Recall (Target In Top 3):{} ({:.1}%)", passed_top3, (passed_top3 as f64 / total as f64) * 100.0);
    println!("Mid-typing Glitches Detected: {}", total_glitches);
    println!("--------------------------------------------------------------------------------\n");

    println!("--------------------------------------------------------------------------------");
    println!("                           FAILURE BREAKDOWN BY CATEGORY                        ");
    println!("--------------------------------------------------------------------------------");
    for (cat, list) in &anomaly_categories {
        println!("\n▶ Category: {} (count: {})", cat, list.len());
        for item in list.iter() {
            println!("   Input:    '{:15}' -> Expected: '{:12}' | Committed: '{:12}'", item.input, item.expected, item.committed);
            println!("   Top Cands: {:?}", item.top_candidates);
            if !item.glitches.is_empty() {
                println!("   Glitches:  {:?}", item.glitches);
            }
        }
    }
    println!("\n================================================================================\n");
}
