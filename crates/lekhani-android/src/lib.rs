pub mod audio;
pub mod avro;
pub mod english;
pub mod error;
pub mod layout;
pub mod probaho;
pub mod session;
pub mod spatial;

pub use audio::{AsrAudioProcessor, AudioAnalysisResult, restore_bengali_punctuation};
pub use error::LekhaniError;
pub use layout::LekhaniLayoutType;
pub use session::{AndroidLekhaniSession, TypingResult, set_dictionary_directory};
pub use spatial::{KeyGeometryConfig, SpatialKeyCandidate, SpatialTouchModel};

uniffi::setup_scaffolding!("lekhani_android");

