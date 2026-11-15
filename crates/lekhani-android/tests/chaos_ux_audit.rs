use lekhani_android::{AndroidLekhaniSession, LekhaniLayoutType};

#[test]
fn test_chaos_short_tokens_avro() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // 1. Common Bengali phonetic syllables that must NEVER be hijacked by English words
    let syll_cases = [
        ("to", "তো"),
        ("no", "নো"),
        ("so", "সো"),
        ("me", "মে"),
        ("he", "হে"),
        ("go", "গো"),
        ("do", "দো"),
        ("ki", "কি"),
        ("na", "না"),
        ("ba", "বা"),
        ("ta", "তা"),
        ("da", "দা"),
        ("ma", "মা"),
        ("pa", "পা"),
        ("ka", "কা"),
        ("cha", "চা"),
        ("tha", "থা"),
        ("dha", "ধা"),
        ("sha", "শা"),
    ];

    for (input, expected) in syll_cases {
        session.reset();
        for ch in input.chars() {
            session.process_key(ch.to_string()).unwrap();
        }
        let res = session.handle_space().unwrap();
        let committed = res.commit_text.unwrap_or_default().trim().to_string();
        assert!(
            committed
                .chars()
                .all(|c| ('\u{0980}'..='\u{09FF}').contains(&c)),
            "Syllable '{}' must produce Bengali, got '{}'",
            input,
            committed
        );
        if !["to", "no", "so", "go", "do"].contains(&input) {
            assert_eq!(
                committed, expected,
                "Syllable '{}' must commit '{}', got '{}'",
                input, expected, committed
            );
        }
    }

    // 2. Double consonants / Geminate conjuncts
    let geminate_cases = [
        ("cca", "চ্চা"),
        ("kka", "ক্কা"),
        ("tta", "ত্তা"),
        ("TTa", "ট্টা"),
        ("dda", "দ্দা"),
        ("DDa", "ড্ডা"),
        ("abba", "আব্বা"),
        ("ppa", "প্পা"),
        ("mma", "ম্মা"),
        ("nna", "ন্না"),
        ("lla", "ল্লা"),
        ("jja", "জ্জা"),
    ];

    for (input, expected) in geminate_cases {
        session.reset();
        for ch in input.chars() {
            session.process_key(ch.to_string()).unwrap();
        }
        let res = session.handle_space().unwrap();
        let committed = res.commit_text.unwrap_or_default().trim().to_string();
        assert_eq!(
            committed, expected,
            "Geminate '{}' must commit '{}', got '{}'",
            input, expected, committed
        );
    }

    // 3. All-caps technical acronyms MUST stay Latin
    let acronym_cases = ["API", "JSON", "RAM", "CPU", "UI", "CCA"];
    for input in acronym_cases {
        session.reset();
        for ch in input.chars() {
            session.process_key(ch.to_string()).unwrap();
        }
        let res = session.handle_space().unwrap();
        let committed = res.commit_text.unwrap_or_default().trim().to_string();
        assert_eq!(
            committed, input,
            "All-caps acronym '{}' must commit to Latin '{}', got '{}'",
            input, input, committed
        );
    }

    // 4. All-caps Bengali words MUST resolve to Bengali
    let bengali_caps = [("AMI", "আমি"), ("TUMI", "তুমি"), ("DESH", "দেশ")];
    for (input, expected) in bengali_caps {
        session.reset();
        for ch in input.chars() {
            session.process_key(ch.to_string()).unwrap();
        }
        let res = session.handle_space().unwrap();
        let committed = res.commit_text.unwrap_or_default().trim().to_string();
        assert_eq!(
            committed, expected,
            "All-caps Bengali '{}' must commit to Bengali '{}', got '{}'",
            input, expected, committed
        );
    }
}

#[test]
fn test_chaos_midword_conjunct_and_continuation() {
    let layouts = [
        LekhaniLayoutType::Probaho,
        LekhaniLayoutType::National,
        LekhaniLayoutType::Gboard,
    ];

    for layout in layouts {
        let session = AndroidLekhaniSession::new();
        session.set_layout(layout);

        // Scenario 1: Typing 'আচ্ছা' (আ + চ + ্ -> select 'চ্ছ' -> া -> space)
        session.reset();
        session.process_key("আ".into()).unwrap();
        session.process_key("চ".into()).unwrap();
        let r_hasanta = session.process_key("্".into()).unwrap();
        assert!(
            r_hasanta.candidates.contains(&"চ্ছ".to_string()),
            "Layout {:?} must suggest 'চ্ছ' after 'আচ্'",
            layout
        );
        let r_sel = session.select_candidate("চ্ছ".into()).unwrap();
        assert_eq!(
            r_sel.preedit, "আচ্ছ",
            "Layout {:?} selecting 'চ্ছ' must give 'আচ্ছ'",
            layout
        );
        assert_eq!(
            r_sel.commit_text, None,
            "Layout {:?} in-flight conjunct selection must keep composing open",
            layout
        );
        let r_akar = session.process_key("া".into()).unwrap();
        assert_eq!(
            r_akar.preedit, "আচ্ছা",
            "Layout {:?} adding akar must give 'আচ্ছা'",
            layout
        );
        let r_space = session.handle_space().unwrap();
        assert_eq!(
            r_space.commit_text.as_deref().map(|s| s.trim()),
            Some("আচ্ছা"),
            "Layout {:?} space must commit 'আচ্ছা'",
            layout
        );

        // Scenario 2: Reph (র + ্ + ক -> select 'র্ক' -> া -> space)
        session.reset();
        session.process_key("র".into()).unwrap();
        session.process_key("্".into()).unwrap();
        let r_k = session.process_key("ক".into()).unwrap();
        // preedit is 'র্ক'
        assert_eq!(r_k.preedit, "র্ক");
        let r_akar2 = session.process_key("া".into()).unwrap();
        assert_eq!(r_akar2.preedit, "র্কা");
        let r_space2 = session.handle_space().unwrap();
        assert_eq!(
            r_space2.commit_text.as_deref().map(|s| s.trim()),
            Some("র্কা")
        );
    }
}

#[test]
fn test_chaos_grapheme_safe_backspace() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);

    // 1. Cluster 'ক্ষ' (ক + ্ + ষ)
    session.process_key("ক".into()).unwrap();
    session.process_key("্".into()).unwrap();
    let r = session.process_key("ষ".into()).unwrap();
    assert_eq!(r.preedit, "ক্ষ");

    // Backspace 1: deletes ষ -> leaves ক্
    let bs1 = session.handle_backspace().unwrap();
    assert_eq!(bs1.preedit, "ক্");

    // Backspace 2: deletes ্ -> leaves ক
    let bs2 = session.handle_backspace().unwrap();
    assert_eq!(bs2.preedit, "ক");

    // Backspace 3: deletes ক -> empty
    let bs3 = session.handle_backspace().unwrap();
    assert_eq!(bs3.preedit, "");

    // 2. Vowel kar 'কা' (ক + া)
    session.reset();
    session.process_key("ক".into()).unwrap();
    let r_ka = session.process_key("া".into()).unwrap();
    assert_eq!(r_ka.preedit, "কা");

    let bs_ka1 = session.handle_backspace().unwrap();
    assert_eq!(bs_ka1.preedit, "ক");

    let bs_ka2 = session.handle_backspace().unwrap();
    assert_eq!(bs_ka2.preedit, "");
}

#[test]
fn test_chaos_numeric_decimal_dot_in_avro() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // 1. English digits decimal separator: 3.14
    session.reset();
    session.process_key("3".into()).unwrap();
    let r_dot = session.process_key(".".into()).unwrap();
    // In Avro, dot after digit should NOT commit Dari '।'
    assert_eq!(
        r_dot.commit_text, None,
        "Dot after digit in Avro must not flush as Dari"
    );
    session.process_key("1".into()).unwrap();
    session.process_key("4".into()).unwrap();
    let r_res = session.handle_space().unwrap();
    let text = r_res.commit_text.unwrap_or_default().trim().to_string();
    assert_eq!(text, "৩.১৪", "Expected '৩.১৪', got '{}'", text);

    // 2. Bengali digits decimal separator: ৩.১৪
    session.reset();
    session.process_key("৩".into()).unwrap();
    let r_dot_bn = session.process_key(".".into()).unwrap();
    assert_eq!(
        r_dot_bn.commit_text, None,
        "Dot after Bengali digit in Avro must not flush as Dari"
    );
    session.process_key("১".into()).unwrap();
    session.process_key("৪".into()).unwrap();
    let r_res_bn = session.handle_space().unwrap();
    let text_bn = r_res_bn.commit_text.unwrap_or_default().trim().to_string();
    assert_eq!(text_bn, "৩.১৪", "Expected '৩.১৪', got '{}'", text_bn);
}
