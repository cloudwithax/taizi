package com.taizi.data.update

import com.taizi.data.network.AssetData
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun `findApkDownloadUrl returns first apk asset url`() {
        val assets = listOf(
            AssetData("app-release.apk", "https://example.com/app.apk", 4_096_000L),
            AssetData("checksums.txt", "https://example.com/checksums.txt", 256L)
        )
        assertEquals("https://example.com/app.apk", UpdateManager.findApkDownloadUrl(assets))
    }

    @Test
    fun `findApkDownloadUrl is case insensitive for apk extension`() {
        val assets = listOf(
            AssetData("app.APK", "https://example.com/app.APK", 4_096_000L)
        )
        assertEquals("https://example.com/app.APK", UpdateManager.findApkDownloadUrl(assets))
    }

    @Test
    fun `findApkDownloadUrl returns empty string when no apk present`() {
        val assets = listOf(
            AssetData("source.zip", "https://example.com/src.zip", 1000L)
        )
        assertEquals("", UpdateManager.findApkDownloadUrl(assets))
    }

    @Test
    fun `findApkDownloadUrl returns empty string for empty assets`() {
        assertEquals("", UpdateManager.findApkDownloadUrl(emptyList()))
    }

    @Test
    fun `findApkDownloadUrl picks first apk when multiple exist`() {
        val assets = listOf(
            AssetData("app-arm64.apk", "https://example.com/arm64.apk", 3_000_000L),
            AssetData("app-armeabi.apk", "https://example.com/armeabi.apk", 2_000_000L)
        )
        assertEquals("https://example.com/arm64.apk", UpdateManager.findApkDownloadUrl(assets))
    }
}
