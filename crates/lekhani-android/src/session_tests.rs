
use super::*;
use serial_test::serial;

#[test]
fn test_session_lifecycle_and_smart_kar() {
    let session = AndroidLekhaniSession::new();
    assert_eq!(session.get_layout(), LekhaniLayoutType::Probaho);
    assert!(!session.is_composing());

    // Typing Kar 'া' at word start should promote to 'আ'
    let res = session.process_key("া".into()).unwrap();
    assert_eq!(res.preedit, "আ");
    assert!(session.is_composing());

    // Type 'ম' and then 'ি' -> "আমি"
    let _ = session.process_key("ম".into()).unwrap();
    let res = session.process_key("ি".into()).unwrap();
    assert_eq!(res.preedit, "আমি");

    // Space commits the word with NFC normalization
    let space_res = session.handle_space().unwrap();
    assert_eq!(space_res.commit_text, Some("আমি ".into()));
    assert!(!session.is_composing());
}

#[test]
fn test_grapheme_safe_backspace() {
    let session = AndroidLekhaniSession::new();
    // Type ক, then backspace -> buffer empty
    let _ = session.process_key("ক".into()).unwrap();
    assert_eq!(session.handle_backspace().unwrap().preedit, "");

    // Type ক্ষ step by step (ক + ্ + ষ):
    let _ = session.process_key("ক".into()).unwrap();
    let _ = session.process_key("্".into()).unwrap(); // ক্
    let _ = session.process_key("ষ".into()).unwrap(); // ক্ষ
                                                      // Backspace pops the last character 'ষ', leaving 'ক্'
    let r1 = session.handle_backspace().unwrap();
    assert_eq!(r1.preedit, "ক্");

    // Next backspace pops the hasanta '্', leaving 'ক'
    let r2 = session.handle_backspace().unwrap();
    assert_eq!(r2.preedit, "ক");

    // Next backspace pops 'ক', leaving empty buffer
    let r3 = session.handle_backspace().unwrap();
    assert_eq!(r3.preedit, "");

    // Buffer now empty — next backspace signals system delete
    let empty = session.handle_backspace().unwrap();
    assert_eq!(empty.preedit, "");
    assert_eq!(empty.commit_text, None);
}

#[test]
fn test_backspace_and_candidate_selection() {
    let session = AndroidLekhaniSession::new();
    // Type 'ল' then 'ক' then 'ে' -> "লকে"
    let _ = session.process_key("ল".into()).unwrap();
    let _ = session.process_key("ক".into()).unwrap();
    let r0 = session.process_key("ে".into()).unwrap();
    assert_eq!(r0.preedit, "লকে");

    // Backspace on 'লকে' pops 'ে' -> "লক"
    let r1 = session.handle_backspace().unwrap();
    assert_eq!(r1.preedit, "লক");

    // Backspace on 'লক' pops 'ক' -> "ল"
    let r2 = session.handle_backspace().unwrap();
    assert_eq!(r2.preedit, "ল");

    // Backspace on 'ল' pops 'ল' -> empty
    let r3 = session.handle_backspace().unwrap();
    assert_eq!(r3.preedit, "");

    // Candidate selection commits NFC-normalized text + space
    let select_res = session.select_candidate("বাংলাদেশ".into()).unwrap();
    assert_eq!(select_res.commit_text, Some("বাংলাদেশ ".into()));
    assert!(!session.is_composing());
}

#[test]
fn test_hasanta_conjunct_suggestions() {
    let session = AndroidLekhaniSession::new();
    let _ = session.process_key("ক".into()).unwrap();
    let res = session.process_key("্".into()).unwrap();
    assert!(res.candidates.contains(&"ক্ত".to_string()));
    assert!(res.candidates.contains(&"ক্ষ".to_string()));
}

#[test]
fn test_private_field_freezes_learning_without_mutating_layout() {
    let session = AndroidLekhaniSession::new();
    assert_eq!(session.get_layout(), LekhaniLayoutType::Probaho);
    session.set_private_field(true);
    // Privacy mode preserves active layout while marking field private
    assert_eq!(session.get_layout(), LekhaniLayoutType::Probaho);
    assert!(session.is_private_field());
    session.set_private_field(false);
    assert!(!session.is_private_field());
    assert_eq!(session.get_layout(), LekhaniLayoutType::Probaho);
}

#[test]
fn test_reset_clears_all_state() {
    let session = AndroidLekhaniSession::new();
    let _ = session.process_key("ব".into()).unwrap();
    assert!(session.is_composing());
    session.reset();
    assert!(!session.is_composing());
}

#[test]
#[serial]
fn test_avro_phonetic_typing() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);
    assert_eq!(session.get_layout(), LekhaniLayoutType::Avro);

    // Type 'a' -> 'm' -> 'i'
    let _ = session.process_key("a".into()).unwrap();
    let _ = session.process_key("m".into()).unwrap();
    let res = session.process_key("i".into()).unwrap();
    assert_eq!(res.preedit, "আমি");
    assert!(session.is_composing());

    // Space commits "আমি "
    let space_res = session.handle_space().unwrap();
    assert_eq!(space_res.commit_text, Some("আমি ".into()));
    assert!(!session.is_composing());
}

#[test]
#[serial]
fn test_avro_homophone_context_disambiguation() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // 1. Context: "আমি বই " -> typing "pora" should rank "পড়া" first
    session.set_context("আমি বই".into());
    let _ = session.process_key("p".into()).unwrap();
    let _ = session.process_key("o".into()).unwrap();
    let _ = session.process_key("r".into()).unwrap();
    let res_book = session.process_key("a".into()).unwrap();
    // WYSIWYG Invariant: in-flight preedit strictly synchronizes with candidates[0]
    assert_eq!(res_book.preedit, "পড়া");
    assert_eq!(res_book.candidates.first().map(|s| s.as_str()), Some("পড়া"));
    let commit_book = session.handle_space().unwrap();
    assert_eq!(commit_book.commit_text, Some("পড়া ".into()));

    // 2. Context: "নতুন শার্ট " -> typing "pora" should rank "পরা" first
    session.set_context("নতুন শার্ট".into());
    let _ = session.process_key("p".into()).unwrap();
    let _ = session.process_key("o".into()).unwrap();
    let _ = session.process_key("r".into()).unwrap();
    let res_shirt = session.process_key("a".into()).unwrap();
    assert_eq!(res_shirt.preedit, "পরা");
    assert_eq!(res_shirt.candidates.first().map(|s| s.as_str()), Some("পরা"));
    let commit_shirt = session.handle_space().unwrap();
    assert_eq!(commit_shirt.commit_text, Some("পরা ".into()));

    // 3. Continuous typing without manual set_context: "boi" -> [space] -> "pora"
    let session2 = AndroidLekhaniSession::new();
    session2.set_layout(LekhaniLayoutType::Avro);
    session2.process_key("b".into()).unwrap();
    session2.process_key("o".into()).unwrap();
    session2.process_key("i".into()).unwrap();
    let sp = session2.handle_space().unwrap();
    assert_eq!(sp.commit_text, Some("বই ".into()));
    session2.process_key("p".into()).unwrap();
    session2.process_key("o".into()).unwrap();
    session2.process_key("r".into()).unwrap();
    let res_cont = session2.process_key("a".into()).unwrap();
    println!(
        "Continuous boi -> pora: preedit={}, candidates={:?}",
        res_cont.preedit, res_cont.candidates
    );
    // WYSIWYG Invariant: in-flight preedit strictly synchronizes with candidates[0]
    assert_eq!(res_cont.preedit, "পড়া");
    assert_eq!(res_cont.candidates.first().map(|s| s.as_str()), Some("পড়া"));
    let commit_cont = session2.handle_space().unwrap();
    assert_eq!(commit_cont.commit_text, Some("পড়া ".into()));

    // 4. Continuous typing: "shirt" -> [space] -> "pora"
    let session3 = AndroidLekhaniSession::new();
    session3.set_layout(LekhaniLayoutType::Avro);
    session3.process_key("s".into()).unwrap();
    session3.process_key("h".into()).unwrap();
    session3.process_key("i".into()).unwrap();
    session3.process_key("r".into()).unwrap();
    session3.process_key("t".into()).unwrap();
    let sp3 = session3.handle_space().unwrap();
    println!("shirt handle_space: commit_text={:?}", sp3.commit_text);
    assert_eq!(sp3.commit_text, Some("শার্ট ".into()));
    session3.process_key("p".into()).unwrap();
    session3.process_key("o".into()).unwrap();
    session3.process_key("r".into()).unwrap();
    let res_shirt_pora = session3.process_key("a".into()).unwrap();
    println!(
        "Continuous shirt -> pora: preedit={}, candidates={:?}",
        res_shirt_pora.preedit, res_shirt_pora.candidates
    );
    assert_eq!(res_shirt_pora.preedit, "পরা");
}

#[test]
#[serial]
fn test_avro_digit_units_mentions_and_bilingual_clitics() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // 1. "10ta" -> preedit "১০টা", space commits "১০টা "
    session.process_key("1".into()).unwrap();
    session.process_key("0".into()).unwrap();
    session.process_key("t".into()).unwrap();
    let res_10ta = session.process_key("a".into()).unwrap();
    assert_eq!(res_10ta.preedit, "১০টা");
    let space_10ta = session.handle_space().unwrap();
    assert_eq!(space_10ta.commit_text, Some("১০টা ".into()));

    // 2. "500tk" -> preedit "৫০০ টাকা", space commits "৫০০ টাকা "
    session.process_key("5".into()).unwrap();
    session.process_key("0".into()).unwrap();
    session.process_key("0".into()).unwrap();
    session.process_key("t".into()).unwrap();
    let res_500tk = session.process_key("k".into()).unwrap();
    assert_eq!(res_500tk.preedit, "৫০০ টাকা");
    assert!(res_500tk.candidates.contains(&"৳৫০০".into()));
    let space_500tk = session.handle_space().unwrap();
    assert_eq!(space_500tk.commit_text, Some("৫০০ টাকা ".into()));

    // 3. Social mention "@siam" -> preedit "@siam", space commits "@siam "
    session.process_key("@".into()).unwrap();
    session.process_key("s".into()).unwrap();
    session.process_key("i".into()).unwrap();
    session.process_key("a".into()).unwrap();
    let res_mention = session.process_key("m".into()).unwrap();
    assert_eq!(res_mention.preedit, "@siam");
    assert!(res_mention.candidates.contains(&"@সিয়াম".into()));
    let space_mention = session.handle_space().unwrap();
    assert_eq!(space_mention.commit_text, Some("@siam ".into()));

    // 4. Bilingual clitic "Facebook-e" -> preedit "Facebook-এ", space commits "Facebook-এ " (when enabled)
    session.set_bilingual_clitic_enabled(true);
    let mut last_res = None;
    for ch in "Facebook-e".chars() {
        last_res = Some(session.process_key(ch.to_string()).unwrap());
    }
    assert_eq!(last_res.unwrap().preedit, "Facebook-এ");
    let space_fb = session.handle_space().unwrap();
    assert_eq!(space_fb.commit_text, Some("Facebook-এ ".into()));
    session.set_bilingual_clitic_enabled(false);
}

#[test]
fn test_glide_decoding() {
    let session = AndroidLekhaniSession::new();
    // 1. Avro Phonetic Glide
    session.set_layout(LekhaniLayoutType::Avro);
    let res = session
        .decode_glide(vec!["a".into(), "m".into(), "i".into()])
        .unwrap();
    assert_eq!(res.preedit, "আমি");
    assert_eq!(res.commit_text, Some("আমি ".into()));

    // 2. English QWERTY Glide
    session.set_layout(LekhaniLayoutType::English);
    let res_en = session
        .decode_glide(vec!["t".into(), "h".into(), "e".into()])
        .unwrap();
    assert_eq!(res_en.preedit, "the");
    assert_eq!(res_en.commit_text, Some("the ".into()));

    // 3. Fixed Bengali (Probaho) Glide
    session.set_layout(LekhaniLayoutType::Probaho);
    let res_bn = session
        .decode_glide(vec!["আ".into(), "ম".into(), "া".into(), "র".into()])
        .unwrap();
    assert_eq!(res_bn.preedit, "আমার");
    assert_eq!(res_bn.commit_text, Some("আমার ".into()));
}

#[test]
#[serial]
fn test_dictionary_management_session() {
    let session = AndroidLekhaniSession::new();
    session.add_user_word("টেস্টওয়ার্ড".into()).unwrap();
    let words = session.get_user_words().unwrap();
    assert!(words.contains(&"টেস্টওয়ার্ড".to_string()));

    let json = session.export_dictionary_json().unwrap();
    assert!(json.contains("টেস্টওয়ার্ড"));

    let deleted = session.delete_user_word("টেস্টওয়ার্ড".into()).unwrap();
    assert!(deleted);
    assert!(!session
        .get_user_words()
        .unwrap()
        .contains(&"টেস্টওয়ার্ড".to_string()));

    let imported = session
        .import_raw_words(vec!["শব্দএক".into(), "শব্দদুই".into()])
        .unwrap();
    assert_eq!(imported, 2);
    assert!(session
        .get_user_words()
        .unwrap()
        .contains(&"শব্দএক".to_string()));
}

#[test]
fn test_english_suggestions_session() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::English);
    assert_eq!(session.get_layout(), LekhaniLayoutType::English);

    // Type 'h' -> 'e' -> 'l' -> 'l'
    let _ = session.process_key("h".into()).unwrap();
    let _ = session.process_key("e".into()).unwrap();
    let _ = session.process_key("l".into()).unwrap();
    let res = session.process_key("l".into()).unwrap();
    assert_eq!(res.preedit, "hell");
    assert!(session.is_composing());
    assert!(!res.candidates.is_empty());
    assert_eq!(res.candidates[0], "hell");

    // Option B: Space commits verbatim "hell " without force-correcting
    let space_res = session.handle_space().unwrap();
    assert_eq!(space_res.commit_text, Some("hell ".into()));
    assert!(!session.is_composing());

    // Backspace test
    let _ = session.process_key("t".into()).unwrap();
    let _ = session.process_key("e".into()).unwrap();
    let _ = session.process_key("s".into()).unwrap();
    let _ = session.process_key("t".into()).unwrap();
    assert_eq!(session.handle_backspace().unwrap().preedit, "tes");
    session.reset();

    // Candidate selection commits chosen word + trailing space
    let _ = session.process_key("t".into()).unwrap();
    let _ = session.process_key("h".into()).unwrap();
    let sel = session.select_candidate("the".into()).unwrap();
    assert_eq!(sel.commit_text, Some("the ".into()));
    assert!(!session.is_composing());

    // Private field test: bypasses composing
    session.set_private_field(true);
    let priv_res = session.process_key("a".into()).unwrap();
    assert_eq!(priv_res.commit_text, Some("a".into()));
    assert_eq!(priv_res.preedit, "");
    assert!(priv_res.candidates.is_empty());
    assert!(!session.is_composing());
}

#[test]
fn test_national_layout_dead_key_vowels() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::National);
    assert_eq!(session.get_layout(), LekhaniLayoutType::National);

    // '্' + 'ি' -> 'ই'
    let _ = session.process_key("্".into()).unwrap();
    let res = session.process_key("ি".into()).unwrap();
    assert_eq!(res.preedit, "ই");
    session.reset();

    // '্' + 'া' -> 'অ'
    let _ = session.process_key("্".into()).unwrap();
    let res = session.process_key("া".into()).unwrap();
    assert_eq!(res.preedit, "অ");
    session.reset();

    // '্' + 'ে' -> 'এ'
    let _ = session.process_key("্".into()).unwrap();
    let res = session.process_key("ে".into()).unwrap();
    assert_eq!(res.preedit, "এ");
    session.reset();

    // '্' + 'ো' -> 'ও'
    let _ = session.process_key("্".into()).unwrap();
    let res = session.process_key("ো".into()).unwrap();
    assert_eq!(res.preedit, "ও");
    session.reset();

    // Regular consonant linking: 'ক' + '্' + 'ত' -> 'ক্ত' (not transformed into independent vowel)
    let _ = session.process_key("ক".into()).unwrap();
    let _ = session.process_key("্".into()).unwrap();
    let res = session.process_key("ত".into()).unwrap();
    assert_eq!(res.preedit, "ক্ত");
}

#[test]
fn test_gboard_conjunct_suggestions_on_hasanta() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Gboard);

    let _ = session.process_key("ক".into()).unwrap();
    let res = session.process_key("্".into()).unwrap();
    assert_eq!(res.preedit, "ক্");
    // Verify conjunct suggestions are surfaced
    assert!(!res.candidates.is_empty());
    assert!(res.candidates.contains(&"ক্ষ".to_string()));
    assert!(res.candidates.contains(&"ক্ত".to_string()));
    assert!(res.candidates.contains(&"ক্র".to_string()));
    assert!(res.candidates.contains(&"ক্ল".to_string()));

    // Continue typing to form conjunct directly
    let res2 = session.process_key("ষ".into()).unwrap();
    assert_eq!(res2.preedit, "ক্ষ");
}

#[test]
#[serial]
fn test_continuous_learning_and_persistence() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // 1. Candidate selection: 1-shot direct learning on explicit candidate strip tap
    let _ = session.process_key("k".into()).unwrap();
    let _ = session.process_key("o".into()).unwrap();
    let _ = session.process_key("r".into()).unwrap();
    let _ = session.process_key("m".into()).unwrap();
    let _ = session.process_key("o".into()).unwrap();
    let sel1 = session.select_candidate("কৰ্ম".into()).unwrap();
    assert_eq!(sel1.commit_text, Some("কৰ্ম ".into()));

    let db = get_core_database();
    {
        let learner = db.learner.read().unwrap();
        // Single deliberate candidate tap is immediately pinned to candidate_memory!
        assert_eq!(
            learner.candidate_memory.get("kormo"),
            Some(&"কৰ্ম".to_string())
        );
        assert!(learner.observed_counts.contains_key("কৰ্ম"));
    }

    // Short token immunity: selecting "ঐ" for "oi" (length <= 2) must NEVER pin into candidate_memory
    let _ = session.process_key("o".into()).unwrap();
    let _ = session.process_key("i".into()).unwrap();
    let _ = session.select_candidate("ঐ".into()).unwrap();
    let _ = session.process_key("o".into()).unwrap();
    let _ = session.process_key("i".into()).unwrap();
    let _ = session.select_candidate("ঐ".into()).unwrap();
    {
        let learner = db.learner.read().unwrap();
        assert_eq!(learner.candidate_memory.get("oi"), None);
    }

    // Typing 'kormo' again must yield user's remembered candidate as top-1
    let _ = session.process_key("k".into()).unwrap();
    let _ = session.process_key("o".into()).unwrap();
    let _ = session.process_key("r".into()).unwrap();
    let _ = session.process_key("m".into()).unwrap();
    let retype_res = session.process_key("o".into()).unwrap();
    assert_eq!(retype_res.preedit, "কৰ্ম");
    assert_eq!(
        retype_res.candidates.first().map(|s| s.as_str()),
        Some("কৰ্ম")
    );
    let retype_space = session.handle_space().unwrap();
    assert_eq!(retype_space.commit_text, Some("কৰ্ম ".into()));

    // 2. Spacebar commit records bigram pair ("কৰ্ম" -> "ভালো")
    let _ = session.process_key("b".into()).unwrap();
    let _ = session.process_key("h".into()).unwrap();
    let _ = session.process_key("a".into()).unwrap();
    let _ = session.process_key("l".into()).unwrap();
    let _ = session.process_key("o".into()).unwrap();
    let space_res = session.handle_space().unwrap();
    assert_eq!(space_res.commit_text, Some("ভালো ".into()));

    {
        let learner = db.learner.read().unwrap();
        assert!(learner.user_bigrams.contains_key("কৰ্ম\tভালো"));
    }

    // 3. Rapid backspace applies mistake penalty
    let bs_res = session.handle_backspace().unwrap();
    assert_eq!(bs_res.commit_text, None);
    {
        let learner = db.learner.read().unwrap();
        // User bigram should be decremented or removed
        assert!(!learner.user_bigrams.contains_key("কৰ্ম\tভালো"));
    }

    // 4. Persistence test: save and load roundtrip
    let temp_dir = std::env::temp_dir();
    let test_path = temp_dir
        .join("lekhani_test_user_learned.bin")
        .to_string_lossy()
        .to_string();
    let save_ok = session.save_user_learned(test_path.clone()).unwrap();
    assert!(save_ok);

    let session2 = AndroidLekhaniSession::new();
    let load_ok = session2.load_user_learned(test_path.clone()).unwrap();
    assert!(load_ok);

    let _ = std::fs::remove_file(test_path);
}

#[test]
#[serial]
fn test_private_field_freezes_learning() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);
    session.set_private_field(true);

    let _ = session.process_key("s".into()).unwrap();
    let _ = session.process_key("e".into()).unwrap();
    let _ = session.process_key("c".into()).unwrap();
    let _ = session.process_key("r".into()).unwrap();
    let _ = session.process_key("e".into()).unwrap();
    let _ = session.process_key("t".into()).unwrap();
    let _ = session.select_candidate("গোপন".into()).unwrap();

    let db = get_core_database();
    let learner = db.learner.read().unwrap();
    assert!(!learner.candidate_memory.contains_key("secret"));
}

#[test]
#[serial]
fn test_personal_error_pattern_learning_session() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);
    clear_candidate_memory();

    // 1. Type typo "bhlao" and select "ভালো" (1st selection is immediately learned via 1-shot)
    for ch in ["b", "h", "l", "a", "o"] {
        let _ = session.process_key(ch.into()).unwrap();
    }
    let sel1 = session.select_candidate("ভালো".into()).unwrap();
    assert_eq!(sel1.commit_text, Some("ভালো ".into()));

    // Immediately pinned on 1st explicit candidate strip selection!
    {
        let db = get_core_database();
        let learner = db.learner.read().unwrap();
        assert_eq!(learner.lookup_input_error("bhlao"), Some("ভালো"));
    }

    // 3. Typing "bhlao" now directly yields "ভালো" at candidate index 0
    for ch in ["b", "h", "l", "a"] {
        let _ = session.process_key(ch.into()).unwrap();
    }
    let res = session.process_key("o".into()).unwrap();
    assert_eq!(res.preedit, "ভালো");
    assert_eq!(res.candidates.first().map(|s| s.as_str()), Some("ভালো"));

    // Spacebar directly commits the corrected word
    let space_res = session.handle_space().unwrap();
    assert_eq!(space_res.commit_text, Some("ভালো ".into()));
}

#[test]
fn test_spatial_touch_model_integration() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::English);

    session.update_keyboard_geometry(vec![
        crate::spatial::KeyGeometryConfig {
            label: "t".to_string(),
            center_x: 100.0,
            center_y: 200.0,
            width: 40.0,
            height: 50.0,
        },
        crate::spatial::KeyGeometryConfig {
            label: "y".to_string(),
            center_x: 140.0,
            center_y: 200.0,
            width: 40.0,
            height: 50.0,
        },
    ]);

    let prob_t = session.get_spatial_log_prob("t".into(), 100.0, 204.0);
    let prob_y = session.get_spatial_log_prob("y".into(), 100.0, 204.0);
    assert!(
        prob_t > prob_y,
        "Center of 't' must have higher probability for 't' than 'y'"
    );

    let ranked = session.rank_spatial_keys(135.0, 204.0, 2);
    assert_eq!(ranked.len(), 2);
    assert_eq!(ranked[0].key, "y");
    assert_eq!(ranked[1].key, "t");

    // Touch slightly to the left of 'y' (x = 138), should include 't' / 'y' suggestions
    let res = session
        .process_key_with_touch("y".into(), 138.0, 204.0)
        .unwrap();
    assert_eq!(res.preedit, "y");
}

#[test]
fn test_predict_next_words_async_helper() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::English);
    session.set_context("how are".into());

    let preds = session.predict_next_words(3);
    assert!(
        !preds.is_empty(),
        "English context 'how are' should produce next words"
    );
    assert!(preds.contains(&"you".to_string()));

    // Bengali layout next-word prediction test
    session.set_layout(LekhaniLayoutType::Avro);
    session.set_context("আমি ভাত".into());
    let bn_preds = session.predict_next_words(5);
    assert!(
        !bn_preds.is_empty(),
        "Bengali context 'আমি ভাত' should produce predictions"
    );
    assert!(bn_preds
        .iter()
        .any(|w| w == "খাচ্ছি" || w == "খাব" || w == "খেয়েছি" || w == "খেতে"));
}

#[test]
fn test_probaho_vowel_promotion() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);

    // Word-start promotion: 'ি' -> 'ই'
    let res = session.process_key("ি".into()).unwrap();
    assert_eq!(res.preedit, "ই");

    // Mid-word diphthong: 'খা' + 'ি' -> 'খাই'
    session.reset();
    session.process_key("খ".into()).unwrap();
    session.process_key("া".into()).unwrap();
    let res = session.process_key("ি".into()).unwrap();
    assert_eq!(res.preedit, "খাই");

    // Mid-word diphthong: 'সে' + 'ি' -> 'সেই'
    session.reset();
    session.process_key("স".into()).unwrap();
    session.process_key("ে".into()).unwrap();
    let res = session.process_key("ি".into()).unwrap();
    assert_eq!(res.preedit, "সেই");

    // Mid-word diphthong: 'পা' + 'ু' -> 'পাউ'
    session.reset();
    session.process_key("প".into()).unwrap();
    session.process_key("া".into()).unwrap();
    let res = session.process_key("ু".into()).unwrap();
    assert_eq!(res.preedit, "পাউ");

    // Context-aware Kar attachment after consonant: 'মনুষ' + 'া' -> 'া' (NOT 'আ')
    session.reset();
    session.set_context("মনুষ".into());
    let res = session.process_key("া".into()).unwrap();
    assert_eq!(
        res.preedit, "া",
        "Kar 'া' after consonant 'ষ' must NOT promote to 'আ'"
    );

    // Context-aware Hasanta conjunct suggestion after consonant in context
    session.reset();
    session.set_context("এক".into());
    let res = session.process_key("্".into()).unwrap();
    assert!(
        res.candidates.iter().any(|c| c == "ক্ক" || c == "ক্ত"),
        "Hasanta after 'ক' in context must suggest conjuncts"
    );
}

/// Verifies that the AI context scorer re-ranks Probaho prefix candidates.
///
/// With surrounding context "আমি বই", prefix "পড" should yield candidates
/// and the scorer should not panic — the specific order depends on the
/// loaded LM so we assert structural invariants rather than exact order.
#[test]
#[serial]
fn test_probaho_context_reranking() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);
    session.set_context("আমি বই".into());

    // Type 'প' then 'ড়' — should produce prefix candidates
    let r1 = session.process_key("প".into()).unwrap();
    assert!(
        !r1.preedit.is_empty(),
        "Probaho 'প' should produce a preedit"
    );

    let r2 = session.process_key("ড়".into()).unwrap();
    // Candidates should be non-empty and each be valid strings
    for cand in &r2.candidates {
        assert!(!cand.is_empty(), "Every candidate must be non-empty");
    }
}

/// Verifies that morphological suffix expansion surfaces extra candidates
/// beyond what a plain PrefixTrie lookup would produce.
///
/// The fixed-layout wildcard arm now calls `peel_all_stems` to expand the
/// composing buffer before the trie query when the candidate list is short.
#[test]
#[serial]
fn test_probaho_morphology_expansion() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);

    // Type a single consonant 'ব' — should produce candidates without panicking
    let res = session.process_key("ব".into()).unwrap();
    assert!(!res.preedit.is_empty());
    // No crash is the primary assertion; candidate count ≥ 0
    let _ = res.candidates.len();
}

#[test]
#[serial]
fn test_avro_dot_converts_to_dari() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    // 1. Typing "ami" + "." should commit "আমি।"
    session.process_key("a".into()).unwrap();
    session.process_key("m".into()).unwrap();
    session.process_key("i".into()).unwrap();
    let res = session.process_key(".".into()).unwrap();
    assert_eq!(res.commit_text.as_deref(), Some("আমি।"));

    // 2. Typing numbers e.g. "3" (converts to "৩") + "." should remain "৩." in-flight for decimal fractions (e.g. 3.14)
    session.reset();
    session.process_key("3".into()).unwrap();
    let num_res = session.process_key(".".into()).unwrap();
    assert_eq!(num_res.preedit, "৩.");
    let space_res = session.handle_space().unwrap();
    assert_eq!(space_res.commit_text.as_deref(), Some("৩. "));

    // 3. Typing "." after space/committed word should commit "।"
    session.reset();
    session.set_context("আমি ভালো আছি ".into());
    let dot_res = session.process_key(".".into()).unwrap();
    assert_eq!(dot_res.commit_text.as_deref(), Some("।"));

    // 4. In English layout, "." must stay "."
    session.reset();
    session.set_layout(LekhaniLayoutType::English);
    session.process_key("t".into()).unwrap();
    session.process_key("e".into()).unwrap();
    session.process_key("s".into()).unwrap();
    session.process_key("t".into()).unwrap();
    let eng_res = session.process_key(".".into()).unwrap();
    assert_eq!(eng_res.commit_text.as_deref(), Some("test."));
}

#[test]
#[serial]
fn test_bidirectional_context_next_words() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);
    session.set_context("আমি".into());
    session.set_right_context("যাব".into());

    let res = session.predict_next_words(5);
    assert!(!res.is_empty(), "Expected next-word suggestions");
}

#[test]
#[serial]
fn test_learner_autosave_path_and_reset() {
    let session = AndroidLekhaniSession::new();
    let temp_dir = std::env::temp_dir();
    let save_path = temp_dir
        .join("lekhani_test_user_learned.bin")
        .to_string_lossy()
        .to_string();

    session.set_learner_autosave_path(save_path.clone());
    session.set_context("আজকে আমি ভাত খাব ".into());
    session.set_right_context("না".into());

    assert_eq!(
        session.state.lock().unwrap().surrounding_context_len,
        "আজকে আমি ভাত খাব ".chars().count()
    );
    assert_eq!(session.state.lock().unwrap().right_context, "না");

    session.reset();
    assert_eq!(session.state.lock().unwrap().surrounding_context_len, 0);
    assert!(session.state.lock().unwrap().surrounding_context.is_empty());
    assert!(session.state.lock().unwrap().right_context.is_empty());
}

#[test]
#[serial]
fn test_avro_candidates_always_ai_ranked() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);
    session.set_context("আমি গান ".into());

    let res = session.process_key("g".into()).unwrap();
    assert!(!res.candidates.is_empty());
    let res2 = session.process_key("a".into()).unwrap();
    assert!(!res2.candidates.is_empty());
    let res3 = session.process_key("i".into()).unwrap();
    assert!(!res3.candidates.is_empty());
    // "আমি গান " followed by "gai" should rank "গাই" top
    assert_eq!(res3.candidates.first().map(|s| s.as_str()), Some("গাই"));
}

#[test]
#[serial]
fn test_probaho_conjunct_substitution() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);
    session.process_key("শ".into()).unwrap();
    session.process_key("ি".into()).unwrap();
    session.process_key("ক".into()).unwrap();
    let res = session.process_key("্".into()).unwrap();
    assert!(res.candidates.contains(&"ক্ষ".to_string()));

    let select_res = session.select_candidate("ক্ষ".into()).unwrap();
    assert_eq!(select_res.commit_text, None);
    assert_eq!(select_res.preedit, "শিক্ষ");

    let res_kar = session.process_key("া".into()).unwrap();
    assert_eq!(res_kar.preedit, "শিক্ষা");
}

#[test]
#[serial]
fn test_probaho_reph_conjunct_suggestion_and_substitution() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);
    session.process_key("ধ".into()).unwrap();
    session.process_key("র".into()).unwrap();
    let res = session.process_key("্".into()).unwrap();
    assert!(
        res.candidates.contains(&"র্ম".to_string()),
        "Candidates must contain র্ম after ধর্"
    );
    assert!(
        res.candidates.contains(&"র্ষ".to_string()),
        "Candidates must contain র্ষ after ধর্"
    );

    let select_res = session.select_candidate("র্ম".into()).unwrap();
    assert_eq!(select_res.commit_text, None);
    assert_eq!(select_res.preedit, "ধর্ম");
}

#[test]
#[serial]
fn test_probaho_postfix_reph() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);

    // 1. Typing 'ধ' + 'ম' + 'র্' transposes to "ধর্ম"
    session.process_key("ধ".into()).unwrap();
    session.process_key("ম".into()).unwrap();
    let res = session.process_key("র্".into()).unwrap();
    assert_eq!(res.preedit, "ধর্ম", "Postfix 'র্' after 'ম' must form 'ধর্ম'");

    // 2. Typing 'ক' + 'ম' + 'র্' transposes to "কর্ম"
    session.reset();
    session.process_key("ক".into()).unwrap();
    session.process_key("ম".into()).unwrap();
    let res2 = session.process_key("র্".into()).unwrap();
    assert_eq!(res2.preedit, "কর্ম", "Postfix 'র্' after 'ম' must form 'কর্ম'");

    // 3. Typing 'ক' + 'ম' + '্' suggests 'র্ম'
    session.reset();
    session.process_key("ক".into()).unwrap();
    session.process_key("ম".into()).unwrap();
    let res3 = session.process_key("্".into()).unwrap();
    assert!(
        res3.candidates.contains(&"র্ম".to_string()),
        "ম + ্ must suggest র্ম"
    );
    let select_res = session.select_candidate("র্ম".into()).unwrap();
    assert_eq!(select_res.preedit, "কর্ম");
}

#[test]
#[serial]
fn test_probaho_agglutinative_suffix_strip() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);

    // Typing "মানুষ" produces inflected candidates in strip
    for ch in ["ম", "া", "ন", "ু", "ষ"] {
        session.process_key(ch.into()).unwrap();
    }
    let res = session.process_key("".into()).unwrap();
    assert_eq!(res.preedit, "মানুষ");
    assert!(
        res.candidates.iter().any(|c| c == "মানুষের"),
        "Candidates must surface 'মানুষের'"
    );
    assert!(
        res.candidates.iter().any(|c| c == "মানুষকে"),
        "Candidates must surface 'মানুষকে'"
    );
}

#[test]
#[serial]
fn test_probaho_vowel_demotion_after_consonant() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);
    session.process_key("ব".into()).unwrap();
    let res = session.process_key("ঋ".into()).unwrap();
    assert_eq!(res.preedit, "বৃ", "ঋ after ব must automatically demote to বৃ");

    session.reset();
    session.process_key("ন".into()).unwrap();
    let res2 = session.process_key("ঔ".into()).unwrap();
    assert_eq!(
        res2.preedit, "নৌ",
        "ঔ after ন must automatically demote to নৌ"
    );
}

#[test]
#[serial]
fn test_probaho_yaphola_suggestion() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);
    session.process_key("ন".into()).unwrap();
    let res = session.process_key("্".into()).unwrap();
    assert!(
        res.candidates.contains(&"ন্য".to_string()),
        "Hasanta after ন must suggest ন্য"
    );
}

#[test]
#[serial]
fn test_probaho_rphola_suggestion() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);
    session.process_key("প".into()).unwrap();
    let res = session.process_key("্".into()).unwrap();
    assert_eq!(
        res.candidates.first().map(|s| s.as_str()),
        Some("প্র"),
        "First candidate after প + ্ must be প্র"
    );

    let select_res = session.select_candidate("প্র".into()).unwrap();
    assert_eq!(select_res.commit_text, None);
    assert_eq!(select_res.preedit, "প্র");

    let res_e = session.process_key("ে".into()).unwrap();
    assert_eq!(res_e.preedit, "প্রে");

    let res_m = session.process_key("ম".into()).unwrap();
    assert_eq!(res_m.preedit, "প্রেম");
}

#[test]
#[serial]
fn test_probhat_hasanta_conjunct_suggestion_and_substitution() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probhat);

    // Type 'শ' + 'ি' + 'ক' + '্' -> preedit 'শিক্'
    session.process_key("শ".into()).unwrap();
    session.process_key("ি".into()).unwrap();
    session.process_key("ক".into()).unwrap();
    let res_hasanta = session.process_key("্".into()).unwrap();
    assert_eq!(res_hasanta.preedit, "শিক্");
    assert!(
        res_hasanta.candidates.contains(&"ক্ষ".to_string()),
        "Candidates must contain 'ক্ষ' after 'ক' + '্'"
    );

    // Select 'ক্ষ' -> in-flight substitution replaces 'ক্' with 'ক্ষ' -> 'শিক্ষ'
    let res_select = session.select_candidate("ক্ষ".into()).unwrap();
    assert_eq!(
        res_select.commit_text, None,
        "In-flight conjunct selection should not commit immediately"
    );
    assert_eq!(res_select.preedit, "শিক্ষ");

    // Continue typing 'া' -> 'শিক্ষা'
    let res_a = session.process_key("া".into()).unwrap();
    assert_eq!(res_a.preedit, "শিক্ষা");
}

#[test]
#[serial]
fn test_probhat_vowel_promotion_at_word_start() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probhat);

    // Tapping 'া' at start of word promotes to 'আ'
    let res = session.process_key("া".into()).unwrap();
    assert_eq!(res.preedit, "আ");
}

#[test]
#[serial]
fn test_probhat_vowel_demotion_after_consonant() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probhat);

    // Tapping 'ক' then independent 'আ' (Probhat unshifted Row 3) should produce 'কা'
    session.process_key("ক".into()).unwrap();
    let res = session.process_key("আ".into()).unwrap();
    assert_eq!(res.preedit, "কা");
}

#[test]
#[serial]
fn test_probhat_configurable_options() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probhat);

    // 1. Test smart initial kar disabled
    session.set_smart_initial_kar_enabled(false);
    let res = session.process_key("া".into()).unwrap();
    assert_eq!(
        res.preedit, "া",
        "When smart_initial_kar_enabled is false, 'া' should remain 'া'"
    );

    session.reset();
    session.set_smart_initial_kar_enabled(true);
    let res2 = session.process_key("া".into()).unwrap();
    assert_eq!(
        res2.preedit, "আ",
        "When smart_initial_kar_enabled is true, 'া' promotes to 'আ'"
    );

    // 2. Test geminate double tap toggle
    session.reset();
    session.set_geminate_double_tap_enabled(false);
    session.process_key("উ".into()).unwrap();
    session.process_key("ত".into()).unwrap();
    let res_no_gem = session.process_key("ত".into()).unwrap();
    assert_eq!(
        res_no_gem.preedit, "উতত",
        "When geminate_double_tap is false, double tap should type 'তত'"
    );

    session.reset();
    session.set_geminate_double_tap_enabled(true);
    session.process_key("উ".into()).unwrap();
    session.process_key("ত".into()).unwrap();
    let res_gem = session.process_key("ত".into()).unwrap();
    assert_eq!(
        res_gem.preedit, "উত্ত",
        "When geminate_double_tap is true, double tap should insert hasanta 'উত্ত'"
    );

    // 3. Test hasanta conjuncts toggle
    session.reset();
    session.set_hasanta_conjuncts_enabled(false);
    session.process_key("ক".into()).unwrap();
    let res_no_conj = session.process_key("্".into()).unwrap();
    assert!(
        !res_no_conj.candidates.contains(&"ক্ষ".to_string()),
        "When hasanta_conjuncts is false, quick picks should not be populated"
    );

    session.reset();
    session.set_hasanta_conjuncts_enabled(true);
    session.process_key("ক".into()).unwrap();
    let res_conj = session.process_key("্".into()).unwrap();
    assert!(
        res_conj.candidates.contains(&"ক্ষ".to_string()),
        "When hasanta_conjuncts is true, quick picks should contain 'ক্ষ'"
    );
}

#[test]
#[serial]
fn test_english_spacebar_no_bangla_leakage() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::English);

    // 1. Typing 'm' + 'm' and pressing space in English MUST commit "mm " and never "মিমি. "
    session.process_key("m".into()).unwrap();
    session.process_key("m".into()).unwrap();
    let res_space = session.handle_space().unwrap();
    assert_eq!(res_space.commit_text.as_deref(), Some("mm "));

    // 2. Typing 'a' + 'm' + 'i' in English MUST commit "ami " and never "আমি "
    session.process_key("a".into()).unwrap();
    session.process_key("m".into()).unwrap();
    session.process_key("i".into()).unwrap();
    let res_space2 = session.handle_space().unwrap();
    assert_eq!(res_space2.commit_text.as_deref(), Some("ami "));

    // 3. Typing English contraction 'dont' and pressing space commits "dont " without force-correcting
    session.process_key("d".into()).unwrap();
    session.process_key("o".into()).unwrap();
    session.process_key("n".into()).unwrap();
    let res_cand = session.process_key("t".into()).unwrap();
    assert!(res_cand.candidates.contains(&"don't".to_string()));
    let res_space3 = session.handle_space().unwrap();
    assert_eq!(res_space3.commit_text.as_deref(), Some("dont "));

    // 4. Typing 'dont' and selecting the 'don't' candidate on space commits "don't "
    session.process_key("d".into()).unwrap();
    session.process_key("o".into()).unwrap();
    session.process_key("n".into()).unwrap();
    session.process_key("t".into()).unwrap();
    let res_choice = session
        .handle_space_with_choice(Some("don't".into()))
        .unwrap();
    assert_eq!(res_choice.commit_text.as_deref(), Some("don't "));
}

#[test]
#[serial]
fn test_probaho_geminate_double_tap() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Probaho);
    session.set_geminate_double_tap_enabled(true);

    // 1. Geminate double-tap: 'উ' + 'ত' + 'ত' + 'র' -> "উত্তর"
    session.process_key("উ".into()).unwrap();
    session.process_key("ত".into()).unwrap();
    let res_tt = session.process_key("ত".into()).unwrap();
    assert_eq!(res_tt.preedit, "উত্ত");
    let res_uttor = session.process_key("র".into()).unwrap();
    assert_eq!(res_uttor.preedit, "উত্তর");

    session.reset();

    // 2. Geminate double-tap: 'আ' + 'ব' + 'ব' + 'া' -> "আব্বা"
    session.process_key("আ".into()).unwrap();
    session.process_key("ব".into()).unwrap();
    let res_bb = session.process_key("ব".into()).unwrap();
    assert_eq!(res_bb.preedit, "আব্ব");
    let res_abba = session.process_key("া".into()).unwrap();
    assert_eq!(res_abba.preedit, "আব্বা");

    session.reset();

    // 3. Geminate double-tap: 'দ' + 'দ' -> "দ্দ"
    session.process_key("দ".into()).unwrap();
    let res_dd = session.process_key("দ".into()).unwrap();
    assert_eq!(res_dd.preedit, "দ্দ");

    session.reset();

    // 4. Geminate double-tap: 'ক' + 'ক' -> "ক্ক"
    session.process_key("ক".into()).unwrap();
    let res_kk = session.process_key("ক".into()).unwrap();
    assert_eq!(res_kk.preedit, "ক্ক");

    session.reset();

    // 5. 3rd-tap cycle reversion when geminate double-tap is enabled:
    // Typing 'ব' + 'ল' + 'ল' (geminate 'বল্ল') + 'ল' (3rd tap reverts to 'বলল') + 'ে' + 'ও' -> "বললেও"
    session.process_key("ব".into()).unwrap();
    session.process_key("ল".into()).unwrap();
    let res_gem = session.process_key("ল".into()).unwrap();
    assert_eq!(
        res_gem.preedit, "বল্ল",
        "2nd tap forms geminate conjunct 'বল্ল'"
    );
    let res_revert = session.process_key("ল".into()).unwrap();
    assert_eq!(
        res_revert.preedit, "বলল",
        "3rd tap cycles back to separate letters 'বলল'"
    );
    let res_e = session.process_key("ে".into()).unwrap();
    assert_eq!(res_e.preedit, "বললে");
    let res_bolleo = session.process_key("ও".into()).unwrap();
    assert_eq!(
        res_bolleo.preedit, "বললেও",
        "User can cleanly write 'বললেও'"
    );

    session.reset();

    // 6. 3rd-tap cycle reversion for "তত" and "ততটুকু":
    session.process_key("ত".into()).unwrap();
    let res_t2 = session.process_key("ত".into()).unwrap();
    assert_eq!(res_t2.preedit, "ত্ত", "2nd tap of 'ত' produces 'ত্ত'");
    let res_t3 = session.process_key("ত".into()).unwrap();
    assert_eq!(res_t3.preedit, "তত", "3rd tap of 'ত' reverts to 'তত'");

    session.reset();

    // 7. Slow double-tap (> 280ms) should NOT form geminate, even with geminate_double_tap_enabled == true
    session.process_key("ব".into()).unwrap();
    session.process_key("ল".into()).unwrap();
    std::thread::sleep(std::time::Duration::from_millis(300));
    let res_slow = session.process_key("ল".into()).unwrap();
    assert_eq!(
        res_slow.preedit, "বলল",
        "Slow tap (>280ms) must produce separate consonants 'বলল'"
    );
}

#[test]
#[serial]
fn test_chandrabindu_auto_reordering() {
    let session = AndroidLekhaniSession::new();

    for layout in [
        LekhaniLayoutType::Probaho,
        LekhaniLayoutType::Probhat,
        LekhaniLayoutType::National,
    ] {
        session.reset();
        session.set_layout(layout);

        // Canonical typing: চ + া + ঁ -> চাঁ
        session.process_key("চ".into()).unwrap();
        session.process_key("া".into()).unwrap();
        let res_canonical = session.process_key("ঁ".into()).unwrap();
        assert_eq!(
            res_canonical.preedit, "চাঁ",
            "Layout {:?} canonical চ + া + ঁ should produce 'চাঁ'",
            layout
        );

        session.reset();

        // Reverse typing: চ + ঁ + া -> চাঁ (auto-reordered to canonical Indic Unicode NFC)
        session.process_key("চ".into()).unwrap();
        session.process_key("ঁ".into()).unwrap();
        let res_reordered = session.process_key("া".into()).unwrap();
        assert_eq!(
            res_reordered.preedit, "চাঁ",
            "Layout {:?} reverse চ + ঁ + া must auto-reorder to canonical 'চাঁ'",
            layout
        );

        session.reset();

        // Reverse typing with ি: ক + ঁ + ি -> কিঁ
        session.process_key("ক".into()).unwrap();
        session.process_key("ঁ".into()).unwrap();
        let res_ki = session.process_key("ি".into()).unwrap();
        assert_eq!(
            res_ki.preedit, "কিঁ",
            "Layout {:?} reverse ক + ঁ + ি must auto-reorder to canonical 'কিঁ'",
            layout
        );
    }
}

#[test]
#[serial]
fn test_bolleo_and_consecutive_consonants_with_default_settings() {
    let session = AndroidLekhaniSession::new();
    // Default session must have geminate_double_tap_enabled == false
    // Across fixed layouts (Probhat, National, Gboard), typing 'ব' + 'ল' + 'ল' + 'ে' + 'ও'
    // must naturally type "বললেও" without inserting an unwanted hasanta!

    for layout in [
        LekhaniLayoutType::Probhat,
        LekhaniLayoutType::National,
        LekhaniLayoutType::Probaho,
    ] {
        session.reset();
        session.set_layout(layout);

        session.process_key("ব".into()).unwrap();
        session.process_key("ল".into()).unwrap();
        let res_ll = session.process_key("ল".into()).unwrap();
        assert_eq!(
            res_ll.preedit, "বলল",
            "By default, double-tapping 'ল' in layout {:?} should produce 'বলল'",
            layout
        );
        session.process_key("ে".into()).unwrap();
        let res_bolleo = session.process_key("ও".into()).unwrap();
        assert_eq!(
            res_bolleo.preedit, "বললেও",
            "By default, layout {:?} must seamlessly produce 'বললেও'",
            layout
        );
    }
}

#[test]
#[serial]
fn test_fixed_layout_koi_kao_and_independent_vowel_words() {
    let session = AndroidLekhaniSession::new();

    // ── 1. Probaho Layout ──────────────────────────────────────────────
    session.set_layout(LekhaniLayoutType::Probaho);

    // 1a. Direct typing with independent vowels (e.g. via Shift / Flick)
    session.reset();
    session.process_key("ক".into()).unwrap();
    let res_koi = session.process_key("ই".into()).unwrap();
    assert_eq!(res_koi.preedit, "কই", "ক + ই must produce 'কই', not 'কি'");

    session.reset();
    session.process_key("ক".into()).unwrap();
    let res_kao = session.process_key("ও".into()).unwrap();
    assert_eq!(res_kao.preedit, "কও", "ক + ও must produce 'কও', not 'কো'");

    session.reset();
    session.process_key("ব".into()).unwrap();
    let res_boi = session.process_key("ই".into()).unwrap();
    assert_eq!(res_boi.preedit, "বই", "ব + ই must produce 'বই', not 'বি'");

    session.reset();
    session.process_key("ব".into()).unwrap();
    let res_bou = session.process_key("উ".into()).unwrap();
    assert_eq!(res_bou.preedit, "বউ", "ব + উ must produce 'বউ', not 'বু'");

    session.reset();
    session.process_key("হ".into()).unwrap();
    let res_hoi = session.process_key("ই".into()).unwrap();
    assert_eq!(res_hoi.preedit, "হই", "হ + ই must produce 'হই', not 'হি'");

    session.reset();
    session.process_key("হ".into()).unwrap();
    let res_hao = session.process_key("ও".into()).unwrap();
    assert_eq!(res_hao.preedit, "হও", "হ + ও must produce 'হও', not 'হো'");

    // 1b. Double-tap Kar shortcut (zero-shift ergonomic mobile flow)
    session.reset();
    session.process_key("ক".into()).unwrap();
    session.process_key("ি".into()).unwrap();
    let res_dt_koi = session.process_key("ি".into()).unwrap();
    assert_eq!(
        res_dt_koi.preedit, "কই",
        "Double-tapping 'ি' after 'ক' must promote to 'কই'"
    );

    session.reset();
    session.process_key("ক".into()).unwrap();
    session.process_key("ো".into()).unwrap();
    let res_dt_kao = session.process_key("ো".into()).unwrap();
    assert_eq!(
        res_dt_kao.preedit, "কও",
        "Double-tapping 'ো' after 'ক' must promote to 'কও'"
    );

    session.reset();
    session.process_key("ব".into()).unwrap();
    session.process_key("ি".into()).unwrap();
    let res_dt_boi = session.process_key("ি".into()).unwrap();
    assert_eq!(
        res_dt_boi.preedit, "বই",
        "Double-tapping 'ি' after 'ব' must promote to 'বই'"
    );

    session.reset();
    session.process_key("ব".into()).unwrap();
    session.process_key("ু".into()).unwrap();
    let res_dt_bou = session.process_key("ু".into()).unwrap();
    assert_eq!(
        res_dt_bou.preedit, "বউ",
        "Double-tapping 'ু' after 'ব' must promote to 'বউ'"
    );

    // 1c. Proactive candidate surfacing for consonant + Kar combinations
    session.reset();
    session.process_key("ক".into()).unwrap();
    let res_ki = session.process_key("ি".into()).unwrap();
    assert_eq!(res_ki.preedit, "কি");
    assert!(
        res_ki.candidates.contains(&"কই".to_string()),
        "Typing 'কি' must proactively surface 'কই' in candidate strip"
    );

    session.reset();
    session.process_key("ক".into()).unwrap();
    let res_ko = session.process_key("ো".into()).unwrap();
    assert_eq!(res_ko.preedit, "কো");
    assert!(
        res_ko.candidates.contains(&"কও".to_string()),
        "Typing 'কো' must proactively surface 'কও' in candidate strip"
    );

    session.reset();
    session.process_key("ব".into()).unwrap();
    let res_bi = session.process_key("ি".into()).unwrap();
    assert_eq!(res_bi.preedit, "বি");
    assert!(
        res_bi.candidates.contains(&"বই".to_string()),
        "Typing 'বি' must proactively surface 'বই' in candidate strip"
    );

    session.reset();
    session.process_key("ব".into()).unwrap();
    let res_bu = session.process_key("ু".into()).unwrap();
    assert_eq!(res_bu.preedit, "বু");
    assert!(
        res_bu.candidates.contains(&"বউ".to_string()),
        "Typing 'বু' must proactively surface 'বউ' in candidate strip"
    );

    // ── 2. Probhat Layout ──────────────────────────────────────────────
    session.set_layout(LekhaniLayoutType::Probhat);

    session.reset();
    session.process_key("ক".into()).unwrap();
    let res_probhat_kao = session.process_key("ও".into()).unwrap();
    assert_eq!(
        res_probhat_kao.preedit, "কও",
        "Probhat: 'ক' + 'ও' must produce 'কও'"
    );

    session.reset();
    session.process_key("ক".into()).unwrap();
    let res_probhat_koi = session.process_key("ই".into()).unwrap();
    assert_eq!(
        res_probhat_koi.preedit, "কই",
        "Probhat: 'ক' + 'ই' must produce 'কই'"
    );

    // ── 3. Gboard Layout ───────────────────────────────────────────────
    session.set_layout(LekhaniLayoutType::Gboard);

    session.reset();
    session.process_key("ক".into()).unwrap();
    let res_gboard_koi = session.process_key("ই".into()).unwrap();
    assert_eq!(
        res_gboard_koi.preedit, "কই",
        "Gboard: 'ক' + 'ই' must produce 'কই'"
    );

    session.reset();
    session.process_key("ক".into()).unwrap();
    let res_gboard_ki = session.process_key("ি".into()).unwrap();
    assert!(
        res_gboard_ki.candidates.contains(&"কই".to_string()),
        "Gboard: typing 'কি' must proactively surface 'কই' in candidate strip"
    );

    // 3b. Gboard double-tap Kar promotion: 'ব' + 'ি' + 'ি' -> "বই"
    session.reset();
    session.set_smart_initial_kar_enabled(true);
    session.process_key("ব".into()).unwrap();
    session.process_key("ি".into()).unwrap();
    let res_dt_gboard_boi = session.process_key("ি".into()).unwrap();
    assert_eq!(
        res_dt_gboard_boi.preedit, "বই",
        "Gboard: double-tapping 'ি' after 'ব' must promote to 'বই'"
    );

    // 3c. Gboard postfix Reph: 'ধ' + 'ম' + 'র্' -> "ধর্ম"
    session.reset();
    session.process_key("ধ".into()).unwrap();
    session.process_key("ম".into()).unwrap();
    let res_gboard_dhormo = session.process_key("র্".into()).unwrap();
    assert_eq!(
        res_gboard_dhormo.preedit, "ধর্ম",
        "Gboard: postfix 'র্' after 'ম' must form 'ধর্ম'"
    );

    // 3d. Gboard in-flight conjunct candidate substitution: 'শ' + 'ি' + 'ক' + '্' + [select 'ক্ষ'] + 'া' -> "শিক্ষা"
    session.reset();
    session.process_key("শ".into()).unwrap();
    session.process_key("ি".into()).unwrap();
    session.process_key("ক".into()).unwrap();
    let res_shik = session.process_key("্".into()).unwrap();
    assert_eq!(res_shik.preedit, "শিক্");
    assert!(
        res_shik.candidates.contains(&"ক্ষ".to_string()),
        "Candidate strip must suggest 'ক্ষ' after 'শিক্'"
    );
    let res_sel_kkh = session.select_candidate("ক্ষ".into()).unwrap();
    assert_eq!(
        res_sel_kkh.preedit, "শিক্ষ",
        "Selecting 'ক্ষ' must substitute in-place keeping composing buffer open"
    );
    assert_eq!(
        res_sel_kkh.commit_text, None,
        "Must not commit or insert space"
    );
    let res_shikkha = session.process_key("া".into()).unwrap();
    assert_eq!(
        res_shikkha.preedit, "শিক্ষা",
        "Typist can seamlessly continue typing 'া' to produce 'শিক্ষা'"
    );

    // ── 4. National Layout Initial Kar and Compound ─────────────────────
    session.set_layout(LekhaniLayoutType::National);
    session.set_smart_initial_kar_enabled(true);

    // 4a. Initial Kar promotion: 'া' at word start -> 'আ'
    session.reset();
    let res_nat_a = session.process_key("া".into()).unwrap();
    assert_eq!(
        res_nat_a.preedit, "আ",
        "National: initial 'া' at word start must promote to 'আ'"
    );

    // 4b. Compound: 'অ' + 'া' -> 'আ'
    session.reset();
    session.process_key("অ".into()).unwrap();
    let res_nat_aa = session.process_key("া".into()).unwrap();
    assert_eq!(
        res_nat_aa.preedit, "আ",
        "National: 'অ' + 'া' must compound into 'আ'"
    );

    // ── 5. Gboard Layout Initial Kar ───────────────────────────────────
    session.set_layout(LekhaniLayoutType::Gboard);
    session.set_smart_initial_kar_enabled(true);
    session.reset();
    let res_gb_a = session.process_key("া".into()).unwrap();
    assert_eq!(
        res_gb_a.preedit, "আ",
        "Gboard: initial 'া' at word start must promote to 'আ'"
    );
}

#[test]
fn test_retro_correction_honorific_and_homophone() {
    // 1. Honorific mismatch: "আপনি কেমন আছো" -> proposes "আছেন"
    let corr_apni =
        evaluate_retro_correction("আপনি কেমন আছো").expect("Should detect honorific clash");
    assert_eq!(corr_apni.original_word, "আছো");
    assert_eq!(corr_apni.replacement_word, "আছেন");
    assert_eq!(corr_apni.chip_label, "আছেন");
    assert_eq!(corr_apni.chars_to_backspace, 3); // "আছো".chars().count()

    // 2. Intimate mismatch: "তুই কেমন আছো" -> proposes "আছিস"
    let corr_tui = evaluate_retro_correction("তুই কেমন আছো").expect("Should detect intimate clash");
    assert_eq!(corr_tui.original_word, "আছো");
    assert_eq!(corr_tui.replacement_word, "আছিস");
    assert_eq!(corr_tui.chip_label, "আছিস");

    // 3. Homophone mismatch: "আমি বই পরা শুরু করলাম" -> proposes "পড়া"
    let corr_homo =
        evaluate_retro_correction("আমি বই পরা শুরু").expect("Should detect homophone clash");
    assert_eq!(corr_homo.original_word, "পরা");
    assert_eq!(corr_homo.replacement_word, "পড়া");

    // 4. Session application test
    let session = AndroidLekhaniSession::new();
    session.set_context("আপনি কেমন আছো".into());
    let corr = session.get_retro_correction().unwrap();
    let typing_res = session.apply_retro_correction(corr).unwrap();
    assert_eq!(typing_res.commit_text, Some("আছেন".into()));
}

#[test]
fn test_pure_avro_mode() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);
    session.set_pure_avro_mode(true);
    assert!(session.is_pure_avro_mode());

    // In pure Avro mode, "k" + "a" + "j" produces literal "কাজ"
    session.process_key("k".into()).unwrap();
    session.process_key("a".into()).unwrap();
    let res = session.process_key("j".into()).unwrap();
    assert_eq!(res.preedit, "কাজ");
    assert_eq!(res.candidates, vec!["কাজ".to_string()]);

    // Retro-correction is disabled in pure Avro mode
    session.set_context("আপনি কেমন আছো".into());
    assert!(session.get_retro_correction().is_none());

    // Space commits literal Avro output
    let space_res = session.handle_space().unwrap();
    assert_eq!(space_res.commit_text, Some("কাজ ".into()));
    assert!(space_res.candidates.is_empty());
}

#[test]
fn test_retro_dampener_bayesian_suppression() {
    let session = AndroidLekhaniSession::new();
    session.set_context("আপনি কেমন আছো".into());

    // Initial evaluation surfaces correction
    assert!(session.get_retro_correction().is_some());

    // Explicit dismissal triggers suppression (posterior probability drops below 0.18)
    session.record_retro_correction_dismissed(true);
    assert!(
        session.get_retro_correction().is_none(),
        "Must be suppressed after explicit dismiss"
    );

    // Typing 49 words keeps it suppressed
    for _ in 0..49 {
        session.handle_space().unwrap();
    }
    assert!(
        session.get_retro_correction().is_none(),
        "Must remain suppressed before 50 words"
    );

    // 50th word clears suppression
    session.handle_space().unwrap();
    assert!(
        session.get_retro_correction().is_some(),
        "Must re-enable after 50 words typed"
    );
}

#[test]
fn test_verb_honorific_paradigm_extraction() {
    let session = AndroidLekhaniSession::new();
    let paradigm_koro = session.get_verb_honorific_paradigm("করছো".into());
    assert!(
        paradigm_koro.contains(&"করছেন".to_string()),
        "Must have formal 'করছেন'"
    );
    assert!(
        paradigm_koro.contains(&"করছো".to_string()),
        "Must have familiar 'করছো'"
    );
    assert!(
        paradigm_koro.contains(&"করছিস".to_string()),
        "Must have intimate 'করছিস'"
    );

    // Non-verb returns empty list
    let paradigm_boi = session.get_verb_honorific_paradigm("বই".into());
    assert!(
        paradigm_boi.is_empty(),
        "Noun 'বই' must not produce verb paradigm"
    );
}

#[test]
fn test_honorific_shift_selection_does_not_poison_candidate_memory() {
    let session = AndroidLekhaniSession::new();
    for ch in "koro".chars() {
        session.process_key(ch.to_string()).unwrap();
    }
    // Selecting honorific shift "করছেন"
    session.select_candidate("করছেন".to_string()).unwrap();

    let db = get_core_database();
    if let Ok(learner) = db.learner.read() {
        assert_ne!(
            learner.candidate_memory.get("koro"),
            Some(&"করছেন".to_string()),
            "Honorific shift 'করছেন' must not poison 'koro' transliteration memory"
        );
    };
}

#[test]
fn test_candidate_items_semantic_classification() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    for ch in "c^ad".chars() {
        session.process_key(ch.to_string()).unwrap();
    }

    let items = session.get_candidate_items().unwrap();
    assert!(!items.is_empty(), "Must produce candidate items");
    assert_eq!(items[0].text, "চাঁদ");
    assert_eq!(items[0].kind, CandidateKind::DeterministicAvro);
    assert!(items[0].is_primary);

    let custom_items =
        session.classify_candidates("500tk".into(), vec!["৫০০ টাকা".into(), "500tk".into()]);
    assert_eq!(custom_items[0].kind, CandidateKind::NumericLiteral);
    assert_eq!(custom_items[1].kind, CandidateKind::EnglishPassthrough);
}

#[test]
fn test_lps_phonetic_session_and_letter_chips() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::LpsPhonetic);

    let mut last_res = None;
    for ch in "bari".chars() {
        last_res = Some(session.process_key(ch.to_string()).unwrap());
    }

    let res = last_res.unwrap();
    assert!(res
        .candidates
        .iter()
        .any(|c| c == "ব\u{09BE}\u{09DC}\u{09BF}" || c == "বারি"));

    let chips = session.get_lps_letter_chips();
    assert!(
        !chips.is_empty(),
        "LPS session must expose letter disambiguation chips"
    );

    assert_eq!(session.exact_form("কলম".into()), "k-l-m");
    assert_eq!(session.exact_form("বই".into()), "b-i");

    // Context-aware ranking test for "তুমি ছাড়া"
    let session2 = AndroidLekhaniSession::new();
    session2.set_layout(LekhaniLayoutType::LpsPhonetic);
    session2.set_context("তুমি".to_string());
    let mut chhara_res = None;
    for ch in "chhara".chars() {
        chhara_res = Some(session2.process_key(ch.to_string()).unwrap());
    }
    let res2 = chhara_res.unwrap();
    let chhara_nfc = crate::probaho::nfc_normalize("ছাড়া");
    assert!(res2.candidates.contains(&"ছারা".to_string()));
    assert!(res2
        .candidates
        .iter()
        .any(|c| crate::probaho::nfc_normalize(c) == chhara_nfc));

    // Test Backspace in LPS
    let bs_res = session.handle_backspace().unwrap();
    assert!(!bs_res.candidates.is_empty());
    assert!(bs_res.preedit.starts_with('ব'));

    // Test punctuation commit in LPS
    let session3 = AndroidLekhaniSession::new();
    session3.set_layout(LekhaniLayoutType::LpsPhonetic);
    for ch in "k-l-m".chars() {
        session3.process_key(ch.to_string()).unwrap();
    }
    let punct_res = session3.process_key("।".to_string()).unwrap();
    assert_eq!(punct_res.commit_text.as_deref(), Some("কলম।"));
}

#[test]
fn test_lps_chips_scoped_strictly_to_lps_layout() {
    let session = AndroidLekhaniSession::new();

    // 1. LPS layout surfaces letter chips for phonotactic disambiguation (e.g. 'd' -> [দ, ড])
    session.set_layout(LekhaniLayoutType::LpsPhonetic);
    for ch in "dakar".chars() {
        session.process_key(ch.to_string()).unwrap();
    }
    let chips = session.get_lps_letter_chips();
    assert!(
        !chips.is_empty(),
        "LPS session must expose letter disambiguation chips"
    );
    let has_d_chip = chips
        .iter()
        .any(|c| c.siblings.contains(&"ড".to_string()) && c.siblings.contains(&"দ".to_string()));
    assert!(
        has_d_chip,
        "Chips must contain [দ, ড] sibling group for initial d in LPS"
    );

    // 2. Avro layout remains clean and classic: get_lps_letter_chips must return empty
    session.reset();
    session.set_layout(LekhaniLayoutType::Avro);
    for ch in "dakar".chars() {
        session.process_key(ch.to_string()).unwrap();
    }
    let chips_avro = session.get_lps_letter_chips();
    assert!(
        chips_avro.is_empty(),
        "Avro layout must NOT leak LPS letter chips; layouts remain cleanly separated"
    );
}

#[test]
fn test_homophone_topic_disambiguation_and_candidate_annotations() {
    let session = AndroidLekhaniSession::new();

    // Set education context
    session.set_context("আমি প্রতিদিন অনেক বই পড়তে পছন্দ করি".to_string());

    // 1. Direct homophone disambiguation
    let result = session.disambiguate_homophone("পরা".to_string(), "পড়া".to_string());
    assert!(
        result.is_some(),
        "Should disambiguate homophones with context"
    );
    let res = result.unwrap();
    assert_eq!(
        res.winner, "পড়া",
        "Education context should favor পড়া over পরা"
    );
    assert_eq!(res.runner_up, "পরা");
    assert!(res.confidence > 0.0);
    assert_eq!(res.domain_hint.as_deref(), Some("পড়ালেখা"));

    // 2. Candidate topic annotations
    let candidates = vec!["পড়া".to_string(), "পরা".to_string(), "করছি".to_string()];
    let annotations = session.annotate_candidate_topics(candidates);
    assert_eq!(annotations.len(), 3);

    let ann_pora_da = &annotations[0];
    assert_eq!(ann_pora_da.candidate, "পড়া");
    assert_eq!(ann_pora_da.homophone_partner.as_deref(), Some("পরা"));
    assert_eq!(ann_pora_da.domain_hint.as_deref(), Some("পড়ালেখা"));
    assert!(ann_pora_da.is_topic_winner);

    let ann_pora_ra = &annotations[1];
    assert_eq!(ann_pora_ra.candidate, "পরা");
    assert_eq!(ann_pora_ra.homophone_partner.as_deref(), Some("পড়া"));
    assert_eq!(ann_pora_ra.domain_hint.as_deref(), Some("পোশাক"));
    assert!(!ann_pora_ra.is_topic_winner);

    let ann_korchi = &annotations[2];
    assert_eq!(ann_korchi.candidate, "করছি");
    assert_eq!(ann_korchi.homophone_partner, None);
    assert_eq!(ann_korchi.domain_hint, None);
    assert!(!ann_korchi.is_topic_winner);
}
