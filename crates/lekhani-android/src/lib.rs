pub mod audio;
pub mod avro;
pub mod english;
pub mod error;
pub mod layout;
pub mod loaders;
pub mod probaho;
pub mod session;
pub mod session_types;
pub mod spatial;

pub use audio::{restore_bengali_punctuation, AsrAudioProcessor, AudioAnalysisResult};
pub use error::LekhaniError;
pub use layout::LekhaniLayoutType;
pub use session::{
    set_dictionary_directory, AndroidLekhaniSession, CandidateItem, CandidateKind,
    LpsLetterChipItem, TypingResult,
};
pub use spatial::{KeyGeometryConfig, SpatialKeyCandidate, SpatialTouchModel};

uniffi::setup_scaffolding!("lekhani_android");
