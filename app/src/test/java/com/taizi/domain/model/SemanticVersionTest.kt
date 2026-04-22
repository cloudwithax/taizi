package com.taizi.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun `fromString parses standard version`() {
        val v = SemanticVersion.fromString("1.2.3")
        assertEquals(1, v.major)
        assertEquals(2, v.minor)
        assertEquals(3, v.patch)
        assertEquals(null, v.preRelease)
    }

    @Test
    fun `fromString strips v prefix`() {
        val v = SemanticVersion.fromString("v2.0.0")
        assertEquals(2, v.major)
        assertEquals(0, v.minor)
        assertEquals(0, v.patch)
    }

    @Test
    fun `fromString captures pre-release identifier`() {
        val v = SemanticVersion.fromString("1.0.0-beta")
        assertEquals(1, v.major)
        assertEquals(0, v.minor)
        assertEquals(0, v.patch)
        assertEquals("beta", v.preRelease)
    }

    @Test
    fun `fromString captures pre-release with dot segments`() {
        val v = SemanticVersion.fromString("1.0.0-rc.1")
        assertEquals("rc.1", v.preRelease)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromString rejects invalid format`() {
        SemanticVersion.fromString("1.0")
    }

    @Test
    fun `compareTo orders by major minor patch`() {
        assertTrue(SemanticVersion(1, 0, 0).compareTo(SemanticVersion(0, 9, 9)) > 0)
        assertTrue(SemanticVersion(1, 1, 0).compareTo(SemanticVersion(1, 0, 9)) > 0)
        assertTrue(SemanticVersion(1, 0, 1).compareTo(SemanticVersion(1, 0, 0)) > 0)
        assertTrue(SemanticVersion(1, 0, 0).compareTo(SemanticVersion(1, 0, 0)) == 0)
    }

    @Test
    fun `compareTo treats release as higher precedence than pre-release`() {
        val release = SemanticVersion(1, 0, 0)
        val pre = SemanticVersion(1, 0, 0, "beta")
        assertTrue(release.compareTo(pre) > 0)
        assertTrue(pre.compareTo(release) < 0)
    }

    @Test
    fun `compareTo orders pre-release identifiers numerically`() {
        val rc1 = SemanticVersion(1, 0, 0, "rc.1")
        val rc2 = SemanticVersion(1, 0, 0, "rc.2")
        assertTrue(rc1.compareTo(rc2) < 0)
        assertTrue(rc2.compareTo(rc1) > 0)
    }

    @Test
    fun `compareTo orders numeric pre-release lower than alphanumeric`() {
        val numeric = SemanticVersion(1, 0, 0, "1")
        val alpha = SemanticVersion(1, 0, 0, "alpha")
        assertTrue(numeric.compareTo(alpha) < 0)
    }

    @Test
    fun `toString includes pre-release when present`() {
        assertEquals("1.2.3-beta", SemanticVersion(1, 2, 3, "beta").toString())
    }

    @Test
    fun `toString omits pre-release when absent`() {
        assertEquals("1.2.3", SemanticVersion(1, 2, 3).toString())
    }
}
