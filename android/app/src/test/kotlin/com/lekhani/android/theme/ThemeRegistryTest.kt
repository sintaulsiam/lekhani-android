package com.lekhani.android.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRegistryTest {

    @Test
    fun testPresetThemesIntegrity() {
        val presets = ThemeRegistry.PRESET_THEMES
        assertTrue(presets.isNotEmpty())
        assertEquals(38, presets.size)

        val flowTeal = presets.find { it.id == ThemeRegistry.ID_FLOW_TEAL }
        assertNotNull(flowTeal)
        assertEquals("Flow Teal", flowTeal!!.nameEnglish)
        assertTrue(flowTeal.isDark)
        assertEquals(ThemeCategory.CLASSIC, flowTeal.category)

        val oledBlack = presets.find { it.id == ThemeRegistry.ID_OLED_BLACK }
        assertNotNull(oledBlack)
        assertEquals("OLED Pure Black", oledBlack!!.nameEnglish)
        assertEquals(0xFF000000.toInt(), oledBlack.backgroundColor)

        val avroBlue = presets.find { it.id == ThemeRegistry.ID_AVRO_BLUE }
        assertNotNull(avroBlue)
        assertEquals("Classic Blue", avroBlue!!.nameEnglish)

        val cyberIndigo = presets.find { it.id == ThemeRegistry.ID_CYBER_INDIGO }
        assertNotNull(cyberIndigo)

        val daylight = presets.find { it.id == ThemeRegistry.ID_DAYLIGHT_LIGHT }
        assertNotNull(daylight)
        assertTrue(!daylight!!.isDark)

        val sakura = presets.find { it.id == ThemeRegistry.ID_SAKURA_DUSK }
        assertNotNull(sakura)
        assertTrue(sakura!!.isDark)

        val forest = presets.find { it.id == ThemeRegistry.ID_FOREST_EMERALD }
        assertNotNull(forest)
        assertTrue(forest!!.isDark)

        val nordic = presets.find { it.id == ThemeRegistry.ID_NORDIC_FROST }
        assertNotNull(nordic)
        assertTrue(nordic!!.isDark)

        val sunset = presets.find { it.id == ThemeRegistry.ID_SUNSET_AMBER }
        assertNotNull(sunset)
        assertTrue(sunset!!.isDark)

        val mocha = presets.find { it.id == ThemeRegistry.ID_MOCHA_LATTE }
        assertNotNull(mocha)
        assertTrue(!mocha!!.isDark)

        val cyberpunk = presets.find { it.id == ThemeRegistry.ID_CYBERPUNK_NEON }
        assertNotNull(cyberpunk)
        assertEquals(ThemeCategory.NEON, cyberpunk!!.category)

        // New Aesthetic themes
        val duskRose = presets.find { it.id == ThemeRegistry.ID_DUSK_ROSE }
        assertNotNull(duskRose)
        assertEquals(ThemeCategory.AESTHETIC, duskRose!!.category)
        assertTrue(duskRose.isDark)

        val champagne = presets.find { it.id == ThemeRegistry.ID_CHAMPAGNE_LUXURY }
        assertNotNull(champagne)
        assertEquals(ThemeCategory.AESTHETIC, champagne!!.category)

        val iceCrystal = presets.find { it.id == ThemeRegistry.ID_ICE_CRYSTAL }
        assertNotNull(iceCrystal)
        assertEquals(ThemeCategory.AESTHETIC, iceCrystal!!.category)
        assertFalse(iceCrystal.isDark)

        // Dynamic RGB Chroma themes
        val chromaThemes = presets.filter { it.category == ThemeCategory.RGB_CHROMA }
        assertEquals(16, chromaThemes.size)
        for (ct in chromaThemes) {
            assertTrue("Chroma theme ${ct.id} must have isRgbChroma = true", ct.isRgbChroma)
            assertTrue("Chroma theme ${ct.id} must have non-NONE chromaMode", ct.chromaMode != ChromaMode.NONE)
        }

        val ocean = presets.find { it.id == ThemeRegistry.ID_OCEAN_ABYSS }
        assertNotNull(ocean)
        assertEquals(ChromaMode.OCEAN_ABYSS, ocean!!.chromaMode)

        val vaporwave = presets.find { it.id == ThemeRegistry.ID_VAPORWAVE_DREAM }
        assertNotNull(vaporwave)
        assertEquals(ChromaMode.VAPORWAVE_SYNTH, vaporwave!!.chromaMode)

        val sakuraGlow = presets.find { it.id == ThemeRegistry.ID_SAKURA_GLOW }
        assertNotNull(sakuraGlow)
        assertEquals(ChromaMode.SAKURA_GLOW, sakuraGlow!!.chromaMode)

        val magma = presets.find { it.id == ThemeRegistry.ID_MAGMA_EMBER }
        assertNotNull(magma)
        assertEquals(ChromaMode.MAGMA_EMBER, magma!!.chromaMode)

        // New Distinct Dynamic Design Themes
        val midnightPrism = presets.find { it.id == ThemeRegistry.ID_MIDNIGHT_PRISM }
        assertNotNull(midnightPrism)
        assertEquals(ChromaMode.PRISM_SPECTRUM, midnightPrism!!.chromaMode)
        assertEquals(ChromaStyle.CLEAN_MINIMAL, midnightPrism.chromaStyle)

        val electricCyber = presets.find { it.id == ThemeRegistry.ID_ELECTRIC_CYBER }
        assertNotNull(electricCyber)
        assertEquals(ChromaMode.ELECTRIC_CYBER, electricCyber!!.chromaMode)
        assertEquals(ChromaStyle.FULL_BORDER, electricCyber.chromaStyle)

        val frostNebula = presets.find { it.id == ThemeRegistry.ID_FROST_NEBULA }
        assertNotNull(frostNebula)
        assertEquals(ChromaMode.FROST_NEBULA, frostNebula!!.chromaMode)
        assertEquals(ChromaStyle.CLEAN_MINIMAL, frostNebula.chromaStyle)

        val solarEclipse = presets.find { it.id == ThemeRegistry.ID_SOLAR_ECLIPSE }
        assertNotNull(solarEclipse)
        assertEquals(ChromaMode.SOLAR_GOLD, solarEclipse!!.chromaMode)
        assertEquals(ChromaStyle.AMBIENT_BREATHE, solarEclipse.chromaStyle)

        val nordicLights = presets.find { it.id == ThemeRegistry.ID_NORDIC_LIGHTS }
        assertNotNull(nordicLights)
        assertEquals(ChromaMode.NORDIC_AURORA, nordicLights!!.chromaMode)
        assertEquals(ChromaStyle.AMBIENT_BREATHE, nordicLights.chromaStyle)

        // Toolbar Quick Theme Switcher (Strictly maximum 5 flagship themes across all categories)
        val quickThemes = ThemeRegistry.QUICK_TOOLBAR_THEME_IDS
        assertTrue("Quick toolbar themes must not exceed 5", quickThemes.size <= 5)
        assertEquals(5, quickThemes.size)
        for (qId in quickThemes) {
            assertTrue("Quick theme $qId must exist in PRESET_THEMES", presets.any { it.id == qId })
        }
    }

    @Test
    fun testThemeChromaUtilsCalculations() {
        val outHsv = FloatArray(3)
        for (mode in ChromaMode.values()) {
            val duration = ThemeChromaUtils.getCycleDuration(mode)
            assertTrue(duration > 0)

            val color = ThemeChromaUtils.getColor(mode, 1000L, 0.5f, outHsv)
            assertTrue("Color must be non-zero ARGB", color != 0)

            val phaseColor = ThemeChromaUtils.getColorAtPhase(mode, 0.5f, 0.5f)
            assertTrue("Phase color must be non-zero ARGB", phaseColor != 0)
        }
    }

    @Test
    fun testThemeColorContrast() {
        for (theme in ThemeRegistry.PRESET_THEMES) {
            assertTrue("Theme ${theme.id} label should be non-transparent", theme.labelColor != 0)
            assertTrue("Theme ${theme.id} background should be non-transparent", theme.backgroundColor != 0)
            assertTrue("Theme ${theme.id} normal key should be non-transparent", theme.keyNormalColor != 0)
            assertTrue("Theme ${theme.id} accent should be non-transparent", theme.accentColor != 0)
        }
    }

    @Test
    fun testCustomThemeSerializationRoundtrip() {
        val original = ThemeRegistry.THEME_FLOW_TEAL.copy(
            id = "custom_test_123",
            nameBengali = "পরীক্ষামূলক থিম",
            nameEnglish = "Experimental Teal",
            isCustom = true,
            isRgbChroma = true,
            chromaMode = ChromaMode.COSMIC_NEBULA,
            chromaStyle = ChromaStyle.CLEAN_MINIMAL,
            category = ThemeCategory.RGB_CHROMA
        )
        val json = original.toJson()
        val restored = KeyboardTheme.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.nameBengali, restored.nameBengali)
        assertEquals(original.nameEnglish, restored.nameEnglish)
        assertEquals(original.backgroundColor, restored.backgroundColor)
        assertEquals(original.keyNormalColor, restored.keyNormalColor)
        assertEquals(original.accentColor, restored.accentColor)
        assertEquals(original.isDark, restored.isDark)
        assertTrue(restored.isCustom)
        assertTrue(restored.isRgbChroma)
        assertEquals(ChromaMode.COSMIC_NEBULA, restored.chromaMode)
        assertEquals(ChromaStyle.CLEAN_MINIMAL, restored.chromaStyle)
        assertEquals(ThemeCategory.RGB_CHROMA, restored.category)
    }

    @Test
    fun testPresetThemesDefaultNotCustom() {
        for (theme in ThemeRegistry.PRESET_THEMES) {
            assertFalse("Preset ${theme.id} should not be marked as custom", theme.isCustom)
        }
    }
}
