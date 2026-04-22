package com.taizi.domain.model

/**
 * ROM Library domain models
 */

enum class BiosStatus {
    MISSING,
    PRESENT
}

enum class LibraryChange {
    ADDED,
    REMOVED,
    MODIFIED
}

data class System(
    val id: String,
    val name: String,
    val path: String,
    val romCount: Int,
    val emulatorType: String,
    val emulatorPackage: String?,
    val core: String?,
    val lastScanned: Long,
    val biosStatus: BiosStatus = BiosStatus.MISSING,
    val isCustom: Boolean = false,
    val mappedFrom: String? = null
) {
    companion object {
        val EMPTY = System(
            id = "",
            name = "",
            path = "",
            romCount = 0,
            emulatorType = "",
            emulatorPackage = null,
            core = null,
            lastScanned = 0L
        )
    }
}

data class Game(
    val path: String,
    val name: String,
    val systemId: String,
    val size: Long,
    val modified: Long,
    val playCount: Int = 0,
    val lastPlayed: Long? = null,
    val favorite: Boolean = false,
    val discs: List<GameDisc> = listOf(GameDisc(path, name)),
    val boxArtPath: String? = null,
    val metadata: GameMetadata? = null
) {
    val displayName: String
        get() = name.trim()

    val isMultiDisc: Boolean
        get() = discs.size > 1
}

data class GameDisc(
    val path: String,
    val name: String
)

data class GameMetadata(
    val description: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val players: Int? = null,
    val rating: Float? = null,
    val developer: String? = null,
    val publisher: String? = null
)

data class Library(
    val systems: List<System>,
    val gamesBySystem: Map<String, List<Game>>,
    val biosStatus: Map<String, BiosStatus>,
    val lastScanned: Long,
    val romRoot: String,
    val unmappedSystems: List<String> = emptyList()
) {
    val allGames: List<Game>
        get() = gamesBySystem.values.flatten()

    companion object {
        val EMPTY = Library(
            systems = emptyList(),
            gamesBySystem = emptyMap(),
            biosStatus = emptyMap(),
            lastScanned = 0L,
            romRoot = ""
        )
    }
}

data class EmulatorConfig(
    val type: String,
    val packageName: String?,
    val core: String? = null,
    val isInstalled: Boolean = false
)
