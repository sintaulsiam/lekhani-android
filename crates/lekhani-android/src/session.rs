use std::sync::{Mutex, OnceLock};
use unicode_segmentation::UnicodeSegmentation;
use lekhani_core::phonetic::PhoneticDatabase;
use crate::error::LekhaniError;
use crate::layout::LekhaniLayoutType;
use crate::probaho::{get_conjunct_suggestions, nfc_normalize, promote_kar_if_needed};

static CORE_DB: OnceLock<PhoneticDatabase> = OnceLock::new();
static CUSTOM_DICT_DIR: OnceLock<String> = OnceLock::new();
static CONTEXT_SCORER: OnceLock<lekhani_ai::ContextScorer> = OnceLock::new();
static NEXT_WORD_PREDICTOR: OnceLock<lekhani_ai::NextWordPredictor> = OnceLock::new();

/// Sets a custom dictionary directory dynamically from Android application context.
#[uniffi::export]
pub fn set_dictionary_directory(path: String) {
    let _ = CUSTOM_DICT_DIR.set(path);
}

pub fn get_core_database() -> &'static PhoneticDatabase {
    CORE_DB.get_or_init(|| {
        let mut db = PhoneticDatabase::new();
        if let Some(custom_dir) = CUSTOM_DICT_DIR.get() {
            let path = std::path::Path::new(custom_dir);
            if path.exists() {
                let _ = db.load_from_dir(path);
                return db;
            }
        }
        let candidate_dirs = [
            std::path::Path::new("/data/data/com.lekhani.android/files/dictionaries"),
            std::path::Path::new("/data/data/com.lekhani.android.debug/files/dictionaries"),
            std::path::Path::new("/data/user/0/com.lekhani.android/files/dictionaries"),
            std::path::Path::new("/data/user/0/com.lekhani.android.debug/files/dictionaries"),
            std::path::Path::new("/data/user_de/0/com.lekhani.android/files/dictionaries"),
            std::path::Path::new("/data/user_de/0/com.lekhani.android.debug/files/dictionaries"),
            std::path::Path::new("./data/dictionaries"),
            std::path::Path::new("../data/dictionaries"),
            std::path::Path::new("../../data/dictionaries"),
        ];
        for dir in candidate_dirs {
            if dir.exists() {
                let _ = db.load_from_dir(dir);
                break;
            }
        }
        db
    })
}

pub fn get_context_scorer() -> &'static lekhani_ai::ContextScorer {
    CONTEXT_SCORER.get_or_init(|| {
        let mut lm = lekhani_ai::LanguageModel::new();
        if let Some(custom_dir) = CUSTOM_DICT_DIR.get() {
            let path = std::path::Path::new(custom_dir).join("bengali_lm.bin");
            if path.is_file() && lm.load_binary_file(&path).is_ok() {
                return lekhani_ai::ContextScorer::with_language_model(lm);
            }
        }
        let candidate_dirs = [
            std::path::Path::new("/data/data/com.lekhani.android/files/dictionaries"),
            std::path::Path::new("/data/data/com.lekhani.android.debug/files/dictionaries"),
            std::path::Path::new("/data/user/0/com.lekhani.android/files/dictionaries"),
            std::path::Path::new("/data/user/0/com.lekhani.android.debug/files/dictionaries"),
            std::path::Path::new("/data/user_de/0/com.lekhani.android/files/dictionaries"),
            std::path::Path::new("/data/user_de/0/com.lekhani.android.debug/files/dictionaries"),
            std::path::Path::new("./data/dictionaries"),
            std::path::Path::new("../data/dictionaries"),
            std::path::Path::new("../../data/dictionaries"),
        ];
        for dir in candidate_dirs {
            let path = dir.join("bengali_lm.bin");
            if path.is_file() && lm.load_binary_file(&path).is_ok() {
                break;
            }
        }
        lekhani_ai::ContextScorer::with_language_model(lm)
    })
}

pub fn get_next_word_predictor() -> &'static lekhani_ai::NextWordPredictor {
    NEXT_WORD_PREDICTOR.get_or_init(|| {
        let scorer = get_context_scorer();
        lekhani_ai::NextWordPredictor::with_language_model(scorer.lm().clone())
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
}


/// Helper to retrieve next-word predictions combining personalized user bigrams
/// from AutonomousLearner with statistical n-gram predictions.
fn get_bengali_next_words(context: &str) -> Vec<String> {
    let words: Vec<&str> = context.split_whitespace().collect();
    let last_word = words.last().copied();

    let mut results: Vec<String> = Vec::with_capacity(5);

    // 1. Personalized User Bigrams from AutonomousLearner
    let db = get_core_database();
    if let (Some(last), Ok(learner)) = (last_word, db.learner.read()) {
        let user_conts = learner.get_top_user_continuations(last, 3);
        for cont in user_conts {
            if !results.contains(&cont) {
                results.push(cont);
            }
        }
    }

    // 2. Idioms and Statistical N-gram predictions
    let predictor = get_next_word_predictor();
    let predictions = predictor.predict_next(&words, 5);
    for pred in predictions {
        if !results.contains(&pred) {
            results.push(pred);
            if results.len() >= 5 {
                break;
            }
        }
    }

    results
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
                is_private_field: false,
                auto_learn_enabled: true,
                last_commit_info: None,
                spatial_model: crate::spatial::SpatialTouchModel::new(),
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

    // ── Privacy / incognito policy ───────────────────────────────────────────

    /// Called on every `onStartInput()`.
    ///
    /// When `is_private` is `true` (password fields, incognito text areas):
    /// - Automatically switches to English QWERTY.
    /// - Freezes dictionary learning and clipboard capture.
    pub fn set_private_field(&self, is_private: bool) {
        if let Ok(mut state) = self.state.lock() {
            state.is_private_field = is_private;
            if is_private {
                state.layout = LekhaniLayoutType::English;
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
            state.surrounding_context = context;
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
            .map(|s| s.spatial_model.rank_keys_at(touch_x, touch_y, top_k as usize))
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
        let spatial_candidates = {
            let state = self
                .state
                .lock()
                .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
            if !state.spatial_model.is_empty() {
                state.spatial_model.rank_keys_at(touch_x, touch_y, 2)
            } else {
                Vec::new()
            }
        };

        // If the top spatial key differs from the tapped key (touch landed closer to neighbor)
        // or neighbor has high likelihood within 2.8 log-prob, explore neighbor
        if spatial_candidates.len() >= 2 {
            let neighbor = if spatial_candidates[0].key == key {
                &spatial_candidates[1]
            } else {
                &spatial_candidates[0]
            };

            let key_prob = self.get_spatial_log_prob(key, touch_x, touch_y);
            if (neighbor.log_prob - key_prob).abs() <= 2.8 {
                let state = self
                    .state
                    .lock()
                    .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
                if !state.composing_buffer.is_empty() {
                    let mut alt_buf = state.composing_buffer[..state.composing_buffer.len().saturating_sub(1)].to_string();
                    alt_buf.push_str(&neighbor.key);

                    match state.layout {
                        LekhaniLayoutType::English => {
                            let alt_cands = crate::english::get_english_candidates(&alt_buf, 2);
                            for cand in alt_cands {
                                if !result.candidates.contains(&cand) && result.candidates.len() < 7 {
                                    result.candidates.push(cand);
                                }
                            }
                        }
                        LekhaniLayoutType::Avro => {
                            let (_, alt_cands) = crate::avro::transliterate_avro(&alt_buf);
                            for cand in alt_cands {
                                if !result.candidates.contains(&cand) && result.candidates.len() < 7 {
                                    result.candidates.push(cand);
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

        match state.layout {
            LekhaniLayoutType::Probaho => {
                let is_start = state.composing_buffer.is_empty();
                let promoted = promote_kar_if_needed(&key, is_start);
                state.composing_buffer.push_str(&promoted);

                let mut candidates = Vec::new();

                // Hasanta `্` typed: inspect buffer at codepoint level to find the
                // base consonant that precedes the Hasanta.
                // We use codepoints here (not graphemes) because the Unicode
                // segmentation algorithm immediately merges `ক` + `্` into a
                // single grapheme cluster, so grapheme[n-2] would refer to the
                // consonant BEFORE the current sequence rather than the one
                // the user just typed.
                if promoted == "্" {
                    let chars: Vec<char> = state.composing_buffer.chars().collect();
                    // Layout: [..., base_consonant, '্']  <- just appended
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
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = get_context_scorer();
                    candidates = scorer.rank_candidates(&words, &candidates);
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
                let (mut preedit, mut candidates) = crate::avro::transliterate_avro(&state.composing_buffer);
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = get_context_scorer();
                    candidates = scorer.rank_candidates(&words, &candidates);
                    if let Some(top) = candidates.first() {
                        preedit = top.clone();
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
                let is_word_char = key.chars().all(|c| c.is_alphabetic() || c == '\'' || c == '-');
                if is_word_char {
                    state.composing_buffer.push_str(&key);
                    let candidates = crate::english::get_english_candidates(&state.composing_buffer, 5);
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
                        if !state.surrounding_context.is_empty() {
                            state.surrounding_context.push(' ');
                        }
                        state.surrounding_context.push_str(&committed);
                        committed.push_str(&key);

                        let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                        let next_words = crate::english::get_english_next_words(&words, 5);

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
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = get_context_scorer();
                    candidates = scorer.rank_candidates(&words, &candidates);
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
                // Flush buffer immediately on punctuation or newline
                let is_punct = key == "।" || key == "॥" || key == "," || key == ";" || key == ":" || key == "?" || key == "!" || key == "\n";
                if is_punct {
                    let mut committed = std::mem::take(&mut state.composing_buffer);
                    committed.push_str(&key);
                    return Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: Some(nfc_normalize(&committed)),
                        candidates: Vec::new(),
                        cursor_position: 0,
                    });
                }

                // Fixed / transliteration layouts — accumulate in buffer, query PrefixTrie
                state.composing_buffer.push_str(&key);
                let mut candidates = Vec::new();
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
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = get_context_scorer();
                    candidates = scorer.rank_candidates(&words, &candidates);
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
                let (_preedit, mut candidates) = crate::avro::transliterate_avro(&raw_token);
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
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
                // Bengali layouts (Probaho, Probhat, National, Gboard)
                let start_key = &keys[0];
                let end_key = keys.last().unwrap();
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
                        let mut score = freq as i64;
                        let len_diff = (word.len() as isize - keys.len() as isize).abs();
                        score -= (len_diff as i64) * 30;
                        scored.push((word.to_string(), score));
                    }
                }

                scored.sort_unstable_by_key(|a| std::cmp::Reverse(a.1));
                let mut candidates: Vec<String> = scored.into_iter().take(5).map(|(w, _)| w).collect();

                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
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
    pub fn add_autocorrect_rule(&self, trigger: String, replacement: String) -> Result<bool, LekhaniError> {
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
    pub fn get_autocorrect_rules(&self) -> Result<std::collections::HashMap<String, String>, LekhaniError> {
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
                state.composing_buffer.pop();
                if state.composing_buffer.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let (preedit, candidates) = crate::avro::transliterate_avro(&state.composing_buffer);
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
                    let candidates = crate::english::get_english_candidates(&state.composing_buffer, 5);
                    let len = state.composing_buffer.len() as u32;
                    Ok(TypingResult {
                        preedit: state.composing_buffer.clone(),
                        commit_text: None,
                        candidates,
                        cursor_position: len,
                    })
                }
            } else {
                state.composing_buffer.pop();
                if state.composing_buffer.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let len = state.composing_buffer.chars().count() as u32;
                    let mut candidates = Vec::new();
                    let db = get_core_database();
                    let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 4);
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
    /// Under Option B (Conservative spacebar), commits typed text verbatim without
    /// forced autocorrect, followed by English or Bengali next-word predictions.
    pub fn handle_space(&self) -> Result<TypingResult, LekhaniError> {
        let mut state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        if !state.composing_buffer.is_empty() {
            let raw = std::mem::take(&mut state.composing_buffer);
            // Reallocate the buffer to its pre-allocated capacity to avoid
            // the composing buffer shrinking to zero capacity after `take`.
            state.composing_buffer = String::with_capacity(64);
            let mut normalized = if state.layout == LekhaniLayoutType::Avro {
                let (_, mut candidates) = crate::avro::transliterate_avro(&raw);
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = get_context_scorer();
                    candidates = scorer.rank_candidates(&words, &candidates);
                }
                let chosen = candidates.first().map(|s| s.as_str()).unwrap_or(&raw);
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

            let prev_word = state
                .surrounding_context
                .split_whitespace()
                .last()
                .map(|s| s.to_string());

            if !state.is_private_field && state.auto_learn_enabled {
                let db = get_core_database();
                if let Ok(mut learner) = db.learner.write() {
                    learner.observe_and_learn(&word, &db.trie);
                    if let Some(ref p) = prev_word {
                        learner.observe_committed_pair(p, &word);
                    }
                }
                state.last_commit_info =
                    Some((prev_word, word.clone(), std::time::Instant::now()));
            } else {
                state.last_commit_info = None;
            }

            if !state.surrounding_context.is_empty() {
                state.surrounding_context.push(' ');
            }
            state.surrounding_context.push_str(&word);

            let next_words = if state.layout == LekhaniLayoutType::English {
                if state.is_private_field {
                    Vec::new()
                } else {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    crate::english::get_english_next_words(&words, 5)
                }
            } else {
                get_bengali_next_words(&state.surrounding_context)
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
                get_bengali_next_words(&state.surrounding_context)
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

        let typed_buffer = std::mem::take(&mut state.composing_buffer);
        state.composing_buffer = String::with_capacity(64);
        let normalized = nfc_normalize(&candidate);
        let commit = format!("{} ", normalized);

        let prev_word = state
            .surrounding_context
            .split_whitespace()
            .last()
            .map(|s| s.to_string());

        if !state.is_private_field && state.auto_learn_enabled {
            let db = get_core_database();
            if let Ok(mut learner) = db.learner.write() {
                if !typed_buffer.is_empty() {
                    learner.record_candidate_selection(&typed_buffer, &normalized);
                }
                learner.observe_and_learn(&normalized, &db.trie);
                if let Some(ref p) = prev_word {
                    learner.observe_committed_pair(p, &normalized);
                }
            }
            state.last_commit_info =
                Some((prev_word, normalized.clone(), std::time::Instant::now()));
        } else {
            state.last_commit_info = None;
        }

        if !state.surrounding_context.is_empty() {
            state.surrounding_context.push(' ');
        }
        state.surrounding_context.push_str(&normalized);

        let next_words = if state.layout == LekhaniLayoutType::English {
            if state.is_private_field {
                Vec::new()
            } else {
                let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                crate::english::get_english_next_words(&words, 5)
            }
        } else {
            get_bengali_next_words(&state.surrounding_context)
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
            get_bengali_next_words(&state.surrounding_context)
                .into_iter()
                .take(limit)
                .collect()
        }
    }

    // ── Lifecycle helpers ─────────────────────────────────────────────────────


    /// Reset and clear all internal composing state.
    /// Called on `onFinishInput()` and layout switches.
    pub fn reset(&self) {
        if let Ok(mut state) = self.state.lock() {
            state.composing_buffer.clear();
            state.surrounding_context.clear();
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
    fn test_private_field_auto_switches_to_english() {
        let session = AndroidLekhaniSession::new();
        assert_eq!(session.get_layout(), LekhaniLayoutType::Probaho);
        session.set_private_field(true);
        assert_eq!(session.get_layout(), LekhaniLayoutType::English);
        assert!(session.is_private_field());
        // Leaving the private field restores nothing automatically —
        // Kotlin layer calls set_layout() with the user's preferred layout.
        session.set_private_field(false);
        assert!(!session.is_private_field());
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
    fn test_avro_homophone_context_disambiguation() {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);

        // 1. Context: "আমি বই " -> typing "pora" should rank "পড়া" first
        session.set_context("আমি বই".into());
        let _ = session.process_key("p".into()).unwrap();
        let _ = session.process_key("o".into()).unwrap();
        let _ = session.process_key("r".into()).unwrap();
        let res_book = session.process_key("a".into()).unwrap();
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
        println!("Continuous boi -> pora: preedit={}, candidates={:?}", res_cont.preedit, res_cont.candidates);
        assert_eq!(res_cont.preedit, "পড়া");

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
        println!("Continuous shirt -> pora: preedit={}, candidates={:?}", res_shirt_pora.preedit, res_shirt_pora.candidates);
        assert_eq!(res_shirt_pora.preedit, "পরা");
    }

    #[test]
    fn test_glide_decoding() {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);
        let res = session.decode_glide(vec!["a".into(), "m".into(), "i".into()]).unwrap();
        assert_eq!(res.preedit, "আমি");
        assert_eq!(res.commit_text, Some("আমি ".into()));
    }

    #[test]
    fn test_dictionary_management_session() {
        let session = AndroidLekhaniSession::new();
        session.add_user_word("টেস্টওয়ার্ড".into()).unwrap();
        let words = session.get_user_words().unwrap();
        assert!(words.contains(&"টেস্টওয়ার্ড".to_string()));

        let json = session.export_dictionary_json().unwrap();
        assert!(json.contains("টেস্টওয়ার্ড"));

        let deleted = session.delete_user_word("টেস্টওয়ার্ড".into()).unwrap();
        assert!(deleted);
        assert!(!session.get_user_words().unwrap().contains(&"টেস্টওয়ার্ড".to_string()));

        let imported = session.import_raw_words(vec!["শব্দএক".into(), "শব্দদুই".into()]).unwrap();
        assert_eq!(imported, 2);
        assert!(session.get_user_words().unwrap().contains(&"শব্দএক".to_string()));
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
    fn test_continuous_learning_and_persistence() {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::Avro);

        // 1. Candidate selection records override and learns word
        let _ = session.process_key("k".into()).unwrap();
        let _ = session.process_key("o".into()).unwrap();
        let _ = session.process_key("r".into()).unwrap();
        let _ = session.process_key("m".into()).unwrap();
        let _ = session.process_key("o".into()).unwrap();
        let sel = session.select_candidate("কৰ্ম".into()).unwrap();
        assert_eq!(sel.commit_text, Some("কৰ্ম ".into()));

        let db = get_core_database();
        {
            let learner = db.learner.read().unwrap();
            assert_eq!(learner.candidate_memory.get("kormo"), Some(&"কৰ্ম".to_string()));
            assert!(learner.observed_counts.contains_key("কৰ্ম"));
        }

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
        let test_path = temp_dir.join("lekhani_test_user_learned.bin").to_string_lossy().to_string();
        let save_ok = session.save_user_learned(test_path.clone()).unwrap();
        assert!(save_ok);

        let session2 = AndroidLekhaniSession::new();
        let load_ok = session2.load_user_learned(test_path.clone()).unwrap();
        assert!(load_ok);

        let _ = std::fs::remove_file(test_path);
    }

    #[test]
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
        assert!(prob_t > prob_y, "Center of 't' must have higher probability for 't' than 'y'");

        let ranked = session.rank_spatial_keys(135.0, 204.0, 2);
        assert_eq!(ranked.len(), 2);
        assert_eq!(ranked[0].key, "y");
        assert_eq!(ranked[1].key, "t");

        // Touch slightly to the left of 'y' (x = 138), should include 't' / 'y' suggestions
        let res = session.process_key_with_touch("y".into(), 138.0, 204.0).unwrap();
        assert_eq!(res.preedit, "y");
    }

    #[test]
    fn test_predict_next_words_async_helper() {
        let session = AndroidLekhaniSession::new();
        session.set_layout(LekhaniLayoutType::English);
        session.set_context("how are".into());

        let preds = session.predict_next_words(3);
        assert!(!preds.is_empty(), "English context 'how are' should produce next words");
        assert!(preds.contains(&"you".to_string()));
    }
}



