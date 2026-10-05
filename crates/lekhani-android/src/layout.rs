use serde::{Deserialize, Serialize};

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
