package com.taizi.domain.repository

import com.taizi.domain.model.*
import com.taizi.domain.model.LibraryChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LibraryRepository {
    fun getLibrary(): StateFlow<Library>
    suspend fun loadCachedLibraryIfAvailable()
    suspend fun scanLibrary(
        romRoot: String,
        force: Boolean = false,
        onProgress: ((gameName: String, systemName: String, count: Int, total: Int) -> Unit)? = null
    ): Result<Library>
    suspend fun refreshSystem(systemId: String): Result<Library>
    suspend fun launchGame(game: Game): Result<Unit>
    suspend fun updateGamePlayStats(gamePath: String, playCount: Int, lastPlayed: Long): Result<Unit>
    suspend fun updateGameFavorite(gamePath: String, favorite: Boolean): Result<Unit>
    suspend fun getEmulatorConfig(systemId: String): EmulatorConfig?
    suspend fun setEmulatorConfig(systemId: String, config: EmulatorConfig)
    suspend fun getCustomMappings(): Map<String, String>
    suspend fun saveCustomMappings(mappings: Map<String, String>)
    suspend fun getBiosStatus(systemId: String): BiosStatus
    suspend fun getNowPlayingSystems(): Set<String>
    suspend fun setNowPlayingEnabled(systemId: String, enabled: Boolean)
    suspend fun findSystemForFolder(folderName: String): System?
    suspend fun clearCache()
    suspend fun setScraperCredentials(username: String, password: String)
    suspend fun scrapeSystem(
        systemId: String,
        onProgress: ((gameName: String, current: Int, total: Int) -> Unit)? = null
    ): Result<Int>
    suspend fun scrapeAll(
        onProgress: ((gameName: String, systemName: String, current: Int, total: Int) -> Unit)? = null
    ): Result<Int>
    fun startFileObserver(romRoot: String, onChange: (LibraryChange) -> Unit)
    fun stopFileObserver()
}
