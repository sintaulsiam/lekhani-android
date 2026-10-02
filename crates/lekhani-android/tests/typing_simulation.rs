use lekhani_android::{AndroidLekhaniSession, LekhaniLayoutType};

struct TestCase {
    category: &'static str,
    input: &'static str,
    expected_top: &'static str,
    acceptable: &'static [&'static str],
}

#[test]
fn run_comprehensive_typing_simulation() {
    let test_cases = vec![
        // ── 1. Daily Simple Words ──────────────────────────────────────
        TestCase { category: "Daily", input: "ami", expected_top: "আমি", acceptable: &["আমি"] },
        TestCase { category: "Daily", input: "amar", expected_top: "আমার", acceptable: &["আমার"] },
        TestCase { category: "Daily", input: "amake", expected_top: "আমাকে", acceptable: &["আমাকে"] },
        TestCase { category: "Daily", input: "amader", expected_top: "আমাদের", acceptable: &["আমাদের"] },
        TestCase { category: "Daily", input: "tumi", expected_top: "তুমি", acceptable: &["তুমি"] },
        TestCase { category: "Daily", input: "tomar", expected_top: "তোমার", acceptable: &["তোমার"] },
        TestCase { category: "Daily", input: "apni", expected_top: "আপনি", acceptable: &["আপনি"] },
        TestCase { category: "Daily", input: "apnar", expected_top: "আপনার", acceptable: &["আপনার"] },
        TestCase { category: "Daily", input: "bhalo", expected_top: "ভালো", acceptable: &["ভালো", "ভাল"] },
        TestCase { category: "Daily", input: "valo", expected_top: "ভালো", acceptable: &["ভালো", "ভালো"] },
        TestCase { category: "Daily", input: "achi", expected_top: "আছি", acceptable: &["আছি"] },
        TestCase { category: "Daily", input: "achhi", expected_top: "আছি", acceptable: &["আছি"] },
        TestCase { category: "Daily", input: "kemon", expected_top: "কেমন", acceptable: &["কেমন"] },
        TestCase { category: "Daily", input: "acho", expected_top: "আছো", acceptable: &["আছো"] },
        TestCase { category: "Daily", input: "achen", expected_top: "আছেন", acceptable: &["আছেন"] },
        TestCase { category: "Daily", input: "ki", expected_top: "কি", acceptable: &["কি", "কী"] },
        TestCase { category: "Daily", input: "kee", expected_top: "কী", acceptable: &["কী", "কি"] },
        TestCase { category: "Daily", input: "koro", expected_top: "করো", acceptable: &["করো", "কর"] },
        TestCase { category: "Daily", input: "korcho", expected_top: "করছো", acceptable: &["করছো"] },
        TestCase { category: "Daily", input: "kothay", expected_top: "কোথায়", acceptable: &["কোথায়"] },
        TestCase { category: "Daily", input: "jabe", expected_top: "যাবে", acceptable: &["যাবে"] },
        TestCase { category: "Daily", input: "dhonnobad", expected_top: "ধন্যবাদ", acceptable: &["ধন্যবাদ"] },
        TestCase { category: "Daily", input: "shomoy", expected_top: "সময়", acceptable: &["সময়"] },
        TestCase { category: "Daily", input: "somoy", expected_top: "সময়", acceptable: &["সময়"] },
        TestCase { category: "Daily", input: "kaj", expected_top: "কাজ", acceptable: &["কাজ"] },
        TestCase { category: "Daily", input: "bhat", expected_top: "ভাত", acceptable: &["ভাত"] },
        TestCase { category: "Daily", input: "bhaat", expected_top: "ভাত", acceptable: &["ভাত"] },
        TestCase { category: "Daily", input: "khabo", expected_top: "খাবো", acceptable: &["খাবো", "খাব"] },
        TestCase { category: "Daily", input: "pani", expected_top: "পানি", acceptable: &["পানি"] },
        TestCase { category: "Daily", input: "khub", expected_top: "খুব", acceptable: &["খুব"] },
        TestCase { category: "Daily", input: "shundor", expected_top: "সুন্দর", acceptable: &["সুন্দর"] },
        TestCase { category: "Daily", input: "sundor", expected_top: "সুন্দর", acceptable: &["সুন্দর"] },
        TestCase { category: "Daily", input: "shokal", expected_top: "সকাল", acceptable: &["সকাল"] },
        TestCase { category: "Daily", input: "sokal", expected_top: "সকাল", acceptable: &["সকাল"] },
        TestCase { category: "Daily", input: "rat", expected_top: "রাত", acceptable: &["রাত"] },
        TestCase { category: "Daily", input: "ekhon", expected_top: "এখন", acceptable: &["এখন"] },
        TestCase { category: "Daily", input: "kalke", expected_top: "কালকে", acceptable: &["কালকে"] },
        TestCase { category: "Daily", input: "dekha", expected_top: "দেখা", acceptable: &["দেখা"] },
        TestCase { category: "Daily", input: "hobe", expected_top: "হবে", acceptable: &["হবে"] },
        TestCase { category: "Daily", input: "bondhu", expected_top: "বন্ধু", acceptable: &["বন্ধু"] },
        TestCase { category: "Daily", input: "desh", expected_top: "দেশ", acceptable: &["দেশ"] },
        TestCase { category: "Daily", input: "shonar", expected_top: "সোনার", acceptable: &["সোনার", "শোনার"] },
        TestCase { category: "Daily", input: "sonar", expected_top: "সোনার", acceptable: &["সোনার"] },
        TestCase { category: "Daily", input: "manush", expected_top: "মানুষ", acceptable: &["মানুষ"] },
        TestCase { category: "Daily", input: "jibon", expected_top: "জীবন", acceptable: &["জীবন"] },
        TestCase { category: "Daily", input: "ghor", expected_top: "ঘর", acceptable: &["ঘর"] },
        TestCase { category: "Daily", input: "bari", expected_top: "বাড়ি", acceptable: &["বাড়ি", "বারি"] },
        TestCase { category: "Daily", input: "baRi", expected_top: "বাড়ি", acceptable: &["বাড়ি"] },
        TestCase { category: "Daily", input: "gari", expected_top: "গাড়ি", acceptable: &["গাড়ি", "গারি"] },
        TestCase { category: "Daily", input: "gaRi", expected_top: "গাড়ি", acceptable: &["গাড়ি"] },

        // ── 2. Complex Words & Conjuncts (Juktoborno) ─────────────────
        TestCase { category: "Complex Jukto", input: "shikkha", expected_top: "শিক্ষা", acceptable: &["শিক্ষা"] },
        TestCase { category: "Complex Jukto", input: "shikkhok", expected_top: "শিক্ষক", acceptable: &["শিক্ষক"] },
        TestCase { category: "Complex Jukto", input: "bijnan", expected_top: "বিজ্ঞান", acceptable: &["বিজ্ঞান"] },
        TestCase { category: "Complex Jukto", input: "biggan", expected_top: "বিজ্ঞান", acceptable: &["বিজ্ঞান"] },
        TestCase { category: "Complex Jukto", input: "ucchash", expected_top: "উচ্ছ্বাস", acceptable: &["উচ্ছ্বাস"] },
        TestCase { category: "Complex Jukto", input: "shasthyo", expected_top: "স্বাস্থ্য", acceptable: &["স্বাস্থ্য"] },
        TestCase { category: "Complex Jukto", input: "shondhya", expected_top: "সন্ধ্যা", acceptable: &["সন্ধ্যা"] },
        TestCase { category: "Complex Jukto", input: "shondha", expected_top: "সন্ধ্যা", acceptable: &["সন্ধ্যা"] },
        TestCase { category: "Complex Jukto", input: "antorjatik", expected_top: "আন্তর্জাতিক", acceptable: &["আন্তর্জাতিক"] },
        TestCase { category: "Complex Jukto", input: "proshno", expected_top: "প্রশ্ন", acceptable: &["প্রশ্ন"] },
        TestCase { category: "Complex Jukto", input: "brikkho", expected_top: "বৃক্ষ", acceptable: &["বৃক্ষ"] },
        TestCase { category: "Complex Jukto", input: "krittim", expected_top: "কৃত্রিম", acceptable: &["কৃত্রিম"] },
        TestCase { category: "Complex Jukto", input: "dharabahik", expected_top: "ধারাবাহিক", acceptable: &["ধারাবাহিক"] },
        TestCase { category: "Complex Jukto", input: "poribesh", expected_top: "পরিবেশ", acceptable: &["পরিবেশ"] },
        TestCase { category: "Complex Jukto", input: "somporko", expected_top: "সম্পর্ক", acceptable: &["সম্পর্ক"] },
        TestCase { category: "Complex Jukto", input: "porikkha", expected_top: "পরীক্ষা", acceptable: &["পরীক্ষা"] },
        TestCase { category: "Complex Jukto", input: "sriti", expected_top: "স্মৃতি", acceptable: &["স্মৃতি"] },
        TestCase { category: "Complex Jukto", input: "smriti", expected_top: "স্মৃতি", acceptable: &["স্মৃতি"] },
        TestCase { category: "Complex Jukto", input: "dwondwo", expected_top: "দ্বন্দ্ব", acceptable: &["দ্বন্দ্ব"] },
        TestCase { category: "Complex Jukto", input: "dondho", expected_top: "দ্বন্দ্ব", acceptable: &["দ্বন্দ্ব"] },
        TestCase { category: "Complex Jukto", input: "ugro", expected_top: "উগ্র", acceptable: &["উগ্র"] },
        TestCase { category: "Complex Jukto", input: "angul", expected_top: "আঙুল", acceptable: &["আঙুল", "আঙ্গুল"] },
        TestCase { category: "Complex Jukto", input: "bongobondhu", expected_top: "বঙ্গবন্ধু", acceptable: &["বঙ্গবন্ধু"] },
        TestCase { category: "Complex Jukto", input: "shadhinota", expected_top: "স্বাধীনতা", acceptable: &["স্বাধীনতা"] },
        TestCase { category: "Complex Jukto", input: "kintu", expected_top: "কিন্তু", acceptable: &["কিন্তু"] },
        TestCase { category: "Complex Jukto", input: "chotto", expected_top: "ছোট্ট", acceptable: &["ছোট্ট"] },
        TestCase { category: "Complex Jukto", input: "ashchorjo", expected_top: "আশ্চর্য", acceptable: &["আশ্চর্য"] },
        TestCase { category: "Complex Jukto", input: "lokkhi", expected_top: "লক্ষ্মী", acceptable: &["লক্ষ্মী"] },
        TestCase { category: "Complex Jukto", input: "shikkharthi", expected_top: "শিক্ষার্থী", acceptable: &["শিক্ষার্থী"] },
        TestCase { category: "Complex Jukto", input: "bristi", expected_top: "বৃষ্টি", acceptable: &["বৃষ্টি"] },
        TestCase { category: "Complex Jukto", input: "brriShTi", expected_top: "বৃষ্টি", acceptable: &["বৃষ্টি"] },
        TestCase { category: "Complex Jukto", input: "srishti", expected_top: "সৃষ্টি", acceptable: &["সৃষ্টি"] },
        TestCase { category: "Complex Jukto", input: "drishti", expected_top: "দৃষ্টি", acceptable: &["দৃষ্টি"] },
        TestCase { category: "Complex Jukto", input: "ujjwal", expected_top: "উজ্জ্বল", acceptable: &["উজ্জ্বল"] },
        TestCase { category: "Complex Jukto", input: "ontorbhukto", expected_top: "অন্তর্ভুক্ত", acceptable: &["অন্তর্ভুক্ত"] },
        TestCase { category: "Complex Jukto", input: "shromik", expected_top: "শ্রমিক", acceptable: &["শ্রমিক"] },
        TestCase { category: "Complex Jukto", input: "gram", expected_top: "গ্রাম", acceptable: &["গ্রাম"] },
        TestCase { category: "Complex Jukto", input: "britto", expected_top: "বৃত্ত", acceptable: &["বৃত্ত"] },
        TestCase { category: "Complex Jukto", input: "trishna", expected_top: "তৃষ্ণা", acceptable: &["তৃষ্ণা"] },
        TestCase { category: "Complex Jukto", input: "shrestho", expected_top: "শ্রেষ্ঠ", acceptable: &["শ্রেষ্ঠ"] },
        TestCase { category: "Complex Jukto", input: "klanto", expected_top: "ক্লান্ত", acceptable: &["ক্লান্ত"] },
        TestCase { category: "Complex Jukto", input: "glani", expected_top: "গ্লানি", acceptable: &["গ্লানি"] },
        TestCase { category: "Complex Jukto", input: "prothom", expected_top: "প্রথম", acceptable: &["প্রথম"] },
        TestCase { category: "Complex Jukto", input: "prodhan", expected_top: "প্রধান", acceptable: &["প্রধান"] },
        TestCase { category: "Complex Jukto", input: "prostab", expected_top: "প্রস্তাব", acceptable: &["প্রস্তাব"] },

        // ── 3. Special Characters (Chandrabindu, Khanda Ta, Bishorgo, Anusvara) ─
        TestCase { category: "Special Char", input: "c^ad", expected_top: "চাঁদ", acceptable: &["চাঁদ"] },
        TestCase { category: "Special Char", input: "b^ash", expected_top: "বাঁশ", acceptable: &["বাঁশ"] },
        TestCase { category: "Special Char", input: "h^ashi", expected_top: "হাঁসি", acceptable: &["হাঁসি", "হাঁচি", "হাসি"] },
        TestCase { category: "Special Char", input: "ut``shob", expected_top: "উৎসব", acceptable: &["উৎসব"] },
        TestCase { category: "Special Char", input: "utshob", expected_top: "উৎসব", acceptable: &["উৎসব"] },
        TestCase { category: "Special Char", input: "hot``hat``", expected_top: "হঠাৎ", acceptable: &["হঠাৎ"] },
        TestCase { category: "Special Char", input: "hothat", expected_top: "হঠাৎ", acceptable: &["হঠাৎ"] },
        TestCase { category: "Special Char", input: "du:kho", expected_top: "দুঃখ", acceptable: &["দুঃখ"] },
        TestCase { category: "Special Char", input: "rong", expected_top: "রং", acceptable: &["রং", "রঙ"] },
        TestCase { category: "Special Char", input: "shongbad", expected_top: "সংবাদ", acceptable: &["সংবাদ"] },
        TestCase { category: "Special Char", input: "songbad", expected_top: "সংবাদ", acceptable: &["সংবাদ"] },
        TestCase { category: "Special Char", input: "songskar", expected_top: "সংস্কার", acceptable: &["সংস্কার"] },
        TestCase { category: "Special Char", input: "shongjog", expected_top: "সংযোগ", acceptable: &["সংযোগ"] },
        TestCase { category: "Special Char", input: "shongshod", expected_top: "সংসদ", acceptable: &["সংসদ"] },

        // ── 4. Capitalization Distinctions (Avro Standards) ───────────
        TestCase { category: "Case Sensitivity", input: "Taka", expected_top: "টাকা", acceptable: &["টাকা"] },
        TestCase { category: "Case Sensitivity", input: "taka", expected_top: "তাকা", acceptable: &["তাকা", "টাকা"] },
        TestCase { category: "Case Sensitivity", input: "Dal", expected_top: "ডাল", acceptable: &["ডাল"] },
        TestCase { category: "Case Sensitivity", input: "dal", expected_top: "দাল", acceptable: &["দাল", "ডাল"] },
        TestCase { category: "Case Sensitivity", input: "Daktar", expected_top: "ডাক্তার", acceptable: &["ডাক্তার"] },
        TestCase { category: "Case Sensitivity", input: "Thakur", expected_top: "ঠাকুর", acceptable: &["ঠাকুর"] },
        TestCase { category: "Case Sensitivity", input: "thakur", expected_top: "থাকুর", acceptable: &["থাকুর", "ঠাকুর"] },
    ];

    println!("\n========================================================");
    println!("   LEKHANI ANDROID LETTER-BY-LETTER TYPING SIMULATION   ");
    println!("========================================================\n");

    let mut total_passed = 0;
    let mut total_failed = 0;
    let mut failures = Vec::new();
    let mut intermediate_glitches = Vec::new();

    for tc in &test_cases {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);

        let mut step_preedits = Vec::new();

        // Feed character by character
        for ch in tc.input.chars() {
            let res = session.process_key(ch.to_string()).expect("process_key failed");
            step_preedits.push((ch, res.preedit.clone(), res.candidates.clone()));
        }

        // Test space commit
        let space_res = session.handle_space().expect("handle_space failed");
        let committed = space_res.commit_text.unwrap_or_default().trim().to_string();

        let last_step = step_preedits.last().unwrap();
        let final_preedit = &last_step.1;
        let final_candidates = &last_step.2;

        let passed = tc.acceptable.contains(&committed.as_str())
            || tc.acceptable.contains(&final_preedit.as_str())
            || final_candidates.iter().any(|c| tc.acceptable.contains(&c.as_str()));

        if passed {
            total_passed += 1;
        } else {
            total_failed += 1;
            failures.push((
                tc,
                committed.clone(),
                final_preedit.clone(),
                final_candidates.clone(),
                step_preedits.clone(),
            ));
        }

        // Check for intermediate glitches:
        // Did preedit ever become empty during typing of a non-empty word?
        for (i, (ch, pre, _)) in step_preedits.iter().enumerate() {
            if pre.is_empty() {
                intermediate_glitches.push((tc.input, *ch, i, "Preedit became empty mid-word"));
            }
        }
    }

    println!("Total Tested: {}", test_cases.len());
    println!("Passed:       {}", total_passed);
    println!("Failed:       {}", total_failed);
    println!("Success Rate: {:.1}%\n", (total_passed as f64 / test_cases.len() as f64) * 100.0);

    if !failures.is_empty() {
        println!("--------------------------------------------------------");
        println!("FAILURES & UNEXPECTED BEHAVIORS FOUND ({} items):", failures.len());
        println!("--------------------------------------------------------");
        for (tc, committed, preedit, candidates, steps) in &failures {
            println!("\n[FAIL] Category: {}", tc.category);
            println!("  Input:           '{}'", tc.input);
            println!("  Expected Top:    '{}'", tc.expected_top);
            println!("  Acceptable:      {:?}", tc.acceptable);
            println!("  Final Preedit:   '{}'", preedit);
            println!("  Committed Space: '{}'", committed);
            println!("  Top Candidates:  {:?}", candidates.iter().take(5).collect::<Vec<_>>());
            print!("  Keystroke Steps: ");
            for (ch, pr, _) in steps {
                print!("['{}' -> '{}'] ", ch, pr);
            }
            println!();
        }
    }

    if !intermediate_glitches.is_empty() {
        println!("\n--------------------------------------------------------");
        println!("INTERMEDIATE GLITCHES ({} items):", intermediate_glitches.len());
        println!("--------------------------------------------------------");
        for (word, ch, idx, reason) in &intermediate_glitches {
            println!("  Word '{}': char '{}' at pos {} - {}", word, ch, idx, reason);
        }
    }

    println!("\n========================================================\n");
}
