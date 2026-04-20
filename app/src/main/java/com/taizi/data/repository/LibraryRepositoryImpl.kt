package com.taizi.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.pm.PackageManager
import android.os.Build
import android.os.FileObserver
import com.taizi.data.local.BoxArtDao
import com.taizi.data.local.BoxArtEntry
import com.taizi.data.local.LocalDataSource
import com.taizi.data.scraper.ScraperCredentials
import com.taizi.data.scraper.IGDBService
import com.taizi.domain.model.*
import com.taizi.domain.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Repository implementation for Library management
 * Implements Rocknix-style "one folder = one system" scanning
 */
class LibraryRepositoryImpl(
    private val context: Context,
    private val localDataSource: LocalDataSource,
    private val boxArtDao: BoxArtDao
) : LibraryRepository {

    private val _library = MutableStateFlow(Library.EMPTY)

    private var internalFileObserver: FileObserver? = null
    private var observationJob: Job? = null
    private val systemDefinitions: Map<String, SystemDefinition> by lazy {
        builtInSystemDefinitions()
    }

    // Concurrency limiter to avoid overloading disk
    private val scanSemaphore = Semaphore(4) // Max 4 concurrent system scans

    private val archiveExtensions = setOf(".zip", ".7z")
    private val scraperService = IGDBService(context)

    init {
        // Nothing auto-started; ViewModel will trigger cache loading
    }

    override fun getLibrary(): StateFlow<Library> = _library

    /**
     * Load cached library from DataStore if available
     */
    override suspend fun loadCachedLibraryIfAvailable() {
        val cached = localDataSource.getLibraryCache()
        if (cached != null && cached.romRoot.isNotEmpty()) {
            val updatedSystems = cached.systems.map { system ->
                val def = systemDefinitions[system.id]
                if (def != null) {
                    val config = getDefaultEmulatorConfig(def)
                    system.copy(
                        emulatorType = config.type,
                        emulatorPackage = config.packageName,
                        core = config.core,
                        biosStatus = cached.biosStatus[system.id]
                            ?: if (def.bios.isEmpty()) BiosStatus.PRESENT else BiosStatus.MISSING
                    )
                } else system
            }
            _library.value = cached.copy(systems = updatedSystems)
        }
    }

    override suspend fun scanLibrary(
        romRoot: String,
        force: Boolean,
        onProgress: ((gameName: String, systemName: String, count: Int, total: Int) -> Unit)?
    ): Result<Library> {
        return withContext(Dispatchers.IO) {
            try {
                val rootFile = File(romRoot)
                if (!rootFile.exists() || !rootFile.isDirectory) {
                    return@withContext Result.failure(Exception("ROM root does not exist or is not a directory"))
                }

                // Load custom mappings from settings
                val customMappings = localDataSource.getCustomMappings()

                // Get immediate subdirectories (one folder = one system)
                val systemFolders = rootFile.listFiles { file -> file.isDirectory }?.toList()
                    ?: emptyList()

                // Detect BIOS folder and exclude from game scanning
                val biosFolder: File? = systemFolders.find { it.name.equals("bios", true) }
                val gameFolders: List<File> = if (biosFolder != null) systemFolders - biosFolder else systemFolders

                // Scan each system folder in parallel (limited concurrency)
                val systems = coroutineScope {
                    gameFolders.map { folder: File ->
                        async(Dispatchers.IO) {
                            scanSystemFolder(folder, customMappings)
                        }
                    }.awaitAll().filterNotNull()
                }

                // Scan BIOS folder if exists
                val biosStatus = if (biosFolder != null) {
                    scanBiosFolder(biosFolder, systems)
                } else {
                    systems.associate { system ->
                        val def = systemDefinitions[system.id]
                        system.id to if (def == null || def.bios.isEmpty()) BiosStatus.PRESENT else BiosStatus.MISSING
                    }
                }

                val systemsWithBios = systems.map { system ->
                    system.copy(biosStatus = biosStatus[system.id] ?: BiosStatus.MISSING)
                }

                // Deduplicate systems by ID, merging paths from duplicate folders
                val deduplicatedSystems = systemsWithBios
                    .groupBy { it.id }
                    .map { (_, dupes) -> dupes.first().copy(
                        romCount = dupes.sumOf { it.romCount }
                    )}

                // Build games map with progress reporting
                var scannedCount = 0
                val gamesBySystem = mutableMapOf<String, List<Game>>()
                for (system in systemsWithBios) {
                    val games = scanGamesForSystem(system) { gameName ->
                        scannedCount++
                        onProgress?.invoke(gameName, system.name, scannedCount, 0)
                    }
                    val existing = gamesBySystem[system.id] ?: emptyList()
                    gamesBySystem[system.id] = existing + games
                }

                // Restore box art paths from persistent database
                val allArt = boxArtDao.getAll().associateBy { it.romPath }
                for ((systemId, games) in gamesBySystem) {
                    gamesBySystem[systemId] = games.map { game ->
                        val entry = allArt[game.path]
                        if (entry != null && File(entry.artPath).exists()) {
                            game.copy(
                                boxArtPath = entry.artPath,
                                metadata = GameMetadata(
                                    description = entry.description,
                                    genre = entry.genre,
                                    developer = entry.developer,
                                    publisher = entry.publisher,
                                    releaseDate = entry.releaseDate,
                                    players = entry.players,
                                    rating = entry.rating
                                )
                            )
                        } else game
                    }
                }

                // Update romCount to match actual scanned games
                val finalSystems = deduplicatedSystems.map { system ->
                    system.copy(romCount = gamesBySystem[system.id]?.size ?: 0)
                }

                val library = Library(
                    systems = finalSystems,
                    gamesBySystem = gamesBySystem,
                    biosStatus = biosStatus,
                    lastScanned = java.lang.System.currentTimeMillis(),
                    romRoot = romRoot,
                    unmappedSystems = finalSystems.filter { it.isCustom && it.mappedFrom != null }
                        .map { it.mappedFrom!! }
                )

                // Update state and cache
                _library.value = library
                localDataSource.saveLibraryCache(library)

                Result.success(library)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun scanSystemFolder(
        folder: File,
        customMappings: Map<String, String>
    ): System? {
        val folderName = folder.name.lowercase().replace("_", " ").trim()

        // Try to find matching system definition
        val systemDef = systemDefinitions.values.firstOrNull { def ->
            def.folderNames.any { it.equals(folderName, true) }
        }

        val (systemId, systemName) = if (systemDef != null) {
            systemDef.id to systemDef.name
        } else {
            // Check custom mappings
            val mappedId = customMappings[folderName]
            if (mappedId != null) {
                val def = systemDefinitions[mappedId]
                if (def != null) mappedId to def.name else mappedId to folderName
            } else {
                // Unknown system - create placeholder
                return System(
                    id = "custom_${folderName}",
                    name = "Custom: ${folder.name}",
                    path = folder.absolutePath,
                    romCount = 0,
                    emulatorType = "",
                    emulatorPackage = null,
                    core = null,
                    lastScanned = 0L,
                    isCustom = true,
                    mappedFrom = folder.name
                )
            }
        }

        val def = systemDefinitions[systemId] ?: return null

        // Count ROM files (quick check, no full scan yet)
        val romExts = def.extensions.map { it.lowercase() }.toSet() + archiveExtensions
        val fileCount = countFilesWithExtension(folder, romExts, maxDepth = 2)

        // Provide default emulator config
        val defaultEmulator = getDefaultEmulatorConfig(def)

        return System(
            id = systemId,
            name = systemName,
            path = folder.absolutePath,
            romCount = fileCount,
            emulatorType = defaultEmulator.type,
            emulatorPackage = defaultEmulator.packageName,
            core = defaultEmulator.core,
            lastScanned = java.lang.System.currentTimeMillis()
        )
    }

    private fun countFilesWithExtension(
        folder: File,
        extensions: Set<String>,
        maxDepth: Int = 2
    ): Int {
        var count = 0
        fun scan(dir: File, depth: Int) {
            if (depth > maxDepth) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    if (file.name.equals("bios", true) ||
                        file.name.equals("images", true) ||
                        file.name.equals("imgs", true)) {
                        return@forEach
                    }
                    scan(file, depth + 1)
                } else {
                    val ext = ".${file.extension.lowercase()}"
                    if (ext in extensions) {
                        count++
                    }
                }
            }
        }
        scan(folder, 1)
        return count
    }

    private fun scanGamesForSystem(
        system: System,
        onGameFound: ((String) -> Unit)? = null
    ): List<Game> {
        val folder = File(system.path)
        if (!folder.exists()) return emptyList()

        val def = systemDefinitions[system.id] ?: return emptyList()
        val validExtensions = def.extensions.map { it.lowercase() }.toSet() + archiveExtensions

        val romFiles = mutableListOf<File>()
        fun collectROMs(dir: File, depth: Int) {
            if (depth > 2) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    if (file.name.equals("bios", true) ||
                        file.name.equals("images", true) ||
                        file.name.equals("imgs", true)) {
                        return@forEach
                    }
                    collectROMs(file, depth + 1)
                } else {
                    val ext = ".${file.extension.lowercase()}"
                    if (ext in validExtensions) {
                        romFiles.add(file)
                    }
                }
            }
        }
        collectROMs(folder, 1)

        return romFiles.map { file ->
            val game = parseGameFile(file, system.id)
            onGameFound?.invoke(game.name)
            game
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseGameFile(file: File, systemId: String): Game {
        val filename = file.nameWithoutExtension
        val cleanName = cleanGameName(filename)

        // Check for multi-disc patterns
        val discs = mutableListOf(GameDisc(file.absolutePath, file.name))

        // Look for .m3u playlist
        val m3uFile = File(file.parentFile, "${file.nameWithoutExtension}.m3u")
        if (m3uFile.exists()) {
            val m3uDiscs = parseM3U(m3uFile)
            if (m3uDiscs.isNotEmpty()) {
                discs.clear()
                discs.addAll(m3uDiscs)
            }
        }

        return Game(
            path = file.absolutePath,
            name = cleanName,
            systemId = systemId,
            size = file.length(),
            modified = file.lastModified(),
            playCount = 0,
            lastPlayed = null,
            favorite = false,
            discs = discs.distinctBy { it.path }
        )
    }

    private fun parseM3U(m3uFile: File): List<GameDisc> {
        return m3uFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val discFile = File(m3uFile.parentFile, line)
                if (discFile.exists()) {
                    GameDisc(discFile.absolutePath, discFile.name)
                } else {
                    null
                }
            }
    }

    private fun cleanGameName(filename: String): String {
        var name = filename
            .replace(Regex("\\[[A-Za-z]+\\]"), "")
            .replace(Regex("\\(v[0-9.]+\\),?\\s*"), "")
            .replace(Regex("\\(Rev\\s*[0-9]+\\)"), "")
            .replace(Regex("\\(Disc\\s*[0-9]+\\)"), "")
            .replace(Regex("\\(Disk\\s*[0-9]+\\)"), "")
            .replace(Regex("\\([A-Za-z]{2}\\)"), "")
            .replace(Regex("\\[[0-9]+\\)"), "")
            .replace(Regex("\\[!]\\)"), "")
            .replace(Regex("\\[a]\\)"), "")
            .replace(Regex("\\[t]\\)"), "")
            .replace(Regex("\\[T]\\)"), "")
            .replace(Regex("^[\\s._-]+|[\\s._-]+$"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return name
    }

    private fun scanBiosFolder(biosFolder: File, systems: List<System>): Map<String, BiosStatus> {
        val status = mutableMapOf<String, BiosStatus>()

        systems.forEach { system ->
            val def = systemDefinitions[system.id] ?: return@forEach
            if (def.bios.isEmpty()) {
                status[system.id] = BiosStatus.PRESENT
                return@forEach
            }

            val biosFound = def.bios.any { biosName ->
                File(biosFolder, biosName).exists() ||
                        File(biosFolder, "${system.id}/${biosName}").exists()
            }

            status[system.id] = if (biosFound) BiosStatus.PRESENT else BiosStatus.MISSING
        }

        return status
    }

    override suspend fun refreshSystem(systemId: String): Result<Library> {
        val currentLibrary = _library.value
        val system = currentLibrary.systems.find { it.id == systemId }
            ?: return Result.failure(Exception("System not found"))

        return try {
            val newSystem = scanSystemFolder(File(system.path), localDataSource.getCustomMappings())
            if (newSystem != null) {
                val newSystems = currentLibrary.systems.map { if (it.id == systemId) newSystem else it }
                val newGames = scanGamesForSystem(newSystem)

                val newLibrary = currentLibrary.copy(
                    systems = newSystems,
                    gamesBySystem = currentLibrary.gamesBySystem + (systemId to newGames),
                    lastScanned = java.lang.System.currentTimeMillis()
                )
                _library.value = newLibrary
                localDataSource.saveLibraryCache(newLibrary)
                Result.success(newLibrary)
            } else {
                Result.failure(Exception("Failed to scan system"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun launchGame(game: Game): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            val currentLibrary = _library.value
            val system = currentLibrary.systems.find { it.id == game.systemId }
                ?: return@withContext Result.failure(Exception("System not found"))

            val packageName = system.emulatorPackage
                ?: return@withContext Result.failure(Exception("No emulator configured for this system"))

            if (!isPackageInstalled(packageName)) {
                return@withContext Result.failure(Exception("Emulator not installed: $packageName"))
            }

            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder().build()
            )

            val intent: Intent
            if (system.emulatorType == "retroarch") {
                val coreName = system.core ?: return@withContext Result.failure(
                    Exception("No core configured for ${system.name}")
                )
                val coreDir = "/data/user/0/$packageName/cores"
                val corePath = "$coreDir/${coreName}_libretro_android.so"
                val configPath = "/storage/emulated/0/Android/data/$packageName/files/retroarch.cfg"

                intent = Intent().apply {
                    component = android.content.ComponentName(
                        packageName,
                        "com.retroarch.browser.retroactivity.RetroActivityFuture"
                    )
                    putExtra("ROM", game.path)
                    putExtra("LIBRETRO", corePath)
                    putExtra("CONFIGFILE", configPath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            } else {
                val romUri = android.net.Uri.parse("file://" + game.path)
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@withContext Result.failure(Exception("Cannot resolve launcher for $packageName"))

                intent = Intent(launchIntent).apply {
                    action = Intent.ACTION_VIEW
                    data = romUri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            context.startActivity(intent)
            updateGamePlayStats(game.path, game.playCount + 1, java.lang.System.currentTimeMillis())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGamePlayStats(
        gamePath: String,
        playCount: Int,
        lastPlayed: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = _library.value
            val updated = current.gamesBySystem.mapValues { (_, games) ->
                games.map { if (it.path == gamePath) it.copy(playCount = playCount, lastPlayed = lastPlayed) else it }
            }
            val newLibrary = current.copy(gamesBySystem = updated)
            _library.value = newLibrary
            localDataSource.saveLibraryCache(newLibrary)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGameFavorite(gamePath: String, favorite: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val current = _library.value
                val updated = current.gamesBySystem.mapValues { (_, games) ->
                    games.map { if (it.path == gamePath) it.copy(favorite = favorite) else it }
                }
                val newLibrary = current.copy(gamesBySystem = updated)
                _library.value = newLibrary
                localDataSource.saveLibraryCache(newLibrary)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getEmulatorConfig(systemId: String): EmulatorConfig? {
        val system = _library.value.systems.find { it.id == systemId } ?: return null
        return EmulatorConfig(
            type = system.emulatorType,
            packageName = system.emulatorPackage,
            core = system.core,
            isInstalled = isEmulatorInstalled(system.emulatorPackage)
        )
    }

    override suspend fun setEmulatorConfig(systemId: String, config: EmulatorConfig) {
        val current = _library.value
        val updated = current.systems.map {
            if (it.id == systemId) it.copy(
                emulatorType = config.type,
                emulatorPackage = config.packageName,
                core = config.core
            ) else it
        }
        val newLibrary = current.copy(systems = updated)
        _library.value = newLibrary
        localDataSource.saveLibraryCache(newLibrary)
    }

    private fun isEmulatorInstalled(packageName: String?): Boolean {
        if (packageName == null) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getCustomMappings(): Map<String, String> =
        localDataSource.getCustomMappings()

    override suspend fun saveCustomMappings(mappings: Map<String, String>) =
        localDataSource.saveCustomMappings(mappings)

    override suspend fun getBiosStatus(systemId: String): BiosStatus {
        return _library.value.biosStatus[systemId] ?: BiosStatus.MISSING
    }

    override suspend fun findSystemForFolder(folderName: String): System? {
        return _library.value.systems.firstOrNull { system ->
            system.path.endsWith(folderName, true) ||
                    system.mappedFrom?.equals(folderName, true) == true ||
                    system.name.contains(folderName, true)
        }
    }

    override suspend fun clearCache() {
        localDataSource.clearCache()
        _library.value = Library.EMPTY
    }

    override suspend fun setScraperCredentials(username: String, password: String) {
        localDataSource.setScraperAccount("$username:$password")
    }

    private suspend fun getCredentials(): ScraperCredentials? {
        val prefs = localDataSource.getScraperAccount()
        if (prefs.isNullOrBlank()) return null
        val parts = prefs.split(":", limit = 2)
        return if (parts.size == 2) ScraperCredentials(parts[0], parts[1]) else null
    }

    override suspend fun scrapeSystem(
        systemId: String,
        onProgress: ((gameName: String, current: Int, total: Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val library = _library.value
            val games = library.gamesBySystem[systemId] ?: return@withContext Result.success(0)
            var scraped = 0

            games.forEachIndexed { index, game ->
                if (game.boxArtPath != null) {
                    onProgress?.invoke(game.name, index + 1, games.size)
                    return@forEachIndexed
                }

                onProgress?.invoke(game.name, index + 1, games.size)
                val romFileName = File(game.path).name
                val info = scraperService.scrapeGame(romFileName, systemId)
                    ?: return@forEachIndexed

                val artUrl = info.boxArtUrl ?: return@forEachIndexed
                val localPath = scraperService.downloadBoxArt(artUrl, systemId, game.name)
                    ?: return@forEachIndexed

                val updatedGame = game.copy(
                    boxArtPath = localPath,
                    metadata = GameMetadata(
                        description = info.description,
                        genre = info.genre,
                        developer = info.developer,
                        publisher = info.publisher,
                        releaseDate = info.releaseDate,
                        players = info.players,
                        rating = info.rating
                    )
                )
                updateGameInLibrary(systemId, updatedGame)
                persistBoxArt(updatedGame)
                scraped++

                delay(300)
            }

            localDataSource.saveLibraryCache(_library.value)
            Result.success(scraped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun scrapeAll(
        onProgress: ((gameName: String, systemName: String, current: Int, total: Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val library = _library.value
            val allGames = library.gamesBySystem.entries.flatMap { (sysId, games) ->
                games.map { sysId to it }
            }
            var scraped = 0
            Log.d("Scraper", "Starting scrapeAll: ${allGames.size} games")

            allGames.forEachIndexed { index, (systemId, game) ->
                val systemName = library.systems.find { it.id == systemId }?.name ?: systemId
                onProgress?.invoke(game.name, systemName, index + 1, allGames.size)

                if (game.boxArtPath != null) {
                    Log.d("Scraper", "Skip (has art): ${game.name}")
                    return@forEachIndexed
                }

                val romFileName = File(game.path).name
                val info = scraperService.scrapeGame(romFileName, systemId)
                if (info == null) {
                    Log.w("Scraper", "No IGDB result for: ${game.name} ($systemId)")
                    return@forEachIndexed
                }

                val artUrl = info.boxArtUrl
                if (artUrl == null) {
                    Log.w("Scraper", "No box art URL for: ${game.name}")
                    return@forEachIndexed
                }
                val localPath = scraperService.downloadBoxArt(artUrl, systemId, game.name)
                if (localPath == null) {
                    Log.e("Scraper", "Download failed for: ${game.name}")
                    return@forEachIndexed
                }

                val updatedGame = game.copy(
                    boxArtPath = localPath,
                    metadata = GameMetadata(
                        description = info.description,
                        genre = info.genre,
                        developer = info.developer,
                        publisher = info.publisher,
                        releaseDate = info.releaseDate,
                        players = info.players,
                        rating = info.rating
                    )
                )
                updateGameInLibrary(systemId, updatedGame)
                persistBoxArt(updatedGame)
                scraped++

                delay(300)
            }

            localDataSource.saveLibraryCache(_library.value)
            Result.success(scraped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun persistBoxArt(game: Game) {
        val art = game.boxArtPath ?: return
        boxArtDao.upsert(
            BoxArtEntry(
                romPath = game.path,
                systemId = game.systemId,
                gameName = game.name,
                artPath = art,
                description = game.metadata?.description,
                genre = game.metadata?.genre,
                developer = game.metadata?.developer,
                publisher = game.metadata?.publisher,
                releaseDate = game.metadata?.releaseDate,
                players = game.metadata?.players,
                rating = game.metadata?.rating
            )
        )
    }

    private fun updateGameInLibrary(systemId: String, updatedGame: Game) {
        val current = _library.value
        val updatedGames = current.gamesBySystem.toMutableMap()
        updatedGames[systemId] = (updatedGames[systemId] ?: emptyList()).map { g ->
            if (g.path == updatedGame.path) updatedGame else g
        }
        _library.value = current.copy(gamesBySystem = updatedGames)
    }

    override fun startFileObserver(romRoot: String, onChange: (LibraryChange) -> Unit) {
        stopFileObserver()
        val rootFile = File(romRoot)
        if (!rootFile.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            internalFileObserver = object : FileObserver(rootFile, ALL_EVENTS) {
                override fun onEvent(event: Int, path: String?) {
                    path ?: return
                    if (shouldIgnorePath(path)) return

                    when (event) {
                        CREATE, MOVED_TO -> onChange(LibraryChange.ADDED)
                        DELETE, MOVED_FROM -> onChange(LibraryChange.REMOVED)
                        MODIFY -> onChange(LibraryChange.MODIFIED)
                    }
                }

                private fun shouldIgnorePath(path: String): Boolean {
                    val lower = path.lowercase()
                    return lower.contains("gamelist.xml") ||
                            lower.contains("systeminfo.txt") ||
                            lower.endsWith(".tmp") ||
                            lower.endsWith(".cache")
                }
            }.also {
                it.startWatching()
            }
        }

        // Also watch existing system folders (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            _library.value.systems.forEach { system ->
                try {
                    val sysObserver = object : FileObserver(File(system.path), ALL_EVENTS) {
                        override fun onEvent(event: Int, path: String?) {
                            onChange(LibraryChange.MODIFIED)
                        }
                    }
                    sysObserver.startWatching()
                } catch (e: Exception) {
                    // Folder may not exist
                }
            }
        }
    }

    override fun stopFileObserver() {
        internalFileObserver?.stopWatching()
        internalFileObserver = null
        observationJob?.cancel()
    }

    private fun builtInSystemDefinitions(): Map<String, SystemDefinition> {
        return mapOf(
            "gb" to SystemDefinition(
                id = "gb",
                name = "Game Boy",
                folderNames = listOf("gb", "gameboy", "sgb"),
                extensions = listOf(".gb"),
                emulator = "retroarch",
                core = "gambatte",
                bios = listOf("gb_bios.bin"),
                theme = "gb"
            ),
            "gbc" to SystemDefinition(
                id = "gbc",
                name = "Game Boy Color",
                folderNames = listOf("gbc", "gameboycolor"),
                extensions = listOf(".gbc"),
                emulator = "retroarch",
                core = "gambatte",
                bios = listOf("gbc_bios.bin"),
                theme = "gbc"
            ),
            "gba" to SystemDefinition(
                id = "gba",
                name = "Game Boy Advance",
                folderNames = listOf("gba", "gameboyadvance"),
                extensions = listOf(".gba"),
                emulator = "retroarch",
                core = "gpsp",
                bios = listOf("gba_bios.bin"),
                theme = "gba"
            ),
            "nes" to SystemDefinition(
                id = "nes",
                name = "Nintendo Entertainment System",
                folderNames = listOf("nes", "nintendoentertainmentsystem", "famicom"),
                extensions = listOf(".nes", ".fds"),
                emulator = "retroarch",
                core = "fceumm",
                bios = emptyList(),
                theme = "nes"
            ),
            "snes" to SystemDefinition(
                id = "snes",
                name = "Super Nintendo",
                folderNames = listOf("snes", "supernes", "supernintendo", "sfc"),
                extensions = listOf(".sfc", ".smc", ".fig"),
                emulator = "retroarch",
                core = "snes9x",
                bios = emptyList(),
                theme = "snes"
            ),
            "n64" to SystemDefinition(
                id = "n64",
                name = "Nintendo 64",
                folderNames = listOf("n64", "nintendo64"),
                extensions = listOf(".z64", ".n64", ".v64"),
                emulator = "retroarch",
                core = "mupen64plus_next_gles3",
                bios = emptyList(),
                theme = "n64"
            ),
            "psx" to SystemDefinition(
                id = "psx",
                name = "PlayStation",
                folderNames = listOf("psx", "playstation", "ps1", "psone"),
                extensions = listOf(".bin", ".img", ".iso", ".cue", ".chd"),
                emulator = "duckstation",
                core = "pcsx_rearmed",
                bios = listOf("scph1001.bin", "scph5501.bin", "scph5502.bin", "scph5552.bin"),
                theme = "psx"
            ),
            "psp" to SystemDefinition(
                id = "psp",
                name = "PlayStation Portable",
                folderNames = listOf("psp", "playstationportable"),
                extensions = listOf(".iso", ".cso"),
                emulator = "ppsspp",
                core = null,
                bios = emptyList(),
                theme = "psp"
            ),
            "nds" to SystemDefinition(
                id = "nds",
                name = "Nintendo DS",
                folderNames = listOf("nds", "nintendo ds"),
                extensions = listOf(".nds"),
                emulator = "drastic",
                core = null,
                bios = emptyList(),
                theme = "nds"
            ),
            "dc" to SystemDefinition(
                id = "dc",
                name = "Dreamcast",
                folderNames = listOf("dc", "dreamcast"),
                extensions = listOf(".gdi", ".cdi", ".chd"),
                emulator = "flycast",
                core = null,
                bios = listOf("dc_boot.bin", "dc_flash.bin"),
                theme = "dc"
            ),
            "mame" to SystemDefinition(
                id = "mame",
                name = "MAME",
                folderNames = listOf("mame", "arcade"),
                extensions = listOf(".zip"),
                emulator = "retroarch",
                core = "mame2003_plus",
                bios = emptyList(),
                theme = "mame"
            ),
            "pce" to SystemDefinition(
                id = "pce",
                name = "PC Engine",
                folderNames = listOf("pce", "pcengine", "turbografx", "tg16"),
                extensions = listOf(".pce", ".cue", ".bin"),
                emulator = "retroarch",
                core = "mednafen_pce_fast",
                bios = listOf("syscard3.pce"),
                theme = "pce"
            ),
            "atari2600" to SystemDefinition(
                id = "atari2600",
                name = "Atari 2600",
                folderNames = listOf("atari2600", "atari 2600"),
                extensions = listOf(".a26", ".bin"),
                emulator = "retroarch",
                core = "stella",
                bios = emptyList(),
                theme = "atari2600"
            ),
            "genesis" to SystemDefinition(
                id = "genesis",
                name = "Sega Genesis",
                folderNames = listOf("genesis", "megadrive", "md", "mega drive", "sega genesis"),
                extensions = listOf(".md", ".gen", ".smd", ".bin"),
                emulator = "retroarch",
                core = "genesis_plus_gx",
                bios = emptyList(),
                theme = "genesis"
            ),
            "sms" to SystemDefinition(
                id = "sms",
                name = "Sega Master System",
                folderNames = listOf("sms", "mastersystem", "master system"),
                extensions = listOf(".sms", ".bin"),
                emulator = "retroarch",
                core = "genesis_plus_gx",
                bios = emptyList(),
                theme = "sms"
            ),
            "gamegear" to SystemDefinition(
                id = "gamegear",
                name = "Sega Game Gear",
                folderNames = listOf("gg", "gamegear", "game gear"),
                extensions = listOf(".gg", ".bin"),
                emulator = "retroarch",
                core = "genesis_plus_gx",
                bios = emptyList(),
                theme = "gamegear"
            ),
            "saturn" to SystemDefinition(
                id = "saturn",
                name = "Sega Saturn",
                folderNames = listOf("saturn", "segasaturn"),
                extensions = listOf(".cue", ".bin", ".iso", ".chd", ".mds"),
                emulator = "retroarch",
                core = "kronos",
                bios = listOf("saturn_bios.bin"),
                theme = "saturn"
            ),
            "segacd" to SystemDefinition(
                id = "segacd",
                name = "Sega CD",
                folderNames = listOf("segacd", "sega cd", "megacd", "mega cd"),
                extensions = listOf(".cue", ".bin", ".iso", ".chd"),
                emulator = "retroarch",
                core = "genesis_plus_gx",
                bios = listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"),
                theme = "segacd"
            ),
            "neogeo" to SystemDefinition(
                id = "neogeo",
                name = "Neo Geo",
                folderNames = listOf("neogeo", "neo geo", "aes", "mvs"),
                extensions = listOf(".zip", ".neo"),
                emulator = "retroarch",
                core = "fbneo",
                bios = listOf("neogeo.zip"),
                theme = "neogeo"
            ),
            "ngpc" to SystemDefinition(
                id = "ngpc",
                name = "Neo Geo Pocket Color",
                folderNames = listOf("ngpc", "ngp", "neo geo pocket", "neogeopocket"),
                extensions = listOf(".ngc", ".ngp"),
                emulator = "retroarch",
                core = "beetle_ngp",
                bios = emptyList(),
                theme = "ngpc"
            ),
            "atari7800" to SystemDefinition(
                id = "atari7800",
                name = "Atari 7800",
                folderNames = listOf("atari7800", "atari 7800"),
                extensions = listOf(".a78", ".bin"),
                emulator = "retroarch",
                core = "prosystem",
                bios = listOf("7800 BIOS (U).rom"),
                theme = "atari7800"
            ),
            "lynx" to SystemDefinition(
                id = "lynx",
                name = "Atari Lynx",
                folderNames = listOf("lynx", "atarilynx"),
                extensions = listOf(".lnx", ".o"),
                emulator = "retroarch",
                core = "handy",
                bios = listOf("lynxboot.img"),
                theme = "lynx"
            ),
            "jaguar" to SystemDefinition(
                id = "jaguar",
                name = "Atari Jaguar",
                folderNames = listOf("jaguar", "atarijaguar"),
                extensions = listOf(".j64", ".jag", ".bin"),
                emulator = "retroarch",
                core = "virtual_jaguar",
                bios = emptyList(),
                theme = "jaguar"
            ),
            "virtualboy" to SystemDefinition(
                id = "virtualboy",
                name = "Virtual Boy",
                folderNames = listOf("virtualboy", "virtual boy", "vb"),
                extensions = listOf(".vb", ".vboy"),
                emulator = "retroarch",
                core = "beetle_vb",
                bios = emptyList(),
                theme = "virtualboy"
            ),
            "wonderswan" to SystemDefinition(
                id = "wonderswan",
                name = "WonderSwan",
                folderNames = listOf("wonderswan", "ws", "wsc", "wonderswancolor"),
                extensions = listOf(".ws", ".wsc", ".pc2"),
                emulator = "retroarch",
                core = "beetle_wswan",
                bios = emptyList(),
                theme = "wonderswan"
            ),
            "colecovision" to SystemDefinition(
                id = "colecovision",
                name = "ColecoVision",
                folderNames = listOf("colecovision", "coleco"),
                extensions = listOf(".col", ".bin", ".rom"),
                emulator = "retroarch",
                core = "bluemsx",
                bios = emptyList(),
                theme = "colecovision"
            ),
            "intellivision" to SystemDefinition(
                id = "intellivision",
                name = "Intellivision",
                folderNames = listOf("intellivision", "intv"),
                extensions = listOf(".int", ".bin", ".rom"),
                emulator = "retroarch",
                core = "freeintv",
                bios = listOf("exec.bin", "grom.bin"),
                theme = "intellivision"
            ),
            "3do" to SystemDefinition(
                id = "3do",
                name = "3DO",
                folderNames = listOf("3do", "3do interactive"),
                extensions = listOf(".iso", ".bin", ".cue", ".chd"),
                emulator = "retroarch",
                core = "opera",
                bios = listOf("panafz10.bin"),
                theme = "3do"
            ),
            "vectrex" to SystemDefinition(
                id = "vectrex",
                name = "Vectrex",
                folderNames = listOf("vectrex"),
                extensions = listOf(".vec", ".bin"),
                emulator = "retroarch",
                core = "vecx",
                bios = emptyList(),
                theme = "vectrex"
            )
        )
    }

    private val retroArchPackages = listOf(
        "com.retroarch.aarch64",
        "com.retroarch",
        "com.retroarch.ra32"
    )

    private val standaloneEmulators: Map<String, List<Pair<String, String>>> = mapOf(
        "psx" to listOf(
            "com.github.stenzek.duckstation" to "standalone",
            "org.duckstation.android" to "standalone"
        ),
        "psp" to listOf(
            "org.ppsspp.ppsspp" to "standalone",
            "org.ppsspp.ppssppgold" to "standalone"
        ),
        "nds" to listOf(
            "com.dsemu.drastic" to "standalone",
            "me.magnum.melonds" to "standalone"
        ),
        "dc" to listOf(
            "com.flycast.emulator" to "standalone",
            "io.recompiled.redream" to "standalone"
        ),
        "n64" to listOf(
            "org.mupen64plusae.v3.fzurita" to "standalone",
            "org.mupen64plusae.v3.alpha" to "standalone"
        ),
        "saturn" to listOf(
            "org.uoyabause.urern" to "standalone",
            "org.devmiyax.yabasanshiro" to "standalone"
        ),
        "3ds" to listOf(
            "org.azahar_emu.azahar" to "standalone",
            "org.citra.citra_emu" to "standalone"
        )
    )

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun findInstalledRetroArch(): String? =
        retroArchPackages.firstOrNull { isPackageInstalled(it) }

    private fun getDefaultEmulatorConfig(def: SystemDefinition): EmulatorConfig {
        val standalones = standaloneEmulators[def.id]
        if (standalones != null) {
            val found = standalones.firstOrNull { isPackageInstalled(it.first) }
            if (found != null) {
                return EmulatorConfig(
                    type = found.second,
                    packageName = found.first,
                    core = null
                )
            }
        }

        val raPackage = findInstalledRetroArch()
        if (raPackage != null) {
            return EmulatorConfig(
                type = "retroarch",
                packageName = raPackage,
                core = def.core
            )
        }

        return when (def.emulator) {
            "retroarch" -> EmulatorConfig(
                type = "retroarch",
                packageName = retroArchPackages.first(),
                core = def.core
            )
            else -> EmulatorConfig(
                type = "standalone",
                packageName = standalones?.firstOrNull()?.first ?: retroArchPackages.first(),
                core = null
            )
        }
    }

    private data class SystemDefinition(
        val id: String,
        val name: String,
        val folderNames: List<String>,
        val extensions: List<String>,
        val emulator: String,
        val core: String?,
        val bios: List<String>,
        val theme: String
    )

    companion object {
        const val ALL_EVENTS = FileObserver.ALL_EVENTS
    }
}
