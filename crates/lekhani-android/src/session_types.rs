/// Result returned to Android `InputConnection` after processing a key event.
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

/// Project "পশ্চাৎ-শোধন" (Retro-Correction) Suggestion.
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
    /// Formatted UI chip label
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
    history: Vec<(bool, bool)>,
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
