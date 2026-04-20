package com.taizi.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.FileObserver
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.taizi.data.local.BoxArtDao
import com.taizi.data.local.BoxArtEntry
import com.taizi.data.local.LocalDataSource
import com.taizi.data.scraper.IGDBService
import com.taizi.data.scraper.ScrapedGame
import com.taizi.data.scraper.ScraperCredentials
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

    private val gson = Gson()
    private val _library = MutableStateFlow(Library.EMPTY)

    private var internalFileObserver: FileObserver? = null
    private var observationJob: Job? = null
    private val systemDefinitions: Map<String, SystemDefinition> by lazy {
        builtInSystemDefinitions()
    }

    // Concurrency limiter to avoid overloading disk
    private val scanSemaphore = Semaphore(4) // Max 4 concurrent system scans
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
            _library.value = cached
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
                    emptyMap()
                }

                // First pass: collect ROM files per system so we know the total up front
                val filesBySystem: Map<System, List<File>> = systems.associateWith { collectRomFiles(it) }
                val total = filesBySystem.values.sumOf { it.size }

                // Second pass: parse and report live progress per ROM
                var scannedCount = 0
                val gamesBySystem = mutableMapOf<String, List<Game>>()
                for ((system, files) in filesBySystem) {
                    val games = files.map { file ->
                        scannedCount++
                        val game = parseGameFile(file, system.id)
                        onProgress?.invoke(game.name, system.name, scannedCount, total)
                        game
                    }.sortedBy { it.name.lowercase() }
                    gamesBySystem[system.id] = games
                }

                // Restore box art paths from database
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

                // Only surface systems that actually have ROMs on disk
                val populatedSystems = systems.filter { (gamesBySystem[it.id]?.size ?: 0) > 0 }
                val populatedGames = gamesBySystem.filterKeys { id ->
                    populatedSystems.any { it.id == id }
                }

                val library = Library(
                    systems = populatedSystems,
                    gamesBySystem = populatedGames,
                    biosStatus = biosStatus,
                    lastScanned = java.lang.System.currentTimeMillis(),
                    romRoot = romRoot,
                    unmappedSystems = populatedSystems.filter { it.isCustom && it.mappedFrom != null }
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
        val romExts = def.extensions.map { it.lowercase() }.toSet()
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

    private fun isSkippableDir(name: String): Boolean {
        val lower = name.lowercase()
        return lower in SKIP_DIRS || lower.startsWith(".")
    }

    private fun isRomFile(file: File, validExtensions: Set<String>): Boolean {
        val name = file.name
        if (name.startsWith(".") || name.startsWith("_")) return false
        if (name.lowercase() in SKIP_FILES) return false
        val ext = ".${file.extension.lowercase()}"
        return ext in validExtensions || ext in ARCHIVE_EXTENSIONS
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
                    if (isSkippableDir(file.name)) return@forEach
                    scan(file, depth + 1)
                } else if (isRomFile(file, extensions)) {
                    count++
                }
            }
        }
        scan(folder, 1)
        return count
    }

    private fun collectRomFiles(system: System): List<File> {
        val folder = File(system.path)
        if (!folder.exists()) return emptyList()

        val def = systemDefinitions[system.id] ?: return emptyList()
        val validExtensions = def.extensions.map { it.lowercase() }.toSet()

        val romFiles = mutableListOf<File>()
        fun collectROMs(dir: File, depth: Int) {
            if (depth > 2) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    if (isSkippableDir(file.name)) return@forEach
                    collectROMs(file, depth + 1)
                } else if (isRomFile(file, validExtensions)) {
                    romFiles.add(file)
                }
            }
        }
        collectROMs(folder, 1)
        return romFiles
    }

    private fun scanGamesForSystem(system: System): List<Game> =
        collectRomFiles(system)
            .map { parseGameFile(it, system.id) }
            .sortedBy { it.name.lowercase() }

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

            // Check if emulator is installed
            val pm = context.packageManager
            pm.getPackageInfo(packageName, 0) // Will throw if not installed

            val intent = Intent(Intent.ACTION_VIEW).apply {
                // RetroArch
                if (system.emulatorType == "retroarch") {
                    action = "org.libretro.RUN_GAME"
                    `package` = packageName
                    putExtra("core", system.core)
                    putExtra("rom", game.path)
                } else {
                    // Standalone emulator
                    `package` = packageName
                    data = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(game.path)
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            context.startActivity(intent)

            // Update play count
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
            // ---------- Nintendo ----------
            "gb" to SystemDefinition("gb", "Game Boy", listOf("gb", "gameboy", "sgb", "gbh", "gb2players"), listOf(".gb"), "retroarch", "gambatte", listOf("gb_bios.bin"), "gb"),
            "gbc" to SystemDefinition("gbc", "Game Boy Color", listOf("gbc", "gameboycolor", "gbch", "gbc2players"), listOf(".gbc"), "retroarch", "gambatte", listOf("gbc_bios.bin"), "gbc"),
            "gba" to SystemDefinition("gba", "Game Boy Advance", listOf("gba", "gameboyadvance", "gbah", "gbav"), listOf(".gba"), "retroarch", "gpsp", listOf("gba_bios.bin"), "gba"),
            "nes" to SystemDefinition("nes", "Nintendo Entertainment System", listOf("nes", "nintendoentertainmentsystem", "famicom", "fc", "nesh"), listOf(".nes", ".unf", ".unif"), "retroarch", "fceumm", emptyList(), "nes"),
            "fds" to SystemDefinition("fds", "Famicom Disk System", listOf("fds", "famicomdisk"), listOf(".fds"), "retroarch", "fceumm", listOf("disksys.rom"), "fds"),
            "snes" to SystemDefinition("snes", "Super Nintendo", listOf("snes", "supernes", "supernintendo", "sfc", "superfamicom", "snesh", "sneshd", "snesna", "snes-hacks", "snes-msu1", "snesmsu1", "satellaview", "sufami"), listOf(".sfc", ".smc", ".fig", ".swc"), "retroarch", "snes9x", emptyList(), "snes"),
            "n64" to SystemDefinition("n64", "Nintendo 64", listOf("n64", "nintendo64", "n64dd"), listOf(".z64", ".n64", ".v64", ".ndd"), "retroarch", "mupen64plus_next_gles3", emptyList(), "n64"),
            "gamecube" to SystemDefinition("gamecube", "GameCube", listOf("gc", "gamecube", "ngc"), listOf(".iso", ".gcm", ".gcz", ".rvz", ".wbfs", ".ciso"), "retroarch", "dolphin", emptyList(), "gamecube"),
            "wii" to SystemDefinition("wii", "Wii", listOf("wii"), listOf(".iso", ".wbfs", ".wad", ".rvz", ".wia"), "retroarch", "dolphin", emptyList(), "wii"),
            "nds" to SystemDefinition("nds", "Nintendo DS", listOf("nds", "nintendo ds", "ndsiware"), listOf(".nds", ".dsi"), "drastic", null, emptyList(), "nds"),
            "3ds" to SystemDefinition("3ds", "Nintendo 3DS", listOf("3ds", "n3ds"), listOf(".3ds", ".cia", ".cci", ".3dsx"), "retroarch", "citra", emptyList(), "3ds"),
            "virtualboy" to SystemDefinition("virtualboy", "Virtual Boy", listOf("virtualboy", "virtual boy", "vb"), listOf(".vb", ".vboy"), "retroarch", "beetle_vb", emptyList(), "virtualboy"),
            "pokemini" to SystemDefinition("pokemini", "Pokemon Mini", listOf("pokemini", "pokemonmini", "poke"), listOf(".min"), "retroarch", "pokemini", listOf("bios.min"), "pokemini"),
            "gameandwatch" to SystemDefinition("gameandwatch", "Game & Watch", listOf("gameandwatch", "gw"), listOf(".mgw", ".zip"), "retroarch", "gw", emptyList(), "gameandwatch"),

            // ---------- Sony ----------
            "psx" to SystemDefinition("psx", "PlayStation", listOf("psx", "playstation", "ps1", "psone", "ps"), listOf(".bin", ".img", ".iso", ".cue", ".chd", ".pbp", ".ecm", ".m3u"), "duckstation", "pcsx_rearmed", listOf("scph1001.bin", "scph5501.bin", "scph5502.bin", "scph5552.bin"), "psx"),
            "ps2" to SystemDefinition("ps2", "PlayStation 2", listOf("ps2", "playstation2"), listOf(".iso", ".cso", ".chd", ".bin", ".gz", ".mdf"), "retroarch", "pcsx2", emptyList(), "ps2"),
            "psp" to SystemDefinition("psp", "PlayStation Portable", listOf("psp", "playstationportable", "pspminis"), listOf(".iso", ".cso", ".pbp"), "ppsspp", null, emptyList(), "psp"),

            // ---------- Sega ----------
            "genesis" to SystemDefinition("genesis", "Sega Genesis", listOf("genesis", "megadrive", "md", "mega drive", "sega genesis", "megadriveh", "genesish", "genesiswide", "megadrivejp", "megadrive-japan", "megadrivemsu", "msu-md", "msumd", "genh"), listOf(".md", ".gen", ".smd", ".bin", ".sg"), "retroarch", "genesis_plus_gx", emptyList(), "genesis"),
            "sms" to SystemDefinition("sms", "Sega Master System", listOf("sms", "mastersystem", "master system"), listOf(".sms", ".bin"), "retroarch", "genesis_plus_gx", emptyList(), "sms"),
            "gamegear" to SystemDefinition("gamegear", "Sega Game Gear", listOf("gg", "gamegear", "game gear", "gamegearh"), listOf(".gg", ".bin"), "retroarch", "genesis_plus_gx", emptyList(), "gamegear"),
            "sg1000" to SystemDefinition("sg1000", "SG-1000", listOf("sg-1000", "sg1000", "sc-3000"), listOf(".sg", ".bin"), "retroarch", "genesis_plus_gx", emptyList(), "sg1000"),
            "sega32x" to SystemDefinition("sega32x", "Sega 32X", listOf("sega32x", "32x", "sega32xjp", "sega32xna"), listOf(".32x", ".smd", ".bin"), "retroarch", "picodrive", emptyList(), "sega32x"),
            "segacd" to SystemDefinition("segacd", "Sega CD", listOf("segacd", "sega cd", "megacd", "mega cd", "mdcd", "megacdjp"), listOf(".cue", ".bin", ".iso", ".chd"), "retroarch", "genesis_plus_gx", listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"), "segacd"),
            "saturn" to SystemDefinition("saturn", "Sega Saturn", listOf("saturn", "segasaturn", "saturnjp", "st-v", "stv"), listOf(".cue", ".bin", ".iso", ".chd", ".mds"), "retroarch", "kronos", listOf("saturn_bios.bin"), "saturn"),
            "dc" to SystemDefinition("dc", "Dreamcast", listOf("dc", "dreamcast"), listOf(".gdi", ".cdi", ".chd", ".m3u"), "flycast", null, listOf("dc_boot.bin", "dc_flash.bin"), "dc"),
            "naomi" to SystemDefinition("naomi", "Sega NAOMI", listOf("naomi", "naomi2", "naomigd", "atomiswave"), listOf(".zip", ".bin", ".chd", ".lst"), "flycast", "flycast", emptyList(), "naomi"),

            // ---------- NEC ----------
            "pce" to SystemDefinition("pce", "PC Engine", listOf("pce", "pcengine", "turbografx", "tg16", "turbografxcd", "tg16cd", "tg-cd", "pcenginecd", "pcecd"), listOf(".pce", ".cue", ".bin", ".iso", ".chd"), "retroarch", "mednafen_pce_fast", listOf("syscard3.pce"), "pce"),
            "supergrafx" to SystemDefinition("supergrafx", "SuperGrafx", listOf("supergrafx", "sgfx"), listOf(".pce", ".sgx", ".cue", ".bin"), "retroarch", "mednafen_supergrafx", emptyList(), "supergrafx"),
            "pcfx" to SystemDefinition("pcfx", "PC-FX", listOf("pcfx"), listOf(".cue", ".ccd", ".toc", ".chd"), "retroarch", "mednafen_pcfx", listOf("pcfx.rom"), "pcfx"),

            // ---------- Atari ----------
            "atari2600" to SystemDefinition("atari2600", "Atari 2600", listOf("atari2600", "atari 2600", "a2600"), listOf(".a26", ".bin"), "retroarch", "stella", emptyList(), "atari2600"),
            "atari5200" to SystemDefinition("atari5200", "Atari 5200", listOf("atari5200", "atari 5200", "a5200"), listOf(".a52", ".bin"), "retroarch", "atari800", listOf("5200.rom"), "atari5200"),
            "atari7800" to SystemDefinition("atari7800", "Atari 7800", listOf("atari7800", "atari 7800", "a7800"), listOf(".a78", ".bin"), "retroarch", "prosystem", listOf("7800 BIOS (U).rom"), "atari7800"),
            "atari800" to SystemDefinition("atari800", "Atari 8-bit", listOf("atari800", "a800", "atarixe", "atarixegs", "xegs"), listOf(".atr", ".atx", ".xex", ".cas", ".cart", ".bin"), "retroarch", "atari800", emptyList(), "atari800"),
            "atarist" to SystemDefinition("atarist", "Atari ST", listOf("atarist"), listOf(".st", ".stx", ".ipf", ".msa", ".dim"), "retroarch", "hatari", emptyList(), "atarist"),
            "lynx" to SystemDefinition("lynx", "Atari Lynx", listOf("lynx", "atarilynx"), listOf(".lnx", ".o"), "retroarch", "handy", listOf("lynxboot.img"), "lynx"),
            "jaguar" to SystemDefinition("jaguar", "Atari Jaguar", listOf("jaguar", "atarijaguar", "atarijaguarcd", "jaguarcd"), listOf(".j64", ".jag", ".bin", ".cue"), "retroarch", "virtual_jaguar", emptyList(), "jaguar"),

            // ---------- SNK ----------
            "neogeo" to SystemDefinition("neogeo", "Neo Geo", listOf("neogeo", "neo geo", "aes", "mvs"), listOf(".zip", ".neo"), "retroarch", "fbneo", listOf("neogeo.zip"), "neogeo"),
            "neogeocd" to SystemDefinition("neogeocd", "Neo Geo CD", listOf("neogeocd", "neocd", "neogeocdjp"), listOf(".cue", ".iso", ".chd"), "retroarch", "neocd", listOf("neocd.bin"), "neogeocd"),
            "ngpc" to SystemDefinition("ngpc", "Neo Geo Pocket Color", listOf("ngpc", "ngp", "neo geo pocket", "neogeopocket"), listOf(".ngc", ".ngp"), "retroarch", "beetle_ngp", emptyList(), "ngpc"),
            "wonderswan" to SystemDefinition("wonderswan", "WonderSwan", listOf("wonderswan", "ws", "wsc", "wswan", "wswanc", "wonderswancolor"), listOf(".ws", ".wsc", ".pc2"), "retroarch", "beetle_wswan", emptyList(), "wonderswan"),

            // ---------- Arcade ----------
            "mame" to SystemDefinition("mame", "MAME", listOf("mame", "arcade", "mame-advmame", "mame-mame4all", "hbmame", "varcade", "mam", "mess"), listOf(".zip", ".7z", ".chd"), "retroarch", "mame2003_plus", emptyList(), "mame"),
            "fbneo" to SystemDefinition("fbneo", "FinalBurn Neo", listOf("fbneo", "fba", "fbn"), listOf(".zip", ".7z"), "retroarch", "fbneo", emptyList(), "fbneo"),
            "cps1" to SystemDefinition("cps1", "Capcom CPS-1", listOf("cps1", "cps"), listOf(".zip"), "retroarch", "fbneo", emptyList(), "cps1"),
            "cps2" to SystemDefinition("cps2", "Capcom CPS-2", listOf("cps2"), listOf(".zip"), "retroarch", "fbneo", emptyList(), "cps2"),
            "cps3" to SystemDefinition("cps3", "Capcom CPS-3", listOf("cps3"), listOf(".zip"), "retroarch", "fbneo", emptyList(), "cps3"),
            "model2" to SystemDefinition("model2", "Sega Model 2", listOf("model2"), listOf(".zip"), "retroarch", "mame", emptyList(), "model2"),
            "daphne" to SystemDefinition("daphne", "Daphne", listOf("daphne", "singe"), listOf(".daphne", ".zip"), "retroarch", "daphne", emptyList(), "daphne"),

            // ---------- Commodore / Amiga ----------
            "c64" to SystemDefinition("c64", "Commodore 64", listOf("c64", "commodore64"), listOf(".d64", ".t64", ".prg", ".crt", ".tap", ".g64"), "retroarch", "vice_x64", emptyList(), "c64"),
            "c128" to SystemDefinition("c128", "Commodore 128", listOf("c128"), listOf(".d64", ".t64", ".prg", ".crt"), "retroarch", "vice_x128", emptyList(), "c128"),
            "vic20" to SystemDefinition("vic20", "VIC-20", listOf("vic20"), listOf(".d64", ".t64", ".prg", ".crt"), "retroarch", "vice_xvic", emptyList(), "vic20"),
            "cplus4" to SystemDefinition("cplus4", "Commodore Plus/4", listOf("cplus4", "c16", "c20", "plus4"), listOf(".d64", ".t64", ".prg"), "retroarch", "vice_xplus4", emptyList(), "cplus4"),
            "pet" to SystemDefinition("pet", "Commodore PET", listOf("pet"), listOf(".d64", ".t64", ".prg"), "retroarch", "vice_xpet", emptyList(), "pet"),
            "amiga" to SystemDefinition("amiga", "Amiga", listOf("amiga", "amiga500", "amiga600", "amiga1200", "amigacd32", "cd32", "amigacdtv", "cdtv"), listOf(".adf", ".adz", ".dms", ".ipf", ".hdf", ".lha", ".uae", ".slave"), "retroarch", "puae", emptyList(), "amiga"),

            // ---------- Amstrad / Sinclair ----------
            "amstradcpc" to SystemDefinition("amstradcpc", "Amstrad CPC", listOf("amstradcpc", "cpc"), listOf(".dsk", ".cdt", ".sna", ".tap", ".cpr"), "retroarch", "cap32", emptyList(), "amstradcpc"),
            "amstradgx4000" to SystemDefinition("amstradgx4000", "Amstrad GX4000", listOf("amstradgx4000", "gx4000"), listOf(".cpr"), "retroarch", "cap32", emptyList(), "amstradgx4000"),
            "zxspectrum" to SystemDefinition("zxspectrum", "ZX Spectrum", listOf("zxspectrum", "spectrum", "zx"), listOf(".tap", ".tzx", ".z80", ".sna", ".dsk", ".scl", ".trd"), "retroarch", "fuse", emptyList(), "zxspectrum"),
            "zx81" to SystemDefinition("zx81", "ZX81", listOf("zx81"), listOf(".p", ".81", ".tzx"), "retroarch", "81", emptyList(), "zx81"),

            // ---------- MSX ----------
            "msx" to SystemDefinition("msx", "MSX", listOf("msx", "msx1"), listOf(".rom", ".mx1", ".mx2", ".dsk", ".cas"), "retroarch", "bluemsx", emptyList(), "msx"),
            "msx2" to SystemDefinition("msx2", "MSX2", listOf("msx2", "msx2+", "msxturbor"), listOf(".rom", ".mx2", ".dsk", ".cas"), "retroarch", "bluemsx", emptyList(), "msx2"),

            // ---------- Apple / BBC / Acorn ----------
            "apple2" to SystemDefinition("apple2", "Apple II", listOf("apple2"), listOf(".dsk", ".woz", ".po", ".nib", ".do", ".hdv", ".2mg"), "retroarch", "mame2003_plus", emptyList(), "apple2"),
            "apple2gs" to SystemDefinition("apple2gs", "Apple IIGS", listOf("apple2gs"), listOf(".2mg", ".po", ".hdv"), "retroarch", "mame", emptyList(), "apple2gs"),
            "archimedes" to SystemDefinition("archimedes", "Acorn Archimedes", listOf("archimedes", "acorn"), listOf(".adf", ".hdf", ".apd"), "retroarch", "mame", emptyList(), "archimedes"),
            "bbcmicro" to SystemDefinition("bbcmicro", "BBC Micro", listOf("bbc", "bbcmicro"), listOf(".ssd", ".dsd", ".uef", ".img"), "retroarch", "mame", emptyList(), "bbcmicro"),
            "electron" to SystemDefinition("electron", "Acorn Electron", listOf("electron"), listOf(".uef", ".ssd", ".dsd"), "retroarch", "mame", emptyList(), "electron"),

            // ---------- Japanese PCs ----------
            "pc88" to SystemDefinition("pc88", "NEC PC-8801", listOf("pc88", "pc-88"), listOf(".d88", ".88d", ".cmt", ".t88"), "retroarch", "quasi88", emptyList(), "pc88"),
            "pc98" to SystemDefinition("pc98", "NEC PC-9801", listOf("pc98", "pc-98"), listOf(".d98", ".hdi", ".fdi", ".98d"), "retroarch", "np2kai", emptyList(), "pc98"),
            "x1" to SystemDefinition("x1", "Sharp X1", listOf("x1"), listOf(".d88", ".dx1", ".2d", ".tap"), "retroarch", "x1", emptyList(), "x1"),
            "x68000" to SystemDefinition("x68000", "Sharp X68000", listOf("x68000"), listOf(".dim", ".img", ".d88", ".88d", ".hdm", ".xdf"), "retroarch", "px68k", emptyList(), "x68000"),
            "fm7" to SystemDefinition("fm7", "Fujitsu FM-7", listOf("fm7"), listOf(".d77", ".d88", ".t77"), "retroarch", "mame", emptyList(), "fm7"),
            "fmtowns" to SystemDefinition("fmtowns", "FM Towns", listOf("fmtowns", "fmtownsux"), listOf(".iso", ".bin", ".cue"), "retroarch", "mame", emptyList(), "fmtowns"),

            // ---------- DOS / PC ----------
            "dos" to SystemDefinition("dos", "DOS", listOf("dos", "pc", "windows"), listOf(".conf", ".exe", ".bat", ".com", ".zip", ".7z"), "retroarch", "dosbox_pure", emptyList(), "dos"),
            "scummvm" to SystemDefinition("scummvm", "ScummVM", listOf("scummvm"), listOf(".svm", ".scummvm", ".zip"), "retroarch", "scummvm", emptyList(), "scummvm"),

            // ---------- Misc consoles / handhelds ----------
            "colecovision" to SystemDefinition("colecovision", "ColecoVision", listOf("colecovision", "coleco"), listOf(".col", ".bin", ".rom"), "retroarch", "bluemsx", emptyList(), "colecovision"),
            "intellivision" to SystemDefinition("intellivision", "Intellivision", listOf("intellivision", "intv"), listOf(".int", ".bin", ".rom"), "retroarch", "freeintv", listOf("exec.bin", "grom.bin"), "intellivision"),
            "3do" to SystemDefinition("3do", "3DO", listOf("3do", "3do interactive"), listOf(".iso", ".bin", ".cue", ".chd"), "retroarch", "opera", listOf("panafz10.bin"), "3do"),
            "vectrex" to SystemDefinition("vectrex", "Vectrex", listOf("vectrex"), listOf(".vec", ".bin"), "retroarch", "vecx", emptyList(), "vectrex"),
            "odyssey2" to SystemDefinition("odyssey2", "Magnavox Odyssey²", listOf("odyssey2", "videopac", "videopacplus", "o2em"), listOf(".bin", ".rom"), "retroarch", "o2em", listOf("o2rom.bin"), "odyssey2"),
            "channelf" to SystemDefinition("channelf", "Fairchild Channel F", listOf("channelf"), listOf(".bin", ".rom"), "retroarch", "freechaf", emptyList(), "channelf"),
            "supervision" to SystemDefinition("supervision", "Watara Supervision", listOf("supervision", "watara"), listOf(".sv", ".bin"), "retroarch", "potator", emptyList(), "supervision"),
            "gamecom" to SystemDefinition("gamecom", "Tiger Game.com", listOf("gamecom"), listOf(".tgc", ".bin"), "retroarch", "mame", emptyList(), "gamecom"),
            "megaduck" to SystemDefinition("megaduck", "Mega Duck", listOf("megaduck"), listOf(".bin"), "retroarch", "sameduck", emptyList(), "megaduck"),
            "gamate" to SystemDefinition("gamate", "Gamate", listOf("gamate"), listOf(".bin"), "retroarch", "mame", emptyList(), "gamate"),
            "gamepock" to SystemDefinition("gamepock", "Epoch Game Pocket", listOf("gamepock"), listOf(".bin"), "retroarch", "mame", emptyList(), "gamepock"),
            "cdi" to SystemDefinition("cdi", "Philips CD-i", listOf("cdi", "cdimono1"), listOf(".chd", ".iso", ".cue", ".bin"), "retroarch", "same_cdi", emptyList(), "cdi"),
            "supracan" to SystemDefinition("supracan", "Super A'Can", listOf("supracan"), listOf(".bin"), "retroarch", "mame", emptyList(), "supracan"),
            "mac" to SystemDefinition("mac", "Macintosh", listOf("mac", "macintosh", "vmac"), listOf(".dsk", ".img", ".hdv", ".iso"), "retroarch", "minivmac", emptyList(), "mac"),
            "palm" to SystemDefinition("palm", "Palm OS", listOf("palm"), listOf(".prc", ".pdb", ".zip", ".img"), "retroarch", "mu", emptyList(), "palm"),
            "ti99" to SystemDefinition("ti99", "TI-99/4A", listOf("ti99"), listOf(".ctg", ".bin", ".rpk"), "retroarch", "ti99", emptyList(), "ti99"),
            "oric" to SystemDefinition("oric", "Tangerine Oric", listOf("oric", "tanodragon"), listOf(".dsk", ".tap"), "retroarch", "mame", emptyList(), "oric"),
            "thomson" to SystemDefinition("thomson", "Thomson", listOf("thomson", "to8", "mo5"), listOf(".fd", ".k7", ".m7", ".rom"), "retroarch", "theodore", emptyList(), "thomson"),
            "samcoupe" to SystemDefinition("samcoupe", "Sam Coupé", listOf("samcoupe"), listOf(".dsk", ".mgt"), "retroarch", "simcoupe", emptyList(), "samcoupe"),
            "trs80" to SystemDefinition("trs80", "TRS-80", listOf("trs-80", "trs80", "coco", "coco3"), listOf(".ccc", ".dsk", ".cas"), "retroarch", "mame", emptyList(), "trs80"),

            // ---------- Fantasy consoles ----------
            "pico8" to SystemDefinition("pico8", "PICO-8", listOf("pico-8", "pico8"), listOf(".p8", ".png"), "retroarch", "fake08", emptyList(), "pico8"),
            "tic80" to SystemDefinition("tic80", "TIC-80", listOf("tic-80", "tic80"), listOf(".tic"), "retroarch", "tic80", emptyList(), "tic80"),
            "wasm4" to SystemDefinition("wasm4", "WASM-4", listOf("wasm4"), listOf(".wasm"), "retroarch", "wasm4", emptyList(), "wasm4"),
            "vircon32" to SystemDefinition("vircon32", "Vircon32", listOf("vircon32"), listOf(".v32"), "retroarch", "vircon32", emptyList(), "vircon32"),
            "chip8" to SystemDefinition("chip8", "CHIP-8", listOf("chip-8", "chip8"), listOf(".ch8", ".sch", ".xo8"), "retroarch", "chip8", emptyList(), "chip8"),

            // ---------- Other ----------
            "arduboy" to SystemDefinition("arduboy", "Arduboy", listOf("arduboy"), listOf(".hex", ".arduboy"), "retroarch", "arduous", emptyList(), "arduboy"),
            "uzebox" to SystemDefinition("uzebox", "Uzebox", listOf("uzebox"), listOf(".uze"), "retroarch", "uzem", emptyList(), "uzebox"),
            "vmu" to SystemDefinition("vmu", "Dreamcast VMU", listOf("vmu"), listOf(".vms", ".bin"), "retroarch", "vemulator", emptyList(), "vmu"),
            "scv" to SystemDefinition("scv", "Super Cassette Vision", listOf("scv"), listOf(".bin", ".0"), "retroarch", "mame", emptyList(), "scv"),
            "advision" to SystemDefinition("advision", "Entex Adventure Vision", listOf("advision"), listOf(".bin"), "retroarch", "mame", emptyList(), "advision"),
            "crvision" to SystemDefinition("crvision", "CreatiVision", listOf("crvision"), listOf(".bin", ".rom"), "retroarch", "mame", emptyList(), "crvision"),
            "vc4000" to SystemDefinition("vc4000", "Interton VC 4000", listOf("vc4000"), listOf(".bin"), "retroarch", "mame", emptyList(), "vc4000"),
            "pv1000" to SystemDefinition("pv1000", "Casio PV-1000", listOf("pv1000"), listOf(".bin"), "retroarch", "mame", emptyList(), "pv1000"),
            "apfm1000" to SystemDefinition("apfm1000", "APF MP-1000", listOf("apfm1000", "apf"), listOf(".bin"), "retroarch", "mame", emptyList(), "apfm1000"),
            "arcadia" to SystemDefinition("arcadia", "Emerson Arcadia 2001", listOf("arcadia"), listOf(".bin"), "retroarch", "mame", emptyList(), "arcadia"),
            "astrocade" to SystemDefinition("astrocade", "Bally Astrocade", listOf("astrocde", "astrocade"), listOf(".bin"), "retroarch", "mame", emptyList(), "astrocade"),
            "gametank" to SystemDefinition("gametank", "Game Tank", listOf("gametank"), listOf(".gtr"), "retroarch", "gametank", emptyList(), "gametank"),
            "gp32" to SystemDefinition("gp32", "GamePark GP32", listOf("gp32"), listOf(".smc"), "retroarch", "mame", emptyList(), "gp32"),
            "vsmile" to SystemDefinition("vsmile", "VTech V.Smile", listOf("vsmile"), listOf(".zip", ".bin"), "retroarch", "mame", emptyList(), "vsmile"),
            "socrates" to SystemDefinition("socrates", "VTech Socrates", listOf("socrates"), listOf(".bin"), "retroarch", "mame", emptyList(), "socrates"),
            "gmaster" to SystemDefinition("gmaster", "Hartung Game Master", listOf("gmaster"), listOf(".bin"), "retroarch", "mame", emptyList(), "gmaster"),
            "multivision" to SystemDefinition("multivision", "Tsukuda Multivision", listOf("multivision"), listOf(".bin"), "retroarch", "mame", emptyList(), "multivision"),
            "j2me" to SystemDefinition("j2me", "Java ME", listOf("j2me", "freej2me", "java"), listOf(".jar"), "retroarch", "freej2me", emptyList(), "j2me"),
            "openbor" to SystemDefinition("openbor", "OpenBOR", listOf("openbor"), listOf(".pak"), "retroarch", "openbor", emptyList(), "openbor")
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

    override suspend fun clearCache() {
        localDataSource.clearCache()
        _library.value = Library.EMPTY
    }

    override suspend fun setScraperCredentials(username: String, password: String) {
        localDataSource.setScraperAccount("$username:$password")
    }

    override suspend fun scrapeSystem(
        systemId: String,
        onProgress: ((gameName: String, current: Int, total: Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val library = _library.value
            val games = library.gamesBySystem[systemId] ?: return@withContext Result.success(0)
            var scraped = 0
            val downloadSemaphore = Semaphore(4)

            val chunks = games.chunked(4)
            var processed = 0

            for (chunk in chunks) {
                data class ScrapeResult(val game: Game, val info: ScrapedGame)
                val results = mutableListOf<ScrapeResult>()

                for (game in chunk) {
                    processed++
                    onProgress?.invoke(game.name, processed, games.size)
                    if (game.boxArtPath != null) continue

                    val romFileName = File(game.path).name
                    val info = scraperService.scrapeGame(romFileName, systemId) ?: continue
                    if (info.boxArtUrl == null) continue

                    results.add(ScrapeResult(game, info))
                    delay(250)
                }

                coroutineScope {
                    results.map { (game, info) ->
                        async {
                            downloadSemaphore.acquire()
                            try {
                                val localPath = scraperService.downloadBoxArt(
                                    info.boxArtUrl!!, systemId, game.name
                                ) ?: return@async

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
                                boxArtDao.upsert(BoxArtEntry(
                                    romPath = game.path,
                                    systemId = systemId,
                                    gameName = game.name,
                                    artPath = localPath,
                                    description = info.description,
                                    genre = info.genre,
                                    developer = info.developer,
                                    publisher = info.publisher,
                                    releaseDate = info.releaseDate,
                                    players = info.players,
                                    rating = info.rating
                                ))
                                scraped++
                            } finally {
                                downloadSemaphore.release()
                            }
                        }
                    }.awaitAll()
                }
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
            val downloadSemaphore = Semaphore(4)

            // Process in chunks: lookup API sequentially (rate limited), download images in parallel
            val chunks = allGames.chunked(4)
            var processed = 0

            for (chunk in chunks) {
                // Phase 1: API lookups (sequential, 250ms apart)
                data class ScrapeResult(
                    val systemId: String,
                    val game: Game,
                    val info: ScrapedGame
                )
                val results = mutableListOf<ScrapeResult>()

                for ((systemId, game) in chunk) {
                    processed++
                    val system = library.systems.find { it.id == systemId }
                    val systemName = system?.name ?: systemId
                    onProgress?.invoke(game.name, systemName, processed, allGames.size)

                    if (game.boxArtPath != null) continue

                    val romFileName = File(game.path).name
                    val info = scraperService.scrapeGame(romFileName, systemId) ?: continue
                    if (info.boxArtUrl == null) continue

                    results.add(ScrapeResult(systemId, game, info))
                    delay(250)
                }

                // Phase 2: Download images in parallel (CDN, no rate limit)
                coroutineScope {
                    results.map { (systemId, game, info) ->
                        async {
                            downloadSemaphore.acquire()
                            try {
                                val localPath = scraperService.downloadBoxArt(
                                    info.boxArtUrl!!, systemId, game.name
                                ) ?: return@async

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
                                boxArtDao.upsert(BoxArtEntry(
                                    romPath = game.path,
                                    systemId = systemId,
                                    gameName = game.name,
                                    artPath = localPath,
                                    description = info.description,
                                    genre = info.genre,
                                    developer = info.developer,
                                    publisher = info.publisher,
                                    releaseDate = info.releaseDate,
                                    players = info.players,
                                    rating = info.rating
                                ))
                                scraped++
                            } finally {
                                downloadSemaphore.release()
                            }
                        }
                    }.awaitAll()
                }
            }

            localDataSource.saveLibraryCache(_library.value)
            Result.success(scraped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateGameInLibrary(systemId: String, updatedGame: Game) {
        val current = _library.value
        val updatedGames = current.gamesBySystem.toMutableMap()
        updatedGames[systemId] = (updatedGames[systemId] ?: emptyList()).map { g ->
            if (g.path == updatedGame.path) updatedGame else g
        }
        _library.value = current.copy(gamesBySystem = updatedGames)
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
        val ARCHIVE_EXTENSIONS = setOf(".zip", ".7z", ".rar")
        val SKIP_DIRS = setOf(
            "bios", "images", "imgs", "media", "manuals", "videos", "video",
            "screenshots", "marquees", "box", "boxart", "cover", "covers",
            "downloaded_images", "gamelist", "cheats", "saves", "savestates",
            "states", "patches", "overlays", "shaders", "textures"
        )
        val SKIP_FILES = setOf(
            "gamelist.xml", "systeminfo.txt", "metadata.txt", "_info.txt",
            "desktop.ini", "thumbs.db", ".ds_store"
        )
    }
}
