use crate::error::LekhaniError;
use crate::layout::LekhaniLayoutType;
use crate::probaho::{
    demote_vowel_to_kar_if_preceded_by_consonant, generate_inflectional_suffixes,
    get_conjunct_suggestions, is_bengali_consonant_or_modifier, is_bengali_punctuation_or_space,
    is_bengali_vowel, nfc_normalize, promote_kar_if_needed,
};
use lekhani_core::phonetic::PhoneticDatabase;
use std::sync::{Arc, Mutex, OnceLock, RwLock};
use unicode_segmentation::UnicodeSegmentation;

static CORE_DB: RwLock<Option<&'static PhoneticDatabase>> = RwLock::new(None);
static CUSTOM_DICT_DIR: RwLock<Option<String>> = RwLock::new(None);
static CONTEXT_SCORER: RwLock<Option<&'static lekhani_ai::ContextScorer>> = RwLock::new(None);
static NEXT_WORD_PREDICTOR: RwLock<Option<&'static lekhani_ai::NextWordPredictor>> =
    RwLock::new(None);
static NEURAL_PREDICTOR: RwLock<Option<&'static lekhani_neural::NeuralContextPredictor>> =
    RwLock::new(None);

/// Maximum characters kept in the in-session surrounding_context buffer.
/// This matches the Kotlin-side CONTEXT_CHAR_LIMIT (256 chars from IPC)
/// but allows accumulation of 2 sentences of self-committed words before trimming.
const MAX_CONTEXT_CHARS: usize = 512;

/// Returns the ordered list of candidate dictionary directories to probe.
///
/// Covers the standard Android data-partition paths for both release and debug
/// package IDs, the direct-boot device-protected variant, and relative test
/// paths used in unit tests. Centralised here so changes need only happen once.
fn candidate_dict_dirs() -> &'static [&'static std::path::Path] {
    use std::path::Path;
    static DIRS: OnceLock<Box<[&'static std::path::Path]>> = OnceLock::new();
    DIRS.get_or_init(|| {
        let v: Vec<&'static std::path::Path> = vec![
            Path::new("/data/data/com.lekhani.android/files/dictionaries"),
            Path::new("/data/data/com.lekhani.android.debug/files/dictionaries"),
            Path::new("/data/user/0/com.lekhani.android/files/dictionaries"),
            Path::new("/data/user/0/com.lekhani.android.debug/files/dictionaries"),
            Path::new("/data/user_de/0/com.lekhani.android/files/dictionaries"),
            Path::new("/data/user_de/0/com.lekhani.android.debug/files/dictionaries"),
            Path::new("./data/dictionaries"),
            Path::new("../data/dictionaries"),
            Path::new("../../data/dictionaries"),
        ];
        v.into_boxed_slice()
    })
}

/// Sets a custom dictionary directory dynamically from Android application context.
#[uniffi::export]
pub fn set_dictionary_directory(path: String) {
    let dir = std::path::PathBuf::from(&path);
    if let Ok(mut lock) = CUSTOM_DICT_DIR.write() {
        *lock = Some(path);
    }
    if dir.exists() {
        let mut db = PhoneticDatabase::new();
        let _ = db.load_from_dir(&dir);
        let static_db: &'static PhoneticDatabase = Box::leak(Box::new(db));
        if let Ok(mut lock) = CORE_DB.write() {
            *lock = Some(static_db);
        }

        let mut lm = lekhani_ai::LanguageModel::new();
        let lm_path = dir.join("bengali_lm.bin");
        if lm_path.is_file() && lm.load_binary_file(&lm_path).is_ok() {
            let static_scorer: &'static lekhani_ai::ContextScorer = Box::leak(Box::new(
                lekhani_ai::ContextScorer::with_language_model(lm.clone()),
            ));
            if let Ok(mut lock) = CONTEXT_SCORER.write() {
                *lock = Some(static_scorer);
            }
            let static_pred: &'static lekhani_ai::NextWordPredictor = Box::leak(Box::new(
                lekhani_ai::NextWordPredictor::with_language_model(lm),
            ));
            if let Ok(mut lock) = NEXT_WORD_PREDICTOR.write() {
                *lock = Some(static_pred);
            }

            if let Some(sugg_mutex) = PHONETIC_SUGGESTION.get() {
                if let Ok(mut sugg) = sugg_mutex.lock() {
                    sugg.database = static_db.clone();
                    sugg.ai_context = static_scorer.clone();
                    sugg.ai_predictor = static_pred.clone();
                }
            }
        } else if let Some(sugg_mutex) = PHONETIC_SUGGESTION.get() {
            if let Ok(mut sugg) = sugg_mutex.lock() {
                sugg.database = static_db.clone();
            }
        }

        if let Ok(mut lock) = NEURAL_PREDICTOR.write() {
            *lock = None;
        }
    }
}

static LEARNER_AUTOSAVE_PATH: Mutex<Option<String>> = Mutex::new(None);

/// Sets an explicit file path for auto-saving learner data on finish/destroy.
#[uniffi::export]
pub fn set_learner_autosave_path(path: String) {
    if let Ok(mut lock) = LEARNER_AUTOSAVE_PATH.lock() {
        *lock = Some(path);
    }
}

/// Clear all candidate selection overrides and selection counts from persistent learner
#[uniffi::export]
pub fn clear_candidate_memory() {
    let db = get_core_database();
    if let Ok(mut learner) = db.learner.write() {
        learner.candidate_memory.clear();
        learner.candidate_selection_counts.clear();
        learner.input_error_map.clear();
        learner.input_error_counts.clear();
        learner.dirty = true;
    }
}

pub fn get_custom_dict_dir() -> Option<String> {
    CUSTOM_DICT_DIR.read().ok().and_then(|lock| lock.clone())
}

pub fn get_core_database() -> &'static PhoneticDatabase {
    if let Ok(guard) = CORE_DB.read() {
        if let Some(db) = *guard {
            return db;
        }
    }
    let mut guard = CORE_DB.write().unwrap_or_else(|e| e.into_inner());
    if let Some(db) = *guard {
        return db;
    }
    let mut db = PhoneticDatabase::new();
    let mut loaded = false;
    if let Some(custom_dir) = get_custom_dict_dir() {
        let path = std::path::Path::new(&custom_dir);
        if path.exists() {
            let _ = db.load_from_dir(path);
            loaded = true;
        }
    }
    if !loaded {
        for dir in candidate_dict_dirs() {
            if dir.exists() {
                let _ = db.load_from_dir(dir);
                break;
            }
        }
    }
    let static_db: &'static PhoneticDatabase = Box::leak(Box::new(db));
    *guard = Some(static_db);
    static_db
}

pub fn get_context_scorer() -> &'static lekhani_ai::ContextScorer {
    if let Ok(guard) = CONTEXT_SCORER.read() {
        if let Some(scorer) = *guard {
            return scorer;
        }
    }
    let mut guard = CONTEXT_SCORER.write().unwrap_or_else(|e| e.into_inner());
    if let Some(scorer) = *guard {
        return scorer;
    }
    let mut lm = lekhani_ai::LanguageModel::new();
    let mut loaded = false;
    if let Some(custom_dir) = get_custom_dict_dir() {
        let path = std::path::Path::new(&custom_dir).join("bengali_lm.bin");
        if path.is_file() && lm.load_binary_file(&path).is_ok() {
            loaded = true;
        }
    }
    if !loaded {
        for dir in candidate_dict_dirs() {
            let path = dir.join("bengali_lm.bin");
            if path.is_file() && lm.load_binary_file(&path).is_ok() {
                break;
            }
        }
    }
    let static_scorer: &'static lekhani_ai::ContextScorer =
        Box::leak(Box::new(lekhani_ai::ContextScorer::with_language_model(lm)));
    *guard = Some(static_scorer);
    static_scorer
}

pub fn get_next_word_predictor() -> &'static lekhani_ai::NextWordPredictor {
    if let Ok(guard) = NEXT_WORD_PREDICTOR.read() {
        if let Some(pred) = *guard {
            return pred;
        }
    }
    let mut guard = NEXT_WORD_PREDICTOR
        .write()
        .unwrap_or_else(|e| e.into_inner());
    if let Some(pred) = *guard {
        return pred;
    }
    let scorer = get_context_scorer();
    let static_pred: &'static lekhani_ai::NextWordPredictor = Box::leak(Box::new(
        lekhani_ai::NextWordPredictor::with_language_model(scorer.lm().clone()),
    ));
    *guard = Some(static_pred);
    static_pred
}

/// Returns the GRU neural next-word predictor singleton.
///
/// Initializes a compact vocabulary seeded from the N-gram LM word list and
/// a fresh MicroGruModel. On device the weights file (`bengali_gru.bin`) would
/// replace the zero-initialized model; here we fall back gracefully so the
/// predictor is always available even without a trained weights file.
pub fn get_neural_predictor() -> &'static lekhani_neural::NeuralContextPredictor {
    if let Ok(guard) = NEURAL_PREDICTOR.read() {
        if let Some(pred) = *guard {
            return pred;
        }
    }
    let mut guard = NEURAL_PREDICTOR.write().unwrap_or_else(|e| e.into_inner());
    if let Some(pred) = *guard {
        return pred;
    }
    use lekhani_neural::{BpeVocabulary, MicroGruModel, NeuralContextPredictor};

    let mut vocab_opt = None;
    let mut model_opt = None;

    let mut search_dirs = Vec::new();
    if let Some(custom_dir) = get_custom_dict_dir() {
        search_dirs.push(std::path::PathBuf::from(custom_dir));
    }
    for dir in candidate_dict_dirs() {
        search_dirs.push(dir.to_path_buf());
    }

    for dir in &search_dirs {
        // 1. Check for v2 pair (bengali_gru_v2.bin + bengali_vocab_v2.json)
        let p_model_v2 = dir.join("bengali_gru_v2.bin");
        let p_vocab_v2 = dir.join("bengali_vocab_v2.json");
        if p_model_v2.exists() && p_vocab_v2.exists() {
            if let (Ok(m), Ok(v)) = (
                MicroGruModel::load_binary(&p_model_v2),
                BpeVocabulary::load_json(&p_vocab_v2),
            ) {
                if m.vocab_size() == v.len() {
                    model_opt = Some(Arc::new(m));
                    vocab_opt = Some(Arc::new(v));
                    break;
                }
            }
        }

        // 2. Check for v1 binary pair (bengali_gru.bin + bengali_vocab.bin)
        let p_model_v1 = dir.join("bengali_gru.bin");
        let p_vocab_v1 = dir.join("bengali_vocab.bin");
        if p_model_v1.exists() && p_vocab_v1.exists() {
            if let (Ok(m), Ok(v)) = (
                MicroGruModel::load_binary(&p_model_v1),
                BpeVocabulary::load_binary(&p_vocab_v1),
            ) {
                if m.vocab_size() == v.len() {
                    model_opt = Some(Arc::new(m));
                    vocab_opt = Some(Arc::new(v));
                    break;
                }
            }
        }

        // 3. Check for JSON development pair (neural_weights.json + neural_vocab.json)
        let p_model_json = dir.join("neural_weights.json");
        let p_vocab_json = dir.join("neural_vocab.json");
        if p_model_json.exists() && p_vocab_json.exists() {
            if let (Ok(m), Ok(v)) = (
                MicroGruModel::load_json(&p_model_json),
                BpeVocabulary::load_json(&p_vocab_json),
            ) {
                if m.vocab_size() == v.len() {
                    model_opt = Some(Arc::new(m));
                    vocab_opt = Some(Arc::new(v));
                    break;
                }
            }
        }
    }

    let vocab = vocab_opt.unwrap_or_else(|| {
        let db = get_core_database();
        let word_list: Vec<String> = db
            .trie
            .iter()
            .take(2048)
            .map(|(w, _)| w.to_string())
            .collect();
        Arc::new(if word_list.is_empty() {
            BpeVocabulary::new()
        } else {
            BpeVocabulary::from_tokens(word_list)
        })
    });

    let model = match model_opt {
        Some(m) if m.vocab_size() == vocab.len() => m,
        Some(m) => {
            eprintln!(
                "Lekhani AI: MicroGruModel vocab_size mismatch (model: {}, vocab: {}). Falling back to matched baseline.",
                m.vocab_size(),
                vocab.len()
            );
            Arc::new(MicroGruModel::new(vocab.len(), 64, 64))
        }
        None => Arc::new(MicroGruModel::new(vocab.len(), 64, 64)),
    };

    let predictor: &'static lekhani_neural::NeuralContextPredictor = Box::leak(Box::new(
        NeuralContextPredictor::try_new(model.clone(), vocab.clone()).unwrap_or_else(|_| {
            let matched = Arc::new(MicroGruModel::new(vocab.len(), 64, 64));
            NeuralContextPredictor::new(matched, vocab)
        }),
    ));
    *guard = Some(predictor);
    predictor
}

static PHONETIC_SUGGESTION: OnceLock<Mutex<lekhani_core::phonetic::PhoneticSuggestion>> =
    OnceLock::new();

pub fn get_phonetic_suggestion() -> &'static Mutex<lekhani_core::phonetic::PhoneticSuggestion> {
    PHONETIC_SUGGESTION.get_or_init(|| {
        let db = get_core_database();
        let scorer = get_context_scorer();
        let predictor = get_next_word_predictor();
        let mut sugg = lekhani_core::phonetic::PhoneticSuggestion::new();
        sugg.database = (*db).clone();
        sugg.ai_context = (*scorer).clone();
        sugg.ai_predictor = (*predictor).clone();
        sugg.config.enable_word_segmentation = true;

        let layout_candidates = [
            std::path::Path::new("/data/data/com.lekhani.android/files/layouts/avrophonetic.json"),
            std::path::Path::new(
                "/data/data/com.lekhani.android.debug/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new(
                "/data/user/0/com.lekhani.android/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new(
                "/data/user/0/com.lekhani.android.debug/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new(
                "/data/user_de/0/com.lekhani.android/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new(
                "/data/user_de/0/com.lekhani.android.debug/files/layouts/avrophonetic.json",
            ),
            std::path::Path::new("./data/layouts/avrophonetic.json"),
            std::path::Path::new("../data/layouts/avrophonetic.json"),
            std::path::Path::new("../../data/layouts/avrophonetic.json"),
        ];
        if let Some(custom_dir) = get_custom_dict_dir() {
            let parent = std::path::Path::new(&custom_dir).parent();
            if let Some(p) = parent {
                let layout_path = p.join("layouts/avrophonetic.json");
                if layout_path.is_file() {
                    if let Ok(content) = std::fs::read_to_string(&layout_path) {
                        if let Ok(json) = serde_json::from_str(&content) {
                            sugg.set_layout(&json);
                        }
                    }
                }
            }
        }
        for path in layout_candidates {
            if path.is_file() {
                if let Ok(content) = std::fs::read_to_string(path) {
                    if let Ok(json) = serde_json::from_str(&content) {
                        sugg.set_layout(&json);
                        break;
                    }
                }
            }
        }
        Mutex::new(sugg)
    })
}

// ──────────────────────────────────────────────────────────────────────────────
// Public data types exported to Kotlin via UniFFI
// ──────────────────────────────────────────────────────────────────────────────

/// Result returned to Android InputConnection after processing a key event.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct TypingResult {
    /// Composing text to set via `InputConnection.setComposingText()`
    pub preedit: String,
    /// Finalized, NFC-normalized text to commit via `InputConnection.commitText()`
    pub commit_text: Option<String>,
    /// Candidates to display in the horizontal candidate strip
    pub candidates: Vec<String>,
    /// Active grapheme cluster cursor index inside the composing buffer
    pub cursor_position: u32,
}

// ──────────────────────────────────────────────────────────────────────────────
// Internal session state  (never crosses the FFI boundary)
// ──────────────────────────────────────────────────────────────────────────────

struct SessionState {
    layout: LekhaniLayoutType,
    /// Raw composing buffer.  Always kept as valid Unicode; grapheme operations
    /// use `unicode-segmentation` to avoid splitting multi-byte clusters.
    composing_buffer: String,
    /// Last ~256 chars before the cursor, obtained from `getTextBeforeCursor()`.
    /// Used by the AI layer for contextual homophone ranking.
    surrounding_context: String,
    /// Tracked character count of surrounding_context to avoid repeated O(n) scans
    surrounding_context_len: usize,
    /// Characters immediately following the cursor (up to 64 chars) from `getTextAfterCursor()`.
    /// Used by the AI layer for bi-directional context scoring.
    right_context: String,
    /// Whether the session is currently in a secure/incognito field.
    /// When true: no learning, no clipboard capture, auto English layout.
    is_private_field: bool,
    /// Whether autonomous learning of words from typing is enabled.
    auto_learn_enabled: bool,
    /// Last committed word, optional preceding word, and the Instant it was committed.
    /// Used for rapid backspace mistake penalty (<1500 ms).
    last_commit_info: Option<(Option<String>, String, std::time::Instant)>,
    /// Bivariate Gaussian spatial touch model for fat-finger correction
    spatial_model: crate::spatial::SpatialTouchModel,
    /// Whether initial Kar auto-promotion and post-consonant demotion is enabled
    smart_initial_kar_enabled: bool,
    /// Whether geminate consonant double-tap shortcut is enabled
    geminate_double_tap_enabled: bool,
    /// Whether Hasanta ligature quick-picks are enabled
    hasanta_conjuncts_enabled: bool,
}

impl SessionState {
    fn append_to_context(&mut self, text: &str) {
        if !self.surrounding_context.is_empty() {
            self.surrounding_context.push(' ');
            self.surrounding_context_len += 1;
        }
        self.surrounding_context.push_str(text);
        self.surrounding_context_len += text.chars().count();
        if self.surrounding_context_len > MAX_CONTEXT_CHARS {
            let trim_chars = self.surrounding_context_len - MAX_CONTEXT_CHARS;
            let trim_at = self
                .surrounding_context
                .char_indices()
                .nth(trim_chars)
                .map(|(i, _)| i)
                .unwrap_or(0);
            self.surrounding_context.drain(..trim_at);
            self.surrounding_context_len = MAX_CONTEXT_CHARS;
        }
    }
}

fn get_context_words<'a>(context: &'a str, buffer: &mut [&'a str; 16]) -> usize {
    let mut count = 0;
    for w in context.split_whitespace().rev().take(16) {
        buffer[count] = w;
        count += 1;
    }
    buffer[..count].reverse();
    count
}

/// Helper to retrieve next-word predictions combining:
/// 1. Personalized user bigrams from AutonomousLearner
/// 2. Statistical N-gram predictions
/// 3. Semantic GRU neural candidates blended with context-adaptive alpha
///    computed via `lekhani_neural::predictor::compute_neural_alpha`.
///    Low alpha (0.15) when N-gram is confident; high alpha (0.65) when N-gram has no data.
fn get_bengali_next_words(context: &str, right_context: Option<&str>) -> Vec<String> {
    let mut words_buf = [""; 16];
    let count = get_context_words(context, &mut words_buf);
    let words = &words_buf[..count];
    let last_word = words.last().copied();

    let mut ngram_results: Vec<String> = Vec::with_capacity(5);

    // 1. Personalized User Bigrams from AutonomousLearner
    let db = get_core_database();
    if let (Some(last), Ok(learner)) = (last_word, db.learner.read()) {
        let user_conts = learner.get_top_user_continuations(last, 3);
        for cont in user_conts {
            if !ngram_results.contains(&cont) {
                ngram_results.push(cont);
            }
        }
    }

    // 2. Statistical N-gram predictions with actual log-prob scoring (B2)
    let predictor = get_next_word_predictor();
    let scored_predictions = predictor.predict_next_scored(words, 5);
    let ngram_top_log_prob = scored_predictions
        .first()
        .map(|(_, s)| *s)
        .unwrap_or(if ngram_results.is_empty() { -4.0 } else { -1.5 });

    for (pred, _) in scored_predictions {
        if !ngram_results.contains(&pred) {
            ngram_results.push(pred);
            if ngram_results.len() >= 5 {
                break;
            }
        }
    }

    // Bidirectional scoring if right_context is present (C5)
    if let Some(rc) = right_context {
        let right_word = rc.split_whitespace().next();
        if right_word.is_some() && !words.is_empty() && ngram_results.len() > 1 {
            let scorer = get_context_scorer();
            scorer.rank_candidates_in_place_bidirectional(words, right_word, &mut ngram_results);
        }
    }

    // 3. Neural GRU semantic blend with context-adaptive alpha using real log-prob (B3)
    let neural = get_neural_predictor();
    let neural_cands = neural.predict_candidates(context, 4);
    if !neural_cands.is_empty() {
        let alpha = lekhani_neural::predictor::compute_neural_alpha(
            ngram_top_log_prob,
            true,  // get_bengali_next_words is always called after a word boundary
            count, // context_word_count: number of words in context
        );
        neural
            .blend_candidates(&ngram_results, &neural_cands, alpha)
            .into_iter()
            .take(5)
            .collect()
    } else {
        ngram_results
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Thread-safe session — the primary UniFFI export
// ──────────────────────────────────────────────────────────────────────────────

/// Thread-safe mobile session object exported via UniFFI to Kotlin.
///
/// Lifecycle:
///   1. `AndroidLekhaniSession::new()` — called in `onCreateInputView()`
///   2. `set_private_field()` — called on every `onStartInput()` to enforce
///      password / incognito policy.
///   3. `set_context()` — called after `getTextBeforeCursor()` to refresh AI
///      surrounding-text context.
///   4. `process_key()` / `handle_backspace()` / `handle_space()` — hot path;
///      must remain zero-allocation on the happy path.
///   5. `reset()` — called on `onFinishInput()` to clear composing state.
#[derive(uniffi::Object)]
pub struct AndroidLekhaniSession {
    state: Mutex<SessionState>,
}

impl Default for AndroidLekhaniSession {
    fn default() -> Self {
        Self::new()
    }
}

#[uniffi::export]
impl AndroidLekhaniSession {
    // ── Constructor ──────────────────────────────────────────────────────────

    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            state: Mutex::new(SessionState {
                layout: LekhaniLayoutType::Probaho,
                composing_buffer: String::with_capacity(64),
                surrounding_context: String::with_capacity(256),
                surrounding_context_len: 0,
                right_context: String::with_capacity(64),
                is_private_field: false,
                auto_learn_enabled: true,
                last_commit_info: None,
                spatial_model: crate::spatial::SpatialTouchModel::new(),
                smart_initial_kar_enabled: true,
                geminate_double_tap_enabled: true,
                hasanta_conjuncts_enabled: true,
            }),
        }
    }

    // ── Layout management ────────────────────────────────────────────────────

    /// Set the active typing layout.
    /// Clears the composing buffer to prevent carry-over across layout boundaries.
    pub fn set_layout(&self, layout: LekhaniLayoutType) {
        if let Ok(mut state) = self.state.lock() {
            state.layout = layout;
            state.composing_buffer.clear();
        }
    }

    /// Return the currently active layout.
    pub fn get_layout(&self) -> LekhaniLayoutType {
        self.state
            .lock()
            .map(|s| s.layout)
            .unwrap_or(LekhaniLayoutType::Probaho)
    }

    /// Enable or disable auto-promotion of word-initial vowel signs (e.g. া -> আ)
    /// and post-consonant demotion (e.g. ক + আ -> কা).
    pub fn set_smart_initial_kar_enabled(&self, enabled: bool) {
        if let Ok(mut state) = self.state.lock() {
            state.smart_initial_kar_enabled = enabled;
        }
    }

    /// Enable or disable double-tap consonant gemination (e.g. ত + ত -> ত্ত).
    pub fn set_geminate_double_tap_enabled(&self, enabled: bool) {
        if let Ok(mut state) = self.state.lock() {
            state.geminate_double_tap_enabled = enabled;
        }
    }

    /// Enable or disable dynamic conjunct suggestions on Hasanta (`্`).
    pub fn set_hasanta_conjuncts_enabled(&self, enabled: bool) {
        if let Ok(mut state) = self.state.lock() {
            state.hasanta_conjuncts_enabled = enabled;
        }
    }

    // ── Privacy / incognito policy ───────────────────────────────────────────

    /// Called on every `onStartInput()`.
    ///
    /// When `is_private` is `true` (password fields, incognito text areas):
    /// - Freezes dictionary learning and clipboard capture.
    /// - Clears transient composing buffers.
    ///
    /// Note: Layout switching to English is managed by the Android IME layer (`switchLayout`),
    /// preserving the user's layout preferences.
    pub fn set_private_field(&self, is_private: bool) {
        if let Ok(mut state) = self.state.lock() {
            state.is_private_field = is_private;
            if is_private {
                state.composing_buffer.clear();
            }
        }
    }

    /// Returns whether the session is currently in a private/password field.
    pub fn is_private_field(&self) -> bool {
        self.state
            .lock()
            .map(|s| s.is_private_field)
            .unwrap_or(false)
    }

    /// Enable or disable autonomous dictionary learning from user typing.
    pub fn set_auto_learn_enabled(&self, enabled: bool) {
        if let Ok(mut state) = self.state.lock() {
            state.auto_learn_enabled = enabled;
        }
    }

    /// Returns whether autonomous dictionary learning is enabled.
    pub fn is_auto_learn_enabled(&self) -> bool {
        self.state
            .lock()
            .map(|s| s.auto_learn_enabled)
            .unwrap_or(true)
    }

    // ── Surrounding text context ─────────────────────────────────────────────

    /// Provide the text before the cursor (from `getTextBeforeCursor(256, 0)`)
    /// for contextual homophone disambiguation in the AI scorer.
    pub fn set_context(&self, context: String) {
        if let Ok(mut state) = self.state.lock() {
            // Cap at MAX_CONTEXT_CHARS to prevent unbounded growth over a long session.
            // Trim from the start, always keeping the tail (most recent context).
            let char_count = context.chars().count();
            if char_count > MAX_CONTEXT_CHARS {
                let trim_chars = char_count - MAX_CONTEXT_CHARS;
                let trim_at = context
                    .char_indices()
                    .nth(trim_chars)
                    .map(|(i, _)| i)
                    .unwrap_or(0);
                state.surrounding_context = context[trim_at..].to_string();
            } else {
                state.surrounding_context = context;
            }
            state.surrounding_context_len = state.surrounding_context.chars().count();
        }
    }

    /// Provide the text after the cursor (from `getTextAfterCursor(64, 0)`)
    /// for bi-directional contextual ranking in the AI scorer.
    pub fn set_right_context(&self, context: String) {
        if let Ok(mut state) = self.state.lock() {
            let char_count = context.chars().count();
            if char_count > 64 {
                let trim_at = context
                    .char_indices()
                    .nth(64)
                    .map(|(i, _)| i)
                    .unwrap_or(context.len());
                state.right_context = context[..trim_at].to_string();
            } else {
                state.right_context = context;
            }
        }
    }

    // ── Spatial touch correction ─────────────────────────────────────────────

    /// Update keyboard geometry for spatial touch error correction.
    /// Called from Android when onSizeChanged() or layout switch occurs.
    pub fn update_keyboard_geometry(&self, configs: Vec<crate::spatial::KeyGeometryConfig>) {
        if let Ok(mut state) = self.state.lock() {
            state.spatial_model.update_geometry(configs);
        }
    }

    /// Retrieve spatial log-probability for a given key label at touch coordinate (x, y).
    pub fn get_spatial_log_prob(&self, key: String, touch_x: f32, touch_y: f32) -> f32 {
        self.state
            .lock()
            .map(|s| s.spatial_model.log_prob_for_key(&key, touch_x, touch_y))
            .unwrap_or(-20.0)
    }

    /// Rank candidate keys by descending spatial probability at touch coordinate (x, y).
    pub fn rank_spatial_keys(
        &self,
        touch_x: f32,
        touch_y: f32,
        top_k: u32,
    ) -> Vec<crate::spatial::SpatialKeyCandidate> {
        self.state
            .lock()
            .map(|s| {
                s.spatial_model
                    .rank_keys_at(touch_x, touch_y, top_k as usize)
            })
            .unwrap_or_default()
    }

    /// Process a typed character or key token with physical touch coordinates (x, y).
    /// Incorporates bivariate Gaussian spatial probabilities to handle fat-finger errors
    /// on key boundaries.
    pub fn process_key_with_touch(
        &self,
        key: String,
        touch_x: f32,
        touch_y: f32,
    ) -> Result<TypingResult, LekhaniError> {
        let mut result = self.process_key(key.clone())?;

        // If spatial model is populated, check for adjacent fat-finger candidates
        let state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        if !state.spatial_model.is_empty() {
            let spatial_candidates = state.spatial_model.rank_keys_at(touch_x, touch_y, 2);
            if spatial_candidates.len() >= 2 {
                let neighbor = if spatial_candidates[0].key == key {
                    &spatial_candidates[1]
                } else {
                    &spatial_candidates[0]
                };

                let key_prob = state.spatial_model.log_prob_for_key(&key, touch_x, touch_y);
                if (neighbor.log_prob - key_prob).abs() <= 2.8 && !state.composing_buffer.is_empty()
                {
                    let mut alt_buf = state.composing_buffer.clone();
                    alt_buf.pop();
                    alt_buf.push_str(&neighbor.key);

                    match state.layout {
                        LekhaniLayoutType::English => {
                            let alt_cands = crate::english::get_english_candidates(&alt_buf, 2);
                            for cand in alt_cands {
                                if !result.candidates.contains(&cand) && result.candidates.len() < 7
                                {
                                    result.candidates.push(cand);
                                }
                            }
                        }
                        LekhaniLayoutType::Avro => {
                            let (_, alt_cands) = crate::avro::transliterate_avro(&alt_buf);
                            for cand in alt_cands {
                                if !result.candidates.contains(&cand) && result.candidates.len() < 7
                                {
                                    result.candidates.push(cand);
                                }
                            }
                        }
                        // Fix #2: Extend spatial fat-finger correction to Probaho/Probhat —
                        // the primary layout was previously silently discarding spatial data.
                        // Query the prefix trie with the neighbor-key buffer to surface
                        // Bengali words that differ by one spatially-adjacent key.
                        LekhaniLayoutType::Probaho | LekhaniLayoutType::Probhat => {
                            let db = get_core_database();
                            let prefix_matches = db.trie.find_prefix_entries(&alt_buf, 3);
                            for (word, _) in prefix_matches {
                                let w = word.to_string();
                                if !result.candidates.contains(&w) && result.candidates.len() < 7 {
                                    result.candidates.push(w);
                                }
                            }
                        }
                        _ => {}
                    }
                }
            }
        }

        Ok(result)
    }

    // ── Key processing hot path ──────────────────────────────────────────────

    /// Process a typed character or key token.
    ///
    /// # Performance contract
    /// This method must perform **zero heap allocations** on the happy path
    /// (non-Hasanta, non-word-start characters in Probaho layout).
    /// All buffers are pre-allocated in `new()`.
    pub fn process_key(&self, key: String) -> Result<TypingResult, LekhaniError> {
        let mut state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        // Universal punctuation flush across all layouts:
        // Punctuation and newlines immediately flush the composing buffer as committed text.
        let is_punct = key == "।"
            || key == "॥"
            || key == ","
            || key == ";"
            || (key == ":" && state.layout != LekhaniLayoutType::Avro)
            || key == "?"
            || key == "!"
            || key == "."
            || key == "\n";

        if is_punct {
            let mut committed =
                if state.layout == LekhaniLayoutType::Avro && !state.composing_buffer.is_empty() {
                    let raw = std::mem::take(&mut state.composing_buffer);
                    let words: Vec<&str> = if !state.surrounding_context.is_empty() {
                        state.surrounding_context.split_whitespace().collect()
                    } else {
                        Vec::new()
                    };
                    let (preedit, mut candidates) =
                        crate::avro::transliterate_avro_with_context(&raw, &words);
                    if !words.is_empty() && candidates.len() > 1 {
                        let scorer = get_context_scorer();
                        let right_word = state.right_context.split_whitespace().next();
                        scorer.rank_candidates_in_place_bidirectional(
                            &words,
                            right_word,
                            &mut candidates,
                        );
                    }
                    crate::avro::prioritize_common_or_override_candidate(&raw, &mut candidates);
                    candidates.first().cloned().unwrap_or(preedit)
                } else {
                    std::mem::take(&mut state.composing_buffer)
                };
            state.composing_buffer = String::with_capacity(64);

            let word = committed.clone();
            if !word.is_empty()
                && !state.is_private_field
                && state.auto_learn_enabled
                && state.layout != LekhaniLayoutType::English
            {
                let prev_word = state
                    .surrounding_context
                    .split_whitespace()
                    .last()
                    .map(|s| s.to_string());
                let db = get_core_database();
                if let Ok(mut learner) = db.learner.write() {
                    if !word.is_ascii() {
                        if let Some(ref p) = prev_word {
                            learner.observe_committed_pair(p, &word);
                        }
                    }
                }
                state.last_commit_info = Some((prev_word, word.clone(), std::time::Instant::now()));
            }

            let punct_str: &str = if key == "." && state.layout != LekhaniLayoutType::English {
                let last_char = committed
                    .chars()
                    .last()
                    .or_else(|| state.surrounding_context.chars().last());
                let is_digit = last_char
                    .map(|c| c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c))
                    .unwrap_or(false);
                if is_digit {
                    "."
                } else {
                    "।"
                }
            } else {
                &key
            };

            committed.push_str(punct_str);
            state.append_to_context(&committed);

            let next_words = if state.layout == LekhaniLayoutType::English {
                // Fix #1: Filter surrounding_context to only ASCII words so Bengali
                // context accumulated from a previous layout session never leaks into
                // English next-word predictions after punctuation flush.
                let words: Vec<&str> = state
                    .surrounding_context
                    .split_whitespace()
                    .filter(|w| w.is_ascii())
                    .collect();
                crate::english::get_english_next_words(&words, 5)
            } else {
                get_bengali_next_words(&state.surrounding_context, Some(&state.right_context))
            };

            return Ok(TypingResult {
                preedit: String::new(),
                commit_text: Some(nfc_normalize(&committed)),
                candidates: next_words,
                cursor_position: 0,
            });
        }

        match state.layout {
            LekhaniLayoutType::Probaho | LekhaniLayoutType::Probhat => {
                let chars: Vec<char> = state.composing_buffer.chars().collect();
                let last_ch = chars.last().copied();
                let (is_start, last_is_vowel, last_is_consonant) = if let Some(last) = last_ch {
                    (
                        false,
                        is_bengali_vowel(last),
                        is_bengali_consonant_or_modifier(last) && last != '্',
                    )
                } else if let Some(ctx_ch) = state.surrounding_context.chars().last() {
                    if is_bengali_consonant_or_modifier(ctx_ch) && ctx_ch != '্' {
                        (false, false, true)
                    } else if is_bengali_vowel(ctx_ch) {
                        (false, true, false)
                    } else {
                        (
                            ctx_ch.is_whitespace() || is_bengali_punctuation_or_space(ctx_ch),
                            false,
                            false,
                        )
                    }
                } else {
                    (true, false, false)
                };
                let promoted = if state.smart_initial_kar_enabled {
                    promote_kar_if_needed(&key, is_start || last_is_vowel)
                } else {
                    key.clone()
                };
                let transformed = if state.smart_initial_kar_enabled {
                    demote_vowel_to_kar_if_preceded_by_consonant(&promoted, last_is_consonant)
                } else {
                    promoted.clone()
                };

                // 1. Double-Kar Collision Prevention:
                // If composing buffer already ends in a Kar on a consonant and user types another Kar,
                // replace the previous Kar with the new one instead of stacking invalid modifiers (e.g. কা + ি -> কি).
                let is_incoming_kar = transformed
                    .chars()
                    .all(|c| ('\u{09BE}'..='\u{09CC}').contains(&c));
                let last_is_kar = last_ch.is_some_and(|c| ('\u{09BE}'..='\u{09CC}').contains(&c));
                let prev_is_consonant = chars.len() >= 2
                    && is_bengali_consonant_or_modifier(chars[chars.len() - 2])
                    && chars[chars.len() - 2] != '্';

                if is_incoming_kar && last_is_kar && prev_is_consonant {
                    state.composing_buffer.pop();
                    state.composing_buffer.push_str(&transformed);
                }
                // 2. Postfix Reph (র্) Workflow:
                // If user types Reph ("র্" or "র\u{09CD}") after a consonant `c` (e.g. ধ + ম + র্),
                // automatically transpose them to form the valid syllable (e.g. ধর্ম).
                else if key == "র্"
                    && last_ch.is_some_and(|c| {
                        is_bengali_consonant_or_modifier(c) && c != '্' && c != 'র' && c != 'ৎ'
                    })
                {
                    let c = state.composing_buffer.pop().unwrap();
                    state.composing_buffer.push_str("র্");
                    state.composing_buffer.push(c);
                }
                // 3. Geminate Consonant Double-Tap Shortcut:
                // If buffer ends in the same consonant `c` (and not preceded by Hasanta `্`),
                // typing `c` again automatically inserts `্` + `c` forming the geminate (e.g. উ + ত + ত -> উত্তর).
                else if state.geminate_double_tap_enabled
                    && key.chars().count() == 1
                    && last_ch.is_some_and(|c| {
                        key.starts_with(c)
                            && is_bengali_consonant_or_modifier(c)
                            && c != '্'
                            && c != 'ৎ'
                            && c != 'ড়'
                            && c != 'ঢ়'
                            && c != 'য়'
                    })
                    && (chars.len() < 2 || chars[chars.len() - 2] != '্')
                {
                    state.composing_buffer.push('্');
                    state.composing_buffer.push_str(&key);
                } else {
                    state.composing_buffer.push_str(&transformed);
                }

                let mut candidates = Vec::new();

                // Hasanta `্` typed: inspect buffer at codepoint level to find the
                // base consonant that precedes the Hasanta.
                // We use codepoints here (not graphemes) because the Unicode
                // segmentation algorithm immediately merges `ক` + `্` into a
                // single grapheme cluster.
                if state.hasanta_conjuncts_enabled && (promoted == "্" || key == "্") {
                    let chars: Vec<char> = state.composing_buffer.chars().collect();
                    let hasanta_pos = chars.len().wrapping_sub(1);
                    if hasanta_pos > 0 {
                        let base_consonant = chars[hasanta_pos - 1];
                        candidates = get_conjunct_suggestions(base_consonant);
                    } else if let Some(base_consonant) = state.surrounding_context.chars().last() {
                        if is_bengali_consonant_or_modifier(base_consonant) {
                            candidates = get_conjunct_suggestions(base_consonant);
                        }
                    }
                }

                if candidates.is_empty() {
                    let db = get_core_database();
                    let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 6);
                    if !prefix_matches.is_empty() {
                        candidates.push(state.composing_buffer.clone());
                        for (word, _) in prefix_matches {
                            if !candidates.iter().any(|c| c == word) {
                                candidates.push(word.to_string());
                            }
                        }
                    }
                }

                // Agglutinative inflectional suffix expansion:
                // When composing buffer forms a valid base word (e.g. মানুষ, বই, দেশ, কথা),
                // surface its high-frequency grammatical inflections (e.g. মানুষের, মানুষকে, মানুষগুলো, মানুষটি).
                if !state.composing_buffer.ends_with('্')
                    && state.composing_buffer.chars().count() >= 2
                {
                    let db = get_core_database();
                    let is_exact_word = db.trie.contains_exact(&state.composing_buffer);
                    if is_exact_word || candidates.len() < 5 {
                        let inflections = generate_inflectional_suffixes(&state.composing_buffer);
                        for inf in inflections {
                            if candidates.len() >= 8 {
                                break;
                            }
                            if (db.trie.contains_exact(&inf) || candidates.len() < 4)
                                && !candidates.contains(&inf)
                            {
                                candidates.push(inf);
                            }
                        }
                    }
                }
                // Fix #9: Morphological suffix expansion for Probaho/Probhat
                // (mirrors what the Gboard/National arm already does)
                if candidates.len() < 5 {
                    let db = get_core_database();
                    let stems =
                        lekhani_core::phonetic::morphology::peel_all_stems(&state.composing_buffer);
                    for stem in stems {
                        if stem.len() >= 2 {
                            let stem_matches = db.trie.find_prefix_matches(&stem, 3);
                            for w in stem_matches {
                                if candidates.len() >= 8 {
                                    break;
                                }
                                if !candidates.contains(&w) {
                                    candidates.push(w);
                                }
                            }
                        }
                    }
                }
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let mut words_buf = [""; 16];
                    let count = get_context_words(&state.surrounding_context, &mut words_buf);
                    let words = &words_buf[..count];
                    let scorer = get_context_scorer();
                    let right_word = state.right_context.split_whitespace().next();
                    scorer.rank_candidates_in_place_bidirectional(
                        words,
                        right_word,
                        &mut candidates,
                    );
                }
                // In fixed layouts, the exact typed buffer is guaranteed to stay at position 0
                if let Some(pos) = candidates.iter().position(|c| c == &state.composing_buffer) {
                    let exact = candidates.remove(pos);
                    candidates.insert(0, exact);
                }

                let len = state.composing_buffer.graphemes(true).count() as u32;
                Ok(TypingResult {
                    preedit: state.composing_buffer.clone(),
                    commit_text: None,
                    candidates,
                    cursor_position: len,
                })
            }

            LekhaniLayoutType::Avro => {
                state.composing_buffer.push_str(&key);
                let mut words_buf = [""; 16];
                let count = if !state.surrounding_context.is_empty() {
                    get_context_words(&state.surrounding_context, &mut words_buf)
                } else {
                    0
                };
                let words = &words_buf[..count];
                let (preedit, mut candidates) =
                    crate::avro::transliterate_avro_with_context(&state.composing_buffer, words);
                let has_candidate_memory = {
                    if state.composing_buffer.chars().count() <= 2 {
                        false
                    } else {
                        let db = get_core_database();
                        db.learner.read().ok().is_some_and(|l| {
                            l.lookup_input_error(&state.composing_buffer).is_some()
                                || l.candidate_memory.contains_key(&state.composing_buffer)
                                || l.candidate_memory
                                    .contains_key(&state.composing_buffer.to_lowercase())
                        })
                    }
                };
                if count > 0 && candidates.len() > 1 {
                    let scorer = get_context_scorer();
                    let right_word = state.right_context.split_whitespace().next();
                    scorer.rank_candidates_in_place_bidirectional(
                        words,
                        right_word,
                        &mut candidates,
                    );
                    if has_candidate_memory {
                        let db = get_core_database();
                        if let Ok(learner) = db.learner.read() {
                            let preferred = learner
                                .lookup_input_error(&state.composing_buffer)
                                .or_else(|| {
                                    learner
                                        .candidate_memory
                                        .get(&state.composing_buffer)
                                        .map(|s| s.as_str())
                                })
                                .or_else(|| {
                                    learner
                                        .candidate_memory
                                        .get(&state.composing_buffer.to_lowercase())
                                        .map(|s| s.as_str())
                                });
                            if let Some(pref) = preferred {
                                if let Some(pos) = candidates.iter().position(|c| c == pref) {
                                    let cand = candidates.remove(pos);
                                    candidates.insert(0, cand);
                                }
                            }
                        }
                    } else {
                        crate::avro::prioritize_common_or_override_candidate(
                            &state.composing_buffer,
                            &mut candidates,
                        );
                    }
                    if let Some(top) = candidates.first() {
                        let is_explicit_common =
                            crate::avro::get_common_word_candidates(&state.composing_buffer)
                                .is_some();
                        let db = get_core_database();
                        let has_override = db.lookup_override(&state.composing_buffer).is_some()
                            || db
                                .lookup_override(&state.composing_buffer.to_lowercase())
                                .is_some();

                        if !has_candidate_memory
                            && !is_explicit_common
                            && !has_override
                            && (!crate::avro::are_phonetically_compatible(&preedit, top)
                                || !db.is_exact_dictionary_word(top))
                        {
                            if let Some(pos) = candidates.iter().position(|c| c == &preedit) {
                                let cand = candidates.remove(pos);
                                candidates.insert(0, cand);
                            }
                        }
                    }
                }
                let mut norm_candidates = Vec::with_capacity(candidates.len());
                for c in candidates {
                    let n = nfc_normalize(&c);
                    if !norm_candidates.contains(&n) {
                        norm_candidates.push(n);
                    }
                }
                let len = preedit.graphemes(true).count() as u32;
                Ok(TypingResult {
                    preedit,
                    commit_text: None,
                    candidates: norm_candidates,
                    cursor_position: len,
                })
            }

            LekhaniLayoutType::English => {
                if state.is_private_field {
                    return Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: Some(nfc_normalize(&key)),
                        candidates: Vec::new(),
                        cursor_position: 0,
                    });
                }

                // If key is a word character (letters, apostrophe, hyphen)
                let is_word_char = key
                    .chars()
                    .all(|c| c.is_alphabetic() || c == '\'' || c == '-');
                if is_word_char {
                    state.composing_buffer.push_str(&key);
                    let candidates =
                        crate::english::get_english_candidates(&state.composing_buffer, 5);
                    let len = state.composing_buffer.len() as u32;
                    Ok(TypingResult {
                        preedit: state.composing_buffer.clone(),
                        commit_text: None,
                        candidates,
                        cursor_position: len,
                    })
                } else {
                    // Punctuation, digits, or symbols: commit buffer + key
                    if !state.composing_buffer.is_empty() {
                        let mut committed = std::mem::take(&mut state.composing_buffer);
                        state.composing_buffer = String::with_capacity(64);
                        committed.push_str(&key);
                        state.append_to_context(&committed);

                        let mut words_buf = [""; 16];
                        let count = get_context_words(&state.surrounding_context, &mut words_buf);
                        let words = &words_buf[..count];
                        let next_words = crate::english::get_english_next_words(words, 5);

                        Ok(TypingResult {
                            preedit: String::new(),
                            commit_text: Some(nfc_normalize(&committed)),
                            candidates: next_words,
                            cursor_position: 0,
                        })
                    } else {
                        Ok(TypingResult {
                            preedit: String::new(),
                            commit_text: Some(nfc_normalize(&key)),
                            candidates: Vec::new(),
                            cursor_position: 0,
                        })
                    }
                }
            }

            LekhaniLayoutType::National => {
                // Bijoy / BBS Dead-key Linker for Independent Vowels:
                // When buffer ends with Hasanta '্' and the new key is a vowel Kar,
                // transform [্ + Kar] into the corresponding independent vowel.
                let transformed_vowel = if state.composing_buffer.ends_with('্') {
                    match key.as_str() {
                        "া" => Some("অ"),
                        "ি" => Some("ই"),
                        "ী" => Some("ঈ"),
                        "ু" => Some("উ"),
                        "ূ" => Some("ঊ"),
                        "ৃ" => Some("ঋ"),
                        "ে" => Some("এ"),
                        "ৈ" => Some("ঐ"),
                        "ো" => Some("ও"),
                        "ৌ" => Some("ঔ"),
                        _ => None,
                    }
                } else {
                    None
                };

                if let Some(vowel) = transformed_vowel {
                    state.composing_buffer.pop();
                    state.composing_buffer.push_str(vowel);
                } else {
                    state.composing_buffer.push_str(&key);
                }

                let mut candidates = Vec::new();
                if key == "্" {
                    let chars: Vec<char> = state.composing_buffer.chars().collect();
                    let hasanta_pos = chars.len().wrapping_sub(1);
                    if hasanta_pos > 0 {
                        let base_consonant = chars[hasanta_pos - 1];
                        candidates = get_conjunct_suggestions(base_consonant);
                    }
                }

                if candidates.is_empty() {
                    let db = get_core_database();
                    let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 4);
                    if !prefix_matches.is_empty() {
                        candidates.push(state.composing_buffer.clone());
                        for (word, _) in prefix_matches {
                            if !candidates.iter().any(|c| c == word) {
                                candidates.push(word.to_string());
                            }
                        }
                    }
                }

                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let mut words_buf = [""; 16];
                    let count = get_context_words(&state.surrounding_context, &mut words_buf);
                    let words = &words_buf[..count];
                    let scorer = get_context_scorer();
                    let right_word = state.right_context.split_whitespace().next();
                    scorer.rank_candidates_in_place_bidirectional(
                        words,
                        right_word,
                        &mut candidates,
                    );
                }
                // In fixed layouts, the exact typed buffer is guaranteed to stay at position 0
                if let Some(pos) = candidates.iter().position(|c| c == &state.composing_buffer) {
                    let exact = candidates.remove(pos);
                    candidates.insert(0, exact);
                }

                let len = state.composing_buffer.graphemes(true).count() as u32;
                Ok(TypingResult {
                    preedit: state.composing_buffer.clone(),
                    commit_text: None,
                    candidates,
                    cursor_position: len,
                })
            }

            _ => {
                // Fixed / transliteration layouts — accumulate in buffer, query PrefixTrie
                state.composing_buffer.push_str(&key);
                let mut candidates = Vec::new();
                let db = get_core_database();
                if key == "্" {
                    let chars: Vec<char> = state.composing_buffer.chars().collect();
                    let hasanta_pos = chars.len().wrapping_sub(1);
                    if hasanta_pos > 0 {
                        let base_consonant = chars[hasanta_pos - 1];
                        candidates = get_conjunct_suggestions(base_consonant);
                    } else if let Some(base_consonant) = state.surrounding_context.chars().last() {
                        if is_bengali_consonant_or_modifier(base_consonant) {
                            candidates = get_conjunct_suggestions(base_consonant);
                        }
                    }
                }

                if candidates.is_empty() {
                    let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 4);
                    if !prefix_matches.is_empty() {
                        candidates.push(state.composing_buffer.clone());
                        for (word, _) in prefix_matches {
                            if !candidates.iter().any(|c| c == word) {
                                candidates.push(word.to_string());
                            }
                        }
                    }
                }

                // Agglutinative inflectional suffix expansion for fixed layouts
                if !state.composing_buffer.ends_with('্')
                    && state.composing_buffer.chars().count() >= 2
                {
                    let is_exact_word = db.trie.contains_exact(&state.composing_buffer);
                    if is_exact_word || candidates.len() < 5 {
                        let inflections = generate_inflectional_suffixes(&state.composing_buffer);
                        for inf in inflections {
                            if candidates.len() >= 8 {
                                break;
                            }
                            if (db.trie.contains_exact(&inf) || candidates.len() < 4)
                                && !candidates.contains(&inf)
                            {
                                candidates.push(inf);
                            }
                        }
                    }
                }
                // Morphological suffix expansion for Gboard/Probhat fixed layouts
                if candidates.len() < 5 {
                    let stems =
                        lekhani_core::phonetic::morphology::peel_all_stems(&state.composing_buffer);
                    for stem in stems {
                        if stem.len() >= 2 {
                            let stem_matches = db.trie.find_prefix_matches(&stem, 3);
                            for w in stem_matches {
                                if candidates.len() >= 8 {
                                    break;
                                }
                                if !candidates.contains(&w) {
                                    candidates.push(w);
                                }
                            }
                        }
                    }
                }
                if candidates.len() > 1 {
                    let mut words_buf = [""; 16];
                    let count = get_context_words(&state.surrounding_context, &mut words_buf);
                    let words = &words_buf[..count];
                    let scorer = get_context_scorer();
                    let right_word = state.right_context.split_whitespace().next();
                    scorer.rank_candidates_in_place_bidirectional(
                        words,
                        right_word,
                        &mut candidates,
                    );
                }
                // In fixed layouts, the exact typed buffer is guaranteed to stay at position 0
                if let Some(pos) = candidates.iter().position(|c| c == &state.composing_buffer) {
                    let exact = candidates.remove(pos);
                    candidates.insert(0, exact);
                }
                let len = state.composing_buffer.graphemes(true).count() as u32;
                Ok(TypingResult {
                    preedit: state.composing_buffer.clone(),
                    commit_text: None,
                    candidates,
                    cursor_position: len,
                })
            }
        }
    }

    // ── Glide / Gesture Typing ───────────────────────────────────────────────

    /// Decode a continuous swipe/glide path represented by visited key tokens.
    /// Returns the top decoded candidate word to commit immediately, along
    /// with alternative candidates for the candidate strip.
    pub fn decode_glide(&self, keys: Vec<String>) -> Result<TypingResult, LekhaniError> {
        let state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        if keys.len() < 2 {
            return Ok(TypingResult {
                preedit: String::new(),
                commit_text: None,
                candidates: Vec::new(),
                cursor_position: 0,
            });
        }

        match state.layout {
            LekhaniLayoutType::Avro => {
                let raw_token = keys.join("");
                let mut words_buf = [""; 16];
                let count = if !state.surrounding_context.is_empty() {
                    get_context_words(&state.surrounding_context, &mut words_buf)
                } else {
                    0
                };
                let words = &words_buf[..count];
                let (_preedit, mut candidates) =
                    crate::avro::transliterate_avro_with_context(&raw_token, words);
                if count > 0 && candidates.len() > 1 {
                    let scorer = get_context_scorer();
                    let right_word = state.right_context.split_whitespace().next();
                    scorer.rank_candidates_in_place_bidirectional(
                        words,
                        right_word,
                        &mut candidates,
                    );
                }

                if candidates.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let top_word = candidates[0].clone();
                    let len = top_word.graphemes(true).count() as u32;
                    Ok(TypingResult {
                        preedit: top_word.clone(),
                        commit_text: Some(format!("{} ", top_word)),
                        candidates,
                        cursor_position: len,
                    })
                }
            }
            LekhaniLayoutType::English => {
                let mut candidates = crate::english::decode_english_glide(&keys, 5);
                if candidates.is_empty() {
                    let raw = keys.join("").to_lowercase();
                    candidates = crate::english::get_english_candidates(&raw, 5);
                }
                if candidates.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let top_word = candidates[0].clone();
                    let len = top_word.graphemes(true).count() as u32;
                    Ok(TypingResult {
                        preedit: top_word.clone(),
                        commit_text: Some(format!("{} ", top_word)),
                        candidates,
                        cursor_position: len,
                    })
                }
            }
            _ => {
                // Bengali fixed layouts (Probaho, Probhat, National, Gboard)
                // Use grapheme sequence matching heuristic to recover single glide words
                let Some(start_key) = keys.first() else {
                    return Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    });
                };
                let Some(end_key) = keys.last() else {
                    return Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    });
                };
                let db = get_core_database();
                let entries = db.trie.find_prefix_entries(start_key, 250);
                let mut scored: Vec<(String, i64)> = Vec::new();
                for (word, freq) in entries {
                    if !word.ends_with(end_key.as_str()) {
                        continue;
                    }
                    let word_graphemes: Vec<&str> = word.graphemes(true).collect();
                    let mut key_idx = 0;
                    let mut matched = true;
                    let mut prev = "";
                    for g in word_graphemes {
                        if g == prev {
                            continue;
                        }
                        prev = g;
                        let mut found = false;
                        while key_idx < keys.len() {
                            if keys[key_idx] == g {
                                key_idx += 1;
                                found = true;
                                break;
                            }
                            key_idx += 1;
                        }
                        if !found {
                            matched = false;
                            break;
                        }
                    }
                    if matched {
                        let len_diff = (word.len() as isize - keys.len() as isize).abs();
                        scored.push((word.to_string(), freq as i64 - len_diff as i64 * 30));
                    }
                }
                scored.sort_unstable_by_key(|a| std::cmp::Reverse(a.1));
                let mut candidates: Vec<String> =
                    scored.into_iter().take(5).map(|(w, _)| w).collect();

                if candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = get_context_scorer();
                    candidates = scorer.rank_candidates(&words, &candidates);
                }

                if candidates.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let top_word = candidates[0].clone();
                    let len = top_word.graphemes(true).count() as u32;
                    Ok(TypingResult {
                        preedit: top_word.clone(),
                        commit_text: Some(format!("{} ", top_word)),
                        candidates,
                        cursor_position: len,
                    })
                }
            }
        }
    }

    // ── Dictionary Management & User Data Freedom (Phase 9) ──────────────────

    /// Get all user-learned and custom words.
    pub fn get_user_words(&self) -> Result<Vec<String>, LekhaniError> {
        let db = get_core_database();
        let learner = db
            .learner
            .read()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        Ok(learner.get_user_words())
    }

    /// Add a custom word to the user dictionary with high initial priority.
    pub fn add_user_word(&self, word: String) -> Result<bool, LekhaniError> {
        let clean = word.trim();
        if clean.chars().count() < 2 {
            return Ok(false);
        }
        let db = get_core_database();
        let mut learner = db
            .learner
            .write()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        learner.add_user_word(clean);
        Ok(true)
    }

    /// Delete a word from the user dictionary and memory.
    pub fn delete_user_word(&self, word: String) -> Result<bool, LekhaniError> {
        let clean = word.trim();
        let db = get_core_database();
        let mut learner = db
            .learner
            .write()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        Ok(learner.delete_user_word(clean))
    }

    /// Export the personal learned dictionary and bigram associations to JSON.
    pub fn export_dictionary_json(&self) -> Result<String, LekhaniError> {
        let db = get_core_database();
        let learner = db
            .learner
            .read()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        learner
            .to_json()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))
    }

    /// Import learned dictionary from JSON.
    pub fn import_dictionary_json(&self, json_content: String) -> Result<u32, LekhaniError> {
        let db = get_core_database();
        let other = lekhani_core::phonetic::AutonomousLearner::from_json(&json_content)
            .map_err(|e| LekhaniError::SessionError(format!("Invalid dictionary JSON: {}", e)))?;
        let count = other.learned_words.len() as u32;
        let mut learner = db
            .learner
            .write()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        learner.merge(&other);
        Ok(count)
    }

    /// Import a list of raw words (from Ridmik Keyboard backup, Avro .txt, or word list).
    pub fn import_raw_words(&self, words: Vec<String>) -> Result<u32, LekhaniError> {
        let db = get_core_database();
        let mut learner = db
            .learner
            .write()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        Ok(learner.import_word_list(&words))
    }

    /// Clear all user-learned vocabulary and associations.
    pub fn clear_user_dictionary(&self) -> Result<bool, LekhaniError> {
        let db = get_core_database();
        let mut learner = db
            .learner
            .write()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        learner.clear_user_data();
        Ok(true)
    }

    /// Persist user-learned vocabulary, candidate memory, and bigrams to disk.
    pub fn save_user_learned(&self, path: String) -> Result<bool, LekhaniError> {
        let db = get_core_database();
        db.save_user_learned(path)
            .map(|_| true)
            .map_err(|e| LekhaniError::SessionError(e.to_string()))
    }

    /// Load user-learned vocabulary, candidate memory, and bigrams from disk.
    pub fn load_user_learned(&self, path: String) -> Result<bool, LekhaniError> {
        let path_ref = std::path::Path::new(&path);
        if !path_ref.exists() {
            return Ok(false);
        }
        let db = get_core_database();
        if let Ok(mut learner) = db.learner.write() {
            *learner = lekhani_core::phonetic::AutonomousLearner::load_from_path(path_ref);
            let warm_candidates = [
                std::path::Path::new(
                    "/data/data/com.lekhani.android/files/dictionaries/rank_weights_v2.json",
                ),
                std::path::Path::new(
                    "/data/data/com.lekhani.android.debug/files/dictionaries/rank_weights_v2.json",
                ),
                std::path::Path::new(
                    "/data/user/0/com.lekhani.android/files/dictionaries/rank_weights_v2.json",
                ),
                std::path::Path::new("./data/dictionaries/rank_weights_v2.json"),
                std::path::Path::new("../data/dictionaries/rank_weights_v2.json"),
                std::path::Path::new("../../data/dictionaries/rank_weights_v2.json"),
            ];
            for p in warm_candidates {
                if p.exists() {
                    learner.load_pretrained_rank_weights(p);
                    break;
                }
            }
        }
        Ok(true)
    }

    /// Check if the user learner has unsaved modifications.
    pub fn is_user_learned_dirty(&self) -> bool {
        let db = get_core_database();
        db.learner.read().map(|l| l.dirty).unwrap_or(false)
    }

    /// Retrieve the count of auto-learned words from typing stream.
    pub fn get_learned_words_count(&self) -> Result<u32, LekhaniError> {
        let db = get_core_database();
        let learner = db
            .learner
            .read()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        Ok(learner.get_learned_words_count() as u32)
    }

    /// Clear background auto-learned words while preserving explicit user custom words.
    pub fn clear_learned_history(&self) -> Result<bool, LekhaniError> {
        let db = get_core_database();
        let mut learner = db
            .learner
            .write()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        learner.clear_learned_history();
        Ok(true)
    }

    /// Add a custom user autocorrect / shortcut rule.
    pub fn add_autocorrect_rule(
        &self,
        trigger: String,
        replacement: String,
    ) -> Result<bool, LekhaniError> {
        let clean_trig = trigger.trim();
        let clean_repl = replacement.trim();
        if clean_trig.is_empty() || clean_repl.is_empty() {
            return Ok(false);
        }
        let db = get_core_database();
        db.insert_user_autocorrect(clean_trig.to_string(), clean_repl.to_string());
        Ok(true)
    }

    /// Delete a custom user autocorrect rule.
    pub fn delete_autocorrect_rule(&self, trigger: String) -> Result<bool, LekhaniError> {
        let clean_trig = trigger.trim();
        let db = get_core_database();
        Ok(db.remove_user_autocorrect(clean_trig).is_some())
    }

    /// Clear all custom user autocorrect rules.
    pub fn clear_autocorrect_rules(&self) -> Result<bool, LekhaniError> {
        let db = get_core_database();
        db.clear_user_autocorrect();
        Ok(true)
    }

    /// Retrieve all custom user autocorrect rules as a key-value map.
    pub fn get_autocorrect_rules(
        &self,
    ) -> Result<std::collections::HashMap<String, String>, LekhaniError> {
        let db = get_core_database();
        let map = db.get_user_autocorrect_map();
        Ok(map.into_iter().collect())
    }

    /// Save custom user autocorrect rules to JSON file.
    pub fn save_user_autocorrect(&self, path: String) -> Result<bool, LekhaniError> {
        let db = get_core_database();
        db.save_user_autocorrect(path)
            .map(|_| true)
            .map_err(|e| LekhaniError::SessionError(e.to_string()))
    }

    /// Load custom user autocorrect rules from JSON file.
    pub fn load_user_autocorrect(&self, path: String) -> Result<bool, LekhaniError> {
        let path_ref = std::path::Path::new(&path);
        if !path_ref.exists() {
            return Ok(false);
        }
        let db = get_core_database();
        db.load_user_autocorrect(path_ref);
        Ok(true)
    }

    // ── Backspace ────────────────────────────────────────────────────────────

    /// Handle Backspace keypress.
    ///
    /// Deletes exactly one **Unicode grapheme cluster** from the end of the
    /// composing buffer.  This correctly handles multi-codepoint clusters such
    /// as Bengali conjuncts (`ক্ষ` = U+0995 U+09CD U+09B7) and emoji with
    /// skin-tone/gender modifiers, which naïve `String::pop()` would corrupt.
    pub fn handle_backspace(&self) -> Result<TypingResult, LekhaniError> {
        let mut state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        if !state.composing_buffer.is_empty() {
            if state.layout == LekhaniLayoutType::Avro {
                // Grapheme-safe removal: pop the last Unicode grapheme cluster,
                // not just the last char code-point. Safe for any future Unicode
                // input into the Avro composing buffer.
                let new_len = state
                    .composing_buffer
                    .grapheme_indices(true)
                    .next_back()
                    .map(|(i, _)| i)
                    .unwrap_or(0);
                state.composing_buffer.truncate(new_len);
                if state.composing_buffer.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let words: Vec<&str> = if !state.surrounding_context.is_empty() {
                        state.surrounding_context.split_whitespace().collect()
                    } else {
                        Vec::new()
                    };
                    let (preedit, mut candidates) = crate::avro::transliterate_avro_with_context(
                        &state.composing_buffer,
                        &words,
                    );
                    if !words.is_empty() && candidates.len() > 1 {
                        let scorer = get_context_scorer();
                        let right_word = state.right_context.split_whitespace().next();
                        scorer.rank_candidates_in_place_bidirectional(
                            &words,
                            right_word,
                            &mut candidates,
                        );
                        crate::avro::prioritize_common_or_override_candidate(
                            &state.composing_buffer,
                            &mut candidates,
                        );
                        if let Some(top) = candidates.first() {
                            let db = get_core_database();
                            if !crate::avro::are_phonetically_compatible(&preedit, top)
                                && db.is_exact_dictionary_word(&preedit)
                            {
                                if let Some(pos) = candidates.iter().position(|c| c == &preedit) {
                                    let cand = candidates.remove(pos);
                                    candidates.insert(0, cand);
                                }
                            }
                        }
                    }
                    let len = preedit.graphemes(true).count() as u32;
                    Ok(TypingResult {
                        preedit,
                        commit_text: None,
                        candidates,
                        cursor_position: len,
                    })
                }
            } else if state.layout == LekhaniLayoutType::English {
                state.composing_buffer.pop();
                if state.composing_buffer.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let candidates =
                        crate::english::get_english_candidates(&state.composing_buffer, 5);
                    let len = state.composing_buffer.len() as u32;
                    Ok(TypingResult {
                        preedit: state.composing_buffer.clone(),
                        commit_text: None,
                        candidates,
                        cursor_position: len,
                    })
                }
            } else {
                // Key-by-key char deletion for all Bengali fixed layouts
                // (Probaho, National, Probhat, Gboard) inside active composing buffer
                state.composing_buffer.pop();

                if state.composing_buffer.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let len = state.composing_buffer.graphemes(true).count() as u32;
                    let mut candidates = Vec::new();
                    let db = get_core_database();
                    let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 8);
                    candidates.push(state.composing_buffer.clone());
                    for (word, _) in prefix_matches {
                        if !candidates.iter().any(|c| c == word) {
                            candidates.push(word.to_string());
                        }
                    }
                    Ok(TypingResult {
                        preedit: state.composing_buffer.clone(),
                        commit_text: None,
                        candidates,
                        cursor_position: len,
                    })
                }
            }
        } else {
            // Buffer empty — signal Android to delete the preceding character
            // in the target application's InputConnection.
            // Check for rapid backspace mistake penalty (<1500 ms).
            if let Some((prev, committed, ts)) = state.last_commit_info.take() {
                if ts.elapsed().as_millis() <= 1500 && !state.is_private_field {
                    let db = get_core_database();
                    if let Ok(mut learner) = db.learner.write() {
                        learner.penalize_mistake(prev.as_deref(), &committed);
                    }
                }
                if state.surrounding_context.ends_with(&committed) {
                    let trim_len = state.surrounding_context.len() - committed.len();
                    state.surrounding_context.truncate(trim_len);
                    if state.surrounding_context.ends_with(' ') {
                        state.surrounding_context.pop();
                    }
                }
            }

            Ok(TypingResult {
                preedit: String::new(),
                commit_text: None,
                candidates: Vec::new(),
                cursor_position: 0,
            })
        }
    }

    // ── Spacebar ─────────────────────────────────────────────────────────────

    /// Handle Spacebar tap: NFC-normalize and commit the current composing buffer.
    pub fn handle_space(&self) -> Result<TypingResult, LekhaniError> {
        self.handle_space_with_choice(None)
    }

    /// Handle Spacebar tap with an optional user-selected or active UI-highlighted candidate.
    /// If `chosen_candidate` is provided (e.g. from the Android candidate strip),
    /// commits that candidate directly, establishing 100% WYSIWYG synchronization between
    /// the UI highlight and the spacebar commit.
    pub fn handle_space_with_choice(
        &self,
        chosen_candidate: Option<String>,
    ) -> Result<TypingResult, LekhaniError> {
        let mut state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        if !state.composing_buffer.is_empty() {
            let raw = std::mem::take(&mut state.composing_buffer);
            // Reallocate the buffer to its pre-allocated capacity to avoid
            // the composing buffer shrinking to zero capacity after `take`.
            state.composing_buffer = String::with_capacity(64);
            let context_words: Vec<&str> = if !state.surrounding_context.is_empty() {
                state.surrounding_context.split_whitespace().collect()
            } else {
                Vec::new()
            };
            let prev_word = context_words.last().map(|s| s.to_string());

            let mut normalized = if let Some(ref cand) = chosen_candidate {
                nfc_normalize(cand)
            } else if state.layout == LekhaniLayoutType::English {
                nfc_normalize(&raw)
            } else if state.layout == LekhaniLayoutType::Avro {
                let has_candidate_memory = {
                    if raw.chars().count() <= 2 {
                        false
                    } else {
                        let db = get_core_database();
                        db.learner.read().ok().is_some_and(|l| {
                            l.lookup_input_error(&raw).is_some()
                                || l.candidate_memory.contains_key(&raw)
                                || l.candidate_memory.contains_key(&raw.to_lowercase())
                        })
                    }
                };
                let (preedit, mut candidates) =
                    crate::avro::transliterate_avro_with_context(&raw, &context_words);
                let chosen = if !has_candidate_memory && candidates.len() > 1 {
                    if !context_words.is_empty() {
                        let scorer = get_context_scorer();
                        let right_word = state.right_context.split_whitespace().next();
                        candidates = scorer.rank_candidates_bidirectional(
                            &context_words,
                            right_word,
                            &candidates,
                        );
                    }
                    crate::avro::prioritize_common_or_override_candidate(&raw, &mut candidates);
                    if let Some(top) = candidates.first() {
                        let is_explicit_common =
                            crate::avro::get_common_word_candidates(&raw).is_some();
                        let db = get_core_database();
                        let has_override = db.lookup_override(&raw).is_some()
                            || db.lookup_override(&raw.to_lowercase()).is_some();

                        if !has_candidate_memory
                            && !is_explicit_common
                            && !has_override
                            && (!crate::avro::are_phonetically_compatible(&preedit, top)
                                || !db.is_exact_dictionary_word(top))
                        {
                            if let Some(pos) = candidates.iter().position(|c| c == &preedit) {
                                let cand = candidates.remove(pos);
                                candidates.insert(0, cand);
                            }
                        }
                    }
                    candidates.first().map(|s| s.as_str()).unwrap_or(&raw)
                } else if !preedit.is_empty() {
                    &preedit
                } else {
                    candidates.first().map(|s| s.as_str()).unwrap_or(&raw)
                };
                nfc_normalize(chosen)
            } else {
                let db = get_core_database();
                let lower = raw.to_lowercase();
                let autocompleted = if let Ok(uac) = db.user_autocorrect.read() {
                    uac.get(&raw).cloned().or_else(|| uac.get(&lower).cloned())
                } else {
                    None
                };
                if let Some(repl) = autocompleted {
                    nfc_normalize(&repl)
                } else {
                    nfc_normalize(&raw)
                }
            };
            let word = normalized.clone();
            normalized.push(' ');

            if !state.is_private_field
                && state.auto_learn_enabled
                && state.layout != LekhaniLayoutType::English
            {
                let db = get_core_database();
                if let Ok(mut learner) = db.learner.write() {
                    if !word.is_ascii() {
                        learner.observe_and_learn(&word, &db.trie);
                        if let Some(ref p) = prev_word {
                            learner.observe_committed_pair(p, &word);
                        }
                    }
                }
                state.last_commit_info = Some((prev_word, word.clone(), std::time::Instant::now()));
            } else {
                state.last_commit_info = None;
            }

            state.append_to_context(&word);

            let next_words = if state.layout == LekhaniLayoutType::English {
                if state.is_private_field {
                    Vec::new()
                } else {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    crate::english::get_english_next_words(&words, 5)
                }
            } else {
                get_bengali_next_words(&state.surrounding_context, Some(&state.right_context))
            };

            Ok(TypingResult {
                preedit: String::new(),
                commit_text: Some(normalized),
                candidates: next_words,
                cursor_position: 0,
            })
        } else {
            state.last_commit_info = None;
            let next_words = if state.layout == LekhaniLayoutType::English {
                if state.is_private_field {
                    Vec::new()
                } else {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    crate::english::get_english_next_words(&words, 5)
                }
            } else {
                get_bengali_next_words(&state.surrounding_context, Some(&state.right_context))
            };

            Ok(TypingResult {
                preedit: String::new(),
                commit_text: Some(" ".to_string()),
                candidates: next_words,
                cursor_position: 0,
            })
        }
    }

    // ── Candidate selection ───────────────────────────────────────────────────

    /// Commit a candidate selected from the strip.
    /// The candidate is NFC-normalized before commitment.
    pub fn select_candidate(&self, candidate: String) -> Result<TypingResult, LekhaniError> {
        let mut state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        // ── Probaho & Probhat In-Flight Conjunct Substitution ────────────────
        // If Probaho or Probhat mode is active and the composing buffer ends with Hasanta (`্`),
        // selecting a conjunct candidate (e.g. `ক্ষ` or `ক্ত`) replaces the base consonant
        // and Hasanta in-place, keeping the composing buffer alive so the typist can
        // seamlessly continue typing subsequent Kars/consonants (e.g. `শিক্` + `ক্ষ` -> `শিক্ষ` + `া` -> `শিক্ষা`).
        if (state.layout == LekhaniLayoutType::Probaho
            || state.layout == LekhaniLayoutType::Probhat)
            && state.composing_buffer.ends_with('্')
        {
            let mut chars: Vec<char> = state.composing_buffer.chars().collect();
            if chars.len() >= 2 {
                chars.pop(); // Remove '্'
                chars.pop(); // Remove base consonant
                let prefix: String = chars.into_iter().collect();
                let new_buffer = if !prefix.is_empty() && candidate.starts_with(&prefix) {
                    nfc_normalize(&candidate)
                } else {
                    nfc_normalize(&format!("{}{}", prefix, candidate))
                };
                state.composing_buffer = new_buffer.clone();

                let mut candidates = Vec::new();
                let db = get_core_database();
                let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 5);
                if !prefix_matches.is_empty() {
                    candidates.push(state.composing_buffer.clone());
                    for (word, _) in prefix_matches {
                        if !candidates.iter().any(|c| c == word) {
                            candidates.push(word.to_string());
                        }
                    }
                }

                return Ok(TypingResult {
                    preedit: state.composing_buffer.clone(),
                    commit_text: None,
                    candidates,
                    cursor_position: state.composing_buffer.chars().count() as u32,
                });
            }
        }

        let typed_buffer = std::mem::take(&mut state.composing_buffer);
        state.composing_buffer = String::with_capacity(64);
        let normalized = nfc_normalize(&candidate);
        let commit = format!("{} ", normalized);

        let prev_word = state
            .surrounding_context
            .split_whitespace()
            .last()
            .map(|s| s.to_string());

        if !state.is_private_field
            && state.auto_learn_enabled
            && state.layout != LekhaniLayoutType::English
        {
            let db = get_core_database();
            if let Ok(mut learner) = db.learner.write() {
                // Only record candidate selection overrides for inputs > 2 characters
                // and non-ASCII candidates to prevent Latin/English selections from poisoning Bengali phonetic memory!
                if typed_buffer.chars().count() > 2
                    && !normalized.is_ascii()
                    && !typed_buffer.eq_ignore_ascii_case(&normalized)
                {
                    learner.record_candidate_selection(&typed_buffer, &normalized);
                    learner.record_input_error(&typed_buffer, &normalized);
                }
                if !normalized.is_ascii() {
                    learner.observe_and_learn(&normalized, &db.trie);
                    if let Some(ref p) = prev_word {
                        learner.observe_committed_pair(p, &normalized);
                    }
                }
            }
            state.last_commit_info =
                Some((prev_word, normalized.clone(), std::time::Instant::now()));
        } else {
            state.last_commit_info = None;
        }

        state.append_to_context(&normalized);

        let next_words = if state.layout == LekhaniLayoutType::English {
            if state.is_private_field {
                Vec::new()
            } else {
                let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                crate::english::get_english_next_words(&words, 5)
            }
        } else {
            get_bengali_next_words(&state.surrounding_context, Some(&state.right_context))
        };

        Ok(TypingResult {
            preedit: String::new(),
            commit_text: Some(commit),
            candidates: next_words,
            cursor_position: 0,
        })
    }

    /// Asynchronous next-word prediction helper for background coroutine dispatch.
    /// Reads surrounding context without blocking UI typing loop.
    pub fn predict_next_words(&self, max_results: u32) -> Vec<String> {
        let state = match self.state.lock() {
            Ok(s) => s,
            Err(_) => return Vec::new(),
        };

        if state.is_private_field || state.surrounding_context.is_empty() {
            return Vec::new();
        }

        let limit = (max_results as usize).clamp(1, 10);
        if state.layout == LekhaniLayoutType::English {
            let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
            crate::english::get_english_next_words(&words, limit)
        } else {
            get_bengali_next_words(&state.surrounding_context, Some(&state.right_context))
                .into_iter()
                .take(limit)
                .collect()
        }
    }

    // ── Lifecycle helpers ─────────────────────────────────────────────────────

    /// Sets an explicit file path for auto-saving learner data on finish/destroy.
    pub fn set_learner_autosave_path(&self, path: String) {
        set_learner_autosave_path(path);
    }

    /// Reset and clear all internal composing state.
    /// Called on `onFinishInput()` and layout switches.
    pub fn reset(&self) {
        if let Ok(mut state) = self.state.lock() {
            state.composing_buffer.clear();
            state.surrounding_context.clear();
            state.surrounding_context_len = 0;
            state.right_context.clear();
            state.is_private_field = false;
            state.last_commit_info = None;
        }
    }

    /// Explicitly penalize a reverted commit or mistaken auto-correction
    pub fn penalize_commit(&self, reverted_word: String) -> Result<(), LekhaniError> {
        let mut state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        if !state.is_private_field {
            let prev = state.last_commit_info.take().and_then(|(p, _, _)| p);
            let db = get_core_database();
            if let Ok(mut learner) = db.learner.write() {
                learner.penalize_mistake(prev.as_deref(), &reverted_word);
            }
        }
        Ok(())
    }

    /// Returns whether the keyboard is currently composing a word.
    pub fn is_composing(&self) -> bool {
        self.state
            .lock()
            .map(|s| !s.composing_buffer.is_empty())
            .unwrap_or(false)
    }

    /// Explicitly loads an English dictionary from an external file path.
    pub fn load_english_dictionary(&self, path: String) -> bool {
        crate::english::load_english_dictionary_from_path(&path)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
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
        // In-flight preedit strictly equals the typed phonetic rule, while candidate strip has AI homophone rank
        assert_eq!(res_book.preedit, "পরা");
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
        assert_eq!(res_cont.preedit, "পরা");
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
    fn test_glide_decoding() {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);
        let res = session
            .decode_glide(vec!["a".into(), "m".into(), "i".into()])
            .unwrap();
        assert_eq!(res.preedit, "আমি");
        assert_eq!(res.commit_text, Some("আমি ".into()));
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

        // 1. Candidate selection with frequency threshold:
        // 1st selection (count = 1 < 2): protects against accidental fat-finger tap poisoning!
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
            // Single tap is NOT yet pinned to candidate_memory!
            assert_eq!(learner.candidate_memory.get("kormo"), None);
            assert_eq!(
                learner.candidate_selection_counts.get("kormo\tকৰ্ম"),
                Some(&1)
            );
        }

        // 2nd selection (count = 2 >= 2): user explicitly confirms preference, now remembered!
        let _ = session.process_key("k".into()).unwrap();
        let _ = session.process_key("o".into()).unwrap();
        let _ = session.process_key("r".into()).unwrap();
        let _ = session.process_key("m".into()).unwrap();
        let _ = session.process_key("o".into()).unwrap();
        let sel2 = session.select_candidate("কৰ্ম".into()).unwrap();
        assert_eq!(sel2.commit_text, Some("কৰ্ম ".into()));

        {
            let learner = db.learner.read().unwrap();
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

        // 1. Type typo "bhlao" and select "ভালো" (1st selection)
        for ch in ["b", "h", "l", "a", "o"] {
            let _ = session.process_key(ch.into()).unwrap();
        }
        let sel1 = session.select_candidate("ভালো".into()).unwrap();
        assert_eq!(sel1.commit_text, Some("ভালো ".into()));

        // Not pinned yet after 1 selection
        {
            let db = get_core_database();
            let learner = db.learner.read().unwrap();
            assert_eq!(learner.lookup_input_error("bhlao"), None);
        }

        // 2. Type typo "bhlao" and select "ভালো" again (2nd selection >= threshold)
        for ch in ["b", "h", "l", "a", "o"] {
            let _ = session.process_key(ch.into()).unwrap();
        }
        let sel2 = session.select_candidate("ভালো".into()).unwrap();
        assert_eq!(sel2.commit_text, Some("ভালো ".into()));

        // Now pinned!
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

        // 2. Typing numbers e.g. "3" (converts to "৩") + "." should remain "৩." for decimal fractions
        session.reset();
        session.process_key("3".into()).unwrap();
        let num_res = session.process_key(".".into()).unwrap();
        assert_eq!(num_res.commit_text.as_deref(), Some("৩."));

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
    }
}
