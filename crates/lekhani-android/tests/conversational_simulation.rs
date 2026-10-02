use lekhani_android::{AndroidLekhaniSession, LekhaniLayoutType};

#[test]
fn test_sentence_and_conversational_simulation() {
    let sentences = vec![
        ("ami bhalo achi", "আমি ভালো আছি"),
        ("tumi kemon acho", "তুমি কেমন আছো"),
        ("apni kemon achen", "আপনি কেমন আছেন"),
        ("ami tomake bhalobashi", "আমি তোমাকে ভালোবাসি"),
        ("amar sonar bangla", "আমার সোনার বাংলা"),
        ("kalke kothay jabe", "কালকে কোথায় যাবে"),
        ("tumi ki korcho", "তুমি কি করছো"),
        ("apni kothay thaken", "আপনি কোথায় থাকেন"),
        ("ami bhat khabo", "আমি ভাত খাবো"),
        ("ekhon shomoy koto", "এখন সময় কত"),
        ("kalke shokale dekha hobe", "কালকে সকালে দেখা হবে"),
    ];

    println!("\n========================================================");
    println!("      SENTENCE LETTER-BY-LETTER TYPING SIMULATION       ");
    println!("========================================================\n");

    let mut passed_sentences = 0;
    for (input_sentence, expected_sentence) in &sentences {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);

        let mut reconstructed_words = Vec::new();

        for word in input_sentence.split_whitespace() {
            // Type letter by letter
            for ch in word.chars() {
                session.process_key(ch.to_string()).unwrap();
            }
            // Space to commit
            let res = session.handle_space().unwrap();
            let committed = res.commit_text.unwrap_or_default().trim().to_string();
            reconstructed_words.push(committed);
        }

        let full_output = reconstructed_words.join(" ");
        let ok = full_output == *expected_sentence;
        if ok {
            passed_sentences += 1;
            println!("[PASS] '{}' -> '{}'", input_sentence, full_output);
        } else {
            println!("[FAIL] Input:    '{}'", input_sentence);
            println!("       Expected: '{}'", expected_sentence);
            println!("       Actual:   '{}'", full_output);
        }
    }
    println!("\nSentences Passed: {}/{}", passed_sentences, sentences.len());

    // ── Conversational Words Simulation ──────────────────────────────
    println!("\n========================================================");
    println!("     CONVERSATIONAL WORDS LETTER-BY-LETTER TEST         ");
    println!("========================================================\n");

    let conversational_words = vec![
        ("tahole", "তাহলে"),
        ("amio", "আমিও"),
        ("karun", "কারণ"),
        ("karon", "কারণ"),
        ("shobai", "সবাই"),
        ("sobai", "সবাই"),
        ("ekta", "একটা"),
        ("onek", "অনেক"),
        ("shathe", "সাথে"),
        ("sathe", "সাথে"),
        ("pore", "পরে"),
        ("age", "আগে"),
        ("beshi", "বেশি"),
        ("kom", "কম"),
        ("shobshomoy", "সবসময়"),
        ("sobshomoy", "সবসময়"),
        ("ashole", "আসলে"),
        ("mone", "মনে"),
        ("hoye", "হয়ে"),
        ("hoy", "হয়"),
        ("chilo", "ছিল"),
        ("chilam", "ছিলাম"),
        ("chilen", "ছিলেন"),
        ("korun", "করুন"),
        ("korben", "করবেন"),
        ("bolun", "বলুন"),
        ("dekhi", "দেখি"),
        ("dekhun", "দেখুন"),
        ("shunun", "শুনুন"),
        ("likhbo", "লিখবো"),
        ("janbo", "জানবো"),
        ("bujhte", "বুঝতে"),
        ("parchi", "পারছি"),
        ("parbo", "পারবো"),
        ("parben", "পারবেন"),
        ("hacche", "হচ্ছে"),
        ("hocche", "হচ্ছে"),
        ("jacche", "যাচ্ছে"),
        ("khacche", "খাচ্ছে"),
        ("dekhche", "দেখছে"),
        ("bolche", "বলছে"),
        ("khuje", "খুঁজে"),
        ("bollam", "বললাম"),
        ("gechilam", "গিয়েছিলাম"),
        ("ashchi", "আসছি"),
        ("shune", "শুনে"),
        ("bujhe", "বুঝে"),
        ("shikhe", "শিখে"),
        ("jante", "জানতে"),
        ("bolte", "বলতে"),
    ];

    let mut conv_passed = 0;
    let mut conv_failed = Vec::new();

    for (input, expected) in &conversational_words {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);

        let mut step_trace = Vec::new();
        for ch in input.chars() {
            let res = session.process_key(ch.to_string()).unwrap();
            step_trace.push((ch, res.preedit));
        }
        let space_res = session.handle_space().unwrap();
        let committed = space_res.commit_text.unwrap_or_default().trim().to_string();

        if committed == *expected {
            conv_passed += 1;
        } else {
            conv_failed.push((*input, *expected, committed, step_trace));
        }
    }

    println!("Conversational Words Passed: {}/{}", conv_passed, conversational_words.len());
    if !conv_failed.is_empty() {
        println!("\nFailures in Conversational Words ({}):", conv_failed.len());
        for (input, exp, actual, trace) in &conv_failed {
            println!("\n  Input:    '{}'", input);
            println!("  Expected: '{}'", exp);
            println!("  Actual:   '{}'", actual);
            print!("  Steps:    ");
            for (c, p) in trace {
                print!("['{}'->'{}'] ", c, p);
            }
            println!();
        }
    }

    // ── Backspace simulation test ─────────────────────────────────────
    println!("\n========================================================");
    println!("           BACKSPACE EDITING SIMULATION                 ");
    println!("========================================================\n");

    {
        // Test: type "bhal", backspace 'l', type 'i' -> "bhai" -> ভাই
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);
        session.process_key("b".into()).unwrap();
        session.process_key("h".into()).unwrap();
        session.process_key("a".into()).unwrap();
        let r1 = session.process_key("l".into()).unwrap();
        println!("Typed 'bhal' -> preedit: '{}'", r1.preedit);

        let r_bs = session.handle_backspace().unwrap();
        println!("After Backspace -> preedit: '{}'", r_bs.preedit);

        let r2 = session.process_key("i".into()).unwrap();
        println!("Typed 'i' -> preedit: '{}'", r2.preedit);
        let r_sp = session.handle_space().unwrap();
        let final_word = r_sp.commit_text.unwrap_or_default().trim().to_string();
        println!("Committed word: '{}' (Expected: 'ভাই')", final_word);
        assert_eq!(final_word, "ভাই", "Backspace edit 'bhal' -> BS -> 'i' should produce 'ভাই'");
    }

    println!("\n========================================================\n");
}
