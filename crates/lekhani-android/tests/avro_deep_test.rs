use lekhani_android::{AndroidLekhaniSession, LekhaniLayoutType};

struct AvroRuleTest {
    feature: &'static str,
    input: &'static str,
    expected: &'static str,
}

#[test]
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
            println!("  Candidates:    {:?}", &cands.iter().take(5).collect::<Vec<_>>());
            print!("  Steps:         ");
            for (ch, pr, _) in steps {
                print!("['{}'->'{}'] ", ch, pr);
            }
            println!();
        }
    }
    println!("\n========================================================\n");
}
