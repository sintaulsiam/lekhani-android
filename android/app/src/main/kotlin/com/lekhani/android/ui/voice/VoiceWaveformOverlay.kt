package com.lekhani.android.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.voice.VoiceTypingState
import kotlinx.coroutines.flow.StateFlow

// ── Design Tokens ─────────────────────────────────────────────────────────────

private val OverlayBg        = Color(0xF00D1117)
private val CardBg           = Color(0xFF161B22)
private val BorderColor      = Color(0xFF30363D)
private val PrimaryAccent    = Color(0xFF00D4A0) // Lekhani Teal
private val SecondaryAccent  = Color(0xFF00AAFF) // Lekhani Blue
private val RecordingRed     = Color(0xFFFF453A)
private val TextPrimary      = Color(0xFFF0F6FC)
private val TextSecondary    = Color(0xFF8B949E)

/**
 * VoiceWaveformOverlay
 * ══════════════════════════════════════════════════════════════════════════════
 * Visual audio waveform and live transcription feedback overlay.
 *
 * Features (ROADMAP.md Phase 5):
 *   ✅ Real-time multi-bar animated waveform responding dynamically to RMS amplitude
 *   ✅ Live partial transcript preview
 *   ✅ Pulsing recording indicator
 *   ✅ Quick actions: Done (commit) and Cancel
 *   ✅ WCAG 2.1 TalkBack accessibility descriptions
 */
@Composable
fun VoiceWaveformOverlay(
    voiceStateFlow: StateFlow<VoiceTypingState>,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by voiceStateFlow.collectAsState()
    val isListening = state is VoiceTypingState.Listening

    AnimatedVisibility(
        visible = isListening,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(150)),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(100)),
        modifier = modifier,
    ) {
        val listeningState = state as? VoiceTypingState.Listening ?: return@AnimatedVisibility

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(OverlayBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics {
                    contentDescription = "ভয়েস টাইপিং চলছে। কথা বলুন।"
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .padding(16.dp),
            ) {
                // Top header: Recording indicator + Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PulsingRecordingDot()
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "শুনছি...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                }

                // Dynamic Audio Waveform
                AudioWaveformBars(rmsLevel = listeningState.rmsLevel)

                // Live partial transcript display
                Text(
                    text = if (listeningState.partialTranscript.isNotBlank())
                        listeningState.partialTranscript
                    else
                        "কথা বলুন, এখানে লেখা দেখা যাবে...",
                    fontSize = 16.sp,
                    color = if (listeningState.partialTranscript.isNotBlank()) TextPrimary else TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Action buttons: Done & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF21262D))
                            .clickable { onCancel() }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .semantics { contentDescription = "বাতিল করুন" },
                    ) {
                        Text(
                            text = "বাতিল",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(PrimaryAccent, SecondaryAccent)
                                )
                            )
                            .clickable { onDone() }
                            .padding(horizontal = 28.dp, vertical = 8.dp)
                            .semantics { contentDescription = "সম্পন্ন করুন" },
                    ) {
                        Text(
                            text = "সম্পন্ন",
                            fontSize = 14.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated audio waveform bars whose heights dynamically modulate based on RMS level.
 */
@Composable
private fun AudioWaveformBars(rmsLevel: Float) {
    val barCount = 7
    val baseMultipliers = listOf(0.4f, 0.7f, 1.0f, 1.3f, 1.0f, 0.7f, 0.4f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .padding(vertical = 4.dp),
    ) {
        for (i in 0 until barCount) {
            val targetHeight = ((rmsLevel * 100f * baseMultipliers[i]).coerceIn(6f, 40f)).dp
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
                label = "barHeight_$i",
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PrimaryAccent),
            )
        }
    }
}

/**
 * Pulsing red circle indicator signifying active microphone recording.
 */
@Composable
private fun PulsingRecordingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(RecordingRed),
    )
}
