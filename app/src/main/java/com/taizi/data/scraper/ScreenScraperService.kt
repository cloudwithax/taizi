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
import java.net.URLEncoder

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

class ScreenScraperService(private val context: Context) {

    companion object {
        private const val API_URL = "https://www.screenscraper.fr/api2"
        private const val DEV_ID = "Bkg2k"
        private const val DEV_PASSWORD = "H2j26mjFnBN6tFDg"
        private const val SOFT_NAME = "Skraper"

        // Internal system ID -> ScreenScraper systemeid
        val PLATFORM_IDS = mapOf(
            // Nintendo
            "gb" to 9,
            "gbc" to 10,
            "gba" to 12,
            "nes" to 3,
            "fds" to 106,
            "snes" to 4,
            "n64" to 14,
            "gamecube" to 13,
            "wii" to 16,
            "nds" to 15,
            "3ds" to 17,
            "virtualboy" to 11,
            "pokemini" to 211,
            // Sony
            "psx" to 57,
            "ps2" to 58,
            "psp" to 61,
            // Sega
            "genesis" to 1,
            "sms" to 2,
            "gamegear" to 21,
            "sg1000" to 109,
            "sega32x" to 19,
            "segacd" to 20,
            "saturn" to 22,
            "dc" to 23,
            "naomi" to 56,
            // NEC
            "pce" to 31,
            "supergrafx" to 105,
            "pcfx" to 72,
            // Atari
            "atari2600" to 26,
            "atari5200" to 40,
            "atari7800" to 41,
            "atari800" to 43,
            "atarist" to 42,
            "lynx" to 28,
            "jaguar" to 27,
            // SNK
            "neogeo" to 142,
            "neogeocd" to 70,
            "ngpc" to 82,
            "wonderswan" to 45,
            "wonderswancolor" to 46,
            // Arcade
            "mame" to 75,
            "fbneo" to 75,
            "cps1" to 6,
            "cps2" to 7,
            "cps3" to 8,
            "model2" to 54,
            "daphne" to 49,
            // Commodore / Amiga
            "c64" to 66,
            "c128" to 66,
            "vic20" to 73,
            "cplus4" to 99,
            "pet" to 240,
            "amiga" to 64,
            // Amstrad / Sinclair
            "amstradcpc" to 65,
            "zxspectrum" to 76,
            "zx81" to 77,
            // MSX
            "msx" to 113,
            "msx2" to 116,
            // Apple / BBC / Acorn
            "apple2" to 86,
            "apple2gs" to 217,
            "archimedes" to 84,
            "bbcmicro" to 37,
            "electron" to 85,
            // Japanese PCs
            "pc88" to 221,
            "pc98" to 208,
            "x1" to 220,
            "x68000" to 79,
            "fm7" to 97,
            "fmtowns" to 253,
            // DOS / PC
            "dos" to 135,
            // Misc
            "colecovision" to 48,
            "intellivision" to 115,
            "3do" to 29,
            "vectrex" to 102,
            "odyssey2" to 104,
            "channelf" to 80,
            "cdi" to 133,
            "mac" to 146,
            "ti99" to 205,
            "thomson" to 141,
            "trs80" to 144,
            "vc4000" to 281,
            "astrocade" to 44,
            "j2me" to 302,
            // Fantasy consoles
            "pico8" to 234,
            "tic80" to 222,
            "wasm4" to 262,
            "vircon32" to 272,
            "chip8" to 234,
            // Other
            "arduboy" to 263,
            "uzebox" to 216,
            "vmu" to 23,
            "scv" to 67,
            "advision" to 78,
            "crvision" to 241,
            "arcadia" to 94,
            "gametank" to 75,
            "gp32" to 101,
            "vsmile" to 120,
            "socrates" to 75,
            "gmaster" to 103,
            "multivision" to 75,
            "samcoupe" to 213,
            "oric" to 131,
            "palm" to 219,
            "supervision" to 207,
            "gamecom" to 121,
            "megaduck" to 90,
            "gamate" to 266,
            "gamepock" to 95,
            "supracan" to 100,
            "openbor" to 214,
            "scummvm" to 123
        )
    }

    private val gson = Gson()

    private suspend fun apiGet(endpoint: String, params: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val query = buildString {
                append("devid=$DEV_ID")
                append("&devpassword=$DEV_PASSWORD")
                append("&softname=$SOFT_NAME")
                append("&output=json")
                params.forEach { (k, v) ->
                    if (v.isNotBlank()) {
                        append("&${k}=${URLEncoder.encode(v, "UTF-8")}")
                    }
                }
            }
            val url = URL("$API_URL/$endpoint.php?$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000

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

    suspend fun scrapeGame(romFile: File, systemId: String): ScrapedGame? = withContext(Dispatchers.IO) {
        val platformId = PLATFORM_IDS[systemId] ?: return@withContext null

        val hash = RomHasher.hash(romFile, systemId)
        val params = mutableMapOf(
            "systemeid" to platformId.toString(),
            "romtype" to "rom",
            "romnom" to romFile.name
        )
        if (hash != null) {
            params["crc"] = hash.crc32
            params["romtaille"] = hash.size.toString()
        } else if (romFile.isFile) {
            params["romtaille"] = romFile.length().toString()
        }

        val body = apiGet("jeuInfos", params) ?: return@withContext null

        val json = gson.fromJson(body, JsonObject::class.java)
        val response = json.getAsJsonObject("response") ?: return@withContext null
        val jeu = response.getAsJsonObject("jeu") ?: return@withContext null

        val title = jeu.getAsJsonArray("noms")
            ?.firstOrNull { it.asJsonObject.get("region")?.asString == "ss" }
            ?.asJsonObject?.get("text")?.asString
            ?: jeu.getAsJsonArray("noms")?.firstOrNull()?.asJsonObject?.get("text")?.asString

        val description = jeu.getAsJsonArray("synopsis")
            ?.firstOrNull { it.asJsonObject.get("langue")?.asString == "en" }
            ?.asJsonObject?.get("text")?.asString

        val genre = jeu.getAsJsonArray("genres")
            ?.firstOrNull { it.asJsonObject.get("principale")?.asString == "1" }
            ?.asJsonObject?.getAsJsonArray("noms")
            ?.firstOrNull { it.asJsonObject.get("langue")?.asString == "en" }
            ?.asJsonObject?.get("text")?.asString
            ?: jeu.getAsJsonArray("genres")?.firstOrNull()
                ?.asJsonObject?.getAsJsonArray("noms")
                ?.firstOrNull { it.asJsonObject.get("langue")?.asString == "en" }
                ?.asJsonObject?.get("text")?.asString

        val developer = jeu.getAsJsonObject("developpeur")?.get("text")?.asString
        val publisher = jeu.getAsJsonObject("editeur")?.get("text")?.asString

        val players = jeu.getAsJsonObject("joueurs")?.get("text")?.asString?.let { j ->
            // Parse "1-2" or "1" -> max value
            j.split("-").lastOrNull()?.trim()?.toIntOrNull()
        }

        val rating = jeu.getAsJsonObject("note")?.get("text")?.asFloat?.let { it / 20f }

        val releaseDate = jeu.getAsJsonArray("dates")
            ?.firstOrNull { it.asJsonObject.get("region")?.asString in listOf("us", "eu", "wor", "ss") }
            ?.asJsonObject?.get("text")?.asString
            ?: jeu.getAsJsonArray("dates")?.firstOrNull()
                ?.asJsonObject?.get("text")?.asString

        val boxArtUrl = findBestBoxArt(jeu.getAsJsonArray("medias"))

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

    private fun findBestBoxArt(medias: JsonArray?): String? {
        if (medias == null) return null
        val regionPriority = listOf("wor", "us", "eu", "ss", "jp")
        val boxArtList = medias.map { it.asJsonObject }
            .filter { it.get("type")?.asString == "box-2D" }

        for (region in regionPriority) {
            val match = boxArtList.firstOrNull {
                it.get("region")?.asString == region
            }
            if (match != null) return match.get("url")?.asString
        }
        return boxArtList.firstOrNull()?.get("url")?.asString
    }

    suspend fun downloadBoxArt(imageUrl: String, systemId: String, gameName: String): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "boxart/$systemId")
            dir.mkdirs()

            val safeName = gameName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
            val file = File(dir, "$safeName.png")

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
