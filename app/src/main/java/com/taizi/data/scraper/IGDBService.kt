package com.taizi.data.scraper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ScraperCredentials(
    val clientId: String,
    val clientSecret: String
)

data class ScrapedGame(
    val title: String?,
    val boxArtUrl: String?,
    val description: String?,
    val genre: String?,
    val developer: String?,
    val publisher: String?,
    val releaseDate: String?,
    val players: Int?,
    val rating: Float?
)

class IGDBService(private val context: Context) {

    companion object {
        private const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        private const val IGDB_URL = "https://api.igdb.com/v4"
        private const val CLIENT_ID = "xwf51r40hl6nzkggu53qv6p4zbsbdo"
        private const val CLIENT_SECRET = "3rg36w8g692ly7ddik1kqbf1oinem4"

        val PLATFORM_IDS = mapOf(
            "gb" to 33,
            "gbc" to 22,
            "gba" to 24,
            "nes" to 18,
            "snes" to 19,
            "n64" to 4,
            "psx" to 7,
            "psp" to 38,
            "nds" to 20,
            "dc" to 23,
            "mame" to 52,
            "pce" to 86,
            "atari2600" to 59
        )
    }

    private val gson = Gson()
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        if (cachedToken != null && java.lang.System.currentTimeMillis() < tokenExpiry) {
            return@withContext cachedToken
        }

        try {
            val url = URL("$TOKEN_URL?client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&grant_type=client_credentials")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Length", "0")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = gson.fromJson(body, JsonObject::class.java)
            val token = json.get("access_token")?.asString ?: return@withContext null
            val expiresIn = json.get("expires_in")?.asLong ?: 3600

            cachedToken = token
            tokenExpiry = java.lang.System.currentTimeMillis() + (expiresIn * 1000) - 60_000
            token
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun igdbPost(endpoint: String, query: String): String? = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext null
        try {
            val url = URL("$IGDB_URL/$endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Client-ID", CLIENT_ID)
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "text/plain")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000

            conn.outputStream.use { it.write(query.toByteArray()) }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            body
        } catch (_: Exception) {
            null
        }
    }

    fun cleanRomName(fileName: String): String {
        return fileName
            .substringBeforeLast('.')
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("[_-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun scrapeGame(romFileName: String, systemId: String): ScrapedGame? = withContext(Dispatchers.IO) {
        val platformId = PLATFORM_IDS[systemId] ?: return@withContext null
        val gameName = cleanRomName(romFileName)
        if (gameName.isBlank()) return@withContext null

        val escapedName = gameName.replace("\"", "\\\"")
        val query = """
            search "$escapedName";
            fields name, cover.image_id, summary, genres.name,
                   involved_companies.company.name, involved_companies.developer, involved_companies.publisher,
                   first_release_date, game_modes.name, total_rating;
            where platforms = ($platformId);
            limit 10;
        """.trimIndent()

        val body = igdbPost("games", query) ?: return@withContext null
        val games = gson.fromJson(body, JsonArray::class.java)
        if (games == null || games.size() == 0) return@withContext null

        val normalizedSearch = gameName.lowercase()
        val game = games.map { it.asJsonObject }
            .sortedBy { obj ->
                val name = (obj.get("name")?.asString ?: "").lowercase()
                when {
                    name == normalizedSearch -> 0
                    name.startsWith(normalizedSearch) -> 1
                    name.contains(normalizedSearch) -> 2
                    else -> 3 + levenshtein(normalizedSearch, name)
                }
            }
            .first()

        val title = game.get("name")?.asString
        val description = game.get("summary")?.asString
        val rating = game.get("total_rating")?.asFloat?.let { it / 100f }
        val releaseDate = game.get("first_release_date")?.asLong?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(it * 1000))
        }

        val genre = game.getAsJsonArray("genres")
            ?.firstOrNull()?.asJsonObject
            ?.get("name")?.asString

        var developer: String? = null
        var publisher: String? = null
        game.getAsJsonArray("involved_companies")?.forEach { ic ->
            val obj = ic.asJsonObject
            val companyName = obj.getAsJsonObject("company")?.get("name")?.asString
            if (obj.get("developer")?.asBoolean == true && developer == null) developer = companyName
            if (obj.get("publisher")?.asBoolean == true && publisher == null) publisher = companyName
        }

        val players = game.getAsJsonArray("game_modes")?.size()

        val imageId = game.getAsJsonObject("cover")?.get("image_id")?.asString
        val boxArtUrl = if (imageId != null) {
            "https://images.igdb.com/igdb/image/upload/t_cover_big/$imageId.jpg"
        } else null

        ScrapedGame(
            title = title,
            boxArtUrl = boxArtUrl,
            description = description,
            genre = genre,
            developer = developer,
            publisher = publisher,
            releaseDate = releaseDate,
            players = players,
            rating = rating
        )
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        return dp[a.length][b.length]
    }

    suspend fun downloadBoxArt(imageUrl: String, systemId: String, gameName: String): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "boxart/$systemId")
            dir.mkdirs()

            val safeName = gameName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
            val file = File(dir, "$safeName.jpg")

            if (file.exists()) return@withContext file.absolutePath

            val url = URL(imageUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()

            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
