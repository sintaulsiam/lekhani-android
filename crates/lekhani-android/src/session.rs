use std::sync::{Mutex, OnceLock};
use unicode_segmentation::UnicodeSegmentation;
use lekhani_core::phonetic::PhoneticDatabase;
use crate::error::LekhaniError;
use crate::layout::LekhaniLayoutType;
use crate::probaho::{get_conjunct_suggestions, nfc_normalize, promote_kar_if_needed};

static CORE_DB: OnceLock<PhoneticDatabase> = OnceLock::new();

pub fn get_core_database() -> &'static PhoneticDatabase {
    CORE_DB.get_or_init(|| {
        let mut db = PhoneticDatabase::new();
        let candidate_dirs = [
            std::path::Path::new("/data/data/com.lekhani.android/files/dictionaries"),
            std::path::Path::new("/data/user/0/com.lekhani.android/files/dictionaries"),
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

    // ── Surrounding text context ─────────────────────────────────────────────

    /// Provide the text before the cursor (from `getTextBeforeCursor(256, 0)`)
    /// for contextual homophone disambiguation in the AI scorer.
    pub fn set_context(&self, context: String) {
        if let Ok(mut state) = self.state.lock() {
            state.surrounding_context = context;
        }
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
                    candidates.push(state.composing_buffer.clone());
                    // Tier 1 lekhani-core PrefixTrie lookup for prefix completions
                    let db = get_core_database();
                    let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 4);
                    for (word, _) in prefix_matches {
                        if !candidates.iter().any(|c| c == word) {
                            candidates.push(word.to_string());
                        }
                    }
                }
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = lekhani_ai::ContextScorer::new();
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
                let (preedit, mut candidates) = crate::avro::transliterate_avro(&state.composing_buffer);
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = lekhani_ai::ContextScorer::new();
                    candidates = scorer.rank_candidates(&words, &candidates);
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
                    candidates.push(state.composing_buffer.clone());
                    let db = get_core_database();
                    let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 4);
                    for (word, _) in prefix_matches {
                        if !candidates.iter().any(|c| c == word) {
                            candidates.push(word.to_string());
                        }
                    }
                }

                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = lekhani_ai::ContextScorer::new();
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
                // Fixed / transliteration layouts — accumulate in buffer, query PrefixTrie
                state.composing_buffer.push_str(&key);
                let mut candidates = vec![state.composing_buffer.clone()];
                let db = get_core_database();
                let prefix_matches = db.trie.find_prefix_entries(&state.composing_buffer, 4);
                for (word, _) in prefix_matches {
                    if !candidates.iter().any(|c| c == word) {
                        candidates.push(word.to_string());
                    }
                }
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = lekhani_ai::ContextScorer::new();
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

        if keys.is_empty() {
            return Ok(TypingResult {
                preedit: String::new(),
                commit_text: None,
                candidates: Vec::new(),
                cursor_position: 0,
            });
        }

        let raw_token = keys.join("");
        match state.layout {
            LekhaniLayoutType::Avro => {
                let (preedit, mut candidates) = crate::avro::transliterate_avro(&raw_token);
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = lekhani_ai::ContextScorer::new();
                    candidates = scorer.rank_candidates(&words, &candidates);
                }
                let len = preedit.graphemes(true).count() as u32;
                let top_word = if candidates.is_empty() { preedit.clone() } else { candidates[0].clone() };
                Ok(TypingResult {
                    preedit: preedit.clone(),
                    commit_text: Some(format!("{} ", top_word)),
                    candidates,
                    cursor_position: len,
                })
            }
            LekhaniLayoutType::English => {
                let lower = raw_token.to_lowercase();
                let candidates = crate::english::get_english_candidates(&lower, 5);
                let top_word = if candidates.is_empty() { lower.clone() } else { candidates[0].clone() };
                Ok(TypingResult {
                    preedit: lower,
                    commit_text: Some(format!("{} ", top_word)),
                    candidates,
                    cursor_position: 0,
                })
            }
            _ => {
                // Bengali layouts (Probaho, Probhat, National, Gboard)
                let normalized = nfc_normalize(&raw_token);
                let db = get_core_database();
                let mut candidates = vec![normalized.clone()];
                let prefix_matches = db.trie.find_prefix_entries(&normalized, 4);
                for (word, _) in prefix_matches {
                    if !candidates.iter().any(|c| c == word) {
                        candidates.push(word.to_string());
                    }
                }
                if !state.surrounding_context.is_empty() && candidates.len() > 1 {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    let scorer = lekhani_ai::ContextScorer::new();
                    candidates = scorer.rank_candidates(&words, &candidates);
                }
                let len = normalized.graphemes(true).count() as u32;
                let top_word = if candidates.is_empty() { normalized.clone() } else { candidates[0].clone() };
                Ok(TypingResult {
                    preedit: normalized.clone(),
                    commit_text: Some(format!("{} ", top_word)),
                    candidates,
                    cursor_position: len,
                })
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
                // Collect grapheme cluster byte-index boundaries
                let last_grapheme_start = state
                    .composing_buffer
                    .grapheme_indices(true)
                    .next_back()
                    .map(|(i, _)| i)
                    .unwrap_or(0);
                state.composing_buffer.truncate(last_grapheme_start);

                let len = state.composing_buffer.graphemes(true).count() as u32;
                let candidates = if state.composing_buffer.is_empty() {
                    Vec::new()
                } else {
                    vec![state.composing_buffer.clone()]
                };

                Ok(TypingResult {
                    preedit: state.composing_buffer.clone(),
                    commit_text: None,
                    candidates,
                    cursor_position: len,
                })
            }
        } else {
            // Buffer empty — signal Android to delete the preceding character
            // in the target application's InputConnection.
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
                let (transliterated, _) = crate::avro::transliterate_avro(&raw);
                nfc_normalize(&transliterated)
            } else {
                nfc_normalize(&raw)
            };
            let word = normalized.clone();
            normalized.push(' ');

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
                let predictor = lekhani_ai::NextWordPredictor::new();
                let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                predictor.predict_next(&words, 5)
            };

            Ok(TypingResult {
                preedit: String::new(),
                commit_text: Some(normalized),
                candidates: next_words,
                cursor_position: 0,
            })
        } else {
            let next_words = if state.layout == LekhaniLayoutType::English {
                if state.is_private_field {
                    Vec::new()
                } else {
                    let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                    crate::english::get_english_next_words(&words, 5)
                }
            } else {
                let predictor = lekhani_ai::NextWordPredictor::new();
                let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                predictor.predict_next(&words, 5)
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

        state.composing_buffer.clear();
        state.composing_buffer = String::with_capacity(64);
        let normalized = nfc_normalize(&candidate);
        let commit = format!("{} ", normalized);

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
            let predictor = lekhani_ai::NextWordPredictor::new();
            let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
            predictor.predict_next(&words, 5)
        };

        Ok(TypingResult {
            preedit: String::new(),
            commit_text: Some(commit),
            candidates: next_words,
            cursor_position: 0,
        })
    }

    // ── Lifecycle helpers ─────────────────────────────────────────────────────

    /// Reset and clear all internal composing state.
    /// Called on `onFinishInput()` and layout switches.
    pub fn reset(&self) {
        if let Ok(mut state) = self.state.lock() {
            state.composing_buffer.clear();
            state.surrounding_context.clear();
            state.is_private_field = false;
        }
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
        // Type ক্ষ (ka + hasanta + sha).
        // Unicode treats the full conjunct as ONE grapheme cluster once sha is appended.
        // So each of the 3 process_key calls grows the raw codepoint buffer, but the
        // grapheme count goes: 1 (ক) → 1 (ক্) → 1 (ক্ষ).
        // A single backspace therefore removes the ENTIRE conjunct at once,
        // leaving an empty buffer.
        let _ = session.process_key("ক".into()).unwrap();
        assert_eq!(session.handle_backspace().unwrap().preedit, "");

        // Re-type ক্ষ step by step, showing the conjunct formation:
        let _ = session.process_key("ক".into()).unwrap();
        let _ = session.process_key("্".into()).unwrap(); // ক্  (incomplete conjunct, 1 cluster)
        let _ = session.process_key("ষ".into()).unwrap(); // ক্ষ (complete conjunct, 1 cluster)
        // One backspace removes the complete conjunct cluster.
        let r = session.handle_backspace().unwrap();
        assert_eq!(r.preedit, "",
            "Expected full conjunct ক্ষ to be deleted as a single grapheme cluster");

        // Buffer now empty — next backspace signals system delete
        let empty = session.handle_backspace().unwrap();
        assert_eq!(empty.preedit, "");
        assert_eq!(empty.commit_text, None);
    }

    #[test]
    fn test_backspace_and_candidate_selection() {
        let session = AndroidLekhaniSession::new();
        // Type 'ব' then 'া'.
        // 'বা' (ba + aa-kar) is ONE grapheme cluster in Unicode — the Aa-kar
        // is a combining character that attaches to the base consonant.
        // A single backspace therefore removes the whole 'বা' cluster.
        let _ = session.process_key("ব".into()).unwrap();
        let _ = session.process_key("া".into()).unwrap();
        // Backspace on 'বা' (1 grapheme) → empty buffer
        assert_eq!(session.handle_backspace().unwrap().preedit, "");

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
}

