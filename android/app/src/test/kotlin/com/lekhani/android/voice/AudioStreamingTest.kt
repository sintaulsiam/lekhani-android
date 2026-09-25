package com.lekhani.android.voice

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for voice typing state management and audio chunk mathematics.
 * Pure JVM test suite.
 */
class AudioStreamingTest {

    @Test
    fun `audio chunk duration calculation is accurate for 16kHz`() {
        val sampleRate = AudioStreamingManager.SAMPLE_RATE
        val chunkSize = AudioStreamingManager.CHUNK_SIZE_SAMPLES

        // Duration in ms = (chunkSize * 1000) / sampleRate
        val durationMs = (chunkSize * 1000) / sampleRate
        assertEquals(100, durationMs)

        // 1.5-second silence timeout equates to 15 consecutive 100ms chunks
        val chunksForTimeout = 1500 / durationMs
        assertEquals(15, chunksForTimeout)
    }

    @Test
    fun `voice typing states preserve values correctly`() {
        val idleState = VoiceTypingState.Idle
        assertEquals(VoiceTypingState.Idle, idleState)

        val listeningState = VoiceTypingState.Listening(
            rmsLevel = 0.45f,
            partialTranscript = "আমি ভাত"
        )
        assertEquals(0.45f, listeningState.rmsLevel, 0.001f)
        assertEquals("আমি ভাত", listeningState.partialTranscript)

        val completedState = VoiceTypingState.Completed(
            finalTranscript = "আমি ভাত খাব।"
        )
        assertEquals("আমি ভাত খাব।", completedState.finalTranscript)

        val errorState = VoiceTypingState.Error("Microphone busy")
        assertEquals("Microphone busy", errorState.message)
    }

    @Test
    fun `partial transcript accumulation logic produces expected output`() {
        val buffer = StringBuilder()

        fun appendWord(text: String) {
            if (text.isNotBlank()) {
                if (buffer.isNotEmpty()) buffer.append(" ")
                buffer.append(text.trim())
            }
        }

        appendWord("আজকে")
        appendWord("আবহাওয়া")
        appendWord("খুব ভালো")

        assertEquals("আজকে আবহাওয়া খুব ভালো", buffer.toString())
    }
}
