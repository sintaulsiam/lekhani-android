use lekhani_android::{AndroidLekhaniSession, LekhaniLayoutType};

#[derive(Debug, Clone)]
pub struct ExtendedTestCase {
    pub section: &'static str,
    pub input_keys: &'static str,
    pub expected: &'static str,
    pub alt_expected: Option<&'static str>, // Accepted alternatives per Section 21 specifications
    pub category: &'static str,
}

#[derive(Debug, Clone)]
pub struct IntermediateStepLog {
    pub step: usize,
    pub key: char,
    pub preedit: String,
    pub candidates: Vec<String>,
}

#[derive(Debug, Clone)]
pub struct WordSimReport {
    pub section: String,
    pub input: String,
    pub expected: String,
    pub actual_committed: String,
    pub passed_first_choice: bool,
    pub found_in_candidates: bool,
    pub intermediate_flickers: usize,
    pub steps: Vec<IntermediateStepLog>,
    pub next_word_predictions: Vec<String>,
}

#[test]
fn test_extended_sections_13_to_22_letter_by_letter() {
    let session = AndroidLekhaniSession::new();
    session.set_layout(LekhaniLayoutType::Avro);

    let test_corpus: Vec<ExtendedTestCase> = vec![
        // ====================================================================
        // SECTION 13: জটিল একাডেমিক বাংলা: ভাষাবিজ্ঞান, ধ্বনিতত্ত্ব ও বাংলা বানান
        // ====================================================================
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "bangla", expected: "বাংলা", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "bhashar", expected: "ভাষার", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "dhonitattik", expected: "ধ্বনিতাত্ত্বিক", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "ruptattik", expected: "রূপতাত্ত্বিক", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "bakkotattik", expected: "বাক্যতাত্ত্বিক", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "boishishto", expected: "বৈশিষ্ট্য", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "bishleshon", expected: "বিশ্লেষণ", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "korte", expected: "করতে", alt_expected: None, category: "common_verb" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "prothomei", expected: "প্রথমেই", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "sposhtobhabe", expected: "স্পষ্টভাবে", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "upolobdhi", expected: "উপলব্ধি", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "proyojon", expected: "প্রয়োজন", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "likhito", expected: "লিখিত", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "uccarito", expected: "উচ্চারিত", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "rup", expected: "রূপ", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "shomporkito", expected: "সম্পর্কিত", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "protinidhitwokari", expected: "প্রতিনিধিত্বকারী", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "ekadhik", expected: "একাধিক", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "borno", expected: "বর্ণ", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "juktobanjoner", expected: "যুক্তব্যঞ্জনের", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "shorolikoron", expected: "সরলীকরণ", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "shwordhonir", expected: "স্বরধ্বনির", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "hrisswota-dirghotar", expected: "হ্রস্বতা-দীর্ঘতার", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "oitihashik", expected: "ঐতিহাসিক", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "probhab", expected: "প্রভাব", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "ancholik", expected: "আঞ্চলিক", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "uccaroner", expected: "উচ্চারণের", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "boicitro", expected: "বৈচিত্র্য", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "jotil", expected: "জটিল", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "obhidhan", expected: "অভিধান", alt_expected: None, category: "academic_phonetic" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "ami", expected: "আমি", alt_expected: None, category: "pronoun" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "valo", expected: "ভালো", alt_expected: None, category: "adjective" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "achi", expected: "আছি", alt_expected: None, category: "verb" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "asi", expected: "আছি", alt_expected: Some("আসি"), category: "dialect" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "korlo", expected: "করল", alt_expected: Some("করলো"), category: "verb_variant" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "porlo", expected: "পড়ল", alt_expected: Some("পড়লো"), category: "verb_variant" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "dhorlo", expected: "ধরল", alt_expected: Some("ধরলে"), category: "verb_variant" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "jacchi", expected: "যাচ্ছি", alt_expected: None, category: "verb_progressive" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "jaitechi", expected: "যাইতেছি", alt_expected: None, category: "verb_sadhu" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "jaitachi", expected: "যাইতাছি", alt_expected: None, category: "verb_dialect" },
        ExtendedTestCase { section: "Sec 13: Linguistics", input_keys: "jaitasi", expected: "যাইতাসি", alt_expected: None, category: "verb_dialect" },

        // ====================================================================
        // SECTION 14: বিশ্ববিদ্যালয়ের জীবন, বন্ধুত্ব, হতাশা ও বাস্তবতা
        // ====================================================================
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "bishwobiddaloye", expected: "বিশ্ববিদ্যালয়ে", alt_expected: Some("বিশ্ববিদ্যালয়ের"), category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "bhorte", expected: "ভর্তি", alt_expected: None, category: "academic" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "cinemar", expected: "সিনেমার", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "shadhinota", expected: "স্বাধীনতা", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "adda", expected: "আড্ডা", alt_expected: None, category: "colloquial" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "campus", expected: "ক্যাম্পাস", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "porashona", expected: "পড়াশোনা", alt_expected: None, category: "common" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "assignment", expected: "অ্যাসাইনমেন্ট", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "quiz", expected: "কুইজ", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "lab", expected: "ল্যাব", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "report", expected: "রিপোর্ট", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "presentation", expected: "প্রেজেন্টেশন", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "deadline", expected: "deadline", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "midterm", expected: "midterm", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "group", expected: "group", alt_expected: Some("গ্রুপ"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "project-er", expected: "project-এর", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "meeting", expected: "meeting", alt_expected: Some("মিটিং"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "manage", expected: "manage", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "submission-er", expected: "submission-এর", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "coding", expected: "coding", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "documentation", expected: "documentation", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "slides", expected: "slides", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "CGPA", expected: "CGPA", alt_expected: None, category: "acronym" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "shustho", expected: "সুস্থ", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 14: University Life", input_keys: "shomporko", expected: "সম্পর্ক", alt_expected: None, category: "tatshama" },

        // ====================================================================
        // SECTION 15: আঞ্চলিক কথ্য বাংলা ও শহুরে Slang-এর মিশ্রণ
        // ====================================================================
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "theika", expected: "থেইকা", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "ditachi", expected: "দিতাছি", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "dichos", expected: "দিছস", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "story-o", expected: "স্টোরিও", alt_expected: Some("story-ও"), category: "loanword_clitic" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "reply", expected: "reply", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "deoner", expected: "দেওনের", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "busy", expected: "busy", alt_expected: Some("ব্যস্ত"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "kos", expected: "কস", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "dhoros", expected: "ধরস", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "attitude-ta", expected: "attitude-টা", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "dangerous", expected: "dangerous", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "level-e", expected: "level-এ", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "bosum", expected: "বসুম", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "korum", expected: "করুম", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "career", expected: "career", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "planning", expected: "planning", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "hoibo", expected: "হইব", alt_expected: Some("হইবো"), category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "fail", expected: "fail", alt_expected: Some("ফেল"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "debate", expected: "debate", alt_expected: Some("ডিবেট"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "koiya", expected: "কইয়া", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "formal", expected: "formal", alt_expected: Some("ফর্মাল"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "complaint", expected: "complaint", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "committee", expected: "committee", alt_expected: Some("কমিটি"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "member", expected: "member", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "adviser", expected: "adviser", alt_expected: Some("এডভাইজার"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 15: Dialect & Slang", input_keys: "management-er", expected: "management-এর", alt_expected: None, category: "hyphen_clitic" },

        // ====================================================================
        // SECTION 16: বিজ্ঞান, কৃত্রিম বুদ্ধিমত্তা ও ভবিষ্যৎ প্রযুক্তি
        // ====================================================================
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "krittrim", expected: "কৃত্রিম", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "buddhimmottar", expected: "বুদ্ধিমত্তার", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "shamprotik", expected: "সাম্প্রতিক", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "ogrogoti", expected: "অগ্রগতি", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "computar", expected: "কম্পিউটার", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "LLM", expected: "LLM", alt_expected: None, category: "acronym" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "inference-time", expected: "inference-time", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "computation", expected: "computation", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "context", expected: "context", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "window", expected: "window", alt_expected: Some("উইন্ডো"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "evaluation", expected: "evaluation", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "methodology", expected: "methodology", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "benchmark", expected: "benchmark", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "score", expected: "score", alt_expected: Some("স্কোর"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "varsity", expected: "ভার্সিটি", alt_expected: None, category: "loanword" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "aijka", expected: "আইজকা", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "jamu", expected: "যামু", alt_expected: None, category: "dialect" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "hybrid", expected: "hybrid", alt_expected: Some("হাইব্রিড"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "architecture", expected: "architecture", alt_expected: Some("আর্কিটেকচার"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 16: AI & Tech", input_keys: "suggestion", expected: "suggestion", alt_expected: None, category: "code_switch" },

        // ====================================================================
        // SECTION 17: দর্শন, যুক্তিবিদ্যা ও জ্ঞানতত্ত্ব
        // ====================================================================
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "darshon", expected: "দর্শন", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "juktibiddar", expected: "যুক্তিবিদ্যার", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "gganototto", expected: "জ্ঞানতত্ত্ব", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "deductive", expected: "deductive", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "inductive", expected: "inductive", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "reasoning", expected: "reasoning", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "uposhonghar", expected: "উপসংহার", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "porjobekkhan", expected: "পর্যবেক্ষণ", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "association", expected: "association", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "confounding", expected: "confounding", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "variables", expected: "variables", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "skepticism", expected: "skepticism", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 17: Philosophy", input_keys: "cynicism", expected: "cynicism", alt_expected: None, category: "code_switch" },

        // ====================================================================
        // SECTION 18: বাংলাদেশের নগরায়ণ, পরিবেশ ও সামাজিক বৈষম্য
        // ====================================================================
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "nogorayon", expected: "নগরায়ণ", alt_expected: Some("নগরায়ন"), category: "tatshama" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "kormoshongsthan", expected: "কর্মসংস্থান", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "jogajogbabosthar", expected: "যোগাযোগব্যবস্থার", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "poribahanbabosthar", expected: "পরিবহনব্যবস্থার", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "utpadonshilota", expected: "উৎপাদনশীলতা", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "shusthota", expected: "সুস্থতা", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "kongkritayon", expected: "কংক্রিটায়ন", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "jolaradh", expected: "জলাধার", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "nishkashonbabostha", expected: "নিষ্কাশনব্যবস্থা", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "abashon", expected: "আবাসন", alt_expected: None, category: "tatshama" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "ontorbhuktimulok", expected: "অন্তর্ভুক্তিমূলক", alt_expected: None, category: "tatshama_complex" },
        ExtendedTestCase { section: "Sec 18: Urban & Environment", input_keys: "morjadar", expected: "মর্যাদার", alt_expected: None, category: "tatshama" },

        // ====================================================================
        // SECTION 19: সফটওয়্যার ইঞ্জিনিয়ারিং, Rust ও Cross-platform Architecture
        // ====================================================================
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "cross-platform", expected: "cross-platform", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "Rust-e", expected: "Rust-এ", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "Kotlin", expected: "Kotlin", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "Compose", expected: "Compose", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "InputMethodService-er", expected: "InputMethodService-এর", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "Linux", expected: "Linux", alt_expected: Some("লিনাক্স"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "Windows", expected: "Windows", alt_expected: Some("উইন্ডোস"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "Slint-bhittik", expected: "Slint-ভিত্তিক", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "Fcitx", expected: "Fcitx", alt_expected: Some("ফচিতক্স"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "IBus-er", expected: "IBus-এর", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "API-te", expected: "API-তে", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "Unicode", expected: "Unicode", alt_expected: Some("ইউনিকোড"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "indexing-er", expected: "indexing-এর", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "grapheme", expected: "grapheme", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "cluster", expected: "cluster", alt_expected: Some("ক্লাস্টার"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "latency", expected: "latency", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 19: Software & Rust", input_keys: "profiling", expected: "profiling", alt_expected: None, category: "code_switch" },

        // ====================================================================
        // SECTION 20: দীর্ঘ কথোপকথন, আবেগ, রসিকতা ও দৈনন্দিন জীবনের দ্বন্দ্ব
        // ====================================================================
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "Calendar-e", expected: "Calendar-এ", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "event", expected: "event", alt_expected: Some("ইভেন্ট"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "invite-o", expected: "invite-ও", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "alarm", expected: "alarm", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "message", expected: "message", alt_expected: Some("মেসেজ"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "breakfast", expected: "breakfast", alt_expected: Some("ব্রেকফাস্ট"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "crisis", expected: "crisis", alt_expected: Some("ক্রাইসিস"), category: "code_switch" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "Wi-Fi-er", expected: "Wi-Fi-এর", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "webpage", expected: "webpage", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "refresh", expected: "refresh", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "internet-ke", expected: "internet-কে", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "review", expected: "review", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 20: Narrative & Life", input_keys: "slide", expected: "slide", alt_expected: Some("স্লাইড"), category: "code_switch" },

        // ====================================================================
        // SECTION 21: বাংলা বানান, শব্দার্থ, বিরামচিহ্ন ও Adversarial Input
        // ====================================================================
        ExtendedTestCase { section: "Sec 21: Homophones & Adversarial", input_keys: "kal", expected: "কাল", alt_expected: None, category: "homophone" },
        ExtendedTestCase { section: "Sec 21: Homophones & Adversarial", input_keys: "kalo", expected: "কালো", alt_expected: Some("কাল"), category: "homophone" },
        ExtendedTestCase { section: "Sec 21: Homophones & Adversarial", input_keys: "kajta", expected: "কাজটা", alt_expected: None, category: "common" },
        ExtendedTestCase { section: "Sec 21: Homophones & Adversarial", input_keys: "aj-i", expected: "আজই", alt_expected: Some("aj-i"), category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 21: Homophones & Adversarial", input_keys: "koro", expected: "করো", alt_expected: None, category: "verb" },
        ExtendedTestCase { section: "Sec 21: Homophones & Adversarial", input_keys: "korte", expected: "করতে", alt_expected: None, category: "verb" },
        ExtendedTestCase { section: "Sec 21: Homophones & Adversarial", input_keys: "bolini", expected: "বলিনি", alt_expected: None, category: "verb_negative" },
        ExtendedTestCase { section: "Sec 21: Homophones & Adversarial", input_keys: "ashbo", expected: "আসব", alt_expected: Some("আসবো"), category: "verb_future" },

        // ====================================================================
        // SECTION 22: অত্যন্ত জটিল Mixed-script ও Numerical Stress Test
        // ====================================================================
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "12486", expected: "১২৪৮৬", alt_expected: None, category: "numeric" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "8.4", expected: "৮.৪", alt_expected: None, category: "numeric_decimal" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "21.7", expected: "২১.৭", alt_expected: None, category: "numeric_decimal" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "47.2", expected: "৪৭.২", alt_expected: None, category: "numeric_decimal" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "CER", expected: "CER", alt_expected: None, category: "acronym" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "WER", expected: "WER", alt_expected: None, category: "acronym" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "function-ti", expected: "function-টি", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "callback-ta", expected: "callback-টা", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "synchronous", expected: "synchronous", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "execute", expected: "execute", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "normalization-er", expected: "normalization-এর", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "12", expected: "১২", alt_expected: None, category: "numeric" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "10", expected: "১০", alt_expected: None, category: "numeric" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "RFC-XXXX-er", expected: "RFC-XXXX-এর", alt_expected: None, category: "hyphen_clitic" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "Content-Type", expected: "Content-Type", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "surrogate", expected: "surrogate", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "NaN", expected: "NaN", alt_expected: None, category: "code_switch" },
        ExtendedTestCase { section: "Sec 22: Mixed & Numerical", input_keys: "Infinity", expected: "Infinity", alt_expected: None, category: "code_switch" },
    ];

    println!("\n=========================================================================================");
    println!("     EXTENDED SECTIONS 13–22: KEYSTROKE-BY-KEYSTROKE IN-FLIGHT SIMULATION                ");
    println!("=========================================================================================\n");

    let mut reports: Vec<WordSimReport> = Vec::new();
    let mut passed_count = 0;
    let mut in_candidates_count = 0;
    let total_cases = test_corpus.len();

    let mut cumulative_context = String::new();

    for tc in &test_corpus {
        session.reset();
        // Provide surrounding context accumulated from previous tests
        if !cumulative_context.is_empty() {
            session.set_context(cumulative_context.clone());
        }

        let mut step_logs: Vec<IntermediateStepLog> = Vec::new();
        let mut prev_preedit_len = 0;
        let mut flicker_count = 0;

        for (idx, ch) in tc.input_keys.chars().enumerate() {
            let res = session.process_key(ch.to_string()).expect("process_key failed");
            let preedit_len = res.preedit.chars().count();
            
            // Detection of unexpected backward syllable collapse / flicker
            if idx > 0 && preedit_len < prev_preedit_len && !ch.is_alphabetic() && ch != '-' {
                flicker_count += 1;
            }
            prev_preedit_len = preedit_len;

            step_logs.push(IntermediateStepLog {
                step: idx + 1,
                key: ch,
                preedit: res.preedit.clone(),
                candidates: res.candidates.iter().take(5).cloned().collect(),
            });
        }

        // Commit with Spacebar
        let space_res = session.handle_space().expect("handle_space failed");
        let committed = space_res.commit_text.as_deref().unwrap_or("").trim().to_string();
        let norm_committed = lekhani_android::probaho::nfc_normalize(&committed);
        let norm_expected = lekhani_android::probaho::nfc_normalize(tc.expected);
        let norm_alt = tc.alt_expected.map(lekhani_android::probaho::nfc_normalize);

        let passed = norm_committed == norm_expected
            || norm_alt.as_deref() == Some(&norm_committed);
        if passed {
            passed_count += 1;
        }

        // Check if expected candidate was surfaced anywhere in final step candidates
        let last_step_candidates = step_logs.last().map(|s| s.candidates.clone()).unwrap_or_default();
        let in_cands = last_step_candidates.iter().any(|c| {
            let nc = lekhani_android::probaho::nfc_normalize(c);
            nc == norm_expected || norm_alt.as_deref() == Some(&nc)
        });
        if in_cands || passed {
            in_candidates_count += 1;
        }

        // Update context for next word prediction test
        cumulative_context.push_str(&norm_committed);
        cumulative_context.push(' ');
        if cumulative_context.len() > 100 {
            // keep context bounded
            cumulative_context = cumulative_context.chars().rev().take(100).collect::<String>().chars().rev().collect();
        }

        reports.push(WordSimReport {
            section: tc.section.to_string(),
            input: tc.input_keys.to_string(),
            expected: tc.expected.to_string(),
            actual_committed: norm_committed,
            passed_first_choice: passed,
            found_in_candidates: in_cands,
            intermediate_flickers: flicker_count,
            steps: step_logs,
            next_word_predictions: space_res.candidates.iter().take(5).cloned().collect(),
        });
    }

    // Print summary per section
    let mut current_sec = "";
    for r in &reports {
        if r.section != current_sec {
            current_sec = &r.section;
            println!("\n>>> SECTION: {}", current_sec);
        }
        let status = if r.passed_first_choice {
            "✓ PASS [Slot 0]"
        } else if r.found_in_candidates {
            "~ PASS [Candidate Strip]"
        } else {
            "✗ FAIL"
        };
        println!(
            "  {:24} | Input: {:20} -> Committed: {:20} (Expected: {:20}) | NextWords: {:?}",
            status,
            r.input,
            r.actual_committed,
            r.expected,
            r.next_word_predictions
        );

        // If failed or flickered, print intermediate step trace
        if !r.passed_first_choice || r.intermediate_flickers > 0 {
            println!("     [Trace for '{}']:", r.input);
            for s in &r.steps {
                println!(
                    "       Step {:2} ['{}']: Preedit='{}' | Candidates={:?}",
                    s.step, s.key, s.preedit, s.candidates
                );
            }
        }
    }

    println!("\n=========================================================================================");
    println!("     EXTENDED TEST SUITE EXECUTION SUMMARY                                               ");
    println!("=========================================================================================");
    println!("  Total Test Cases Checked     : {}", total_cases);
    println!("  First-Choice Commits Passed  : {} ({:.1}%)", passed_count, (passed_count as f64 / total_cases as f64) * 100.0);
    println!("  Top Candidates Coverage      : {} ({:.1}%)", in_candidates_count, (in_candidates_count as f64 / total_cases as f64) * 100.0);
    println!("=========================================================================================\n");

    // Print granular breakdown of failure types if any
    let failures: Vec<&WordSimReport> = reports.iter().filter(|r| !r.passed_first_choice).collect();
    if !failures.is_empty() {
        println!("FAILURES REQUIRING ATTENTION (Total: {}):", failures.len());
        for f in failures {
            println!(
                "  - [{}] Input: '{}' -> Actual: '{}', Expected: '{}', Candidates: {:?}",
                f.section, f.input, f.actual_committed, f.expected, f.steps.last().map(|s| &s.candidates)
            );
        }
    }
}
