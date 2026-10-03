package com.lekhani.android.theme

import android.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

/**
 * ThemeChromaUtils
 * High-performance, zero-allocation chromatic color calculation algorithms
 * for 120 FPS dynamic animated RGB themes and Compose UI previews.
 */
object ThemeChromaUtils {

    /**
     * Duration in milliseconds for a full cycle of the given [ChromaMode].
     */
    fun getCycleDuration(mode: ChromaMode): Long = when (mode) {
        ChromaMode.RAINBOW_FLOW -> 3600L
        ChromaMode.AURORA_BOREALIS -> 4200L
        ChromaMode.SUNSET_HORIZON -> 4000L
        ChromaMode.COSMIC_NEBULA -> 3800L
        ChromaMode.MATRIX_PULSE -> 2800L
        ChromaMode.OCEAN_ABYSS -> 4000L
        ChromaMode.VAPORWAVE_SYNTH -> 3600L
        ChromaMode.SAKURA_GLOW -> 4400L
        ChromaMode.MAGMA_EMBER -> 3200L
        ChromaMode.CELESTIAL_AMETHYST -> 4200L
        ChromaMode.ENCHANTED_JADE -> 3800L
        ChromaMode.NONE -> 3600L
    }

    /**
     * Fills [hsv] (must have size >= 3) with Hue (0..360), Saturation (0..1), and Value (0..1)
     * based on [mode], normalized animation [phase] (0f..1f), and horizontal position [xRatio] (0f..1f).
     *
     * Guaranteed zero heap allocations when using a pre-allocated [hsv] buffer on the 120 FPS hot path.
     */
    fun computeHsv(mode: ChromaMode, phase: Float, xRatio: Float, hsv: FloatArray) {
        when (mode) {
            ChromaMode.RAINBOW_FLOW -> {
                val hue = (phase * 360f + xRatio * 180f) % 360f
                hsv[0] = hue
                hsv[1] = 0.90f
                hsv[2] = 1.0f
            }
            ChromaMode.AURORA_BOREALIS -> {
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                hsv[0] = 150f + wave * (280f - 150f)
                hsv[1] = 0.88f
                hsv[2] = 1.0f
            }
            ChromaMode.SUNSET_HORIZON -> {
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                val h = 315f + wave * 80f
                hsv[0] = if (h >= 360f) h - 360f else h
                hsv[1] = 0.92f
                hsv[2] = 1.0f
            }
            ChromaMode.COSMIC_NEBULA -> {
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                hsv[0] = 230f + wave * (340f - 230f)
                hsv[1] = 0.88f
                hsv[2] = 1.0f
            }
            ChromaMode.MATRIX_PULSE -> {
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                hsv[0] = 115f + wave * (175f - 115f)
                hsv[1] = 0.95f
                hsv[2] = 1.0f
            }
            ChromaMode.OCEAN_ABYSS -> {
                // Bioluminescent deep sapphire (225°) to glowing aquamarine/cyan (170°)
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                hsv[0] = 170f + wave * (228f - 170f)
                hsv[1] = 0.88f
                hsv[2] = 1.0f
            }
            ChromaMode.VAPORWAVE_SYNTH -> {
                // Neon electric cyan (180°) -> laser violet (270°) -> hot magenta (325°)
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                hsv[0] = 180f + wave * (325f - 180f)
                hsv[1] = 0.92f
                hsv[2] = 1.0f
            }
            ChromaMode.SAKURA_GLOW -> {
                // Delicate pastel cherry blossom (335°) through rose gold to warm peach coral (25°)
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                val h = 335f + wave * 50f
                hsv[0] = if (h >= 360f) h - 360f else h
                hsv[1] = 0.72f
                hsv[2] = 1.0f
            }
            ChromaMode.MAGMA_EMBER -> {
                // Volcanic molten ruby (350°) -> fiery blood orange (18°) -> radiant ember gold (45°)
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                val h = 350f + wave * 55f
                hsv[0] = if (h >= 360f) h - 360f else h
                hsv[1] = 0.95f
                hsv[2] = 1.0f
            }
            ChromaMode.CELESTIAL_AMETHYST -> {
                // Mystic orchid (260°) -> royal shimmering lilac -> celestial starlight rose (325°)
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                hsv[0] = 260f + wave * (325f - 260f)
                hsv[1] = 0.85f
                hsv[2] = 1.0f
            }
            ChromaMode.ENCHANTED_JADE -> {
                // Phosphorescent firefly lime (85°) -> lush emerald jade (155°)
                val wave = (sin((phase * 2.0 * PI) + (xRatio * PI)).toFloat() + 1f) * 0.5f
                hsv[0] = 85f + wave * (155f - 85f)
                hsv[1] = 0.90f
                hsv[2] = 1.0f
            }
            ChromaMode.NONE -> {
                hsv[0] = 0f
                hsv[1] = 0f
                hsv[2] = 1.0f
            }
        }
    }

    /**
     * Zero-allocation color solver for 120 FPS Canvas rendering.
     * Computes the 32-bit packed ARGB color directly using caller-provided [outHsv] array.
     */
    fun getColor(mode: ChromaMode, nowMs: Long, xRatio: Float, outHsv: FloatArray): Int {
        val duration = getCycleDuration(mode)
        val phase = ((nowMs % duration).toFloat() / duration.toFloat())
        computeHsv(mode, phase, xRatio, outHsv)
        return hsvToColor(outHsv)
    }

    /**
     * Resolves 32-bit packed ARGB color at a given animation [phase] (0f..1f).
     * Creates an ephemeral FloatArray; ideal for Compose UI / preview transitions.
     */
    fun getColorAtPhase(mode: ChromaMode, phase: Float, xRatio: Float = 0.5f): Int {
        val hsv = FloatArray(3)
        computeHsv(mode, phase, xRatio, hsv)
        return hsvToColor(hsv)
    }

    /**
     * Converts HSV components to packed ARGB int.
     * Uses [Color.HSVToColor] on Android, with a robust pure math fallback for JVM unit tests.
     */
    fun hsvToColor(hsv: FloatArray): Int {
        try {
            return Color.HSVToColor(hsv)
        } catch (_: Throwable) {
            val h = (hsv[0] % 360f + 360f) % 360f
            val s = hsv[1].coerceIn(0f, 1f)
            val v = hsv[2].coerceIn(0f, 1f)
            val c = v * s
            val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
            val m = v - c
            val hSector = (h / 60f).toInt()
            val rPrime: Float
            val gPrime: Float
            val bPrime: Float
            when (hSector) {
                0 -> { rPrime = c; gPrime = x; bPrime = 0f }
                1 -> { rPrime = x; gPrime = c; bPrime = 0f }
                2 -> { rPrime = 0f; gPrime = c; bPrime = x }
                3 -> { rPrime = 0f; gPrime = x; bPrime = c }
                4 -> { rPrime = x; gPrime = 0f; bPrime = c }
                else -> { rPrime = c; gPrime = 0f; bPrime = x }
            }
            val r = ((rPrime + m) * 255f).toInt().coerceIn(0, 255)
            val g = ((gPrime + m) * 255f).toInt().coerceIn(0, 255)
            val b = ((bPrime + m) * 255f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
