package com.taizi.data.network

import com.taizi.domain.model.SemanticVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubService {
    private val okHttpClient = OkHttpClient.Builder().build()

    suspend fun getLatestRelease(owner: String, repo: String): ReleaseData? {
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    json?.let { parseReleaseJson(it) }
                } else null
            }
        } catch (e: Exception) { null }
    }

    fun extractVersionFromTag(tag: String) = SemanticVersion.fromString(tag)

    private fun parseReleaseJson(json: String): ReleaseData? {
        return try {
            val tagName = extractValue(json, "tag_name")
            val name = extractValue(json, "name")
            val body = extractValue(json, "body")
            val htmlUrl = extractValue(json, "html_url")
            if (tagName.isEmpty()) return null
            ReleaseData(tagName, name, body, htmlUrl, emptyList())
        } catch (e: Exception) { null }
    }

    private fun extractValue(json: String, key: String): String {
        val regex = "\"$key\":\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}

data class ReleaseData(
    val tagName: String,
    val name: String?,
    val body: String?,
    val htmlUrl: String,
    val assets: List<AssetData>
)

data class AssetData(
    val name: String,
    val downloadUrl: String,
    val size: Long
)