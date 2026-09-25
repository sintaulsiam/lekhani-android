use std::sync::Mutex;
use crate::error::LekhaniError;
use crate::probaho::nfc_normalize;

// ──────────────────────────────────────────────────────────────────────────────
// UniFFI Data Records
// ──────────────────────────────────────────────────────────────────────────────

/// Result returned after analyzing an audio frame chunk (16kHz PCM 16-bit mono).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AudioAnalysisResult {
    /// Normalized root-mean-square amplitude in range [0.0, 1.0].
    /// Used by the Compose waveform visualizer to animate sound waves.
    pub rms_level: f32,
    /// Whether this chunk contains active speech energy above the noise floor.
    pub is_speech: bool,
    /// True when speech was previously detected and silence has continuously
    /// exceeded the 1.5-second threshold (VAD auto-stop trigger).
    pub silence_timeout_triggered: bool,
    /// Total duration of speech processed so far in milliseconds.
    pub total_speech_ms: u32,
}

// ──────────────────────────────────────────────────────────────────────────────
// Audio Processor State
// ──────────────────────────────────────────────────────────────────────────────

struct AudioProcessorState {
    /// Has speech been detected at least once during this session?
    has_detected_speech: bool,
    /// Continuous silence elapsed in milliseconds since speech was last heard.
    consecutive_silence_ms: u32,
    /// Total active speech duration in milliseconds.
    total_speech_ms: u32,
    /// Energy threshold for speech detection (normalized RMS).
    speech_threshold: f32,
    /// Silence duration in ms before auto-stop is triggered (1500 ms).
    silence_timeout_ms: u32,
}

impl Default for AudioProcessorState {
    fn default() -> Self {
        Self {
            has_detected_speech: false,
            consecutive_silence_ms: 0,
            total_speech_ms: 0,
            speech_threshold: 0.035, // Typical speech threshold for mobile mic
            silence_timeout_ms: 1500, // 1.5 seconds per ROADMAP Phase 5
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// AsrAudioProcessor (UniFFI Object)
// ──────────────────────────────────────────────────────────────────────────────

/// Audio streaming processor for on-device voice typing.
///
/// Features (ROADMAP.md Phase 5):
///   - Real-time RMS decibel calculation for waveform rendering (< 0.1 ms hot path)
///   - Voice Activity Detection (VAD) with 1.5s automatic silence auto-stop
///   - Bengali punctuation auto-restoration (automatic `।`, `,`, `?`)
#[derive(uniffi::Object)]
pub struct AsrAudioProcessor {
    state: Mutex<AudioProcessorState>,
}

impl Default for AsrAudioProcessor {
    fn default() -> Self {
        Self::new()
    }
}

#[uniffi::export]
impl AsrAudioProcessor {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            state: Mutex::new(AudioProcessorState::default()),
        }
    }

    /// Process a chunk of 16-bit 16kHz mono PCM samples.
    ///
    /// Hot-path calculation: Computes RMS amplitude and tracks VAD state.
    /// Zero allocations on the heap except for the returned struct.
    pub fn process_pcm(&self, samples: Vec<i16>) -> Result<AudioAnalysisResult, LekhaniError> {
        let mut state = self.state.lock().map_err(|e| {
            LekhaniError::SessionError(format!("Lock poisoned: {e}"))
        })?;

        if samples.is_empty() {
            return Ok(AudioAnalysisResult {
                rms_level: 0.0,
                is_speech: false,
                silence_timeout_triggered: false,
                total_speech_ms: state.total_speech_ms,
            });
        }

        // Calculate RMS (Root-Mean-Square) normalized to 0.0..1.0
        let mut sum_sq: f64 = 0.0;
        for &sample in &samples {
            let normalized = sample as f64 / 32768.0;
            sum_sq += normalized * normalized;
        }
        let rms = ((sum_sq / samples.len() as f64).sqrt() as f32).clamp(0.0, 1.0);

        // At 16,000 samples per second, duration of this chunk in ms:
        // duration_ms = (samples.len() * 1000) / 16000 = samples.len() / 16
        let chunk_duration_ms = (samples.len() as u32) / 16;

        let is_speech = rms >= state.speech_threshold;
        let mut timeout_triggered = false;

        if is_speech {
            state.has_detected_speech = true;
            state.consecutive_silence_ms = 0;
            state.total_speech_ms += chunk_duration_ms;
        } else if state.has_detected_speech {
            state.consecutive_silence_ms += chunk_duration_ms;
            if state.consecutive_silence_ms >= state.silence_timeout_ms {
                timeout_triggered = true;
            }
        }

        Ok(AudioAnalysisResult {
            rms_level: rms,
            is_speech,
            silence_timeout_triggered: timeout_triggered,
            total_speech_ms: state.total_speech_ms,
        })
    }

    /// Reset all audio and VAD state for a new recording session.
    pub fn reset(&self) -> Result<(), LekhaniError> {
        let mut state = self.state.lock().map_err(|e| {
            LekhaniError::SessionError(format!("Lock poisoned: {e}"))
        })?;
        *state = AudioProcessorState::default();
        Ok(())
    }

    /// Restores Bengali punctuation (Dari `।`, Question mark `?`, and Comma `,`)
    /// based on sentence structure, interrogative keywords, and clause boundaries.
    pub fn restore_punctuation(&self, transcript: String) -> String {
        restore_bengali_punctuation(&transcript)
    }

    /// Returns whether speech has been detected in the current stream.
    pub fn has_detected_speech(&self) -> bool {
        self.state.lock().map(|s| s.has_detected_speech).unwrap_or(false)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Bengali Punctuation Auto-Restoration Engine
// ──────────────────────────────────────────────────────────────────────────────

/// Bengali interrogative markers (প্রশ্নবোধক শব্দ).
/// If a sentence contains any of these markers, it ends in '?' instead of '।'.
const INTERROGATIVES: &[&str] = &[
    "কী", "কি", "কেন", "কোথায়", "কোথায়", "কিভাবে", "কীভাবে",
    "কবে", "কে", "কার", "কাকে", "কখন", "কিসের", "কীসের", "কত",
];

/// Coordinating conjunctions that introduce clauses deserving a preceding comma.
const CONJUNCTIONS: &[&str] = &[
    "এবং", "কিন্তু", "অথবা", "তবে", "সুতরাং", "বরং", "নতুবা", "নচেৎ", "আর",
];

/// Restores Bengali punctuation to raw ASR transcript output.
///
/// Rules:
///   1. Trims whitespace and canonicalizes to NFC.
///   2. Inserts commas before coordinating conjunctions when preceded by words.
///   3. Inspects words for interrogative particles:
///      - If present -> appends '?'
///      - If absent  -> appends '।' (Bengali Dari)
///   4. Does not duplicate existing punctuation marks.
pub fn restore_bengali_punctuation(text: &str) -> String {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return String::new();
    }

    let nfc = nfc_normalize(trimmed);
    let words: Vec<&str> = nfc.split_whitespace().collect();
    if words.is_empty() {
        return String::new();
    }

    // Check if sentence already ends in a punctuation mark
    let last_char = nfc.chars().last().unwrap_or(' ');
    let already_punctuated = matches!(last_char, '।' | '?' | '!' | '.' | ',');

    let mut result: Vec<String> = Vec::new();
    for (i, &word) in words.iter().enumerate() {
        // Add comma before conjunction if it's not the first word and previous word has no comma
        if i > 0 && CONJUNCTIONS.contains(&word) {
            if let Some(prev) = result.last_mut() {
                let prev_str: &str = prev.as_str();
                if !prev_str.ends_with(',') && !prev_str.ends_with('।') && !prev_str.ends_with('?') {
                    *prev = format!("{},", prev_str);
                }
            }
        }
        result.push(word.to_string());
    }

    let mut joined = result.join(" ");

    if !already_punctuated {
        let is_question = words.iter().any(|&w| {
            let stripped = w.trim_matches(|c: char| c.is_ascii_punctuation() || c == '।' || c == ',');
            INTERROGATIVES.contains(&stripped)
        });

        if is_question {
            joined.push('?');
        } else {
            joined.push('।');
        }
    }

    joined
}

// ──────────────────────────────────────────────────────────────────────────────
// Unit Tests
// ──────────────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rms_calculation_and_silence() {
        let processor = AsrAudioProcessor::new();

        // 1600 samples (100ms at 16kHz) of pure zero silence
        let silence_samples = vec![0i16; 1600];
        let result = processor.process_pcm(silence_samples).unwrap();

        assert_eq!(result.rms_level, 0.0);
        assert!(!result.is_speech);
        assert!(!result.silence_timeout_triggered);
    }

    #[test]
    fn test_speech_detection_and_vad_timeout() {
        let processor = AsrAudioProcessor::new();

        // 16000 samples (1 second at 16kHz) of high amplitude square wave (speech)
        let mut speech_samples = Vec::with_capacity(16000);
        for i in 0..16000 {
            speech_samples.push(if (i / 100) % 2 == 0 { 15000i16 } else { -15000i16 });
        }

        let speech_res = processor.process_pcm(speech_samples).unwrap();
        assert!(speech_res.is_speech);
        assert!(speech_res.rms_level > 0.3);
        assert!(processor.has_detected_speech());

        // Now send 1.6 seconds of silence (16 chunks of 100ms = 1600ms > 1500ms timeout)
        let mut timeout_hit = false;
        for _ in 0..16 {
            let silence_chunk = vec![0i16; 1600];
            let res = processor.process_pcm(silence_chunk).unwrap();
            if res.silence_timeout_triggered {
                timeout_hit = true;
                break;
            }
        }

        assert!(timeout_hit, "1.5s silence should trigger timeout after speech was detected");
    }

    #[test]
    fn test_bengali_punctuation_dari() {
        let text = "আমি ভাত খাব";
        let punctuated = restore_bengali_punctuation(text);
        assert_eq!(punctuated, "আমি ভাত খাব।");
    }

    #[test]
    fn test_bengali_punctuation_question() {
        let text1 = "তুমি কেমন আছো এবং তোমার নাম কী";
        let punctuated1 = restore_bengali_punctuation(text1);
        assert_eq!(punctuated1, "তুমি কেমন আছো, এবং তোমার নাম কী?");

        let text2 = "সে কেন গেল";
        let punctuated2 = restore_bengali_punctuation(text2);
        assert_eq!(punctuated2, "সে কেন গেল?");
    }

    #[test]
    fn test_bengali_punctuation_conjunction_comma() {
        let text = "আমি বাজারে যাব কিন্তু কিছু কিনব না";
        let punctuated = restore_bengali_punctuation(text);
        assert_eq!(punctuated, "আমি বাজারে যাব, কিন্তু কিছু কিনব না।");
    }

    #[test]
    fn test_bengali_punctuation_already_punctuated() {
        let text = "বাংলাদেশ একটি সুন্দর দেশ।";
        let punctuated = restore_bengali_punctuation(text);
        assert_eq!(punctuated, "বাংলাদেশ একটি সুন্দর দেশ।");
    }
}
