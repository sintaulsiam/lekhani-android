package com.lekhani.android.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRegistryTest {

    @Test
    fun testPresetThemesIntegrity() {
        val presets = ThemeRegistry.PRESET_THEMES
        assertTrue(presets.isNotEmpty())
        assertEquals(11, presets.size)

        val flowTeal = presets.find { it.id == ThemeRegistry.ID_FLOW_TEAL }
        assertNotNull(flowTeal)
        assertEquals("Flow Teal", flowTeal!!.nameEnglish)
        assertTrue(flowTeal.isDark)

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
}
