package com.taizi.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taizi.data.scraper.BoxArtScrapeService
import com.taizi.data.scraper.ScrapeStatus
import com.taizi.domain.model.*
import com.taizi.domain.model.LibraryChange
import com.taizi.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanProgress(
    val gameName: String = "",
    val systemName: String = "",
    val count: Int = 0,
    val total: Int = 0
)

sealed class MainUiState {
    object Loading : MainUiState()
    object Initial : MainUiState()
    object Scanning : MainUiState()
    data class LibraryLoaded(val library: Library) : MainUiState()
    data class Error(val message: String) : MainUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LibraryRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.SystemList)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private var systemPagerPage: Int = 0
    fun getSystemPagerPage(): Int = systemPagerPage
    fun setSystemPagerPage(page: Int) { systemPagerPage = page }

    private val gameListScroll = mutableMapOf<String, Pair<Int, Int>>()
    fun getGameListScroll(systemId: String): Pair<Int, Int> =
        gameListScroll[systemId] ?: (0 to 0)
    fun setGameListScroll(systemId: String, index: Int, offset: Int) {
        gameListScroll[systemId] = index to offset
    }

    private var searchScroll: Pair<Int, Int> = 0 to 0
    fun getSearchScroll(): Pair<Int, Int> = searchScroll
    fun setSearchScroll(index: Int, offset: Int) { searchScroll = index to offset }

    private var appDrawerScroll: Pair<Int, Int> = 0 to 0
    fun getAppDrawerScroll(): Pair<Int, Int> = appDrawerScroll
    fun setAppDrawerScroll(index: Int, offset: Int) { appDrawerScroll = index to offset }

    private var settingsScroll: Pair<Int, Int> = 0 to 0
    fun getSettingsScroll(): Pair<Int, Int> = settingsScroll
    fun setSettingsScroll(index: Int, offset: Int) { settingsScroll = index to offset }

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private var scanJob: Job? = null
    private var fileObserverJob: Job? = null
    private var scrapeCollectionJob: Job? = null

    init {
        viewModelScope.launch {
            repository.loadCachedLibraryIfAvailable()
            val cached = repository.getLibrary().value
            if (cached.romRoot.isNotEmpty()) {
                _uiState.value = MainUiState.LibraryLoaded(cached)
                startFileObserver(cached.romRoot)
            } else {
                _uiState.value = MainUiState.Initial
            }
        }
        viewModelScope.launch {
            repository.getLibrary().collect { lib ->
                if (lib.romRoot.isNotEmpty() && _uiState.value is MainUiState.LibraryLoaded) {
                    _uiState.value = MainUiState.LibraryLoaded(lib)
                }
            }
        }
    }

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun navigateToSystem(systemId: String) {
        _currentScreen.value = Screen.GameList(systemId)
    }

    fun navigateBack() {
        _currentScreen.value = Screen.SystemList
    }

    fun triggerFullScan(romRoot: String? = null) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val hadLibrary = _uiState.value is MainUiState.LibraryLoaded
            val isNewRoot = romRoot != null
            if (!hadLibrary || isNewRoot) _uiState.value = MainUiState.Scanning

            val root = romRoot ?: repository.getLibrary().value.romRoot
            if (root.isEmpty()) {
                if (!hadLibrary || isNewRoot) _uiState.value = MainUiState.Error("No ROM root configured")
                return@launch
            }

            _scanProgress.value = ScanProgress()
            val result = repository.scanLibrary(root, force = true) { gameName, systemName, count, total ->
                _scanProgress.value = ScanProgress(gameName, systemName, count, total)
            }
            result.fold(
                onSuccess = { library ->
                    _uiState.value = MainUiState.LibraryLoaded(library)
                    startFileObserver(library.romRoot)
                },
                onFailure = { error ->
                    if (!hadLibrary || isNewRoot) _uiState.value = MainUiState.Error(error.message ?: "Scan failed")
                }
            )
        }
    }

    private fun startFileObserver(romRoot: String) {
        fileObserverJob?.cancel()
        repository.startFileObserver(romRoot) { change ->
            viewModelScope.launch {
                delay(500)
                triggerFullScan(romRoot)
            }
        }
    }

    fun launchGame(game: Game) {
        viewModelScope.launch {
            val result = repository.launchGame(game)
            if (result.isFailure) {
                android.util.Log.e("Taizi", "Launch failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun toggleFavorite(gamePath: String, favorite: Boolean) {
        viewModelScope.launch {
            repository.updateGameFavorite(gamePath, favorite)
        }
    }

    fun getSystemById(systemId: String): System? {
        if (systemId == FAVORITES_SYSTEM_ID) return buildFavoritesSystem(repository.getLibrary().value)
        return repository.getLibrary().value.systems.find { system: System -> system.id == systemId }
    }

    fun getGamesForSystem(systemId: String): List<Game> {
        if (systemId == FAVORITES_SYSTEM_ID) {
            return repository.getLibrary().value.gamesBySystem.values.flatten().filter { it.favorite }
        }
        return repository.getLibrary().value.gamesBySystem[systemId] ?: emptyList()
    }

    fun getAllGames(): List<Game> {
        return repository.getLibrary().value.gamesBySystem.values.flatten()
    }

    fun systemsForDisplay(library: Library): List<System> {
        val fav = buildFavoritesSystem(library) ?: return library.systems
        return listOf(fav) + library.systems
    }

    private fun buildFavoritesSystem(library: Library): System? {
        val count = library.gamesBySystem.values.sumOf { games -> games.count { it.favorite } }
        if (count == 0) return null
        return System(
            id = FAVORITES_SYSTEM_ID,
            name = "Favorites",
            path = "",
            romCount = count,
            emulatorType = "",
            emulatorPackage = null,
            core = null,
            lastScanned = 0L
        )
    }

    companion object {
        const val FAVORITES_SYSTEM_ID = "__favorites__"
    }

    val scrapeStatus: StateFlow<ScrapeStatus> = BoxArtScrapeService.status

    fun scrapeAll() {
        BoxArtScrapeService.start(appContext)
        scrapeCollectionJob?.cancel()
        scrapeCollectionJob = viewModelScope.launch {
            scrapeStatus.collect { status ->
                if (!status.isRunning && status.current > 0) {
                    val lib = repository.getLibrary().value
                    if (lib.romRoot.isNotEmpty()) {
                        _uiState.value = MainUiState.LibraryLoaded(lib)
                    }
                }
            }
        }
    }

    fun cancelScrape() {
        BoxArtScrapeService.stop(appContext)
        scrapeCollectionJob?.cancel()
        scrapeCollectionJob = null
    }

    fun setScraperCredentials(username: String, password: String) {
        viewModelScope.launch {
            repository.setScraperCredentials(username, password)
        }
    }

    fun clearCache() {
        scanJob?.cancel()
        fileObserverJob?.cancel()
        repository.stopFileObserver()
        viewModelScope.launch {
            repository.clearCache()
            _uiState.value = MainUiState.Initial
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        fileObserverJob?.cancel()
        scrapeCollectionJob?.cancel()
        repository.stopFileObserver()
    }
}

sealed class Screen {
    object SystemList : Screen()
    data class GameList(val systemId: String) : Screen()
    object Settings : Screen()
    object AppDrawer : Screen()
    object Search : Screen()
    object SystemManager : Screen()
    object EmulatorManager : Screen()
    object Scraper : Screen()
    object BiosStatus : Screen()
}
