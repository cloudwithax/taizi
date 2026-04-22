package com.taizi.data.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
            val root = JsonParser.parseString(json).asJsonObject
            val tagName = root.get("tag_name")?.takeUnless { it.isJsonNull }?.asString ?: return null
            val name = root.get("name")?.takeUnless { it.isJsonNull }?.asString
            val body = root.get("body")?.takeUnless { it.isJsonNull }?.asString
            val htmlUrl = root.get("html_url")?.takeUnless { it.isJsonNull }?.asString ?: ""
            val assets = root.getAsJsonArray("assets")?.mapNotNull { parseAsset(it.asJsonObject) } ?: emptyList()
            ReleaseData(tagName, name, body, htmlUrl, assets)
        } catch (e: Exception) { null }
    }

    private fun parseAsset(obj: JsonObject): AssetData? {
        val name = obj.get("name")?.asString ?: return null
        val url = obj.get("browser_download_url")?.asString ?: return null
        val size = obj.get("size")?.asLong ?: 0L
        return AssetData(name, url, size)
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