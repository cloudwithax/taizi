package com.taizi.data.scraper

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IGDBServiceTest {

    @Test
    fun `PLATFORM_IDS covers major systems`() {
        val ids = IGDBService.PLATFORM_IDS
        assertNotNull(ids["gb"])
        assertNotNull(ids["gba"])
        assertNotNull(ids["nes"])
        assertNotNull(ids["snes"])
        assertNotNull(ids["n64"])
        assertNotNull(ids["psx"])
        assertNotNull(ids["psp"])
        assertNotNull(ids["dc"])
        assertNotNull(ids["genesis"])
        assertNotNull(ids["mame"])
    }

    @Test
    fun `PLATFORM_IDS includes expanded mappings`() {
        val ids = IGDBService.PLATFORM_IDS
        // Previously missing systems that now have mappings
        assertNotNull(ids["gamecube"])
        assertNotNull(ids["wii"])
        assertNotNull(ids["3ds"])
        assertNotNull(ids["ps2"])
        assertNotNull(ids["saturn"])
        assertNotNull(ids["neogeo"])
        assertNotNull(ids["c64"])
        assertNotNull(ids["amiga"])
        assertNotNull(ids["zxspectrum"])
        assertNotNull(ids["dos"])
    }

    @Test
    fun `PLATFORM_IDS has more than the original eleven entries`() {
        assertTrue(
            "PLATFORM_IDS should cover significantly more than the original 11 systems",
            IGDBService.PLATFORM_IDS.size > 40
        )
    }
}
