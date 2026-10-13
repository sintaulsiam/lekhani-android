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

/// Dispatches the active layout type to its concrete Strategy instance.
pub(crate) fn get_layout_strategy(layout: LekhaniLayoutType) -> &'static dyn LayoutStrategy {
    match layout {
        LekhaniLayoutType::Avro => &crate::avro::AvroStrategy,
        LekhaniLayoutType::English => &crate::english::EnglishStrategy,
        LekhaniLayoutType::Probaho => &crate::probaho::ProbahoStrategy,
        LekhaniLayoutType::National => &crate::probaho::NationalStrategy,
        LekhaniLayoutType::Probhat => &crate::probaho::ProbhatStrategy,
        LekhaniLayoutType::Gboard => &crate::probaho::GboardStrategy,
    }
}
