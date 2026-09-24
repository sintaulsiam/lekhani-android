use thiserror::Error;

#[derive(Error, Debug, uniffi::Error)]
pub enum LekhaniError {
    #[error("Session error: {0}")]
    SessionError(String),

    #[error("Invalid layout: {0}")]
    InvalidLayout(String),

    #[error("Invalid input character: {0}")]
    InvalidInput(String),

    #[error("Candidate index out of bounds: {0}")]
    IndexOutOfBounds(u32),
}
