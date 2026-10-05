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

    println!("\n========================================================\n");
}
