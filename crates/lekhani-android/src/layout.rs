use serde::{Deserialize, Serialize};
use crate::error::LekhaniError;
use crate::session::{SessionState, TypingResult};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default, uniffi::Enum)]
pub enum LekhaniLayoutType {
    /// Ergonomic two-thumb flow layout (Vowels on left, Consonants on right)
    #[default]
    Probaho,
    /// Classic phonetic transliteration
    Avro,
    /// Lekhani Phonetic Standard v1.0
    LpsPhonetic,
    /// Official Bangladesh BBS fixed standard (National / জাতীয়)
    National,
    /// Popular phonetic fixed layout (Probhat / প্রভাত)
    Probhat,
    /// Google Gboard style Bengali fixed mapping
    Gboard,
    /// Standard English QWERTY
    English,
}

/// Strategy trait for keyboard layout engines.
pub(crate) trait LayoutStrategy: Send + Sync {
    /// Process an individual keystroke according to the layout's input rules.
    fn process_key(&self, key: &str, state: &mut SessionState) -> Result<TypingResult, LekhaniError>;

    /// Resolve the committed text upon spacebar press.
    fn resolve_space_commit(&self, raw: &str, state: &mut SessionState, chosen_candidate: Option<&str>) -> String;

    /// Regenerate suggestions for the candidate strip during editing or cursor movement.
    fn regenerate_candidates(&self, state: &SessionState, input: &str) -> Vec<String>;
}

static LPS_ENGINE: std::sync::OnceLock<lekhani_parser::lps::LpsEngine> = std::sync::OnceLock::new();

#[inline]
pub(crate) fn get_lps_engine() -> &'static lekhani_parser::lps::LpsEngine {
    LPS_ENGINE.get_or_init(lekhani_parser::lps::LpsEngine::new)
}

pub(crate) struct LpsStrategy;

impl LayoutStrategy for LpsStrategy {
    fn process_key(&self, key: &str, state: &mut SessionState) -> Result<TypingResult, LekhaniError> {
        state.composing_buffer.push_str(key);
        let engine = get_lps_engine();
        let (lps_cands, _) = engine.parse(&state.composing_buffer);
        let mut candidates: Vec<String> = lps_cands.iter().map(|c| c.text.clone()).collect();

        // 🧠 Pragyan AI Context-Aware Ranking:
        // Rank homophones/candidates using preceding surrounding words
        if candidates.len() > 1 && !state.surrounding_context.is_empty() {
            let mut words_buf = [""; 16];
            let count = crate::session::get_context_words(&state.surrounding_context, &mut words_buf);
            if count > 0 {
                let context_words = &words_buf[..count];
                let right_word = state.right_context.split_whitespace().next();
                let scorer = crate::session::get_context_scorer();
                scorer.rank_candidates_in_place_bidirectional(context_words, right_word, &mut candidates);
            }
        }

        let preedit = candidates.first().cloned().unwrap_or_else(|| state.composing_buffer.clone());
        if !state.is_private_field && !candidates.contains(&state.composing_buffer) {
            candidates.push(state.composing_buffer.clone());
        }
        let len = unicode_segmentation::UnicodeSegmentation::graphemes(preedit.as_str(), true).count() as u32;
        Ok(TypingResult {
            preedit,
            commit_text: None,
            candidates,
            cursor_position: len,
        })
    }

    fn resolve_space_commit(&self, raw: &str, state: &mut SessionState, chosen_candidate: Option<&str>) -> String {
        if let Some(cand) = chosen_candidate {
            crate::probaho::nfc_normalize(cand)
        } else {
            let candidates = self.regenerate_candidates(state, raw);
            candidates.first().cloned().unwrap_or_else(|| raw.to_string())
        }
    }

    fn regenerate_candidates(&self, state: &SessionState, input: &str) -> Vec<String> {
        let engine = get_lps_engine();
        let (cands, _) = engine.parse(input);
        let mut candidates: Vec<String> = cands.into_iter().map(|c| c.text).collect();

        if candidates.len() > 1 && !state.surrounding_context.is_empty() {
            let mut words_buf = [""; 16];
            let count = crate::session::get_context_words(&state.surrounding_context, &mut words_buf);
            if count > 0 {
                let context_words = &words_buf[..count];
                let right_word = state.right_context.split_whitespace().next();
                let scorer = crate::session::get_context_scorer();
                scorer.rank_candidates_in_place_bidirectional(context_words, right_word, &mut candidates);
            }
        }
        candidates
    }
}

/// Dispatches the active layout type to its concrete Strategy instance.
pub(crate) fn get_layout_strategy(layout: LekhaniLayoutType) -> &'static dyn LayoutStrategy {
    match layout {
        LekhaniLayoutType::Avro => &crate::avro::AvroStrategy,
        LekhaniLayoutType::LpsPhonetic => &LpsStrategy,
        LekhaniLayoutType::English => &crate::english::EnglishStrategy,
        LekhaniLayoutType::Probaho => &crate::probaho::ProbahoStrategy,
        LekhaniLayoutType::National => &crate::probaho::NationalStrategy,
        LekhaniLayoutType::Probhat => &crate::probaho::ProbhatStrategy,
        LekhaniLayoutType::Gboard => &crate::probaho::GboardStrategy,
    }
}
