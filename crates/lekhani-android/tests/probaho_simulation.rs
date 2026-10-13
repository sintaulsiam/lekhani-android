use lekhani_android::{AndroidLekhaniSession, LekhaniLayoutType};

#[test]
fn test_probaho_simulation() {
    println!("\n========================================================");
    println!("          PROBAHO LAYOUT TYPING SIMULATION              ");
    println!("========================================================\n");

    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);

    // Test: Typing "আমি" on Probaho:
    // Probaho has direct Bengali characters on keys.
    // 'আ' is direct vowel, 'ম' is consonant, 'ি' is Kar.
    // In Probaho, when 'ি' is pressed after 'ম', does it combine into "মি"?
    let r1 = session.process_key("আ".into()).unwrap();
    println!("Probaho 'আ' -> preedit: '{}'", r1.preedit);
    let r2 = session.process_key("ম".into()).unwrap();
    println!("Probaho 'ম' -> preedit: '{}'", r2.preedit);
    let r3 = session.process_key("ি".into()).unwrap();
    println!("Probaho 'ি' -> preedit: '{}'", r3.preedit);

    let res = session.handle_space().unwrap();
    let word = res.commit_text.unwrap_or_default().trim().to_string();
    println!("Probaho space commit: '{}' (Expected: 'আমি')", word);
    assert_eq!(word, "আমি");

    // Test Juktoborno on Probaho:
    // e.g. ক + ্ + ষ = ক্ষ
    let session2 = AndroidLekhaniSession::new();
    session2.set_layout(LekhaniLayoutType::Probaho);
    session2.process_key("শ".into()).unwrap();
    session2.process_key("ি".into()).unwrap();
    session2.process_key("ক".into()).unwrap();
    session2.process_key("্".into()).unwrap();
    session2.process_key("ষ".into()).unwrap();
    session2.process_key("া".into()).unwrap();
    let res2 = session2.handle_space().unwrap();
    let word2 = res2.commit_text.unwrap_or_default().trim().to_string();
    println!(
        "Probaho 'শিক্ষা' -> space commit: '{}' (Expected: 'শিক্ষা')",
        word2
    );
    assert_eq!(word2, "শিক্ষা");

    // Test: In-flight conjunct candidate substitution for 'আচ্ছা' on Probaho layout:
    let session3 = AndroidLekhaniSession::new();
    session3.set_layout(LekhaniLayoutType::Probaho);
    session3.process_key("আ".into()).unwrap();
    session3.process_key("চ".into()).unwrap();
    let r_hasanta = session3.process_key("্".into()).unwrap();
    assert!(r_hasanta.candidates.contains(&"চ্ছ".to_string()), "Must suggest 'চ্ছ' after 'আচ্'");
    let r_sel = session3.select_candidate("চ্ছ".into()).unwrap();
    assert_eq!(r_sel.preedit, "আচ্ছ", "Selecting 'চ্ছ' must form 'আচ্ছ'");
    assert_eq!(r_sel.commit_text, None, "In-flight conjunct selection must keep composing open");
    let r_akar = session3.process_key("া".into()).unwrap();
    assert_eq!(r_akar.preedit, "আচ্ছা");
    let r_commit = session3.handle_space().unwrap();
    assert_eq!(r_commit.commit_text.as_deref().map(|s| s.trim()), Some("আচ্ছা"));

    // Test: In-flight conjunct candidate substitution for 'আচ্ছা' on National layout:
    let session_nat = AndroidLekhaniSession::new();
    session_nat.set_layout(LekhaniLayoutType::National);
    session_nat.process_key("আ".into()).unwrap();
    session_nat.process_key("চ".into()).unwrap();
    let r_hasanta_nat = session_nat.process_key("্".into()).unwrap();
    assert!(r_hasanta_nat.candidates.contains(&"চ্ছ".to_string()));
    let r_sel_nat = session_nat.select_candidate("চ্ছ".into()).unwrap();
    assert_eq!(r_sel_nat.preedit, "আচ্ছ");
    assert_eq!(r_sel_nat.commit_text, None);
    let r_akar_nat = session_nat.process_key("া".into()).unwrap();
    assert_eq!(r_akar_nat.preedit, "আচ্ছা");
    let r_commit_nat = session_nat.handle_space().unwrap();
    assert_eq!(r_commit_nat.commit_text.as_deref().map(|s| s.trim()), Some("আচ্ছা"));

    // Test: In-flight conjunct candidate substitution for 'আচ্ছা' on Gboard layout:
    let session_gb = AndroidLekhaniSession::new();
    session_gb.set_layout(LekhaniLayoutType::Gboard);
    session_gb.process_key("আ".into()).unwrap();
    session_gb.process_key("চ".into()).unwrap();
    let r_hasanta_gb = session_gb.process_key("্".into()).unwrap();
    assert!(r_hasanta_gb.candidates.contains(&"চ্ছ".to_string()));
    let r_sel_gb = session_gb.select_candidate("চ্ছ".into()).unwrap();
    assert_eq!(r_sel_gb.preedit, "আচ্ছ");
    assert_eq!(r_sel_gb.commit_text, None);
    let r_akar_gb = session_gb.process_key("া".into()).unwrap();
    assert_eq!(r_akar_gb.preedit, "আচ্ছা");
    let r_commit_gb = session_gb.handle_space().unwrap();
    assert_eq!(r_commit_gb.commit_text.as_deref().map(|s| s.trim()), Some("আচ্ছা"));
    println!("\n========================================================\n");
}
