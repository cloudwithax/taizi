package com.taizi.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubServiceTest {

    private val service = GitHubService()

    @Test
    fun `extractVersionFromTag parses plain tag`() {
        val v = service.extractVersionFromTag("v1.2.3")
        assertEquals(1, v.major)
        assertEquals(2, v.minor)
        assertEquals(3, v.patch)
    }

    @Test
    fun `extractVersionFromTag parses pre-release tag`() {
        val v = service.extractVersionFromTag("1.0.0-beta.2")
        assertEquals(1, v.major)
        assertEquals(0, v.minor)
        assertEquals(0, v.patch)
        assertEquals("beta.2", v.preRelease)
    }

    @Test
    fun `parseReleaseJson extracts all fields including assets`() {
        val json = """
            {
                "tag_name": "v1.3.0",
                "name": "Version 1.3.0",
                "body": "Release notes",
                "html_url": "https://github.com/user/repo/releases/tag/v1.3.0",
                "assets": [
                    {
                        "name": "app-release.apk",
                        "browser_download_url": "https://github.com/user/repo/releases/download/v1.3.0/app-release.apk",
                        "size": 4096000
                    }
                ]
            }
        """.trimIndent()

        // Use reflection to access private method for testing
        val method = GitHubService::class.java.getDeclaredMethod("parseReleaseJson", String::class.java)
        method.isAccessible = true
        val result = method.invoke(service, json) as ReleaseData

        assertEquals("v1.3.0", result.tagName)
        assertEquals("Version 1.3.0", result.name)
        assertEquals("Release notes", result.body)
        assertEquals("https://github.com/user/repo/releases/tag/v1.3.0", result.htmlUrl)
        assertEquals(1, result.assets.size)
        assertEquals("app-release.apk", result.assets[0].name)
        assertEquals("https://github.com/user/repo/releases/download/v1.3.0/app-release.apk", result.assets[0].downloadUrl)
        assertEquals(4096000L, result.assets[0].size)
    }

    @Test
    fun `parseReleaseJson handles empty assets array`() {
        val json = """
            {
                "tag_name": "v1.0.0",
                "name": null,
                "body": null,
                "html_url": "https://example.com",
                "assets": []
            }
        """.trimIndent()

        val method = GitHubService::class.java.getDeclaredMethod("parseReleaseJson", String::class.java)
        method.isAccessible = true
        val result = method.invoke(service, json) as ReleaseData

        assertEquals(0, result.assets.size)
    }

    @Test
    fun `parseReleaseJson handles escaped quotes in body`() {
        val json = """
            {
                "tag_name": "v1.0.0",
                "name": "Release",
                "body": "Fixes bug in \"launch game\" feature",
                "html_url": "https://example.com",
                "assets": []
            }
        """.trimIndent()

        val method = GitHubService::class.java.getDeclaredMethod("parseReleaseJson", String::class.java)
        method.isAccessible = true
        val result = method.invoke(service, json) as ReleaseData

        assertEquals("Fixes bug in \"launch game\" feature", result.body)
    }

    @Test
    fun `parseReleaseJson returns null when tag_name missing`() {
        val json = """{"name": "broken"}"""
        val method = GitHubService::class.java.getDeclaredMethod("parseReleaseJson", String::class.java)
        method.isAccessible = true
        val result = method.invoke(service, json)
        assertNull(result)
    }
}
