package com.taizi.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.FileObserver
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.taizi.data.local.LocalDataSource
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
    private val localDataSource: LocalDataSource
) : LibraryRepository {

    private val gson = Gson()
    private val _library = MutableStateFlow(Library.EMPTY)

    private var internalFileObserver: FileObserver? = null
    private var observationJob: Job? = null
    private val systemDefinitions: Map<String, SystemDefinition> by lazy {
        loadSystemDefinitions()
    }

    // Concurrency limiter to avoid overloading disk
    private val scanSemaphore = Semaphore(4) // Max 4 concurrent system scans

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

    override suspend fun scanLibrary(romRoot: String, force: Boolean): Result<Library> {
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

                // Build games map
                val gamesBySystem = systems.associate { system ->
                    system.id to scanGamesForSystem(system)
                }

                val library = Library(
                    systems = systems,
                    gamesBySystem = gamesBySystem,
                    biosStatus = biosStatus,
                    lastScanned = java.lang.System.currentTimeMillis(),
                    romRoot = romRoot,
                    unmappedSystems = systems.filter { it.isCustom && it.mappedFrom != null }
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

    private fun scanGamesForSystem(system: System): List<Game> {
        val folder = File(system.path)
        if (!folder.exists()) return emptyList()

        val def = systemDefinitions[system.id] ?: return emptyList()
        val validExtensions = def.extensions.map { it.lowercase() }.toSet()

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
            parseGameFile(file, system.id)
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

    private fun loadSystemDefinitions(): Map<String, SystemDefinition> {
        return try {
            context.assets.open("systems.json").reader().use { reader ->
                val json = reader.readText()
                val type = object : TypeToken<Map<String, SystemDefinition>>() {}.type
                gson.fromJson<Map<String, SystemDefinition>>(json, type) ?: emptyMap()
            }
        } catch (e: Exception) {
            // Fallback to built-in minimal definitions
            builtInSystemDefinitions()
        }
    }

    private fun builtInSystemDefinitions(): Map<String, SystemDefinition> {
        return mapOf(
            "gb" to SystemDefinition(
                id = "gb",
                name = "Game Boy",
                folderNames = listOf("gb", "gameboy", "gbc", "gameboycolor"),
                extensions = listOf(".gb", ".gbc"),
                emulator = "retroarch",
                core = "gambatte",
                bios = listOf("gb_bios.bin", "gbc_bios.bin"),
                theme = "gb"
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
            )
            // Add more as needed
        )
    }

    private fun getDefaultEmulatorConfig(def: SystemDefinition): EmulatorConfig {
        return when (def.emulator) {
            "retroarch" -> EmulatorConfig(
                type = "retroarch",
                packageName = "com.retroarch",
                core = def.core
            )
            "duckstation" -> EmulatorConfig(
                type = "standalone",
                packageName = "com.github.stenzek.duckstation",
                core = null
            )
            "ppsspp" -> EmulatorConfig(
                type = "standalone",
                packageName = "org.ppsspp.ppsspp",
                core = null
            )
            "drastic" -> EmulatorConfig(
                type = "standalone",
                packageName = "com.draustinus.drastic",
                core = null
            )
            "flycast" -> EmulatorConfig(
                type = "standalone",
                packageName = "com.flycast",
                core = null
            )
            else -> EmulatorConfig(
                type = "retroarch",
                packageName = "com.retroarch",
                core = def.core
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
