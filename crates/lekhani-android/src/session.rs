use crate::error::LekhaniError;
use crate::layout::LekhaniLayoutType;
pub use crate::loaders::*;
use crate::probaho::nfc_normalize;
use lekhani_core::phonetic::PhoneticDatabase;
use std::sync::Mutex;
use unicode_segmentation::UnicodeSegmentation;

const MAX_CONTEXT_CHARS: usize = 512;

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

/// Semantic category of a keyboard candidate item for rich UI strip rendering.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum CandidateKind {
    /// Deterministic Avro rule transliteration (Slot 0 / Slot 1 bypass)
    DeterministicAvro,
    /// High-frequency statistical / context-ranked candidate
    ContextualAi,
    /// Exact user override / learned personal dictionary word
    LearnedMemory,
    /// Typo recovery / fat-finger spatial correction
    TypoRescue,
    /// Literal English passthrough / tech acronym
    EnglishPassthrough,
    /// Bengali numeric or currency representation
    NumericLiteral,
    /// Honorific / grammatical agreement verb (Anvaya)
    GrammarConcordant,
}

/// Detailed candidate item with semantic metadata, exported via UniFFI.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CandidateItem {
    /// The candidate text to be committed if selected
    pub text: String,
    /// Semantic classification of this candidate
    pub kind: CandidateKind,
    /// Relative ranking score (1.0 = top pick)
    pub score: f64,
    /// True if this candidate is the current primary preedit / default commit
    pub is_primary: bool,
}

/// Letter-level ambiguous chip group for UI presentation in mobile candidate strip.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LpsLetterChipItem {
    pub char_index: u32,
    pub active: String,
    pub siblings: Vec<String>,
}

/// Project "পশ্চাৎ-শোধন" (Retro-Correction) Suggestion
///
/// Surfaces a discrete, 1-tap contextual fix chip when a grammatical agreement
/// clash (e.g. আপনি + আছো -> আছেন) or homophone error (e.g. বই পরা -> পড়া)
/// is detected in the surrounding sentence context.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct RetroCorrection {
    /// The misspelled or grammatically clashing original word
    pub original_word: String,
    /// The recommended contextual replacement
    pub replacement_word: String,
    /// Explanation of why the correction is proposed
    pub reason: String,
    /// Formatted UI chip label (e.g. "🪄 আছেন")
    pub chip_label: String,
    /// Number of Unicode grapheme characters to delete backwards before committing replacement
    pub chars_to_backspace: u32,
    /// The text to commit after backspacing
    pub text_to_commit: String,
}

/// Disambiguation result comparing two competing homophones under running context topic.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TopicDisambiguationResult {
    /// The winning candidate word with highest semantic topic alignment
    pub winner: String,
    /// The runner-up competing candidate word
    pub runner_up: String,
    /// Confidence margin between winner and runner-up in [-1.0, 1.0]
    pub confidence: f32,
    /// Semantic domain hint in Bengali (e.g. "পড়ালেখা", "পোশাক", "আদালত", "নদী/তীর")
    pub domain_hint: Option<String>,
}

/// Dynamic semantic topic annotation for a candidate in the candidate strip.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CandidateTopicAnnotation {
    /// The candidate word
    pub candidate: String,
    /// Contrasting homophone partner word (e.g. "পরা" for candidate "পড়া")
    pub homophone_partner: Option<String>,
    /// Inferred semantic domain hint (e.g. "পড়ালেখা", "পোশাক", "আদালত")
    pub domain_hint: Option<String>,
    /// True if this candidate is the disambiguated winner under active topic context
    pub is_topic_winner: bool,
}

/// Bayesian short-window dampener for Project "পশ্চাৎ-শোধন" (Retro-Correction).
///
/// Tracks the last 7 chip interactions in a bounded sliding window:
/// - Explicit dismiss: weight -2.0
/// - Passive ignore: weight -0.2
/// - Accept: weight +1.5
///
/// Using a Beta(1.0, 3.0) prior, if the posterior acceptance probability drops
/// below 0.18, chips are suppressed until 50 words are typed without annoyance.
#[derive(Debug, Clone)]
pub struct RetroDampener {
    history: Vec<(bool, bool)>, // (is_accept, is_explicit)
    words_since_suppression: usize,
    is_suppressed: bool,
}

impl Default for RetroDampener {
    fn default() -> Self {
        Self {
            history: Vec::with_capacity(7),
            words_since_suppression: 0,
            is_suppressed: false,
        }
    }
}

impl RetroDampener {
    pub fn record_accept(&mut self) {
        if self.history.len() >= 7 {
            self.history.remove(0);
        }
        self.history.push((true, false));
        self.update_suppression();
    }

    pub fn record_dismiss(&mut self, explicit: bool) {
        if self.history.len() >= 7 {
            self.history.remove(0);
        }
        self.history.push((false, explicit));
        self.update_suppression();
    }

    pub fn on_word_typed(&mut self) {
        if self.is_suppressed {
            self.words_since_suppression += 1;
            if self.words_since_suppression >= 50 {
                self.is_suppressed = false;
                self.words_since_suppression = 0;
                self.history.clear();
            }
        }
    }

    fn update_suppression(&mut self) {
        let mut alpha = 1.0f32;
        let mut beta = 3.0f32;

        for &(is_accept, explicit) in &self.history {
            if is_accept {
                alpha += 1.5;
            } else if explicit {
                beta += 2.0;
            } else {
                beta += 0.2;
            }
        }

        let posterior_prob = alpha / (alpha + beta);
        if posterior_prob < 0.18 {
            self.is_suppressed = true;
            self.words_since_suppression = 0;
        }
    }

    pub fn is_suppressed(&self) -> bool {
        self.is_suppressed
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Internal session state  (never crosses the FFI boundary)
// ──────────────────────────────────────────────────────────────────────────────

/// Memento recording a committed word action for rapid backspace mistake penalization and context rollback.
#[derive(Debug, Clone)]
pub(crate) struct CommitAction {
    pub(crate) previous_word: Option<String>,
    pub(crate) committed_text: String,
    pub(crate) timestamp: std::time::Instant,
}

impl CommitAction {
    pub(crate) fn new(previous_word: Option<String>, committed_text: String) -> Self {
        Self {
            previous_word,
            committed_text,
            timestamp: std::time::Instant::now(),
        }
    }

    #[inline]
    pub(crate) fn is_rapid_backspace(&self, threshold_ms: u128) -> bool {
        self.timestamp.elapsed().as_millis() <= threshold_ms
    }

    pub(crate) fn undo_context(&self, context: &mut String) {
        if context.ends_with(&self.committed_text) {
            let trim_len = context.len() - self.committed_text.len();
            context.truncate(trim_len);
            if context.ends_with(' ') {
                context.pop();
            }
        }
    }
}

pub(crate) struct SessionState {
    pub(crate) layout: LekhaniLayoutType,
    /// Raw composing buffer.  Always kept as valid Unicode; grapheme operations
    /// use `unicode-segmentation` to avoid splitting multi-byte clusters.
    pub(crate) composing_buffer: String,
    /// Last ~256 chars before the cursor, obtained from `getTextBeforeCursor()`.
    /// Used by the AI layer for contextual homophone ranking.
    pub(crate) surrounding_context: String,
    /// Tracked character count of surrounding_context to avoid repeated O(n) scans
    pub(crate) surrounding_context_len: usize,
    /// Characters immediately following the cursor (up to 64 chars) from `getTextAfterCursor()`.
    /// Used by the AI layer for bi-directional context scoring.
    pub(crate) right_context: String,
    /// Whether the session is currently in a secure/incognito field.
    /// When true: no learning, no clipboard capture, auto English layout.
    pub(crate) is_private_field: bool,
    /// Whether autonomous learning of words from typing is enabled.
    pub(crate) auto_learn_enabled: bool,
    /// Memento of last committed word, optional preceding word, and timestamp.
    /// Used for rapid backspace mistake penalty (<1500 ms).
    pub(crate) last_commit_action: Option<CommitAction>,
    /// Bivariate Gaussian spatial touch model for fat-finger correction
    pub(crate) spatial_model: crate::spatial::SpatialTouchModel,
    /// Uncommitted per-tap touch coordinates `(key, x, y)` for the active word.
    /// Calibrated into `spatial_model` ONLY after a clean commit (never on typos).
    pub(crate) pending_touch_samples: Vec<(String, f32, f32)>,
    /// Whether initial Kar auto-promotion and post-consonant demotion is enabled
    pub(crate) smart_initial_kar_enabled: bool,
    /// Whether geminate consonant double-tap shortcut is enabled
    pub(crate) geminate_double_tap_enabled: bool,
    /// Whether Hasanta ligature quick-picks are enabled
    pub(crate) hasanta_conjuncts_enabled: bool,
    /// Timestamp and character of last consonant keystroke for rapid double-tap geminates
    pub(crate) last_consonant_tap: Option<(char, std::time::Instant)>,
    /// Whether pure classic Avro mode is enabled (bypassing AI reranking & Anvaya)
    pub(crate) pure_avro_mode: bool,
    /// Bayesian dampener for retro-corrections
    pub(crate) retro_dampener: RetroDampener,
}

impl SessionState {
    pub(crate) fn append_to_context(&mut self, text: &str) {
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

fn is_honorific_shift_internal(typed: &str, candidate: &str) -> bool {
    if lekhani_ai::AnvayaAgreementEngine::is_honorific_shift(typed, candidate) {
        return true;
    }
    let cand_tier = lekhani_ai::AnvayaAgreementEngine::classify_verb_tier(candidate);
    if cand_tier == lekhani_ai::VerbConjugationTier::Neutral {
        return false;
    }

    if typed.is_ascii() {
        let clean = typed.to_ascii_lowercase();
        let typed_tier = if clean.ends_with("chen")
            || clean.ends_with("ben")
            || clean.ends_with("len")
            || clean.ends_with("ten")
            || clean.ends_with("en")
        {
            lekhani_ai::VerbConjugationTier::Formal
        } else if clean.ends_with("chis")
            || clean.ends_with("is")
            || clean.ends_with("bi")
            || clean.ends_with("li")
            || clean.ends_with("ti")
        {
            lekhani_ai::VerbConjugationTier::Intimate
        } else if clean.ends_with("cho")
            || clean.ends_with('o')
            || clean.ends_with("be")
            || clean.ends_with("le")
            || clean.ends_with("te")
        {
            lekhani_ai::VerbConjugationTier::Familiar
        } else {
            lekhani_ai::VerbConjugationTier::Neutral
        };

        if typed_tier != lekhani_ai::VerbConjugationTier::Neutral && typed_tier != cand_tier {
            return true;
        }

        let parser = crate::avro::get_avro_parser();
        let pure = parser.convert(typed);
        if lekhani_ai::AnvayaAgreementEngine::is_honorific_shift(&pure, candidate) {
            return true;
        }
    }
    false
}

pub(crate) fn get_context_words<'a>(context: &'a str, buffer: &mut [&'a str; 16]) -> usize {
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
pub(crate) fn get_bengali_next_words(context: &str, right_context: Option<&str>) -> Vec<String> {
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

/// Helper to classify candidate text into semantic CandidateKind
pub fn classify_candidate(
    input: &str,
    cand: &str,
    index: usize,
    db: &PhoneticDatabase,
) -> CandidateKind {
    let lower = input.to_lowercase();
    if cand.is_empty() {
        return CandidateKind::ContextualAi;
    }
    // 1. Numeric / currency / digit unit
    if cand
        .chars()
        .any(|c| ('\u{09E6}'..='\u{09EF}').contains(&c) || c.is_ascii_digit())
        && (cand.contains("টাকা")
            || cand.contains("টি")
            || cand.contains("টা")
            || cand.starts_with('৳')
            || cand.starts_with('$')
            || cand.chars().all(|c| {
                ('\u{09E6}'..='\u{09EF}').contains(&c)
                    || c.is_ascii_digit()
                    || c == '.'
                    || c == ','
                    || c == ' '
            }))
    {
        return CandidateKind::NumericLiteral;
    }
    // 2. English / Acronym passthrough
    if cand.is_ascii() {
        return CandidateKind::EnglishPassthrough;
    }
    // 3. Learned Memory
    if let Ok(learner) = db.learner.read() {
        if learner.lookup_input_error(input) == Some(cand)
            || learner.lookup_input_error(&lower) == Some(cand)
            || learner.candidate_memory.get(input).map(|s| s.as_str()) == Some(cand)
            || learner.candidate_memory.get(&lower).map(|s| s.as_str()) == Some(cand)
            || learner.custom_user_words.contains(cand)
        {
            return CandidateKind::LearnedMemory;
        }
    }
    // 4. Deterministic Avro
    let def = crate::avro::get_avro_parser().convert(input);
    if !def.is_empty() && cand == def {
        return CandidateKind::DeterministicAvro;
    }
    // 5. Grammar Concordant
    if !def.is_empty() && lekhani_ai::AnvayaAgreementEngine::is_honorific_shift(&def, cand) {
        return CandidateKind::GrammarConcordant;
    }
    // 6. Typo rescue (heuristic: placed at slot 3)
    if index == 3 {
        return CandidateKind::TypoRescue;
    }
    // 7. Contextual AI default
    CandidateKind::ContextualAi
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
        let epoch_now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        if epoch_now > 0 {
            let db = get_core_database();
            if let Ok(mut l) = db.learner.write() {
                l.decay_if_needed(epoch_now);
            };
        }

        Self {
            state: Mutex::new(SessionState {
                layout: LekhaniLayoutType::Probaho,
                composing_buffer: String::with_capacity(64),
                surrounding_context: String::with_capacity(256),
                surrounding_context_len: 0,
                right_context: String::with_capacity(64),
                is_private_field: false,
                auto_learn_enabled: true,
                last_commit_action: None,
                spatial_model: crate::spatial::SpatialTouchModel::new(),
                pending_touch_samples: Vec::with_capacity(16),
                smart_initial_kar_enabled: true,
                geminate_double_tap_enabled: false,
                hasanta_conjuncts_enabled: true,
                last_consonant_tap: None,
                pure_avro_mode: false,
                retro_dampener: RetroDampener::default(),
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
            state.pending_touch_samples.clear();
            state.last_consonant_tap = None;
        }
    }

    /// Return the currently active layout.
    pub fn get_layout(&self) -> LekhaniLayoutType {
        self.state
            .lock()
            .map(|s| s.layout)
            .unwrap_or(LekhaniLayoutType::Probaho)
    }

    /// Returns the letter-level disambiguation chips for the active LPS composing buffer.
    pub fn get_lps_letter_chips(&self) -> Vec<LpsLetterChipItem> {
        if let Ok(state) = self.state.lock() {
            if state.layout == LekhaniLayoutType::LpsPhonetic && !state.composing_buffer.is_empty()
            {
                let engine = crate::layout::get_lps_engine();
                let (cands, _) = engine.parse(&state.composing_buffer);
                for cand in cands {
                    if !cand.chips.is_empty() {
                        return cand
                            .chips
                            .into_iter()
                            .map(|c| LpsLetterChipItem {
                                char_index: c.char_index as u32,
                                active: c.active,
                                siblings: c.siblings,
                            })
                            .collect();
                    }
                }
            }
        }
        Vec::new()
    }

    /// Generates canonical exact LPS Latin form for given Bengali text.
    pub fn exact_form(&self, text: String) -> String {
        lekhani_parser::lps::exact(&text)
    }

    /// Transliterates the given raw Latin string into pure, rule-based deterministic Avro (NFC),
    /// completely bypassing any surrounding context, N-gram scorer, or AI contextual adaptation.
    pub fn transliterate_pure_avro(&self, raw: String) -> String {
        let (primary, _) = crate::avro::transliterate_avro(&raw);
        crate::probaho::nfc_normalize(&primary)
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

    /// Enable or disable Pure Classic Avro Mode (bypasses all AI ranking & Anvaya).
    pub fn set_pure_avro_mode(&self, enabled: bool) {
        if let Ok(mut state) = self.state.lock() {
            state.pure_avro_mode = enabled;
        }
    }

    /// Returns whether Pure Classic Avro Mode is currently enabled.
    pub fn is_pure_avro_mode(&self) -> bool {
        self.state.lock().map(|s| s.pure_avro_mode).unwrap_or(false)
    }

    /// Enable or disable bilingual hyphen/unhyphenated clitic resolution (default: false).
    pub fn set_bilingual_clitic_enabled(&self, enabled: bool) {
        crate::avro::set_bilingual_clitic_enabled(enabled);
    }

    /// Returns whether bilingual clitic resolution is currently enabled.
    pub fn is_bilingual_clitic_enabled(&self) -> bool {
        crate::avro::is_bilingual_clitic_enabled()
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

    /// Calibrates the personal spatial touch model with a verified key tap coordinate (x, y).
    pub fn record_touch_sample(&self, key: String, touch_x: f32, touch_y: f32) {
        if let Ok(mut state) = self.state.lock() {
            state
                .spatial_model
                .record_touch_sample(&key, touch_x, touch_y);
        }
    }

    /// Exports learned touch calibration offsets to a compact JSON string for DPS persistence.
    pub fn export_spatial_calibration(&self) -> String {
        self.state
            .lock()
            .map(|s| s.spatial_model.export_calibration_json())
            .unwrap_or_else(|_| "{}".to_string())
    }

    /// Imports personal touch calibration offsets from JSON and restores active key offsets.
    pub fn import_spatial_calibration(&self, json_str: String) -> bool {
        if let Ok(mut state) = self.state.lock() {
            state.spatial_model.import_calibration_json(&json_str)
        } else {
            false
        }
    }

    /// Process a typed character or key token with physical touch coordinates (x, y).
    /// Incorporates bivariate Gaussian spatial probabilities to handle fat-finger errors
    /// on key boundaries across the active composing word, and queues touch samples for
    /// verified post-commit calibration (never calibrating on unverified/mistyped taps).
    pub fn process_key_with_touch(
        &self,
        key: String,
        touch_x: f32,
        touch_y: f32,
    ) -> Result<TypingResult, LekhaniError> {
        let mut result = self.process_key(key.clone())?;

        let mut state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        if result.commit_text.is_some() || state.composing_buffer.is_empty() {
            state.pending_touch_samples.clear();
            return Ok(result);
        }

        if state.pending_touch_samples.len() < 32 {
            state.pending_touch_samples.push((key, touch_x, touch_y));
        }

        if !state.spatial_model.is_empty() && state.pending_touch_samples.len() <= 14 {
            let db = get_core_database();
            let raw_buf = state.composing_buffer.clone();
            let current_is_valid = match state.layout {
                LekhaniLayoutType::English => crate::english::get_english_trie()
                    .map(|t| t.contains_exact(&raw_buf.to_lowercase()))
                    .unwrap_or(false),
                LekhaniLayoutType::Avro | LekhaniLayoutType::LpsPhonetic => {
                    db.is_exact_dictionary_word(&result.preedit)
                        || db.trie.contains_exact(&result.preedit)
                        || crate::avro::has_override_word(&raw_buf)
                }
                _ => db.trie.contains_exact(&result.preedit),
            };

            // Evaluate spatial neighbor swaps at each tap position (starting from most recent)
            let mut best_slot0: Option<(String, Vec<String>, f32)> = None;
            let tap_count = state.pending_touch_samples.len();
            let raw_chars: Vec<char> = raw_buf.chars().collect();

            if tap_count == raw_chars.len() {
                for pos in (0..tap_count).rev() {
                    let (ref tap_key, tx, ty) = state.pending_touch_samples[pos];
                    let spatial_candidates = state.spatial_model.rank_keys_at(tx, ty, 2);
                    if spatial_candidates.len() < 2 {
                        continue;
                    }
                    let neighbor = if spatial_candidates[0].key == *tap_key {
                        &spatial_candidates[1]
                    } else {
                        &spatial_candidates[0]
                    };
                    if neighbor.key.chars().count() != 1 {
                        continue;
                    }
                    let key_prob = state.spatial_model.log_prob_for_key(tap_key, tx, ty);
                    if (neighbor.log_prob - key_prob).abs() > 2.8 {
                        continue;
                    }

                    let mut alt_chars = raw_chars.clone();
                    if let Some(nc) = neighbor.key.chars().next() {
                        alt_chars[pos] = nc;
                    }
                    let alt_buf: String = alt_chars.into_iter().collect();

                    match state.layout {
                        LekhaniLayoutType::English => {
                            let alt_cands = crate::english::get_english_candidates(&alt_buf, 3);
                            let exact_match = crate::english::get_english_trie()
                                .and_then(|t| t.get_exact(&alt_buf.to_lowercase()))
                                .map(|(_, f)| f)
                                .unwrap_or(0);
                            if !current_is_valid && exact_match > 0 {
                                let score = neighbor.log_prob + (exact_match as f32).ln_1p();
                                if best_slot0.as_ref().is_none_or(|b| score > b.2) {
                                    best_slot0 = Some((alt_buf, alt_cands, score));
                                }
                            } else {
                                for cand in alt_cands {
                                    if !result.candidates.contains(&cand)
                                        && result.candidates.len() < 7
                                    {
                                        result.candidates.push(cand);
                                    }
                                }
                            }
                        }
                        LekhaniLayoutType::Avro => {
                            let (alt_preedit, alt_cands) =
                                crate::avro::transliterate_avro(&alt_buf);
                            let alt_top = alt_cands.first().cloned().unwrap_or(alt_preedit);
                            let alt_freq = db.get_frequency(&alt_top);
                            if !current_is_valid
                                && (alt_freq > 0
                                    || db.trie.contains_exact(&alt_top)
                                    || crate::avro::has_override_word(&alt_buf))
                            {
                                let score = neighbor.log_prob + (alt_freq as f32).ln_1p();
                                if best_slot0.as_ref().is_none_or(|b| score > b.2) {
                                    best_slot0 = Some((alt_top, alt_cands, score));
                                }
                            } else {
                                for cand in alt_cands {
                                    if !result.candidates.contains(&cand)
                                        && result.candidates.len() < 7
                                    {
                                        result.candidates.push(cand);
                                    }
                                }
                            }
                        }
                        LekhaniLayoutType::LpsPhonetic => {
                            let engine = lekhani_parser::lps::LpsEngine::new();
                            let (alt_cands, _) = engine.parse(&alt_buf);
                            for cand in alt_cands {
                                if !result.candidates.contains(&cand.text)
                                    && result.candidates.len() < 7
                                {
                                    result.candidates.push(cand.text);
                                }
                            }
                        }
                        LekhaniLayoutType::Probaho
                        | LekhaniLayoutType::Probhat
                        | LekhaniLayoutType::National
                        | LekhaniLayoutType::Gboard => {
                            let prefix_matches = db.trie.find_prefix_entries(&alt_buf, 3);
                            if !current_is_valid && db.trie.contains_exact(&alt_buf) {
                                let freq = db.trie.get_frequency(&alt_buf);
                                let score = neighbor.log_prob + (freq as f32).ln_1p();
                                let cands: Vec<String> = prefix_matches
                                    .into_iter()
                                    .map(|(w, _)| w.to_string())
                                    .collect();
                                if best_slot0.as_ref().is_none_or(|b| score > b.2) {
                                    best_slot0 = Some((alt_buf, cands, score));
                                }
                            } else {
                                for (word, _) in prefix_matches {
                                    let w = word.to_string();
                                    if !result.candidates.contains(&w)
                                        && result.candidates.len() < 7
                                    {
                                        result.candidates.push(w);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if let Some((promoted_preedit, promoted_cands, _)) = best_slot0 {
                let old_preedit = result.preedit.clone();
                result.preedit = promoted_preedit.clone();
                result.cursor_position = promoted_preedit.graphemes(true).count() as u32;
                let mut merged = Vec::with_capacity(7);
                merged.push(promoted_preedit);
                if !old_preedit.is_empty() && !merged.contains(&old_preedit) {
                    merged.push(old_preedit);
                }
                for c in promoted_cands.into_iter().chain(result.candidates) {
                    if !merged.contains(&c) && merged.len() < 7 {
                        merged.push(c);
                    }
                }
                result.candidates = merged;
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

        if key == " " {
            drop(state);
            return self.handle_space();
        }

        // Universal punctuation flush across all layouts:
        // Punctuation and newlines immediately flush the composing buffer as committed text.
        // Exception: In Avro layout, a dot preceded by purely numeric digits is a decimal fraction separator (e.g. 8.4, 21.7).
        let is_decimal_dot_in_flight = key == "."
            && (state.layout == LekhaniLayoutType::Avro
                || state.layout == LekhaniLayoutType::LpsPhonetic)
            && !state.composing_buffer.is_empty()
            && state
                .composing_buffer
                .chars()
                .all(|c| c.is_ascii_digit() || ('\u{09E6}'..='\u{09EF}').contains(&c));
        let is_punct = (key == "।"
            || key == "॥"
            || key == ","
            || key == ";"
            || (key == ":"
                && state.layout != LekhaniLayoutType::Avro
                && state.layout != LekhaniLayoutType::LpsPhonetic)
            || key == "?"
            || key == "!"
            || key == "."
            || key == "\n")
            && !is_decimal_dot_in_flight;

        if is_punct {
            let mut committed =
                if state.layout == LekhaniLayoutType::Avro && !state.composing_buffer.is_empty() {
                    let raw = std::mem::take(&mut state.composing_buffer);
                    if state.pure_avro_mode {
                        let parser = crate::avro::get_avro_parser();
                        parser.convert(&raw)
                    } else {
                        let mut words_buf = [""; 16];
                        let count = if !state.surrounding_context.is_empty() {
                            get_context_words(&state.surrounding_context, &mut words_buf)
                        } else {
                            0
                        };
                        let words = &words_buf[..count];
                        let right_word = state.right_context.split_whitespace().next();
                        let (preedit, candidates) =
                            crate::avro::resolve_avro_candidates(&raw, words, right_word);
                        candidates.first().cloned().unwrap_or(preedit)
                    }
                } else if state.layout == LekhaniLayoutType::LpsPhonetic
                    && !state.composing_buffer.is_empty()
                {
                    let raw = std::mem::take(&mut state.composing_buffer);
                    let engine = lekhani_parser::lps::LpsEngine::new();
                    let (cands, _) = engine.parse(&raw);
                    cands.first().map(|c| c.text.clone()).unwrap_or(raw)
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
                state.last_commit_action = Some(CommitAction::new(prev_word, word.clone()));
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
            state.last_consonant_tap = None;

            let next_words = if state.is_private_field || state.pure_avro_mode {
                Vec::new()
            } else if state.layout == LekhaniLayoutType::English {
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

            state.retro_dampener.on_word_typed();

            return Ok(TypingResult {
                preedit: String::new(),
                commit_text: Some(nfc_normalize(&committed)),
                candidates: next_words,
                cursor_position: 0,
            });
        }

        crate::layout::get_layout_strategy(state.layout).process_key(&key, &mut state)
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
                let right_word = state.right_context.split_whitespace().next();
                let (top_word, candidates) =
                    crate::avro::resolve_avro_candidates(&raw_token, words, right_word);

                if candidates.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
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
                let prefix_2 = if keys.len() >= 2 {
                    format!("{}{}", keys[0], keys[1])
                } else {
                    start_key.clone()
                };
                let mut entries = db.trie.find_prefix_entries(&prefix_2, 300);
                if entries.is_empty() {
                    entries = db.trie.find_prefix_entries(start_key, 1500);
                }
                let path_chars: Vec<char> = keys.iter().filter_map(|k| k.chars().next()).collect();

                let mut scored: Vec<(String, i64)> = Vec::new();
                for (word, freq) in entries {
                    if !word.ends_with(end_key.as_str()) {
                        continue;
                    }
                    let word_chars: Vec<char> = word.chars().collect();
                    let mut key_idx = 0;
                    let mut matched = true;
                    let mut prev = '\0';
                    for &c in &word_chars {
                        if c == prev {
                            continue;
                        }
                        prev = c;
                        let mut found = false;
                        while key_idx < path_chars.len() {
                            if path_chars[key_idx] == c {
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
                        let len_diff =
                            (word.chars().count() as isize - path_chars.len() as isize).abs();
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

    /// Checks whether `candidate` represents an honorific register shift relative to `typed`.
    #[uniffi::method]
    pub fn is_honorific_shift(&self, typed: String, candidate: String) -> bool {
        is_honorific_shift_internal(&typed, &candidate)
    }

    /// Returns the honorific verb paradigm (Formal, Familiar, Intimate) if `verb` is a recognized inflected verb.
    #[uniffi::method]
    pub fn get_verb_honorific_paradigm(&self, verb: String) -> Vec<String> {
        let clean = crate::probaho::nfc_normalize(verb.trim());
        let mut results = Vec::new();
        if let Some(formal) = lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(
            &clean,
            lekhani_ai::HonorificRegister::FormalHonorific,
        ) {
            results.push(formal.to_string());
        }
        if let Some(familiar) = lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(
            &clean,
            lekhani_ai::HonorificRegister::Familiar,
        ) {
            let s = familiar.to_string();
            if !results.contains(&s) {
                results.push(s);
            }
        }
        if let Some(intimate) = lekhani_ai::AnvayaAgreementEngine::synthesize_concordant_verb(
            &clean,
            lekhani_ai::HonorificRegister::Intimate,
        ) {
            let s = intimate.to_string();
            if !results.contains(&s) {
                results.push(s);
            }
        }
        results
    }

    /// Disambiguates between two competing phonetic homophones based on surrounding conversational topic.
    /// Returns the winning candidate, confidence delta, and optional semantic domain hint.
    #[uniffi::method]
    pub fn disambiguate_homophone(
        &self,
        cand_a: String,
        cand_b: String,
    ) -> Option<TopicDisambiguationResult> {
        let state = self.state.lock().ok()?;
        let tokens: Vec<&str> = if state.surrounding_context.is_empty() {
            Vec::new()
        } else {
            state.surrounding_context.split_whitespace().collect()
        };
        if tokens.is_empty() {
            return None;
        }

        let scorer = get_context_scorer();
        let (winner, delta, domain_hint) =
            scorer.disambiguate_homophone_with_domain_by_topic(&tokens, &cand_a, &cand_b)?;

        let winner_str = winner.to_string();
        let domain_hint_str = domain_hint.map(|s| s.to_string());
        let runner_up = if winner_str == cand_a { cand_b } else { cand_a };
        Some(TopicDisambiguationResult {
            winner: winner_str,
            runner_up,
            confidence: delta,
            domain_hint: domain_hint_str,
        })
    }

    /// Evaluates the candidate list against the active semantic topic vector.
    /// Detects homophones, determines topic-grounded winners, and attaches domain hints.
    #[uniffi::method]
    pub fn annotate_candidate_topics(
        &self,
        candidates: Vec<String>,
    ) -> Vec<CandidateTopicAnnotation> {
        if candidates.is_empty() {
            return Vec::new();
        }

        let state = match self.state.lock() {
            Ok(s) => s,
            Err(_) => return Vec::new(),
        };

        let scorer = get_context_scorer();
        let tokens: Vec<&str> = if state.surrounding_context.is_empty() {
            Vec::new()
        } else {
            state.surrounding_context.split_whitespace().collect()
        };

        let topic_vec = if !tokens.is_empty() {
            Some(scorer.compute_topic_vector(&tokens))
        } else {
            None
        };

        candidates
            .into_iter()
            .map(|cand| {
                let info = lekhani_ai::lookup_homophone_info(&cand);
                let (homophone_partner, domain_hint) = match info {
                    Some((partner, hint)) => (Some(partner.to_string()), Some(hint.to_string())),
                    None => (None, None),
                };

                let is_topic_winner = if let (Some(ref topic), Some(ref partner)) =
                    (&topic_vec, &homophone_partner)
                {
                    topic
                        .disambiguate(&cand, partner, scorer.embeddings())
                        .map(|(winner, _)| winner == cand)
                        .unwrap_or(false)
                } else {
                    false
                };

                CandidateTopicAnnotation {
                    candidate: cand,
                    homophone_partner,
                    domain_hint,
                    is_topic_winner,
                }
            })
            .collect()
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
        state.last_consonant_tap = None;

        if !state.composing_buffer.is_empty() {
            state.pending_touch_samples.pop();
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
                } else if state.pure_avro_mode {
                    let parser = crate::avro::get_avro_parser();
                    let preedit = parser.convert(&state.composing_buffer);
                    let len = preedit.graphemes(true).count() as u32;
                    let candidates = vec![preedit.clone()];
                    Ok(TypingResult {
                        preedit,
                        commit_text: None,
                        candidates,
                        cursor_position: len,
                    })
                } else {
                    let mut words_buf = [""; 16];
                    let count = if !state.surrounding_context.is_empty() {
                        get_context_words(&state.surrounding_context, &mut words_buf)
                    } else {
                        0
                    };
                    let words = &words_buf[..count];
                    let right_word = state.right_context.split_whitespace().next();
                    let (preedit, candidates) = crate::avro::resolve_avro_candidates(
                        &state.composing_buffer,
                        words,
                        right_word,
                    );
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
            } else if state.layout == LekhaniLayoutType::LpsPhonetic {
                state.composing_buffer.pop();
                if state.composing_buffer.is_empty() {
                    Ok(TypingResult {
                        preedit: String::new(),
                        commit_text: None,
                        candidates: Vec::new(),
                        cursor_position: 0,
                    })
                } else {
                    let engine = lekhani_parser::lps::LpsEngine::new();
                    let (lps_cands, _) = engine.parse(&state.composing_buffer);
                    let mut candidates: Vec<String> =
                        lps_cands.iter().map(|c| c.text.clone()).collect();
                    let preedit = candidates
                        .first()
                        .cloned()
                        .unwrap_or_else(|| state.composing_buffer.clone());
                    if !state.is_private_field && !candidates.contains(&state.composing_buffer) {
                        candidates.push(state.composing_buffer.clone());
                    }
                    let len = unicode_segmentation::UnicodeSegmentation::graphemes(
                        preedit.as_str(),
                        true,
                    )
                    .count() as u32;
                    Ok(TypingResult {
                        preedit,
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
            if let Some(action) = state.last_commit_action.take() {
                if action.is_rapid_backspace(1500) && !state.is_private_field {
                    let db = get_core_database();
                    if let Ok(mut learner) = db.learner.write() {
                        learner.penalize_mistake(
                            action.previous_word.as_deref(),
                            &action.committed_text,
                        );
                    };
                }
                action.undo_context(&mut state.surrounding_context);
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
            let pending_taps = std::mem::take(&mut state.pending_touch_samples);
            if chosen_candidate.is_none() && !state.is_private_field {
                for (tap_key, tx, ty) in pending_taps {
                    state.spatial_model.record_touch_sample(&tap_key, tx, ty);
                }
            }
            let prev_word = state
                .surrounding_context
                .split_whitespace()
                .last()
                .map(|s| s.to_string());

            let mut normalized = crate::layout::get_layout_strategy(state.layout)
                .resolve_space_commit(&raw, &mut state, chosen_candidate.as_deref());
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
                state.last_commit_action = Some(CommitAction::new(prev_word, word.clone()));
            } else {
                state.last_commit_action = None;
            }

            state.append_to_context(&word);
            state.retro_dampener.on_word_typed();

            let next_words = if state.is_private_field || state.pure_avro_mode {
                Vec::new()
            } else if state.layout == LekhaniLayoutType::English {
                let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                crate::english::get_english_next_words(&words, 5)
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
            state.last_commit_action = None;
            state.retro_dampener.on_word_typed();
            let next_words = if state.is_private_field || state.pure_avro_mode {
                Vec::new()
            } else if state.layout == LekhaniLayoutType::English {
                let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
                crate::english::get_english_next_words(&words, 5)
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

        // ── Fixed Layouts (Probaho, Probhat, Gboard, National) In-Flight Conjunct Substitution ────────────────
        // If a fixed Bengali layout is active and the composing buffer ends with Hasanta (`্`),
        // selecting a conjunct candidate (e.g. `ক্ষ` or `ক্ত`) replaces the base consonant
        // and Hasanta in-place, keeping the composing buffer alive so the typist can
        // seamlessly continue typing subsequent Kars/consonants (e.g. `শিক্` + `ক্ষ` -> `শিক্ষ` + `া` -> `শিক্ষা`).
        if state.layout != LekhaniLayoutType::Avro
            && state.layout != LekhaniLayoutType::English
            && state.layout != LekhaniLayoutType::LpsPhonetic
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
        state.pending_touch_samples.clear();
        state.last_consonant_tap = None;
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
            let sugg_mutex = get_phonetic_suggestion();
            let (chosen_feat, rejected_feat) = {
                let sugg = match sugg_mutex.lock() {
                    Ok(g) => g,
                    Err(p) => p.into_inner(),
                };
                let last_cands = sugg.get_last_computed_candidates();
                let last_feats = sugg.get_last_computed_features();
                let chosen_idx = last_cands.iter().position(|c| c == &normalized);
                if let Some(idx) = chosen_idx {
                    if idx > 0 {
                        (last_feats.get(idx).copied(), last_feats.first().copied())
                    } else {
                        (None, None)
                    }
                } else {
                    (None, None)
                }
            };

            let db = get_core_database();
            if let Ok(mut learner) = db.learner.write() {
                if let (Some(c), Some(r)) = (chosen_feat, rejected_feat) {
                    learner.rank_weights.update_online(&c, &r, 20.0);
                    learner.dirty = true;
                }
                // Record candidate selection overrides for inputs > 2 characters
                // and non-ASCII candidates to prevent Latin/English selections from poisoning Bengali phonetic memory.
                // Uses 1-shot direct learning for explicit candidate strip taps (matching desktop parity).
                if typed_buffer.chars().count() > 2
                    && !normalized.is_ascii()
                    && !typed_buffer.eq_ignore_ascii_case(&normalized)
                    && !is_honorific_shift_internal(&typed_buffer, &normalized)
                {
                    learner.record_candidate_selection_direct(&typed_buffer, &normalized);
                    learner.record_input_error_direct(&typed_buffer, &normalized);
                }
                if !normalized.is_ascii() {
                    learner.observe_and_learn(&normalized, &db.trie);
                    if let Some(ref p) = prev_word {
                        learner.observe_committed_pair(p, &normalized);
                    }
                }
            }
            state.last_commit_action = Some(CommitAction::new(prev_word, normalized.clone()));
        } else {
            state.last_commit_action = None;
        }

        state.append_to_context(&normalized);

        let next_words = if state.is_private_field {
            Vec::new()
        } else if state.layout == LekhaniLayoutType::English {
            let words: Vec<&str> = state.surrounding_context.split_whitespace().collect();
            crate::english::get_english_next_words(&words, 5)
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
            state.pending_touch_samples.clear();
            state.surrounding_context.clear();
            state.surrounding_context_len = 0;
            state.right_context.clear();
            state.is_private_field = false;
            state.last_commit_action = None;
            state.last_consonant_tap = None;
        }
    }

    /// Explicitly penalize a reverted commit or mistaken auto-correction
    pub fn penalize_commit(&self, reverted_word: String) -> Result<(), LekhaniError> {
        let mut state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;

        if !state.is_private_field {
            let prev = state
                .last_commit_action
                .take()
                .and_then(|a| a.previous_word);
            let db = get_core_database();
            if let Ok(mut learner) = db.learner.write() {
                learner.penalize_mistake(prev.as_deref(), &reverted_word);
            };
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

    /// Evaluates the active clause for Project "পশ্চাৎ-শোধন" (Retro-Correction).
    pub fn get_retro_correction(&self) -> Option<RetroCorrection> {
        let state = match self.state.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        if state.is_private_field || state.pure_avro_mode || state.retro_dampener.is_suppressed() {
            return None;
        }
        evaluate_retro_correction(&state.surrounding_context)
    }

    /// Record whether the user explicitly or passively dismissed a retro-correction chip.
    pub fn record_retro_correction_dismissed(&self, explicit: bool) {
        if let Ok(mut state) = self.state.lock() {
            state.retro_dampener.record_dismiss(explicit);
        }
    }

    /// Atomically applies a retro-correction to the active editor.
    pub fn apply_retro_correction(
        &self,
        correction: RetroCorrection,
    ) -> Result<TypingResult, LekhaniError> {
        let mut state = match self.state.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };

        state.retro_dampener.record_accept();

        if state.surrounding_context_len >= correction.chars_to_backspace as usize {
            let trim_chars = state.surrounding_context_len - correction.chars_to_backspace as usize;
            let byte_idx = state
                .surrounding_context
                .char_indices()
                .nth(trim_chars)
                .map(|(i, _)| i)
                .unwrap_or(0);
            state.surrounding_context.truncate(byte_idx);
            state.surrounding_context_len = trim_chars;
        }

        state.append_to_context(&correction.text_to_commit);

        Ok(TypingResult {
            preedit: String::new(),
            commit_text: Some(correction.text_to_commit),
            candidates: Vec::new(),
            cursor_position: 0,
        })
    }

    /// Returns rich semantic candidate items for the active composing buffer or context.
    pub fn get_candidate_items(&self) -> Result<Vec<CandidateItem>, LekhaniError> {
        let state = self
            .state
            .lock()
            .map_err(|e| LekhaniError::SessionError(e.to_string()))?;
        let input = state.composing_buffer.clone();
        let db = get_core_database();
        if input.is_empty() {
            let next_words = if state.layout == LekhaniLayoutType::English {
                let words: Vec<&str> = state
                    .surrounding_context
                    .split_whitespace()
                    .filter(|w| w.is_ascii())
                    .collect();
                crate::english::get_english_next_words(&words, 5)
            } else {
                get_bengali_next_words(&state.surrounding_context, Some(&state.right_context))
            };
            return Ok(next_words
                .into_iter()
                .enumerate()
                .map(|(i, w)| CandidateItem {
                    text: w,
                    kind: CandidateKind::ContextualAi,
                    score: 1.0 - (i as f64 * 0.1),
                    is_primary: i == 0,
                })
                .collect());
        }

        let candidates =
            crate::layout::get_layout_strategy(state.layout).regenerate_candidates(&state, &input);

        let total = candidates.len();
        let items = candidates
            .into_iter()
            .enumerate()
            .map(|(i, cand)| {
                let kind = classify_candidate(&input, &cand, i, &db);
                let score = if total <= 1 {
                    1.0
                } else {
                    1.0 - (i as f64 / total as f64) * 0.5
                };
                CandidateItem {
                    text: cand,
                    kind,
                    score,
                    is_primary: i == 0,
                }
            })
            .collect();
        Ok(items)
    }

    /// Classifies an arbitrary list of candidate strings against an input token.
    pub fn classify_candidates(
        &self,
        input: String,
        candidates: Vec<String>,
    ) -> Vec<CandidateItem> {
        let db = get_core_database();
        let total = candidates.len();
        candidates
            .into_iter()
            .enumerate()
            .map(|(i, cand)| {
                let kind = classify_candidate(&input, &cand, i, &db);
                let score = if total <= 1 {
                    1.0
                } else {
                    1.0 - (i as f64 / total as f64) * 0.5
                };
                CandidateItem {
                    text: cand,
                    kind,
                    score,
                    is_primary: i == 0,
                }
            })
            .collect()
    }
}

/// Project "পশ্চাৎ-শোধন" (Retro-Correction) Engine
///
/// Analyzes the active clause in the surrounding context to surface discrete, 1-tap
/// corrections for honorific clashes, intimate clashes, and contextual homophones.
pub fn evaluate_retro_correction(surrounding_context: &str) -> Option<RetroCorrection> {
    if surrounding_context.is_empty() {
        return None;
    }

    let clause = surrounding_context
        .rsplit(['।', '?', '!', '\n'])
        .next()
        .unwrap_or(surrounding_context)
        .trim();

    let tokens: Vec<&str> = clause.split_whitespace().collect();
    if tokens.is_empty() {
        return None;
    }

    let token_count = tokens.len();

    // 1. Honorific Concordance: Formal Subject (আপনি, আপনারা, তিনি, তাঁরা)
    let has_formal_subject = tokens.iter().any(|&t| {
        matches!(
            t,
            "আপনি"
                | "আপনারা"
                | "আপনার"
                | "আপনাকে"
                | "তিনি"
                | "তাঁরা"
                | "তাঁর"
                | "তাঁকে"
                | "উনি"
        )
    });

    if has_formal_subject {
        for (idx, &token) in tokens.iter().enumerate().rev().take(2) {
            let replacement = match token {
                "আছো" => Some("আছেন"),
                "করছো" => Some("করছেন"),
                "যাচ্ছো" => Some("যাচ্ছেন"),
                "বলছো" => Some("বলছেন"),
                "খাচ্ছো" => Some("খাচ্ছেন"),
                "দেখছো" => Some("দেখছেন"),
                "শুনছো" => Some("শুনছেন"),
                "আসছো" => Some("আসছেন"),
                _ => None,
            };

            if let Some(repl) = replacement {
                let offset_from_end = token_count - 1 - idx;
                let chars_to_backspace = if offset_from_end == 0 {
                    token.chars().count() as u32
                } else {
                    let trailing_tokens = &tokens[idx..];
                    trailing_tokens.join(" ").chars().count() as u32
                };

                let text_to_commit = if offset_from_end == 0 {
                    repl.to_string()
                } else {
                    let mut text = repl.to_string();
                    for t in &tokens[idx + 1..] {
                        text.push(' ');
                        text.push_str(t);
                    }
                    text
                };

                return Some(RetroCorrection {
                    original_word: token.to_string(),
                    replacement_word: repl.to_string(),
                    reason: "শ্রদ্ধাবাচক সর্বনামের সাথে ক্রিয়ার সঙ্গতি".to_string(),
                    chip_label: repl.to_string(),
                    chars_to_backspace,
                    text_to_commit,
                });
            }
        }
    }

    // 2. Intimate Concordance: Intimate Subject (তুই, তোরা, তোর)
    let has_intimate_subject = tokens
        .iter()
        .any(|&t| matches!(t, "তুই" | "তোরা" | "তোর" | "তোকে"));
    if has_intimate_subject {
        for (idx, &token) in tokens.iter().enumerate().rev().take(2) {
            let replacement = match token {
                "আছো" | "আছেন" => Some("আছিস"),
                "করছো" | "করছেন" => Some("করছিস"),
                "যাচ্ছো" | "যাচ্ছেন" => Some("যাচ্ছিস"),
                "বলছো" | "বলছেন" => Some("বলছিস"),
                "খাচ্ছো" | "খাচ্ছেন" => Some("খাচ্ছিস"),
                _ => None,
            };

            if let Some(repl) = replacement {
                let offset_from_end = token_count - 1 - idx;
                let chars_to_backspace = if offset_from_end == 0 {
                    token.chars().count() as u32
                } else {
                    let trailing_tokens = &tokens[idx..];
                    trailing_tokens.join(" ").chars().count() as u32
                };

                let text_to_commit = if offset_from_end == 0 {
                    repl.to_string()
                } else {
                    let mut text = repl.to_string();
                    for t in &tokens[idx + 1..] {
                        text.push(' ');
                        text.push_str(t);
                    }
                    text
                };

                return Some(RetroCorrection {
                    original_word: token.to_string(),
                    replacement_word: repl.to_string(),
                    reason: "ঘনিষ্ঠ সর্বনামের সাথে ক্রিয়ার সঙ্গতি".to_string(),
                    chip_label: repl.to_string(),
                    chars_to_backspace,
                    text_to_commit,
                });
            }
        }
    }

    // 3. Contextual Homophone Retro-Correction
    if token_count >= 2 {
        for idx in 0..token_count - 1 {
            let t1 = tokens[idx];
            let t2 = tokens[idx + 1];

            // Case A: [বই, পরা] or [পরা, বই]
            if (matches!(t1, "বই" | "পত্রিকা" | "ক্লাস" | "পরীক্ষা" | "লেখা") && t2 == "পরা")
                || (t1 == "পরা" && matches!(t2, "বই" | "পত্রিকা" | "ক্লাস" | "পরীক্ষা" | "লেখা"))
            {
                let target_idx = if t2 == "পরা" { idx + 1 } else { idx };
                let offset_from_end = token_count - 1 - target_idx;
                let chars_to_backspace = if offset_from_end == 0 {
                    t2.chars().count() as u32
                } else {
                    let trailing_tokens = &tokens[target_idx..];
                    trailing_tokens.join(" ").chars().count() as u32
                };

                let text_to_commit = if offset_from_end == 0 {
                    "পড়া".to_string()
                } else {
                    let mut text = "পড়া".to_string();
                    for t in &tokens[target_idx + 1..] {
                        text.push(' ');
                        text.push_str(t);
                    }
                    text
                };

                return Some(RetroCorrection {
                    original_word: "পরা".to_string(),
                    replacement_word: "পড়া".to_string(),
                    reason: "বই পড়ার সাথে সঙ্গতিপূর্ণ বানান".to_string(),
                    chip_label: "পড়া".to_string(),
                    chars_to_backspace,
                    text_to_commit,
                });
            }

            // Case B: [শার্ট, পড়া] or [পড়া, শার্ট]
            if (matches!(t1, "শার্ট" | "প্যান্ট" | "জামা" | "কাপড়" | "জুতো" | "ঘড়ি") && t2 == "পড়া")
                || (t1 == "পড়া"
                    && matches!(
                        t2,
                        "শার্ট"
                            | "প্যান্ট"
                            | "জামা"
                            | "কাপড়"
                            | "জুতো"
                            | "ঘড়ি"
                    ))
            {
                let target_idx = if t2 == "পড়া" { idx + 1 } else { idx };
                let offset_from_end = token_count - 1 - target_idx;
                let chars_to_backspace = if offset_from_end == 0 {
                    t2.chars().count() as u32
                } else {
                    let trailing_tokens = &tokens[target_idx..];
                    trailing_tokens.join(" ").chars().count() as u32
                };

                let text_to_commit = if offset_from_end == 0 {
                    "পরা".to_string()
                } else {
                    let mut text = "পরা".to_string();
                    for t in &tokens[target_idx + 1..] {
                        text.push(' ');
                        text.push_str(t);
                    }
                    text
                };

                return Some(RetroCorrection {
                    original_word: "পড়া".to_string(),
                    replacement_word: "পরা".to_string(),
                    reason: "পোশাক পরার সাথে সঙ্গতিপূর্ণ বানান".to_string(),
                    chip_label: "পরা".to_string(),
                    chars_to_backspace,
                    text_to_commit,
                });
            }
        }
    }

    None
}

// ──────────────────────────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────────────────────────

#[cfg(test)]
#[path = "session_tests.rs"]
mod tests;
