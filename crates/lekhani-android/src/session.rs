use std::sync::{Mutex, OnceLock};
use unicode_segmentation::UnicodeSegmentation;
use lekhani_core::phonetic::PhoneticDatabase;
use crate::error::LekhaniError;
use crate::layout::LekhaniLayoutType;
use crate::probaho::{get_conjunct_suggestions, nfc_normalize, promote_kar_if_needed};

static CORE_DB: OnceLock<PhoneticDatabase> = OnceLock::new();

pub fn get_core_database() -> &'static PhoneticDatabase {
    CORE_DB.get_or_init(PhoneticDatabase::new)
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
                // Pass-through; NFC on commit even for English (handles copy-paste of mixed text)
                Ok(TypingResult {
                    preedit: String::new(),
                    commit_text: Some(nfc_normalize(&key)),
                    candidates: Vec::new(),
                    cursor_position: 0,
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
                Ok(TypingResult {
                    preedit: lower.clone(),
                    commit_text: Some(format!("{} ", lower)),
                    candidates: vec![lower],
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
            normalized.push(' ');

            Ok(TypingResult {
                preedit: String::new(),
                commit_text: Some(normalized),
                candidates: Vec::new(),
                cursor_position: 0,
            })
        } else {
            Ok(TypingResult {
                preedit: String::new(),
                commit_text: Some(" ".to_string()),
                candidates: Vec::new(),
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
        let commit = format!("{} ", nfc_normalize(&candidate));

        Ok(TypingResult {
            preedit: String::new(),
            commit_text: Some(commit),
            candidates: Vec::new(),
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
}

