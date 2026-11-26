
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
    assert!(
        cands_smi.contains(&"আমি".to_string()),
        "Expected 'আমি' in candidates for 'smi'"
    );
    assert!(
        cands_smi.contains(&"স্মি".to_string()),
        "Expected 'স্মি' in candidates for 'smi'"
    );

    // Adjacent transposition typo: "bhlao" -> "ভালো"
    let (_pre_bhlao, cands_bhlao) = transliterate_avro("bhlao");
    assert!(
        cands_bhlao.contains(&"ভালো".to_string()),
        "Expected 'ভালো' in candidates for 'bhlao'"
    );
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

#[test]
fn test_digit_units_quantifiers_and_ordinals() {
    // 1. Quantifiers: "10ta" -> "১০টা", "2ti" -> "২টি"
    let (ta_res, ta_cands) = transliterate_avro("10ta");
    assert_eq!(ta_res, "১০টা");
    assert!(ta_cands.contains(&"10ta".to_string()));

    let (ti_res, ti_cands) = transliterate_avro("2ti");
    assert_eq!(ti_res, "২টি");
    assert!(ti_cands.contains(&"2ti".to_string()));

    // 2. Currency: "500tk" -> "৫০০ টাকা", offers "৳৫০০"
    let (tk_res, tk_cands) = transliterate_avro("500tk");
    assert_eq!(tk_res, "৫০০ টাকা");
    assert!(tk_cands.contains(&"৳৫০০".to_string()));
    assert!(tk_cands.contains(&"500tk".to_string()));

    // 3. Units: "5kg" -> "৫ কেজি", "500gm" -> "৫০০ গ্রাম"
    let (kg_res, kg_cands) = transliterate_avro("5kg");
    assert_eq!(kg_res, "৫ কেজি");
    assert!(kg_cands.contains(&"5kg".to_string()));

    let (gm_res, _) = transliterate_avro("500gm");
    assert_eq!(gm_res, "৫০০ গ্রাম");

    // 4. Ordinals: "1st" -> "১ম", "2nd" -> "২য়", "3rd" -> "৩য়", "4th" -> "৪র্থ"
    let (st1, _) = transliterate_avro("1st");
    assert_eq!(st1, "১ম");

    let (nd2, _) = transliterate_avro("2nd");
    assert_eq!(nd2, "২য়");

    let (rd3, _) = transliterate_avro("3rd");
    assert_eq!(rd3, "৩য়");

    let (th4, _) = transliterate_avro("4th");
    assert_eq!(th4, "৪র্থ");
}

#[test]
fn test_social_mentions_and_hashtags() {
    // 1. Mentions: "@siam" -> verbatim "@siam" primary, offers "@সিয়াম"
    let (mention_res, mention_cands) = transliterate_avro("@siam");
    assert_eq!(mention_res, "@siam");
    assert!(mention_cands.contains(&"@সিয়াম".to_string()));

    // 2. Hashtags: "#dhaka" -> verbatim "#dhaka" primary, offers "#ঢাকা"
    let (tag_res, tag_cands) = transliterate_avro("#dhaka");
    assert_eq!(tag_res, "#dhaka");
    assert!(tag_cands.contains(&"#ঢাকা".to_string()));
}

#[test]
#[serial_test::serial]
fn test_bilingual_hyphen_clitics() {
    set_bilingual_clitic_enabled(true);
    // 1. "Facebook-e" -> "Facebook-এ"
    let (fb_res, fb_cands) = transliterate_avro("Facebook-e");
    assert_eq!(fb_res, "Facebook-এ");
    assert!(fb_cands.contains(&"Facebook-e".to_string()));

    // 2. "bkash-e" -> "bkash-এ"
    let (bk_res, bk_cands) = transliterate_avro("bkash-e");
    assert_eq!(bk_res, "bkash-এ");
    assert!(bk_cands.contains(&"bkash-e".to_string()));

    // 3. "post-ta" -> "post-টা"
    let (post_res, _) = transliterate_avro("post-ta");
    assert_eq!(post_res, "post-টা");

    // 4. "file-gulo" -> "file-গুলো"
    let (file_res, _) = transliterate_avro("file-gulo");
    assert_eq!(file_res, "file-গুলো");
    set_bilingual_clitic_enabled(false);
}

#[test]
#[serial_test::serial]
fn test_unhyphenated_bilingual_sandhi_clitics() {
    set_bilingual_clitic_enabled(true);
    // 1. WhatsAppe -> "WhatsApp-এ", surfaces "হোয়াটসঅ্যাপে" and "WhatsAppe"
    let (wa_res, wa_cands) = transliterate_avro("WhatsAppe");
    assert_eq!(wa_res, "WhatsApp-এ");
    assert!(wa_cands.contains(&"WhatsAppe".to_string()));
    assert!(wa_cands.contains(&"হোয়াটসঅ্যাপে".to_string()));

    // 2. WhatsApper -> "WhatsApp-এর"
    let (wa_er_res, wa_er_cands) = transliterate_avro("WhatsApper");
    assert_eq!(wa_er_res, "WhatsApp-এর");
    assert!(wa_er_cands.contains(&"WhatsApper".to_string()));

    // 3. Meetinger -> "Meeting-এর", surfaces "মিটিংয়ের"
    let (meet_res, meet_cands) = transliterate_avro("Meetinger");
    assert_eq!(meet_res, "Meeting-এর");
    assert!(meet_cands.contains(&"Meetinger".to_string()));
    assert!(meet_cands.contains(&"মিটিংয়ের".to_string()));

    // 4. APIgulor -> "API-গুলোর"
    let (api_res, api_cands) = transliterate_avro("APIgulor");
    assert_eq!(api_res, "API-গুলোর");
    assert!(api_cands.contains(&"APIgulor".to_string()));

    // 5. Bugta -> "Bug-টা"
    let (bug_res, bug_cands) = transliterate_avro("Bugta");
    assert_eq!(bug_res, "Bug-টা");
    assert!(bug_cands.contains(&"Bugta".to_string()));

    // 6. failmarse -> "fail মারছে"
    let (fail_res, _) = transliterate_avro("failmarse");
    assert_eq!(fail_res, "fail মারছে");

    // 7. Everyday inflected loanwords resolve naturally to Bengali spelling, while hyphenated produces code-mixed
    let (post_res, _) = transliterate_avro("poste");
    assert_eq!(post_res, "পোস্টে");
    let (post_hyphen_res, post_hyphen_cands) = transliterate_avro("post-e");
    assert_eq!(post_hyphen_res, "post-এ");
    assert!(post_hyphen_cands.contains(&"post-e".to_string()));

    // 8. Bengali core words are completely safe from false decoupling
    assert_eq!(transliterate_avro("pore").0, "পরে");
    assert_eq!(transliterate_avro("kore").0, "করে");
    assert_eq!(transliterate_avro("amar").0, "আমার");
    assert_eq!(transliterate_avro("tumi").0, "তুমি");
    set_bilingual_clitic_enabled(false);
}

#[test]
fn test_khanda_ta_loanwords_and_dialect_verbs() {
    // 1. Khanda-Ta words
    let (utpadon, _) = transliterate_avro("utpadon");
    assert_eq!(utpadon, "উৎপাদন");

    let (chamatkar, _) = transliterate_avro("chamatkar");
    assert_eq!(chamatkar, "চমৎকার");

    let (utso, _) = transliterate_avro("utso");
    assert_eq!(utso, "উৎস");

    let (jogot, _) = transliterate_avro("jogot");
    assert_eq!(jogot, "জগৎ");

    let (utpotti, _) = transliterate_avro("utpotti");
    assert_eq!(utpotti, "উৎপত্তি");

    // 2. English loanwords with æ / অ্যা
    let (admin, _) = transliterate_avro("admin");
    assert_eq!(admin, "অ্যাডমিন");

    let (match_res, _) = transliterate_avro("match");
    assert_eq!(match_res, "ম্যাচ");

    let (gas, _) = transliterate_avro("gas");
    assert_eq!(gas, "গ্যাস");

    let (plan, _) = transliterate_avro("plan");
    assert_eq!(plan, "প্ল্যান");

    let (address, _) = transliterate_avro("address");
    assert_eq!(address, "অ্যাড্রেস");

    // 3. Dialect continuous verbs
    let (khaitasi, _) = transliterate_avro("khaitasi");
    assert_eq!(khaitasi, "খাইতাসি");

    let (kortasi, _) = transliterate_avro("kortasi");
    assert_eq!(kortasi, "করতাসি");
}

#[test]
fn test_anvaya_slot0_wysiwyg_guarantee() {
    // Under formal subject ("আপনি কেমন"), typing "aso":
    // Slot 0 MUST be the familiar verb "আছো" (WYSIWYG: Space commits what is highlighted)
    // Slot 1 MUST be the Anvaya formal suggestion "আছেন" (commits on tap, not on Space)
    let (primary, candidates) = transliterate_avro_with_context("aso", &["আপনি", "কেমন"]);
    assert_eq!(primary, "আছো", "Slot 0 must be familiar 'আছো'");
    assert!(candidates.len() >= 2, "Must have at least 2 candidates");
    assert_eq!(candidates[0], "আছো", "Candidate[0] must be 'আছো'");
    assert_eq!(candidates[1], "আছেন", "Candidate[1] must be 'আছেন'");

    // Intimate subject ("তুই"), typing "koro":
    // Slot 0 is "করো", Slot 1 is "করছিস"
    let (primary_tui, candidates_tui) = transliterate_avro_with_context("koro", &["তুই"]);
    assert_eq!(primary_tui, "করো", "Slot 0 must be familiar 'করো'");
    assert!(
        candidates_tui.contains(&"করছিস".to_string()),
        "Candidate pool must contain 'করছিস'"
    );
}

#[test]
fn test_grammar_biased_muscle_memory_invariant() {
    let parser = get_avro_parser();
    let roots = [
        "k", "kh", "g", "gh", "c", "ch", "j", "jh", "T", "Th", "D", "Dh", "N", "t", "th", "d",
        "dh", "n", "p", "ph", "b", "bh", "m", "r", "l", "sh", "Sh", "s", "h", "R", "Rh", "y",
        "kSh", "gyan", "rri",
    ];
    let vowels = ["", "a", "i", "I", "u", "U", "e", "o", "O", "OI", "OU"];
    let codas = ["", "r", "l", "n", "m", "t", "k", "sh", "^", ":"];
    let suffixes = ["", "ta", "gulo", "ke", "ra", "der", "te", "techi"];

    let mut checked = 0;
    for &r in &roots {
        for &v in &vowels {
            for &c in &codas {
                for &suf in &suffixes {
                    let mut token = String::new();
                    token.push_str(r);
                    token.push_str(v);
                    token.push_str(c);
                    token.push_str(suf);

                    if token.is_empty() {
                        continue;
                    }

                    let expected = parser.convert(&token);
                    if expected.is_empty() {
                        continue;
                    }

                    let (_, candidates) = transliterate_avro(&token);
                    assert!(
                        candidates.contains(&expected),
                        "Invariant violated for '{}': expected '{}' not in candidates {:?}",
                        token,
                        expected,
                        candidates
                    );
                    checked += 1;
                }
            }
        }
    }
    assert!(
        checked > 5000,
        "Should have fuzzed > 5000 grammar-biased inputs, fuzzed {}",
        checked
    );
}

#[test]
fn test_cca_and_short_acronym_promotion() {
    // Lowercase "cca" must transliterate to Bengali "চ্চা" in Slot 0, NOT Latin "cca"
    let (primary, cands) = transliterate_avro("cca");
    assert_eq!(
        primary, "চ্চা",
        "Primary preedit for 'cca' must be 'চ্চা', got '{}'",
        primary
    );
    assert_eq!(cands.first().map(|s| s.as_str()), Some("চ্চা"));
    assert!(
        cands.contains(&"cca".to_string()),
        "Candidate strip should still offer Latin 'cca'"
    );

    // Uppercase "CCA" should promote to Slot 0 Latin "CCA" as a technical acronym
    let (primary_caps, cands_caps) = transliterate_avro("CCA");
    assert_eq!(
        primary_caps, "CCA",
        "Primary preedit for all-caps 'CCA' must be 'CCA'"
    );
    assert_eq!(cands_caps.first().map(|s| s.as_str()), Some("CCA"));
}

#[test]
fn test_oov_slang_primacy() {
    // Non-dictionary slang and novel words must maintain literal Avro phonetic transliteration at Slot 0
    let (jhalkhor, cands) = transliterate_avro("jhalkhor");
    assert_eq!(
        jhalkhor, "ঝালখর",
        "jhalkhor must transliterate to 'ঝালখর' at Slot 0"
    );
    assert_eq!(cands.first().map(|s| s.as_str()), Some("ঝালখর"));

    let (falkibazi, cands_falki) = transliterate_avro("falkibazi");
    let def_falki = crate::probaho::nfc_normalize(&get_avro_parser().convert("falkibazi"));
    assert_eq!(
        falkibazi, def_falki,
        "falkibazi must match pure Avro def at Slot 0"
    );
    assert_eq!(
        cands_falki.first().map(|s| s.as_str()),
        Some(def_falki.as_str())
    );

    let (falkibaji, cands_baji) = transliterate_avro("falkibaji");
    let def_baji = crate::probaho::nfc_normalize(&get_avro_parser().convert("falkibaji"));
    assert_eq!(
        falkibaji, def_baji,
        "falkibaji must match pure Avro def at Slot 0"
    );
    assert_eq!(
        cands_baji.first().map(|s| s.as_str()),
        Some(def_baji.as_str())
    );

    // Standard dictionary words must still preserve their correct dictionary forms
    let (soccho, _) = transliterate_avro("soccho");
    assert_eq!(
        soccho, "স্বচ্ছ",
        "soccho must still resolve to dictionary word 'স্বচ্ছ' at Slot 0"
    );

    let (kemon, _) = transliterate_avro("kemon");
    assert_eq!(kemon, "কেমন", "kemon must resolve to 'কেমন' at Slot 0");
}

#[test]
fn test_explicit_avro_rule_intent_detection() {
    // 1. Explicit operators must be detected
    assert!(has_explicit_avro_intent("c^ad"));
    assert!(has_explicit_avro_intent("h^as"));
    assert!(has_explicit_avro_intent("b^ash"));
    assert!(has_explicit_avro_intent("du:kho"));
    assert!(has_explicit_avro_intent("k`kh"));
    assert!(has_explicit_avro_intent("k'kh"));
    assert!(has_explicit_avro_intent("hot`at"));
    assert!(has_explicit_avro_intent("hot'at"));
    assert!(has_explicit_avro_intent("k,,k"));
    assert!(has_explicit_avro_intent("k..k"));
    assert!(has_explicit_avro_intent("$500"));

    // 2. Mid-word explicit Avro uppercase distinctions must be detected
    assert!(has_explicit_avro_intent("paR"));
    assert!(has_explicit_avro_intent("kOno"));
    assert!(has_explicit_avro_intent("kaTh"));
    assert!(has_explicit_avro_intent("biSTi"));
    assert!(has_explicit_avro_intent("bODh"));
    assert!(has_explicit_avro_intent("baRi"));

    // 3. Sentence-initial titlecase & casual typing must NOT be marked as explicit intent
    assert!(!has_explicit_avro_intent("Tumi"));
    assert!(!has_explicit_avro_intent("Din"));
    assert!(!has_explicit_avro_intent("Tara"));
    assert!(!has_explicit_avro_intent("kemon"));
    assert!(!has_explicit_avro_intent("basha"));
    assert!(!has_explicit_avro_intent("ami"));
    assert!(!has_explicit_avro_intent("dharmo"));

    // 4. End-to-end Slot 0 preedit locking for explicit Avro inputs
    let (cad, _) = transliterate_avro("ca^d");
    assert_eq!(cad, "চাঁদ", "ca^d must lock to 'চাঁদ'");

    let (par, _) = transliterate_avro("paR");
    assert_eq!(par, "পাড়", "paR must lock to 'পাড়'");

    let (por, _) = transliterate_avro("poR");
    assert_eq!(por, "পড়", "poR must lock to 'পড়'");

    let (dukho, _) = transliterate_avro("du:kho");
    assert_eq!(dukho, "দুঃখ", "du:kho must lock to 'দুঃখ'");

    let (hotat, _) = transliterate_avro("hoThat``");
    assert_eq!(hotat, "হঠাৎ", "hoThat`` must lock to 'হঠাৎ'");

    let (biggani, _) = transliterate_avro("bigganI");
    assert_eq!(biggani, "বিজ্ঞানী", "bigganI must lock to 'বিজ্ঞানী'");

    let (kk, _) = transliterate_avro("k`k");
    assert_eq!(kk, "কক", "k`k must break conjunct into 'কক'");

    // 5. Explicit Avro intent must NOT starve vocabulary prefix completions
    let (bari, bari_cands) = transliterate_avro("baRi");
    assert_eq!(bari, "বাড়ি", "baRi must lock Slot 0 to 'বাড়ি'");
    assert!(
        bari_cands.len() > 2,
        "baRi must include Trie prefix completions, not just raw def"
    );
    assert!(
        bari_cands
            .iter()
            .any(|c| c.starts_with("বাড়ি") && c != "বাড়ি"),
        "baRi candidates must include prefix expansions like বাড়িতে/বাড়ির"
    );
}

#[test]
#[serial_test::serial]
fn test_universal_morpheme_snapping_and_soft_hyphen_compounding() {
    // 1. Soft-hyphen compounding joins segments into canonical compound Bengali words
    let (res_hyphen, cands_hyphen) = transliterate_avro("antorjatik-bhabe");
    assert_eq!(
        res_hyphen, "আন্তর্জাতিকভাবে",
        "antorjatik-bhabe must compound to 'আন্তর্জাতিকভাবে'"
    );
    assert!(cands_hyphen.contains(&"আন্তর্জাতিক-ভাবে".to_string()));

    let (res_univ_hyphen, _) = transliterate_avro("bishwobiddaloy-gulote");
    assert_eq!(
        res_univ_hyphen, "বিশ্ববিদ্যালয়গুলোতে",
        "bishwobiddaloy-gulote must compound to 'বিশ্ববিদ্যালয়গুলোতে'"
    );

    // 2. Continuous unhyphenated compound snapping for complex long words
    let (res_snap1, _) = transliterate_avro("antorjatikbhabe");
    assert_eq!(
        res_snap1, "আন্তর্জাতিকভাবে",
        "antorjatikbhabe must snap to 'আন্তর্জাতিকভাবে'"
    );

    let (res_snap2, _) = transliterate_avro("shadhinotahinbhabe");
    assert_eq!(
        res_snap2, "স্বাধীনতাহীনভাবে",
        "shadhinotahinbhabe must snap to 'স্বাধীনতাহীনভাবে'"
    );

    let (res_snap3, _) = transliterate_avro("dayittoshilota");
    assert_eq!(
        crate::probaho::nfc_normalize(&res_snap3),
        crate::probaho::nfc_normalize("দায়িত্বশীলতা"),
        "dayittoshilota must snap to 'দায়িত্বশীলতা'"
    );

    let (res_snap4, _) = transliterate_avro("shikkhokderke");
    assert_eq!(
        crate::probaho::nfc_normalize(&res_snap4),
        crate::probaho::nfc_normalize("শিক্ষকদেরকে"),
        "shikkhokderke must snap to 'শিক্ষকদেরকে'"
    );
}

#[test]
fn test_gibberish_and_pure_avro_passthrough() {
    let (res, cands) = transliterate_avro("asdfgh");
    let def = get_avro_parser().convert("asdfgh");
    assert_eq!(
        res, def,
        "Gibberish 'asdfgh' must resolve to pure Avro transliteration"
    );
    assert_eq!(
        cands[0], def,
        "Slot 0 candidate for gibberish must be pure Avro transliteration"
    );

    let session = crate::session::AndroidLekhaniSession::new();
    let pure = session.transliterate_pure_avro("aso".to_string());
    assert_eq!(
        pure, "আছো",
        "Pure Avro must return 'আছো' without Anvaya shift to 'আছেন'"
    );
}

#[test]
fn test_contextual_acronym_vc_and_vcr() {
    let (_, vc_cands) = resolve_avro_candidates("vc", &[], None);
    assert_eq!(vc_cands.first().map(String::as_str), Some("ভিসি"));

    let (vcr_preedit, vcr_cands) = resolve_avro_candidates("vcr", &["এই", "মুলা"], None);
    assert_eq!(vcr_preedit, "ভিসির");
    assert_eq!(vcr_cands.first().map(String::as_str), Some("ভিসির"));
}
