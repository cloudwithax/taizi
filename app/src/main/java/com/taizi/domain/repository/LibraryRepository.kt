package com.taizi.domain.repository

import com.taizi.domain.model.*
import com.taizi.domain.model.LibraryChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing the ROM library
 */
interface LibraryRepository {
    fun getLibrary(): StateFlow<Library>
    suspend fun loadCachedLibraryIfAvailable()
    suspend fun scanLibrary(romRoot: String, force: Boolean = false): Result<Library>
    suspend fun refreshSystem(systemId: String): Result<Library>
    suspend fun launchGame(game: Game): Result<Unit>
    suspend fun updateGamePlayStats(gamePath: String, playCount: Int, lastPlayed: Long): Result<Unit>
    suspend fun updateGameFavorite(gamePath: String, favorite: Boolean): Result<Unit>
    suspend fun getEmulatorConfig(systemId: String): EmulatorConfig?
    suspend fun setEmulatorConfig(systemId: String, config: EmulatorConfig)
    suspend fun getCustomMappings(): Map<String, String>
    suspend fun saveCustomMappings(mappings: Map<String, String>)
    suspend fun getBiosStatus(systemId: String): BiosStatus
    suspend fun findSystemForFolder(folderName: String): System?
    fun startFileObserver(romRoot: String, onChange: (LibraryChange) -> Unit)
    fun stopFileObserver()
}
