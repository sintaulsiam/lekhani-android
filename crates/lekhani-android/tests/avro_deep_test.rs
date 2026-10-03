use lekhani_android::{AndroidLekhaniSession, LekhaniLayoutType};
use serial_test::serial;

struct AvroRuleTest {
    feature: &'static str,
    input: &'static str,
    expected: &'static str,
}

#[test]
#[serial]
fn test_avro_exhaustive_features() {
    let tests = vec![
        // ── 1. Independent Vowels ─────────────────────────────────────
        AvroRuleTest { feature: "Independent Vowel", input: "o", expected: "অ" },
        AvroRuleTest { feature: "Independent Vowel", input: "a", expected: "আ" },
        AvroRuleTest { feature: "Independent Vowel", input: "i", expected: "ই" },
        AvroRuleTest { feature: "Independent Vowel", input: "I", expected: "ঈ" },
        AvroRuleTest { feature: "Independent Vowel", input: "u", expected: "উ" },
        AvroRuleTest { feature: "Independent Vowel", input: "U", expected: "ঊ" },
        AvroRuleTest { feature: "Independent Vowel", input: "rri", expected: "ঋ" },
        AvroRuleTest { feature: "Independent Vowel", input: "e", expected: "এ" },
        AvroRuleTest { feature: "Independent Vowel", input: "OI", expected: "ঐ" },
        AvroRuleTest { feature: "Independent Vowel", input: "O", expected: "ও" },
        AvroRuleTest { feature: "Independent Vowel", input: "OU", expected: "ঔ" },

        // ── 2. Vowel Kars after Consonants ────────────────────────────
        AvroRuleTest { feature: "Vowel Kar", input: "ka", expected: "কা" },
        AvroRuleTest { feature: "Vowel Kar", input: "ki", expected: "কি" },
        AvroRuleTest { feature: "Vowel Kar", input: "kI", expected: "কী" },
        AvroRuleTest { feature: "Vowel Kar", input: "ku", expected: "কু" },
        AvroRuleTest { feature: "Vowel Kar", input: "kU", expected: "কূ" },
        AvroRuleTest { feature: "Vowel Kar", input: "krri", expected: "কৃ" },
        AvroRuleTest { feature: "Vowel Kar", input: "ke", expected: "কে" },
        AvroRuleTest { feature: "Vowel Kar", input: "kOI", expected: "কৈ" },
        AvroRuleTest { feature: "Vowel Kar", input: "ko", expected: "কো" },
        AvroRuleTest { feature: "Vowel Kar", input: "kOU", expected: "কৌ" },

        // ── 3. Consonants and Aspirates ───────────────────────────────
        AvroRuleTest { feature: "Consonant", input: "ka", expected: "কা" },
        AvroRuleTest { feature: "Consonant", input: "kha", expected: "খা" },
        AvroRuleTest { feature: "Consonant", input: "ga", expected: "গা" },
        AvroRuleTest { feature: "Consonant", input: "gha", expected: "ঘা" },
        AvroRuleTest { feature: "Consonant", input: "Nga", expected: "ঙা" },
        AvroRuleTest { feature: "Consonant", input: "ca", expected: "চা" },
        AvroRuleTest { feature: "Consonant", input: "cha", expected: "ছা" },
        AvroRuleTest { feature: "Consonant", input: "ja", expected: "জা" },
        AvroRuleTest { feature: "Consonant", input: "jha", expected: "ঝা" },
        AvroRuleTest { feature: "Consonant", input: "Ta", expected: "টা" },
        AvroRuleTest { feature: "Consonant", input: "Tha", expected: "ঠা" },
        AvroRuleTest { feature: "Consonant", input: "Da", expected: "ডা" },
        AvroRuleTest { feature: "Consonant", input: "Dha", expected: "ঢা" },
        AvroRuleTest { feature: "Consonant", input: "Na", expected: "ণা" },
        AvroRuleTest { feature: "Consonant", input: "ta", expected: "তা" },
        AvroRuleTest { feature: "Consonant", input: "tha", expected: "থা" },
        AvroRuleTest { feature: "Consonant", input: "da", expected: "দা" },
        AvroRuleTest { feature: "Consonant", input: "dha", expected: "ধা" },
        AvroRuleTest { feature: "Consonant", input: "na", expected: "না" },
        AvroRuleTest { feature: "Consonant", input: "pa", expected: "পা" },
        AvroRuleTest { feature: "Consonant", input: "pha", expected: "ফা" },
        AvroRuleTest { feature: "Consonant", input: "fa", expected: "ফা" },
        AvroRuleTest { feature: "Consonant", input: "ba", expected: "বা" },
        AvroRuleTest { feature: "Consonant", input: "bha", expected: "ভা" },
        AvroRuleTest { feature: "Consonant", input: "va", expected: "ভা" },
        AvroRuleTest { feature: "Consonant", input: "ma", expected: "মা" },
        AvroRuleTest { feature: "Consonant", input: "za", expected: "জা" },
        AvroRuleTest { feature: "Consonant", input: "Za", expected: "যা" },
        AvroRuleTest { feature: "Consonant", input: "ra", expected: "রা" },
        AvroRuleTest { feature: "Consonant", input: "la", expected: "লা" },
        AvroRuleTest { feature: "Consonant", input: "sha", expected: "শা" },
        AvroRuleTest { feature: "Consonant", input: "Sha", expected: "ষা" },
        AvroRuleTest { feature: "Consonant", input: "sa", expected: "সা" },
        AvroRuleTest { feature: "Consonant", input: "ha", expected: "হা" },
        AvroRuleTest { feature: "Consonant", input: "Ra", expected: "ড়া" },
        AvroRuleTest { feature: "Consonant", input: "Rha", expected: "ঢ়া" },
        AvroRuleTest { feature: "Consonant", input: "ya", expected: "য়া" },

        // ── 4. Reph (র্ ) ─────────────────────────────────────────────
        AvroRuleTest { feature: "Reph", input: "barna", expected: "বর্ণ" },
        AvroRuleTest { feature: "Reph", input: "kormo", expected: "কর্ম" },
        AvroRuleTest { feature: "Reph", input: "dharmo", expected: "ধর্ম" },
        AvroRuleTest { feature: "Reph", input: "shorgo", expected: "স্বর্গ" },
        AvroRuleTest { feature: "Reph", input: "purno", expected: "পূর্ণ" },
        AvroRuleTest { feature: "Reph", input: "durga", expected: "দুর্গা" },
        AvroRuleTest { feature: "Reph", input: "shurjo", expected: "সূর্য" },

        // ── 5. Ya-phola (্য) and Ba-phola (্ব) ─────────────────────────
        AvroRuleTest { feature: "Phola", input: "bakkya", expected: "বাক্য" },
        AvroRuleTest { feature: "Phola", input: "onnya", expected: "অন্য" },
        AvroRuleTest { feature: "Phola", input: "shotto", expected: "সত্য" },
        AvroRuleTest { feature: "Phola", input: "shadhin", expected: "স্বাধীন" },
        AvroRuleTest { feature: "Phola", input: "dwitiyo", expected: "দ্বিতীয়" },
        AvroRuleTest { feature: "Phola", input: "bissho", expected: "বিশ্ব" },

        // ── 6. Ra-phola (্র) and Ri-kar (ৃ) ───────────────────────────
        AvroRuleTest { feature: "Ra-phola", input: "prothom", expected: "প্রথম" },
        AvroRuleTest { feature: "Ra-phola", input: "bhabro", expected: "ভাব্র" },
        AvroRuleTest { feature: "Ra-phola", input: "chhatro", expected: "ছাত্র" },
        AvroRuleTest { feature: "Ri-kar", input: "krripa", expected: "কৃপা" },
        AvroRuleTest { feature: "Ri-kar", input: "kripa", expected: "কৃপা" },
        AvroRuleTest { feature: "Ri-kar", input: "brritti", expected: "বৃত্তি" },
        AvroRuleTest { feature: "Ri-kar", input: "britti", expected: "বৃত্তি" },

        // ── 7. Juktoborno Clusters ────────────────────────────────────
        AvroRuleTest { feature: "Juktoborno", input: "kkh", expected: "ক্ষ" },
        AvroRuleTest { feature: "Juktoborno", input: "shikkha", expected: "শিক্ষা" },
        AvroRuleTest { feature: "Juktoborno", input: "lokkhi", expected: "লক্ষ্মী" },
        AvroRuleTest { feature: "Juktoborno", input: "bijnan", expected: "বিজ্ঞান" },
        AvroRuleTest { feature: "Juktoborno", input: "ongko", expected: "অঙ্ক" },
        AvroRuleTest { feature: "Juktoborno", input: "shongko", expected: "শঙ্ক" },
        AvroRuleTest { feature: "Juktoborno", input: "bongo", expected: "বঙ্গ" },
        AvroRuleTest { feature: "Juktoborno", input: "shonchoi", expected: "সঞ্চয়" },
        AvroRuleTest { feature: "Juktoborno", input: "onjo", expected: "অঞ্জ" },
        AvroRuleTest { feature: "Juktoborno", input: "ghonTa", expected: "ঘণ্টা" },
        AvroRuleTest { feature: "Juktoborno", input: "ghonta", expected: "ঘণ্টা" },
        AvroRuleTest { feature: "Juktoborno", input: "kando", expected: "কাণ্ড" },
        AvroRuleTest { feature: "Juktoborno", input: "shanto", expected: "শান্ত" },
        AvroRuleTest { feature: "Juktoborno", input: "sundor", expected: "সুন্দর" },
        AvroRuleTest { feature: "Juktoborno", input: "mondir", expected: "মন্দির" },
        AvroRuleTest { feature: "Juktoborno", input: "shomporko", expected: "সম্পর্ক" },
        AvroRuleTest { feature: "Juktoborno", input: "somporko", expected: "সম্পর্ক" },
        AvroRuleTest { feature: "Juktoborno", input: "lomba", expected: "লম্বা" },
        AvroRuleTest { feature: "Juktoborno", input: "shobdo", expected: "শব্দ" },
        AvroRuleTest { feature: "Juktoborno", input: "shobdo", expected: "শব্দ" },
        AvroRuleTest { feature: "Juktoborno", input: "shubho", expected: "শুভ" },
        AvroRuleTest { feature: "Juktoborno", input: "ashchorjo", expected: "আশ্চর্য" },
        AvroRuleTest { feature: "Juktoborno", input: "shoshto", expected: "ষষ্ঠ" },
        AvroRuleTest { feature: "Juktoborno", input: "shoshTho", expected: "ষষ্ঠ" },
        AvroRuleTest { feature: "Juktoborno", input: "koshTo", expected: "কষ্ট" },
        AvroRuleTest { feature: "Juktoborno", input: "koshto", expected: "কষ্ট" },
        AvroRuleTest { feature: "Juktoborno", input: "shreshTho", expected: "শ্রেষ্ঠ" },
        AvroRuleTest { feature: "Juktoborno", input: "shrestho", expected: "শ্রেষ্ঠ" },

        // ── 8. Special Modifiers ──────────────────────────────────────
        AvroRuleTest { feature: "Special", input: "c^ad", expected: "চাঁদ" },
        AvroRuleTest { feature: "Special", input: "b^ash", expected: "বাঁশ" },
        AvroRuleTest { feature: "Special", input: "p^ac", expected: "পাঁচ" },
        AvroRuleTest { feature: "Special", input: "ut``shob", expected: "উৎসব" },
        AvroRuleTest { feature: "Special", input: "hot``hat``", expected: "হঠাৎ" },
        AvroRuleTest { feature: "Special", input: "du:kho", expected: "দুঃখ" },
        AvroRuleTest { feature: "Special", input: "bipod:jonok", expected: "বিপদঃজনক" },
        AvroRuleTest { feature: "Special", input: "rong", expected: "রং" },
        AvroRuleTest { feature: "Special", input: "shongshod", expected: "সংসদ" },
    ];

    println!("\n========================================================");
    println!("       EXHAUSTIVE AVRO PHONETIC SPECIFICATION TEST       ");
    println!("========================================================\n");

    let mut passed = 0;
    let mut failed = 0;
    let mut failures = Vec::new();

    for t in &tests {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);

        let mut step_history = Vec::new();
        for ch in t.input.chars() {
            let res = session.process_key(ch.to_string()).unwrap();
            step_history.push((ch, res.preedit.clone(), res.candidates.clone()));
        }

        let space_res = session.handle_space().unwrap();
        let committed = space_res.commit_text.unwrap_or_default().trim().to_string();

        let last_step = step_history.last().unwrap();
        let preedit = &last_step.1;
        let candidates = &last_step.2;

        let ok = committed == t.expected
            || preedit == t.expected
            || candidates.iter().any(|c| c == t.expected);

        if ok {
            passed += 1;
        } else {
            failed += 1;
            failures.push((t, committed, preedit.clone(), candidates.clone(), step_history));
        }
    }

    println!("Total Tested: {}", tests.len());
    println!("Passed:       {}", passed);
    println!("Failed:       {}", failed);
    println!("Pass Rate:    {:.1}%\n", (passed as f64 / tests.len() as f64) * 100.0);

    if !failures.is_empty() {
        println!("--------------------------------------------------------");
        println!("FAILURES IN AVRO PHONETIC SPECIFICATION ({} items):", failures.len());
        println!("--------------------------------------------------------");
        for (t, comm, pre, cands, steps) in &failures {
            println!("\n[FAIL] Feature:  {}", t.feature);
            println!("  Input:         '{}'", t.input);
            println!("  Expected:      '{}'", t.expected);
            println!("  Final Preedit: '{}'", pre);
            println!("  Committed:     '{}'", comm);
            println!("  Candidates:    {:?}", cands.iter().take(5).collect::<Vec<_>>());
            print!("  Steps:         ");
            for (ch, pr, _) in steps {
                print!("['{}'->'{}'] ", ch, pr);
            }
            println!();
        }
    }
    println!("\n========================================================\n");
}

#[test]
#[serial]
fn test_diagnose_avro_failures() {
    let failed_inputs = vec![
        "ko",
        "jha",
        "za",
        "Za",
        "ya",
        "barna",
        "onnya",
        "kkh",
        "shongko",
        "shonchoi",
        "onjo",
        "du:kho",
        "koto",
        "pore",
        "ashole",
        "hacche",
        "chotto",
    ];

    println!("\n=== DIAGNOSE FAILURES: PARSER vs CORE SUGGESTION ===");
    let parser = lekhani_android::avro::get_avro_parser();
    let (sugg_guard, _) = (lekhani_android::session::get_phonetic_suggestion(), ());
    let mut sugg = sugg_guard.lock().unwrap();

    for inp in failed_inputs {
        let parser_output = parser.convert(inp);
        let empty_ctx: [&str; 0] = [];
        let empty_mem = hashbrown::HashMap::new();
        let (sugg_cands, sel_idx) = sugg.suggest_with_multi_context(inp, &empty_ctx, false, true, &empty_mem);
        println!("Input: '{:10}' | Parser: '{:10}' | Sugg Cands: {:?} (sel: {})", inp, parser_output, sugg_cands.iter().take(4).collect::<Vec<_>>(), sel_idx);
    }
}

#[test]
#[serial]
fn test_audit_bug_fixes_and_ux_primitives() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // ── Bug 1: Latin memory infection prevented ──────────────────────────────
    // User types "bhalo" and selects Latin "bhalo" chip twice
    for _ in 0..3 {
        for c in "bhalo".chars() {
            session.process_key(c.to_string()).unwrap();
        }
        session.select_candidate("bhalo".to_string()).unwrap();
    }
    // Typing "bhalo" and hitting space MUST commit Bengali "ভালো", not Latin "bhalo"!
    for c in "bhalo".chars() {
        session.process_key(c.to_string()).unwrap();
    }
    let res = session.handle_space().unwrap();
    assert_eq!(res.commit_text.as_deref(), Some("ভালো "));

    // ── Bug 3: Short English words unblocked ──────────────────────────────────
    let (_, cands_ok) = lekhani_android::avro::transliterate_avro("ok");
    assert!(cands_ok.iter().any(|c| c.eq_ignore_ascii_case("ok")), "cands_ok must contain 'ok': {:?}", cands_ok);

    let (_, cands_hi) = lekhani_android::avro::transliterate_avro("hi");
    assert!(cands_hi.iter().any(|c| c.eq_ignore_ascii_case("hi")), "cands_hi must contain 'hi': {:?}", cands_hi);

    let (_, cands_fb) = lekhani_android::avro::transliterate_avro("fb");
    assert!(cands_fb.iter().any(|c| c.eq_ignore_ascii_case("fb")), "cands_fb must contain 'fb': {:?}", cands_fb);

    // ── Bug 5: Force Avro def preservation ────────────────────────────────────
    let (pri_amr, cands_amr) = lekhani_android::avro::transliterate_avro("amr");
    assert_eq!(pri_amr, "আমার");
    assert_eq!(cands_amr.first().map(|s| s.as_str()), Some("আমার"));
    assert_eq!(cands_amr.get(1).map(|s| s.as_str()), Some("আম্র"), "Force Avro def 'আম্র' must be at index 1: {:?}", cands_amr);

    let (pri_apni, cands_apni) = lekhani_android::avro::transliterate_avro("apni");
    assert_eq!(pri_apni, "আপনি");
    assert_eq!(cands_apni.first().map(|s| s.as_str()), Some("আপনি"));
    assert_eq!(cands_apni.get(1).map(|s| s.as_str()), Some("আপ্নি"), "Force Avro def 'আপ্নি' must be at index 1: {:?}", cands_apni);

    // ── Bug 8: Backtick ZWNJ conjunct breaker ─────────────────────────────────
    let (pri_ryab, _) = lekhani_android::avro::transliterate_avro("r`yab");
    assert_eq!(pri_ryab, "র‍্যাব");

    let (pri_kk, _) = lekhani_android::avro::transliterate_avro("k`k");
    assert_eq!(pri_kk, "কক");

    // ── Bug 9 & 10: Titlecase and All-Caps normalization ───────────────────────
    let (pri_din, cands_din) = lekhani_android::avro::transliterate_avro("Din");
    assert_eq!(pri_din, "দিন", "Din must prioritize 'দিন' over 'ডিন'");
    assert!(cands_din.contains(&"ডিন".to_string()));

    let (pri_tara, cands_tara) = lekhani_android::avro::transliterate_avro("Tara");
    assert_eq!(pri_tara, "তারা", "Tara must prioritize 'তারা' over 'টারা'");
    assert!(cands_tara.contains(&"টারা".to_string()));

    let (pri_ami, _) = lekhani_android::avro::transliterate_avro("AMI");
    assert_eq!(pri_ami, "আমি", "All-caps AMI must produce 'আমি'");

    let (pri_tumi, _) = lekhani_android::avro::transliterate_avro("TUMI");
    assert_eq!(pri_tumi, "তুমি", "All-caps TUMI must produce 'তুমি'");

    // ── Bug 11: Dual digits & currency ───────────────────────────────────────
    let (_, cands_num) = lekhani_android::avro::transliterate_avro("1234");
    assert!(cands_num.contains(&"1234".to_string()), "Numerals must offer Latin '1234': {:?}", cands_num);
    assert!(cands_num.contains(&"১২৩৪".to_string()));

    let (_, cands_cur) = lekhani_android::avro::transliterate_avro("$100");
    assert!(cands_cur.contains(&"$100".to_string()), "Currency must offer Latin '$100': {:?}", cands_cur);
    assert!(cands_cur.contains(&"৳১০০".to_string()));

    // ── WYSIWYG Spacebar commit with choice ──────────────────────────────────
    session.reset();
    for c in "amr".chars() {
        session.process_key(c.to_string()).unwrap();
    }
    // Hitting spacebar with chosen "আম্র" (from Slot 2 on strip) commits "আম্র "!
    let choice_res = session.handle_space_with_choice(Some("আম্র".to_string())).unwrap();
    assert_eq!(choice_res.commit_text.as_deref(), Some("আম্র "));
}

#[test]
#[serial]
fn test_typo_recovery_always_surfaces() {
    // Verify that typo/proximity corrections appear within the first 5 candidates.
    // Users who mistype common words must see the recovery suggestion without scrolling.
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    struct Case {
        input: &'static str,
        expected_in_top5: &'static str,
    }
    let cases = [
        // "bhlao" is a common mistyping of "bhalo" (ভালো)
        Case {
            input: "bhlao",
            expected_in_top5: "ভালো",
        },
        // "ammi" is a near-miss for "ami" (আমি)
        Case {
            input: "ammi",
            expected_in_top5: "আমি",
        },
        // "tomi" is a common mistype for "tumi" (তুমি)
        Case {
            input: "tomi",
            expected_in_top5: "তুমি",
        },
    ];

    for case in &cases {
        session.reset();
        let mut last_candidates = Vec::new();
        for ch in case.input.chars() {
            let res = session.process_key(ch.to_string()).unwrap();
            last_candidates = res.candidates;
        }
        let found = last_candidates.iter().take(5).any(|c| c == case.expected_in_top5);
        assert!(
            found,
            "Expected '{}' in top-5 candidates for input '{}', got: {:?}",
            case.expected_in_top5,
            case.input,
            &last_candidates[..last_candidates.len().min(5)]
        );
    }
}

#[test]
#[serial]
fn test_contextual_homophone_disambiguation_matrix() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // 1. "বই" (book) -> "pora" should rank "পড়া" higher than "পরা"
    session.reset();
    session.set_context("বই ".into());
    for c in "pora".chars() {
        session.process_key(c.to_string()).unwrap();
    }
    let res1 = session.handle_space().unwrap();
    assert_eq!(res1.commit_text.as_deref(), Some("পড়া "));

    // 2. "জামা" (shirt/clothes) -> "pora" should rank "পরা" higher than "পড়া"
    session.reset();
    session.set_context("জামা ".into());
    for c in "pora".chars() {
        session.process_key(c.to_string()).unwrap();
    }
    let res2 = session.handle_space().unwrap();
    assert_eq!(res2.commit_text.as_deref(), Some("পরা "));

    // 3. "আমার" -> "matha" should rank "মাথা"
    session.reset();
    session.set_context("আমার ".into());
    for c in "matha".chars() {
        session.process_key(c.to_string()).unwrap();
    }
    let res3 = session.handle_space().unwrap();
    assert_eq!(res3.commit_text.as_deref(), Some("মাথা "));

    // 4. "গরম" -> "bhat" should rank "ভাত"
    session.reset();
    session.set_context("গরম ".into());
    for c in "bhat".chars() {
        session.process_key(c.to_string()).unwrap();
    }
    let res4 = session.handle_space().unwrap();
    assert_eq!(res4.commit_text.as_deref(), Some("ভাত "));
}

#[test]
#[serial]
fn test_homophone_and_authoritative_candidate_ranking() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // Test cases where candidate 0 must be the authoritative common word
    // and candidate 1 is the literal or homophone alternative.
    let cases: &[(&str, &str, Option<&str>)] = &[
        ("shob", "সব", Some("শব")),
        ("bari", "বাড়ি", Some("বারি")),
        ("gari", "গাড়ি", Some("গারি")),
        ("hotat", "হঠাৎ", Some("হতাত")),
        ("sriti", "স্মৃতি", Some("সৃতি")),
        ("dondho", "দ্বন্দ্ব", Some("দন্ধ")),
        ("kkh", "ক্ষ", None),
        ("karon", "কারণ", Some("কারন")),
    ];

    for &(input, expected_first, expected_second) in cases {
        session.reset();
        let mut last_candidates = Vec::new();
        for ch in input.chars() {
            let res = session.process_key(ch.to_string()).unwrap();
            last_candidates = res.candidates;
        }

        assert_eq!(
            last_candidates.first().map(|s| s.as_str()),
            Some(expected_first),
            "For input '{}', candidate[0] must be '{}', got: {:?}",
            input,
            expected_first,
            last_candidates
        );

        if let Some(second) = expected_second {
            assert!(
                last_candidates.contains(&second.to_string()),
                "For input '{}', candidates must contain '{}', got: {:?}",
                input,
                second,
                last_candidates
            );
        }

        let space_res = session.handle_space().unwrap();
        assert_eq!(
            space_res.commit_text.as_deref(),
            Some(&format!("{} ", expected_first)[..]),
            "Spacebar for '{}' must commit '{} '",
            input,
            expected_first
        );
    }
}




