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

pub(crate) struct LpsStrategy;

impl LayoutStrategy for LpsStrategy {
    fn process_key(&self, key: &str, state: &mut SessionState) -> Result<TypingResult, LekhaniError> {
        state.composing_buffer.push_str(key);
        let engine = lekhani_parser::lps::LpsEngine::new();
        let (lps_cands, _) = engine.parse(&state.composing_buffer);
        let mut candidates: Vec<String> = lps_cands.iter().map(|c| c.text.clone()).collect();
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

    fn resolve_space_commit(&self, raw: &str, _state: &mut SessionState, chosen_candidate: Option<&str>) -> String {
        if let Some(cand) = chosen_candidate {
            crate::probaho::nfc_normalize(cand)
        } else {
            let engine = lekhani_parser::lps::LpsEngine::new();
            let (cands, _) = engine.parse(raw);
            cands.first().map(|c| c.text.clone()).unwrap_or_else(|| raw.to_string())
        }
    }

    fn regenerate_candidates(&self, _state: &SessionState, input: &str) -> Vec<String> {
        let engine = lekhani_parser::lps::LpsEngine::new();
        let (cands, _) = engine.parse(input);
        cands.into_iter().map(|c| c.text).collect()
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
