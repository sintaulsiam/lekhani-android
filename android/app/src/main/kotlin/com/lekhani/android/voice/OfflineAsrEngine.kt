package com.lekhani.android.voice

import com.lekhani.android.ffi.AsrAudioProcessor
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level state of the offline ASR engine.
 */
sealed class VoiceTypingState {
    data object Idle : VoiceTypingState()
    data class Listening(val rmsLevel: Float, val partialTranscript: String) : VoiceTypingState()
    data class Completed(val finalTranscript: String) : VoiceTypingState()
    data class Error(val message: String) : VoiceTypingState()
}

/**
 * OfflineAsrEngine
 *
 * Contract for 100% on-device embedded speech-to-text recognition.
 *
 * Design constraints:
 *   - Zero Network: Engine must execute 100% locally from bundled APK assets or DPS.
 *   - Sub-second responsiveness: VAD auto-stop within 1.5s of silence.
 *   - Canonical Unicode: Restored Bengali punctuation (Dari `।`, `?`, `,`).
 */
interface OfflineAsrEngine {
    val state: StateFlow<VoiceTypingState>

    /** Returns true if local ASR models are installed and initialized on-device. */
    fun isModelReady(): Boolean

    /** Starts recording audio and streaming to the local recognizer. */
    fun startListening()

    /** Stops recording, flushes audio, and returns the final punctuated transcript. */
    fun stopListening(): String

    /** Cancels current recording and discards audio buffers. */
    fun cancel()

    /** Releases all hardware audio resources and decoder instances. */
    fun release()
}
